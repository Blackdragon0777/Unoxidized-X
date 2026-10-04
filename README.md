# Unoxidized X
Build: 1) python tools/keygen.py -> copy PUBLIC KEY into app/.../Core.kt (PUB). 2) Open folder in Android Studio, let it sync, Build > Build APK. 3) Install the APK.
Sell/issue keys: user opens app, sends you their Device ID, you run: python tools/keygen.py DEVICEID 30 TheirName  (each key only works on that device).
User guide is inside the app (menu > Step-by-step guide).
