import http from '@ohos.net.http'
import { ArkTSUtils } from '@kit.ArkTS'
import {
  ConsumptionRecord,
  DashboardSnapshot,
  HotWaterConfig,
  OperationResult,
  PendingSettlement,
  UsageSession
} from './HotWaterModels'
import { HotWaterStore } from './HotWaterStore'
import { ConsumptionSettlement } from './ConsumptionSettlement'
import { HotWaterPayload } from './HotWaterPayload'

const BASE_URL: string = 'https://v3-api.china-qzxy.cn'
const DEVICE_BUSY_ERROR_CODE: number = 307
const DEVICE_OFFLINE_ERROR_CODE: number = 306
const OPEN_RESULT_NETWORK_ERROR_CODE: number = 1
const DEVICE_OFFLINE_MESSAGE: string = '设备不在线'
const DEVICE_OFFLINE_MESSAGE_ALT: string = '设备离线'
const OPEN_RESULT_NETWORK_SLOW_MESSAGE: string = '网络通讯慢'
const DEVICE_OFFLINE_STATUS_MESSAGE: string = '热水器不在线，请确认寝室供电后重试。'
const DEVICE_BUSY_BY_OTHER_MESSAGE: string = '已有他人正在使用这台设备，请稍后再试。'
const PENDING_CLOSE_OPEN_GUARD_MS: number = 15 * 1000
const PROVISIONAL_OPENING_MAX_AGE_MS: number = 2 * 60 * 1000
// Keep a conservative guard against a transient empty-order response. The server's
// observed idle auto-close window is about five minutes.
const MIN_AUTO_CLOSE_USAGE_AGE_MS: number = 5 * 60 * 1000
const DEVICE_WORKFLOW_LOCK = ArkTSUtils.locks.AsyncLock.request('cn.qzxy.hotwater.device-workflow')

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
    return await DEVICE_WORKFLOW_LOCK.lockAsync(async () => await this.checkExclusive(config))
  }

  private async checkExclusive(config: HotWaterConfig): Promise<OperationResult> {
    this.ensureConfig(config)
    const snapshot = await this.refreshSnapshot(config)
    await this.tryReconcilePendingSettlements(config, snapshot)
    await this.applyPendingBalanceGuard(snapshot)
    const result = new OperationResult()
    result.title = snapshot.autoClosedOrderNo.length > 0 ? '热水已自动关闭' : '余额已刷新'
    result.summary = this.buildSummary(snapshot)
    result.details = ''
    result.snapshot = snapshot
    result.usageDurationMillis = snapshot.autoClosedUsageDurationMillis
    result.usageStartedAtMillis = snapshot.autoClosedUsageStartedAtMillis
    result.historyId = snapshot.autoClosedHistoryId
    result.automaticClose = snapshot.autoClosedOrderNo.length > 0
    return result
  }

  async open(config: HotWaterConfig): Promise<OperationResult> {
    return await DEVICE_WORKFLOW_LOCK.lockAsync(async () => await this.openExclusive(config))
  }

  private async openExclusive(config: HotWaterConfig): Promise<OperationResult> {
    this.ensureConfig(config)
    const pendingClose = await this.findLatestPendingClose()
    const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
    if (this.isDeviceBusyByOther(statusText)) {
      return await this.buildDeviceBusyResult(config, statusText)
    }

    let activeOrderNo = this.extractFirstOrEmpty(statusText, 'orderNo')
    if (pendingClose !== null) {
      if (this.isConfirmedWithoutActiveOrder(statusText)) {
        pendingClose.closeConfirmed = true
        await this.store.markPendingCloseConfirmed(pendingClose.orderNo)
      } else if (pendingClose.confirmedConsumptionYuan >= 0 && !pendingClose.closeConfirmed) {
        pendingClose.closeConfirmed = true
        await this.store.markPendingCloseConfirmed(pendingClose.orderNo)
      } else if (pendingClose.shouldBlockOpen(Date.now(), PENDING_CLOSE_OPEN_GUARD_MS)) {
        if (await this.queryCloseConfirmed(config, pendingClose.orderNo)) {
          pendingClose.closeConfirmed = true
          await this.store.markPendingCloseConfirmed(pendingClose.orderNo)
        } else if (activeOrderNo.length === 0 || pendingClose.matchesOrder(activeOrderNo)) {
          return this.buildPendingCloseGateResult(config, statusText)
        }
      }

      if (pendingClose.closeConfirmed && pendingClose.matchesOrder(activeOrderNo)) {
        activeOrderNo = ''
      }
    }

    if (activeOrderNo.length > 0) {
      await this.store.saveOrderNo(activeOrderNo)
      await this.store.confirmUsageSession()
      let snapshot: DashboardSnapshot
      try {
        snapshot = await this.refreshSnapshotWithStatus(config, statusText)
      } catch (error) {
        if (HotWaterPayload.isCredentialExpiredError(error)) {
          throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
        }
        await this.store.startUsageSessionIfAbsent(-1, false)
        snapshot = this.fallbackSnapshot(
          config,
          '检测到本人正在使用热水，已恢复当前订单。',
          activeOrderNo,
          false
        )
      }
      await this.tryReconcilePendingSettlements(config, snapshot)
      const result = new OperationResult()
      result.title = '热水正在使用中'
      result.summary = `检测到本人已有活动订单，没有重复开水。\n余额 ${snapshot.balanceLabel} 元`
      result.details = statusText
      result.snapshot = snapshot
      return result
    }

    await this.store.clearUsageSession()
    let openingBalanceYuan: number = -1
    try {
      openingBalanceYuan = await this.fetchBalanceYuan(config)
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      // 开水前余额只用于提高结算精度，读取失败不能阻断设备操作。
    }
    await this.store.startUsageSessionIfAbsent(
      openingBalanceYuan,
      ConsumptionSettlement.isKnownBalance(openingBalanceYuan),
      true
    )
    let openResponseText: string
    try {
      openResponseText = await this.postForm(
        '/order/ali/tcpDevice/downRate/rateOrder',
        this.buildOpenFormString(config)
      )
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      await this.store.clearUsageSession()
      throw new Error(String(error))
    }
    if (this.isDeviceBusyByOther(openResponseText)) {
      return await this.buildDeviceBusyResult(config, openResponseText)
    }

    let orderNo = this.extractFirstOrEmpty(openResponseText, 'orderNo')
    let success = false
    let deviceOnlineOverride: boolean | null = null
    let offlineProbeSeen = false
    let offlineProbeOrderNo = ''
    let busyByOtherText = ''

    for (let index = 0; index < 5; index++) {
      await this.sleep(2000)
      let openResultText: string
      try {
        openResultText = await this.postForm(
          '/order/tcpDevice/query/downRateResult',
          this.buildFormString(config, orderNo)
        )
      } catch (error) {
        if (HotWaterPayload.isCredentialExpiredError(error)) {
          throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
        }
        continue
      }
      if (this.isDeviceBusyByOther(openResultText)) {
        busyByOtherText = openResultText
        break
      }
      const responseOrderNo = this.extractFirstOrEmpty(openResultText, 'orderNo').trim()
      const probeState = this.openResultDeviceOnlineState(openResultText, orderNo)
      if (probeState === false) {
        deviceOnlineOverride = false
        offlineProbeSeen = true
        if (offlineProbeOrderNo.length === 0) {
          offlineProbeOrderNo = orderNo.length > 0 ? orderNo : responseOrderNo
        }
      } else if (probeState === true) {
        const matchesOfflineProbe = !offlineProbeSeen ||
          (offlineProbeOrderNo.length > 0 && responseOrderNo === offlineProbeOrderNo)
        const confirmedResult = this.isConfirmedOpenResult(openResultText, orderNo)
        if (confirmedResult && matchesOfflineProbe) {
          deviceOnlineOverride = true
        }
      }
      if (orderNo.length === 0) {
        orderNo = responseOrderNo
      }
      if (this.isOpenResultSuccessful(openResultText, orderNo)) {
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
    if (success || orderNo.length > 0) {
      await this.store.confirmUsageSession()
    }

    let snapshotWithUsageSession: DashboardSnapshot
    try {
      snapshotWithUsageSession = await this.refreshSnapshot(config, deviceOnlineOverride)
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      snapshotWithUsageSession = this.fallbackSnapshot(
        config,
        success ? '热水已开启，可以直接关闭。' : '开热水请求已发送，等待设备确认。',
        orderNo,
        false,
        deviceOnlineOverride
      )
    }
    if (!snapshotWithUsageSession.hasCachedOrder) {
      await this.store.clearUsageSession()
    }
    await this.tryReconcilePendingSettlements(config, snapshotWithUsageSession)
    const deviceOffline = snapshotWithUsageSession.isDeviceOnline === false
    const result = new OperationResult()
    result.title = deviceOffline ? '热水器不在线' : (success ? '开热水完成' : '开热水请求已发送')
    result.summary = deviceOffline
      ? `${DEVICE_OFFLINE_STATUS_MESSAGE}\n余额 ${snapshotWithUsageSession.balanceLabel} 元`
      : (success ? `热水已开启。\n余额 ${snapshotWithUsageSession.balanceLabel} 元` : this.buildSummary(snapshotWithUsageSession))
    result.details = openResponseText
    result.snapshot = snapshotWithUsageSession
    return result
  }

  async close(config: HotWaterConfig): Promise<OperationResult> {
    return await DEVICE_WORKFLOW_LOCK.lockAsync(async () => await this.closeExclusive(config))
  }

  private async closeExclusive(config: HotWaterConfig): Promise<OperationResult> {
    this.ensureConfig(config)
    let usageSession = await this.store.loadUsageSession()
    const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
    if (this.isDeviceBusyByOther(statusText)) {
      return await this.buildDeviceBusyResult(config, statusText)
    }

    const remoteOrderNo = this.extractFirstOrEmpty(statusText, 'orderNo')
    let orderNo = remoteOrderNo
    if (orderNo.length === 0) {
      orderNo = await this.store.loadOrderNo()
    }
    if (orderNo.length === 0) {
      throw new Error('没有找到可关闭的订单号，请先开一次热水。')
    }
    if (remoteOrderNo.length > 0) {
      await this.store.confirmUsageSession()
    }

    let activeBalanceYuan: number = -1
    try {
      activeBalanceYuan = await this.fetchBalanceYuan(config)
      if (!usageSession) {
        usageSession = await this.store.startUsageSessionIfAbsent(
          ConsumptionSettlement.recoverStartBalance(activeBalanceYuan),
          true
        )
      } else if (!usageSession.startBalanceIsBeforePreauthorization) {
        usageSession = await this.store.updateUsageSessionStartBalance(
          ConsumptionSettlement.recoverStartBalance(activeBalanceYuan)
        )
      }
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      // 余额预读失败不阻断关水；结算刷新仍会继续尝试。
    }
    if (!usageSession) {
      usageSession = await this.store.startUsageSessionIfAbsent(-1, false)
    }
    if (usageSession && !usageSession.startBalanceIsBeforePreauthorization &&
      ConsumptionSettlement.isKnownBalance(usageSession.startBalanceYuan)) {
      usageSession = await this.store.updateUsageSessionStartBalance(
        ConsumptionSettlement.recoverStartBalance(usageSession.startBalanceYuan)
      )
    }
    if (!usageSession) {
      usageSession = await this.store.startUsageSessionIfAbsent(-1, false)
    }
    if (activeBalanceYuan < 0 && usageSession && usageSession.startBalanceIsBeforePreauthorization) {
      activeBalanceYuan = ConsumptionSettlement.expectedActiveBalance(usageSession.startBalanceYuan)
    }

    if (remoteOrderNo.length === 0 && this.isConfirmedWithoutActiveOrder(statusText) &&
      ConsumptionSettlement.canAutoCloseUsage(
        orderNo,
        usageSession,
        true,
        Date.now(),
        MIN_AUTO_CLOSE_USAGE_AGE_MS
      )) {
      return await this.buildAutomaticCloseResult(
        config,
        orderNo,
        usageSession!!
      )
    }

    const closeResponseText = await this.postForm('/order/tcpDevice/closeOrder', this.buildFormString(config, orderNo))
    const closeAccepted = this.extractSuccess(closeResponseText) || this.extractErrorCode(closeResponseText) == 0
    let settlementRecord: ConsumptionRecord | null = null
    if (closeAccepted && usageSession) {
      settlementRecord = await this.persistPendingSettlementContext(
        orderNo,
        usageSession,
        activeBalanceYuan,
        Date.now()
      )
    }

    let success = false
    if (!closeAccepted) {
      for (let index = 0; index < 5; index++) {
        await this.sleep(2000)
        let closeResultText: string
        try {
          closeResultText = await this.postForm(
            '/order/tcpDevice/closeOrder/result/query',
            this.buildFormString(config, orderNo)
          )
        } catch (error) {
          if (HotWaterPayload.isCredentialExpiredError(error)) {
            throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
          }
          continue
        }
        if (this.extractSuccess(closeResultText) && this.extractErrorCode(closeResultText) == 0) {
          success = true
          break
        }
      }
    }

    const usageEnded = closeAccepted || success
    if (usageEnded && settlementRecord === null && usageSession) {
      settlementRecord = await this.persistPendingSettlementContext(
        orderNo,
        usageSession,
        activeBalanceYuan,
        Date.now(),
        success
      )
    }

    const closeStatus = success
      ? '热水已关闭，余额和账单正在同步。'
      : (closeAccepted ? '关热水请求已受理，余额和账单正在同步。' : '关热水请求尚未确认。')
    const snapshot = usageEnded
      ? this.fallbackSnapshot(config, closeStatus, '', false)
      : await this.refreshSnapshot(config)

    const result = new OperationResult()
    if (usageEnded && usageSession && settlementRecord !== null) {
      result.usageDurationMillis = Math.max(
        0,
        settlementRecord.endedAtMillis - usageSession.startedAtMillis
      )
      if (settlementRecord.settlementVerified) {
        result.consumptionYuan = settlementRecord.consumptionYuan
      }
    }

    result.title = success ? '关热水完成' : (closeAccepted ? '关热水已受理' : '关热水请求未确认')
    result.summary = usageEnded
      ? `${closeStatus}\n余额 ${snapshot.balanceLabel} 元`
      : this.buildSummary(snapshot)
    result.details = closeResponseText
    result.snapshot = snapshot
    result.historyId = settlementRecord?.id ?? 0
    result.usageStartedAtMillis = settlementRecord?.startedAtMillis ?? 0
    return result
  }

  private async refreshSnapshot(
    config: HotWaterConfig,
    deviceOnlineOverride: boolean | null = null
  ): Promise<DashboardSnapshot> {
    const userInfoText = await this.get('/user/info', this.buildUserInfoQuery(config))
    const deviceInfoText = await this.get('/device/info/mac', this.buildDeviceInfoQuery(config))
    const balanceText = await this.get('/account/wallet/money', this.buildBalanceQuery(config))
    const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
    return await this.ensureUsageSession(await this.buildSnapshot(
      config,
      userInfoText,
      deviceInfoText,
      balanceText,
      statusText,
      deviceOnlineOverride
    ))
  }

  private async persistPendingSettlementContext(
    orderNo: string,
    usageSession: UsageSession,
    activeBalanceYuan: number,
    endedAtMillis: number,
    closeConfirmed: boolean = false
  ): Promise<ConsumptionRecord> {
    const record = new ConsumptionRecord()
    record.id = endedAtMillis
    record.orderNo = orderNo
    record.startedAtMillis = usageSession.startedAtMillis
    record.endedAtMillis = endedAtMillis
    record.usageDurationMillis = Math.max(0, endedAtMillis - usageSession.startedAtMillis)
    record.consumptionYuan = -1
    record.settlementVerified = false
    const pending = new PendingSettlement()
    pending.orderNo = orderNo
    pending.recordId = record.id
    pending.startedAtMillis = record.startedAtMillis
    pending.endedAtMillis = record.endedAtMillis
    pending.startBalanceYuan = this.normalizeStartBalance(usageSession)
    pending.activeBalanceYuan = activeBalanceYuan
    pending.balanceFallbackEligible = true
    pending.closeConfirmed = closeConfirmed
    return await this.store.savePendingSettlementContext(record, pending)
  }

  private async buildAutomaticCloseResult(
    config: HotWaterConfig,
    orderNo: string,
    usageSession: UsageSession
  ): Promise<OperationResult> {
    const closedAtMillis = Date.now()
    const persistedRecord = await this.persistPendingSettlementContext(
      orderNo,
      usageSession,
      ConsumptionSettlement.activeBalanceForSession(usageSession),
      closedAtMillis,
      true
    )
    const snapshot = this.fallbackSnapshot(
      config,
      '检测到热水已自动关闭，账单正在同步。',
      '',
      false
    )
    snapshot.remoteNoActiveOrderConfirmed = true
    snapshot.autoClosedOrderNo = orderNo
    snapshot.autoClosedHistoryId = persistedRecord.id
    snapshot.autoClosedUsageStartedAtMillis = persistedRecord.startedAtMillis
    snapshot.autoClosedUsageDurationMillis = persistedRecord.usageDurationMillis

    const result = new OperationResult()
    result.title = '热水已自动关闭'
    result.summary = '检测到热水已自动关闭，账单正在同步。\n余额 -- 元'
    result.details = '服务端已确认当前没有活动订单，未重复发送关水请求。'
    result.snapshot = snapshot
    result.usageDurationMillis = persistedRecord.usageDurationMillis
    result.usageStartedAtMillis = persistedRecord.startedAtMillis
    result.historyId = persistedRecord.id
    result.automaticClose = true
    return result
  }

  private async refreshSnapshotWithStatus(
    config: HotWaterConfig,
    statusText: string,
    deviceOnlineOverride: boolean | null = null
  ): Promise<DashboardSnapshot> {
    const userInfoText = await this.get('/user/info', this.buildUserInfoQuery(config))
    const deviceInfoText = await this.get('/device/info/mac', this.buildDeviceInfoQuery(config))
    const balanceText = await this.get('/account/wallet/money', this.buildBalanceQuery(config))
    return await this.ensureUsageSession(await this.buildSnapshot(
      config,
      userInfoText,
      deviceInfoText,
      balanceText,
      statusText,
      deviceOnlineOverride
    ))
  }

  private async findLatestPendingClose(): Promise<PendingSettlement | null> {
    const pendingSettlements = await this.store.loadPendingSettlements()
    let latest: PendingSettlement | null = null
    for (let index = 0; index < pendingSettlements.length; index++) {
      const candidate = pendingSettlements[index]
      if (latest === null || candidate.endedAtMillis > latest.endedAtMillis ||
        (candidate.endedAtMillis === latest.endedAtMillis && candidate.recordId > latest.recordId)) {
        latest = candidate
      }
    }
    return latest
  }

  private async queryCloseConfirmed(config: HotWaterConfig, orderNo: string): Promise<boolean> {
    try {
      const closeResultText = await this.postForm(
        '/order/tcpDevice/closeOrder/result/query',
        this.buildFormString(config, orderNo)
      )
      return this.extractSuccess(closeResultText) && this.extractErrorCode(closeResultText) === 0
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      return false
    }
  }

  private buildPendingCloseGateResult(config: HotWaterConfig, statusText: string): OperationResult {
    const snapshot = this.fallbackSnapshot(config, '关水确认中，请稍后再试。', '', false)
    const result = new OperationResult()
    result.title = '关水确认中'
    result.summary = '关水确认中，请稍后再试。\n余额 -- 元'
    result.details = statusText
    result.snapshot = snapshot
    return result
  }

  private async buildDeviceBusyResult(config: HotWaterConfig, statusText: string): Promise<OperationResult> {
    await this.store.clearOrderNo()
    await this.store.clearUsageSession()
    let snapshot: DashboardSnapshot
    try {
      snapshot = await this.refreshSnapshotWithStatus(config, statusText)
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
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
    statusText: string,
    deviceOnlineOverride: boolean | null = null
  ): Promise<DashboardSnapshot> {
    const snapshot = this.snapshotFromTexts(config, userInfoText, deviceInfoText, balanceText)
    const busyByOther = this.isDeviceBusyByOther(statusText)
    const extractedOrderNo = this.extractFirstOrEmpty(statusText, 'orderNo')
    let autoClosedOrderNo: string = ''
    let autoClosedHistoryId: number = 0
    let autoClosedUsageStartedAtMillis: number = 0
    let autoClosedUsageDurationMillis: number = -1
    if (busyByOther) {
      await this.store.clearOrderNo()
      await this.store.clearUsageSession()
    } else if (extractedOrderNo.length > 0) {
      const ignoreRecovered = await this.store.shouldIgnoreRecoveredOrder()
      const matchesPendingSettlement = await this.store.hasPendingSettlementForOrder(extractedOrderNo)
      if (!ignoreRecovered && !matchesPendingSettlement) {
        await this.store.saveOrderNo(extractedOrderNo)
        await this.store.confirmUsageSession()
        snapshot.remoteOrderConfirmed = true
      }
    } else if (this.isConfirmedWithoutActiveOrder(statusText)) {
      snapshot.remoteNoActiveOrderConfirmed = true
      const nowMillis = Date.now()
      await this.store.clearExpiredProvisionalUsageState(nowMillis, PROVISIONAL_OPENING_MAX_AGE_MS)
      const cachedOrderNo = await this.store.loadOrderNo()
      const usageSession = await this.store.loadUsageSession()
      const hasPendingSettlement = cachedOrderNo.length > 0 &&
        await this.store.hasPendingSettlementForOrder(cachedOrderNo)
      if (!hasPendingSettlement && ConsumptionSettlement.canAutoCloseUsage(
        cachedOrderNo,
        usageSession,
        true,
        nowMillis,
        MIN_AUTO_CLOSE_USAGE_AGE_MS
      )) {
        const persistedRecord = await this.persistPendingSettlementContext(
          cachedOrderNo,
          usageSession!!,
          ConsumptionSettlement.activeBalanceForSession(usageSession!!),
          nowMillis,
          true
        )
        autoClosedOrderNo = cachedOrderNo
        autoClosedHistoryId = persistedRecord.id
        autoClosedUsageStartedAtMillis = persistedRecord.startedAtMillis
        autoClosedUsageDurationMillis = persistedRecord.usageDurationMillis
      }
    }
    const cachedOrderNo = busyByOther ? '' : await this.store.loadOrderNo()
    snapshot.orderNo = cachedOrderNo
    snapshot.hasCachedOrder = cachedOrderNo.length > 0
    snapshot.isDeviceBusyByOther = busyByOther
    snapshot.isDeviceOnline = deviceOnlineOverride !== null
      ? deviceOnlineOverride
      : this.isDeviceOnline(statusText)
    if (snapshot.isDeviceOnline === false) {
      snapshot.statusLabel = DEVICE_OFFLINE_STATUS_MESSAGE
    } else if (busyByOther) {
      snapshot.statusLabel = DEVICE_BUSY_BY_OTHER_MESSAGE
    } else if (autoClosedOrderNo.length > 0) {
      snapshot.statusLabel = '检测到热水已自动关闭，账单正在同步。'
    } else if (cachedOrderNo.length > 0) {
      snapshot.statusLabel = '已缓存订单号，点击主按钮将尝试关热水。'
    } else if (this.extractSuccess(statusText)) {
      snapshot.statusLabel = '当前没有缓存订单号，点击主按钮将尝试开热水。'
    } else {
      const errorMessage = this.extractFirstOrEmpty(statusText, 'errorMessage')
      snapshot.statusLabel = errorMessage.length > 0 ? errorMessage : '状态待确认，建议先刷新余额。'
    }
    snapshot.autoClosedOrderNo = autoClosedOrderNo
    snapshot.autoClosedHistoryId = autoClosedHistoryId
    snapshot.autoClosedUsageStartedAtMillis = autoClosedUsageStartedAtMillis
    snapshot.autoClosedUsageDurationMillis = autoClosedUsageDurationMillis
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
    isDeviceBusyByOther: boolean,
    deviceOnlineOverride: boolean | null = null
  ): DashboardSnapshot {
    const snapshot = new DashboardSnapshot()
    snapshot.balanceLabel = '--'
    snapshot.userLabel = HotWaterConfig.maskMiddle(config.telephone, 3, 4)
    snapshot.deviceName = '热水设备已连接'
    snapshot.locationLabel = '打开应用后可自动刷新宿舍位置'
    snapshot.statusLabel = deviceOnlineOverride === false
      ? DEVICE_OFFLINE_STATUS_MESSAGE
      : statusLabel
    snapshot.orderNo = orderNo
    snapshot.hasCachedOrder = orderNo.length > 0
    snapshot.isDeviceBusyByOther = isDeviceBusyByOther
    snapshot.isDeviceOnline = deviceOnlineOverride !== null
      ? deviceOnlineOverride
      : (isDeviceBusyByOther ? true : null)
    return snapshot
  }

  private async ensureUsageSession(snapshot: DashboardSnapshot): Promise<DashboardSnapshot> {
    if (!snapshot.hasCachedOrder || snapshot.isDeviceBusyByOther || !snapshot.remoteOrderConfirmed) {
      return snapshot
    }
    const existing = await this.store.loadUsageSession()
    if (!existing) {
      await this.store.startUsageSessionIfAbsent(
        ConsumptionSettlement.recoverStartBalance(snapshot.balanceYuan),
        true
      )
    } else if (!existing.startBalanceIsBeforePreauthorization &&
      ConsumptionSettlement.isKnownBalance(existing.startBalanceYuan)) {
      await this.store.updateUsageSessionStartBalance(
        ConsumptionSettlement.recoverStartBalance(snapshot.balanceYuan)
      )
    }
    return snapshot
  }

  private async fetchBalanceYuan(config: HotWaterConfig): Promise<number> {
    const balanceText = await this.get('/account/wallet/money', this.buildBalanceQuery(config))
    return HotWaterPayload.extractBalanceYuan(balanceText)
  }

  private normalizeStartBalance(session: UsageSession): number {
    if (!ConsumptionSettlement.isKnownBalance(session.startBalanceYuan)) {
      return -1
    }
    return session.startBalanceIsBeforePreauthorization
      ? session.startBalanceYuan
      : ConsumptionSettlement.recoverStartBalance(session.startBalanceYuan)
  }

  private async tryReconcilePendingSettlements(
    config: HotWaterConfig,
    snapshot: DashboardSnapshot
  ): Promise<void> {
    try {
      await this.reconcilePendingSettlements(config, snapshot)
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      // 历史回填失败不应让余额刷新或设备操作失败，下一次刷新会继续尝试。
    }
  }

  private async applyPendingBalanceGuard(snapshot: DashboardSnapshot): Promise<void> {
    if (snapshot.hasCachedOrder || snapshot.isDeviceBusyByOther) {
      return
    }
    const localUsageSession = await this.store.loadUsageSession()
    const localOrderNo = await this.store.loadOrderNo()
    if (localUsageSession !== null || localOrderNo.length > 0) {
      return
    }

    const pendingSettlements = await this.store.loadPendingSettlements()
    let latestGuard: PendingSettlement | null = null
    for (let index = 0; index < pendingSettlements.length; index++) {
      const pending = pendingSettlements[index]
      if (pending.confirmedConsumptionYuan < 0 ||
        !ConsumptionSettlement.isFreshSettlement(pending.endedAtMillis, Date.now())) {
        continue
      }
      if (latestGuard === null || pending.endedAtMillis > latestGuard.endedAtMillis) {
        latestGuard = pending
      }
    }
    if (latestGuard === null || ConsumptionSettlement.matchesExplicitConsumption(
      latestGuard.startBalanceYuan,
      latestGuard.confirmedConsumptionYuan,
      snapshot.balanceYuan
    )) {
      return
    }

    snapshot.balanceYuan = 0
    snapshot.balanceLabel = '--'
    snapshot.statusLabel = '热水已关闭，余额仍在结算中。'
  }

  private async reconcilePendingSettlements(
    config: HotWaterConfig,
    snapshot: DashboardSnapshot
  ): Promise<void> {
    const pendingSettlements = await this.store.loadPendingSettlements()
    let latestPendingRecordId: number = 0
    let latestPendingEndedAtMillis: number = -1
    for (let index = 0; index < pendingSettlements.length; index++) {
      const candidate = pendingSettlements[index]
      if (candidate.endedAtMillis > latestPendingEndedAtMillis ||
        (candidate.endedAtMillis === latestPendingEndedAtMillis && candidate.recordId > latestPendingRecordId)) {
        latestPendingEndedAtMillis = candidate.endedAtMillis
        latestPendingRecordId = candidate.recordId
      }
    }
    let remoteHasNoActiveOrder: boolean | null = null
    const reconciliationStartedAtMillis = Date.now()
    for (let index = 0; index < pendingSettlements.length; index++) {
      const pending = pendingSettlements[index]
      let closeResultConfirmed = pending.closeConfirmed
      if (!closeResultConfirmed && !snapshot.remoteNoActiveOrderConfirmed && pending.shouldBlockOpen(
        reconciliationStartedAtMillis,
        PENDING_CLOSE_OPEN_GUARD_MS
      )) {
        closeResultConfirmed = await this.queryCloseConfirmed(config, pending.orderNo)
      }
      let consumptionYuan: number = pending.confirmedConsumptionYuan
      if (consumptionYuan < 0) {
        try {
          const consumptionText = await this.postForm(
            '/order/consumeOrder/result/query',
            this.buildFormString(config, pending.orderNo)
          )
          consumptionYuan = HotWaterPayload.extractConfirmedConsumptionYuan(consumptionText)
        } catch (error) {
          if (HotWaterPayload.isCredentialExpiredError(error)) {
            throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
          }
          consumptionYuan = -1
        }
      }

      if (snapshot.remoteNoActiveOrderConfirmed || closeResultConfirmed || consumptionYuan >= 0) {
        pending.closeConfirmed = true
        await this.store.markPendingCloseConfirmed(pending.orderNo)
      }

      const localUsageSession = await this.store.loadUsageSession()
      const localOrderNo = await this.store.loadOrderNo()
      const hasLocalActivity = snapshot.hasCachedOrder || snapshot.isDeviceBusyByOther ||
        localUsageSession !== null || localOrderNo.length > 0
      if (consumptionYuan >= 0) {
        const record = this.recordFromPendingSettlement(pending, consumptionYuan)
        const keepPendingForBalance = ConsumptionSettlement.shouldKeepConfirmedBalanceGuard(
          pending.startBalanceYuan,
          consumptionYuan,
          snapshot.balanceYuan,
          pending.endedAtMillis,
          reconciliationStartedAtMillis,
          hasLocalActivity
        )
        await this.store.finalizePendingSettlement(record, pending.orderNo, keepPendingForBalance)
        continue
      }

      if (consumptionYuan < 0 && pending.recordId === latestPendingRecordId) {
        const localFallbackPreconditionsMet = ConsumptionSettlement.canUseBalanceFallback(
          pending.endedAtMillis,
          reconciliationStartedAtMillis,
          true,
          pending.balanceFallbackEligible,
          hasLocalActivity,
          true
        )
        if (localFallbackPreconditionsMet) {
          if (remoteHasNoActiveOrder === null) {
            remoteHasNoActiveOrder = await this.hasConfirmedNoActiveOrder(config)
          }
        }
        if (localFallbackPreconditionsMet && remoteHasNoActiveOrder === true) {
          pending.closeConfirmed = true
          await this.store.markPendingCloseConfirmed(pending.orderNo)
          const latestUsageSession = await this.store.loadUsageSession()
          const latestOrderNo = await this.store.loadOrderNo()
          if (latestUsageSession === null && latestOrderNo.length === 0) {
            const latestBalanceYuan = await this.fetchBalanceYuan(config)
            const finalUsageSession = await this.store.loadUsageSession()
            const finalOrderNo = await this.store.loadOrderNo()
            if (finalUsageSession === null && finalOrderNo.length === 0) {
              consumptionYuan = ConsumptionSettlement.calculateSettledConsumption(
                pending.startBalanceYuan,
                pending.activeBalanceYuan,
                latestBalanceYuan
              )
            }
          }
        }
      }
      if (consumptionYuan >= 0) {
        const record = this.recordFromPendingSettlement(pending, consumptionYuan)
        await this.store.finalizePendingSettlement(record, pending.orderNo)
      }
    }
  }

  private recordFromPendingSettlement(pending: PendingSettlement, consumptionYuan: number): ConsumptionRecord {
    const record = new ConsumptionRecord()
    record.id = pending.recordId
    record.orderNo = pending.orderNo
    record.startedAtMillis = pending.startedAtMillis
    record.endedAtMillis = pending.endedAtMillis
    record.usageDurationMillis = Math.max(0, pending.endedAtMillis - pending.startedAtMillis)
    record.consumptionYuan = consumptionYuan
    record.settlementVerified = true
    return record
  }

  private async hasConfirmedNoActiveOrder(config: HotWaterConfig): Promise<boolean> {
    try {
      const statusText = await this.postForm('/order/tcpDevice/query/rateOrder/using', this.buildFormString(config))
      return this.isConfirmedWithoutActiveOrder(statusText)
    } catch (error) {
      if (HotWaterPayload.isCredentialExpiredError(error)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      return false
    }
  }

  private isConfirmedWithoutActiveOrder(statusText: string): boolean {
    return this.extractSuccess(statusText) &&
      this.extractErrorCode(statusText) === 0 &&
      this.extractFirstOrEmpty(statusText, 'orderNo').length === 0 &&
      !this.isDeviceBusyByOther(statusText)
  }

  private ensureConfig(config: HotWaterConfig): void {
    if (!config.isComplete()) {
      throw new Error('配置还不完整，请先登录趣智校园或补全字段。')
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
      const responseText = String(response.result)
      if (HotWaterPayload.isCredentialExpiredResponse(responseText, response.responseCode)) {
        throw new Error(HotWaterPayload.CREDENTIAL_EXPIRED_MESSAGE)
      }
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new Error('HTTP ' + String(response.responseCode))
      }
      return responseText
    } finally {
      client.destroy()
    }
  }

  private extractSuccess(text: string): boolean {
    return /"success"\s*:\s*true/i.test(text)
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
    if (isOwner === true) {
      return false
    }
    if (isOwner === false) {
      return true
    }
    return errorMessage.indexOf('设备正在使用') >= 0
  }

  private isDeviceOnline(statusText: string): boolean | null {
    if (this.isExplicitDeviceOffline(statusText)) {
      return false
    }
    const reportedState = this.reportedDeviceOnlineState(statusText)
    if (reportedState !== null) {
      return reportedState
    }
    if (this.isDeviceBusyByOther(statusText)) {
      return true
    }
    return null
  }

  private openResultDeviceOnlineState(text: string, expectedOrderNo: string): boolean | null {
    const errorCode = this.extractErrorCode(text)
    const errorMessage = this.extractFirstOrEmpty(text, 'errorMessage')
    if (this.isExplicitDeviceOffline(text) ||
      (errorCode === OPEN_RESULT_NETWORK_ERROR_CODE && errorMessage.indexOf(OPEN_RESULT_NETWORK_SLOW_MESSAGE) >= 0)) {
      return false
    }
    const reportedState = this.reportedDeviceOnlineState(text)
    if (reportedState !== null) {
      return reportedState
    }
    if (this.isDeviceBusyByOther(text)) {
      return true
    }
    if (this.isConfirmedOpenResult(text, expectedOrderNo)) {
      return true
    }
    return null
  }

  private isExplicitDeviceOffline(text: string): boolean {
    const errorMessage = this.extractFirstOrEmpty(text, 'errorMessage')
    return this.extractErrorCode(text) === DEVICE_OFFLINE_ERROR_CODE ||
      errorMessage.indexOf(DEVICE_OFFLINE_MESSAGE) >= 0 ||
      errorMessage.indexOf(DEVICE_OFFLINE_MESSAGE_ALT) >= 0
  }

  private isConfirmedOpenResult(text: string, expectedOrderNo: string): boolean {
    let parsed: Object
    try {
      parsed = JSON.parse(text) as Object
    } catch (error) {
      return false
    }
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return false
    }
    const root = parsed as Record<string, Object>
    if (root['success'] !== true || Number(root['errorCode']) !== 0) {
      return false
    }
    const data = root['data']
    if (typeof data !== 'object' || data === null || Array.isArray(data)) {
      return false
    }
    const dataRecord = data as Record<string, Object>
    const responseOrderNo = String(dataRecord['orderNo'] ?? '').trim()
    if (responseOrderNo.length === 0) {
      return false
    }
    if (expectedOrderNo.length > 0 && responseOrderNo !== expectedOrderNo) {
      return false
    }
    return Number(dataRecord['state']) === 1 && Number(dataRecord['result']) === 0
  }

  private isOpenResultSuccessful(text: string, expectedOrderNo: string): boolean {
    return this.isConfirmedOpenResult(text, expectedOrderNo)
  }

  private reportedDeviceOnlineState(text: string): boolean | null {
    // onlineStatusId is static device metadata; the offline capture kept it at 1.
    const keys: string[] = ['isOnline', 'online', 'deviceOnline', 'onlineStatus', 'tcpDeviceOnline']
    for (let index = 0; index < keys.length; index++) {
      const value = this.extractFirstOrEmpty(text, keys[index]).trim().toLowerCase()
      if (value === 'online' || value === 'connected' || value === 'true' || value === '1') {
        return true
      }
      if (value === 'offline' || value === 'disconnected' || value === 'false' || value === '0') {
        return false
      }
    }
    return null
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
    return HotWaterPayload.extractBalanceYuan(text)
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
