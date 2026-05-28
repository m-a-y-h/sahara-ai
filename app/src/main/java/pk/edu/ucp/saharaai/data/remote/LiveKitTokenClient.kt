package pk.edu.ucp.saharaai.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveKitTokenResponse(
    val serverUrl: String,
    val token: String,
)

object LiveKitTokenClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun fetchToken(
        tokenUrl: String,
        fallbackServerUrl: String,
        roomName: String,
        identity: String,
        displayName: String,
        mode: String,
        counselorKey: String,
        userId: String,
    ): Result<LiveKitTokenResponse> = runCatching {
        require(tokenUrl.isNotBlank()) { "LiveKit token endpoint is not configured." }
        val payload = JSONObject()
            .put("roomName", roomName)
            .put("identity", identity)
            .put("displayName", displayName)
            .put("mode", mode)
            .put("counselorKey", counselorKey)
            .put("userId", userId)
            .toString()
        val request = Request.Builder()
            .url(tokenUrl)
            .post(payload.toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("LiveKit token request failed (${response.code}).")
            }
            val json = JSONObject(body)
            LiveKitTokenResponse(
                serverUrl = json.optString("serverUrl").ifBlank {
                    json.optString("url").ifBlank { fallbackServerUrl }
                },
                token = json.optString("token"),
            ).also {
                require(it.serverUrl.isNotBlank()) { "LiveKit server URL is missing." }
                require(it.token.isNotBlank()) { "LiveKit token is missing." }
            }
        }
    }
}
