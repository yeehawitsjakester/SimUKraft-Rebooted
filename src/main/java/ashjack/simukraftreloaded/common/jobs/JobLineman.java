package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.power.EnderIO;
import ashjack.simukraftreloaded.common.power.PowerGrid;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Builds and keeps the town's power grid (see PowerGrid) from its power station: wires up the station, lays power
 * lines to the buildings near it and puts transceivers in the ones further off, a light in each, electric furnaces
 * where they've been paid for, and keeps the generators fed with coal. One job at a time, walking to each.
 */
public class JobLineman extends Job implements Serializable
{
    private static final long serialVersionUID = 1L;

    public Vocation vocation = null;
    public FolkData theFolk = null;

    private transient PowerGrid.Task task;
    private transient long lastRun, taskSince, idleUntil;
    private transient int failures;

    private static final long RUN_MS = 1200, GIVE_UP_WALKING_MS = 30000;

    public JobLineman() {}

    public JobLineman(FolkData folk)
    {
        theFolk = folk;
        if (theFolk != null && theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    @Override
    public void resetJob()
    {
        drop();
    }

    private void drop()
    {
        if (task != null && theFolk != null)
        {
            PowerGrid.release(task, theFolk.name);
        }
        task = null;
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.action = FolkAction.ATWORK;
        theFolk.statusText = "Checking the station's dials";
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
        Town town = Towns.at(theFolk.employedAt);
        if (jobWorld == null || town == null)
        {
            theFolk.statusText = "The power station needs to be inside a town";
            return;
        }
        if (!EnderIO.available())
        {
            theFolk.statusText = "No EnderIO - nothing to build a grid with";
            return;
        }
        if (task == null)
        {
            if (now < idleUntil)
            {
                return;
            }
            task = PowerGrid.next(town, jobWorld, theFolk.name);
            if (task == null)
            {
                theFolk.isWorking = false;
                theFolk.stayPut = false;
                theFolk.statusText = "The grid's all up";
                idleUntil = now + 20000;
                return;
            }
            taskSince = now;
            theFolk.gotoXYZ(new V3((double) task.x, (double) task.y + 1, (double) task.z, theFolk.employedAt.theDimension), GotoMethod.WALK);
        }

        theFolk.updateLocationFromEntity();
        double dx = theFolk.location.x - task.x, dz = theFolk.location.z - task.z;
        if (dx * dx + dz * dz > 36)
        {
            theFolk.isWorking = false;
            theFolk.statusText = task.what;
            if (now - taskSince > GIVE_UP_WALKING_MS)
            {
                if (theFolk.isSpawned())
                {
                    // round whatever's in the way: next to the job, on the surface
                    int y = ashjack.simukraftreloaded.common.military.TownWall.ground(jobWorld, task.x + 1, task.z);
                    theFolk.theEntity.setPositionAndUpdate(task.x + 1.5, (y < 0 ? task.y : y) + 1, task.z + 0.5);
                }
            }
            else
            {
                if (theFolk.destination == null)
                {
                    theFolk.gotoXYZ(new V3((double) task.x, (double) task.y + 1, (double) task.z, theFolk.employedAt.theDimension), GotoMethod.WALK);
                }
                return;
            }
        }
        theFolk.destination = null;
        theFolk.isWorking = true;
        theFolk.statusText = task.what;
        boolean done = PowerGrid.perform(town, jobWorld, task);
        if (done)
        {
            failures = 0;
            Economy.pay(theFolk, 0.05f);
            jobWorld.playSoundEffect(task.x + 0.5, task.y + 0.5, task.z + 0.5, "random.click", 0.5f, 1.2f);
        }
        else
        {
            theFolk.statusText = "Couldn't finish: " + task.what.toLowerCase() + " (out of money or parts?)";
            if (++failures >= 3)
            {
                failures = 0;
                idleUntil = now + 60000; // try again in a minute
            }
        }
        drop();
    }

    /** coal for the station's generators, for the keeper to bring */
    @Override
    public Map<Item, Integer> remainingNeeds()
    {
        Map<Item, Integer> out = new LinkedHashMap<Item, Integer>();
        if (theFolk == null || theFolk.employedAt == null || jobWorld == null)
        {
            return out;
        }
        // one lineman speaks for the station
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.LINEMAN && theFolk.employedAt.isSameCoordsAs(f.employedAt, true, true))
            {
                if (f != theFolk)
                {
                    return out;
                }
                break;
            }
        }
        Town town = Towns.at(theFolk.employedAt);
        int coal = town == null ? 0 : PowerGrid.coalWanted(jobWorld, PowerGrid.station(town));
        if (coal > 0)
        {
            out.put(Items.coal, coal);
        }
        return out;
    }
}
