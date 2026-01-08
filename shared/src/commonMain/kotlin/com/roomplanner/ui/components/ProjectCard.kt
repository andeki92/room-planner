package com.roomplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roomplanner.data.models.Project
import com.roomplanner.localization.strings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Thumbnail (or placeholder icon)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Project name
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )

            // Modified date
            val modifiedDate =
                project.modifiedAt
                    .toLocalDateTime(TimeZone.currentSystemDefault())
            Text(
                text = "${modifiedDate.month.name.take(3)} ${modifiedDate.dayOfMonth}, ${modifiedDate.year}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Delete button
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.deleteButton,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.deleteProjectTitle) },
            text = { Text(strings.deleteProjectMessage(project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                ) {
                    Text(strings.deleteButton, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancelButton)
                }
            },
        )
    }
}
