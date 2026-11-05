package com.shuiyu.octoberrain

import android.os.Build
import android.view.View
import android.widget.FrameLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.NotificationPanelAccess
import com.shuiyu.octoberrain.core.device.DeviceRuntime

/** Keep system-bar avoidance for controls separate from the wallpaper canvas. */
internal object WindowInsetsLayout {
    /** Reserve native status space only when decor has not already placed the root below it. */
    fun apply(
        view: View,
        windowInsets: android.view.WindowInsets?,
        contentFrame: FrameLayout,
        statusHolder: FrameLayout,
    ) {
        val profile = com.shuiyu.octoberrain.core.device.DeviceRuntime.profile
        val physicalInsets =
            com.shuiyu.octoberrain.core.device.DisplaySafeArea.insets(
                profile.physicalSideInsetPx,
                view.width,
                view.height,
            )
        val systemTop =
            if (AppServices.config.state.value.tray.overlayEnabled) {
                0
            } else {
                val position = IntArray(2)
                view.getLocationOnScreen(position)
                @Suppress("DEPRECATION")
                val insetTop =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        windowInsets
                            ?.getInsets(
                                android.view.WindowInsets.Type
                                    .statusBars(),
                            )?.top ?: 0
                    } else {
                        windowInsets?.systemWindowInsetTop ?: 0
                    }
                (insetTop - position[1]).coerceAtLeast(0)
            }
        val insets = physicalInsets.copy(top = physicalInsets.top + systemTop)
        val current =
            com.shuiyu.octoberrain.core.device.DisplaySafeArea.Insets(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                view.paddingBottom,
            )
        if (current != insets) view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
        applyNavigationInsets(windowInsets, physicalInsets, contentFrame, statusHolder)
    }

    /** Navigation avoidance belongs to controls; wallpaper retains its full available canvas. */
    private fun applyNavigationInsets(
        windowInsets: android.view.WindowInsets?,
        physical: com.shuiyu.octoberrain.core.device.DisplaySafeArea.Insets,
        contentFrame: FrameLayout,
        statusHolder: FrameLayout,
    ) {
        val nativeFallback = DeviceRuntime.profile.physicalSideInsetPx > 0 && !NotificationPanelAccess.available.value
        val edgeToEdge = AppServices.config.state.value.tray.overlayEnabled && !nativeFallback
        val margins =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && edgeToEdge) {
                val navigation =
                    windowInsets?.getInsets(
                        android.view.WindowInsets.Type
                            .navigationBars(),
                    )
                if (navigation == null) {
                    Triple(0, 0, 0)
                } else {
                    Triple(
                        (navigation.left - physical.left).coerceAtLeast(0),
                        (navigation.right - physical.right).coerceAtLeast(0),
                        (navigation.bottom - physical.bottom).coerceAtLeast(0),
                    )
                }
            } else {
                Triple(0, 0, 0)
            }
        val (left, right, bottom) = margins
        val content = contentFrame.layoutParams as FrameLayout.LayoutParams
        if (content.leftMargin != left || content.rightMargin != right || content.bottomMargin != bottom) {
            contentFrame.layoutParams =
                content.apply {
                    leftMargin = left
                    rightMargin = right
                    bottomMargin = bottom
                }
        }
        val bar = statusHolder.layoutParams as FrameLayout.LayoutParams
        if (bar.leftMargin != left || bar.rightMargin != right) {
            statusHolder.layoutParams =
                bar.apply {
                    leftMargin = left
                    rightMargin = right
                }
        }
    }
}
