# Release Guide

Both mobile clients use the latest Gitee Release as their update source.

1. Set Android and HarmonyOS to the same semantic version `X.Y.Z`.
2. Create the Gitee tag and Release `vX.Y.Z`.
3. Upload this required asset to that Release:
   - `hotwater-android-vX.Y.Z.apk`
   - An optional `hotwater-harmony-vX.Y.Z.hap` may be attached for manual distribution.
4. Put user-facing changes in the Release description.

The Android APK must keep the existing application ID and signing key. Android downloads
the APK and opens the system installer after user confirmation. HarmonyOS compares the
same Release tag after confirming that the Android APK is present, then asks the user to
download the project manually; it does not download or install the HAP.
