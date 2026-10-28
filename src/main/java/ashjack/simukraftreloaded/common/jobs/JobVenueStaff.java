package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;

import ashjack.simukraftreloaded.common.life.Leisure;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;

/**
 * Works at a venue (see Leisure): a bartender at the tavern, a DJ at the club, a librarian, a park keeper, a coach at
 * the gym. On duty through the day and on into the evening, when the bars and clubs are busiest. With staff on duty a
 * venue's visitors have a better time and it draws more of them; staff are paid from the venue's takings (the town
 * covers it while those are short), more when it's busy.
 */
public class JobVenueStaff extends Job implements Serializable
{
    private static final long serialVersionUID = 1L;

    public Vocation vocation = null;
    public FolkData theFolk = null;
    private transient long lastRun, lastPaid;

    public JobVenueStaff() {}

    public JobVenueStaff(FolkData folk)
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
        theFolk.statusText = "Opening up";
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (!SimukraftReloaded.isDayTime() && !Leisure.evening())
        {
            return; // closed for the night
        }
        if (SimukraftReloaded.isDayTime())
        {
            super.onUpdateGoingToWork(theFolk);
        }
        long now = System.currentTimeMillis();
        if (theFolk.employedAt == null || now - lastRun < 2000)
        {
            return;
        }
        lastRun = now;
        Building venue = Building.getBuilding(theFolk.employedAt);
        Leisure.Venue kind = Leisure.venueOf(venue);
        if (venue == null || kind == null)
        {
            theFolk.statusText = "This isn't somewhere people go out to";
            return;
        }
        if (theFolk.action != FolkAction.ATWORK)
        {
            return;
        }
        int in = Leisure.crowd(venue);
        theFolk.isWorking = in > 0;
        theFolk.stayPut = true;
        theFolk.statusText = kind.staffTitle + " at the " + venue.displayNameWithoutPK
                + (in == 0 ? " - quiet" : " - " + in + (in == 1 ? " in" : " in tonight"));
        if (now - lastPaid > 60000)
        {
            lastPaid = now;
            Economy.pay(theFolk, 0.04f + 0.02f * Math.min(in, 8));
        }
    }
}
