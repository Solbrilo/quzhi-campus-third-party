package cn.qzxy.hotwater.ui

import cn.qzxy.hotwater.HotWaterConfig
import java.util.Locale

/**
 * The persisted configuration is an external source for the form. Keep a small source
 * fingerprint so returning through a SaveableStateHolder reacts to displayed-field changes while
 * ignoring runtime metadata updates.
 */
internal fun configFormFingerprint(config: HotWaterConfig): String = buildString {
    append(config.projectId ?: "")
    append('\u0000')
    append(config.userId ?: "")
    append('\u0000')
    append(config.accountId ?: "")
    append('\u0000')
    append(config.loginCode)
    append('\u0000')
    append(config.telephone)
    append('\u0000')
    append(normalizeConfigSnCode(config.snCode))
}

internal fun shouldRefreshConfigForm(
    lastAppliedConfigFingerprint: String,
    currentConfigFingerprint: String,
): Boolean = lastAppliedConfigFingerprint != currentConfigFingerprint

internal data class ConfigFormRefresh(
    val projectId: String,
    val userId: String,
    val accountId: String,
    val loginCode: String,
    val telephone: String,
    val snCode: String,
    val snCodeDirty: Boolean,
)

internal fun shouldKeepSnCodeDraft(
    snCodeDirty: Boolean,
    draftSourceTelephone: String,
    refreshedTelephone: String,
): Boolean {
    if (!snCodeDirty) return false
    val sourceTelephone = draftSourceTelephone.trim()
    val nextTelephone = refreshedTelephone.trim()
    return sourceTelephone.isBlank() || nextTelephone.isBlank() || sourceTelephone == nextTelephone
}

/**
 * Merge a newly loaded configuration into the form. Login owns the account fields; an unsaved
 * device-code draft is retained only when it belongs to the same account.
 */
internal fun refreshConfigFormFromConfig(
    config: HotWaterConfig,
    currentSnCode: String,
    snCodeDirty: Boolean,
    preserveSnCodeDraft: Boolean = snCodeDirty,
): ConfigFormRefresh {
    val persistedSnCode = normalizeConfigSnCode(config.snCode)
    val draftSnCode = normalizeConfigSnCode(currentSnCode)
    val keepDraft = snCodeDirty && preserveSnCodeDraft
    val nextSnCode = if (keepDraft) draftSnCode else persistedSnCode
    return ConfigFormRefresh(
        projectId = config.projectId?.toString().orEmpty(),
        userId = config.userId?.toString().orEmpty(),
        accountId = config.accountId?.toString().orEmpty(),
        loginCode = config.loginCode,
        telephone = config.telephone,
        snCode = nextSnCode,
        snCodeDirty = keepDraft && nextSnCode != persistedSnCode,
    )
}

internal fun normalizeConfigSnCode(value: String): String =
    value.trim().replace(":", "").uppercase(Locale.ROOT)
