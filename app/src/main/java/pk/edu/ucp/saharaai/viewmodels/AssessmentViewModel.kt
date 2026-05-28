package pk.edu.ucp.saharaai.viewmodels

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import pk.edu.ucp.saharaai.ASSESSMENT_VALIDITY_MS
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_EVER_COMPLETED
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_SCORE
import pk.edu.ucp.saharaai.KEY_ASSESSMENT_TIMESTAMP
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.RiskProfileRepository
import pk.edu.ucp.saharaai.ui.screens.GlobalAppState
import pk.edu.ucp.saharaai.utils.NotificationManager

data class QuizQuestion(
    val textEn: String,
    val textUr: String,
    val isReverseScored: Boolean = false
)

enum class QuizType { DAST10, DAST20, YOUTH_ASSESSMENT }

class AssessmentViewModel : ViewModel() {
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    fun clearSaveError() {
        saveError = null
    }

    fun restoreLatestAssessment(onRestored: () -> Unit) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val latest = RealtimeDBService.loadLatestAssessment(uid) ?: return@launch
            val score = (latest["score"] as? Int) ?: (latest["score"] as? Long)?.toInt() ?: return@launch
            val timestamp = (latest["timestamp"] as? Long) ?: 0L
            val ageMs = if (timestamp > 0) System.currentTimeMillis() - timestamp else 0L
            if (timestamp == 0L || ageMs <= ASSESSMENT_VALIDITY_MS) {
                GlobalAppState.dast10Score = score
                GlobalAppState.lastAssessmentTimestamp = timestamp
                GlobalAppState.hasCompletedInitialAssessment = true
                GlobalAppState.hasEverCompletedAssessment = true
                onRestored()
            } else {
                GlobalAppState.hasCompletedInitialAssessment = false
                GlobalAppState.hasEverCompletedAssessment = true
                GlobalAppState.dast10Score = score
                GlobalAppState.lastAssessmentTimestamp = timestamp
            }
        }
    }

    fun saveResult(
        context: Context,
        quizType: String,
        score: Int,
        riskLevel: String,
        date: String,
        answers: Map<Int, Triple<String, String, Boolean>>,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch {
            isSaving = true
            saveError = null
            val uid = Firebase.auth.currentUser?.uid
            val timestamp = System.currentTimeMillis()
            if (!uid.isNullOrBlank()) {
                RealtimeDBService.saveAssessment(uid, quizType, score, riskLevel, date, answers)
                    .onFailure { saveError = it.message }
                RiskProfileRepository.registerAssessmentCycle(score, timestamp)
                    .onFailure { saveError = it.message ?: "Could not start monitoring cycle." }
            }
            context.applicationContext.getSharedPreferences("sahara_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ASSESSMENT_SCORE, score)
                .putLong(KEY_ASSESSMENT_TIMESTAMP, timestamp)
                .putBoolean(KEY_ASSESSMENT_EVER_COMPLETED, true)
                .apply()
            NotificationManager.logAssessment(
                oldScore = GlobalAppState.dast10Score,
                newScore = score
            )
            GlobalAppState.dast10Score = score
            GlobalAppState.lastAssessmentTimestamp = timestamp
            GlobalAppState.hasCompletedInitialAssessment = true
            GlobalAppState.hasEverCompletedAssessment = true
            isSaving = false
            onComplete(score)
        }
    }

    val dast10Questions = listOf(
        QuizQuestion("Have you used drugs other than for medical reasons?", "Kya aapne medical zaroorat ke bina koi drugs use kiye hain?"),
        QuizQuestion("Do you use more than one drug at a time?", "Kya aap ek time mein ek se zyada drugs use karte hain?"),
        QuizQuestion("Are you able to stop using drugs when you want to?", "Kya aap jab chahein drugs chhor sakte hain?", isReverseScored = true),
        QuizQuestion("Have you had blackouts or flashbacks from drug use?", "Kya drugs ki wajah se aapko blackouts (kuch yaad na aana) hue hain?"),
        QuizQuestion("Do you ever feel bad or guilty about your drug use?", "Kya aapko drugs use karne par bura ya guilty feel hota hai?"),
        QuizQuestion("Does your family ever complain about your drug use?", "Kya aapki family kabhi aapke drugs use karne par gussa ya shikayat karti hai?"),
        QuizQuestion("Have you neglected your family because of drugs?", "Kya drugs ki wajah se aapne apni family ko ignore kiya hai?"),
        QuizQuestion("Have you done illegal things to get drugs?", "Kya drugs lene ke liye aapne koi illegal (ghair-qanooni) kaam kiya hai?"),
        QuizQuestion("Have you felt sick (withdrawals) when you stopped drugs?", "Kya drugs rokne par aapko beemari ya takleef (withdrawals) feel hui?"),
        QuizQuestion("Have you had medical problems because of drugs?", "Kya drugs ki wajah se aapko koi medical masla hua hai?")
    )

    val dast20Questions = listOf(
        QuizQuestion("Have you used drugs other than for medical reasons?", "Kya aapne medical zaroorat ke bina drugs use kiye hain?"),
        QuizQuestion("Have you used controlled pills without medical authorization?", "Kya aapne doctor ki ijazat ke bina controlled goliyan use ki hain?"),
        QuizQuestion("Do you use more than one drug at a time?", "Kya aap ek waqt mein 1 se zyada drugs use karte hain?"),
        QuizQuestion("Can you get through the week without using drugs?", "Kya aap drugs ke bina poora hafta guzaar sakte hain?", isReverseScored = true),
        QuizQuestion("Are you always able to stop using drugs when you want to?", "Kya aap jab chahein drugs rok sakte hain?", isReverseScored = true),
        QuizQuestion("Have you had blackouts or flashbacks from drug use?", "Kya drugs ki wajah se aapko blackouts hue hain?"),
        QuizQuestion("Do you ever feel bad or guilty about your drug use?", "Kya aapko drugs lene par guilty feel hota hai?"),
        QuizQuestion("Does your spouse or parents complain about your drugs?", "Kya aapke parents ya partner drugs par shikayat karte hain?"),
        QuizQuestion("Has drug use created problems with your partner or parents?", "Kya drugs ki wajah se parents ya partner ke sath maslay hue?"),
        QuizQuestion("Have you lost friends because of your drug use?", "Kya drugs ki wajah se aapke dost aapse door hue?"),
        QuizQuestion("Have you neglected your family because of drugs?", "Kya drugs ki wajah se aapne apni family ko ignore kiya hai?"),
        QuizQuestion("Have you been in trouble at work because of drug use?", "Kya kaam ya job par drugs ki wajah se koi masla hua?"),
        QuizQuestion("Have you lost a job because of drug use?", "Kya drugs ki wajah se aapki job chhooti?"),
        QuizQuestion("Have you gotten into fights when under the influence?", "Nashe ki halat mein kya aapki laraian (fights) hui hain?"),
        QuizQuestion("Have you done illegal things to get drugs?", "Kya drugs lene ke liye aapne koi illegal kaam kiya hai?"),
        QuizQuestion("Have you been arrested for possession of illegal drugs?", "Kya drugs rakhne ki wajah se aap kabhi arrest hue hain?"),
        QuizQuestion("Have you ever experienced withdrawal symptoms (felt sick)?", "Kya drugs rokne par aap beemar (withdrawals) feel karte hain?"),
        QuizQuestion("Have you had medical problems because of drugs?", "Kya drugs ki wajah se aapko koi medical problem hui hai?"),
        QuizQuestion("Have you gone to anyone for help for a drug problem?", "Kya aapne kabhi drugs chhorne ke liye kisi se help mangi hai?"),
        QuizQuestion("Have you been involved in a treatment program specifically related to drug use?", "Kya aap kisi drug rehab ya treatment program ka hissa rahe hain?")
    )

    val youthQuestions = listOf(
        QuizQuestion("Have you used drugs other than those required for medical reasons?", "Kya aapne medical zaroorat ke ilawa koi drugs use kiye hain?"),
        QuizQuestion("Have you used controlled pills without medical authorization?", "Kya aapne doctor ki ijazat ke bina controlled goliyan use ki hain?"),
        QuizQuestion("Do you use more than one drug at a time?", "Kya aap ek waqt mein ek se zyada drugs use karte hain?"),
        QuizQuestion("Can you get through the week without using drugs?", "Kya aap poora hafta drugs ke bina guzaar sakte hain?", isReverseScored = true),
        QuizQuestion("Are you always able to stop using drugs when you want to?", "Kya aap jab chahein drugs rok sakte hain?", isReverseScored = true),
        QuizQuestion("Have you had \"blackouts\" or \"flashbacks\" as a result or drug use?", "Kya drugs ki wajah se aapko blackouts (kuch yaad na rehna) ya flashbacks hue hain?"),
        QuizQuestion("Do you every feel bad or guilty about your drug use?", "Kya aapko drugs use karne par bura ya guilty feel hota hai?"),
        QuizQuestion("Do your parents ever complain about your involvement with drugs?", "Kya aapke parents aapke drugs use karne par shikayat karte hain?"),
        QuizQuestion("Has drug use created problems between you and your parents?", "Kya drugs ki wajah se aapka aur aapke parents ka koi masla hua hai?"),
        QuizQuestion("Have you lost friends because of your use of drugs?", "Kya drugs ki wajah se aapke dost aapse door hue hain?"),
        QuizQuestion("Have you neglected your family because of your use of drugs?", "Kya drugs ki wajah se aapne apni family ko ignore kiya hai?"),
        QuizQuestion("Have you been in trouble at school because of drug use?", "Kya school mein drugs ki wajah se aapko kisi pareshani ka saamna karna para?"),
        QuizQuestion("Have you missed school assignments because of drug use?", "Kya drugs ki wajah se aapke school assignments reh gaye hain?"),
        QuizQuestion("Have you gotten into fights when under the influence of drugs?", "Nashe ki halat mein kya aapki laraian (fights) hui hain?"),
        QuizQuestion("Have you engaged in illegal activities in order to obtain drugs?", "Kya drugs lene ke liye aapne koi illegal (ghair-qanooni) kaam kiya hai?"),
        QuizQuestion("Have you been arrested for possession of illegal drugs?", "Kya drugs rakhne ki wajah se aap kabhi arrest hue hain?"),
        QuizQuestion("Have you ever experienced withdrawal symptoms (felt sick) when you stopped taking drugs?", "Kya drugs rokne par aapko beemari ya takleef (withdrawals) feel hoti hai?"),
        QuizQuestion("Have you had medical problems as a result of your drug use (e.g. memory loss, hepatitis, convulsions, bleeding, etc.)?", "Kya drugs ki wajah se aapko koi medical masla (maslan memory loss, hepatitis) hua hai?"),
        QuizQuestion("Have you gone to anyone for help for drug problem?", "Kya aapne kabhi drugs chhorne ke liye kisi se help mangi hai?"),
        QuizQuestion("Have you been involved in a treatment program specifically related to drug use?", "Kya aap kisi drug treatment ya rehab program ka hissa rahe hain?")
    )

    var selectedQuiz by mutableStateOf<List<QuizQuestion>?>(null)
        private set

    var currentQ by mutableIntStateOf(0)
        private set

    val answers = mutableStateMapOf<Int, Boolean>()

    fun selectQuiz(type: QuizType) {
        selectedQuiz = when (type) {
            QuizType.DAST10 -> dast10Questions
            QuizType.DAST20 -> dast20Questions
            QuizType.YOUTH_ASSESSMENT -> youthQuestions
        }
        currentQ = 0
        answers.clear()
    }

    fun nextQuestion() {
        selectedQuiz?.let {
            if (currentQ < it.size - 1) {
                currentQ++
            }
        }
    }

    fun previousQuestion() {
        if (currentQ > 0) {
            currentQ--
        }
    }

    fun onAnswer(index: Int, isYes: Boolean) {
        answers[index] = isYes
    }

    fun completeAssessment(onComplete: (Int) -> Unit) {
        val questions = selectedQuiz ?: return
        var score = 0
        answers.forEach { (index, isYes) ->
            val q = questions[index]
            if (q.isReverseScored) {
                if (!isYes) score++
            } else {
                if (isYes) score++
            }
        }

        val normalizedScore = if (questions.size > 10) {
            when (score) {
                0 -> 0
                in 1..5 -> 1
                in 6..10 -> 3
                in 11..15 -> 6
                else -> 9
            }
        } else {
            score
        }

        NotificationManager.logAssessment(
            oldScore = GlobalAppState.dast10Score,
            newScore = normalizedScore
        )

        GlobalAppState.dast10Score = normalizedScore
        GlobalAppState.hasCompletedInitialAssessment = true
        GlobalAppState.hasEverCompletedAssessment = true
        onComplete(normalizedScore)
    }
}
