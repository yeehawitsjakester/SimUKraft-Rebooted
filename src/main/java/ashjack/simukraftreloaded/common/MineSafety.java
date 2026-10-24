package ashjack.simukraftreloaded.common;

import java.util.ArrayList;

import ashjack.simukraftreloaded.blocks.functionality.MiningBox;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Where the mines are, so folk can be kept safe around them: miners wear a harness (no fall damage), nobody gets
 * shoved about at the rim, and everyone else keeps out of the pit. A mine is the rectangle its three markers span,
 * from just above the surface down to bedrock.
 */
public final class MineSafety
{
    private MineSafety() {}

    /** x0, z0, x1, z1, topY, dimension of each mine (server data) */
    public static ArrayList<int[]> areas()
    {
        ArrayList<int[]> out = new ArrayList<int[]>();
        for (MiningBox m : new ArrayList<MiningBox>(SimukraftReloaded.serverMiningBoxes))
        {
            V3[] marks = {m.marker1XYZ, m.marker2XYZ, m.marker3XYZ};
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, top = 0;
            int dim = 0;
            boolean any = false;
            for (V3 v : marks)
            {
                if (v == null)
                {
                    continue;
                }
                any = true;
                x0 = Math.min(x0, v.x.intValue()); x1 = Math.max(x1, v.x.intValue());
                z0 = Math.min(z0, v.z.intValue()); z1 = Math.max(z1, v.z.intValue());
                top = Math.max(top, v.y.intValue());
                dim = v.theDimension;
            }
            if (any)
            {
                out.add(new int[] {x0, z0, x1, z1, top + 1, dim});
            }
        }
        return out;
    }

    /** within margin blocks of a mine's pit (sideways), at or below its surface */
    public static boolean inMine(ArrayList<int[]> areas, int dim, int x, int y, int z, int margin)
    {
        for (int[] a : areas)
        {
            if (a[5] == dim && y <= a[4] && x >= a[0] - margin && x <= a[2] + margin && z >= a[1] - margin && z <= a[3] + margin)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean inMine(int dim, double x, double y, double z, int margin)
    {
        return inMine(areas(), dim, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), margin);
    }
}
