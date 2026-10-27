package ashjack.simukraftreloaded.core.building;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Turns a decorative building - a skyscraper, a tower, a castle - into flats. On every floor, each space you can
 * stand in that's walled in and roofed over is somewhere to live: a small one is one flat, a big open office floor is
 * split into several, and each gets a Room Box (as an apartment) in a corner, sized for a single, a couple or a family
 * by how much room it has. The ground floor is left as the lobby. Stairwells, ladder shafts, rooftops and balconies
 * are too small or not roofed in, so they're left out.
 *
 * Some towers are only a shell, with nothing inside: those are fitted out first, with a floor every four blocks
 * (three-high rooms) and a ladder up through them all.
 *
 * Like a motel, the result is a blueprint generated from its name: GEN-Apts_+x_other_PKID405-Sears Tower is the tower
 * built as flats from the start; GEN-AptsConv_... is the same for a tower that's already standing, with every one of
 * its own blocks left as it is (Building.KEEP), so a builder only adds the floors, ladder and Room Boxes.
 */
public final class ApartmentConversion
{
    private ApartmentConversion() {}

    public static final String PREFIX = "GEN-Apts_", CONVERT_PREFIX = "GEN-AptsConv_";

    /** the parts of a generated name */
    public static class Name
    {
        public final String dir, type, original;
        public final boolean convert;

        Name(String dir, String type, String original, boolean convert)
        {
            this.dir = dir;
            this.type = type;
            this.original = original;
            this.convert = convert;
        }
    }

    public static boolean isGenerated(String name)
    {
        return name != null && (name.startsWith(PREFIX) || name.startsWith(CONVERT_PREFIX));
    }

    public static String name(String dir, String type, String original, boolean convert)
    {
        return (convert ? CONVERT_PREFIX : PREFIX) + dir + "_" + type + "_" + original;
    }

    public static Name parse(String name)
    {
        if (!isGenerated(name))
        {
            return null;
        }
        boolean convert = name.startsWith(CONVERT_PREFIX);
        String[] p = name.substring((convert ? CONVERT_PREFIX : PREFIX).length()).split("_", 3);
        return p.length < 3 ? null : new Name(p[0], p[1], p[2], convert);
    }

    /** whether a building could be turned into flats: decorative ones (homes already are, shops and farms are busy) */
    public static boolean eligible(Building b)
    {
        return b != null && "other".equals(b.type) && !MotelPlan.isGenerated(b.displayName) && !isGenerated(b.displayName);
    }

    /** how many flats a generated blueprint has */
    public static int flats(Building b)
    {
        int n = 0;
        if (b != null && b.structure != null)
        {
            for (Block block : b.structure)
            {
                if (block == SimukraftReloadedBlocks.roomBox)
                {
                    n++;
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- fitting out

    private static final int STOREY = 4;

    /**
     * adds floors to a hollow interior, a ladder up through them and the flats' Room Boxes, to a blueprint already
     * loaded into b. Returns the indices of every cell it changed.
     */
    public static List<Integer> fitOut(Building b, String dir)
    {
        List<Integer> changed = new ArrayList<Integer>();
        int L = b.ltrCount, F = b.ftbCount, H = b.layerCount;

        // floors: in any column of open space inside the walls, a floor four blocks above the last one (or above the
        // ground, for a shell with no floor of its own), as long as there's room for another storey above it and
        // something over the top
        for (int z = 1; z < F - 1; z++)
        {
            for (int x = 1; x < L - 1; x++)
            {
                int lastSolid = -1; // the ground under it
                for (int y = 0; y < H; y++)
                {
                    if (solidAt(b, x, y, z))
                    {
                        lastSolid = y;
                    }
                    else if (y - lastSolid == STOREY && enclosed(b, x, y, z) && roomAbove(b, x, y, z))
                    {
                        set(b, x, y, z, Blocks.double_stone_slab, 0, changed);
                        lastSolid = y;
                    }
                }
            }
        }

        // ladders from the lowest floor to the top one, through every floor between. A tower that narrows as it goes
        // up gets one ladder after another, each starting on the floor where the last one comes out.
        int start = Integer.MIN_VALUE;
        for (int n = 0; n < 8; n++)
        {
            int top = ladder(b, dir, start, changed);
            if (top == Integer.MIN_VALUE || top == start)
            {
                break;
            }
            start = top;
        }

        for (Unit u : plan(b))
        {
            set(b, u.ltr, u.layer, u.ftb, SimukraftReloadedBlocks.roomBox, 0, changed);
        }
        return changed;
    }

    /**
     * the best ladder from the floor at layer start (any floor, if it's MIN_VALUE): the column with the most floors
     * above it, against a wall the builder puts up before it (west or north of it in the blueprint) or it falls off
     * when the blocks around it go down. It may cut through floors, never walls or furniture. Returns the top floor it
     * reaches, or MIN_VALUE if there's nowhere for one.
     */
    private static int ladder(Building b, String dir, int start, List<Integer> changed)
    {
        int L = b.ltrCount, F = b.ftbCount, H = b.layerCount;
        int bestX = -1, bestZ = -1, bestFloors = 1, bestFrom = 0, bestTo = 0, bestTop = 0, bestDx = 0, bestDz = 0;
        for (int z = 1; z < F - 1; z++)
        {
            for (int x = 1; x < L - 1; x++)
            {
                // floors in this column: solid with room to stand on it (the ground counts as one, under layer 0)
                int lowest = Integer.MAX_VALUE, highest = -1, floors = 0;
                for (int y = start == Integer.MIN_VALUE ? -1 : start; y < H - 2; y++)
                {
                    if ((y < 0 || solidAt(b, x, y, z)) && openAt(b, x, y + 1, z) && openAt(b, x, y + 2, z)
                            && enclosed(b, x, y + 1, z))
                    {
                        lowest = Math.min(lowest, y);
                        highest = y;
                        floors++;
                    }
                }
                if (floors <= bestFloors || start != Integer.MIN_VALUE && lowest != start)
                {
                    continue;
                }
                int from = lowest + 1, to = Math.min(H - 1, highest + 1);
                for (int[] d : new int[][] {{-1, 0}, {0, -1}})
                {
                    boolean ok = true;
                    for (int y = from; ok && y <= to; y++)
                    {
                        boolean floorHere = solidAt(b, x, y, z) && openAt(b, x, y + 1, z);
                        ok = solidAt(b, x + d[0], y, z + d[1]) && (openAt(b, x, y, z) || floorHere);
                    }
                    if (ok)
                    {
                        bestX = x;
                        bestZ = z;
                        bestFloors = floors;
                        bestFrom = from;
                        bestTo = to;
                        bestTop = highest;
                        bestDx = d[0];
                        bestDz = d[1];
                        break;
                    }
                }
            }
        }
        if (bestX < 0)
        {
            return Integer.MIN_VALUE;
        }
        int[] wall = MotelPlan.stepToWorld(dir, bestDx, bestDz);
        // a ladder's meta is the face of its wall it's on: 2 wall to the south, 3 north, 4 east, 5 west
        int meta = wall[1] > 0 ? 2 : wall[1] < 0 ? 3 : wall[0] > 0 ? 4 : 5;
        for (int y = bestFrom; y <= bestTo; y++)
        {
            set(b, bestX, y, bestZ, Blocks.ladder, meta, changed);
        }
        return bestTop;
    }

    private static void set(Building b, int x, int y, int z, Block block, int meta, List<Integer> changed)
    {
        int i = (y * b.ftbCount + z) * b.ltrCount + x;
        b.structure[i] = block;
        b.structureSub[i] = meta;
        if (!changed.contains(i))
        {
            changed.add(i);
        }
    }

    /** walled in on all four sides on this layer */
    private static boolean enclosed(Building b, int x, int y, int z)
    {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs)
        {
            int cx = x, cz = z;
            boolean wall = false;
            while (cx >= 0 && cz >= 0 && cx < b.ltrCount && cz < b.ftbCount)
            {
                if (solidAt(b, cx, y, cz))
                {
                    wall = true;
                    break;
                }
                cx += d[0];
                cz += d[1];
            }
            if (!wall)
            {
                return false;
            }
        }
        return true;
    }

    /** a whole storey of open space above y, and a roof somewhere over it */
    private static boolean roomAbove(Building b, int x, int y, int z)
    {
        for (int up = y + 1; up < y + STOREY; up++)
        {
            if (!openAt(b, x, up, z))
            {
                return false;
            }
        }
        for (int up = y + STOREY; up < b.layerCount; up++)
        {
            if (solidAt(b, x, up, z))
            {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- finding the flats

    /** where a flat's Room Box goes (blueprint ltr, layer, ftb) and how many it's for (1, 2 or 4) */
    public static class Unit
    {
        public final int ltr, layer, ftb, size;

        Unit(int ltr, int layer, int ftb, int size)
        {
            this.ltr = ltr;
            this.layer = layer;
            this.ftb = ftb;
            this.size = size;
        }
    }

    /** spaces smaller than this are corridors, stairwells and cupboards */
    private static final int MIN_AREA = 8;
    /** about this much floor per flat when a space is split up */
    private static final int AREA_PER_FLAT = 30;
    private static final int MAX_PER_SPACE = 8, MAX_FLATS = 96;

    /** the flats in a blueprint; empty if its structure isn't loaded */
    public static List<Unit> plan(Building b)
    {
        List<Unit> units = new ArrayList<Unit>();
        if (b == null || b.structure == null || b.ltrCount <= 0 || b.ftbCount <= 0 || b.layerCount <= 0
                || b.structure.length < b.ltrCount * b.ftbCount * b.layerCount)
        {
            return units;
        }
        int L = b.ltrCount, F = b.ftbCount, H = b.layerCount;

        // the spaces on each floor: standable, roofed over and away from the outside edge
        List<List<int[]>> spaces = new ArrayList<List<int[]>>();
        List<Integer> spaceLayer = new ArrayList<Integer>();
        boolean[] seen = new boolean[L * F];
        for (int y = 1; y < H - 1; y++)
        {
            java.util.Arrays.fill(seen, false);
            for (int z = 1; z < F - 1; z++)
            {
                for (int x = 1; x < L - 1; x++)
                {
                    if (seen[z * L + x] || !indoors(b, x, y, z))
                    {
                        continue;
                    }
                    List<int[]> space = new ArrayList<int[]>();
                    ArrayDeque<int[]> todo = new ArrayDeque<int[]>();
                    todo.add(new int[] {x, z});
                    seen[z * L + x] = true;
                    while (!todo.isEmpty())
                    {
                        int[] c = todo.poll();
                        space.add(c);
                        int[][] next = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
                        for (int[] n : next)
                        {
                            if (n[0] >= 1 && n[0] < L - 1 && n[1] >= 1 && n[1] < F - 1 && !seen[n[1] * L + n[0]]
                                    && indoors(b, n[0], y, n[1]))
                            {
                                seen[n[1] * L + n[0]] = true;
                                todo.add(n);
                            }
                        }
                    }
                    if (space.size() >= MIN_AREA)
                    {
                        spaces.add(space);
                        spaceLayer.add(y);
                    }
                }
            }
        }

        // the lowest floor with any space is the lobby, if there are floors above it to live on
        int lobby = Integer.MAX_VALUE, top = -1;
        for (int y : spaceLayer)
        {
            lobby = Math.min(lobby, y);
            top = Math.max(top, y);
        }
        boolean keepLobby = top > lobby;

        // floor by floor, so a tower with too many is thinned out evenly rather than full at the bottom only
        TreeMap<Integer, List<Unit>> byFloor = new TreeMap<Integer, List<Unit>>();
        int total = 0;
        for (int i = 0; i < spaces.size(); i++)
        {
            int y = spaceLayer.get(i);
            if (keepLobby && y == lobby)
            {
                continue;
            }
            List<int[]> space = spaces.get(i);
            int flats = Math.max(1, Math.min(MAX_PER_SPACE, Math.round(space.size() / (float) AREA_PER_FLAT)));
            int size = space.size() / flats < 12 ? 1 : space.size() / flats < AREA_PER_FLAT ? 2 : 4;
            List<Unit> floor = byFloor.get(y);
            if (floor == null)
            {
                byFloor.put(y, floor = new ArrayList<Unit>());
            }
            for (int[] spot : spread(b, space, y, flats))
            {
                floor.add(new Unit(spot[0], y, spot[1], size));
                total++;
            }
        }
        int every = Math.max(1, (total + MAX_FLATS - 1) / MAX_FLATS), n = 0;
        for (List<Unit> floor : byFloor.values())
        {
            if (n++ % every == 0)
            {
                for (Unit u : floor)
                {
                    if (units.size() < MAX_FLATS)
                    {
                        units.add(u);
                    }
                }
            }
        }
        return units;
    }

    /**
     * up to n spots for Room Boxes, as far apart as they'll go: in corners if there are any (where a box is least in
     * the way), against a wall otherwise, and always with somewhere beside it to stand
     */
    private static List<int[]> spread(Building b, List<int[]> space, int y, int n)
    {
        List<int[]> corners = new ArrayList<int[]>(), walls = new ArrayList<int[]>();
        for (int[] c : space)
        {
            int solid = 0, open = 0;
            int[][] next = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
            for (int[] d : next)
            {
                if (solidAt(b, d[0], y, d[1]))
                {
                    solid++;
                }
                else if (indoors(b, d[0], y, d[1]))
                {
                    open++;
                }
            }
            if (open == 0)
            {
                continue;
            }
            if (solid >= 2 && open <= 2)
            {
                corners.add(c);
            }
            else if (solid >= 1)
            {
                walls.add(c);
            }
        }
        List<int[]> pool = new ArrayList<int[]>(corners);
        if (corners.size() < n)
        {
            pool.addAll(walls);
        }
        List<int[]> picked = new ArrayList<int[]>();
        if (pool.isEmpty())
        {
            return picked;
        }
        // farthest-point: start anywhere, then keep adding the spot farthest from those picked
        picked.add(pool.get(0));
        while (picked.size() < n)
        {
            int[] best = null;
            int bestD = -1;
            for (int[] c : pool)
            {
                int d = Integer.MAX_VALUE;
                for (int[] p : picked)
                {
                    d = Math.min(d, Math.abs(c[0] - p[0]) + Math.abs(c[1] - p[1]));
                }
                if (d > bestD)
                {
                    bestD = d;
                    best = c;
                }
            }
            if (best == null || bestD < 3)
            {
                break; // not enough room for another without crowding
            }
            picked.add(best);
        }
        return picked;
    }

    // ---------------------------------------------------------------- reading the blueprint

    private static Block at(Building b, int x, int y, int z)
    {
        if (x < 0 || y < 0 || z < 0 || x >= b.ltrCount || y >= b.layerCount || z >= b.ftbCount)
        {
            return null;
        }
        return b.structure[(y * b.ftbCount + z) * b.ltrCount + x];
    }

    /** something to stand on, or a wall */
    private static boolean solidAt(Building b, int x, int y, int z)
    {
        Block block = at(b, x, y, z);
        if (block == null || block == SimukraftReloadedBlocks.livingBlock || block == SimukraftReloadedBlocks.specialBlock)
        {
            return false;
        }
        Material m = block.getMaterial();
        return m.blocksMovement() && m != Material.leaves && !m.isLiquid();
    }

    /** room for a body (torches, carpets, ladders and signs don't count as in the way) */
    private static boolean openAt(Building b, int x, int y, int z)
    {
        Block block = at(b, x, y, z);
        return block != null && !solidAt(b, x, y, z) && !block.getMaterial().isLiquid();
    }

    /** somewhere to stand, inside: a floor, two blocks of room, and a ceiling somewhere above */
    private static boolean indoors(Building b, int x, int y, int z)
    {
        if (!solidAt(b, x, y - 1, z) || !openAt(b, x, y, z) || !openAt(b, x, y + 1, z))
        {
            return false;
        }
        for (int up = y + 2; up < b.layerCount; up++)
        {
            if (solidAt(b, x, up, z))
            {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- once built

    /**
     * names and sizes the flats a builder has just finished, built from the constructor box at con: "Sears Tower flat
     * 12", for a single, a couple or a family as planned, with their standing spots worked out now the walls are up
     */
    public static void settleFlats(Building b, World world, V3 con)
    {
        Name n = parse(b.displayName);
        if (n == null || con == null || world == null)
        {
            return;
        }
        String title = b.displayNameWithoutPK.replace(" (apartments)", "");
        int number = 0;
        for (Building room : SimukraftReloaded.buildings())
        {
            if (room.isRoom() && room.displayName.startsWith(title + " flat "))
            {
                number++;
            }
        }
        // planned again from the whole blueprint (a conversion's own has its old blocks left out)
        Building full = Building.fromGenerated(name(n.dir, n.type, n.original, false), n.type);
        for (Unit u : plan(withoutBoxes(full)))
        {
            int[] p = MotelPlan.toWorld(n.dir, con.x.intValue(), con.z.intValue(), u.ltr, u.ftb);
            Building room = Building.getBuilding(new V3((double) p[0], (double) (con.y.intValue() + u.layer), (double) p[1], con.theDimension));
            if (room != null && room.isRoom() && !room.displayName.startsWith(title + " flat "))
            {
                room.displayName = title + " flat " + (++number);
                room.displayNameWithoutPK = room.displayName;
                room.roomSize = u.size;
                room.updateRoomRent();
                room.refreshLivingSpot(world);
            }
        }
    }

    /** the blueprint as it was planned from: the Room Boxes it gained taken out again (they'd fill the spaces) */
    private static Building withoutBoxes(Building b)
    {
        Building copy = new Building();
        copy.ltrCount = b.ltrCount;
        copy.ftbCount = b.ftbCount;
        copy.layerCount = b.layerCount;
        copy.structure = b.structure.clone();
        for (int i = 0; i < copy.structure.length; i++)
        {
            if (copy.structure[i] == SimukraftReloadedBlocks.roomBox || copy.structure[i] == null)
            {
                copy.structure[i] = Blocks.air;
            }
        }
        return copy;
    }
}
