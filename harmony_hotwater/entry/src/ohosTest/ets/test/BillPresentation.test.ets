import { describe, it, expect } from '@ohos/hypium'
import {
  BillGrouping,
  BillScope,
  buildBillEntries,
  filterBillEntries,
  formatBillAmount,
  getBillGroupRenderKey,
  getBillMonths,
  getBillYears,
  groupBillEntries,
  summarizeBillEntries,
  toggleBillGroupCollapsed
} from '../../../main/ets/common/BillPresentation'
import { ConsumptionRecord } from '../../../main/ets/common/HotWaterModels'
import { HotWaterStore } from '../../../main/ets/common/HotWaterStore'

function makeRecord(
  id: number,
  endedAtMillis: number,
  consumptionYuan: number,
  usageDurationMillis: number,
  settlementVerified: boolean = true
): ConsumptionRecord {
  const record = new ConsumptionRecord()
  record.id = id
  record.endedAtMillis = endedAtMillis
  record.consumptionYuan = consumptionYuan
  record.usageDurationMillis = usageDurationMillis
  record.settlementVerified = settlementVerified
  return record
}

export default function billPresentationTest() {
  describe('billPresentation', () => {
    it('usesTheEndTimeForBillingPeriodsAndSortsByEndThenId', 0, () => {
      const january = new Date(2026, 0, 31, 23, 59).getTime()
      const february = new Date(2026, 1, 1, 0, 1).getTime()
      const entries = buildBillEntries([
        makeRecord(4, january, 1, 1),
        makeRecord(2, february, 1, 1),
        makeRecord(3, february, 1, 1)
      ])

      expect(entries[0].record.id).assertEqual(3)
      expect(entries[1].record.id).assertEqual(2)
      expect(entries[2].month).assertEqual(1)
      expect(filterBillEntries(entries, BillScope.YEAR, 2026).length).assertEqual(3)
      expect(filterBillEntries(entries, BillScope.MONTH, 2026, 2).length).assertEqual(2)
      expect(getBillYears(entries).join(',')).assertEqual('2026')
      expect(getBillMonths(entries, 2026).join(',')).assertEqual('2,1')
    })

    it('keepsEveryMonthWhenSwitchingToAnnualScopeAndYearGrouping', 0, () => {
      const august = new Date(2026, 7, 20, 12, 0).getTime()
      const july = new Date(2026, 6, 20, 12, 0).getTime()
      const june = new Date(2026, 5, 20, 12, 0).getTime()
      const previousYear = new Date(2025, 11, 20, 12, 0).getTime()
      const entries = buildBillEntries([
        makeRecord(1, june, 1, 100),
        makeRecord(2, july, 2, 200),
        makeRecord(3, august, 3, 300),
        makeRecord(4, previousYear, 4, 400)
      ])

      const monthlyEntries = filterBillEntries(entries, BillScope.MONTH, 2026, 8)
      const annualEntries = filterBillEntries(entries, BillScope.YEAR, 2026, 8)
      const monthlyGroups = groupBillEntries(monthlyEntries, BillGrouping.YEAR)
      const annualGroups = groupBillEntries(annualEntries, BillGrouping.YEAR)

      expect(monthlyEntries.length).assertEqual(1)
      expect(annualEntries.length).assertEqual(3)
      expect(annualGroups.length).assertEqual(1)
      expect(annualGroups[0].key).assertEqual('year:2026')
      expect(annualGroups[0].entries.length).assertEqual(3)
      expect(annualGroups[0].entries[0].month).assertEqual(8)
      expect(annualGroups[0].entries[1].month).assertEqual(7)
      expect(annualGroups[0].entries[2].month).assertEqual(6)
      expect(getBillGroupRenderKey(
        monthlyGroups[0], BillScope.MONTH, 2026, 8, BillGrouping.YEAR
      ) !== getBillGroupRenderKey(
        annualGroups[0], BillScope.YEAR, 2026, 0, BillGrouping.YEAR
      )).assertTrue()
    })

    it('sumsKnownAmountsInMilliYuanAndKeepsUnknownUsageDuration', 0, () => {
      const endedAtMillis = new Date(2026, 4, 20, 12, 0).getTime()
      const entries = buildBillEntries([
        makeRecord(1, endedAtMillis, 0.004, 500),
        makeRecord(2, endedAtMillis - 1, 1.006, 1000, false),
        makeRecord(3, endedAtMillis - 2, -1, 2000, false),
        makeRecord(4, endedAtMillis - 3, 0, 3000)
      ])
      const summary = summarizeBillEntries(entries)

      expect(summary.recordCount).assertEqual(4)
      expect(summary.settledCount).assertEqual(3)
      expect(summary.pendingCount).assertEqual(1)
      expect(summary.totalMilliYuan).assertEqual(1010)
      expect(summary.totalDurationMillis).assertEqual(6500)
      expect(formatBillAmount(summary.totalMilliYuan)).assertEqual('¥1.01')
    })

    it('groupsEntriesAndMaintainsCollapsedKeysWithoutDuplicates', 0, () => {
      const may = new Date(2026, 4, 20, 12, 0).getTime()
      const april = new Date(2026, 3, 20, 12, 0).getTime()
      const entries = buildBillEntries([
        makeRecord(1, may, 1, 100),
        makeRecord(2, april, 2, 200)
      ])
      const collapsedKeys = toggleBillGroupCollapsed([], 'month:2026-05')
      const groups = groupBillEntries(entries, BillGrouping.MONTH, collapsedKeys)

      expect(groups.length).assertEqual(2)
      expect(groups[0].key).assertEqual('month:2026-05')
      expect(groups[0].collapsed).assertTrue()
      expect(groups[0].summary.totalMilliYuan).assertEqual(1000)
      expect(toggleBillGroupCollapsed(collapsedKeys, 'month:2026-05').length).assertEqual(0)
    })

    it('retainsTheNewestTwelveHundredRecordsByEndTimeThenId', 0, () => {
      const records: ConsumptionRecord[] = []
      const endedAtMillis = new Date(2026, 4, 20, 12, 0).getTime()
      for (let id = 1; id <= 1202; id++) {
        records.push(makeRecord(id, endedAtMillis, 1, 0))
      }
      const normalized = HotWaterStore.normalizeConsumptionRecords(records)

      expect(normalized.length).assertEqual(1200)
      expect(normalized[0].id).assertEqual(1202)
      expect(normalized[1199].id).assertEqual(3)
    })
  })
}
