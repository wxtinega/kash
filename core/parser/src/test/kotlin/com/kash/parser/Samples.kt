package com.kash.parser

/**
 * Sample messages modelled on real M-Pesa wording. Codes, names and numbers are made up.
 * These MUST be extended with real (anonymised) messages before release.
 */
object Samples {
    const val RECEIVED =
        "TKA1B2C3D4 Confirmed.You have received Ksh1,500.00 from JOHN KAMAU 0712345678 on 10/11/26 at 2:15 PM  " +
            "New M-PESA balance is Ksh3,250.50. Separate personal and business funds through Pochi la Biashara on *334#."

    const val SENT =
        "TKB2C3D4E5 Confirmed. Ksh500.00 sent to JANE WANJIKU 0722123456 on 10/11/26 at 3:01 PM. " +
            "New M-PESA balance is Ksh2,743.50. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,500.00."

    const val PAYBILL =
        "TKC3D4E5F6 Confirmed. Ksh1,000.00 sent to KPLC PREPAID for account 37123456789 on 10/11/26 at 4:00 PM " +
            "New M-PESA balance is Ksh1,743.50. Transaction cost, Ksh0.00.Amount you can transact within the day is 498,500.00."

    const val TILL =
        "TKD4E5F6G7 Confirmed. Ksh250.00 paid to MAMA OLIECH CAFE. on 10/11/26 at 6:10 PM.New M-PESA balance is Ksh1,493.50. " +
            "Transaction cost, Ksh0.00. Amount you can transact within the day is 498,250.00."

    const val WITHDRAW =
        "TKE5F6G7H8 Confirmed.on 10/11/26 at 7:00 PMWithdraw Ksh1,000.00 from 123456 - BARAKA AGENCIES KAHAWA " +
            "New M-PESA balance is Ksh464.50. Transaction cost, Ksh29.00. Amount you can transact within the day is 497,250.00."

    const val AIRTIME =
        "TKF6G7H8I9 confirmed.You bought Ksh100.00 of airtime on 10/11/26 at 8:00 PM.New M-PESA balance is Ksh364.50. " +
            "Transaction cost, Ksh0.00. Amount you can transact within the day is 497,150.00."

    const val DEPOSIT =
        "TKG7H8I9J0 Confirmed. On 11/11/26 at 9:00 AM Give Ksh2,000.00 cash to BARAKA AGENCIES KAHAWA " +
            "New M-PESA balance is Ksh2,364.50. You can now access M-PESA via *334#"

    const val MSHWARI_SAVE =
        "TKH8I9J0K1 Confirmed.Ksh500.00 transferred to M-Shwari account on 11/11/26 at 10:00 AM. " +
            "M-PESA balance is Ksh1,864.50 .New M-Shwari saving account balance is Ksh2,500.00. Transaction cost Ksh.0.00"

    const val FULIZA_LOAN =
        "TKI9J0K1L2 Confirmed. Fuliza M-PESA amount is Ksh 700.00. Access Fee charged Ksh 7.00. " +
            "Total Fuliza M-PESA outstanding amount is Ksh 707.00 due on 11/12/26. To check daily charges, Dial *234*0#OK Select Query Charges"

    const val FULIZA_REPAY =
        "TKJ0K1L2M3 Confirmed. Ksh 707.00 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. " +
            "Available Fuliza M-PESA limit is Ksh 1,000.00. M-PESA balance is Ksh 1,293.00."

    const val REVERSAL =
        "TKK1L2M3N4 Confirmed. Transaction TKB2C3D4E5 has been reversed. Your account balance is now Ksh3,250.50."

    const val FAILED =
        "Failed. You do not have enough money in your M-PESA account to pay Ksh5,000.00 to KPLC PREPAID. " +
            "Your M-PESA balance is Ksh364.50."

    const val PROMO =
        "Get 50% extra data bundles when you buy with M-PESA today! Dial *544# to enjoy. T&Cs apply."

    // --- scams (arrive from ordinary numbers) ---
    const val SCAM_FAKE_CONFIRMATION =
        "TKZ9Y8X7W6 Confirmed.You have received Ksh3,500.00 from MARY ATIENO 0733111222 on 10/11/26 at 1:05 PM " +
            "New M-PESA balance is Ksh3,620.00."

    const val SCAM_WRONG_TRANSFER =
        "Habari, nimetuma pesa kimakosa kwa namba yako Ksh3,500. Tafadhali nirudishie, ni pesa ya hospitali. Mungu akubariki"

    const val SCAM_PRIZE =
        "CONGRATULATIONS! You have won Ksh 250,000 in the Safaricom promotion. To claim your prize send your M-PESA PIN " +
            "and call 0711999888 immediately."

    const val FRIEND_CHAT = "Uko wapi? Tukutane town saa nane, nimekutumia 200 ya fare"
}
