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

const NAVY = '#000313';
const CARD = '#10112A';
const ACCENT = '#3B82F6';
const BORDER = '#232447';
const TEXT_MUTED = '#8C8FB5';

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

  return (
    <View style={styles.banner}>
      <Text style={styles.bannerTitle}>Setup</Text>
      <Text style={styles.bannerSubtitle}>
        Without these, watched apps won't reliably ring you.
      </Text>
      {missing.map((item) => (
        <Pressable key={item.label} style={styles.bannerButton} onPress={item.onPress}>
          <Text style={styles.bannerButtonText}>Grant: {item.label}</Text>
        </Pressable>
      ))}
      {missing.length === 0 && (
        <Text style={styles.bannerAllSet}>All core permissions granted</Text>
      )}

      {/* OEM battery managers (Vivo/Oppo/Xiaomi) kill background apps unless
          separately whitelisted - no API exists to detect this state, so this
          stays visible as a manual action rather than a checkable item. */}
      <View style={styles.bannerDivider} />
      <Text style={styles.bannerSubtitle}>
        On Vivo/iQOO, also allow autostart, and long-press NotifyRing in your
        recent apps list and tap the lock icon - otherwise the ringer can
        still get killed in the background.
      </Text>
      <Pressable
        style={[styles.bannerButton, styles.bannerButtonSecondary]}
        onPress={() => NotifyListener.openAutoStartSettings()}
      >
        <Text style={styles.bannerButtonText}>Open autostart settings</Text>
      </Pressable>
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
      <StatusBar barStyle="light-content" backgroundColor={NAVY} />
      <Text style={styles.header}>NotifyRing</Text>
      <Text style={styles.subheader}>Pick apps to watch. Unopened past the threshold rings you.</Text>

      <PermissionBanner state={permissions} />

      <TextInput
        style={styles.search}
        placeholder="Search apps"
        placeholderTextColor={TEXT_MUTED}
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
              <Switch
                value={isWatched}
                onValueChange={(v) => toggleWatch(item, v)}
                trackColor={{ false: BORDER, true: ACCENT }}
                thumbColor="#fff"
              />
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
  safeArea: { flex: 1, backgroundColor: NAVY, paddingHorizontal: 16 },
  header: { fontSize: 26, fontWeight: '700', marginTop: 12, color: '#fff' },
  subheader: { fontSize: 13, color: TEXT_MUTED, marginBottom: 12 },
  banner: { backgroundColor: CARD, borderRadius: 12, padding: 12, marginBottom: 12, borderWidth: 1, borderColor: BORDER },
  bannerTitle: { fontWeight: '700', fontSize: 14, marginBottom: 2, color: '#fff' },
  bannerSubtitle: { fontSize: 12, color: TEXT_MUTED, marginBottom: 8 },
  bannerAllSet: { fontSize: 12, color: '#4ADE80', fontWeight: '600' },
  bannerDivider: { height: 1, backgroundColor: BORDER, marginVertical: 10 },
  bannerButton: {
    backgroundColor: ACCENT,
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    marginTop: 6,
  },
  bannerButtonSecondary: { backgroundColor: '#1F2050' },
  bannerButtonText: { color: '#fff', fontSize: 13, fontWeight: '600' },
  search: {
    borderWidth: 1,
    borderColor: BORDER,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
    color: '#fff',
    backgroundColor: CARD,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: BORDER,
  },
  rowLabel: { flex: 1, paddingRight: 12 },
  rowTitle: { fontSize: 15, fontWeight: '500', color: '#fff' },
  rowSubtitle: { fontSize: 12, color: TEXT_MUTED, marginTop: 2 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'center', padding: 24 },
  modalCard: { backgroundColor: CARD, borderRadius: 16, padding: 20, borderWidth: 1, borderColor: BORDER },
  modalTitle: { fontSize: 18, fontWeight: '700', color: '#fff' },
  modalSubtitle: { fontSize: 13, color: TEXT_MUTED, marginTop: 4, marginBottom: 12 },
  optionsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  optionChip: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 20,
    backgroundColor: '#1F2050',
  },
  optionChipSelected: { backgroundColor: ACCENT },
  optionChipText: { fontSize: 13, color: '#D0D2F0' },
  optionChipTextSelected: { color: '#fff', fontWeight: '600' },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', marginTop: 20, gap: 12 },
  modalCancel: { paddingVertical: 10, paddingHorizontal: 16 },
  modalCancelText: { color: TEXT_MUTED, fontWeight: '600' },
  modalSave: { backgroundColor: ACCENT, paddingVertical: 10, paddingHorizontal: 16, borderRadius: 8 },
  modalSaveText: { color: '#fff', fontWeight: '600' },
});
