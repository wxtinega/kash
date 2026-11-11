# Kash

Android app (Android 11+) that reads M-Pesa and bank SMS on the phone, tracks balances,
spending and net worth, and sends a monthly analysis. Version 1 has no server: all data stays
on the device, encrypted.

## Modules

| Module | What it is | Status |
|---|---|---|
| `core/parser` | Pure Kotlin. Reads messages, filters scams and noise, checks the balance chain. | Done, 53 tests passing (20 from real messages) |
| `core/insights` | Categorising, "What was this?" questions, safe-to-spend, savings & investment schemes, investment tracker, fund messages | Done, 38 tests passing |
| `app` | Jetpack Compose UI: theme, components, lock screen, Home, Activity (live search), Savings, Settings; background sync. Reads the phone's SMS inbox (last 6 months) after consent and permission; refreshes on new SMS, on resume and in the background | Built and published by GitHub Actions |

## How the message filter works

1. **Sanitise.** Every SMS is hostile input: length-capped, control characters stripped,
   look-alike characters folded (NFKC). Names are cleaned before they're shown anywhere.
2. **Sender check.** Only registered sender IDs (`MPESA`, bank IDs) are trusted. Messages from
   ordinary 07xx/01xx numbers are never recorded as money, however real they look.
3. **Format match.** Each M-Pesa format is a `RuleSpec` (type + pattern). Unknown formats from
   a trusted sender go to the review queue instead of being guessed. Failed transactions and
   promos are ignored.
4. **Duplicates.** Dropped by transaction code.
5. **Balance chain.** `previous balance ± amount − fee = new balance`. Breaks mean a missing
   or fake message and are shown to the user. Fuliza credit is accounted for.

Untrusted messages are scored by `ScamDetector`; a score ≥ 50 becomes a user-facing warning.

## Banks

- **Co-op Bank**: specific rules built from real messages: M-Pesa ↔ account transfers, card purchases
  (including foreign currency), paybill requests (marked pending), and payments into the account.
- Other banks: cautious generic reader until we have real samples.

Bank messages that quote an "M-PESA Ref" carry it as `linkedCode`, so the app can recognise a
transfer between the user's own accounts and not count it as spending or income. The user's own
account numbers are learned from deposit messages; unknown accounts are asked about once.
After a history import, review items should be re-run once, since later messages may have taught
the app an account number.

## App notes

- Name: **Kash**. Minimum Android version: 11 (API 30).
- Launch screen: small static text "made by T.I". No animation.

## Money

`Money` stores whole cents in a `Long`. No floating point anywhere, and arithmetic is overflow-checked.

## Categorising

Each payment gets a stable identity (`MerchantKey`): people by phone number, paybills by business +
account, tills and card merchants by name. Evidence, strongest first: transfer between own accounts →
the user's earlier answer → transaction type → built-in directory of Kenyan businesses → name keywords
→ ask. Asking = a notification with three buttons (two likely answers + "Something else"); answering
dismisses it and applies to every future payment to the same place. At most 3 a day, none at night,
none under KSh 50 (those wait in the app's list).

## Savings & investments

`SchemeRegistry` covers ~30 money market funds, Mansa-X, M-Shwari / lock savings, KCB M-PESA, SACCOs,
T-bills (DhowCSD), Hustler Fund savings and shares. `InvestmentTracker` measures each holding's real monthly
return from check-ins and fund messages. Full research and sources: `docs/investments.md`.

## Design

Accent #F86939, restrained glass, Lucide icons, separate light/dark palettes. Every UX requirement and where it lives: `docs/design.md`.

## Running the tests

Open the project in Android Studio (or use the command line):

```
./gradlew test
```

## GitHub

`.github/workflows/build.yml` runs all tests on every push and, once the `app` module exists,
builds a debug APK and attaches it to the run (Actions → latest run → Artifacts → kash-debug-apk).

## Before release

- Replace the sample messages in `Samples.kt` with real, anonymised messages and add tests for each.
- Confirm each bank's sender ID in `SenderPolicy.DEFAULT_TRUSTED`, and add bank-specific rules.
