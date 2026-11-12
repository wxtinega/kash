package com.kash.app.ui.home

import java.time.LocalDate

/**
 * One line about money for each day, shown on Home when there's nothing new to sort.
 *
 * Only three kinds of entry, so nothing is falsely attributed:
 *  - Swahili proverbs (traditional, no author), with their meaning in English;
 *  - quotes from old public-domain works whose source is known (book, letter, year);
 *  - short practical tips written for Kash.
 * Works offline. Everyone sees the same line on the same day.
 */
data class DailyQuote(val text: String, val meaning: String? = null, val source: String)

object DailyQuotes {
    private const val PROVERB = "Swahili proverb"
    private const val TIP = "Kash tip"

    val all: List<DailyQuote> = listOf(
        DailyQuote("Haba na haba hujaza kibaba.", "Little by little fills the measure.", PROVERB),
        DailyQuote("Beware of little expenses; a small leak will sink a great ship.", source = "Benjamin Franklin, The Way to Wealth (1758)"),
        DailyQuote("Pay yourself first: move your savings the day money arrives, not from whatever is left at month end.", source = TIP),
        DailyQuote("Mali bila daftari hupotea bila habari.", "Wealth without a record book disappears without notice.", PROVERB),
        DailyQuote("Whatever you have, spend less.", source = "Samuel Johnson, letter to James Boswell (1782)"),
        DailyQuote("Fuliza is a loan. Its daily charge keeps running until you pay it back.", source = TIP),
        DailyQuote("Bandu bandu huisha gogo.", "Chip by chip, the whole log is finished.", PROVERB),
        DailyQuote("It is not the man who has too little, but the man who craves more, that is poor.", source = "Seneca, Letters to Lucilius"),
        DailyQuote("Usipoziba ufa utajenga ukuta.", "If you don't fill the crack, you'll end up building a wall.", PROVERB),
        DailyQuote("If you would be wealthy, think of saving as well as of getting.", source = "Benjamin Franklin, The Way to Wealth (1758)"),
        DailyQuote("Never share your M-Pesa PIN. Safaricom and your bank will never ask for it.", source = TIP),
        DailyQuote("Akiba haiozi.", "Savings never rot.", PROVERB),
        DailyQuote("Neither a borrower nor a lender be.", source = "William Shakespeare, Hamlet"),
        DailyQuote("Mchumia juani hulia kivulini.", "Whoever earns in the sun eats in the shade.", PROVERB),
        DailyQuote("A message from an ordinary phone number saying you received money is not money. Check your real balance first.", source = TIP),
        DailyQuote("Creditors have better memories than debtors.", source = "Benjamin Franklin, The Way to Wealth (1758)"),
        DailyQuote("Chovya chovya humaliza buyu la asali.", "Dipping again and again empties the gourd of honey.", PROVERB),
        DailyQuote("A man is rich in proportion to the number of things which he can afford to let alone.", source = "Henry David Thoreau, Walden (1854)"),
        DailyQuote("Fimbo ya mbali haiui nyoka.", "A stick far away can't kill the snake. Keep an emergency fund within reach.", PROVERB),
        DailyQuote("Small daily spends add up. KSh 100 a day is about KSh 3,000 a month.", source = TIP),
        DailyQuote("The borrower is servant to the lender.", source = "Proverbs 22:7"),
        DailyQuote("Kila ndege huruka na mbawa zake.", "Every bird flies with its own wings. Live within your means.", PROVERB),
        DailyQuote("Rather go to bed supperless than rise in debt.", source = "Benjamin Franklin, The Way to Wealth (1758)"),
        DailyQuote("Mstahimilivu hula mbivu.", "The patient one eats the ripe fruit.", PROVERB),
        DailyQuote("Before a big purchase, wait a day. If you still want it tomorrow, it's probably worth it.", source = TIP),
        DailyQuote("Annual income twenty pounds, annual expenditure nineteen nineteen and six, result happiness.", source = "Mr Micawber in Charles Dickens, David Copperfield (1850)"),
        DailyQuote("Ahadi ni deni.", "A promise is a debt.", PROVERB),
        DailyQuote("He that goes a borrowing goes a sorrowing.", source = "Benjamin Franklin, The Way to Wealth (1758)"),
        DailyQuote("Haraka haraka haina baraka.", "Hurry, hurry has no blessing. Be wary of any deal that says act now.", PROVERB),
        DailyQuote("Sort your payments for a week and the monthly summary starts telling you something real.", source = TIP),
        DailyQuote("Wealth gotten by vanity shall be diminished: but he that gathereth by labour shall increase.", source = "Proverbs 13:11"),
        DailyQuote("Mwenye pupa hadiriki kula tamu.", "The greedy one never gets to taste the sweet.", PROVERB),
        DailyQuote("Time is money.", source = "Benjamin Franklin, Advice to a Young Tradesman (1748)"),
        DailyQuote("Kidole kimoja hakivunji chawa.", "One finger can't crush a louse. Saving together, as in a chama, goes further.", PROVERB),
        DailyQuote("A money market fund pays interest every day. Even small amounts left there grow.", source = TIP),
        DailyQuote("Go to the ant, thou sluggard; consider her ways, and be wise.", source = "Proverbs 6:6"),
        DailyQuote("Maji yakimwagika hayazoleki.", "Spilled water can't be gathered back. Spent money is gone.", PROVERB),
        DailyQuote("Diligence is the mother of good luck.", source = "Benjamin Franklin, The Way to Wealth (1758)"),
        DailyQuote("Mtegemea cha nduguye hufa maskini.", "Whoever depends on a relative's wealth dies poor.", PROVERB),
        DailyQuote("Count transaction costs as spending. Many small sends usually cost more than one bigger one.", source = TIP),
        DailyQuote("Wealth is not his that has it, but his that enjoys it.", source = "Benjamin Franklin, Poor Richard's Almanack (1736)"),
        DailyQuote("Kamba hukatikia pabovu.", "A rope breaks at its weakest point. Know where your money leaks.", PROVERB),
        DailyQuote("Kawia ufike.", "Arrive late, but arrive. Slow saving still gets there.", PROVERB),
        DailyQuote("If an offer promises double your money fast, it's almost certainly a scam.", source = TIP),
        DailyQuote("Tamaa mbele, mauti nyuma.", "Greed in front, ruin behind.", PROVERB),
        DailyQuote("Penye nia pana njia.", "Where there is a will, there is a way.", PROVERB),
        DailyQuote("Ukiona vyaelea, vimeundwa.", "If you see something floating, someone built it. Wealth is built, not found.", PROVERB),
        DailyQuote("Mtaka cha mvunguni sharti ainame.", "Whoever wants what is under the bed must bend down.", PROVERB),
        DailyQuote("Akufaaye kwa dhiki ndiye rafiki.", "A friend who helps you in hardship is the real friend.", PROVERB),
        DailyQuote("Kupotea njia ndiko kujua njia.", "Getting lost is how you learn the way. A bad month teaches the most.", PROVERB),
        DailyQuote("Keep three months of expenses aside and an emergency becomes an inconvenience.", source = TIP),
        DailyQuote("Fedha ni sabuni ya roho.", "Money is the soap of the soul.", PROVERB),
    )

    /** Same quote all day, a new one at midnight. */
    fun forDay(date: LocalDate = LocalDate.now()): DailyQuote =
        all[Math.floorMod(date.toEpochDay(), all.size.toLong()).toInt()]
}
