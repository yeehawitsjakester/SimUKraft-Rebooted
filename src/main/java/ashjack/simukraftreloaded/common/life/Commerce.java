package ashjack.simukraftreloaded.common.life;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.traits.Traits;

/**
 * Sims' own businesses. A mall, market, shopping centre or strip mall is let out in units (by its size), and a shop or
 * store as one; Sims with savings - out of work, or just wanting to be their own boss - lease one, pay a deposit to
 * the town, choose something to sell (what the shop's name says, or what the town has fewest of) and run it as its
 * shopkeeper. Sims out shopping buy from whichever's open, liking a fair price and what suits them; the owner keeps
 * the margin (the goods are bought in from the town at cost).
 *
 * Every morning each pays its rent to the town. One that can't, or that hardly sold, gets a strike; three in a row and
 * the lease is lost (the owner's out of work, the unit's free). One doing well is talked about.
 */
public final class Commerce
{
    private Commerce() {}

    public enum Line
    {
        GROCERIES("Groceries", 1.0f), CLOTHES("Clothes", 2.0f), BOOKS("Books", 1.5f), FLOWERS("Flowers", 0.8f),
        TOYS("Toys", 1.5f), HARDWARE("Hardware", 2.5f), JEWELLERY("Jewellery", 4.0f), SWEETS("Sweets", 0.6f), PETS("Pets", 3.0f);

        public final String title;
        public final float price;

        Line(String title, float price)
        {
            this.title = title;
            this.price = price;
        }

        public static Line of(String title)
        {
            for (Line l : values())
            {
                if (l.title.equals(title))
                {
                    return l;
                }
            }
            return GROCERIES;
        }
    }

    /** what the goods cost the shop, as a share of their price */
    public static final float COST = 0.6f;
    private static final Random rand = new Random();

    // ---------------------------------------------------------------- premises

    /** how many units a building lets out (0 if it isn't somewhere to lease) */
    public static int units(Building b)
    {
        if (b == null || b.displayName == null || !b.buildingComplete || b.isRoom() || "residential".equals(b.type))
        {
            return 0;
        }
        String n = (b.displayNameWithoutPK != null ? b.displayNameWithoutPK : b.displayName).toLowerCase();
        boolean mall = n.contains("mall") || n.contains("market") || n.contains("shopping") || n.contains("plaza shops")
                || n.contains("arcade shops");
        boolean shop = n.contains("shop") || n.contains("store") || n.contains("boutique") || n.contains("emporium");
        if (!mall && !shop)
        {
            return 0;
        }
        // a shop that's a Sim-U-Kraft workplace already (the grocery store's grocer) isn't let out
        if (!mall)
        {
            for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
            {
                if (f.vocation != Vocation.SHOPKEEPER && b.primaryXYZ != null && b.primaryXYZ.isSameCoordsAs(f.employedAt, true, true))
                {
                    return 0;
                }
            }
            return 1;
        }
        return Math.max(2, Math.min(12, b.ltrCount * b.ftbCount / 40));
    }

    /** the daily rent for a unit: more in a big mall, where the customers are */
    public static float rent(Building b)
    {
        return 2.0f + 0.25f * units(b);
    }

    public static float deposit(Building b)
    {
        return 3 * rent(b) + 10;
    }

    // ---------------------------------------------------------------- opening up

    /** once a day for a Sim with savings: maybe they lease a unit and open a shop. True if they did */
    public static boolean maybeOpen(FolkData f)
    {
        if (f.age < 18 || f.isVisitor || f.vocation == Vocation.SHOPKEEPER || f.location == null)
        {
            return false;
        }
        boolean idle = f.employedAt == null;
        // out of work: likely; in work: the odd one wants to be their own boss, if they've plenty put by
        if (!(idle && rand.nextInt(3) == 0 || !idle && rand.nextInt(25) == 0))
        {
            return false;
        }
        Town town = Towns.of(f);
        Building where = null;
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            int units = units(b);
            if (units > b.leases.size() && b.primaryXYZ != null && (town == null || town.contains(b.primaryXYZ))
                    && f.money >= deposit(b) * (idle ? 1 : 3) && (where == null
                            || b.primaryXYZ.getDistanceTo(f.location) < where.primaryXYZ.getDistanceTo(f.location)))
            {
                where = b;
            }
        }
        if (where == null)
        {
            return false;
        }
        float deposit = deposit(where);
        f.money -= deposit;
        Economy.earn(Towns.at(where.primaryXYZ), deposit);
        Lease l = new Lease();
        l.owner = f.name;
        l.line = chooseLine(where).title;
        l.markup = 1.2f + rand.nextFloat() * 0.6f;
        l.since = day();
        where.leases.add(l);
        if (!idle && f.theirJob != null)
        {
            f.theirJob.resetJob();
        }
        f.employedAt = where.primaryXYZ.clone();
        f.setTheirJob(Vocation.SHOPKEEPER);
        where.saveThisBuilding();
        SimukraftReloaded.sendChat(f.name + " has opened a " + l.line.toLowerCase() + " shop at the " + where.displayNameWithoutPK);
        return true;
    }

    /** what a new shop sells: what the shop's called after, else what the town has fewest of */
    private static Line chooseLine(Building b)
    {
        String n = b.displayName.toLowerCase();
        for (Line l : Line.values())
        {
            if (n.contains(l.title.toLowerCase().replaceAll("s$", "")) || l == Line.PETS && n.contains("pet")
                    || l == Line.FLOWERS && n.contains("flower") || l == Line.GROCERIES && n.contains("grocer"))
            {
                return l;
            }
        }
        int[] count = new int[Line.values().length];
        for (Building o : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            for (Lease l : o.leases)
            {
                count[Line.of(l.line).ordinal()]++;
            }
        }
        Line best = Line.GROCERIES;
        for (Line l : Line.values())
        {
            if (l != Line.PETS && count[l.ordinal()] + rand.nextDouble() < count[best.ordinal()] + rand.nextDouble())
            {
                best = l;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- customers

    /** a Sim out shopping here buys something, if a shop's open and they've the money; says what, or null */
    public static String shop(FolkData f, Building b)
    {
        if (b.leases.isEmpty() || f.money <= 0)
        {
            return null;
        }
        Lease pick = null;
        double best = 0;
        for (Lease l : b.leases)
        {
            FolkData owner = FolkData.getFolkByName(l.owner);
            if (owner == null || owner == f || !open(owner, b))
            {
                continue;
            }
            Line line = Line.of(l.line);
            double price = line.price * l.markup;
            if (price > f.money)
            {
                continue;
            }
            // a fair price and goods that suit them
            double score = 2.5 - l.markup + appeal(f, line) + rand.nextDouble();
            if (score > best)
            {
                best = score;
                pick = l;
            }
        }
        if (pick == null)
        {
            return null;
        }
        Line line = Line.of(pick.line);
        float price = line.price * pick.markup;
        FolkData owner = FolkData.getFolkByName(pick.owner);
        f.money -= price;
        float cost = line.price * COST;
        Town town = Towns.at(b.primaryXYZ);
        Economy.earn(town, cost + (price - cost) * Economy.taxRate(town));
        owner.money += (float) ((price - cost) * (1 - Economy.taxRate(town)));
        pick.today += price;
        pick.total += price;
        pick.salesToday++;
        return "bought some " + line.title.toLowerCase() + " from " + owner.name;
    }

    private static boolean open(FolkData owner, Building b)
    {
        return owner.vocation == Vocation.SHOPKEEPER && b.primaryXYZ.isSameCoordsAs(owner.employedAt, true, true)
                && owner.action == FolkData.FolkAction.ATWORK;
    }

    private static double appeal(FolkData f, Line l)
    {
        double a = 0;
        if (f.hasTrait(Traits.traitGreenThumb) && l == Line.FLOWERS) a += 1.5;
        if (f.hasTrait(Traits.traitWorkaholic) && (l == Line.BOOKS || l == Line.HARDWARE)) a += 1;
        if (f.hasTrait(Traits.traitFriendly) && (l == Line.SWEETS || l == Line.TOYS)) a += 1;
        if (f.hasTrait(Traits.traitStrong) && l == Line.HARDWARE) a += 1;
        if (f.hasTrait(Traits.traitLovesOutdoors) && (l == Line.FLOWERS || l == Line.PETS)) a += 1;
        if (l == Line.GROCERIES && f.levelFood < 6) a += 2;
        if (l == Line.JEWELLERY && f.money < 30) a -= 2;
        return a;
    }

    // ---------------------------------------------------------------- every morning

    private static long lastDay = -1;

    /** called often: once each new day, rents are paid and the struggling lose their lease */
    public static void tick()
    {
        long d = day();
        if (d == lastDay || d < 0)
        {
            return;
        }
        boolean first = lastDay < 0;
        lastDay = d;
        if (first)
        {
            return; // the server's just started: not a new day
        }
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b.leases.isEmpty())
            {
                continue;
            }
            float rent = rent(b);
            Town town = Towns.at(b.primaryXYZ);
            boolean changed = false;
            for (Iterator<Lease> it = b.leases.iterator(); it.hasNext();)
            {
                Lease l = it.next();
                FolkData owner = FolkData.getFolkByName(l.owner);
                if (owner == null || owner.vocation != Vocation.SHOPKEEPER || !b.primaryXYZ.isSameCoordsAs(owner.employedAt, true, true))
                {
                    it.remove(); // they've gone, or moved on
                    changed = true;
                    continue;
                }
                boolean paid = owner.money >= rent;
                if (paid)
                {
                    owner.money -= rent;
                    Economy.earn(town, rent);
                }
                boolean poor = l.today < rent * 0.75f;
                l.strikes = !paid || poor ? l.strikes + 1 : 0;
                if (l.today >= rent * 4 && rand.nextInt(3) == 0)
                {
                    SimukraftReloaded.sendChat(owner.name + "'s " + l.line.toLowerCase() + " shop at the " + b.displayNameWithoutPK
                            + " is doing a roaring trade");
                    owner.levelFun = Math.min(10, owner.levelFun + 2);
                }
                l.yesterday = l.today;
                l.today = 0;
                l.salesToday = 0;
                if (l.strikes >= 3)
                {
                    it.remove();
                    owner.theirJob = null;
                    owner.vocation = null;
                    owner.employedAt = null;
                    owner.statusText = "Lost the lease on their shop";
                    owner.levelFun = Math.max(1, owner.levelFun - 3);
                    SimukraftReloaded.sendChat(owner.name + "'s " + l.line.toLowerCase() + " shop at the " + b.displayNameWithoutPK
                            + " has closed - " + (paid ? "not enough customers" : "they couldn't pay the rent"));
                }
                changed = true;
            }
            if (changed)
            {
                b.saveThisBuilding();
            }
        }
        // and the odd Sim with savings opens up
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            maybeOpen(f);
        }
    }

    public static long day()
    {
        try
        {
            return MinecraftServer.getServer().worldServers[0].getTotalWorldTime() / 24000L;
        }
        catch (Exception e)
        {
            return -1;
        }
    }
}
