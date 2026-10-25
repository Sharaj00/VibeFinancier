# VibeFinancier 0.1.6

## Categories and account groups

Open **Menu → Categories**. Income categories, expense categories, ordinary accounts and debt accounts are shown in separate sections. Tap a parent row to expand its children. Swipe right to edit; swipe left to archive the whole subtree. Archiving hides entries while retaining their IDs, operations and balances. Restore archived items under **Settings → Accounts & currencies → Archived items**.

Choose **Move** to select another parent, or **No parent** and a new group. Ordinary/debt accounts can be reclassified without changing currency. Children move together. A selected parent determines the group. Cycles and incompatible entity kinds are rejected. An account cannot be converted into an expense category: these represent different financial entities. Historical transaction kinds and postings remain unchanged when metadata is moved; a previous loan does not become income merely because its account changes type.

## Currencies and conversions

The rightmost coin button on Home and account details switches account values between original currencies and current base-currency estimates. The highlighted button indicates base-currency display. Historical income/expense reports always retain their saved rates. An unavailable quote leaves the original currency visible and marks the aggregate valuation as partial.

**Menu → Currencies** lists currencies used by accounts, source/quote time, current values, recent observations, a price chart and deposit markers. **Load 7 days** downloads recent history. The app also records observations when automatic rates refresh. A WorkManager job refreshes quotes while online, at startup when due and periodically about every six hours; Android may defer background work. Manual quotes can be locked against automatic replacement.

Sources:

- Fiat: [Bank of Russia XML](https://www.cbr.ru/development/sxml/), with [cbr-xml-daily.ru HTTPS mirror](https://www.cbr-xml-daily.ru/) if the original endpoint is unavailable. The actual source appears beside each quote. TLS certificate verification remains enabled.
- Crypto: [CoinGecko](https://docs.coingecko.com/demo/reference/simple-price), combined with the fiat reference for current base conversion. USDT is priced from the provider; it is never assumed to equal USD.
- Intraday crypto history: [CoinGecko time-range observations](https://docs.coingecko.com/demo/reference/coins-id-market-chart-range). Only a verified observation at or before the operation, no more than two hours old, is accepted. The free service generally limits history to the last 365 days and can impose rate limits. Missing timezones, unsupported pairs, unavailable history and provider failures are reported, not filled with guesses.

An operation in a foreign currency offers **Automatic reference rate**. Turn it off for a manual execution/reference rate. The source and quote time are saved. Past crypto dates use a prior intraday observation; fiat uses the daily official reference. Offline use retains saved quotes with their original timestamps. A missing quote requires an explicit manual value.

The operation form accepts an optional fee in the source currency. Expenses deduct principal plus fee; income records gross principal, deducts the fee from the account and reports the fee separately as expense. Transfers record both actual amounts. Enter **Received amount** to derive the execution cross-rate, or choose **Calculate received amount from quotes**. The cross-rate and base-currency valuation are separate fields. Fees do not inflate transfer principal or create income.

### Deposit gain/loss

The currency page shows a FIFO unrealized benchmark for remaining holdings across non-debt accounts. Internal same-currency transfers do not create new acquisition cost; transfer fees reduce holdings. Cross-currency purchase cost includes the source fee. Unknown acquisition rates and negative positions make the result partial. Only the remaining quantity with known cost contributes to gain/loss. This is not a realized trading-profit or tax report.

## Existing crypto operations

Open **Currencies → Review historical crypto rates**. The preview lists old/new rates, observation timestamps, granularity and skipped records. Nothing is rewritten while preparing the preview.

**Apply verified quotes** stores a private full backup before committing revisions, rechecks that the operations still match the preview, and updates only the historical base valuation and fee valuation. Original amounts, currencies, postings, received amounts and execution cross-rates stay intact. Original rate data is retained in `rate_before_repair`, and earlier event revisions remain in the journal. Repeating an identical repair adds no new revisions.

**Settings → Data & recovery → Historical rate repair report** shows the result. **Export backup before rate repair** exports the saved pre-repair backup. Restoring that backup into an existing journal merges events; it does not erase later revisions. It can restore the original snapshot into a clean installation. Original individual rates are also visible in **Show all fields** and can be entered manually when reviewing an operation.

Normal release tests do not rewrite a personal ledger. Historical repairs are applied only as an explicitly authorized maintenance action or through the reviewed action in the app. Version 0.1.7 batches historical requests by currency pair and time range, preserving a separate prior-observation check for each operation.

## Settings and language

Settings are grouped into Appearance & language, Google table & synchronization, Accounts & currencies, Data & recovery, and About. Choose **Settings → Appearance & language → Language → Русский / English**. Language selection never rewrites names, comments, JSON keys or stored amounts. The preference survives restart and applies to forms, dialogs and widget choices. Google table setup is under Settings; its separate drawer entry was removed.

## Responsiveness

Account timelines and balance indexes are prepared on a background executor when the validated ledger revision changes. Account details initially inflate 30 operation rows, with **Load more operations** for additional pages. Returning to Home reuses the complete existing view when its revision and filters are unchanged. The first full load still validates the journal; it is performed off the UI thread.

See [verification](VERIFICATION.md) for automated, physical-device and live-provider evidence. Install the APK over the existing app; no uninstall or data reset is needed.
