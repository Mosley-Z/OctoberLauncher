package com.shuiyu.octoberrain.core.common

/** Pixel bounds in the coordinate space named by their owner. */
data class ImmersiveLandscapeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float
        get() = right - left
    val height: Float
        get() = bottom - top
    val centerX: Float
        get() = (left + right) / 2f
    val centerY: Float
        get() = (top + bottom) / 2f
}

/** One immutable window snapshot shared by Home and the reading widget. */
data class ImmersiveLandscapeGeometry(
    val window: ImmersiveLandscapeBounds,
    val homeX: Float,
    val homeY: Float,
    val originalBook: ImmersiveLandscapeBounds,
    val book: ImmersiveLandscapeBounds,
    val weight: Float,
) {
    /** Publish one decision for every consumer; rejection restores the original layout. */
    fun admitted(
        dateFits: Boolean,
        statisticsFit: Boolean,
    ): ImmersiveLandscapeGeometry? = takeIf { dateFits && statisticsFit }

    fun homeX(fraction: Float): Float = window.left + window.width * fraction - homeX

    fun homeY(fraction: Float): Float = window.top + window.height * fraction - homeY

    fun bookX(fraction: Float): Float = homeX(fraction) - book.left

    fun bookY(fraction: Float): Float = homeY(fraction) - book.top

    fun blend(
        old: Float,
        target: Float,
    ): Float = old + (target - old) * weight

    fun bookBounds(target: ImmersiveLandscapeBounds): ImmersiveLandscapeBounds =
        ImmersiveLandscapeBounds(
            bookX(target.left),
            bookY(target.top),
            bookX(target.right),
            bookY(target.bottom),
        )

    val titleLeft: Float
        get() = bookX(.289f)
    val titleTop: Float
        get() = bookY(.097f)
    val authorBaseline: Float
        get() = bookY(.214f)
    val railIconYs: List<Float>
        get() = listOf(.152f, .343f, .519f, .707f, .869f).map(::homeY)
    val railLabelBaseline: Float
        get() = homeY(.915f)
    val progress: ImmersiveLandscapeBounds
        get() = ImmersiveLandscapeBounds(titleLeft, bookY(.243f), bookX(.774f), bookY(.243f) + 4f)
    val refreshX: Float
        get() = bookX(.810f)
    val refreshY: Float
        get() = bookY(.243f)
    val statsLeft: Float
        get() = bookX(.061f)
    val statsRight: Float
        get() = bookX(.212f)
    val railCenter: Float
        get() = homeX(.907f)
    val clockLeft: Float
        get() = homeX(.061f)
    val clockBaseline: Float
        get() = homeY(.205f)
    val dateBaseline: Float
        get() = homeY(.249f)
    val coverArea: ImmersiveLandscapeBounds
        get() = bookBounds(ImmersiveLandscapeBounds(.25f, .377f, .84f, .92f))

    val dividerYs: List<Float>
        get() = listOf(.339f, .524f, .712f).map(::bookY)
    val valueYs: List<Float>
        get() = listOf(.428f, .615f, .802f).map(::bookY)
    val labelYs: List<Float>
        get() = listOf(.470f, .659f, .845f).map(::bookY)

    /** Preserve readable text; spend vertical space before reducing the covers. */
    fun readingBudget(
        titleLineHeight: Float,
        detailLineHeight: Float,
        progressLineHeight: Float,
        gap: Float,
        titleLines: Int = 1,
    ): ImmersiveLandscapeReadingBudget {
        val barY = maxOf(progress.top, titleTop + titleLineHeight * titleLines + detailLineHeight + gap * 2)
        val coverTop = maxOf(coverArea.top, barY + 4f + gap * 2 + progressLineHeight)
        val target = cover()
        val height = minOf(target.height, (target.bottom - coverTop).coerceAtLeast(0f))
        val width = height / 1.45f
        return ImmersiveLandscapeReadingBudget(
            barY,
            ImmersiveLandscapeBounds(
                target.centerX - width / 2,
                coverTop,
                target.centerX + width / 2,
                coverTop + height,
            ),
        )
    }

    /** The cover keeps its real 1:1.45 shape, with room for both visible side rings. */
    fun cover(): ImmersiveLandscapeBounds {
        val area = coverArea
        val width = minOf(window.width * .253f, window.height * .487f / 1.45f, area.width / 2.24f)
        val height = minOf(width * 1.45f, area.height.coerceAtLeast(0f))
        val actualWidth = height / 1.45f
        val cx = bookX(.5405f).coerceIn(area.left + actualWidth * 1.12f, area.right - actualWidth * 1.12f)
        return ImmersiveLandscapeBounds(cx - actualWidth / 2, area.top, cx + actualWidth / 2, area.top + height)
    }

    companion object {
        fun dateInkFits(
            clockInkBottomAbsolute: Float,
            dateInkTopAbsolute: Float,
            dateInkBottomAbsolute: Float,
            divider: Float,
            gap: Float,
        ): Boolean =
            dateInkTopAbsolute >= clockInkBottomAbsolute + gap &&
                dateInkBottomAbsolute <= divider - gap

        /** Calibrate at default size, then carry the actual app/system magnification. */
        fun magnifiedGlyphSize(
            target: Float,
            actual: Float,
            default: Float,
        ): Float = maxOf(target, default) * actual / default.coerceAtLeast(1f)

        /** Weight always comes from the ORIGINAL statistics slot, before any new layout. */
        fun create(
            window: ImmersiveLandscapeBounds,
            homeX: Float,
            homeY: Float,
            homeWidth: Float,
            homeHeight: Float,
            originalBook: ImmersiveLandscapeBounds,
            density: Float,
        ): ImmersiveLandscapeGeometry {
            val weight = ((originalBook.height * .21f / density - 90f) / 20f).coerceIn(0f, 1f)

            fun x(fraction: Float): Float = (window.left + window.width * fraction - homeX).coerceIn(0f, homeWidth)

            fun y(fraction: Float): Float = (window.top + window.height * fraction - homeY).coerceIn(0f, homeHeight)

            fun mix(
                old: Float,
                target: Float,
            ): Float = old + (target - old) * weight
            val book =
                ImmersiveLandscapeBounds(
                    mix(originalBook.left, minOf(originalBook.left, x(.045f))),
                    mix(originalBook.top, minOf(originalBook.top, y(.08f))),
                    mix(originalBook.right, maxOf(originalBook.right, x(.85f))),
                    mix(originalBook.bottom, maxOf(originalBook.bottom, y(.94f))),
                )
            return ImmersiveLandscapeGeometry(window, homeX, homeY, originalBook, book, weight)
        }
    }
}

/** Book-local reading budget, using measured line heights rather than nominal text sizes. */
data class ImmersiveLandscapeReadingBudget(
    val barY: Float,
    val cover: ImmersiveLandscapeBounds,
)

/** Read-only admission using the consumer's current content and typography. */
interface ImmersiveLandscapeAdmission {
    fun admitsLandscapeGeometry(geometry: ImmersiveLandscapeGeometry): Boolean
}
