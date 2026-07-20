/**
 * Carries one-shot results between the configuration page and child routes.
 * Scanner intentionally never writes the persisted account configuration.
 */
export class ConfigFlowBridge {
  private static pendingSnCode: string = ''
  private static loginConfigChanged: boolean = false

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

  /**
   * A login refresh owns the account fields, while an unsaved device scan still belongs to the
   * configuration form. Keep that one field so returning from login cannot overwrite it.
   */
  static resolveSnCodeAfterLogin(
    currentSnCode: string,
    snCodeDirty: boolean,
    persistedSnCode: string
  ): string {
    return snCodeDirty ? ConfigFlowBridge.normalizeSnCode(currentSnCode) :
      ConfigFlowBridge.normalizeSnCode(persistedSnCode)
  }

  static hasPendingSnCodeAfterLogin(
    currentSnCode: string,
    snCodeDirty: boolean,
    persistedSnCode: string
  ): boolean {
    return snCodeDirty &&
      ConfigFlowBridge.normalizeSnCode(currentSnCode) !== ConfigFlowBridge.normalizeSnCode(persistedSnCode)
  }

  private static normalizeSnCode(value: string): string {
    return value.trim().replace(/:/g, '').toUpperCase()
  }
}
