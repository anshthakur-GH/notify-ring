import { NativeModule, requireNativeModule } from 'expo';
import type { InstalledApp, WatchedApp } from './NotifyListenerModule.types';

declare class NotifyListenerModule extends NativeModule<{}> {
  isNotificationAccessGranted(): boolean;
  openNotificationAccessSettings(): void;

  isIgnoringBatteryOptimizations(): boolean;
  requestIgnoreBatteryOptimizations(): void;
  openAutoStartSettings(): void;

  canScheduleExactAlarms(): boolean;
  openExactAlarmSettings(): void;

  getInstalledApps(): InstalledApp[];

  getWatchedApps(): WatchedApp[];
  setWatchedApp(packageName: string, thresholdSeconds: number, enabled: boolean): void;
  removeWatchedApp(packageName: string): void;
}

export default requireNativeModule<NotifyListenerModule>('NotifyListener');
export type { InstalledApp, WatchedApp };
