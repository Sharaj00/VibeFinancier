# Storage templates

VibeFinancier 0.1.2 and later calculates everything on Android using BigDecimal. The app creates the Google Sheets structure and checks exact text storage automatically. No script installation is required.

VibeFinancier-template.xlsx is an empty compatible storage template. VibeFinancier-isolated-test.xlsx contains synthetic data only; do not select it as your main ledger.

Code.gs and appsscript.json remain historical reference files for the earlier Google-side calculation design. They are not called or installed by the current Android app. Older bound scripts may remain in existing tables; the app does not rely on them and replaces Summary with a plain-text snapshot on synchronization.
