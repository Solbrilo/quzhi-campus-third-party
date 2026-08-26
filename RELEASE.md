# Release Guide

This repository is the source and release repository for the Android and HarmonyOS clients.
Both clients use the same semantic version format, but each platform only advertises a release
when its own installable package has been uploaded.

## Update Manifest

The clients read the static update manifest instead of calling Gitee's anonymous Release REST API:

```text
https://gitee.com/Solbrilo/quzhi-campus-third-party/raw/main/update-manifest.json
```

The raw endpoint avoids the low shared rate limit of the Release API. Its schema has a platform
specific latest package and a shared release-note history:

```json
{
  "schemaVersion": 1,
  "android": {
    "latest": {
      "tagName": "vX.Y.Z",
      "title": "water",
      "body": "Release notes",
      "publishedAt": "2026-01-01T00:00:00+08:00",
      "url": "https://.../hotwater-android-vX.Y.Z.apk"
    }
  },
  "harmony": {
    "latest": {
      "tagName": "vX.Y.Z",
      "title": "water",
      "body": "Release notes",
      "publishedAt": "2026-01-01T00:00:00+08:00",
      "url": "https://.../hotwater-harmony-vX.Y.Z.hap"
    }
  },
  "releases": [
    {
      "tagName": "vX.Y.Z",
      "title": "water",
      "body": "Release notes",
      "publishedAt": "2026-01-01T00:00:00+08:00"
    }
  ]
}
```

`tagName` must use the exact `vX.Y.Z` format. Each `url` must be HTTPS and use the exact
platform filename. Do not point either platform's `latest` entry at a package that has not yet
been uploaded. Add every formal release to `releases` so both clients can render its changelog.

## v2.2.1 Release Build

1. Android uses `versionCode 25` and `versionName 2.2.1`.
2. HarmonyOS uses `versionCode 2002001` and `versionName 2.2.1`.
3. Run the Android unit tests and package build, then run the HarmonyOS tests and HAP build.
4. Create and push the `v2.2.1` tag with the source commit.
5. Upload the generated packages as `hotwater-android-v2.2.1.apk` and
   `hotwater-harmony-v2.2.1.hap` to the Gitee Release.

Until the packages are uploaded, the manifest intentionally continues to point to the latest
available packages: Android `v2.1.0` and HarmonyOS `v2.0.1`.

## Future Formal Release

1. Build and validate Android APK and HarmonyOS HAP with matching `X.Y.Z` versions.
2. Commit and push the source change first.
3. Create the formal Gitee Release `vX.Y.Z` named `water`, then upload exact assets:
   `hotwater-android-vX.Y.Z.apk` and `hotwater-harmony-vX.Y.Z.hap`.
4. Update and push `update-manifest.json` with the uploaded platform URLs and changelog entry.
5. Verify the raw manifest and both platform downloads anonymously.

Android packages must keep application ID `cn.qzxy.hotwater` and the existing signing
certificate. HarmonyOS HAPs must keep bundle name `cn.qzxy.hotwater.hm` and use the configured
HarmonyOS signing identity.
