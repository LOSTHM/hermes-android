package com.luka.hermes.gateway

/**
 * All Hermes JSON‑RPC method names in one place.
 * Keep this alphabetically grouped by domain so it doubles as a protocol reference.
 */
object RpcMethods {
    // ── Session ──────────────────────────────────────────────────────────
    const val SESSION_CREATE = "session.create"
    const val SESSION_LIST = "session.list"
    const val SESSION_RESUME = "session.resume"
    const val SESSION_ACTIVATE = "session.activate"
    const val SESSION_DELETE = "session.delete"
    const val SESSION_TITLE = "session.title"
    const val SESSION_HISTORY = "session.history"
    const val SESSION_STATUS = "session.status"
    const val SESSION_USAGE = "session.usage"
    const val SESSION_INTERRUPT = "session.interrupt"
    const val SESSION_COMPRESS = "session.compress"
    const val SESSION_BRANCH = "session.branch"
    const val SESSION_CLOSE = "session.close"
    const val SESSION_CONTEXT_BREAKDOWN = "session.context_breakdown"
    const val SESSION_UNDO = "session.undo"

    // ── Prompt ───────────────────────────────────────────────────────────
    const val PROMPT_SUBMIT = "prompt.submit"

    // ── Interaction ──────────────────────────────────────────────────────
    const val CLARIFY_RESPOND = "clarify.respond"
    const val APPROVAL_RESPOND = "approval.respond"
    const val SUDO_RESPOND = "sudo.respond"
    const val SECRET_RESPOND = "secret.respond"

    // ── Config ───────────────────────────────────────────────────────────
    const val CONFIG_GET = "config.get"
    const val CONFIG_SET = "config.set"
    const val CONFIG_SHOW = "config.show"

    // ── Model ────────────────────────────────────────────────────────────
    const val MODEL_OPTIONS = "model.options"

    // ── Process ──────────────────────────────────────────────────────────
    const val PROCESS_LIST = "process.list"
    const val PROCESS_KILL = "process.kill"

    // ── System ───────────────────────────────────────────────────────────
    const val SYSTEM_BATTERY = "system.battery"

    // ── Usage ────────────────────────────────────────────────────────────
    const val USAGE_BARS = "usage.bars"

    // ── Tools & Toolsets ─────────────────────────────────────────────────
    const val TOOLS_LIST = "tools.list"
    const val TOOLSETS_LIST = "toolsets.list"

    // ── Cron ─────────────────────────────────────────────────────────────
    const val CRON_MANAGE = "cron.manage"

    // ── Skills ───────────────────────────────────────────────────────────
    const val SKILLS_MANAGE = "skills.manage"

    // ── Plugins ──────────────────────────────────────────────────────────
    const val PLUGINS_LIST = "plugins.list"

    // ── Agents ───────────────────────────────────────────────────────────
    const val AGENTS_LIST = "agents.list"
}
