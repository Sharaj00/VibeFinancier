# VibeFinancier 1.0.0

First stable personal release, package `app.vibefinancier`, version code 11. Upgrade from 0.1.9 using the same signing certificate; existing financial data is preserved.

- Removed Home and Transfer / debt payment from the navigation drawer.
- Added Payments & subscriptions: calendar, one-off/weekly/monthly/annual repeats, overdue items, editable/archivable plans, skip, and explicit confirmation through the full operation editor.
- Added opt-in offline payment reminders with Android notification-permission handling.
- Plans and confirmations share the existing private Sheets sync and complete backup transport.
- Added atomic, restart-safe occurrence confirmation and concurrent-device deduplication/conflict tests.
- Reviewed source and distribution privacy; rewrote README for a new user/build environment.

No automatic banking debit is performed. Notification delivery may be delayed by Android. See [setup](SETUP.md), [payments](PAYMENTS.md), [review](REVIEW-1.0.md), and [verification](VERIFICATION.md).
