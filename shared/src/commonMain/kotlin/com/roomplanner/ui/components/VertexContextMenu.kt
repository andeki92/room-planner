package com.roomplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomplanner.localization.strings

/**
 * Context menu shown when long-pressing a selected vertex.
 * Shows Delete action and Cancel.
 *
 * Design rationale:
 * - Native iOS/Android pattern (long-press → context menu)
 * - Positioned near touch point (centered horizontally, above vertically)
 * - Delete action highlighted in error color (red) with icon
 * - Localized strings (compile-time safe via Strings interface)
 *
 * @param position Screen position where menu appears (near long-press location)
 * @param onDelete Called when Delete is tapped
 * @param onDismiss Called when Cancel is tapped or outside tap
 */
@Composable
fun VertexContextMenu(
    position: Offset,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()

    Box(
        modifier =
            modifier
                .offset(
                    x = position.x.dp - 100.dp, // Center horizontally (menu width ~200dp)
                    y = position.y.dp - 80.dp, // Above touch point
                ).width(200.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Delete button
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDelete() }
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = strings.deleteButton,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = strings.deleteButton,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Cancel button
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = strings.cancelButton,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
