package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** mesh 单个光斑 */
data class MeshSpot(
    val xFraction: Float,     // 光斑中心 X（占屏幕宽比例，0~1）
    val yFraction: Float,     // 光斑中心 Y（占屏幕高比例，0~1）
    val color: Color,         // 光斑颜色（含 alpha）
    val radiusFraction: Float,// 光斑半径（占 min(W,H) 比例）
)

/**
 * 玻璃令牌 —— 一套完整的玻璃态视觉参数
 */
data class GlassTokens(
    val meshBase: Color,           // mesh 底色
    val meshSpots: List<MeshSpot>, // 光斑列表（4-5 个）
    val tintConvex: Color,         // 凸起面半透叠加
    val tintConcave: Color,        // 凹陷面半透叠加
    val borderHi: Color,           // 渐变边框高亮端
    val borderLo: Color,           // 渐变边框暗端
    val innerHighlight: Color,     // 顶部内高光线
    val outerShadow: Color,        // 外阴影色
    val textPrimary: Color,        // 主文字色
    val textSecondary: Color,      // 次文字色
    val textTertiary: Color,       // 三级文字色
)

// ═══ 深色玻璃（炫彩暗底） ═══
val DarkGlassTokens = GlassTokens(
    meshBase = Color(0xFF0F1320),
    meshSpots = listOf(
        MeshSpot(0.20f, 0.25f, Color(0x8C4A6EAA), 0.55f), // 左上：蓝紫
        MeshSpot(0.84f, 0.16f, Color(0x73786096), 0.52f), // 右上：灰紫
        MeshSpot(0.30f, 0.90f, Color(0x663C7878), 0.55f), // 左下：青绿
        MeshSpot(0.90f, 0.82f, Color(0x6B5A548C), 0.55f), // 右下：紫蓝
    ),
    tintConvex = Color(0x1FFFFFFF),  // 12% 白透 → 凸起面微亮
    tintConcave = Color(0x24000000), // 14% 黑透 → 凹陷面微暗
    borderHi = Color(0x8CFFFFFF),    // 55% 白 → 上边框亮
    borderLo = Color(0x1FFFFFFF),    // 12% 白 → 下边框暗
    innerHighlight = Color(0x73FFFFFF), // 45% 白 → 顶部高光线
    outerShadow = Color(0x47000000),    // 28% 黑 → 外阴影
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xC7FFFFFF),
    textTertiary = Color(0x8CFFFFFF),
)

// ═══ 浅色玻璃（通透奶白） ═══
val LightGlassTokens = GlassTokens(
    meshBase = Color(0xFFF3EFEA),  // 暖调浅底（非纯白，有纸张温度）
    meshSpots = listOf(
        MeshSpot(0.18f, 0.22f, Color(0x735AA0F0), 0.62f), // 左上：蓝
        MeshSpot(0.86f, 0.15f, Color(0x669C8AE6), 0.60f), // 右上：淡紫
        MeshSpot(0.28f, 0.90f, Color(0x6B4FB8C9), 0.62f), // 左下：青
        MeshSpot(0.90f, 0.84f, Color(0x617E86E0), 0.60f), // 右下：蓝紫
        MeshSpot(0.52f, 0.46f, Color(0x4F7FC0E8), 0.82f), // 中央：宽柔青蓝（消除白板感）
    ),
    tintConvex = Color(0x2EFFFFFF),  // 18% 白透
    tintConcave = Color(0x12000000), // 7% 黑透（浅色下凹陷不能太暗）
    borderHi = Color(0xCCFFFFFF),    // 80% 白
    borderLo = Color(0x1F000000),    // 12% 黑
    innerHighlight = Color(0x80FFFFFF),
    outerShadow = Color(0x33737D99), // 蓝灰调外阴影（匹配 mesh 色系）
    textPrimary = Color(0xFF1A1B1E),
    textSecondary = Color(0xB31A1B1E),
    textTertiary = Color(0x801A1B1E),
)

val LocalGlassTokens = staticCompositionLocalOf { DarkGlassTokens }
