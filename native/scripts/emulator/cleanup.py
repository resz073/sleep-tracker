#!/usr/bin/env python3
"""
Deletes the emulator test's throwaway accounts (emulator-results/accounts.txt) if the test didn't get to
delete them itself, with Firebase's REST API: the sleep data (users/{uid}), then the account.
Needs FIREBASE_WEB_CONFIG or GOOGLE_SERVICES_JSON for the project and an API key.
"""
import json, os, re, sys, urllib.error, urllib.request

path = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("ACCOUNTS", "emulator-results/accounts.txt")
if not os.path.exists(path):
    sys.exit(0)

keys, project = [], None
web = os.environ.get("FIREBASE_WEB_CONFIG", "")
m = re.search(r"apiKey\W+([A-Za-z0-9_\-]{20,})", web)
if m:
    keys.append(m.group(1))
m = re.search(r"projectId\W+([a-z0-9\-]+)", web)
if m:
    project = m.group(1)
try:
    gs = json.loads(os.environ.get("GOOGLE_SERVICES_JSON", "") or "{}")
    project = project or gs.get("project_info", {}).get("project_id")
    for c in gs.get("client", []):
        for k in c.get("api_key", []):
            if k.get("current_key") and k["current_key"] not in keys:
                keys.append(k["current_key"])
except ValueError:
    pass


def call(url, body=None, token=None, method="POST"):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except ValueError:
            return e.code, {}


for line in open(path):
    parts = line.split()
    if len(parts) != 2:
        continue
    email, password = parts
    done = False
    for key in keys:
        code, r = call("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + key,
                       {"email": email, "password": password, "returnSecureToken": True})
        msg = (r.get("error") or {}).get("message", "")
        if code == 200:
            token, uid = r["idToken"], r["localId"]
            if project:
                call("https://firestore.googleapis.com/v1/projects/%s/databases/(default)/documents/users/%s" % (project, uid), token=token, method="DELETE")
            code2, _ = call("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=" + key, {"idToken": token})
            print("::notice title=Test account::%s was left by the test; deleted it now (%s)." % (email, "ok" if code2 == 200 else code2))
            done = True
            break
        if msg.startswith("EMAIL_NOT_FOUND") or msg.startswith("INVALID_LOGIN_CREDENTIALS") or msg.startswith("INVALID_PASSWORD"):
            print("Test account %s is already deleted." % email)
            done = True
            break
    if not done:
        print("::warning title=Test account::Couldn't check or delete the test account %s. If it exists, delete it in Firebase (Authentication)." % email)
