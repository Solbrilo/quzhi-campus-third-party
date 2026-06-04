package cn.qzxy.hotwater

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import android.view.View
import android.widget.FrameLayout

object FeedbackHelper {
    private const val SNACKBAR_BOTTOM_MARGIN_DP = 80 // 距离底部切换按钮的间距

    fun showSuccess(context: Context, parent: View, message: String) {
        showFeedback(context, parent, message, FeedbackType.SUCCESS)
    }

    fun showError(context: Context, parent: View, message: String) {
        showFeedback(context, parent, message, FeedbackType.ERROR)
    }

    fun showWarning(context: Context, parent: View, message: String) {
        showFeedback(context, parent, message, FeedbackType.WARNING)
    }

    private fun showFeedback(context: Context, parent: View, message: String, type: FeedbackType) {
        val snackbar = Snackbar.make(parent, "", Snackbar.LENGTH_SHORT)
        val snackbarView = snackbar.view
        val density = context.resources.displayMetrics.density

        // 清除 Snackbar 默认背景和内边距
        snackbarView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        snackbarView.setPadding(0, 0, 0, 0)

        // 设置自定义布局
        val inflater = LayoutInflater.from(context)
        val customView = inflater.inflate(R.layout.layout_snackbar_feedback, null)

        // 在自定义布局上设置背景
        when (type) {
            FeedbackType.SUCCESS -> {
                customView.setBackgroundResource(R.drawable.snackbar_success_background)
            }
            FeedbackType.ERROR -> {
                customView.setBackgroundResource(R.drawable.snackbar_error_background)
            }
            FeedbackType.WARNING -> {
                customView.setBackgroundResource(R.drawable.snackbar_warning_background)
            }
        }

        // 设置图标和文本
        val iconText = customView.findViewById<TextView>(R.id.feedbackIcon)
        val messageText = customView.findViewById<TextView>(R.id.feedbackMessage)

        when (type) {
            FeedbackType.SUCCESS -> iconText.text = "✓"
            FeedbackType.ERROR -> iconText.text = "✗"
            FeedbackType.WARNING -> iconText.text = "!"
        }

        messageText.text = message

        // 设置 Snackbar 内容
        val snackbarLayout = snackbarView as FrameLayout
        snackbarLayout.removeAllViews()

        // 添加自定义布局，设置合适的宽度和边距
        val horizontalMargin = (20 * density).toInt()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxWidth = screenWidth - (horizontalMargin * 2)

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setMargins(horizontalMargin, 0, horizontalMargin, 0)
        }
        snackbarLayout.addView(customView, layoutParams)

        // 限制最大宽度
        customView.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        // 设置 Snackbar 位置：贴在底部切换按钮上方
        val marginBottomPx = (SNACKBAR_BOTTOM_MARGIN_DP * density).toInt()
        val params = snackbarView.layoutParams as? ViewGroup.MarginLayoutParams
        params?.setMargins(
            params.leftMargin,
            params.topMargin,
            params.rightMargin,
            marginBottomPx
        )
        snackbarView.layoutParams = params

        snackbar.show()
    }

    private enum class FeedbackType {
        SUCCESS,
        ERROR,
        WARNING
    }
}
