package ashjack.simukraftreloaded.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Kind;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Source;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.TownMarket;
import ashjack.simukraftreloaded.core.town.Towns;

/**
 * Buying from and selling to a town at its market (the bank). You can only buy what the town actually has, taken out of
 * its chests (the warehouse first, then its mines, leftovers and factories - a factory's goods are bought from it
 * first); what you sell goes into its warehouse, if there's room, for nine-tenths of the price. Your wallet pays and is
 * paid; the treasury is the other side of every trade.
 */
public final class MarketActions
{
    private MarketActions() {}

    public static final float SELL_SHARE = 0.9f;

    static void register()
    {
        ServerActions.register("marketBuy", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Town town = Towns.at(ActionHandlers.getV3(data, "atm"));
                Item item = (Item) Item.itemRegistry.getObject(data.getString("item"));
                int qty = Math.max(1, Math.min(576, data.getInteger("qty")));
                if (town == null || item == null)
                {
                    ActionHandlers.tell(player, "This bank isn't in a town");
                    return;
                }
                TownMarket market = TownMarket.of(town);
                float each = market.price(new ItemStack(item)), total = each * qty;
                String name = player.getCommandSenderName();
                if (Economy.wallet(name) < total)
                {
                    ActionHandlers.tell(player, qty + " would cost " + SimukraftReloaded.displayMoney(total) + " - you have "
                            + SimukraftReloaded.displayMoney(Economy.wallet(name)));
                    return;
                }
                World world = player.worldObj;
                List<Source> storage = TownMarket.storage(town, world);
                // the warehouse's own chests first
                List<Source> order = new ArrayList<Source>();
                for (Source s : storage)
                {
                    if (s.kind == Kind.WAREHOUSE)
                    {
                        order.add(s);
                    }
                }
                for (Source s : storage)
                {
                    if (s.kind != Kind.WAREHOUSE)
                    {
                        order.add(s);
                    }
                }
                int got = 0;
                for (Source s : order)
                {
                    if (got >= qty || !s.offers(item))
                    {
                        continue;
                    }
                    for (ItemStack is : WarehouseStock.take(s.chests, item, qty - got))
                    {
                        if (s.kind == Kind.FACTORY)
                        {
                            Economy.townBuys(s.at, is); // it's the factory's until the town buys it
                        }
                        got += is.stackSize;
                        if (!player.inventory.addItemStackToInventory(is))
                        {
                            player.dropPlayerItemWithRandomChoice(is, false);
                        }
                    }
                }
                if (got == 0)
                {
                    ActionHandlers.tell(player, town.name + " hasn't any " + new ItemStack(item).getDisplayName() + " to sell");
                    return;
                }
                float paid = each * got;
                Economy.addToWallet(name, -paid);
                Economy.earn(town, paid);
                market.want(item, got);
                market.count(town);
                player.inventoryContainer.detectAndSendChanges();
                ActionHandlers.tell(player, "Bought " + got + " " + new ItemStack(item).getDisplayName() + " for "
                        + SimukraftReloaded.displayMoney(paid) + (got < qty ? " (all " + town.name + " had)" : ""));
            }
        });

        ServerActions.register("marketSell", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Town town = Towns.at(ActionHandlers.getV3(data, "atm"));
                Item item = (Item) Item.itemRegistry.getObject(data.getString("item"));
                int qty = Math.max(1, Math.min(2304, data.getInteger("qty")));
                // just the one slot (the sell tab's click), or any of the item, slot by slot
                int onlySlot = data.hasKey("slot") ? data.getInteger("slot") : -1;
                if (town == null || item == null)
                {
                    return;
                }
                TownMarket market = TownMarket.of(town);
                ItemStack sample = new ItemStack(item);
                List<IInventory> warehouse = new ArrayList<IInventory>();
                for (Source s : TownMarket.storage(town, player.worldObj))
                {
                    if (s.kind == Kind.WAREHOUSE)
                    {
                        warehouse.addAll(s.chests);
                    }
                }
                if (warehouse.isEmpty())
                {
                    ActionHandlers.tell(player, town.name + " needs a warehouse to buy things into");
                    return;
                }
                int sold = 0;
                float paid = 0;
                for (int slot = 0; slot < player.inventory.mainInventory.length && sold < qty; slot++)
                {
                    ItemStack is = player.inventory.mainInventory[slot];
                    if (is == null || is.getItem() != item || onlySlot >= 0 && slot != onlySlot)
                    {
                        continue;
                    }
                    sample = is;
                    int n = Math.min(is.stackSize, qty - sold);
                    // as many as the treasury can pay for, at the falling price
                    while (n > 0 && paid + market.sellValueAfter(is, sold, n) > Economy.balance(town))
                    {
                        n--;
                    }
                    if (n <= 0)
                    {
                        break;
                    }
                    ItemStack part = is.splitStack(n);
                    ItemStack left = WarehouseStock.put(warehouse, part);
                    int in = n - (left == null ? 0 : left.stackSize);
                    if (left != null)
                    {
                        is.stackSize += left.stackSize; // didn't fit
                    }
                    if (is.stackSize <= 0)
                    {
                        player.inventory.mainInventory[slot] = null;
                    }
                    paid += market.sellValueAfter(is, sold, in);
                    sold += in;
                    if (left != null)
                    {
                        break; // the warehouse is full
                    }
                }
                if (sold == 0)
                {
                    ActionHandlers.tell(player, Economy.balance(town) < market.sellValue(sample, 1) ? town.name + " can't afford to buy right now"
                            : "Nothing sold - is the warehouse full?");
                    return;
                }
                Economy.spend(town, paid);
                Economy.addToWallet(player.getCommandSenderName(), paid);
                market.count(town);
                player.inventoryContainer.detectAndSendChanges();
                ActionHandlers.tell(player, "Sold " + sold + " " + new ItemStack(item).getDisplayName() + " to " + town.name
                        + " for " + SimukraftReloaded.displayMoney(paid));
            }
        });
    }
}
