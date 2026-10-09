# ReNotify

A notification history for Android. ReNotify keeps every notification you
receive, including the ones you swiped away, and can put any of them back into
your notification bar with its original time.

This is the open source edition. Every feature is free, there are no purchases,
no banners and no advertising, and it sends nothing anywhere unless whoever
built it chose a statistics server (see [Statistics](#statistics)).

## What it does

- **History.** App, title, full text and time of every incoming notification,
  grouped by app and searchable. Text can be selected, copied and shared.
- **Resend.** Push any saved notification back into the notification bar,
  one at a time, several at once, or all of them, with the original timestamp
  and the original app's icon.
- **Remind me later.** Turn a notification into a reminder: in an hour, this
  evening, tomorrow morning, or at a time you pick.
- **Rules.** Hide or silence notifications by app, keyword and time of day.
  Whatever a rule hides still lands in the history.
- **Status bar filter.** Let only the apps you choose stay in the status bar.
- **Delivery per notification.** Banner, sound, vibration and flashlight,
  decided per notification or per app.
- **Your own notifications** with any title, text, date and time.
- **Widgets** for the home screen: a list, a single row, or a counter for today.
- **Trends**: which apps interrupt you most, and when.
- **Auto-delete** after 1, 3, 7, 30 or 90 days, and CSV export.
- English, German, Spanish, French, Italian and Portuguese, with dark mode.

## Limits worth knowing

- Recording starts when the app gets notification access. No app can read
  notifications from before that.
- Only text is stored, not images or attachments.
- A resent notification opens the exact chat or page it pointed to for about
  six hours and until the phone restarts. Android discards that link
  (the PendingIntent) after that; the notification then opens the app.
- Android starts a notification's sound before any app is told about it. A
  silence rule cuts the sound off and removes the banner, but a short tone can
  still get through.
- Calls, alarms, calendar reminders, navigation, media players and running
  services are never hidden or silenced, by any rule or filter.
- Whether every notification is captured also depends on the phone's battery
  management. The app shows how to exempt it.

## Permissions

| Permission | Why |
| --- | --- |
| Notification access | Reading and saving notifications. Granted in system settings, revocable at any time. |
| Post notifications | Putting saved notifications back and showing reminders. |
| Exact alarms | Reminders at the minute you picked. |
| Receive boot completed | Re-arming reminders after a restart. |
| Vibrate | Vibration for resent notifications, if you want it. |
| Ignore battery optimizations (request) | Asking to be exempted, so the history has no gaps. |
| Internet | Only for the statistics described below. A build without a statistics server never opens a connection. |

Notification content stays in a local database on the device. Uninstalling the
app deletes it.

## Statistics

Statistics are off unless the build has a server for them. This repository
ships without one: the `TELEMETRY_URL` placeholder is empty, the app never asks
and never sends.

If you build the app for others and want to see which versions are in use,
put your own server into `secrets.properties`:

```properties
TELEMETRY_URL=https://stats.example.com
```

The app then asks each user once and sends nothing until they agree. With
consent it sends, about once a day, `POST /api/ping` with this JSON:

```json
{
  "device": "random UUID created by the app",
  "version": "1.8.5",
  "versionCode": 19,
  "sdk": 36,
  "locale": "de-DE",
  "notifTotal": 1234
}
```

and after a crash `POST /api/error` with `device`, `version`, `at` (time in
milliseconds) and `stack` (the stack trace, at most 4000 characters). Never
notification content, titles or app names. Withdrawing consent deletes the
random ID on the device.

## The Play Store edition

ReNotify is also on Google Play. That edition is built from the same code by
Fluxera LLC and differs in what it adds around the app:

- Pro is a one-time purchase through Google Play Billing; the free version
  keeps the last 7 days of history and has a 7 day Pro trial.
- The free version shows a small banner for our own apps. No ad network.
- The RevenueCat SDK runs in observer mode for revenue statistics. It contacts
  RevenueCat on every app start with a random identifier; it receives purchase
  confirmations, never notification data, names or email addresses.
- Statistics go to re-notify.com, only after the user agreed, as described
  above.

None of that is in this repository.

## Building

You need JDK 17 or newer and the Android SDK (compile SDK 36).

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. All keys in
`secrets.properties` and `keystore.properties` are optional; see
`secrets.properties.template`.

## Contributing

Bug reports and pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).
Security issues please by email, see [SECURITY.md](SECURITY.md).

ReNotify is developed with the help of AI coding tools. Every change is
reviewed and tested before it ships.

## License

The code is licensed under the GNU General Public License v3.0, see
[LICENSE](LICENSE). Copyright Fluxera LLC.

The name ReNotify and the app icon are not covered by that license. If you
publish a modified version, give it a different name, icon and application id.
