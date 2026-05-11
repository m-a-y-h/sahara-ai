package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class BadgeVariant { DEFAULT, SECONDARY, DESTRUCTIVE, OUTLINE }

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.DEFAULT
) {
    val backgroundColor = when (variant) {
        BadgeVariant.DEFAULT -> MaterialTheme.colorScheme.primary
        BadgeVariant.SECONDARY -> MaterialTheme.colorScheme.secondary
        BadgeVariant.DESTRUCTIVE -> MaterialTheme.colorScheme.error
        BadgeVariant.OUTLINE -> Color.Transparent
    }

    val textColor = when (variant) {
        BadgeVariant.OUTLINE -> MaterialTheme.colorScheme.onSurface
        else -> Color.White
    }

    val borderModifier = if (variant == BadgeVariant.OUTLINE) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .then(borderModifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}