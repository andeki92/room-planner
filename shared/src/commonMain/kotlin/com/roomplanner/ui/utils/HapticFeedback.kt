package com.roomplanner.ui.utils

/**
 * Haptic feedback utility for providing tactile feedback.
 *
 * Platform-specific implementation:
 * - iOS: Uses UIImpactFeedbackGenerator
 * - Android: Would use Vibrator (currently disabled)
 *
 * Design rationale:
 * - Expect/actual pattern for platform-specific code
 * - Simple API with predefined feedback types
 * - Safe no-op on platforms without haptics
 */
expect object HapticFeedback {
    /**
     * Trigger light impact feedback (selection, tap).
     * iOS: UIImpactFeedbackGenerator with light style
     */
    fun light()

    /**
     * Trigger medium impact feedback (button press, menu open).
     * iOS: UIImpactFeedbackGenerator with medium style
     */
    fun medium()

    /**
     * Trigger heavy impact feedback (error, important action).
     * iOS: UIImpactFeedbackGenerator with heavy style
     */
    fun heavy()

    /**
     * Trigger selection feedback (scrolling through options).
     * iOS: UISelectionFeedbackGenerator
     */
    fun selection()
}
