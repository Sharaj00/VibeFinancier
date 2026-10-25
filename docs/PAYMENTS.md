# Planned payments, schema 1

`payment_plan` entities use the existing journal and full-backup transport. Fields: `id`, `name`, `payment_kind` (`expense` or `transfer`), `account`, `currency`, decimal-text `amount`, `category` or `to` and `received_amount`, ISO local `start`, `frequency` (`Once`, `Weekly`, `Monthly`, `Yearly`), integer `remind_days` (0..30), `note`, `archived`.

Occurrences derive from the original date plus N calendar intervals. End-of-month clamping never replaces the original anchor. Calendar browsing is read-only. Archiving preserves prior confirmations and operations.

Occurrence ID: `payment.` + SHA-256(plan ID + LF + due date). A `payment_receipt` contains `id`, `entity`, `plan`, and `date`. Its deterministic event is identical across devices, without divergent status or last-writer timestamps. Confirmation atomically commits a transaction with ID `<occurrence>.transaction`, `payment_plan`, `payment_date`, plus the receipt. It retains the user's reviewed actual operation time, amount, rate, received amount, fee, and postings. Skip writes only a receipt. Closed occurrences without an active operation show "Completed / skipped". Deleting an operation does not reopen the occurrence.

Commit rechecks current plan revision/archive state, occurrence date, source account availability, and currency. Local repeat confirmation and restart replay create no second operation. Independent device confirmations create competing heads of the **same transaction entity**, never two expenses. That entity stays excluded until explicit conflict resolution in Settings. Concurrent skip and confirmation merge to one confirmed operation, without a receipt checksum collision.

Changing the start date or repeat interval changes calendar dates and preserves existing operations. Confirmed operations remain editable in ordinary history. Reminders neither pay nor advance a plan.

Reminders use an opt-in 15-minute WorkManager check with no network requirement. Work persists across restarts/reboots; Android can defer it in Doze or while force-stopped. Eligibility starts at 09:00 device-local time on due date minus lead days. The earliest outstanding occurrence per plan is considered; closing it allows later occurrences to become eligible. A local delivery marker is written after successful notification submission. Permission/channel checks precede marking. Denial leaves the calendar usable. Notifications contain generic text and open the calendar, without a silent confirmation action.

Enablement/delivery markers are local preferences, outside financial backups. Re-enabling does not re-notify delivered occurrences; new/restored devices may remind again. Plans, receipts, operations, and links round-trip through full backups. CSV is a transaction-view export rather than a backup of plans.

Official references: [periodic work](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest), [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).
