# Setup and required user actions

## 1. Google Cloud configuration

The app cannot register an OAuth client in your Google account. This step must be completed by the project owner. You do **not** need to send a password, access token, client secret or `google-services.json` to the developer.

1. Open https://console.cloud.google.com/ and create or select your own project, named for example `VibeFinancier`.
2. Under **APIs & Services → Library**, enable **Google Sheets API**, **Google Drive API** and **Google Picker API** in that same project.
3. Open **Google Auth platform**. Configure Branding (app name `VibeFinancier`, your support/developer email), Audience (External for a personal Gmail account), and add your Google account as a test user while the app is in testing.
4. Under **Data Access**, add only `https://www.googleapis.com/auth/drive.file`. This is Google's recommended non-sensitive per-file scope. Do not add full Drive access or publish the spreadsheet.
5. Under **Clients → Create client**, choose **Android**. Enter:
   - Package name: `app.vibefinancier`
   - SHA-1 for the APK built in this workspace: `D2:AE:B2:8A:E2:B9:1B:70:49:64:2B:67:0F:3B:FF:57:69:F3:CD:88`
6. Save the client. Android AuthorizationClient identifies the app using its package and signing certificate. No OAuth secret is embedded in this APK. A different signing key requires a corresponding Android OAuth registration.
7. Open VibeFinancier, **Settings → Google table & synchronization**. Any Google action requests the scope through Google Play services. Select the Google account that owns the table.

Official references: [Android authorization](https://developer.android.com/identity/authorization), [Sheets scopes](https://developers.google.com/workspace/sheets/api/scopes), [native Android Google Picker](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker).

## 2. Automatic table setup (0.1.2 and later)

All calculations run on Android with BigDecimal. Google Sheets stores and synchronizes immutable events, manual quotes and an optional report snapshot. Apps Script installation, setup(), deployment and formula verification are no longer required.

1. On the home screen choose **Create private table**, or **Choose existing table** for a compatible VibeFinancier workbook. These actions are also in **Settings → Google table & synchronization**.
2. Select your Google account and authorize access. Creating a new table automatically initializes Config, Summary, Rates and Guide. Selecting an existing table validates its schema before downloading its history.
3. An automatic temporary-tab check writes the same synthetic rows twice, reads the exact strings back and verifies both a large fiat balance and one billionth TON locally. The temporary tab is removed. This check never uploads the real ledger.
4. The completion dialog offers **Open table** and **Copy link**. The same controls remain available through **Open / copy connected table link**. No link transcription is necessary.
5. Use **Sync now** after reviewing imports. Existing scripts in an older workbook are not needed. The app writes Summary as plain text values and never uses it to calculate the ledger.

An interrupted setup resumes a saved candidate ID instead of deliberately creating another workbook. A network failure before Google returns the creation response can still leave an unreferenced empty workbook in Drive. A 404 from an old connection permits a new table only if local history is empty; existing local history blocks automatic replacement. Previously referenced files are not deleted.

OAuth project registration in section 1 is a one-time developer/distribution requirement. A preconfigured APK asks an end user only for Google consent and a create/select choice. A build signed with a new certificate needs its own OAuth registration.

## 3. Existing compatible tables

Create a private compatible template through the app. No shared workbook or public template URL is required.

Use the app's native Google Picker to grant access to an existing workbook. Knowing a file ID alone does not grant access. Do not select the synthetic test workbook as the main ledger. Tables containing unrelated data are rejected rather than overwritten.

WorkManager retains queued operations across restarts and retries when connected. Google may occasionally require consent again. The cloud is the synchronized history; the local SQLite database holds the offline copy and pending edits.

## 4. Import and reconciliation

1. Keep the original ProCoins CSV and its SHA-256. Work on a copy.
2. Run `importer/procoins.py` as shown in the README. It writes a staging JSON and a detailed audit JSON.
3. Transfer the staging JSON to your phone. In the app, open **Settings → Import / restore JSON**. Review the source date range, accounts, currencies, missing references and errors. Map known accounts/categories only when they represent the same actual entity; currencies cannot be silently changed.
4. Import locally. Repeat the same import with the same mapping to confirm it adds zero events. Do not change mappings on a repeat import; conflicting source identities require review.
5. Review **Last import report** and compare each account against ProCoins. Some ProCoins exports omit referenced accounts or categories. Missing references are retained and reported; their identities and balances are never reconstructed by guessing.
6. After the automatic storage test passes and balances are reviewed, sync to the main workbook. Check operation count, date range and balances again. Retain the original CSV as a separate source artifact.

## 5. Widget and offline behavior

Long-press the Android home screen, choose Widgets, find VibeFinancier, and place its widget. Tap the left icon to choose the source account in a compact rounded popup.

Tap the amount to activate your system numeric keyboard. The expression appears directly in the widget, with no separate amount window or custom keypad. Use the keyboard's arithmetic signs, e.g. `30+50*2/4`. Done/Enter evaluates it to `55` and dismisses the keyboard. The result stays in the widget until you confirm the financial operation. The tested Samsung keyboard includes these signs; key availability depends on the installed keyboard.

Single-tap the right icon to choose a destination immediately. The popup is positioned beside the widget, leaving the widget controls available. First choose a parent expense category, then a subcategory if available. Select chooses the parent itself. Root choices also include accounts/debts; the popup's direction switch selects income categories. The right label opens the picker immediately. Tap the check mark in the small widget header once to save offline, show a floating confirmation and clear the amount. Tap the application name in that header to open VibeFinancier. The right icon only selects a destination; double tapping is no longer used. The route stays selected.

Drafts and saved operations survive restarts. Unfinished expressions cannot be committed. Repeating a commit after a crash does not duplicate the event. Use the full app for transfers with different currencies or fees. Balance visibility remains configurable and private by default.

The main app's account/category editor offers **Load SVG file**. Choose a static, self-contained vector SVG up to 16 KB. Its content is stored with the entity and included in backup/synchronization. Built-in icons come from the bundled Lucide SVG subset. No separate icon download is needed after installation.

## 6. Build on Windows

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-17'
$env:ANDROID_HOME = 'C:\Path\To\Android\Sdk'
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

The wrapper downloads Gradle 8.14.3. Android SDK licenses and the required platforms must be installed through Android Studio or Google's command-line tools. `local.properties`, if present, takes precedence over `ANDROID_HOME`; use an escaped Windows drive separator such as `sdk.dir=C\:/Android/Sdk`.

For device tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Tests use an isolated `isolated-persistence-test.db` and do not clear ProCoins or the user's VibeFinancier ledger.

## Quick gestures and editing

Move a home-screen icon directly onto another icon after holding it still for about 0.2 seconds. Income → account, account → expense category and account → another account/debt open a preselected entry form. A valid target is highlighted. Release the circle, enter an amount (or use Calculator), select the date/time, subcategory and comment, then Save. Cancel creates no transaction.

Tap an account to view its card and day-by-day history. Edit opens the icon/color grid, current balance and archive/exclusion controls. A balance change is an explicit adjustment, excluded from ordinary income/expense. Categories can be edited through Menu → Categories. An accessibility Edit action remains available on each circle.

The widget background is transparent. Existing placements can be resized in the launcher; new placements default to a compact 3 by 1 cells.

## Reports and history gestures

Menu opens the side drawer. Reports shows a period selector, previous/next period arrows, an income/expense toggle, a category donut and rows that open the underlying filtered history. Accounts and Debts are accessed from their Home circles rather than separate drawer pages.

Swipe a transaction right to edit it or left to move it to Trash. Undo restores the last removal; Settings provides Trash for older records. History initially shows 60 rows; use Load more for subsequent rows. Drag directly from the circular icon to create an operation; drag labels or empty areas to scroll.

## Version 0.1.6

For category moves, currencies, historical rate review and language selection, follow [the update guide](UPGRADE-0.1.6.md). No new Google OAuth scope, Apps Script deployment or manual spreadsheet schema upgrade is required.

## Related history and gestures (0.1.8)

Tap a Home account, debt or category to open its linked operations. The summary card's pencil edits that entity. Category history includes subcategories. Select **All history** or the top filter to narrow the period, search text and (for category history) account, without losing the selected category/account. Rows are grouped by day; **Load more operations** extends the visible list.

Start a drag from an income, account or debt icon after a brief 0.2-second hold. Drop an account onto an expense category to prepare an expense, an income onto an ordinary account for income, or an account/debt onto another compatible account for a transfer/debt movement. Expense categories cannot initiate a drag. Financial records are only created after saving the entry form.

## Home behavior (0.1.9)

Swipe horizontally to browse a band. To create an operation by dragging, hold an eligible icon still for about 0.2 seconds, then move it to a compatible target. Taps open linked history. Home fits the available viewport without vertical scrolling; compact windows arrange the four bands in two columns. Import details remain under Settings rather than on Home.
