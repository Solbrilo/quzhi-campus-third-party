import { describe, it, expect } from '@ohos/hypium'
import { FeedbackHistoryEntry, FeedbackHistoryStore } from '../main/ets/common/FeedbackHistoryStore'

export default function feedbackHistoryStoreTest() {
  describe('feedbackHistoryStore', () => {
    it('roundTripsAndLimitsSuccessfulFeedbackEntries', 0, () => {
      const entries: FeedbackHistoryEntry[] = []
      for (let index = 0; index < FeedbackHistoryStore.MAX_ENTRIES + 3; index++) {
        const entry = new FeedbackHistoryEntry()
        entry.submittedAtMillis = 1_000 + index
        entry.feedbackId = `fb_${index}`
        entry.category = 'bug'
        entry.email = `user${index}@example.com`
        entry.message = `反馈 ${index}`
        entries.push(entry)
      }

      const restored = FeedbackHistoryStore.deserialize(FeedbackHistoryStore.serialize(entries))
      expect(restored.length).assertEqual(FeedbackHistoryStore.MAX_ENTRIES)
      expect(restored[0].feedbackId).assertEqual('fb_0')
      expect(restored[FeedbackHistoryStore.MAX_ENTRIES - 1].feedbackId)
        .assertEqual(`fb_${FeedbackHistoryStore.MAX_ENTRIES - 1}`)
    })

    it('masksTheEmailShownInHistory', 0, () => {
      expect(FeedbackHistoryStore.maskEmail('alice@example.com')).assertEqual('al***e@example.com')
      expect(FeedbackHistoryStore.maskEmail('a@example.com')).assertEqual('a***@example.com')
      expect(FeedbackHistoryStore.maskEmail('invalid')).assertEqual('***')
    })
  })
}
