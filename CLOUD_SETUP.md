# NACHT X server setup (Firebase) - one time, about 10 minutes

The apps talk to your Firebase project `nacht-x` over HTTPS. Google's servers enforce who can do what (see `firestore.rules`), so a modified APK cannot give itself Premium.

## 1. Turn on sign-in
Firebase console > **Build > Authentication > Get started > Sign-in method > Email/Password > Enable > Save**.

## 2. Create the database
**Build > Firestore Database > Create database** > pick a region near you (e.g. asia-southeast1) > **Production mode**.

## 3. Publish the rules
Firestore > **Rules** tab > delete everything > paste the whole content of `firestore.rules` from this repo > **Publish**.

## 4. Create your owner account
1. **Authentication > Users > Add user**: your email + a strong password. Copy its **User UID**.
2. **Firestore > Start collection** named `admins` > Document ID = that **User UID** > add one field `enabled` (boolean) = `true` > Save.
3. Open **NACHT X Owner > Accounts**, sign in with that email and password.

## 5. (Recommended) Limit the API key
Google Cloud console > APIs & Services > Credentials > the key starting `AIzaSyDt...` > API restrictions > allow only **Identity Toolkit API**, **Token Service API** and **Cloud Firestore API**.

## How it works
- User signs up in NACHT X (email + password) and a `users/<uid>` record is created as pending Free. They cannot change status, role or key.
- User pays GCash and submits the reference: a `payments/<reference>` record is created as pending. A repeated reference is refused by the server.
- You check the payment in GCash, then Owner > Accounts > **APPROVE**. In one atomic write the app signs a key for that user's device, marks the payment approved and records the reference as used.
- The user's app checks the server every ~10 seconds and switches Premium on when the owner-signed key arrives. The key's signature is verified on the phone.
- **SUSPEND** blocks that account at app start. Premium also still ends by itself when the key expires.

## Honest limits
- The server decides who can approve. Premium on the phone is still unlocked by a signed key checked on the phone, so a person who modifies the APK could skip the lock inside their own copy. They still cannot get a valid key for another device or change anything on the server.
- If the server is unreachable, the apps fall back to the older GitHub `control.json` route (Owner > GITHUB REQUESTS) and Telegram.
