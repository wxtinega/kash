package com.kash.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.kash.app.ui.icons.Lucide
import com.kash.insights.Category
import com.kash.parser.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "KSh 1,500" (no cents when whole), or dots when amounts are hidden. */
fun Money.display(hidden: Boolean, sign: Boolean = false, incoming: Boolean = false): String {
    if (hidden) return "KSh ••••"
    val abs = Math.abs(cents)
    val whole = String.format(Locale.US, "%,d", abs / 100)
    val frac = abs % 100
    val body = if (frac == 0L) "KSh $whole" else "KSh $whole.${frac.toString().padStart(2, '0')}"
    val prefix = when {
        sign && incoming -> "+"
        sign -> "−"
        cents < 0 -> "−"
        else -> ""
    }
    return prefix + body
}

/** Screen readers get words, not symbols. */
fun Money.spoken(hidden: Boolean, incoming: Boolean? = null): String {
    if (hidden) return "Amount hidden"
    val dir = when (incoming) { true -> "received "; false -> "spent "; null -> "" }
    return dir + display(false).replace("KSh", "") + " shillings"
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

fun LocalDateTime.shortTime(): String = format(TIME)

fun LocalDate.dayLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(DAY)
}

fun Category.icon(): ImageVector = when (this) {
    Category.FOOD -> Lucide.Utensils
    Category.FUEL_TRANSPORT -> Lucide.Fuel
    Category.AIRTIME_DATA -> Lucide.Smartphone
    Category.RENT_HOUSING -> Lucide.House
    Category.UTILITIES -> Lucide.Zap
    Category.SCHOOL -> Lucide.GraduationCap
    Category.SHOPPING -> Lucide.ShoppingBag
    Category.GOING_OUT -> Lucide.Wine
    Category.BETTING -> Lucide.Dices
    Category.HEALTH -> Lucide.HeartPulse
    Category.FAMILY -> Lucide.Users
    Category.FRIENDS -> Lucide.User
    Category.SUBSCRIPTIONS -> Lucide.Tv
    Category.CASH -> Lucide.Banknote
    Category.FEES -> Lucide.Percent
    Category.LOAN_REPAYMENT -> Lucide.HandCoins
    Category.SAVINGS -> Lucide.PiggyBank
    Category.TRANSFER -> Lucide.ArrowLeftRight
    Category.MONEY_IN -> Lucide.ArrowDownLeft
    Category.OTHER -> Lucide.Ellipsis
}
