# Storage, financial semantics and synchronization

## Money and exchange rates

Money is stored as signed plain decimal **text**, never a floating-point database or spreadsheet number. Android performs every calculation with `BigDecimal`. Google Sheets is storage and synchronization; no script or spreadsheet formula participates in financial calculations. Newly entered fiat values follow ISO currency precision; USDT/USDC use 6 digits, TON 9, BTC 8 and ETH 18. Imported decimals retain their original precision even if it exceeds the usual currency precision. No discarded source precision is guessed back.

Every normal income/expense stores its original amount/currency, account, local timestamp and timezone, base currency, saved conversion rate, source/as-of time and rounded historical base amount. Rounding is half-even at the base currency precision. An edit preserves the prior historical base. Changing the application's base currency does not invent historical quotes for old operations; statistics explicitly report unavailable conversions.

Current asset valuation uses current/manual quotes and excludes debt accounts and accounts explicitly marked exclude_from_total. Their individual balances and history are unchanged. Historic period statistics use only saved operation rates. Quotes never rewrite history. Automatic fiat quotes come from CBR; supported crypto quotes come from CoinGecko, bridged through USD/CBR when required. Provider failure preserves the last quote and its timestamp. Manual locks are skipped by automatic updates. Exotic currencies outside the supported registry need an explicit future registry extension.

## Entities

- `account`: id, name, currency, account_type, icon, icon_svg (optional embedded SVG), color, archived, exclude_from_total; debt accounts represent counterparties, with positive balance meaning owed to the user and negative meaning the user owes money.
- `category`: id, name, direction (`income` / `expense`), parent, archived.
- `transaction`: id, kind, time, amount, currency, account/category/comment, optional due date, rate evidence and a `postings` array of account/currency/signed-amount legs.
- `rate`: currency/base/rate/source/asof/manual flag.
- `source_record`: uninterpreted source data retained for round-trip backup and later analysis.
- `payment_plan`: expected expense or transfer/debt payment, original recurrence anchor, repeat interval, and reminder lead days; no financial postings.
- `payment_receipt`: deterministic plan/date completion marker, committed atomically with a confirmed operation or alone when skipped. See [payment semantics](PAYMENTS.md).

Transactions are `income`, `expense`, `transfer`, `debt`, `opening` or `adjustment`. Transfers retain separate source and received amounts/currencies. Fees affect the source cash balance and expense total once. Debt principal changes cash and the counterparty account in opposite directions and is excluded from income/expense. Partial/full repayment is another linked counterparty movement; the account's operation history and signed remaining balance are always available. Individual-loan installment schedules are outside the first version.

Opening and adjustment entries are separate from income. Archiving an account/category preserves history. Deletion creates a revision with `deleted=true`; restoring it creates another revision. No event is physically deleted during these operations.

## Google Sheet schema 1

- `Config!A1:B6`: schema marker `vibefinancier/1`, base currency, inclusive from/to dates, account filter and category filter.
- `Rates!A:F`: currency, base, exact rate text, source, as-of text, manual (`true`/`false`). Manual quotes here override matching event quotes and are mirrored into a revision on the next sync.
- `J_<32 lowercase hex device id>!A:F`: event_id, entity_id, comma-separated parent event IDs, entity kind, exact JSON payload, SHA-256 checksum.
- `Summary!A:F`: a plain-text snapshot calculated by the app — exact account balances, current assets, income/expenses/difference, parent/subcategory aggregates, monthly net, missing conversions and unresolved conflicts.
- `Guide`: setup notes.

Each device owns one journal. There are 19,999 available event rows per device in this version; the app stops safely when full. The capacity can be extended in a schema update. Journal reads use bounded ranges. Monetary outputs are strings so Sheets does not round them to its numeric precision.

## Fixed rows and retry safety

SQLite assigns an immutable journal row to each queued event in the same transaction as saving the event. A timeout or process restart retries the same range and same data; it never calls Sheets append. Before writing, the client reads existing rows and rejects a changed occupied slot. An acknowledged event remains in local history but leaves the queue. A response lost after Google committed the request is recovered by matching the same event ID/checksum remotely.

Writes are batched in groups of up to 200, at most 20 groups per worker run. WorkManager retains the queue, waits for connectivity and uses exponential backoff. Google Sheets documents 60 read and 60 write requests per user/project/minute (300/project/minute); the client batches requests and retries failures. Multiple devices can use different journal tabs without competing for append positions.

Immutable IDs/checksums also deduplicate identical events seen in multiple journal tabs. If two devices concurrently import the same source before either sees the other's write, physical copies can exist in different device journals; financial calculations count the event ID once. Normal repeat sends from one device overwrite the same row.

## Conflicts and manual editing

Every revision names its parent revision(s). Two edits to the same parent produce two live heads. The app does not silently choose a last writer: the affected entity is excluded from resolved projections and listed as a conflict. **More → Resolve edit conflicts** creates a resolution event naming all heads. Invalid checksums, missing parents, cycles and an occupied journal slot with unexpected contents stop synchronization.

Supported manual edits: Config date/account/category filters and text manual quotes in Rates. Do not edit, delete, sort or insert journal rows, alter IDs/parents/payloads/checksums, or edit the generated Summary snapshot. Use app revisions for account/category/operation changes. Older templates may contain warning protections; they are not a lock against the owner. As Sheets has no general compare-and-swap row API, edits that violate the journal contract are detected where possible, not made safe by an unsupported claim of transactional multi-writer editing.

## Backup format

`vibefinancier-backup`, version 1, contains `content` (a JSON string) and `sha256` of its exact UTF-8 bytes. Content includes version, complete event history, base currency and source import report. Each event also carries a checksum of `event_id + LF + entity_id + LF + parents + LF + kind + LF + payload`. Tokens, account credentials and Android journal identity/slot allocator are excluded. Restoring merges immutable events, preserves relationships, assigns fresh local upload rows only to new events, and rejects corrupt/cyclic history before commit.

SHA-256 detects accidental changes; it is not encryption or authentication against an attacker who can rewrite the file and recompute the checksum. Keep backups private. CSV is a readable flattened transaction view, not the complete restore format. Formula-like text is escaped in the CSV viewer export.

## ProCoins CSV field mapping (zero-based positions)

Confirmed by static inspection of the installed 1.13.7 APK's export writer and model/table fields:

| Section | Positions in source order |
| --- | --- |
| category (14) | id, type, icon, value, currency, status, name, color, color_false, phone, position, show_in_report, subtype, value_limit |
| subcategory (5) | id, name, status, category, position |
| transaction (18) | id, type, category, fromcategory, subcategory, value, value_currency, currency, description, status, period, iteration, image_uri, data, time, data2, child, id_split |

`fromcategory` supplies an outgoing amount `value`; `category` receives `value_currency`. References are interpreted as account postings only when the referenced exported category is an actual account/debt entity. ProCoins `purse` operations are expenses, `profit` are income; transfer/debt principal are not income/expense. All source rows, including SMS rules and fields without a new-app counterpart, remain in staging and source records. Missing references are reported by source ID, not named or reconstructed by inference.

The source writer selects categories/subcategories with `status=1`, transactions with `status!=0`, flattens line breaks in notes, uses source DOUBLE values and excludes attachments/update metadata. Consequently the export cannot prove completeness of archived categories, deleted transactions, attachment bytes or a historical timezone.

Local derived data: `picker_entities` indexes account/category/rate entity IDs; `events_by_entity` accelerates reading their complete revision chains. Existing databases initialize the index lazily and transactionally. Every later metadata event updates it in the same transaction as the event insertion. Financial event payloads and IDs are unchanged. Picker indexes, resolved-snapshot caches and `category_usage` are disposable and are not the financial backup authority. Cache reads return defensive JSON copies; committed event changes invalidate cached snapshots.

## Version 0.1.6 additions (schema 1, backward compatible)

- Accounts may have a `parent` account ID. Categories retain `parent` category IDs. Moves update subtree metadata with revision parents and preserve entity IDs, currencies and transaction postings/kinds. Cycles and cross-kind parent links are rejected. Swipe removal archives metadata and descendants.
- `quote` entities are immutable reference observations: `id`, `entity`, `currency`, `base`, `rate`, `source`, `asof`. Deterministic IDs/events exclude device retrieval time, so the same observation fetched on different devices has an identical payload and checksum. `rate` entities remain the latest quote/manual lock. Observations and rate revisions use the existing journal, sync and full-backup mechanisms.
- Transactions may include `fee` and `fee_base` for ordinary income/expense as well as transfers. Income is gross; its fee reduces the posting and is a separate expense. Expense postings deduct amount plus fee. Transfer `execution_rate` is received_amount / amount, excluding the fee; it does not replace the source-to-base `rate`.
- Historical repair retains `rate_before_repair` and `rate_granularity`, changes only valuation fields/source/time, and retains the original postings and execution cross-rate. Timezone is required for safe intraday backfill. A stale preview is rejected atomically before writes.
- The language catalog lives in `app/src/main/assets/localization/ru.json`; English source messages are the fallback. UI translation does not modify financial persistence or user text fields.
- Current display preference and language are local device settings. They do not redefine the historical base currency of existing operations.

## Related history presentation (0.1.8)

`HistoryData` groups active operations by their stored local date. Account/debt daily totals use signed postings for the selected account; global daily totals net postings separately per original currency. Category daily totals include descendants and show original source-currency amounts including fees. The category summary uses saved historical base amounts/fee values, and marks missing valuations rather than converting with current quotes. Metadata moves do not retroactively change transaction kinds. Opening and adjustment movements affect debt balance, but not debt counterparty-transfer totals or ordinary income/expense. No schema or stored financial-record migration is required for these screens.
