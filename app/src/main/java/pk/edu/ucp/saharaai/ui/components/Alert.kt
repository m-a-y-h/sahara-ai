package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun Alert(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isDestructive) colorScheme.errorContainer else colorScheme.surface
    val contentColor = if (isDestructive) colorScheme.onErrorContainer else colorScheme.onSurface
    val borderColor = if (isDestructive) colorScheme.error.copy(alpha = 0.5f) else colorScheme.outlineVariant
    val icon = if (isDestructive) Icons.Default.Warning else Icons.Default.Info

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = contentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}