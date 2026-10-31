package app.henachti.verify

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends one live camera frame to Claude and asks whether tefillin are actually worn on the arm.
 * Raw HTTP against the Messages API (the official Java SDK does not document Android support).
 */
class TefillinVerifier(private val apiKey: String) {

    sealed class Result {
        data class Verified(val note: String) : Result()
        data class Rejected(val reason: String) : Result()
        data class Error(val message: String, val authProblem: Boolean = false) : Result()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * @param fingers the random liveness challenge: how many fingers (thumb excluded) the user was
     * told to hold up on the hand that wears the strap. A stored photo cannot match a fresh number.
     */
    fun verify(jpeg: ByteArray, fingers: Int): Result {
        val image = JSONObject()
            .put("type", "image")
            .put(
                "source", JSONObject()
                    .put("type", "base64")
                    .put("media_type", "image/jpeg")
                    .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            )
        val text = JSONObject().put("type", "text").put("text", userPrompt(fingers))
        val body = baseBody(maxTokens = 400)
            .put("system", SYSTEM_PROMPT)
            .put("output_config", JSONObject().put("effort", "low"))
            .put(
                "messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", JSONArray().put(image).put(text))
                )
            )

        return when (val response = post(body)) {
            is Http.Failure -> Result.Error(response.message, response.authProblem)
            is Http.Success -> interpret(response.json, fingers)
        }
    }

    /** Returns null when the key works, otherwise a Hebrew message. */
    fun testKey(): String? {
        val body = baseBody(maxTokens = 64)
            .put("output_config", JSONObject().put("effort", "low"))
            .put(
                "messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "Reply with the single word OK.")
                )
            )
        return when (val response = post(body)) {
            is Http.Failure -> response.message
            is Http.Success -> null
        }
    }

    private fun baseBody(maxTokens: Int): JSONObject = JSONObject()
        .put("model", MODEL)
        .put("max_tokens", maxTokens)
        .put("fallbacks", "default")

    private sealed class Http {
        data class Success(val json: JSONObject) : Http()
        data class Failure(val message: String, val authProblem: Boolean = false) : Http()
    }

    private fun post(body: JSONObject): Http {
        if (apiKey.isBlank()) return Http.Failure("חסר מפתח API", authProblem = true)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", "server-side-fallback-2026-07-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val auth = response.code == 401 || response.code == 402 || response.code == 403
                    val detail = runCatching {
                        JSONObject(raw).getJSONObject("error").getString("message")
                    }.getOrDefault(raw.take(200))
                    val prefix = when (response.code) {
                        401 -> "המפתח לא תקין"
                        402 -> "אין יתרה בחשבון ה-API"
                        403 -> "למפתח אין הרשאה"
                        429 -> "יותר מדי בקשות, חכה רגע"
                        529 -> "השרת עמוס, נסה שוב"
                        else -> "שגיאת שרת ${response.code}"
                    }
                    Http.Failure("$prefix. $detail", auth)
                } else {
                    Http.Success(JSONObject(raw))
                }
            }
        } catch (e: IOException) {
            Http.Failure("אין חיבור לאינטרנט, או שהשרת לא זמין כרגע.")
        } catch (e: Exception) {
            Http.Failure("שגיאה לא צפויה: ${e.message}")
        }
    }

    private fun interpret(json: JSONObject, fingers: Int): Result {
        if (json.optString("stop_reason") == "refusal") {
            return Result.Error("המודל סירב לנתח את התמונה. נסה לצלם שוב.")
        }
        val content = json.optJSONArray("content") ?: return Result.Error("תשובה ריקה מהשרת")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        val parsed = extractJson(sb.toString())
            ?: return Result.Error("לא הצלחתי לקרוא את תשובת המודל. נסה שוב.")

        val onArm = parsed.optBoolean("tefillin_on_arm", false)
        val live = parsed.optBoolean("live_scene", true)
        val confidence = parsed.optDouble("confidence", 0.0)
        val shown = parsed.optInt("fingers_shown", -1)
        val note = parsed.optString("note", "").trim()

        return if (onArm && live && confidence >= 0.6 && shown == fingers) {
            Result.Verified(note)
        } else {
            val reason = when {
                !live -> "זה נראה כמו צילום של תמונה או מסך, לא יד אמיתית."
                !onArm && note.isNotBlank() -> note
                !onArm -> "לא זוהו תפילין על היד."
                shown < 0 -> "התפילין נראות, אבל לא ראיתי את האצבעות המורמות. הראה את כף היד עם הרצועות."
                shown != fingers -> "התפילין נראות, אבל ספרתי $shown אצבעות במקום $fingers. נסה שוב."
                note.isNotBlank() -> note
                else -> "לא הצלחתי לאשר. נסה שוב באור טוב יותר."
            }
            Result.Rejected(reason)
        }
    }

    private fun extractJson(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-opus-5-5"

        private val SYSTEM_PROMPT = """
            You are a strict verifier inside a self-discipline app. The user must prove that they are wearing tefillin right now by showing their arm to the live camera.

            Tefillin shel yad: a small black leather cube (the bayit) strapped onto the bicep of the upper arm, with a long black leather strap (retzuah) wound several times around the forearm and across the hand and middle finger. Sometimes only the forearm and hand with the windings are visible; that is acceptable when the windings are clearly the black tefillin strap on a real arm.

            As a liveness check the user is also told to hold up a specific number of fingers on the hand that wears the strap.

            Reply with ONLY a JSON object and no other text:
            {"tefillin_on_arm": boolean, "live_scene": boolean, "confidence": number from 0 to 1, "fingers_shown": integer, "note": string}

            - tefillin_on_arm: true only if tefillin (the box and/or the wound strap) are physically worn on a real human arm or hand in this photo.
            - live_scene: false if the image looks like a photo of a screen, a printed picture, a drawing, a mannequin, or shows tefillin in a bag, in a box or on a table instead of being worn.
            - confidence: how sure you are about tefillin_on_arm.
            - fingers_shown: the number of fingers clearly held up (extended) on the hand that wears the strap, thumb excluded. Use -1 if that hand's fingers are not visible.
            - note: one short, friendly sentence in Hebrew describing what you see, for example what is missing.
        """.trimIndent()

        private fun userPrompt(fingers: Int): String =
            "Is the person in this photo wearing tefillin on their arm right now? " +
                "They were asked to hold up exactly $fingers finger(s), thumb excluded, on the hand that wears the strap. " +
                "Count the raised fingers on that hand. JSON only."
    }
}
