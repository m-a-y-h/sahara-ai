package pk.edu.ucp.saharaai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.edu.ucp.saharaai.ui.theme.SaharaAiTheme

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaharaAiTheme {
                SleepPermissionRationale(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun SleepPermissionRationale(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Sleep data access",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Sahara reads sleep duration from Health Connect only when you choose Import. " +
                "It uses the last seven days to show your weekly sleep summary and to store " +
                "the source of each imported record. This Health Connect import does not " +
                "read phone-motion data."
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You can remove the Sleep permission at any time in Health Connect settings. " +
                "Manual sleep logging remains available without this permission.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}
