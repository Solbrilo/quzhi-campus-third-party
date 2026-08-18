import preferences from '@ohos.data.preferences'
import type common from '@ohos.app.ability.common'

export class AppearanceThemeMode {
  static readonly SYSTEM: string = 'system'
  static readonly LIGHT: string = 'light'
  static readonly DARK: string = 'dark'
}

export class AppearanceMaterialType {
  static readonly ADAPTIVE: string = 'adaptive'
  static readonly IMMERSIVE: string = 'immersive'
  static readonly NONE: string = 'none'
}

export class AppearanceMaterialLevel {
  static readonly ADAPTIVE: string = 'adaptive'
  static readonly EXQUISITE: string = 'exquisite'
  static readonly GENTLE: string = 'gentle'
  static readonly SMOOTH: string = 'smooth'
}

export const APPEARANCE_THEME_MODE_KEY: string = 'appearanceThemeMode'
export const APPEARANCE_MATERIAL_TYPE_KEY: string = 'appearanceMaterialType'
export const APPEARANCE_MATERIAL_LEVEL_KEY: string = 'appearanceMaterialLevel'

export class AppAppearance {
  themeMode: string = AppearanceThemeMode.SYSTEM
  materialType: string = AppearanceMaterialType.ADAPTIVE
  materialLevel: string = AppearanceMaterialLevel.ADAPTIVE
}

export class AppAppearanceStore {
  private static readonly STORE_NAME: string = 'hotwater_appearance'
  private context: common.Context

  constructor(context: common.Context) {
    this.context = context
  }

  async load(): Promise<AppAppearance> {
    const store = await preferences.getPreferences(this.context, AppAppearanceStore.STORE_NAME)
    const appearance = new AppAppearance()
    appearance.themeMode = normalizeThemeMode(String(await store.get('themeMode', appearance.themeMode)))
    appearance.materialType = normalizeMaterialType(String(await store.get('materialType', appearance.materialType)))
    appearance.materialLevel = normalizeMaterialLevel(String(await store.get('materialLevel', appearance.materialLevel)))
    return appearance
  }

  async save(appearance: AppAppearance): Promise<AppAppearance> {
    const normalized = new AppAppearance()
    normalized.themeMode = normalizeThemeMode(appearance.themeMode)
    normalized.materialType = normalizeMaterialType(appearance.materialType)
    normalized.materialLevel = normalizeMaterialLevel(appearance.materialLevel)
    const store = await preferences.getPreferences(this.context, AppAppearanceStore.STORE_NAME)
    await store.put('themeMode', normalized.themeMode)
    await store.put('materialType', normalized.materialType)
    await store.put('materialLevel', normalized.materialLevel)
    await store.flush()
    return normalized
  }
}

export function publishAppearance(appearance: AppAppearance): void {
  AppStorage.setOrCreate(APPEARANCE_THEME_MODE_KEY, normalizeThemeMode(appearance.themeMode))
  AppStorage.setOrCreate(APPEARANCE_MATERIAL_TYPE_KEY, normalizeMaterialType(appearance.materialType))
  AppStorage.setOrCreate(APPEARANCE_MATERIAL_LEVEL_KEY, normalizeMaterialLevel(appearance.materialLevel))
}

export function normalizeThemeMode(value: string): string {
  switch (value) {
    case AppearanceThemeMode.LIGHT:
    case AppearanceThemeMode.DARK:
      return value
    default:
      return AppearanceThemeMode.SYSTEM
  }
}

export function normalizeMaterialType(value: string): string {
  switch (value) {
    case AppearanceMaterialType.IMMERSIVE:
    case AppearanceMaterialType.NONE:
      return value
    default:
      return AppearanceMaterialType.ADAPTIVE
  }
}

export function normalizeMaterialLevel(value: string): string {
  switch (value) {
    case AppearanceMaterialLevel.EXQUISITE:
    case AppearanceMaterialLevel.GENTLE:
    case AppearanceMaterialLevel.SMOOTH:
      return value
    default:
      return AppearanceMaterialLevel.ADAPTIVE
  }
}
