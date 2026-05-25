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
}

export class OperationResult {
  title: string = ''
  summary: string = ''
  details: string = ''
  snapshot: DashboardSnapshot = new DashboardSnapshot()
}
