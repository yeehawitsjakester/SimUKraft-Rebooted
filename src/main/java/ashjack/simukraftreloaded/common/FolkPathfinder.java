package ashjack.simukraftreloaded.common;

import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.entity.EntityFolk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * A* pathfinding for folk. Vanilla's pathfinder recognises fences and walls by render type, so modded fences and
 * walls (graveyards, decorative mods...) look like 1-block steps: folk plan to hop onto them and walk into them
 * forever. This judges blocks by their real collision boxes instead: anything taller than a block is a wall.
 * Never loads chunks; unloaded blocks count as solid.
 */
public final class FolkPathfinder
{
    private FolkPathfinder() {}

    /** nodes to expand before giving up and settling for the closest point reached */
    private static final int MAX_NODES = 4000;
    /** how far a folk will drop down in one step */
    private static final int MAX_DROP = 3;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final class Node implements Comparable<Node>
    {
        final int x, y, z;
        float g, f;
        Node parent;
        boolean closed;

        Node(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int compareTo(Node o)
        {
            return Float.compare(f, o.f);
        }
    }

    /**
     * A path from the entity towards x,y,z. If the target can't be reached (or is too far to search), the path
     * leads to the closest point found, so following it and pathing again makes progress. Null if there's no
     * progress to be made.
     */
    public static PathEntity findPath(Entity entity, int tx, int ty, int tz)
    {
        World world = entity.worldObj;
        // only miners go into mine pits; everyone else keeps a block back from the rim
        mines = isMiner(entity) ? new ArrayList<int[]>() : MineSafety.areas();
        dim = entity.dimension;
        int sx = MathHelper.floor_double(entity.posX);
        // start at the height PathNavigate measures them at (half a block above their feet), or on top of a chest
        // (7/8 high) the path starts inside the chest, the first point is a level below where they stand, the
        // navigator never counts it reached, and they turn on the spot. Swimmers start in the water, see toPath
        int sy = entity.isInWater() ? MathHelper.floor_double(entity.posY + 0.1)
                : MathHelper.floor_double(entity.boundingBox.minY + 0.5);
        int sz = MathHelper.floor_double(entity.posZ);

        HashMap<Long, Node> nodes = new HashMap<Long, Node>();
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        Node start = new Node(sx, sy, sz);
        start.f = heuristic(sx, sy, sz, tx, ty, tz);
        nodes.put(key(sx, sy, sz), start);
        open.add(start);

        Node best = start;
        float bestH = start.f;
        int expanded = 0;

        while (!open.isEmpty() && expanded < MAX_NODES)
        {
            Node n = open.poll();
            if (n.closed)
            {
                continue;
            }
            n.closed = true;
            expanded++;

            float h = heuristic(n.x, n.y, n.z, tx, ty, tz);
            if (h < bestH)
            {
                best = n;
                bestH = h;
            }
            // folk "arrive" within 2 blocks, so that's close enough
            if (Math.abs(n.x - tx) <= 1 && Math.abs(n.z - tz) <= 1 && Math.abs(n.y - ty) <= 2)
            {
                return toPath(world, n);
            }

            for (int[] d : DIRS)
            {
                int nx = n.x + d[0], nz = n.z + d[1];
                boolean diagonal = d[0] != 0 && d[1] != 0;
                // no cutting corners past walls
                if (diagonal && (!isOpen(world, n.x + d[0], n.y, n.z) || !isOpen(world, n.x, n.y, n.z + d[1])))
                {
                    continue;
                }
                int ny = landing(world, n, nx, nz, diagonal);
                if (ny == Integer.MIN_VALUE)
                {
                    continue;
                }
                float cost = (diagonal ? 1.414f : 1f) + (ny > n.y ? 0.5f : 0f) + (inWater(world, nx, ny, nz) ? 2f : 0f);
                long k = key(nx, ny, nz);
                Node m = nodes.get(k);
                if (m == null)
                {
                    m = new Node(nx, ny, nz);
                    m.g = Float.MAX_VALUE;
                    nodes.put(k, m);
                }
                if (m.closed || n.g + cost >= m.g)
                {
                    continue;
                }
                m.g = n.g + cost;
                m.f = m.g + heuristic(nx, ny, nz, tx, ty, tz);
                m.parent = n;
                open.add(m); // duplicates are skipped by the closed flag
            }
        }

        // couldn't reach it: head for the closest point if that's real progress
        if (best != start && heuristic(sx, sy, sz, tx, ty, tz) - bestH >= 2f)
        {
            return toPath(world, best);
        }
        return null;
    }

    /** the y a folk ends up at stepping from n into column nx,nz (same level, one up, or a drop), or MIN_VALUE */
    private static int landing(World world, Node n, int nx, int nz, boolean diagonal)
    {
        if (canStand(world, nx, n.y, nz))
        {
            return n.y;
        }
        // step up: needs headroom above the current spot too
        if (!diagonal && isOpen(world, n.x, n.y + 2, n.z) && canStand(world, nx, n.y + 1, nz))
        {
            return n.y + 1;
        }
        // drop down: the column has to be open on the way
        if (isOpen(world, nx, n.y, nz) && isOpen(world, nx, n.y + 1, nz))
        {
            for (int dy = 1; dy <= MAX_DROP; dy++)
            {
                if (canStand(world, nx, n.y - dy, nz))
                {
                    return n.y - dy;
                }
                if (!isOpen(world, nx, n.y - dy, nz))
                {
                    break;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** mines this search keeps out of (empty for miners); the server thread is the only caller */
    private static ArrayList<int[]> mines = new ArrayList<int[]>();
    private static int dim;

    private static boolean isMiner(Entity entity)
    {
        return entity instanceof EntityFolk && ((EntityFolk) entity).theData != null
                && ((EntityFolk) entity).theData.vocation == Vocation.MINER;
    }

    /** room for feet and head, and ground to stand on */
    private static boolean canStand(World world, int x, int y, int z)
    {
        if (y < 1 || y > 254 || !isOpen(world, x, y, z) || !isOpen(world, x, y + 1, z))
        {
            return false;
        }
        if (!mines.isEmpty() && MineSafety.inMine(mines, dim, x, y, z, 1))
        {
            return false;
        }
        if (inWater(world, x, y, z))
        {
            return true; // swimming
        }
        Block below = world.getBlock(x, y - 1, z);
        if (below.getMaterial() == Material.leaves || below.getMaterial() == Material.lava)
        {
            return false;
        }
        // standing on a fence or wall isn't a thing folk can get to
        double top = collisionTop(world, below, x, y - 1, z);
        return top > 0.4 && top <= 1.0;
    }

    /** can a folk's body be in this block: no collision (or a low one: carpet, snow), a door, or an open gate */
    private static boolean isOpen(World world, int x, int y, int z)
    {
        if (y < 0 || y > 255 || !world.blockExists(x, y, z))
        {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z))
        {
            return true;
        }
        Material m = block.getMaterial();
        if (m == Material.lava || m == Material.fire || m == Material.cactus)
        {
            return false;
        }
        if (m == Material.water)
        {
            return true;
        }
        if (block instanceof BlockDoor)
        {
            return m == Material.wood; // EntityAIOpenDoor opens wooden doors; iron ones need redstone
        }
        if (block instanceof BlockFenceGate)
        {
            return BlockFenceGate.isFenceGateOpen(world.getBlockMetadata(x, y, z));
        }
        double top = collisionTop(world, block, x, y, z);
        return top <= 0.25; // -1 = no collision at all
    }

    /**
     * height of the block's collision above its own y, or -1 if it has none. Asks for the full collision boxes:
     * fences and walls report a plain 1-block box from getCollisionBoundingBoxFromPool and only add their 1.5-high
     * box here, which made folk plan to walk along the top of fences.
     */
    private static double collisionTop(World world, Block block, int x, int y, int z)
    {
        try
        {
            ArrayList<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
            block.addCollisionBoxesToList(world, x, y, z, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 2, z + 1), boxes, null);
            double top = -1;
            for (AxisAlignedBB bb : boxes)
            {
                top = Math.max(top, bb.maxY - y);
            }
            return top;
        }
        catch (Exception e)
        {
            return 1.0; // some modded blocks throw out of context; treat as a normal block
        }
    }

    private static boolean inWater(World world, int x, int y, int z)
    {
        return world.getBlock(x, y, z).getMaterial() == Material.water;
    }

    private static float heuristic(int x, int y, int z, int tx, int ty, int tz)
    {
        float dx = x - tx, dy = y - ty, dz = z - tz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static long key(int x, int y, int z)
    {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * Points in water go at the water's surface, as vanilla's own paths do: PathNavigate measures a swimming entity at
     * the surface and only moves on to the next point when it's at that height, so a point at a swimmer's feet is
     * never reached and they turn on the spot forever.
     */
    private static PathEntity toPath(World world, Node end)
    {
        ArrayList<PathPoint> points = new ArrayList<PathPoint>();
        for (Node n = end; n != null; n = n.parent)
        {
            int y = n.y;
            while (y < 255 && inWater(world, n.x, y, n.z))
            {
                y++;
            }
            points.add(0, new PathPoint(n.x, y, n.z));
        }
        return new PathEntity(points.toArray(new PathPoint[points.size()]));
    }
}
