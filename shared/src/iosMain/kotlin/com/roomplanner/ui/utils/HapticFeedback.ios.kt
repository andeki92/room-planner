package com.roomplanner.ui.utils

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS implementation of haptic feedback using UIKit.
 */
actual object HapticFeedback {
    private val lightGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val mediumGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavyGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val selectionGenerator = UISelectionFeedbackGenerator()

    actual fun light() {
        lightGenerator.impactOccurred()
    }

    actual fun medium() {
        mediumGenerator.impactOccurred()
    }

    actual fun heavy() {
        heavyGenerator.impactOccurred()
    }

    actual fun selection() {
        selectionGenerator.selectionChanged()
    }
}
