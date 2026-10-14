#!/usr/bin/env bash
# Builds Deckboard.app from the Kotlin/Native release executable, optionally signs it, and optionally wraps it in a DMG.
# Usage: package-macos.sh <app|dmg|notarize> <executable> <resources-dir>... (called by the Gradle tasks in build.gradle.kts)
#
# Environment:
#   DECKBOARD_MAC_SIGN_IDENTITY   codesign identity, e.g. "Developer ID Application: Name (TEAMID)". Default "-" = ad-hoc (runs locally only)
#   DECKBOARD_NOTARY_PROFILE      notarytool keychain profile (xcrun notarytool store-credentials <profile> ...)
set -euo pipefail

mode="$1"; exe="$2"; version="$3"; shift 3
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/build/macos-app"
app="$out/Deckboard.app"
dmg="$out/Deckboard-$version.dmg"
identity="${DECKBOARD_MAC_SIGN_IDENTITY:--}"

build_app() {
  rm -rf "$app"
  mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources/compose-resources"
  cp "$exe" "$app/Contents/MacOS/Deckboard"
  cp "$here/../desktopApp/icons/icon.icns" "$app/Contents/Resources/icon.icns"
  for dir in "$@"; do
    [ -d "$dir" ] && cp -R "$dir/." "$app/Contents/Resources/compose-resources/"
  done
  cat > "$app/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>Deckboard</string>
    <key>CFBundleDisplayName</key><string>Deckboard</string>
    <key>CFBundleIdentifier</key><string>de.kloostech.deckboard</string>
    <key>CFBundleExecutable</key><string>Deckboard</string>
    <key>CFBundleIconFile</key><string>icon</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>$version</string>
    <key>CFBundleVersion</key><string>$version</string>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleLocalizations</key><array><string>en</string><string>de</string></array>
    <key>LSMinimumSystemVersion</key><string>12.0</string>
    <key>LSApplicationCategoryType</key><string>public.app-category.productivity</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSPrincipalClass</key><string>NSApplication</string>
    <key>ITSAppUsesNonExemptEncryption</key><false/>
    <key>NSHumanReadableCopyright</key><string>Copyright 2026 KloosTech. Apache License 2.0.</string>
</dict>
</plist>
PLIST
  plutil -lint "$app/Contents/Info.plist" >/dev/null
  codesign --force --deep --options runtime --timestamp=none --sign "$identity" "$app"
  codesign --verify --deep --strict "$app"
  echo "Built $app (signed with: $identity)"
}

build_dmg() {
  [ -d "$app" ] || build_app "$@"
  local stage="$out/dmg-stage"
  rm -rf "$stage" "$dmg"; mkdir -p "$stage"
  cp -R "$app" "$stage/"
  ln -s /Applications "$stage/Applications"
  hdiutil create -volname "Deckboard" -srcfolder "$stage" -ov -format UDZO "$dmg" >/dev/null
  rm -rf "$stage"
  if [ "$identity" != "-" ]; then codesign --force --sign "$identity" "$dmg"; fi
  echo "Built $dmg"
}

notarize() {
  [ "$identity" != "-" ] || { echo "Notarization needs a Developer ID identity (DECKBOARD_MAC_SIGN_IDENTITY)"; exit 1; }
  [ -n "${DECKBOARD_NOTARY_PROFILE:-}" ] || { echo "Set DECKBOARD_NOTARY_PROFILE (xcrun notarytool store-credentials)"; exit 1; }
  # Hardened runtime and a secure timestamp are required for notarization.
  build_app "$@"
  codesign --force --deep --options runtime --timestamp --sign "$identity" "$app"
  build_dmg "$@"
  codesign --force --timestamp --sign "$identity" "$dmg"
  xcrun notarytool submit "$dmg" --keychain-profile "$DECKBOARD_NOTARY_PROFILE" --wait
  xcrun stapler staple "$dmg"
  echo "Notarized and stapled $dmg"
}

case "$mode" in
  app) build_app "$@" ;;
  dmg) build_app "$@"; build_dmg "$@" ;;
  notarize) notarize "$@" ;;
  *) echo "unknown mode $mode"; exit 2 ;;
esac
