// Android features through Capacitor: reminders, the back button, resume events and sharing files.
// Every function is safe to call in a normal browser, where it does nothing.
import { Capacitor } from "@capacitor/core";
import { LocalNotifications } from "@capacitor/local-notifications";
import { App } from "@capacitor/app";
import { Filesystem, Directory, Encoding } from "@capacitor/filesystem";
import { Share } from "@capacitor/share";

export const native = (() => { try { return Capacitor.isNativePlatform(); } catch (e) { return false; } })();
const REMINDER_IDS = Array.from({ length: 40 }, (_, i) => 100 + i);

export async function notificationState() {
  if (!native) return "unsupported";
  try { return (await LocalNotifications.checkPermissions()).display; } catch (e) { return "denied"; }
}
export async function askNotifications() {
  if (!native) return "unsupported";
  try { return (await LocalNotifications.requestPermissions()).display; } catch (e) { return "denied"; }
}

export async function setupNotifications(onAction) {
  if (!native) return;
  try {
    await LocalNotifications.createChannel({ id: "reminders", name: "Sleep reminders", description: "Wind-down, bedtime and morning reminders", importance: 4, visibility: 1 });
  } catch (e) { /* channel exists */ }
  try {
    await LocalNotifications.registerActionTypes({ types: [
      { id: "BEDTIME", actions: [{ id: "sleep", title: "Going to sleep" }] },
      { id: "MORNING", actions: [{ id: "log", title: "Log it" }] }
    ] });
  } catch (e) { /* older Android */ }
  LocalNotifications.addListener("localNotificationActionPerformed", a => {
    onAction(a.actionId || "tap", (a.notification && a.notification.extra) || {});
  });
}

// list: [{ id, kind, type, title, body, at: Date }]
export async function scheduleReminders(list) {
  if (!native) return;
  try { await LocalNotifications.cancel({ notifications: REMINDER_IDS.map(id => ({ id })) }); } catch (e) { /* none pending */ }
  if (!list.length) return;
  if ((await notificationState()) !== "granted") return;
  await LocalNotifications.schedule({ notifications: list.map(n => ({
    id: n.id,
    title: n.title,
    body: n.body,
    schedule: { at: n.at, allowWhileIdle: true },
    channelId: "reminders",
    smallIcon: "ic_stat_moon",
    iconColor: "#4D52CF",
    actionTypeId: n.type || undefined,
    extra: { kind: n.kind },
    // The app doesn't ask for the "Alarms & reminders" permission, so Android may deliver these a few minutes late.
    autoCancel: true
  })) });
}

export function onBack(fn) { if (native) App.addListener("backButton", fn); }
export function onResume(fn) { if (native) App.addListener("appStateChange", s => { if (s.isActive) fn(); }); }
export function exitApp() { if (native) App.exitApp(); }

export async function shareFile(name, text) {
  const w = await Filesystem.writeFile({ path: name, data: text, directory: Directory.Cache, encoding: Encoding.UTF8 });
  await Share.share({ title: "Sleep log", url: w.uri, dialogTitle: "Save or send your sleep log" });
}
