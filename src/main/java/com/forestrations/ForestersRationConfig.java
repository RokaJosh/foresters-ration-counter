package com.forestrations;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ForestersRationConfig.GROUP)
public interface ForestersRationConfig extends Config
{
    String GROUP = "forestersrationcounter";

    @ConfigItem(
        keyName = "setCount",
        name = "Set current rations",
        description = "Enter the number stored inside the Forestry kit only (inventory is counted automatically). Set -1 if unknown.",
        position = 0
    )
    default int setCount()
    {
        return -1;
    }

    @ConfigItem(
        keyName = "autoSync",
        name = "Sync when kit opens",
        description = "Read the visible Forestry kit interface each game tick and update the ration count when a reliable number is found.",
        position = 1
    )
    default boolean autoSync()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showAtZero",
        name = "Show when empty",
        description = "Keep the infobox visible at zero.",
        position = 2
    )
    default boolean showAtZero()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notifyAtZero",
        name = "Notify when empty",
        description = "Send a RuneLite notification when total rations reach zero (a game chat message is always shown).",
        position = 3
    )
    default boolean notifyAtZero()
    {
        return true;
    }
}
