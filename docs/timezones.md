# Per-Medication Time Zones

When you travel across time zones, medication schedules can behave in two different ways. Some medications should follow your local routine — you take your vitamins at breakfast wherever you are. Others need to maintain a fixed interval — birth control pills taken 24 hours apart regardless of what the clock says. HelloMeds lets you choose the behavior per medication.

## The Two Modes

Each medication has a timezone mode, set during creation:

### LOCAL (default)

"Take this at 9 PM wherever I am."

The schedule follows the device's current timezone. If you fly from New York to Tokyo, a 9 PM medication shifts to 9 PM Tokyo time. The wall-clock time stays the same, but the absolute moment changes.

### FIXED

"Take this at 9 PM in my home timezone."

The schedule is anchored to the timezone that was active when you created the schedule. If you created it in New York and fly to Tokyo, the app still fires the notification at 9 PM Eastern — which is 10 AM the next day in Tokyo. The absolute moment stays the same, but the wall-clock time changes.

## How It Works

### Timezone Capture

When you create a new schedule, the app records the device's current timezone as the schedule's `originTimeZone` (an IANA identifier like `America/New_York` or `Asia/Tokyo`). This value is stored on the schedule entity and does not change if you edit the schedule later.

### Timezone Resolution

When the app needs to generate events for a schedule — to display them in the UI or decide when to fire notifications — it resolves which timezone to use:

1. If the medication is in FIXED mode and the schedule has a valid `originTimeZone`: use that timezone
2. Otherwise: use the device's current system timezone

This resolution happens per-schedule, so a single medication with multiple schedules (e.g., morning and evening doses) will use the same mode for all of them, but each schedule carries its own origin timezone from when it was individually created.

### Event Generation

`ScheduleProjector` converts the queried time range into local dates in the resolved timezone, adds a one-day buffer on each side to handle events near date boundaries, generates all occurrences, and then trims the results back to the exact time range. The buffer handles cases where a timezone offset causes an event to land on a different calendar date than you might expect — for example, a 10 PM event in US Eastern time falls on the next calendar day in UTC.

## Effect on Notifications

When the device's timezone changes — either because the user traveled or because of a daylight saving transition — both platforms detect the change and immediately reconcile all scheduled alarms.

| Platform | Detection | Response |
|----------|-----------|----------|
| **Android** | `TimezoneChangeReceiver` listens for `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` | Triggers `AlarmReconciler.reconcile()` immediately, then schedules a safety-net worker |
| **iOS** | `NSNotificationCenter` observer for timezone change notifications | Triggers `IOSScheduleReconciler.reconcile()` with mutex serialization |

After reconciliation:

- **LOCAL medications**: alarms are rescheduled for the same wall-clock time in the new timezone. A 9 PM alarm in New York becomes a 9 PM alarm in Tokyo.
- **FIXED medications**: alarms are rescheduled for the same absolute moment. A 9 PM Eastern alarm becomes a 10 AM (next day) alarm in Tokyo.

### Travel Example

You have two medications, both scheduled for 9 PM:

| | Before travel (New York, UTC-5) | After travel (London, UTC+0) |
|---|---|---|
| **Vitamin D** (LOCAL) | Notification at 9 PM ET | Notification at 9 PM GMT |
| **Birth control** (FIXED, origin: New York) | Notification at 9 PM ET | Notification at 2 AM GMT (next day) |

The vitamin follows your routine. The birth control maintains its 24-hour interval.

## When to Use Each Mode

**LOCAL** is the right choice when the medication is tied to your daily routine rather than a strict time interval:

- Vitamins and supplements taken with meals
- Medications tied to waking up or going to sleep
- Anything where "same time in your day" matters more than "same absolute interval"

**FIXED** is the right choice when the interval between doses matters more than convenience:

- Oral contraceptives (24-hour interval)
- Antibiotics with strict dosing intervals
- Any medication where the prescribing information specifies a maximum or minimum hours between doses

If you do not travel across time zones, both modes behave identically.

## Fallback Behavior

The app handles missing or invalid timezone data defensively:

| Scenario | Behavior |
|----------|----------|
| Mode is LOCAL (default) | System timezone is used; `originTimeZone` is ignored even if present |
| Mode is FIXED, `originTimeZone` is null | Falls back to system timezone |
| Mode is FIXED, `originTimeZone` is an invalid IANA identifier | Falls back to system timezone |

In all fallback cases, the medication behaves as if it were in LOCAL mode. No error is shown to the user.

## Backup

Both timezone fields are included in backup files:

- `timeZoneMode` — serialized as `"LOCAL"` or `"FIXED"`
- `originTimeZone` — the IANA timezone identifier, preserved exactly as stored

On import, a FIXED-mode medication retains its original timezone regardless of the importing device's location. If you set up a medication in New York and restore the backup in Tokyo, the medication continues to fire at New York times.

If the `timeZoneMode` field is missing or unrecognized in a backup file (e.g., from an older app version), it defaults to LOCAL.

## Files

| File | Purpose |
|------|---------|
| `core/data/src/commonMain/.../model/enums/TimeZoneMode.kt` | `LOCAL` / `FIXED` enum |
| `core/data/src/commonMain/.../database/entities/Medication.kt` | `timeZoneMode` field |
| `core/data/src/commonMain/.../database/entities/Schedule.kt` | `originTimeZone` field |
| `core/data/src/commonMain/.../service/ScheduleTimeZoneResolver.kt` | Resolution logic |
| `core/data/src/commonMain/.../service/ScheduleProjector.kt` | Event generation with timezone-aware date conversion |
| `androidApp/.../receivers/TimezoneChangeReceiver.kt` | Android timezone change detection |
| `shared/src/iosMain/.../notifications/IOSScheduleReconciler.kt` | iOS timezone change detection and reconciliation |
