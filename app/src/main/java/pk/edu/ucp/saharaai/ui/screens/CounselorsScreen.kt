package pk.edu.ucp.saharaai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.BuildConfig
import pk.edu.ucp.saharaai.data.model.CounselorAttributeCatalog
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.CounselorsViewModel

data class Counselor(
    val key: String,
    val name: String,
    val specialtyEn: String,
    val specialtyUr: String,
    val attributeIds: List<String>,
    val rating: Double,
    val reviews: Int,
    val experienceEn: String,
    val experienceUr: String,
    val available: Boolean,
    val avatar: String,
    val nextAvailableEn: String,
    val nextAvailableUr: String,
    val feePkr: Int,
    val callEnabled: Boolean,
)


@Composable
fun CounselorsScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    counselorsViewModel: CounselorsViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
    var paymentCounselor by remember { mutableStateOf<Counselor?>(null) }
    var paymentCounselorKey by remember { mutableStateOf("") }
    val submissionMessage = counselorsViewModel.submissionMessage
    val myPaymentStatuses = counselorsViewModel.paymentStatuses
    val uid = counselorsViewModel.uid
    // All ACTIVE counselors (online AND offline). Each entry carries
    // `effectiveOnline = isOnline && !isInvisible`. The UI surfaces both, with
    // offline ones rendered with a grey status pip and sorted after the online
    // ones so users can still discover them and queue a message.
    val activeRawCounselors = counselorsViewModel.allCounselors
    val isLoading = counselorsViewModel.isLoading

    LaunchedEffect(Unit) {
        counselorsViewModel.initialize()
    }

    val allCounselors = remember(activeRawCounselors) {
        activeRawCounselors.map { data ->
            val key        = (data["key"] as? String).orEmpty()
            val name       = (data["assignedName"] as? String)?.ifBlank { "Counselor" } ?: "Counselor"
            val attributeIds = (data["attributeIds"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() } }
                .orEmpty()
            val labelsEn = (data["attributeLabelsEn"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() } }
                .orEmpty()
            val labelsUr = (data["attributeLabelsUr"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() } }
                .orEmpty()
            val fallbackSpec = (data["specialization"] as? String)?.takeIf { it.isNotBlank() } ?: "Mental Health"
            val rating     = (data["rating"] as? Double) ?: 0.0
            val totalRatings = ((data["totalRatings"] as? Long)?.toInt()) ?: 0
            val sessions   = ((data["sessionCount"] as? Long)?.toInt()) ?: 0
            val feePkr = when (val rawFee = data["feePkr"]) {
                is Long -> rawFee.toInt()
                is Int -> rawFee
                is Double -> rawFee.toInt()
                is String -> rawFee.toIntOrNull() ?: 0
                else -> 0
            }.coerceIn(0, 5000)
            val initials   = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
            val effectiveOnline = data["effectiveOnline"] as? Boolean ?: false
            Counselor(
                key            = key,
                name           = name,
                specialtyEn    = labelsEn.ifEmpty { listOf(fallbackSpec) }.joinToString(", "),
                specialtyUr    = labelsUr.ifEmpty { listOf(fallbackSpec) }.joinToString(", "),
                attributeIds   = attributeIds,
                rating         = rating,
                reviews        = totalRatings,
                experienceEn   = if (sessions > 0) "$sessions sessions" else "New",
                experienceUr   = if (sessions > 0) "$sessions sessions" else "Naya",
                // Drives the status pill in the counselor card: online → green
                // "Online" pill, offline → grey "Offline" pill (see ~line 354).
                available      = effectiveOnline,
                avatar         = initials.ifEmpty { name.take(2).uppercase() },
                nextAvailableEn = if (effectiveOnline) "Available Now" else "Currently Offline",
                nextAvailableUr = if (effectiveOnline) "Abhi Dastiyab" else "Abhi Offline",
                feePkr         = feePkr,
                callEnabled    = data["callEnabled"] as? Boolean ?: false,
            )
        }.sortedByDescending { it.available }   // online counselors first
    }

    val filteredCounselors = allCounselors.filter {
        (selectedFilter == "all" || it.attributeIds.contains(selectedFilter)) &&
                (it.name.contains(searchQuery, ignoreCase = true) ||
                    it.specialtyEn.contains(searchQuery, ignoreCase = true) ||
                    it.specialtyUr.contains(searchQuery, ignoreCase = true))
    }

    val categories = listOf("all" to if (isEnglish) "All" else "Sab") +
        CounselorAttributeCatalog.all.map { it.id to if (isEnglish) it.labelEn else it.labelUr }

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }

    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    val blobMotion = rememberBackdropBlobMotion()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).primaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
            Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).secondaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))
        }

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Professional Counselors" else "Mahir Counselors",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaStrongGreen
                        )
                        Text(
                            text = if (isEnglish) "Find the right support for you" else "Apne liye sahi rehnumai chunein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (isEnglish) "Search by name or specialty..." else "Naam ya marz se talash karein...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SaharaStrongGreen) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SaharaStrongGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { (categoryId, categoryLabel) ->
                        val isSelected = selectedFilter == categoryId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) SaharaStrongGreen else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .border(1.dp, if (isSelected) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                .clickable { selectedFilter = categoryId }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (submissionMessage.isNotBlank()) {
                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text(submissionMessage, color = SaharaStrongGreen, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    myPaymentStatuses.firstOrNull()?.let { latest ->
                        SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Column {
                                Text(
                                    if (isEnglish) "Paid consultation request" else "Paid consultation request",
                                    fontWeight = FontWeight.Bold,
                                    color = SaharaStrongGreen
                                )
                                Text(
                                    "${latest.counselorName}: ${latest.status.replace("_", " ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    when {
                        
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(color = SaharaStrongGreen)
                                    Text(
                                        text = if (isEnglish) "Loading counselors..." else "Counselors load ho rahe hain...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        
                        filteredCounselors.isEmpty() -> {
                            SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = SaharaStrongGreen.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = if (isEnglish) "No counselors match your search" else "Aap ke search se koi counselor match nahi hua",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isEnglish) "Try a different category or clear your search. Offline counselors are listed too so you can queue a message."
                                                   else "Doosri category try karein ya search clear karein. Offline counselors bhi yahan dikhte hain — message queue kar sakte hain.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        
                        else -> {
                            filteredCounselors.forEach { counselor ->
                                val counselorId  = counselor.key
                                val specialty    = if (isEnglish) counselor.specialtyEn else counselor.specialtyUr
                                val experience   = if (isEnglish) counselor.experienceEn else counselor.experienceUr
                                val nextAvail    = if (isEnglish) counselor.nextAvailableEn else counselor.nextAvailableUr
                                val context      = androidx.compose.ui.platform.LocalContext.current
                                val latestPayment = myPaymentStatuses.firstOrNull { it.counselorKey == counselorId }
                                val hasApprovedPayment = latestPayment?.status == "ASSIGNED"
                                val feeLabel = if (counselor.feePkr <= 0) {
                                    if (isEnglish) "Free" else "Free"
                                } else {
                                    "PKR ${counselor.feePkr}"
                                }

                                SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth()) {

                                        
                                        Row(verticalAlignment = Alignment.Top) {
                                            Box(
                                                modifier = Modifier.size(56.dp).background(SaharaStrongGreen.copy(alpha = 0.8f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(counselor.avatar, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                        Text(counselor.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                        Text(specialty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    // Online presence pill: green dot + "Online" for connected
                                                    // counselors, grey dot + "Offline" otherwise. Offline counselors
                                                    // stay visible in the list (sorted after online ones) so users can
                                                    // still see who's around and queue a message.
                                                    val statusColor = if (counselor.available) SaharaStrongGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .background(statusColor.copy(0.15f), MaterialTheme.shapes.small)
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(statusColor),
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = if (counselor.available) (if (isEnglish) "Online" else "Online")
                                                                   else (if (isEnglish) "Offline" else "Offline"),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = statusColor,
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = SaharaWarning, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("${counselor.rating}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(" (${counselor.reviews})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SaharaSky, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(experience, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = SaharaStrongGreen, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(nextAvail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Payments, contentDescription = null, tint = SaharaStrongGreen, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(feeLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                                            
                                            OutlinedIconButton(
                                                onClick = {
                                                    when {
                                                        !hasApprovedPayment && counselor.feePkr > 0 -> context.showLocalizedToast(
                                                            isEnglish,
                                                            "Please submit payment details and wait for approval before calling.",
                                                            "Call se pehle payment details submit kar ke approval ka intizar karein.",
                                                        )
                                                        !counselor.callEnabled -> context.showLocalizedToast(
                                                            isEnglish,
                                                            "This counselor is not taking calls right now.",
                                                            "Ye counselor abhi calls nahi le raha.",
                                                        )
                                                        counselorId.isNotBlank() -> navController.navigate("counselor-call/$counselorId/${counselor.name.replace(" ", "_")}/voice/self")
                                                    }
                                                },
                                                modifier = Modifier.size(48.dp),
                                                border = BorderStroke(1.dp, SaharaStrongGreen.copy(alpha = 0.5f)),
                                                enabled = counselorId.isNotBlank()
                                            ) {
                                                Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = SaharaStrongGreen)
                                            }

                                            
                                            OutlinedIconButton(
                                                onClick = {
                                                    when {
                                                        !hasApprovedPayment && counselor.feePkr > 0 -> context.showLocalizedToast(
                                                            isEnglish,
                                                            "Please submit payment details and wait for approval before calling.",
                                                            "Call se pehle payment details submit kar ke approval ka intizar karein.",
                                                        )
                                                        !counselor.callEnabled -> context.showLocalizedToast(
                                                            isEnglish,
                                                            "This counselor is not taking calls right now.",
                                                            "Ye counselor abhi calls nahi le raha.",
                                                        )
                                                        counselorId.isNotBlank() -> navController.navigate("counselor-call/$counselorId/${counselor.name.replace(" ", "_")}/video/self")
                                                    }
                                                },
                                                modifier = Modifier.size(48.dp),
                                                border = BorderStroke(1.dp, SaharaSky.copy(alpha = 0.5f)),
                                                enabled = counselorId.isNotBlank()
                                            ) {
                                                Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = SaharaSky)
                                            }

                                            
                                            SaharaButton(
                                                text = if (isEnglish) "Chat" else "Chat",
                                                onClick = {
                                                    if (!hasApprovedPayment && counselor.feePkr > 0) {
                                                        context.showLocalizedToast(
                                                            isEnglish,
                                                            "Please submit payment details and wait for approval before chat.",
                                                            "Chat se pehle payment details submit kar ke approval ka intizar karein.",
                                                        )
                                                    } else if (counselorId.isNotBlank() && uid.isNotBlank()) {
                                                        navController.navigate("counselor-chat/$counselorId/${counselor.name.replace(" ", "_")}")
                                                    }
                                                },
                                                variant = ButtonVariant.DEFAULT,
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                isEnglish = isEnglish,
                                                enabled = counselorId.isNotBlank()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = {
                                                paymentCounselor = counselor
                                                paymentCounselorKey = counselorId
                                            },
                                            enabled = uid.isNotBlank() && counselorId.isNotBlank() && counselor.feePkr > 0,
                                            modifier = Modifier.fillMaxWidth(),
                                            border = BorderStroke(1.dp, SaharaStrongGreen.copy(alpha = .5f)),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (hasApprovedPayment) {
                                                    if (isEnglish) "Pay again for this counselor" else "Isi counselor ke liye dobara pay karein"
                                                } else {
                                                    if (isEnglish) "Submit payment details" else "Payment details submit karein"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 32.dp))
            }
        }

        paymentCounselor?.let { counselor ->
            PaymentProofDialog(
                hazeState = hazeState,
                counselorName = counselor.name,
                amountPkr = counselor.feePkr,
                isEnglish = isEnglish,
                onDismiss = { paymentCounselor = null },
                onSubmit = { accountTitle, reference, proofUri ->
                    counselorsViewModel.submitPayment(
                        counselorKey = paymentCounselorKey,
                        counselorName = counselor.name,
                        amount = counselor.feePkr.toString(),
                        accountTitle = accountTitle,
                        reference = reference,
                        proofUri = proofUri,
                        isEnglish = isEnglish,
                        onSuccess = {
                            paymentCounselor = null
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun PaymentProofDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    counselorName: String,
    amountPkr: Int,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Uri?) -> Unit
) {
    var accountTitle by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var proofUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { proofUri = it }

    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text(if (isEnglish) "Paid session with $counselorName" else "$counselorName ke saath paid session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEnglish)
                        "Send the locked counselor fee to the receiving account below. Approval is manual and usually takes half an hour to 1 day."
                    else
                        "Locked counselor fee neeche diye gaye account mein bhejein. Approval manual hai aur aam tor par aadha ghanta se 1 din lagta hai.",
                    style = MaterialTheme.typography.bodySmall
                )
                PaymentReceiverRow("Title", BuildConfig.BANK_ACCOUNT_TITLE)
                PaymentReceiverRow("IBAN", BuildConfig.BANK_IBAN)
                PaymentReceiverRow("Bank", BuildConfig.BANK_NAME)
                PaymentReceiverRow("Account #", BuildConfig.BANK_ACCOUNT_NUMBER)
                OutlinedTextField(
                    value = "PKR $amountPkr",
                    onValueChange = {},
                    label = { Text(if (isEnglish) "Locked counselor fee" else "Locked counselor fee") },
                    singleLine = true,
                    enabled = false
                )
                OutlinedTextField(
                    value = accountTitle,
                    onValueChange = { accountTitle = it },
                    label = { Text(if (isEnglish) "Your account title *" else "Aapka account title *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text(if (isEnglish) "Transaction ID (optional)" else "Transaction ID (optional)") },
                    singleLine = true
                )
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (proofUri == null) {
                            if (isEnglish) "Attach screenshot (optional)" else "Screenshot lagayein (optional)"
                        } else {
                            if (isEnglish) "Screenshot selected" else "Screenshot select ho gayi"
                        }
                    )
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        accountTitle.isBlank() -> error = if (isEnglish) "Enter your account title." else "Apna account title likhein."
                        else -> onSubmit(accountTitle.trim(), reference.trim(), proofUri)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
            ) { Text(if (isEnglish) "Submit" else "Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isEnglish) "Cancel" else "Cancel") } }
    )
}

@Composable
private fun PaymentReceiverRow(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "Not configured" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
