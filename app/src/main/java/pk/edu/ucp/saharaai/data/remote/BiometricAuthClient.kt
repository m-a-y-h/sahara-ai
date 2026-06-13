package pk.edu.ucp.saharaai.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BiometricLoginResponse(
    val customToken: String,
    val email: String,
    val displayName: String,
)

class BiometricAuthHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message) {
    val isTerminalAuthFailure: Boolean
        get() = statusCode == 401 || statusCode == 403 || statusCode == 404
}

object BiometricAuthClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun enroll(
        endpoint: String,
        idToken: String,
        deviceId: String,
        deviceSecret: String,
        emailHint: String,
        displayNameHint: String,
    ): Result<Unit> = post(endpoint) {
        put("id_token", idToken)
        put("device_id", deviceId)
        put("device_secret", deviceSecret)
        put("email_hint", emailHint)
        put("display_name_hint", displayNameHint)
    }.map { }

    suspend fun login(
        endpoint: String,
        deviceId: String,
        deviceSecret: String,
    ): Result<BiometricLoginResponse> = post(endpoint) {
        put("device_id", deviceId)
        put("device_secret", deviceSecret)
    }.map { json ->
        BiometricLoginResponse(
            customToken = json.optString("custom_token").ifBlank { json.optString("customToken") },
            email = json.optString("email"),
            displayName = json.optString("display_name").ifBlank { json.optString("displayName") },
        ).also {
            require(it.customToken.isNotBlank()) { "Biometric login response did not include a Firebase token." }
        }
    }

    suspend fun disable(
        endpoint: String,
        idToken: String,
        deviceId: String,
    ): Result<Unit> = post(endpoint) {
        put("id_token", idToken)
        put("device_id", deviceId)
    }.map { }

    private suspend fun post(
        endpoint: String,
        payload: JSONObject.() -> Unit,
    ): Result<JSONObject> {
        val url = endpoint.trim()
        if (url.isBlank()) {
            return Result.failure(IllegalStateException("Biometric service is not configured."))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply(payload).toString()
                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(jsonType))
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body.string()
                    if (!response.isSuccessful) {
                        val detail = runCatching {
                            JSONObject(raw).optString("detail")
                        }.getOrDefault("").ifBlank { raw.take(200) }
                        throw BiometricAuthHttpException(
                            statusCode = response.code,
                            message = detail.ifBlank { "Biometric service returned HTTP ${response.code}." },
                        )
                    }
                    if (raw.isBlank()) JSONObject() else JSONObject(raw)
                }
            }
        }
    }
}
