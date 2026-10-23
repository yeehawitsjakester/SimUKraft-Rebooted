package ashjack.simukraftreloaded.common;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

/**
 * Block-space helpers. The 1.6-era code tested "getBlock(...) == null" for empty space, which is never true
 * in 1.7 (getBlock returns air), so these replace those checks.
 */
public final class WorldUtil
{
    private WorldUtil() {}

    /** true if an entity can occupy this block: air, or something without collision that isn't a liquid (tall grass, flowers, snow layer...) */
    public static boolean isPassable(World world, int x, int y, int z)
    {
        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z))
        {
            return true;
        }
        Material m = block.getMaterial();
        return !m.blocksMovement() && !m.isLiquid();
    }

    /** true if a folk can stand at x,y,z: room for feet and head, and solid non-leaf ground below */
    public static boolean canStandAt(World world, int x, int y, int z)
    {
        if (y < 1 || y > 254)
        {
            return false;
        }
        Material below = world.getBlock(x, y - 1, z).getMaterial();
        return below.blocksMovement() && below != Material.leaves
                && isPassable(world, x, y, z) && isPassable(world, x, y + 1, z);
    }

    /**
     * Finds the closest y to startY (searching up and down by at most range) where a folk can stand.
     * Returns -1 if there is none, or if the column isn't loaded - this never loads chunks.
     */
    public static int findStandableY(World world, int x, int startY, int z, int range)
    {
        if (!world.blockExists(x, 64, z))
        {
            return -1;
        }
        int y = Math.max(1, Math.min(254, startY));
        for (int d = 0; d <= range; d++)
        {
            if (canStandAt(world, x, y - d, z))
            {
                return y - d;
            }
            if (d > 0 && canStandAt(world, x, y + d, z))
            {
                return y + d;
            }
        }
        return -1;
    }
}
