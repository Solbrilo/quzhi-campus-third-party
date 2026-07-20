# Release Guide

This repository is the single source and release repository for the Android and HarmonyOS clients.
Both clients use the same formal Gitee Release and version tag.

## Shared Release

- Update API: `https://gitee.com/api/v5/repos/Solbrilo/quzhi-campus-third-party/releases/latest`
- Release tag: `vX.Y.Z`
- Release display name: `water`
- Android asset: `hotwater-android-vX.Y.Z.apk`
- HarmonyOS asset: `hotwater-harmony-vX.Y.Z.hap`

Both clients require an exact `vX.Y.Z` tag and exact asset filename. A missing platform asset makes
that client ignore the Release without presenting an invalid update.

## Android

The Android APK must keep application ID `cn.qzxy.hotwater`, the existing signing certificate,
and APK Signature Scheme v2. The optional `android-vX.Y.Z` tag is retained only as a historical
source milestone and must point to the same commit as the formal Release tag.

## HarmonyOS

The HarmonyOS HAP must keep bundle name `cn.qzxy.hotwater.hm`, use the configured HarmonyOS signing
identity, and be uploaded with the exact shared Release version. Its updater validates both the
Release tag and the HarmonyOS asset name before showing an update.

## v2.0.0 Checklist

1. Set Android to `versionCode 20` and `versionName 2.0.0`.
2. Set HarmonyOS to `versionCode 2000000` and `versionName 2.0.0`.
3. Run Android unit tests and release lint, then build the signed release APK.
4. Run HarmonyOS unit tests and build the signed release HAP.
5. Verify package identifiers, signing certificates, byte counts, and SHA-256 hashes.
6. Commit all source changes and push `main`.
7. Create annotated tags `v2.0.0` and `android-v2.0.0` at the release commit, then push them.
8. Create Gitee Release `v2.0.0` named `water` and upload both platform assets.
9. Verify the anonymous Release API and both asset downloads.
