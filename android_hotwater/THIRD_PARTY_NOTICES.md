# LinYu protocol reference

SMS verification, nearby water-device discovery and server bill API integration were informed by
[yehu-imei/linyu](https://github.com/yehu-imei/linyu), snapshot
`11eb9ec85c57bc2061ca49ee689d2efa12be5e2c`. The original license notice is retained below.

MIT License

Copyright (c) 2026 LinYu Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


# AndroidLiquidGlass / Backdrop

The Android liquid material renderer uses io.github.kyant0:backdrop:1.0.0
from Kyant's AndroidLiquidGlass project, copyright 2025 Kyant,
licensed under the Apache License, Version 2.0. The unmodified dependency
provides background sampling, blur, refraction and highlights.
Source: https://github.com/Kyant0/AndroidLiquidGlass
License: app/src/main/assets/licenses/AndroidLiquidGlass-LICENSE.txt

# Muguang recharge reference

Recharge package/payment-mode discovery, the four-field request signature, Alipay order
handling and payment-result semantics were informed by
[Ranxian/muguang](https://github.com/Ranxian/muguang), snapshot
`abd2f95f62d2724111fa3f7999aa793363dd2ac1`.
The implementation is adapted to this app's OkHttp API, account-generation guards,
ViewModel state and Compose half-height sheet. No upstream account data is used.
The notice is also distributed in `app/src/main/assets/licenses/Muguang-LICENSE.txt`.

MIT License

Copyright (c) 2026 Ranxian (沐光 / Muguang)
Copyright (c) 2026 LinYu Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

# Alipay Android SDK

App payments use the official `com.alipay.sdk:alipaysdk-android:15.8.42` binary SDK.
It is a payment service dependency, not listed as an open-source reference project.
Its manifest and consumer ProGuard rules are merged from the published AAR.

# Android runtime and development dependencies

The in-app support page also identifies these direct libraries and the principal bundled foundations:

| Project | Use | License / upstream |
| --- | --- | --- |
| AndroidX / Jetpack | Compose UI, Material 3, Activity, Lifecycle, WorkManager, CameraX, Security Crypto, AppCompat, test libraries | Apache-2.0 · https://github.com/androidx/androidx |
| Material Components / Material Design Icons | Android themes, components and icon assets | Apache-2.0 · https://github.com/material-components/material-components-android · https://github.com/google/material-design-icons |
| Kotlin / kotlinx.coroutines | Language runtime and coroutines | Apache-2.0 · https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines |
| OkHttp / Okio | Network transport and I/O | Apache-2.0 · https://github.com/square/okhttp · https://github.com/square/okio |
| Haze | Background blur compatibility | Apache-2.0 · https://github.com/chrisbanes/haze |
| Tink | Cryptography used by AndroidX Security | Apache-2.0 · https://github.com/tink-crypto/tink |
| Gradle / Android Gradle Plugin | Build tooling | Apache-2.0 · https://github.com/gradle/gradle · https://android.googlesource.com/platform/tools/base/ |
| JUnit 4 | Local unit tests | EPL-1.0 · https://github.com/junit-team/junit4 |

The unmodified dependencies retain their upstream copyright and licensing. The complete Apache-2.0 text is distributed at `app/src/main/assets/licenses/AndroidLiquidGlass-LICENSE.txt`; project-specific sources and notices are available from the links above.

Google ML Kit barcode scanning and Google Play services components are official binary SDK dependencies governed by Google's SDK terms (https://developers.google.com/ml-kit/terms). They and the Alipay SDK are identified separately in-app; they are not represented as fully open-source projects.

The 4.0.0 account management endpoints (phone change, password update, SMS reset and project information) were additionally informed by the same muguang snapshot above. Passwords and verification codes remain memory-only; the application does not adopt upstream request logging.
