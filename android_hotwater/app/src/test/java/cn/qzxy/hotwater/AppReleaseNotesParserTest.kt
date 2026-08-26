package cn.qzxy.hotwater

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseNotesParserTest {
    @Test
    fun parsesEveryPublishedVersionAndRetainsDetails() {
        val manifest = JSONObject(
            """
            {
              "schemaVersion": 1,
              "android": {
                "latest": {
                  "tagName": "v2.1.0",
                  "title": "光感导航与更多页",
                  "body": "- 新增更多页面\\n- 优化导航动画",
                  "publishedAt": "2026-08-18T10:20:30+08:00",
                  "url": "https://gitee.com/Solbrilo/quzhi-campus-third-party/releases/download/v2.1.0/hotwater-android-v2.1.0.apk"
                }
              },
              "releases": [
                {
                  "tagName": "v2.1.0",
                  "title": "光感导航与更多页",
                  "body": "- 新增更多页面\\n- 优化导航动画",
                  "publishedAt": "2026-08-18T10:20:30+08:00"
                },
                {
                  "tagName": "v2.0.0",
                  "title": "首个正式版本",
                  "body": "首个正式版本",
                  "publishedAt": "2026-07-01T00:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        )
        val releases = parseReleaseNotesPayload(manifest)
        val latest = requireNotNull(parseAndroidLatestUpdate(manifest))

        assertEquals(listOf("2.1.0", "2.0.0"), releases.map { it.versionName })
        assertEquals("光感导航与更多页", releases.first().title)
        assertTrue(releases.first().body.contains("更多页面"))
        assertEquals("2026-08-18T10:20:30+08:00", releases.first().publishedAt)
        assertEquals("2026-07-01T00:00:00Z", releases.last().publishedAt)
        assertEquals("2.1.0", latest.versionName)
        assertTrue(latest.apkUrl.endsWith("hotwater-android-v2.1.0.apk"))
    }

    @Test
    fun ignoresDraftAndMalformedReleaseTags() {
        val releases = parseReleaseNotesPayload(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "releases": [
                    { "tagName": "v2.1.0", "body": "可见" },
                    { "tagName": "v2.2.0", "draft": true, "body": "草稿" },
                    { "tagName": "android-v2.1.0", "body": "历史标签" },
                    { "tagName": "2.0.0", "body": "缺少 v 前缀" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(1, releases.size)
        assertEquals("2.1.0", releases.single().versionName)
    }

    @Test
    fun rejectsUnsupportedSchemasAndUrlsForAnotherPackage() {
        val unsupported = JSONObject("""{ "schemaVersion": 2 }""")
        assertTrue(!isSupportedUpdateManifest(unsupported))
        assertNull(parseAndroidLatestUpdate(unsupported))

        val wrongPackage = JSONObject(
            """
            {
              "schemaVersion": 1,
              "android": {
                "latest": {
                  "tagName": "v2.1.0",
                  "url": "https://example.test/hotwater-harmony-v2.1.0.hap"
                }
              }
            }
            """.trimIndent(),
        )
        assertNull(parseAndroidLatestUpdate(wrongPackage))
    }

    @Test
    fun includesPlatformLatestWhenSharedHistoryHasNotCaughtUp() {
        val releases = parseReleaseNotesPayload(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "android": {
                    "latest": {
                      "tagName": "v2.2.0",
                      "title": "当前版本",
                      "body": "修复登录流程",
                      "publishedAt": "2026-08-24T00:00:00+08:00"
                    }
                  },
                  "releases": [
                    { "tagName": "v2.1.0", "body": "历史版本" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("2.1.0", "2.2.0"), releases.map { it.versionName })
        assertEquals("当前版本", releases.last().title)
    }

    @Test
    fun currentVersionFallbackBodyDescribesTheCurrentRelease() {
        assertTrue(LOCAL_CURRENT_RELEASE_BODY.contains("非配置页清空所有信息"))
        assertTrue(LOCAL_CURRENT_RELEASE_BODY.contains("在途配置写入"))
    }
}
