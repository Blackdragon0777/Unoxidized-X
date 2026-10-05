#!/usr/bin/env bash
# Run with:  bash update.sh   (inside ~/UnoxidizedX)
set -e
cd "$(dirname "$0")"
rm -f app/src/main/java/com/unoxidized/x/Pub.kt
git add -A
git commit -m "NACHT X: 4 apps" || echo "Nothing new to commit"
git push
echo "DONE. Now open GitHub > Actions and wait for the green check."
