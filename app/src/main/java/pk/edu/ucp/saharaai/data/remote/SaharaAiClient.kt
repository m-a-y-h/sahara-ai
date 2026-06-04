package pk.edu.ucp.saharaai.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object SaharaAiClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS    = 45_000
    private const val MAX_USER_INPUT_CHARS = 900

    private val gson = Gson()

    data class ChatRequest(
        @SerializedName("user_input") val userInput: String,
        @SerializedName("language")   val language: String?  = null,
    )

    data class ChatResponse(
        @SerializedName("reply")               val reply: String?               = null,
        @SerializedName("trigger_counselor")   val triggerCounselor: Boolean?   = null,
        @SerializedName("substance_detected")  val substanceDetected: String?   = null,
        @SerializedName("substances_detected") val substancesDetected: List<String>? = null,
        @SerializedName("risk_level")          val riskLevel: String?           = null,
        @SerializedName("message_type")        val messageType: String?         = null,
        @SerializedName("action_destination")  val actionDestination: String?   = null,
        @SerializedName("quick_replies")       val quickReplies: List<String>?  = null,
        @SerializedName("safety_flags")        val safetyFlags: List<String>?   = null,
        @SerializedName("detected_symptoms")   val detectedSymptoms: List<String>? = null,
        @SerializedName("user_intent")         val userIntent: String?          = null,
    )

    suspend fun postChat(
        endpoint: String,
        userInput: String,
        language: String?,
    ): Result<ChatResponse> {
        val trimmedEndpoint = endpoint.trim()
        if (trimmedEndpoint.isBlank()) {
            return Result.failure(IllegalStateException("SAHARA_AI_CHAT_URL is not configured"))
        }
        val cleanInput = userInput.trim().take(MAX_USER_INPUT_CHARS)
        if (cleanInput.isBlank()) {
            return Result.failure(IllegalArgumentException("user input is empty"))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(trimmedEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout    = READ_TIMEOUT_MS
                    doOutput       = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val requestBody = gson.toJson(ChatRequest(cleanInput, language))
                        .toByteArray(Charsets.UTF_8)
                    connection.outputStream.use { it.write(requestBody) }

                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream
                                 else connection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        .orEmpty()

                    if (status !in 200..299) {
                        throw IOException("SAHARA AI returned HTTP $status: ${body.take(200)}")
                    }
                    if (body.isBlank()) {
                        throw IOException("SAHARA AI returned an empty body")
                    }

                    parseResponse(body)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun parseResponse(body: String): ChatResponse {
        runCatching {
            return gson.fromJson(body, ChatResponse::class.java)
        }
        val start = body.indexOf('{')
        val end   = body.lastIndexOf('}')
        if (start in 0 until end) {
            val sliced = body.substring(start, end + 1)
            return gson.fromJson(sliced, ChatResponse::class.java)
        }
        throw IOException("could not parse Sahara AI response JSON")
    }
}
