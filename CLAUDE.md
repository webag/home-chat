# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Family chat for 6 people: Android app (Kotlin, Jetpack Compose) + a small server on a VPS. Users are non-technical family members; UI text is Russian, addressing the user as «ты».

## Working rules

- Plan first, then code: propose a plan and wait for approval before implementing non-trivial changes.
- Never commit (or push/tag) without being asked.

## Commands

No tests or linters exist. Verify changes by building.

Android (run from `android/`; the JDK, SDK and Gradle live in the gitignored `.tools/`, nothing is installed system-wide):

```powershell
$env:JAVA_HOME = "C:\OSPanel\home\home-chat\.tools\jdk-17.0.20.1+1"
.\gradlew.bat assembleRelease    # -> app/build/outputs/apk/release/app-release.apk
.\gradlew.bat installDebug       # onto a USB-connected phone (adb is in .tools/sdk/platform-tools)
```

Release (users get the update through the in-app updater):

```bash
git tag v1.3 && git push origin v1.3   # .github/workflows/release.yml builds, signs, publishes
```

Server deploy and admin (VPS `root@89.125.106.233`, dir `/opt/home-chat`, URL `https://89-125-106-233.sslip.io`):

```bash
tar -C server -cf - --exclude=members.json . | ssh root@89.125.106.233 "cd /opt/home-chat && tar -xf - && docker compose up -d --build"
docker compose exec api python admin.py list | pin <name> <pin> | setup /app/members.json | rules /app/firestore.rules
```

## Architecture

**Firestore is the database; the VPS only does what the free Firebase plan can't** (file storage, sending FCM pushes). The app talks to Firestore directly for all messaging.

- **Auth**: each member is a Firebase email/password account (`memberN@home-chat.family`, password = PIN), created by `server/api/admin.py`, which also sets the custom claim `family: true`. Both `firestore.rules` and the server's `require_member()` check that claim; the server accepts the Firebase ID token as a Bearer token.
- **Firestore layout**: `users/{uid}` (name, color, order, fcmTokens); `chats/{id}` with `members`, `lastMessage`, `readBy{uid: timestamp}`; `chats/{id}/messages/{id}`. The group chat id is `family`; direct chats are `dm_<uidA>_<uidB>` (sorted) and are created lazily by the batch that writes their first message (hence `getAfter` in the rules). Read receipts are just `readBy` timestamps compared with `createdAt`.
- **Sending** (`data/Repo.send`): one batch writes the message and the chat summary, then `push/NotifyWorker` (WorkManager, so it survives the app closing) calls the server's `POST /notify`, which reads the message from Firestore and sends FCM to the other members. `push/PushService` shows the notification unless that chat is open (`App.openChatId`).
- **Attachments** (`data/Outbox`): images are compressed and videos get a thumbnail on the phone, then uploaded to `POST /upload`; the message stores a `FileRef` with a server path. Files are served by Caddy straight from disk at `/f/<path>`, bypassing the API. The server enforces 50 MB per file and 3 GB in total, deleting the oldest uploads past the total.
- **Server** (`server/`): docker compose with `caddy` (the only public container, auto-HTTPS for `$DOMAIN` from `.env` on the VPS) and `api` (FastAPI, `main.py`). `data/files`, `service-account.json`, `members.json` and `firestore.rules` are bind-mounted from the host, not baked into the image.
- **Avatars**: the app crops to a 512px square WebP (`ui/ProfileScreen`) and sends it to `POST /avatar`; the server keeps it in `data/files/avatars/<uid>/` (excluded from the quota cleanup) and writes `users/{uid}.avatar` itself via the Admin SDK, so the client never writes that field.
- **Custom contact names**: stored in `users/{uid}/private/contacts` (`names: {uid: name}`), readable only by the owner. `MainViewModel.members` applies them, so every screen shows them; `Member.realName` keeps the original. `data/NicknameCache` mirrors them in SharedPreferences for push notifications, which are built from the server payload (`senderId`) without Firestore.
- **Manual "unread" mark**: `users/{uid}/private/state` (`unread: {chatId: true}`), never via `readBy` (that would undo the sender's read ticks). `MainViewModel.entries` merges it into `unread`; opening the chat clears it.
- **Images**: compressed on the phone to WebP 80%, long side ≤ 1920px (`Media.compressImage`); only that copy is uploaded. GIFs and undecodable images are sent as files.
- **Updates** (`data/Updater`, `ui/UpdateDialog`): on start the app fetches `releases/latest/download/version.json` from the public GitHub repo and offers `HomeChat.apk` if its `versionCode` is newer.
- **UI**: single activity with Compose Navigation (`ui/MainActivity`), one shared `MainViewModel`. Every color comes from `ui/Theme.kt`: a fixed brand palette (blue→violet `BrandGradient`, no dynamic color) plus `LocalChatColors` for chat-only colors (bubbles, background). Don't hardcode colors in screens.

## Constraints

- `versionCode` = git commit count (`build.gradle.kts`), so it grows by itself; CI needs `fetch-depth: 0`. Never set it by hand.
- Never change `applicationId` or the signing key (`android/homechat.jks`, gitignored, also stored in GitHub Secrets): installed apps would refuse the update. Debug builds use a different key and can't install over the release build.
- Gitignored secrets: `homechat.jks`, `keystore.properties`, `*firebase-adminsdk*.json` / `service-account.json`, `server/members.json`, `PIN-коды.txt`. `android/app/google-services.json` is committed on purpose: it's the public client config.
- The VPS has 2 GB RAM and an 11 GB disk; containers have `mem_limit` set.
