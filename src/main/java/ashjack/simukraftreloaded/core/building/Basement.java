package ashjack.simukraftreloaded.core.building;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Storage basements dug under a warehouse: each one a stone-brick room the size of its footprint, three blocks high,
 * lined with chests (plain and trapped by turns, so none join up into double chests that won't fit), lit from the
 * ceiling, with a ladder down from the warehouse floor through every basement. The chests are inside the warehouse's
 * footprint, so the keeper stocks them like the rest.
 *
 * Like flats, the blueprint is generated from the name: GEN-Basement2_+x_industrial_Warehouse is the Warehouse with two
 * basements under it. The building is laid out one basement deeper for each (offsetUp), and everything already there
 * is left as it is (Building.KEEP) apart from the hole in the floor above for the ladder, so a builder only digs out
 * and fits the new one.
 */
public final class Basement
{
    private Basement() {}

    public static final String PREFIX = "GEN-Basement";
    public static final int HEIGHT = 5, MAX = 3;

    public static class Name
    {
        public final int count;
        public final String dir, type, original;

        Name(int count, String dir, String type, String original)
        {
            this.count = count;
            this.dir = dir;
            this.type = type;
            this.original = original;
        }
    }

    public static boolean isGenerated(String name)
    {
        return name != null && name.startsWith(PREFIX);
    }

    public static String name(int count, String dir, String type, String original)
    {
        return PREFIX + count + "_" + dir + "_" + type + "_" + original;
    }

    public static Name parse(String name)
    {
        if (!isGenerated(name))
        {
            return null;
        }
        String[] p = name.substring(PREFIX.length()).split("_", 4);
        try
        {
            return p.length < 4 ? null : new Name(Integer.parseInt(p[0]), p[1], p[2], p[3]);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** a warehouse that's had this many dug (0 for none) */
    public static int count(Building b)
    {
        Name n = b == null ? null : parse(b.displayName);
        return n == null ? 0 : n.count;
    }

    /** the blueprint it's built from, under any basements */
    public static String original(Building b)
    {
        Name n = parse(b.displayName);
        return n == null ? b.displayName : n.original;
    }

    /**
     * fills b (already sized) from the original blueprint with the basements under it, the new deepest one to be built
     * and everything else kept; returns the cells that are to be built, or null if there's nowhere for the ladder
     */
    static List<Integer> lay(Building b, Building orig, int count)
    {
        int L = orig.ltrCount, F = orig.ftbCount, H = orig.layerCount, below = HEIGHT * count;
        List<Integer> build = new ArrayList<Integer>();
        for (int i = 0; i < b.structure.length; i++)
        {
            b.structure[i] = null;
            b.structureSub[i] = Building.KEEP;
        }

        // the ladder: inside a wall of the warehouse, where its floor has standing room above it
        int[] spot = ladderSpot(orig);
        if (spot == null)
        {
            return null;
        }
        int lx = spot[0], lz = spot[1];

        // the new basement, at the bottom
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int z = 0; z < F; z++)
            {
                for (int x = 0; x < L; x++)
                {
                    boolean wall = x == 0 || z == 0 || x == L - 1 || z == F - 1;
                    boolean ring = !wall && (x == 1 || z == 1 || x == L - 2 || z == F - 2);
                    boolean ladder = x == lx && z == lz;
                    boolean byLadder = Math.abs(x - lx) + Math.abs(z - lz) == 1;
                    Block block;
                    int meta = 0;
                    if (ladder && y > 0)
                    {
                        block = Blocks.ladder;
                    }
                    else if (y == 0 || wall)
                    {
                        block = Blocks.stonebrick;
                    }
                    else if (y == HEIGHT - 1)
                    {
                        boolean light = x % 4 == 2 && z % 4 == 2;
                        block = light ? Blocks.glowstone : Blocks.stonebrick;
                    }
                    else if (ring && !byLadder && y <= 2)
                    {
                        block = (x + z) % 2 == 0 ? Blocks.chest : Blocks.trapped_chest;
                    }
                    else
                    {
                        block = Blocks.air;
                    }
                    set(b, L, F, x, y, z, block, meta, build);
                }
            }
        }
        // the ladder on up through the floor of the basement above, or the warehouse's own floor
        int above = HEIGHT;
        set(b, L, F, lx, above, lz, Blocks.ladder, 0, build);
        return build;
    }

    private static void set(Building b, int L, int F, int x, int y, int z, Block block, int meta, List<Integer> build)
    {
        int i = (y * F + z) * L + x;
        b.structure[i] = block;
        b.structureSub[i] = meta;
        build.add(i);
    }

    /** the ladder's column: one in from a wall, where the floor's solid with two of air above, against solid wall */
    private static int[] ladderSpot(Building orig)
    {
        int L = orig.ltrCount, F = orig.ftbCount;
        if (L < 4 || F < 4 || orig.layerCount < 3)
        {
            return null;
        }
        int[][] tries = new int[2 * (L + F)][];
        int n = 0;
        for (int z = 2; z < F - 2; z++)
        {
            tries[n++] = new int[] {1, z, 0, z};
            tries[n++] = new int[] {L - 2, z, L - 1, z};
        }
        for (int x = 2; x < L - 2; x++)
        {
            tries[n++] = new int[] {x, 1, x, 0};
            tries[n++] = new int[] {x, F - 2, x, F - 1};
        }
        for (int t = 0; t < n; t++)
        {
            int[] s = tries[t];
            if (solid(orig, s[0], 0, s[1]) && solid(orig, s[2], 0, s[3]) && !solid(orig, s[0], 1, s[1]) && !solid(orig, s[0], 2, s[1]))
            {
                return new int[] {s[0], s[1]};
            }
        }
        return null;
    }

    private static boolean solid(Building b, int x, int y, int z)
    {
        int i = (y * b.ftbCount + z) * b.ltrCount + x;
        Block block = i < 0 || i >= b.structure.length ? null : b.structure[i];
        return block != null && block != Blocks.air && block.getMaterial().isSolid();
    }

    /** each ladder in the building turned to face away from something it can hang on (the builder can't tell) */
    public static void fixLadders(World world, int[] f)
    {
        if (world == null || f == null)
        {
            return;
        }
        for (int x = f[0]; x <= f[3]; x++)
        {
            for (int y = f[1]; y <= f[4]; y++)
            {
                for (int z = f[2]; z <= f[5]; z++)
                {
                    if (world.getBlock(x, y, z) != Blocks.ladder)
                    {
                        continue;
                    }
                    // ladder meta: 2 hangs on the block to the south (z+1), 3 north, 4 east, 5 west
                    int meta = world.isSideSolid(x, y, z + 1, net.minecraftforge.common.util.ForgeDirection.NORTH) ? 2
                            : world.isSideSolid(x, y, z - 1, net.minecraftforge.common.util.ForgeDirection.SOUTH) ? 3
                            : world.isSideSolid(x + 1, y, z, net.minecraftforge.common.util.ForgeDirection.WEST) ? 4
                            : world.isSideSolid(x - 1, y, z, net.minecraftforge.common.util.ForgeDirection.EAST) ? 5 : -1;
                    if (meta > 0 && world.getBlockMetadata(x, y, z) != meta)
                    {
                        world.setBlockMetadataWithNotify(x, y, z, meta, 3);
                    }
                }
            }
        }
    }
}
