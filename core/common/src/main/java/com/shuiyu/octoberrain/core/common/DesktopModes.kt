package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.Serializable

/** 沉浸保留普通宫格/列表配置，切换不改应用顺序。 */
@Serializable
enum class DesktopMode { NORMAL, IMMERSIVE }

@Serializable
enum class DesktopOrientation { PORTRAIT, LANDSCAPE, REVERSE_LANDSCAPE }

@Serializable
enum class WidgetSwitchGesture { RIGHT, UP, OFF }
