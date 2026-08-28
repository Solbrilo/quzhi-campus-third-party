# Release Guide

This repository is the source and release repository for the Android and HarmonyOS clients.
Both clients use the same semantic version format, but each platform only advertises a release
when its own installable package has been uploaded.

## Gitee Releases

Both clients obtain update information and changelogs only from Gitee's public Release API:

```text
https://gitee.com/api/v5/repos/Solbrilo/quzhi-campus-third-party/releases?per_page=100&page=1
```

Only actual Release records with an exact `vX.Y.Z` tag are displayed in the changelog. The
release body is the changelog source; clients do not add local fallback entries. To offer an
update, a Release must also contain the matching platform asset and its HTTPS
`browser_download_url`:

- Android: `hotwater-android-vX.Y.Z.apk`
- HarmonyOS: `hotwater-harmony-vX.Y.Z.hap`

## v2.2.1 Release Build

1. Android uses `versionCode 25` and `versionName 2.2.1`.
2. HarmonyOS uses `versionCode 2002001` and `versionName 2.2.1`.
3. Run the Android unit tests and package build, then run the HarmonyOS tests and HAP build.
4. Create and push the `v2.2.1` tag with the source commit.
5. Upload the generated packages as `hotwater-android-v2.2.1.apk` and
   `hotwater-harmony-v2.2.1.hap` to the Gitee Release.

Until the packages are uploaded to a formal Gitee Release, clients must not show `v2.2.1` as an
available update or a changelog entry.

## Future Formal Release

1. Build and validate Android APK and HarmonyOS HAP with matching `X.Y.Z` versions.
2. Commit and push the source change first.
3. Create the formal Gitee Release `vX.Y.Z` named `water`, then upload exact assets:
    `hotwater-android-vX.Y.Z.apk` and `hotwater-harmony-vX.Y.Z.hap`.
4. Write the release notes in the Gitee Release body.
5. Verify the Gitee Release API response and both platform downloads anonymously.

Android packages must keep application ID `cn.qzxy.hotwater` and the existing signing
certificate. HarmonyOS HAPs must keep bundle name `cn.qzxy.hotwater.hm` and use the configured
HarmonyOS signing identity.
