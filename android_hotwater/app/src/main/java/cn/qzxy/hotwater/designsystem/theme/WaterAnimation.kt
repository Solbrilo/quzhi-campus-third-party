package cn.qzxy.hotwater.designsystem.theme

/** Animations are independent of the installed theme presets. Keep existing stored values. */
enum class WaterAnimation(val storageValue: String, val label: String) {
    FLOW("sky", "流转"),
    RIPPLE("tide", "涟漪"),
    HALO("ember", "日晕"),
    TICK("mono", "刻度"),
    GEAR("brass", "齿轮");

    companion object {
        fun fromStorageValue(value: String?) = entries.firstOrNull { it.storageValue == value } ?: FLOW
    }
}
