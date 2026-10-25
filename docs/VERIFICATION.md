# Verification: VibeFinancier 1.0.0

Validation date: 2026-10-25. Physical device: Samsung Galaxy A32 (SM-A325F), Android 13. Package app.vibefinancier, version code 11, same signing certificate as previous personal builds.

## Automated checks

- 51 JVM tests: exact money/rounding, ledger revisions/replay, transfers/debt/fees, reports, category hierarchy, rate repair, history selection, and recurrence/reminder eligibility.
- Python importer: 3 tests, including malformed/duplicate rows and unresolved references.
- Android full suite: payment persistence, atomic confirmation, repeated confirmation/restart, cross-device conflict/skip races, offline queue, backup round-trip, setup transport mocks, native history/swipes/drag/widgets/themes, and calendar/form rendering. Final runner: OK (46 tests), 87.404 seconds: 43 executed successfully, 3 opt-in tests skipped.
- Lint: 0 errors, 50 warnings (existing resource/localization/accessibility/dependency conventions). Passing lint is not visual or live-cloud proof.

## Native interaction evidence

The calendar and payment editor were rendered on the physical device with synthetic accounts/plans in separate databases. A 100 RUB template opened a prefilled editor and saved one transaction plus one completion receipt. Editing the confirmed operation preserves its plan/date links. Drawer checks verify the new payments entry and absence of Home and Transfer / debt payment. Calendar/form screenshots were visually checked after animations finished.

Existing native checks cover 200 ms drag activation versus quick horizontal scrolling, history filtering, recoverable swipes, SVG rendering, widget drafts/calculation/commit, and fitted Home layout. Test-only database writes do not create personal operations.

## Review and privacy

See [review](REVIEW-1.0.md) for findings and fixes. Source distribution is allowlisted. Personal exports, financial databases/backups, workbook links, diagnostic screenshots/logs, and signing keys are excluded.

A prior authorized live Google transport check succeeded during setup. The 1.0 regression run uses isolated transport tests; it does not claim a new live upload of personal data. Live setup, provider requests, and personal-history maintenance are opt-in and skipped in the normal suite.

## Limits of evidence

Reminder date eligibility and permission/channel guards are checked in code and tests. Real delivery at a future calendar time, after reboot/Doze, or under vendor battery restrictions is not guaranteed or proven by the fast suite. WorkManager timing is inexact. The calendar works without notification permission.

Only the stated physical device was exercised; other Android versions, launchers, screen sizes, and accessibility configurations need their own checks. Concurrent heads remain excluded pending user resolution. Historical provider gaps, journal capacity, personal signing/OAuth requirements, and restore-merge behavior remain documented constraints.

