# Medication Cycles

Some medications follow a repeating pattern of active and break days,
birth control packs being the obvious example (21 active, 7 break). The
cycle masks the underlying schedule rather than replacing it.

## Configuration

Four fields on the medication describe the cycle:

| Field        | Description |
|--------------|-------------|
| Active days  | Consecutive days the medication is taken |
| Break days   | Consecutive days off after the active period |
| Has placebos | Whether break days still generate reminders |
| Anchor date  | Day 1 of the first active period |

Cycle length is active + break, and after the last break day it restarts
from day 1. Presets cover 21/7 (combined OC), 24/4 (extended OC), and
28/0 (continuous daily). Custom values from 1 to 365 are accepted for
both active and break days, and the placebo toggle only appears when
break days are greater than zero.

During setup the user picks the current cycle day, and the app computes
the anchor by counting backward from today, so the user only needs to
know what day of the pack they are on right now.

## Position math

For a given date, the cycle position is the number of days between the
anchor and the target date, taken modulo the cycle length. If the
remainder is less than the active days the date sits in the active
period, otherwise it sits in the break period. Strict modulo wraps
negative values, so dates before the anchor work too.

For a 21/7 cycle anchored on January 1, Jan 1 to 21 is active (days 0 to
20), Jan 22 to 28 is break (days 21 to 27), and Jan 29 is active again
(day 0 of the next cycle).

## Effect on the schedule

`ScheduleProjector` generates events on the fly and runs each generated
event through a cycle mask. Events with a history record pass through
unchanged, since history always wins. Events in the active period also
pass through unchanged. In the break period with placebos on, the event
is marked as a placebo and fires with the downgraded rules below. In the
break period with placebos off, the event is suppressed entirely, with
no entry and no notification.

## Effect on notifications

The active period behaves like a medication with no cycle. The importance
label decides the channel (Android) or interruption level (iOS),
follow-up behavior, and escalation.

Placebo events fire but the system forces them to the lowest tier
regardless of the importance label:

| Platform | Normal | Placebo |
|----------|--------|---------|
| Android  | Channel from label | Forced to Normal, no DND bypass, no alarm sound |
| iOS      | Interruption level from label | Forced to Active, no Focus bypass, default sound |

The downgrade happens at display time and the label itself is not
changed. Follow-ups still fire on placebo days at the configured
intervals, but each one is downgraded too. Escalation thresholds
(`criticalAfterFollowUp`, `alarmAfterFollowUp`) are tracked internally
but blocked by the placebo downgrade, so a placebo notification never
escalates.

## Backup fields

Each medication carries `cycleType` (`"CYCLIC"` or `"NONE"`),
`cycleDaysActive`, `cycleDaysBreak`, `cycleHasPlacebos`, and
`cycleStartDate` (the anchor, ISO-8601 date). The anchor is stored
absolutely rather than relative to export time, so restoring later
resumes the cycle at the correct position.
