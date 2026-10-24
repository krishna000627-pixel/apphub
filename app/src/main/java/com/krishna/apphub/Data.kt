package com.krishna.apphub

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val lastError: String = "",
    /** v38: ProjectType name (APP, WEBSITE, SERVER). Stored as text so old rows and backups stay readable. */
    val type: String = "APP",
    /** v38: where a website is live / where a server answers (empty = not set). */
    val liveUrl: String = ""
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM Project ORDER BY name") fun all(): Flow<List<Project>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: Project): Long
    @Delete suspend fun delete(p: Project)
}

@Database(entities = [Project::class], version = 2, exportSchema = false)
abstract class Db : RoomDatabase() { abstract fun dao(): ProjectDao }

/** The one place the database is opened (app and background worker), so both always know the migrations. */
object HubDb {
    /** v38: project type + live URL. Existing rows become APP with no URL, so nothing changes for current projects. */
    private val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Project ADD COLUMN type TEXT NOT NULL DEFAULT 'APP'")
            db.execSQL("ALTER TABLE Project ADD COLUMN liveUrl TEXT NOT NULL DEFAULT ''")
        }
    }
    fun build(ctx: Context): Db = Room.databaseBuilder(ctx, Db::class.java, "hub").addMigrations(M1_2).build()
}

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
    /** Expiring tokens (GitHub Apps, 8 hours): the refresh token and when the access token stops working (0 = never expires). */
    var refreshToken: String
        get() = s("refresh_token")
        set(v) = put("refresh_token", v)
    var tokenExpiry: Long
        get() = sp.getLong("token_expiry", 0L)
        set(v) { sp.edit().putLong("token_expiry", v).apply() }
    var clientId: String
        get() = s("client").ifBlank { BuildConfig.APPHUB_CLIENT_ID }
        set(v) = put("client", v)

    var clientSecret: String
        get() = s("client_secret").ifBlank { BuildConfig.APPHUB_CLIENT_SECRET }
        set(v) = put("client_secret", v.trim())

    // pending login (survives the app being killed while the browser is open)
    var pendingState: String
        get() = s("p_state")
        set(v) = put("p_state", v)
    var pendingVerifier: String
        get() = s("p_verifier")
        set(v) = put("p_verifier", v)
    var pendingDevice: String
        get() = s("p_device")
        set(v) = put("p_device", v)
    var pendingCode: String
        get() = s("p_code")
        set(v) = put("p_code", v)
    var pendingInterval: Int
        get() = sp.getInt("p_interval", 5)
        set(v) { sp.edit().putInt("p_interval", v).apply() }
    var pendingExpiry: Long
        get() = sp.getLong("p_expiry", 0L)
        set(v) { sp.edit().putLong("p_expiry", v).apply() }

    var seeded: Boolean
        get() = sp.getBoolean("seeded_v1", false)
        set(v) { sp.edit().putBoolean("seeded_v1", v).apply() }

    fun pinned(id: Long) = sp.getBoolean("pin_$id", false)
    fun setPinned(id: Long, v: Boolean) { sp.edit().putBoolean("pin_$id", v).apply() }
    fun installed(id: Long) = s("inst_$id")
    fun setInstalled(id: Long, v: String) = put("inst_$id", v)

    fun lastRun(id: Long) = sp.getLong("lr_$id", 0L)
    fun setLastRun(id: Long, v: Long) { sp.edit().putLong("lr_$id", v).apply() }
    /** Version label the user set by hand (Settings / bump); only then may the label stay ahead of the last pushed "vNNN:" commit. */
    fun manualVer(id: Long) = s("mv_$id")
    fun setManualVer(id: Long, v: String) = put("mv_$id", v)
    fun pushedVer(id: Long) = s("pv_$id")
    fun setPushedVer(id: Long, v: String) = put("pv_$id", v)

    /** Release tag of the APK sitting in the cache for this app ("" = nothing cached). */
    fun cachedTag(id: Long) = s("ct_$id")
    fun setCachedTag(id: Long, v: String) = put("ct_$id", v)
    /** When the system installer was opened for the cached APK (0 = not opened yet). */
    fun installTry(id: Long) = sp.getLong("it_$id", 0L)
    fun setInstallTry(id: Long, v: Long) { sp.edit().putLong("it_$id", v).apply() }

    /** Presets already offered once, so a preset you removed is not brought back by an update. */
    fun presetSeen(repo: String) = sp.getBoolean("ps_$repo", false)
    fun setPresetSeen(repo: String) { sp.edit().putBoolean("ps_$repo", true).apply() }

    /** Open the installer by itself when a pushed build passes (what termux/release.sh does). */
    var autoInstall: Boolean
        get() = sp.getBoolean("auto_install", true)
        set(v) { sp.edit().putBoolean("auto_install", v).apply() }

    /** When build runs / details were last pulled from GitHub (millis), so screens can skip the sync while the cache is fresh. */
    fun syncedAt(id: Long) = sp.getLong("sy_$id", 0L)
    fun setSyncedAt(id: Long, v: Long) { sp.edit().putLong("sy_$id", v).apply() }
    fun detailsAt(id: Long) = sp.getLong("dt_$id", 0L)
    fun setDetailsAt(id: Long, v: Long) { sp.edit().putLong("dt_$id", v).apply() }

    /** Save new APKs in the background (WorkManager), even when the app is closed. */
    var autoDownload: Boolean
        get() = sp.getBoolean("auto_dl", true)
        set(v) { sp.edit().putBoolean("auto_dl", v).apply() }
    /** Background downloads only on Wi-Fi / unmetered networks. */
    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", true)
        set(v) { sp.edit().putBoolean("wifi_only", v).apply() }

    // ---- v27 notifications (feature 3) ----
    /** One notification when a build finishes and one when an update APK has been downloaded. */
    var notifyOn: Boolean
        get() = sp.getBoolean("notify_on", true)
        set(v) { sp.edit().putBoolean("notify_on", v).apply() }
    /** Newest finished run that was already announced (0 = nothing seen yet; the first sync only remembers, it never announces). */
    fun notifiedRun(id: Long) = sp.getLong("nr_$id", 0L)
    fun setNotifiedRun(id: Long, v: Long) { sp.edit().putLong("nr_$id", v).apply() }

    // ---- v27 rollback (feature 5): release tag of the previous APK kept next to the latest one ----
    fun prevTag(id: Long) = s("pt_$id")
    fun setPrevTag(id: Long, v: String) = put("pt_$id", v)

    // ---- v27 live sync (feature 6): seconds between silent syncs, 0 = off ----
    var syncSecs: Int
        get() = sp.getInt("sync_secs", 3)
        set(v) { sp.edit().putInt("sync_secs", v).apply() }

    fun zipSeen(id: Long) = s("zs_$id")
    fun setZipSeen(id: Long, v: String) = put("zs_$id", v)

    fun autoVer(id: Long) = sp.getBoolean("auto_$id", true)
    fun setAutoVer(id: Long, v: Boolean) { sp.edit().putBoolean("auto_$id", v).apply() }

    fun saveKeys(repo: String, v: String) = put("ks_$repo", v)
    fun keys(repo: String) = s("ks_$repo")

    // ---- v39 Vercel: personal access token, account name, optional team id; project id is kept per repo ----
    var vercelToken: String
        get() = s("vc_token")
        set(v) = put("vc_token", v.trim())
    var vercelUser: String
        get() = s("vc_user")
        set(v) = put("vc_user", v)
    var vercelTeam: String
        get() = s("vc_team")
        set(v) = put("vc_team", v.trim())
    // ---- v43 Supabase access token, Render API key and the Render account name ----
    var supabaseToken: String
        get() = s("sb_token")
        set(v) = put("sb_token", v.trim())
    var renderKey: String
        get() = s("rd_key")
        set(v) = put("rd_key", v.trim())
    var renderUser: String
        get() = s("rd_user")
        set(v) = put("rd_user", v)
    /** Vercel project id this repo deploys to ("" = not on Vercel). */
    fun vercelId(repo: String) = s("vp_$repo")
    fun setVercelId(repo: String, v: String) = put("vp_$repo", v)

    // ---- v40: switches keep their last position (stored on disk, so cache clear / force stop never resets them) ----
    var setupCreate: Boolean            // Setup: Create GitHub repo
        get() = sp.getBoolean("su_create", true)
        set(v) { sp.edit().putBoolean("su_create", v).apply() }
    var setupKeys: Boolean              // Setup: Generate signing keys + set secrets
        get() = sp.getBoolean("su_keys", true)
        set(v) { sp.edit().putBoolean("su_keys", v).apply() }
    // ---- v42: server down / up alerts. State per server: 0 unknown, 1 up, 2 down (never announced), 3 down (announced) ----
    var serverAlerts: Boolean
        get() = sp.getBoolean("srv_alerts", true)
        set(v) { sp.edit().putBoolean("srv_alerts", v).apply() }
    fun serverState(id: Long) = sp.getInt("ss_$id", 0)
    fun setServerState(id: Long, v: Int) { sp.edit().putInt("ss_$id", v).apply() }
    fun serverFails(id: Long) = sp.getInt("sf_$id", 0)
    fun setServerFails(id: Long, v: Int) { sp.edit().putInt("sf_$id", v).apply() }

    var promptDeliver: Boolean          // Prompt Maker: Add zip name + version bump rules
        get() = sp.getBoolean("pm_deliver", true)
        set(v) { sp.edit().putBoolean("pm_deliver", v).apply() }
}

/** Shown when a release has no notes, the way Google Play falls back to a generic line. */
const val DEFAULT_NOTES = "Bug fixes and performance improvements."
fun notesOrDefault(body: String): String = body.trim().ifBlank { DEFAULT_NOTES }

/** Apps that are added automatically on first launch (owner is filled in once you log in). */
data class Preset(val name: String, val repo: String, val pkg: String, val path: String,
                  val stack: String = "Kotlin • Compose", val version: String = "v1")

object Presets {
    private const val HOME = "/data/data/com.termux/files/home/"
    val all = listOf(
        Preset("QuestBlock", "questblock", "", HOME + "QuestBlock", version = "v285"),
        Preset("BgBrowser", "bgbrowser", "com.bgbrowser", HOME + "BgBrowser"),
        Preset("App Hub", "apphub", "com.krishna.apphub", HOME + "AppHub", "Kotlin • Compose • Room", "v27"),
        Preset("Videowall", "videowall", "", HOME + "Videowall")
    )
}
