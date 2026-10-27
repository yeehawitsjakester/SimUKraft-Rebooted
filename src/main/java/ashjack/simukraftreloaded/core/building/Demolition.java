package ashjack.simukraftreloaded.core.building;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Kind;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Source;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Taking a building down and keeping what it was made of. Every block of it comes away whole - the blocks themselves,
 * as if carefully taken apart, not what breaking them would drop - along with everything in its chests and furnaces,
 * and goes into the town's warehouses (its own town's first, then the nearest), their ME networks too. Anything that
 * won't fit is left in chests on the cleared site.
 *
 * What counts as the building: every block of its blueprint laid out where it was built, whatever's there now (so a
 * block someone swapped in goes too), and every block its builders recorded placing. The ground itself - dirt, grass,
 * stone, sand, gravel, water - stays, as does anything that's part of another building.
 */
public final class Demolition
{
    private Demolition() {}

    public static String demolish(Building b, World world)
    {
        V3 con = WarehouseStock.conBoxOf(b);
        Set<Long> seen = new HashSet<Long>();
        List<int[]> cells = new ArrayList<int[]>();

        // the blueprint, laid out from its constructor box
        if (con != null && b.structure != null && b.buildDirection != null && !b.buildDirection.isEmpty())
        {
            V3 o = b.layoutBox(con);
            int ox = o.x.intValue(), oy = o.y.intValue(), oz = o.z.intValue();
            for (int y = 0; y < b.layerCount; y++)
            {
                for (int z = 0; z < b.ftbCount; z++)
                {
                    for (int x = 0; x < b.ltrCount; x++)
                    {
                        int i = (y * b.ftbCount + z) * b.ltrCount + x;
                        if (i >= b.structure.length)
                        {
                            continue;
                        }
                        Block planned = b.structure[i];
                        boolean keep = planned == null && b.structureSub[i] == Building.KEEP; // part built before (flats, basements)
                        if (!keep && (planned == null || planned == Blocks.air || isGround(planned)))
                        {
                            continue; // meant to be empty, or its foundations: whatever's there isn't to go
                        }
                        int[] w = MotelPlan.toWorld(b.buildDirection, ox, oz, x, z);
                        add(cells, seen, w[0], oy + y, w[1], keep);
                    }
                }
            }
        }
        // and what the builders recorded
        if (b.blockLocations != null)
        {
            for (V3 v : new ArrayList<V3>(b.blockLocations))
            {
                if (v != null)
                {
                    add(cells, seen, v.x.intValue(), v.y.intValue(), v.z.intValue(), true);
                }
            }
        }
        if (b.primaryXYZ != null)
        {
            add(cells, seen, b.primaryXYZ.x.intValue(), b.primaryXYZ.y.intValue(), b.primaryXYZ.z.intValue(), false);
        }
        if (cells.isEmpty())
        {
            return "Couldn't work out which blocks are the " + b.displayNameWithoutPK + "'s";
        }

        // other buildings' control boxes are theirs
        Set<Long> others = new HashSet<Long>();
        for (Building o : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (o != b && o.primaryXYZ != null)
            {
                others.add(key(o.primaryXYZ.x.intValue(), o.primaryXYZ.y.intValue(), o.primaryXYZ.z.intValue()));
            }
        }

        // top down, so nothing's left hanging on the way
        java.util.Collections.sort(cells, new java.util.Comparator<int[]>()
        {
            public int compare(int[] a, int[] c)
            {
                return c[1] - a[1];
            }
        });
        List<ItemStack> salvage = new ArrayList<ItemStack>();
        int blocks = 0;
        for (int[] p : cells)
        {
            int x = p[0], y = p[1], z = p[2];
            if (y < 1 || y > 255 || !world.blockExists(x, y, z) || others.contains(key(x, y, z)))
            {
                continue;
            }
            Block block = world.getBlock(x, y, z);
            // a block the blueprint has there goes whatever it is; elsewhere (kept parts, recorded spots) not the ground
            if (block.isAir(world, x, y, z) || block.getMaterial().isLiquid() || block.getBlockHardness(world, x, y, z) < 0
                    || p[3] == 1 && isGround(block))
            {
                continue;
            }
            int meta = world.getBlockMetadata(x, y, z);
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IInventory)
            {
                IInventory inv = (IInventory) te;
                for (int i = 0; i < inv.getSizeInventory(); i++)
                {
                    ItemStack is = inv.getStackInSlot(i);
                    if (is != null)
                    {
                        add(salvage, is.copy());
                        inv.setInventorySlotContents(i, null); // or it spills them
                    }
                }
            }
            for (ItemStack is : whole(world, block, x, y, z, meta))
            {
                add(salvage, is);
            }
            // no block updates: a door's other half or a torch on a wall would pop off and drop a second time
            world.setBlock(x, y, z, Blocks.air, 0, 2);
            blocks++;
            if (blocks % 16 == 0)
            {
                ashjack.simukraftreloaded.common.Effects.particles(world, "explode", x + 0.5, y + 0.5, z + 0.5, 1, 0.3, 0.05);
            }
        }

        int total = count(salvage);
        int stored = store(b, world, salvage);
        String leftNote = "";
        if (!salvage.isEmpty())
        {
            int chests = leaveOnSite(world, cells, salvage);
            leftNote = ", " + (total - stored) + " left in " + chests + (chests == 1 ? " chest" : " chests") + " on the site";
        }
        Building.removeBuilding(b, "demolished");
        SimukraftReloaded.log.info("Demolition: " + b.displayNameWithoutPK + " at " + b.primaryXYZ + ": " + blocks + " blocks, "
                + total + " items, " + stored + " stored");
        return "Took down the " + b.displayNameWithoutPK + ": " + blocks + " blocks. " + stored + " of its " + total
                + " items went to the warehouse" + leftNote;
    }

    /** the block itself, as it was put there (a slab pair as two slabs, a door as a door) */
    private static List<ItemStack> whole(World world, Block block, int x, int y, int z, int meta)
    {
        List<ItemStack> out = new ArrayList<ItemStack>();
        Item item = Item.getItemFromBlock(block);
        if (block.getMaterial().isLiquid())
        {
            return out;
        }
        if (item != null && !(block instanceof BlockSlab && block.isOpaqueCube()))
        {
            out.add(new ItemStack(item, 1, block.damageDropped(meta)));
            return out;
        }
        try
        {
            ArrayList<ItemStack> drops = block.getDrops(world, x, y, z, meta, 0);
            if (drops != null)
            {
                out.addAll(drops);
            }
        }
        catch (Exception e)
        {
            // a block that can't say
        }
        return out;
    }

    private static boolean isGround(Block b)
    {
        Material m = b.getMaterial();
        return b == Blocks.dirt || b == Blocks.grass || b == Blocks.stone || b == Blocks.sand || b == Blocks.gravel
                || b == Blocks.bedrock || b == Blocks.mycelium || m.isLiquid();
    }

    /** into the town's warehouses, nearest first; how many items went in. What's left stays in the list. */
    private static int store(Building b, World world, List<ItemStack> salvage)
    {
        int before = count(salvage);
        Town home = Towns.at(b.primaryXYZ);
        List<Building> warehouses = new ArrayList<Building>();
        for (Building o : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (o != b && Upgrades.isWarehouse(o) && o.buildingComplete && o.primaryXYZ != null
                    && o.primaryXYZ.theDimension == b.primaryXYZ.theDimension)
            {
                warehouses.add(o);
            }
        }
        final V3 from = b.primaryXYZ;
        final Town town = home;
        java.util.Collections.sort(warehouses, new java.util.Comparator<Building>()
        {
            public int compare(Building a, Building c)
            {
                boolean ah = town != null && town.contains(a.primaryXYZ), ch = town != null && town.contains(c.primaryXYZ);
                if (ah != ch)
                {
                    return ah ? -1 : 1;
                }
                return a.primaryXYZ.getDistanceTo(from) - c.primaryXYZ.getDistanceTo(from);
            }
        });
        for (Building wh : warehouses)
        {
            if (salvage.isEmpty())
            {
                break;
            }
            WarehouseStock st = WarehouseStock.scan(wh, wh.primaryXYZ, world);
            for (Source s : st.sources)
            {
                if (s.kind != Kind.WAREHOUSE && s.kind != Kind.ME)
                {
                    continue;
                }
                for (java.util.Iterator<ItemStack> it = salvage.iterator(); it.hasNext();)
                {
                    ItemStack is = it.next();
                    ItemStack left = s.kind == Kind.WAREHOUSE ? WarehouseStock.put(s.chests, is) : WarehouseStock.putIntoNetwork(s.network, is);
                    if (left == null)
                    {
                        it.remove();
                    }
                    else
                    {
                        is.stackSize = left.stackSize;
                    }
                }
            }
        }
        return before - count(salvage);
    }

    /** what wouldn't fit, in chests on the cleared ground (plain and trapped by turns, so they stay single) */
    private static int leaveOnSite(World world, List<int[]> cells, List<ItemStack> salvage)
    {
        int placed = 0;
        Set<Long> used = new HashSet<Long>();
        for (int[] p : cells)
        {
            if (salvage.isEmpty())
            {
                break;
            }
            int x = p[0], z = p[2];
            if ((x + z) % 2 != 0 || used.contains(key(x, 0, z)))
            {
                continue;
            }
            int y = world.getHeightValue(x, z);
            if (y < 1 || y > 254 || !world.isAirBlock(x, y, z) || !world.getBlock(x, y - 1, z).getMaterial().isSolid())
            {
                continue;
            }
            used.add(key(x, 0, z));
            world.setBlock(x, y, z, placed % 2 == 0 ? Blocks.chest : Blocks.trapped_chest, 0, 3);
            TileEntity te = world.getTileEntity(x, y, z);
            if (!(te instanceof IInventory))
            {
                continue;
            }
            List<IInventory> one = new ArrayList<IInventory>();
            one.add((IInventory) te);
            for (java.util.Iterator<ItemStack> it = salvage.iterator(); it.hasNext();)
            {
                ItemStack is = it.next();
                ItemStack left = WarehouseStock.put(one, is);
                if (left == null)
                {
                    it.remove();
                }
                else
                {
                    is.stackSize = left.stackSize;
                    break; // full
                }
            }
            placed++;
        }
        return placed;
    }

    /** onto a stack of the same where there's room */
    private static void add(List<ItemStack> list, ItemStack is)
    {
        if (is == null || is.getItem() == null || is.stackSize <= 0)
        {
            return;
        }
        for (ItemStack s : list)
        {
            if (s.isItemEqual(is) && ItemStack.areItemStackTagsEqual(s, is) && s.stackSize + is.stackSize <= s.getMaxStackSize())
            {
                s.stackSize += is.stackSize;
                return;
            }
        }
        list.add(is.copy());
    }

    private static int count(List<ItemStack> list)
    {
        int n = 0;
        for (ItemStack is : list)
        {
            n += is.stackSize;
        }
        return n;
    }

    private static void add(List<int[]> cells, Set<Long> seen, int x, int y, int z, boolean unplanned)
    {
        if (seen.add(key(x, y, z)))
        {
            cells.add(new int[] {x, y, z, unplanned ? 1 : 0});
        }
    }

    private static long key(int x, int y, int z)
    {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
