export type InstalledApp = {
  packageName: string;
  label: string;
};

export type WatchedApp = {
  packageName: string;
  thresholdSeconds: number;
  enabled: boolean;
};
