package ashjack.simukraftreloaded.common.life;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.SharedMonsterAttributes;
import ashjack.simukraftreloaded.common.rail.RailPlan;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.traits.Traits;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Getting about, and what it does to them. Every trip a Sim makes - to work, out, home - is walked, or ridden on the
 * town's railway when one runs from near where they are to near where they're going (its two ends are stations; see
 * RailPlan). Who rides: the lazy whenever they can; most for a long trip; the active hardly ever. A long walk is a
 * pleasure to the active and a chore to everyone else (the lazy most of all).
 *
 * Walking keeps a Sim fit: fitness (0-10) goes up on a day with plenty of walking and down on one without, and sets
 * how much health they have (60% to all of it). So riding everywhere is comfortable, and doesn't bother the lazy, but
 * slowly wears them down.
 */
public final class Transport
{
    private Transport() {}

    /** a station: one end of a finished railway, with the other end */
    private static class Line
    {
        final V3 a, b;
        final int length;

        Line(V3 a, V3 b, int length)
        {
            this.a = a;
            this.b = b;
            this.length = length;
        }
    }

    private static final int STATION_REACH = 24, SHORT = 16, LONG = 48, VERY_LONG = 120;

    private static List<Line> lines(int dim)
    {
        List<Line> out = new ArrayList<Line>();
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (!RailPlan.isDepot(b) || b.primaryXYZ == null || b.primaryXYZ.theDimension != dim)
            {
                continue;
            }
            RailPlan p = RailPlan.of(b);
            if (p != null && p.finished && p.route.size() > 1)
            {
                int[] s = p.route.get(0), e = p.route.get(p.route.size() - 1);
                out.add(new Line(new V3((double) s[0], (double) s[1], (double) s[2], dim), new V3((double) e[0], (double) e[1], (double) e[2], dim),
                        p.route.size()));
            }
        }
        return out;
    }

    private static double flat(V3 a, V3 b)
    {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * a trip from where they are to here: walked or ridden, counted towards their fitness, and how they felt about it.
     * A ride puts them at the far station (the walk from there to where they're going is theirs to make)
     */
    public static void trip(FolkData f, V3 to)
    {
        if (f == null || to == null || f.location == null || f.location.theDimension != to.theDimension)
        {
            return;
        }
        double d = flat(f.location, to);
        if (d < SHORT)
        {
            f.walkedToday += d;
            return;
        }
        boolean lazy = f.hasTrait(Traits.traitLazy), active = f.hasTrait(Traits.traitActive);
        // the best railway for it: its ends near here and near there, and a real saving on the walk
        Line best = null;
        V3 board = null, alight = null;
        double bestWalk = d;
        for (Line l : lines(to.theDimension))
        {
            for (int dir = 0; dir < 2; dir++)
            {
                V3 s1 = dir == 0 ? l.a : l.b, s2 = dir == 0 ? l.b : l.a;
                double w1 = flat(f.location, s1), w2 = flat(s2, to);
                if (w1 <= STATION_REACH && w2 <= STATION_REACH && w1 + w2 < bestWalk - 32)
                {
                    best = l;
                    board = s1;
                    alight = s2;
                    bestWalk = w1 + w2;
                }
            }
        }
        boolean ride = best != null && (lazy || !active && d > 80 || active && d > 250);
        if (ride)
        {
            f.walkedToday += bestWalk;
            f.rodeToday += best.length;
            V3 at = alight.clone();
            if (f.isSpawned())
            {
                f.theEntity.worldObj.playSoundAtEntity(f.theEntity, "minecart.base", 0.6f, 1f);
                f.theEntity.setPositionAndUpdate(at.x + 0.5, at.y, at.z + 0.5);
            }
            f.location = at;
            f.destination = null;
            if (lazy)
            {
                f.levelFun = Math.min(10, f.levelFun + 1);
                f.funNote = "Took the train instead of walking";
            }
            return;
        }
        f.walkedToday += d;
        if (active && d > LONG)
        {
            f.levelFun = Math.min(10, f.levelFun + 1);
            f.funNote = "Enjoyed a good long walk";
        }
        else if (!active && d > (lazy ? LONG : VERY_LONG))
        {
            f.levelFun = Math.max(1, f.levelFun - 1);
            f.funNote = "A long walk across town" + (best == null && lazy ? " - if only there were a train" : "");
        }
    }

    // ---------------------------------------------------------------- fitness

    /** how much of their full health their fitness gives them */
    public static float healthFactor(FolkData f)
    {
        return 0.6f + 0.04f * Math.max(0, Math.min(10, f.fitness));
    }

    public static float maxHealth(FolkData f)
    {
        return SimukraftReloadedConfig.configSimMaxHealth * healthFactor(f);
    }

    /** their entity's health to match their fitness */
    public static void applyFitness(FolkData f)
    {
        if (f.isSpawned())
        {
            f.theEntity.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(maxHealth(f));
            if (f.theEntity.getHealth() > maxHealth(f))
            {
                f.theEntity.setHealth(maxHealth(f));
            }
        }
    }

    private static long lastDay = -1;

    /** called often: each new day, the walking (or not) of the day before shows in their fitness */
    public static void tick()
    {
        long d = Commerce.day();
        if (d == lastDay || d < 0)
        {
            return;
        }
        boolean first = lastDay < 0;
        lastDay = d;
        if (first)
        {
            return;
        }
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.isVisitor || f.age < 12)
            {
                continue;
            }
            float before = f.fitness;
            if (f.walkedToday >= 200)
            {
                f.fitness += f.hasTrait(Traits.traitActive) ? 0.8f : 0.5f;
            }
            else if (f.walkedToday < 80)
            {
                f.fitness -= 0.5f;
            }
            f.fitness = Math.max(0, Math.min(10, f.fitness));
            if (before >= 3 && f.fitness < 3)
            {
                SimukraftReloaded.sendChat(f.name + " is getting out of shape" + (f.hasTrait(Traits.traitLazy) ? " (lazy, and it shows)" : ""));
            }
            f.walkedToday = 0;
            f.rodeToday = 0;
            applyFitness(f);
        }
    }
}
