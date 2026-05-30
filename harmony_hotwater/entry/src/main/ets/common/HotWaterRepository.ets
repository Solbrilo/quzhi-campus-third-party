import http from '@ohos.net.http'
import { DashboardSnapshot, HotWaterConfig, OperationResult } from './HotWaterModels'
import { HotWaterStore } from './HotWaterStore'

const BASE_URL: string = 'https://v3-api.china-qzxy.cn'
const DEVICE_BUSY_ERROR_CODE: number = 307
const DEVICE_BUSY_BY_OTHER_MESSAGE: string = '已有他人正在使用这台设备，请稍后再试。'

interface RequestHeaders {
  Accept?: string
  'User-Agent'?: string
  'Content-Type'?: string
}

export class HotWaterRepository {
  private store: HotWaterStore

  constructor(store: HotWaterStore) {
    this.store = store
  }

  async check(config: HotWaterConfig): Promise<OperationResult> {
    this.ensureConfig(config)
    const snapshot = await this.refreshSnapshot(config)
    const result = new OperationResult()
    result.title = '余额已刷新'
    result.summary = this.buildSummary(snapshot)
    result.details = ''
    result.snapshot = snapshot
    return result
  }

  async open(config: HotWaterConfig): Promise<OperationResult> {
    this.ensureConfig(config)
    const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
    if (this.isDeviceBusyByOther(statusText)) {
      return await this.buildDeviceBusyResult(config, statusText)
    }

    const openResponseText = await this.postForm('/order/ali/tcpDevice/downRate/rateOrder', this.buildOpenFormString(config))
    if (this.isDeviceBusyByOther(openResponseText)) {
      return await this.buildDeviceBusyResult(config, openResponseText)
    }

    let orderNo = this.extractFirstOrEmpty(openResponseText, 'orderNo')
    let success = false
    let busyByOtherText = ''

    for (let index = 0; index < 5; index++) {
      await this.sleep(2000)
      const openResultText = await this.postForm('/order/tcpDevice/query/downRateResult', this.buildFormString(config, orderNo))
      if (this.isDeviceBusyByOther(openResultText)) {
        busyByOtherText = openResultText
        break
      }
      if (orderNo.length === 0) {
        orderNo = this.extractFirstOrEmpty(openResultText, 'orderNo')
      }
      if (this.extractBoolean(openResultText, 'isSuccess') || this.extractErrorCode(openResultText) == 0) {
        success = true
        break
      }
    }

    if (busyByOtherText.length > 0) {
      return await this.buildDeviceBusyResult(config, busyByOtherText)
    }

    if (orderNo.length > 0) {
      await this.store.saveOrderNo(orderNo)
    }

    const snapshot = await this.refreshSnapshot(config)
    const result = new OperationResult()
    result.title = success ? '开热水完成' : '开热水请求已发送'
    result.summary = success ? `热水已开启。\n余额 ${snapshot.balanceLabel} 元` : this.buildSummary(snapshot)
    result.details = openResponseText
    result.snapshot = snapshot
    return result
  }

  async close(config: HotWaterConfig): Promise<OperationResult> {
    this.ensureConfig(config)
    const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
    if (this.isDeviceBusyByOther(statusText)) {
      return await this.buildDeviceBusyResult(config, statusText)
    }

    let orderNo = this.extractFirstOrEmpty(statusText, 'orderNo')
    if (orderNo.length === 0) {
      orderNo = await this.store.loadOrderNo()
    }
    if (orderNo.length === 0) {
      throw new Error('没有找到可关闭的订单号，请先开一次热水。')
    }

    const closeResponseText = await this.postForm('/order/tcpDevice/closeOrder', this.buildFormString(config, orderNo))
    const closeAccepted = this.extractSuccess(closeResponseText) || this.extractErrorCode(closeResponseText) == 0
    if (closeAccepted) {
      await this.store.clearOrderNo()
    }

    let success = false
    for (let index = 0; index < 5; index++) {
      await this.sleep(2000)
      const closeResultText = await this.postForm('/order/tcpDevice/closeOrder/result/query', this.buildFormString(config, orderNo))
      if (this.extractSuccess(closeResultText) && this.extractErrorCode(closeResultText) == 0) {
        success = true
        await this.store.clearOrderNo()
        break
      }
    }

    const snapshot = closeAccepted || success
      ? await this.refreshSnapshotAfterClose(config, success ? '热水已关闭。' : '关热水请求已发送，稍后可刷新确认。')
      : await this.refreshSnapshot(config)

    const result = new OperationResult()
    result.title = success ? '关热水完成' : '关热水请求已发送'
    result.summary = success ? `热水已关闭。\n余额 ${snapshot.balanceLabel} 元` : this.buildSummary(snapshot)
    result.details = closeResponseText
    result.snapshot = snapshot
    return result
  }

  private async refreshSnapshot(config: HotWaterConfig): Promise<DashboardSnapshot> {
    const userInfoText = await this.get('/user/info', this.buildUserInfoQuery(config))
    const deviceInfoText = await this.get('/device/info/mac', this.buildDeviceInfoQuery(config))
    const balanceText = await this.get('/account/wallet/money', this.buildBalanceQuery(config))
    const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
    return await this.buildSnapshot(config, userInfoText, deviceInfoText, balanceText, statusText)
  }

  private async refreshSnapshotAfterClose(config: HotWaterConfig, forcedStatus: string): Promise<DashboardSnapshot> {
    const userInfoText = await this.get('/user/info', this.buildUserInfoQuery(config))
    const deviceInfoText = await this.get('/device/info/mac', this.buildDeviceInfoQuery(config))
    const balanceText = await this.get('/account/wallet/money', this.buildBalanceQuery(config))
    const snapshot = this.snapshotFromTexts(config, userInfoText, deviceInfoText, balanceText)
    snapshot.statusLabel = forcedStatus
    snapshot.orderNo = ''
    snapshot.hasCachedOrder = false
    return snapshot
  }

  private async refreshSnapshotWithStatus(config: HotWaterConfig, statusText: string): Promise<DashboardSnapshot> {
    const userInfoText = await this.get('/user/info', this.buildUserInfoQuery(config))
    const deviceInfoText = await this.get('/device/info/mac', this.buildDeviceInfoQuery(config))
    const balanceText = await this.get('/account/wallet/money', this.buildBalanceQuery(config))
    return await this.buildSnapshot(config, userInfoText, deviceInfoText, balanceText, statusText)
  }

  private async buildDeviceBusyResult(config: HotWaterConfig, statusText: string): Promise<OperationResult> {
    await this.store.clearOrderNo()
    let snapshot: DashboardSnapshot
    try {
      snapshot = await this.refreshSnapshotWithStatus(config, statusText)
    } catch (error) {
      snapshot = this.fallbackSnapshot(config, DEVICE_BUSY_BY_OTHER_MESSAGE, '', true)
    }
    const result = new OperationResult()
    result.title = '设备正在使用中'
    result.summary = `${DEVICE_BUSY_BY_OTHER_MESSAGE}\n余额 ${snapshot.balanceLabel} 元`
    result.details = statusText
    result.snapshot = snapshot
    return result
  }

  private async buildSnapshot(
    config: HotWaterConfig,
    userInfoText: string,
    deviceInfoText: string,
    balanceText: string,
    statusText: string
  ): Promise<DashboardSnapshot> {
    const snapshot = this.snapshotFromTexts(config, userInfoText, deviceInfoText, balanceText)
    const busyByOther = this.isDeviceBusyByOther(statusText)
    const extractedOrderNo = this.extractFirstOrEmpty(statusText, 'orderNo')
    if (busyByOther) {
      await this.store.clearOrderNo()
    } else if (extractedOrderNo.length > 0) {
      const ignoreRecovered = await this.store.shouldIgnoreRecoveredOrder()
      if (!ignoreRecovered) {
        await this.store.saveOrderNo(extractedOrderNo)
      }
    }
    const cachedOrderNo = busyByOther ? '' : await this.store.loadOrderNo()
    snapshot.orderNo = cachedOrderNo
    snapshot.hasCachedOrder = cachedOrderNo.length > 0
    snapshot.isDeviceBusyByOther = busyByOther
    if (busyByOther) {
      snapshot.statusLabel = DEVICE_BUSY_BY_OTHER_MESSAGE
    } else if (cachedOrderNo.length > 0) {
      snapshot.statusLabel = '已缓存订单号，点击主按钮将尝试关热水。'
    } else if (this.extractSuccess(statusText)) {
      snapshot.statusLabel = '当前没有缓存订单号，点击主按钮将尝试开热水。'
    } else {
      const errorMessage = this.extractFirstOrEmpty(statusText, 'errorMessage')
      snapshot.statusLabel = errorMessage.length > 0 ? errorMessage : '状态待确认，建议先刷新余额。'
    }
    return snapshot
  }

  private snapshotFromTexts(
    config: HotWaterConfig,
    userInfoText: string,
    deviceInfoText: string,
    balanceText: string
  ): DashboardSnapshot {
    const snapshot = new DashboardSnapshot()
    const balanceYuan = this.extractMoneyYuan(balanceText)
    const telephone = this.extractFirstOrEmpty(userInfoText, 'telephone')
    const deviceName = this.extractFirstOrEmpty(deviceInfoText, 'deviceName')
    const areaName = this.extractFirstOrEmpty(deviceInfoText, 'areaName')
    const buildingName = this.extractFirstOrEmpty(deviceInfoText, 'buildingName')
    const roomName = this.extractFirstOrEmpty(deviceInfoText, 'roomName')
    const location = this.joinLocation(areaName, buildingName, roomName)
    const deviceLocation = this.extractFirstOrEmpty(deviceInfoText, 'deviceLocation')

    snapshot.balanceYuan = balanceYuan
    snapshot.balanceLabel = this.formatYuan(balanceYuan)
    snapshot.userLabel = HotWaterConfig.maskMiddle(telephone.length > 0 ? telephone : config.telephone, 3, 4)
    snapshot.deviceName = deviceName.length > 0 ? deviceName : '热水设备待识别'
    snapshot.locationLabel = location.length > 0 ? location : (deviceLocation.length > 0 ? deviceLocation : '位置待刷新')
    return snapshot
  }

  private buildSummary(snapshot: DashboardSnapshot): string {
    return `${snapshot.statusLabel}\n余额 ${snapshot.balanceLabel} 元`
  }

  private fallbackSnapshot(
    config: HotWaterConfig,
    statusLabel: string,
    orderNo: string,
    isDeviceBusyByOther: boolean
  ): DashboardSnapshot {
    const snapshot = new DashboardSnapshot()
    snapshot.balanceLabel = '--'
    snapshot.userLabel = HotWaterConfig.maskMiddle(config.telephone, 3, 4)
    snapshot.deviceName = '热水设备已连接'
    snapshot.locationLabel = '打开应用后可自动刷新宿舍位置'
    snapshot.statusLabel = statusLabel
    snapshot.orderNo = orderNo
    snapshot.hasCachedOrder = orderNo.length > 0
    snapshot.isDeviceBusyByOther = isDeviceBusyByOther
    return snapshot
  }

  private ensureConfig(config: HotWaterConfig): void {
    if (!config.isComplete()) {
      throw new Error('配置还不完整，请先导入 .chls 或补全字段。')
    }
  }

  private buildUserInfoQuery(config: HotWaterConfig): string {
    return 'projectId=' + encodeURIComponent(config.projectId.toString()) +
      '&userId=' + encodeURIComponent(config.userId.toString()) +
      '&loginCode=' + encodeURIComponent(config.loginCode)
  }

  private buildDeviceInfoQuery(config: HotWaterConfig): string {
    return this.buildBaseQuery(config) +
      '&macAddress=' + encodeURIComponent(config.snCode) +
      '&phoneSystem=' + encodeURIComponent(config.phoneSystem.length > 0 ? config.phoneSystem : 'Harmony') +
      '&version=' + encodeURIComponent(config.version.length > 0 ? config.version : '0.0.1')
  }

  private buildBalanceQuery(config: HotWaterConfig): string {
    return 'projectId=' + encodeURIComponent(config.projectId.toString()) +
      '&userId=' + encodeURIComponent(config.userId.toString()) +
      '&loginCode=' + encodeURIComponent(config.loginCode) +
      '&accountId=' + encodeURIComponent(config.accountId.toString()) +
      '&telPhone=' + encodeURIComponent(config.telephone) +
      '&phoneSystem=' + encodeURIComponent(config.phoneSystem.length > 0 ? config.phoneSystem : 'Harmony') +
      '&version=' + encodeURIComponent(config.version.length > 0 ? config.version : '0.0.1')
  }

  private buildBaseQuery(config: HotWaterConfig): string {
    return 'projectId=' + encodeURIComponent(config.projectId.toString()) +
      '&userId=' + encodeURIComponent(config.userId.toString()) +
      '&loginCode=' + encodeURIComponent(config.loginCode) +
      '&accountId=' + encodeURIComponent(config.accountId.toString()) +
      '&telephone=' + encodeURIComponent(config.telephone)
  }

  private buildFormString(config: HotWaterConfig, orderNo: string = ''): string {
    let form = 'accountId=' + encodeURIComponent(config.accountId.toString()) +
      '&loginCode=' + encodeURIComponent(config.loginCode) +
      '&phoneSystem=' + encodeURIComponent(config.phoneSystem.length > 0 ? config.phoneSystem : 'Harmony') +
      '&projectId=' + encodeURIComponent(config.projectId.toString()) +
      '&snCode=' + encodeURIComponent(config.snCode) +
      '&telephone=' + encodeURIComponent(config.telephone) +
      '&userId=' + encodeURIComponent(config.userId.toString()) +
      '&version=' + encodeURIComponent(config.version.length > 0 ? config.version : '0.0.1')
    if (orderNo.length > 0) {
      form += '&orderNo=' + encodeURIComponent(orderNo)
    }
    return form
  }

  private buildOpenFormString(config: HotWaterConfig): string {
    return this.buildFormString(config) + '&orderAuthCode='
  }

  private async get(endpoint: string, queryString: string): Promise<string> {
    return await this.request(`${BASE_URL}${endpoint}?${queryString}`, http.RequestMethod.GET, '', '')
  }

  private async postForm(endpoint: string, formBody: string): Promise<string> {
    return await this.request(
      `${BASE_URL}${endpoint}`,
      http.RequestMethod.POST,
      formBody,
      'application/x-www-form-urlencoded;charset=UTF-8'
    )
  }

  private async request(
    url: string,
    method: http.RequestMethod,
    extraData: string,
    contentType: string
  ): Promise<string> {
    const client = http.createHttp()
    try {
      let headers: RequestHeaders
      if (contentType.length > 0) {
        headers = {
          Accept: 'application/json, text/plain, */*',
          'User-Agent': 'Mozilla/5.0 (Linux; HarmonyOS) AppleWebKit/537.36 Mobile',
          'Content-Type': contentType
        }
      } else {
        headers = {
          Accept: 'application/json, text/plain, */*',
          'User-Agent': 'Mozilla/5.0 (Linux; HarmonyOS) AppleWebKit/537.36 Mobile'
        }
      }
      const options: http.HttpRequestOptions = {
        method: method,
        extraData: extraData,
        expectDataType: http.HttpDataType.STRING,
        header: headers
      }
      const response = await client.request(url, options)
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new Error('HTTP ' + String(response.responseCode))
      }
      return String(response.result)
    } finally {
      client.destroy()
    }
  }

  private extractSuccess(text: string): boolean {
    return text.indexOf('"success":true') >= 0
  }

  private extractErrorCode(text: string): number {
    const match = text.match(/"errorCode"\s*:\s*(-?\d+)/)
    return match && match[1] ? Number(match[1]) : -1
  }

  private extractFirstOrEmpty(text: string, key: string): string {
    const pattern = new RegExp('"' + key + '"\\s*:\\s*(?:"([^"]*)"|([^",}\\]\\s]+))', 'i')
    const match = text.match(pattern)
    const value = match && match[1] !== undefined ? String(match[1]) : (match && match[2] !== undefined ? String(match[2]) : '')
    return value === 'null' ? '' : value
  }

  private extractBoolean(text: string, key: string): boolean {
    const pattern = new RegExp('"' + key + '"\\s*:\\s*(true|false)', 'i')
    const match = text.match(pattern)
    return !!(match && match[1] && match[1].toLowerCase() === 'true')
  }

  private isDeviceBusyByOther(text: string): boolean {
    if (this.extractErrorCode(text) !== DEVICE_BUSY_ERROR_CODE) {
      return false
    }
    const isOwner = this.extractBooleanValue(text, 'isOwner')
    const errorMessage = this.extractFirstOrEmpty(text, 'errorMessage')
    return isOwner === false || errorMessage.indexOf('设备正在使用') >= 0
  }

  private extractBooleanValue(text: string, key: string): boolean | null {
    const pattern = new RegExp('"' + key + '"\\s*:\\s*(true|false)', 'i')
    const match = text.match(pattern)
    if (match && match[1]) {
      return match[1].toLowerCase() === 'true'
    }
    return null
  }

  private extractMoneyYuan(text: string): number {
    const stringValue = this.extractFirstOrEmpty(text, 'accountRealMoneyStr')
    if (stringValue.length > 0) {
      const parsed = Number(stringValue)
      if (!Number.isNaN(parsed)) {
        return parsed
      }
    }
    const rawValue = Number(this.extractFirstOrEmpty(text, 'accountRealMoney'))
    if (Number.isNaN(rawValue)) {
      return 0
    }
    return rawValue / 1000
  }

  private joinLocation(areaName: string, buildingName: string, roomName: string): string {
    let result = ''
    if (areaName.length > 0) {
      result += areaName
    }
    if (buildingName.length > 0) {
      result += result.length > 0 ? ' ' + buildingName : buildingName
    }
    if (roomName.length > 0) {
      result += result.length > 0 ? ' ' + roomName : roomName
    }
    return result
  }

  private formatYuan(value: number): string {
    return value.toFixed(2)
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve()
      }, ms)
    })
  }
}
