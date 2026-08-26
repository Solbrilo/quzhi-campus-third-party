import preferences from '@ohos.data.preferences'
import type common from '@ohos.app.ability.common'

export class FeedbackHistoryEntry {
  submittedAtMillis: number = 0
  feedbackId: string = ''
  category: string = ''
  email: string = ''
  message: string = ''
}

export class FeedbackHistoryStore {
  static readonly MAX_ENTRIES: number = 20
  private static readonly STORE_NAME: string = 'hotwater_feedback_history'
  private static readonly HISTORY_KEY: string = 'entries'
  private context: common.Context

  constructor(context: common.Context) {
    this.context = context
  }

  async load(): Promise<FeedbackHistoryEntry[]> {
    const store = await preferences.getPreferences(this.context, FeedbackHistoryStore.STORE_NAME)
    const serialized = String(await store.get(FeedbackHistoryStore.HISTORY_KEY, ''))
    return FeedbackHistoryStore.deserialize(serialized)
  }

  async append(entry: FeedbackHistoryEntry): Promise<FeedbackHistoryEntry[]> {
    const entries = await this.load()
    entries.unshift(FeedbackHistoryStore.copyEntry(entry))
    const limitedEntries = FeedbackHistoryStore.limit(entries)
    const store = await preferences.getPreferences(this.context, FeedbackHistoryStore.STORE_NAME)
    await store.put(FeedbackHistoryStore.HISTORY_KEY, FeedbackHistoryStore.serialize(limitedEntries))
    await store.flush()
    return limitedEntries
  }

  async clear(): Promise<void> {
    const store = await preferences.getPreferences(this.context, FeedbackHistoryStore.STORE_NAME)
    await store.delete(FeedbackHistoryStore.HISTORY_KEY)
    await store.flush()
  }

  static serialize(entries: FeedbackHistoryEntry[]): string {
    const limitedEntries = FeedbackHistoryStore.limit(entries)
    return JSON.stringify(limitedEntries)
  }

  static deserialize(serialized: string): FeedbackHistoryEntry[] {
    if (serialized.trim().length === 0) {
      return []
    }
    try {
      const parsed = JSON.parse(serialized) as Array<Object>
      if (!Array.isArray(parsed)) {
        return []
      }
      const entries: FeedbackHistoryEntry[] = []
      for (let index = 0; index < parsed.length && entries.length < FeedbackHistoryStore.MAX_ENTRIES;
        index++) {
        const value = parsed[index] as Record<string, Object>
        const entry = new FeedbackHistoryEntry()
        entry.submittedAtMillis = Number(value['submittedAtMillis'])
        entry.feedbackId = String(value['feedbackId'] ?? '').trim()
        entry.category = String(value['category'] ?? '').trim()
        entry.email = String(value['email'] ?? '').trim()
        entry.message = String(value['message'] ?? '').trim()
        if (Number.isFinite(entry.submittedAtMillis) && entry.submittedAtMillis > 0 &&
          entry.feedbackId.length > 0 && entry.category.length > 0 && entry.email.length > 0 &&
          entry.message.length > 0) {
          entries.push(entry)
        }
      }
      return entries
    } catch (error) {
      return []
    }
  }

  static maskEmail(email: string): string {
    const normalized = email.trim()
    const atIndex = normalized.indexOf('@')
    if (atIndex <= 0 || atIndex === normalized.length - 1) {
      return '***'
    }
    const localPart = normalized.substring(0, atIndex)
    const domain = normalized.substring(atIndex + 1)
    const prefix = localPart.substring(0, Math.min(2, localPart.length))
    const suffix = localPart.length > 2 ? localPart.substring(localPart.length - 1) : ''
    return `${prefix}***${suffix}@${domain}`
  }

  static formatTimestamp(value: number): string {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return '时间未知'
    }
    return `${date.getFullYear()}-${FeedbackHistoryStore.twoDigits(date.getMonth() + 1)}` +
      `-${FeedbackHistoryStore.twoDigits(date.getDate())} ` +
      `${FeedbackHistoryStore.twoDigits(date.getHours())}:${FeedbackHistoryStore.twoDigits(date.getMinutes())}`
  }

  private static copyEntry(entry: FeedbackHistoryEntry): FeedbackHistoryEntry {
    const copied = new FeedbackHistoryEntry()
    copied.submittedAtMillis = entry.submittedAtMillis
    copied.feedbackId = entry.feedbackId.trim()
    copied.category = entry.category.trim()
    copied.email = entry.email.trim()
    copied.message = entry.message.trim()
    return copied
  }

  private static limit(entries: FeedbackHistoryEntry[]): FeedbackHistoryEntry[] {
    const limited: FeedbackHistoryEntry[] = []
    for (let index = 0; index < entries.length && limited.length < FeedbackHistoryStore.MAX_ENTRIES;
      index++) {
      limited.push(FeedbackHistoryStore.copyEntry(entries[index]))
    }
    return limited
  }

  private static twoDigits(value: number): string {
    return value < 10 ? `0${value}` : String(value)
  }
}
