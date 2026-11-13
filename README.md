# Storage Helper

A **client-side Fabric mod for Minecraft Java Edition 26.3** that makes chest storage easier:

| Feature | What it does |
|---|---|
| **Deposit Matching** | One click moves every item type that is already in the chest from your inventory and hotbar into it (partial stacks topped up first, then empty slots; overflow stays with you). |
| **Sort** | Physically rearranges the chest contents (never your inventory) either **A-Z** or **By Type**, consolidating partial stacks. The result persists because the items really move. |
| **Player Sort** | Sort only the 27 main player-inventory slots, leaving hotbar, equipment, offhand, crafting slots, and the container untouched. Available beside player storage in supported containers and in the survival inventory screen. |
| **Search** | A search box highlights matching chest slots live as you type (names and registry ids, case-insensitive), with a match counter and a clear button. |

It works in single-player and on ordinary vanilla/Paper/Fabric servers **without** being installed on the server: every change is made through normal, server-validated inventory clicks, exactly as if you had clicked the slots yourself.

Supported containers: regular single and double chests, including trapped chests and the copper chest variants, plus all 17 placed shulker-box colors. Barrels, ender chests, furnaces, crafting tables, merchants and server-plugin menus are left alone. Shulker boxes opened directly from inventory are not supported.

---

## Requirements

| Component | Version | Notes |
|---|---|---|
| Minecraft Java Edition | **26.3** | The mod declares `"minecraft": "~26.3"`. It is not built for other versions. |
| Java | **JDK 25** | Required by Minecraft 26.3 itself (`javaVersion.majorVersion = 25` in Mojang's version manifest). |
| Fabric Loader | **0.20.1** or newer | https://fabricmc.net/use/ |
| Fabric API | **0.166.2+26.3** (any 26.3 build) | Required separately. The mod uses its Screen, Lifecycle and Interaction event modules. |
| Gradle | 9.8.2 via the included wrapper | Nothing to install; `./gradlew` downloads it. |
| Fabric Loom | 1.19.4 | Gradle plugin, pulled automatically. |

These versions were checked on 2026-11-11 against Mojang's `piston-meta` manifest, `meta.fabricmc.net`, `maven.fabricmc.net` and the official `FabricMC/fabric-example-mod` `26.3` branch.

---

## Installation (playing)

1. Install the Fabric Loader for Minecraft 26.3 with the official installer (https://fabricmc.net/use/installer/) and start the game once with the new "fabric-loader-26.3" profile so the `mods` folder is created.
2. Download **Fabric API** for 26.3 (https://modrinth.com/mod/fabric-api or CurseForge) and put the jar in `mods`.
3. Put `storagehelper-1.2.0.jar` (from `build/libs`, see below) in the same `mods` folder:
   * macOS: `~/Library/Application Support/minecraft/mods`
   * Windows: `%APPDATA%\.minecraft\mods`
   * Linux: `~/.minecraft/mods`
4. Launch the Fabric profile. Open any chest: the Storage Helper panel appears to the right of it.

Only the client needs the mod. Servers do not need anything.

Settings are stored in `config/storagehelper.json` (inside the game directory):

```json
{
  "sortMode": "alphabetical",   // or "by_type"; also changed from the in-game selector
  "scrollItemsPerUnit": 2.0,   // proportional wheel sensitivity (1-32)
  "scrollSensitivityVersion": 1, // migration marker; keep this when customizing
  "scrollClicksPerTick": 4,    // separate scroll click budget per tick (1-5)
  "sortClicksPerTick": 4,      // shared container/player sorting budget, 1-5
  "clicksPerTick": 1,           // 1-5 Deposit Matching clicks per game tick
  "dimNonMatching": true        // darken chest slots that do not match the search
}
```

---

## Building from source (macOS setup)

```bash
# 1. JDK 25 (any distribution). Homebrew example:
brew install --cask temurin@25
#    or use an existing install, e.g. /Library/Java/JavaVirtualMachines/jdk-25.jdk

# 2. Point JAVA_HOME at it for this shell:
export JAVA_HOME=$(/usr/libexec/java_home -v 25)

# 3. Build (first run downloads Gradle, Loom, Minecraft 26.3 and Fabric API; a few minutes):
cd StorageHelper
./gradlew build
```

Outputs in `build/libs/`:

* `storagehelper-1.2.0.jar` – **the distributable mod** (install this).
* `storagehelper-1.2.0-sources.jar` – sources, not needed for playing.

The build runs the unit tests (`./gradlew test` runs them alone). Reports land in `build/reports/tests/test/index.html`.

### Development launch

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./gradlew runClient          # starts Minecraft 26.3 with the mod from source
./gradlew genSources         # decompiled Minecraft sources for your IDE
```

Import the folder as a Gradle project in IntelliJ IDEA or VS Code (Gradle for Java extension); Loom generates the run configurations.

---

## Using the panel

The search field sits above the container, aligned with its width. Compact 20-pixel action icons form a vertical column down the right side of the container, with a left-side fallback if needed. If a double chest leaves insufficient space above at a large GUI scale, search moves beside the container without covering titles or slots. Controls reposition on resize and GUI-scale changes; hover an icon for its action and sorting-mode tooltip.

* **Deposit Matching** – moves matching items. Hover for a tooltip; matching hotbar items are included, armor/offhand/cursor are not. While it runs the button reads **Cancel**.
* **Sort** – sorts the chest in the order shown on the selector below it. Reads **Cancel** while running.
* **Order icon: A-Z / categories** – click to toggle; the choice is saved.
* **Search…** – type to highlight. The cross icon clears. The counter shows "N matching" or "No matches". While the box is focused, keys go only to the box (no hotbar swaps, no `E` closing the chest); **Enter** or **Esc** leaves the box, a second **Esc** closes the chest. Clicking anywhere else also leaves the box.

Both operations require an empty cursor and refuse to start while another one runs. Busy actions use disabled states and cancel icons. Operation outcomes are written only to the debug log; no status text, HUD messages, chat, or toasts are shown.

### Hover previews and precise wheel transfers

- Hover any shulker-box item in your inventory or a supported container to see its 9×3 contents grid, with names, icons, and counts. This uses client-available item data; missing data is labeled rather than guessed. No placing or opening is needed.
- Hover a bundle, move into its popup, and left-click a selectable entry to extract that stack to an empty cursor using vanilla selection and inventory clicks. Scroll inside the popup to see more entries. Entries beyond vanilla's selectable front portion are read-only; remove earlier stacks first. Creative-inventory popups are read-only.
- Hold **Shift + scroll DOWN over your inventory/hotbar** to deposit a proportional quantity, or **Shift + scroll UP over the container** to withdraw. By default, one effective wheel unit requests 2 items. Fractional input accumulates, and a one-item source or remaining capacity still transfers that one item. Tiny fractional trackpad motion accumulates; it does not move a stack immediately. The opposite direction in the original source inventory returns the remembered transfer, even over an unrelated or empty slot after releasing Shift. Up to 64 items can be queued; excess input is ignored. Release Shift or move away to cancel pending requests. Unstarted work expires after 200 ms without input; an active batch safely completes or returns its cursor.

Wheel transfers, Deposit Matching, and Sort share one operation lock. Scroll sensitivity (`scrollItemsPerUnit`, default 2) is independent of its throughput (`scrollClicksPerTick`, default 4, maximum 5). Whole-stack requests use vanilla quick-move; partial quantities use half-stack pickup, capped merges, and single clicks only for exact remainders. A 32-item request from a 64-item stack into an empty destination takes two clicks instead of 96. Arbitrary quantities can take more clicks; server validation and tick pacing still apply. Deposit Matching retains `clicksPerTick`; sorting has its own `sortClicksPerTick` budget. Search retains its text when unfocused and supports clicking back into it, caret positioning, selection, and native platform clipboard shortcuts.

### Remembered returns and player sorting

- After a deposit, **Shift + scroll UP anywhere in the player storage/hotbar area** returns the remembered item. After a withdrawal, **Shift + scroll DOWN in the container area** returns it. Two items per wheel unit remains the default, including fractional accumulation and one-item remainders.
- History stores only actual predicted vanilla transfers, with exact item/components and a net quantity. Opposite input discards queued/unplaced work first, safely recovers any owned cursor, and then returns completed items. Releasing Shift or changing source slots stops pending work but retains history. Closing/changing containers clears it.
- Returns prefer the original source slot, then compatible partials and eligible empty slots in that inventory. Donors are resolved from the live layout, so sorting does not redirect a return to the wrong item. New forward movement of another item/direction replaces history; compatible movement in the same direction accumulates. Unexpected matching-item total changes invalidate the balance. History is a convenience, not a transaction log or a server acknowledgement.
- The **person/order icon beside player storage** sorts only main slots 9–35. Its shared mode icon selects Alphabetical or By Type, including when only the normal survival inventory is open. Conflicting operations and bundle extraction share the same lock; clicking the running sort icon cancels safely.
- Both sorts default to four validated clicks per tick (configurable 1–5), with a two-tick settling counter and a final verification. The planner avoids dismantling future slots already at their target when unfinished donors can supply the items. One pure-model fixture drops from six clicks to four; this is not a general in-game speed benchmark. Full inventories, component variants, bundles, and correction safeguards continue to use the shared planner.

Configurations without `scrollSensitivityVersion` migrate the previous default value of 8 to 2. Other legacy custom values are preserved; a legacy custom value of exactly 8 cannot be distinguished from the old default. Versioned settings, including a customized 8, retain their values.

Bundle names appear only in the popup title. Supplementary lore and other tooltip lines are retained without repeating the title; tooltips for contained items are unchanged.

See [interaction verification and manual checklist](docs/verification.md) for API details, limitations, and checks still needed on real servers and trackpads.


---

## How it works

### Main classes

| Class | Role |
|---|---|
| `StorageHelperClient` | Client entrypoint. Loads config, registers the chest-click listener and the screen-init hook. |
| `chest.ChestDetector` | Recognizes vanilla chest screens and placed `ShulkerBoxScreen`s, leaving unrelated containers alone. |
| `chest.ChestSession` / `ChestSessions` | One session per supported container screen: builds the widgets, owns the search state, runs the active operation each screen tick, and filters keyboard/mouse input. Closing the screen discards the session, so the query resets per container. |
| `chest.op.DepositOperation` | Deposit Matching as a queue of shift-clicks. |
| `chest.op.SortOperation` | Sort as a stream of single left-clicks decided tick by tick. |
| `chest.McStackType`, `ChestSnapshots`, `ItemFactsFactory`, `ClickOverrideDetector` | Adapters from live `ItemStack`s/container menus to the game-agnostic planning model. Container and player slot boundaries come from the live menu layout. |
| `logic.SortPlanner`, `ClickSimulator`, `DepositPlanner`, `ItemClassifier`, `SearchMatcher`, `SortMode` | Pure logic with no Minecraft dependency; fully unit-tested. |
| `gui.SlotHighlighter` + `mixin.AbstractContainerScreenMixin` | Draws the search outline/dim right after each slot's item is drawn (the only injecting mixin). `AbstractContainerScreenAccessor` just reads the GUI rectangle for panel placement. |

### Deposit logic

1. On click the chest's item **registry ids** are snapshotted. Every player slot (main inventory + hotbar, which are the only player slots a chest menu exposes) whose item id is in that set is queued.
2. Each tick, up to `clicksPerTick` queued slots are **re-read**. A slot is skipped if it changed or if the chest has no room for that exact stack (no same-components partial stack and no empty slot). Otherwise it is **shift-clicked** (`QUICK_MOVE`), which is vanilla's own "fill compatible partial stacks first, then empty slots, leave the rest" behaviour, validated by the server. Components are never touched; only vanilla's stacking rules decide what merges.
3. When the queue is empty the number of items that left your inventory is reported ("Deposited N items", "No room in the chest", or "Nothing to deposit").

### Sort logic

1. The chest contents are reduced to `(type, count)` pairs where *type* means item **plus all components** (vanilla's `isSameItemSameComponents`), so a renamed or enchanted stack never merges with a plain one.
2. A **target layout** is computed: totals per type → full stacks plus at most one remainder → types ordered by the chosen mode → packed left-to-right with empty slots last. Alphabetical compares the displayed name case-insensitively, then the exact name, registry id and a component tie-breaker, so the result is deterministic. By Type orders by category first: tools & weapons, armor, ores & minerals, food & drinks, blocks, redstone & mechanisms, misc. Categories come from data components (`tool`, `weapon`, `equippable`, `food`, `consumable`), item tags (vanilla and `c:` convention tags when present) and registry-id keywords as a fallback for modded items. Ore blocks count as ores; redstone dust/blocks and mechanisms count as redstone even though they are blocks.
3. The planner is **stateless**: each tick it re-reads the live slots and cursor and derives the next left-click. Positions are finalised left to right. For position *p*: if the cursor holds the right type, click *p* (place, top up, or swap out whatever is there); if the cursor is empty, pick up a donor stack of the right type from a later slot; if the cursor holds something else, click a donor slot so vanilla swaps them. Every click either fills *p* further or completes it, so it terminates, **needs no free slot in the chest or your inventory**, consolidates partial stacks as a side effect, and ends with an empty cursor because the target contains exactly the chest's items. A simulator models vanilla's left-click rules and is used both in tests and as a no-op guard at runtime.
4. **Bundles** (and any modded item that overrides click-on-stack behaviour) are special: left-clicking them onto an item puts the item inside the bundle. The planner never clicks such a stack onto an occupied slot or vice versa; it parks the cursor in an empty slot first. For a *completely full* chest that contains bundles it first consolidates ordinary partial stacks to free a slot, and if that is impossible it refuses up front ("Bundles need one empty chest slot to sort").

### Search logic

The normalised query is matched as a substring of each chest stack's displayed name and of its registry id (`minecraft:ender_pearl`). Matches are recomputed every tick and on every keystroke; the mixin only reads a boolean per slot and draws. Nothing is moved, hidden or filtered. Player-inventory slots are never highlighted.

### Synchronisation and safety

* All clicks go through `MultiPlayerGameMode.handleContainerInput` with the active menu's container id, the same path as a real mouse click, so the server validates each one and the client applies its usual prediction. No packets are forged and the server needs no mod.
* Pacing is tick-based: default **1 click per tick** (configurable up to 5), never a burst.
* Before every click the live slot/cursor state is re-read. Deposit skips slots that changed; Sort derives each click from the live state and, if the contents no longer add up to the target (another player added or removed items, a server correction arrived), recomputes the target (bounded number of times) or stops with a message. After the layout matches, Sort waits a few ticks and **re-verifies** so late server corrections are caught.
* Operations require an empty cursor to start, block slot clicks / hotbar keys while running, cannot be started twice, and each completes with an empty cursor.
* **Interrupted operations:** pressing **Cancel** sets a flag; Sort first returns the stack on the cursor to the chest (same-type partial stack, else an empty slot, which always exists while the cursor holds chest items) and only then stops. Deposit stops before its next click. If the screen is closed mid-operation (Esc, `E`, death, server closing the menu, container replaced), the session is discarded and no further clicks are sent; whatever is on the cursor is handled by vanilla's own container-close logic (returned to your inventory). Nothing is ever clicked "outside" the window, so nothing is dropped.
* **Full-chest sorting:** handled by the cursor-swap strategy above; no empty slot is needed except for the bundle case described earlier.
* Item counts and components are preserved by construction: every move is a vanilla pick-up, place, merge or swap on the server.

---

## Testing

### Automated (run on every build)

`./gradlew test` – 50 JUnit 5 tests over the pure logic, including a 600-case randomized sort fuzz (single and double chests, full and partly empty, mixed stack sizes, unstackables with differing components, bundles) that checks: termination within a click bound, no no-op clicks, no bundle-vs-occupied click, item totals unchanged, cursor empty at the end, final layout equal to the target; plus deposit candidate/room rules, category precedence, search matching, ordering determinism, fractional scrolling, bounded queues, and restriction-aware batch destination planning.

What the automated tests **do not** cover: anything that needs the game (rendering, input routing, real server validation, timing).

### Automated in-game test (drives the real game)

`src/gametest` contains a Fabric **client gametest** (`StorageHelperGameTest`). It launches the actual Minecraft 26.3 client, creates a flat world, places and fills a chest with commands, right-clicks the chest like a player, and then:

1. checks the panel attached (this also proves both mixins applied, since the panel layout reads the GUI position through the accessor and the chest screen class is loaded);
2. presses **Deposit Matching** and verifies the *server-side* chest afterwards (cobblestone 40+40+64 → 64/64/16 with partial stacks topped up first, dirt merged, the non-matching stick left in the hotbar);
3. presses **Sort** in A-Z and then By Type and verifies the exact server-side slot layout and unchanged totals;
4. clicks into the search box, types `COBB`, verifies three highlighted slots, then presses the hotbar-1 key and the inventory key while hovering a chest slot and verifies nothing moved and the chest stayed open;
5. verifies Esc unfocuses first, the clear button resets the search, a second Esc closes the chest, and item totals are intact.

Screenshots are written to `build/run/clientGameTest/screenshots/`.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./gradlew runClientGameTest -PclientGameTests=true   # opens a game window; takes about a minute
```

It is opt-in (`-PclientGameTests=true`) so a normal `./gradlew build` never launches the game.

**Result on 2026-11-11 (macOS, JDK 25, Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3):** the game test passed end to end. Server-side chest contents matched the expected layouts after deposit, A-Z sort and By Type sort; the panel, highlight and status labels rendered as expected in the captured screenshots.

What is **not** covered automatically: multiplayer with a second player editing the chest, servers with inventory restrictions, GUI-scale/resize behaviour, bundles in-game, and visual polish. Please run through the checklist below for those.

### In-game checklist

Deposit
- [ ] Chest with a partial cobblestone stack, 2 cobblestone stacks in inventory → chest stack tops up first, then empty slots used.
- [ ] Chest with only a *full* cobblestone stack + empty slots → inventory cobblestone still deposited into empty slots.
- [ ] Chest full for that type → nothing moves, message "No room in the chest".
- [ ] Mixed inventory → non-matching items untouched; hotbar matches included; armor/offhand untouched.
- [ ] Renamed/enchanted/damaged variants: deposited (same id) but never merged into a plain stack; components intact afterwards.
- [ ] Overflow stays in inventory and is reported correctly.
- [ ] Item on cursor → refuses with message. Click twice quickly → only one run.

Sort
- [ ] A-Z and By Type on single and double chests, partially empty and completely full (no empty slot).
- [ ] Partial stacks consolidated; empties at the end; close and reopen: order persists.
- [ ] Unstackable items (tools, armor) with different enchantments/names/durability: all kept, deterministic order; counts/components unchanged (compare before/after screenshots or use F3+H).
- [ ] Player inventory never changes.
- [ ] Chest containing bundles with ≥1 empty slot sorts; completely full chest with bundles and nothing to consolidate is refused with the bundle message.
- [ ] Cancel mid-sort → cursor ends empty, items all still in chest.
- [ ] Close the chest (Esc) mid-sort → no item loss; held stack returned to inventory by vanilla.
- [ ] Multiplayer: second player adds/removes items during a sort → operation re-plans or stops with "Chest kept changing", never duplicates/deletes.
- [ ] Server with inventory restrictions (e.g. a protected chest): clicks are rejected by the server; mod stops cleanly.

Search
- [ ] Mixed case, partial names ("cobb"), registry ids ("ender_pearl", "minecraft:"), custom names.
- [ ] Empty query → normal rendering; no matches → "No matches"; counter correct.
- [ ] Highlight keeps item and count readable; player inventory not highlighted; The cross icon clears.
- [ ] Typing digits/E/Q while focused does nothing to the inventory; Esc/Enter unfocus; second Esc closes.
- [ ] Open a different chest → query is empty.

Interface
- [ ] GUI scale 1–4 and Auto, window resize/fullscreen toggle: panel stays visible and re-positions (right, then left, then edge).
- [ ] Normal clicks, shift-clicks, drag-splitting and tooltips still work when nothing is running; blocked while an operation runs.
- [ ] Panel absent on furnace, crafting table, barrel, ender chest, shulker box, villager, anvil and plugin menus.

---

## License

MIT – see `LICENSE`.
