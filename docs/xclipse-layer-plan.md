# Xclipse Compatibility Layer для форка Winlator 11.2 — архитектура и план

Статус: **черновик на согласование. Исходники Winlator не менялись.**
Все выводы ниже проверены по реальному коду `brunodev85/winlator` @ `v11.2.0`
(подмодули: `app` @ `c2f4ad4`, `vortek` @ `ab7329c`, `gladio` @ `eaa2a8d`).
Пути даны относительно подмодуля `app` (`app/app/src/main/...` в суперпроекте).

---

## 0. Что выяснено по коду

| # | Факт | Где |
|---|------|-----|
| 1 | Тег `v11.2.0` существует. Но `versionName` в приложении у этого коммита всё ещё `"11.1"`, README ссылается на `Winlator_11.1.apk`. | `app/build.gradle:11`, `README.md` |
| 2 | Суперпроект почти пустой: Android-код лежит в подмодулях `winlator-app`, `vortek`, `gladio`. Все публичные. Лицензия **LGPL-2.1** (не GPL): форк обязан сохранить лицензию и авторство. | `.gitmodules`, `LICENSE` |
| 3 | Сборка: `compileSdk 34`, NDK `24.0.8215888`, CMake `3.22.1`, ABI только `arm64-v8a`. Git LFS нет, ассеты ~164 МБ обычными файлами (самый крупный `rootfs.tzst` 65 МБ). | `app/build.gradle`, `src/main/assets` |
| 4 | Определение GPU умеет **только Adreno** (regex по `GL_RENDERER`). Ни Mali, ни Xclipse в Java-коде нет вообще (grep `xclipse/exynos/samsung` пуст). | `core/GPUHelper.java` (`getAdrenoModelId`) |
| 5 | Vulkan-драйверы в выборе: только `turnip` и `vortek`. Дефолт: Adreno → Turnip, **всё остальное → Vortek**. Значит Xclipse уже идёт через Vortek. | `container/GraphicsDrivers.java` (`getItems`, `getDefaultDriver`) |
| 6 | «Штатный Android Vulkan fallback» уже есть: если пользовательский драйвер не задан, Vortek делает `dlopen(LIBVULKAN_PATH)`, то есть системный Vulkan. | `cpp/vortekrenderer/src/main.c` (`openVulkanLibrary`) |
| 7 | Импорт драйвера существует **только внутри Vortek**: поле «Adrenotools Driver». Это ZIP с `*.json` (`name`, `libraryName`) + `.so`. Путь: `GeneralComponents` → `VortekRendererComponent.Options.libvulkanPath` → `adrenotools_open_libvulkan(..., ADRENOTOOLS_DRIVER_CUSTOM, ...)`. | `core/GeneralComponents.java:185-200, 290-298`, `xenvironment/components/VortekRendererComponent.java:67-68`, `contentdialog/VortekConfigDialog.java:47-48` |
| 8 | Qualcomm-специфичный код (`kgsl`) в `libadrenotools` включается **отдельными флагами** (`GPU_MAPPING_IMPORT`, `FILE_REDIRECT`). Winlator передаёт только `CUSTOM`, то есть это подмена загружаемой `libvulkan` без kgsl. Теоретически подходит и для не-Qualcomm драйвера. **На Xclipse не проверялось.** | `cpp/libadrenotools/src/hook/hook_impl.cpp:80,93,127` |
| 9 | В Vortek есть обходы для **Mali** (по `deviceName` содержит `"Mali"`): `checkClipDistance`, `removeImageBoundCheck`. Для Xclipse обходов нет. | `cpp/vortekrenderer/src/shader_inspector.c:594` (серверная часть Vortek внутри `app`; подмодуль `vortek` это клиент в госте Wine) |
| 10 | Профили на игру уже существуют: «extras» ярлыка (`graphicsDriver`, `graphicsDriverConfig`, `dxwrapper`, `box64Preset`, `screenSize`, `forceFullscreen`, `controlsProfile`…) переопределяют настройки контейнера. | `XServerDisplayActivity.java:206-211`, `contentdialog/ShortcutSettingsDialog.java` |
| 11 | Выбор DXVK: `2.4.1` при Turnip или Vulkan ≥ 1.3 на Vortek, иначе `1.10.3`. | `core/DefaultVersion.java` (`DXVK(String)`) |

### Внешние данные (не код, проверено по источникам)

- **radv-xclipse** (порт RADV для Xclipse, автор JimVulkan) существует и заявлен как «ранняя стадия»,
  по данным [Android Authority, 12.11.2026](https://androidauthority.com/samsung-custom-driver-emulation-exynos-3734301).
  **Сам репозиторий и формат пакета я не нашла**, поэтому совместимость с импортом Winlator (п. 7) не подтверждена.
- В форке Winlator Bannerlator на Galaxy S25 FE (Xclipse 940) сообщали: GTA IV — нестабильный frame pacing,
  GTA V — случайные вылеты, возможно из-за памяти ([issue](https://github.com/The412Banner/Bannerlator/issues/134)).
  Это отчёт одного пользователя, не измерение.
- Список моделей: 920 (Exynos 2200), 940 (2400), 950 (2500), 960 (2600), 540 (1580), 530 — подтверждаются
  новостными источниками. **550 по найденным источникам не подтверждён**, детектор не должен зависеть
  от жёсткого списка (см. ниже).

---

## 1. Архитектура

Пакет: `com.winlator.xclipse` (новая папка `java/com/winlator/xclipse/` в подмодуле `app`).
Принцип: **аддитивно, ноль правок поведения для не-Xclipse.** Каждая точка входа начинается с
`if (gpu.vendor != XCLIPSE) return passthrough`.

```
com.winlator.xclipse
├── GpuDetector            GL_RENDERER (через существующий GPUHelper) + Vulkan deviceName/vendorId/driverId
├── VulkanCapabilityProbe  apiVersion, расширения, лимиты памяти, фичи, нужные DXVK/Vortek (JNI)
├── DriverManager          обёртка над существующим ADRENOTOOLS_DRIVER; только валидация и выбор
├── CompatibilityManager   решение: какой драйвер/DXVK/пресет, причина fallback
├── GameProfileManager     JSON-профили GTA IV / GTA V -> «extras» ярлыка
├── BenchmarkManager       замеры frametime (поздняя фаза)
└── XclipseLog             диагностические логи, экспорт файлом
```

| Класс | Ответственность | Опирается на существующее |
|-------|-----------------|---------------------------|
| `GpuDetector` | `GpuInfo{vendor, model, vkDeviceName, vkVendorId, vkDriverId, apiVersion}`. Xclipse определяется по regex `xclipse\s*(\d{3})` **и** по Vulkan vendorId Samsung (`0x144D`). Неизвестная модель Xclipse = «Xclipse (unknown)», а не ошибка. | `GPUHelper.glGetRenderer`, `vkGetApiVersion` |
| `VulkanCapabilityProbe` | Нативная функция вроде `vkGetDeviceInfo()` рядом с существующей `vkGetDeviceExtensions()`. Выдаёт deviceName, driverID, версию API, нужные расширения, `maxMemoryAllocation`. | `GPUHelper.vkGetDeviceExtensions` |
| `DriverManager` | Список установленных пользовательских драйверов + проверка метаданных (`gpuFamilies`, `minApi`, `arch`), **никакой автоустановки**. Выбор драйвера остаётся действием пользователя. | `GeneralComponents.Type.ADRENOTOOLS_DRIVER` |
| `CompatibilityManager` | Вход: `GpuInfo` + capabilities + выбранный драйвер. Выход: `Decision{vulkanDriver, libvulkanPath|null, dxvkVersion, reason}`. Любая ошибка = штатный fallback (`libvulkanPath = null`, Vortek/System). | `VortekRendererComponent.Options`, `DefaultVersion.DXVK` |
| `GameProfileManager` | JSON из `assets/xclipse/profiles/*.json`. Применяются **только по кнопке** «Xclipse-профиль» в настройках ярлыка и пишут лишь существующие ключи extras. Контейнер не меняется. | `Shortcut.putExtra`, `ShortcutSettingsDialog` |
| `BenchmarkManager` | Короткий замер frametime/1% low, сохранение в лог. | — |
| `XclipseLog` | Файл в `files/xclipse/logs/`, кнопка «поделиться отчётом». | — |

### Куда класть данные

Предложенная тобой структура с папками `xclipse/{drivers,profiles,compatibility,benchmarks}` рядом с `vortek/` и `gladio/`
в суперпроекте **Gradle в APK не упакует** (он собирает только модуль `app`). Рекомендую:

```
app/app/src/main/assets/xclipse/
    profiles/gta4.json  gta5.json
    compatibility/known-gpus.json     таблица моделей и заметок
    drivers/README.md                 описание формата манифеста (драйверы НЕ вшиваются)
    benchmarks/
```

Папку `xclipse/` в суперпроекте можно завести позже для нативных исходников, если они понадобятся.

---

## 2. Список изменяемых файлов

### Новые (подмодуль `app`)

- `java/com/winlator/xclipse/{GpuDetector,VulkanCapabilityProbe,DriverManager,CompatibilityManager,GameProfileManager,BenchmarkManager,XclipseLog}.java`
- `cpp/…` небольшая JNI-функция для `VulkanCapabilityProbe` (в существующей библиотеке `winlator`, где уже лежит `vkGetDeviceExtensions`)
- `assets/xclipse/**` (см. выше)
- `res/layout/xclipse_report_dialog.xml`, строки в `values/strings.xml`

### Существующие, точечные правки (всё остальное не трогаем)

| Файл | Правка | Фаза |
|------|--------|------|
| `XServerDisplayActivity.java` ~стр. 533 | Один вызов `CompatibilityManager.preflight(...)` перед созданием `VortekRendererComponent`; при проблеме подставляет `libvulkanPath = null`. | 3 |
| `contentdialog/VortekConfigDialog.java` ~стр. 47 | Для Xclipse: метка/предупреждение у выбранного драйвера (совместим / не проверен / не подходит). | 2 |
| `contentdialog/ShortcutSettingsDialog.java` | Кнопка «Xclipse-профиль» (видна только на Xclipse). | 3 |
| `core/GPUHelper.java` | **Не меняем** (используем публичные методы). | — |
| `container/GraphicsDrivers.java` | **Не меняем** (дефолт для Xclipse уже Vortek). | — |

### Не меняем вообще

`Container.java`, `ContainerManager.java` (контейнеры), `TurnipConfigDialog`, `libadrenotools`,
`gladio`, Wine/rootfs, Box64, логику `getDefaultDriver`. Vortek по умолчанию тоже не трогаем:
правки `cpp/vortekrenderer/src/shader_inspector.c` (по образцу обходов для Mali) возможны **только** если диагностика покажет конкретный дефект.

---

## 3. План по фазам

| Фаза | Что делаем | Критерий готовности |
|------|-----------|---------------------|
| **0** (блокер, без кода) | Найти репозиторий и формат пакета `radv-xclipse`; понять, грузится ли он через `ADRENOTOOLS_DRIVER_CUSTOM`. Найти Samsung-тестера. | Известен формат; есть устройство с Xclipse |
| **1** Диагностика | `GpuDetector` + `VulkanCapabilityProbe` + `XclipseLog` + диалог «Отчёт об устройстве». Поведение игры **не меняется**. | Отчёт корректен на Xclipse; на Pixel (Mali) и Adreno ничего не изменилось |
| **2** DriverManager | Валидация и метки пользовательских драйверов, только ручной выбор. | Несовместимый драйвер нельзя выбрать молча |
| **3** Совместимость и профили | `CompatibilityManager` с fallback, профили GTA IV/V из реальных логов, кнопка в ярлыке. | Любая ошибка драйвера откатывается на системный Vulkan |
| **4** Бенчмарк | `BenchmarkManager`, сравнение профилей. | Повторяемые замеры |
| **CI** | Workflow для форка: `sdkmanager "ndk;24.0.8215888" "cmake;3.22.1"`, `assembleDebug`, APK в релиз. Проверю на реальной сборке до любых правок. | Чистый форк собирается без изменений |

Профили GTA IV/V **не пишутся наугад**: значения берутся из логов фазы 1. Единственное, что известно уже сейчас, это
наборы extras, которые Winlator принимает (п. 10).

---

## 4. Риски и открытые вопросы

1. **Нет Xclipse-устройства у автора заявки (Google Pixel).** Pixel 6–9 на Tensor используют Mali, Pixel 10 — PowerVR, поэтому
   Xclipse-ветки на нём не запустятся. Pixel годится как **регрессионный тест «Mali не сломан»**. Для Xclipse нужен тестер с Samsung
   (Galaxy S22+/S24/S25/A55 и т.п.).
2. **`radv-xclipse` не проверен** (п. 0): формат, ABI, совместимость с Android linker namespace Winlator.
3. Версия: тег `11.2.0` содержит приложение с `versionName "11.1"`. Нужно выбрать базу осознанно (тег или `main` подмодуля).
4. Лицензия LGPL-2.1: сохранить `LICENSE`, авторство, сменить название/иконку/`applicationId`, чтобы форк не выдавался за оригинал.
5. GitHub: форки подмодулей нужно создать вручную (я не могу создавать репозитории).
