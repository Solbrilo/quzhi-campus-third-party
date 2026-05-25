export class LaunchActionBridge {
  private static pendingAction: string = ''

  static setPendingAction(action: string): void {
    LaunchActionBridge.pendingAction = action
  }

  static consumePendingAction(): string {
    const action = LaunchActionBridge.pendingAction
    LaunchActionBridge.pendingAction = ''
    return action
  }
}
