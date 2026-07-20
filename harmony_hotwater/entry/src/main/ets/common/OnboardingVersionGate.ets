export class OnboardingVersionGate {
  static shouldShow(
    completedVersionCode: number,
    completedInstallTime: number,
    currentVersionCode: number,
    currentInstallTime: number
  ): boolean {
    if (!Number.isFinite(currentVersionCode) || currentVersionCode <= 0) {
      return true
    }
    if (completedVersionCode !== currentVersionCode) {
      return true
    }
    if (!Number.isFinite(currentInstallTime) || currentInstallTime <= 0) {
      return false
    }
    return !Number.isFinite(completedInstallTime) || completedInstallTime <= 0 ||
      completedInstallTime !== currentInstallTime
  }
}
