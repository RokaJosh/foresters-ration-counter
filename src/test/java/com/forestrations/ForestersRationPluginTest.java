package com.forestrations;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ForestersRationPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ForestersRationPlugin.class);
        RuneLite.main(args);
    }
}
