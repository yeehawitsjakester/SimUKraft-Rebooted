package ashjack.simukraftreloaded.core.building;

import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A watch tower: a 5x5 stone-brick shaft with arrow slits, a ladder up the inside to a platform behind battlements,
 * and its control box and a chest (the lookout's arrows) at the foot. Generated from its name (GEN-Tower_12_+x) like a
 * motel, so the ladder faces its wall whichever way it's built. A lookout posted here keeps watch from the platform
 * (see Posts and JobSoldier).
 */
public class TowerPlan
{
    public static final String PREFIX = "GEN-Tower_";
    public static final int MIN_HEIGHT = 8, MAX_HEIGHT = 24, SIZE = 5;

    public final int height;
    public final String dir;

    public TowerPlan(int height, String dir)
    {
        this.height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
        this.dir = dir == null || dir.isEmpty() ? "-x" : dir;
    }

    public static boolean isGenerated(String name)
    {
        return name != null && name.startsWith(PREFIX);
    }

    public String name()
    {
        return PREFIX + height + "_" + dir;
    }

    public static TowerPlan parse(String name)
    {
        if (!isGenerated(name))
        {
            return null;
        }
        try
        {
            String[] p = name.substring(PREFIX.length()).split("_");
            return new TowerPlan(Integer.parseInt(p[0]), p[1]);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public String title()
    {
        return "Watch Tower (" + height + " high)";
    }

    /** the platform's floor layer; the lookout stands on it */
    private int platform()
    {
        return height - 2;
    }

    public String blueprint()
    {
        int H = height, P = platform();
        char[][][] g = new char[H][SIZE][SIZE];
        for (int y = 0; y < H; y++)
        {
            for (int z = 0; z < SIZE; z++)
            {
                for (int x = 0; x < SIZE; x++)
                {
                    boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
                    char c;
                    if (y == 0 || y == P)
                    {
                        c = 'B'; // the floor, and the platform
                    }
                    else if (y == H - 1)
                    {
                        c = edge && (x + z) % 2 == 0 ? 'B' : 'A'; // battlements
                    }
                    else if (edge)
                    {
                        boolean middle = x == SIZE / 2 || z == SIZE / 2;
                        c = middle && y % 4 == 2 && y < P ? 'S' : 'B'; // arrow slits up the middle of each side
                    }
                    else
                    {
                        c = 'A';
                    }
                    g[y][z][x] = c;
                }
            }
        }
        // the way in at the front, the box and a chest at the foot, a light on the platform
        g[1][0][2] = 'A';
        g[2][0][2] = 'A';
        g[1][1][1] = '$';
        g[1][1][3] = 'C';
        g[P][1][1] = 'L';
        // the ladder up the back wall, through the platform
        for (int y = 1; y <= P; y++)
        {
            g[y][3][2] = 'H';
        }
        StringBuilder sb = new StringBuilder();
        sb.append(SIZE).append('x').append(SIZE).append('x').append(H).append('\n');
        sb.append("A=0:0;B=minecraft:stonebrick,0;S=minecraft:iron_bars,0;C=minecraft:chest,0;L=minecraft:glowstone,0;H=minecraft:ladder,")
                .append(ladderMeta()).append(";AU=Sim-U-Kraft;DESC=A watch tower: post a soldier as lookout, and they keep watch "
                        + "from the platform with a bow;CAT=Defence\n");
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

    /** the ladder hangs on the back wall (towards +ftb): 2 wall to the south, 3 north, 4 east, 5 west */
    private int ladderMeta()
    {
        int[] o = MotelPlan.toWorld(dir, 0, 0, 0, 0), w = MotelPlan.toWorld(dir, 0, 0, 0, 1);
        int dx = w[0] - o[0], dz = w[1] - o[1];
        return dz > 0 ? 2 : dz < 0 ? 3 : dx > 0 ? 4 : 5;
    }

    /** where the lookout stands: on the platform, in the middle */
    public V3 lookout(Building b, V3 con)
    {
        V3 o = b.layoutBox(con);
        int[] p = MotelPlan.toWorld(dir, o.x.intValue(), o.z.intValue(), 2, 2);
        return new V3((double) p[0], (double) (o.y.intValue() + platform() + 1), (double) p[1], o.theDimension);
    }
}
