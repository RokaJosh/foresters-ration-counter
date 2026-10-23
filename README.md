# Forester's Ration Counter

A RuneLite plugin that tracks the number of Forester's rations available to the player for use by a felling axe and displays the total in an infobox.

## Features

- Displays the total number of Forester's rations in the top-left infobox.
- Counts rations stored in the Forestry kit and the player's inventory.
- Automatically synchronises the stored ration count when viewing the Forestry kit.
- Tracks ration consumption while using a felling axe.
- Saves the tracked count between sessions.
- Displays a game chat message when rations run out.
- Supports optional RuneLite notifications when rations are depleted.
- Automatically hides the infobox when no Forestry kit or rations are carried or equipped.

## Configuration

| Setting | Description |
|---|---|
| Set current rations | Manually sets the number of rations stored in the Forestry kit. |
| Sync when kit opens | Automatically updates the stored ration count when viewing the Forestry kit. |
| Show when empty | Keeps the infobox visible when the ration count reaches zero. |
| Notify when empty | Sends a RuneLite notification when all available rations have been consumed. |

## Usage

The plugin tracks rations stored in the Forestry kit and any additional rations held in the player's inventory.

To initialise the counter, open the Forestry kit using the **View** option. The plugin will read the stored quantity and update the infobox.

Once synchronised, the counter updates as rations are consumed. Opening the Forestry kit again will refresh the stored quantity.

The tracked kit count can also be adjusted manually through the plugin configuration.