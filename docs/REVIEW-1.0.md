# Version 1.0 code review

Reviewed locally before publication, 2026-10-25. This is an implementation review and focused regression check, not an independent security audit.

## Scope

Examined the immutable journal, SQLite transactions/caches, fixed-row cloud transport, backups/import replay, exact monetary calculations, operation editor, reminder lifecycle, calendar recurrence, navigation, widget entry paths, manifest, signing configuration, and distributable allowlist.

## Findings addressed

1. **Repeat payment confirmation:** a receipt and financial operation now commit in one SQLite transaction. Occurrence/operation identities are deterministic; repeated confirmation after a restart is rejected.
2. **Concurrent devices:** distinct confirmation events target one operation entity, entering ordinary explicit conflict resolution rather than double-counting. Receipts are canonical and status-independent, so skip/confirm races do not produce event-ID checksum collisions.
3. **Stale plans:** commit rechecks the current revision, archive state, valid occurrence date, and source/destination accounts and posting currencies. Ordinary operation editing retains plan/date links. A deleted operation does not reopen an occurrence.
4. **Calendar drift:** month/year repeats derive from the original anchor, preserving the 31st and leap-year behavior. Date-range seeking avoids scanning every prior month merely to render a future month.
5. **Notification permission/privacy:** Android 13 permission and channel availability are checked before marking delivery. Reminder text contains no names, balances, or amounts. PendingIntent is immutable, opens the calendar, and cannot directly pay or confirm.
6. **Failed plan save:** editor remains open and its save action is re-enabled after an error.
7. **Release privacy:** removed legacy private workbook links and personal-export counts/dates/checksums from published documentation. Financial files, keys, diagnostics, and local tooling remain outside the allowlisted release source.

## Deliberately retained constraints

- WorkManager reminders are battery-aware and inexact, not alarm-clock delivery guarantees. Device notification preference is not cloud state.
- Concurrent transaction heads are excluded until resolved; no last-writer guess. Offline devices cannot prevent the initial conflict but cannot create two resolved expenses for the same occurrence.
- Calendar plans are expense/transfer/debt templates, not a banking service or generated loan-amortization engine.
- Full backup restore merges revisions; it is not a destructive rollback of later edits.
- Existing personal release signing is preserved for upgrades. No signing key is distributed. Another build/distribution needs its own signing/OAuth setup.
- Existing per-device journal capacity and unsupported historical-rate gaps are documented in README and the data format.

Automated/native-device results and remaining manual limits are recorded in [verification](VERIFICATION.md).
