package com.roomplanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.roomplanner.localization.strings

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val strings = strings()
    var projectName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newProjectTitle) },
        text = {
            Column {
                Text(strings.enterProjectNamePrompt)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(strings.projectNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(projectName) },
                enabled = projectName.isNotBlank(),
            ) {
                Text(strings.createButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        },
    )
}
