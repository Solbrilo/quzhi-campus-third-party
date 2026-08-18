import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { bundleManager } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { display, window } from '@kit.ArkUI';
import { connection } from '@kit.NetworkKit';
import { LaunchActionBridge } from '../common/LaunchActionBridge';
import { OnboardingStore } from '../common/OnboardingStore';
import {
  AppAppearance,
  AppAppearanceStore,
  AppearanceThemeMode,
  publishAppearance
} from '../common/AppAppearance';

const DOMAIN = 0x0000;

export default class EntryAbility extends UIAbility {
  private mainWindow?: window.Window;
  private networkConnection?: connection.NetConnection;
  private networkMonitorActive: boolean = false;
  private networkMonitorRegistered: boolean = false;
  private avoidAreaChangeCallback: (options: window.AvoidAreaOptions) => void = (options) => {
    if (options.type === window.AvoidAreaType.TYPE_SYSTEM ||
      options.type === window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR) {
      try {
        this.publishAvoidArea(this.mainWindow);
      } catch (error) {
        hilog.warn(DOMAIN, 'testTag', 'Unable to update avoid area.');
      }
    }
  };
  private networkAvailableCallback: (netHandle: connection.NetHandle) => void = (netHandle) => {
    if (this.networkMonitorActive && netHandle.netId !== 0) {
      this.publishNetworkAvailability(true, true);
    }
  };
  private networkLostCallback: (netHandle: connection.NetHandle) => void = (netHandle) => {
    if (this.networkMonitorActive && netHandle.netId !== 0) {
      this.publishNetworkAvailability(false, true);
    }
  };
  private networkUnavailableCallback: () => void = () => {
    if (this.networkMonitorActive) {
      this.publishNetworkAvailability(false, true);
    }
  };

  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.applyLaunchAction(want);
    this.startNetworkMonitor();
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    this.stopNetworkMonitor();
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageCreate');

    this.initializeWindowStage(windowStage);
  }

  onWindowStageDestroy(): void {
    if (this.mainWindow) {
      try {
        this.mainWindow.off('avoidAreaChange', this.avoidAreaChangeCallback);
      } catch (error) {
        hilog.warn(DOMAIN, 'testTag', 'Unable to unregister avoid area callback.');
      }
      this.mainWindow = undefined;
    }
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

  private async initializeWindowStage(windowStage: window.WindowStage): Promise<void> {
    await this.setupImmersiveWindow(windowStage);
    await this.restoreAppearance();
    await this.loadInitialPage(windowStage);
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

  private async restoreAppearance(): Promise<void> {
    const fallback = new AppAppearance()
    try {
      const appearance = await new AppAppearanceStore(this.context).load()
      publishAppearance(appearance)
      this.context.getApplicationContext().setColorMode(this.resolveColorMode(appearance.themeMode))
    } catch (error) {
      publishAppearance(fallback)
      this.context.getApplicationContext().setColorMode(
        ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
      )
    }
  }

  private resolveColorMode(themeMode: string): ConfigurationConstant.ColorMode {
    if (themeMode === AppearanceThemeMode.DARK) {
      return ConfigurationConstant.ColorMode.COLOR_MODE_DARK
    }
    if (themeMode === AppearanceThemeMode.LIGHT) {
      return ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT
    }
    return ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
  }

  private async setupImmersiveWindow(windowStage: window.WindowStage): Promise<void> {
    try {
      const win = windowStage.getMainWindowSync();
      this.mainWindow = win;
      win.setWindowLayoutFullScreen(true);

      await win.setWindowSystemBarProperties({
        statusBarColor: '#00FFFFFF',
        statusBarContentColor: '#11213B',
        navigationBarColor: '#00FFFFFF',
        navigationBarContentColor: '#11213B'
      });

      this.publishAvoidArea(win);
      win.off('avoidAreaChange', this.avoidAreaChangeCallback);
      win.on('avoidAreaChange', this.avoidAreaChangeCallback);
    } catch (err) {
      hilog.warn(DOMAIN, 'testTag', 'setupImmersiveWindow fallback, using default layout');
    }
  }

  private publishAvoidArea(win?: window.Window): void {
    if (!win) {
      return;
    }

    const systemAvoidArea = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
    const indicatorAvoidArea = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR);
    AppStorage.setOrCreate('statusBarHeight', this.px2vp(systemAvoidArea.topRect.height));
    AppStorage.setOrCreate(
      'bottomSafeHeight',
      this.px2vp(Math.max(systemAvoidArea.bottomRect.height, indicatorAvoidArea.bottomRect.height))
    );
  }

  private px2vp(px: number): number {
    return px / (display.getDefaultDisplaySync().densityDPI / 160);
  }

  private startNetworkMonitor(): void {
    this.networkMonitorActive = true;
    this.publishNetworkAvailability(false, false);
    try {
      const networkConnection = connection.createNetConnection();
      this.networkConnection = networkConnection;
      networkConnection.on('netAvailable', this.networkAvailableCallback);
      networkConnection.on('netLost', this.networkLostCallback);
      networkConnection.on('netUnavailable', this.networkUnavailableCallback);
      this.networkMonitorRegistered = true;
      networkConnection.register((error) => {
        if (error) {
          this.networkMonitorRegistered = false;
          hilog.warn(DOMAIN, 'testTag', 'Unable to register network status listener.');
        }
      });
      this.refreshNetworkAvailability();
    } catch (error) {
      this.networkConnection = undefined;
      this.networkMonitorRegistered = false;
      hilog.warn(DOMAIN, 'testTag', 'Unable to start network status listener.');
    }
  }

  private stopNetworkMonitor(): void {
    this.networkMonitorActive = false;
    const networkConnection = this.networkConnection;
    const wasRegistered = this.networkMonitorRegistered;
    this.networkConnection = undefined;
    this.networkMonitorRegistered = false;
    if (!networkConnection || !wasRegistered) {
      return;
    }
    try {
      networkConnection.unregister((error) => {
        if (error) {
          hilog.warn(DOMAIN, 'testTag', 'Unable to unregister network status listener.');
        }
      });
    } catch (error) {
      hilog.warn(DOMAIN, 'testTag', 'Unable to unregister network status listener.');
    }
  }

  private async refreshNetworkAvailability(): Promise<void> {
    try {
      const netHandle = await connection.getDefaultNet();
      if (this.networkMonitorActive) {
        this.publishNetworkAvailability(netHandle.netId !== 0, true);
      }
    } catch (error) {
      hilog.warn(DOMAIN, 'testTag', 'Unable to query current network status.');
    }
  }

  private publishNetworkAvailability(isAvailable: boolean, isKnown: boolean): void {
    AppStorage.setOrCreate('networkAvailable', isAvailable);
    AppStorage.setOrCreate('networkStatusKnown', isKnown);
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
