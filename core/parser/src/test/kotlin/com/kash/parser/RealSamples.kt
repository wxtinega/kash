package com.kash.parser

/**
 * Real messages (Oct–Nov 2026), anonymised: names, phone and account numbers replaced,
 * everything else (spacing, line breaks, masks, links, amounts, codes) kept exactly.
 */
object RealSamples {
    const val SENT =
        "UKADR23ZKR Confirmed. Ksh100.00 sent to ABEL  otieno 0727000111 on 10/11/26 at 9:12 PM. New M-PESA balance is Ksh416.74. " +
            "Transaction cost, Ksh0.00.  Amount you can transact within the day is 499,860.00. See all your balances now https://saf.cx/iqIzU"

    const val RECEIVED_MASKED =
        "UKARQ0TXOQ Confirmed.You have received Ksh500.00 from PETER  MWANGI 0720***080 on 10/11/26 at 12:33 PM  " +
            "New M-PESA balance is Ksh556.74. Invest & earn daily interest with ZIIDI on https://saf.cx/cF6ir"

    const val SENT_SMALL =
        "UK8DR1SRAQ Confirmed. Ksh20.00 sent to DIANA  WAFULA 0799000222 on 8/11/26 at 2:44 PM. New M-PESA balance is Ksh56.74. " +
            "Transaction cost, Ksh0.00.  Amount you can transact within the day is 499,880.00. See all your balances now https://saf.cx/iqIzU"

    const val TILL =
        "UK7DR1NBQN Confirmed. Ksh80.00 paid to Chron Investments. on 7/11/26 at 12:09 PM.New M-PESA balance is Ksh558.74. " +
            "Transaction cost, Ksh0.00. Amount you can transact within the day is 499,920.00. See all your balances now https://saf.cx/kWQpy"

    const val PAYBILL =
        "UJVDR0TTGL Confirmed. Ksh300.00 sent to KINGS CHOICE WINE LIMITED for account king's on 31/10/26 at 10:12 PM " +
            "New M-PESA balance is Ksh662.34. Transaction cost, Ksh5.00.Amount you can transact within the day is 499,120.00. " +
            "See all your balances now https://saf.cx/kWQpy"

    /** Code on its own line; balance with no currency. */
    const val FULIZA_REPAY =
        "UJUDR0N233\n Confirmed. Ksh 300.66 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. " +
            "Available Fuliza M-PESA limit is Ksh 300.00. Your M-PESA balance is 699.34."

    const val FULIZA_LOAN =
        "UJRDR09NOC Confirmed. Fuliza M-PESA amount is Ksh 20.00. Access Fee charged Ksh 0.20. " +
            "Total Fuliza M-PESA outstanding amount is Ksh300.23 due on 25/11/26. To check daily charges, Dial *334#OK Select Query Charges"

    const val FAILED =
        "Failed. Insufficient funds in your M-PESA account as well as Fuliza M-PESA to pay KSH80.00. " +
            "Your M-PESA balance KSH0.00. See all your balances now https://saf.cx/lPKcC."

    const val RECEIVED_2 =
        "UJO84B1T9N Confirmed.You have received Ksh110.00 from HENRY  KIPRONO 0720***057 on 24/10/26 at 12:02 PM  " +
            "New M-PESA balance is Ksh110.00. Invest & earn daily interest with ZIIDI on https://saf.cx/cF6ir"

    // ---- Co-op Bank ----
    const val COOP_TO_MPESA_OTHER =
        "Please NEVER share your PIN, PASSWORD, any codes or CARD details with ANYONE! not even people who may claim to be bank staff. " +
            "Ref:AC54CC26C326: Dear JOHN, MPESA transfer of KES 300 to LINDA ACHIENG-254722000333 at 07-11-2026 04:38 PM " +
            "was successful.MPESA Ref:UK75E1HSWW."

    const val COOP_FROM_MPESA =
        "Ref: AC54CB831326. Dear JOHN, your M-PESA to Account Deposit of KES 320 to account 01103999888001 was successful. " +
            "M-PESA Ref: UK7DR1OIW6."

    const val COOP_TO_OWN_MPESA =
        "Please NEVER share your PIN, PASSWORD, any codes or CARD details with ANYONE! not even people who may claim to be bank staff. " +
            "Ref:AC4E73E9AE26: Dear JOHN, MPESA transfer of KES 50 to your mobile -254716000444 at 24-10-2026 04:34 PM " +
            "was successful.MPESA Ref:UJODRBT8Q9."

    const val COOP_CARD_USD =
        "Never share your card details with anyone. You have made a purchase of USD 23.20 from ANTHROPIC* CLAUDE SUB>+14152360599  " +
            "at 20-Oct-2026 12:46:42. Your available balance is KES 344.04. For queries call +254703027000"

    const val COOP_PAYBILL_PENDING =
        "Please NEVER share your PIN, PASSWORD, any codes or CARD details with ANYONE! not even people who may claim to be bank staff. " +
            "Dear JOHN your request to pay KES  1600 to Paybill Number 247247 and A/c 0350000111222 on 25-09-2026 04:14 PM " +
            "has been received and is being processed. REF NO:AC41F88BE826 "

    const val COOP_PAID_IN_BY_OTHER =
        "Dear PAUL JAMES MOKAYA, you have sent Ksh. 4000.0 to JOHN KIMANI MOKAYA 01103****001 \n" +
            " on 09/02/2026 at 16:31:49. MPESA Ref. UI2DR5F56T. Pay bills on MCoopCash App or Dial *667#."
}
