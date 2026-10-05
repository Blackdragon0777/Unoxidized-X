#!/bin/sh
# run inside ~/UnoxidizedX after unzipping the update
rm -f app/src/main/java/com/unoxidized/x/Pub.kt
git add -A && git commit -m "v3: two apps" && git push
