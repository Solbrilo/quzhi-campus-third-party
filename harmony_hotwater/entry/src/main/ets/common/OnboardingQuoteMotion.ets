export class OnboardingQuoteMotion {
  static readonly INITIAL_DELAY_MILLIS: number = 180
  static readonly SENTENCE_DURATION_MILLIS: number = 1200
  static readonly SENTENCE_GAP_MILLIS: number = 280
  static readonly AUTHOR_DELAY_MILLIS: number = 140
  static readonly AUTHOR_DURATION_MILLIS: number = 620
  static readonly ENTRY_TRANSLATE_Y: number = 18

  static firstSentenceCompleteAtMillis(): number {
    return OnboardingQuoteMotion.INITIAL_DELAY_MILLIS +
      OnboardingQuoteMotion.SENTENCE_DURATION_MILLIS
  }

  static secondSentenceStartAtMillis(): number {
    return OnboardingQuoteMotion.firstSentenceCompleteAtMillis() +
      OnboardingQuoteMotion.SENTENCE_GAP_MILLIS
  }

  static secondSentenceCompleteAtMillis(): number {
    return OnboardingQuoteMotion.secondSentenceStartAtMillis() +
      OnboardingQuoteMotion.SENTENCE_DURATION_MILLIS
  }

  static authorStartAtMillis(): number {
    return OnboardingQuoteMotion.secondSentenceCompleteAtMillis() +
      OnboardingQuoteMotion.AUTHOR_DELAY_MILLIS
  }

  static authorCompleteAtMillis(): number {
    return OnboardingQuoteMotion.authorStartAtMillis() +
      OnboardingQuoteMotion.AUTHOR_DURATION_MILLIS
  }
}
