package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.Serializable

/** Persistent page slots are stable while applications flow between them. Page zero uses legacy home settings. */
@Serializable
data class PageWidgetConfig(
    val widgetId: String = "",
    val widgetImagePath: String? = null,
    val imageCarouselSeconds: Int = IMAGE_CAROUSEL_DEFAULT,
    val imageSwipeDirection: Int = IMAGE_SWIPE_OFF,
    val todoWeekStartDay: Int = TODO_WEEK_START_DEFAULT,
    val wereadBoardPoolMask: Int = WEREAD_BOARD_POOL_DEFAULT,
    val boardForm: Int? = null,
)

fun DesktopConfig.forWidgetPage(page: Int): DesktopConfig {
    if (page <= 0) return this
    val slot = pageWidgets[page] ?: PageWidgetConfig()
    return copy(
        widgetId = slot.widgetId,
        widgetImagePath = slot.widgetImagePath,
        imageCarouselSeconds = slot.imageCarouselSeconds,
        imageSwipeDirection = slot.imageSwipeDirection,
        todoWeekStartDay = slot.todoWeekStartDay,
        wereadBoardPoolMask = slot.wereadBoardPoolMask,
        negativePage =
            if (slot.boardForm == null) {
                negativePage
            } else {
                negativePage.copy(
                    forms = negativePage.forms + (WEREAD_BOARD_WIDGET_ID to slot.boardForm),
                )
            },
    )
}

/** A new slot inherits home options, then retains its own component options. */
fun DesktopConfig.editWidgetPage(
    page: Int,
    edit: (DesktopConfig) -> DesktopConfig,
): DesktopConfig {
    if (page <= 0) return edit(this)
    val edited = edit(if (page in pageWidgets) forWidgetPage(page) else this)
    val slot =
        PageWidgetConfig(
            edited.widgetId,
            edited.widgetImagePath,
            edited.imageCarouselSeconds,
            edited.imageSwipeDirection,
            edited.todoWeekStartDay,
            edited.wereadBoardPoolMask,
            edited.negativePage.forms[WEREAD_BOARD_WIDGET_ID],
        )
    return copy(pageWidgets = if (slot.widgetId.isBlank()) pageWidgets - page else pageWidgets + (page to slot))
}

fun DesktopConfig.firstGridRows(
    landscape: Boolean,
    model: String = "",
): Int {
    val preferred = if (landscape) landscapeFirstPageRows else portraitFirstPageRows
    return (preferred ?: firstPageRows).takeIf { it > 0 }
        ?: if (landscape) {
            if (model.equals("P4", true) || model.equals("S4", true)) 3 else 4
        } else if (model.equals("P4", true)) {
            1
        } else {
            2
        }
}

fun DesktopConfig.firstGridColumns(landscape: Boolean): Int =
    ((if (landscape) landscapeFirstPageColumns else portraitFirstPageColumns) ?: firstPageColumns)
        .takeIf { it > 0 } ?: if (landscape) 2 else 3

fun DesktopConfig.withFirstGrid(
    landscape: Boolean,
    columns: Int,
    rows: Int,
): DesktopConfig =
    if (landscape) {
        copy(landscapeFirstPageColumns = columns, landscapeFirstPageRows = rows)
    } else {
        copy(portraitFirstPageColumns = columns, portraitFirstPageRows = rows)
    }
