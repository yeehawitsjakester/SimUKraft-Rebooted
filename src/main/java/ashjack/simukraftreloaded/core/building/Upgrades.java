package ashjack.simukraftreloaded.core.building;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.jobs.Workshop;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.Loader;

/**
 * Upgrades bought at a building's control box, paid for by the town's treasury (and, for chests, with the metal from
 * the warehouse's own stock). Building.level says how far it's come.
 *
 *   Warehouse  level 2-4: every chest in it made an Iron Chests iron chest, then gold, then diamond (when that mod's
 *              in), its contents kept. Basements are dug by a builder instead (see Basement).
 *   Smithy     level 2: a better forge, for diamond swords.
 */
public final class Upgrades
{
    private Upgrades() {}

    public static final float SMITHY_COST = 500f, CHEST_FEE = 20f;

    public static boolean hasIronChests()
    {
        return ashjack.simukraftreloaded.core.registry.ModSupport.Feature.IRON_CHESTS.on();
    }

    public static boolean isWarehouse(Building b)
    {
        return b != null && b.displayName != null && b.displayName.contains("Warehouse");
    }

    /** the next chest upgrade's name, or null for none left */
    public static String nextChests(Building b)
    {
        switch (b.level)
        {
            case 1: return "Iron chests";
            case 2: return "Gold chests";
            case 3: return "Diamond chests";
            default: return null;
        }
    }

    /** what each chest takes to go up a tier: 8 iron, 8 gold, then 3 diamonds */
    private static ItemStack costPerChest(int level)
    {
        return level == 1 ? new ItemStack(Items.iron_ingot, 8) : level == 2 ? new ItemStack(Items.gold_ingot, 8) : new ItemStack(Items.diamond, 3);
    }

    /** every chest in the warehouse up a tier, as far as the metal in stock and the treasury go. Says how it went. */
    public static String upgradeChests(Building wh, World world)
    {
        if (!hasIronChests())
        {
            return "Better chests need the Iron Chests mod";
        }
        String next = nextChests(wh);
        if (next == null)
        {
            return "Its chests are already diamond";
        }
        Block iron = Block.getBlockFromName("IronChest:BlockIronChest");
        V3 box = wh.primaryXYZ;
        if (iron == null || world == null || box == null)
        {
            return "Couldn't find the Iron Chests' blocks";
        }
        int[] f = WarehouseStock.bounds(wh, box);
        List<int[]> todo = new ArrayList<int[]>();
        for (int x = f[0] - 1; x <= f[3] + 1; x++)
        {
            for (int y = f[1] - 1; y <= f[4] + 1; y++)
            {
                for (int z = f[2] - 1; z <= f[5] + 1; z++)
                {
                    Block b = world.getBlock(x, y, z);
                    boolean lower = wh.level == 1 ? b == Blocks.chest || b == Blocks.trapped_chest
                            : b == iron && world.getBlockMetadata(x, y, z) == wh.level - 2;
                    if (lower)
                    {
                        todo.add(new int[] {x, y, z});
                    }
                }
            }
        }
        if (todo.isEmpty())
        {
            wh.level++;
            wh.saveThisBuilding();
            return "There were no chests left to upgrade - it's on " + next.toLowerCase() + " now";
        }

        ItemStack cost = costPerChest(wh.level);
        ArrayList<IInventory> stock = WarehouseStock.chestsInside(wh, box, world);
        int metal = WarehouseStock.count(stock, cost.getItem());
        int affordable = Math.min(todo.size(), Math.min(metal / cost.stackSize, (int) (Economy.balance(box) / CHEST_FEE)));
        if (affordable <= 0)
        {
            return next + " take " + cost.stackSize + " " + cost.getDisplayName() + " each from the warehouse's stock (it has "
                    + metal + ") and " + SimukraftReloaded.displayMoney(CHEST_FEE) + " each from the treasury";
        }
        WarehouseStock.take(stock, cost.getItem(), affordable * cost.stackSize);
        Economy.spend(box, affordable * CHEST_FEE);
        int done = 0;
        for (int[] p : todo)
        {
            if (done >= affordable)
            {
                break;
            }
            if (replace(world, p[0], p[1], p[2], iron, wh.level - 1))
            {
                done++;
            }
        }
        boolean all = done == todo.size();
        if (all)
        {
            wh.level++;
            wh.saveThisBuilding();
        }
        SimukraftReloaded.log.info("Upgrades: " + done + " of " + todo.size() + " chests at " + box + " made " + next);
        return all ? "Every chest in the warehouse (" + done + ") is " + next.toLowerCase().replace(" chests", "") + " now"
                : done + " of the " + todo.size() + " chests upgraded - the rest need more " + cost.getDisplayName()
                  + " in stock or money in the treasury";
    }

    /** one chest swapped for an iron chest of this tier, its things moved across and facing the same way */
    private static boolean replace(World world, int x, int y, int z, Block iron, int meta)
    {
        TileEntity old = world.getTileEntity(x, y, z);
        if (!(old instanceof IInventory))
        {
            return false;
        }
        IInventory inv = (IInventory) old;
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (int i = 0; i < inv.getSizeInventory(); i++)
        {
            ItemStack is = inv.getStackInSlot(i);
            if (is != null)
            {
                items.add(is.copy());
                inv.setInventorySlotContents(i, null); // or breaking it spills them
            }
        }
        byte facing = facing(world, x, y, z, old);
        world.setBlock(x, y, z, iron, meta, 3);
        TileEntity now = world.getTileEntity(x, y, z);
        if (now != null)
        {
            try
            {
                now.getClass().getMethod("setFacing", byte.class).invoke(now, facing);
            }
            catch (Exception e)
            {
                // facing the default way
            }
        }
        if (now instanceof IInventory)
        {
            IInventory into = (IInventory) now;
            int slot = 0;
            for (ItemStack is : items)
            {
                while (slot < into.getSizeInventory() && into.getStackInSlot(slot) != null)
                {
                    slot++;
                }
                if (slot < into.getSizeInventory())
                {
                    into.setInventorySlotContents(slot++, is);
                }
                else
                {
                    world.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(world, x + 0.5, y + 1, z + 0.5, is));
                }
            }
            into.markDirty();
        }
        return true;
    }

    private static byte facing(World world, int x, int y, int z, TileEntity te)
    {
        try
        {
            Object f = te.getClass().getMethod("getFacing").invoke(te);
            return ((Number) f).byteValue();
        }
        catch (Exception e)
        {
            int meta = world.getBlockMetadata(x, y, z); // a vanilla chest: 2-5, the way it faces
            return (byte) (meta >= 2 && meta <= 5 ? meta : 2);
        }
    }

    /** a smithy's better forge: diamond swords */
    public static String upgradeSmithy(Building smithy)
    {
        if (!Workshop.isSmithy(smithy))
        {
            return "Only a smithy's forge can be upgraded";
        }
        if (smithy.level >= 2)
        {
            return "The forge is as good as it gets";
        }
        if (Economy.balance(smithy.primaryXYZ) < SMITHY_COST)
        {
            return "A better forge costs " + SimukraftReloaded.displayMoney(SMITHY_COST) + " from the treasury";
        }
        Economy.spend(smithy.primaryXYZ, SMITHY_COST);
        smithy.level = 2;
        smithy.saveThisBuilding();
        return "The smithy's forge is hot enough for diamond now: its blacksmiths make diamond swords for archers and up "
                + "(when the warehouse has diamonds)";
    }

    public static final float MASONRY_COST = 300f;

    /** a masonry's electric furnace: needs the town's power grid; a lineman puts in an Alloy Smelter */
    public static String upgradeMasonry(Building masonry)
    {
        if (!Workshop.isMasonry(masonry))
        {
            return "Only a masonry can have an electric furnace";
        }
        if (masonry.level >= 2)
        {
            return "It has its electric furnace" + (masonry.powerPoint == null ? " coming - a lineman will fit it" : "");
        }
        ashjack.simukraftreloaded.core.town.Town town = ashjack.simukraftreloaded.core.town.Towns.at(masonry.primaryXYZ);
        if (!ashjack.simukraftreloaded.common.power.PowerGrid.hasGrid(town))
        {
            return "An electric furnace needs the town's power grid: build a Sim-U-Power Station (and EnderIO)";
        }
        if (Economy.balance(masonry.primaryXYZ) < MASONRY_COST)
        {
            return "An electric furnace costs " + SimukraftReloaded.displayMoney(MASONRY_COST) + " from the treasury";
        }
        Economy.spend(masonry.primaryXYZ, MASONRY_COST);
        masonry.level = 2;
        masonry.saveThisBuilding();
        return "Ordered an electric furnace for the masonry: a lineman will fit an Alloy Smelter, and its stonemasons will "
                + "smelt faster and without coal";
    }

    /** for the GUI: a short line about where the building's at */
    public static String describe(Building b)
    {
        if (isWarehouse(b))
        {
            int n = Basement.count(b);
            String chests = b.level <= 1 ? "plain chests" : b.level == 2 ? "iron chests" : b.level == 3 ? "gold chests" : "diamond chests";
            return chests + (n > 0 ? ", " + n + (n == 1 ? " basement" : " basements") : "");
        }
        if (Workshop.isSmithy(b))
        {
            return b.level >= 2 ? "diamond forge" : "iron forge";
        }
        return null;
    }
}
