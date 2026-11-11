# Kash design system and UX rules

| Requirement | How it's done | Where |
|---|---|---|
| Instant navigation | Tabs cross-fade in 120ms; each tab keeps its scroll and search state | `ui/KashNav.kt` |
| No unnecessary loading screens | Data is local and preloaded at app start; skeletons appear only if loading takes over 150ms | `KashApp.onCreate`, `rememberShowLoading` |
| Optimistic UI | "What was this?" answers disappear instantly; restored with a plain message if saving fails | `HomeViewModel.answer` |
| Smooth transitions | List items animate in/out (`animateItem`), cards resize smoothly, lock → app fade | screens |
| Empty states | Every list: icon, what it is, why it's empty, what to do (and "Clear search" for no results) | `EmptyState` |
| No frozen buttons | Work runs off the main thread; busy buttons show an inline ring and ignore repeat taps instead of greying out | `KashButton` |
| Touch feedback | One `pressable` modifier everywhere: 97% press-down + ripple + accessibility role | `Pressable.kt` |
| Search as you type | Filters local data on every keystroke, off the main thread, no debounce | `ActivityViewModel` |
| Background sync | WorkManager every 3h (battery not low), no internet needed | `sync/SyncWorker.kt` |
| Internet interruptions | App works fully offline; online-only features say "waiting for internet" calmly | `core/Connectivity.kt` |
| No purple gradients | None. One faint accent glow behind the top of screens | `KashBackground` |
| Orange instead of green | Accent #F86939; green/red only for small +/− amounts | `theme/Color.kt` |
| Compact spacing | 4/8/12/16/24 scale, 16dp screen margins | `Space` |
| No overlapping text | Text columns use `weight(1f)`, titles wrap to 2 lines, chips use `FlowRow` | `TxRow`, `QuestionCard` |
| 48dp touch targets | Buttons, pills, toggles, tab items, icon buttons | `Space.touch` |
| Consistent icons | 49 Lucide icons generated from the official set; no emoji | `ui/icons/Lucide.kt` |
| Skeletons, not spinners | Skeleton rows match real row height; only spinner is 16dp inside a busy button | `States.kt` |
| Text scaling | All text in sp; no fixed text heights; tabular figures for amounts | `theme/Type.kt` |
| Human-readable errors | Every exception mapped to a sentence | `core/HumanErrors.kt` |
| Independent dark/light themes | Two hand-tuned palettes, all text ≥ 4.5:1 contrast | `theme/Color.kt` |
| Manual theme choice | System / Light / Dark in Settings, saved | `SettingsScreen`, `SettingsStore` |
| Restrained glass | Translucent surfaces + 1dp hairline borders; no backdrop blur (readability and frame rate) | `GlassCard` |

## Contrast (WCAG)
White on #F86939 is only 2.97:1, so text on orange is near-black #1C1410 (6.1:1).
Orange used as text on light backgrounds is #B9461F (5.3:1). Muted text: 5.9:1 light, 7.5:1 dark.
