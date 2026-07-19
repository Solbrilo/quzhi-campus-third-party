import { ConsumptionRecord } from './HotWaterModels'

export enum BillScope {
  YEAR = 0,
  MONTH = 1
}

export enum BillGrouping {
  YEAR = 0,
  MONTH = 1
}

export class BillEntry {
  record: ConsumptionRecord = new ConsumptionRecord()
  endedAtMillis: number = 0
  year: number = 0
  month: number = 0
}

export class BillSummary {
  recordCount: number = 0
  settledCount: number = 0
  pendingCount: number = 0
  totalMilliYuan: number = 0
  totalDurationMillis: number = 0
}

export class BillGroup {
  key: string = ''
  label: string = ''
  entries: BillEntry[] = []
  summary: BillSummary = new BillSummary()
  collapsed: boolean = false
}

const MILLIS_PER_SECOND: number = 1000
const MAX_SAFE_INTEGER: number = Number.MAX_SAFE_INTEGER

export function buildBillEntries(records: ConsumptionRecord[]): BillEntry[] {
  const entries: BillEntry[] = []
  for (let index = 0; index < records.length; index++) {
    const record = records[index]
    if (!Number.isFinite(record.endedAtMillis) || record.endedAtMillis <= 0) {
      continue
    }
    const endedAt = new Date(record.endedAtMillis)
    const year = endedAt.getFullYear()
    const month = endedAt.getMonth() + 1
    if (!Number.isFinite(year) || month < 1 || month > 12) {
      continue
    }
    const entry = new BillEntry()
    entry.record = record
    entry.endedAtMillis = record.endedAtMillis
    entry.year = year
    entry.month = month
    entries.push(entry)
  }
  entries.sort(compareBillEntriesDescending)
  return entries
}

export function filterBillEntries(
  entries: BillEntry[],
  scope: BillScope,
  year: number,
  month: number = 0
): BillEntry[] {
  if (!Number.isFinite(year)) {
    return []
  }
  if (scope === BillScope.MONTH && (month < 1 || month > 12)) {
    return []
  }
  const filtered: BillEntry[] = []
  for (let index = 0; index < entries.length; index++) {
    const entry = entries[index]
    if (entry.year !== year || (scope === BillScope.MONTH && entry.month !== month)) {
      continue
    }
    filtered.push(entry)
  }
  return filtered
}

export function getBillYears(entries: BillEntry[]): number[] {
  const years: number[] = []
  for (let index = 0; index < entries.length; index++) {
    const year = entries[index].year
    if (years.indexOf(year) < 0) {
      years.push(year)
    }
  }
  years.sort((left: number, right: number): number => right - left)
  return years
}

export function getBillMonths(entries: BillEntry[], year: number): number[] {
  const months: number[] = []
  for (let index = 0; index < entries.length; index++) {
    const entry = entries[index]
    if (entry.year === year && months.indexOf(entry.month) < 0) {
      months.push(entry.month)
    }
  }
  months.sort((left: number, right: number): number => right - left)
  return months
}

export function summarizeBillEntries(entries: BillEntry[]): BillSummary {
  const summary = new BillSummary()
  summary.recordCount = entries.length
  for (let index = 0; index < entries.length; index++) {
    const record = entries[index].record
    const milliYuan = consumptionToMilliYuan(record)
    if (milliYuan < 0) {
      summary.pendingCount += 1
    } else {
      summary.settledCount += 1
      summary.totalMilliYuan = saturatingAdd(summary.totalMilliYuan, milliYuan)
    }
    summary.totalDurationMillis = saturatingAdd(
      summary.totalDurationMillis,
      normalizeDurationMillis(record.usageDurationMillis)
    )
  }
  return summary
}

export function groupBillEntries(
  entries: BillEntry[],
  grouping: BillGrouping,
  collapsedKeys: string[] = []
): BillGroup[] {
  const groups: BillGroup[] = []
  for (let index = 0; index < entries.length; index++) {
    const entry = entries[index]
    const key = getBillGroupKey(entry, grouping)
    let group: BillGroup | null = null
    for (let groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      if (groups[groupIndex].key === key) {
        group = groups[groupIndex]
        break
      }
    }
    if (group === null) {
      group = new BillGroup()
      group.key = key
      group.label = getBillGroupLabel(entry, grouping)
      group.collapsed = isBillGroupCollapsed(collapsedKeys, key)
      groups.push(group)
    }
    group.entries.push(entry)
  }
  for (let index = 0; index < groups.length; index++) {
    groups[index].summary = summarizeBillEntries(groups[index].entries)
  }
  return groups
}

export function getBillGroupKey(entry: BillEntry, grouping: BillGrouping): string {
  if (grouping === BillGrouping.YEAR) {
    return 'year:' + entry.year.toString()
  }
  return 'month:' + entry.year.toString() + '-' + entry.month.toString().padStart(2, '0')
}

export function isBillGroupCollapsed(collapsedKeys: string[], key: string): boolean {
  return collapsedKeys.indexOf(key) >= 0
}

export function toggleBillGroupCollapsed(collapsedKeys: string[], key: string): string[] {
  const updatedKeys: string[] = []
  let removed = false
  for (let index = 0; index < collapsedKeys.length; index++) {
    const candidate = collapsedKeys[index]
    if (candidate === key) {
      removed = true
      continue
    }
    if (updatedKeys.indexOf(candidate) < 0) {
      updatedKeys.push(candidate)
    }
  }
  if (!removed) {
    updatedKeys.push(key)
  }
  return updatedKeys
}

export function formatBillAmount(totalMilliYuan: number): string {
  const normalizedMilliYuan = normalizeNonNegativeInteger(totalMilliYuan)
  if (normalizedMilliYuan > 0 && normalizedMilliYuan < 10) {
    return '< ¥0.01'
  }
  const cents = Math.round(normalizedMilliYuan / 10)
  const yuan = Math.floor(cents / 100)
  const fraction = (cents % 100).toString().padStart(2, '0')
  return '¥' + yuan.toString() + '.' + fraction
}

export function formatBillDuration(durationMillis: number): string {
  const totalSeconds = Math.floor(normalizeDurationMillis(durationMillis) / MILLIS_PER_SECOND)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) {
    return hours.toString() + ':' + minutes.toString().padStart(2, '0') + ':' +
      seconds.toString().padStart(2, '0')
  }
  return minutes.toString().padStart(2, '0') + ':' + seconds.toString().padStart(2, '0')
}

function compareBillEntriesDescending(left: BillEntry, right: BillEntry): number {
  if (left.endedAtMillis !== right.endedAtMillis) {
    return right.endedAtMillis > left.endedAtMillis ? 1 : -1
  }
  if (left.record.id === right.record.id) {
    return 0
  }
  return right.record.id > left.record.id ? 1 : -1
}

function getBillGroupLabel(entry: BillEntry, grouping: BillGrouping): string {
  if (grouping === BillGrouping.YEAR) {
    return entry.year.toString() + '年'
  }
  return entry.year.toString() + '年' + entry.month.toString() + '月'
}

function consumptionToMilliYuan(record: ConsumptionRecord): number {
  if (!Number.isFinite(record.consumptionYuan) || record.consumptionYuan < 0) {
    return -1
  }
  return normalizeNonNegativeInteger(Math.round(record.consumptionYuan * 1000))
}

function normalizeDurationMillis(durationMillis: number): number {
  if (!Number.isFinite(durationMillis) || durationMillis <= 0) {
    return 0
  }
  return normalizeNonNegativeInteger(Math.floor(durationMillis))
}

function normalizeNonNegativeInteger(value: number): number {
  if (!Number.isFinite(value) || value <= 0) {
    return 0
  }
  return Math.min(MAX_SAFE_INTEGER, Math.floor(value))
}

function saturatingAdd(left: number, right: number): number {
  if (right <= 0) {
    return left
  }
  return left > MAX_SAFE_INTEGER - right ? MAX_SAFE_INTEGER : left + right
}
