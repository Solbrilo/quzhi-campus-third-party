import preferences from '@ohos.data.preferences'
import type common from '@ohos.app.ability.common'
import { ArkTSUtils } from '@kit.ArkTS'
import {
  ConsumptionRecord,
  HotWaterConfig,
  PendingSettlement,
  UsageSession
} from './HotWaterModels'

const STORE_STATE_LOCK = ArkTSUtils.locks.AsyncLock.request('cn.qzxy.hotwater.store-state')

export class HotWaterStore {
  private static readonly STORE_NAME: string = 'hotwater_store'
  private static readonly ORDER_RECOVERY_GRACE_MS: number = 12000
  private static readonly MAX_CONSUMPTION_RECORDS: number = 1200
  private static readonly MAX_PENDING_SETTLEMENTS: number = 20
  private context: common.Context

  constructor(context: common.Context) {
    this.context = context
  }

  private async getStore(): Promise<preferences.Preferences> {
    return await preferences.getPreferences(this.context, HotWaterStore.STORE_NAME)
  }

  async loadConfig(): Promise<HotWaterConfig> {
    const store = await this.getStore()
    const config = new HotWaterConfig()
    config.projectId = Number(await store.get('projectId', 0))
    config.userId = Number(await store.get('userId', 0))
    config.accountId = Number(await store.get('accountId', 0))
    config.loginCode = String(await store.get('loginCode', ''))
    config.telephone = String(await store.get('telephone', ''))
    config.snCode = String(await store.get('snCode', ''))
    config.macAddress = String(await store.get('macAddress', ''))
    config.phoneSystem = String(await store.get('phoneSystem', ''))
    config.version = String(await store.get('version', ''))
    return config
  }

  async saveConfig(config: HotWaterConfig): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => {
      const store = await this.getStore()
      const identityChanged = await this.hasConfigIdentityChanged(store, config)
      await store.put('projectId', config.projectId)
      await store.put('userId', config.userId)
      await store.put('accountId', config.accountId)
      await store.put('loginCode', config.loginCode)
      await store.put('telephone', config.telephone)
      await store.put('snCode', config.snCode)
      await store.put('macAddress', config.macAddress)
      await store.put('phoneSystem', config.phoneSystem)
      await store.put('version', config.version)
      if (config.loginCode.trim().length > 0) {
        await store.delete('credentialExpired')
      }
      if (identityChanged) {
        await this.clearAccountRuntimeStateUnlocked(store)
      }
      await store.flush()
    })
  }

  async invalidateLoginCredential(): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => {
      const store = await this.getStore()
      await store.put('loginCode', '')
      await store.put('credentialExpired', true)
      await store.flush()
    })
  }

  async isLoginCredentialExpired(): Promise<boolean> {
    const store = await this.getStore()
    return Boolean(await store.get('credentialExpired', false))
  }

  private async hasConfigIdentityChanged(
    store: preferences.Preferences,
    config: HotWaterConfig
  ): Promise<boolean> {
    const stored = new HotWaterConfig()
    stored.projectId = Number(await store.get('projectId', 0))
    stored.userId = Number(await store.get('userId', 0))
    stored.accountId = Number(await store.get('accountId', 0))
    stored.telephone = String(await store.get('telephone', ''))
    stored.snCode = String(await store.get('snCode', ''))
    return !HotWaterConfig.hasSameRuntimeIdentity(stored, config)
  }

  async applyQzxyLogin(config: HotWaterConfig): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => {
      const store = await this.getStore()
      const identityChanged = await this.hasConfigIdentityChanged(store, config)
      await store.put('projectId', config.projectId)
      await store.put('userId', config.userId)
      await store.put('accountId', config.accountId)
      await store.put('loginCode', config.loginCode)
      await store.put('telephone', config.telephone)
      await store.put('snCode', config.snCode)
      await store.put('macAddress', config.macAddress)
      await store.put('phoneSystem', config.phoneSystem)
      await store.put('version', config.version)
      if (config.loginCode.trim().length > 0) {
        await store.delete('credentialExpired')
      }
      if (identityChanged) {
        await this.clearAccountRuntimeStateUnlocked(store)
      }
      await store.flush()
    })
  }

  async loadOrderNo(): Promise<string> {
    const store = await this.getStore()
    return String(await store.get('orderNo', ''))
  }

  async saveOrderNo(orderNo: string): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => {
      const store = await this.getStore()
      await store.put('orderNo', orderNo)
      await store.put('orderClearedAt', 0)
      await store.flush()
    })
  }

  async clearOrderNo(): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => {
      const store = await this.getStore()
      await store.put('orderNo', '')
      await store.put('orderClearedAt', Date.now())
      await store.flush()
    })
  }

  async loadUsageSession(): Promise<UsageSession | null> {
    const store = await this.getStore()
    const startedAtMillis = Number(await store.get('usageStartedAtMillis', 0))
    if (startedAtMillis <= 0) {
      return null
    }
    const session = new UsageSession()
    session.startedAtMillis = startedAtMillis
    session.startBalanceYuan = Number(await store.get('usageStartBalanceYuan', -1))
    session.startBalanceIsBeforePreauthorization =
      Boolean(await store.get('usageStartBalanceIsBeforePreauthorization', false))
    session.provisionalOpening = Boolean(await store.get('usageSessionProvisionalOpening', false))
    return session
  }

  async startUsageSessionIfAbsent(
    startBalanceYuan: number,
    startBalanceIsBeforePreauthorization: boolean,
    provisionalOpening: boolean = false
  ): Promise<UsageSession> {
    return await STORE_STATE_LOCK.lockAsync(async () => await this.startUsageSessionIfAbsentUnlocked(
      startBalanceYuan,
      startBalanceIsBeforePreauthorization,
      provisionalOpening
    ))
  }

  private async startUsageSessionIfAbsentUnlocked(
    startBalanceYuan: number,
    startBalanceIsBeforePreauthorization: boolean,
    provisionalOpening: boolean
  ): Promise<UsageSession> {
    const existing = await this.loadUsageSession()
    if (existing) {
      return existing
    }
    const session = new UsageSession()
    session.startedAtMillis = Date.now()
    session.startBalanceYuan = startBalanceYuan
    session.startBalanceIsBeforePreauthorization = startBalanceIsBeforePreauthorization
    session.provisionalOpening = provisionalOpening
    const store = await this.getStore()
    await store.put('usageStartedAtMillis', session.startedAtMillis)
    await store.put('usageStartBalanceYuan', session.startBalanceYuan)
    await store.put(
      'usageStartBalanceIsBeforePreauthorization',
      session.startBalanceIsBeforePreauthorization
    )
    await store.put('usageSessionProvisionalOpening', session.provisionalOpening)
    await store.flush()
    return session
  }

  async updateUsageSessionStartBalance(startBalanceYuan: number): Promise<UsageSession | null> {
    return await STORE_STATE_LOCK.lockAsync(async () =>
      await this.updateUsageSessionStartBalanceUnlocked(startBalanceYuan))
  }

  private async updateUsageSessionStartBalanceUnlocked(startBalanceYuan: number): Promise<UsageSession | null> {
    const session = await this.loadUsageSession()
    if (!session) {
      return null
    }
    session.startBalanceYuan = startBalanceYuan
    session.startBalanceIsBeforePreauthorization = true
    const store = await this.getStore()
    await store.put('usageStartBalanceYuan', session.startBalanceYuan)
    await store.put('usageStartBalanceIsBeforePreauthorization', true)
    await store.flush()
    return session
  }

  async clearUsageSession(): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => await this.clearUsageSessionUnlocked())
  }

  private async clearUsageSessionUnlocked(): Promise<void> {
    const store = await this.getStore()
    await store.delete('usageStartedAtMillis')
    await store.delete('usageStartBalanceYuan')
    await store.delete('usageStartBalanceIsBeforePreauthorization')
    await store.delete('usageSessionProvisionalOpening')
    await store.flush()
  }

  private async clearAccountRuntimeStateUnlocked(store: preferences.Preferences): Promise<void> {
    await store.put('orderNo', '')
    await store.put('orderClearedAt', Date.now())
    await store.delete('usageStartedAtMillis')
    await store.delete('usageStartBalanceYuan')
    await store.delete('usageStartBalanceIsBeforePreauthorization')
    await store.delete('usageSessionProvisionalOpening')
    await store.delete('pendingSettlements')
  }

  async confirmUsageSession(): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => {
      const session = await this.loadUsageSession()
      if (!session || !session.provisionalOpening) {
        return
      }
      const store = await this.getStore()
      await store.put('usageSessionProvisionalOpening', false)
      await store.flush()
    })
  }

  async clearExpiredProvisionalUsageState(nowMillis: number, maxAgeMillis: number): Promise<boolean> {
    return await STORE_STATE_LOCK.lockAsync(async () => {
      const session = await this.loadUsageSession()
      if (!session || !session.isExpiredProvisionalOpening(nowMillis, maxAgeMillis)) {
        return false
      }
      const store = await this.getStore()
      await store.put('orderNo', '')
      await store.put('orderClearedAt', nowMillis)
      await store.delete('usageStartedAtMillis')
      await store.delete('usageStartBalanceYuan')
      await store.delete('usageStartBalanceIsBeforePreauthorization')
      await store.delete('usageSessionProvisionalOpening')
      await store.flush()
      return true
    })
  }

  async saveConsumptionRecord(record: ConsumptionRecord): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => await this.saveConsumptionRecordUnlocked(record))
  }

  private async saveConsumptionRecordUnlocked(record: ConsumptionRecord): Promise<void> {
    const records = await this.loadConsumptionRecords()
    let replaced = false
    for (let index = 0; index < records.length; index++) {
      if (records[index].id === record.id) {
        if (ConsumptionRecord.canReplaceSettlement(records[index].settlementVerified, record.settlementVerified)) {
          records[index] = record
        }
        replaced = true
        break
      }
    }
    if (!replaced) {
      records.unshift(record)
    }
    await this.writeConsumptionRecords(records)
  }

  async updateConsumptionRecord(recordId: number, consumptionYuan: number): Promise<boolean> {
    return await STORE_STATE_LOCK.lockAsync(async () =>
      await this.updateConsumptionRecordUnlocked(recordId, consumptionYuan))
  }

  private async updateConsumptionRecordUnlocked(recordId: number, consumptionYuan: number): Promise<boolean> {
    if (recordId <= 0 || !Number.isFinite(consumptionYuan) || consumptionYuan < 0) {
      return false
    }
    const records = await this.loadConsumptionRecords()
    for (let index = 0; index < records.length; index++) {
      if (records[index].id === recordId) {
        records[index].consumptionYuan = consumptionYuan
        records[index].settlementVerified = true
        await this.writeConsumptionRecords(records)
        return true
      }
    }
    return false
  }

  async loadConsumptionRecords(): Promise<ConsumptionRecord[]> {
    const store = await this.getStore()
    const rawValue = String(await store.get('consumptionRecords', ''))
    if (rawValue.length === 0) {
      return []
    }
    try {
      const parsed = JSON.parse(rawValue) as Array<Record<string, Object>>
      const records: ConsumptionRecord[] = []
      parsed.forEach((item) => {
        const record = new ConsumptionRecord()
        record.id = Number(item['id'])
        record.orderNo = String(item['orderNo'] ?? '')
        const parsedStartedAtMillis = Number(item['startedAtMillis'])
        const parsedEndedAtMillis = Number(item['endedAtMillis'])
        record.startedAtMillis = Number.isFinite(parsedStartedAtMillis) ? parsedStartedAtMillis : 0
        record.endedAtMillis = Number.isFinite(parsedEndedAtMillis) ? parsedEndedAtMillis : 0
        const parsedConsumptionYuan = Number(item['consumptionYuan'])
        record.settlementVerified = Boolean(item['settlementVerified'])
        record.consumptionYuan = Number.isFinite(parsedConsumptionYuan) &&
          (record.settlementVerified || parsedConsumptionYuan > 0)
          ? parsedConsumptionYuan
          : -1
        const parsedUsageDurationMillis = Number(item['usageDurationMillis'])
        record.usageDurationMillis = Number.isFinite(parsedUsageDurationMillis)
          ? Math.max(0, parsedUsageDurationMillis)
          : 0
        if (Number.isFinite(record.id) && record.id > 0) {
          records.push(record)
        }
      })
      return HotWaterStore.normalizeConsumptionRecords(records)
    } catch (error) {
      return []
    }
  }

  async loadPendingSettlements(): Promise<PendingSettlement[]> {
    const store = await this.getStore()
    const rawValue = String(await store.get('pendingSettlements', ''))
    if (rawValue.length === 0) {
      return []
    }
    try {
      const parsed = JSON.parse(rawValue) as Array<Record<string, Object>>
      const pendingSettlements: PendingSettlement[] = []
      parsed.forEach((item) => {
        const pending = new PendingSettlement()
        pending.orderNo = String(item['orderNo'] ?? '')
        pending.recordId = Number(item['recordId'])
        pending.startedAtMillis = Number(item['startedAtMillis'])
        pending.endedAtMillis = Number(item['endedAtMillis'])
        pending.startBalanceYuan = Number(item['startBalanceYuan'])
        pending.activeBalanceYuan = Number(item['activeBalanceYuan'])
        const parsedConfirmedConsumptionYuan = Number(item['confirmedConsumptionYuan'])
        pending.confirmedConsumptionYuan = Number.isFinite(parsedConfirmedConsumptionYuan)
          ? parsedConfirmedConsumptionYuan
          : -1
        pending.balanceFallbackEligible = Boolean(item['balanceFallbackEligible'])
        pending.closeConfirmed = Boolean(item['closeConfirmed'])
        if (pending.orderNo.length > 0 && pending.recordId > 0) {
          pendingSettlements.push(pending)
        }
      })
      return HotWaterStore.limitPendingSettlements(pendingSettlements)
    } catch (error) {
      return []
    }
  }

  async markPendingCloseConfirmed(orderNo: string): Promise<void> {
    if (orderNo.length === 0) {
      return
    }
    await STORE_STATE_LOCK.lockAsync(async () => {
      const pendingSettlements = await this.loadPendingSettlements()
      let changed = false
      for (let index = 0; index < pendingSettlements.length; index++) {
        if (pendingSettlements[index].orderNo === orderNo && !pendingSettlements[index].closeConfirmed) {
          pendingSettlements[index].closeConfirmed = true
          changed = true
        }
      }
      if (changed) {
        await this.writePendingSettlements(pendingSettlements)
      }
    })
  }

  async hasPendingSettlementForOrder(orderNo: string): Promise<boolean> {
    if (orderNo.length === 0) {
      return false
    }
    const pendingSettlements = await this.loadPendingSettlements()
    for (let index = 0; index < pendingSettlements.length; index++) {
      if (pendingSettlements[index].matchesOrder(orderNo)) {
        return true
      }
    }
    return false
  }

  async savePendingSettlement(pending: PendingSettlement): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => await this.savePendingSettlementUnlocked(pending))
  }

  private async savePendingSettlementUnlocked(pending: PendingSettlement): Promise<void> {
    const pendingSettlements = await this.loadPendingSettlements()
    pending.balanceFallbackEligible = true
    let replaced = false
    for (let index = 0; index < pendingSettlements.length; index++) {
      pendingSettlements[index].balanceFallbackEligible = false
    }
    for (let index = 0; index < pendingSettlements.length; index++) {
      if (pendingSettlements[index].recordId === pending.recordId ||
        pendingSettlements[index].orderNo === pending.orderNo) {
        pendingSettlements[index] = pending
        replaced = true
        break
      }
    }
    if (!replaced) {
      pendingSettlements.unshift(pending)
    }
    if (pendingSettlements.length > HotWaterStore.MAX_PENDING_SETTLEMENTS) {
      pendingSettlements.splice(HotWaterStore.MAX_PENDING_SETTLEMENTS)
    }
    await this.writePendingSettlements(pendingSettlements)
  }

  async savePendingSettlementContext(
    record: ConsumptionRecord,
    pending: PendingSettlement
  ): Promise<ConsumptionRecord> {
    return await STORE_STATE_LOCK.lockAsync(async () =>
      await this.savePendingSettlementContextUnlocked(record, pending))
  }

  private async savePendingSettlementContextUnlocked(
    record: ConsumptionRecord,
    pending: PendingSettlement
  ): Promise<ConsumptionRecord> {
    const records = await this.loadConsumptionRecords()
    const pendingSettlements = await this.loadPendingSettlements()
    let persistedRecord = record
    let existingPendingFound = false

    for (let index = 0; index < pendingSettlements.length; index++) {
      const existingPending = pendingSettlements[index]
      if (existingPending.orderNo !== pending.orderNo) {
        continue
      }
      existingPendingFound = true
      pending.recordId = existingPending.recordId
      pending.startedAtMillis = existingPending.startedAtMillis
      pending.endedAtMillis = existingPending.endedAtMillis
      if (existingPending.startBalanceYuan >= 0) {
        pending.startBalanceYuan = existingPending.startBalanceYuan
      }
      if (existingPending.activeBalanceYuan >= 0) {
        pending.activeBalanceYuan = existingPending.activeBalanceYuan
      }
      if (existingPending.confirmedConsumptionYuan >= 0) {
        pending.confirmedConsumptionYuan = existingPending.confirmedConsumptionYuan
      }
      if (existingPending.closeConfirmed) {
        pending.closeConfirmed = true
      }
      for (let recordIndex = 0; recordIndex < records.length; recordIndex++) {
        if (records[recordIndex].id === existingPending.recordId) {
          persistedRecord = records[recordIndex]
          break
        }
      }
      if (persistedRecord.id !== existingPending.recordId) {
        persistedRecord.id = existingPending.recordId
        persistedRecord.startedAtMillis = existingPending.startedAtMillis
        persistedRecord.endedAtMillis = existingPending.endedAtMillis
        persistedRecord.usageDurationMillis = Math.max(
          0,
          existingPending.endedAtMillis - existingPending.startedAtMillis
        )
      }
      break
    }

    for (let index = 0; index < records.length; index++) {
      const existingRecord = records[index]
      if (existingRecord.orderNo !== pending.orderNo) {
        continue
      }
      if (!existingPendingFound || existingRecord.settlementVerified) {
        persistedRecord = existingRecord
      }
      if (existingRecord.settlementVerified) {
        break
      }
    }
    persistedRecord.orderNo = pending.orderNo

    const updatedRecords: ConsumptionRecord[] = []
    let recordInserted = false
    for (let index = 0; index < records.length; index++) {
      const matchesOrder = pending.orderNo.length > 0 && records[index].orderNo === pending.orderNo
      if (records[index].id === persistedRecord.id || matchesOrder) {
        if (!recordInserted) {
          updatedRecords.push(persistedRecord)
          recordInserted = true
        }
      } else {
        updatedRecords.push(records[index])
      }
    }
    if (!recordInserted) {
      updatedRecords.unshift(persistedRecord)
    }
    pending.recordId = persistedRecord.id
    pending.startedAtMillis = persistedRecord.startedAtMillis
    pending.endedAtMillis = persistedRecord.endedAtMillis
    pending.balanceFallbackEligible = true
    const updatedPendingSettlements: PendingSettlement[] = []
    let pendingInserted = false
    for (let index = 0; index < pendingSettlements.length; index++) {
      pendingSettlements[index].balanceFallbackEligible = false
      if (pendingSettlements[index].recordId === pending.recordId ||
        pendingSettlements[index].orderNo === pending.orderNo) {
        if (persistedRecord.settlementVerified &&
          pendingSettlements[index].confirmedConsumptionYuan >= 0 && !pendingInserted) {
          updatedPendingSettlements.push(pendingSettlements[index])
          pendingInserted = true
        } else if (!persistedRecord.settlementVerified && !pendingInserted) {
          updatedPendingSettlements.push(pending)
          pendingInserted = true
        }
      } else {
        updatedPendingSettlements.push(pendingSettlements[index])
      }
    }
    if (!persistedRecord.settlementVerified && !pendingInserted) {
      updatedPendingSettlements.unshift(pending)
    }
    if (updatedPendingSettlements.length > HotWaterStore.MAX_PENDING_SETTLEMENTS) {
      updatedPendingSettlements.splice(HotWaterStore.MAX_PENDING_SETTLEMENTS)
    }

    const store = await this.getStore()
    const normalizedRecords = HotWaterStore.normalizeConsumptionRecords(updatedRecords)
    await store.put('consumptionRecords', JSON.stringify(normalizedRecords))
    if (updatedPendingSettlements.length === 0) {
      await store.delete('pendingSettlements')
    } else {
      await store.put('pendingSettlements', JSON.stringify(updatedPendingSettlements))
    }
    await store.put('orderNo', '')
    await store.put('orderClearedAt', Date.now())
    await store.delete('usageStartedAtMillis')
    await store.delete('usageStartBalanceYuan')
    await store.delete('usageStartBalanceIsBeforePreauthorization')
    await store.delete('usageSessionProvisionalOpening')
    await store.flush()
    return persistedRecord
  }

  async finalizePendingSettlement(
    record: ConsumptionRecord,
    orderNo: string,
    keepPendingForBalance: boolean = false
  ): Promise<ConsumptionRecord> {
    return await STORE_STATE_LOCK.lockAsync(async () =>
      await this.finalizePendingSettlementUnlocked(record, orderNo, keepPendingForBalance))
  }

  private async finalizePendingSettlementUnlocked(
    record: ConsumptionRecord,
    orderNo: string,
    keepPendingForBalance: boolean
  ): Promise<ConsumptionRecord> {
    record.orderNo = orderNo
    const records = await this.loadConsumptionRecords()
    let persistedRecord = record
    const updatedRecords: ConsumptionRecord[] = []
    let recordInserted = false
    for (let index = 0; index < records.length; index++) {
      const existingRecord = records[index]
      const matchesOrder = orderNo.length > 0 && existingRecord.orderNo === orderNo
      if (existingRecord.id === record.id || matchesOrder) {
        if (!recordInserted) {
          persistedRecord = existingRecord.settlementVerified ? existingRecord : record
          updatedRecords.push(persistedRecord)
          recordInserted = true
        }
      } else {
        updatedRecords.push(existingRecord)
      }
    }
    if (!recordInserted) {
      updatedRecords.unshift(record)
      persistedRecord = record
    }
    const pendingSettlements = await this.loadPendingSettlements()
    const retainedPendingSettlements: PendingSettlement[] = []
    for (let index = 0; index < pendingSettlements.length; index++) {
      const pending = pendingSettlements[index]
      if (orderNo.length > 0 && pending.orderNo === orderNo && pending.recordId !== persistedRecord.id) {
        pending.recordId = persistedRecord.id
        pending.startedAtMillis = persistedRecord.startedAtMillis
        pending.endedAtMillis = persistedRecord.endedAtMillis
      }
      const matchesOrder = orderNo.length === 0 || pending.orderNo === orderNo
      const matchesSettlement = pending.recordId === persistedRecord.id && matchesOrder
      if (matchesSettlement && persistedRecord.settlementVerified && keepPendingForBalance) {
        pending.confirmedConsumptionYuan = persistedRecord.consumptionYuan
        pending.balanceFallbackEligible = false
        pending.closeConfirmed = true
        retainedPendingSettlements.push(pending)
      } else if (!matchesSettlement || !persistedRecord.settlementVerified) {
        retainedPendingSettlements.push(pending)
      }
    }

    const store = await this.getStore()
    const normalizedRecords = HotWaterStore.normalizeConsumptionRecords(updatedRecords)
    const limitedPendingSettlements = HotWaterStore.limitPendingSettlements(retainedPendingSettlements)
    await store.put('consumptionRecords', JSON.stringify(normalizedRecords))
    if (limitedPendingSettlements.length === 0) {
      await store.delete('pendingSettlements')
    } else {
      await store.put('pendingSettlements', JSON.stringify(limitedPendingSettlements))
    }
    await store.flush()
    return persistedRecord
  }

  async removePendingSettlement(recordId: number): Promise<void> {
    await STORE_STATE_LOCK.lockAsync(async () => await this.removePendingSettlementUnlocked(recordId))
  }

  private async removePendingSettlementUnlocked(recordId: number): Promise<void> {
    const pendingSettlements = await this.loadPendingSettlements()
    const retained: PendingSettlement[] = []
    pendingSettlements.forEach((pending) => {
      if (pending.recordId !== recordId) {
        retained.push(pending)
      }
    })
    await this.writePendingSettlements(retained)
  }

  private async writeConsumptionRecords(records: ConsumptionRecord[]): Promise<void> {
    const store = await this.getStore()
    const normalizedRecords = HotWaterStore.normalizeConsumptionRecords(records)
    await store.put('consumptionRecords', JSON.stringify(normalizedRecords))
    await store.flush()
  }

  private async writePendingSettlements(pendingSettlements: PendingSettlement[]): Promise<void> {
    const store = await this.getStore()
    const limitedPendingSettlements = HotWaterStore.limitPendingSettlements(pendingSettlements)
    if (limitedPendingSettlements.length === 0) {
      await store.delete('pendingSettlements')
    } else {
      await store.put('pendingSettlements', JSON.stringify(limitedPendingSettlements))
    }
    await store.flush()
  }

  static normalizeConsumptionRecords(records: ConsumptionRecord[]): ConsumptionRecord[] {
    const normalizedRecords = records.slice()
    normalizedRecords.sort(HotWaterStore.compareConsumptionRecordsDescending)
    if (normalizedRecords.length > HotWaterStore.MAX_CONSUMPTION_RECORDS) {
      normalizedRecords.splice(HotWaterStore.MAX_CONSUMPTION_RECORDS)
    }
    return normalizedRecords
  }

  private static compareConsumptionRecordsDescending(
    left: ConsumptionRecord,
    right: ConsumptionRecord
  ): number {
    const leftEndedAtMillis = HotWaterStore.sortableNumber(left.endedAtMillis)
    const rightEndedAtMillis = HotWaterStore.sortableNumber(right.endedAtMillis)
    if (leftEndedAtMillis !== rightEndedAtMillis) {
      return rightEndedAtMillis > leftEndedAtMillis ? 1 : -1
    }
    const leftId = HotWaterStore.sortableNumber(left.id)
    const rightId = HotWaterStore.sortableNumber(right.id)
    if (leftId === rightId) {
      return 0
    }
    return rightId > leftId ? 1 : -1
  }

  private static limitPendingSettlements(pendingSettlements: PendingSettlement[]): PendingSettlement[] {
    if (pendingSettlements.length <= HotWaterStore.MAX_PENDING_SETTLEMENTS) {
      return pendingSettlements
    }
    return pendingSettlements.slice(0, HotWaterStore.MAX_PENDING_SETTLEMENTS)
  }

  private static sortableNumber(value: number): number {
    return Number.isFinite(value) ? value : 0
  }

  async shouldIgnoreRecoveredOrder(): Promise<boolean> {
    const store = await this.getStore()
    const clearedAt = Number(await store.get('orderClearedAt', 0))
    return clearedAt > 0 && Date.now() - clearedAt < HotWaterStore.ORDER_RECOVERY_GRACE_MS
  }
}
