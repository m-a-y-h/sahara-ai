package pk.edu.ucp.saharaai.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pk.edu.ucp.saharaai.data.model.VoiceAnalyzeResponse
import java.io.IOException
import java.util.concurrent.TimeUnit


object SaharaVoiceClient {

    private const val FIELD_NAME = "file"
    private const val DEFAULT_FILENAME = "voice.m4a"

    private val gson = Gson()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .build()
    }

    
    suspend fun analyze(
        endpoint: String,
        audioBytes: ByteArray,
        mimeType: String = "audio/m4a",
        filename: String = DEFAULT_FILENAME,
    ): Result<VoiceAnalyzeResponse> {
        val url = endpoint.trim()
        if (url.isBlank()) {
            return Result.failure(IllegalStateException("SAHARA_VOICE_ANALYZE_URL is not configured"))
        }
        if (audioBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("audioBytes is empty"))
        }
        val media = runCatching { mimeType.toMediaType() }
            .getOrElse { "application/octet-stream".toMediaType() }

        return withContext(Dispatchers.IO) {
            runCatching {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        name = FIELD_NAME,
                        filename = filename,
                        body = audioBytes.toRequestBody(media),
                    )
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IOException("Sahara Voice HTTP ${resp.code}: ${raw.take(200)}")
                    }
                    if (raw.isBlank()) {
                        throw IOException("Sahara Voice returned an empty body")
                    }
                    gson.fromJson(raw, VoiceAnalyzeResponse::class.java)
                        ?: throw IOException("Sahara Voice response was not valid JSON")
                }
            }
        }
    }
}
