package ashjack.simukraftreloaded.core.building;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * A motel made to order instead of read from a blueprint file: straight, L or U shaped, one or two floors, so many
 * rooms. Everything about it is in its name (GEN-Motel_L_2_5_3_+x), so the blueprint is generated again wherever it's
 * needed - the client's preview, the server's builder, after a restart - and comes out the same every time.
 *
 * The office and the main wing run left to right from the constructor box; L and U motels have side wings running
 * away from it, with the walkways facing the courtyard between. It grows only away from the box (the main wing longer,
 * the side wings deeper), so a bigger motel always contains the smaller one in the same place and an upgrade only
 * builds what's new. An upgrade's name also carries the size it grew from (..._+x_4_2): everything the two sizes have
 * in common is left alone, so nothing players have put in the rooms gets cleared.
 *
 * Block metadata isn't turned by the builder, so the build direction is part of the plan and doors and stairs are
 * generated facing the right way for it.
 */
public class MotelPlan
{
    public static final String PREFIX = "GEN-Motel_";
    public static final int MAX_ROOMS = 12;

    /** S (straight), L or U */
    public final char shape;
    public final int floors;
    /** rooms on each floor of the main wing (besides the office) */
    public final int roomsA;
    /** rooms on each floor of each side wing (none for a straight motel) */
    public final int roomsB;
    public final String dir;
    /** the size this is an upgrade from, or -1 */
    public final int fromA, fromB;

    /** a cell the builder leaves as it is (structure null, structureSub KEEP) */
    public static final char KEEP_CHAR = '@';
    public static final char HOTEL_ROOM_CHAR = '#';

    private static final int MODULE = 4, DEPTH = 8, STOREY = 4;

    public MotelPlan(char shape, int floors, int roomsA, int roomsB, String dir)
    {
        this(shape, floors, roomsA, roomsB, dir, -1, -1);
    }

    public MotelPlan(char shape, int floors, int roomsA, int roomsB, String dir, int fromA, int fromB)
    {
        this.shape = shape == 'L' || shape == 'U' ? shape : 'S';
        this.floors = Math.max(1, Math.min(2, floors));
        this.roomsA = Math.max(minA(this.shape), Math.min(MAX_ROOMS, roomsA));
        this.roomsB = this.shape == 'S' ? 0 : Math.max(1, Math.min(MAX_ROOMS, roomsB));
        this.dir = dir == null || dir.isEmpty() ? "-x" : dir;
        this.fromA = fromA;
        this.fromB = fromB;
    }

    /** a U needs a few rooms along the main wing, or there's no courtyard between the side wings */
    public static int minA(char shape)
    {
        return shape == 'U' ? 4 : 1;
    }

    public static boolean isGenerated(String name)
    {
        return name != null && name.startsWith(PREFIX);
    }

    public String name()
    {
        String n = PREFIX + shape + "_" + floors + "_" + roomsA + "_" + roomsB + "_" + dir;
        return fromA < 0 ? n : n + "_" + fromA + "_" + fromB;
    }

    /** the plan in a name, or null if it isn't one */
    public static MotelPlan parse(String name)
    {
        if (!isGenerated(name))
        {
            return null;
        }
        try
        {
            String[] p = name.substring(PREFIX.length()).split("_");
            int fa = p.length > 6 ? Integer.parseInt(p[5]) : -1, fb = p.length > 6 ? Integer.parseInt(p[6]) : -1;
            return new MotelPlan(p[0].charAt(0), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], fa, fb);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** the same motel, finished: no longer an upgrade */
    public MotelPlan finished()
    {
        return new MotelPlan(shape, floors, roomsA, roomsB, dir);
    }

    /** this motel with more rooms, as an upgrade from this size */
    public MotelPlan grownTo(int newA, int newB)
    {
        return new MotelPlan(shape, floors, newA, newB, dir, roomsA, roomsB);
    }

    public int rooms()
    {
        int perFloor = roomsA + (shape == 'S' ? 0 : shape == 'L' ? roomsB : 2 * roomsB);
        // upstairs, the office's floor is a room too
        return perFloor * floors + (floors > 1 ? 1 : 0);
    }

    public String shapeName()
    {
        return shape == 'L' ? "L-shaped" : shape == 'U' ? "U-shaped" : "Straight";
    }

    public String title()
    {
        return "Motel (" + shapeName() + ", " + (floors == 1 ? "1 floor" : floors + " floors") + ", " + rooms() + " rooms)";
    }

    // ---------------------------------------------------------------- size

    private static int wingALength(int roomsA)
    {
        return MODULE * (roomsA + 1) + 1; // the office, then the rooms
    }

    private static int wingBLength(int roomsB)
    {
        return MODULE * roomsB + 1;
    }

    public int ltrCount()
    {
        return Math.max(wingALength(roomsA), shape == 'S' ? 0 : DEPTH);
    }

    public int ftbCount()
    {
        return shape == 'S' ? DEPTH : DEPTH + wingBLength(roomsB);
    }

    public int layerCount()
    {
        return STOREY * floors + 1;
    }

    // ---------------------------------------------------------------- generating

    /** [layer][ftb][ltr] */
    private char[][][] grid;
    private final Map<Character, String> key = new LinkedHashMap<Character, String>();

    private static final int WING_A = 0, WING_B = 1, WING_C = 2;

    /** the blueprint, in the same text format as the files in the buildings folders */
    public String blueprint()
    {
        char[][][] g = cells();
        if (fromA >= 0)
        {
            char[][][] old = new MotelPlan(shape, floors, fromA, fromB, dir).cells();
            for (int y = 0; y < g.length; y++)
            {
                for (int z = 0; z < old[y].length; z++)
                {
                    for (int x = 0; x < old[y][z].length; x++)
                    {
                        if (z < g[y].length && x < g[y][z].length && g[y][z][x] == old[y][z][x])
                        {
                            g[y][z][x] = KEEP_CHAR;
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ltrCount()).append('x').append(ftbCount()).append('x').append(layerCount()).append('\n');
        for (Map.Entry<Character, String> e : key.entrySet())
        {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        sb.append("AU=Sim-U-Kraft;DESC=A motel: visitors from out of town take the rooms for a few nights and pay for "
                + "their stay. Add more rooms later from its control box;CAT=Temporary housing\n");
        for (char[][] layer : g)
        {
            for (char[] row : layer)
            {
                sb.append(row);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** the finished motel's cells, [layer][ftb][ltr] */
    private char[][][] cells()
    {
        key.clear();
        key.put('A', "0:0");
        key.put('a', "minecraft:stone_slab,0");      // walkway, ground floor
        key.put('b', "minecraft:stone_slab,8");      // walkway upstairs (top half, level with the floors)
        key.put('c', "minecraft:planks,0");          // floors
        key.put('d', "minecraft:log,0");             // corner posts
        key.put('e', "minecraft:stained_hardened_clay,0"); // walls
        key.put('f', "minecraft:glass_pane,0");
        key.put('g', "minecraft:fence,0");
        key.put('h', "minecraft:double_wooden_slab,1"); // roof: full blocks, so the lights in it sit flush
        key.put('i', "minecraft:glowstone,0");
        key.put('j', "minecraft:wool,14");           // the bed
        key.put('k', "minecraft:cobblestone,0");     // foundations under the walls
        key.put('l', "minecraft:bookshelf,0");
        key.put('r', "minecraft:wooden_door,8");     // a door's top half

        int L = ltrCount(), F = ftbCount(), H = layerCount();
        grid = new char[H][F][L];
        for (char[][] layer : grid)
        {
            for (char[] row : layer)
            {
                java.util.Arrays.fill(row, 'A');
            }
        }

        int lenA = wingALength(roomsA);
        wing(WING_A, lenA, true);
        if (shape != 'S')
        {
            wing(WING_B, wingBLength(roomsB), false);
        }
        if (shape == 'U')
        {
            wing(WING_C, wingBLength(roomsB), false);
        }
        return grid;
    }

    /**
     * One wing, in its own coordinates: u along it, v across it (0-1 the walkway, 2 the front wall with the doors,
     * 3-6 inside, 7 the back wall).
     */
    private void wing(int w, int len, boolean office)
    {
        int top = STOREY * floors;
        for (int f = 0; f < floors; f++)
        {
            int base = STOREY * f;
            for (int u = 0; u < len; u++)
            {
                boolean column = u % MODULE == 0;
                int room = u / MODULE, at = u % MODULE; // which module, and where in it
                boolean isOffice = office && room == 0 && f == 0;

                // floor: walkway, foundations under the walls, planks inside
                for (int v = 0; v < DEPTH; v++)
                {
                    char floor;
                    if (v < 2)
                    {
                        if (f == 0)
                        {
                            floor = 'a';
                        }
                        else if (office && v == 0 && u <= 2)
                        {
                            floor = 'A'; // the stairwell
                        }
                        else if (office && v == 0 && u == 3)
                        {
                            continue; // the top stair goes here
                        }
                        else
                        {
                            floor = 'b';
                        }
                    }
                    else
                    {
                        floor = f == 0 && (column || v == 2 || v == DEPTH - 1) ? 'k' : 'c';
                    }
                    if (get(w, u, v, base) != 'i') // the light in the ceiling of the room below
                    {
                        set(w, u, v, base, floor);
                    }
                }

                // walls
                for (int y = base + 1; y <= base + 3; y++)
                {
                    int ly = y - base; // 1 to 3
                    for (int v = 2; v < DEPTH; v++)
                    {
                        boolean front = v == 2, back = v == DEPTH - 1;
                        char c;
                        if (column)
                        {
                            c = front || back ? 'd' : 'e';
                        }
                        else if (front)
                        {
                            // door on the left of each room, a window on the right
                            int door = isOffice ? 1 : (office && room == 0 ? 3 : 1);
                            if (at == door && ly == 1)
                            {
                                c = doorChar(w, true);
                            }
                            else if (at == door && ly == 2)
                            {
                                c = 'r';
                            }
                            else if (at == (door == 1 ? 3 : 1) && ly == 2)
                            {
                                c = 'f';
                            }
                            else
                            {
                                c = 'e';
                            }
                        }
                        else if (back)
                        {
                            boolean streetDoor = isOffice && shape != 'S' && at == 2;
                            if (streetDoor && ly == 1)
                            {
                                c = doorChar(w, false);
                            }
                            else if (streetDoor && ly == 2)
                            {
                                c = 'r';
                            }
                            else
                            {
                                c = at == 2 && ly == 2 ? 'f' : 'e';
                            }
                        }
                        else
                        {
                            c = 'A';
                        }
                        set(w, u, v, y, c);
                    }
                }

                if (!column)
                {
                    if (isOffice)
                    {
                        if (at == 3)
                        {
                            set(w, u, 4, base + 1, '$');
                        }
                        if (at == 1)
                        {
                            set(w, u, 6, base + 1, 'l');
                            set(w, u, 6, base + 2, 'l');
                        }
                    }
                    else if (at == 1)
                    {
                        set(w, u, 6, base + 1, HOTEL_ROOM_CHAR);
                    }
                    else if (at == 3)
                    {
                        set(w, u, 5, base + 1, 'j');
                        set(w, u, 6, base + 1, 'j');
                    }
                    // a light in the middle of the ceiling (the next floor, or the roof)
                    if (at == 2)
                    {
                        set(w, u, 4, base + STOREY, 'i');
                    }
                }

                // walkway posts, and the railing upstairs
                if (column && !(office && f > 0 && u == 0) && !(office && f == 0 && u == 0 && floors > 1))
                {
                    for (int y = base + 1; y <= base + 3; y++)
                    {
                        set(w, u, 0, y, 'g');
                    }
                }
                if (f > 0 && !(office && u <= 3) && !isJunction(w, u))
                {
                    set(w, u, 0, base + 1, 'g');
                }
                if (f > 0 && u == len - 1)
                {
                    set(w, u, 1, base + 1, 'g');
                }
            }
        }

        // the stairs up the end of the office's walkway
        if (office && floors > 1)
        {
            for (int s = 0; s < 4; s++)
            {
                set(w, s, 0, s + 1, stairChar(w));
            }
        }

        // the roof over all of it, walkway and all, with a light over the walkway in front of each room
        for (int u = 0; u < len; u++)
        {
            for (int v = 0; v < DEPTH; v++)
            {
                char c = get(w, u, v, top);
                if (c != 'i')
                {
                    set(w, u, v, top, v == 1 && u % MODULE == 2 ? 'i' : 'h');
                }
            }
        }
    }

    /** where a side wing's walkway meets the main wing's: no railing across it */
    private boolean isJunction(int w, int u)
    {
        if (w != WING_A || shape == 'S')
        {
            return false;
        }
        int lenA = wingALength(roomsA);
        return u == DEPTH - 2 || u == DEPTH - 1 || shape == 'U' && (u == lenA - DEPTH || u == lenA - DEPTH + 1);
    }

    /** wing coordinates to the blueprint's ltr, ftb */
    private int[] map(int w, int u, int v)
    {
        if (w == WING_A)
        {
            return shape == 'S' ? new int[] {u, v} : new int[] {u, DEPTH - 1 - v};
        }
        if (w == WING_B)
        {
            return new int[] {DEPTH - 1 - v, DEPTH + u};
        }
        return new int[] {wingALength(roomsA) - DEPTH + v, DEPTH + u};
    }

    private void set(int w, int u, int v, int y, char c)
    {
        int[] p = map(w, u, v);
        if (y >= 0 && y < grid.length && p[1] >= 0 && p[1] < grid[y].length && p[0] >= 0 && p[0] < grid[y][p[1]].length)
        {
            grid[y][p[1]][p[0]] = c;
        }
    }

    private char get(int w, int u, int v, int y)
    {
        int[] p = map(w, u, v);
        if (y >= 0 && y < grid.length && p[1] >= 0 && p[1] < grid[y].length && p[0] >= 0 && p[0] < grid[y][p[1]].length)
        {
            return grid[y][p[1]][p[0]];
        }
        return 'A';
    }

    // ---------------------------------------------------------------- facing

    /** a wing direction (+u or +v) as a world x,z step */
    private int[] worldStep(int w, boolean alongU)
    {
        // the step in blueprint ltr, ftb
        int dl, df;
        if (w == WING_A)
        {
            dl = alongU ? 1 : 0;
            df = alongU ? 0 : (shape == 'S' ? 1 : -1);
        }
        else if (w == WING_B)
        {
            dl = alongU ? 0 : -1;
            df = alongU ? 1 : 0;
        }
        else
        {
            dl = alongU ? 0 : 1;
            df = alongU ? 1 : 0;
        }
        // blueprint to world, as JobBuilder lays it out
        int[] L, Fw;
        if (dir.equals("+x"))
        {
            L = new int[] {0, -1};
            Fw = new int[] {-1, 0};
        }
        else if (dir.equals("-z"))
        {
            L = new int[] {-1, 0};
            Fw = new int[] {0, 1};
        }
        else if (dir.equals("+z"))
        {
            L = new int[] {1, 0};
            Fw = new int[] {0, -1};
        }
        else
        {
            L = new int[] {0, 1};
            Fw = new int[] {1, 0};
        }
        return new int[] {dl * L[0] + df * Fw[0], dl * L[1] + df * Fw[1]};
    }

    /** a step in the blueprint (along ltr, along ftb) as a world x, z step, for a build direction */
    public static int[] stepToWorld(String dir, int dl, int df)
    {
        int[] o = toWorld(dir, 0, 0, 0, 0), s = toWorld(dir, 0, 0, dl, df);
        return new int[] {s[0] - o[0], s[1] - o[1]};
    }

    /** a door's bottom half: inwards for the walkway doors, outwards (towards the street) for the office's back one */
    private char doorChar(int w, boolean inwards)
    {
        int[] s = worldStep(w, false);
        if (!inwards)
        {
            s = new int[] {-s[0], -s[1]};
        }
        // the way the player faces placing it: 0 east, 1 south, 2 west, 3 north
        int meta = s[0] > 0 ? 0 : s[1] > 0 ? 1 : s[0] < 0 ? 2 : 3;
        char c = (char) ('n' + meta);
        key.put(c, "minecraft:wooden_door," + meta);
        return c;
    }

    /** stairs climbing along the wing */
    private char stairChar(int w)
    {
        int[] s = worldStep(w, true);
        // stairs: 0 climbs east, 1 west, 2 south, 3 north
        int meta = s[0] > 0 ? 0 : s[0] < 0 ? 1 : s[1] > 0 ? 2 : 3;
        char c = (char) ('s' + meta);
        key.put(c, "minecraft:oak_stairs," + meta);
        return c;
    }

    // ---------------------------------------------------------------- room to build

    /**
     * What's in the way of building this from a constructor box at cx,cy,cz, or null if nothing is. Plants, snow and
     * natural ground are fine (the builder clears them); anything else - a wall, a chest, another building - is in
     * the way. An upgrade only checks what's new. Never loads chunks: unloaded counts as in the way.
     */
    public String obstruction(World world, int cx, int cy, int cz)
    {
        char[][][] g = cells();
        char[][][] old = fromA >= 0 ? new MotelPlan(shape, floors, fromA, fromB, dir).cells() : null;
        for (int y = 0; y < g.length; y++)
        {
            for (int z = 0; z < g[y].length; z++)
            {
                for (int x = 0; x < g[y][z].length; x++)
                {
                    if (old != null && z < old[y].length && x < old[y][z].length)
                    {
                        continue; // part of what's already built
                    }
                    int[] p = toWorld(cx, cz, x, z);
                    int wy = cy + y;
                    if (!world.blockExists(p[0], wy, p[1]))
                    {
                        return "too far away to check at " + p[0] + "," + wy + "," + p[1];
                    }
                    Block b = world.getBlock(p[0], wy, p[1]);
                    if (!clearable(world, b, p[0], wy, p[1]))
                    {
                        return b.getLocalizedName() + " in the way at " + p[0] + "," + wy + "," + p[1];
                    }
                }
            }
        }
        return null;
    }

    public int[] toWorld(int cx, int cz, int ltr, int ftb)
    {
        return toWorld(dir, cx, cz, ltr, ftb);
    }

    /** a blueprint ltr, ftb to world x, z for a build direction, the way JobBuilder lays it out */
    public static int[] toWorld(String dir, int cx, int cz, int ltr, int ftb)
    {
        if (dir.equals("+x"))
        {
            return new int[] {cx - 1 - ftb, cz - ltr};
        }
        if (dir.equals("-z"))
        {
            return new int[] {cx - ltr, cz + 1 + ftb};
        }
        if (dir.equals("+z"))
        {
            return new int[] {cx + ltr, cz - 1 - ftb};
        }
        return new int[] {cx + 1 + ftb, cz + ltr};
    }

    private static boolean clearable(World world, Block b, int x, int y, int z)
    {
        if (world.getTileEntity(x, y, z) != null)
        {
            return false; // chests, furnaces...
        }
        if (b.isAir(world, x, y, z) || b.isReplaceable(world, x, y, z))
        {
            return true;
        }
        Material m = b.getMaterial();
        return m == Material.plants || m == Material.vine || m == Material.leaves || m == Material.snow
                || m == Material.craftedSnow || m == Material.grass || m == Material.ground || m == Material.sand
                || m == Material.clay || b == Blocks.stone || b == Blocks.sandstone || b == Blocks.gravel
                || b == Blocks.cactus || b == Blocks.reeds || (b == Blocks.log || b == Blocks.log2) && inATree(world, x, y, z);
    }

    /** logs with leaves on them: a tree, not someone's log cabin */
    private static boolean inATree(World world, int x, int y, int z)
    {
        for (int dy = 0; dy <= 6; dy++)
        {
            Block above = world.getBlock(x, y + dy, z);
            if (above.getMaterial() == Material.leaves)
            {
                return true;
            }
            if (above != Blocks.log && above != Blocks.log2)
            {
                return false;
            }
        }
        return false;
    }
}
