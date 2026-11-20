package com.hamrochurch.remote.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hamrochurch.remote.model.ChurchPcProfile
import org.json.JSONArray
import org.json.JSONObject

class ProfileStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        createSecurePrefs()
    }

    private fun createSecurePrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Recover cleanly if OEM keystore invalidated keys after OS update
            appContext.deleteSharedPreferences(PREFS_NAME)
            try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                appContext.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    fun getProfiles(): List<ChurchPcProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ChurchPcProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val profile = ChurchPcProfile.fromJson(obj)
                if (profile.id.isNotEmpty() && profile.ips.isNotEmpty() && profile.token.isNotEmpty()) {
                    list.add(profile)
                }
            }
            list.sortedByDescending { it.lastConnectedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfile(profile: ChurchPcProfile) {
        val current = getProfiles().filterNot { it.id == profile.id }.toMutableList()
        current.add(0, profile)
        val trimmed = current.take(10)
        val arr = JSONArray()
        trimmed.forEach { arr.put(it.toJson()) }
        prefs.edit()
            .putString(KEY_PROFILES, arr.toString())
            .putString(KEY_LAST_PROFILE_ID, profile.id)
            .apply()
    }

    fun deleteProfile(profileId: String) {
        val next = getProfiles().filterNot { it.id == profileId }
        val arr = JSONArray()
        next.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun getLastProfileId(): String? = prefs.getString(KEY_LAST_PROFILE_ID, null)

    fun isNepaliLanguage(): Boolean = prefs.getBoolean(KEY_NEPALI_UI, true)
    fun setNepaliLanguage(nepali: Boolean) {
        prefs.edit().putBoolean(KEY_NEPALI_UI, nepali).apply()
    }

    fun isVolumeNavEnabled(): Boolean = prefs.getBoolean(KEY_VOLUME_NAV, true)
    fun setVolumeNavEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_NAV, enabled).apply()
    }

    fun isKeepScreenAwake(): Boolean = prefs.getBoolean(KEY_KEEP_AWAKE, true)
    fun setKeepScreenAwake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply()
    }

    fun isNepaliTypingEnabled(): Boolean = prefs.getBoolean(KEY_NEPALI_TYPING, true)
    fun setNepaliTypingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEPALI_TYPING, enabled).apply()
    }

    fun getDismissedPromotions(): Map<String, String> {
        val raw = prefs.getString(KEY_DISMISSED_PROMOTIONS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "")
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    result[key] = value
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveDismissedPromotions(map: Map<String, String>) {
        val entries = map.entries.toList()
        val trimmed = if (entries.size > 100) entries.takeLast(80) else entries
        val obj = JSONObject()
        trimmed.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_DISMISSED_PROMOTIONS, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "hamro_church_remote_secure_prefs"
        private const val KEY_PROFILES = "saved_pc_profiles"
        private const val KEY_LAST_PROFILE_ID = "last_profile_id"
        private const val KEY_NEPALI_UI = "nepali_ui"
        private const val KEY_VOLUME_NAV = "volume_key_nav"
        private const val KEY_KEEP_AWAKE = "keep_screen_awake"
        private const val KEY_NEPALI_TYPING = "nepali_phonetic_typing"
        private const val KEY_DISMISSED_PROMOTIONS = "dismissed_promotions"
    }
}
