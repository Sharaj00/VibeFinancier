# VibeFinancier 1.0

Native Android personal finance with an offline ledger and optional synchronization to a private Google Sheet in your own Drive. All financial calculations use exact decimals on the device. No mandatory third-party server or Apps Script deployment.

Android 8.0+; Java 17; SDK 35. English/Russian interfaces, Light, Dark, and OLED themes.

## Features

- Cash, bank, savings, crypto accounts; fiat currencies, USDT, USDC, TON, BTC, ETH.
- Nested categories, custom colors, bundled Lucide SVG icons, and custom SVG import.
- Income, expenses, transfers, signed debt balances, repayments, optional fees, received amounts, and balance adjustments.
- Saved historical rates separate from current valuations, source/time evidence, and manual rates.
- Daily history, search, filters, swipe-to-edit/delete, and recoverable trash.
- Period reports, category diagrams, currency history, and a FIFO unrealized holding benchmark.
- Transparent widget with account/category selection, calculator expressions, and a save check.
- Payment/subscription calendar: one-off, weekly, monthly, annual plans, overdue items, optional reminders, explicit confirmation.
- Reviewed ProCoins migration, repeat-import deduplication, checked full backups, and transaction CSV export.

## Start

Install over an existing app with the same signature; do not uninstall to upgrade. Use local storage immediately or open **Settings → Google table & synchronization** to create/select a private table.

Self-built APKs need Google Cloud configuration for their package/signing certificate; follow [setup](docs/SETUP.md). No OAuth secret belongs in the app.

Tap a Home circle for related history. Hold an income/account/debt circle for 0.2 seconds, then drag onto a destination to prepare an operation. Quick swipes scroll; expenses are destinations only.

## Payments and subscriptions

Open **Payments & subscriptions → Add planned payment**. Choose an expense or transfer/debt payment, source account, amount, destination/category, date, repeat interval, and reminder lead days. Select a calendar date to review items. Archive stops future reminders and preserves history.

**Confirm payment** opens the normal operation editor. Review actual amount, date/time, rate, comment, and fee before saving. Plans never charge a bank account or alter balances automatically. Skip closes one occurrence without a transaction. Monthly repeats retain their original anchor: January 31 becomes February's last day, then March 31.

Reminders need notification permission on Android 13+. They start at 09:00 local time on the reminder day; Android battery optimization can delay delivery. Notification text omits names/amounts. Enablement is local; plans and confirmations synchronize and round-trip through backups. See [payment rules](docs/PAYMENTS.md).

## ProCoins import

Preserve the original CSV and its SHA-256. Convert a working copy:

~~~powershell
python .\importer\procoins.py 'C:\path\to\ProCoins.csv' --output .\data\working\procoins.vfimport.json
~~~

Copy the resulting JSON to Android, then **Settings → Data & recovery → Import / restore JSON**. Review mapping, skipped records, missing references, and balances in a separate test profile/table before uploading personal history. Repeating a source with unchanged mapping does not duplicate records. Android reads staging JSON, not the original CSV. Missing data is reported without guesses.

## Build and test

Install JDK 17/21 and Android SDK Platform 35 / Build Tools 35.0.0. Set JAVA_HOME and ANDROID_HOME, or create an untracked local.properties with sdk.dir.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug
python -m unittest discover -s importer -p 'test_*.py'
adb install -r app\build\outputs\apk\release\app-release.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w app.vibefinancier.test/androidx.test.runner.AndroidJUnitRunner
~~~

APK: app/build/outputs/apk/release/app-release.apk. Run python scripts/package.py for an allowlisted source ZIP, APK, and SHA-256 manifest. Financial write tests use isolated databases. Live setup/provider/history maintenance tests are opt-in.

The personal release is non-debuggable and uses this machine's existing Android signing key. Back it up for upgrade compatibility. New keys need their own OAuth registration and cannot update installations signed with a different key. Configure your own signing policy before app-store distribution. No keys are included.

## Privacy, formats, limits

SQLite holds the offline ledger and durable upload queue. Private Sheets journals store immutable revisions, exact monetary text, and checksums. Concurrent edits require explicit resolution. See [data/sync rules](docs/DATA_FORMAT.md), [verification/review](docs/VERIFICATION.md), and [setup](docs/SETUP.md).

Rate providers receive currency codes and dates/time intervals, not amounts, account names, comments, or balances. Google receives the ledger after connection and synchronization. Personal exports, financial records, private workbook links, diagnostics, tokens, and signing keys are excluded from this repository.

Limits: 19,999 journal events per device; missing historical quotes need manual rates; holding gain/loss is an unrealized benchmark rather than a tax report; reminders are inexact; loan amortization schedules are not generated. Restore merges history and preserves later revisions.

## Source layout

| Path | Purpose |
| --- | --- |
| app/src/main | Native UI, ledger, SQLite, Google transport, widget, payments |
| app/src/test | JVM financial/calendar tests |
| app/src/androidTest | Isolated device persistence/sync/interaction tests |
| importer | ProCoins converter and tests |
| sheets | Schema/template tools; Apps Script is legacy reference only |
| docs | Setup, formats, review, verification |

Lucide (ISC/Feather MIT) and AndroidSVG (Apache 2.0) notices are bundled in app/src/main/assets/licenses. Dependencies are pinned. No additional project-wide license is granted here; third-party assets retain their own licenses.

