import { describe, it, expect } from '@ohos/hypium'
import {
  FeedbackCategory,
  FeedbackClient,
  FeedbackSubmitState
} from '../main/ets/common/FeedbackClient'

export default function feedbackClientTest() {
  describe('feedbackClient', () => {
    it('serializesOnlyContractFields', 0, () => {
      const json = FeedbackClient.buildPayloadForTest('1.4.1', FeedbackCategory.BUG, '余额没有刷新')
      const payload = JSON.parse(json) as Record<string, Object>
      const keys = Object.keys(payload).sort().join(',')
      expect(keys).assertEqual('appVersion,category,message,platform,schemaVersion')
      expect(String(payload['platform'])).assertEqual('harmonyos')
      expect(String(payload['category'])).assertEqual('bug')
    })

    it('countsUnicodeCodePoints', 0, () => {
      expect(FeedbackClient.countUnicodeCharacters('😀好')).assertEqual(2)
      expect(FeedbackClient.validationError('😀')).assertEqual('请至少填写 2 个字。')
      expect(FeedbackClient.validationError('😀好')).assertEqual('')
    })

    it('returnsFeedbackIdOnCreated', 0, () => {
      const result = FeedbackClient.mapResponse(
        201,
        '{"ok":true,"feedbackId":"fb_test_123"}',
        ''
      )
      expect(result.state).assertEqual(FeedbackSubmitState.SUCCESS)
      expect(result.feedbackId).assertEqual('fb_test_123')
    })

    it('parsesRetryAfter', 0, () => {
      expect(FeedbackClient.parseRetryAfter('75', 0)).assertEqual(75)
      const now = Date.parse('Fri, 15 Jan 2027 08:00:30 GMT')
      expect(FeedbackClient.parseRetryAfter('Fri, 15 Jan 2027 08:01:00 GMT', now)).assertEqual(30)
    })
  })
}
