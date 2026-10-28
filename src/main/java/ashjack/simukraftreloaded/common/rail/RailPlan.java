package ashjack.simukraftreloaded.common.rail;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.blocks.functionality.Marker;
import ashjack.simukraftreloaded.common.jobs.Workshop;
import ashjack.simukraftreloaded.common.military.TownWall;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.building.Upgrades;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A railway a rail depot's builders are putting down: the route RailPlanner found between two markers, saved with the
 * depot (Railways/x_y_z_d.sk2). How far it's got is read from the world - a cell with its rail down is done - so it
 * carries on after a restart, and puts back any rail that goes missing.
 *
 * Each cell: the rail's space and two blocks of headroom cleared (a cutting, or a tunnel), something solid under it
 * (an embankment or a bridge deck, with a pillar down to the ground now and then), the roof propped where it's sand or
 * gravel, a light every so often in a tunnel, and the rail, shaped from the route. Every few blocks, and on every
 * slope, it's a powered rail on a block of redstone, so a cart keeps going uphill.
 *
 * What it takes comes from the depot's chests, then the town's warehouses, and the treasury buys what they haven't.
 */
public final class RailPlan
{
    public static final float PRICE_RAIL = 0.5f, PRICE_POWERED = 3f, PRICE_COBBLE = 0.05f, PRICE_REDSTONE = 2f, PRICE_LIGHT = 1f;
    private static final int POWERED_EVERY = 6, LIGHT_EVERY = 8, PILLAR_EVERY = 4;

    public final String depot;
    public List<int[]> route = new ArrayList<int[]>();
    public String from = "", to = "";
    public boolean finished;
    private int cursor;

    private static final Map<String, RailPlan> plans = new HashMap<String, RailPlan>();
    /** cells a builder's working on -> who */
    private static final Map<Long, String> claims = new HashMap<Long, String>();

    private RailPlan(String depot)
    {
        this.depot = depot;
    }

    public static synchronized RailPlan of(Building depot)
    {
        if (depot == null || depot.primaryXYZ == null)
        {
            return null;
        }
        String k = depot.primaryXYZ.toString();
        RailPlan p = plans.get(k);
        if (p == null)
        {
            p = load(k);
            plans.put(k, p);
        }
        return p;
    }

    public static boolean isDepot(Building b)
    {
        return b != null && b.displayName != null && (b.displayName.contains("Rail Depot") || b.displayName.contains("Railway Depot"));
    }

    // ---------------------------------------------------------------- planning

    /** a new route from the first marker placed to the second; says how it went */
    public static synchronized String planFromMarkers(Building depot, World w)
    {
        ArrayList<Marker> markers = BlockMarker.serverMarkers;
        if (markers.size() < 2)
        {
            return "Place two markers first: where the railway starts, then where it ends";
        }
        V3 a = markers.get(0).toV3(), b = markers.get(1).toV3();
        if (a.theDimension != b.theDimension || w.provider.dimensionId != a.theDimension)
        {
            return "Both markers need to be in this world";
        }
        // the land between them (and a little round) loaded, so the planner can see it all
        int x0 = Math.min(a.x.intValue(), b.x.intValue()) - 32 >> 4, x1 = Math.max(a.x.intValue(), b.x.intValue()) + 32 >> 4;
        int z0 = Math.min(a.z.intValue(), b.z.intValue()) - 32 >> 4, z1 = Math.max(a.z.intValue(), b.z.intValue()) + 32 >> 4;
        if ((x1 - x0 + 1) * (z1 - z0 + 1) <= 900)
        {
            for (int cx = x0; cx <= x1; cx++)
            {
                for (int cz = z0; cz <= z1; cz++)
                {
                    w.getChunkProvider().loadChunk(cx, cz);
                }
            }
        }
        String[] why = new String[1];
        List<int[]> route = RailPlanner.route(w, new int[] {a.x.intValue(), a.y.intValue(), a.z.intValue()},
                new int[] {b.x.intValue(), b.y.intValue(), b.z.intValue()}, why);
        if (route == null)
        {
            return "Couldn't find a way for the railway: " + why[0];
        }
        RailPlan p = of(depot);
        p.route = route;
        p.from = a.x.intValue() + ", " + a.z.intValue();
        p.to = b.x.intValue() + ", " + b.z.intValue();
        p.finished = false;
        p.cursor = 0;
        p.save();
        int tunnel = 0, raised = 0, ground = 0;
        for (int[] c : route)
        {
            int g = TownWall.ground(w, c[0], c[2]);
            if (c[1] > g + 1)
            {
                raised++;
            }
            else if (c[1] <= g)
            {
                tunnel++;
            }
            else
            {
                ground++;
            }
        }
        return "Planned a railway from " + p.from + " to " + p.to + ": " + route.size() + " blocks (" + ground + " on the ground, "
                + raised + " raised or bridged, " + tunnel + " in cuttings and tunnels). Rail builders at the depot will lay it";
    }

    // ---------------------------------------------------------------- building

    /** the next cell to build, claimed for this builder, or -1 */
    public synchronized int next(World w, String builder)
    {
        int n = route.size();
        for (int k = 0; k < n; k++)
        {
            int i = (cursor + k) % n;
            int[] c = route.get(i);
            long key = key(c);
            String who = claims.get(key);
            if (done(w, i) || who != null && !who.equals(builder) || !w.getChunkProvider().chunkExists(c[0] >> 4, c[2] >> 4))
            {
                continue;
            }
            claims.put(key, builder);
            cursor = i;
            return i;
        }
        return -1;
    }

    public synchronized void release(int i, String builder)
    {
        if (i >= 0 && i < route.size() && builder.equals(claims.get(key(route.get(i)))))
        {
            claims.remove(key(route.get(i)));
        }
    }

    public boolean done(World w, int i)
    {
        int[] c = route.get(i);
        Block b = w.getBlock(c[0], c[1], c[2]);
        return b == Blocks.rail || b == Blocks.golden_rail;
    }

    /** how many cells are down */
    public int laid(World w)
    {
        int n = 0;
        for (int i = 0; i < route.size(); i++)
        {
            int[] c = route.get(i);
            if (w.getChunkProvider().chunkExists(c[0] >> 4, c[2] >> 4) && done(w, i))
            {
                n++;
            }
        }
        return n;
    }

    /** builds cell i; false if it couldn't be paid for */
    public boolean build(World w, int i, Building depotB)
    {
        int[] c = route.get(i);
        int x = c[0], y = c[1], z = c[2];
        Town town = Towns.at(depotB.primaryXYZ);
        // the rail's space and two of headroom
        for (int yy = y; yy <= y + 2; yy++)
        {
            Block b = w.getBlock(x, yy, z);
            if (!b.isAir(w, x, yy, z) && b != Blocks.rail && b != Blocks.golden_rail && b.getBlockHardness(w, x, yy, z) >= 0
                    && w.getTileEntity(x, yy, z) == null)
            {
                w.setBlockToAir(x, yy, z);
            }
        }
        // a roof of sand or gravel would fall in
        if (w.getBlock(x, y + 3, z) instanceof BlockFalling)
        {
            if (!take(w, depotB, town, Item.getItemFromBlock(Blocks.cobblestone), PRICE_COBBLE))
            {
                return false;
            }
            w.setBlock(x, y + 3, z, Blocks.cobblestone, 0, 3);
        }
        // a light in the tunnel now and then
        boolean tunnel = w.getBlock(x, y + 3, z).getMaterial().isSolid() && TownWall.ground(w, x, z) > y + 3;
        if (tunnel && i % LIGHT_EVERY == 0 && w.getBlock(x, y + 3, z) != Blocks.glowstone)
        {
            if (take(w, depotB, town, Item.getItemFromBlock(Blocks.glowstone), PRICE_LIGHT))
            {
                w.setBlock(x, y + 3, z, Blocks.glowstone, 0, 3);
            }
        }
        boolean slope = i + 1 < route.size() && route.get(i + 1)[1] != y || i > 0 && route.get(i - 1)[1] != y;
        boolean powered = (i % POWERED_EVERY == 0 || slope) && RailPlanner.straight(route, i);
        // something solid under it: the land, a deck (a block of redstone for a powered rail), and a pillar sometimes
        Block under = w.getBlock(x, y - 1, z);
        if (powered)
        {
            if (under != Blocks.redstone_block)
            {
                if (!take(w, depotB, town, Item.getItemFromBlock(Blocks.redstone_block), PRICE_REDSTONE))
                {
                    return false;
                }
                w.setBlock(x, y - 1, z, Blocks.redstone_block, 0, 3);
            }
        }
        else if (!under.getMaterial().isSolid() || under.getMaterial().isLiquid())
        {
            if (!take(w, depotB, town, Item.getItemFromBlock(Blocks.cobblestone), PRICE_COBBLE))
            {
                return false;
            }
            w.setBlock(x, y - 1, z, Blocks.cobblestone, 0, 3);
            if (i % PILLAR_EVERY == 0)
            {
                for (int yy = y - 2; yy > 1 && yy > y - 2 - RailPlanner.MAX_RAISE; yy--)
                {
                    Block b = w.getBlock(x, yy, z);
                    if (b.getMaterial().isSolid() && !b.getMaterial().isLiquid())
                    {
                        break;
                    }
                    if (!take(w, depotB, town, Item.getItemFromBlock(Blocks.cobblestone), PRICE_COBBLE))
                    {
                        break;
                    }
                    w.setBlock(x, yy, z, Blocks.cobblestone, 0, 3);
                }
            }
        }
        // the rail, shaped from the route (set without updates, or it'd reshape itself from whatever's next to it)
        int meta = RailPlanner.railMeta(route, i);
        if (powered)
        {
            if (!take(w, depotB, town, Item.getItemFromBlock(Blocks.golden_rail), PRICE_POWERED))
            {
                return false;
            }
            w.setBlock(x, y, z, Blocks.golden_rail, meta, 2);
        }
        else
        {
            if (!take(w, depotB, town, Item.getItemFromBlock(Blocks.rail), PRICE_RAIL))
            {
                return false;
            }
            w.setBlock(x, y, z, Blocks.rail, meta, 2);
        }
        w.markBlockForUpdate(x, y, z);
        if (powered)
        {
            w.notifyBlockOfNeighborChange(x, y, z, Blocks.redstone_block); // it switches on (a plain rail would reshape)
        }
        checkFinished(w);
        return true;
    }

    private void checkFinished(World w)
    {
        if (finished || laid(w) < route.size())
        {
            return;
        }
        finished = true;
        save();
        int[] s = route.get(0);
        EntityMinecartEmpty cart = new EntityMinecartEmpty(w, s[0] + 0.5, s[1] + 0.1, s[2] + 0.5);
        w.spawnEntityInWorld(cart);
        SimukraftReloaded.sendChat("The railway from " + from + " to " + to + " is finished - there's a cart waiting at " + from);
    }

    /** one of these from the depot's chests, the town's warehouses, or bought */
    private static boolean take(World w, Building depot, Town town, Item item, float price)
    {
        if (!WarehouseStock.take(Workshop.chests(w, depot.primaryXYZ), item, 1).isEmpty())
        {
            return true;
        }
        if (town != null)
        {
            for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
            {
                if (Upgrades.isWarehouse(b) && b.buildingComplete && b.primaryXYZ != null && town.contains(b.primaryXYZ)
                        && !WarehouseStock.take(WarehouseStock.chestsInside(b, b.primaryXYZ, w), item, 1).isEmpty())
                {
                    return true;
                }
            }
            if (Economy.balance(town) >= price)
            {
                Economy.spend(town, price);
                return true;
            }
        }
        return false;
    }

    /** rails still to lay, for the keeper (a smithy makes them) */
    public int railsWanted(World w)
    {
        if (finished || route.isEmpty())
        {
            return 0;
        }
        return Math.min(128, route.size() - laid(w));
    }

    // ---------------------------------------------------------------- saving

    private static File file(String depot)
    {
        return new File(SimukraftReloaded.getSavesDataFolder() + "Railways" + File.separator + depot.replace(',', '_') + ".sk2");
    }

    private void save()
    {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("from|" + from);
        lines.add("to|" + to);
        lines.add("finished|" + finished);
        for (int[] c : route)
        {
            lines.add("cell|" + c[0] + "," + c[1] + "," + c[2]);
        }
        File f = file(depot);
        f.getParentFile().mkdirs();
        SimukraftReloaded.saveSK2(f.getAbsolutePath(), lines);
    }

    private static RailPlan load(String depot)
    {
        RailPlan p = new RailPlan(depot);
        File f = file(depot);
        if (!f.exists())
        {
            return p;
        }
        for (String line : SimukraftReloaded.loadSK2(f.getAbsolutePath()))
        {
            int bar = line.indexOf('|');
            if (bar < 0)
            {
                continue;
            }
            String k = line.substring(0, bar), v = line.substring(bar + 1);
            try
            {
                if (k.equals("from")) p.from = v;
                else if (k.equals("to")) p.to = v;
                else if (k.equals("finished")) p.finished = Boolean.parseBoolean(v);
                else if (k.equals("cell"))
                {
                    String[] c = v.split(",");
                    p.route.add(new int[] {Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
                }
            }
            catch (Exception e)
            {
                // a broken line
            }
        }
        return p;
    }

    public static synchronized void clear()
    {
        plans.clear();
        claims.clear();
    }

    private static long key(int[] c)
    {
        return ((long) (c[0] & 0x3FFFFFF) << 38) | ((long) (c[2] & 0x3FFFFFF) << 12) | (c[1] & 0xFFF);
    }
}
