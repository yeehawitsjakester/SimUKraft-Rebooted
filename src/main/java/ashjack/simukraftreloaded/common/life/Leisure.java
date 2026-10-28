package ashjack.simukraftreloaded.common.life;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.Relationship;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.folk.traits.Traits;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Going out. A venue is any building that's a place to go by what it is - a tavern or inn, a club, a café, a park or
 * plaza, a library, a gym or pool, a bowling alley or cinema, a church - so the town's existing buildings count. Sims
 * go on their days off, and after work in the evening when they're short of fun or company. Where they go depends on
 * what they're short of, their traits (the friendly to the bar, the outdoorsy to the park, the religious to church,
 * the lazy to whatever's nearest), what's open (clubs at night, parks by day), how busy it is, and what they can
 * afford: the entry or a round is paid from their own money to the venue's business. While there they have fun, and
 * if others are there, company - and they get to know each other: acquaintances, then friends. Staff on duty (see
 * JobVenueStaff) make it better.
 */
public final class Leisure
{
    private Leisure() {}

    public enum When
    {
        DAY, EVENING, ANY
    }

    public enum Venue
    {
        BAR("having a drink", "Bartender", 0.6f, 1, 2, When.ANY, "pub", "bar ", "tavern", " inn", "saloon", "brewery"),
        CLUB("dancing", "DJ", 1.0f, 2, 2, When.EVENING, "club", "disco", "lounge"),
        CAFE("eating out", "Waiter", 0.5f, 1, 1, When.ANY, "cafe", "café", "restaurant", "diner", "bistro", "pizz", "burger"),
        PARK("taking it easy", "Park keeper", 0f, 1, 1, When.DAY, "park", "garden", "plaza", "fountain", "square", "statue"),
        LIBRARY("reading", "Librarian", 0.1f, 1, 0, When.DAY, "library", "museum", "gallery"),
        FITNESS("working out", "Coach", 0.3f, 1, 1, When.ANY, "gym", "pool", "sports", "stadium", "arena", "whirlpool"),
        AMUSEMENT("having a laugh", "Attendant", 0.8f, 2, 1, When.ANY, "bowling", "arcade", "casino", "theatre", "theater", "cinema", "movie"),
        CHURCH("at a service", "Priest", 0f, 1, 1, When.DAY, "church", "temple", "chapel", "cathedral", "shrine");

        public final String doing, staffTitle;
        public final float price;
        public final int fun, social;
        public final When when;
        final String[] words;

        Venue(String doing, String staffTitle, float price, int fun, int social, When when, String... words)
        {
            this.doing = doing;
            this.staffTitle = staffTitle;
            this.price = price;
            this.fun = fun;
            this.social = social;
            this.when = when;
            this.words = words;
        }
    }

    /** what kind of venue a building is, or null */
    public static Venue venueOf(Building b)
    {
        if (b == null || b.displayName == null || b.isRoom() || "residential".equals(b.type))
        {
            return null;
        }
        String n = " " + (b.displayNameWithoutPK != null ? b.displayNameWithoutPK : b.displayName).toLowerCase() + " ";
        for (Venue v : Venue.values())
        {
            for (String w : v.words)
            {
                if (n.contains(w))
                {
                    return v;
                }
            }
        }
        return null;
    }

    private static final long TICK_MS = 15000;
    private static final Random rand = new Random();

    /** evening, by the world's clock: after work and before bed (dusk) */
    public static boolean evening()
    {
        long t = MinecraftServer.getServer().worldServers[0].getWorldTime() % 24000;
        return t >= 12000 && t < 14500;
    }

    // ---------------------------------------------------------------- choosing

    /** sets them off to the best venue for them now, if there's one; false if there isn't */
    public static boolean goOut(FolkData f, boolean evening)
    {
        if (f.location == null || f.age < 18 || f.isVisitor || f.leisureAt != null)
        {
            return false;
        }
        Building best = null;
        double bestScore = 0;
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            Venue v = venueOf(b);
            if (v == null || !b.buildingComplete || b.primaryXYZ == null || b.primaryXYZ.theDimension != f.location.theDimension)
            {
                continue;
            }
            if (evening ? v.when == When.DAY : v.when == When.EVENING)
            {
                continue; // shut
            }
            double dist = f.location.getDistanceTo(b.primaryXYZ);
            if (dist > 160 || v.price > 0 && f.money < v.price)
            {
                continue;
            }
            double score = 10 + rand.nextDouble() * 6;
            score += (10 - f.levelFun) * v.fun * 1.5 + (10 - f.levelSocial) * v.social * 1.5;
            score += affinity(f, v);
            score += Math.min(4, crowd(b)) * v.social * 1.5;
            if (staffed(b))
            {
                score += 4;
            }
            // how far they'll go for it: the lazy hardly at all, the active gladly
            double perBlock = f.hasTrait(Traits.traitLazy) ? 1 / 6.0 : f.hasTrait(Traits.traitActive) ? 1 / 40.0 : 1 / 16.0;
            score -= dist * perBlock;
            if (score > bestScore)
            {
                bestScore = score;
                best = b;
            }
        }
        if (best == null)
        {
            return false;
        }
        f.leisureAt = best.primaryXYZ.clone();
        f.leisureSince = System.currentTimeMillis();
        f.leisureUntil = f.leisureSince + 120000 + rand.nextInt(150000);
        f.leisurePaid = false;
        f.action = FolkAction.WANDER;
        f.isWorking = false;
        f.stayPut = false;
        f.statusText = "Off to the " + best.displayNameWithoutPK;
        if (f.isSpawned())
        {
            f.gotoXYZ(best.primaryXYZ.clone(), GotoMethod.WALK);
            if (f.destination != null)
            {
                f.destination.doNotTimeout = true;
            }
        }
        else
        {
            f.location = best.primaryXYZ.clone(); // nobody's watching: they're there
        }
        return true;
    }

    /** how much this Sim's traits draw them to a kind of venue */
    private static double affinity(FolkData f, Venue v)
    {
        double a = 0;
        if (f.hasTrait(Traits.traitFriendly) && (v == Venue.BAR || v == Venue.CLUB || v == Venue.CAFE))
        {
            a += 6;
        }
        if (f.hasTrait(Traits.traitLovesOutdoors) && v == Venue.PARK)
        {
            a += 8;
        }
        if (f.hasTrait(Traits.traitHatesOutdoors))
        {
            a += v == Venue.PARK ? -10 : v == Venue.LIBRARY || v == Venue.AMUSEMENT ? 4 : 0;
        }
        if (f.hasTrait(Traits.traitReligious) && v == Venue.CHURCH)
        {
            a += 12;
        }
        if (f.hasTrait(Traits.traitStrong) && v == Venue.FITNESS)
        {
            a += 8;
        }
        if (f.hasTrait(Traits.traitNightOwl) && (v == Venue.CLUB || v == Venue.BAR))
        {
            a += 6;
        }
        if (f.hasTrait(Traits.traitWorkaholic) && v == Venue.LIBRARY)
        {
            a += 5;
        }
        if (f.hasTrait(Traits.traitActive) && (v == Venue.FITNESS || v == Venue.PARK))
        {
            a += 6;
        }
        return a;
    }

    /** how many are there now */
    public static int crowd(Building b)
    {
        int n = 0;
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.leisureAt != null && f.leisureArrived && f.leisureAt.isSameCoordsAs(b.primaryXYZ, true, true))
            {
                n++;
            }
        }
        return n;
    }

    /** someone working there on duty */
    public static boolean staffed(Building b)
    {
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.VENUESTAFF && f.action == FolkAction.ATWORK && b.primaryXYZ.isSameCoordsAs(f.employedAt, true, true))
            {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- being there

    /** every second while they're out: getting there, then fun, company and meeting people, then home */
    public static void tick(FolkData f, long now)
    {
        if (f.leisureAt == null)
        {
            return;
        }
        Building b = Building.getBuilding(f.leisureAt);
        Venue v = venueOf(b);
        boolean late = !SimukraftReloaded.isDayTime() && !evening();
        if (b == null || v == null || now > f.leisureUntil || late)
        {
            end(f);
            return;
        }
        if (!f.leisureArrived)
        {
            if (f.isSpawned())
            {
                f.updateLocationFromEntity();
            }
            if (!f.isSpawned() || f.location.getDistanceTo(f.leisureAt) <= 5 || now - f.leisureSince > 60000)
            {
                f.leisureArrived = true;
                f.lastLeisureTick = now;
                if (!f.leisurePaid && v.price > 0)
                {
                    Economy.purchase(f, b.primaryXYZ, v.price);
                    f.leisurePaid = true;
                }
                f.destination = null;
                f.stayPut = true;
            }
            else
            {
                return;
            }
        }
        // the status says "Relaxing" so their fun holds up (FolkData's needs look for it)
        f.statusText = "Relaxing at the " + b.displayNameWithoutPK + " - " + v.doing;
        if (now - f.lastLeisureTick < TICK_MS)
        {
            return;
        }
        f.lastLeisureTick = now;
        boolean staff = staffed(b);
        f.levelFun = Math.min(10, f.levelFun + v.fun + (staff ? 1 : 0));
        f.funNote = "Had a good time " + v.doing + " at the " + b.displayNameWithoutPK;
        FolkData other = someoneElseThere(f, b);
        if (other != null)
        {
            if (v.social > 0)
            {
                f.levelSocial = Math.min(10, f.levelSocial + v.social);
                f.socialNote = "Out with " + other.name + " at the " + b.displayNameWithoutPK;
            }
            // getting to know each other: the friendly more so
            int chance = 30 + (f.hasTrait(Traits.traitFriendly) ? 25 : 0) + v.social * 10;
            if (rand.nextInt(100) < chance)
            {
                meet(f, other);
            }
        }
    }

    private static FolkData someoneElseThere(FolkData f, Building b)
    {
        ArrayList<FolkData> there = new ArrayList<FolkData>();
        for (FolkData o : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (o != f && o.leisureAt != null && o.leisureArrived && o.leisureAt.isSameCoordsAs(b.primaryXYZ, true, true))
            {
                there.add(o);
            }
        }
        return there.isEmpty() ? null : there.get(rand.nextInt(there.size()));
    }

    /** two Sims out at the same place get on (mostly) */
    private static void meet(FolkData a, FolkData b)
    {
        if (a.isVisitor || b.isVisitor)
        {
            return;
        }
        Relationship rel = Relationship.getRelationshipBetween(a, b);
        if (rel == null)
        {
            Relationship.addRelationship(new Relationship(a, b, Relationship.Level.AQUAINTANCE, false));
            return;
        }
        if (rand.nextInt(10) == 0)
        {
            rel.levelDecrease(5 + rand.nextInt(10));
        }
        else
        {
            rel.levelIncrease(10 + rand.nextInt(20));
        }
    }

    /** the evening or the visit's over: home (at night) or off wandering */
    public static void end(FolkData f)
    {
        f.leisureAt = null;
        f.leisureArrived = false;
        f.stayPut = false;
        f.destination = null;
        f.statusText = SimukraftReloaded.isDayTime() ? "Wandering" : "Going Home";
        f.action = FolkAction.WANDER;
    }
}
