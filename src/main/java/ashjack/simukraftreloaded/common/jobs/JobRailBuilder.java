package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.military.TownWall;
import ashjack.simukraftreloaded.common.rail.RailPlan;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Lays the railway their depot has planned (see RailPlan), a block at a time along the route: digging the cutting or
 * tunnel, building up the embankment or bridge, and putting the rail down. Several builders spread out along it.
 */
public class JobRailBuilder extends Job implements Serializable
{
    private static final long serialVersionUID = 1L;

    public Vocation vocation = null;
    public FolkData theFolk = null;

    private transient int cell = -1;
    private transient long lastRun, cellSince, idleUntil;

    private static final long RUN_MS = 1000, GIVE_UP_WALKING_MS = 25000;

    public JobRailBuilder() {}

    public JobRailBuilder(FolkData folk)
    {
        theFolk = folk;
        if (theFolk != null && theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    private RailPlan plan()
    {
        return theFolk == null || theFolk.employedAt == null ? null : RailPlan.of(Building.getBuilding(theFolk.employedAt));
    }

    @Override
    public void resetJob()
    {
        drop();
    }

    private void drop()
    {
        RailPlan p = plan();
        if (p != null && cell >= 0)
        {
            p.release(cell, theFolk.name);
        }
        cell = -1;
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.action = FolkAction.ATWORK;
        theFolk.statusText = "Looking over the route";
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (!SimukraftReloaded.isDayTime())
        {
            drop();
            return;
        }
        super.onUpdateGoingToWork(theFolk);
        long now = System.currentTimeMillis();
        if (theFolk.action != FolkAction.ATWORK || theFolk.employedAt == null || now - lastRun < RUN_MS)
        {
            return;
        }
        lastRun = now;
        if (jobWorld == null)
        {
            jobWorld = MinecraftServer.getServer().worldServerForDimension(theFolk.employedAt.theDimension);
        }
        Building depot = Building.getBuilding(theFolk.employedAt);
        RailPlan p = plan();
        if (jobWorld == null || p == null || depot == null)
        {
            return;
        }
        if (p.route.isEmpty() || p.finished)
        {
            ashjack.simukraftreloaded.common.ChunkKeeper.release("rail@" + theFolk.employedAt);
            if (p.route.isEmpty())
            {
                theFolk.statusText = "No railway planned - place two markers and plan one at the depot";
                theFolk.isWorking = false;
                return;
            }
        }
        else
        {
            keepAhead(p);
        }
        if (cell < 0)
        {
            if (now < idleUntil)
            {
                return;
            }
            cell = p.next(jobWorld, theFolk.name);
            if (cell < 0)
            {
                theFolk.isWorking = false;
                theFolk.stayPut = false;
                theFolk.statusText = p.finished ? "The railway to " + p.to + " is finished" : "Waiting for the rest of the route to be nearby";
                idleUntil = now + 20000;
                return;
            }
            cellSince = now;
            theFolk.gotoXYZ(spot(p), GotoMethod.WALK);
        }
        int[] c = p.route.get(cell);
        theFolk.updateLocationFromEntity();
        double dx = theFolk.location.x - c[0], dz = theFolk.location.z - c[2];
        if (dx * dx + dz * dz > 25)
        {
            theFolk.isWorking = false;
            theFolk.statusText = "Off along the line";
            if (now - cellSince > GIVE_UP_WALKING_MS)
            {
                if (theFolk.isSpawned())
                {
                    V3 s = spot(p);
                    theFolk.theEntity.setPositionAndUpdate(s.x + 0.5, s.y, s.z + 0.5); // across whatever's in the way
                }
            }
            else
            {
                if (theFolk.destination == null)
                {
                    theFolk.gotoXYZ(spot(p), GotoMethod.WALK);
                }
                return;
            }
        }
        theFolk.destination = null;
        theFolk.isWorking = true;
        boolean ok = p.build(jobWorld, cell, depot);
        if (ok)
        {
            Economy.pay(theFolk, 0.03f);
            jobWorld.playSoundEffect(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, "step.metal", 0.6f, 1f);
            theFolk.statusText = "Laying the railway to " + p.to + " (" + p.laid(jobWorld) + " of " + p.route.size() + ")";
        }
        else
        {
            theFolk.statusText = "Out of rails and money - waiting for the depot to be stocked";
            idleUntil = now + 30000;
        }
        drop();
    }

    /** the next stretch of the line kept loaded (the first unbuilt cells, for a few chunks), so it's built far from anyone */
    private void keepAhead(RailPlan p)
    {
        java.util.LinkedHashSet<net.minecraft.world.ChunkCoordIntPair> chunks = new java.util.LinkedHashSet<net.minecraft.world.ChunkCoordIntPair>();
        int start = -1;
        for (int i = 0; i < p.route.size(); i++)
        {
            int[] c = p.route.get(i);
            if (!jobWorld.getChunkProvider().chunkExists(c[0] >> 4, c[2] >> 4) || !p.done(jobWorld, i))
            {
                start = i;
                break;
            }
        }
        if (start < 0)
        {
            return;
        }
        for (int i = Math.max(0, start - 1); i < p.route.size() && chunks.size() < ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig.configKeepChunksPerJob; i++)
        {
            int[] c = p.route.get(i);
            chunks.add(ashjack.simukraftreloaded.common.ChunkKeeper.chunkOf(c[0], c[2]));
        }
        ashjack.simukraftreloaded.common.ChunkKeeper.keep("rail@" + theFolk.employedAt, jobWorld, chunks);
    }

    /** where to stand for a cell: on top of where the rail will go, or the ground beside it if that's in a hill */
    private V3 spot(RailPlan p)
    {
        int[] c = p.route.get(cell);
        int g = TownWall.ground(jobWorld, c[0], c[2]);
        int y = g >= c[1] ? g + 1 : c[1] + 1;
        return new V3((double) c[0], (double) y, (double) c[2], theFolk.employedAt.theDimension);
    }

    /** the rails the rest of the line needs (one builder says, for the depot): a keeper brings them, a smithy makes them */
    @Override
    public Map<Item, Integer> remainingNeeds()
    {
        Map<Item, Integer> out = new LinkedHashMap<Item, Integer>();
        RailPlan p = plan();
        if (p == null || jobWorld == null)
        {
            return out;
        }
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.RAILBUILDER && theFolk.employedAt.isSameCoordsAs(f.employedAt, true, true))
            {
                if (f != theFolk)
                {
                    return out;
                }
                break;
            }
        }
        int rails = p.railsWanted(jobWorld);
        if (rails > 0)
        {
            out.put(Item.getItemFromBlock(Blocks.rail), rails);
            out.put(Item.getItemFromBlock(Blocks.golden_rail), Math.max(1, rails / 6));
        }
        return out;
    }
}
