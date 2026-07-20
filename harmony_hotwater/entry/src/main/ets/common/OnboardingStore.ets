import preferences from '@ohos.data.preferences'
import type common from '@ohos.app.ability.common'
import { OnboardingVersionGate } from './OnboardingVersionGate'

export class OnboardingStore {
  private static readonly STORE_NAME: string = 'hotwater_onboarding'
  private static readonly VERSION_KEY: string = 'completedVersionCode'
  private static readonly INSTALL_TIME_KEY: string = 'completedInstallTime'
  private context: common.Context

  constructor(context: common.Context) {
    this.context = context
  }

  async shouldShow(versionCode: number, installTime: number): Promise<boolean> {
    const store = await preferences.getPreferences(this.context, OnboardingStore.STORE_NAME)
    const completedVersionCode: number = Number(
      await store.get(OnboardingStore.VERSION_KEY, -1)
    )
    const completedInstallTime: number = Number(
      await store.get(OnboardingStore.INSTALL_TIME_KEY, -1)
    )
    return OnboardingVersionGate.shouldShow(
      completedVersionCode,
      completedInstallTime,
      versionCode,
      installTime
    )
  }

  async markCompleted(versionCode: number, installTime: number): Promise<void> {
    if (!Number.isFinite(versionCode) || versionCode <= 0) {
      throw new Error('Invalid onboarding version code')
    }
    const store = await preferences.getPreferences(this.context, OnboardingStore.STORE_NAME)
    await store.put(OnboardingStore.VERSION_KEY, versionCode)
    await store.put(
      OnboardingStore.INSTALL_TIME_KEY,
      Number.isFinite(installTime) && installTime > 0 ? installTime : 0
    )
    await store.flush()
  }
}
