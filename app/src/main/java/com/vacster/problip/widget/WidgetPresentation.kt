package com.vacster.problip.widget

/**
 * Pure widget size policy. The widget's primary supported size is 1x1, so the
 * compact presentation is the baseline: brand mark + status hint + one large
 * START/STOP surface. The only expanded addition is the PROBLIP wordmark, shown
 * when the launcher reports enough width for it to fit beside the status hint.
 *
 * Deliberately not a responsive framework: size in, one boolean out. It can never
 * add an action — the widget stays START/STOP-only at every size, which
 * [WidgetStateTest] and [WidgetPresentationTest] pin.
 */
object WidgetPresentation {

    /**
     * At or above this reported minimum width the PROBLIP wordmark fits beside
     * the status hint without ellipsis; below it the mark alone carries the brand.
     */
    const val EXPANDED_MIN_WIDTH_DP = 140

    fun titleVisible(minWidthDp: Int): Boolean = minWidthDp >= EXPANDED_MIN_WIDTH_DP
}
