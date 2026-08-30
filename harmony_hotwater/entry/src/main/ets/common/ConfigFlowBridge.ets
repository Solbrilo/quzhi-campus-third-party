/**
 * Carries one-shot results between the configuration page and child routes.
 * Scanner intentionally never writes the persisted account configuration.
 */
export class ConfigFlowBridge {
  private static pendingSnCode: string = ''
  private static loginConfigChanged: boolean = false
  private static loginSkipped: boolean = false
  // In-memory generation used to invalidate stale initialization snapshots when a
  // user-driven flow starts writing configuration.
  private static configMutationGeneration: number = 0
  private static dataCleared: boolean = false
  private static dataClearGeneration: number = 0
  private static completedDataClearGeneration: number = 0

  static setPendingSnCode(snCode: string): void {
    ConfigFlowBridge.pendingSnCode = snCode.trim().replace(/:/g, '').toUpperCase()
  }

  static consumePendingSnCode(): string {
    const snCode = ConfigFlowBridge.pendingSnCode
    ConfigFlowBridge.pendingSnCode = ''
    return snCode
  }

  static markLoginConfigChanged(): void {
    ConfigFlowBridge.loginConfigChanged = true
  }

  static consumeLoginConfigChanged(): boolean {
    const changed = ConfigFlowBridge.loginConfigChanged
    ConfigFlowBridge.loginConfigChanged = false
    return changed
  }

  /** Marks the beginning of a configuration write and returns its generation. */
  static markConfigMutationStarted(): number {
    ConfigFlowBridge.configMutationGeneration++
    return ConfigFlowBridge.configMutationGeneration
  }

  static getConfigMutationGeneration(): number {
    return ConfigFlowBridge.configMutationGeneration
  }

  /**
   * A credential-expiry login can be dismissed without replacing the imported configuration.
   * Keep this as a one-shot signal so the host page can resume its foreground state.
   */
  static markLoginSkipped(): void {
    ConfigFlowBridge.loginSkipped = true
  }

  static consumeLoginSkipped(): boolean {
    const skipped = ConfigFlowBridge.loginSkipped
    ConfigFlowBridge.loginSkipped = false
    return skipped
  }

  /**
   * Invalidates work that was started with the previous account data before the stores are erased.
   * The completion signal is published separately so a failed erase does not redirect the user.
   */
  static beginDataClear(): number {
    ConfigFlowBridge.dataClearGeneration++
    return ConfigFlowBridge.dataClearGeneration
  }

  static getDataClearGeneration(): number {
    return ConfigFlowBridge.dataClearGeneration
  }

  static isDataClearGenerationCurrent(generation: number): boolean {
    return generation === ConfigFlowBridge.dataClearGeneration
  }

  /** Compatibility alias for callers that name the operation "mark clearing". */
  static markDataClearing(): number {
    return ConfigFlowBridge.beginDataClear()
  }

  /** Completes the one-shot navigation signal after persistence is cleared. */
  static markDataCleared(generation?: number): void {
    const completedGeneration = generation ?? ConfigFlowBridge.dataClearGeneration
    if (completedGeneration !== ConfigFlowBridge.dataClearGeneration) {
      return
    }
    ConfigFlowBridge.completedDataClearGeneration = completedGeneration
    ConfigFlowBridge.dataCleared = true
  }

  /**
   * Returns the last successfully completed clear generation. Pages can use this
   * in addition to the one-shot flag because a route instance may not receive a
   * matching onPageShow callback after the clear navigation.
   */
  static getCompletedDataClearGeneration(): number {
    return ConfigFlowBridge.completedDataClearGeneration
  }

  static consumeDataCleared(): boolean {
    const cleared = ConfigFlowBridge.dataCleared
    ConfigFlowBridge.dataCleared = false
    return cleared
  }

  /**
   * A login refresh owns the account fields. Keep an unsaved device scan only when it belongs to
   * the same phone (or the form has not been associated with an account yet).
   */
  static resolveSnCodeAfterLogin(
    currentSnCode: string,
    snCodeDirty: boolean,
    persistedSnCode: string,
    draftSourceTelephone: string = '',
    refreshedTelephone: string = ''
  ): string {
    return ConfigFlowBridge.shouldKeepSnCodeDraft(
      snCodeDirty,
      draftSourceTelephone,
      refreshedTelephone
    ) ? ConfigFlowBridge.normalizeSnCode(currentSnCode) :
      ConfigFlowBridge.normalizeSnCode(persistedSnCode)
  }

  static hasPendingSnCodeAfterLogin(
    currentSnCode: string,
    snCodeDirty: boolean,
    persistedSnCode: string,
    draftSourceTelephone: string = '',
    refreshedTelephone: string = ''
  ): boolean {
    return ConfigFlowBridge.shouldKeepSnCodeDraft(
      snCodeDirty,
      draftSourceTelephone,
      refreshedTelephone
    ) &&
      ConfigFlowBridge.normalizeSnCode(currentSnCode) !== ConfigFlowBridge.normalizeSnCode(persistedSnCode)
  }

  private static shouldKeepSnCodeDraft(
    snCodeDirty: boolean,
    draftSourceTelephone: string,
    refreshedTelephone: string
  ): boolean {
    if (!snCodeDirty) {
      return false
    }
    const sourceTelephone = draftSourceTelephone.trim()
    const nextTelephone = refreshedTelephone.trim()
    return sourceTelephone.length === 0 || nextTelephone.length === 0 || sourceTelephone === nextTelephone
  }

  private static normalizeSnCode(value: string): string {
    return value.trim().replace(/:/g, '').toUpperCase()
  }
}
