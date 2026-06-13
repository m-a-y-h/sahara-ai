package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.CommunityUiState
import pk.edu.ucp.saharaai.viewmodels.CommunityViewModel
import java.text.SimpleDateFormat
import java.util.*



private fun relativeTime(ts: Long): String {
    val ms = System.currentTimeMillis() - ts
    return when {
        ms < 60_000L       -> "Just now"
        ms < 3_600_000L    -> "${ms / 60_000}m ago"
        ms < 86_400_000L   -> "${ms / 3_600_000}h ago"
        ms < 604_800_000L  -> "${ms / 86_400_000}d ago"
        else               -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

private val categoryColors = mapOf(
    "GENERAL"    to SaharaLavender,
    "RECOVERY"   to SaharaStrongGreen,
    "SUPPORT"    to SaharaSky,
    "MOTIVATION" to SaharaPeach
)



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommunityScreen(
    navController: NavController,
    isEnglish: Boolean = false,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    communityViewModel: CommunityViewModel = viewModel()
) {
    val hazeState = remember { HazeState() }
    val isDark    = isSystemInDarkTheme()

    val bgGradient = if (isDark)
        listOf(SaharaLavender.copy(0.22f), MaterialTheme.colorScheme.background.copy(0.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaLavender.copy(0.28f), SaharaSkyLight.copy(0.12f), MaterialTheme.colorScheme.background.copy(0.2f))

    val uid             = communityViewModel.uid
    val posts           by communityViewModel.posts.collectAsState()
    val likedPostIds    by communityViewModel.likedPostIds.collectAsState()
    val uiState         by communityViewModel.uiState.collectAsState()
    val userName        by communityViewModel.userName.collectAsState()
    val repliesMap      by communityViewModel.repliesMap.collectAsState()
    val expandedReplies by communityViewModel.expandedReplies.collectAsState()

    LaunchedEffect(Unit) { communityViewModel.listenToPosts() }

    
    var showNewPost      by remember { mutableStateOf(false) }
    var newPostContent   by remember { mutableStateOf("") }
    var isAnonymous      by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("GENERAL") }

    var deleteTarget by remember { mutableStateOf<String?>(null) }   
    var flagTarget   by remember { mutableStateOf<String?>(null) }   

    
    Box(modifier = Modifier.fillMaxSize()) {
        ScreenBackdrop(
            hazeState = hazeState,
            bgGradient = bgGradient,
            primaryBlob = BackdropBlobSpec(
                size = 300.dp,
                offsetX = 200.dp,
                offsetY = (-100).dp,
                color = SaharaLavender.copy(0.2f),
            ),
            secondaryBlob = null,
        )

        Scaffold(
            bottomBar    = { BottomNav(navController = navController, hazeState = hazeState) },
            floatingActionButton = {
                if (uid.isNotBlank()) {
                    FloatingActionButton(
                        onClick        = { showNewPost = true },
                        containerColor = SaharaLavender,
                        shape          = CircleShape
                    ) { Icon(Icons.Default.Add, null, tint = Color.White) }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(
                        onClick = onNavigateBack,
                        hazeState = hazeState,
                        tint = SaharaLavender
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isEnglish) "Community" else "Community",
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color      = SaharaLavender
                        )
                        Text(
                            if (isEnglish) "Connect with peers safely" else "Dosron ke saath rabta karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                
                when {
                    uiState is CommunityUiState.Loading && posts.isEmpty() -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            CircularProgressIndicator(color = SaharaLavender)
                        }
                    }

                    uiState is CommunityUiState.Error && posts.isEmpty() -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, null, tint = SaharaCoral, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    (uiState as CommunityUiState.Error).message,
                                    style     = MaterialTheme.typography.bodyMedium,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { communityViewModel.listenToPosts() }) {
                                    Text(if (isEnglish) "Retry" else "Dobara Try Karein", color = SaharaLavender)
                                }
                            }
                        }
                    }

                    posts.isEmpty() -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Forum, null, tint = SaharaLavender.copy(0.4f), modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (isEnglish) "No posts yet.\nTap + to be the first!" else "Abhi koi post nahi.\n+ dabayein aur pehli post likhein!",
                                    style     = MaterialTheme.typography.bodyMedium,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier            = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding      = PaddingValues(bottom = 16.dp)
                        ) {
                            items(posts, key = { it["postId"] as? String ?: it.hashCode().toString() }) { post ->
                                val postId = post["postId"] as? String ?: return@items
                                CommunityPostCard(
                                    post              = post,
                                    currentUid        = uid,
                                    isEnglish         = isEnglish,
                                    isLiked           = postId in likedPostIds,
                                    isRepliesExpanded = postId in expandedReplies,
                                    replies           = repliesMap[postId] ?: emptyList(),
                                    onLike            = { communityViewModel.toggleLike(postId) },
                                    onToggleReplies   = { communityViewModel.toggleReplies(postId) },
                                    onFlag            = { flagTarget = postId },
                                    onDelete          = { deleteTarget = postId },
                                    onAddReply        = { text, anon -> communityViewModel.addReply(postId, text, anon) },
                                    isDark            = isDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    
    deleteTarget?.let { postId ->
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (isEnglish) "Delete Post?" else "Post Delete Karein?", fontWeight = FontWeight.Bold) },
            text  = { Text(if (isEnglish) "This post will be permanently deleted." else "Yeh post hamesha ke liye delete ho jaegi.") },
            confirmButton = {
                TextButton(onClick = { communityViewModel.deletePost(postId); deleteTarget = null }) {
                    Text(if (isEnglish) "Delete" else "Delete", color = SaharaCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(if (isEnglish) "Cancel" else "Cancel Karein") } }
        )
    }

    flagTarget?.let { postId ->
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { flagTarget = null },
            title = { Text(if (isEnglish) "Report Post?" else "Post Report Karein?", fontWeight = FontWeight.Bold) },
            text  = { Text(if (isEnglish) "This post will be flagged for review." else "Yeh post review ke liye flag ho jaegi.") },
            confirmButton = {
                TextButton(onClick = { communityViewModel.flagPost(postId); flagTarget = null }) {
                    Text(if (isEnglish) "Report" else "Report Karein", color = SaharaCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { flagTarget = null }) { Text(if (isEnglish) "Cancel" else "Cancel Karein") } }
        )
    }

    if (showNewPost) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { showNewPost = false },
            title = {
                Text(
                    if (isEnglish) "Share with Community" else "Community ke saath share karein",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value         = newPostContent,
                        onValueChange = { newPostContent = it },
                        label         = { Text(if (isEnglish) "What's on your mind?" else "Aap kya soch rahe hain?") },
                        minLines      = 3,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = SaharaLavender,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                        )
                    )
                    Spacer(Modifier.height(14.dp))

                    
                    Text(
                        if (isEnglish) "Category" else "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        listOf("GENERAL", "RECOVERY", "SUPPORT", "MOTIVATION").forEach { cat ->
                            val catColor = categoryColors[cat] ?: SaharaLavender
                            val selected = selectedCategory == cat
                            Surface(
                                shape    = RoundedCornerShape(20.dp),
                                color    = if (selected) catColor.copy(0.18f) else Color.Transparent,
                                border   = BorderStroke(
                                    1.dp,
                                    if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                                ),
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text       = cat.lowercase().replaceFirstChar { it.uppercaseChar() },
                                    fontSize   = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked         = isAnonymous,
                            onCheckedChange = { isAnonymous = it },
                            colors          = CheckboxDefaults.colors(checkedColor = SaharaLavender)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                if (isEnglish) "Post anonymously" else "Anjaane taur par post karein",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isAnonymous) {
                                Text(
                                    if (isEnglish) "Your name won't be shown" else "Aapka naam nahi dikhega",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPostContent.isNotBlank() && uid.isNotBlank()) {
                            communityViewModel.createPost(
                                content     = newPostContent.trim(),
                                isAnonymous = isAnonymous,
                                category    = selectedCategory
                            )
                        }
                        newPostContent   = ""
                        isAnonymous      = false
                        selectedCategory = "GENERAL"
                        showNewPost      = false
                    }
                ) {
                    Text(
                        if (isEnglish) "Post" else "Post Karein",
                        color      = SaharaLavender,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPost = false; newPostContent = "" }) {
                    Text(if (isEnglish) "Cancel" else "Radd Karein")
                }
            }
        )
    }
}



@Composable
fun CommunityPostCard(
    post: Map<String, Any>,
    currentUid: String,
    isEnglish: Boolean,
    isLiked: Boolean,
    isRepliesExpanded: Boolean,
    replies: List<Map<String, Any>>,
    onLike: () -> Unit,
    onToggleReplies: () -> Unit,
    onFlag: () -> Unit,
    onDelete: () -> Unit,
    onAddReply: (String, Boolean) -> Unit,
    isDark: Boolean
) {
    
    val authorId     = post["authorId"]    as? String  ?: ""
    val authorName   = post["authorName"]  as? String  ?: "User"
    val isAnonymous  = post["isAnonymous"] as? Boolean ?: false
    val content      = post["content"]     as? String  ?: ""
    val category     = post["category"]    as? String  ?: "GENERAL"
    val likesCount   = ((post["likesCount"]   as? Long) ?: (post["likesCount"] as? Int)?.toLong() ?: 0L).toInt()
    val repliesCount = ((post["repliesCount"] as? Long) ?: (post["repliesCount"] as? Int)?.toLong() ?: 0L).toInt()
    val timestamp    = (post["timestamp"] as? Long) ?: 0L

    val isOwnPost    = authorId == currentUid
    val catColor     = categoryColors[category] ?: SaharaLavender
    val cardBg       = if (isDark) Color.White.copy(0.07f) else Color.White.copy(0.6f)

    
    val displayName  = if (isAnonymous) (if (isEnglish) "User" else "User") else authorName

    
    var replyText    by remember { mutableStateOf("") }
    var replyAnon    by remember { mutableStateOf(false) }

    Surface(
        shape          = RoundedCornerShape(20.dp),
        color          = cardBg,
        tonalElevation = 2.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column {
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    
                    Box(
                        modifier         = Modifier.size(38.dp).clip(CircleShape).background(catColor.copy(0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isAnonymous) Icons.Default.Person else Icons.Default.AccountCircle,
                            null, tint = catColor, modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            
                            Surface(shape = RoundedCornerShape(6.dp), color = catColor.copy(0.15f)) {
                                Text(
                                    category.lowercase().replaceFirstChar { it.uppercaseChar() },
                                    fontSize = 10.sp, color = catColor, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), fontSize = 12.sp)
                            Text(relativeTime(timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                        }
                    }
                    
                    if (isOwnPost) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = SaharaCoral.copy(0.7f), modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(onClick = onFlag, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                
                Text(
                    content,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.06f))
                Spacer(Modifier.height(6.dp))

                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onLike() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null,
                            tint     = if (isLiked) SaharaCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "$likesCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLiked) SaharaCoral else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleReplies() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (isRepliesExpanded) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                            null,
                            tint     = if (isRepliesExpanded) SaharaLavender else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (repliesCount > 0)
                                "$repliesCount ${if (isEnglish) "repl${if (repliesCount == 1) "y" else "ies"}" else "replies"}"
                            else
                                if (isEnglish) "Reply" else "Jawab",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isRepliesExpanded) SaharaLavender else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            
            AnimatedVisibility(
                visible = isRepliesExpanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                val sectionBg = if (isDark) Color.White.copy(0.04f) else SaharaLavender.copy(0.05f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sectionBg)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (replies.isEmpty()) {
                        Text(
                            if (isEnglish) "No replies yet. Be the first!" else "Abhi koi jawab nahi. Pehle aap likhein!",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    } else {
                        replies.forEach { reply ->
                            ReplyItem(reply = reply, isDark = isDark, isEnglish = isEnglish)
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    
                    val fieldBg    = if (isDark) Color(0xFF2A3942) else Color.White
                    val hintColor  = if (isDark) Color.White.copy(0.35f) else Color(0xFF8696A0)
                    val textColor  = if (isDark) Color.White else Color(0xFF111111)

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        
                        IconButton(
                            onClick  = { replyAnon = !replyAnon },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (replyAnon) Icons.Default.VisibilityOff else Icons.Default.Person,
                                contentDescription = if (isEnglish) "Toggle anonymous" else "Anonymous toggle",
                                tint     = if (replyAnon) SaharaLavender else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(fieldBg)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value         = replyText,
                                onValueChange = { replyText = it },
                                modifier      = Modifier.fillMaxWidth(),
                                textStyle     = TextStyle(color = textColor, fontSize = 14.sp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                cursorBrush   = SolidColor(SaharaLavender),
                                singleLine    = true,
                                decorationBox = { inner ->
                                    if (replyText.isEmpty()) {
                                        Text(
                                            if (isEnglish) "Write a reply…" else "Jawab likhein…",
                                            color = hintColor, fontSize = 14.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }

                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (replyText.isNotBlank()) SaharaLavender else SaharaLavender.copy(0.3f))
                                .clickable(enabled = replyText.isNotBlank()) {
                                    onAddReply(replyText.trim(), replyAnon)
                                    replyText = ""
                                    replyAnon = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp).offset(x = 1.dp))
                        }
                    }

                    if (replyAnon) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isEnglish) "Replying anonymously" else "Anjaane taur par jawab de rahe hain",
                            style  = MaterialTheme.typography.labelSmall,
                            color  = SaharaLavender.copy(0.8f),
                            modifier = Modifier.padding(start = 40.dp)
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun ReplyItem(
    reply: Map<String, Any>,
    isDark: Boolean,
    isEnglish: Boolean
) {
    val authorName  = reply["authorName"]  as? String  ?: "User"
    val isAnonymous = reply["isAnonymous"] as? Boolean ?: false
    val content     = reply["content"]     as? String  ?: ""
    val timestamp   = (reply["timestamp"] as? Long) ?: 0L
    val displayName = if (isAnonymous) (if (isEnglish) "User" else "User") else authorName

    Row(
        verticalAlignment = Alignment.Top,
        modifier          = Modifier.fillMaxWidth()
    ) {
        
        Box(
            modifier         = Modifier.size(28.dp).clip(CircleShape).background(SaharaLavender.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isAnonymous) Icons.Default.Person else Icons.Default.AccountCircle,
                null, tint = SaharaLavender, modifier = Modifier.size(15.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), fontSize = 11.sp)
                Text(relativeTime(timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                content,
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                lineHeight = 18.sp
            )
        }
    }
}
