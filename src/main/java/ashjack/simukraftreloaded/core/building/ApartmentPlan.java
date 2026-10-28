package ashjack.simukraftreloaded.core.building;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.World;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * An apartment building made to order, like a motel: apartments (brick, couples' flats, up to 6 floors), condos
 * (stone, family flats, up to 8) or a tower (glass and stone, up to 20), so many flats along a corridor, on one side
 * of it or both, so many floors high. Everything about it is in its name (GEN-Block_C_4_2_6_+x), so the blueprint is
 * generated again wherever it's needed and comes out the same every time.
 *
 * A lobby at the left end has the way in and a ladder up through every floor; the corridor runs from it past every
 * flat's door. Each flat has a window, a light, and a Room Box (as an apartment), which is what Sims move into; when
 * it's built, the flats are named and sized (settle). Block metadata isn't turned by the builder, so the build
 * direction is part of the plan and the doors and ladder are generated facing the right way for it.
 */
public class ApartmentPlan
{
    public static final String PREFIX = "GEN-Block_";
    public static final int MAX_ACROSS = 8;

    /** A (apartments), C (condos) or T (tower) */
    public final char kind;
    /** flats along the corridor, on each side */
    public final int across;
    /** 1: flats on one side of the corridor, 2: both */
    public final int rows;
    public final int floors;
    public final String dir;

    private static final int STOREY = 4, HALL = 3;

    public ApartmentPlan(char kind, int across, int rows, int floors, String dir)
    {
        this.kind = kind == 'C' || kind == 'T' ? kind : 'A';
        this.across = Math.max(1, Math.min(MAX_ACROSS, across));
        this.rows = rows == 1 ? 1 : 2;
        this.floors = Math.max(1, Math.min(maxFloors(this.kind), floors));
        this.dir = dir == null || dir.isEmpty() ? "-x" : dir;
    }

    public static int maxFloors(char kind)
    {
        return kind == 'T' ? 20 : kind == 'C' ? 8 : 6;
    }

    public static char nextKind(char kind)
    {
        return kind == 'A' ? 'C' : kind == 'C' ? 'T' : 'A';
    }

    public static boolean isGenerated(String name)
    {
        return name != null && name.startsWith(PREFIX);
    }

    public String name()
    {
        return PREFIX + kind + "_" + across + "_" + rows + "_" + floors + "_" + dir;
    }

    public static ApartmentPlan parse(String name)
    {
        if (!isGenerated(name))
        {
            return null;
        }
        try
        {
            String[] p = name.substring(PREFIX.length()).split("_");
            return new ApartmentPlan(p[0].charAt(0), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public String kindName()
    {
        return kind == 'C' ? "Condos" : kind == 'T' ? "Tower" : "Apartments";
    }

    public int flats()
    {
        return across * rows * floors;
    }

    /** who a flat is for: 1 a single, 2 a couple, 4 a family (Building.roomSize) */
    public int flatSize()
    {
        return kind == 'C' ? 4 : 2;
    }

    public String title()
    {
        return kindName() + " (" + flats() + " flats, " + (floors == 1 ? "1 floor" : floors + " floors") + ")";
    }

    // ---------------------------------------------------------------- size

    /** a flat's inside, across and deep */
    private int w()
    {
        return kind == 'C' ? 7 : 5;
    }

    private int d()
    {
        return kind == 'C' ? 6 : 5;
    }

    public int ltrCount()
    {
        return 2 + HALL + across * (w() + 1);
    }

    public int ftbCount()
    {
        return rows == 2 ? 2 * d() + 6 : d() + 5;
    }

    public int layerCount()
    {
        return STOREY * floors + 1;
    }

    /** the corridor's two rows (ftb) */
    private int corridor()
    {
        return rows == 2 ? d() + 2 : 1;
    }

    // ---------------------------------------------------------------- generating

    private char[][][] grid;
    private final Map<Character, String> key = new LinkedHashMap<Character, String>();

    public String blueprint()
    {
        char[][][] g = cells();
        StringBuilder sb = new StringBuilder();
        sb.append(ltrCount()).append('x').append(ftbCount()).append('x').append(layerCount()).append('\n');
        for (Map.Entry<Character, String> e : key.entrySet())
        {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        sb.append("AU=Sim-U-Kraft;DESC=" + kindName() + " made to order: flats along a corridor, a ladder up from the "
                + "lobby. Sims move into the flats and pay rent;CAT=Housing\n");
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

    private char[][][] cells()
    {
        key.clear();
        key.put('A', "0:0");
        key.put('O', kind == 'A' ? "minecraft:brick_block,0" : "minecraft:stonebrick,0"); // outside walls
        key.put('I', kind == 'T' ? "minecraft:wool,0" : kind == 'C' ? "minecraft:planks,1" : "minecraft:planks,0"); // between flats
        key.put('P', kind == 'C' ? "minecraft:planks,1" : "minecraft:planks,0"); // flats' floors
        key.put('S', "minecraft:double_stone_slab,0"); // corridor, lobby and roof
        key.put('G', "minecraft:glass_pane,0");
        key.put('W', "minecraft:glass,0");
        key.put('L', "minecraft:glowstone,0");
        key.put('R', "ashjacksimukraftreloaded:SUKroombox,0"); // a flat (a Room Box as an apartment)
        key.put('H', "minecraft:ladder," + ladderMeta());
        key.put('N', "minecraft:wooden_door," + doorMeta(true));  // into a flat on the far side of the corridor
        key.put('M', "minecraft:wooden_door," + doorMeta(false)); // into a flat on the near side
        key.put('Q', "minecraft:wooden_door,8"); // a door's top half

        int L = ltrCount(), F = ftbCount(), H = layerCount(), W = w(), D = d(), c0 = corridor();
        grid = new char[H][F][L];
        for (int f = 0; f < floors; f++)
        {
            int base = STOREY * f;
            for (int z = 0; z < F; z++)
            {
                for (int x = 0; x < L; x++)
                {
                    boolean edge = x == 0 || z == 0 || x == L - 1 || z == F - 1;
                    boolean open = x >= 1 && x <= HALL || z == c0 || z == c0 + 1; // lobby and corridor
                    grid[base][z][x] = edge ? 'O' : open ? 'S' : 'P';
                    for (int y = base + 1; y <= base + 3; y++)
                    {
                        grid[y][z][x] = edge ? 'O' : open ? 'A' : wallAt(x, z) ? 'I' : 'A';
                    }
                }
            }
            for (int k = 0; k < across; k++)
            {
                int x0 = 2 + HALL + k * (W + 1); // a flat's first column inside
                for (int side = 0; side < rows; side++)
                {
                    boolean far = rows == 2 && side == 1 || rows == 1; // across the corridor from the front
                    int z0 = far ? c0 + 3 : 1, zWall = far ? c0 + 2 : D + 1, zOut = far ? F - 1 : 0;
                    // its door from the corridor, its light, its window, and its Room Box in the far corner
                    grid[base + 1][zWall][x0 + 1] = far ? 'N' : 'M';
                    grid[base + 2][zWall][x0 + 1] = 'Q';
                    for (int x = x0; x < x0 + W; x++)
                    {
                        if (kind == 'T')
                        {
                            for (int y = base + 1; y <= base + 3; y++)
                            {
                                grid[y][zOut][x] = 'W';
                            }
                        }
                        else if (Math.abs(x - (x0 + W / 2)) <= (kind == 'C' ? 1 : 0))
                        {
                            grid[base + 2][zOut][x] = 'G';
                        }
                    }
                    grid[base + 1][far ? z0 + D - 1 : z0][x0 + W - 1] = 'R';
                }
            }
            // the way in, on the ground floor
            if (f == 0)
            {
                grid[1][0][2] = 'A';
                grid[2][0][2] = 'A';
            }
        }
        // the roof
        for (int z = 0; z < F; z++)
        {
            for (int x = 0; x < L; x++)
            {
                grid[H - 1][z][x] = 'S';
            }
        }
        // lights in every ceiling (the floor above, or the roof): one in each flat, and along the corridor and lobby.
        // After all the floors, or each floor laid would cover the lights of the one below
        for (int f = 0; f < floors; f++)
        {
            int ceiling = STOREY * (f + 1);
            for (int k = 0; k < across; k++)
            {
                int x0 = 2 + HALL + k * (W + 1);
                for (int side = 0; side < rows; side++)
                {
                    boolean far = rows == 2 && side == 1 || rows == 1;
                    grid[ceiling][(far ? c0 + 3 : 1) + D / 2][x0 + W / 2] = 'L';
                }
            }
            for (int x = 2; x < L - 1; x += 4)
            {
                grid[ceiling][c0][x] = 'L';
            }
        }
        // the ladder up the lobby's end wall, through every floor to the top one
        for (int y = 1; y <= STOREY * (floors - 1); y++)
        {
            grid[y][c0][1] = 'H';
        }
        return grid;
    }

    /** a wall between flats, or between the flats and the corridor or lobby */
    private boolean wallAt(int x, int z)
    {
        int W = w(), c0 = corridor();
        if (x == 1 + HALL)
        {
            return true; // the lobby's side
        }
        if ((x - (1 + HALL)) % (W + 1) == 0)
        {
            return true; // between two flats
        }
        return z == c0 - 1 && rows == 2 || z == c0 + 2;
    }

    // ---------------------------------------------------------------- facing

    private int[] step(int dl, int df)
    {
        int[] o = MotelPlan.toWorld(dir, 0, 0, 0, 0), s = MotelPlan.toWorld(dir, 0, 0, dl, df);
        return new int[] {s[0] - o[0], s[1] - o[1]};
    }

    /** the ladder hangs on the lobby's end wall (towards ltr 0): 2 wall to the south, 3 north, 4 east, 5 west */
    private int ladderMeta()
    {
        int[] wall = step(-1, 0);
        return wall[1] > 0 ? 2 : wall[1] < 0 ? 3 : wall[0] > 0 ? 4 : 5;
    }

    /** a door's bottom half, opening into its flat: the way someone faces placing it, 0 east, 1 south, 2 west, 3 north */
    private int doorMeta(boolean awayFromFront)
    {
        int[] in = step(0, awayFromFront ? 1 : -1);
        return in[0] > 0 ? 0 : in[1] > 0 ? 1 : in[0] < 0 ? 2 : 3;
    }

    // ---------------------------------------------------------------- once it's built

    /** each flat's Room Box named (Condos flat 7) and sized for who it's for, with somewhere to stand worked out */
    public static void settle(Building b, World world, V3 con)
    {
        ApartmentPlan plan = parse(b.displayName);
        if (plan == null || world == null || con == null)
        {
            return;
        }
        V3 o = b.layoutBox(con);
        String prefix = plan.kindName() + " flat ";
        int number = 0;
        for (Building room : SimukraftReloaded.buildings())
        {
            if (room.isRoom() && room.displayName != null && room.displayName.startsWith(prefix))
            {
                number++;
            }
        }
        char[][][] g = plan.cells();
        for (int y = 0; y < g.length; y++)
        {
            for (int z = 0; z < g[y].length; z++)
            {
                for (int x = 0; x < g[y][z].length; x++)
                {
                    if (g[y][z][x] != 'R')
                    {
                        continue;
                    }
                    int[] p = MotelPlan.toWorld(plan.dir, o.x.intValue(), o.z.intValue(), x, z);
                    Building room = Building.getBuilding(new V3((double) p[0], (double) (o.y.intValue() + y), (double) p[1], o.theDimension));
                    if (room != null && room.isRoom() && (room.displayName == null || !room.displayName.startsWith(prefix)))
                    {
                        room.displayName = prefix + (++number);
                        room.displayNameWithoutPK = room.displayName;
                        room.roomSize = plan.flatSize();
                        room.updateRoomRent();
                        room.refreshLivingSpot(world);
                        room.saveThisBuilding();
                    }
                }
            }
        }
    }
}
