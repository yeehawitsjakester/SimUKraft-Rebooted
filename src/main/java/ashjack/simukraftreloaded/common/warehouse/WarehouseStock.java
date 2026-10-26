package ashjack.simukraftreloaded.common.warehouse;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.blocks.functionality.MiningBox;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobBuilder;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.Loader;

/**
 * What a warehouse can get its hands on, read fresh from the real chests (and ME network) on every scan. Nothing is
 * kept as a running tally, so items the player takes or adds, or an explosion destroys, simply aren't there next scan.
 */
public class WarehouseStock
{
    public enum Kind
    {
        WAREHOUSE("the warehouse"), MINE("a mine"), LEFTOVERS("leftovers by a finished building"), ME("the ME network");

        public final String label;

        Kind(String label)
        {
            this.label = label;
        }
    }

    /** somewhere items can be picked up: chests around a point, or an ME network */
    public static class Source
    {
        public final Kind kind;
        public final V3 at;
        public final ArrayList<IInventory> chests = new ArrayList<IInventory>();
        public Object network;
        /** any-meta counts, for planning */
        public final Map<Item, Integer> counts = new IdentityHashMap<Item, Integer>();

        Source(Kind kind, V3 at)
        {
            this.kind = kind;
            this.at = at;
        }

        /** at the warehouse itself, so no walking to pick up */
        public boolean isLocal()
        {
            return kind == Kind.WAREHOUSE || kind == Kind.ME;
        }

        public int count(Item item)
        {
            Integer n = counts.get(item);
            return n == null ? 0 : n;
        }
    }

    /** blocks builders need in Normal mode, so the ME network gets asked about them */
    private static final Item[] BUILDING_MATERIALS = {
        Item.getItemFromBlock(Blocks.planks), Item.getItemFromBlock(Blocks.cobblestone), Item.getItemFromBlock(Blocks.glass),
        Item.getItemFromBlock(Blocks.wool), Item.getItemFromBlock(Blocks.brick_block), Item.getItemFromBlock(Blocks.dirt),
        Item.getItemFromBlock(Blocks.stonebrick), Item.getItemFromBlock(Blocks.fence), Item.getItemFromBlock(Blocks.stone),
        Item.getItemFromBlock(Blocks.log)};

    public final ArrayList<Source> sources = new ArrayList<Source>();
    /** "item|meta" -> count per Kind, for the stock screen */
    public final LinkedHashMap<String, int[]> byStack = new LinkedHashMap<String, int[]>();
    public int chestCount;
    public int meTypes = -1;

    private static Boolean ae2;

    public static boolean hasAE2()
    {
        if (ae2 == null)
        {
            ae2 = Loader.isModLoaded("appliedenergistics2");
        }
        return ae2;
    }

    /** read everything a warehouse can reach. Never loads chunks. */
    public static WarehouseStock scan(Building warehouse, V3 box, World world)
    {
        WarehouseStock stock = new WarehouseStock();
        IdentityHashMap<IInventory, Boolean> seen = new IdentityHashMap<IInventory, Boolean>();
        int dim = box.theDimension;

        // chests by a builder at work are deliveries' destinations, never sources
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.BUILDER && f.employedAt != null && f.employedAt.theDimension == dim)
            {
                for (IInventory inv : storageNear(world, f.employedAt, 4))
                {
                    seen.put(inv, true);
                }
            }
        }

        // the warehouse: every chest inside the building (and a block around it)
        int[] b = bounds(warehouse, box);
        Source home = new Source(Kind.WAREHOUSE, box);
        for (int x = b[0] - 1; x <= b[3] + 1; x++)
        {
            for (int y = b[1] - 1; y <= b[4] + 1; y++)
            {
                for (int z = b[2] - 1; z <= b[5] + 1; z++)
                {
                    IInventory inv = storageAt(world, x, y, z);
                    if (inv != null && seen.put(inv, true) == null)
                    {
                        home.chests.add(inv);
                    }
                }
            }
        }
        stock.add(home);

        if (hasAE2())
        {
            try
            {
                Object net = AE2Bridge.findNetwork(world, b[0] - 2, b[1] - 2, b[2] - 2, b[3] + 2, b[4] + 2, b[5] + 2);
                if (net != null)
                {
                    Source me = new Source(Kind.ME, box);
                    me.network = net;
                    stock.meTypes = AE2Bridge.typeCount(net);
                    for (Item item : BUILDING_MATERIALS)
                    {
                        long n = AE2Bridge.count(net, item);
                        if (n > 0)
                        {
                            me.counts.put(item, (int) Math.min(n, Integer.MAX_VALUE));
                            stock.tally(Kind.ME, Item.itemRegistry.getNameForObject(item) + "|*", (int) Math.min(n, Integer.MAX_VALUE));
                        }
                    }
                    stock.sources.add(me);
                }
            }
            catch (Throwable e)
            {
                SimukraftReloaded.log.warning("Warehouse: couldn't read the ME network: " + e);
            }
        }

        for (MiningBox m : new ArrayList<MiningBox>(SimukraftReloaded.serverMiningBoxes))
        {
            if (m.location != null && m.location.theDimension == dim)
            {
                stock.add(chestSource(Kind.MINE, m.location, world, seen));
            }
        }

        for (Building other : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            V3 conBox = conBoxOf(other);
            if (other.buildingComplete && other != warehouse && conBox != null && conBox.theDimension == dim)
            {
                stock.add(chestSource(Kind.LEFTOVERS, conBox, world, seen));
            }
        }
        return stock;
    }

    private static Source chestSource(Kind kind, V3 at, World world, IdentityHashMap<IInventory, Boolean> seen)
    {
        Source s = new Source(kind, at);
        for (IInventory inv : storageNear(world, at, 4))
        {
            if (seen.put(inv, true) == null)
            {
                s.chests.add(inv);
            }
        }
        return s;
    }

    private void add(Source s)
    {
        if (s.chests.isEmpty())
        {
            return;
        }
        chestCount += s.chests.size();
        for (IInventory inv : s.chests)
        {
            for (int i = 0; i < inv.getSizeInventory(); i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                if (is == null || is.getItem() == null || is.stackSize <= 0)
                {
                    continue;
                }
                s.counts.put(is.getItem(), s.count(is.getItem()) + is.stackSize);
                tally(s.kind, Item.itemRegistry.getNameForObject(is.getItem()) + "|" + is.getItemDamage(), is.stackSize);
            }
        }
        sources.add(s);
    }

    private void tally(Kind kind, String key, int n)
    {
        int[] c = byStack.get(key);
        if (c == null)
        {
            c = new int[Kind.values().length];
            byStack.put(key, c);
        }
        c[kind.ordinal()] += n;
    }

    /** any meta, everywhere */
    public int total(Item item)
    {
        int n = 0;
        for (Source s : sources)
        {
            n += s.count(item);
        }
        return n;
    }

    /**
     * min/max x,y,z of the building: its blueprint's footprint laid out from its constructor box the way JobBuilder
     * lays it out, or a 13x13 area around the box if that can't be worked out. (Its recorded block list can't be
     * trusted for this: builds resumed after a restart only hold the blocks placed since.)
     */
    private static int[] bounds(Building warehouse, V3 box)
    {
        int bx = box.x.intValue(), by = box.y.intValue(), bz = box.z.intValue();
        int[] r = {bx - 6, by - 1, bz - 6, bx + 6, by + 5, bz + 6};
        int[] f = warehouse == null ? null : footprint(warehouse, conBoxOf(warehouse));
        if (f != null && f[0] <= bx && bx <= f[3] && f[1] <= by && by <= f[4] && f[2] <= bz && bz <= f[5])
        {
            r = f; // sanity: the control box is part of the building
        }
        return r;
    }

    /** the blueprint laid out from a constructor box, as JobBuilder places it, or null */
    private static int[] footprint(Building b, V3 con)
    {
        if (con == null || b.ltrCount <= 0 || b.ftbCount <= 0 || b.layerCount <= 0 || b.buildDirection == null)
        {
            return null;
        }
        int cx = con.x.intValue(), cy = con.y.intValue(), cz = con.z.intValue();
        int ltr = b.ltrCount - 1, ftb = b.ftbCount, top = cy + b.layerCount - 1;
        String d = b.buildDirection;
        if (d.contentEquals("-x"))
        {
            return new int[] {cx + 1, cy, cz, cx + ftb, top, cz + ltr};
        }
        if (d.contentEquals("+x"))
        {
            return new int[] {cx - ftb, cy, cz - ltr, cx - 1, top, cz};
        }
        if (d.contentEquals("-z"))
        {
            return new int[] {cx - ltr, cy, cz + 1, cx, top, cz + ftb};
        }
        if (d.contentEquals("+z"))
        {
            return new int[] {cx, cy, cz - ftb, cx + ltr, top, cz - 1};
        }
        return null;
    }

    /**
     * where a building's constructor box was. Buildings finished after a restart mid-build never saved it, but the
     * first block a builder records is the corner next to the box, so it can be worked back from that.
     */
    public static V3 conBoxOf(Building b)
    {
        if (b.conBoxLocation != null)
        {
            return b.conBoxLocation;
        }
        if (b.blockLocations == null || b.blockLocations.isEmpty() || b.blockLocations.get(0) == null
                || b.buildDirection == null || b.primaryXYZ == null)
        {
            return null;
        }
        V3 first = b.blockLocations.get(0);
        V3 con = first.clone();
        String d = b.buildDirection;
        if (d.contentEquals("-x")) con.x = con.x - 1;
        else if (d.contentEquals("+x")) con.x = con.x + 1;
        else if (d.contentEquals("-z")) con.z = con.z - 1;
        else if (d.contentEquals("+z")) con.z = con.z + 1;
        else return null;
        // only if the building then really covers its control box
        int[] f = footprint(b, con);
        int px = b.primaryXYZ.x.intValue(), py = b.primaryXYZ.y.intValue(), pz = b.primaryXYZ.z.intValue();
        if (f == null || px < f[0] || px > f[3] || py < f[1] || py > f[4] || pz < f[2] || pz > f[5])
        {
            return null;
        }
        b.conBoxLocation = con; // saved with the building from now on
        return con;
    }

    // ---------------------------------------------------------------- chests

    /** a chest-like inventory: chests and big plain inventories (iron chests...), not machines, furnaces or hoppers */
    public static IInventory storageAt(World world, int x, int y, int z)
    {
        if (y < 0 || y > 255 || !world.blockExists(x, y, z))
        {
            return null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityChest)
        {
            return (IInventory) te;
        }
        if (te instanceof IInventory && !(te instanceof ISidedInventory) && ((IInventory) te).getSizeInventory() >= 27)
        {
            return (IInventory) te;
        }
        return null;
    }

    /** chest-like inventories within r blocks (a cube) of a point */
    public static ArrayList<IInventory> storageNear(World world, V3 at, int r)
    {
        ArrayList<IInventory> out = new ArrayList<IInventory>();
        int cx = at.x.intValue(), cy = at.y.intValue(), cz = at.z.intValue();
        for (int y = cy - r; y <= cy + r; y++)
        {
            for (int x = cx - r; x <= cx + r; x++)
            {
                for (int z = cz - r; z <= cz + r; z++)
                {
                    IInventory inv = storageAt(world, x, y, z);
                    if (inv != null)
                    {
                        out.add(inv);
                    }
                }
            }
        }
        return out;
    }

    /** how many of an item (any meta) the chests hold */
    public static int count(List<IInventory> chests, Item item)
    {
        int n = 0;
        for (IInventory inv : chests)
        {
            for (int i = 0; i < inv.getSizeInventory(); i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                if (is != null && is.getItem() == item)
                {
                    n += is.stackSize;
                }
            }
        }
        return n;
    }

    /** take up to qty of an item (any meta) out of the chests, keeping each stack's meta and NBT */
    public static List<ItemStack> take(List<IInventory> chests, Item item, int qty)
    {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (IInventory inv : chests)
        {
            for (int i = 0; i < inv.getSizeInventory() && qty > 0; i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                if (is != null && is.getItem() == item && is.stackSize > 0)
                {
                    ItemStack got = inv.decrStackSize(i, Math.min(qty, is.stackSize));
                    if (got != null && got.stackSize > 0)
                    {
                        qty -= got.stackSize;
                        out.add(got);
                    }
                }
            }
        }
        return out;
    }

    /** take up to qty items of any kind out of the chests, whole stacks at a time where they fit */
    public static List<ItemStack> takeAny(List<IInventory> chests, int qty)
    {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (IInventory inv : chests)
        {
            for (int i = 0; i < inv.getSizeInventory() && qty > 0; i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                if (is != null && is.getItem() != null && is.stackSize > 0)
                {
                    ItemStack got = inv.decrStackSize(i, Math.min(qty, is.stackSize));
                    if (got != null && got.stackSize > 0)
                    {
                        qty -= got.stackSize;
                        out.add(got);
                    }
                }
            }
        }
        return out;
    }

    /** put a stack into the chests, topping up matching stacks first; returns what didn't fit, or null */
    public static ItemStack put(List<IInventory> chests, ItemStack stack)
    {
        if (stack == null || stack.stackSize <= 0)
        {
            return null;
        }
        ItemStack left = stack.copy();
        for (int pass = 0; pass < 2 && left.stackSize > 0; pass++)
        {
            for (IInventory inv : chests)
            {
                int limit = Math.min(inv.getInventoryStackLimit(), left.getMaxStackSize());
                for (int i = 0; i < inv.getSizeInventory() && left.stackSize > 0; i++)
                {
                    ItemStack in = inv.getStackInSlot(i);
                    if (pass == 0 && in != null && in.isItemEqual(left) && ItemStack.areItemStackTagsEqual(in, left)
                            && in.stackSize < limit)
                    {
                        int n = Math.min(limit - in.stackSize, left.stackSize);
                        in.stackSize += n;
                        left.stackSize -= n;
                        inv.markDirty();
                    }
                    else if (pass == 1 && in == null && inv.isItemValidForSlot(i, left))
                    {
                        ItemStack part = left.copy();
                        part.stackSize = Math.min(limit, left.stackSize);
                        left.stackSize -= part.stackSize;
                        inv.setInventorySlotContents(i, part);
                        inv.markDirty();
                    }
                }
            }
        }
        return left.stackSize > 0 ? left : null;
    }

    // ---------------------------------------------------------------- ME, through the bridge

    public static List<ItemStack> takeFromNetwork(Object network, Item item, int qty)
    {
        return AE2Bridge.extract(network, item, qty);
    }

    public static ItemStack putIntoNetwork(Object network, ItemStack stack)
    {
        return AE2Bridge.insert(network, stack);
    }
}
