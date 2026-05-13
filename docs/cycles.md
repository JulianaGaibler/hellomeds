# Medication Cycles

Some medications follow a repeating pattern of active and break days — birth control packs are the most common example, with 21 days of active pills followed by 7 days off (or placebo pills). HelloMeds models this as a cycle that masks the underlying schedule, suppressing or modifying notifications during break periods so the user only receives reminders that match the current phase.

## Cycle Configuration

A cycle is defined by four values on the medication:

| Field | Description |
|-------|-------------|
| **Active days** | Number of consecutive days the medication is taken (e.g., 21) |
| **Break days** | Number of consecutive days off after the active period (e.g., 7) |
| **Has placebos** | Whether the break period includes placebo pills that should still generate reminders |
| **Anchor date** | The date the cycle count starts from — day 1 of the first active period |

The total cycle length is the sum of active and break days. After the last break day, the cycle restarts from day 1.

### Presets

The medication creation wizard offers three presets:

| Preset | Active | Break | Typical use |
|--------|--------|-------|-------------|
| 21/7 | 21 | 7 | Standard combined oral contraceptive |
| 24/4 | 24 | 4 | Extended-cycle oral contraceptive |
| 28/0 | 28 | 0 | Continuous daily (no break) |

Custom values between 1 and 365 are accepted for both active and break days. The placebo toggle only appears when break days are greater than zero.

### Setting the Current Position

During setup, you choose which day of the cycle the medication is currently on. The app calculates the anchor date by counting backward from today. This means you do not need to remember when the first cycle started — you only need to know what day of the pack you are on right now.

## How Cycle Position Is Calculated

For any given date, the app determines where it falls in the cycle using modulo arithmetic:

1. Count the number of days between the anchor date and the target date
2. Take the remainder when dividing by the total cycle length
3. If the remainder is less than the number of active days, the date is in the active period; otherwise, it is in the break period

This works for dates before the anchor as well. The calculation uses a strict modulo that wraps negative values correctly, so cycles extend infinitely in both directions from the anchor.

**Example:** A 21/7 cycle anchored on January 1st:
- January 1–21: active period (days 0–20)
- January 22–28: break period (days 21–27)
- January 29: active again (day 0 of the next cycle)
- December 25 (before the anchor): the cycle math wraps backward and places this date at the correct position

## Effect on the Schedule

HelloMeds never stores future events in the database. Instead, `ScheduleProjector` generates events on the fly for any queried time range. For cyclic medications, each generated event passes through a cycle mask before it reaches the UI or the notification system.

The mask applies the following rules, in order:

1. **Event has a history record** (the user already acted on it): pass through unchanged, regardless of cycle position. History always takes priority — if a user logged a dose on a break day, that record is preserved.
2. **Active period**: pass through unchanged. The event appears normally.
3. **Break period, placebos enabled**: the event is marked as a placebo. It still appears in the schedule and generates notifications, but the app treats it differently (see below).
4. **Break period, placebos disabled**: the event is suppressed entirely. No entry appears in the schedule and no notification is generated.

## Effect on Notifications

This is where cycles have the most impact. The cycle's effect on notifications depends on whether the event falls in the active or break period, and whether placebos are enabled.

### Active Period

Events during the active period behave exactly as if no cycle were configured. The medication's importance label determines the notification channel (Android) or interruption level (iOS), follow-up behavior, and escalation rules.

### Break Period — Placebos Enabled

Placebo events generate notifications, but the system forces them to the lowest notification tier regardless of the medication's importance label:

| Platform | Normal behavior | Placebo behavior |
|----------|----------------|------------------|
| **Android** | Channel set by importance label (Normal, Critical, or Alarm) | Forced to Normal channel — no Do Not Disturb bypass, no alarm sound, standard priority |
| **iOS** | Interruption level set by importance label (Time Sensitive, Critical, or Alarm) | Forced to Active level — no Focus Mode bypass, default notification sound |

A medication with a Critical or Alarm importance label still receives standard-priority notifications on placebo days. The downgrade happens at notification display time, so the label configuration itself is not modified.

### Break Period — No Placebos

No event is generated. No notification fires. The medication is effectively silent during the break.

### Follow-ups During Placebo Periods

Follow-up reminders still fire at their configured intervals during placebo days. However, every follow-up notification is also forced to normal/active priority. Escalation thresholds (`criticalAfterFollowUp`, `alarmAfterFollowUp`) are tracked internally but blocked by the placebo downgrade at display time. A placebo notification never escalates to Critical or Alarm, even after multiple unanswered follow-ups.

In concrete terms: if a medication is configured with 3 follow-ups that escalate to Critical after the 2nd follow-up, and the current day is a placebo day, all 3 follow-ups will fire at their scheduled times — but all of them will arrive as standard-priority notifications.

### Summary Table

| Cycle state | Event generated? | Notification fires? | Importance label respected? | Follow-ups fire? | Escalation possible? |
|-------------|:---:|:---:|:---:|:---:|:---:|
| Active period | Yes | Yes | Yes | Yes | Yes |
| Break — placebos | Yes (marked as placebo) | Yes (downgraded to normal) | No — forced to normal | Yes (also downgraded) | No — blocked |
| Break — no placebos | No | No | N/A | N/A | N/A |

## Backup

Cycle configuration is included in backup files. The following fields are serialized per medication:

- `cycleType` (`"CYCLIC"` or `"NONE"`)
- `cycleDaysActive`
- `cycleDaysBreak`
- `cycleHasPlacebos`
- `cycleStartDate` (the anchor, as an ISO 8601 date string)

On import, the cycle resumes from the stored anchor date. If the backup is restored days or weeks later, the cycle position will have advanced accordingly — the anchor date is absolute, not relative to the export date.

## Files

| File | Purpose |
|------|---------|
| `core/data/src/commonMain/.../database/entities/Medication.kt` | Cycle fields on the entity |
| `core/data/src/commonMain/.../model/CycleDayInfo.kt` | Cycle position calculation |
| `core/data/src/commonMain/.../model/enums/CycleType.kt` | `NONE` / `CYCLIC` enum |
| `core/data/src/commonMain/.../service/ScheduleProjector.kt` | Cycle mask applied during event projection |
| `shared/src/commonMain/.../ui/features/medication/steps/MedicationCycleStep.kt` | Cycle configuration UI |
| `shared/src/commonMain/.../ui/components/medication/CycleProgressIndicator.kt` | Visual cycle progress bar |
