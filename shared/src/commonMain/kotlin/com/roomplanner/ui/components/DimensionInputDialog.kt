package com.roomplanner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.localization.strings

/**
 * Dialog for entering dimension constraints.
 * Optimized for quick numeric input (phone keypad).
 *
 * Phase 1.5: Distance constraint input
 *
 * Features:
 * - Decimal keyboard for numeric input
 * - Unit-aware (metric cm vs imperial inches)
 * - Pre-fills existing constraint value for editing
 * - Validates input (must be positive number)
 *
 * Design rationale:
 * - AlertDialog for modal focus (blocks canvas interaction)
 * - NO preset buttons (rooms vary too much for generic presets to be useful)
 * - Density-independent units displayed to user
 * - Validation prevents invalid constraints (negative, zero, non-numeric)
 *
 * @param initialValue Existing dimension value (for editing), null for new constraint
 * @param units Measurement system (METRIC or IMPERIAL)
 * @param onDismiss Callback when dialog is dismissed/cancelled
 * @param onConfirm Callback when user confirms dimension (provides validated value)
 */
@Composable
fun DimensionInputDialog(
    initialValue: Double?,
    units: MeasurementUnits,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()
    var inputText by remember { mutableStateOf(initialValue?.toString() ?: "") }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Unit label for display
    val unitLabel =
        when (units) {
            MeasurementUnits.IMPERIAL -> "inches"
            MeasurementUnits.METRIC -> "cm"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.setDimension) },
        text = {
            // ✅ SIMPLIFIED: Just text field, no preset buttons
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("${strings.length} ($unitLabel)") },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    inputText.toDoubleOrNull()?.let { value ->
                        if (value > 0) {
                            onConfirm(value)
                            onDismiss()
                        }
                    }
                },
                enabled = inputText.toDoubleOrNull()?.let { it > 0 } == true,
            ) {
                Text(strings.setButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        },
        modifier = modifier,
    )
}
