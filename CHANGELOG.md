# Changelog

## 1.8.7

- New, in beta: "Real silence" in Settings. ReNotify plays the sound and
  vibration of notifications itself, so rules that hide or silence a
  notification take effect before anything rings. Calls are never affected,
  and if ReNotify stops running, Android plays the sounds again.
- The Push up switch on a rule's gear now works for silence rules: on, the
  notification still shows a banner without sound; off, it lands quietly in
  the notification bar.

## 1.8.6

First open source release, with a signed APK on the Releases page.

The app as it is on Google Play, with every feature free and without the
parts that only the Play Store edition needs: no purchases, no trial, no
banner for our other apps, no RevenueCat. Statistics are off unless the build
sets a server, and even then only after the user agreed.

Recent changes that are part of this release:

- Usage statistics only after consent, crash reports included.
- Rules can silence a notification instead of hiding it.
- Auto-delete the history after 1, 3, 7, 30 or 90 days.
- Back goes up one level instead of leaving the app; the app opens on the
  list of all apps; a notification shows when its reminder is due.
- German texts say "Benachrichtigungen" throughout.
