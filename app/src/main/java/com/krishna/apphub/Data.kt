package com.krishna.apphub

import android.content.Context
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow

@Entity
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val repo: String,
    val packageName: String = "",
    val localPath: String = "",
    val version: String = "v1",
    val stack: String = "Kotlin • Compose",
    val lastStatus: String = "none",
    val lastError: String = ""
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM Project ORDER BY name") fun all(): Flow<List<Project>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: Project): Long
    @Delete suspend fun delete(p: Project)
}

@Database(entities = [Project::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() { abstract fun dao(): ProjectDao }

class Prefs(ctx: Context) {
    private val sp = EncryptedSharedPreferences.create(
        ctx, "hub_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private fun s(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var token: String
        get() = s("token")
        set(v) = put("token", v)
    var login: String
        get() = s("login")
        set(v) = put("login", v)
    var clientId: String
        get() = s("client", BuildConfig.APPHUB_CLIENT_ID)
        set(v) = put("client", v)

    fun saveKeys(repo: String, v: String) = put("ks_$repo", v)
    fun keys(repo: String) = s("ks_$repo")
}
