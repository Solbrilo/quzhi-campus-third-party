import { describe, beforeAll, beforeEach, afterEach, afterAll, it, expect } from '@ohos/hypium';
import { ConsumptionSettlement } from '../main/ets/common/ConsumptionSettlement';
import { ConsumptionRecord, HotWaterConfig, PendingSettlement, UsageSession } from '../main/ets/common/HotWaterModels';
import { HotWaterPayload } from '../main/ets/common/HotWaterPayload';
import { QrCodeParser } from '../main/ets/common/QrCodeParser';
import { OnboardingVersionGate } from '../main/ets/common/OnboardingVersionGate';
import { ConfigFlowBridge } from '../main/ets/common/ConfigFlowBridge';
import { CaptureParser } from '../main/ets/common/CaptureParser';
import { OnboardingQuoteMotion } from '../main/ets/common/OnboardingQuoteMotion';
import { OnboardingResumeGate, OnboardingResumeTarget } from '../main/ets/common/OnboardingResumeGate';
import { HotWaterConfigTransfer } from '../main/ets/common/HotWaterConfigTransfer';
import { ScannerSessionCoordinator } from '../main/ets/common/ScannerSessionCoordinator';

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
    it('parsesSchoolWaterDeviceQrCode', 0, () => {
      expect(QrCodeParser.parseSnCode('KLCXKJ-Water,M,C47F0EDA85D8')).assertEqual('C47F0EDA85D8');
      expect(QrCodeParser.parseSnCode('  klcxkj-water, M, c4:7f0eda85d8\r\n')).assertEqual('C47F0EDA85D8');
      expect(QrCodeParser.parseSnCode('KLCXKJ-Water,M,ROOM,C47F0EDA85D8')).assertEqual('C47F0EDA85D8');
    });
    it('rejectsInvalidSchoolWaterQrCode', 0, () => {
      expect(QrCodeParser.parseSnCode('')).assertEqual(null);
      expect(QrCodeParser.parseSnCode('OTHER,M,C47F0EDA85D8')).assertEqual(null);
      expect(QrCodeParser.parseSnCode('KLCXKJ-Water,C47F0EDA85D8')).assertEqual(null);
      expect(QrCodeParser.parseSnCode('KLCXKJ-Water,,C47F0EDA85D8')).assertEqual(null);
      expect(QrCodeParser.parseSnCode('KLCXKJ-Water,M,not-a-device')).assertEqual(null);
    });
    it('normalizesManuallyEnteredSnCode', 0, () => {
      expect(QrCodeParser.normalizeSnCode(' c4:7f:0e:da:85:d8 ')).assertEqual('C47F0EDA85D8');
      expect(QrCodeParser.normalizeSnCode('not-a-device')).assertEqual(null);
    });
    it('importsTheLatestSessionFromAMultiLoginCapture', 0, () => {
      const oldCode = '11111111111111111111111111111111';
      const newCode = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';
      const capture = `projectId=1&userId=2&accountId=3&loginCode=${oldCode}` +
        '&telephone=13800138000&snCode=001122334455&phoneSystem=Harmony&version=1.0.0\n' +
        `projectId=11&userId=22&accountId=33&loginCode=${newCode}` +
        '&telPhone=13900139000&macAddress=C4:7F:0E:DA:85:D8&phoneSystem=harmonyos&version=1.1.11';
      const config = CaptureParser.parse(capture);
      expect(config.projectId).assertEqual(11);
      expect(config.userId).assertEqual(22);
      expect(config.accountId).assertEqual(33);
      expect(config.loginCode).assertEqual(newCode);
      expect(config.telephone).assertEqual('13900139000');
      expect(config.snCode).assertEqual('C47F0EDA85D8');
      expect(config.phoneSystem).assertEqual('harmonyos');
      expect(config.version).assertEqual('1.1.11');
    });
    it('showsOnboardingOnlyWhenTheVersionHasNotBeenCompleted', 0, () => {
      expect(OnboardingVersionGate.shouldShow(-1, -1, 1006000, 123)).assertTrue();
      expect(OnboardingVersionGate.shouldShow(1005000, 123, 1006000, 123)).assertTrue();
      expect(OnboardingVersionGate.shouldShow(1007000, 123, 1006000, 123)).assertTrue();
      expect(OnboardingVersionGate.shouldShow(1006000, 123, 1006000, 123)).assertFalse();
      expect(OnboardingVersionGate.shouldShow(1006000, 123, 1006000, 456)).assertTrue();
      expect(OnboardingVersionGate.shouldShow(1006000, -1, 1006000, 123)).assertTrue();
      expect(OnboardingVersionGate.shouldShow(1006000, 123, 1006000, 0)).assertFalse();
    });
    it('resumesPersistedConfigurationAtTheFurthestCompletedStep', 0, () => {
      const config = new HotWaterConfig();
      config.projectId = 11;
      config.userId = 22;
      config.accountId = 33;
      config.loginCode = 'login-code';
      config.telephone = '13800138000';
      config.snCode = 'C47F0EDA85D8';
      expect(OnboardingResumeGate.resolve(config)).assertEqual(OnboardingResumeTarget.COMPLETE);

      config.snCode = '';
      expect(OnboardingResumeGate.resolve(config)).assertEqual(OnboardingResumeTarget.SCANNER);

      config.loginCode = '';
      expect(OnboardingResumeGate.resolve(config)).assertEqual(OnboardingResumeTarget.LOGIN);
    });
    it('revealsWelcomeQuoteOneSentenceAtATime', 0, () => {
      expect(OnboardingQuoteMotion.SENTENCE_DURATION_MILLIS).assertEqual(1200);
      expect(OnboardingQuoteMotion.firstSentenceCompleteAtMillis()).assertEqual(1380);
      expect(OnboardingQuoteMotion.secondSentenceStartAtMillis()).assertEqual(1660);
      expect(OnboardingQuoteMotion.secondSentenceCompleteAtMillis()).assertEqual(2860);
    });
    it('revealsWelcomeQuoteAuthorAfterBothSentences', 0, () => {
      expect(OnboardingQuoteMotion.authorStartAtMillis()).assertEqual(3000);
      expect(OnboardingQuoteMotion.authorCompleteAtMillis()).assertEqual(3620);
      expect(
        OnboardingQuoteMotion.authorStartAtMillis() >
          OnboardingQuoteMotion.secondSentenceCompleteAtMillis()
      ).assertTrue();
    });
    it('roundTripsExportedConfiguration', 0, () => {
      const config = new HotWaterConfig();
      config.projectId = 11;
      config.userId = 22;
      config.accountId = 33;
      config.loginCode = 'login-code';
      config.telephone = '13800138000';
      config.snCode = 'C47F0EDA85D8';
      config.macAddress = 'C4:7F:0E:DA:85:D8';
      config.phoneSystem = 'Harmony';
      config.version = '23';
      const exported = HotWaterConfigTransfer.serialize(config, 1234);
      const imported = HotWaterConfigTransfer.parse(exported);
      expect(imported.projectId).assertEqual(11);
      expect(imported.userId).assertEqual(22);
      expect(imported.accountId).assertEqual(33);
      expect(imported.loginCode).assertEqual('login-code');
      expect(imported.telephone).assertEqual('13800138000');
      expect(imported.snCode).assertEqual('C47F0EDA85D8');
      expect(exported).assertContain('"appVersion":"2.2.0"');
      expect(HotWaterConfigTransfer.generateFileName()).assertEqual('hotwater_config.json');
    });
    it('rejectsIncompleteImportedConfiguration', 0, () => {
      expect(() => {
        HotWaterConfigTransfer.parse('{"projectId":1}');
      }).assertThrowError('配置缺少有效的 userId');
    });
    it('serializesScannerCleanupBeforeTheNextStart', 0, async () => {
      const coordinator = new ScannerSessionCoordinator();
      const firstOwner = coordinator.createToken();
      const secondOwner = coordinator.createToken();
      let operationOrder = '';
      expect(await coordinator.acquire(firstOwner, () => true, async () => {
        operationOrder += 'cleanup-start,';
        await Promise.resolve();
        operationOrder += 'cleanup-end,';
      })).assertTrue();
      const firstCleanup = coordinator.release(firstOwner);
      const secondStart = coordinator.acquire(secondOwner, () => {
        operationOrder += 'start-second,';
        return true;
      }, async () => {
      });
      expect(await firstCleanup).assertTrue();
      expect(await secondStart).assertTrue();
      expect(operationOrder).assertEqual('cleanup-start,cleanup-end,start-second,');
    });
    it('doesNotLetStaleScannerCleanupReleaseTheNewOwner', 0, async () => {
      const coordinator = new ScannerSessionCoordinator();
      const firstOwner = coordinator.createToken();
      const secondOwner = coordinator.createToken();
      let firstCleanupCount = 0;
      let secondCleanupCount = 0;
      expect(await coordinator.acquire(firstOwner, () => true, async () => {
        firstCleanupCount++;
      })).assertTrue();
      expect(await coordinator.acquire(secondOwner, () => true, async () => {
        secondCleanupCount++;
      })).assertTrue();
      expect(firstCleanupCount).assertEqual(1);
      expect(coordinator.owns(secondOwner)).assertTrue();
      expect(await coordinator.release(firstOwner)).assertFalse();
      expect(coordinator.owns(secondOwner)).assertTrue();
      expect(await coordinator.release(secondOwner)).assertTrue();
      expect(firstCleanupCount).assertEqual(1);
      expect(secondCleanupCount).assertEqual(1);
    });
    it('keepsRuntimeIdentityWhenOnlyTheLoginCredentialRotates', 0, () => {
      const before = new HotWaterConfig();
      before.projectId = 3;
      before.userId = 2;
      before.accountId = 4;
      before.telephone = '13800138000';
      before.snCode = 'C47F0EDA85D8';
      before.loginCode = 'old-code';
      const after = new HotWaterConfig();
      after.projectId = before.projectId;
      after.userId = before.userId;
      after.accountId = before.accountId;
      after.telephone = before.telephone;
      after.snCode = before.snCode;
      after.loginCode = 'new-code';
      expect(HotWaterConfig.hasSameRuntimeIdentity(before, after)).assertTrue();
      after.snCode = 'AABBCCDDEEFF';
      expect(HotWaterConfig.hasSameRuntimeIdentity(before, after)).assertFalse();
    });
    it('keepsAnUnsavedScannedDeviceCodeWhenLoginRefreshesAccountFields', 0, () => {
      expect(ConfigFlowBridge.resolveSnCodeAfterLogin(
        'c4:7f0eda85d8',
        true,
        'AABBCCDDEEFF'
      )).assertEqual('C47F0EDA85D8');
      expect(ConfigFlowBridge.hasPendingSnCodeAfterLogin(
        'c4:7f0eda85d8',
        true,
        'AABBCCDDEEFF'
      )).assertTrue();
      expect(ConfigFlowBridge.resolveSnCodeAfterLogin(
        'stale-value',
        false,
        'AABBCCDDEEFF'
      )).assertEqual('AABBCCDDEEFF');
      expect(ConfigFlowBridge.hasPendingSnCodeAfterLogin(
        'AABBCCDDEEFF',
        true,
        'AABBCCDDEEFF'
      )).assertFalse();
      expect(ConfigFlowBridge.resolveSnCodeAfterLogin(
        'AABBCCDDEEFF',
        false,
        ''
      )).assertEqual('');
      expect(ConfigFlowBridge.hasPendingSnCodeAfterLogin(
        'AABBCCDDEEFF',
        false,
        ''
      )).assertFalse();
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
    it('recognizesHttpAndBusinessCredentialExpiry', 0, () => {
      expect(HotWaterPayload.isCredentialExpiredResponse('', 401)).assertTrue();
      expect(HotWaterPayload.isCredentialExpiredResponse(
        '{"success":true,"errorCode":401,"data":null}',
        200
      )).assertTrue();
      expect(HotWaterPayload.isCredentialExpiredResponse(
        '{"success":false,"errorCode":403,"errorMessage":"loginCode 已失效"}',
        403
      )).assertTrue();
    });
    it('doesNotTreatLockedOrDeviceBusyResponsesAsCredentialExpiry', 0, () => {
      expect(HotWaterPayload.isCredentialExpiredResponse(
        '{"success":false,"errorCode":403,"errorMessage":"账号已被锁定"}',
        403
      )).assertFalse();
      expect(HotWaterPayload.isCredentialExpiredResponse(
        '{"success":false,"errorCode":307,"errorMessage":"设备正在使用"}',
        200
      )).assertFalse();
    });
    it('requiresAnExplicitFailureForCredentialKeywords', 0, () => {
      expect(HotWaterPayload.isCredentialExpiredResponse(
        '{"success":true,"errorCode":0,"data":{"notice":"auth token invalid 示例"}}',
        200
      )).assertFalse();
      expect(HotWaterPayload.isCredentialExpiredResponse(
        '{"success":false,"errorCode":12,"errorMessage":"请重新登录"}',
        200
      )).assertTrue();
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
    it('blocksReopenOnlyForAFreshUnconfirmedClose', 0, () => {
      const pending = new PendingSettlement();
      pending.orderNo = 'closed-order';
      pending.recordId = 1;
      pending.endedAtMillis = 1_000;

      expect(pending.shouldBlockOpen(15_999, 15_000)).assertTrue();
      expect(pending.shouldBlockOpen(16_000, 15_000)).assertFalse();
      expect(pending.shouldBlockOpen(999, 15_000)).assertFalse();

      pending.closeConfirmed = true;
      expect(pending.shouldBlockOpen(2_000, 15_000)).assertFalse();
      pending.closeConfirmed = false;
      pending.confirmedConsumptionYuan = 0;
      expect(pending.shouldBlockOpen(2_000, 15_000)).assertFalse();
    });
    it('matchesOnlyTheClosedOrderDuringSnapshotRecovery', 0, () => {
      const pending = new PendingSettlement();
      pending.orderNo = 'closed-order';
      expect(pending.matchesOrder('closed-order')).assertTrue();
      expect(pending.matchesOrder('new-order')).assertFalse();
      expect(pending.matchesOrder('')).assertFalse();
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
