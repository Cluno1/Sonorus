package io.github.cluno1.sonorus.shared.presentation.components.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.unit.Dp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sonorus issue 169: the one way the app tells the user something happened.
 *
 * Invariants:
 * - Every message goes through [RhythmMessages]; the only `Toast.makeText` left in the app is the
 *   background fallback below. A system Toast ignores the app theme and queues one after another.
 * - Newest wins: [RhythmMessageBus.latest] is conflated, and the host shows the latest message right
 *   away, dismissing the one on screen. Posting 100 messages in a row shows one, never 100 in sequence.
 * - A message is shown in-app only while a host is visible (its Activity is STARTED). With no visible
 *   host (app in background, a Service posting) it falls back to exactly one system Toast, otherwise
 *   the message would be lost silently.
 *
 * Rejected: a queue of snackbars (that is exactly the old "100 toasts in a row" bug), and a host per
 * screen (the main screen used to render nothing at all because its Scaffold had `snackbarHost = {}`).
 */
enum class RhythmMessageKind { SUCCESS, INFO, ERROR }

data class RhythmMessage(
    val id: Long,
    val text: String,
    val kind: RhythmMessageKind,
    val long: Boolean,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** Pure routing core of [RhythmMessages], free of Android so it can be unit-tested. */
class RhythmMessageBus(private val fallback: (RhythmMessage) -> Unit) {
    private val nextId = AtomicLong()
    private val visibleHosts = AtomicInteger()
    private val _latest = MutableStateFlow<RhythmMessage?>(null)
    val latest: StateFlow<RhythmMessage?> = _latest.asStateFlow()

    fun hostVisible() {
        visibleHosts.incrementAndGet()
    }

    fun hostHidden() {
        visibleHosts.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Returns true when the message went to an in-app host, false when it fell back. */
    fun post(
        text: String,
        kind: RhythmMessageKind,
        long: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ): Boolean {
        val message = RhythmMessage(nextId.incrementAndGet(), text, kind, long, actionLabel, onAction)
        if (visibleHosts.get() > 0) {
            _latest.value = message
            return true
        }
        fallback(message)
        return false
    }

    /** The host calls this when a message has been shown, so a recreated host does not show it again. */
    fun consumed(id: Long) {
        val current = _latest.value
        if (current != null && current.id == id) _latest.compareAndSet(current, null)
    }
}

object RhythmMessages {
    private const val TAG = "RhythmMessages"

    @Volatile
    private var appContext: Context? = null

    /** Shared by every host and by screens that need an action result (e.g. "重新登记"). */
    val hostState = SnackbarHostState()

    val bus = RhythmMessageBus { message ->
        val context = appContext
        if (context == null) {
            Log.w(TAG, "No host and no context; message dropped: kind=${message.kind}")
            return@RhythmMessageBus
        }
        Handler(Looper.getMainLooper()).post {
            // issue 169: the single remaining system Toast, used only while no in-app host is visible.
            Toast.makeText(context, message.text, if (message.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    private val _bottomInsets = MutableStateFlow<List<Pair<String, Dp>>>(emptyList())

    /** The latest registered bottom inset wins (the full player registers over the main screen). */
    val bottomInsets: StateFlow<List<Pair<String, Dp>>> = _bottomInsets.asStateFlow()

    fun setBottomInset(key: String, inset: Dp) {
        _bottomInsets.value = _bottomInsets.value.filterNot { it.first == key } + (key to inset)
    }

    fun clearBottomInset(key: String) {
        _bottomInsets.value = _bottomInsets.value.filterNot { it.first == key }
    }

    fun show(
        context: Context,
        text: CharSequence,
        kind: RhythmMessageKind = RhythmMessageKind.INFO,
        long: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        if (appContext == null) appContext = context.applicationContext
        bus.post(text.toString(), kind, long, actionLabel, onAction)
    }

    fun success(context: Context, text: CharSequence, long: Boolean = false) =
        show(context, text, RhythmMessageKind.SUCCESS, long)

    fun info(context: Context, text: CharSequence, long: Boolean = false) =
        show(context, text, RhythmMessageKind.INFO, long)

    fun error(context: Context, text: CharSequence, long: Boolean = false) =
        show(context, text, RhythmMessageKind.ERROR, long)

    /** A failure the user should see as fixed text; the exception detail only goes to the log (issue 169). */
    fun failure(context: Context, @StringRes res: Int, cause: Throwable?, long: Boolean = false) {
        Log.w(TAG, "Failure shown to user: ${runCatching { context.resources.getResourceEntryName(res) }.getOrDefault(res.toString())}", cause)
        error(context, context.getString(res), long)
    }

    fun success(context: Context, @StringRes res: Int, vararg args: Any) =
        success(context, context.getString(res, *args))

    fun info(context: Context, @StringRes res: Int, vararg args: Any) =
        info(context, context.getString(res, *args))

    fun error(context: Context, @StringRes res: Int, vararg args: Any) =
        error(context, context.getString(res, *args))
}
