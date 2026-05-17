# Per-Medication Time Zones

Each medication has a timezone mode that controls how its schedule
behaves when the device changes timezone.

## Modes

LOCAL is the default and means "take this at 9 PM wherever I am." The
schedule follows the device's current timezone, so flying from New York
to Tokyo shifts a 9 PM reminder to 9 PM Tokyo time. Wall-clock time is
preserved and absolute time changes.

FIXED means "take this at 9 PM in my home timezone." The schedule is
anchored to the timezone active when it was created, so a schedule
created in New York and used in Tokyo still fires at 9 PM Eastern, which
is 10 AM the next day in Tokyo. Absolute time is preserved and
wall-clock time changes.

## How it works

When a new schedule is created, the device's current IANA timezone (for
example `America/New_York`) is stored on the schedule as
`originTimeZone`. Editing the schedule later does not change this value.

When events are generated, the resolver uses `originTimeZone` if the
medication is FIXED and the value is valid, otherwise it uses the
device's current timezone. Resolution is per-schedule, so a medication
with morning and evening schedules uses the same mode for both, but
each schedule carries its own origin from creation time.

`ScheduleProjector` converts the query range into local dates in the
resolved timezone, adds a one-day buffer on each side, generates
occurrences, and trims back to the original range. The buffer handles
events near date boundaries, where the timezone offset can move an
event onto a different calendar date.

## Notifications on timezone change

When the device timezone changes (travel or DST), both platforms
reconcile all scheduled alarms.

| Platform | Detection | Response |
|----------|-----------|----------|
| Android  | `TimezoneChangeReceiver` on `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` | `AlarmReconciler.reconcile()` plus safety-net worker |
| iOS      | `NSNotificationCenter` timezone observer | `IOSScheduleReconciler.reconcile()` with mutex serialization |

After reconciliation, LOCAL medications are rescheduled for the same
wall-clock time in the new timezone, while FIXED medications are
rescheduled for the same absolute moment.

For two medications both scheduled at 9 PM:

|                                        | New York (UTC-5) | London (UTC+0)      |
|----------------------------------------|------------------|---------------------|
| Vitamin D (LOCAL)                      | 9 PM ET          | 9 PM GMT            |
| Birth control (FIXED, origin New York) | 9 PM ET          | 2 AM GMT (next day) |
