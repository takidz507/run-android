# Run Android v3.2.0 — Implementation Notes (`IMPLEMENTATION-NOTES.md`)

- **Application ID**: `com.takidz.run`
- **Version**: `versionCode = 5`, `versionName = "3.2.0"`
- **Signing Keystore**: `debug.keystore` preserved untouched in the project root (`2666` bytes).
- **Wire Protocol & Storage**: 100% unchanged from v3.0.0 / v3.1.0 and Web v2 Pro (`MqttRoomManager.java`, `RoomCryptoUtils.java`, `PrefsManager.java`).

---

## 1. Summary of v3.2.0 Visual Rebuild (Design A — `7196.png`) & Bug Fixes

### A. Visual Rebuild Strictly Following Reference Image `7196.png` (Design A · Warm Paper)
1. **Home Screen (`activity_main.xml`, `item_recent_room.xml`, `MainActivity.java`)**:
   - **Light Top Bar**: `Run.` brand wordmark with a lime period dot (`SpannableString`), subtle connection status dot (`#29`), `⋮` More menu button, and circular `T` user avatar orb on the top-right. Zero duplicate header icons.
   - **Single-Line Greeting**: Rendered directly on the warm paper canvas (`Good evening, Taqi` / `مساء الخير، تقي`) with no enclosing profile card box.
   - **Slim 1-Line Online Strip**: Compact horizontal pill (`● 1 online`) + single-line horizontal user chips instead of tall 2-line avatar blocks.
   - **Slim Join / Create Bar**: Single-line pill input (`Enter room code or create / filter…`) with a compact `Join →` pill button.
   - **`Your rooms` + `Recent` Sections**:
     - `Your rooms` title sits directly over an overlapping lime highlighter marker bar (`FrameLayout` with `viewYourRoomsHighlighter` at `bottom|start`), matching `7196.png`.
     - Room rows sit directly on the canvas (`recentRoomsCard` is transparent with `0dp` stroke — no card-inside-card nesting).
     - Each room card (`item_recent_room.xml`) displays:
       - **Line 1**: Bold room title on the start + single-line unbreakable `#CODE` on the end (`singleLine="true"`, `maxLines="1"`).
       - **Line 2**: Single-line last message preview (`Salim: I uploaded the TD solutions`) or relative activity (`Last active 2h ago`).
       - **Line 3**: Single clean lime pill badge (`● 1 online` or `🌐 Public group`), with the 3 right-hand action buttons (`copy`, `archive`, `delete`) removed from the row and moved to a long-press BottomSheet (`showRoomQuickActionsSheet`).
     - When more than 3 active rooms exist, a **`Recent`** sub-heading is automatically inserted above older rooms (matching `Chemistry Lab` under `Recent` in `7196.png`).
   - **Floating `+` Button**: Circular `56dp` floating `+` button at bottom-end (`#B9E832` in Warm Paper; translucent frosted glass droplet with specular stroke in Liquid Glass).
   - **Owner's Strict `⋮` Rule (*"أي شيء يأكل مساحة يروح لقائمة ⋮"*)**: The `⋮` More sheet gathers Public Groups, Feedback Board, Archived Rooms, My Saved Messages (`#31`), Online People & Friends (`#13`), Pair/Transfer Account (`#25`/`#30`), Language (`#28`), and Theme & Profile (`#14`/`#22`).

2. **Room Screen (`activity_room.xml`, `item_message.xml`, `item_task.xml`, `RoomActivity.java`, `MessagesAdapter.java`)**:
   - **Borderless Room Header**: Sits directly on the room paper canvas (`topBarCard` is transparent with `0dp` border) — Back arrow + Room Title (`Study Group D5` with overlapping lime highlighter marker) + single-line subtitle (`12 online · 48 messages`) on the start; `⋮` Room More button + circular single-letter room orb (`S`) on the end.
   - **Three Separate Pill Tabs**: `Chat` (dark charcoal `#23211C` pill with cream text in light themes), `Tasks` (soft warm beige `#ECE6D8` pill), and `Notes` (soft warm beige `#ECE6D8` pill) — separate rounded pills rather than a full-width segmented box.
   - **Clean Message Bubbles**: Incoming bubbles (`Salim`, `Amine`) use warm cream (`#FAF6EE`) with a `1dp` `#EDE7DA` border; outgoing bubbles (`You`) use warm lime (`#D9F27E`) with a `1dp` `#C5E265` border. Removed the cramped 3-button text bar from the bottom of every bubble (Swipe-to-Reply `#18` and Long-Press Sheet `#18`/`#28`/`#31` provide 1-tap Emoji Reactions, Save to My Saved, Reply/Quote, Translate, and Copy).
   - **Inline Tasks Preview on Chat**: Renders up to 2 compact rounded task pills (`[✓] Revise Analysis ch.2`, `[ ] Physics TP report — Friday`) directly below the chat list in `chatPanel` (matching the right phone in `7196.png`), with 1-tap checkbox toggling and tap-to-open `Tasks`.
   - **Slim Stadium Composer Pill**: Single clean bottom pill (`Write a message...` with attach, mic, and send icons inside).

---

### B. Root-Cause Fixes for the 3 Owner-Reported v3.1 Bugs

1. **Bug #1 Fixed — Unreadable `⋮` More Sheet in Liquid Glass (White Text on Light Cream Background)**:
   - *Cause*: `BottomSheetDialog` uses `@style/Theme.RunApp`, whose Material `colorSurface` is `#FCF9F2` (light cream). In `THEME_GLASS`, `palette.panel` is `#29FFFFFF` (`16%` translucent white). Setting `container.setBackgroundColor(palette.panel)` painted 16% white over the default `#FCF9F2` cream sheet while text used `palette.text` (`#F4FAF6` white).
   - *Fix*: Added `palette.sheetBg` and `ThemeHelper.prepareBottomSheet(BottomSheetDialog, View, Palette)` in `ThemeHelper.java`. Every BottomSheet across `MainActivity` and `RoomActivity` now clears Material's default `@id/design_bottom_sheet` background to `Color.TRANSPARENT` and paints the sheet root with a rounded `28dp` top-corner `GradientDrawable` filled with `palette.sheetBg` (`#122536` frosted deep ocean in `liquid_glass`, `#0B1520` in `liquid_glass_dark`, `#FAF6EE` in `warm_paper`, etc.) and a `1dp` specular top border.

2. **Bug #2 Fixed — Settings Sheet Scrolling Disappearing List & Stray Lime Artifacts**:
   - *Cause*:
     1. `dialog_theme_live_preview.xml` used a plain `android.widget.ScrollView` inside `BottomSheetDialog`, which does not participate in `CoordinatorLayout` nested scrolling — causing `BottomSheetBehavior` to intercept vertical drags and collapse/clip the sheet content during up/down scrolling.
     2. Calling `setBackgroundTintList(...)` on views backed by shared XML drawables (`@drawable/bg_brand_logo`, `@drawable/bg_highlighter_bar`, `@drawable/bg_code_pill`) mutated shared drawable constant state across views, leaving stray lime rectangles when views scrolled.
     3. In `THEME_GLASS`, setting `cards[i].setCardBackgroundColor(p.panel)` (`#29FFFFFF`) inside the scrollable sheet caused translucent overlaps during scroll invalidation.
   - *Fix*:
     1. Replaced `ScrollView` with `androidx.core.widget.NestedScrollView` in `dialog_theme_live_preview.xml` and `dialog_account_pairing.xml`, and configured `BottomSheetBehavior` in `ThemeHelper.prepareBottomSheet(...)` with `setSkipCollapsed(true)` and `STATE_EXPANDED`.
     2. Replaced shared XML drawable tinting with fresh programmatic `GradientDrawable` instances on every preview badge, highlighter bar, and checkmark.
     3. Added `palette.cardOpaque` in `ThemeHelper.java` so cards inside scrollable BottomSheets always have a clean, non-bleeding surface fill across all 7 themes.

3. **Bug #3 Fixed — Cramped Feel (`محشوراً`)**:
   - Removed the outer card around `Your rooms`, removed the profile card box around the greeting, removed the 3 action buttons from every room row, removed the outer card around the Room header, and removed the 3-button footer bar from every message bubble.

---

## 2. File-by-File Changelog (v3.1.0 → v3.2.0)

| File | What Changed in v3.2.0 |
|---|---|
| `app/build.gradle.kts` | Bumped `versionCode = 5` and `versionName = "3.2.0"`. |
| `app/src/main/java/com/takidz/run/utils/ThemeHelper.java` | Added `sheetBg` and `cardOpaque` to `Palette` across all 7 themes (`warm_paper`, `warm_paper_dark`, `clean_air`, `clean_air_dark`, `deep_night`, `liquid_glass`, `liquid_glass_dark`), and added `prepareBottomSheet(...)` & `applySheetSurface(...)` to fix Bug #1 and Bug #2. |
| `app/src/main/java/com/takidz/run/utils/MotionHelper.java` | Preserved `LIQUID_GLASS` droplet/elastic physics (`OvershootInterpolator(2.15f)` + specular glass sheen) as the gold standard and elevated `WARM_PAPER`, `CLEAN_AIR`, and `DEEP_NIGHT` with tactile scale and `animateStaggeredCard(...)` while respecting Lite Mode (`#21`). |
| `app/src/main/res/layout/activity_main.xml` | Rebuilt Home screen layout to match Design A (`7196.png`): light top bar (`Run.` + `⋮` + `T`), 1-line greeting directly on canvas, slim 1-line online strip, slim join/create bar, overlapping lime highlighter on `Your rooms`, unnested room list, and floating `+` button. |
| `app/src/main/res/layout/item_recent_room.xml` | Rebuilt room card to match Design A (`7196.png`): title + single-line unbreakable `#CODE`, 1-line message preview, and single clean lime status pill (`● 1 online` / `🌐 Public group`) with zero button clutter. |
| `app/src/main/res/layout/activity_room.xml` | Rebuilt Room screen layout to match Design A (`7196.png` right phone): borderless room header on canvas, overlapping lime highlighter under title, circular `S` room orb + `⋮` button, 3 separate pill tabs (`Chat`, `Tasks`, `Notes`), inline tasks preview strip below chat messages, and slim bottom stadium composer pill. |
| `app/src/main/res/layout/item_message.xml` | Rebuilt message bubble layout to match Design A (`7196.png`): `20dp` radius, `1dp` border, clean author + message body without the bottom 3-button text bar. |
| `app/src/main/res/layout/item_task.xml` | Streamlined task pill card (`16dp` radius) matching Design A (`7196.png`). |
| `app/src/main/res/layout/dialog_theme_live_preview.xml` | Switched root from `ScrollView` to `androidx.core.widget.NestedScrollView` (`overScrollMode="never"`) and isolated preview drawables to fix Bug #2. |
| `app/src/main/res/layout/dialog_account_pairing.xml` | Switched root from `ScrollView` to `androidx.core.widget.NestedScrollView`. |
| `app/src/main/java/com/takidz/run/ui/MainActivity.java` | Wired Design A Home structure, `Run.` lime-dot Spannable, `Recent` room sub-section, long-press room action sheet, `ThemeHelper.prepareBottomSheet(...)` on all BottomSheets, and fresh `GradientDrawable` instances in Settings Live Preview. |
| `app/src/main/java/com/takidz/run/ui/RoomActivity.java` | Wired Design A Room header, separate pill tabs, inline tasks preview (`renderInlineTasksPreview`), `⋮` Room More BottomSheet (`showRoomMoreSheet`), and `ThemeHelper.prepareBottomSheet(...)` on all Room BottomSheets. |
| `app/src/main/java/com/takidz/run/ui/adapters/MessagesAdapter.java` | Updated bubble stroke width (`1dp`) and tap/long-press handling to match Design A (`7196.png`). |
| `debug.keystore` | Preserved byte-for-byte in project root (`2666` bytes). |

---

## 3. Honesty & Trade-offs Disclosure
- **No APK built locally**: Per instructions, only the complete source tree + `debug.keystore` is delivered in the ZIP for CI/Mous verification.
- **No protocol or storage changes**: All MQTT brokers (`broker.emqx.io`, `broker.hivemq.com`, `test.mosquitto.org`), topic structures (`run-v2-pro/room-...`, `run-v2-pro/signal-...`), `SharedPreferences` keys (`run_v2_pro_prefs`), and SHA-256 hashed `ANDROID_ID` serial counting (`#24`) remain 100% untouched.
