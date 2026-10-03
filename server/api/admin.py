"""Admin commands, run inside the api container:

  docker compose exec api python admin.py setup /app/members.json   # create accounts + family chat
  docker compose exec api python admin.py pin <name> <new-pin>      # change someone's PIN
  docker compose exec api python admin.py rules /app/firestore.rules
  docker compose exec api python admin.py list

members.json: [{"name": "Папа", "pin": "123456"}, ...]  (PIN: at least 6 digits)
"""
import json
import os
import sys

import firebase_admin
from firebase_admin import auth, credentials, firestore

SA_PATH = os.environ.get("SA_PATH", "/app/service-account.json")
COLORS = ["#E57373", "#64B5F6", "#81C784", "#FFB74D", "#BA68C8", "#4DB6AC", "#F06292", "#A1887F"]

firebase_admin.initialize_app(credentials.Certificate(SA_PATH))
db = firestore.client()


def setup(members_file):
    members = json.load(open(members_file, encoding="utf-8"))
    uids = []
    for i, m in enumerate(members):
        email = f"member{i + 1}@home-chat.family"
        try:
            user = auth.get_user_by_email(email)
            auth.update_user(user.uid, password=m["pin"], display_name=m["name"])
        except auth.UserNotFoundError:
            user = auth.create_user(email=email, password=m["pin"], display_name=m["name"])
        auth.set_custom_user_claims(user.uid, {"family": True})
        db.collection("users").document(user.uid).set({
            "name": m["name"], "email": email, "color": COLORS[i % len(COLORS)], "order": i,
        }, merge=True)
        uids.append(user.uid)
        print(f"ok  {m['name']:<12} {email}  {user.uid}")

    db.collection("chats").document("family").set({
        "type": "group", "title": "Семья", "members": uids,
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }, merge=True)
    print("ok  family chat")


def set_pin(name, pin):
    for doc in db.collection("users").where("name", "==", name).stream():
        auth.update_user(doc.id, password=pin)
        print("PIN updated for", name)
        return
    print("no such member:", name)


def deploy_rules(rules_file):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_file(
        SA_PATH, scopes=["https://www.googleapis.com/auth/cloud-platform"])
    project = creds.project_id
    s = AuthorizedSession(creds)
    base = f"https://firebaserules.googleapis.com/v1/projects/{project}"
    r = s.post(f"{base}/rulesets", json={"source": {"files": [
        {"name": "firestore.rules", "content": open(rules_file, encoding="utf-8").read()}]}})
    r.raise_for_status()
    ruleset = r.json()["name"]
    release = {"name": f"projects/{project}/releases/cloud.firestore", "rulesetName": ruleset}
    r = s.patch(f"{base}/releases/cloud.firestore", json={"release": release})
    if r.status_code == 404:
        r = s.post(f"{base}/releases", json=release)
    r.raise_for_status()
    print("rules deployed:", ruleset)


def list_members():
    for doc in db.collection("users").order_by("order").stream():
        d = doc.to_dict()
        print(f"{d['name']:<12} {d['email']}  tokens={len(d.get('fcmTokens', []))}")


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    {"setup": setup, "pin": set_pin, "rules": deploy_rules, "list": list_members}[cmd](*args)
