package pk.edu.ucp.saharaai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import pk.edu.ucp.saharaai.data.model.AvatarRequest
import pk.edu.ucp.saharaai.data.model.PaymentRequest
import pk.edu.ucp.saharaai.data.model.RegistrationRequest
import pk.edu.ucp.saharaai.data.model.BugReport
import pk.edu.ucp.saharaai.data.model.CounselorAttributeCatalog
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.AdminDashboardViewModel

private data class AdminMetric(
    val label: String,
    val value: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
)

@Composable
fun AdminDashboardScreen(
    navController: NavController,
    isEnglish: Boolean,
    dashboardViewModel: AdminDashboardViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val applications = dashboardViewModel.applications
    val payments = dashboardViewModel.payments
    val bugReports = dashboardViewModel.bugReports
    val avatarRequests = dashboardViewModel.avatarRequests
    val error = dashboardViewModel.error

    val counselorApplications = applications.filter { it.applicantType.equals("COUNSELOR", ignoreCase = true) }
    val ngoApplications = applications.filter { it.applicantType.equals("NGO", ignoreCase = true) }
    val otherApplications = applications.filterNot {
        it.applicantType.equals("COUNSELOR", ignoreCase = true) ||
            it.applicantType.equals("NGO", ignoreCase = true)
    }
    val pendingApplications = applications.filter { it.status.equals("PENDING_REVIEW", ignoreCase = true) }
    val pendingCounselorApplications = counselorApplications.count { it.status.equals("PENDING_REVIEW", ignoreCase = true) }
    val pendingNgoApplications = ngoApplications.count { it.status.equals("PENDING_REVIEW", ignoreCase = true) }
    val pendingPayments = payments.filter { it.status == "PENDING_REVIEW" }
    val pendingAvatars = avatarRequests.filter { it.status == "PENDING_REVIEW" }
    val unresolvedBugs = bugReports.filter { it.status != "RESOLVED" }
    val bg = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF10251D), MaterialTheme.colorScheme.background))
    } else {
        Brush.verticalGradient(listOf(SaharaStrongGreen.copy(.18f), MaterialTheme.colorScheme.background))
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.statusBarsPadding())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        if (isEnglish) "Admin Dashboard" else "Admin Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen
                    )
                    Text(
                        if (isEnglish) "Manual review and assignments" else "Manual review aur assignments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = SaharaWarning)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isEnglish)
                            "Payment screenshots must be verified manually against your receiving account before approval."
                        else
                            "Approve karne se pehle screenshot ko receiving account se manually verify karein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AdminMetricGrid(
                metrics = listOf(
                    AdminMetric(
                        if (isEnglish) "All forms" else "Forms",
                        pendingApplications.size,
                        Icons.Default.Badge,
                        SaharaSky
                    ),
                    AdminMetric(
                        if (isEnglish) "Counselors" else "Counselors",
                        pendingCounselorApplications,
                        Icons.Default.Psychology,
                        SaharaStrongGreen
                    ),
                    AdminMetric(
                        if (isEnglish) "NGOs" else "NGOs",
                        pendingNgoApplications,
                        Icons.Default.Groups,
                        SaharaPeach
                    ),
                    AdminMetric(
                        if (isEnglish) "Bugs" else "Bugs",
                        unresolvedBugs.size,
                        Icons.Default.BugReport,
                        if (unresolvedBugs.isEmpty()) SaharaStrongGreen else SaharaCoral
                    ),
                    AdminMetric(
                        if (isEnglish) "Payments" else "Payments",
                        pendingPayments.size,
                        Icons.Default.Payments,
                        SaharaStrongGreen
                    ),
                    AdminMetric(
                        if (isEnglish) "Avatars" else "Avatars",
                        pendingAvatars.size,
                        Icons.Default.AccountCircle,
                        if (pendingAvatars.isEmpty()) SaharaStrongGreen else SaharaWarning
                    )
                )
            )

            if (error.isNotBlank()) {
                Text(error, color = SaharaCoral, style = MaterialTheme.typography.bodySmall)
            }

            Text(
                if (isEnglish) "Counselor & NGO Reports" else "Counselor aur NGO Reports",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (applications.isEmpty()) {
                AdminEmptyCard(if (isEnglish) "No application forms submitted." else "Koi application form submit nahi hua.")
            } else {
                RegistrationReviewSection(
                    title = if (isEnglish) "Counselor Applications" else "Counselor Applications",
                    emptyMessage = if (isEnglish) "No counselor applications yet." else "Abhi koi counselor application nahi.",
                    requests = counselorApplications,
                    isEnglish = isEnglish,
                    onEmail = { request, issuedKey ->
                        val subject = Uri.encode("Sahara AI ${request.applicantType} application")
                        val body = if (issuedKey.isBlank()) "" else Uri.encode(
                            "Your documents have been approved. Your Sahara AI ${request.applicantType} access key is: $issuedKey"
                        )
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:${request.email}?subject=$subject&body=$body")
                        )
                        context.startActivity(intent)
                    },
                    onApprove = { request, key, notes, attributeIds ->
                        dashboardViewModel.approveRegistration(request, key, notes, attributeIds)
                    },
                    onReject = { request, notes ->
                        dashboardViewModel.rejectRegistration(request.requestId, notes)
                    }
                )
                RegistrationReviewSection(
                    title = if (isEnglish) "NGO Applications" else "NGO Applications",
                    emptyMessage = if (isEnglish) "No NGO applications yet." else "Abhi koi NGO application nahi.",
                    requests = ngoApplications,
                    isEnglish = isEnglish,
                    onEmail = { request, issuedKey ->
                        val subject = Uri.encode("Sahara AI ${request.applicantType} application")
                        val body = if (issuedKey.isBlank()) "" else Uri.encode(
                            "Your documents have been approved. Your Sahara AI ${request.applicantType} access key is: $issuedKey"
                        )
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:${request.email}?subject=$subject&body=$body")
                        )
                        context.startActivity(intent)
                    },
                    onApprove = { request, key, notes, attributeIds ->
                        dashboardViewModel.approveRegistration(request, key, notes, attributeIds)
                    },
                    onReject = { request, notes ->
                        dashboardViewModel.rejectRegistration(request.requestId, notes)
                    }
                )
                if (otherApplications.isNotEmpty()) {
                    RegistrationReviewSection(
                        title = if (isEnglish) "Other Applications" else "Other Applications",
                        emptyMessage = "",
                        requests = otherApplications,
                        isEnglish = isEnglish,
                        onEmail = { request, issuedKey ->
                            val subject = Uri.encode("Sahara AI ${request.applicantType} application")
                            val body = if (issuedKey.isBlank()) "" else Uri.encode(
                                "Your documents have been approved. Your Sahara AI ${request.applicantType} access key is: $issuedKey"
                            )
                            val intent = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:${request.email}?subject=$subject&body=$body")
                            )
                            context.startActivity(intent)
                        },
                        onApprove = { request, key, notes, attributeIds ->
                            dashboardViewModel.approveRegistration(request, key, notes, attributeIds)
                        },
                        onReject = { request, notes ->
                            dashboardViewModel.rejectRegistration(request.requestId, notes)
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (isEnglish) "Avatar Review" else "Avatar Review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (pendingAvatars.isEmpty()) {
                AdminEmptyCard(if (isEnglish) "No avatar requests awaiting review." else "Koi avatar request pending nahi.")
            } else {
                pendingAvatars.forEach { request ->
                    AvatarReviewCard(
                        request = request,
                        isEnglish = isEnglish,
                        onOpenImage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.fileUrl)))
                        },
                        onApprove = { dashboardViewModel.approveAvatar(request, it) },
                        onReject = { dashboardViewModel.rejectAvatar(request, it) },
                        onBlock = { dashboardViewModel.blockAvatarUser(request, it) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (isEnglish) "User Bug Reports" else "Users ki Bug Reports",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (bugReports.isEmpty()) {
                AdminEmptyCard(if (isEnglish) "No bug reports submitted." else "Koi bug report submit nahi ki gayi hai.")
            } else {
                bugReports.forEach { report ->
                    BugReviewCard(
                        report = report,
                        isEnglish = isEnglish,
                        onOpenScreenshot = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.screenshotUrl)))
                        },
                        onResolve = { dashboardViewModel.resolveBugReport(report.reportId) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (isEnglish) "Payment Proof Review" else "Payment Proof Review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (pendingPayments.isEmpty()) {
                AdminEmptyCard(if (isEnglish) "No payment screenshots awaiting review." else "Koi payment screenshot pending nahi.")
            } else {
                pendingPayments.forEach { payment ->
                    PaymentReviewCard(
                        payment = payment,
                        isEnglish = isEnglish,
                        onOpenProof = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(payment.proofUrl)))
                        },
                        onApprove = { notes ->
                            dashboardViewModel.approvePayment(payment, notes)
                        },
                        onReject = { notes, attachment ->
                            dashboardViewModel.rejectPayment(payment, notes, attachment)
                        }
                    )
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}

@Composable
private fun AvatarReviewCard(
    request: AvatarRequest,
    isEnglish: Boolean,
    onOpenImage: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    var notes by remember(request.requestId) { mutableStateOf("") }
    var confirmBlock by remember(request.requestId) { mutableStateOf(false) }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(if (isEnglish) "Block this user?" else "Is user ko block karein?") },
            text = { Text(if (isEnglish) "This prevents this UID/email from continuing after sign-in." else "Ye UID/email ko sign-in ke baad app access se rok dega.") },
            confirmButton = {
                TextButton(onClick = { confirmBlock = false; onBlock(notes) }) {
                    Text(if (isEnglish) "Block" else "Block", color = SaharaCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) {
                    Text(if (isEnglish) "Cancel" else "Cancel")
                }
            }
        )
    }
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(request.userName.ifBlank { request.userId.take(10) }, fontWeight = FontWeight.Bold)
                    Text(request.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${request.sizeBytes / 1024} KB", style = MaterialTheme.typography.labelMedium, color = SaharaStrongGreen)
            }
            AsyncImage(
                model = request.fileUrl,
                contentDescription = "Avatar request",
                modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.LightGray.copy(.12f), RoundedCornerShape(12.dp))
            )
            OutlinedButton(onClick = onOpenImage) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                Spacer(Modifier.width(6.dp))
                Text(if (isEnglish) "Open image" else "Image kholein")
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(if (isEnglish) "Admin comment" else "Admin comment") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            AdminActionRow {
                Button(
                    onClick = { onApprove(notes) },
                    colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                ) { Text(if (isEnglish) "Approve" else "Approve") }
                OutlinedButton(onClick = { onReject(notes) }) {
                    Text(if (isEnglish) "Reject" else "Reject")
                }
                OutlinedButton(onClick = { confirmBlock = true }) {
                    Text(if (isEnglish) "Block" else "Block", color = SaharaCoral)
                }
            }
        }
    }
}

@Composable
private fun BugReviewCard(
    report: BugReport,
    isEnglish: Boolean,
    onOpenScreenshot: () -> Unit,
    onResolve: () -> Unit,
) {
    var confirmResolve by remember(report.reportId) { mutableStateOf(false) }
    val isResolved = report.status == "RESOLVED"
    if (confirmResolve) {
        AlertDialog(
            onDismissRequest = { confirmResolve = false },
            title = { Text(if (isEnglish) "Resolve this bug?" else "Kya ye bug hal ho gaya hai?") },
            text = {
                Text(
                    if (isEnglish) "Confirm only after checking the screenshot and issue. This prevents an accidental resolution."
                    else "Screenshot aur issue check karne ke baad hi confirm karein. Is se ghalti se resolve nahi hoga."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmResolve = false; onResolve() }) {
                    Text(if (isEnglish) "Confirm Resolved" else "Hal Ho Gaya Hai")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResolve = false }) {
                    Text(if (isEnglish) "Cancel" else "Cancel Karein")
                }
            },
        )
    }

    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(report.maskedEmail, fontWeight = FontWeight.Bold)
                Text(
                    if (isResolved) {
                        if (isEnglish) "RESOLVED" else "HAL HO GAYA HAI"
                    } else {
                        if (isEnglish) "UNRESOLVED" else "HAL HONA BAQI HAI"
                    },
                    color = if (isResolved) SaharaStrongGreen else SaharaCoral,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(report.deviceModel, style = MaterialTheme.typography.bodySmall)
            if (report.description.isNotBlank()) {
                Text(report.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AsyncImage(
                model = report.screenshotUrl,
                contentDescription = "Bug screenshot",
                modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.LightGray.copy(alpha = .12f), RoundedCornerShape(12.dp)),
            )
            AdminActionRow {
                OutlinedButton(onClick = onOpenScreenshot) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isEnglish) "Open Screenshot" else "Screenshot Kholein")
                }
                if (!isResolved) {
                    Button(
                        onClick = { confirmResolve = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen),
                    ) {
                        Text(if (isEnglish) "Resolve" else "Hal Karein")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetricGrid(metrics: List<AdminMetric>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 520.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowMetrics.forEach { metric ->
                        AdminCountCard(
                            label = metric.label,
                            value = metric.value,
                            icon = metric.icon,
                            tint = metric.tint,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowMetrics.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCountCard(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier
) {
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.height(8.dp))
            Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminEmptyCard(message: String) {
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminActionRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun RegistrationReviewSection(
    title: String,
    emptyMessage: String,
    requests: List<RegistrationRequest>,
    isEnglish: Boolean,
    onEmail: (RegistrationRequest, String) -> Unit,
    onApprove: (RegistrationRequest, String, String, List<String>) -> Unit,
    onReject: (RegistrationRequest, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SaharaStrongGreen,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (requests.isEmpty()) {
            AdminEmptyCard(emptyMessage)
        } else {
            requests.forEach { request ->
                RegistrationReviewCard(
                    request = request,
                    isEnglish = isEnglish,
                    onEmail = { issuedKey -> onEmail(request, issuedKey) },
                    onApprove = { key, notes, attributeIds -> onApprove(request, key, notes, attributeIds) },
                    onReject = { notes -> onReject(request, notes) }
                )
            }
        }
    }
}

@Composable
private fun RegistrationReviewCard(
    request: RegistrationRequest,
    isEnglish: Boolean,
    onEmail: (String) -> Unit,
    onApprove: (String, String, List<String>) -> Unit,
    onReject: (String) -> Unit
) {
    var issuedKey by remember(request.requestId) { mutableStateOf("") }
    var notes by remember(request.requestId) { mutableStateOf("") }
    var selectedAttributeIds by remember(request.requestId) { mutableStateOf(request.approvedAttributeIds.toSet()) }
    val isCounselorRequest = request.applicantType.equals("COUNSELOR", ignoreCase = true)
    val context = LocalContext.current
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    request.applicantType,
                    fontWeight = FontWeight.Bold,
                    color = SaharaSky,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(request.status.replace("_", " "), style = MaterialTheme.typography.labelMedium)
            }
            Text(request.region, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(request.applicantName, fontWeight = FontWeight.Bold)
            if (request.organizationName.isNotBlank()) Text(request.organizationName)
            Text(request.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (request.verificationBody.isNotBlank() || request.registrationNumber.isNotBlank()) {
                Text(
                    listOf(request.verificationBody, request.registrationNumber)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.city.isNotBlank()) {
                Text(
                    listOf(request.district, request.city)
                        .filter { it.isNotBlank() }
                        .joinToString(", ") +
                        if (request.locationAccuracyMeters > 0f) " (${request.locationAccuracyMeters.toInt()}m)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.qualificationSummary.isNotBlank()) {
                Text(request.qualificationSummary, style = MaterialTheme.typography.bodySmall)
            }
            Text(request.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AdminActionRow {
                OutlinedButton(onClick = { onEmail(request.issuedKey) }) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (request.issuedKey.isBlank()) "Email" else "Email Key")
                }
                val documentEntries = request.documentUrls.ifEmpty {
                    if (request.documentUrl.isNotBlank()) mapOf("document" to request.documentUrl) else emptyMap()
                }
                documentEntries.entries.take(2).forEach { (label, url) ->
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(4.dp))
                        Text(label.replace("_", " ").take(14))
                    }
                }
            }
            val extraDocs = request.documentUrls.size - 2
            if (extraDocs > 0) {
                Text(
                    if (isEnglish) "+$extraDocs more evidence files in request data"
                    else "+$extraDocs aur evidence files request data mein",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.status == "PENDING_REVIEW") {
                OutlinedTextField(
                    value = issuedKey,
                    onValueChange = { issuedKey = it },
                    label = { Text(if (isEnglish) "Key to issue after approval" else "Approval ke baad key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isCounselorRequest) {
                    Text(
                        if (isEnglish) "Counselor search attributes" else "Counselor search attributes",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaStrongGreen,
                    )
                    CounselorAttributeCatalog.all.forEach { attribute ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedAttributeIds.contains(attribute.id),
                                onCheckedChange = { checked ->
                                    selectedAttributeIds = if (checked) {
                                        selectedAttributeIds + attribute.id
                                    } else {
                                        selectedAttributeIds - attribute.id
                                    }
                                },
                            )
                            Column {
                                Text(attribute.labelEn, style = MaterialTheme.typography.bodySmall)
                                Text(attribute.labelUr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isEnglish) "Review notes" else "Review notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                AdminActionRow {
                    Button(
                        onClick = { onApprove(issuedKey.trim(), notes, selectedAttributeIds.toList()) },
                        enabled = issuedKey.isNotBlank() && (!isCounselorRequest || selectedAttributeIds.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                    ) { Text(if (isEnglish) "Approve & Issue Key" else "Approve") }
                    OutlinedButton(onClick = { onReject(notes) }) { Text(if (isEnglish) "Reject" else "Reject") }
                }
            } else if (request.issuedKey.isNotBlank()) {
                Text(
                    (if (isEnglish) "Issued key: " else "Issue ki hui key: ") + request.issuedKey,
                    color = SaharaStrongGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PaymentReviewCard(
    payment: PaymentRequest,
    isEnglish: Boolean,
    onOpenProof: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, Uri?) -> Unit
) {
    var notes by remember(payment.requestId) { mutableStateOf("") }
    var rejectError by remember(payment.requestId) { mutableStateOf("") }
    var attachmentUri by remember(payment.requestId) { mutableStateOf<Uri?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        attachmentUri = it
    }
    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(payment.counselorName, fontWeight = FontWeight.Bold)
                Text("PKR ${payment.amountPkr}", fontWeight = FontWeight.Bold, color = SaharaStrongGreen)
            }
            Text(
                "User: ${payment.userId.take(10)}...  Account title: ${payment.accountTitle.ifBlank { "missing" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Reference: ${payment.transactionReference.ifBlank { "not supplied" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (payment.proofUrl.isNotBlank()) {
                AsyncImage(
                    model = payment.proofUrl,
                    contentDescription = "Uploaded payment screenshot",
                    modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.LightGray.copy(.12f), RoundedCornerShape(12.dp))
                )
                OutlinedButton(onClick = onOpenProof) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEnglish) "Open full proof" else "Proof kholein")
                }
            } else {
                Text(
                    if (isEnglish) "No screenshot supplied." else "Screenshot nahi di gayi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(if (isEnglish) "Verification note" else "Tasdeeq note") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            OutlinedButton(onClick = { attachmentPicker.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (attachmentUri == null) {
                        if (isEnglish) "Attach rejection image" else "Reject note image lagayein"
                    } else {
                        if (isEnglish) "Rejection image attached" else "Reject image lag gayi"
                    }
                )
            }
            if (rejectError.isNotBlank()) {
                Text(rejectError, color = SaharaCoral, style = MaterialTheme.typography.labelSmall)
            }
            AdminActionRow {
                Button(
                    onClick = { onApprove(notes) },
                    colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                ) { Text(if (isEnglish) "Approve & Assign" else "Approve & Assign") }
                OutlinedButton(
                    onClick = {
                        if (notes.isBlank()) {
                            rejectError = if (isEnglish) "A rejection note is required." else "Reject note zaroori hai."
                        } else {
                            rejectError = ""
                            onReject(notes, attachmentUri)
                        }
                    }
                ) { Text(if (isEnglish) "Reject" else "Reject") }
            }
        }
    }
}
