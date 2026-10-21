# Техническое задание: UltraCore — v3

> **Отступления, принятые при реализации (2026-10-21):**
> - `minSdk = 31` (Android 12) оставлен: требование «Android 15+» выполняется, а старые телефоны не отсекаются. Код, которому нужен новый API, проверяет `Build.VERSION.SDK_INT`.
> - `compileSdk/targetSdk = 35` и AGP 8.7.3 оставлены, чтобы не ломать стабильную сборку в CI. Переход на compileSdk 37 — отдельным шагом.
> - Терминал (фаза 3): история и макросы хранятся в JSON, а не в Room — без плагина KSP сборка в CI надёжнее; для сотен записей этого достаточно.
> - Hilt не используется (как и раньше в проекте): ViewModel + синглтоны. Room добавится в фазе 3, когда понадобится история команд. Результаты CapabilityChecker пока хранятся в JSON.
> - hiddenapibypass добавится в фазах 4–5, когда понадобятся скрытые API.
> - Навигация: 4 вкладки — **Главная** (Обзор | Оптимизация), **Файлы** (Очистка | Сортировка), **Приложения**, **Терминал**. Старые функции UltraCore уже встроены, поэтому фаза 6 сводится к экрану совместимости.

## 1. Концепция
**UltraCore** — системный комбайн для **любого Android-смартфона на Android 15, 16, 17 и новее**. Работает без Root через **Shizuku (ADB, UID 2000)**. Если Shizuku запущен с Root (UID 0), функции, которым нужен Root, включаются автоматически.

Проект — развитие существующего репозитория UltraCore. Изначально он был под Galaxy S25 Ultra, теперь становится универсальным. Уже сделанные функции (очистка мусора, сортировка файлов) сохраняются: в Phase 1 их нужно проверить и переписать без привязки к модели телефона.

Четыре вкладки (Material 3 + Pure AMOLED):
1. **Dashboard & Tweaker** — паспорт батареи и быстрые твики.
2. **Activity Launcher** — запуск экранов приложений и ярлыки.
3. **App Manager & Sandbox** — заморозка, бэкап APK, установка, «песочница».
4. **Shell Deck** — терминал, каталог команд, макросы.

### Главный принцип: никакой привязки к производителю
- Используются только **стандартные возможности Android**: публичные API Android SDK и команды AOSP (`pm`, `am`, `settings`, `cmd`, `dumpsys`, `device_config`).
- **В коде нет веток вида «если Samsung / если Xiaomi».** Доступность функции определяется **проверкой на самом телефоне**, а не по марке. Приложение пробует безопасно, смотрит результат и запоминает его.
- Каждая функция показывается в одном из трёх состояний:
  - ✅ работает;
  - ⚠️ работает частично / прошивка изменила поведение (с пояснением);
  - 🔒 недоступно (с причиной: «нужен Root», «прошивка запрещает эту команду»).
- Ошибки не скрываются: пользователь видит понятное пояснение и текст ошибки.

---

## 2. Технический стек (Strict Rules for AI)
- **Язык:** Kotlin 2.x (без Java, кроме сгенерированных AIDL).
- **UI:** 100% Jetpack Compose (Material 3), Dynamic Colors + режим Pure AMOLED (`#000000`). XML — только манифест и базовая тема.
- **Edge-to-edge обязателен** (с Android 15 включается принудительно): `enableEdgeToEdge()`, корректная обработка `WindowInsets`. Поддержка predictive back.
- **Архитектура:** Single-Activity, MVVM/MVI, слои `core-shizuku`, `data`, `domain`, `ui`.
- **Асинхронность:** Coroutines + `StateFlow`/`SharedFlow`.
- **DI:** Hilt (если конфликт версий KSP — Koin).
- **Хранилище:** Room (история команд, избранное, макросы, песочница, результаты проверок функций) + DataStore (настройки).
- **Навигация:** Navigation Compose (Type-Safe).
- **Сборка:** Gradle Kotlin DSL + Version Catalog.
- **SDK:** `minSdk = 35` (Android 15), `compileSdk = 37` (Android 17), `targetSdk = 36` (повышать до 37 после тестов).
- Все версии библиотек — **последние стабильные на момент сборки**. ИИ обязан их проверить, а не брать из памяти.

### Зависимости
```
dev.rikka.shizuku:api        (последняя стабильная, не ниже 13.1.5)
dev.rikka.shizuku:provider   (та же версия)
org.lsposed.hiddenapibypass:hiddenapibypass (последняя стабильная)
```
Shizuku лежит в Maven Central, JitPack не нужен.

---

## 3. Ядро (`core-shizuku`)

### ShizukuStateManager
- Слушатели: `OnBinderReceivedListener`, `OnBinderDeadListener`, `OnRequestPermissionResultListener`.
- `StateFlow<ShizukuStatus>`: `NOT_INSTALLED`, `NOT_RUNNING`, `PERMISSION_REQUIRED`, `READY(mode = ADB | ROOT)`.
- Экран-помощник: как запустить Shizuku через «Отладку по Wi-Fi» без компьютера. Предупреждение: после перезагрузки телефона Shizuku нужно запускать заново (без Root).

### ShellExecutor — через AIDL UserService
- `suspend fun exec(cmd): ShellResult(exitCode, stdout, stderr, truncated)`. Вывод обрезается до 256 КБ (лимит Binder ~1 МБ).
- `fun stream(cmd): Flow<String>` — для долгих команд. Вывод идёт через `ParcelFileDescriptor.createPipe()`, клиент читает его построчно.
- Таймауты, принудительное закрытие потоков, отмена через корутину.
- Ленивое подключение, `daemon = false`, автоотключение через 60 сек простоя.
- `HiddenApiBypass.addHiddenApiExemptions("")` вызывается в `Application.onCreate()` и в `init {}` UserService.

### CapabilityChecker (вместо адаптеров производителей)
- Для каждой функции есть **безопасная пробная проверка**, например: прочитать текущее значение настройки и записать его же обратно, или выполнить команду в режиме, который ничего не меняет.
- Результат (✅/⚠️/🔒 + причина) сохраняется в Room. Проверка повторяется после обновления системы (смена `Build.FINGERPRINT`) или по кнопке «Проверить заново».
- **ErrorClassifier** разбирает stderr по общим признакам: `SecurityException`, `Permission Denial`, `not allowed`, `INSTALL_FAILED_*`, `Unknown command`. По ним выдаётся понятное пояснение. Общий совет: «Ваша прошивка ограничивает команды отладки. Проверьте в "Для разработчиков" дополнительные переключатели отладки, а в настройках безопасности — функции блокировки USB/ADB-команд».

---

## 4. Функциональные модули

### Модуль 1: Dashboard & Tweaker
**Карточка Shizuku:** статус, кнопка запроса прав, переход в приложение Shizuku.

**Паспорт батареи** — от стандартного к дополнительному:
1. Публичное API (без Shizuku): уровень, температура, напряжение, статус, технология из `ACTION_BATTERY_CHANGED`. **Циклы** — `BatteryManager.EXTRA_CYCLE_COUNT`. Ёмкость и прочее — `BatteryManager.getIntProperty/getLongProperty`, если прошивка отдаёт значения.
2. `dumpsys battery` через Shizuku. Парсер **универсальный**: разбирает все пары «ключ: значение». Известные ключи (циклы, здоровье, ёмкость, ASOC и т.п.) показываются в карточках, остальные — в раскрывающемся списке «Дополнительные данные от прошивки».
3. `/sys/class/power_supply/battery/*` — только если чтение разрешено (на многих телефонах запрещено, это нормально).

У каждого значения подписан источник. Если данных нет — пишем «Недоступно на этом устройстве», а не 0.

**Быстрые твики (только стандартные настройки Android):**
- **Анимации** (0x / 0.5x / 1x): `settings put global window_animation_scale | transition_animation_scale | animator_duration_scale`.
- **Частота экрана:** `settings put system peak_refresh_rate` / `min_refresh_rate`. Варианты берутся из `Display.getSupportedModes()`. После применения приложение проверяет фактическую частоту (`Display.getRefreshRate()`). Если прошивка её не применила — ⚠️ «Ваш телефон управляет частотой своими настройками, используйте системное меню».
- **Phantom Process Killer:** сначала инструкция про штатный переключатель в «Для разработчиков» → «Отключить ограничения дочерних процессов». Запасной вариант: `device_config set_sync_disabled_for_tests persistent` + `device_config put activity_manager max_phantom_processes 2147483647`, плюс кнопка «Вернуть как было».
- **Очистка кэша:** `pm trim-caches 999G`. При отказе — ⚠️.
- У каждого твика есть кнопка «Сбросить по умолчанию». Исходные значения сохраняются перед первым изменением.

### Модуль 2: Activity Launcher
- Сканер: `getInstalledPackages(PackageInfoFlags.of(GET_ACTIVITIES or MATCH_DISABLED_COMPONENTS))`, асинхронно, с кэшем.
- Поиск по имени, пакету и классу. Фильтры: Все / Системные / Пользовательские / Не экспортируемые.
- Запуск:
  - `exported = true` → обычный `Intent`;
  - `exported = true` с защитой разрешением → `am start -n pkg/cls` через Shizuku;
  - `exported = false` → только в режиме ROOT. В режиме ADB показывается 🔒 «нужен Root».
- Ошибки показываются понятным Snackbar.
- Избранное (Room) и ярлыки на рабочий стол через `ShortcutManagerCompat` → прозрачная `ProxyActivity`.

### Модуль 3: App Manager & Sandbox
- Мультивыбор, пакетные операции.
- Заморозка: `pm disable-user --user 0` / `pm enable` (значок ❄️).
- Скрытие (`pm hide`) — только в режиме ROOT. В режиме ADB кнопка 🔒, взамен предлагается заморозка.
- Удаление для пользователя: `pm uninstall -k --user 0` + восстановление `cmd package install-existing`. Для критичных системных компонентов — предупреждение. Список определяется **по ролям Android** (`RoleManager`: лаунчер, телефон, SMS), по пакету `android`, SystemUI и Настройкам, а не по названиям пакетов конкретных марок.
- **Экспорт APK:** пути из `ApplicationInfo.sourceDir` и `splitSourceDirs`, сохранение через MediaStore в `Download/UltraCore/Backups`. Split-APK сохраняются как `.apks` (ZIP).
- **Установка (`.apk` и `.apks`) — сессией:**
  1. `pm install-create -r [-d] [--bypass-low-target-sdk-block]` → `session_id`;
  2. для каждого APK: `pm install-write -S <размер> <session_id> <имя> -` (stdin);
  3. `pm install-commit <session_id>`, при ошибке — `pm install-abandon`.
  - Откат версии (`-d`) — экспериментальный: без Root работает только для debuggable-приложений, иначе показывается понятное объяснение.
  - Опция (по умолчанию выкл.) «Указывать установщик Google Play» (`-i com.android.vending`) — для прошивок, которые блокируют установку от shell.
- **Песочница:**
  - По нажатию: `pm enable` → запуск. После выключения экрана и задержки (0 / 30 сек / 2 мин, по умолчанию 30 сек) — `pm disable-user`.
  - `ACTION_SCREEN_OFF` регистрируется динамически (`RECEIVER_NOT_EXPORTED`) в foreground service типа `specialUse`. Сервис работает, только пока есть размороженные приложения из песочницы. Ожидание идёт под `PARTIAL_WAKE_LOCK`.
  - Очередь `PENDING_FREEZE` в Room. Если Shizuku недоступен, заморозка выполнится при следующем подключении или следующем запуске UltraCore.
  - Общая подсказка: убрать UltraCore и Shizuku из оптимизации батареи (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) и разрешить работу в фоне / автозапуск в настройках телефона, если такой пункт есть.

### Модуль 4: Shell Deck & Macros
- Терминал: ввод с автодополнением из истории, моноширинный вывод с автопрокруткой, кнопки Копировать / Очистить / Стоп / Экспорт в `.txt`.
- `assets/commands.json` — 30+ **стандартных AOSP-команд** по категориям (Сеть, Батарея, Дисплей, Пакеты, Диагностика) с описанием RU/EN, параметрами и полем `minSdk`.
- Макросы: цепочка команд одной кнопкой — с главного экрана, ярлыком или плиткой в шторке (`TileService`, открытие экрана через `startActivityAndCollapse(PendingIntent)`).
- Перед запуском макроса показываются команды и запрашивается подтверждение.

---

## 5. Манифест и разрешения
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:enabled="true"
    android:exported="true"
    android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

<service
    android:name=".sandbox.SandboxService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Auto-freeze sandboxed apps when the screen turns off" />
</service>
```
Распространение — APK вне Google Play.

---

## 6. CI/CD (GitHub Actions)
`.github/workflows/android-build.yml`:
- запуск на push в `main` и `workflow_dispatch`;
- JDK 17 (Temurin), или 21, если этого требует версия Android Gradle Plugin;
- Android SDK с платформой 37;
- кэш Gradle (`gradle/actions/setup-gradle`);
- `chmod +x ./gradlew` → `./gradlew assembleDebug`;
- `app-debug.apk` загружается в Artifacts с именем `UltraCore-debug-<номер сборки>`.

---

## 7. План реализации
**ВНИМАНИЕ ДЛЯ CLAUDE CODE:** работаем строго по фазам. После каждой фазы — сборка (`./gradlew assembleDebug` или зелёный GitHub Actions) и отчёт.

- **Phase 1 — Фундамент:** ревизия текущего репозитория UltraCore (что оставить, что переписать); обновление Gradle/SDK/Version Catalog; workflow; манифест; тема (M3 + AMOLED, edge-to-edge); навигация на 4 вкладки; `core-shizuku` (StateManager, ShellExecutor, CapabilityChecker, ErrorClassifier).
- **Phase 2 — Dashboard:** паспорт батареи с универсальным парсером, твики с проверкой результата и сбросом.
- **Phase 3 — Shell Deck:** терминал, Room, `commands.json`, TileService.
- **Phase 4 — Activity Launcher:** сканер, фильтры, запуск ADB/ROOT, ярлыки.
- **Phase 5 — App Manager & Sandbox:** список, пакетные операции, экспорт и установка APK, песочница.
- **Phase 6 — Перенос старых функций UltraCore** (очистка мусора, сортировка файлов) в общую архитектуру + экран «Что работает на моём телефоне» (сводка ✅⚠️🔒 по CapabilityChecker).
