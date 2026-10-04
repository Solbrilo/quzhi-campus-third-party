package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.*
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** UI regression cases; run on an emulator/device, never send real SMS or control requests. */
class AccountDeviceBillScreensInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun loginDefaultsToSmsAndCanSwitchBothWaysWithoutLosingPhone() {
        var method: QzxyLoginMethod? = null
        var submittedCredential = ""
        rule.setContent {
            GlassTheme(themeMode = ThemeMode.LIGHT) {
                LoginPage(initialTelephone = "13800138000", loginState = QzxyLoginState(),
                    onLogin = { m, _, c -> method = m; submittedCredential = c }, onBack = {})
            }
        }
        rule.onNodeWithText("验证码登录").assertIsSelected()
        rule.onNodeWithContentDescription("短信验证码").performTextInput("123456")
        rule.onNodeWithText("登录", substring = false).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(QzxyLoginMethod.SMS, method); assertEquals("123456", submittedCredential) }
        rule.onNodeWithText("密码登录").performScrollTo().performClick()
        rule.onNodeWithContentDescription("手机号").assertTextContains("13800138000")
        rule.onNodeWithContentDescription("密码").assertExists()
        rule.onNodeWithText("验证码登录").performClick()
        rule.onNodeWithContentDescription("短信验证码").assertTextEquals("")
    }

    @Test fun deviceSelectorShowsServiceNameCurrentDeviceAndTriggersExplicitSelection() {
        val device = NearbyDevice("KLCXKJ-Water", "AA:BB:CC:DD:EE:FF", -55,
            NearbyDeviceBinding("AABBCCDDEEFF", "101 宿舍热水器", "1号楼 101"))
        var selected: NearbyDevice? = null
        var scanRequested = false
        rule.setContent {
            GlassTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.width(320.dp)) {
                    NearbyDeviceListCard(listOf(device), NearbyScanState(), NearbyPairState(), "AABBCCDDEEFF",
                        true, {}, {}, { selected = it }, onScanQrCode = { scanRequested = true })
                }
            }
        }
        rule.onNodeWithText("当前设备").assertExists()
        rule.onNodeWithText("101 宿舍热水器").performClick()
        rule.runOnIdle { assertEquals(device, selected) }
        rule.onNodeWithContentDescription("扫码添加").performClick()
        rule.runOnIdle { assertEquals(true, scanRequested) }
    }

    @Test fun billsRenderServerAmountsAndErrorsWithoutLocalDurationOrFakeZero() {
        val month = YearMonth.now()
        val bill = ServerBill("server-bill", "1", "server-order", "$month-01 10:00:00", 80,
            "101 热水器", null, month)
        val state = ServerBillsState(BillQuery(month.year, month.monthValue), listOf(bill),
            loaded = false, message = "部分账单加载失败", failedMonths = listOf(month))
        rule.setContent {
            GlassTheme(themeMode = ThemeMode.LIGHT) {
                RecordsPage(state, "test-account", onLoad = { _, _ -> })
            }
        }
        rule.onNodeWithText("部分账单加载失败").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("101 热水器").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("¥0.08").assertCountEquals(2)
        rule.onNodeWithText("用水时长").assertDoesNotExist()
    }
}
