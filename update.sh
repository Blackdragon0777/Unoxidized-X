#!/usr/bin/env bash
# Clean sync of the NACHT X project into your git repo. Run with:  bash update.sh
set -e
cd "$(dirname "$0")"
rm -rf generator info app/src/main/res/layout
rm -f app/src/main/java/com/unoxidized/x/Pub.kt
# remove stray Kotlin files that are not part of this project (left behind by older versions or other tools)
KEEP_APP=" MainActivity.kt Core.kt Sys.kt Fps.kt Features.kt Tuner.kt Boot.kt MainSync.kt Library.kt Branding.kt FloatingService.kt Premium.kt CloudUi.kt "
KEEP_OWN=" OwnerActivity.kt OwnerControl.kt OwnerBranding.kt OwnerRequests.kt OwnerCloud.kt "
for f in $(find app/src owner/src shared -name "*.kt" 2>/dev/null); do
  b=$(basename "$f")
  case "$f" in
    app/src/*) case "$KEEP_APP" in *" $b "*) ;; *) echo "removing stray file: $f"; rm -f "$f";; esac;;
    owner/src/*) case "$KEEP_OWN" in *" $b "*) ;; *) echo "removing stray file: $f"; rm -f "$f";; esac;;
  esac
done
BR=$(git rev-parse --abbrev-ref HEAD)
git fetch origin "$BR" 2>/dev/null || true
git reset --soft "origin/$BR" 2>/dev/null || true
git add -A
git commit -m "NACHT X 7.0 clean sync: Nacht X + Owner" || echo "Nothing new to commit"
git push origin "$BR"
echo "DONE. Now open GitHub > Actions and wait for the green check."
