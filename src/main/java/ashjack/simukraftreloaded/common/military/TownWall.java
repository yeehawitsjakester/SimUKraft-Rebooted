package ashjack.simukraftreloaded.common.military;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.WorldUtil;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A town's wall: stone brick along its border, three high with battlements on top, following the lie of the land. A
 * gate is left in the middle of each side, and a gap wherever the wall would run into a building or across a road, a
 * river or something someone built. Trees and plants in its way are cleared.
 *
 * Wall builders (JobWallBuilder) take one column at a time. When the border moves, every column of the old wall that
 * isn't on the new line is taken down first - the bricks go back to stock - and the new line built. What's been built
 * is saved per town (Towns/wall-ID.sk2), so the wall knows what's its own: only its own bricks are ever taken down.
 */
public final class TownWall
{
    private TownWall() {}

    /** the wall's height above the ground, and the battlements on every other column above that */
    public static final int HEIGHT = 3, GATE_HALF_WIDTH = 1;
    public static final Block BLOCK = Blocks.stonebrick;

    /** a column of wall to put up, or take down */
    public static class Task
    {
        public final int x, z;
        public final boolean takeDown;
        /** the ground under it and its top block (taking down: as it was built; building: once prepared) */
        public int ground, top;

        Task(int x, int z, boolean takeDown)
        {
            this.x = x;
            this.z = z;
            this.takeDown = takeDown;
        }

        public long key()
        {
            return TownWall.key(x, z);
        }
    }

    private static class State
    {
        /** columns standing: key -> {x, z, ground, top} */
        final LinkedHashMap<Long, int[]> built = new LinkedHashMap<Long, int[]>();
        /** the current line, and where on it the builders have got to */
        List<int[]> line;
        int lineRadius = -1, lineCx, lineCz, cursor;
        /** columns on the current line left as gaps (a building, a road, someone's wall), until the line changes */
        final Set<Long> gaps = new HashSet<Long>();
        boolean dirty;
    }

    private static final Map<Integer, State> states = new HashMap<Integer, State>();
    /** columns a builder is on (by key) -> who */
    private static final Map<Long, String> claims = new HashMap<Long, String>();

    // ---------------------------------------------------------------- planning

    /** the next column for this builder: an old one to take down, else the next of the current line that's wanted */
    public static synchronized Task next(Town town, World world, String builder)
    {
        State s = state(town);
        if (s == null || world == null)
        {
            return null;
        }
        refreshLine(town, s);
        Set<Long> onLine = new HashSet<Long>();
        for (int[] c : s.line)
        {
            onLine.add(key(c[0], c[1]));
        }
        // the old wall first, where it isn't on the new line
        for (int[] c : s.built.values())
        {
            long k = key(c[0], c[1]);
            if (!onLine.contains(k) && !claimedByOther(k, builder) && loaded(world, c[0], c[1]))
            {
                Task t = new Task(c[0], c[1], true);
                t.ground = c[2];
                t.top = c[3];
                claims.put(k, builder);
                return t;
            }
        }
        // then round the line from where they'd got to
        int n = s.line.size();
        for (int i = 0; i < n; i++)
        {
            int[] c = s.line.get((s.cursor + i) % n);
            long k = key(c[0], c[1]);
            if (s.built.containsKey(k) || s.gaps.contains(k) || claimedByOther(k, builder) || !loaded(world, c[0], c[1]))
            {
                continue;
            }
            if (isGate(town, c[0], c[1]) || inBuilding(world, c[0], c[1]) || road(world, c[0], c[1]))
            {
                s.gaps.add(k);
                continue;
            }
            s.cursor = (s.cursor + i) % n;
            claims.put(k, builder);
            return new Task(c[0], c[1], false);
        }
        return null;
    }

    public static synchronized void release(Task t, String builder)
    {
        if (t != null && builder.equals(claims.get(t.key())))
        {
            claims.remove(t.key());
        }
    }

    /** how many bricks the rest of the line still needs, roughly (for asking the warehouse) */
    public static synchronized int bricksWanted(Town town)
    {
        State s = state(town);
        if (s == null)
        {
            return 0;
        }
        refreshLine(town, s);
        int columns = 0;
        for (int[] c : s.line)
        {
            long k = key(c[0], c[1]);
            if (!s.built.containsKey(k) && !s.gaps.contains(k))
            {
                columns++;
            }
        }
        return columns * (HEIGHT + 1) - columns / 2;
    }

    /** the columns of the line done, and in all */
    public static synchronized int[] progress(Town town)
    {
        State s = state(town);
        if (s == null)
        {
            return new int[] {0, 0};
        }
        refreshLine(town, s);
        int done = 0, wanted = 0;
        for (int[] c : s.line)
        {
            long k = key(c[0], c[1]);
            if (!s.gaps.contains(k) && !isGate(town, c[0], c[1]))
            {
                wanted++;
                if (s.built.containsKey(k))
                {
                    done++;
                }
            }
        }
        return new int[] {done, wanted};
    }

    private static void refreshLine(Town town, State s)
    {
        int r = town.radius, cx = town.centre.x.intValue(), cz = town.centre.z.intValue();
        if (s.line != null && s.lineRadius == r && s.lineCx == cx && s.lineCz == cz)
        {
            return;
        }
        List<int[]> line = new ArrayList<int[]>();
        // round the square from the north-west corner, clockwise
        for (int x = cx - r; x < cx + r; x++)
        {
            line.add(new int[] {x, cz - r});
        }
        for (int z = cz - r; z < cz + r; z++)
        {
            line.add(new int[] {cx + r, z});
        }
        for (int x = cx + r; x > cx - r; x--)
        {
            line.add(new int[] {x, cz + r});
        }
        for (int z = cz + r; z > cz - r; z--)
        {
            line.add(new int[] {cx - r, z});
        }
        s.line = line;
        s.lineRadius = r;
        s.lineCx = cx;
        s.lineCz = cz;
        s.cursor = 0;
        s.gaps.clear();
    }

    private static boolean isGate(Town town, int x, int z)
    {
        int cx = town.centre.x.intValue(), cz = town.centre.z.intValue();
        boolean northSouth = Math.abs(z - cz) == town.radius, eastWest = Math.abs(x - cx) == town.radius;
        return northSouth && !eastWest && Math.abs(x - cx) <= GATE_HALF_WIDTH
                || eastWest && !northSouth && Math.abs(z - cz) <= GATE_HALF_WIDTH;
    }

    private static boolean claimedByOther(long k, String builder)
    {
        String who = claims.get(k);
        return who != null && !who.equals(builder);
    }

    private static boolean loaded(World world, int x, int z)
    {
        return world.getChunkProvider().chunkExists(x >> 4, z >> 4);
    }

    /** inside (or right against) a building's footprint */
    private static boolean inBuilding(World world, int x, int z)
    {
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            try
            {
                V3 con = b.isRoom() ? null : WarehouseStock.conBoxOf(b);
                int[] f = con == null ? null : WarehouseStock.footprint(b, b.layoutBox(con));
                if (f != null && x >= f[0] - 1 && x <= f[3] + 1 && z >= f[2] - 1 && z <= f[5] + 1)
                {
                    return true;
                }
            }
            catch (Exception e)
            {
                // can't say where it is
            }
        }
        return false;
    }

    /** a road, path or water where the wall would stand: left open */
    private static boolean road(World world, int x, int z)
    {
        int g = ground(world, x, z);
        if (g < 0)
        {
            return true;
        }
        Block b = world.getBlock(x, g, z);
        return b == Blocks.gravel || b == Blocks.cobblestone || b == Blocks.stonebrick || b == Blocks.planks
                || b == Blocks.stone_slab || b == Blocks.double_stone_slab || b.getMaterial().isLiquid()
                || world.getBlock(x, g + 1, z).getMaterial().isLiquid();
    }

    // ---------------------------------------------------------------- the ground and what's on it

    /** the natural ground: the top solid block, under any trees, plants and snow; -1 if there isn't any */
    public static int ground(World world, int x, int z)
    {
        int top = world.getHeightValue(x, z);
        for (int y = Math.min(255, top); y > 1; y--)
        {
            Block b = world.getBlock(x, y, z);
            if (b.isAir(world, x, y, z) || clearable(world, b, x, y, z))
            {
                continue;
            }
            return b.getMaterial().isLiquid() || b.getMaterial().isSolid() ? y : -1;
        }
        return -1;
    }

    /** what can be cleared for the wall: trees, plants, snow */
    static boolean clearable(World world, Block b, int x, int y, int z)
    {
        if (ashjack.simukraftreloaded.common.PlacedBlocks.placed(world, x, y, z))
        {
            return false; // someone put it there
        }
        Material m = b.getMaterial();
        return b.isLeaves(world, x, y, z) || b.isWood(world, x, y, z) || m == Material.plants || m == Material.vine
                || m == Material.snow || m == Material.craftedSnow || m == Material.cactus || m == Material.gourd
                || b.isReplaceable(world, x, y, z) && !m.isLiquid();
    }

    /** the blocks a column at x, z is made of, bottom up: {y...} above this ground */
    public static int top(int x, int z, int ground)
    {
        return ground + HEIGHT + ((x + z) % 2 == 0 ? 1 : 0);
    }

    /**
     * whether the column's space is clear enough to build in (cleared of plants as it goes); false if something
     * someone built is in the way, and it becomes a gap
     */
    public static synchronized boolean prepare(Town town, World world, Task t)
    {
        int g = ground(world, t.x, t.z);
        if (g < 0)
        {
            gap(town, t);
            return false;
        }
        for (int y = g + 1; y <= top(t.x, t.z, g); y++)
        {
            Block b = world.getBlock(t.x, y, t.z);
            if (b.isAir(world, t.x, y, t.z))
            {
                continue;
            }
            if (!clearable(world, b, t.x, y, t.z))
            {
                gap(town, t);
                return false;
            }
            world.setBlockToAir(t.x, y, t.z);
        }
        t.ground = g;
        t.top = top(t.x, t.z, g);
        return true;
    }

    private static void gap(Town town, Task t)
    {
        State s = state(town);
        if (s != null)
        {
            s.gaps.add(t.key());
        }
    }

    public static synchronized void built(Town town, Task t, String builder)
    {
        State s = state(town);
        if (s != null)
        {
            s.built.put(t.key(), new int[] {t.x, t.z, t.ground, t.top});
            s.dirty = true;
        }
        release(t, builder);
    }

    public static synchronized void takenDown(Town town, Task t, String builder)
    {
        State s = state(town);
        if (s != null)
        {
            s.built.remove(t.key());
            s.dirty = true;
        }
        release(t, builder);
    }

    /** where a border guard walks: just inside the wall, every so often round it */
    public static synchronized List<V3> patrol(Town town, World world)
    {
        List<V3> stops = new ArrayList<V3>();
        State s = state(town);
        if (s == null || world == null)
        {
            return stops;
        }
        refreshLine(town, s);
        int cx = town.centre.x.intValue(), cz = town.centre.z.intValue();
        for (int i = 0; i < s.line.size(); i += 16)
        {
            int[] c = s.line.get(i);
            // two blocks in towards the middle
            int x = c[0] + Integer.signum(cx - c[0]) * 2, z = c[1] + Integer.signum(cz - c[1]) * 2;
            if (!loaded(world, x, z))
            {
                continue;
            }
            int g = ground(world, x, z);
            int y = g < 0 ? -1 : WorldUtil.findStandableY(world, x, g + 1, z, 4);
            if (y >= 0)
            {
                stops.add(new V3((double) x, (double) y, (double) z, town.dimension()));
            }
        }
        return stops;
    }

    // ---------------------------------------------------------------- saving

    private static State state(Town town)
    {
        if (town == null || town.centre == null)
        {
            return null;
        }
        State s = states.get(town.id);
        if (s == null)
        {
            s = load(town);
            states.put(town.id, s);
        }
        return s;
    }

    private static File file(Town town)
    {
        return new File(SimukraftReloaded.getSavesDataFolder() + "Towns" + File.separator + "wall-" + town.id + ".sk2");
    }

    private static State load(Town town)
    {
        State s = new State();
        File f = file(town);
        if (!f.exists())
        {
            return s;
        }
        for (String line : SimukraftReloaded.loadSK2(f.getAbsolutePath()))
        {
            if (line.startsWith("col|"))
            {
                try
                {
                    String[] p = line.substring(4).split(",");
                    int[] c = {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
                    s.built.put(key(c[0], c[1]), c);
                }
                catch (Exception e)
                {
                    // a broken line
                }
            }
        }
        return s;
    }

    /** every town's wall that's changed since it was last saved */
    public static synchronized void saveAll()
    {
        for (Map.Entry<Integer, State> e : states.entrySet())
        {
            if (!e.getValue().dirty)
            {
                continue;
            }
            Town town = ashjack.simukraftreloaded.core.town.Towns.byId(e.getKey());
            if (town == null)
            {
                continue;
            }
            ArrayList<String> lines = new ArrayList<String>();
            for (int[] c : e.getValue().built.values())
            {
                lines.add("col|" + c[0] + "," + c[1] + "," + c[2] + "," + c[3]);
            }
            SimukraftReloaded.saveSK2(file(town).getAbsolutePath(), lines);
            e.getValue().dirty = false;
        }
    }

    public static synchronized void clear()
    {
        states.clear();
        claims.clear();
    }

    static long key(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
