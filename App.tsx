import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AppState,
  FlatList,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';

import NotifyListener, { InstalledApp, WatchedApp } from './modules/notify-listener';

const THRESHOLD_OPTIONS_MIN = [1, 2, 3, 5, 10, 15, 30];
const DEFAULT_THRESHOLD_MIN = 2;

type PermissionState = {
  notificationAccess: boolean;
  batteryExempt: boolean;
  exactAlarm: boolean;
};

function usePermissionState(): [PermissionState, () => void] {
  const [state, setState] = useState<PermissionState>({
    notificationAccess: false,
    batteryExempt: false,
    exactAlarm: false,
  });

  const refresh = useCallback(() => {
    setState({
      notificationAccess: NotifyListener.isNotificationAccessGranted(),
      batteryExempt: NotifyListener.isIgnoringBatteryOptimizations(),
      exactAlarm: NotifyListener.canScheduleExactAlarms(),
    });
  }, []);

  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  return [state, refresh];
}

function PermissionBanner({ state }: { state: PermissionState }) {
  const items: { label: string; ok: boolean; onPress: () => void }[] = [
    {
      label: 'Notification access',
      ok: state.notificationAccess,
      onPress: () => NotifyListener.openNotificationAccessSettings(),
    },
    {
      label: 'Ignore battery optimization',
      ok: state.batteryExempt,
      onPress: () => NotifyListener.requestIgnoreBatteryOptimizations(),
    },
    {
      label: 'Exact alarm permission',
      ok: state.exactAlarm,
      onPress: () => NotifyListener.openExactAlarmSettings(),
    },
  ];

  const missing = items.filter((i) => !i.ok);
  if (missing.length === 0) return null;

  return (
    <View style={styles.banner}>
      <Text style={styles.bannerTitle}>Setup required</Text>
      <Text style={styles.bannerSubtitle}>
        Without these, watched apps won't reliably ring you.
      </Text>
      {missing.map((item) => (
        <Pressable key={item.label} style={styles.bannerButton} onPress={item.onPress}>
          <Text style={styles.bannerButtonText}>Grant: {item.label}</Text>
        </Pressable>
      ))}
    </View>
  );
}

type ThresholdModalProps = {
  app: InstalledApp | null;
  currentSeconds: number;
  onClose: () => void;
  onSave: (seconds: number) => void;
};

function ThresholdModal({ app, currentSeconds, onClose, onSave }: ThresholdModalProps) {
  const [minutes, setMinutes] = useState(DEFAULT_THRESHOLD_MIN);

  useEffect(() => {
    if (app) setMinutes(Math.max(1, Math.round(currentSeconds / 60)));
  }, [app, currentSeconds]);

  return (
    <Modal visible={!!app} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.modalOverlay}>
        <View style={styles.modalCard}>
          <Text style={styles.modalTitle}>{app?.label}</Text>
          <Text style={styles.modalSubtitle}>Ring me if unopened after:</Text>
          <View style={styles.optionsRow}>
            {THRESHOLD_OPTIONS_MIN.map((m) => (
              <Pressable
                key={m}
                style={[styles.optionChip, minutes === m && styles.optionChipSelected]}
                onPress={() => setMinutes(m)}
              >
                <Text style={[styles.optionChipText, minutes === m && styles.optionChipTextSelected]}>
                  {m}m
                </Text>
              </Pressable>
            ))}
          </View>
          <View style={styles.modalActions}>
            <Pressable style={styles.modalCancel} onPress={onClose}>
              <Text style={styles.modalCancelText}>Cancel</Text>
            </Pressable>
            <Pressable style={styles.modalSave} onPress={() => onSave(minutes * 60)}>
              <Text style={styles.modalSaveText}>Save</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

export default function App() {
  const [permissions, refreshPermissions] = usePermissionState();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [watched, setWatched] = useState<Record<string, WatchedApp>>({});
  const [query, setQuery] = useState('');
  const [editingApp, setEditingApp] = useState<InstalledApp | null>(null);

  const loadApps = useCallback(() => {
    setApps(NotifyListener.getInstalledApps());
    const list = NotifyListener.getWatchedApps();
    const map: Record<string, WatchedApp> = {};
    for (const w of list) map[w.packageName] = w;
    setWatched(map);
  }, []);

  useEffect(() => {
    loadApps();
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    }
  }, [loadApps]);

  const filteredApps = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return apps;
    return apps.filter(
      (a) => a.label.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)
    );
  }, [apps, query]);

  const toggleWatch = (app: InstalledApp, enabled: boolean) => {
    if (enabled) {
      const existingSeconds = watched[app.packageName]?.thresholdSeconds ?? DEFAULT_THRESHOLD_MIN * 60;
      NotifyListener.setWatchedApp(app.packageName, existingSeconds, true);
      setWatched((prev) => ({
        ...prev,
        [app.packageName]: { packageName: app.packageName, thresholdSeconds: existingSeconds, enabled: true },
      }));
    } else {
      NotifyListener.removeWatchedApp(app.packageName);
      setWatched((prev) => {
        const next = { ...prev };
        delete next[app.packageName];
        return next;
      });
    }
  };

  const saveThreshold = (seconds: number) => {
    if (!editingApp) return;
    NotifyListener.setWatchedApp(editingApp.packageName, seconds, true);
    setWatched((prev) => ({
      ...prev,
      [editingApp.packageName]: { packageName: editingApp.packageName, thresholdSeconds: seconds, enabled: true },
    }));
    setEditingApp(null);
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" />
      <Text style={styles.header}>NotifyRing</Text>
      <Text style={styles.subheader}>Pick apps to watch. Unopened past the threshold rings you.</Text>

      <PermissionBanner state={permissions} />

      <TextInput
        style={styles.search}
        placeholder="Search apps"
        value={query}
        onChangeText={setQuery}
        autoCorrect={false}
      />

      <FlatList
        data={filteredApps}
        keyExtractor={(item) => item.packageName}
        onRefresh={() => {
          loadApps();
          refreshPermissions();
        }}
        refreshing={false}
        renderItem={({ item }) => {
          const w = watched[item.packageName];
          const isWatched = !!w;
          return (
            <View style={styles.row}>
              <Pressable
                style={styles.rowLabel}
                disabled={!isWatched}
                onPress={() => isWatched && setEditingApp(item)}
              >
                <Text style={styles.rowTitle}>{item.label}</Text>
                {isWatched && (
                  <Text style={styles.rowSubtitle}>
                    Rings after {Math.round(w.thresholdSeconds / 60)} min &middot; tap to edit
                  </Text>
                )}
              </Pressable>
              <Switch value={isWatched} onValueChange={(v) => toggleWatch(item, v)} />
            </View>
          );
        }}
      />

      <ThresholdModal
        app={editingApp}
        currentSeconds={editingApp ? watched[editingApp.packageName]?.thresholdSeconds ?? DEFAULT_THRESHOLD_MIN * 60 : 0}
        onClose={() => setEditingApp(null)}
        onSave={saveThreshold}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#fff', paddingHorizontal: 16 },
  header: { fontSize: 26, fontWeight: '700', marginTop: 12 },
  subheader: { fontSize: 13, color: '#666', marginBottom: 12 },
  banner: { backgroundColor: '#FFF4E5', borderRadius: 12, padding: 12, marginBottom: 12 },
  bannerTitle: { fontWeight: '700', fontSize: 14, marginBottom: 2 },
  bannerSubtitle: { fontSize: 12, color: '#555', marginBottom: 8 },
  bannerButton: {
    backgroundColor: '#1A1A2E',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    marginTop: 6,
  },
  bannerButtonText: { color: '#fff', fontSize: 13, fontWeight: '600' },
  search: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#eee',
  },
  rowLabel: { flex: 1, paddingRight: 12 },
  rowTitle: { fontSize: 15, fontWeight: '500' },
  rowSubtitle: { fontSize: 12, color: '#888', marginTop: 2 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', padding: 24 },
  modalCard: { backgroundColor: '#fff', borderRadius: 16, padding: 20 },
  modalTitle: { fontSize: 18, fontWeight: '700' },
  modalSubtitle: { fontSize: 13, color: '#666', marginTop: 4, marginBottom: 12 },
  optionsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  optionChip: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 20,
    backgroundColor: '#f0f0f0',
  },
  optionChipSelected: { backgroundColor: '#1A1A2E' },
  optionChipText: { fontSize: 13, color: '#333' },
  optionChipTextSelected: { color: '#fff', fontWeight: '600' },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', marginTop: 20, gap: 12 },
  modalCancel: { paddingVertical: 10, paddingHorizontal: 16 },
  modalCancelText: { color: '#666', fontWeight: '600' },
  modalSave: { backgroundColor: '#1A1A2E', paddingVertical: 10, paddingHorizontal: 16, borderRadius: 8 },
  modalSaveText: { color: '#fff', fontWeight: '600' },
});
