package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;

import ashjack.simukraftreloaded.common.life.Lease;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;

/**
 * Runs their own shop in a unit they lease (see Commerce): there through the day, open while they are, living on the
 * margin of what they sell and paying the rent every morning. No wages: it's their business.
 */
public class JobShopkeeper extends Job implements Serializable
{
    private static final long serialVersionUID = 1L;

    public Vocation vocation = null;
    public FolkData theFolk = null;
    private transient long lastRun;

    public JobShopkeeper() {}

    public JobShopkeeper(FolkData folk)
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
        if (theFolk != null)
        {
            theFolk.isWorking = false;
        }
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.action = FolkAction.ATWORK;
        theFolk.stayPut = true;
        theFolk.statusText = "Opening the shop";
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (!SimukraftReloaded.isDayTime())
        {
            return;
        }
        super.onUpdateGoingToWork(theFolk);
        long now = System.currentTimeMillis();
        if (theFolk.employedAt == null || theFolk.action != FolkAction.ATWORK || now - lastRun < 3000)
        {
            return;
        }
        lastRun = now;
        Building b = Building.getBuilding(theFolk.employedAt);
        Lease mine = null;
        if (b != null)
        {
            for (Lease l : b.leases)
            {
                if (l.owner.equals(theFolk.name))
                {
                    mine = l;
                }
            }
        }
        if (mine == null)
        {
            theFolk.statusText = "Has no shop here any more";
            return;
        }
        theFolk.stayPut = true;
        theFolk.isWorking = mine.salesToday > 0;
        theFolk.statusText = "Running their " + mine.line.toLowerCase() + " shop: " + mine.salesToday + " sold today ("
                + SimukraftReloaded.displayMoney(mine.today) + ")" + (mine.strikes > 0 ? " - struggling" : "");
    }
}
