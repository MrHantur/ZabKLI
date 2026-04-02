package ru.zabkli
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Единая точка доступа к данным авторизации.
 * Хранит access_token, refresh_token, роль, имя пользователя и дополнительную информацию
 * в SharedPreferences "auth_zabkli".
 */
object AuthManager {
    private const val PREFS_NAME   = "auth_zabkli"
    private const val KEY_ACCESS   = "access_token"
    private const val KEY_REFRESH  = "refresh_token"
    private const val KEY_ROLE     = "role"
    private const val KEY_USERNAME = "username"
    private const val KEY_FIRST    = "first_name"
    private const val KEY_LAST     = "last_name"
    private const val KEY_USER_ID  = "user_id"
    private const val KEY_KARMA    = "karma"

    private const val BASE_URL = "http://zab.mrhantur.su:1717"

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getString(KEY_ACCESS, null) != null

    fun getAccessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS, null)

    fun getRefreshToken(context: Context): String? =
        prefs(context).getString(KEY_REFRESH, null)

    fun getRole(context: Context): SettingsActivity.UserType {
        val raw = prefs(context).getString(KEY_ROLE, null) ?: return SettingsActivity.UserType.UNKNOWN
        return roleFromString(raw)
    }

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)

    fun getUserId(context: Context): Int? =
        prefs(context).getInt(KEY_USER_ID, -1).takeIf { it != -1 }

    fun getKarma(context: Context): Int =
        prefs(context).getInt(KEY_KARMA, 0)

    fun getDisplayName(context: Context): String {
        val p = prefs(context)
        val first = p.getString(KEY_FIRST, null)
        val last  = p.getString(KEY_LAST, null)
        return when {
            first != null && last != null -> "$first $last"
            first != null -> first
            last  != null -> last
            else -> p.getString(KEY_USERNAME, null) ?: "Аноним"
        }
    }

    fun getUserInfo(context: Context): UserInfo {
        val p = prefs(context)
        return UserInfo(
            id = p.getInt(KEY_USER_ID, -1).takeIf { it != -1 },
            username = p.getString(KEY_USERNAME, null),
            firstName = p.getString(KEY_FIRST, null),
            lastName = p.getString(KEY_LAST, null),
            role = getRole(context),
            karma = p.getInt(KEY_KARMA, 0)
        )
    }

    suspend fun login(context: Context, username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val body = "username=${encode(username)}&password=${encode(password)}"
                val conn = openConnection("$BASE_URL/auth/login", "POST",
                    contentType = "application/x-www-form-urlencoded")
                conn.outputStream.use { out ->
                    OutputStreamWriter(out).use { it.write(body) }
                }
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    saveTokens(context, JSONObject(json))
                    fetchAndSaveProfile(context)
                    null
                } else {
                    errorMessage(conn)
                }
            } catch (e: Exception) {
                e.message ?: "Неизвестная ошибка"
            }
        }

    suspend fun register(
        context: Context,
        username: String,
        password: String,
        firstName: String?,
        lastName: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val obj = JSONObject().apply {
                put("username", username)
                put("password", password)
                if (!firstName.isNullOrBlank()) put("first_name", firstName)
                if (!lastName.isNullOrBlank()) put("last_name", lastName)
                put("role", "viewer")
            }
            val conn = openConnection("$BASE_URL/auth/register-with-token", "POST",
                contentType = "application/json")
            conn.outputStream.use { out ->
                OutputStreamWriter(out).use { it.write(obj.toString()) }
            }
            if (conn.responseCode == 201) {
                val json = conn.inputStream.bufferedReader().readText()
                saveTokens(context, JSONObject(json))
                fetchAndSaveProfile(context)
                null
            } else {
                errorMessage(conn)
            }
        } catch (e: Exception) {
            e.message ?: "Неизвестная ошибка"
        }
    }

    suspend fun refreshProfile(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val token = getAccessToken(context) ?: return@withContext false
            try {
                val conn = openConnection("$BASE_URL/users/me", "GET")
                conn.setRequestProperty("Authorization", "Bearer $token")
                when (conn.responseCode) {
                    200 -> {
                        fetchAndSaveProfile(context)
                        true
                    }
                    401 -> {
                        tryRefreshTokens(context) && run {
                            fetchAndSaveProfile(context)
                            true
                        }
                    }
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

    fun logout(context: Context) {
        prefs(context).edit { clear() }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun saveTokens(context: Context, json: JSONObject) {
        prefs(context).edit {
            putString(KEY_ACCESS, json.optString("access_token"))
            putString(KEY_REFRESH, json.optString("refresh_token"))
        }
    }

    private fun fetchAndSaveProfile(context: Context) {
        val token = getAccessToken(context) ?: return
        val conn = openConnection("$BASE_URL/users/me", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (conn.responseCode == 200) {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            prefs(context).edit {
                putInt(KEY_USER_ID, json.optInt("id", -1))
                putString(KEY_ROLE, json.optString("role"))
                putString(KEY_USERNAME, json.optString("username"))
                putString(KEY_FIRST, json.optString("first_name"))
                putString(KEY_LAST, json.optString("last_name"))
                putInt(KEY_KARMA, json.optInt("karma", 0))
            }
        }
    }

    private fun tryRefreshTokens(context: Context): Boolean {
        val refreshToken = getRefreshToken(context) ?: return false
        return try {
            val body = JSONObject().put("refresh_token", refreshToken).toString()
            val conn = openConnection("$BASE_URL/auth/refresh", "POST",
                contentType = "application/json")
            conn.outputStream.use { out ->
                OutputStreamWriter(out).use { it.write(body) }
            }
            if (conn.responseCode == 200) {
                saveTokens(context, JSONObject(conn.inputStream.bufferedReader().readText()))
                true
            } else {
                logout(context)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        contentType: String? = null
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout    = 10_000
        if (contentType != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
        }
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun errorMessage(conn: HttpURLConnection): String {
        return try {
            val body = conn.errorStream?.bufferedReader()?.readText() ?: " "
            val json = JSONObject(body)
            json.optString("detail", "Ошибка ${conn.responseCode}")
        } catch (e: Exception) {
            "Ошибка ${conn.responseCode}"
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    fun roleFromString(raw: String): SettingsActivity.UserType = when (raw.lowercase()) {
        "contributor" -> SettingsActivity.UserType.CONTRIBUTOR
        "editor"      -> SettingsActivity.UserType.EDITOR
        "admin"       -> SettingsActivity.UserType.ADMIN
        "viewer"      -> SettingsActivity.UserType.VIEWER
        else          -> SettingsActivity.UserType.UNKNOWN
    }

    data class UserInfo(
        val id: Int?,
        val username: String?,
        val firstName: String?,
        val lastName: String?,
        val role: SettingsActivity.UserType,
        val karma: Int
    )
}