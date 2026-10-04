#!/bin/sh
# run inside ~/UnoxidizedX after unzipping the update
[ -f private.pem ] || { echo "private.pem not found - run this in ~/UnoxidizedX"; exit 1; }
PUB=$(python tools/keygen.py | tail -n1)
printf 'package com.unoxidized.x\nobject Pub { const val KEY = "%s" }\n' "$PUB" > app/src/main/java/com/unoxidized/x/Pub.kt
git add -A && git commit -m "ui v2" && git push
