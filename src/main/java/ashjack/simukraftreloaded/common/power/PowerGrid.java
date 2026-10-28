package ashjack.simukraftreloaded.common.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.jobs.Workshop;
import ashjack.simukraftreloaded.common.military.TownWall;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.building.MotelPlan;
import ashjack.simukraftreloaded.core.building.Upgrades;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A town's power grid, run on EnderIO (see EnderIO). A power station (Sim-U-Power Station) makes it: photovoltaic
 * cells on its roof and two Stirling generators burning coal from its chests, joined by energy conduits to a
 * Dimensional Transceiver sending on the town's channel. Linemen (JobLineman) build and keep it:
 *
 *  - every finished building in town gets a power point by its control box and a Powered Light on it. A building
 *    near the station is wired: a conduit line buried a block under the ground (disguised as what it replaced, so it
 *    doesn't show) from under the station to under the point, rising through the floor. One further off, or that a
 *    line can't reach without crossing another building or water, gets a transceiver receiving on the channel.
 *  - a masonry with the electric furnace upgrade gets an Alloy Smelter by its point: its stonemasons smelt with it,
 *    faster and with no coal.
 *  - the station's generators are kept fuelled, and anything that goes missing is put back.
 *
 * What it's built from comes out of the town's warehouses when they have it (EnderIO's own conduits, transceivers,
 * lights and smelters), and is paid for from the treasury when they don't.
 */
public final class PowerGrid
{
    private PowerGrid() {}

    /** how far (in blocks along the ground) a building can be from the station and still be wired */
    public static final int WIRE_REACH = 40;

    public static final float PRICE_CONDUIT = 2f, PRICE_TRANSCEIVER = 200f, PRICE_LIGHT = 15f, PRICE_SMELTER = 100f;

    /** a job for a lineman */
    public static class Task
    {
        public enum Kind
        {
            CONDUIT, TRANSCEIVER, TUNE_SEND, TUNE_RECEIVE, LIGHT, SMELTER, REFUEL
        }

        public final Kind kind;
        public final int x, y, z;
        public final String what;
        final String claim;

        Task(Kind kind, int x, int y, int z, String what, String claim)
        {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.what = what;
            this.claim = claim;
        }
    }

    /** buildings (and the station) a lineman is working on -> who */
    private static final Map<String, String> claims = new HashMap<String, String>();

    public static String channel(Town t)
    {
        return "Sim-U-Kraft " + t.name + " #" + t.id;
    }

    public static boolean isStation(Building b)
    {
        return b != null && b.displayName != null && b.displayName.contains("Power Station");
    }

    public static Building station(Town t)
    {
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (isStation(b) && b.buildingComplete && b.primaryXYZ != null && t.contains(b.primaryXYZ))
            {
                return b;
            }
        }
        return null;
    }

    public static boolean hasGrid(Town t)
    {
        return t != null && station(t) != null && EnderIO.available();
    }

    // ---------------------------------------------------------------- the station

    /** a cell of the station's blueprint in the world */
    private static int[] cell(Building b, V3 con, int ltr, int layer, int ftb)
    {
        V3 o = b.layoutBox(con);
        int[] p = MotelPlan.toWorld(b.buildDirection, o.x.intValue(), o.z.intValue(), ltr, ftb);
        return new int[] {p[0], o.y.intValue() + layer, p[1]};
    }

    /** the station's conduits: under the cells on the roof, down to the machines, and on down under the floor */
    private static List<int[]> stationConduits(Building b, V3 con)
    {
        List<int[]> out = new ArrayList<int[]>();
        for (int ftb = 2; ftb <= 4; ftb++)
        {
            for (int ltr = 2; ltr <= 4; ltr++)
            {
                out.add(cell(b, con, ltr, 4, ftb));
            }
        }
        for (int layer = 3; layer >= -1; layer--)
        {
            out.add(cell(b, con, 3, layer, 3));
        }
        return out;
    }

    private static int[] stationTransceiver(Building b, V3 con)
    {
        return cell(b, con, 3, 1, 2);
    }

    private static int[][] stationGenerators(Building b, V3 con)
    {
        return new int[][] {cell(b, con, 2, 1, 3), cell(b, con, 4, 1, 3)};
    }

    // ---------------------------------------------------------------- what to do next

    public static synchronized Task next(Town town, World w, String lineman)
    {
        if (town == null || w == null || !EnderIO.available())
        {
            return null;
        }
        Building st = station(town);
        V3 con = st == null ? null : WarehouseStock.conBoxOf(st);
        if (st == null || con == null || st.buildDirection == null)
        {
            return null;
        }
        String stKey = "station@" + st.primaryXYZ;
        if (free(stKey, lineman))
        {
            for (int[] c : stationConduits(st, con))
            {
                if (loaded(w, c) && !EnderIO.isConduit(w, c[0], c[1], c[2]))
                {
                    return claim(new Task(Task.Kind.CONDUIT, c[0], c[1], c[2], "Wiring up the power station", stKey), lineman);
                }
            }
            int[] t = stationTransceiver(st, con);
            if (loaded(w, t) && EnderIO.isTransceiver(w, t[0], t[1], t[2]) && !tuned(w, t, channel(town), true))
            {
                return claim(new Task(Task.Kind.TUNE_SEND, t[0], t[1], t[2], "Setting the station's transceiver to send", stKey), lineman);
            }
            for (int[] g : stationGenerators(st, con))
            {
                if (loaded(w, g) && EnderIO.isStirling(w, g[0], g[1], g[2]) && fuel(w, g) < 8 && coalAt(w, st) > 0)
                {
                    return claim(new Task(Task.Kind.REFUEL, g[0], g[1], g[2], "Stoking the generators", stKey), lineman);
                }
            }
        }
        int[] outlet = cell(st, con, 3, -1, 3);
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b == st || b.isRoom() || !b.buildingComplete || b.primaryXYZ == null || !town.contains(b.primaryXYZ)
                    || b.primaryXYZ.theDimension != town.dimension())
            {
                continue;
            }
            String key = "b@" + b.primaryXYZ;
            if (!free(key, lineman) || !loaded(w, new int[] {b.primaryXYZ.x.intValue(), 0, b.primaryXYZ.z.intValue()}))
            {
                continue;
            }
            if (b.powerPoint == null && !plan(b, w, st, con, outlet))
            {
                continue; // nowhere by its box for a light
            }
            Task t = forBuilding(town, w, b, st, con, outlet, key);
            if (t != null)
            {
                return claim(t, lineman);
            }
        }
        return null;
    }

    private static Task forBuilding(Town town, World w, Building b, Building st, V3 con, int[] outlet, String key)
    {
        V3 p = b.powerPoint;
        int px = p.x.intValue(), py = p.y.intValue(), pz = p.z.intValue();
        if ("wire".equals(b.powerVia))
        {
            List<int[]> line = line(w, st, con, outlet, b, p);
            if (line == null)
            {
                b.powerVia = "radio"; // the way's been built over since: a transceiver instead
                return null;
            }
            for (int[] c : line)
            {
                if (!loaded(w, c))
                {
                    return null;
                }
                if (!EnderIO.isConduit(w, c[0], c[1], c[2]))
                {
                    return new Task(Task.Kind.CONDUIT, c[0], c[1], c[2], "Laying the power line to the " + b.displayNameWithoutPK, key);
                }
            }
            if (!EnderIO.isLight(w, px, py, pz))
            {
                return new Task(Task.Kind.LIGHT, px, py, pz, "Putting a light in the " + b.displayNameWithoutPK, key);
            }
        }
        else
        {
            if (!EnderIO.isTransceiver(w, px, py, pz))
            {
                return new Task(Task.Kind.TRANSCEIVER, px, py, pz, "Putting a transceiver in the " + b.displayNameWithoutPK, key);
            }
            if (!tuned(w, new int[] {px, py, pz}, channel(town), false))
            {
                return new Task(Task.Kind.TUNE_RECEIVE, px, py, pz, "Tuning the " + b.displayNameWithoutPK + "'s transceiver", key);
            }
            if (!EnderIO.isLight(w, px, py + 1, pz))
            {
                return new Task(Task.Kind.LIGHT, px, py + 1, pz, "Putting a light in the " + b.displayNameWithoutPK, key);
            }
        }
        if (Workshop.isMasonry(b) && b.level >= 2)
        {
            int[] s = smelterSpot(w, b);
            if (s != null && !EnderIO.isAlloySmelter(w, s[0], s[1], s[2]))
            {
                return new Task(Task.Kind.SMELTER, s[0], s[1], s[2], "Installing the masonry's electric furnace", key);
            }
        }
        return null;
    }

    /**
     * picks a building's power point: beside its control box where there's floor, with room above for a light (and a
     * transceiver under it, if it isn't wired), and whether a line from the station can reach it
     */
    private static boolean plan(Building b, World w, Building st, V3 con, int[] outlet)
    {
        int cx = b.primaryXYZ.x.intValue(), cy = b.primaryXYZ.y.intValue(), cz = b.primaryXYZ.z.intValue();
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int pass = 0; pass < 2; pass++)
        {
            for (int[] d : around)
            {
                int x = cx + d[0], z = cz + d[1];
                boolean floor = w.getBlock(x, cy - 1, z).getMaterial().isSolid();
                if (floor && w.isAirBlock(x, cy, z) && w.isAirBlock(x, cy + 1, z))
                {
                    V3 p = new V3((double) x, (double) cy, (double) z, b.primaryXYZ.theDimension);
                    boolean wire = line(w, st, con, outlet, b, p) != null;
                    // the first pass looks for a wired spot; the second takes any
                    if (wire || pass == 1)
                    {
                        b.powerPoint = p;
                        b.powerVia = wire ? "wire" : "radio";
                        b.saveThisBuilding();
                        return true;
                    }
                }
            }
        }
        // on top of the box itself, by transceiver
        if (w.isAirBlock(cx, cy + 1, cz) && w.isAirBlock(cx, cy + 2, cz))
        {
            b.powerPoint = new V3((double) cx, (double) (cy + 1), (double) cz, b.primaryXYZ.theDimension);
            b.powerVia = "radio";
            b.saveThisBuilding();
            return true;
        }
        return false;
    }

    /**
     * the buried line from under the station to under a power point, cell by cell, or null if it can't get there: too
     * far, or across another building or water. A block under the ground all the way, stepping up and down with it
     * (the step always in the higher ground, so it never comes out into the air), then up through the floor.
     */
    private static List<int[]> line(World w, Building st, V3 stCon, int[] outlet, Building to, V3 p)
    {
        int sx = outlet[0], sz = outlet[2], tx = p.x.intValue(), tz = p.z.intValue();
        if (Math.abs(tx - sx) + Math.abs(tz - sz) > WIRE_REACH)
        {
            return null;
        }
        int[] stF = WarehouseStock.footprint(st, st.layoutBox(stCon));
        V3 toCon = WarehouseStock.conBoxOf(to);
        int[] toF = toCon == null ? null : WarehouseStock.footprint(to, to.layoutBox(toCon));
        // the columns: along x first, then z
        List<int[]> columns = new ArrayList<int[]>();
        int x = sx, z = sz;
        columns.add(new int[] {x, z});
        while (x != tx)
        {
            x += Integer.signum(tx - x);
            columns.add(new int[] {x, z});
        }
        while (z != tz)
        {
            z += Integer.signum(tz - z);
            columns.add(new int[] {x, z});
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<Long>();
        List<int[]> cells = new ArrayList<int[]>();
        int prevY = outlet[1], prevX = sx, prevZ = sz;
        add(cells, seen, sx, prevY, sz);
        for (int i = 1; i < columns.size(); i++)
        {
            int cxx = columns.get(i)[0], czz = columns.get(i)[1];
            if (!w.getChunkProvider().chunkExists(cxx >> 4, czz >> 4))
            {
                return null;
            }
            boolean ours = in(stF, cxx, czz) || in(toF, cxx, czz);
            int y;
            if (ours)
            {
                y = prevY; // under the station or the building: straight on at the same depth
            }
            else
            {
                if (otherBuilding(w, cxx, czz, st, to))
                {
                    return null;
                }
                int g = TownWall.ground(w, cxx, czz);
                if (g < 2 || w.getBlock(cxx, g, czz).getMaterial().isLiquid())
                {
                    return null;
                }
                y = g - 1;
            }
            if (y < prevY)
            {
                for (int yy = prevY - 1; yy >= y; yy--)
                {
                    add(cells, seen, prevX, yy, prevZ); // down, in the column before (the higher ground)
                }
            }
            else if (y > prevY)
            {
                for (int yy = prevY; yy < y; yy++)
                {
                    add(cells, seen, cxx, yy, czz); // up, in this column (the higher ground)
                }
            }
            add(cells, seen, cxx, y, czz);
            prevY = y;
            prevX = cxx;
            prevZ = czz;
        }
        // up through the floor to just under the point
        for (int yy = prevY + 1; yy < p.y.intValue(); yy++)
        {
            add(cells, seen, tx, yy, tz);
        }
        // never through something someone built (it'd be swapped for a disguised conduit): a transceiver instead
        for (int[] c : cells)
        {
            if (ashjack.simukraftreloaded.common.PlacedBlocks.placed(w, c[0], c[1], c[2]))
            {
                return null;
            }
        }
        return cells;
    }

    private static void add(List<int[]> cells, LinkedHashSet<Long> seen, int x, int y, int z)
    {
        long k = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        if (seen.add(k))
        {
            cells.add(new int[] {x, y, z});
        }
    }

    private static boolean in(int[] f, int x, int z)
    {
        return f != null && x >= f[0] && x <= f[3] && z >= f[2] && z <= f[5];
    }

    private static boolean otherBuilding(World w, int x, int z, Building a, Building b)
    {
        for (Building o : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (o == a || o == b || o.isRoom())
            {
                continue;
            }
            try
            {
                V3 con = WarehouseStock.conBoxOf(o);
                if (con != null && in(WarehouseStock.footprint(o, o.layoutBox(con)), x, z))
                {
                    return true;
                }
            }
            catch (Exception e)
            {
                // can't say where it is
            }
        }
        return false;
    }

    /** the masonry's electric furnace: beside its power point, where there's room */
    public static int[] smelterSpot(World w, Building b)
    {
        if (b.powerPoint == null)
        {
            return null;
        }
        int x = b.powerPoint.x.intValue(), y = b.powerPoint.y.intValue(), z = b.powerPoint.z.intValue();
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around)
        {
            if (EnderIO.isAlloySmelter(w, x + d[0], y, z + d[1]))
            {
                return new int[] {x + d[0], y, z + d[1]};
            }
        }
        for (int[] d : around)
        {
            int ax = x + d[0], az = z + d[1];
            if ((ax != b.primaryXYZ.x.intValue() || az != b.primaryXYZ.z.intValue()) && w.isAirBlock(ax, y, az))
            {
                return new int[] {ax, y, az};
            }
        }
        return null;
    }

    /** a masonry's electric furnace is in and the grid's up */
    public static boolean electricFurnace(World w, Building b)
    {
        int[] s = b == null || w == null || b.level < 2 ? null : smelterSpot(w, b);
        return s != null && EnderIO.isAlloySmelter(w, s[0], s[1], s[2]);
    }

    // ---------------------------------------------------------------- doing it

    /** does a task; false if it couldn't be (no money, nothing in stock, nothing there) */
    public static synchronized boolean perform(Town town, World w, Task t)
    {
        switch (t.kind)
        {
            case CONDUIT:
                return pay(town, w, "conduit", PRICE_CONDUIT) && EnderIO.placeConduit(w, t.x, t.y, t.z);
            case TRANSCEIVER:
                if (!w.isAirBlock(t.x, t.y, t.z))
                {
                    return false;
                }
                return pay(town, w, "transceiver", PRICE_TRANSCEIVER) && EnderIO.placeTransceiver(w, t.x, t.y, t.z)
                        && EnderIO.tune(w, t.x, t.y, t.z, channel(town), false);
            case TUNE_SEND:
                return EnderIO.tune(w, t.x, t.y, t.z, channel(town), true);
            case TUNE_RECEIVE:
                return EnderIO.tune(w, t.x, t.y, t.z, channel(town), false);
            case LIGHT:
                if (!w.isAirBlock(t.x, t.y, t.z))
                {
                    return false;
                }
                return pay(town, w, "light", PRICE_LIGHT) && EnderIO.placeLight(w, t.x, t.y, t.z);
            case SMELTER:
                if (!w.isAirBlock(t.x, t.y, t.z))
                {
                    return false;
                }
                return pay(town, w, "smelter", PRICE_SMELTER) && EnderIO.placeAlloySmelter(w, t.x, t.y, t.z);
            case REFUEL:
                return refuel(town, w, t);
            default:
                return false;
        }
    }

    public static synchronized void release(Task t, String lineman)
    {
        if (t != null && lineman.equals(claims.get(t.claim)))
        {
            claims.remove(t.claim);
        }
    }

    /** from the town's warehouses if they have one, otherwise the treasury pays */
    private static boolean pay(Town town, World w, String part, float price)
    {
        ItemStack want = EnderIO.item(part);
        if (want != null)
        {
            for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
            {
                if (Upgrades.isWarehouse(b) && b.buildingComplete && b.primaryXYZ != null && town.contains(b.primaryXYZ))
                {
                    if (!WarehouseStock.take(WarehouseStock.chestsInside(b, b.primaryXYZ, w), want.getItem(), 1).isEmpty())
                    {
                        return true;
                    }
                }
            }
        }
        if (Economy.balance(town) < price)
        {
            return false;
        }
        Economy.spend(town, price);
        return true;
    }

    private static int fuel(World w, int[] g)
    {
        TileEntity te = w.getTileEntity(g[0], g[1], g[2]);
        if (!(te instanceof IInventory))
        {
            return 99;
        }
        ItemStack is = ((IInventory) te).getStackInSlot(0);
        return is == null ? 0 : is.stackSize;
    }

    private static int coalAt(World w, Building st)
    {
        return WarehouseStock.count(Workshop.chests(w, st.primaryXYZ), Items.coal);
    }

    /** coal from the station's chests into a generator, up to sixteen */
    private static boolean refuel(Town town, World w, Task t)
    {
        Building st = station(town);
        TileEntity te = w.getTileEntity(t.x, t.y, t.z);
        if (st == null || !(te instanceof IInventory))
        {
            return false;
        }
        IInventory gen = (IInventory) te;
        ItemStack in = gen.getStackInSlot(0);
        if (in != null && in.getItem() != Items.coal)
        {
            return true; // someone's put their own fuel in
        }
        int want = 16 - (in == null ? 0 : in.stackSize);
        int got = 0;
        for (ItemStack is : WarehouseStock.take(Workshop.chests(w, st.primaryXYZ), Items.coal, want))
        {
            got += is.stackSize;
        }
        if (got <= 0)
        {
            return false;
        }
        gen.setInventorySlotContents(0, new ItemStack(Items.coal, (in == null ? 0 : in.stackSize) + got, in == null ? 0 : in.getItemDamage()));
        gen.markDirty();
        return true;
    }

    /** coal the station's chests are short of (the generators burn it), for the keeper */
    public static int coalWanted(World w, Building st)
    {
        return st == null ? 0 : Math.max(0, 64 - coalAt(w, st));
    }

    private static boolean tuned(World w, int[] c, String name, boolean send)
    {
        // EnderIO.tune adds the channel if it isn't there: ask without changing anything
        return EnderIO.isTransceiver(w, c[0], c[1], c[2]) && channelSet(w, c, name, send);
    }

    @SuppressWarnings("unchecked")
    private static boolean channelSet(World w, int[] c, String name, boolean send)
    {
        try
        {
            TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
            Class<?> type = Class.forName("crazypants.enderio.machine.transceiver.ChannelType");
            Object power = type.getField("POWER").get(null);
            Object ch = Class.forName("crazypants.enderio.machine.transceiver.Channel").getConstructor(String.class, type).newInstance(name, power);
            java.util.Set<Object> have = (java.util.Set<Object>) te.getClass()
                    .getMethod(send ? "getSendChannels" : "getRecieveChannels", type).invoke(te, power);
            return have != null && have.contains(ch);
        }
        catch (Throwable e)
        {
            return true; // can't tell: leave it be
        }
    }

    private static boolean free(String key, String lineman)
    {
        String who = claims.get(key);
        return who == null || who.equals(lineman);
    }

    private static Task claim(Task t, String lineman)
    {
        claims.put(t.claim, lineman);
        return t;
    }

    private static boolean loaded(World w, int[] c)
    {
        return w.getChunkProvider().chunkExists(c[0] >> 4, c[2] >> 4);
    }
}
