package pk.edu.ucp.saharaai.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import pk.edu.ucp.saharaai.data.model.BugReport
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.viewmodels.HelpCenterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BugReportsDialog(
    reports: List<BugReport>,
    isEnglish: Boolean,
    viewModel: HelpCenterViewModel,
    onDismiss: () -> Unit,
) {
    var showNewReport by rememberSaveable { mutableStateOf(reports.isEmpty()) }
    val autoDeviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    var deviceModel by rememberSaveable { mutableStateOf(autoDeviceModel) }
    var manuallyEditDeviceModel by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var screenshotUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val screenshotUri = screenshotUriString?.let(Uri::parse)

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        screenshotUriString = uri?.toString()
    }
    LaunchedEffect(viewModel.submittedReportId) {
        if (viewModel.submittedReportId.isNotBlank()) {
            showNewReport = false
            description = ""
            screenshotUriString = null
            viewModel.clearSubmittedEvent()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, null, tint = SaharaCoral)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isEnglish) "Bug Reports" else "Bug Batayen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }

                if (showNewReport) {
                    Text(
                        if (isEnglish) "Device model and screenshot are required. Description is optional."
                        else "Device model aur screenshot zaroori hain. Tafseel optional hai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = deviceModel,
                        onValueChange = { if (manuallyEditDeviceModel) deviceModel = it },
                        readOnly = !manuallyEditDeviceModel,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Device model *" else "Device model *") },
                        trailingIcon = {
                            IconButton(onClick = {
                                manuallyEditDeviceModel = !manuallyEditDeviceModel
                                if (!manuallyEditDeviceModel) deviceModel = autoDeviceModel
                            }) {
                                Icon(
                                    if (manuallyEditDeviceModel) Icons.Default.PhoneAndroid else Icons.Default.Edit,
                                    if (manuallyEditDeviceModel) "Use detected model" else "Enter manually",
                                )
                            }
                        },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = { screenshotPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (screenshotUri == null) {
                                if (isEnglish) "Attach screenshot *" else "Screenshot lagayen *"
                            } else {
                                if (isEnglish) "Screenshot selected" else "Screenshot select ho gaya hai"
                            }
                        )
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isEnglish) "Description (optional)" else "Tafseel (optional)") },
                        minLines = 3,
                        maxLines = 4,
                    )
                    if (viewModel.error.isNotBlank()) {
                        Text(viewModel.error, color = SaharaCoral, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (deviceModel.isBlank() || screenshotUri == null) return@Button
                            viewModel.submit(deviceModel, description, screenshotUri)
                        },
                        enabled = deviceModel.isNotBlank() && screenshotUri != null && !viewModel.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen),
                    ) {
                        if (viewModel.isSubmitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(if (isEnglish) "Submit Bug Report" else "Bug Report Bhejein")
                        }
                    }
                    if (reports.isNotEmpty()) {
                        TextButton(onClick = { showNewReport = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (isEnglish) "View submitted reports" else "Bheji hui reports dekhein")
                        }
                    }
                } else {
                    val openCount = reports.count { it.status != "RESOLVED" }
                    Text(
                        if (isEnglish) "$openCount unresolved report(s)" else "$openCount report ka hal abhi baqi hai",
                        color = if (openCount > 0) SaharaCoral else SaharaStrongGreen,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reports, key = { it.reportId }) { report ->
                            UserBugReportRow(report, isEnglish)
                        }
                    }
                    Button(
                        onClick = { showNewReport = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen),
                    ) {
                        Text(if (isEnglish) "Report New Bug" else "Naya Bug Batayen")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBugReportRow(report: BugReport, isEnglish: Boolean) {
    val resolved = report.status == "RESOLVED"
    Surface(
        color = if (resolved) SaharaStrongGreen.copy(alpha = 0.08f) else SaharaCoral.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).background(if (resolved) SaharaStrongGreen else SaharaCoral, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (resolved) {
                        if (isEnglish) "Resolved" else "Hal ho gayi hai"
                    } else {
                        if (isEnglish) "Unresolved" else "Hal hona baqi hai"
                    },
                    color = if (resolved) SaharaStrongGreen else SaharaCoral,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(report.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(report.deviceModel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (report.description.isNotBlank()) {
                Text(report.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
