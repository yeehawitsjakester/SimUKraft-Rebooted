package ashjack.simukraftreloaded.common.rail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.military.TownWall;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;

/**
 * Finds a route for a railway between two points: the cheapest line of track the rules allow, over the land as it
 * is. Track rises or falls at most a block per block along, and a slope has to be straight (a rail can't turn and climb
 * at once). Each step's cost says how much work it is: track on the ground is cheapest; raising it (an embankment, or a
 * bridge over water) or sinking it into the land (a cutting, or a tunnel through a hill) costs more the higher or
 * deeper it goes; a turn or a slope costs a little. It goes round buildings, and only over land that's loaded.
 *
 * The result is the track's blocks in order, each with the height of the rail.
 */
public final class RailPlanner
{
    private RailPlanner() {}

    /** how far a route can be (blocks, along the ground), how high or deep the track can go, and how hard to look */
    public static final int MAX_LENGTH = 512, MAX_RAISE = 8, MAX_SINK = 16, MAX_NODES = 250000;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static class Node implements Comparable<Node>
    {
        final int x, y, z, dir;
        /** came down a slope: has to carry straight on */
        final boolean straight;
        final double g, f;
        final Node from;

        Node(int x, int y, int z, int dir, boolean straight, double g, double f, Node from)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = dir;
            this.straight = straight;
            this.g = g;
            this.f = f;
            this.from = from;
        }

        public int compareTo(Node o)
        {
            return Double.compare(f, o.f);
        }

        long key()
        {
            return ((long) (x & 0xFFFFF) << 44) | ((long) (z & 0xFFFFF) << 24) | ((y & 0xFF) << 4) | ((dir + 1) << 1) | (straight ? 1 : 0);
        }
    }

    /**
     * the route from a to b ({x, y, z} each, y the rail's height at the ends), as rail positions in order, or null if
     * there isn't one within reach (with why in reason[0])
     */
    public static List<int[]> route(World w, int[] a, int[] b, String[] reason)
    {
        if (Math.abs(a[0] - b[0]) + Math.abs(a[2] - b[2]) > MAX_LENGTH)
        {
            reason[0] = "they're too far apart (more than " + MAX_LENGTH + " blocks)";
            return null;
        }
        List<int[]> footprints = footprints();
        Map<Long, Integer> grounds = new HashMap<Long, Integer>();
        Map<Long, Double> best = new HashMap<Long, Double>();
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        Node start = new Node(a[0], a[1], a[2], -1, false, 0, h(a[0], a[1], a[2], b), null);
        open.add(start);
        best.put(start.key(), 0.0);
        int expanded = 0;
        while (!open.isEmpty())
        {
            Node n = open.poll();
            if (n.x == b[0] && n.z == b[2] && n.y == b[1])
            {
                return path(n);
            }
            Double seen = best.get(n.key());
            if (seen != null && seen < n.g)
            {
                continue;
            }
            if (++expanded > MAX_NODES)
            {
                reason[0] = "the land's too rough to find a way in time - try points closer together";
                return null;
            }
            for (int d = 0; d < 4; d++)
            {
                if (n.straight && d != n.dir)
                {
                    continue;
                }
                int x = n.x + DIRS[d][0], z = n.z + DIRS[d][1];
                Integer g = ground(w, grounds, x, z);
                if (g == null || inBuilding(footprints, x, z) && !(x == b[0] && z == b[2]))
                {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++)
                {
                    int y = n.y + dy;
                    // climbing: the lower cell (this one) must be straight, so the way in must be this way - and not
                    // one it just came down into, or it'd have to slope both ways at once
                    if (dy > 0 && (n.dir >= 0 && n.dir != d || n.straight))
                    {
                        continue;
                    }
                    // (the end marker's someone's block too: the line ends on it)
                    boolean goal = x == b[0] && z == b[2];
                    double step = !goal && placedInTheWay(w, x, y, z) ? -1 : cost(g, y, w, x, z);
                    if (step < 0)
                    {
                        continue;
                    }
                    if (dy != 0)
                    {
                        step += 0.5;
                    }
                    if (n.dir >= 0 && d != n.dir)
                    {
                        step += 0.3;
                    }
                    double ng = n.g + step;
                    Node m = new Node(x, y, z, d, dy < 0, ng, ng + h(x, y, z, b), n);
                    Double old = best.get(m.key());
                    if (old == null || ng < old)
                    {
                        best.put(m.key(), ng);
                        open.add(m);
                    }
                }
            }
        }
        reason[0] = "there's no way through (buildings, unloaded land, or cliffs too high)";
        return null;
    }

    /** something someone built where the rail, its deck or its headroom would go: never cleared for a railway */
    private static boolean placedInTheWay(World w, int x, int y, int z)
    {
        for (int yy = y - 1; yy <= y + 2; yy++)
        {
            if (ashjack.simukraftreloaded.common.PlacedBlocks.placed(w, x, yy, z))
            {
                return true;
            }
        }
        return false;
    }

    private static double h(int x, int y, int z, int[] b)
    {
        return Math.abs(x - b[0]) + Math.abs(z - b[2]) + Math.abs(y - b[1]);
    }

    /** the work for a rail at height y over ground g: <0 if it's not allowed */
    private static double cost(int g, int y, World w, int x, int z)
    {
        boolean water = w.getBlock(x, g, z).getMaterial().isLiquid();
        int above = y - (g + 1);
        if (above == 0)
        {
            return water ? 2.0 : 1.0; // on the ground (or a deck on the water)
        }
        if (above > 0)
        {
            return above > MAX_RAISE ? -1 : 1.0 + 1.5 * above; // an embankment or bridge
        }
        int depth = -above;
        if (water || depth > MAX_SINK)
        {
            return -1; // not under water
        }
        return 1.0 + 1.2 * depth; // a cutting, then a tunnel
    }

    private static Integer ground(World w, Map<Long, Integer> cache, int x, int z)
    {
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        if (cache.containsKey(k))
        {
            return cache.get(k);
        }
        Integer g = null;
        if (w.getChunkProvider().chunkExists(x >> 4, z >> 4))
        {
            int y = TownWall.ground(w, x, z);
            g = y < 2 ? null : y;
        }
        cache.put(k, g);
        return g;
    }

    private static List<int[]> footprints()
    {
        List<int[]> out = new ArrayList<int[]>();
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            try
            {
                ashjack.simukraftreloaded.proxies.CommonProxy.V3 con = b.isRoom() ? null : WarehouseStock.conBoxOf(b);
                int[] f = con == null ? null : WarehouseStock.footprint(b, b.layoutBox(con));
                if (f != null)
                {
                    out.add(f);
                }
            }
            catch (Exception e)
            {
                // can't say where it is
            }
        }
        return out;
    }

    private static boolean inBuilding(List<int[]> footprints, int x, int z)
    {
        for (int[] f : footprints)
        {
            if (x >= f[0] && x <= f[3] && z >= f[2] && z <= f[5])
            {
                return true;
            }
        }
        return false;
    }

    private static List<int[]> path(Node n)
    {
        List<int[]> out = new ArrayList<int[]>();
        for (Node m = n; m != null; m = m.from)
        {
            out.add(new int[] {m.x, m.y, m.z});
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * the rail's metadata at cell i of a route (vanilla rails): 0 north-south, 1 east-west, 2-5 climbing east, west,
     * north, south, 6-9 curves south-east, south-west, north-west, north-east
     */
    public static int railMeta(List<int[]> route, int i)
    {
        int[] c = route.get(i);
        int[] p = i > 0 ? route.get(i - 1) : null, n = i + 1 < route.size() ? route.get(i + 1) : null;
        // a slope: the lower cell climbs towards the higher neighbour
        if (n != null && n[1] > c[1])
        {
            return climb(n[0] - c[0], n[2] - c[2]);
        }
        if (p != null && p[1] > c[1])
        {
            return climb(p[0] - c[0], p[2] - c[2]);
        }
        int[] a = p != null ? new int[] {p[0] - c[0], p[2] - c[2]} : null;
        int[] b = n != null ? new int[] {n[0] - c[0], n[2] - c[2]} : null;
        if (a == null)
        {
            a = new int[] {-b[0], -b[1]};
        }
        if (b == null)
        {
            b = new int[] {-a[0], -a[1]};
        }
        boolean xAxis = a[0] != 0 && b[0] != 0, zAxis = a[1] != 0 && b[1] != 0;
        if (xAxis)
        {
            return 1;
        }
        if (zAxis)
        {
            return 0;
        }
        boolean east = a[0] > 0 || b[0] > 0, south = a[1] > 0 || b[1] > 0;
        return south ? (east ? 6 : 7) : (east ? 9 : 8);
    }

    private static int climb(int dx, int dz)
    {
        return dx > 0 ? 2 : dx < 0 ? 3 : dz < 0 ? 4 : 5;
    }

    /** whether the rail at i is straight (powered rails can't curve) */
    public static boolean straight(List<int[]> route, int i)
    {
        return railMeta(route, i) < 6;
    }
}
