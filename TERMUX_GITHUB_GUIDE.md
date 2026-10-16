# Build NACHT X + NACHT X Owner from your phone (Termux + GitHub Actions)

GitHub builds the APKs. Termux only uploads the source. This project is v15.1.

## 0. One-time: add the GitHub secrets (needed for signing and the Owner app)
Open `secrets_to_add.txt` (the private file I gave you, NOT part of the repo zip).
GitHub repo > **Settings > Secrets and variables > Actions > New repository secret**. Add all 7 names exactly:
`UX_KEYSTORE_B64`, `UX_STORE_PASS`, `UX_KEY_PASS`, `UX_KEY_ALIAS`, `OWNER_PRIV`, `OWNER_HASH`, `OWNER_ZIP_PASS`.
Never paste those values into a file in the repo. (Without them the build still goes green, but the Owner app cannot unlock and the two apps get different signatures.)

## 1. One-time Termux setup
```
pkg update -y && pkg install -y git unzip
git config --global user.name  "Blackdragon0777"
git config --global user.email "you@example.com"
termux-setup-storage
```
Create a GitHub token: Settings > Developer settings > Personal access tokens (classic) with `repo` and `workflow` scopes.

## 2. Upload
```
cd ~ && rm -rf nachtx && mkdir nachtx
unzip -o ~/storage/downloads/Unoxidized-X-v15.1.zip -d nachtx
cd nachtx/Unoxidized-X-main
git init -b main
git add -A
git commit -m "NACHT X v15.1: Tuner.kt fix, secrets moved to GitHub, server accounts"
git remote add origin https://github.com/Blackdragon0777/Unoxidized-X.git
git push -u origin main --force
```
Password prompt = your **token**. `--force` replaces the old history on GitHub with this clean copy (the old commits that contained keys stop being the branch tip; read SECURITY.md about rotating).
Later updates: `cd ~/nachtx/Unoxidized-X-main && git add -A && git commit -m update && git push`.

## 3. Get the APKs
GitHub > **Actions** > latest "Build NACHT X (2 apps)" run > wait for the green check (3-6 min) > **Artifacts**:
- **Nacht-X-APK** > `Nacht X.apk`
- **Nacht-X-Owner-APK** > `Nacht X Owner (encrypted).zip` (open it with your OWNER_ZIP_PASS in ZArchiver or `7z x`), inside is `Nacht X Owner.apk`.

## 4. If it is red
Open the failed step and find the **first** line starting with `e:` (Kotlin) or `error:`. The file name, line and column are in that line. Send me that line.

## 5. Server (accounts, payments, instant approval)
Follow `CLOUD_SETUP.md` once (Firebase console, about 10 minutes).

## Updating an existing upload with a new zip (keeps your git history)
```
cd ~/nachtx
unzip -o ~/storage/downloads/Unoxidized-X-v15.2.zip
cd Unoxidized-X-main
git add -A
git commit -m "NACHT X v15.2"
git push
```
Then in GitHub open the new Actions run. Download the artifacts again (old downloads can be corrupt). You can also grab the main APK directly, without any zip, from the repo's **Releases** page.
