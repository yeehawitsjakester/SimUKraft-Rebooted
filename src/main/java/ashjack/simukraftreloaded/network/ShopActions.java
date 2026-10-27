package ashjack.simukraftreloaded.network;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import ashjack.simukraftreloaded.common.Effects;
import ashjack.simukraftreloaded.common.PricesForBlocks;
import ashjack.simukraftreloaded.common.jobs.Job;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.proxies.CommonProxy.Commodity;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Server side of the bank ATM and the builder's merchant. The old GUIs changed credits and inventories on the
 * client (selling from the client's copy of the inventory left the real items in place: free money).
 */
public final class ShopActions
{
    private ShopActions() {}

    /** what the bank pays for one of an item, or 0 if it doesn't buy it */
    public static float bankPrice(ItemStack is)
    {
        if (is == null)
        {
            return 0f;
        }
        Item item = is.getItem();
        if (item == Items.diamond) return PricesForBlocks.bankPriceDiamond;
        if (item == Items.emerald) return PricesForBlocks.bankPriceEmerald;
        if (item == Items.redstone) return PricesForBlocks.bankPriceRedstone;
        if (item == Items.glowstone_dust) return PricesForBlocks.bankPriceGlowstone;
        if (item == Items.gold_ingot) return PricesForBlocks.bankPriceGold;
        return 0f;
    }

    /** the bank's vault blocks have been mined away */
    public static boolean bankRobbed(V3 atm)
    {
        return Job.findClosestBlocks(atm, Blocks.diamond_block, 10).isEmpty()
                || Job.findClosestBlocks(atm, Blocks.emerald_block, 10).isEmpty()
                || Job.findClosestBlocks(atm, Blocks.gold_block, 10).isEmpty();
    }

    /** the 9 builder's merchant goods, in the order of the merchant screen */
    static final Block[] MERCHANT_GOODS = {Blocks.planks, Blocks.log, Blocks.cobblestone, Blocks.stone, Blocks.glass,
        Blocks.wool, Blocks.brick_block, Blocks.stonebrick, Blocks.fence};

    static void register()
    {
        // sell one, or the whole stack, from an inventory slot
        ServerActions.register("bankSell", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 atm = ActionHandlers.getV3(data, "atm");
                int slot = data.getInteger("slot");
                if (atm == null || slot < 0 || slot >= player.inventory.getSizeInventory())
                {
                    return;
                }
                if (bankRobbed(atm))
                {
                    ActionHandlers.tell(player, "Looks like you've robbed the bank! Replace the items and we'll let you use this ATM.");
                    return;
                }
                ItemStack is = player.inventory.getStackInSlot(slot);
                float each = bankPrice(is);
                if (each <= 0f)
                {
                    return;
                }
                int count = data.getBoolean("all") ? is.stackSize : 1;
                Economy.earn(atm, each * count);
                player.inventory.decrStackSize(slot, count);
                player.inventoryContainer.detectAndSendChanges();
                Effects.sound(player.worldObj, player.posX, player.posY, player.posZ, "ashjacksimukraftreloaded:cashshort");
            }
        });

        // buy commodities: a list of {item, damage, qty}
        ServerActions.register("bankBuy", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 atm = ActionHandlers.getV3(data, "atm");
                if (atm == null || bankRobbed(atm))
                {
                    return;
                }
                NBTTagList cart = data.getTagList("cart", 10);
                float cost = 0f;
                ArrayList<Commodity> bought = new ArrayList<Commodity>();
                ArrayList<Integer> qtys = new ArrayList<Integer>();
                for (int i = 0; i < cart.tagCount(); i++)
                {
                    NBTTagCompound c = cart.getCompoundTagAt(i);
                    Commodity comm = findCommodity(c.getString("item"), c.getInteger("damage"));
                    int qty = c.getInteger("qty");
                    if (comm == null || qty < 1 || qty > comm.quantity)
                    {
                        continue;
                    }
                    cost += qty * comm.priceEach;
                    bought.add(comm);
                    qtys.add(qty);
                }
                if (bought.isEmpty())
                {
                    return;
                }
                if (cost > Economy.balance(atm))
                {
                    ActionHandlers.tell(player, "The cost is " + SimukraftReloaded.displayMoney(cost) + ", but you only have "
                            + SimukraftReloaded.displayMoney(Economy.balance(atm)));
                    return;
                }
                for (int i = 0; i < bought.size(); i++)
                {
                    Commodity comm = bought.get(i);
                    ItemStack is = comm.theItemStack.copy();
                    is.stackSize = qtys.get(i);
                    if (!player.inventory.addItemStackToInventory(is))
                    {
                        player.dropPlayerItemWithRandomChoice(is, false);
                    }
                    SimukraftReloaded.theCommodities.remove(comm);
                }
                player.inventoryContainer.detectAndSendChanges();
                Economy.spend(atm, cost);
                ActionHandlers.tell(player, "Bought commodities worth " + SimukraftReloaded.displayMoney(cost));
                Effects.sound(player.worldObj, player.posX, player.posY, player.posZ, "ashjacksimukraftreloaded:cash");
            }
        });

        // builder's merchant: stacks of 64 into the chest next to the player
        ServerActions.register("merchantBuy", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 here = new V3(player.posX, player.posY, player.posZ, player.dimension); // paid by the town it's in
                int[] quantities = data.getIntArray("qty");
                ArrayList<IInventory> chests = Job.inventoriesFindClosest(new V3(player.posX, player.posY, player.posZ, player.dimension), 5);
                if (chests == null || chests.isEmpty())
                {
                    ActionHandlers.tell(player, "Merchant: Please place a chest down here, and I will place your items in there.");
                    return;
                }
                float total = 0f;
                for (int i = 0; i < MERCHANT_GOODS.length && i < quantities.length; i++)
                {
                    total += Math.max(0, quantities[i]) * PricesForBlocks.getPrice(MERCHANT_GOODS[i], true);
                }
                if (total <= 0f)
                {
                    return;
                }
                if (Economy.balance(here) < total)
                {
                    ActionHandlers.tell(player, "Merchant: 'Sorry, your card has been declined, you could try buying less.'");
                    return;
                }
                for (int i = 0; i < MERCHANT_GOODS.length && i < quantities.length; i++)
                {
                    for (int c = 0; c < quantities[i]; c++)
                    {
                        placeIntoChest(chests.get(0), new ItemStack(MERCHANT_GOODS[i], 64), 0, 64);
                        Economy.spend(here, PricesForBlocks.getPrice(MERCHANT_GOODS[i], true));
                    }
                    if (quantities[i] > 0)
                    {
                        PricesForBlocks.adjustPrice(MERCHANT_GOODS[i], true);
                    }
                }
                Effects.sound(player.worldObj, player.posX, player.posY, player.posZ, "ashjacksimukraftreloaded:cash");
                Effects.sound(player.worldObj, player.posX, player.posY, player.posZ, "ashjacksimukraftreloaded:merchm");
            }
        });

        // builder's merchant buys every full stack of 64 it deals in from the chest next to the player
        ServerActions.register("merchantSell", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 here = new V3(player.posX, player.posY, player.posZ, player.dimension); // paid to the town it's in
                ArrayList<IInventory> chests = Job.inventoriesFindClosest(new V3(player.posX, player.posY, player.posZ, player.dimension), 5);
                if (chests == null || chests.isEmpty())
                {
                    ActionHandlers.tell(player, "Merchant: Please place a chest down here, and place stacks of 64 blocks in there.");
                    return;
                }
                IInventory chest = chests.get(0);
                float total = 0f;
                for (int g = 0; g < chest.getSizeInventory(); g++)
                {
                    ItemStack is = chest.getStackInSlot(g);
                    if (is == null || is.stackSize != 64)
                    {
                        continue;
                    }
                    Block block = Block.getBlockFromItem(is.getItem());
                    float price = PricesForBlocks.getPrice(block, false);
                    if (price > 0)
                    {
                        Economy.earn(here, price);
                        PricesForBlocks.adjustPrice(block, false); // the old code passed a null block here
                        total += price;
                        chest.setInventorySlotContents(g, null);
                    }
                }
                if (total == 0f)
                {
                    ActionHandlers.tell(player, "Merchant: There were no valid stacks I want to buy from you in the chest?!");
                }
                else
                {
                    Effects.sound(player.worldObj, player.posX, player.posY, player.posZ, "ashjacksimukraftreloaded:cash");
                    ActionHandlers.tell(player, "Sold all valid stacks for a total of " + SimukraftReloaded.displayMoney(total));
                }
            }
        });
    }

    private static Commodity findCommodity(String itemName, int damage)
    {
        for (Commodity c : SimukraftReloaded.theCommodities)
        {
            if (c.theItemStack != null && itemName.equals(Item.itemRegistry.getNameForObject(c.theItemStack.getItem()))
                    && c.theItemStack.getItemDamage() == damage)
            {
                return c;
            }
        }
        return null;
    }

    private static boolean placeIntoChest(IInventory chest, ItemStack stack, int idmeta, int quantity)
    {
        boolean placedOK = false;
        for (int q = 1; q <= quantity; q++)
        {
            for (int g = 0; g < chest.getSizeInventory(); g++)
            {
                ItemStack is = chest.getStackInSlot(g);
                if (is == null)
                {
                    chest.setInventorySlotContents(g, new ItemStack(stack.getItem(), 1, idmeta));
                    placedOK = true;
                    break;
                }
                else if (is.getItem() == stack.getItem() && is.getItemDamage() == idmeta && is.stackSize < 64)
                {
                    is.stackSize++;
                    chest.setInventorySlotContents(g, is);
                    placedOK = true;
                    break;
                }
            }
        }
        return placedOK;
    }
}
