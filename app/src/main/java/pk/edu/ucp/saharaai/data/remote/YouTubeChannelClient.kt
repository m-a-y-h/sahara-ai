package pk.edu.ucp.saharaai.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YouTubeSubscription(
    val channelId: String,
    val channelTitle: String
)

data class YouTubeAuthorizedData(
    val channelId: String,
    val channelTitle: String,
    val subscriptions: List<YouTubeSubscription>,
    val subscriptionsTruncated: Boolean
)

private data class YouTubeChannelSnippet(val title: String = "")
private data class YouTubeChannelItem(
    val id: String = "",
    val snippet: YouTubeChannelSnippet = YouTubeChannelSnippet()
)
private data class YouTubeChannelListResponse(val items: List<YouTubeChannelItem> = emptyList())
private data class YouTubeSubscriptionResourceId(val channelId: String = "")
private data class YouTubeSubscriptionSnippet(
    val title: String = "",
    val resourceId: YouTubeSubscriptionResourceId = YouTubeSubscriptionResourceId()
)
private data class YouTubeSubscriptionItem(
    val snippet: YouTubeSubscriptionSnippet = YouTubeSubscriptionSnippet()
)
private data class YouTubeSubscriptionListResponse(
    val nextPageToken: String? = null,
    val items: List<YouTubeSubscriptionItem> = emptyList()
)


object YouTubeChannelClient {
    private const val MAX_SUBSCRIPTIONS = 1_000

    private val gson = Gson()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun loadAuthorizedData(accessToken: String): Result<YouTubeAuthorizedData> {
        if (accessToken.isBlank()) {
            return Result.failure(IllegalArgumentException("YouTube access token is empty"))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true&maxResults=1")
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("YouTube channel request failed (${response.code})")
                    }
                    val channel = gson.fromJson(raw, YouTubeChannelListResponse::class.java)
                        ?.items
                        ?.firstOrNull()
                        ?: throw IOException("This Google account does not have a YouTube channel to link")
                    if (channel.id.isBlank()) {
                        throw IOException("YouTube returned an invalid channel identity")
                    }

                    val subscriptions = mutableListOf<YouTubeSubscription>()
                    var nextPageToken: String? = null
                    var truncated = false
                    do {
                        val endpoint = "https://www.googleapis.com/youtube/v3/subscriptions"
                            .toHttpUrl()
                            .newBuilder()
                            .addQueryParameter("part", "snippet")
                            .addQueryParameter("mine", "true")
                            .addQueryParameter("maxResults", "50")
                            .apply { nextPageToken?.let { addQueryParameter("pageToken", it) } }
                            .build()
                        val subscriptionRequest = Request.Builder()
                            .url(endpoint)
                            .header("Authorization", "Bearer $accessToken")
                            .header("Accept", "application/json")
                            .build()
                        client.newCall(subscriptionRequest).execute().use { subscriptionResponse ->
                            val subscriptionsRaw = subscriptionResponse.body.string()
                            if (!subscriptionResponse.isSuccessful) {
                                throw IOException(
                                    "YouTube subscriptions request failed (${subscriptionResponse.code})"
                                )
                            }
                            val payload = gson.fromJson(
                                subscriptionsRaw,
                                YouTubeSubscriptionListResponse::class.java
                            )
                            val remaining = MAX_SUBSCRIPTIONS - subscriptions.size
                            subscriptions += payload.items
                                .mapNotNull { item ->
                                    val id = item.snippet.resourceId.channelId
                                    if (id.isBlank()) null
                                    else YouTubeSubscription(id, item.snippet.title)
                                }
                                .take(remaining)
                            nextPageToken = payload.nextPageToken?.takeIf { it.isNotBlank() }
                            truncated = subscriptions.size >= MAX_SUBSCRIPTIONS && nextPageToken != null
                        }
                    } while (nextPageToken != null && subscriptions.size < MAX_SUBSCRIPTIONS)

                    YouTubeAuthorizedData(
                        channelId = channel.id,
                        channelTitle = channel.snippet.title,
                        subscriptions = subscriptions,
                        subscriptionsTruncated = truncated
                    )
                }
            }
        }
    }
}
