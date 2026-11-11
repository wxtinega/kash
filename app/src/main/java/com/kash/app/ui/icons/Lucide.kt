// Generated from lucide-static 1.72.0 (ISC licence, https://lucide.dev). Do not edit by hand.
package com.kash.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Lucide icons: 24×24, 2px round stroke. Tinted by the Icon composable. */
object Lucide {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val House: ImageVector by lazy {
        icon(
            "house",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val Receipt: ImageVector by lazy {
        icon(
            "receipt",
            "M12 17V7",
            "M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8",
            "M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z",
        )
    }

    val PiggyBank: ImageVector by lazy {
        icon(
            "piggy-bank",
            "M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1z",
            "M16 10h.01",
            "M2 8v1a2 2 0 0 0 2 2h1",
        )
    }

    val Settings: ImageVector by lazy {
        icon(
            "settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0",
        )
    }

    val Search: ImageVector by lazy {
        icon(
            "search",
            "m21 21-4.34-4.34",
            "M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0",
        )
    }

    val X: ImageVector by lazy {
        icon(
            "x",
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }

    val ArrowUpRight: ImageVector by lazy {
        icon(
            "arrow-up-right",
            "M7 7h10v10",
            "M7 17 17 7",
        )
    }

    val ArrowDownLeft: ImageVector by lazy {
        icon(
            "arrow-down-left",
            "M17 7 7 17",
            "M17 17H7V7",
        )
    }

    val Wallet: ImageVector by lazy {
        icon(
            "wallet",
            "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
            "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
        )
    }

    val Landmark: ImageVector by lazy {
        icon(
            "landmark",
            "M10 18v-7",
            "M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z",
            "M14 18v-7",
            "M18 18v-7",
            "M3 22h18",
            "M6 18v-7",
        )
    }

    val Smartphone: ImageVector by lazy {
        icon(
            "smartphone",
            "M7.0 2.0h10.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-10.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0z",
            "M12 18h.01",
        )
    }

    val ShieldAlert: ImageVector by lazy {
        icon(
            "shield-alert",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "M12 8v4",
            "M12 16h.01",
        )
    }

    val CircleHelp: ImageVector by lazy {
        icon(
            "circle-help",
            "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0",
            "M9 9.5a3 2.5 0 016 0c0 2-2 2-3 3.5",
            "M12 17h.01",
        )
    }

    val Check: ImageVector by lazy {
        icon(
            "check",
            "M20 6 9 17l-5-5",
        )
    }

    val ChevronRight: ImageVector by lazy {
        icon(
            "chevron-right",
            "m9 18 6-6-6-6",
        )
    }

    val Eye: ImageVector by lazy {
        icon(
            "eye",
            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
            "M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0",
        )
    }

    val EyeOff: ImageVector by lazy {
        icon(
            "eye-off",
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20",
        )
    }

    val Fingerprint: ImageVector by lazy {
        icon(
            "fingerprint",
            "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
            "M14 13.12c0 2.38 0 6.38-1 8.88",
            "M17.29 21.02c.12-.6.43-2.3.5-3.02",
            "M2 12a10 10 0 0 1 18-6",
            "M2 16h.01",
            "M21.8 16c.2-2 .131-5.354 0-6",
            "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
            "M8.65 22c.21-.66.45-1.32.57-2",
            "M9 6.8a6 6 0 0 1 9 5.2v2",
        )
    }

    val WifiOff: ImageVector by lazy {
        icon(
            "wifi-off",
            "M12 20h.01",
            "M8.5 16.429a5 5 0 0 1 7 0",
            "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523",
            "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764",
            "m2 2 20 20",
        )
    }

    val RefreshCw: ImageVector by lazy {
        icon(
            "refresh-cw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    val Utensils: ImageVector by lazy {
        icon(
            "utensils",
            "M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2",
            "M7 2v20",
            "M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7",
        )
    }

    val Fuel: ImageVector by lazy {
        icon(
            "fuel",
            "M14 13h2a2 2 0 0 1 2 2v2a2 2 0 0 0 4 0v-6.998a2 2 0 0 0-.59-1.42L18 5",
            "M14 21V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v16",
            "M2 21h13",
            "M3 9h11",
        )
    }

    val ShoppingBag: ImageVector by lazy {
        icon(
            "shopping-bag",
            "M16 10a4 4 0 0 1-8 0",
            "M3.103 6.034h17.794",
            "M3.4 5.467a2 2 0 0 0-.4 1.2V20a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.667a2 2 0 0 0-.4-1.2l-2-2.667A2 2 0 0 0 17 2H7a2 2 0 0 0-1.6.8z",
        )
    }

    val Zap: ImageVector by lazy {
        icon(
            "zap",
            "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z",
        )
    }

    val GraduationCap: ImageVector by lazy {
        icon(
            "graduation-cap",
            "M21.42 10.922a1 1 0 0 0-.019-1.838L12.83 5.18a2 2 0 0 0-1.66 0L2.6 9.08a1 1 0 0 0 0 1.832l8.57 3.908a2 2 0 0 0 1.66 0z",
            "M22 10v6",
            "M6 12.5V16a6 3 0 0 0 12 0v-3.5",
        )
    }

    val HeartPulse: ImageVector by lazy {
        icon(
            "heart-pulse",
            "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
            "M3.22 13H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27",
        )
    }

    val Users: ImageVector by lazy {
        icon(
            "users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M16 3.128a4 4 0 0 1 0 7.744",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M5.0 7.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0",
        )
    }

    val User: ImageVector by lazy {
        icon(
            "user",
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            "M8.0 7.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0",
        )
    }

    val Tv: ImageVector by lazy {
        icon(
            "tv",
            "m17 2-5 5-5-5",
            "M4.0 7.0h16.0a2.0 2.0 0 0 1 2.0 2.0v11.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-11.0a2.0 2.0 0 0 1 2.0 -2.0z",
        )
    }

    val Banknote: ImageVector by lazy {
        icon(
            "banknote",
            "M4.0 6.0h16.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0z",
            "M10.0 12.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0",
            "M6 12h.01M18 12h.01",
        )
    }

    val Percent: ImageVector by lazy {
        icon(
            "percent",
            "M19 5L5 19",
            "M4.0 6.5a2.5 2.5 0 1 0 5.0 0a2.5 2.5 0 1 0 -5.0 0",
            "M15.0 17.5a2.5 2.5 0 1 0 5.0 0a2.5 2.5 0 1 0 -5.0 0",
        )
    }

    val HandCoins: ImageVector by lazy {
        icon(
            "hand-coins",
            "M11 15h2a2 2 0 1 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 17",
            "m7 21 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a2 2 0 0 0-2.75-2.91l-4.2 3.9",
            "m2 16 6 6",
            "M13.1 9.0a2.9 2.9 0 1 0 5.8 0a2.9 2.9 0 1 0 -5.8 0",
            "M3.0 5.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0",
        )
    }

    val TrendingUp: ImageVector by lazy {
        icon(
            "trending-up",
            "M16 7h6v6",
            "m22 7-8.5 8.5-5-5L2 17",
        )
    }

    val TrendingDown: ImageVector by lazy {
        icon(
            "trending-down",
            "M16 17h6v-6",
            "m22 17-8.5-8.5-5 5L2 7",
        )
    }

    val ArrowLeftRight: ImageVector by lazy {
        icon(
            "arrow-left-right",
            "M8 3 4 7l4 4",
            "M4 7h16",
            "m16 21 4-4-4-4",
            "M20 17H4",
        )
    }

    val CircleDollarSign: ImageVector by lazy {
        icon(
            "circle-dollar-sign",
            "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0",
            "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8",
            "M12 18V6",
        )
    }

    val Dices: ImageVector by lazy {
        icon(
            "dices",
            "M4.0 10.0h8.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-8.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0z",
            "m17.92 14 3.5-3.5a2.24 2.24 0 0 0 0-3l-5-4.92a2.24 2.24 0 0 0-3 0L10 6",
            "M6 18h.01",
            "M10 14h.01",
            "M15 6h.01",
            "M18 9h.01",
        )
    }

    val Wine: ImageVector by lazy {
        icon(
            "wine",
            "M8 22h8",
            "M7 10h10",
            "M12 15v7",
            "M12 15a5 5 0 0 0 5-5c0-2-.5-4-2-8H9c-1.5 4-2 6-2 8a5 5 0 0 0 5 5Z",
        )
    }

    val Phone: ImageVector by lazy {
        icon(
            "phone",
            "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384",
        )
    }

    val Ellipsis: ImageVector by lazy {
        icon(
            "ellipsis",
            "M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0",
            "M18.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0",
            "M4.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0",
        )
    }

    val Sun: ImageVector by lazy {
        icon(
            "sun",
            "M8.0 12.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0",
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }

    val Moon: ImageVector by lazy {
        icon(
            "moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    val Monitor: ImageVector by lazy {
        icon(
            "monitor",
            "M4.0 3.0h16.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0z",
            "M8 21L16 21",
            "M12 17L12 21",
        )
    }

    val Inbox: ImageVector by lazy {
        icon(
            "inbox",
            "M22 12L16 12L14 15L10 15L8 12L2 12",
            "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
        )
    }

    val Calendar: ImageVector by lazy {
        icon(
            "calendar",
            "M8 2v3",
            "M16 2v3",
            "M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0z",
            "M3 9h18",
        )
    }

    val Bell: ImageVector by lazy {
        icon(
            "bell",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        )
    }

    val Lock: ImageVector by lazy {
        icon(
            "lock",
            "M5.0 11.0h14.0a2.0 2.0 0 0 1 2.0 2.0v7.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-7.0a2.0 2.0 0 0 1 2.0 -2.0z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val TriangleAlert: ImageVector by lazy {
        icon(
            "triangle-alert",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    val ChartNoAxesColumn: ImageVector by lazy {
        icon(
            "chart-no-axes-column",
            "M5 21v-6",
            "M12 21V3",
            "M19 21V9",
        )
    }

}
