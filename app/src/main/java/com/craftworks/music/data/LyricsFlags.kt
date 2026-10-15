package com.craftworks.music.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.repository.LyricsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.craftworks.music.BuildConfig
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.id
import com.craftworks.music.data.model.providerId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Lyrics the listener marked as wrong from Now Playing, kept so they can be looked into later.
 *
 * Each flag is a JSON line in `lyrics_flags.jsonl` in the app's private files. Nothing on the
 * device can read that file directly - `run-as` needs a debuggable build, and Fire OS keeps
 * `Android/data` from adb - so [LyricsFlagsProvider] serves it to adb's shell:
 *
 *     adb shell content query --uri content://com.craftworks.music.lyricsflags/flags
 *     adb shell content delete --uri content://com.craftworks.music.lyricsflags/flags --where "id='<id>'"
 *
 * The `json` column is the whole record; delete with no `--where` clears every flag.
 */
@Serializable
data class LyricsFlag(
    val id: String,
    val flaggedAt: Long,
    val appVersion: String,
    val device: String,
    val songId: String?,
    val providerId: String?,
    val title: String?,
    val artist: String?,
    /** What the lyrics sources were searched under - see `lyricsArtist` in MediaModel. */
    val lyricsArtist: String?,
    val lyricsAlbumArtist: String?,
    val album: String?,
    val isrc: String?,
    val durationMs: Long?,
    /** Where playback was when the flag was raised - the line that looked wrong is near it. */
    val positionMs: Long,
    val source: String,
    val syncType: String,
    /** The lyrics as shown: `[mm:ss.cc] text` per line, untimed lines without a stamp. */
    val lines: List<String>,
)

object LyricsFlags {
    private const val FILE_NAME = "lyrics_flags.jsonl"
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun create(
        metadata: MediaMetadata?,
        durationMs: Long?,
        positionMs: Long,
        lyrics: Lyrics,
    ): LyricsFlag {
        val extras = metadata?.extras
        return LyricsFlag(
            id = UUID.randomUUID().toString().substring(0, 8),
            flaggedAt = System.currentTimeMillis(),
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            songId = metadata?.id,
            providerId = metadata?.providerId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            lyricsArtist = extras?.getString("lyricsArtist"),
            lyricsAlbumArtist = extras?.getString("lyricsAlbumArtist"),
            album = metadata?.albumTitle?.toString(),
            isrc = extras?.getString("isrc"),
            durationMs = durationMs?.takeIf { it > 0 },
            positionMs = positionMs,
            source = lyrics.source.name,
            syncType = lyrics.syncType.name,
            lines = lyrics.lines.map { line ->
                val text = line.lines.joinToString(" / ") { it.text }
                if (line.startMs < 0) text else "[${timestamp(line.startMs)}] $text"
            },
        )
    }

    /** Flags the lyrics showing for the controller's current item, and says so. */
    fun flagCurrent(context: Context, mediaController: MediaController?, scope: CoroutineScope) {
        val lyrics = LyricsState.lyrics.value ?: return
        val flag = create(
            mediaController?.mediaMetadata,
            mediaController?.duration,
            mediaController?.currentPosition ?: 0L,
            lyrics
        )
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { add(appContext, flag) }
                .onFailure { Log.e("Lyrics", "Could not save a lyrics flag", it) }
                .isSuccess
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    if (saved) R.string.now_playing_lyrics_flagged else R.string.now_playing_lyrics_flag_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @Synchronized
    fun add(context: Context, flag: LyricsFlag) {
        file(context).appendText(json.encodeToString(flag) + "\n")
        Log.i("Lyrics", "Flagged lyrics ${flag.id}: ${flag.title} - ${flag.artist} from ${flag.source}")
    }

    @Synchronized
    fun all(context: Context): List<LyricsFlag> {
        val file = file(context)
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.mapNotNull {
            runCatching { json.decodeFromString<LyricsFlag>(it) }.getOrNull()
        }
    }

    /** Removes the flags [matching] picks, or all of them; returns how many went. */
    @Synchronized
    fun remove(context: Context, matching: (LyricsFlag) -> Boolean = { true }): Int {
        val flags = all(context)
        val kept = flags.filterNot(matching)
        file(context).writeText(kept.joinToString("") { json.encodeToString(it) + "\n" })
        return flags.size - kept.size
    }

    private fun timestamp(ms: Int): String =
        String.format(Locale.ROOT, "%02d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms % 1000 / 10)
}

/**
 * Hands the lyrics flags to adb (see [LyricsFlags]). Exported, but readable and writable only
 * with `android.permission.DUMP`, which the shell holds and no ordinary app can be granted.
 */
class LyricsFlagsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("id", "flagged_at", "title", "artist", "source", "json"))
        LyricsFlags.all(context!!).forEach {
            cursor.addRow(arrayOf<Any?>(it.id, it.flaggedAt, it.title, it.artist, it.source, Json.encodeToString(it)))
        }
        return cursor
    }

    /** `--where "id='abc123'"` removes one flag; no selection removes them all. */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = selection?.let { Regex("""id\s*=\s*'([^']*)'""").find(it)?.groupValues?.get(1) }
        if (selection != null && id == null) return 0
        return LyricsFlags.remove(context!!) { id == null || it.id == id }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
