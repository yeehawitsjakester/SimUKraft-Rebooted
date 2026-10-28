package ashjack.simukraftreloaded.core.town;

import java.io.File;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/** every town in the world (the server's, or the client's synced copy), and finding the one something is in */
public final class Towns
{
    private Towns() {}

    public static ArrayList<Town> serverTowns = new ArrayList<Town>();

    public static ArrayList<Town> all()
    {
        return SimukraftReloaded.isClientThread() ? ClientMirror.towns : serverTowns;
    }

    /** the town whose border this is inside, or null for the wilds */
    public static Town at(int dim, double x, double z)
    {
        for (Town t : all())
        {
            if (t.contains(dim, x, z))
            {
                return t;
            }
        }
        return null;
    }

    public static Town at(V3 v)
    {
        return v == null ? null : at(v.theDimension, v.x, v.z);
    }

    public static Town at(EntityPlayer p)
    {
        return at(p.dimension, p.posX, p.posZ);
    }

    /** a Sim's town: where they work, or else where they are */
    public static Town of(FolkData f)
    {
        Town t = f.employedAt == null ? null : at(f.employedAt);
        return t != null ? t : at(f.location);
    }

    public static Town byId(int id)
    {
        for (Town t : all())
        {
            if (t.id == id)
            {
                return t;
            }
        }
        return null;
    }

    public static Town byName(String name)
    {
        for (Town t : all())
        {
            if (t.name.equalsIgnoreCase(name))
            {
                return t;
            }
        }
        return null;
    }

    /** the towns a player owns or belongs to */
    public static ArrayList<Town> of(String player)
    {
        ArrayList<Town> out = new ArrayList<Town>();
        for (Town t : all())
        {
            if (t.isMember(player))
            {
                out.add(t);
            }
        }
        return out;
    }

    /** whether a town of this size at this centre would overlap another (ignoring one, when it's resizing) */
    public static Town overlapping(V3 centre, int radius, Town ignore)
    {
        for (Town t : all())
        {
            if (t != ignore && t.dimension() == centre.theDimension
                    && Math.abs(t.centre.x - centre.x) <= t.radius + radius
                    && Math.abs(t.centre.z - centre.z) <= t.radius + radius)
            {
                return t;
            }
        }
        return null;
    }

    /** how far outside a border a new building can be and still be taken into that town, and the room left around it */
    public static final int REACH = 48, MARGIN = 32;

    /**
     * a building going up in a town, or just outside one its builder belongs to: the border moves out, free, to take it
     * in with some room to spare - as far as it can without running into another town. Says which town grew, or null.
     */
    public static Town takeIn(int dim, int[] f, String player)
    {
        if (f == null || f.length < 6)
        {
            return null;
        }
        Town best = null;
        int bestNeed = Integer.MAX_VALUE;
        for (Town t : serverTowns)
        {
            if (t.centre == null || t.dimension() != dim)
            {
                continue;
            }
            int need = need(t, f);
            boolean inside = t.contains(dim, (f[0] + f[3]) / 2.0, (f[2] + f[5]) / 2.0);
            if (need - MARGIN - t.radius > REACH || !(inside || player == null || !t.isClaimed() || t.isMember(player)))
            {
                continue;
            }
            if (inside || need < bestNeed)
            {
                best = t;
                bestNeed = need;
                if (inside)
                {
                    break;
                }
            }
        }
        if (best == null || bestNeed <= best.radius)
        {
            return null;
        }
        int to = Math.min(Town.MAX_RADIUS, bestNeed);
        while (to > best.radius && overlapping(best.centre, to, best) != null)
        {
            to--;
        }
        if (to <= best.radius)
        {
            return null;
        }
        best.radius = to;
        best.save();
        return best;
    }

    /** the radius that would cover this footprint with room around it */
    private static int need(Town t, int[] f)
    {
        int cx = t.centre.x.intValue(), cz = t.centre.z.intValue();
        int r = Math.max(Math.max(Math.abs(f[0] - cx), Math.abs(f[3] - cx)), Math.max(Math.abs(f[2] - cz), Math.abs(f[5] - cz)));
        return r + MARGIN;
    }

    public static Town found(String name, String owner, V3 centre, int radius)
    {
        Town t = new Town();
        int id = 1;
        for (Town o : serverTowns)
        {
            id = Math.max(id, o.id + 1);
        }
        t.id = id;
        t.name = name;
        t.owner = owner;
        t.centre = centre.clone();
        t.radius = radius;
        t.founded = System.currentTimeMillis();
        serverTowns.add(t);
        t.save();
        return t;
    }

    public static void disband(Town t)
    {
        serverTowns.remove(t);
        File f = t.saveFile();
        if (f.exists())
        {
            f.delete();
        }
    }

    // ---------------------------------------------------------------- saving

    public static void loadAll()
    {
        serverTowns.clear();
        File folder = new File(SimukraftReloaded.getSavesDataFolder() + "Towns");
        File[] files = folder.listFiles();
        if (files != null)
        {
            for (File f : files)
            {
                if (f.getName().endsWith(".sk2"))
                {
                    Town t = Town.load(f);
                    if (t != null)
                    {
                        serverTowns.add(t);
                    }
                }
            }
        }
        if (serverTowns.isEmpty())
        {
            migrate();
        }
        takeInAll();
        SimukraftReloaded.log.info("Towns: " + serverTowns.size() + " loaded");
    }

    public static void saveAll()
    {
        for (Town t : new ArrayList<Town>(serverTowns))
        {
            t.save();
        }
    }

    /** buildings put up before borders grew by themselves (or past them): their towns take them in now */
    private static void takeInAll()
    {
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            try
            {
                V3 con = b.isRoom() ? null : WarehouseStock.conBoxOf(b);
                int[] f = con == null ? null : WarehouseStock.footprint(b, b.layoutBox(con));
                Town t = takeIn(con == null ? 0 : con.theDimension, f, null);
                if (t != null)
                {
                    SimukraftReloaded.log.info("Towns: " + t.name + "'s border moved out to " + (2 * t.radius + 1)
                            + " across to take in the " + b.displayNameWithoutPK);
                }
            }
            catch (Exception e)
            {
                // a building that can't say where it is stays where the border puts it
            }
        }
    }

    /**
     * a world from before towns: its buildings become one unclaimed town, big enough for all of them, and the old
     * shared credits become its treasury. The first player to /town claim it there owns it.
     */
    private static void migrate()
    {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, y = 64;
        int dim = 0, n = 0;
        for (Building b : SimukraftReloaded.serverBuildings)
        {
            if (b.primaryXYZ != null && b.primaryXYZ.theDimension == 0)
            {
                minX = Math.min(minX, b.primaryXYZ.x);
                maxX = Math.max(maxX, b.primaryXYZ.x);
                minZ = Math.min(minZ, b.primaryXYZ.z);
                maxZ = Math.max(maxZ, b.primaryXYZ.z);
                y = b.primaryXYZ.y;
                n++;
            }
        }
        if (n == 0)
        {
            return; // a new world: towns are founded with City Boxes
        }
        V3 centre = new V3(Math.floor((minX + maxX) / 2), y, Math.floor((minZ + maxZ) / 2), dim);
        int radius = (int) Math.max(Town.MIN_RADIUS, Math.min(Town.MAX_RADIUS, Math.max(maxX - minX, maxZ - minZ) / 2 + 24));
        Town t = found("Town", "", centre, radius);
        t.treasury = SimukraftReloaded.serverStates.credits;
        t.save();
        SimukraftReloaded.log.info("Towns: made an unclaimed town of the " + n + " buildings already here, radius " + radius
                + ", with the " + t.treasury + " credits as its treasury");
    }
}
