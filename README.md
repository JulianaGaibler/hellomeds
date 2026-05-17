# HelloMeds

A medication tracking app for Android and iOS. Learn more at [juliana.me/projects/hellomeds](https://juliana.me/projects/hellomeds).

## Overview

HelloMeds handles scheduling, reminders, dose logging, and stock tracking. It is designed to adapt to how different medications actually need to be managed. All data stays on your device, encrypted at rest. 

## Features

**Scheduling.** Medications can be scheduled daily, on specific days of the week, or at custom intervals. Each medication can have multiple schedules at different times. For medications that follow a cycle pattern, HelloMeds supports configurable active/break periods.

**Reminders and notification types.** Each medication is assigned a notification type that controls its entire notification behavior. Types are user-configurable and determine how many follow-up reminders are sent if a dose goes unconfirmed, and whether those follow-ups escalate to critical alerts or alarm sounds. This lets you set up aggressive escalation chains for essential medications while keeping supplement reminders low-key or silent.

**Stock tracking.** Additionally, you can track your medication stock levels and get low-stock reminders. When you log a dose as taken, the app automatically deducts from your stock based on the dose amount and unit.

**Camera detection.** HelloMeds can optionally identify medications using your device's camera with on-device machine learning.

**Dose logging.** When a scheduled dose comes due, you can mark it as taken, skip it, or let it auto-skip after the reminder window passes. The app keeps a full history of every action for each medication.

**Backups.** Data can be exported manually or backed up automatically on a daily schedule. Backups are encrypted and stored locally.

**Privacy.** All medication data is stored locally in an encrypted database. There are no accounts, no cloud sync, and no data collection.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture, build instructions, and development patterns.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
