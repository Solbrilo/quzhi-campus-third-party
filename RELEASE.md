# Release Guide

This repository is the single source and release repository for both clients. Only Android is
actively published. HarmonyOS stays at `1.3.0` and ignores Android-only Releases.

## Android

- Update API: `https://gitee.com/api/v5/repos/Solbrilo/quzhi-campus-third-party/releases/latest`
- Release tag: `vX.Y.Z`
- Required asset: `hotwater-android-vX.Y.Z.apk`
- Source milestone tag: `android-vX.Y.Z`

The Android APK must keep application ID `cn.qzxy.hotwater`, the existing signing certificate,
and APK Signature Scheme v2. Both tags point to the release commit. The formal Gitee Release uses
`vX.Y.Z`; `android-vX.Y.Z` makes the source milestone explicit without splitting repositories.

## HarmonyOS

HarmonyOS has no active binary release. Its updater reads the same latest Release but accepts an
update only when all of these conditions are true:

- The Release tag is exactly `vX.Y.Z`.
- The version is newer than the installed HarmonyOS version.
- The Release contains `hotwater-harmony-vX.Y.Z.hap`.

An Android Release contains only the APK, so the HarmonyOS client ignores it without showing the
automatic update dialog. Do not upload a HarmonyOS HAP unless HarmonyOS publishing is explicitly
resumed in a future change.

## Android v1.4.1 Checklist

1. Set Android to `versionCode 14` and `versionName 1.4.1`.
2. Keep HarmonyOS at `versionCode 1003000` and `versionName 1.3.0`.
3. Run unit tests, connected UI tests, release lint, and the release build.
4. Verify package name, v2 signature certificate, byte count, and SHA-256.
5. Commit the source and push `main` plus source milestone tag `android-v1.4.1`.
6. Create Release `v1.4.1` named `water` in this repository.
7. Upload `hotwater-android-v1.4.1.apk` and verify it through an anonymous download.
8. Confirm `/releases/latest` returns `v1.4.1` with the exact Android APK asset.
9. Confirm the same payload has no HarmonyOS HAP, so HarmonyOS ignores it.
