import { HotWaterConfig } from './HotWaterModels'

export enum OnboardingResumeTarget {
  LOGIN = 0,
  SCANNER = 1,
  COMPLETE = 2
}

export class OnboardingResumeGate {
  static resolve(config: HotWaterConfig): OnboardingResumeTarget {
    if (config.isComplete()) {
      return OnboardingResumeTarget.COMPLETE
    }
    if (OnboardingResumeGate.hasPersistedAccount(config)) {
      return OnboardingResumeTarget.SCANNER
    }
    return OnboardingResumeTarget.LOGIN
  }

  private static hasPersistedAccount(config: HotWaterConfig): boolean {
    return config.projectId > 0 &&
      config.userId > 0 &&
      config.accountId > 0 &&
      config.loginCode.trim().length > 0 &&
      config.telephone.trim().length > 0
  }
}
