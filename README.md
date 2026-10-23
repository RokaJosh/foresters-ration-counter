# Forester's Ration Counter — total usable rations (v7)

## Changes in v7

- Fixes false zero alerts when depositing loose rations into the Forestry kit. While the inventory stack disappears and the kit interface is still updating, the infobox retains the previous total. A stable kit interface reading replaces the held count; a short five-tick fallback prevents the hold from lasting indefinitely.
- Empty warnings are checked only once per game tick and need two consecutive zero readings. They only fire after a positive total has previously been observed in the current session. Game chat and optional RuneLite notification are preserved.
- All v6 features and saved config keys are retained.

## Changes in v6

- The top-left infobox shows **kit contents + loose inventory rations** rather than just the kit contents. The live inventory stack is read from the game each tick and on inventory changes.
- Wearing the Forestry kit or Forestry basket counts as carrying it; having one in inventory also counts.
- The infobox disappears if you have **neither a kit/basket nor loose Forester's rations** in your inventory. It also stays hidden if a kit exists but its contents have not yet been determined; open **View** to sync.
- When the total transitions from a known positive value to **0**, the plugin always adds a **game chat message**, and optionally sends the existing standard RuneLite client notification (controlled by **Notify when empty**).
- Debug settings remain removed. The interface auto-sync still uses two stable readings to avoid the transient `1`.

## First use and upgrading from v5

Extract the ZIP, open the folder in IntelliJ as a Gradle project, reload Gradle and run the `run` task. Set your Gradle JVM to Java 17 or later.

**Set current rations now means KIT RATIONS ONLY.** The inventory stack is detected and added automatically. The previous saved number from v5 is retained because it represented your kit balance when it was synced. If you had manually entered a combined total in v5, open the Forestry kit to resync it before relying on v6.

## Tracking caveats

Kit quantities are read directly only when the Forestry kit interface is open. Between reads, the kit balance is estimated from known consumption messages. Inventory rations are read live. Consumption messages and inventory updates can arrive at different points in a game tick; the plugin tries to avoid double-subtraction by reconciling them on the following tick. For the most accurate kit balance after withdrawing/depositing, reopen **View** to sync.

The game-chat warning is always enabled; **Notify when empty** only controls the RuneLite desktop/client notification.

This is a development plugin source project, not an officially published Plugin Hub plugin. Changes have not been tested in-game here.
