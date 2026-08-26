package cn.qzxy.hotwater

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class FeedbackHistoryEntry(
    val submittedAtMillis: Long,
    val feedbackId: String,
    val category: FeedbackCategory,
    val email: String,
    val message: String,
) {
    fun maskedEmail(): String = FeedbackHistoryCodec.maskEmail(email)
}

/** Stores only feedback entries confirmed by the feedback service. */
class FeedbackHistoryStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): List<FeedbackHistoryEntry> = runCatching {
        FeedbackHistoryCodec.deserialize(prefs.getString(KEY_ENTRIES, null))
    }.getOrDefault(emptyList())

    fun save(entry: FeedbackHistoryEntry) {
        val entries = FeedbackHistoryCodec.addEntry(load(), entry)
        prefs.edit()
            .putString(KEY_ENTRIES, FeedbackHistoryCodec.serialize(entries))
            .apply()
    }

    fun clear(): Boolean = prefs.edit().clear().commit()

    private companion object {
        private const val PREFS_NAME = "feedback_history_store"
        private const val KEY_ENTRIES = "entries"
    }
}

internal object FeedbackHistoryCodec {
    const val MAX_ENTRIES = 50

    fun addEntry(
        existing: List<FeedbackHistoryEntry>,
        entry: FeedbackHistoryEntry,
    ): List<FeedbackHistoryEntry> =
        (listOf(entry) + existing.filterNot { it.feedbackId == entry.feedbackId })
            .take(MAX_ENTRIES)

    fun serialize(entries: List<FeedbackHistoryEntry>): String {
        val array = JSONArray()
        entries.take(MAX_ENTRIES).forEach { entry ->
            array.put(
                JSONObject()
                    .put("submittedAtMillis", entry.submittedAtMillis)
                    .put("feedbackId", entry.feedbackId)
                    .put("category", entry.category.apiValue)
                    .put("email", entry.email)
                    .put("message", entry.message),
            )
        }
        return array.toString()
    }

    fun deserialize(serialized: String?): List<FeedbackHistoryEntry> {
        if (serialized.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::decodeEntry)?.let(::add)
                }
            }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    fun maskEmail(email: String): String {
        val normalized = email.trim()
        val atIndex = normalized.lastIndexOf('@')
        if (atIndex <= 0 || atIndex == normalized.lastIndex) return "***"
        return "${normalized.take(1)}***${normalized.substring(atIndex)}"
    }

    private fun decodeEntry(json: JSONObject): FeedbackHistoryEntry? {
        val submittedAtMillis = json.optLong("submittedAtMillis", 0L)
        val feedbackId = json.optString("feedbackId").trim()
        val category = FeedbackCategory.entries.firstOrNull {
            it.apiValue == json.optString("category").trim()
        }
        val email = json.optString("email").trim()
        val message = json.optString("message").trim()
        if (submittedAtMillis <= 0L || feedbackId.isEmpty() || category == null || email.isEmpty() || message.isEmpty()) {
            return null
        }
        return FeedbackHistoryEntry(
            submittedAtMillis = submittedAtMillis,
            feedbackId = feedbackId,
            category = category,
            email = email,
            message = message,
        )
    }
}
