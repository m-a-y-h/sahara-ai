package pk.edu.ucp.saharaai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edu.ucp.saharaai.ui.theme.SaharaPeach
import pk.edu.ucp.saharaai.ui.theme.SaharaWarning



@Composable
fun PrescriptionRedirectCard(isEnglish: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = SaharaPeach.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SaharaPeach.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.MedicalServices,
                contentDescription = null,
                tint = SaharaPeach,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "Outside Sahara AI's scope" else "Sahara AI ke scope se bahar",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = SaharaPeach,
                )
                Text(
                    text = if (isEnglish) {
                        "Please talk to your prescribing doctor or a licensed pharmacist."
                    } else {
                        "Apne doctor ya licensed pharmacist se baat karein."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SaharaUnreachableBanner(
    visible: Boolean,
    isEnglish: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = SaharaWarning.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SaharaWarning.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = SaharaWarning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isEnglish) {
                    "Sahara AI is unavailable right now. Your message was not sent; try again in a moment."
                } else {
                    "Sahara AI abhi available nahi. Aapka message send nahi hua; thori der baad dobara koshish karein."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
