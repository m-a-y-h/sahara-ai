package pk.edu.ucp.saharaai.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pk.edu.ucp.saharaai.data.model.LensScanResponse
import java.io.IOException
import java.util.concurrent.TimeUnit


object SaharaLensClient {

    private const val FIELD_NAME = "file"
    private const val DEFAULT_FILENAME = "lens.jpg"
    private const val JPEG_MIME = "image/jpeg"

    private val gson = Gson()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            
            
            .readTimeout(75, TimeUnit.SECONDS)
            .build()
    }

    
    suspend fun scan(
        endpoint: String,
        imageBytes: ByteArray,
        filename: String = DEFAULT_FILENAME,
    ): Result<LensScanResponse> {
        val url = endpoint.trim()
        if (url.isBlank()) {
            return Result.failure(IllegalStateException("SAHARA_LENS_SCAN_URL is not configured"))
        }
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("imageBytes is empty"))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        name = FIELD_NAME,
                        filename = filename,
                        body = imageBytes.toRequestBody(JPEG_MIME.toMediaType()),
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
                        throw IOException("Sahara Lens HTTP ${resp.code}: ${raw.take(200)}")
                    }
                    if (raw.isBlank()) {
                        throw IOException("Sahara Lens returned an empty body")
                    }
                    gson.fromJson(raw, LensScanResponse::class.java)
                        ?: throw IOException("Sahara Lens response was not valid JSON")
                }
            }
        }
    }
}
