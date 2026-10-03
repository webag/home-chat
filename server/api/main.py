"""Home Chat API: file storage, push notifications, member list.

Messages themselves live in Firestore; this service only handles what the
free Firebase plan can't: storing attachments and sending FCM pushes.
"""
import os
import re
import secrets
import shutil
import threading
import time
from pathlib import Path
from urllib.parse import quote

import firebase_admin
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from firebase_admin import auth, credentials, firestore, messaging
from pydantic import BaseModel

FILES_DIR = Path(os.environ.get("FILES_DIR", "/data/files"))
MAX_FILE_BYTES = int(os.environ.get("MAX_FILE_MB", "50")) * 1024 * 1024
MAX_TOTAL_BYTES = int(os.environ.get("MAX_TOTAL_MB", "3072")) * 1024 * 1024
MIN_DISK_FREE_BYTES = 500 * 1024 * 1024

FILES_DIR.mkdir(parents=True, exist_ok=True)
firebase_admin.initialize_app(credentials.Certificate(os.environ.get("SA_PATH", "/app/service-account.json")))
db = firestore.client()
app = FastAPI(docs_url=None, redoc_url=None)
quota_lock = threading.Lock()


def require_member(authorization: str | None) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "no token")
    try:
        token = auth.verify_id_token(authorization[7:])
    except Exception:
        raise HTTPException(401, "bad token")
    if not token.get("family"):
        raise HTTPException(403, "not a family member")
    return token


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/members")
def members():
    """Public list for the login screen: who can sign in and with which email."""
    out = []
    for doc in db.collection("users").stream():
        d = doc.to_dict()
        out.append({"uid": doc.id, "name": d.get("name"), "email": d.get("email"),
                    "color": d.get("color"), "order": d.get("order", 0)})
    return sorted(out, key=lambda m: m["order"])


# ---------- files ----------

def safe_name(name: str) -> str:
    name = re.sub(r"[^\w.\- ()]+", "_", name).strip(" .") or "file"
    if len(name) > 100:
        stem, dot, ext = name.rpartition(".")
        name = (stem[: 90] + dot + ext[:9]) if dot else name[:100]
    return name


def enforce_quota():
    """Delete oldest uploads once total size exceeds the limit (down to 90%)."""
    with quota_lock:
        entries = []
        total = 0
        for d in FILES_DIR.iterdir():
            if not d.is_dir():
                continue
            size = sum(f.stat().st_size for f in d.iterdir() if f.is_file())
            entries.append((d.stat().st_mtime, size, d))
            total += size
        if total <= MAX_TOTAL_BYTES:
            return
        entries.sort()
        target = MAX_TOTAL_BYTES * 0.9
        for _, size, d in entries:
            if total <= target:
                break
            shutil.rmtree(d, ignore_errors=True)
            total -= size


@app.post("/upload")
async def upload(request: Request, name: str, authorization: str | None = Header(None)):
    await run_in_threadpool(require_member, authorization)
    if shutil.disk_usage(FILES_DIR).free < MIN_DISK_FREE_BYTES:
        raise HTTPException(507, "server disk is full")

    file_id = secrets.token_urlsafe(16)
    fname = safe_name(name)
    folder = FILES_DIR / file_id
    folder.mkdir()
    path = folder / fname
    size = 0
    try:
        with open(path, "wb") as f:
            async for chunk in request.stream():
                size += len(chunk)
                if size > MAX_FILE_BYTES:
                    raise HTTPException(413, "file too large")
                f.write(chunk)
    except BaseException:
        shutil.rmtree(folder, ignore_errors=True)
        raise
    await run_in_threadpool(enforce_quota)
    return {"path": f"{file_id}/{quote(fname)}", "name": fname, "size": size}


# ---------- push ----------

class NotifyIn(BaseModel):
    chatId: str
    messageId: str


def preview(msg: dict) -> str:
    kind = msg.get("type", "text")
    text = (msg.get("text") or "").strip()
    if kind == "image":
        return "📷 " + (text or "Фото")
    if kind == "video":
        return "🎬 " + (text or "Видео")
    if kind == "file":
        return "📎 " + (msg.get("file", {}).get("name") or "Файл")
    return text[:300]


@app.post("/notify")
def notify(body: NotifyIn, authorization: str | None = Header(None)):
    token = require_member(authorization)
    uid = token["uid"]
    chat_ref = db.collection("chats").document(body.chatId)
    chat = chat_ref.get()
    if not chat.exists or uid not in chat.get("members"):
        raise HTTPException(403, "not in chat")
    msg = chat_ref.collection("messages").document(body.messageId).get()
    if not msg.exists or msg.get("senderId") != uid:
        raise HTTPException(404, "message not found")
    chat_d, msg_d = chat.to_dict(), msg.to_dict()

    user_refs = [db.collection("users").document(m) for m in chat_d["members"]]
    users = {u.id: u.to_dict() for u in db.get_all(user_refs) if u.exists}
    sender_name = users.get(uid, {}).get("name", "")
    is_group = chat_d.get("type") == "group"

    targets = []  # (token, owner uid)
    for m, u in users.items():
        if m != uid:
            targets += [(t, m) for t in u.get("fcmTokens", [])]
    if not targets:
        return {"sent": 0}

    data = {
        "chatId": body.chatId,
        "messageId": body.messageId,
        "title": chat_d.get("title", "Семья") if is_group else sender_name,
        "sender": sender_name,
        "senderId": uid,
        "body": preview(msg_d),
        "group": "1" if is_group else "0",
    }
    resp = messaging.send_each_for_multicast(messaging.MulticastMessage(
        tokens=[t for t, _ in targets],
        data=data,
        android=messaging.AndroidConfig(priority="high", ttl=7 * 24 * 3600),
    ))

    # Drop tokens of uninstalled apps / reset devices.
    for (tok, owner), r in zip(targets, resp.responses):
        if not r.success and isinstance(r.exception, (messaging.UnregisteredError, messaging.SenderIdMismatchError)):
            db.collection("users").document(owner).update({"fcmTokens": firestore.ArrayRemove([tok])})
    return {"sent": resp.success_count}
