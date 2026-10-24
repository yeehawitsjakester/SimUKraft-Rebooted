package ashjack.simukraftreloaded.common.jobs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Kind;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Source;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Keeps a warehouse: knows what's in its chests, the mines' chests, the chests left by finished buildings' constructor
 * boxes and a connected ME network, and carries what builders still need to the chests by their constructor boxes,
 * starting with whatever a builder is stuck waiting for.
 *
 * Deliveries are reserved while under way (shared by every keeper), so two trips never count on the same items. The
 * stock itself is read fresh on each scan rather than tallied. What the keeper carries is in FolkData.inventory, which
 * is saved, and goes back into the warehouse if a trip is interrupted.
 */
public class JobWarehouse extends Job
{
    public Vocation vocation = null;
    public FolkData theFolk = null;
    public Stage theStage;

    public enum Stage
    {
        IDLE, FETCHING, DELIVERING, RETURNING;
    }

    /** a trip under way */
    static class Delivery
    {
        String keeper;
        String builder;
        V3 to;
        Source from;
        Item item;
        int qty;
    }

    /** every keeper's trips, so they don't promise the same items twice */
    static final ArrayList<Delivery> underWay = new ArrayList<Delivery>();

    private static final int TRIP_MAX = 256;
    private static final long RUN_MS = 2000, SCAN_MS = 15000;

    private transient Delivery current;
    private transient WarehouseStock stock;
    private transient long lastRun, lastScan;

    public JobWarehouse() {}

    public JobWarehouse(FolkData folk)
    {
        theFolk = folk;
        theStage = Stage.IDLE;

        if (theFolk != null && theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    @Override
    public void resetJob()
    {
        cancelTrip();
        theStage = Stage.IDLE;
        if (theFolk != null)
        {
            theFolk.isWorking = false;
        }
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.stayPut = true;
        theFolk.statusText = "Opening up the warehouse";
        theStage = Stage.IDLE;
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();

        if (!SimukraftReloaded.isDayTime())
        {
            // home time: anything they're carrying goes back in the warehouse in the morning
            cancelTrip();
            theStage = Stage.IDLE;
            return;
        }

        super.onUpdateGoingToWork(theFolk);

        if (theFolk.action != FolkAction.ATWORK || theFolk.employedAt == null || jobWorld == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRun < RUN_MS)
        {
            return;
        }
        lastRun = now;

        try
        {
            if (theStage == null || theStage == Stage.IDLE)
            {
                stageIdle(now);
            }
            else if (theStage == Stage.FETCHING)
            {
                stageFetching();
            }
            else if (theStage == Stage.DELIVERING)
            {
                stageDelivering();
            }
            else if (theStage == Stage.RETURNING)
            {
                if (arrived(theFolk.employedAt))
                {
                    theStage = Stage.IDLE;
                }
                else if (theFolk.destination == null)
                {
                    theFolk.gotoXYZ(theFolk.employedAt, null);
                }
            }
        }
        catch (Exception e)
        {
            SimukraftReloaded.log.warning("Warehouse: " + theFolk.name + " hit " + e);
            cancelTrip();
            theStage = Stage.IDLE;
        }
    }

    private void stageIdle(long now)
    {
        theFolk.isWorking = false;
        Building warehouse = Building.getBuilding(theFolk.employedAt);

        if (now - lastScan > SCAN_MS || stock == null)
        {
            stock = WarehouseStock.scan(warehouse, theFolk.employedAt, jobWorld);
            lastScan = now;
            publish();
        }

        // back from a trip (or a restart) with things still in hand: put them away first
        if (!theFolk.inventory.isEmpty())
        {
            if (!arrived(theFolk.employedAt))
            {
                theStage = Stage.RETURNING;
                theFolk.gotoXYZ(theFolk.employedAt, null);
                return;
            }
            putAway();
            if (!theFolk.inventory.isEmpty())
            {
                theFolk.statusText = "The warehouse is full!";
                return;
            }
            lastScan = 0; // the stock just changed
            return;
        }

        Delivery d = plan();
        if (d == null)
        {
            theFolk.statusText = stock.chestCount + " chests" + (stock.meTypes >= 0 ? " and an ME network" : "")
                    + " in stock, nothing to deliver";
            return;
        }

        current = d;
        underWay.add(d);
        String what = d.qty + " " + new ItemStack(d.item).getDisplayName();
        if (d.from.isLocal())
        {
            pickUp();
            if (theFolk.inventory.isEmpty())
            {
                cancelTrip();
                lastScan = 0;
                return;
            }
            theFolk.statusText = "Taking " + what + " to " + d.builder;
            theFolk.isWorking = true;
            theFolk.gotoXYZ(d.to, null);
            theStage = Stage.DELIVERING;
        }
        else
        {
            theFolk.statusText = "Fetching " + what + " from " + d.from.kind.label + " for " + d.builder;
            theFolk.isWorking = true;
            theFolk.gotoXYZ(d.from.at, null);
            theStage = Stage.FETCHING;
        }
    }

    private void stageFetching()
    {
        if (current == null)
        {
            goBack();
            return;
        }
        if (!arrived(current.from.at))
        {
            if (theFolk.destination == null)
            {
                theFolk.gotoXYZ(current.from.at, null);
            }
            return;
        }
        pickUp();
        if (theFolk.inventory.isEmpty())
        {
            // someone emptied it since the last scan
            cancelTrip();
            lastScan = 0;
            goBack();
            return;
        }
        theFolk.statusText = "Taking " + carried(current.item) + " " + new ItemStack(current.item).getDisplayName() + " to " + current.builder;
        theFolk.gotoXYZ(current.to, null);
        theStage = Stage.DELIVERING;
    }

    private void stageDelivering()
    {
        if (current == null)
        {
            goBack();
            return;
        }
        if (!arrived(current.to))
        {
            if (theFolk.destination == null)
            {
                theFolk.gotoXYZ(current.to, null);
            }
            return;
        }
        ArrayList<IInventory> chests = WarehouseStock.storageNear(jobWorld, current.to, 4);
        if (chests.isEmpty())
        {
            theFolk.statusText = "There's no chest by " + current.builder + "'s constructor box";
            return;
        }
        int given = 0;
        for (Iterator<ItemStack> it = theFolk.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            if (is == null)
            {
                it.remove();
                continue;
            }
            int before = is.stackSize;
            ItemStack left = WarehouseStock.put(chests, is);
            given += before - (left == null ? 0 : left.stackSize);
            if (left == null)
            {
                it.remove();
            }
            else
            {
                is.stackSize = left.stackSize;
            }
        }
        if (given > 0)
        {
            if (GameMode.gameMode != GameMode.GAMEMODES.CREATIVE)
            {
                SimukraftReloaded.states().credits -= 0.05f;
            }
            SimukraftReloaded.log.info("Warehouse: " + theFolk.name + " delivered " + given + " "
                    + new ItemStack(current.item).getDisplayName() + " to " + current.builder);
        }
        theFolk.statusText = theFolk.inventory.isEmpty() ? "Delivered " + given + " to " + current.builder
                : current.builder + "'s chests are full, taking the rest back";
        cancelTrip();
        goBack();
    }

    // ---------------------------------------------------------------- planning

    /** the most useful trip: a block a builder is stuck on first, then whatever's most short */
    private Delivery plan()
    {
        Delivery best = null;
        int bestScore = 0;

        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (!(f.theirJob instanceof JobBuilder) || f.employedAt == null
                    || f.employedAt.theDimension != theFolk.employedAt.theDimension)
            {
                continue;
            }
            JobBuilder job = (JobBuilder) f.theirJob;
            Map<Item, Integer> needs = job.remainingNeeds();
            if (needs.isEmpty())
            {
                continue;
            }
            ArrayList<IInventory> theirChests = WarehouseStock.storageNear(jobWorld, f.employedAt, 4);
            if (theirChests.isEmpty())
            {
                continue; // nowhere to put it
            }
            for (Map.Entry<Item, Integer> need : needs.entrySet())
            {
                Item item = need.getKey();
                int shortBy = need.getValue() - WarehouseStock.count(theirChests, item) - onTheWay(f.employedAt, item);
                if (shortBy <= 0)
                {
                    continue;
                }
                Source from = bestSource(item);
                if (from == null)
                {
                    continue;
                }
                boolean stuck = job.wanted != null && job.wanted.getItem() == item;
                int score = (stuck ? 1000000 : 0) + Math.min(shortBy, TRIP_MAX);
                if (score > bestScore)
                {
                    bestScore = score;
                    best = new Delivery();
                    best.keeper = theFolk.name;
                    best.builder = f.name;
                    best.to = f.employedAt.clone();
                    best.from = from;
                    best.item = item;
                    best.qty = Math.min(Math.min(shortBy, TRIP_MAX), from.count(item) - reserved(from, item));
                }
            }
        }
        return best;
    }

    /** where to pick an item up: the warehouse (or ME) if it has any spare, else the closest place that does */
    private Source bestSource(Item item)
    {
        Source best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Source s : stock.sources)
        {
            if (s.count(item) - reserved(s, item) <= 0)
            {
                continue;
            }
            int dist = s.isLocal() ? -1 : theFolk.employedAt.getDistanceTo(s.at);
            if (dist < bestDist)
            {
                best = s;
                bestDist = dist;
            }
        }
        return best;
    }

    private static int reserved(Source s, Item item)
    {
        int n = 0;
        for (Delivery d : underWay)
        {
            if (d.item == item && d.from.kind == s.kind && d.from.at.isSameCoordsAs(s.at, true, true))
            {
                n += d.qty;
            }
        }
        return n;
    }

    private static int onTheWay(V3 to, Item item)
    {
        int n = 0;
        for (Delivery d : underWay)
        {
            if (d.item == item && d.to.isSameCoordsAs(to, true, true))
            {
                n += d.qty;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- carrying

    private void pickUp()
    {
        List<ItemStack> got;
        if (current.from.kind == Kind.ME)
        {
            got = WarehouseStock.takeFromNetwork(current.from.network, current.item, current.qty);
        }
        else
        {
            // re-read the chests now: the scan may be a few seconds old
            ArrayList<IInventory> chests = current.from.kind == Kind.WAREHOUSE ? current.from.chests
                    : WarehouseStock.storageNear(jobWorld, current.from.at, 4);
            got = WarehouseStock.take(chests, current.item, current.qty);
        }
        theFolk.inventory.addAll(got);
    }

    /** everything in hand into the warehouse chests, then the ME network */
    private void putAway()
    {
        Building warehouse = Building.getBuilding(theFolk.employedAt);
        WarehouseStock here = WarehouseStock.scan(warehouse, theFolk.employedAt, jobWorld);
        Source chests = null, me = null;
        for (Source s : here.sources)
        {
            if (s.kind == Kind.WAREHOUSE)
            {
                chests = s;
            }
            else if (s.kind == Kind.ME)
            {
                me = s;
            }
        }
        for (Iterator<ItemStack> it = theFolk.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            ItemStack left = is;
            if (chests != null)
            {
                left = WarehouseStock.put(chests.chests, left);
            }
            if (left != null && me != null)
            {
                left = WarehouseStock.putIntoNetwork(me.network, left);
            }
            if (left == null)
            {
                it.remove();
            }
            else
            {
                is.stackSize = left.stackSize;
            }
        }
        theFolk.statusText = theFolk.inventory.isEmpty() ? "Put everything away" : "The warehouse is full!";
    }

    private int carried(Item item)
    {
        int n = 0;
        for (ItemStack is : theFolk.inventory)
        {
            if (is != null && is.getItem() == item)
            {
                n += is.stackSize;
            }
        }
        return n;
    }

    private void cancelTrip()
    {
        if (current != null)
        {
            underWay.remove(current);
            current = null;
        }
        // trips of keepers who were fired or reloaded
        for (Iterator<Delivery> it = underWay.iterator(); it.hasNext();)
        {
            FolkData k = FolkData.getFolkByName(it.next().keeper);
            if (k == null || !(k.theirJob instanceof JobWarehouse) || ((JobWarehouse) k.theirJob).current == null)
            {
                it.remove();
            }
        }
    }

    private void goBack()
    {
        theStage = Stage.RETURNING;
        theFolk.gotoXYZ(theFolk.employedAt, null);
    }

    private boolean arrived(V3 where)
    {
        theFolk.updateLocationFromEntity();
        return theFolk.location != null && where != null && theFolk.location.theDimension == where.theDimension
                && theFolk.location.getDistanceTo(where) <= 3;
    }

    // ---------------------------------------------------------------- for the stock screen

    /** the stock and trips as text lines on FolkData, which MirrorSync sends to the client */
    private void publish()
    {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#|" + stock.chestCount + "|" + stock.meTypes);
        for (Map.Entry<String, int[]> e : stock.byStack.entrySet())
        {
            int[] c = e.getValue();
            StringBuilder sb = new StringBuilder(e.getKey());
            for (int n : c)
            {
                sb.append('|').append(n);
            }
            lines.add(sb.toString());
        }
        theFolk.warehouseStock = lines;

        ArrayList<String> trips = new ArrayList<String>();
        for (Delivery d : underWay)
        {
            trips.add(d.keeper + "|" + d.qty + "|" + Item.itemRegistry.getNameForObject(d.item) + "|" + d.builder + "|" + d.from.kind.label);
        }
        theFolk.warehouseTrips = trips;
    }
}
