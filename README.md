# Home Chat

Семейный чат на 6 человек: Android-приложение + небольшой сервер.

- **Сообщения, вход** — Firebase (Firestore + Auth, бесплатный план Spark).
- **Файлы и push** — свой VPS: Caddy (HTTPS) + FastAPI (`server/`), push через FCM.
- **Шеринг** из любых приложений через стандартное «Поделиться».

## Структура

```
android/   приложение (Kotlin, Jetpack Compose)
server/    docker compose: Caddy + API, правила Firestore, admin-скрипт
```

## Секреты (не в репозитории)

| Файл | Где | Зачем |
|---|---|---|
| `android/homechat.jks`, `android/keystore.properties` | локально | подпись APK — **без них обновления не встанут поверх** |
| `*firebase-adminsdk*.json` | локально и `/opt/home-chat/service-account.json` | admin-доступ к Firebase |
| `server/members.json`, `PIN-коды.txt` | локально и на сервере | имена и PIN-коды |

`android/app/google-services.json` в репозитории: это публичный конфиг клиента, доступ закрыт правилами Firestore.

## Сборка APK

Нужны JDK 17 и Android SDK (platform 35, build-tools 34). В `android/local.properties` указать `sdk.dir`.

```bash
cd android
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Для обновления поверх установленной версии увеличить `versionCode` в `android/app/build.gradle.kts`.

## Сервер

VPS `89.125.106.233`, папка `/opt/home-chat`, адрес `https://89-125-106-233.sslip.io`.

```bash
# выкатить изменения
tar -C server -cf - --exclude=members.json . | ssh root@89.125.106.233 "cd /opt/home-chat && tar -xf - && docker compose up -d --build"

# админка (внутри контейнера)
docker compose exec api python admin.py list                      # участники
docker compose exec api python admin.py pin Марк 654321           # сменить PIN
docker compose exec api python admin.py setup /app/members.json   # завести/обновить участников
docker compose exec api python admin.py rules /app/firestore.rules
```

Лимиты: 50 МБ на файл, 3 ГБ на все вложения (старые удаляются автоматически).
