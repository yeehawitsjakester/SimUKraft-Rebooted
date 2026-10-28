package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.Care;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A nurse or paramedic at a clinic or hospital, on call day and night. They go out to anyone in town too hurt to walk
 * (one medic each), patch them up enough to walk in, and send them to a bed; back at work they treat the patients in
 * its beds - each medic speeds everyone's mending, more so at a hospital - and anyone hurt who passes close by.
 * See Care for the patients' side.
 */
public class JobMedic extends Job implements Serializable
{
    public Vocation vocation = null;
    public FolkData theFolk = null;
    public Stage theStage;
    private transient long timeSinceLastRun, lastRound, lastPassing, calloutSince, lastRepath, treatingSince;
    private transient String callout;
    /** where they're on duty (see Care.workSpot), and when they set off back there (0: they're there) */
    private transient V3 post;
    private transient long postFound, postSince;

    /** patient name -> the medic going to them */
    private static final Map<String, String> claims = new HashMap<String, String>();
    private static final long CALLOUT_GIVE_UP_MS = 180000, TREAT_MS = 3000;

    public enum Stage
    {
        IDLE, ONDUTY, CALLOUT;
    }

    public JobMedic()
    {
    }

    public JobMedic(FolkData folk)
    {
        theFolk = folk;
        if (theStage == null)
        {
            theStage = Stage.IDLE;
        }
        if (theFolk != null && theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    @Override
    public void resetJob()
    {
        release();
        theStage = Stage.IDLE;
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        long now = System.currentTimeMillis();
        if (now - timeSinceLastRun < 500 || theFolk.employedAt == null)
        {
            return;
        }
        timeSinceLastRun = now;
        if (jobWorld == null)
        {
            jobWorld = MinecraftServer.getServer().worldServerForDimension(theFolk.employedAt.theDimension);
        }
        Building work = Building.getBuilding(theFolk.employedAt);
        if (jobWorld == null || work == null)
        {
            return;
        }
        theFolk.action = FolkAction.ATWORK;
        theFolk.heldItem = null;

        if (callout == null)
        {
            FolkData down = findDown(work);
            if (down != null)
            {
                callout = down.name;
                claims.put(down.name, theFolk.name);
                calloutSince = now;
                lastRepath = 0;
                treatingSince = 0;
                theStage = Stage.CALLOUT;
                SimukraftReloaded.sendChat(theFolk.name + " is on the way to help " + down.name);
            }
        }
        if (callout != null)
        {
            goToPatient(work, now);
            return;
        }

        theStage = Stage.ONDUTY;
        theFolk.updateLocationFromEntity();
        // on duty inside, by the beds - not out at the control box, which can be on a corner of the foundation
        if (post == null || now - postFound > 60000)
        {
            postFound = now;
            V3 spot = Care.workSpot(work, jobWorld);
            post = spot != null ? spot : theFolk.employedAt.clone();
        }
        if (theFolk.isSpawned() && theFolk.location.getDistanceTo(post) > 3)
        {
            theFolk.isWorking = false;
            theFolk.stayPut = false;
            theFolk.statusText = "Back to the " + work.displayNameWithoutPK;
            if (theFolk.destination == null)
            {
                theFolk.gotoXYZ(post, GotoMethod.WALK);
            }
            if (postSince == 0)
            {
                postSince = now; // set off back
            }
            else if (now - postSince > 20000)
            {
                theFolk.theEntity.setPositionAndUpdate(post.x + 0.5, post.y, post.z + 0.5); // round whatever's in the way
                theFolk.destination = null;
            }
            return;
        }
        postSince = 0;
        rounds(work, now);
        passersBy(now);
    }

    // ---------------------------------------------------------------- call-outs

    /** someone in town too hurt to walk that no other medic is going to */
    private FolkData findDown(Building work)
    {
        Town town = Towns.at(work.primaryXYZ);
        FolkData best = null;
        double bestDist = Double.MAX_VALUE;
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.folks()))
        {
            if (f.care != Care.Stage.DOWN || f == theFolk || claims.containsKey(f.name) || f.location == null
                    || f.location.theDimension != work.primaryXYZ.theDimension)
            {
                continue;
            }
            double d = f.location.getDistanceTo(work.primaryXYZ);
            if (town != null ? !town.contains(f.location) : d > 160)
            {
                continue;
            }
            if (d < bestDist)
            {
                bestDist = d;
                best = f;
            }
        }
        return best;
    }

    private void goToPatient(Building work, long now)
    {
        FolkData patient = FolkData.getFolkByName(callout);
        if (patient == null || patient.care != Care.Stage.DOWN || now - calloutSince > CALLOUT_GIVE_UP_MS)
        {
            release();
            theStage = Stage.ONDUTY;
            theFolk.destination = null;
            return;
        }
        theFolk.stayPut = false;
        theFolk.updateLocationFromEntity();
        patient.updateLocationFromEntity();
        boolean close = theFolk.location.getDistanceTo(patient.location) <= 3
                || (!theFolk.isSpawned() && now - calloutSince > 30000); // nobody's watching: they get there
        if (!close)
        {
            theFolk.isWorking = false;
            theFolk.statusText = "Going to help " + patient.name;
            if (now - lastRepath > 3000 || theFolk.destination == null)
            {
                lastRepath = now;
                theFolk.gotoXYZ(patient.location.clone(), GotoMethod.WALK);
            }
            return;
        }
        theFolk.destination = null;
        theFolk.stayPut = true;
        theFolk.isWorking = true;
        theFolk.statusText = "Treating " + patient.name;
        if (treatingSince == 0)
        {
            treatingSince = now;
            return;
        }
        if (now - treatingSince < TREAT_MS)
        {
            return;
        }
        Care.stabilised(patient, theFolk);
        Economy.pay(theFolk, 0.5);
        SimukraftReloaded.sendChat(theFolk.name + " patched up " + patient.name
                + (patient.careAt != null ? ", who's walking to a bed" : ", who's going home to rest"));
        theFolk.isWorking = false;
        theFolk.stayPut = false;
        release();
        theStage = Stage.ONDUTY;
    }

    private void release()
    {
        if (callout != null && theFolk != null && theFolk.name.equals(claims.get(callout)))
        {
            claims.remove(callout);
        }
        callout = null;
        treatingSince = 0;
    }

    // ---------------------------------------------------------------- at work

    /** treats the patients in the beds here */
    private void rounds(Building work, long now)
    {
        boolean hospital = Care.isHospital(work);
        List<FolkData> patients = new ArrayList<FolkData>();
        for (FolkData p : Care.patientsAt(work))
        {
            if (p.care == Care.Stage.IN_CARE)
            {
                patients.add(p);
            }
        }
        if (patients.isEmpty())
        {
            theFolk.isWorking = false;
            theFolk.stayPut = true;
            theFolk.statusText = "On duty at the " + work.displayNameWithoutPK;
            return;
        }
        theFolk.isWorking = true;
        theFolk.stayPut = true;
        theFolk.statusText = "Looking after " + patients.size() + (patients.size() == 1 ? " patient" : " patients");
        if (now - lastRound < (hospital ? 2000 : 3000))
        {
            return;
        }
        lastRound = now;
        for (FolkData p : patients)
        {
            Care.heal(p, hospital ? 2 : 1);
        }
        Economy.pay(theFolk, 0.02 * patients.size());
    }

    /** a bit of first aid for anyone hurt who comes close */
    private void passersBy(long now)
    {
        if (now - lastPassing < 5000 || !theFolk.isSpawned())
        {
            return;
        }
        lastPassing = now;
        V3 me = theFolk.location;
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.folks()))
        {
            if (f != theFolk && f.isSpawned() && f.care == Care.Stage.NONE && f.location != null
                    && f.location.getDistanceTo(me) <= 6 && Care.fraction(f) < 1)
            {
                Care.heal(f, 1);
            }
        }
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.updateLocationFromEntity();
        if (theFolk.location.getDistanceTo(theFolk.employedAt) <= 3)
        {
            theFolk.action = FolkAction.ATWORK;
            theFolk.statusText = "Reporting for duty";
            theStage = Stage.ONDUTY;
        }
        else
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }
}
