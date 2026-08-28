package cn.qzxy.hotwater

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseNotesParserTest {
    @Test
    fun parsesPublishedGiteeReleaseLogsAndAndroidAsset() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name": "v2.1.0",
                "name": "water",
                "body": "- 新增更多页面\\n- 优化导航动画",
                "created_at": "2026-08-18T10:20:30+08:00",
                "assets": [
                  {
                    "name": "hotwater-android-v2.1.0.apk",
                    "browser_download_url": "https://gitee.com/example/releases/download/v2.1.0/hotwater-android-v2.1.0.apk"
                  }
                ]
              },
              {
                "tag_name": "v2.0.0",
                "name": "首个正式版本",
                "body": "首个正式版本",
                "published_at": "2026-07-01T00:00:00Z",
                "assets": []
              }
            ]
            """.trimIndent(),
        )

        val notes = parseGiteeReleaseNotesPayload(releases)
        val latest = requireNotNull(parseAndroidLatestGiteeRelease(releases))

        assertEquals(listOf("2.1.0", "2.0.0"), notes.map { it.versionName })
        assertEquals("water", notes.first().title)
        assertTrue(notes.first().body.contains("更多页面"))
        assertEquals("2026-08-18T10:20:30+08:00", notes.first().publishedAt)
        assertEquals("2026-07-01T00:00:00Z", notes.last().publishedAt)
        assertEquals("2.1.0", latest.versionName)
        assertEquals(
            "https://gitee.com/example/releases/download/v2.1.0/hotwater-android-v2.1.0.apk",
            latest.apkUrl,
        )
    }

    @Test
    fun ignoresPreReleaseDraftAndMalformedGiteeTags() {
        val releases = JSONArray(
            """
            [
              { "tag_name": "v2.2.0", "prerelease": true, "body": "预发布" },
              { "tag_name": "v2.1.0", "draft": true, "body": "草稿" },
              { "tag_name": "android-v2.1.0", "body": "历史标签" },
              { "tag_name": "2.0.0", "body": "缺少 v 前缀" },
              { "tag_name": "v2.0.0", "body": "可见" }
            ]
            """.trimIndent(),
        )

        val notes = parseGiteeReleaseNotesPayload(releases)

        assertEquals(1, notes.size)
        assertEquals("2.0.0", notes.single().versionName)
    }

    @Test
    fun updateUsesOnlyExactAndroidAssetNameAndBrowserDownloadUrl() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name": "v2.3.0",
                "body": "鸿蒙包不应触发 Android 更新",
                "assets": [
                  {
                    "name": "hotwater-harmony-v2.3.0.hap",
                    "browser_download_url": "https://gitee.com/example/releases/download/v2.3.0/hotwater-harmony-v2.3.0.hap"
                  }
                ]
              },
              {
                "tag_name": "v2.2.0",
                "body": "名称相同但下载地址不匹配时不应使用",
                "assets": [
                  {
                    "name": "hotwater-android-v2.2.0.apk",
                    "browser_download_url": "https://gitee.com/example/releases/download/v2.2.0/not-the-android-package.apk"
                  }
                ]
              },
              {
                "tag_name": "v2.1.0",
                "body": "有效 Android 包",
                "assets": [
                  {
                    "name": "hotwater-android-v2.1.0.apk",
                    "browser_download_url": "https://gitee.com/example/releases/download/v2.1.0/hotwater-android-v2.1.0.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        val latest = requireNotNull(parseAndroidLatestGiteeRelease(releases))

        assertEquals("2.1.0", latest.versionName)
        assertEquals("有效 Android 包", latest.releaseNotes)
    }

    @Test
    fun selectsTheGreatestValidAndroidReleaseRegardlessOfApiOrder() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name": "v2.1.0",
                "assets": [
                  {
                    "name": "hotwater-android-v2.1.0.apk",
                    "browser_download_url": "https://gitee.com/example/hotwater-android-v2.1.0.apk"
                  }
                ]
              },
              {
                "tag_name": "v2.3.0",
                "assets": [
                  {
                    "name": "hotwater-android-v2.3.0.apk",
                    "browser_download_url": "https://gitee.com/example/hotwater-android-v2.3.0.apk"
                  }
                ]
              },
              {
                "tag_name": "v2.2.0",
                "assets": [
                  {
                    "name": "hotwater-android-v2.2.0.apk",
                    "browser_download_url": "https://gitee.com/example/hotwater-android-v2.2.0.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals("2.3.0", parseAndroidLatestGiteeRelease(releases)?.versionName)
    }

    @Test
    fun releaseLogNeverInventsVersionsMissingFromGitee() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name": "v2.1.0",
                "name": "远端版本",
                "body": "远端版本更新说明"
              }
            ]
            """.trimIndent(),
        )

        val notes = parseGiteeReleaseNotesPayload(releases)

        assertEquals(listOf("2.1.0"), notes.map { it.versionName })
        assertNull(parseAndroidLatestGiteeRelease(releases))
    }
}
