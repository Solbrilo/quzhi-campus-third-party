export const HOT_WATER_PREAUTHORIZATION_YUAN: number = 5

export class ConsumptionSettlement {
  private static readonly MONEY_SCALE: number = 1000
  private static readonly BALANCE_CHANGE_EPSILON_YUAN: number = 0.0005
  private static readonly BALANCE_FALLBACK_MAX_AGE_MS: number = 10 * 60 * 1000

  static isKnownBalance(value: number): boolean {
    return Number.isFinite(value) && value >= 0
  }

  static recoverStartBalance(activeBalanceYuan: number): number {
    return ConsumptionSettlement.roundYuan(activeBalanceYuan + HOT_WATER_PREAUTHORIZATION_YUAN)
  }

  static expectedActiveBalance(startBalanceYuan: number): number {
    return ConsumptionSettlement.roundYuan(startBalanceYuan - HOT_WATER_PREAUTHORIZATION_YUAN)
  }

  static calculateConsumption(startBalanceYuan: number, settledBalanceYuan: number): number {
    if (!ConsumptionSettlement.isKnownBalance(startBalanceYuan) ||
      !ConsumptionSettlement.isKnownBalance(settledBalanceYuan)) {
      return -1
    }
    const consumptionYuan = ConsumptionSettlement.roundYuan(startBalanceYuan - settledBalanceYuan)
    return consumptionYuan >= ConsumptionSettlement.BALANCE_CHANGE_EPSILON_YUAN ? consumptionYuan : -1
  }

  static hasSettledBalanceChanged(activeBalanceYuan: number, candidateBalanceYuan: number): boolean {
    if (!Number.isFinite(activeBalanceYuan) || !Number.isFinite(candidateBalanceYuan)) {
      return false
    }
    if (activeBalanceYuan < 0 || candidateBalanceYuan < 0) {
      return false
    }
    return Math.abs(candidateBalanceYuan - activeBalanceYuan) >=
      ConsumptionSettlement.BALANCE_CHANGE_EPSILON_YUAN
  }

  static balancesMatch(expectedBalanceYuan: number, candidateBalanceYuan: number): boolean {
    if (!ConsumptionSettlement.isKnownBalance(expectedBalanceYuan) ||
      !ConsumptionSettlement.isKnownBalance(candidateBalanceYuan)) {
      return false
    }
    return Math.abs(expectedBalanceYuan - candidateBalanceYuan) <
      ConsumptionSettlement.BALANCE_CHANGE_EPSILON_YUAN
  }

  static matchesExplicitConsumption(
    startBalanceYuan: number,
    explicitConsumptionYuan: number,
    candidateBalanceYuan: number
  ): boolean {
    if (!ConsumptionSettlement.isKnownBalance(startBalanceYuan) ||
      !Number.isFinite(explicitConsumptionYuan) || explicitConsumptionYuan < 0) {
      return false
    }
    const expectedBalanceYuan = ConsumptionSettlement.roundYuan(
      startBalanceYuan - explicitConsumptionYuan
    )
    return ConsumptionSettlement.balancesMatch(expectedBalanceYuan, candidateBalanceYuan)
  }

  static calculateSettledConsumption(
    startBalanceYuan: number,
    activeBalanceYuan: number,
    settledBalanceYuan: number
  ): number {
    if (!ConsumptionSettlement.hasSettledBalanceChanged(activeBalanceYuan, settledBalanceYuan)) {
      return -1
    }
    return ConsumptionSettlement.calculateTrustedConsumption(startBalanceYuan, settledBalanceYuan)
  }

  static resolveConsumption(
    explicitConsumptionYuan: number,
    startBalanceYuan: number,
    settledBalanceYuan: number,
    settledBalanceIsTrusted: boolean
  ): number {
    if (Number.isFinite(explicitConsumptionYuan) &&
      explicitConsumptionYuan >= ConsumptionSettlement.BALANCE_CHANGE_EPSILON_YUAN) {
      return ConsumptionSettlement.roundYuan(explicitConsumptionYuan)
    }
    if (!settledBalanceIsTrusted) {
      return -1
    }
    return ConsumptionSettlement.calculateTrustedConsumption(startBalanceYuan, settledBalanceYuan)
  }

  static canUseBalanceFallback(
    endedAtMillis: number,
    nowMillis: number,
    isLatestSettlement: boolean,
    balanceFallbackEligible: boolean,
    hasLocalActivity: boolean,
    remoteHasNoActiveOrder: boolean
  ): boolean {
    if (!isLatestSettlement || !balanceFallbackEligible || hasLocalActivity || !remoteHasNoActiveOrder) {
      return false
    }
    return ConsumptionSettlement.isFreshSettlement(endedAtMillis, nowMillis)
  }

  static isFreshSettlement(endedAtMillis: number, nowMillis: number): boolean {
    if (!Number.isFinite(endedAtMillis) || !Number.isFinite(nowMillis) || endedAtMillis <= 0) {
      return false
    }
    const ageMillis = nowMillis - endedAtMillis
    return ageMillis >= 0 && ageMillis <= ConsumptionSettlement.BALANCE_FALLBACK_MAX_AGE_MS
  }

  static shouldKeepConfirmedBalanceGuard(
    startBalanceYuan: number,
    confirmedConsumptionYuan: number,
    candidateBalanceYuan: number,
    endedAtMillis: number,
    nowMillis: number,
    hasLocalActivity: boolean
  ): boolean {
    if (hasLocalActivity || !ConsumptionSettlement.isFreshSettlement(endedAtMillis, nowMillis) ||
      !Number.isFinite(confirmedConsumptionYuan) || confirmedConsumptionYuan < 0) {
      return false
    }
    if (!ConsumptionSettlement.isKnownBalance(startBalanceYuan)) {
      return true
    }
    return !ConsumptionSettlement.matchesExplicitConsumption(
      startBalanceYuan,
      confirmedConsumptionYuan,
      candidateBalanceYuan
    )
  }

  private static calculateTrustedConsumption(startBalanceYuan: number, settledBalanceYuan: number): number {
    if (!ConsumptionSettlement.isKnownBalance(startBalanceYuan) ||
      !ConsumptionSettlement.isKnownBalance(settledBalanceYuan)) {
      return -1
    }
    const consumptionYuan = ConsumptionSettlement.roundYuan(startBalanceYuan - settledBalanceYuan)
    return consumptionYuan >= 0 ? consumptionYuan : -1
  }

  private static roundYuan(value: number): number {
    return Math.round(value * ConsumptionSettlement.MONEY_SCALE) / ConsumptionSettlement.MONEY_SCALE
  }
}
