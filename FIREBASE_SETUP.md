# NACHT X — Backend setup (Firebase)

This gives NACHT X a real server-side authority for accounts, approval, and roles,
instead of the on-device-only checks it has today. Free, no credit card needed for
this scale of usage.

## 1. Create the Firebase project (you do this, ~5 min)
1. Go to https://console.firebase.google.com → **Add project** → name it (e.g. `nacht-x`).
   Google Analytics can be skipped.
2. In the project, click **Build > Authentication > Get started**.
   Enable **Email/Password**. (Anonymous sign-in can be added later for guests.)
3. Click **Build > Firestore Database > Create database** → **Start in production mode**
   → pick the region closest to your users.
4. Click the **gear icon > Project settings > General**, scroll to **Your apps**,
   click the Android icon, and register:
   - Package name: `com.unoxidized.x`
   - Download the generated **google-services.json**.
5. Repeat "Add app" for the Owner app with package name `com.unoxidized.owner`
   and download its own config (or reuse the same file — one file can cover
   multiple apps in the same project if you register both package names).
6. Send me the `google-services.json` file (or just its contents) and tell me
   you've enabled Email/Password auth. I'll then add the Firebase libraries,
   wire real sign-up/login, and build the approval system against it.

## 2. Data model I will use (Firestore)
```
users/{uid}
  email: string
  deviceId: string              # Settings.Secure.ANDROID_ID, for device-bound checks
  status: "pending" | "approved" | "rejected" | "suspended"
  tier: "free" | "premium"
  role: "user" | "owner"
  createdAt: timestamp
  updatedAt: timestamp

entitlements/{tier}             # e.g. entitlements/free, entitlements/premium
  features: { tuner: bool, profiles: bool, sessions: bool, importExport: bool, autoBoost: bool }

auditLog/{autoId}
  uid: string
  action: string                # "approved" | "rejected" | "suspended" | "role_changed" ...
  by: string                    # owner uid
  at: timestamp
```

## 3. Security rules (draft, tightened once Owner UIDs are known)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read: if request.auth.uid == uid || isOwner();
      allow create: if request.auth.uid == uid;
      allow update: if isOwner();            // only the owner can approve/change status or role
    }
    match /entitlements/{tier} { allow read: if request.auth != null; allow write: if isOwner(); }
    match /auditLog/{id} { allow read, create: if isOwner(); }
    function isOwner() {
      return request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == "owner";
    }
  }
}
```

## 4. What changes in the two apps once this is wired
- **NACHT X:** real sign-up/login screen, "Pending approval" screen shown until an
  owner approves the account, live feature access driven by `entitlements/{tier}`
  and the user's own `status`/`tier` (a Firestore listener, not a one-time check),
  session kept via Firebase Auth (survives reinstall if the user logs back in).
- **NACHT X Owner:** a real User Management screen reading `users/*` live — search,
  approve/reject/suspend/reactivate/revoke, change tier/role, and an audit log tab
  reading `auditLog/*`. The existing Owner Key screen stays as the local unlock for
  opening the Owner app itself; Firestore enforces that only a `role: owner` account
  can write privileged fields, so a modified APK still can't grant itself access.

## 5. What I still can't do
- I can't create the Firebase project or click through its console for you — that
  needs your Google account. I also can't host anything myself; Firebase is Google's
  hosted service, which is why this approach doesn't require you to run a server.
- Until you send the config file, the apps keep using today's on-device key system
  so the build doesn't break on a missing credential.
