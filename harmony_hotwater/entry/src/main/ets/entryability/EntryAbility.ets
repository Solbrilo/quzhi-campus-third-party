import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { bundleManager } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { display, window } from '@kit.ArkUI';
import { LaunchActionBridge } from '../common/LaunchActionBridge';
import { OnboardingStore } from '../common/OnboardingStore';

const DOMAIN = 0x0000;

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    this.applyLaunchAction(want);
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageCreate');

    this.loadInitialPage(windowStage);

    this.setupImmersiveWindow(windowStage);
  }

  onWindowStageDestroy(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onForeground');
  }

  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.applyLaunchAction(want);
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onNewWant');
  }

  onBackground(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onBackground');
  }

  private async loadInitialPage(windowStage: window.WindowStage): Promise<void> {
    let initialPage = 'pages/Onboarding';
    try {
      const bundleInfo = await bundleManager.getBundleInfoForSelf(
        bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT
      );
      const onboardingStore = new OnboardingStore(this.context);
      if (!await onboardingStore.shouldShow(bundleInfo.versionCode, bundleInfo.installTime)) {
        initialPage = 'pages/Index';
      }
    } catch (error) {
      hilog.warn(DOMAIN, 'testTag', 'Unable to determine onboarding state, opening onboarding page.');
    }
    windowStage.loadContent(initialPage, (err) => {
      if (err.code) {
        hilog.error(DOMAIN, 'testTag', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(DOMAIN, 'testTag', 'Succeeded in loading the content.');
    });
  }

  private async setupImmersiveWindow(windowStage: window.WindowStage): Promise<void> {
    try {
      const win = windowStage.getMainWindowSync();
      win.setWindowLayoutFullScreen(true);

      await win.setWindowSystemBarProperties({
        statusBarColor: '#00FFFFFF',
        statusBarContentColor: '#11213B',
        navigationBarColor: '#00FFFFFF',
        navigationBarContentColor: '#11213B'
      });

      const avoidArea = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
      AppStorage.setOrCreate('statusBarHeight', this.px2vp(avoidArea.topRect.height));

      const bottomAvoidArea = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR);
      AppStorage.setOrCreate('bottomSafeHeight', this.px2vp(bottomAvoidArea.bottomRect.height));
    } catch (err) {
      hilog.warn(DOMAIN, 'testTag', 'setupImmersiveWindow fallback, using default layout');
    }
  }

  private px2vp(px: number): number {
    return px / (display.getDefaultDisplaySync().densityDPI / 160);
  }

  private applyLaunchAction(want: Want): void {
    try {
      const parameters = want.parameters;
      if (parameters && parameters['widgetAction']) {
        LaunchActionBridge.setPendingAction(String(parameters['widgetAction']));
      }
    } catch (error) {
      hilog.warn(DOMAIN, 'testTag', 'applyLaunchAction failed');
    }
  }
}
