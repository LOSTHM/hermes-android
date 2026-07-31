package com.luka.hermes.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// ── Notification helpers ─────────────────────────────────────────────────────

/**
 * Thin wrapper over [NotificationManager] for surfacing background Hermes
 * events (daemon wake-ups, session lifecycle pings, error toasts that should
 * outlive the activity, etc).
 *
 * All public functions are safe to call on any thread — the underlying
 * platform calls are themselves non-blocking.
 */
object NotificationHelper {

    /** Stable channel id reused across all Hermes events. */
    const val CHANNEL_ID_EVENTS = "hermes_events"

    /** Separate channel for error / failure notifications. */
    const val CHANNEL_ID_ERRORS = "hermes_errors"

    private const val CHANNEL_NAME_EVENTS = "Hermes events"
    private const val CHANNEL_DESC_EVENTS = "Background session and daemon notifications"
    private const val CHANNEL_NAME_ERRORS = "Hermes errors"
    private const val CHANNEL_DESC_ERRORS = "Error notifications from Hermes"

    /**
     * Ensure both the events and errors channels exist on API 26+. Older
     * platforms ignore channels and fall back to legacy importance, so this
     * is a no-op there. Safe to call repeatedly; creation is idempotent.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(CHANNEL_ID_EVENTS) == null) {
            val events = NotificationChannel(
                CHANNEL_ID_EVENTS,
                CHANNEL_NAME_EVENTS,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESC_EVENTS
                enableLights(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(events)
        }

        if (nm.getNotificationChannel(CHANNEL_ID_ERRORS) == null) {
            val errors = NotificationChannel(
                CHANNEL_ID_ERRORS,
                CHANNEL_NAME_ERRORS,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESC_ERRORS
                enableLights(true)
            }
            nm.createNotificationChannel(errors)
        }
    }

    /**
     * Post a notification on the events channel.
     *
     * @param notificationId caller-defined id; pick stable ids per logical
     *   stream so consecutive updates replace each other instead of stacking.
     */
    fun notifyEvent(
        context: Context,
        title: String,
        message: String,
        notificationId: Int,
    ) {
        post(
            context = context,
            channelId = CHANNEL_ID_EVENTS,
            title = title,
            message = message,
            notificationId = notificationId,
            autoCancel = true,
        )
    }

    /**
     * Post a notification on the errors channel. Always uses a stable id
     * so repeat failures coalesce rather than spamming the shade.
     */
    fun notifyError(context: Context, message: String) {
        post(
            context = context,
            channelId = CHANNEL_ID_ERRORS,
            title = "Hermes error",
            message = message,
            notificationId = NOTIFICATION_ID_ERROR,
            autoCancel = true,
        )
    }

    private fun post(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int,
        autoCancel: Boolean,
    ) {
        // Ensure the channel exists even if the caller forgot to invoke
        // createChannel() on cold start.
        createChannel(context)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(autoCancel)

        // Best-effort tap intent: launch the main activity if it is exported.
        runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                val pending = PendingIntent.getActivity(
                    context,
                    notificationId,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.setContentIntent(pending)
            }
        }

        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        runCatching { nm.notify(notificationId, builder.build()) }
    }

    /** Stable id for [notifyError] so duplicates collapse. */
    private const val NOTIFICATION_ID_ERROR = 1001
}

// ── Share helpers ────────────────────────────────────────────────────────────

/**
 * Wraps the boring bits of firing an `ACTION_SEND` chooser — keeping callers
 * free of Intent flags, chooser boilerplate, and FLAG_GRANT_READ_URI_PERMISSION
 * concerns.
 */
object ShareHelper {

    /**
     * Share an arbitrary plain-text string.
     *
     * @param title chooser title shown in the system share sheet
     * @param text payload to share (placed under `Intent.EXTRA_TEXT`)
     */
    fun shareText(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    /**
     * Export an entire session's [messages] as plain text and hand it to the
     * system share sheet.
     *
     * Layout:
     * ```
     * <sessionTitle>
     *
     * [User] <text>
     * [Assistant] <text>
     * [Tool: <name>] <summary or result>
     * [Thinking] <text>
     * [Error] <message>
     * …
     * ```
     *
     * Unknown subtypes are skipped so the output stays clean.
     */
    fun shareSession(
        context: Context,
        sessionTitle: String,
        messages: List<ChatItem>,
    ) {
        val text = formatSession(sessionTitle, messages)
        shareText(context, sessionTitle, text)
    }

    /** Pure formatter, exposed for tests / future preview UI. */
    fun formatSession(sessionTitle: String, messages: List<ChatItem>): String {
        val sb = StringBuilder()
        sb.append(sessionTitle.ifBlank { "Hermes session" })
        sb.append("\n\n")
        for (item in messages) {
            when (item) {
                is ChatItem.UserMessage -> {
                    sb.append("[User] ").append(item.text.trim()).append('\n')
                }
                is ChatItem.AssistantMessage -> {
                    sb.append("[Assistant] ").append(item.text.trim()).append('\n')
                }
                is ChatItem.ToolCallCard -> {
                    sb.append("[Tool: ").append(item.name).append("] ")
                    val detail = item.summary
                        ?: item.result
                        ?: item.args
                        ?: "(no output)"
                    sb.append(detail.trim()).append('\n')
                }
                is ChatItem.ThinkingBlock -> {
                    sb.append("[Thinking] ").append(item.text.trim()).append('\n')
                }
                is ChatItem.ErrorItem -> {
                    sb.append("[Error] ").append(item.message.trim()).append('\n')
                }
            }
        }
        return sb.toString().trimEnd('\n')
    }
}