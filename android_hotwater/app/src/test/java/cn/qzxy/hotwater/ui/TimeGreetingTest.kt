package cn.qzxy.hotwater.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeGreetingTest {
    @Test fun deviceLocalHourChoosesGreetingAcrossAllBoundaries() {
        mapOf(0 to "晚上好！", 4 to "晚上好！", 5 to "早上好！", 8 to "早上好！",
            9 to "上午好！", 11 to "上午好！", 12 to "中午好！", 13 to "中午好！",
            14 to "下午好！", 17 to "下午好！", 18 to "晚上好！", 23 to "晚上好！")
            .forEach { (hour, greeting) -> assertEquals("Hour $hour", greeting, greetingForHour(hour)) }
    }
}
