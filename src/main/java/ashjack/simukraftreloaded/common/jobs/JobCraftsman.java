package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;

/**
 * A blacksmith or fletcher at a smithy, making what the town's soldiers are short of (see Armory.wanted and
 * Workshop.orders): swords at the forge - diamond ones too once the smithy's been upgraded - and bows and arrows at the
 * fletcher's bench. They work from the chests by the smithy's control box; a warehouse keeper brings the iron, string,
 * feathers, flint and logs they ask for and takes what they make to the barracks.
 *
 * Or a stonemason at a masonry, making the stone the builders are short of: smelting cobblestone into stone, clay
 * into bricks and netherrack into nether bricks over coal, and cutting stone bricks, brick blocks, slabs, stairs and
 * walls from them.
 */
public class JobCraftsman extends Job implements Serializable
{
    private static final long serialVersionUID = 1L;

    public Vocation vocation = null;
    public FolkData theFolk = null;
    public transient Workshop.Shop shop;

    private transient long lastRun, lastOrders;
    /** how many more things the coal burning in the masonry's furnace will smelt */
    private transient int[] fuel = {0};
    private transient LinkedHashMap<Item, Integer> orders = new LinkedHashMap<Item, Integer>();
    /** what they're stuck for, and how many: a keeper brings it */
    private transient LinkedHashMap<Item, Integer> missing = new LinkedHashMap<Item, Integer>();

    private static final long RUN_MS = 1500, ORDERS_MS = 10000;

    public JobCraftsman() {}

    public JobCraftsman(FolkData folk, Vocation v)
    {
        theFolk = folk;
        vocation = v;
        if (theFolk != null && theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    Workshop.Shop shop()
    {
        if (shop == null)
        {
            Vocation v = theFolk != null && theFolk.vocation != null ? theFolk.vocation : vocation;
            shop = v == Vocation.FLETCHER ? Workshop.Shop.FLETCHER : v == Vocation.STONEMASON ? Workshop.Shop.MASON : Workshop.Shop.SMITHY;
        }
        return shop;
    }

    /** whether any smithy in this dimension has been upgraded to forge diamond (and has a blacksmith) */
    public static boolean forgesDiamond(int dim)
    {
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.BLACKSMITH && f.employedAt != null && f.employedAt.theDimension == dim)
            {
                Building b = Building.getBuilding(f.employedAt);
                if (b != null && b.level >= 2)
                {
                    return true;
                }
            }
        }
        return false;
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
        theFolk.stayPut = true;
        theFolk.action = FolkAction.ATWORK;
        theFolk.statusText = shop() == Workshop.Shop.SMITHY ? "Lighting the forge"
                : shop() == Workshop.Shop.MASON ? "Sharpening the chisels" : "Setting out the bench";
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
        if (theFolk.action != FolkAction.ATWORK || theFolk.employedAt == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRun < RUN_MS)
        {
            return;
        }
        lastRun = now;
        if (jobWorld == null)
        {
            jobWorld = MinecraftServer.getServer().worldServerForDimension(theFolk.employedAt.theDimension);
        }
        if (jobWorld == null)
        {
            return;
        }
        ArrayList<IInventory> chests = Workshop.chests(jobWorld, theFolk.employedAt);
        if (chests.isEmpty())
        {
            theFolk.statusText = "Needs a chest by the control box to work from";
            theFolk.isWorking = false;
            return;
        }
        if (now - lastOrders > ORDERS_MS || orders == null)
        {
            lastOrders = now;
            orders = Workshop.orders(shop(), theFolk, chests, jobWorld);
            if (orders.containsKey(Items.diamond_sword))
            {
                Building b = Building.getBuilding(theFolk.employedAt);
                if (b == null || b.level < 2)
                {
                    orders.remove(Items.diamond_sword); // another smithy's job
                }
            }
        }
        if (orders.isEmpty())
        {
            missing.clear();
            theFolk.isWorking = false;
            theFolk.statusText = shop() == Workshop.Shop.SMITHY ? "No swords wanted"
                    : shop() == Workshop.Shop.MASON ? "No stone wanted" : "No bows or arrows wanted";
            return;
        }

        // an electric furnace (a masonry upgrade, on the grid): no coal, and two at a time
        boolean electric = shop() == Workshop.Shop.MASON
                && ashjack.simukraftreloaded.common.power.PowerGrid.electricFurnace(jobWorld, Building.getBuilding(theFolk.employedAt));
        if (electric)
        {
            fuel[0] = Math.max(fuel[0], 8);
        }
        for (int pass = 0; pass < (electric ? 2 : 1); pass++)
        {
            if (!craftOne(chests))
            {
                return;
            }
        }
    }

    /** one craft for the first order something can be done for; false if nothing could be */
    private boolean craftOne(ArrayList<IInventory> chests)
    {
        // the first order something can be done for; what the others are stuck on is asked for meanwhile
        LinkedHashMap<Item, Integer> stuck = new LinkedHashMap<Item, Integer>();
        for (Map.Entry<Item, Integer> o : new ArrayList<Map.Entry<Item, Integer>>(orders.entrySet()))
        {
            if (fuel == null)
            {
                fuel = new int[] {0};
            }
            Workshop.Step s = Workshop.step(shop(), chests, o.getKey(), fuel);
            if (s.made != null)
            {
                missing = stuck;
                theFolk.isWorking = true;
                theFolk.statusText = "Made " + s.made.stackSize + " " + s.made.getDisplayName()
                        + (s.made.getItem() == o.getKey() ? "" : " for " + new ItemStack(o.getKey()).getDisplayName());
                jobWorld.playSoundEffect(theFolk.employedAt.x, theFolk.employedAt.y, theFolk.employedAt.z,
                        shop() == Workshop.Shop.SMITHY ? "random.anvil_use" : shop() == Workshop.Shop.MASON ? "dig.stone" : "dig.wood", 0.4f, 1f);
                if (s.made.getItem() == o.getKey())
                {
                    int left = o.getValue() - s.made.stackSize;
                    if (left > 0)
                    {
                        orders.put(o.getKey(), left);
                    }
                    else
                    {
                        orders.remove(o.getKey());
                    }
                    Economy.pay(theFolk, 0.05f);
                }
                return true;
            }
            if (s.missing != null)
            {
                Integer n = stuck.get(s.missing.getItem());
                // enough for the whole order, not just the next one
                int want = Math.max(s.missingCount, s.missingCount * o.getValue());
                stuck.put(s.missing.getItem(), Math.min(256, (n == null ? 0 : n) + want));
            }
        }
        missing = stuck;
        theFolk.isWorking = false;
        theFolk.statusText = stuck.isEmpty() ? "Can't make what's wanted here"
                : "Waiting for " + new ItemStack(stuck.keySet().iterator().next()).getDisplayName();
        return false;
    }

    @Override
    public Map<Item, Integer> remainingNeeds()
    {
        return missing == null ? new LinkedHashMap<Item, Integer>() : new LinkedHashMap<Item, Integer>(missing);
    }

    @Override
    public Item waitingFor()
    {
        return missing == null || missing.isEmpty() || theFolk.isWorking ? null : missing.keySet().iterator().next();
    }

    @Override
    public boolean isWorkshop()
    {
        return true;
    }

    @Override
    public boolean workshopOffers(Item item)
    {
        return shop().makes(item);
    }

    @Override
    public String workshopName()
    {
        return shop() == Workshop.Shop.MASON ? "the masonry" : "the smithy";
    }
}
