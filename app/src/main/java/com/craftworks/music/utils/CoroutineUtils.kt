package com.craftworks.music.utils

import android.util.Log
import com.craftworks.music.managers.DataRefreshManager
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.IOException

/**
 * Give this to every `viewModelScope.launch`. Those coroutines ask the media server for things,
 * and without a handler a request that fails - the server is down, or the TV woke before its
 * network did - ends as an uncaught exception on the main thread, which kills the app at startup.
 * The failure is logged instead, and the screen keeps what it had; the loading flags are reset by
 * each launch's own `finally`. A failure to reach the server at all also has the screens reload
 * once a network comes up (`DataRefreshManager.watchNetwork`).
 */
val LogFailures = CoroutineExceptionHandler { _, e ->
    Log.w("Chora", "Background work failed", e)
    if (e is IOException) DataRefreshManager.notifyLoadFailedOffline()
}
