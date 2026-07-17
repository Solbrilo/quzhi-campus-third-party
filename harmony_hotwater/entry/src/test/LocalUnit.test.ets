import { describe, beforeAll, beforeEach, afterEach, afterAll, it, expect } from '@ohos/hypium';
import { ConsumptionSettlement } from '../main/ets/common/ConsumptionSettlement';
import { ConsumptionRecord, UsageSession } from '../main/ets/common/HotWaterModels';
import { HotWaterPayload } from '../main/ets/common/HotWaterPayload';

export default function localUnitTest() {
  describe('localUnitTest', () => {
    // Defines a test suite. Two parameters are supported: test suite name and test suite function.
    beforeAll(() => {
      // Presets an action, which is performed only once before all test cases of the test suite start.
      // This API supports only one parameter: preset action function.
    });
    beforeEach(() => {
      // Presets an action, which is performed before each unit test case starts.
      // The number of execution times is the same as the number of test cases defined by **it**.
      // This API supports only one parameter: preset action function.
    });
    afterEach(() => {
      // Presets a clear action, which is performed after each unit test case ends.
      // The number of execution times is the same as the number of test cases defined by **it**.
      // This API supports only one parameter: clear action function.
    });
    afterAll(() => {
      // Presets a clear action, which is performed after all test cases of the test suite end.
      // This API supports only one parameter: clear action function.
    });
    it('assertContain', 0, () => {
      // Defines a test case. This API supports three parameters: test case name, filter parameter, and test case function.
      let a = 'abc';
      let b = 'b';
      // Defines a variety of assertion methods, which are used to declare expected boolean conditions.
      expect(a).assertContain(b);
      expect(a).assertEqual(a);
    });
    it('usesBalanceBeforePreauthorizationAsSettlementBaseline', 0, () => {
      expect(ConsumptionSettlement.recoverStartBalance(7.31)).assertEqual(12.31);
      expect(ConsumptionSettlement.calculateConsumption(12.31, 11.87)).assertEqual(0.44);
    });
    it('detectsWhenPostCloseBalanceLeavesTheReservedValue', 0, () => {
      expect(ConsumptionSettlement.expectedActiveBalance(12.31)).assertEqual(7.31);
      expect(ConsumptionSettlement.hasSettledBalanceChanged(7.31, 7.31)).assertFalse();
      expect(ConsumptionSettlement.hasSettledBalanceChanged(7.31, 11.87)).assertTrue();
      expect(ConsumptionSettlement.calculateSettledConsumption(12.31, -1, 11.87)).assertEqual(-1);
      expect(ConsumptionSettlement.calculateSettledConsumption(12.31, 7.31, 7.31)).assertEqual(-1);
      expect(ConsumptionSettlement.calculateSettledConsumption(12.31, 7.31, 11.87)).assertEqual(0.44);
    });
    it('acceptsOnlyTheWalletBalanceMatchingTheFinalConsumption', 0, () => {
      expect(ConsumptionSettlement.matchesExplicitConsumption(12.31, 0.44, 11.87)).assertTrue();
      expect(ConsumptionSettlement.matchesExplicitConsumption(12.31, 0.44, 7.31)).assertFalse();
      expect(ConsumptionSettlement.matchesExplicitConsumption(12.31, 0.44, 11.90)).assertFalse();
    });
    it('keepsZeroOnlyAfterTheReservedBalanceHasBeenReleased', 0, () => {
      expect(ConsumptionSettlement.calculateSettledConsumption(20, 15, 15)).assertEqual(-1);
      expect(ConsumptionSettlement.calculateSettledConsumption(20, 15, 20)).assertEqual(0);
      expect(ConsumptionSettlement.resolveConsumption(-1, 20, 20, true)).assertEqual(0);
    });
    it('doesNotRenderSmallPositiveConsumptionAsZero', 0, () => {
      const record = new ConsumptionRecord();
      record.consumptionYuan = 0.004;
      expect(record.getConsumptionLabel()).assertEqual('< ¥0.01');
    });
    it('keepsExactOneCentOutOfTheSubCentLabel', 0, () => {
      const record = new ConsumptionRecord();
      record.consumptionYuan = 0.01;
      expect(record.getConsumptionLabel()).assertEqual('¥ 0.01');
    });
    it('doesNotTreatMissingPayloadAmountsAsZero', 0, () => {
      expect(HotWaterPayload.extractConsumptionYuan('{"success":true,"data":null}')).assertEqual(-1);
      expect(() => {
        HotWaterPayload.extractBalanceYuan('{"success":true,"data":null}');
      }).assertThrowError('余额响应缺少有效金额。');
    });
    it('usesFinalOrderAmountWhenFiveYuanBalanceDoesNotMove', 0, () => {
      const payload = '{"success":true,"errorCode":0,"data":{"consumeMoney":5000}}';
      const explicitConsumptionYuan = HotWaterPayload.extractConfirmedConsumptionYuan(payload);
      expect(explicitConsumptionYuan).assertEqual(5);
      expect(ConsumptionSettlement.resolveConsumption(explicitConsumptionYuan, 20, 15, true)).assertEqual(5);
      expect(ConsumptionSettlement.matchesExplicitConsumption(20, 5, 15)).assertTrue();
    });
    it('keepsFailedSettlementPayloadPendingEvenWhenItContainsMoney', 0, () => {
      const failedPayload = '{"success":false,"errorCode":307,"data":{"consumeMoney":5000}}';
      const errorPayload = '{"success":true,"errorCode":12,"data":{"consumeMoney":5000}}';
      expect(HotWaterPayload.extractConfirmedConsumptionYuan(failedPayload)).assertEqual(-1);
      expect(HotWaterPayload.extractConfirmedConsumptionYuan(errorPayload)).assertEqual(-1);
      expect(ConsumptionSettlement.calculateSettledConsumption(20, 15, 15)).assertEqual(-1);
    });
    it('acceptsPositiveMilliYuanButIgnoresPlaceholderZero', 0, () => {
      expect(HotWaterPayload.extractConsumptionYuan('{"data":{"consumeMoney":4}}')).assertEqual(0.004);
      expect(HotWaterPayload.extractConsumptionYuan('{"data":{"consumeMoney":0}}')).assertEqual(-1);
      expect(HotWaterPayload.extractConsumptionYuan('{"data":{"consumeMoney":0.4}}')).assertEqual(-1);
    });
    it('acceptsFinalOrderAmountWithoutABalanceBaseline', 0, () => {
      expect(ConsumptionSettlement.resolveConsumption(1.25, -1, 15, true)).assertEqual(1.25);
      expect(ConsumptionSettlement.resolveConsumption(-1, -1, 15, true)).assertEqual(-1);
    });
    it('allowsBalanceFallbackOnlyForTheLatestFreshInactiveSettlement', 0, () => {
      const nowMillis = 1_000_000;
      expect(ConsumptionSettlement.canUseBalanceFallback(
        nowMillis - 60_000,
        nowMillis,
        true,
        true,
        false,
        true
      )).assertTrue();
    });
    it('rejectsStaleBalanceFallback', 0, () => {
      const nowMillis = 1_000_000;
      expect(ConsumptionSettlement.canUseBalanceFallback(
        nowMillis - 10 * 60 * 1000 - 1,
        nowMillis,
        true,
        true,
        false,
        true
      )).assertFalse();
      expect(ConsumptionSettlement.isFreshSettlement(
        nowMillis - 10 * 60 * 1000 - 1,
        nowMillis
      )).assertFalse();
    });
    it('rejectsBalanceFallbackWhenAnotherUsageMayExist', 0, () => {
      const nowMillis = 1_000_000;
      expect(ConsumptionSettlement.canUseBalanceFallback(
        nowMillis - 60_000,
        nowMillis,
        true,
        true,
        true,
        true
      )).assertFalse();
      expect(ConsumptionSettlement.canUseBalanceFallback(
        nowMillis - 60_000,
        nowMillis,
        true,
        true,
        false,
        false
      )).assertFalse();
    });
    it('expiresOnlyProvisionalOpeningSessions', 0, () => {
      const session = new UsageSession();
      session.startedAtMillis = 1_000;
      session.provisionalOpening = true;
      expect(session.isExpiredProvisionalOpening(121_000, 120_000)).assertTrue();
      session.provisionalOpening = false;
      expect(session.isExpiredProvisionalOpening(121_000, 120_000)).assertFalse();
    });
    it('keepsConfirmedBalanceGuardWhenTheBaselineIsUnknown', 0, () => {
      expect(ConsumptionSettlement.shouldKeepConfirmedBalanceGuard(
        -1,
        0.44,
        7.31,
        100_000,
        110_000,
        false
      )).assertTrue();
      expect(ConsumptionSettlement.shouldKeepConfirmedBalanceGuard(
        12.31,
        0.44,
        11.87,
        100_000,
        110_000,
        false
      )).assertFalse();
      expect(ConsumptionSettlement.shouldKeepConfirmedBalanceGuard(
        -1,
        0.44,
        7.31,
        100_000,
        110_000,
        true
      )).assertFalse();
    });
    it('neverDowngradesAVerifiedSettlementToPending', 0, () => {
      expect(ConsumptionRecord.canReplaceSettlement(true, false)).assertFalse();
      expect(ConsumptionRecord.canReplaceSettlement(false, true)).assertTrue();
      expect(ConsumptionRecord.canReplaceSettlement(true, true)).assertTrue();
    });
  });
}
