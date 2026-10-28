package ashjack.simukraftreloaded.common.jobs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Wild trees for lumberjacks. A tree is a trunk standing on the ground, of any mod's logs (Block.isWood), and every log
 * joined to it, branches and all - so a jungle giant comes down whole. It's only wild if leaves that grew there touch
 * it (placed leaves carry vanilla's no-decay bit), which leaves log walls, beams and hedges people made alone, and it
 * mustn't be on or close to a building. Searched outward from a centre, a slice of columns at a time, only where the
 * world is loaded.
 */
public class Forest
{
    /** column offsets (dx, dz pairs), nearest first, for the biggest radius asked for so far */
    private static int[] offsets = new int[0];
    private static int offsetsRadius = -1;
    /** trees a lumberjack is already felling, by their base */
    private static final Set<Long> claimed = Collections.synchronizedSet(new HashSet<Long>());

    private static final int SPREAD = 12, TALLEST = 48, MOST_LOGS = 1000, NEAR_BUILDING = 3, MIN_LEAVES = 3;

    public static class Tree
    {
        public int baseX, baseY, baseZ;
        /** what was left of one in the air, not a standing tree */
        public boolean floating;
        /** x, y, z of every log, lowest first */
        public List<int[]> logs;
        long key;

        public V3 base(int dim)
        {
            return new V3((double) baseX, (double) baseY, (double) baseZ, dim);
        }
    }

    /** a search outward from a centre, carried on from where it left off each time */
    public static class Search
    {
        private final V3 centre;
        private final int radius;
        private int next;
        /** bases looked at and turned down (not wild, or by a building), until the next time round */
        private final Set<Long> rejected = new HashSet<Long>();
        private List<int[]> footprints;
        private long footprintsAt;
        /** the whole area has been looked over since the last tree */
        public boolean exhausted;

        public Search(V3 centre, int radius)
        {
            this.centre = centre.clone();
            this.radius = radius;
        }

        public boolean isFor(V3 c, int r)
        {
            return r == radius && c != null && c.isSameCoordsAs(centre, true, true);
        }

        /** looks at up to this many columns; the nearest unclaimed wild tree found, or null for none yet */
        public Tree step(World world, int columns)
        {
            int[] off = offsets(radius);
            int n = off.length / 2, cx = centre.x.intValue(), cz = centre.z.intValue(), dim = centre.theDimension;
            refreshFootprints();
            for (int i = 0; i < columns; i++)
            {
                if (next >= n)
                {
                    next = 0;
                    rejected.clear();
                    exhausted = true;
                    return null;
                }
                int x = cx + off[2 * next], z = cz + off[2 * next + 1];
                next++;
                if (!world.getChunkProvider().chunkExists(x >> 4, z >> 4)
                        || (!ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig.configLumberInsideTown
                                && Towns.at(dim, x, z) != null))
                {
                    continue;
                }
                int y = trunkBase(world, x, z);
                boolean floating = false;
                if (y < 0)
                {
                    // what's left of a tree someone (or something) half felled: logs in the air with nothing under them
                    y = floatingBase(world, x, z);
                    floating = y >= 0;
                }
                if (y < 0)
                {
                    continue;
                }
                long key = key(x, y, z);
                if (rejected.contains(key) || claimed.contains(key))
                {
                    continue;
                }
                Tree t = collect(world, x, y, z, floating);
                if (t == null || nearBuilding(t))
                {
                    rejected.add(key);
                    continue;
                }
                t.key = key;
                claimed.add(key);
                exhausted = false;
                next--; // its neighbours may be trees too: come back here next time
                return t;
            }
            return null;
        }

        private void refreshFootprints()
        {
            if (footprints != null && System.currentTimeMillis() - footprintsAt < 60000)
            {
                return;
            }
            footprintsAt = System.currentTimeMillis();
            footprints = new ArrayList<int[]>();
            for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
            {
                try
                {
                    V3 con = b.isRoom() ? null : WarehouseStock.conBoxOf(b);
                    int[] f = con == null || con.theDimension != centre.theDimension ? null : WarehouseStock.footprint(b, b.layoutBox(con));
                    if (f != null)
                    {
                        footprints.add(f);
                    }
                }
                catch (Exception e)
                {
                    // can't say where it is
                }
            }
        }

        private boolean nearBuilding(Tree t)
        {
            for (int[] f : footprints)
            {
                for (int[] l : t.logs)
                {
                    if (l[0] >= f[0] - NEAR_BUILDING && l[0] <= f[3] + NEAR_BUILDING && l[2] >= f[2] - NEAR_BUILDING
                            && l[2] <= f[5] + NEAR_BUILDING && l[1] >= f[1] - NEAR_BUILDING && l[1] <= f[4] + NEAR_BUILDING)
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        /** not this tree again this time round (it couldn't be reached) */
        public void reject(Tree t)
        {
            if (t != null)
            {
                rejected.add(t.key);
            }
        }
    }

    /** done with this tree (felled, or given up on) */
    public static void release(Tree t)
    {
        if (t != null)
        {
            claimed.remove(t.key);
        }
    }

    /** a log in this column with nothing solid under it (part of something in the air), or -1 */
    static int floatingBase(World world, int x, int z)
    {
        int top = world.getHeightValue(x, z);
        for (int y = top; y > 1 && y > top - 64; y--)
        {
            Block b = world.getBlock(x, y, z);
            if (b.isWood(world, x, y, z))
            {
                Block below = world.getBlock(x, y - 1, z);
                if (below.isWood(world, x, y - 1, z))
                {
                    continue; // down the log to its foot
                }
                if (!below.getMaterial().isSolid() || below.isLeaves(world, x, y - 1, z))
                {
                    return y;
                }
                return -1; // stands on the ground: a trunk
            }
            Material m = b.getMaterial();
            if (!(m == Material.air || m == Material.vine || m == Material.plants || b.isLeaves(world, x, y, z)))
            {
                return -1;
            }
        }
        return -1;
    }

    /**
     * the lowest log of a trunk in this column standing on the ground, or -1. Looking down from the top through
     * leaves, vines and air: a column under a branch has air beneath its log, so only trunks count.
     */
    static int trunkBase(World world, int x, int z)
    {
        int top = world.getHeightValue(x, z);
        int lowestLog = -1;
        for (int y = top; y > 0 && y > top - 64; y--)
        {
            Block b = world.getBlock(x, y, z);
            if (b.isWood(world, x, y, z))
            {
                lowestLog = y;
                continue;
            }
            Material m = b.getMaterial();
            if (m == Material.air || m == Material.vine || m == Material.plants || b.isLeaves(world, x, y, z))
            {
                lowestLog = -1; // a gap: whatever log was above isn't the trunk's foot
                continue;
            }
            return lowestLog >= 0 && lowestLog == y + 1 && m.isSolid() ? lowestLog : -1;
        }
        return -1;
    }

    /** every log joined to this trunk, if it's a wild tree; null if it isn't */
    static Tree collect(World world, int bx, int by, int bz, boolean floating)
    {
        int lowest = floating ? by - 8 : by;
        Set<Long> seen = new HashSet<Long>();
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        List<int[]> logs = new ArrayList<int[]>();
        queue.add(new int[] {bx, by, bz});
        seen.add(key(bx, by, bz));
        int wildLeaves = 0;
        while (!queue.isEmpty())
        {
            int[] p = queue.poll();
            logs.add(p);
            if (logs.size() > MOST_LOGS)
            {
                return null; // not a tree: a log building, or a forest grown into one lump
            }
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        int x = p[0] + dx, y = p[1] + dy, z = p[2] + dz;
                        if ((dx == 0 && dy == 0 && dz == 0) || y < lowest || y > by + TALLEST || Math.abs(x - bx) > SPREAD
                                || Math.abs(z - bz) > SPREAD || !world.blockExists(x, y, z))
                        {
                            continue;
                        }
                        Block b = world.getBlock(x, y, z);
                        boolean face = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
                        if (face && isWildLeaves(world, b, x, y, z))
                        {
                            wildLeaves++;
                        }
                        else if (face && isMade(world, b, x, y, z))
                        {
                            return null; // planks, fences or glass against it: part of something someone built
                        }
                        if (b.isWood(world, x, y, z) && seen.add(key(x, y, z)))
                        {
                            queue.add(new int[] {x, y, z});
                        }
                    }
                }
            }
        }
        if (floating)
        {
            // only if none of it stands on anything: a standing tree's branches are found from its trunk
            if (logs.size() > 300)
            {
                return null;
            }
            for (int[] l : logs)
            {
                Block below = world.getBlock(l[0], l[1] - 1, l[2]);
                if (below.getMaterial().isSolid() && !below.isWood(world, l[0], l[1] - 1, l[2])
                        && !below.isLeaves(world, l[0], l[1] - 1, l[2]))
                {
                    return null;
                }
            }
        }
        else if (wildLeaves < MIN_LEAVES)
        {
            return null;
        }
        Collections.sort(logs, new Comparator<int[]>()
        {
            public int compare(int[] a, int[] b)
            {
                return a[1] - b[1];
            }
        });
        Tree t = new Tree();
        t.floating = floating;
        t.baseX = bx;
        t.baseY = by;
        t.baseZ = bz;
        t.logs = logs;
        return t;
    }

    /** wood that isn't a log (planks, fences, doors) or glass: things only people put next to logs */
    private static boolean isMade(World world, Block b, int x, int y, int z)
    {
        Material m = b.getMaterial();
        return (m == Material.wood && !b.isWood(world, x, y, z)) || m == Material.glass;
    }

    /** leaves that grew: vanilla-style leaves without the bit set on ones people place */
    private static boolean isWildLeaves(World world, Block b, int x, int y, int z)
    {
        if (!b.isLeaves(world, x, y, z))
        {
            return false;
        }
        return !(b instanceof BlockLeaves) || (world.getBlockMetadata(x, y, z) & 4) == 0;
    }

    private static long key(int x, int y, int z)
    {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static synchronized int[] offsets(int radius)
    {
        if (radius == offsetsRadius)
        {
            return offsets;
        }
        int side = 2 * radius + 1;
        Integer[] order = new Integer[side * side];
        for (int i = 0; i < order.length; i++)
        {
            order[i] = i;
        }
        final int r = radius, s = side;
        Arrays.sort(order, new Comparator<Integer>()
        {
            public int compare(Integer a, Integer b)
            {
                int ax = a % s - r, az = a / s - r, bx = b % s - r, bz = b / s - r;
                return (ax * ax + az * az) - (bx * bx + bz * bz);
            }
        });
        int[] out = new int[order.length * 2];
        for (int i = 0; i < order.length; i++)
        {
            out[2 * i] = order[i] % side - radius;
            out[2 * i + 1] = order[i] / side - radius;
        }
        offsets = out;
        offsetsRadius = radius;
        return out;
    }
}
