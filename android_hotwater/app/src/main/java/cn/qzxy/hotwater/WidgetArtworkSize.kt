package cn.qzxy.hotwater

/** Launchers expose dp bounds, not cell counts. Allow for the wider cells on tablet grids. */
internal fun usesDashboardWidget(minWidth: Float, minHeight: Float, smallestScreenWidthDp: Int): Boolean {
    val twoCellLimit = maxOf(220f, if (smallestScreenWidthDp >= 600) smallestScreenWidthDp * .42f else 220f)
    return minWidth < twoCellLimit && minHeight < twoCellLimit &&
        !usesTallWidget(minWidth, minHeight) && !usesWideTwoRowWidget(minWidth, minHeight)
}

internal fun usesTallWidget(width: Float, height: Float): Boolean = height >= 150f && height >= width * 1.25f

/** Narrow launcher grids can fit 3×2 below the usual 220dp width threshold. */
internal fun usesWideTwoRowWidget(width: Float, height: Float): Boolean = height >= 120f && width >= height * 1.3f
