# Savings & investment schemes: how they work and how Kash tracks them

Researched November 2026. Rates change daily; none are stored in the app.

## The big picture

- Kenya's collective investment schemes (unit trusts, incl. money market funds) held about
  KSh 948.7 billion at 30 June 2026, up 11% on the quarter (CMA Q2 2026 report).
- 98% of investors are local retail investors; investor numbers passed 4 million.
- Money market funds (MMFs) are now about half of that (≈49% in June 2026, down from ≈90% in 2020).
  People are also moving to special funds (e.g. Mansa-X), fixed-income funds and dollar funds.
- Largest MMFs by assets (June 2026): SanlamAllianz (KSh 127.8bn), CIC (KSh 71.0bn). Ziidi had ≈KSh 19.9bn.

## How each kind works → how Kash handles it

| Kind | How money goes in | How it grows | Getting money out | Tax | Kash check-in |
|---|---|---|---|---|---|
| Money market fund | M-Pesa paybill, app, USSD | Interest accrues daily, credited monthly | Usually 1–3 working days; Ziidi instant | 15% withholding on interest (final) | Monthly, on the 5th |
| Ziidi (Safaricom MMF) | Inside M-Pesa (*334# / app), from KSh 100, free | Daily | Instant to M-Pesa, free | 15% | Monthly |
| Special / fixed-income funds (Mansa-X etc.) | App / paybill | Priced daily, can fall | Varies | 15% | Monthly; never assumes growth |
| M-Shwari / KCB M-PESA savings | M-Pesa menu | Daily accrual | Instant | — | Monthly |
| Lock / fixed savings | M-Pesa menu | Daily accrual, paid monthly | At end date (KCB fixed: early exit forfeits interest) | — | At maturity |
| SACCO | Paybill with member no., payroll, standing order | Dividends on shares + interest on deposits, declared at the AGM (usually paid Mar–Jun) | Deposits: usually only on exit with notice; shares: not withdrawable | Dividends 5% WHT (residents) | Quarterly |
| Treasury bills / bonds | DhowCSD app, paid by M-Pesa paybill 200222 (up to KSh 250k) | Bought at a discount, face value at maturity | At maturity (91/182/364 days) or secondary market | T-bill interest exempt for individuals (reported March 2026) | At maturity |
| Hustler Fund savings | 5% of every loan auto-saved (30% short-term / 70% long-term) + voluntary | Interest on balance | Short-term: when no loan is outstanding. Long-term: at 60 (30% every 5 years) | — | Quarterly |
| Shares (NSE, Ziidi Trader) | M-Pesa app / broker | Market price | Ziidi Trader: proceeds same day | — | Monthly |
| Chama | Paybill / send money to treasurer | Contributions only | Group rules | — | User marks a person/paybill as their chama |

## How Kash identifies them

An M-Pesa paybill confirmation shows the **business name**, not the paybill number, so matching is by name
(`SchemeRegistry`):

1. **Strong match** (known manager phrase, e.g. "BRITAM ASSET", "ETICA", "SACCO") → automatically
   *Savings & investments*, linked to the scheme. Money received from a fund is a **withdrawal from savings**,
   not income.
2. **Generic fund words** ("MONEY MARKET", "UNIT TRUST", "MMF") → savings, scheme unknown.
3. **Vague words** ("INVESTMENTS", "CAPITAL", "ASSET", "TRUST") → asked, from KSh 100 up. Under KSh 100 it's
   treated as a shop (a real example: a KSh 80 till called "Chron Investments").
4. Senders with a phone number are people, never funds (surnames like Ndovu).

Watch-out: some funds collect through a **bank's** paybill (e.g. Co-op 400200 + the fund's account number). The
M-Pesa message then names the bank, not the fund. Kash only treats a bank paybill as "your own account" if the
account number is one it knows is yours; otherwise it asks, and the answer is remembered for that account number.

## Fund messages

Redemption, top-up and statement messages from fund managers are read by `FundMessageParser`, only from
senders the user has confirmed. A fund confirmation and the M-Pesa message for the same top-up (same amount
within 3 days) are counted once.

## Value and returns

- Real values come from monthly check-ins ("What's your Britam balance today?") and fund statement messages.
- Monthly return = change in value minus money put in or taken out, annualised.
- Between check-ins, Kash estimates only for daily-accrual schemes, using that holding's own last measured rate,
  and labels the figure "estimate".

## Paybill numbers (reference only, verify before use)

Not used for detection. Collected from public lists; some conflict:
Britam 500005 · Madison 653039 · Old Mutual 600500 · Sanlam 888111 · Etica 4096483 · Cytonn 775093 ·
ICEA Lion 974200 · Jubilee 4103749 · Kuza 8157700 · Lofty-Corban 8752530 · Zimele 501101 ·
CIC 600118 (another 2026 source says 290001) · DhowCSD 200222.

## Sources

- CMA Collective Investment Schemes Quarterly Report, Q2 2026 — cmarcp.or.ke
- Business Daily: "Unit trust investors cross 4m as assets jump to Sh949bn"
- Business Radar: largest money market funds (CMA June 2026 data); Ziidi withdrawal guide
- Safaricom: Ziidi, M-Shwari and M-Shwari Lock Savings pages
- KCB: KCB M-PESA savings; KCB MMF FAQs
- Hustler Fund: mandatory and voluntary savings pages (hustlerfund.go.ke)
- Serrari: T-bills via DhowCSD; MMF tax guide
- Britam: Money Market Fund brochure (*778#, app, SMS confirmations)
- Sacco Review / Paybill KE: SACCO dividends, rebates and paybills
- Kenya MMF Portal (X), Business This Day, The Money Recipe: MMF paybill lists
