package com.roomplanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Dialog for entering angle constraints.
 * Optimized for quick numeric input (phone keypad).
 *
 * Phase 1.6.3: Angle constraint input
 *
 * Features:
 * - Decimal keyboard for numeric input
 * - Shows current angle between edges
 * - Pre-fills existing constraint value for editing
 * - Validates input (must be 0-360 degrees)
 *
 * Design rationale:
 * - AlertDialog for modal focus (blocks canvas interaction)
 * - Shows current angle for reference
 * - Validation prevents invalid constraints (negative, >360, non-numeric)
 *
 * @param currentAngle Current angle between the edges (for reference)
 * @param initialValue Existing constraint value (for editing), null for new constraint
 * @param onDismiss Callback when dialog is dismissed/cancelled
 * @param onConfirm Callback when user confirms angle (provides validated value in degrees)
 */
@Composable
fun AngleInputDialog(
    currentAngle: Double,
    initialValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf(initialValue?.toString() ?: "90") }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Angle Constraint") },
        text = {
            Column {
                Text(
                    text = "Current angle: ${currentAngle.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Target angle (degrees)") },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    inputText.toDoubleOrNull()?.let { value ->
                        if (value >= 0 && value <= 360) {
                            onConfirm(value)
                            onDismiss()
                        }
                    }
                },
                enabled = inputText.toDoubleOrNull()?.let { it >= 0 && it <= 360 } == true,
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    )
}
