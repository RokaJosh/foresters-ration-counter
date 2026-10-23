package com.forestrations;

import com.google.inject.Provides;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.Counter;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "Forester's Ration Counter",
    description = "Show total available Forester's rations in your kit and inventory",
    tags = {"forestry", "woodcutting", "rations", "infobox"}
)
public class ForestersRationPlugin extends Plugin
{
    private static final String KEY_REMAINING = "remaining"; // Kept for backwards compatibility with v5.
    private static final int FORESTRY_KIT = 28136;
    private static final int FORESTRY_BASKET = 28143;
    private static final int REQUIRED_STABLE_TICKS = 2;
    // Allow the kit interface time to catch up with an inventory-to-kit transfer.
    private static final int TRANSFER_GRACE_TICKS = 5;
    // Zero is only considered genuine once it persists across game ticks.
    private static final int ZERO_CONFIRM_TICKS = 2;

    private final ForestryKitReader reader = new ForestryKitReader();
    private Integer pendingSync;
    private int pendingSyncTicks;

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private ForestersRationConfig config;
    @Inject private InfoBoxManager infoBoxManager;
    @Inject private ItemManager itemManager;
    @Inject private Notifier notifier;

    // Count in the kit only. Inventory count is taken directly from the game, not saved.
    private int kitRemaining = -1;
    private int inventoryRations;
    private boolean kitPresent;
    private boolean inventoryReady;
    private int pendingConsumptions;
    private int inventoryAtConsumption;
    private Integer lastKnownTotal;
    private Counter counter;
    private int transferGraceTicks;
    private Integer heldTransferTotal;
    private int zeroCandidateTicks;
    private boolean depletionArmed;

    @Provides
    ForestersRationConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(ForestersRationConfig.class);
    }

    @Override
    protected void startUp()
    {
        String stored = configManager.getConfiguration(ForestersRationConfig.GROUP, KEY_REMAINING);
        try
        {
            kitRemaining = stored == null ? Math.max(-1, config.setCount()) : Math.max(-1, Integer.parseInt(stored));
        }
        catch (NumberFormatException ignored)
        {
            kitRemaining = -1;
        }
        readContainers();
        refreshDisplay();
    }

    @Override
    protected void shutDown()
    {
        removeInfobox();
        lastKnownTotal = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!ForestersRationConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("setCount".equals(event.getKey()))
        {
            setKitCount(Math.max(-1, config.setCount()));
        }
        else if ("showAtZero".equals(event.getKey()))
        {
            refreshDisplay();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()
            || event.getContainerId() == InventoryID.EQUIPMENT.getId())
        {
            int previousInventory = inventoryRations;
            readContainers();
            // The loose stack disappears before the kit widget quantity updates.
            // Hold the last reliable total while a kit deposit is in flight.
            if (inventoryReady && kitPresent && inventoryRations < previousInventory
                && pendingConsumptions == 0 && lastKnownTotal != null
                && reader.read(client).kitOpen)
            {
                heldTransferTotal = lastKnownTotal;
                transferGraceTicks = TRANSFER_GRACE_TICKS;
                zeroCandidateTicks = 0;
            }
            refreshDisplay();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Re-read containers once per tick as a fallback for equipment changes and load transitions.
        readContainers();

        if (config.autoSync())
        {
            ForestryKitReader.Reading reading = reader.read(client);
            if (!reading.kitOpen || reading.rations == null)
            {
                resetPendingSync();
            }
            else
            {
                if (reading.rations.equals(pendingSync))
                {
                    pendingSyncTicks++;
                }
                else
                {
                    pendingSync = reading.rations;
                    pendingSyncTicks = 1;
                }
                if (pendingSyncTicks >= REQUIRED_STABLE_TICKS && reading.rations != kitRemaining)
                {
                    // Reading the kit is authoritative and overrides any interim estimate.
                    kitRemaining = reading.rations;
                    saveKitCount();
                    pendingConsumptions = 0;
                    // Stable widget data is authoritative after a transfer.
                    clearTransferHold();
                }
            }
        }

        if (pendingConsumptions > 0)
        {
            // Inventory stacks may update after the consumption chat message. Avoid subtracting
            // a ration from both the inventory AND the estimated kit balance.
            int usedFromInventory = Math.min(pendingConsumptions,
                Math.max(0, inventoryAtConsumption - inventoryRations));
            int usedFromKit = pendingConsumptions - usedFromInventory;
            if (kitRemaining >= 0 && usedFromKit > 0)
            {
                kitRemaining = Math.max(0, kitRemaining - usedFromKit);
                saveKitCount();
            }
            pendingConsumptions = 0;
        }
        if (transferGraceTicks > 0 && --transferGraceTicks == 0)
        {
            clearTransferHold();
        }
        refreshDisplay();
        confirmZeroOnTick();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            resetPendingSync();
            inventoryReady = false;
            kitPresent = false;
            pendingConsumptions = 0;
            lastKnownTotal = null;
            clearTransferHold();
            zeroCandidateTicks = 0;
            depletionArmed = false;
            removeInfobox();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        String message = Text.removeTags(event.getMessage()).replace('\u2019', '\'').toLowerCase(Locale.ROOT);
        boolean consumed = message.contains("you consume a forester's ration")
            || message.contains("the forester's ration restores some of your energy");
        if (consumed && inventoryReady)
        {
            if (pendingConsumptions == 0)
            {
                inventoryAtConsumption = inventoryRations;
            }
            pendingConsumptions++;
        }
    }

    private void readContainers()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            inventoryReady = false;
            kitPresent = false;
            return;
        }
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        ItemContainer equipped = client.getItemContainer(InventoryID.EQUIPMENT);
        if (inv == null || equipped == null)
        {
            return;
        }
        inventoryReady = true;
        inventoryRations = quantity(inv, ItemID.FORESTERS_RATION);
        kitPresent = containsKit(inv) || containsKit(equipped);
    }

    private static boolean containsKit(ItemContainer container)
    {
        for (Item item : container.getItems())
        {
            if (item != null && (item.getId() == FORESTRY_KIT || item.getId() == FORESTRY_BASKET))
            {
                return true;
            }
        }
        return false;
    }

    private static int quantity(ItemContainer container, int itemId)
    {
        int total = 0;
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() == itemId)
            {
                total += Math.max(0, item.getQuantity());
            }
        }
        return total;
    }

    private void setKitCount(int count)
    {
        kitRemaining = count;
        saveKitCount();
        refreshDisplay();
    }

    private void saveKitCount()
    {
        configManager.setConfiguration(ForestersRationConfig.GROUP, KEY_REMAINING, Integer.toString(kitRemaining));
    }

    private void resetPendingSync()
    {
        pendingSync = null;
        pendingSyncTicks = 0;
    }

    private void clearTransferHold()
    {
        heldTransferTotal = null;
        transferGraceTicks = 0;
    }

    private void confirmZeroOnTick()
    {
        // Do not alert on an intermediate 0 during a kit transfer or widget sync.
        if (heldTransferTotal != null || lastKnownTotal == null || lastKnownTotal > 0)
        {
            zeroCandidateTicks = 0;
            return;
        }
        if (++zeroCandidateTicks >= ZERO_CONFIRM_TICKS && depletionArmed)
        {
            depletionArmed = false;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "You have run out of Forester's rations!", null);
            if (config.notifyAtZero())
            {
                notifier.notify("You have run out of Forester's rations!");
            }
        }
    }

    private void refreshDisplay()
    {
        // Hide when there is neither a kit/basket nor loose rations, even if an old stored
        // balance exists. Do not show a stored kit balance when the kit is absent.
        if (!inventoryReady || (!kitPresent && inventoryRations == 0))
        {
            lastKnownTotal = null;
            removeInfobox();
            return;
        }

        // Without the kit we can show the exact loose stack. With a kit whose balance
        // is unknown, never misrepresent inventory-only rations as the complete total.
        if (kitPresent && kitRemaining < 0)
        {
            lastKnownTotal = null;
            removeInfobox();
            return;
        }
        int total = inventoryRations + (kitPresent ? kitRemaining : 0);
        if (heldTransferTotal != null)
        {
            total = heldTransferTotal;
        }
        if (total > 0)
        {
            zeroCandidateTicks = 0;
            depletionArmed = true;
        }
        lastKnownTotal = total;

        if (total == 0 && !config.showAtZero())
        {
            removeInfobox();
            return;
        }
        if (counter == null)
        {
            counter = new Counter(itemManager.getImage(ItemID.FORESTERS_RATION), this, total);
            counter.setTooltip("Usable Forester's rations (Forestry kit + inventory)");
            infoBoxManager.addInfoBox(counter);
        }
        else
        {
            counter.setCount(total);
        }
    }

    private void removeInfobox()
    {
        if (counter != null)
        {
            infoBoxManager.removeInfoBox(counter);
            counter = null;
        }
    }
}
