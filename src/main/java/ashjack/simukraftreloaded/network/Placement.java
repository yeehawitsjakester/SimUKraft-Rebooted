package ashjack.simukraftreloaded.network;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.building.MotelPlan;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Before a new building is built, it's shown where it'll go: its outline in particles - green, with red where it would
 * clear away someone's blocks and angry puffs on another building's boxes it would destroy - and the constructor box
 * screen moves it left, right, nearer, further, up or down. Shift-right-clicking the box (or Start building) gives the
 * go-ahead, unless it would destroy another building's box. Builders clear everything inside a building's outline, so
 * without this a building put down too close ate its neighbour's constructor box and its builder carried on.
 */
public class Placement
{
    /** the blocks a building can't be built over: the boxes that make other buildings work */
    public static boolean isProtected(Block b)
    {
        return b == SimukraftReloadedBlocks.buildingConstructor || b == SimukraftReloadedBlocks.controlBox
                || b == SimukraftReloadedBlocks.roomBox || b == SimukraftReloadedBlocks.miningBox
                || b == SimukraftReloadedBlocks.farmingBox || b == SimukraftReloadedBlocks.cityBox;
    }

    /** what the last check found, per building */
    private static class Result
    {
        String key;
        long when;
        int[] bounds;
        boolean blocked;
        final List<int[]> boxes = new ArrayList<int[]>(), cleared = new ArrayList<int[]>();
    }

    private static final Map<Building, Result> results = new IdentityHashMap<Building, Result>();
    private static final int MOVE_LIMIT = 32;

    private static String key(Building b)
    {
        return b.offsetLtr + "," + b.offsetFtb + "," + b.offsetUp + "," + b.awaitingAt;
    }

    /** checks where a building would go, sets its placementStatus, and says whether it can be built there */
    public static boolean check(Building b)
    {
        Result r = new Result();
        r.key = key(b);
        r.when = System.currentTimeMillis();
        results.put(b, r);
        V3 box = b.conBoxLocation;
        World world = box == null ? null : MinecraftServer.getServer().worldServerForDimension(box.theDimension);
        r.bounds = box == null ? null : WarehouseStock.footprint(b, b.layoutBox(box));
        if (world == null || r.bounds == null)
        {
            b.placementStatus = "Couldn't work out where it goes";
            r.blocked = true;
            return false;
        }
        int[] f = r.bounds;

        // not across a border into another town
        ashjack.simukraftreloaded.core.town.Town home = ashjack.simukraftreloaded.core.town.Towns.at(box);
        for (ashjack.simukraftreloaded.core.town.Town other : ashjack.simukraftreloaded.core.town.Towns.all())
        {
            if (other != home && other.dimension() == box.theDimension
                    && f[0] <= other.centre.x + other.radius && f[3] >= other.centre.x - other.radius
                    && f[2] <= other.centre.z + other.radius && f[5] >= other.centre.z - other.radius)
            {
                b.placementStatus = "It would reach over into " + other.name + " - move it";
                r.blocked = true;
                return false;
            }
        }

        // the other buildings' outlines, to say whose blocks are in the way
        List<Object[]> others = new ArrayList<Object[]>();
        for (Building o : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            V3 con = o == b || o.isRoom() ? null : WarehouseStock.conBoxOf(o);
            int[] of = con == null ? null : WarehouseStock.footprint(o, o.layoutBox(con));
            if (of != null && of[0] <= f[3] && of[3] >= f[0] && of[1] <= f[4] && of[4] >= f[1] && of[2] <= f[5] && of[5] >= f[2])
            {
                others.add(new Object[] {o.displayNameWithoutPK, of});
            }
        }

        Set<String> whose = new LinkedHashSet<String>();
        String firstBox = null;
        for (int x = f[0]; x <= f[3]; x++)
        {
            for (int y = f[1]; y <= f[4]; y++)
            {
                for (int z = f[2]; z <= f[5]; z++)
                {
                    if (!world.blockExists(x, y, z))
                    {
                        b.placementStatus = "Part of it is too far away to check - go closer";
                        r.blocked = true;
                        return false;
                    }
                    Block block = world.getBlock(x, y, z);
                    if (isProtected(block))
                    {
                        r.boxes.add(new int[] {x, y, z});
                        if (firstBox == null)
                        {
                            boolean own = box.isSameCoordsAs(new V3((double) x, (double) y, (double) z, box.theDimension), true, true);
                            firstBox = own ? "its own constructor box" : "a " + block.getLocalizedName() + " at " + x + "," + y + "," + z;
                        }
                    }
                    else if (!MotelPlan.clearable(world, block, x, y, z))
                    {
                        r.cleared.add(new int[] {x, y, z});
                        for (Object[] o : others)
                        {
                            int[] of = (int[]) o[1];
                            if (x >= of[0] && x <= of[3] && y >= of[1] && y <= of[4] && z >= of[2] && z <= of[5])
                            {
                                whose.add((String) o[0]);
                            }
                        }
                    }
                }
            }
        }

        String moved = b.offsetLtr == 0 && b.offsetFtb == 0 && b.offsetUp == 0 ? "" : " (" + describeOffset(b) + ")";
        if (firstBox != null)
        {
            r.blocked = true;
            b.placementStatus = "It would destroy " + firstBox + " - move it" + moved;
            return false;
        }
        r.blocked = false;
        if (r.cleared.isEmpty())
        {
            b.placementStatus = "Nothing in the way" + moved;
        }
        else
        {
            b.placementStatus = "It would clear " + r.cleared.size() + " blocks" + (whose.isEmpty() ? " of your builds"
                    : " of the " + String.join(", the ", whose)) + moved;
        }
        return true;
    }

    private static String describeOffset(Building b)
    {
        List<String> parts = new ArrayList<String>();
        if (b.offsetLtr != 0)
        {
            parts.add(Math.abs(b.offsetLtr) + (b.offsetLtr > 0 ? " right" : " left"));
        }
        if (b.offsetFtb != 0)
        {
            parts.add(Math.abs(b.offsetFtb) + (b.offsetFtb > 0 ? " further" : " closer"));
        }
        if (b.offsetUp != 0)
        {
            parts.add(Math.abs(b.offsetUp) + (b.offsetUp > 0 ? " up" : " down"));
        }
        return "moved " + String.join(", ", parts);
    }

    /** the building waiting to be placed at this constructor box, or null */
    public static Building waitingAt(V3 box)
    {
        if (box == null)
        {
            return null;
        }
        String at = box.toString();
        for (Building b : new ArrayList<Building>(SimukraftReloaded.buildings()))
        {
            if (at.equals(b.awaitingAt))
            {
                return b;
            }
        }
        return null;
    }

    /** shift-right-click on the box, or Start building: the go-ahead, if nothing's in the way. Whether it started. */
    public static boolean confirm(EntityPlayerMP player, V3 box)
    {
        Building b = waitingAt(box);
        if (b == null)
        {
            return false;
        }
        if (!check(b))
        {
            ActionHandlers.tell(player, "Not yet: " + b.placementStatus);
            return true;
        }
        results.remove(b);
        BuildingActions.assignBuilders(player, box, b);
        return true;
    }

    static void register()
    {
        ServerActions.register("movePlacement", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = waitingAt(ActionHandlers.getV3(data, "box"));
                if (b == null)
                {
                    return;
                }
                b.offsetLtr = clamp(b.offsetLtr + data.getInteger("ltr"), MOVE_LIMIT);
                b.offsetFtb = clamp(b.offsetFtb + data.getInteger("ftb"), MOVE_LIMIT);
                b.offsetUp = clamp(b.offsetUp + data.getInteger("up"), 16);
                check(b);
            }
        });
        ServerActions.register("confirmPlacement", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                confirm(player, ActionHandlers.getV3(data, "box"));
            }
        });
        ServerActions.register("cancelPlacement", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = waitingAt(ActionHandlers.getV3(data, "box"));
                if (b != null)
                {
                    SimukraftReloaded.serverBuildings.remove(b);
                    results.remove(b);
                    ActionHandlers.tell(player, "Cancelled the " + b.displayNameWithoutPK);
                }
            }
        });
    }

    private static int clamp(int v, int limit)
    {
        return Math.max(-limit, Math.min(limit, v));
    }

    // ---------------------------------------------------------------- the outline

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        MinecraftServer server = MinecraftServer.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCounter() % 15 != 0)
        {
            return;
        }
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b.awaitingAt == null || b.conBoxLocation == null)
            {
                continue;
            }
            WorldServer world = server.worldServerForDimension(b.conBoxLocation.theDimension);
            V3 c = b.conBoxLocation;
            if (world == null || world.getClosestPlayer(c.x, c.y, c.z, 96) == null)
            {
                continue;
            }
            Result r = results.get(b);
            if (r == null || !r.key.equals(key(b)) || System.currentTimeMillis() - r.when > 10000)
            {
                check(b);
                r = results.get(b);
            }
            if (r == null || r.bounds == null)
            {
                continue;
            }
            outline(world, r.bounds, r.blocked ? "reddust" : "happyVillager");
            int shown = 0;
            for (int[] p : r.cleared)
            {
                if (shown++ > 80)
                {
                    break;
                }
                world.func_147487_a("reddust", p[0] + 0.5, p[1] + 0.5, p[2] + 0.5, 2, 0.2, 0.2, 0.2, 0);
            }
            for (int[] p : r.boxes)
            {
                world.func_147487_a("angryVillager", p[0] + 0.5, p[1] + 1.2, p[2] + 0.5, 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    /** particles along the twelve edges of the box x0..x1, y0..y1, z0..z1 */
    private static void outline(WorldServer world, int[] f, String particle)
    {
        double x0 = f[0], y0 = f[1], z0 = f[2], x1 = f[3] + 1, y1 = f[4] + 1, z1 = f[5] + 1;
        double[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges)
        {
            double[] a = corners[e[0]], b = corners[e[1]];
            double len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]) + Math.abs(b[2] - a[2]);
            int steps = (int) Math.min(64, Math.max(1, len));
            for (int i = 0; i <= steps; i++)
            {
                double t = i / (double) steps;
                world.func_147487_a(particle, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1, 0, 0, 0, 0);
            }
        }
    }
}
