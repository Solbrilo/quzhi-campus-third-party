# Release Guide

This repository remains the single source repository for both clients. Only Android has an
active binary release channel. HarmonyOS stays at `1.3.0` and must not treat Android APK
releases as HarmonyOS updates.

## Android

- Update API: `https://gitee.com/api/v5/repos/lazyoigg/quzhi-campus-android-releases/releases/latest`
- Release tag: `vX.Y.Z`
- Required asset: `hotwater-android-vX.Y.Z.apk`
- Source-repository tag: `android-vX.Y.Z`

The Android APK must keep application ID `cn.qzxy.hotwater`, the existing signing certificate,
and APK Signature Scheme v2. Push the source tag to this repository, but create the formal
Release and upload the APK only in `lazyoigg/quzhi-campus-android-releases`.

Do not publish Android releases in this source repository. Android `v1.3.1` and `v1.4.0` are
retained here only as historical prereleases. The latest stable source-repository Release must
remain `v1.3.0` so the installed HarmonyOS `1.3.0` client stays current.

## HarmonyOS

HarmonyOS has no active release channel and no HAP is published by this release process. Its
updater continues to read this source repository and accepts an update only when all of these
conditions are true:

- The Release tag is exactly `vX.Y.Z`.
- The version is newer than the installed HarmonyOS version.
- The Release contains `hotwater-harmony-vX.Y.Z.hap`.

An Android-only Release is ignored because it has no matching HAP. Do not upload or publish a
HarmonyOS HAP unless HarmonyOS releases are explicitly resumed in a future change.

## Android v1.4.1 Checklist

1. Set Android to `versionCode 14` and `versionName 1.4.1`.
2. Keep HarmonyOS at `versionCode 1003000` and `versionName 1.3.0`.
3. Run unit tests, connected UI tests, release lint, and the release build.
4. Verify package name, v2 signature certificate, byte count, and SHA-256.
5. Commit the source, push `main`, and push source tag `android-v1.4.1`.
6. Create Android Release `v1.4.1` named `water` in the Android release repository.
7. Upload `hotwater-android-v1.4.1.apk` and verify it through an anonymous download.
8. Confirm the Android release repository returns `v1.4.1` from `/releases/latest`.
9. Confirm this source repository returns stable `v1.3.0` from `/releases/latest`.
