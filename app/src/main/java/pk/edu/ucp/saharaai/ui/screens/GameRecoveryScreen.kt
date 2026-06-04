package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.NetworkUtils
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.utils.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.GameRecoveryViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*



fun fetchRealLocation(context: Context, onLocationFetched: (String) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geocoder  = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address     = addresses[0]
                    val subLocality = address.subLocality ?: address.featureName ?: "Local Arena"
                    val locality    = address.locality    ?: address.subAdminArea ?: "Unknown City"
                    onLocationFetched("$subLocality, $locality")
                } else {
                    onLocationFetched("Unknown Sector")
                }
            } else {
                onLocationFetched("Location Unavailable")
            }
        }
    } catch (e: SecurityException) { onLocationFetched("Permission Denied") }
    catch  (e: Exception)          { onLocationFetched("Location Error")     }
}

fun generateDesiAlias(): String {
    val isMuzakar = listOf(true, false).random()
    val (adjs, nouns) = if (isMuzakar) {
        listOf("Bindaas","Toofani","Jalali","Ninja","Aflatoon","Tez","Masoom","Thanda",
            "Khatta","Meetha","Karara","Chatpata","Zaalim","Shahi","Nawabi","Khufiya",
            "Mast","Chalaak","Rangeela","Classic","Desi","Epic","Kadak","Shandar",
            "Zabardast","Zordaar","Asli","Anokha","Sakht","Kurkura","Lazeez") to
        listOf("Samosa","BunKabab","Roll","Paratha","Tikka","Kabab","Pulao","Zarda",
            "Pakora","Chargha","GolGappa","Broast","Shawarma","DahiBhalla","Naan",
            "Katakat","GolaGanda","AndaShami","RoohAfza","Falooda","Halwa","Qorma",
            "ChapliKabab","GannayKaRas","Gajrela","Amrood","Tarbooz")
    } else {
        listOf("Bindaas","Toofani","Jalali","Ninja","Aflatoon","Tez","Masoom","Thandi",
            "Khatti","Meethi","Karari","Chatpati","Zaalim","Shahi","Nawabi","Khufiya",
            "Mast","Chalaak","Rangeeli","Classic","Desi","Epic","Kadak","Shandar",
            "Zabardast","Zordaar","Asli","Anokhi","Sakht","Kurkuri","Lazeez") to
        listOf("Chutni","Biryani","Lassi","Sewiyaan","Nihari","Karahi","Haleem","Sajji",
            "Chaat","FruitChaat","DahiPhulki","Kachori","Nimko","Jalebi","Kulfi",
            "Kheer","Firni","Barfi","ChanaChaat","AlooTikki","KashmiriChai","Pheni")
    }
    return "${adjs.random()}${nouns.random()}"
}



data class LeaderboardUser(
    val username     : String,
    val location     : String,
    val xp           : Int,
    val rank         : Int,
    val isCurrentUser: Boolean = false
)

data class MilestoneTask(
    val id       : String,
    val titleEn  : String,
    val titleUr  : String,
    val descEn   : String,
    val descUr   : String,
    val xp       : Int,
    val icon     : ImageVector,
    val isComplete: Boolean,
    val requiresAiCheckIn: Boolean = false
)

private data class ScheduledRecoveryActivity(
    val id: String,
    val titleEn: String,
    val titleUr: String,
    val descEn: String,
    val descUr: String,
    val xp: Int,
    val icon: ImageVector
)

private fun activity(
    id: String,
    titleEn: String,
    titleUr: String,
    descEn: String,
    descUr: String,
    xp: Int,
    icon: ImageVector
) = ScheduledRecoveryActivity(id, titleEn, titleUr, descEn, descUr, xp, icon)

private val recoveryActivityCycle = listOf(
    listOf(
        activity("trigger_map", "Trigger Map", "Trigger Map", "Write down one situation, thought, or feeling that brought an urge today. Add one safer response you can try the next time it appears.", "Aaj urge lane wali aik situation, soch ya feeling likhein. Phir aglay martaba use karne ke liye aik safe response likhein.", 50, Icons.Default.Psychology)
    ),
    listOf(
        activity("box_breathing", "Box Breathing", "Box Breathing", "Set a five-minute timer. Breathe in for four counts, hold for four, out for four, and hold for four; repeat without forcing your breath.", "Paanch minute ka timer lagayen. Chaar count saans andar, chaar rokain, chaar bahar, phir chaar rokain; araam se repeat karein.", 35, Icons.Default.Spa),
        activity("support_message", "Support Message", "Support Paighaam", "Message one trusted person with a simple check-in, such as asking to talk or meet this week. You do not need to explain more than you are comfortable sharing.", "Aik bharosaymand shakhs ko chhota check-in message bhejein, jaise is haftay baat ya mulaqat ki darkhwast. Sirf utna batayein jitna theek lage.", 45, Icons.Default.Groups)
    ),
    listOf(
        activity("thought_reframe", "Thought Reframe", "Soch Ko Badlein", "Choose one negative thought from today. Write evidence for it, evidence against it, and a fairer replacement sentence.", "Aaj ki aik manfi soch chunain. Is ke haq aur khilaf daleel likhein, phir aik zyada munasib jumla likhein.", 65, Icons.AutoMirrored.Filled.TrendingUp)
    ),
    listOf(
        activity("mindful_walk", "Mindful Walk", "Hoshmand Chaal", "Walk for ten minutes in a safe place without scrolling. Notice five things you see, four you hear, and three physical sensations.", "Kisi mehfooz jagah par das minute baghair scrolling chalein. Paanch dekhi, chaar suni aur teen mehsoos ki hui cheezein note karein.", 45, Icons.Default.Spa),
        activity("water_reset", "Hydration Reset", "Pani Reset", "Drink a glass of water and prepare one easy non-triggering drink for later. Use this as a pause before making any impulse decision.", "Aik glass pani piyen aur baad ke liye aik asaan safe drink tayar karein. Kisi impulsive faislay se pehle is pause ko use karein.", 25, Icons.Default.Favorite)
    ),
    listOf(
        activity("urge_surfing", "Urge Surfing", "Urge Surfing", "For five minutes, observe an urge as a wave: name where it feels strongest in your body, rate it from 0 to 10, and watch for any change without acting on it.", "Paanch minute urge ko lehar ki tarah dekhein: jism mein kahan zyada hai likhein, 0 se 10 rate karein, aur action liye baghair tabdeeli dekhein.", 55, Icons.Default.Spa)
    ),
    listOf(
        activity("room_reset", "Safe Space Reset", "Mehfooz Jagah Reset", "Tidy one small area for ten minutes and remove any visible item that reminds you of use or impulsive behaviour. Stop when the timer ends.", "Das minute aik chhoti jagah saaf karein aur koi nazar aane wali triggering cheez hata dein. Timer khatam honay par ruk jaein.", 40, Icons.Default.CheckCircle),
        activity("evening_plan", "Evening Plan", "Shaam Ka Plan", "Write a simple plan for the next three hours: where you will be, one healthy activity, and who you can contact if an urge rises.", "Agley teen ghanton ka seedha plan likhein: kahan honge, aik sehatmand activity, aur urge barhay to kis se rabta karenge.", 50, Icons.Default.Psychology)
    ),
    listOf(
        activity("gratitude_note", "Three Good Things", "Teen Achi Baatein", "Record three specific things that were okay or helpful today, even if they were small. Include why one of them mattered.", "Aaj ki teen chhoti ya bari achi cheezein likhein. In mein se aik kyon aham thi yeh bhi likhein.", 35, Icons.Default.Favorite)
    ),
    listOf(
        activity("digital_break", "Scroll Break", "Scroll Se Break", "Put your phone away from reach for twenty minutes and do one offline activity. When finished, note whether your mood or urge changed.", "Phone bees minute door rakh kar aik offline kaam karein. Khatam par note karein ke mood ya urge mein farq aya ya nahi.", 50, Icons.Default.PhoneAndroid),
        activity("stretch_release", "Tension Release", "Tension Kam Karein", "Stretch shoulders, neck, arms, and legs slowly for eight minutes. Pay attention to tension easing rather than pushing into pain.", "Aath minute gardan, kandhay, bazu aur tangain ahista stretch karein. Dard ke bajaye tension kam honay par tawajjo dein.", 30, Icons.Default.Spa)
    ),
    listOf(
        activity("coping_card", "Coping Card", "Coping Card", "Write three reasons you are choosing recovery and two actions that help during a craving. Keep the list easy to open later.", "Recovery chunne ki teen wajah aur craving ke waqt madad dene walay do actions likhein. List asaani se milne wali jagah rakhein.", 55, Icons.Default.Lightbulb)
    ),
    listOf(
        activity("sleep_winddown", "Sleep Wind-down", "Neend Ki Tayari", "Choose a bedtime window tonight and avoid stimulating scrolling for the final thirty minutes before it. Mark completion after preparing that plan.", "Aaj raat sone ka waqt tay karein aur is se pehle aakhri tees minute scrolling se bachein. Plan tayar kar ke complete karein.", 40, Icons.Default.Bedtime),
        activity("body_scan", "Body Scan", "Body Scan", "Sit or lie down safely for five minutes. Move attention from feet to head, noticing sensations without judging or trying to change them.", "Mehfooz tarah baith ya lait kar paanch minute paon se sar tak jism ki feelings notice karein, baghair judge kiye.", 35, Icons.Default.Spa)
    ),
    listOf(
        activity("values_step", "Values Step", "Apni Values Ka Qadam", "Name one value you want recovery to protect, such as family, study, health, or faith. Take one small action for that value today.", "Aik value likhein jise recovery bachati hai, jaise family, parhai, sehat ya imaan. Aaj us ke liye aik chhota qadam uthayein.", 60, Icons.Default.Favorite)
    ),
    listOf(
        activity("delay_plan", "Ten-Minute Delay", "Das Minute Ka Waqfa", "Practice delaying an unwanted impulse for ten minutes. During the delay, breathe, drink water, or leave the triggering setting, then record what happened.", "Kisi na-pasand impulse ko das minute delay karein. Is waqt saans, pani ya jagah tabdeel karein, phir jo hua note karein.", 55, Icons.Default.Psychology),
        activity("check_in_call", "Check-in Call", "Check-in Call", "Call or voice-note a supportive person and ask how they are doing, or tell them you are working on staying steady today.", "Kisi supportive shakhs ko call ya voice note karein; unka haal poochein ya batayein ke aaj steady rehne ki koshish kar rahe hain.", 45, Icons.Default.Groups)
    ),
    listOf(
        activity("meal_anchor", "Healthy Meal Anchor", "Sehatmand Khana", "Plan or eat one balanced meal or snack today rather than skipping food. Note the time you chose and how your energy felt afterward.", "Aaj aik munasib meal ya snack plan karein ya khayein, khana skip na karein. Waqt aur baad ki energy note karein.", 35, Icons.Default.Favorite)
    ),
    listOf(
        activity("cbt_reflection", "CBT Reflection", "CBT Soch-Vichar", "Describe a difficult moment using situation, thought, feeling, and action. Write one different action you could attempt if it repeats.", "Mushkil lamha situation, soch, feeling aur action ke taur par likhein. Dobara ho to aik mukhtalif action bhi likhein.", 70, Icons.AutoMirrored.Filled.TrendingUp),
        activity("music_reset", "Calming Audio Break", "Sukoon Audio Break", "Listen to ten minutes of audio that helps you settle, without pairing it with scrolling or other triggers. Notice your urge level before and after.", "Das minute aisi audio sunain jo sukoon de, scrolling ya triggers ke baghair. Pehle aur baad mein urge level note karein.", 35, Icons.Default.Spa)
    ),
    listOf(
        activity("success_recall", "Recall a Win", "Kamyabi Yaad Karein", "Write about one time you handled stress without falling into an old pattern. Identify the skill or person that helped.", "Aik martaba likhein jab stress ke bawajood purani aadat mein nahi gaye. Woh skill ya shakhs likhein jis ne madad ki.", 45, Icons.Default.EmojiEvents)
    ),
    listOf(
        activity("route_change", "Change the Route", "Rasta Badlein", "Identify a place, time, or route linked with risk and choose an alternative for today. If avoiding it is impossible, write your exit plan.", "Risk se juri jagah, waqt ya rasta pehchan kar aaj alternative chunain. Agar bachna mumkin na ho to exit plan likhein.", 65, Icons.Default.Psychology),
        activity("breathing_pair", "Breathing Pair", "Saans Ki Mashq", "Complete two rounds of three-minute paced breathing, one now and one later today. Keep each breath gentle and unforced.", "Teen minute ki paced breathing ke do rounds karein, aik ab aur aik baad mein. Saans naram aur baghair zor ke rakhein.", 40, Icons.Default.Spa)
    ),
    listOf(
        activity("self_compassion", "Compassionate Note", "Narmi Ka Note", "Write a short note to yourself as if you were supporting a close friend after a difficult day. Avoid blame; focus on the next useful step.", "Apne liye chhota note likhein jaise mushkil din ke baad dost ko support karte. Ilzam ke bajaye aglay kaam ke qadam par focus karein.", 45, Icons.Default.Favorite)
    ),
    listOf(
        activity("movement_burst", "Movement Burst", "Choti Exercise", "Do ten minutes of safe movement such as walking, stretching, or light bodyweight exercise. Choose an intensity that is comfortable for you.", "Das minute mehfooz movement karein jaise walk, stretching ya halki exercise. Apne liye araam wali intensity chunain.", 45, Icons.Default.Spa),
        activity("tomorrow_intention", "Tomorrow Intention", "Kal Ka Irada", "Write one recovery intention for tomorrow and one obstacle you expect. Pair the obstacle with a realistic response.", "Kal ke liye aik recovery irada aur aik mumkin rukawat likhein. Rukawat ke saath aik haqeeqi response bhi likhein.", 40, Icons.Default.Lightbulb)
    ),
    listOf(
        activity("craving_scale", "Craving Scale Check", "Craving Scale Check", "Rate your strongest urge today from 0 to 10 and record what was happening around it. Choose one coping action for any rating above zero.", "Aaj ki sab se strong urge ko 0 se 10 rate kar ke us waqt ka haal likhein. Zero se upar score ke liye aik coping action chunain.", 50, Icons.Default.Psychology)
    ),
    listOf(
        activity("social_boundary", "Set a Boundary", "Hadd Tay Karein", "Identify one invitation, contact, or setting that increases risk. Draft a polite no, mute, or leave-early response you can use.", "Aik dawat, contact ya jagah pehchanain jo risk barhati hai. Adab se mana, mute ya jaldi nikalne ka jawab tayar karein.", 60, Icons.Default.Groups),
        activity("safe_reward", "Healthy Reward", "Sehatmand Inaam", "Choose a small healthy reward after completing today's plan, such as tea, a show, or time with someone supportive. Keep it free of triggers.", "Aaj ka plan karne ke baad chhota sehatmand inaam chunain, jaise chai, show ya supportive shakhs ke sath waqt. Trigger se door rakhein.", 30, Icons.Default.EmojiEvents)
    ),
    listOf(
        activity("grounding_54321", "5-4-3-2-1 Grounding", "Grounding Mashq", "Pause and name five things you can see, four feel, three hear, two smell, and one taste or calming breath. Use it during stress today.", "Ruk kar paanch dekhi, chaar mehsoos, teen suni, do soonghi aur aik zaiqa ya pur-sukoon saans note karein. Aaj stress mein use karein.", 40, Icons.Default.Spa)
    ),
    listOf(
        activity("habit_swap", "Swap One Habit", "Aadat Badlein", "Pick one risky or unhelpful time slot today and schedule a replacement action there, such as a walk, prayer, tea, journaling, or a call.", "Aaj ka aik risky waqt chun kar us mein replacement kaam rakhein, jaise walk, dua, chai, journal ya call.", 60, Icons.AutoMirrored.Filled.TrendingUp),
        activity("phone_support", "Save Support Contacts", "Support Contacts", "Make sure one supportive contact and any local emergency option are easy to access on your phone. This is preparation, not a sign of failure.", "Phone mein aik supportive contact aur local emergency option asaani se milne wali jagah rakhein. Yeh tayyari hai, nakami nahi.", 35, Icons.Default.PhoneAndroid)
    ),
    listOf(
        activity("week_review", "Mini Progress Review", "Chhota Jaiza", "Review the last few days and write one pattern that helped and one risk pattern you noticed. Select one adjustment for tomorrow.", "Pichlay kuch din dekh kar aik madadgar pattern aur aik risky pattern likhein. Kal ke liye aik tabdeeli chunain.", 65, Icons.Default.Psychology)
    ),
    listOf(
        activity("sunlight_pause", "Outdoor Pause", "Bahar Ka Waqfa", "Spend ten safe minutes outdoors or near daylight if possible. Use the time to breathe slowly rather than checking feeds.", "Mumkin ho to das mehfooz minute bahar ya roshni ke qareeb guzarein. Feeds check karne ke bajaye ahista saans lein.", 35, Icons.Default.Spa),
        activity("affirmation", "Recovery Reminder", "Recovery Yaad Dahani", "Write one honest sentence reminding yourself why a setback or craving does not erase progress. Place it somewhere accessible.", "Aik sachcha jumla likhein jo yaad dilaye ke setback ya craving progress mita nahi deti. Isay asaan jagah rakhein.", 35, Icons.Default.Favorite)
    ),
    listOf(
        activity("problem_solve", "Solve One Stressor", "Aik Masla Hal Karein", "Choose one small current stressor. List the next practical step, when you will do it, and what support you may need.", "Aik chhota mojooda masla chunain. Agla amli qadam, us ka waqt aur zaroori madad likhein.", 60, Icons.Default.Lightbulb)
    ),
    listOf(
        activity("unfollow_trigger", "Reduce a Trigger", "Trigger Kam Karein", "Mute, unfollow, avoid, or remove one digital trigger that increases urges or harmful comparisons. Record which trigger you reduced.", "Aik digital trigger ko mute, unfollow, avoid ya remove karein jo urge ya nuqsandah comparison barhata hai. Likhein kya kam kiya.", 50, Icons.Default.PhoneAndroid),
        activity("calm_routine", "Calm Routine", "Sukoon Routine", "Choose a fifteen-minute routine for later today: shower, prayer, stretching, tea, reading, or breathing. Put a reminder in place.", "Aaj baad ke liye pandrah minute ki routine chunain: shower, dua, stretching, chai, parhna ya saans. Reminder laga dein.", 40, Icons.Default.Spa)
    ),
    listOf(
        activity("strength_list", "Strength List", "Apni Taqat", "List three strengths you have used during recovery, even imperfectly. For each, write one way to use it this week.", "Recovery mein use ki hui teen strengths likhein, chahe mukammal na hon. Har aik ko is haftay use karne ka aik tareeqa likhein.", 45, Icons.Default.EmojiEvents)
    ),
    listOf(
        activity("emergency_plan", "High-Urge Plan", "High-Urge Plan", "Prepare a three-step response for a high-risk moment: leave or remove access, contact support, and seek urgent help if safety is at risk.", "High-risk lamhay ke liye teen qadam tayar karein: access se door hona, support se rabta, aur safety risk par foran madad lena.", 70, Icons.Default.Warning),
        activity("night_reflection", "Night Reflection", "Raat Ka Jaiza", "Before sleep, note one difficult moment and one action that protected your recovery today. Keep the reflection brief and specific.", "Sone se pehle aik mushkil lamha aur aik action likhein jis ne aaj recovery ko protect kiya. Note chhota aur wazeh rakhein.", 35, Icons.Default.Bedtime)
    ),
    listOf(
        activity("connection_time", "Healthy Connection", "Sehatmand Rabta", "Spend at least ten minutes in a supportive conversation or shared activity. If nobody is available, write who you will contact tomorrow.", "Kam az kam das minute supportive guftagu ya shared activity mein guzarein. Koi na ho to kal kis se rabta karenge likhein.", 50, Icons.Default.Groups),
        activity("relapse_prevention", "Warning Signs", "Warning Signs", "Write three early warning signs that you may be moving toward risky behaviour and one response for each sign.", "Teen early warning signs likhein jo risky behavior ki taraf jane ka ishara hon aur har sign ka aik response likhein.", 65, Icons.Default.Psychology)
    ),
    listOf(
        activity("month_review", "30-Day Reflection", "30 Din Ka Jaiza", "Review this activity cycle: write one change you noticed, one activity worth repeating, and one support goal for the next cycle.", "Is activity cycle ka jaiza lein: aik tabdeeli, dobara karne wali aik activity, aur aglay cycle ka aik support goal likhein.", 80, Icons.Default.EmojiEvents),
        activity("rest_choice", "Rest Choice", "Araam Ka Faisla", "Schedule a reasonable rest or sleep window tonight. Avoid treating exhaustion as something to push through if it raises your risk.", "Aaj raat munasib araam ya neend ka waqt tay karein. Agar thakan risk barhati ho to usay nazarandaz na karein.", 35, Icons.Default.Bedtime)
    )
).also { plans ->
    require(plans.size == 30 && plans.all { it.size in 1..2 }) {
        "Recovery activity cycle must contain 30 days with one or two activities per day."
    }
}

private fun recoveryCycleDay(date: String): Int = runCatching {
    Math.floorMod(LocalDate.parse(date).toEpochDay(), recoveryActivityCycle.size.toLong()).toInt() + 1
}.getOrDefault(1)

private fun tasksForDate(date: String, completedToday: Set<String>): List<MilestoneTask> {
    val cycleDay = recoveryCycleDay(date)
    val dailyCheckIn = MilestoneTask(
        id = "daily_check_in",
        titleEn = "Daily Check-in",
        titleUr = "Rozana Check-in",
        descEn = "Complete today's AI chat check-in from Dashboard or Chat, then claim your recovery XP here.",
        descUr = "Dashboard ya Chat mein aaj ka AI check-in complete karein, phir yahan recovery XP claim karein.",
        xp = 10,
        icon = Icons.Default.CheckCircle,
        isComplete = "daily_check_in" in completedToday,
        requiresAiCheckIn = true
    )
    return listOf(dailyCheckIn) + recoveryActivityCycle[cycleDay - 1].map { scheduled ->
        val taskId = "cycle_${cycleDay.toString().padStart(2, '0')}_${scheduled.id}"
        MilestoneTask(
            id = taskId,
            titleEn = scheduled.titleEn,
            titleUr = scheduled.titleUr,
            descEn = scheduled.descEn,
            descUr = scheduled.descUr,
            xp = scheduled.xp,
            icon = scheduled.icon,
            isComplete = taskId in completedToday
        )
    }
}

private fun Any?.asRecoveryInt(default: Int = 0): Int = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull() ?: default
    else -> default
}



@Composable
fun GameRecoveryScreen(
    navController  : NavController,
    onNavigateBack : () -> Unit = { navController.popBackStack() },
    isEnglish      : Boolean    = false,
    gameViewModel: GameRecoveryViewModel = viewModel()
) {
    val isDark    = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    val context   = LocalContext.current
    val uid = gameViewModel.uid
    LaunchedEffect(Unit) { gameViewModel.initialize() }

    
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    val thisWeek = remember {
        val cal = Calendar.getInstance()
        "W${cal.get(Calendar.WEEK_OF_YEAR)}"
    }

    
    var tempAlias by remember { mutableStateOf(GlobalAppState.anonymousUsername.ifEmpty { generateDesiAlias() }) }

    
    var totalXp        by remember { mutableIntStateOf(0) }
    var dailyXp        by remember { mutableIntStateOf(0) }
    var weeklyXp       by remember { mutableIntStateOf(0) }
    var level          by remember { mutableIntStateOf(1) }
    var completedToday by remember { mutableStateOf(emptySet<String>()) }
    var isLoadingProfile     by remember { mutableStateOf(true) }
    var isLoadingLeaderboard by remember { mutableStateOf(true) }

    var dailyBoard   by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var weeklyBoard  by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var allTimeBoard by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var myRank       by remember { mutableIntStateOf(0) }   

    
    var loadingTaskId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var isStartingRecovery by remember { mutableStateOf(false) }
    var pendingRecoveryStart by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }

    
    fun Map<String, Any>.toUser(rank: Int, xpField: String): LeaderboardUser = LeaderboardUser(
        username      = this["alias"]?.toString()    ?: "Anonymous",
        location      = this["location"]?.toString() ?: "Unknown",
        xp            = this[xpField].asRecoveryInt(),
        rank          = rank,
        isCurrentUser = this["uid"]?.toString() == uid
    )

    fun applyGameProfile(profile: Map<String, Any>) {
        totalXp = profile["totalXp"].asRecoveryInt()
        dailyXp = profile["dailyXp"].asRecoveryInt()
        weeklyXp = profile["weeklyXp"].asRecoveryInt()
        level = profile["level"].asRecoveryInt(default = 1)
        profile["alias"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            GlobalAppState.anonymousUsername = it
        }
        profile["location"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            GlobalAppState.userLocation = it
            GlobalAppState.hasGrantedLocation = true
        }
        @Suppress("UNCHECKED_CAST")
        completedToday = profile["completedToday"] as? Set<String> ?: emptySet()
    }

    
    LaunchedEffect(uid) {
        if (uid.isBlank()) {
            isLoadingProfile     = false
            isLoadingLeaderboard = false
            return@LaunchedEffect
        }

        gameViewModel.loadGameData(
            today,
            thisWeek,
            onProfileLoaded = { profile ->
                if (profile != null) {
                    applyGameProfile(profile)
                }
                isLoadingProfile = false
            },
            onLeaderboardsLoaded = { daily, weekly, allTime ->
                dailyBoard = daily.mapIndexed { i, entry -> entry.toUser(i + 1, "dailyXp") }
                weeklyBoard = weekly.mapIndexed { i, entry -> entry.toUser(i + 1, "weeklyXp") }
                allTimeBoard = allTime.mapIndexed { i, entry -> entry.toUser(i + 1, "totalXp") }
                myRank = allTimeBoard.indexOfFirst { it.isCurrentUser }.let { if (it >= 0) it + 1 else 0 }
                isLoadingLeaderboard = false
            }
        )
    }

    
    val cycleDay = remember(today) { recoveryCycleDay(today) }
    val dailyTasks = remember(today, completedToday) { tasksForDate(today, completedToday) }
    val selectedTask = dailyTasks.firstOrNull { it.id == selectedTaskId }

    
    val xpPerLevel     = 200
    val levelProgress  = (totalXp % xpPerLevel).toFloat() / xpPerLevel
    val xpToNextLevel  = xpPerLevel - (totalXp % xpPerLevel)

    fun startRecoveryWithLockedAlias() {
        if (isStartingRecovery) return
        if (!NetworkUtils.isNetworkAvailable(context)) {
            pendingRecoveryStart = false
            context.showLocalizedToast(
                isEnglish,
                "Internet connection is required to start Game Recovery.",
                "Game Recovery shuru karne ke liye internet zaroori hai.",
                Toast.LENGTH_LONG,
            )
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gps) {
            context.showLocalizedToast(
                isEnglish,
                "Please turn on GPS in settings.",
                "Please settings mein GPS on karein.",
                Toast.LENGTH_LONG,
            )
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        pendingRecoveryStart = false
        isStartingRecovery = true
        fetchRealLocation(context) { realLoc ->
            GlobalAppState.userLocation = realLoc
            gameViewModel.startRecovery(tempAlias, realLoc) { result ->
                isStartingRecovery = false
                result.onSuccess { profile ->
                    applyGameProfile(profile)
                    NotificationManager.logUsername(profile["alias"]?.toString().orEmpty().ifBlank { tempAlias })
                }.onFailure { error ->
                    context.showLocalizedToast(
                        isEnglish,
                        error.message ?: "Could not start Game Recovery. Try again.",
                        error.message ?: "Game Recovery shuru nahi ho saki. Dobara koshish karein.",
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }
    }

    
    val locationPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Location permission is required for Game Based Recovery leaderboards.",
            deniedUr = "Game Based Recovery leaderboards ke liye location ki ijazat chahiye.",
            settingsEn = "Enable location permission in App settings to join local leaderboards.",
            settingsUr = "Local leaderboards join karne ke liye App settings mein location ki ijazat dein.",
        ),
        onGranted = {
            locationGranted = true
            if (pendingRecoveryStart) startRecoveryWithLockedAlias()
        },
        onDenied = {
            locationGranted = false
            pendingRecoveryStart = false
        },
    )

    fun onStartRecoveryClick() {
        if (locationGranted || locationPermissionRequester.hasPermission()) {
            startRecoveryWithLockedAlias()
        } else {
            pendingRecoveryStart = true
            locationPermissionRequester.request()
        }
    }

    val finalAlias    = GlobalAppState.anonymousUsername.ifEmpty { tempAlias }
    val finalLocation = GlobalAppState.userLocation.ifEmpty { "Locating…" }
    val hasRecoveryAccess = GlobalAppState.anonymousUsername.isNotBlank()
    ObservePermissionState(locationPermissionRequester) { granted ->
        locationGranted = granted
        if (granted && pendingRecoveryStart) startRecoveryWithLockedAlias()
    }
    val displayRank   = if (myRank > 0) "#$myRank" else if (!isLoadingLeaderboard) "—" else "…"

    
    val bgGradient = if (isDark)
        listOf(SaharaStrongGreen.copy(0.2f), MaterialTheme.colorScheme.background.copy(0.6f), MaterialTheme.colorScheme.background)
    else
        listOf(SaharaStrongGreen.copy(0.25f), SaharaSky.copy(0.15f), MaterialTheme.colorScheme.background.copy(0.2f))

    val blobMotion = rememberBackdropBlobMotion()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        ) {
            Box(Modifier.size(350.dp).offset((-80).dp, (-50).dp).primaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaStrongGreen.copy(if (isDark) 0.25f else 0.15f), Color.Transparent))))
            Box(Modifier.size(400.dp).align(Alignment.BottomEnd).offset(100.dp, 50.dp).secondaryBlobMotion(blobMotion)
                .background(Brush.radialGradient(listOf(SaharaSky.copy(if (isDark) 0.2f else 0.18f), Color.Transparent))))
        }

        Scaffold(
        bottomBar        = { BottomNav(navController = navController, hazeState = hazeState) },
        containerColor   = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {

            
            AnimatedVisibility(
                visible = !hasRecoveryAccess,
                enter   = fadeIn(),
                exit    = fadeOut(tween(500)) + scaleOut(targetScale = 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(80.dp).background(SaharaSky.copy(0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = SaharaSky, modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text       = if (isEnglish) "Local Leaderboards" else "Muqami Leaderboards",
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface,
                                textAlign  = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text  = if (isEnglish)
                                    "Start once to join your regional arena. Your anonymous name is checked online and locked permanently."
                                else
                                    "Regional arena join karne ke liye shuru karein. Anonymous naam online check hoga aur phir hamesha ke liye lock ho jayega.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))

                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SaharaStrongGreen.copy(0.05f))
                                    .border(1.dp, SaharaStrongGreen.copy(0.2f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text  = if (isEnglish) "Your Anonymous Alias:" else "Aapka Anonymous Naam:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text       = tempAlias,
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color      = SaharaStrongGreen,
                                            maxLines   = 1,
                                            modifier   = Modifier.basicMarquee()
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    IconButton(
                                        onClick  = { tempAlias = generateDesiAlias() },
                                        enabled = !isStartingRecovery,
                                        modifier = Modifier.background(SaharaStrongGreen.copy(0.1f), CircleShape).size(44.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, "Randomize", tint = SaharaStrongGreen, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (isEnglish) {
                                    "This name cannot be changed after you start."
                                } else {
                                    "Shuru karne ke baad yeh naam badla nahi ja sakega."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SaharaWarning,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(32.dp))

                            SaharaButton(
                                text      = if (isStartingRecovery) {
                                    if (isEnglish) "Starting..." else "Shuru ho raha hai..."
                                } else {
                                    if (isEnglish) "Start" else "Shuru Karein"
                                },
                                onClick   = { onStartRecoveryClick() },
                                variant   = ButtonVariant.DEFAULT,
                                enabled = !isStartingRecovery,
                                modifier  = Modifier.fillMaxWidth().height(54.dp),
                                isEnglish = isEnglish
                            )
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = onNavigateBack) {
                                Text(if (isEnglish) "Not Now" else "Abhi Nahi",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            
            AnimatedVisibility(
                visible = hasRecoveryAccess,
                enter   = fadeIn(tween(500, delayMillis = 300)) + slideInVertically { 50 },
                exit    = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(24.dp))

                    
                    Row(
                        modifier             = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment    = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HazeBackButton(onClick = onNavigateBack, hazeState = hazeState)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text       = if (isEnglish) "Game Based Recovery" else "Game Recovery",
                                style      = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color      = SaharaStrongGreen
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    
                    SaharaCard(
                        variant  = CardVariant.DASHBOARD_GLASS,
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.95f }
                    ) {
                        if (isLoadingProfile) {
                            Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                                CircularProgressIndicator(color = SaharaStrongGreen, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    
                                    if (myRank > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(SaharaSky.copy(if (isDark) 0.3f else 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayRank,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SaharaSky
                                            )
                                        }
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text       = finalAlias,
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.onSurface,
                                            modifier   = Modifier.basicMarquee(Int.MAX_VALUE)
                                        )
                                        Text(
                                            text  = if (isEnglish) "from $finalLocation" else "$finalLocation se",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "$totalXp XP",
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color      = SaharaStrongGreen
                                        )
                                        Text(
                                            if (isEnglish) "Level $level" else "Level $level",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(20.dp))

                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Level $level",
                                        style      = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (isEnglish) "$xpToNextLevel XP to Level ${level + 1}"
                                        else           "Level ${level + 1} ke liye $xpToNextLevel XP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress   = { levelProgress.coerceIn(0.02f, 1f) },
                                    modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color      = SaharaSky,
                                    trackColor = SaharaSky.copy(0.2f)
                                )

                                
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MiniXpBadge(
                                        modifier  = Modifier.weight(1f),
                                        label     = if (isEnglish) "Today" else "Aaj",
                                        xp        = dailyXp,
                                        color     = SaharaWarning,
                                        isDark    = isDark
                                    )
                                    MiniXpBadge(
                                        modifier  = Modifier.weight(1f),
                                        label     = if (isEnglish) "This Week" else "Hafte Mein",
                                        xp        = weeklyXp,
                                        color     = SaharaSky,
                                        isDark    = isDark
                                    )
                                    MiniXpBadge(
                                        modifier  = Modifier.weight(1f),
                                        label     = if (isEnglish) "All Time" else "Hamesha",
                                        xp        = totalXp,
                                        color     = SaharaStrongGreen,
                                        isDark    = isDark
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(36.dp))

                    
                    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 3 })

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.Bottom
                    ) {
                        Text(
                            text       = if (isEnglish) "Top Warriors" else "Top Warriors",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            modifier   = Modifier.padding(start = 8.dp)
                        )
                        Text(
                            text  = when (pagerState.currentPage) {
                                0 -> if (isEnglish) "All Time" else "Hamesha"
                                1 -> if (isEnglish) "Weekly"   else "Hafta War"
                                else -> if (isEnglish) "Daily"  else "Rozana"
                            },
                            style      = MaterialTheme.typography.labelSmall,
                            color      = SaharaStrongGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (isLoadingLeaderboard) {
                        Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                            CircularProgressIndicator(color = SaharaStrongGreen, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                            val activeList = when (page) {
                                0    -> allTimeBoard
                                1    -> weeklyBoard
                                else -> dailyBoard
                            }
                            SaharaCard(variant = CardVariant.DASHBOARD_GLASS, modifier = Modifier.fillMaxWidth()) {
                                if (activeList.isEmpty()) {
                                    Text(
                                        text      = if (isEnglish) "No warriors yet — be the first!"
                                                    else           "Abhi koi warrior nahi - pehle banein!",
                                        modifier  = Modifier.fillMaxWidth().padding(24.dp),
                                        textAlign = TextAlign.Center,
                                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style     = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        activeList.forEachIndexed { index, user ->
                                            LeaderboardRow(
                                                user      = user,
                                                isDark    = isDark,
                                                isEnglish = isEnglish
                                            )
                                            if (index < activeList.size - 1)
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f))
                                        }
                                    }
                                }
                            }
                        }

                        
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            repeat(3) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (isSelected) 14.dp else 10.dp)
                                        .background(
                                            if (isSelected) SaharaStrongGreen
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                                            RoundedCornerShape(topStartPercent = 100, bottomEndPercent = 100)
                                        )
                                        .rotate(45f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(36.dp))

                    
                    Text(
                        text       = if (isEnglish) "Daily Milestones" else "Rozana ke Ahdaaf",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        modifier   = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = if (isEnglish)
                            "Day $cycleDay of 30 - tap an activity for instructions and XP"
                        else
                            "30 mein se din $cycleDay - hidayat aur XP ke liye activity tap karein",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                    )

                    dailyTasks.forEach { task ->
                        val isThisLoading = loadingTaskId == task.id
                        val iconColor     = when {
                            task.isComplete   -> SaharaStrongGreen
                            isThisLoading     -> SaharaWarning
                            else              -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        SaharaCard(
                            variant  = CardVariant.DASHBOARD_GLASS,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .then(
                                    if (!isThisLoading)
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) {
                                            selectedTaskId = task.id
                                        }
                                    else Modifier
                                )
                        ) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier         = Modifier.size(40.dp).background(iconColor.copy(0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isThisLoading) {
                                            CircularProgressIndicator(
                                                color    = SaharaWarning,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(task.icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text       = if (isEnglish) task.titleEn else task.titleUr,
                                            style      = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color      = if (task.isComplete)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier   = Modifier.basicMarquee(Int.MAX_VALUE)
                                        )
                                        Text(
                                            text     = if (isEnglish) task.descEn else task.descUr,
                                            style    = MaterialTheme.typography.bodySmall,
                                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                if (task.isComplete) {
                                    Icon(Icons.Default.CheckCircle, "Done",
                                        tint = SaharaStrongGreen, modifier = Modifier.size(24.dp))
                                } else {
                                    Surface(shape = RoundedCornerShape(8.dp), color = SaharaSky.copy(0.15f)) {
                                        Text(
                                            text       = "+${task.xp} XP",
                                            style      = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color      = SaharaSky,
                                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(innerPadding.calculateBottomPadding() + 40.dp))
                }
            }
        }
        }

        selectedTask?.let { task ->
            val isThisLoading = loadingTaskId == task.id
            GlassAlertDialog(
                hazeState = hazeState,
                isDark = isDark,
                onDismissRequest = {
                    if (!isThisLoading) selectedTaskId = null
                },
                icon = {
                    Icon(
                        task.icon,
                        contentDescription = null,
                        tint = if (task.isComplete) SaharaStrongGreen else SaharaSky
                    )
                },
                title = {
                    Text(
                        text = if (isEnglish) task.titleEn else task.titleUr,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isEnglish) task.descEn else task.descUr,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (task.isComplete) {
                                SaharaStrongGreen.copy(alpha = 0.12f)
                            } else {
                                SaharaSky.copy(alpha = 0.12f)
                            }
                        ) {
                            Text(
                                text = if (task.isComplete) {
                                    if (isEnglish) "Completed today" else "Aaj complete ho gaya"
                                } else {
                                    if (isEnglish) "Reward: +${task.xp} XP" else "Inaam: +${task.xp} XP"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (task.isComplete) SaharaStrongGreen else SaharaSky,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!task.isComplete) {
                        Button(
                            onClick = {
                                if (task.requiresAiCheckIn && !GlobalAppState.hasCheckedIn) {
                                    selectedTaskId = null
                                    navController.navigate("chat") { launchSingleTop = true }
                                    return@Button
                                }
                                loadingTaskId = task.id
                                gameViewModel.completeTask(task.id, task.xp, today, thisWeek) { updated ->
                                    if (updated != null) {
                                        totalXp = updated["totalXp"].asRecoveryInt(totalXp)
                                        dailyXp = updated["dailyXp"].asRecoveryInt(dailyXp)
                                        weeklyXp = updated["weeklyXp"].asRecoveryInt(weeklyXp)
                                        level = updated["level"].asRecoveryInt(level)
                                        @Suppress("UNCHECKED_CAST")
                                        completedToday = updated["completedToday"] as? Set<String> ?: completedToday
                                        selectedTaskId = null
                                    } else {
                                        context.showLocalizedToast(
                                            isEnglish,
                                            "Unable to complete task. Try again.",
                                            "Task complete nahi hua. Dobara koshish karein.",
                                        )
                                    }
                                    loadingTaskId = null
                                }
                            },
                            enabled = !isThisLoading
                        ) {
                            if (isThisLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    if (task.requiresAiCheckIn && !GlobalAppState.hasCheckedIn) {
                                        if (isEnglish) "Open Check-in" else "Check-in Kholein"
                                    } else {
                                        if (isEnglish) "Complete Task" else "Task Complete"
                                    }
                                )
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { selectedTaskId = null },
                        enabled = !isThisLoading
                    ) {
                        Text(if (isEnglish) "Close" else "Band Karein")
                    }
                }
            )
        }
    }
}



@Composable
private fun MiniXpBadge(
    modifier : Modifier,
    label    : String,
    xp       : Int,
    color    : Color,
    isDark   : Boolean
) {
    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(if (isDark) 0.18f else 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = "$xp",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = color,
                fontSize   = 16.sp
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(0.75f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    user      : LeaderboardUser,
    isDark    : Boolean,
    isEnglish : Boolean
) {
    val rankColor = when (user.rank) {
        1    -> Color(0xFFFFD700)
        2    -> Color(0xFFC0C0C0)
        3    -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgTint = if (user.isCurrentUser)
        SaharaStrongGreen.copy(if (isDark) 0.15f else 0.08f)
    else Color.Transparent

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(bgTint)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        
        if (user.rank <= 3) {
            Icon(
                Icons.Default.EmojiEvents, "Trophy",
                tint     = rankColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Text(
                text       = "#${user.rank}",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = rankColor,
                modifier   = Modifier.width(32.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = user.username,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = if (user.isCurrentUser) SaharaStrongGreen
                                 else MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.basicMarquee(Int.MAX_VALUE)
                )
                if (user.isCurrentUser) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = SaharaStrongGreen.copy(0.15f)) {
                        Text(
                            text     = if (isEnglish) "You" else "Aap",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = SaharaStrongGreen,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text  = user.location,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text       = "${user.xp} XP",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = if (user.isCurrentUser) SaharaStrongGreen
                         else MaterialTheme.colorScheme.onSurface
        )
    }
}
