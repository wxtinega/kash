package com.kash.app.data

import com.kash.parser.RawSms
import java.time.Instant

/**
 * Demo inbox in real M-Pesa / Co-op formats (names and numbers made up). Lets the UI be
 * built and tested end-to-end through the real parser before SMS access is wired in.
 */
object DemoMessages {
    private val NOW = Instant.parse("2026-11-11T09:00:00Z")
    private fun m(body: String) = RawSms("MPESA", body, NOW)
    private fun coop(body: String) = RawSms("Co-opBank", body, NOW)

    fun all(): List<RawSms> = listOf(
        m("UKZZ0A1AAA Confirmed.You have received Ksh5,000.00 from PAUL MOKAYA 0720***080 on 1/11/26 at 9:00 AM  New M-PESA balance is Ksh5,120.00."),
        m("UKZZ0B2BBB Confirmed. Ksh1,000.00 sent to BRITAM ASSET MANAGERS for account 77123 on 1/11/26 at 9:30 AM New M-PESA balance is Ksh4,120.00. Transaction cost, Ksh0.00."),
        m("UKZZ0C3CCC Confirmed. Ksh500.00 sent to KPLC PREPAID for account 37123456789 on 2/11/26 at 6:00 PM New M-PESA balance is Ksh3,620.00. Transaction cost, Ksh0.00."),
        m("UKZZ0D4DDD Confirmed. Ksh640.00 paid to NAIVAS KAHAWA. on 3/11/26 at 5:40 PM.New M-PESA balance is Ksh2,980.00. Transaction cost, Ksh0.00."),
        m("UKZZ0E5EEE Confirmed. Ksh250.00 paid to MAMA OLIECH CAFE. on 5/11/26 at 1:10 PM.New M-PESA balance is Ksh2,730.00. Transaction cost, Ksh0.00."),
        m("UKZZ0F6FFF Confirmed. Ksh300.00 sent to KINGS CHOICE WINE LIMITED for account king's on 7/11/26 at 10:12 PM New M-PESA balance is Ksh2,425.00. Transaction cost, Ksh5.00."),
        m("UKZZ0G7GGG Confirmed. Ksh80.00 paid to Chron Investments. on 8/11/26 at 12:09 PM.New M-PESA balance is Ksh2,345.00. Transaction cost, Ksh0.00."),
        m("UKZZ0H8HHH Confirmed. Ksh200.00 sent to JANE  WANJIKU 0722123456 on 9/11/26 at 3:01 PM. New M-PESA balance is Ksh2,138.00. Transaction cost, Ksh7.00."),
        m("UKZZ0I9III confirmed.You bought Ksh100.00 of airtime on 10/11/26 at 8:00 AM.New M-PESA balance is Ksh2,038.00. Transaction cost, Ksh0.00."),
        m("UKZZ0J0JJJ Confirmed. Ksh320.00 sent to Co-operative Bank Money Transfer for account 01103999888001 on 10/11/26 at 11:00 AM New M-PESA balance is Ksh1,718.00. Transaction cost, Ksh0.00."),
        coop("Ref: AC99CB831326. Dear JOHN, your M-PESA to Account Deposit of KES 320 to account 01103999888001 was successful. M-PESA Ref: UKZZ0J0JJJ."),
        coop("Never share your card details with anyone. You have made a purchase of KES 1,150.00 from NETFLIX.COM  at 10-Nov-2026 20:46:42. Your available balance is KES 2,664.04."),
        RawSms("0733111222", "UKZZ9Y8X7W Confirmed.You have received Ksh3,500.00 from MARY ATIENO 0733111222 on 10/11/26 at 1:05 PM New M-PESA balance is Ksh3,620.00.", NOW),
    )
}
