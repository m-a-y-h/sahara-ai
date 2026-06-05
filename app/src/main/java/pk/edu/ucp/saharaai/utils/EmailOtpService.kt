package pk.edu.ucp.saharaai.utils

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit


object EmailOtpService {

    private const val TAG             = "EmailOtpService"
    private const val EMAILJS_URL     = "https://api.emailjs.com/api/v1.0/email/send"
    private const val OTP_EXPIRY_MS   = 10L * 60 * 1000   
    private const val RATE_LIMIT_MS   = 60L * 1000        
    private const val OTP_DIGITS      = 6

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    

    sealed class SendResult {
        object Success                       : SendResult()
        data class Failure(val error: String): SendResult()
        object RateLimited                   : SendResult()
        object NotConfigured                 : SendResult()
    }

    sealed class VerifyResult {
        object Success                        : VerifyResult()
        object WrongCode                      : VerifyResult()
        object Expired                        : VerifyResult()
        object AlreadyUsed                    : VerifyResult()
        object NotFound                       : VerifyResult()
        data class Error(val message: String) : VerifyResult()
    }

    

    
    fun generateOtp(): String {
        val sr = SecureRandom()
        return (100_000 + sr.nextInt(900_000)).toString()
    }

    
    suspend fun storeOtp(uid: String, otp: String): Result<Unit> = runCatching {
        val db   = FirebaseDatabase.getInstance()
        val hash = sha256(otp)
        db.getReference("email_otps").child(uid).setValue(
            mapOf(
                "codeHash" to hash,
                "expiry"   to System.currentTimeMillis() + OTP_EXPIRY_MS,
                "used"     to false,
                "sentAt"   to System.currentTimeMillis()
            )
        ).await()
        Log.d(TAG, "OTP stored for uid=$uid")
    }

    
    suspend fun sendOtpEmail(
        toEmail   : String,
        toName    : String,
        otp       : String,
        mailerUrl : String,
        serviceId : String,
        templateId: String,
        publicKey : String
    ): SendResult {
        if (mailerUrl.isNotBlank()) {
            when (val modalResult = sendOtpWithSaharaMailer(toEmail, toName, otp, mailerUrl)) {
                is SendResult.Success -> return modalResult
                is SendResult.NotConfigured -> Unit
                is SendResult.RateLimited -> return modalResult
                is SendResult.Failure -> Log.w(TAG, "Sahara mailer failed; falling back to EmailJS: ${modalResult.error}")
            }
        }
        return sendOtpWithEmailJs(toEmail, toName, otp, serviceId, templateId, publicKey)
    }

    private suspend fun sendOtpWithSaharaMailer(
        toEmail: String,
        toName: String,
        otp: String,
        mailerUrl: String,
    ): SendResult {
        val idToken = try {
            Firebase.auth.currentUser?.getIdToken(false)?.await()?.token.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get Firebase ID token for Sahara mailer", e)
            ""
        }
        if (idToken.isBlank()) return SendResult.NotConfigured

        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("id_token", idToken)
                    put("to_email", toEmail)
                    put("to_name", toName)
                    put("otp_code", otp)
                    put("expiry_minutes", (OTP_EXPIRY_MS / 60_000).toString())
                }.toString()

                val request = Request.Builder()
                    .url(mailerUrl.trim().trimEnd('/'))
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "Sahara mailer response ${response.code}: $body")

                if (response.isSuccessful) SendResult.Success
                else SendResult.Failure("Sahara mailer ${response.code}: $body")
            } catch (e: Exception) {
                Log.e(TAG, "sendOtpWithSaharaMailer failed", e)
                SendResult.Failure(e.message ?: "Network error")
            }
        }
    }

    private suspend fun sendOtpWithEmailJs(
        toEmail: String,
        toName: String,
        otp: String,
        serviceId: String,
        templateId: String,
        publicKey: String,
    ): SendResult = withContext(Dispatchers.IO) {

        
        if (serviceId.isBlank() || templateId.isBlank() || publicKey.isBlank() ||
            serviceId == "YOUR_SERVICE_ID") {
            Log.w(TAG, "EmailJS credentials not configured")
            return@withContext SendResult.NotConfigured
        }

        return@withContext try {
            val payload = JSONObject().apply {
                put("service_id",  serviceId)
                put("template_id", templateId)
                put("user_id",     publicKey)
                put("template_params", JSONObject().apply {
                    put("to_name",        toName)
                    put("to_email",       toEmail)
                    put("otp_code",       otp)
                    put("expiry_minutes", (OTP_EXPIRY_MS / 60_000).toString())
                })
            }.toString()

            val request = Request.Builder()
                .url(EMAILJS_URL)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body     = response.body?.string() ?: ""
            Log.d(TAG, "EmailJS response ${response.code}: $body")

            if (response.isSuccessful) SendResult.Success
            else SendResult.Failure("EmailJS ${response.code}: $body")

        } catch (e: Exception) {
            Log.e(TAG, "sendOtpEmail failed", e)
            SendResult.Failure(e.message ?: "Network error")
        }
    }

    
    suspend fun resendCooldownSeconds(uid: String): Int {
        return try {
            val snap   = FirebaseDatabase.getInstance()
                .getReference("email_otps").child(uid)
                .child("sentAt").get().await()
            val sentAt = snap.getValue(Long::class.java) ?: 0L
            val elapsed = System.currentTimeMillis() - sentAt
            if (elapsed < RATE_LIMIT_MS) ((RATE_LIMIT_MS - elapsed) / 1000).toInt() else 0
        } catch (_: Exception) { 0 }
    }

    
    suspend fun verifyOtp(uid: String, enteredCode: String): VerifyResult {
        return try {
            val db   = FirebaseDatabase.getInstance()
            val snap = db.getReference("email_otps").child(uid).get().await()

            if (!snap.exists()) return VerifyResult.NotFound

            val storedHash = snap.child("codeHash").getValue(String::class.java)
                ?: return VerifyResult.NotFound
            val expiry     = snap.child("expiry").getValue(Long::class.java) ?: 0L
            val used       = snap.child("used").getValue(Boolean::class.java) ?: false

            if (used)                                       return VerifyResult.AlreadyUsed
            if (System.currentTimeMillis() > expiry)        return VerifyResult.Expired
            if (sha256(enteredCode.trim()) != storedHash)   return VerifyResult.WrongCode

            
            db.getReference("email_otps").child(uid).child("used").setValue(true).await()
            Log.d(TAG, "OTP verified for uid=$uid")
            VerifyResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp error", e)
            VerifyResult.Error(e.message ?: "Verification failed")
        }
    }

    
    suspend fun markEmailVerified(uid: String): Result<Unit> = runCatching {
        FirebaseDatabase.getInstance()
            .getReference("users").child(uid)
            .updateChildren(mapOf("emailVerified" to true))
            .await()
        Log.d(TAG, "Email marked verified for uid=$uid")
    }

    

    
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
