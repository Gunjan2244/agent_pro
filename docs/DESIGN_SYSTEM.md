# Design system — "Clean Professional"

**Principles:** ease of use, professionalism, calm.
- The app is close to white.
- Colour is used only for the main action, the selected state and statuses; everything else is greyscale.
- Screens are uncluttered: one job and one primary action each, with details one tap away.

One theme is shared by the app and the agent websites. All values live in `shared/design-tokens.json` (see SHARED_DEFINITIONS.md); this doc explains them.
- **Dynamic colour (Material You) is off.**
- **Light is the default.** Dark mode is designed separately and follows the system unless the user picks one in Settings.

## Calm-screen rules (apply to every screen)
1. **One primary action per screen,** as a filled button or FAB. Secondary actions are outlined or text buttons, and the rest go in the overflow menu.
2. **One job per screen.** Secondary information sits behind a tap: a row that opens a screen or a bottom sheet.
3. **Home shows at most 3 blocks above the fold,** and no screen other than Insights has more than one chart.
4. **Colour is reserved.** Primary blue is only for the main action, selected state and links. Tones are only for status. Everything else is grey on white.
5. **Whitespace instead of decoration.**
   - Only the 3 spacing values are used: `space.l` (24) between sections and `space.m` (16) for the gutter.
   - Only the 4 type sizes are used (see Typography).
   - No nested cards, no shadows on cards, and no decorative dividers.
   - Hairline dividers are allowed only between list rows.
6. **At most 2 icons in the top bar;** the rest go in overflow.
7. **List rows:** one title line, one supporting line, and at most one trailing element.
8. **Forms show 6 or fewer fields at once.** Optional fields go under **More details**, which expands.
9. **Short copy.** Labels are 3 words or fewer, helper text is one line, and there's no marketing copy inside the app.
10. **Empty states teach the next step** with one sentence and one button.

## Colour
### Light (default)
| Role | Value | Used for |
|---|---|---|
| background / surface | `#FFFFFF` | Screens |
| surfaceContainerLow | `#F9FAFB` | Grouped areas, search field fill |
| surfaceContainer | `#F3F4F6` | Selected list row, chips (unselected) |
| surfaceContainerHigh | `#EDEEF1` | Pressed states |
| outlineVariant | `#E5E7EB` | Hairline borders and dividers |
| outline | `#D1D5DB` | Text field borders |
| onSurface | `#111827` | Primary text |
| onSurfaceVariant | `#4B5563` | Secondary text, icons |
| muted | `#6B7280` | Captions, timestamps (≥ 4.5:1 on white) |
| primary | `#1F4AA8` | Primary buttons, selected nav, links, focus ring |
| onPrimary | `#FFFFFF` | |
| primaryContainer | `#E8EEFB` | Selected chip and nav indicator |
| onPrimaryContainer | `#12306E` | |
| error | `#B42318` | Errors, overdue |

### Dark
| Role | Value |
|---|---|
| background / surface | `#0F1115` |
| surfaceContainerLow → High | `#16191F` · `#1C2027` · `#242932` |
| outlineVariant / outline | `#2A2F38` / `#3A404B` |
| onSurface / onSurfaceVariant / muted | `#F3F4F6` / `#C4C8D0` / `#A1A7B3` |
| primary / onPrimary | `#9DB7F5` / `#0B1F4D` |
| primaryContainer / onPrimaryContainer | `#1E2F57` / `#DCE6FF` |
| error | `#F97066` |

### Semantic tones (status only)
| Tone | Light text / container | Dark text / container |
|---|---|---|
| `info` | `#1F4AA8` / `#E8EEFB` | `#9DB7F5` / `#1E2F57` |
| `success` | `#067647` / `#ECFDF3` | `#47CD89` / `#0B3B26` |
| `warning` | `#B54708` / `#FFFAEB` | `#FDB022` / `#3D2A06` |
| `danger` | `#B42318` / `#FEF3F2` | `#F97066` / `#43160F` |
| `neutral` | `#4B5563` / `#F3F4F6` | `#C4C8D0` / `#242932` |

**A tone is never colour alone.** Every pill shows an icon and a text label.

- **Enquiry statuses:** New = info, Contacted = neutral, Follow-up = warning, Converted = success, Closed = neutral.
- **Policy statuses:** Active = success, Lapsed = danger, Matured = info, Surrendered = neutral.
- **Due urgency (`UrgencyChip`):** overdue = danger, 7 days or less = warning, 30 days or less = info, later = neutral. Labels: "Overdue 3d", "Due in 5d", "Due 15 Jan".
- **Domain status:** Live = success, Setting up = info, Renew soon = warning, Expired or failed = danger.
- **Plan status:** Free trial = info, Active = success, Renew now (grace) = warning, Expired (lapsed) = danger.

## Typography
### Font
| Script | Family | Weights | Why |
|---|---|---|---|
| Latin (English UI, numbers, ₹) | **Inter** | 400 Regular · 500 Medium · 600 SemiBold (and 700 Bold for the website hero only) | Neutral, professional and designed for screens. Its tabular figures keep money aligned |
| Devanagari (Hindi UI) | **Noto Sans Devanagari** | 400 · 500 · 600 | Designed to sit alongside Inter-style sans faces, so Hindi looks the same on every device |

- **App (Android now, iOS later):**
  - The family follows the app language: `en` uses Inter, `hi` uses Noto Sans Devanagari, which also contains Latin glyphs.
  - Hindi words typed by the agent into the English UI fall back to the platform's Devanagari font.
  - Fonts are **bundled** as Compose Multiplatform resources (`composeResources/font`), subset to the needed weights. The app looks identical on both platforms, offline, and on first launch.
- **Web:** `font-family: "Inter", "Noto Sans Devanagari", system-ui, sans-serif`, loaded from Google Fonts with `display=swap`.
- **No other fonts are used** anywhere, including italics. Emphasis comes only from weight and colour.

### Type scale: 4 sizes, each 1.67× the previous one
Each size is 14 × 1.67ⁿ, rounded to a whole sp:

| Token | Size | Exact | Line height | Used for |
|---|---|---|---|---|
| `text.s` | **14sp** | 14 | 20 | Everything that's read: body, list rows, labels, buttons, chips, fields, captions, nav labels, chart axes |
| `text.m` | **23sp** | 23.38 | 30 | Screen titles, sheet and dialog titles, KPI numbers, section headings on the website |
| `text.l` | **39sp** | 39.04 | 46 | Hero moments only: the Welcome headline, "Your site is live", the website hero headline on mobile |
| `text.xl` | **65sp** | 65.20 | 72 | **Website only:** the hero headline on desktop (≥ 1024px). Never used in the app |

**Hierarchy within a size comes from weight and colour only.** No other sizes are allowed.

| Style | Size | Weight | Colour | Examples |
|---|---|---|---|---|
| `body` | s | 400 | onSurface | Paragraphs, field values |
| `bodyMuted` | s | 400 | muted | Timestamps, helper text, captions, axis labels |
| `label` | s | 500 | onSurfaceVariant (onPrimary on buttons) | Buttons, chips, field labels, nav labels, section labels |
| `strong` | s | 600 | onSurface | List row titles, amounts in rows |
| `title` | m | 600 | onSurface | Top bar titles, sheet titles |
| `number` | m | 600, `tnum` | onSurface | KPI tiles, totals |
| `display` | l | 600 | onSurface | Hero moments (no more than one per screen) |
| `webHero` | xl | 700 | onSurface | Website desktop hero |

- **All Material 3 `Typography` slots map onto these 4 sizes.** Display, headline and title go to m or l; body and label go to s. That way library components never bring in another size.
- **Money and numbers always use tabular figures** (`tnum`).
- **In the `hi` locale, line heights increase by 2sp** (20→22, 30→32 and so on). Sizes don't change.
- **Sizes are in sp,** so they scale with the user's font setting. Layouts must survive 200%.

## Shape
| Token | dp | Used for |
|---|---|---|
| sm | 8 | Text fields, chips, small buttons |
| md | 12 | Cards, buttons (48dp tall), list groups |
| lg | 16 | Bottom sheets (top corners), dialogs, FAB |
| full | — | Status pills, avatars |

## Surfaces and elevation
- **Cards** are white with a 1dp `outlineVariant` hairline border and no shadow. In dark mode they use `surfaceContainerLow` and no border.
- **Lists** sit directly on the background with hairline dividers, not inside cards.
- **Only these elevate:** the FAB, bottom sheets, dialogs, menus and the snackbar.
- **The top bar is flat white** and shows a hairline bottom border only once the content scrolls.

## Spacing: 3 values only
| Token | Value | Used for |
|---|---|---|
| `space.s` | **8dp** | Between closely related things: icon and its label, a field label and its field, chips in a row, buttons side by side, the internal padding of pills and chips |
| `space.m` | **16dp** | The default: screen gutter, card and sheet padding, list row padding, between items in a group, leading icon to text |
| `space.l` | **24dp** | Between sections or groups on a screen, above a section header, above the bottom action bar |

**Every padding, margin, gap and `Spacer` uses one of these three values.** Nothing else is allowed, not even 4, 12 or 32.

Fixed **component dimensions** aren't spacing; they're constants inside `core/designsystem` components:
- touch targets ≥ 48dp
- buttons 48dp
- list rows 72dp (two lines: 16 + 20 + 20 + 16) or 56dp (one line)
- icons 24dp
- radii 8/12/16

**Layout rules**
- Never use fixed-width text containers.
- Layouts must survive Hindi and 200% font scale. Rows grow taller rather than truncate, except for list titles, which truncate to one line with an ellipsis.

**Enforcement:** `verifyShared` also scans Kotlin outside `core/designsystem` for raw `.dp` in `padding(`, `spacedBy(`, `Spacer(`/`height(`/`width(` calls used as spacing, and for raw `.sp` anywhere. Either fails the build. The web CSS lint does the same for raw `px`/`rem` in margin, padding and gap.

## Buttons
| Kind | Style | Rule |
|---|---|---|
| Primary | Filled primary, 48dp, radius 12 | **One per screen** |
| Secondary | Outlined (outline colour), 48dp | At most 2 |
| Tertiary | Text button | Inline links, "More details" |
| Destructive | Text or filled in error colour | Only inside confirm dialogs |

## Iconography
Use **Material Symbols Rounded**, outlined weight 400, in `onSurfaceVariant` grey. Icons take the primary colour only when they're selected.

## Motion
- Subtle and quick, with Material 3 standard easing: 150ms for state changes, 250ms for navigation.
- Shared-axis X for forward and back navigation, fade-through for tab switches, no bouncy effects.
- A haptic tick when an action succeeds.
- Respects the system "remove animations" setting.

## Charts
Charts follow the dataviz method: pick the form first, colour last, and validate the palette.
- **Single series:** blue `#2a78d6` light / `#3987e5` dark. No legend; the title names the chart.
- **Two series** (premium collected vs due) add orange `#eb6834` / `#d95926`. They always show a legend, and the latest values carry direct labels.
- **Ordinal stages** (the pipeline) use the blue ramp from step 250 to 550.
- **Never use dual axes.** Two measures on different scales go in two charts or become a stat tile.
- **Never reuse status tones as series colours.**
- **Marks:**
  - Columns have 4dp rounded tops and a 2dp gap between them.
  - Lines are 2dp, with markers of at least 8dp.
  - Gridlines are horizontal only, in `outlineVariant`.
  - Axis text is `muted`.
- **Values** are compact ₹ (₹1.2L, ₹3.4Cr), and tapping a mark shows a tooltip.
- **Every `ChartCard`** has a title, a one-line takeaway and a "View as table" toggle.
- All of this is implemented once in `core/designsystem/ChartStyle.kt`, which wraps Vico.

## Components (`core/designsystem/components`)
| Component | Purpose / rules |
|---|---|
| `AppScaffold` | Top bar, offline banner slot, snackbar host, FAB or bottom action slot |
| `AppTopBar` | Title, back, and at most 2 actions plus overflow |
| `OfflineBanner` | A thin grey bar: "Offline — showing saved data" |
| `ScreenState` | Switches between loading (skeleton), empty, error and content |
| `EmptyState` / `ErrorState` | Line icon, one sentence, one button |
| `Skeletons` | Grey placeholder blocks, with a subtle shimmer |
| `ListRow` | Leading avatar or icon, title, supporting text, one trailing element |
| `NavRow` | A row with a chevron that opens a sub-screen (for progressive disclosure) |
| `StatusPill(tone, icon, label)` | Fully rounded, tonal container |
| `UrgencyChip(dueDate)` | Works out its own tone and label |
| `KpiTile` | Label (`label`), value (`number`, 23sp), delta (arrow icon and `bodyMuted` text) |
| `ChartCard` | Title, takeaway, chart, table toggle, empty state |
| `SectionHeader` | A small grey label plus an optional "See all" |
| `SearchField` | Filled with `surfaceContainerLow`; 300ms debounce |
| `FilterChipRow` | Built from catalog lists, with counts; scrolls sideways |
| `MoneyText` / `MoneyField` | Indian grouping, tabular figures |
| `DateField` | Opens the Material date picker |
| `MoreDetails` | An expandable group for optional form fields |
| `BottomActionBar` | Sticky primary action |
| `AppSnackbarHost` | Includes Undo |
| `ConfirmDialog` | Only for irreversible actions |
| `PriceSummary` | Line items, the total "incl. GST", and the renewal note (used in checkout) |
| `ProgressStepper` | Vertical steps with status (used for domain setup) |
| `FormRenderer` | Renders `FieldSpec` lists; the only way to build forms |

A debug-only **Component gallery** shows every component in light, dark, EN, HI and at 200% font scale. It's the baseline for the Roborazzi screenshot tests.

## Website theme
- Agent sites follow the same principles: a white background, the same fonts, and the **same 4 type sizes and 3 spacing values** (exported to `tokens.css` as `--text-s|m|l|xl` and `--space-s|m|l`). Sections are separated by multiples of `--space-l`, and there's one call to action per section.
- The accent comes from the 8 curated `accentSwatches` and is used only for buttons, links and small highlights, never for large colour blocks.
- Templates differ in layout only.
