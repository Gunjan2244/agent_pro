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
  - **Bundled files** (`composeApp/src/commonMain/composeResources/font/`, OFL licences in `composeResources/files/licenses/`):
    - `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`: the static TTFs from the rsms/inter release **v4.1** (`Inter-4.1.zip`, `extras/ttf/`). No 700: the app never uses `webHero`.
    - `noto_sans_devanagari.ttf`: the **variable** font from google/fonts at commit `2bc5d431b584d89e646214bf6cfa75494e76ceea` (`ofl/notosansdevanagari/NotoSansDevanagari[wdth,wght].ttf`), instanced at 400/500/600 with `FontVariation`. This build covers Devanagari **and Latin, digits and ₹**; the static TTFs of the notofonts release have no Latin letters, so in `hi` English text would have fallen back to the system font.
- **Web:** `font-family: "Inter", "Noto Sans Devanagari", system-ui, sans-serif`, **self-hosted** woff2 files in `web/fonts/` (fontsource 5.3.0: Inter latin + latin-ext 400/500/600/700, Noto Sans Devanagari 400/500/600), declared in `web/styles/fonts.css` with `font-display: swap`. No Google Fonts request.
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

**Enforcement:** `verifyShared` also scans Kotlin outside `core/designsystem` for raw `.dp` in `padding(`, `spacedBy(`, `Spacer(`/`height(`/`width(` calls used as spacing, and for raw `.sp` anywhere. Either fails the build.
- The scan covers `composeApp/src` and `androidApp/src` and reports each problem as `file:line:col`.
- It ignores comments and strings, `0.dp`, and other calls such as `size(48.dp)`.
- Use `Tokens.Space.s|m|l` and `Tokens.Text`, which are generated from `design-tokens.json`. The web CSS lint does the same for raw `px`/`rem` in margin, padding and gap.

## Buttons
| Kind | Style | Rule |
|---|---|---|
| Primary | Filled primary, 48dp, radius 12 | **One per screen** |
| Secondary | Outlined (outline colour), 48dp | At most 2 |
| Tertiary | Text button | Inline links, "More details" |
| Destructive | Text or filled in error colour | Only inside confirm dialogs |
| Google sign-in (`GoogleSignInButton`) | The secondary style with Google's four-colour "G" (24dp, **untinted**, `ic_google_g`) before "Continue with Google" | Welcome only (DECISIONS #112). Google's branding asks for its mark unchanged; it is the only multi-colour icon in the app and the only third-party mark |

## Iconography
Use **Material Symbols Rounded**, outlined weight 400, in `onSurfaceVariant` grey. Icons take the primary colour only when they're selected.

## App icon
- **Design:** a bold, rounded white "A" (for Agent). Its crossbar is a rising chart line that ends in a dot just past the right leg, standing for growth and the app's charts. A gap in the background colour separates the line from the legs. It sits on flat `primary` (#1F4AA8).
  - Only these two colours are used, with no gradients, shadows, text or ₹ symbol.
  - It has no lamp, flame, hands or blue-and-yellow pairing, so it can't be mistaken for the LIC emblem, and it uses no insurer artwork (DECISIONS #47).
- **Source:** `design/app-icon/build_icon.py` defines the geometry on the 108×108dp adaptive-icon canvas. It merges the strokes into one flat even-odd outline, so the same path serves the coloured foreground and the monochrome layer.
  - The script asserts the glyph stays inside the 66dp safe circle.
  - It keeps the path under lint's 800-character `VectorPath` limit by drawing curves as 16-gons, which are under 0.4px off at the largest launcher size.
  - **Never edit the outputs by hand.** Change the script and re-run it from the repo root: `python design/app-icon/build_icon.py`. It needs `shapely`, `cairosvg` and `pillow`, for example in a throwaway venv.
- **Outputs:**
  - **Android:** `res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` (Android 13+ themed icons). `ic_launcher_background.xml` is a solid `primary` shape, written by hand.
    - `res/mipmap-anydpi/ic_launcher{,_round}.xml` combine the three layers.
    - minSdk is 26, so no PNG mipmaps are needed.
  - **Stores:** `design/app-icon/play-store-512.png` (Play listing) and `app-store-1024.png` (iOS later; opaque, no alpha). Both are the visible middle 72dp, full-bleed square, with no corner rounding; the stores apply their own masks.
  - **Master:** `agent-pro-icon.svg` and `agent-pro-glyph.svg`.
  - **Check:** `preview.png` shows the icon in circle and squircle masks and at 96, 48 and 24 px.

## Auth emails
Supabase Auth emails are built to the same tokens by `scripts/gen-email-templates.mjs` (DECISIONS #69); nobody edits the generated HTML by hand.
- **Look:** a white card with a hairline border and a `lg` radius on `surfaceContainerLow`, the wordmark above it, and a 23 px title, 14 px body and a primary button with an `md` radius. Spacing only uses 8, 16 and 24 and type only 14, 23 and 39 (the confirmation code). Primary blue is only the button, the logo mark and the link.
- **Logo:** an HTML "A" on a primary square, not the icon: email clients can't render SVG and block remote images. Swap in the PNG icon once it is hosted (DECISIONS #69).
- **Language:** English first, a divider, then Hindi (`lang="hi"`, line height +2). The tagline from the Welcome screen is in the footer.
- **Dark mode:** `prefers-color-scheme: dark` swaps in the dark tokens (Apple Mail and most mobile clients; Gmail's apps often apply their own inversion).
- **Fonts:** Inter, then system fonts, then Noto Sans Devanagari. No web font is loaded, so no font host sees the reader.
- **Never:** the LIC or any insurer's logo (CLAUDE.md, Branding).

## Motion
Subtle and quick: Material 3 standard easing, 150ms for state changes and 250ms for navigation, and no bouncy or decorative effects. Motion explains where something came from or went; it never delays the agent. All specs live in `Motion.kt`; screens never write their own.

| When | Pattern | Where it's wired |
|---|---|---|
| Opening a screen and going back (list → detail, Home → Settings, every sign-in and onboarding step) | **Shared-axis X**: in from the end and out to the start (a tenth of the width) with a fade; reversed on back. The back transition also follows the predictive back gesture. | `MainShell`'s NavHost; `SharedAxisNavHost` (auth, password recovery, onboarding); the launch screens |
| Switching bottom-bar tabs | **Fade-through**: the old tab fades out (100ms), then the new one fades in while growing from 92% (150ms) | `MainShell` (`isTabSwitch`: both screens are tab roots of different tabs) |
| The app gate: Splash → Welcome, Onboarding → Main, signing out | **Fade-through** | `AppNavHost` |
| A screen's state: skeleton → content, empty or error; Retry | **Crossfade** (150ms). Keyed on the state's *kind*, so a refresh or realtime update with new data doesn't flash. | `ScreenState`, `StateCrossfade` (detail screens) |
| In-place swaps: the customer's Policies / Details / Activity, chart ⇄ table | **Crossfade**; the chart card eases to its new height (250ms) | `StateCrossfade`, `ChartCard` |
| A list row arrives, leaves or moves (a new enquiry, a filter, a delete) | Fade in or out (150ms); the other rows slide into place (250ms). Rows need a stable `key`. | `itemMotion()` on customers, policies, enquiries and dues |
| Showing and hiding a part of the screen (More details, the offline banner, field errors) | Expand or shrink with a fade (150ms) | `AnimatedVisibility` in the components |
| Onboarding progress | The bar's fill animates (250ms) | `StepProgressBar` |
| Inviting a tap on the forward arrow ("Make website from this →", "Use this template →" in the template previews) | **Nudge**: the trailing arrow moves forward by `space.s` and back, forever (`Motion.NUDGE_MILLIS` = 600ms each way, standard easing; mirrored in RTL). One of the three deliberate loops (with the skeleton shimmer and the template grid); it stops when animations are off. | `PrimaryButton`/`BottomActionBar` `trailingIcon` + `nudgeTrailingIcon` |
| The template grid (onboarding step 2, Appearance → Template; DECISIONS #120) | **Auto-scroll**: 4 s after every tile's picture has loaded, one tile at a time slides its full-page picture up at a steady `Motion.TEMPLATE_SCROLL_DP_PER_SECOND` (300 dp/s, linear), jumps back to the top at the end, then the next tile goes; round and round. Stops when animations are off; UI tests' infinite-animation policy stops it too. | `TemplateGrid` (`runTemplateAutoScroll`) |

- A haptic tick when an action succeeds.
- Respects the system "remove animations" setting: Compose scales every animation by the platform's `MotionDurationScale`, so they become instant; the skeleton shimmer stops (`rememberReduceMotion()`).
- **Not used, on purpose:** container transform and shared-element transitions (a list row growing into its detail screen). They look good, but cost a shared-element scope through every feature, and the shared-axis already shows the direction. Animated count-ups on KPI numbers, because numbers should read at once. Animating the bottom bar in and out, because it would resize every screen mid-transition.

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
| `AppScaffold` | Top bar, offline banner slot, snackbar host, FAB or bottom action slot; lifts above the keyboard (`imePadding`) |
| `AppTopBar` | Title, back, and at most 2 actions plus overflow; a `trailing` slot (counts as one action) holds the Home avatar |
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
| `BottomActionBar` | Sticky primary action, with optional helper text ("Needs internet") and one optional text-button alternative (e.g. Publish now / Later); an optional trailing icon (e.g. the nudging forward arrow) |
| `AppSnackbarHost` | Includes Undo |
| `ConfirmDialog` | Only for irreversible actions |
| `PriceSummary` | Line items, the total "incl. GST", and the renewal note (used in checkout) |
| `ProgressStepper` | Vertical steps with status (used for domain setup) |
| `FormRenderer` | Renders `FieldSpec` lists; the only way to build forms. The focused text field gets an accent label and a faint accent fill, and is scrolled into view with the next field visible below it |
| `AppNavigationBar` | The 4-tab bottom bar: flat surface with a hairline on top, grey icons and labels, primary + `primaryContainer` indicator for the selected tab, optional count badge |
| `TemplateGrid` (`feature/website/ui`, not a design-system component but the one tile grid) | 2 columns, `space.s` gaps, `space.m` padding; each row is the grid height ÷ 2.1 so 2 rows show whole and a tenth of the third peeks. A tile is a card-shaped picture (hairline border, `md` radius; the current template has a 2dp `primary` border and a primary "Current" label) with the name in `strong` below; while loading, a 16% wash of the template's accent. |
| `SegmentedControl` | Single-choice segments, e.g. `[ Customers | Dues ]`; the selected one uses `primaryContainer` |
| `StepProgressBar` | The thin progress bar at the top of each onboarding step; TalkBack reads "Step 2 of 5" |
| `AvatarButton` | The Home avatar that opens Settings (48dp touch target around the 40dp `Avatar`) |
| `PhotoCropArea` | The photo editor's crop surface (DECISIONS #124): the photo under a fixed frame of the field's aspect, outside dimmed (`scrim` at 60%), a dark `scrim` surround and a white frame (2dp) in both themes, an optional thin white circle guide; pinch zooms around the fingers (1–4×), drag moves, the frame always stays covered. Radius 12 |
| `PhotoShapePreview` | One "How customers see it" preview: the current crop cut like the website's CSS (`object-fit: cover`, `object-position: 50% focusY%`), 72dp tall (`Dimens.cropPreview`), a circle or radius 12, with a `bodyMuted` label under it. Shown in a wrapping row |
| `AppBottomSheet` | Modal sheet with a title (radius 16 top corners, elevated surface), e.g. the setup checklist |

A debug-only **Component gallery** shows every component in light, dark, EN, HI and at 200% font scale. It's the baseline for the Roborazzi screenshot tests.
- It lives in `core/designsystem/gallery/` and has switches for Dark, Hindi and 200% text.
- It's reachable only in debug builds: `androidApp/src/debug` declares `GalleryActivity` with no launcher entry. Open it with `adb shell am start -n com.sledje.agentpro/com.sledje.agentpro.android.debug.GalleryActivity`.

## Implementation (`composeApp/.../core/designsystem`)
| File | Contains |
|---|---|
| `Theme.kt` | `AgentProTheme(themeMode = System \| Light \| Dark)` and `AgentProTheme(darkTheme)`; `AgentProTheme.colors`, `.text`, `.tone(tone)`; `currentAppLocale()` (catalog `AppLocale`) picks the font family and line heights |
| `Color.kt` | Light and dark M3 `ColorScheme`s mapped from `Tokens`, and `AppColors` (muted, card, cardBorder, elevatedSurface, chart series and ramp, tones). Roles the tokens don't define are derived from token values: secondary/tertiary = primary, secondaryContainer = primaryContainer, surfaceVariant = surfaceContainer, surfaceContainerHighest = surfaceContainerHigh, surfaceTint = surface (no tonal tint), inverse roles = the other theme's surface/onSurface/primary, error container = the `danger` tone |
| `Type.kt` | `AppTextStyles` (`body`, `bodyMuted`, `label`, `strong`, `title`, `number`, `display`) and the M3 `Typography` (display → l; headline and title → m 600; body → s 400; label → s 500). `number` and `MoneyText` use `tnum`. `hi`: +2sp line heights |
| `Shape.kt` | `AppShapes.sm/md/lg/sheet/full` and the M3 `Shapes` |
| `Spacing.kt` | `Spacing.s/m/l` (= `Tokens.Space`) and `Dimens`: the fixed component dimensions (touch 48, button 48, rows 72/56, icon 24, pill icon 18, pill 32, avatar 40, hairline 1, the selected-choice border 2 (the current template tile), swatch 40, image slot 96, chart 200, chart marks, the site QR code 200) |
| `Motion.kt` | Standard easing, 150/250ms specs, shared-axis X, fade-through (`fadeThroughEnter`/`Exit`), `crossfade()`, `rememberReduceMotion()` (the platform's `MotionDurationScale` is 0), `rememberSuccessHaptic()` |
| `ChartStyle.kt` | `AppChart(ChartData)` over Vico: `ChartKind.Column/Line/Ordinal`, at most 2 series, compact ₹ when `money`, tooltip on tap, legend and a direct label of the latest values for 2 series |
| `AppLocale.kt` | `LocalAppLocale` (expect/actual): overrides the language inside a composition (used by the gallery; the iOS `LocaleController` later) |
| `components/` | Every component in the table above, plus `PrimaryButton`/`SecondaryButton`/`TertiaryButton`/`DestructiveButton`/`GoogleSignInButton`, `AppFab`, `AppCard`, `HairlineDivider`, `Avatar`, `AppTextField`, `FieldFrame`, `PickerField`, `SelectField`, `ChoiceChips`, `ToggleRow`, `SwatchPicker`, `ImagePickerSlot`, `NetworkImage` (Coil 3 remote image, centre-cropped in its slot; Phase 8), `AppSnackbar`, `DiscardChangesDialog` and `CardSkeleton` |
| `gallery/ComponentGallery.kt` | The component gallery (sections, `GalleryFrame` variants) |

Component behaviour that the tables above leave to the implementation:
- **Field layout:** the label sits above the field (`label` style, `space.s` gap), with one line of error or helper text below. Multiline fields with a `maxLength` show a counter (`12/160`); other fields don't. Required fields aren't marked; validation says "Required".
- **Password fields** (`FieldType.Password`, app forms only, never a site-schema type) are masked with a show/hide icon button and are never trimmed; `FieldSpec.minLength` (app forms only) adds "At least 8 characters".
- **`FormRenderer`** shows all fields when there are 6 or fewer. Otherwise it shows every required field plus the first optional ones up to 6 (schema order), and the rest go under **More details**; a screen can pass `visibleKeys` instead. A screen that shows some fields elsewhere (onboarding step 1's name and mobile card with Edit) passes `hiddenKeys`: those aren't rendered, even when required, but their values and validation still count. Otherwise required fields are never hidden, and More details opens by itself when a hidden field has an error. `FieldSpec.mustMatch` (app forms; Confirm password) makes a field invalid unless it equals the named field ("Passwords don't match"). A list item is titled "Item 1", "Item 2"…, with Remove and an Add button up to `maxItems`. The image field is a slot with "Add photo"; the screen wires the MediaPicker through `onPickImage`.
- **App-only form fields** (never site-schema types, like `FieldType.Password`): `FieldType.Money` renders a `MoneyField` and returns a `Double` (≤ 2 decimals, `min`/`max` checked); `FieldSpec.suggestions` gives a text field an autocomplete dropdown (up to 5 case-insensitive matches, prefix matches first, any text still allowed; used for `planSuggestions`); `FieldSpec.segmented` shows a select as a `SegmentedControl` (the premium mode).
- **`ListRow(current, muted)`** (setup checklist): `current` colours the leading icon and title primary (the next step); `muted` colours them `onSurfaceVariant` and marks the row disabled for TalkBack (`onClick` is null on such rows).
- **`Avatar(photoUrl)` / `AvatarButton(photoUrl)`:** with a non-blank `photoUrl` the photo covers the circle; the initials stay underneath as the fallback while it loads or fails. Home passes the hero photo, else the profile photo.
- **`ListRow(unseenLabel = …)`** puts an 8dp primary dot (`Dimens.unseenDot`) before the title, with that TalkBack label (unseen enquiries).
- **`UrgencyChip`** tone and icon come from the catalog `dueBuckets`. Labels: "Overdue 3d"; "Due today"; "Due in 5d" inside the first upcoming bucket (this week); "Due 15 Jan" after that.
- **`FilterChipRow`** is single choice, with an "All" chip first; counts read "New · 3".
- **`Money.compact`** truncates to one decimal (₹1,25,000 → ₹1.2L, never rounded up) and shows the full amount below 1 lakh. L/Cr are used in Hindi too.
- **Pills** use 18dp icons so a pill stays one line tall; other icons are 24dp.
- **Dialogs, menus and the date picker** are white in light and `surfaceContainerHigh` in dark; the snackbar uses the inverse colours.
- **Undo snackbars** last exactly 5 seconds (`showUndo`).

## Website theme
- Agent sites follow the same principles: a white background, the same fonts, and the **same 4 type sizes and 3 spacing values** (exported to `tokens.css` as `--text-s|m|l|xl` and `--space-s|m|l`). Sections are separated by multiples of `--space-l`, and there's one call to action per section.
- The accent comes from the 8 curated `accentSwatches` and is used for buttons, links and highlights. Since DECISIONS #117 it may also colour a **ticker band** (the accent mixed 70–75% with black, white text) and the active slider dot; large blocks otherwise use the dark ink band (`#0B1220`–`#111827`, white and `#CBD5E1`–`#E5E7EB` text, always ≥ 4.5:1) or the pale `--tint`.
- `--tint` is the accent mixed 10% into white (`color-mix`, set on `.tpl`). It is a pale surface for small highlights only: the hover of chips, the initials monogram and, **in the "Best offers" family only**, the hero's featured-offer card (DECISIONS #76). Never a section background, never saturated. Muted text on the tint uses `onSurfaceVariant` (`muted` falls below 4.5:1 there).
- **One layout system (2026-10-22; WEB_RENDERER.md "Layout system"):**
  - **Grid:** one content width per template (`--wrap` 1080 px; minimal 640 px) and one gutter (`--gutter`: `space.m` on phones, `space.l` from 720 px) for every section, the hero and the footer; reading text held to a 720 px measure. Breakpoints: 720 and 1024 px only.
  - **Rhythm:** `--sec-y` above and below every section (`space.l` on phones, `space.l`×2 from 1024 px), so sections are 48 / 96 px apart, **separated by whitespace, never by a divider**. Hairlines only between list rows (FAQ rows, achievements, modern's and minimal's rows).
  - **Headings:** `<h1>` `text.l` (`text.xl` from 1024 px; minimal `text.m`), weight 700; section `<h2>` `text.m` 600 with `space.l` below; card titles `text.s` 600, offer and company titles `text.m` 600. Each template treats all of its `<h2>`s alike.
  - **Cards:** white, 1 px `outlineVariant`, `shape.md`, `space.m` padding (`space.l` from 720 px), no shadow. Classic uses soft cards everywhere instead (`surfaceContainerLow`, no border, `shape.lg`); modern and minimal use hairline rows instead of cards.
  - **Buttons:** 48 px, `shape.md`, `space.l` side padding; filled (accent) or outlined (white with an accent border). **Chips** (quick-interest links): 40 px outlined pills; on the guide, 48 px full-width choice buttons (`shape.md`) in a 2-column (4 from 720 px) grid. **Tags** (static labels: insurance types, the compact interest list): 32 px `surfaceContainer` pills with `onSurfaceVariant` text.
  - **FAQ:** an accordion of `<details>` rows (48 px minimum, question 600, an accent "+"/"−" on the right, the answer in `onSurfaceVariant`).
  - **Form:** a card; labels 500 above 48 px fields (`shape.sm`, `outline` border); full-width submit; a 20 px consent checkbox aligned with the first line of its text.
  - **Footer:** a pale `surfaceContainerLow` band aligned to the grid (minimal: plain). The Best offers family also puts its contact section on that band, so the page ends in one calm block.
- **The five templates (DECISIONS #117; supersedes the structures below).** Trust (`classic`, the user's Theme A): sticky top bar, banner picture with a dark text panel, trust ticker, offers slider, photo + dark introduction panel, companies ticker, 2×3 plan cards, "why choose me" and policyholders sliders, dark footer. Fresh (`modern`): sticky top bar, big headline beside a 4:5 portrait on a tinted block with a floating number badge, companies marquee band, big numbers, bento services, wide lead offer, numbered columns, pull-quote slider, FAQ beside its heading, dark contact. Simple (`minimal`): a pale page, the white profile card pinned left from 1024 px, a résumé column with uppercase label headings. Spotlight: dark hero band with an offer slider, a white trust bar overlapping it, an offer wall (first card double width), picture tiles. Guide: centred hero on the tint with picture tiles, a 3-step strip, need groups beside a sticky index, a trust ticker band, one big quote. Shared components: **top bar** (64 px, translucent white with blur, a hairline and shadow once scrolled; links from 1024 px, a 44 px menu button below), **slider** (scroll-snap row, 44 px round arrows, 10 px dots, the current one 28 px wide in the accent), **ticker** (a looping row, 48 px gap, paused on hover), **lift** (cards rise 4 px with a soft shadow on hover) and **zoom** (pictures scale 1.06 on hover).
- **Pages (DECISIONS #122; WEB_RENDERER.md "Pages").** Sites have Home (each template's own hero and sections, with teasers that link on), Insurance, a page per plan type, About me and Contact. The inner pages are **one shared layout** (`styles/pages.css`, loaded only on them) that each template skins in its `style.css`; no new spacing value, type size or font.
  - **Menu:** Home · Insurance · About me · Contact (· Customer login when the portal is on). The current page is in the accent, 600, underlined 2 px with an 8 px offset in the top-bar row (`aria-current="page"`; a plan page marks Insurance). Phones keep the `details.tb-menu` button. Minimal puts the menu in its profile card: a list with hairlines in the pinned card from 1024 px, a row of tabs (2 px accent underline on the current one, 700) under the card on phones. Spotlight's top bar is the ink band (`#0F172A`) with light links, white current link and the accent Call button, so it runs into the dark hero on every page.
  - **Page header (`header.sec-ph`, the page's only `<h1>` at `text.l`; modern `text.xl` from 1024 px):** a muted breadcrumb line ("Home › Insurance › Term insurance", the current part 500), the `<h1>`, a lead. Plan pages and About are **split**: picture or photo left (2 : 3 from 720 px; plan picture 4:3, the agent's photo 4:5, `shape.lg`), text right; on phones the picture is on top. The plan header has "Ask about this plan" + WhatsApp (ghost); About shows the designation as a kicker, the bio, counting numbers (`text.l` 700 accent) and the facts. Skins: classic a `--tint` band, centred, with its 40 px accent rule under the `<h1>` (split headers left-aligned); modern white, left, the 48 px accent rule above the `<h1>`; minimal in the résumé column beside the card; spotlight the ink band with light text and the trust bar overlapping its lower edge on every page; guide a `--tint` band, centred.
  - **Insurance:** plan-type cards: on phones compact rows (112 px 4:3 picture left, name `text.m` 600, three lines of description, "See plan →" and an "N offers" pill); from 720 px picture cards 16:9 on top, 2 then 3 per row (classic soft cards, modern numbered "01" with the first card tinted, minimal plain rows without pictures). Then the offers (cards with pictures; modern as hairline rows, minimal bordered rows; guide keeps its need groups beside the sticky index).
  - **Plan page:** that type's offers two per row; with none a **note**: 2 px dashed `outline` border, `shape.md`, `space.l` padding, a muted line and "Enquire now →". "Other plans" are **picture chips**: 48 px outlined pills with a 40 px round thumbnail, in one row that scrolls sideways.
  - **About:** companies as a ticker band, "Why choose me" as icon cards (40 px icon on a `--tint` circle; 2 per row on phones, 4 from 1024 px), achievements as a **timeline** (a row with accent dots on a 2 px rule from 1024 px; year rows with hairlines on phones), testimonials as a slider of quote cards (1 / 2 / 3 per view).
  - **Contact:** 5 : 7 columns: "Ways to reach me" cards (a 40 px icon on `--tint`, a muted label, the value 600, a 40 px button: Call filled, WhatsApp and Email outlined) and an address/hours card on the left, the enquiry form card on the right; on phones the three buttons in one row above the form, the address card after it. Then the FAQ (3/5) beside the "For my customers" card (2/5) from 1024 px, without the section bands.
  - **Help band** (end of Insurance, plan pages and About): a `shape.lg` block on `surfaceContainerLow` (classic the deep accent with a white button, modern the ink band running into its dark footer, spotlight `--tint`, minimal a white bordered card) with a heading, a lead, "Send an enquiry" and WhatsApp. Guide ends every inner page with its accent trust ticker. Home ends with a **contact band** (heading, one line, Call · WhatsApp · Send an enquiry) instead of the form.
- **Earlier structures, replaced by #117 (kept for history). Two families (DECISIONS #76, #85).** "Meet me": `classic` (centred, round portrait, soft cards, centred rows, a short accent rule under each heading), `modern` (magazine: photo beside a big headline, a short accent rule above each heading, hairline rows, numbered services), `minimal` (a 640 px visiting card: small label headings, text links, plain lists). "Best offers" (`spotlight`, `guide`) may add 16:9 images to offer and plan cards and the tinted featured card, plus a trust strip (hairline pills: years and clients in the accent, highlight titles with their icons) and a lazy hero scene. All stay close to white.
- **No chat-style look (DECISIONS #87):** no speech bubbles, avatars beside text, greeting bubbles or chat-style FAQ; the agent's first-person copy reads as ordinary text.
- **Companies I represent (DECISIONS #90):** company names as plain text (`text.m` 600 on cards, bold in minimal's list), each with its insurance types as tags. Never a logo.
- **Offer cards:** white, hairline `outlineVariant` border, `--shape-md`; badge as a neutral grey pill; title `--text-m` 600; the footer (ends date in `onSurface` 500, the CTA as an accent text link with an arrow, the button only in the offers family's hero, disclaimers `muted`) pinned to the card's bottom, so a row of cards lines up. Bundled illustrations are white-edged, so they sit on white cards with no frame; the tinted featured card never carries an image.
- **Sticky Call / WhatsApp bar (phones, < 720 px):** fixed to the bottom, white with a hairline top border, two equal 48 px buttons (Call filled, WhatsApp outlined), `--space-s` padding plus the safe area; the page reserves its height so nothing moves when it appears. It is not always on screen (DECISIONS #123): hidden on load, it slides up (transform, `--motion-medium`) once 70% of the page is scrolled or on any scroll up, and slides away on a scroll down below 70%; hidden links are unfocusable (`visibility: hidden`), reduced motion skips the slide, and without JavaScript it stays visible.
- **Customer login pages (`web/styles/portal.css`, 2.9 KB; WEB_RENDERER.md "Customer login (portal)"):** one reading column (`--measure`) on the page background, with the agent's accent. They reuse the site components (cards, 48 px buttons and fields, `.pill`, `<details class="more">`) and add only a few things: a **policy card** (plan as `h3`, premium line 600, the due line as text plus a `.pill`), a **summary tile row** (`<dl class="pt-tiles">`: tiles of 1 px `outlineVariant` border, `--shape-md`, `--space-m` padding, a `--text-s` muted label over a `--text-m` 600 value; two columns on phones, four from 720 px; the Next due tile's border follows the due tone, `tone-danger` border `error`, `tone-warning` border `--accent`; overdue pills are filled `error` with `onPrimary` text, due-soon pills use `primaryContainer`; replaces the old Next premium card), a **flash line** (`--tint` background, 600) and an **error box** (1 px `error` border). Due-status pills take their label and tone from `catalog.dueBuckets`; nothing is colour-only (the label is always text). No new spacing value, type size or font. Disclosure rows are 48 px high.
- **"For my customers" section and hero link (`pp-*`, `hero-login` in `base.css`; WEB_RENDERER.md):** the section is one card-like block (heading, a muted lead, four icon + text points, a button and a muted WhatsApp line) that each template restyles inside its own system with the same tokens, nothing new: classic a soft card with the points as a 2×2 grid of tiles, modern a hairline row with accent text-link actions and an arrow, minimal plain lines (points as one dotted line, the button as an underlined text link), the Best offers family a compact card on the pale band with the points as outlined 40 px pills. Icons are the 24 px stroke set in `sections/icons.js`. The hero's **Customer login** link is a small 500-weight text link, absolutely positioned top right with a `--space-s` tap padding so it adds no layout shift; on modern's phone hero it is a `--shape-lg` pill on `surface` over the photo. The accent stays on the link and the icons only.
- **Motion on sites (DECISIONS #117; this replaces the calmer rule below):** the hero text rises `--space-l` on load (transform only, 700 ms, staggered 60 ms; the `<h1>` is painted at once so it stays the LCP element) and the hero picture fades in; below the fold each section rises in, and the cards of a `.stg` grid follow one after another (80 ms apart); sliders autoplay every 4.5–7 s while in view, pause on hover or focus and stop once used; tickers loop at a speed set by their length (20–90 s); numbers count up once (1.2 s, ease-out); guide's step line draws across. Everything is off under `prefers-reduced-motion: reduce` (no autoplay, no count-up, tickers become static wrapped text) and the page is complete without JS.
- **Earlier motion rule (before #117):** only `--motion-short` (chip hover) and `--motion-medium` (an opened FAQ answer rising 4 px into place; a **section reveal**: each section that starts below the fold fades in and rises `--space-s` once as it enters the view, same on every template, the hero never), plus smooth anchor scrolling, all switched off under `prefers-reduced-motion: reduce`. Only opacity and transform change, so nothing shifts; sections in view on load are never hidden; without JS the page is fully visible.
