# NACHT X security notes

## Where the secrets are now
Nothing secret is stored in this repository any more. The signing keystore, its passwords, the Owner signing key and the Owner passcode hash live in
**GitHub > Settings > Secrets and variables > Actions** and are injected only while the build runs. GitHub masks them in logs.

| Secret | Purpose |
|---|---|
| UX_KEYSTORE_B64, UX_STORE_PASS, UX_KEY_PASS, UX_KEY_ALIAS | Signs both APKs with the same key |
| OWNER_PRIV | Owner key that signs Premium keys (compiled only into the Owner app) |
| OWNER_HASH | Hash of your Owner passcode |
| OWNER_ZIP_PASS | Password that encrypts the Owner APK download |

The Owner APK contains OWNER_PRIV, so on a public repo it is uploaded as a password-protected zip. The workflow refuses to upload an unprotected Owner APK
from a public repo. The main app is never given the Owner key.

## Already pushed to a public repo? Rotate.
Git remembers old commits. If an earlier commit with the old keystore or Owner key was ever public, treat those keys as exposed:
anyone could have copied them, so anyone could sign Premium keys or sign an APK as you. Moving secrets out of the files does not erase history.

1. New signing keystore (users must uninstall the old app once):
   `keytool -genkeypair -keystore ux.keystore -alias ux -keyalg RSA -keysize 2048 -validity 10000 -storepass NEWPASS -keypass NEWPASS -dname "CN=NachtX"`
   then `base64 -w0 ux.keystore` into secret UX_KEYSTORE_B64 and update UX_STORE_PASS / UX_KEY_PASS.
2. New Owner signing key: `pip install cryptography && python tools/keygen.py` (creates private.pem, prints a PUBLIC KEY).
   Paste the public key into `PUB` in `shared/Lic.kt`. Secret OWNER_PRIV = `openssl pkcs8 -topk8 -nocrypt -in private.pem -outform DER | base64 -w0`.
   Old Premium keys stop working; re-issue them from the Owner app.
3. New Owner passcode hash:
   `python3 -c "import hashlib;print(hashlib.sha256((input().strip().upper()+'|unx.owner.v1').encode()).hexdigest())"` and put the output in OWNER_HASH.
4. Delete `private.pem` and any file with secrets from your phone/repo after saving the values as GitHub secrets.
5. To drop the old history completely, create a fresh repository and push this folder to it (the guide's `git init` flow), then delete the old repository.

## What the server (Firebase) protects, and what it does not
See CLOUD_SETUP.md. The server stops other users from approving payments or editing roles. It cannot stop someone who modifies their own copy of the APK from unlocking features
inside that copy; Premium on the phone is a signature check. Keep that in mind when deciding what is worth locking.
