# Storage Helper interaction verification

## Implementation notes

Search focus belongs to the screen as well as the edit box. Previously, manually unfocusing the box before clearing the screen focus path could leave the screen referencing that same, now unfocused child. Minecraft ignores assigning focus to the same child again. The session now releases focus through `screen.setFocused(null)` and restores the box's focus on mouse interaction. Native `EditBox` handles caret placement, drag selection, arrows, deletion, and platform clipboard shortcuts. The normalized search query is separate from the editable text and is retained when focus changes.

APIs were inspected in the actual Loom-generated Minecraft 26.3 sources: `ItemContainerContents.copyInto`, `BundleContents.itemCopies`, `BundleMouseActions`, `BundleItem.overrideOtherStackedOnMe`, `AbstractContainerMenu.setSelectedBundleItemIndex`, and the server's bundle-selection handler. Shulker previews use `DataComponents.CONTAINER`; absent data is labeled empty or unavailable. Client data cannot prove a server has not withheld contents.

Bundle entries within `BundleItem.getNumberOfItemsToShow` can be extracted. Left-click sends the vanilla selection packet (with vanilla selection prediction), followed by a normal secondary `PICKUP` on the bundle slot. The cursor must be empty and the source slot must permit modification. Deeper entries are scrollable but read-only: remove earlier stacks first. No client-only removal, remote shulker access, or custom packet is used. Multiplayer behavior still needs verification on actual servers.

Shift + scroll **up over player main inventory/hotbar** deposits a proportional quantity; Shift + scroll **down over container slots** withdraws it. The Phase 2 default `scrollItemsPerUnit` is 2: one effective wheel unit requests two items. Fractional motion accumulates and one-item remainders are permitted. This is based on vanilla's effective mouse delta after its wheel settings, not a hardware-independent distance. Sub-item fractional input accumulates; a 0.01-unit movement requests nothing at the default sensitivity. Source/direction changes or a gap over 200 ms reset the fraction.

`scrollClicksPerTick` defaults to 4 (clamped to 1–5), independently of sensitivity (clamped to 1–32 items per unit). Deposit/Sort keep their existing `clicksPerTick` setting. Up to 64 pending items are accepted, with a per-event cap of 64. Unstarted requests expire after 200 ms without input; an already active batch completes or returns its cursor. Requests from new events can reuse a cursor stack already owned by the active batch.

The previous bottleneck was three inventory clicks per individual item at the default one click per tick. The new path uses vanilla QUICK_MOVE for whole-source requests, secondary PICKUP to take half a stack, and primary PICKUP for bulk/capacity-limited merges. Exact residual quantities use individual secondary clicks only while the same cursor stack remains held; surplus is returned in one primary click. A 32-item request from a source of 64 into an empty destination takes two clicks instead of 96. Arbitrary quantities, fragmented space, click pacing, and server validation can still limit throughput. Every step checks slot insertion limits and components; unexpected inventory/cursor corrections cancel work and trigger safe recovery. No client inventory edits or outside-slot clicks are used.

The shulker preview now calls the actual screen's `getHoveredSlot` through a mixin invoker. The previous 16×16 approximation missed the one-pixel slot border accepted by vanilla's 18×18 hit region, allowing the ordinary text tooltip to take over there. Both custom preview rendering and native tooltips now resolve the same slot. Removing `CONTAINER` from a tooltip-only copy suppresses the textual content list while retaining other tooltip information.

Bundle presentation opens on the next render with available data. The old fixed six-column/three-row allocation and general 300 ms dismissal grace are gone. Geometry uses 18-pixel cells, a 12-pixel header, only the required columns/rows (up to three visible rows), and no instruction footer. Two entries use a 40×34 GUI-pixel popup instead of 128×108. A narrow one-pixel transition corridor joins it to the source; leaving both regions closes it immediately. Another source outside the popup replaces it on the next render. Cached item copies are invalidated when source contents change. Selection/extraction remains the vanilla workflow described above.

GUI-scale tests also exposed that Fabric resets per-screen listeners on resize. Sessions now reattach tick, input, and removal listeners along with their widgets, preserving operation pacing and search behavior after scaling.

## Manual checklist (not all automated)

- [ ] In both chest and shulker screens, type a query, click a slot or background, refocus without clearing, edit at the clicked caret, drag-select, use arrows, Backspace/Delete, and Cmd/Ctrl+A/C/X/V. Verify highlights persist unfocused; X explicitly clears. Inventory/drop/hotbar keys must type while focused.
- [ ] Hover undyed and all 16 colored shulkers in player inventory and supported containers, including empty, filled, renamed, enchanted contents, partial stacks, and unusual components. Verify the 9×3 slot positions, icons/counts/glint, displayed name, and absent-data message. Check GUI scales, corners, resizing, and normal tooltip information.
- [ ] Hover empty and filled bundles. Cross the gap into the popup through its narrow transition gap, hover entries for tooltips/highlights, scroll long contents, and click selectable entries with an empty cursor. Verify exact extracted stack/components, unchanged remaining contents, and read-only deeper entries. Repeat with occupied cursor, changed/moved source, closing screen, and during Sort/Deposit.
- [ ] Shift-scroll one step and several steps in each supported direction over main inventory, hotbar, and container slots. Verify proportional quantities (2 items/unit by default), partial-stack priority, and empty cursor after each completed step. Opposite directions do nothing; ordinary Shift-click still transfers normally.
- [ ] Use a macOS trackpad with fractional movement, reversals, inertia, rapid input, and source changes. Verify no old fraction leaks between gestures, no runaway backlog, and no transfer while editing search or scrolling inside the popup.
- [ ] Test exhausted sources, full destinations, compatible partials, differing names/components/enchantments/durability, unstackables, bundles with contents, and shulker items. Nested shulkers must remain prohibited. Confirm no drops or unintended bundle insertion/extraction.
- [ ] Release Shift or move away during each transfer phase; cancel/close/death/server-close during operations. Check cursor recovery and totals after reopening.
- [ ] On a real multiplayer server, test latency, rejected clicks, concurrent edits, and server corrections for deposits, sort, wheel transfers, and bundle extraction. Confirm no duplication/loss and no continued clicks against a different menu.
- [ ] Recheck Deposit Matching, both sort modes/shared preference, persistent sorting, and search on single/double/trapped/copper chests and all placed shulker colors. Verify panel/transfer controls do not activate in unrelated screens.

## Checks performed for this change

- JDK 25 build and 45 JUnit tests passed. New pure tests cover fractional/reversed/timed-out scrolling, burst and backlog limits, component-sensitive partial stacking, full/limited/rejecting destinations, and bundle click hazards.
- The actual 26.3 Fabric client gametest passed in an integrated singleplayer server: existing chest deposits and both sort modes; search focus release/refocusing/editing; exact eight-item deposit, four-item withdrawal, whole-stack merging, fractional one-item withdrawal, and a two-item request with only one item available with an empty cursor; opening and sorting every placed shulker color; prohibited nested-shulker wheel insertion; and selection/extraction of the second bundle entry with exact server-side cursor and remaining-content assertions.
- Slot-border checks and preview screenshots were exercised at GUI scales 1 and 2, with sparse contents retaining their positions/counts. Popup opening, immediate dismissal, switching between bundles, and extraction were exercised. Pure geometry tests cover screen edges and the transition corridor. This does not cover every GUI scale, item glint, missing-data case, or preview color.
- No real multiplayer server, induced server correction, physical trackpad, platform clipboard shortcut, or complete manual checklist run was performed.

## Phase 2 after checkpoint v1.1.1

Checkpoint commit `e2f8c46a49dc7e818a4f02adf07ef28d0781d0f7` on `main` was built with JDK 25 (40 tests passed), committed, and pushed together with annotated tag `v1.1.1` before these changes. No GitHub Release was created. The subsequent UI refinements retain version 1.1.1.

The duplicate bundle name came from rendering both the popup header and the full ordinary tooltip. Only the first (name) line of that supplementary tooltip is now omitted. Other text, including custom lore, remains; contained-item tooltips retain their own names. Long popup titles scroll inside a clipped header so the full name remains available once.

Fresh sensitivity is two items per effective wheel unit, preserving fractional accumulation, exact quantity planning, efficient batches, and the independent bounded click budget. `scrollSensitivityVersion: 1` marks the new default. Unversioned values of 8 migrate to 2, other custom values remain unchanged, and versioned values of 8 remain customized. An old explicit 8 and an old default 8 were stored identically and cannot be distinguished. The migration writes the marker once.

Deposit, Sort, mode selection, Clear, and cancellation use native 20×20 buttons with game-rendered pixel icons, native focus/hover/disabled sprites, pressed feedback, translated tooltip text, and nonempty narration messages. The mode icon changes from pixel A/Z to colored category squares; its tooltip includes the current mode and category order.

Search is aligned above the container when there are 24 GUI pixels of space. Otherwise it falls back beside the container above the compact action column. Pure layout tests cover single/shulker and double-chest dimensions over multiple GUI widths/heights, excluding titles and slots. The clear control, count, status, search focus handling, and Fabric resize event reattachment remain shared between supported container screens.

Phase 2 manual follow-up: check custom names and lore in other languages, icon contrast/narration with assistive settings, physical macOS trackpads, and real multiplayer corrections. Full destinations, remaining single items, clear/refocus, icon modes/cancellation, and double-chest fallback search should also be rechecked during normal play.

Phase 2 validation: the JDK 25 build and 45 unit tests passed, including sensitivity migration and layout bounds. The actual client gametest exercised icon actions and mode selection, disabled action state during sorting, top/fallback search editing on a real double chest at GUI scales 1 and 2, all placed shulker colors, exact quantities at the new sensitivity, one-item remainders, and custom-named bundle extraction/presentation with retained lore. Screenshots were reviewed; physical trackpads, screen readers, other languages, and real multiplayer corrections were not tested.

## Final side-toolbar refinement

Action icons form a 20-pixel-wide vertical column with 22-pixel spacing beside the container; the default side is right, falling back to left when required. Search stays aligned above the container when space permits and retains its on-screen side fallback for short double-chest screens. The search count remains beneath the icon column.

The operation-status widget and HUD completion messages have been removed. Running operations still disable conflicting buttons and retain their cancel icon. Immediate failures and completed/cancelled outcomes go only to the debug logger; operation state machines, server/cursor checks, recovery, and stopping conditions are unchanged. Search matches, “No matches,” tooltips, and narration remain visible. No replacement chat or toasts are emitted.

Verification includes unit coverage for left-side fallback and container/GUI-size bounds, plus live client checks for a vertical icon column, absent status widgets, search refocusing, both sort modes, deposit, and scroll transfer behavior. Physical trackpad feel, assistive narration, and real multiplayer corrections remain manual follow-up items.
