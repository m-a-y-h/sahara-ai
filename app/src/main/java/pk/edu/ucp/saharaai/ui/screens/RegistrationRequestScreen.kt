package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.theme.SaharaCoral
import pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.utils.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.RegistrationRequestViewModel

private data class EvidenceRequirement(
    val key: String,
    val titleEn: String,
    val titleUr: String,
    val descEn: String,
    val descUr: String,
    val required: Boolean = true,
)

@Composable
fun RegistrationRequestScreen(
    navController: NavController,
    applicantType: String,
    isEnglish: Boolean,
    requestViewModel: RegistrationRequestViewModel = viewModel()
) {
    val context = LocalContext.current
    val isNgo = applicantType == "NGO"
    var name by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var locationAccuracyMeters by remember { mutableFloatStateOf(0f) }
    var counselorIsMedical by remember { mutableStateOf(false) }
    var verificationBody by remember {
        mutableStateOf(if (isNgo) "SECP / Provincial Charity Registrar" else "HEC")
    }
    var registrationNumber by remember { mutableStateOf("") }
    var qualificationSummary by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var activeDocumentKey by remember { mutableStateOf<String?>(null) }
    var documentUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationServicesEnabled by remember { mutableStateOf(context.areLocationServicesEnabled()) }
    val evidenceRequirements = remember(isNgo, counselorIsMedical) {
        registrationEvidenceRequirements(isNgo, counselorIsMedical)
    }
    val requiredDocumentKeys = remember(evidenceRequirements) {
        evidenceRequirements.filter { it.required }.map { it.key }
    }
    val isSubmitting = requestViewModel.isSubmitting
    val submitted = requestViewModel.submitted
    val error = requestViewModel.error
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    LaunchedEffect(submitted) {
        if (submitted) {
            context.showLocalizedToast(
                isEnglish,
                "Request submitted. You will receive an email while the team reviews your documents.",
                "Request submit ho gayi. Team documents review karegi aur aapko email milegi.",
                android.widget.Toast.LENGTH_LONG,
            )
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = activeDocumentKey
        activeDocumentKey = null
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        // Belt-and-suspenders MIME check. We pass "image/*" to the system
        // picker so most pickers already filter, but file managers on
        // Android can ignore that hint — re-check the resolved content
        // type and refuse anything that isn't a JPEG or PNG. Uploads are
        // base64'd through BitmapFactory downstream and PDF/Doc/etc would
        // simply fail there with a less useful error.
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val allowed = mime == "image/jpeg" || mime == "image/jpg" || mime == "image/png"
        if (!allowed) {
            context.showLocalizedToast(
                isEnglish,
                "Only JPG or PNG images are accepted as evidence.",
                "Sirf JPG ya PNG image evidence ke liye qabool hai.",
                android.widget.Toast.LENGTH_LONG,
            )
            requestViewModel.reportError(
                if (isEnglish) "Only JPG / PNG files are allowed for evidence uploads."
                else "Evidence ke liye sirf JPG ya PNG file lagai ja sakti hai."
            )
            return@rememberLauncherForActivityResult
        }
        documentUris = documentUris + (key to uri)
    }
    val applyPreciseLocation: (Boolean) -> Unit = applyLocation@ { openSettingsIfDisabled ->
        locationServicesEnabled = context.areLocationServicesEnabled()
        if (!locationServicesEnabled) {
            requestViewModel.reportLocationError(
                if (isEnglish) "Turn on device location in system settings, then return to continue."
                else "System settings mein device location on karein, phir wapis aa kar continue karein."
            )
            if (openSettingsIfDisabled) {
                context.showLocalizedToast(
                    isEnglish,
                    "Turn on device location to verify the applicant region.",
                    "Applicant region verify karne ke liye device location on karein.",
                    android.widget.Toast.LENGTH_LONG,
                )
                context.openSystemLocationSettings()
            }
            return@applyLocation
        }
        requestViewModel.fetchPreciseLocation(context, isEnglish) { result ->
            city = result.city
            district = result.district
            locationAccuracyMeters = result.accuracyMeters
        }
    }
    val locationPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Select precise location access to submit this request.",
            deniedUr = "Darkhwast bhejne ke liye precise location ki ijazat dein.",
            settingsEn = "Enable precise location in App settings to verify the applicant region.",
            settingsUr = "Applicant region verify karne ke liye App settings mein precise location dein.",
        ),
        onGranted = {
            locationPermissionGranted = true
            applyPreciseLocation(true)
        },
        onDenied = {
            locationPermissionGranted = false
            requestViewModel.reportError(
                if (isEnglish) "Select precise location access to submit this request."
                else "Darkhwast bhejne ke liye precise location ki ijazat dein."
            )
        },
    )
    ObservePermissionState(locationPermissionRequester) { granted ->
        locationPermissionGranted = granted
        locationServicesEnabled = context.areLocationServicesEnabled()
        if (granted && locationServicesEnabled && city.isBlank() && district.isBlank()) {
            applyPreciseLocation(false)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SaharaStrongGreen.copy(.18f), MaterialTheme.colorScheme.background))
        ).then(Modifier.hazeSource(hazeState))
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.statusBarsPadding())
            Row(verticalAlignment = Alignment.CenterVertically) {
                HazeBackButton(onClick = { navController.popBackStack() }, hazeState = hazeState)
                Text(
                    if (isNgo) {
                        if (isEnglish) "NGO Registration Request" else "Darkhwast bā-hesiyat NGO"
                    } else {
                        if (isEnglish) "Counselor Registration Request" else "Darkhwast bā-hesiyat Counselor"
                    },
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = SaharaStrongGreen
                )
            }

            if (submitted) {
                Card(colors = CardDefaults.cardColors(containerColor = SaharaStrongGreen.copy(.12f))) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = SaharaStrongGreen, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isEnglish) "Request submitted for document review." else "Request document review ke liye submit ho gayi.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isEnglish) "Please stand by. We will email you when the review team updates your request." else "Barah-e-karam intizaar karein. Review team update degi to aapko email milegi.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                SaharaButton(
                    text = if (isEnglish) "Submitted" else "Submit ho gayi",
                    onClick = {},
                    variant = ButtonVariant.DEFAULT,
                    enabled = false,
                    isFullWidth = true
                )
                SaharaButton(
                    text = if (isEnglish) "Back" else "Wapis",
                    onClick = { navController.popBackStack() },
                    variant = ButtonVariant.OUTLINE,
                    isFullWidth = true
                )
                return@Column
            }

            Text(
                if (isEnglish)
                    "Submit verifiable credentials, location, and named evidence. Admins review each item before issuing an access key."
                else
                    "Verifiable credentials, location aur named evidence submit karein. Access key se pehle admin har cheez review karega.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SectionTitle(if (isEnglish) "Applicant Details" else "Applicant Tafseel")
            RequestField(
                name,
                { name = it },
                if (isNgo) {
                    if (isEnglish) "Authorized contact name *" else "Rabtay ke zimmedar shakhs ka naam *"
                } else {
                    if (isEnglish) "Counselor full name *" else "Counselor ka poora naam *"
                },
            )
            RequestField(
                organization,
                { organization = it },
                if (isNgo) {
                    if (isEnglish) "Registered NGO name *" else "Registered NGO ka naam *"
                } else {
                    if (isEnglish) "Affiliated NGO / organization (optional)" else "Munsalik NGO / idara (optional)"
                },
            )
            RequestField(email, { email = it }, "Email *")
            RequestField(phone, { phone = it }, if (isEnglish) "Phone" else "Phone")
            if (!isNgo) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isEnglish) "Applying as psychiatrist / medical doctor"
                                else "Psychiatrist / medical doctor ke taur par apply kar rahe hain",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isEnglish) "Requires PMDC/PMC registration evidence."
                                else "PMDC/PMC registration evidence zaroori hogi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = counselorIsMedical,
                            onCheckedChange = {
                                counselorIsMedical = it
                                verificationBody = if (it) "PMDC / PMC" else "HEC"
                                documentUris = documentUris.filterKeys { key ->
                                    registrationEvidenceRequirements(isNgo, it).any { req -> req.key == key }
                                }
                            }
                        )
                    }
                }
            }

            SectionTitle(if (isEnglish) "Verification" else "Tasdeeq")
            RequestField(
                verificationBody,
                {},
                if (isNgo) {
                    if (isEnglish) "Registrar / verification body *" else "Registrar / verification body *"
                } else {
                    if (isEnglish) "Verification body *" else "Verification body *"
                },
                readOnly = true,
            )
            RequestField(
                registrationNumber,
                { registrationNumber = it },
                if (isNgo) {
                    if (isEnglish) "Registration / license / NTN number *" else "Registration / license / NTN number *"
                } else {
                    if (counselorIsMedical) {
                        if (isEnglish) "PMDC / PMC registration number *" else "PMDC / PMC registration number *"
                    } else {
                        if (isEnglish) "HEC degree / professional membership reference *" else "HEC degree / professional membership reference *"
                    }
                },
            )
            RequestField(
                qualificationSummary,
                { qualificationSummary = it },
                if (isNgo) {
                    if (isEnglish) "Organization services and coverage summary *" else "Organization services aur coverage summary *"
                } else {
                    if (isEnglish) "Qualification and supervised experience summary *" else "Qualification aur supervised experience summary *"
                },
            )

            SectionTitle(if (isEnglish) "Location" else "Location")
            Text(
                if (isEnglish)
                    "Precise foreground location is used once to set ${if (isNgo) "the NGO city" else "your district and city"}."
                else
                    "Precise location sirf aik dafa ${if (isNgo) "NGO ka shehar" else "district aur shehar"} set karne ke liye li jayegi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationPermissionGranted = true
                        applyPreciseLocation(true)
                    } else {
                        locationPermissionGranted = false
                        locationPermissionRequester.request()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !requestViewModel.isLocating,
            ) {
                Icon(Icons.Default.LocationOn, null)
                Spacer(Modifier.width(8.dp))
                val needsLocation = city.isBlank()
                RequiredLabel(
                    text = when {
                        requestViewModel.isLocating -> if (isEnglish) "Getting precise location..." else "Precise location li ja rahi hai..."
                        city.isNotBlank() -> if (isEnglish) "Refresh precise location" else "Precise location dobara lein"
                        !locationPermissionGranted -> if (isEnglish) "Enable precise location" else "Precise location enable karein"
                        !locationServicesEnabled -> if (isEnglish) "Turn on device location" else "Device location on karein"
                        else -> if (isEnglish) "Use precise location" else "Precise location dein"
                    },
                    required = needsLocation && !requestViewModel.isLocating,
                )
            }
            if (city.isNotBlank()) {
                LocationSummaryCard(
                    city = city,
                    district = district,
                    accuracyMeters = locationAccuracyMeters,
                    isNgo = isNgo,
                    isEnglish = isEnglish,
                )
            }
            RequestField(city, {}, if (isEnglish) "City (from precise location) *" else "Shehar (precise location se) *", enabled = false)
            if (!isNgo) {
                RequestField(district, {}, if (isEnglish) "District (from precise location) *" else "District (precise location se) *", enabled = false)
            }
            if (requestViewModel.locationError.isNotBlank()) {
                Text(requestViewModel.locationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionTitle(if (isEnglish) "Required Evidence" else "Zaroori Evidence")
            Text(
                if (isEnglish)
                    if (isNgo) {
                        "Use SECP Section 42, provincial society/trust, tax, authorization, and organization-profile evidence where applicable."
                    } else {
                        "Use HEC-attested education evidence. Medical/psychiatry applicants must include PMDC/PMC registration."
                    }
                else
                    if (isNgo) {
                        "SECP Section 42, provincial society/trust, tax, authorization aur organization-profile evidence jahan apply ho use karein."
                    } else {
                        "HEC-attested education evidence use karein. Medical/psychiatry applicants PMDC/PMC registration lazmi dein."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Note about accepted formats — surfaced once for the whole
            // evidence block instead of repeating on every row.
            Text(
                text = if (isEnglish) "Only JPG or PNG images are accepted. Other formats (PDF, DOC, etc.) will be refused."
                       else "Sirf JPG ya PNG image qabool hai. PDF / DOC ya doosri format radd ho jayegi.",
                style = MaterialTheme.typography.labelSmall,
                color = SaharaCoral,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            evidenceRequirements.forEach { requirement ->
                EvidenceUploadRow(
                    requirement = requirement,
                    selected = documentUris.containsKey(requirement.key),
                    isEnglish = isEnglish,
                    onClick = {
                        activeDocumentKey = requirement.key
                        // Hint the system picker; we still re-validate in
                        // the result handler in case a file manager
                        // returns something off-type.
                        picker.launch("image/*")
                    }
                )
            }
            RequestField(details, { details = it }, if (isEnglish) "Additional review notes" else "Additional review notes", false)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            SaharaButton(
                text = when {
                    isSubmitting -> if (isEnglish) "Submitting..." else "Submit ho rahi hai..."
                    submitted -> if (isEnglish) "Submitted" else "Submit ho gayi"
                    else -> if (isEnglish) "Submit for Review" else "Review ke liye Bhejein"
                },
                onClick = {
                    val region = if (isNgo) city else "$district, $city"
                    val missingDocuments = requiredDocumentKeys.filterNot { documentUris.containsKey(it) }
                    when {
                        name.isBlank() || email.isBlank() || (isNgo && organization.isBlank()) ->
                            requestViewModel.reportError(if (isEnglish) "Required identity fields must be completed." else "Zaroori shanakhti fields mukammal karein.")
                        verificationBody.isBlank() || registrationNumber.isBlank() || qualificationSummary.isBlank() ->
                            requestViewModel.reportError(if (isEnglish) "Verification fields must be completed." else "Verification fields mukammal karein.")
                        city.isBlank() || (!isNgo && district.isBlank()) ->
                            requestViewModel.reportError(if (isEnglish) "Precise location is required." else "Precise location zaroori hai.")
                        missingDocuments.isNotEmpty() ->
                            requestViewModel.reportError(if (isEnglish) "Attach all required evidence files." else "Tamam required evidence files lagayen.")
                        else -> {
                            requestViewModel.submit(
                                applicantType, name, organization, email,
                                phone, region, city, if (isNgo) "" else district,
                                locationAccuracyMeters, verificationBody, registrationNumber,
                                qualificationSummary, details, documentUris, requiredDocumentKeys
                            )
                        }
                    }
                },
                variant = ButtonVariant.DEFAULT,
                enabled = !isSubmitting && !submitted,
                isFullWidth = true
            )
            if (isSubmitting) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SaharaStrongGreen)
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = SaharaStrongGreen,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
    )
}

@Composable
private fun RequiredLabel(
    text: String,
    required: Boolean,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val cleanText = text.trim().removeSuffix("*").trimEnd()
    val showRequired = required || text.trimEnd().endsWith("*")
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            append(cleanText)
            if (showRequired) {
                append(" ")
                withStyle(
                    SpanStyle(
                        color = SaharaCoral,
                        fontWeight = FontWeight.ExtraBold,
                    )
                ) {
                    append("*")
                }
            }
        },
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        style = style ?: LocalTextStyle.current,
    )
}

private fun Context.areLocationServicesEnabled(): Boolean {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun Context.openSystemLocationSettings() {
    startActivity(
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun LocationSummaryCard(
    city: String,
    district: String,
    accuracyMeters: Float,
    isNgo: Boolean,
    isEnglish: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = SaharaStrongGreen.copy(alpha = 0.10f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isEnglish) "Detected location" else "Detected location", fontWeight = FontWeight.Bold)
            Text(
                if (isNgo) city else listOf(district, city).filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium
            )
            if (accuracyMeters > 0f) {
                Text(
                    if (isEnglish) "Accuracy: ${accuracyMeters.toInt()}m" else "Accuracy: ${accuracyMeters.toInt()}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EvidenceUploadRow(
    requirement: EvidenceRequirement,
    selected: Boolean,
    isEnglish: Boolean,
    onClick: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                    RequiredLabel(
                        text = if (isEnglish) requirement.titleEn else requirement.titleUr,
                        required = requirement.required,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (isEnglish) requirement.descEn else requirement.descUr,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (requirement.required) {
                    Text(
                        if (isEnglish) "Required" else "Lazmi",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaharaCoral,
                    )
                } else {
                    Text(
                        if (isEnglish) "Optional where not applicable" else "Jahan apply na ho optional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val buttonText = if (selected) {
                if (isEnglish) "Replace" else "Tabdeel"
            } else {
                if (isEnglish) "Attach" else "Attach"
            }
            val buttonIcon = if (selected) Icons.Default.CheckCircle else Icons.Default.AttachFile
            val buttonColors = if (selected) {
                ButtonDefaults.buttonColors(
                    containerColor = SaharaStrongGreen.copy(alpha = 0.16f),
                    contentColor = SaharaStrongGreen,
                )
            } else {
                ButtonDefaults.outlinedButtonColors(contentColor = SaharaStrongGreen)
            }
            if (selected) {
                Button(
                    onClick = onClick,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(buttonText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(buttonText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun registrationEvidenceRequirements(
    isNgo: Boolean,
    counselorIsMedical: Boolean,
): List<EvidenceRequirement> {
    return if (isNgo) {
        listOf(
            EvidenceRequirement(
                key = "registration",
                titleEn = "SECP / provincial registration certificate",
                titleUr = "SECP / provincial registration certificate",
                descEn = "Section 42 license, society registration, trust deed registration, or equivalent registrar certificate.",
                descUr = "Section 42 license, society registration, trust deed registration ya equivalent registrar certificate.",
            ),
            EvidenceRequirement(
                key = "governing",
                titleEn = "Governing document",
                titleUr = "Governing document",
                descEn = "Memorandum and articles, constitution, by-laws, or trust deed for cross-checking objectives.",
                descUr = "Objectives cross-check ke liye memorandum/articles, constitution, by-laws ya trust deed.",
            ),
            EvidenceRequirement(
                key = "tax",
                titleEn = "Tax / certification evidence",
                titleUr = "Tax / certification evidence",
                descEn = "NTN or tax registration; include PCP/FBR certification if available.",
                descUr = "NTN ya tax registration; agar ho to PCP/FBR certification bhi lagayen.",
            ),
            EvidenceRequirement(
                key = "authorization",
                titleEn = "Authorized representative evidence",
                titleUr = "Authorized representative evidence",
                descEn = "Representative CNIC/passport plus board resolution or signed authorization letter.",
                descUr = "Representative CNIC/passport aur board resolution ya signed authorization letter.",
            ),
            EvidenceRequirement(
                key = "organization_profile",
                titleEn = "Organization profile / resume",
                titleUr = "Organization profile / resume",
                descEn = "Services, team, coverage area, referral capacity, and recent work summary.",
                descUr = "Services, team, coverage area, referral capacity aur recent work summary.",
            ),
        )
    } else {
        buildList {
            add(
                EvidenceRequirement(
                    key = "identity",
                    titleEn = "CNIC / passport",
                    titleUr = "CNIC / passport",
                    descEn = "Identity evidence used only for admin verification.",
                    descUr = "Identity evidence sirf admin verification ke liye use hogi.",
                )
            )
            add(
                EvidenceRequirement(
                    key = "degree",
                    titleEn = "HEC-attested degree and transcript",
                    titleUr = "HEC-attested degree aur transcript",
                    descEn = "Relevant psychology, counseling, psychiatry, medicine, or social-work qualification.",
                    descUr = "Relevant psychology, counseling, psychiatry, medicine ya social-work qualification.",
                )
            )
            if (counselorIsMedical) {
                add(
                    EvidenceRequirement(
                        key = "pmdc_registration",
                        titleEn = "PMDC / PMC registration certificate",
                        titleUr = "PMDC / PMC registration certificate",
                        descEn = "Required for psychiatrist or medical-doctor applications.",
                        descUr = "Psychiatrist ya medical-doctor application ke liye zaroori.",
                    )
                )
            } else {
                add(
                    EvidenceRequirement(
                        key = "professional_membership",
                        titleEn = "Professional membership or accreditation",
                        titleUr = "Professional membership ya accreditation",
                        descEn = "Attach if you hold a relevant professional registration or association membership.",
                        descUr = "Agar relevant professional registration ya association membership hai to lagayen.",
                        required = false,
                    )
                )
            }
            add(
                EvidenceRequirement(
                    key = "resume",
                    titleEn = "Resume / CV",
                    titleUr = "Resume / CV",
                    descEn = "Include supervised counseling or clinical experience and current practice setting.",
                    descUr = "Supervised counseling ya clinical experience aur current practice setting shamil karein.",
                )
            )
            add(
                EvidenceRequirement(
                    key = "experience_letter",
                    titleEn = "Experience or affiliation letter",
                    titleUr = "Experience ya affiliation letter",
                    descEn = "Employment, NGO affiliation, supervision, or practice letter that can be verified.",
                    descUr = "Employment, NGO affiliation, supervision ya practice letter jo verify ho sake.",
                )
            )
        }
    }
}

@Composable
private fun RequestField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    required: Boolean = label.trimEnd().endsWith("*"),
    readOnly: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelText = label.trim().removeSuffix("*").trim()
    val shouldWrapLabel = labelText.length > 32
    val labelIsCompact = isFocused || value.isNotEmpty() || shouldWrapLabel
    val labelStyle = if (labelIsCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            RequiredLabel(
                text = label,
                required = required,
                maxLines = if (shouldWrapLabel) 2 else 1,
                style = labelStyle,
                overflow = TextOverflow.Clip,
            )
        },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 2,
        enabled = enabled,
        readOnly = readOnly,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaharaStrongGreen)
    )
}
