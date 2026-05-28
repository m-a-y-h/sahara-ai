package pk.edu.ucp.saharaai.util

object Constants {

    const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    const val GEMINI_MODEL = "gemini-1.5-flash"

    const val COLLECTION_USERS = "users"
    const val COLLECTION_ASSESSMENTS = "assessments"
    const val COLLECTION_CHAT_MESSAGES = "chat_messages"
    const val COLLECTION_JOURNAL_ENTRIES = "journal_entries"
    const val COLLECTION_SLEEP_ENTRIES = "sleep_entries"
    const val COLLECTION_RISK_SCORES = "risk_scores"
    const val COLLECTION_COUNSELORS = "counselors"
    const val COLLECTION_NOTIFICATIONS = "notifications"
    const val COLLECTION_PROGRESS_STATS = "progress_stats"
    const val COLLECTION_SESSIONS = "sessions"

    const val RISK_LOW_MAX = 25.0f
    const val RISK_MEDIUM_MAX = 50.0f
    const val RISK_HIGH_MAX = 75.0f

    const val ASSESSMENT_DAST10 = "DAST10"
    const val ASSESSMENT_DAST20 = "DAST20"
    const val ASSESSMENT_YOUTH = "YOUTH"

    const val ROLE_PATIENT = "PATIENT"
    const val ROLE_COUNSELOR = "COUNSELOR"
    const val ROLE_NGO = "NGO"

    const val VOICE_MIN_DURATION_SECONDS = 30.0f
    const val VOICE_MAX_DURATION_SECONDS = 60.0f

    const val MESSAGE_MAX_LENGTH = 500
    const val MESSAGE_MIN_LENGTH = 1

    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 50

    const val PREF_USER_ID = "user_id"
    const val PREF_USER_ROLE = "user_role"
    const val PREF_LANGUAGE_ENGLISH = "language_english"
    const val PREF_JWT_TOKEN = "jwt_token"

    const val NOTIFICATION_CHANNEL_CRISIS = "channel_crisis"
    const val NOTIFICATION_CHANNEL_GENERAL = "channel_general"
    const val NOTIFICATION_CHANNEL_COUNSELOR = "channel_counselor"

    const val AI_SYSTEM_PROMPT_EN =
        "You are SAHARA AI, a compassionate mental health and addiction recovery support chatbot " +
        "for Pakistani youth. Respond in English. Focus on emotional support, coping strategies, " +
        "and early intervention. Never provide medical diagnoses."

    const val AI_SYSTEM_PROMPT_UR =
        "You are SAHARA AI, a compassionate mental health and addiction recovery support chatbot " +
        "for Pakistani youth. Respond in Urdu. Focus on emotional support, coping strategies, " +
        "and early intervention. Never provide medical diagnoses."

    const val HELPLINE_UMANG = "0317-4288665"
    const val HELPLINE_ROZAN = "051-2890505"
    const val HELPLINE_EDHI = "115"
    const val HELPLINE_RESCUE = "1122"
    const val HELPLINE_POLICE = "15"
}
