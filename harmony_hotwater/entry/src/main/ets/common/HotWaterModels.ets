export class HotWaterConfig {
  projectId: number = 0
  userId: number = 0
  accountId: number = 0
  loginCode: string = ''
  telephone: string = ''
  snCode: string = ''
  macAddress: string = ''
  phoneSystem: string = ''
  version: string = ''

  isComplete(): boolean {
    return this.projectId > 0 &&
      this.userId > 0 &&
      this.accountId > 0 &&
      this.loginCode.length > 0 &&
      this.telephone.length > 0 &&
      this.snCode.length > 0
  }

  static maskMiddle(value: string, left: number = 6, right: number = 4): string {
    if (value.length === 0) {
      return '-'
    }
    if (value.length <= left + right) {
      let masked: string = ''
      for (let index = 0; index < value.length; index++) {
        masked += '*'
      }
      return masked
    }
    let middle: string = ''
    for (let index = 0; index < value.length - left - right; index++) {
      middle += '*'
    }
    return value.substring(0, left) + middle + value.substring(value.length - right)
  }

  static hasSameRuntimeIdentity(left: HotWaterConfig, right: HotWaterConfig): boolean {
    return left.projectId === right.projectId &&
      left.userId === right.userId &&
      left.accountId === right.accountId &&
      left.telephone === right.telephone &&
      left.snCode === right.snCode
  }
}

export class RuntimeDeviceInfo {
  systemLabel: string = 'HarmonyOS 设备'
  phoneSystem: string = 'Harmony'
  version: string = '0.0.1'
  macAddressLabel: string = '系统已隐藏'
}

export class DashboardSnapshot {
  balanceYuan: number = 0
  balanceLabel: string = '--'
  userLabel: string = '未导入账号'
  deviceName: string = '热水设备待刷新'
  locationLabel: string = '先导入配置再刷新'
  statusLabel: string = '当前配置还不完整。'
  orderNo: string = ''
  hasCachedOrder: boolean = false
  isDeviceBusyByOther: boolean = false
  remoteOrderConfirmed: boolean = false
}

export class OperationResult {
  title: string = ''
  summary: string = ''
  details: string = ''
  snapshot: DashboardSnapshot = new DashboardSnapshot()
  consumptionYuan: number = -1
  usageDurationMillis: number = -1
}

export class UsageSession {
  startedAtMillis: number = 0
  startBalanceYuan: number = -1
  startBalanceIsBeforePreauthorization: boolean = false
  provisionalOpening: boolean = false

  isExpiredProvisionalOpening(nowMillis: number, maxAgeMillis: number): boolean {
    if (!this.provisionalOpening || this.startedAtMillis <= 0 || maxAgeMillis < 0) {
      return false
    }
    const ageMillis = nowMillis - this.startedAtMillis
    return ageMillis >= maxAgeMillis
  }
}

export class PendingSettlement {
  orderNo: string = ''
  recordId: number = 0
  startedAtMillis: number = 0
  endedAtMillis: number = 0
  startBalanceYuan: number = -1
  activeBalanceYuan: number = -1
  confirmedConsumptionYuan: number = -1
  balanceFallbackEligible: boolean = false
}

export class ConsumptionRecord {
  id: number = 0
  orderNo: string = ''
  startedAtMillis: number = 0
  endedAtMillis: number = 0
  consumptionYuan: number = -1
  usageDurationMillis: number = 0
  settlementVerified: boolean = false

  static canReplaceSettlement(existingVerified: boolean, incomingVerified: boolean): boolean {
    return !existingVerified || incomingVerified
  }

  getStartedAtLabel(): string {
    const date = new Date(this.startedAtMillis)
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hours = date.getHours().toString().padStart(2, '0')
    const minutes = date.getMinutes().toString().padStart(2, '0')
    return `${month}-${day} ${hours}:${minutes}`
  }

  getDurationLabel(): string {
    return ConsumptionRecord.formatDuration(this.usageDurationMillis)
  }

  getConsumptionLabel(): string {
    if (this.consumptionYuan < 0) {
      return '待同步'
    }
    const normalizedConsumptionYuan = Math.round(this.consumptionYuan * 1000) / 1000
    if (normalizedConsumptionYuan > 0 && normalizedConsumptionYuan < 0.01) {
      return '< ¥0.01'
    }
    return `¥ ${normalizedConsumptionYuan.toFixed(2)}`
  }

  static formatDuration(durationMillis: number): string {
    const totalSeconds = Math.max(0, Math.floor(durationMillis / 1000))
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
    }
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  }
}
