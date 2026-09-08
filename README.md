# Reeelz — Milestone 2

Нативный Android-редактор одного видео. Kotlin, Jetpack Compose, Material 3,
Media3 ExoPlayer / Transformer, Coroutines и ViewModel. Min SDK 29, target/compile SDK 35.

## Запуск

1. Откройте корень в Android Studio с поддержкой AGP 8.9.2.
2. Установите JDK 17 или 21, Android SDK Platform 35 и Build Tools 35.0.0.
3. Укажите SDK через `ANDROID_HOME` или локальный `local.properties` (`sdk.dir=...`).
4. Выполните Gradle Sync и запустите конфигурацию `app` на Android 10+.

Windows: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

macOS/Linux: `sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Gradle wrapper 8.11.1 включён в проект. Инструменты в `.tools/` — только локальное
окружение разработки, не часть проекта. `local.properties`, build-каталоги,
кэши, ключи и секреты исключены через `.gitignore`.

Если Java на Windows выдаёт `Unable to establish loopback connection`, задайте
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<существующий короткий локальный каталог>`
и повторите сборку. Это локальная настройка среды, не Gradle-параметр проекта.

## Использование

«Новый ролик» → системный Photo Picker → одно видео → preview.
На устройствах без Photo Picker контракт AndroidX использует системный
документный picker. Разрешения на всю медиатеку не нужны.

Кнопка воспроизведения управляет preview; выбранный trim зациклен.
Два маркера задают начало и конец (минимум 100 мс, либо вся длина для более короткого видео).
Preview применяет trim после отпускания маркера. Zoom 1–4× и положение X/Y
задают область исходника, заполняющую canvas 9:16 без пустых краёв.
Положение обозначает центр выбранной области исходника; Y направлен вниз.

### Текст (Milestone 2)

В блоке «Текст на видео» введите надпись до 200 символов. Один текстовый блок
показывается на протяжении всего обрезанного ролика. Доступны размер 32–120,
белый/жёлтый/розовый цвет, положение X/Y и удаление. Удерживайте палец на canvas
и тяните для перемещения; ползунки позволяют точнее настроить положение.
Preview закреплён над прокручиваемыми настройками и уменьшается при открытии клавиатуры.
Используется системный полужирный шрифт с тенью. Длинные строки переносятся,
слишком высокий блок автоматически уменьшается до размеров canvas.
Текст остаётся внутри кадра и не зависит от crop/zoom исходника.

Экспорт создаёт MP4 H.264 / AAC 1080×1920 с исходным звуком, если он есть.
Результат появляется в галерее в `Movies/Reeelz`. Если аппаратный кодек
не поддерживает нужный формат, показывается ошибка, понижения разрешения нет.
Во время экспорта оставьте приложение открытым; есть отмена и прогресс.

## Архитектура

- `MainActivity`: Compose Home/Editor, Photo Picker, lifecycle и привязка PlayerView.
- `editor/EditorUiState`: immutable state, источник, trim и crop как non-destructive параметры.
- `editor/EditorViewModel`: загрузка метаданных на IO, действия редактора, StateFlow,
  запуск экспортной coroutine и обработка ошибок. Переживает поворот Activity.
- `editor/EditEffects`: общий MediaItem clipping и последовательность Crop → Presentation
  → CaptionEffect для preview и экспорта; учитывается rotation metadata при расчёте размеров.
- `editor/TextParameters`: non-destructive текст, размер, ARGB-цвет и положение.
- `editor/CaptionEffect`: Android StaticLayout рисует надпись в прозрачный bitmap,
  Media3 BitmapOverlay накладывает его на готовый canvas 1080×1920.
  Один рендерер обслуживает playback и export; Compose не рисует отдельную копию текста.
- `ui/editor/TextControls`: элементы управления текстом, все изменения идут через ViewModel.
- `playback/EditorPlayback`: владеет ExoPlayer, playback state и обработкой ошибок;
  pause при уходе приложения в фон, release при очистке ViewModel.
- `export/ExportEngine`: Transformer на Main, временный файл в cache,
  копирование в MediaStore на IO. `IS_PENDING` скрывает неполную запись.
  Ошибки и отмена удаляют незавершённую запись и временный файл.

Composable не вызывает Transformer. Исходный URI используется только для чтения;
trim/crop никогда не перезаписывают исходник. Экспорт получает снимок state.
Редактирование блокируется на время экспорта.

## Границы второго этапа

Нет выбора шрифтов, нескольких надписей, анимации текста, фильтров, музыки, нескольких клипов, переходов, авторизации, облака,
аналитики или AI. Нет восстановления проекта после завершения процесса и
фонового сервиса экспорта. HDR и необычные кодеки зависят от устройства/Media3;
ошибки выводятся пользователю. Следующий этап автоматически не начинается.

## Проверка на устройстве

Проверено 2026-09-09: `assembleDebug`, `testDebugUnitTest`, `lintDebug` —
BUILD SUCCESSFUL (JDK 17.0.10, Windows). Unit-тесты: crop, ограничения текста,
положение надписи без выхода за canvas.
Lint: 0 ошибок, 10 предупреждений (обновления зависимостей, временная иконка
не задана, рекомендация dataExtractionRules). Пользователь подтвердил работу Milestone 1
в эмуляторе, экспортированные файлы проверены в MediaStore. Preview и экспорт
с текстом Milestone 2 требуют отдельной проверки в эмуляторе/на устройстве.

Unit-тест проверяет геометрию crop для landscape, portrait, square и узкого видео,
крайние положения и масштабы, сохранение 9:16 и отсутствие выхода за исходник.
Дополнительно вручную проверьте:

1. Отмену picker, landscape/portrait с rotation metadata, короткое видео, видео без аудио.
2. Play/Pause, trim обоих концов, zoom и крайние X/Y; совпадение preview с экспортом.
3. Размер 1080×1920, длительность, звук и наличие MP4 в галерее.
4. Отмену экспорта, повторный экспорт, поворот экрана, уход в фон, недоступный URI.
5. Ошибку при нехватке места/неподдерживаемом кодеке и сохранность исходного файла.
6. Кириллицу, несколько строк, 200 символов, смену цветов/размера, крайние положения,
   перемещение удержанием, удаление текста и его совпадение в preview/экспорте.

Физическое устройство необходимо для проверки аппаратного кодирования.

API: [Media3 transformations](https://developer.android.com/media/media3/transformer/transformations),
[Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker).
