# Чистый день — Android APK

## Требования

- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Gradle 8.5 (обёртка включён)
- Android SDK 34

## Сборка

```bash
cd android
./gradlew assembleRelease
```

APK будет в `android/app/build/outputs/apk/release/app-release.apk`

Для подписи потребуется keystore:

```bash
./gradlew assembleRelease -Pandroid.injected.signing.store.file=chestny.keystore \
                          -Pandroid.injected.signing.store.password=... \
                          -Pandroid.injected.signing.key.alias=chestny \
                          -Pandroid.injected.signing.key.password=...
```

## Структура проекта

```
android/
├── build.gradle.kts              # корневой Gradle
├── settings.gradle.kts           # настройки проекта
├── app/
│   ├── build.gradle.kts          # модуль приложения
│   └── src/main/
│       ├── AndroidManifest.xml   # манифест
│       ├── assets/
│       │   ├── index.html        # === ВЕСЬ PWA ===
│       │   ├── sw.js             # Service Worker
│       │   ├── manifest.json     # Web Manifest
│       │   └── icons/            # иконки
│       ├── java/com/chestny/den/
│       │   ├── MainActivity.kt         # WebView + PIN
│       │   ├── WebAppInterface.kt      # JS → Android bridge
│       │   ├── SecureStorage.kt        # EncryptedSharedPreferences
│       │   ├── NotificationHelper.kt   # уведомления
│       │   ├── NotificationReceiver.kt # приёмник будильника
│       │   ├── BootReceiver.kt         # перезапуск после перезагрузки
│       │   ├── FCMService.kt           # Firebase Cloud Messaging
│       │   └── WidgetProvider.kt       # виджет главного экрана
│       └── res/                  # ресурсы (темы, строки, лейауты, иконки)
│
ai-service/                       # AI Recovery Assistant (FastAPI)
├── pyproject.toml
└── main.py
```

## Android → JavaScript bridge

Из JS доступен глобальный объект `AndroidBridge`:

| Метод | Описание |
|---|---|
| `AndroidBridge.getAppVersion()` | Версия приложения |
| `AndroidBridge.getPlatform()` | Всегда `"android"` |
| `AndroidBridge.vibrate(ms)` | Вибрация |
| `AndroidBridge.showNotification(title, body)` | Показать уведомление |
| `AndroidBridge.scheduleNotification(title, body, hour, min, id)` | Запланировать напоминание |
| `AndroidBridge.isBiometricAvailable()` | JSON с `{available, code}` |
| `AndroidBridge.authenticateBiometric(callbackId)` | Запросить отпечаток/FaceID |
| `AndroidBridge.getSecureSettings(key)` | Чтение из EncryptedSharedPrefs |
| `AndroidBridge.setSecureSettings(key, val)` | Запись в EncryptedSharedPrefs |
| `AndroidBridge.removeSecureSettings(key)` | Удаление ключа |
| `AndroidBridge.shareText(text)` | Системный Share Sheet |
| `AndroidBridge.toast(msg)` | Toast-уведомление |
| `AndroidBridge.logEvent(event, data)` | Логирование в Logcat |

## Добавление Firebase

1. Создать проект в [Firebase Console](https://console.firebase.google.com/)
2. Добавить Android-приложение с package name `com.chestny.den`
3. Скачать `google-services.json` в `android/app/`
4. Готово — FCM будет работать

## Обновление PWA

Просто скопировать новые версии файлов в `assets/`:

```bash
cp index.html sw.js manifest.json android/app/src/main/assets/
cp -r icons android/app/src/main/assets/
```

Пересобрать APK.
