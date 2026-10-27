package ashjack.simukraftreloaded.common.jobs;

import ashjack.simukraftreloaded.core.town.Economy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobMiner.Stage;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSapling;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class JobLumberjack extends Job implements Serializable
{
    private static final long serialVersionUID = -1177112207904887741L;

    public Vocation vocation = null;
    public FolkData theFolk = null;
    public Stage theStage;
    transient public int runDelay = 1000;
    transient public long timeSinceLastRun = 0;

    private transient ArrayList<IInventory> millChests = new ArrayList<IInventory>();
    private transient V3 foundWoodAt = new V3();
    private transient Building lumbermill = null;
    private transient long startedGoing = 0l;
    private transient boolean isChopping = false;
    private transient long choppingUntil = 0;
    private transient float pay = 0f;
    private transient boolean onRoute = false;
    private transient Forest.Search search;
    private transient Forest.Tree tree;
    private transient int logIndex;
    private transient long nextChop, idleUntil, allowedMs;
    private transient boolean saidNoTrees;

    /** columns of the area looked over each second, logs carried before taking them back, and the time a log takes */
    private static final int COLUMNS_PER_LOOK = 1500, LOAD = 64;
    private static final long CHOP_MS = 700;
    private static final long ORDERS_MS = 10000;
    private transient LinkedHashMap<Item, Integer> sawOrders, sawMissing = new LinkedHashMap<Item, Integer>();
    private transient long lastOrders;
    /** sawing stopped for want of logs: try again once some come in (a delivery, or their own) */
    private transient boolean sawStuck;

    public JobLumberjack() {}
    public JobLumberjack(FolkData folk)
    {
        theFolk = folk;

        if (theStage == null)
        {
            theStage = Stage.IDLE;
        }

        if (theFolk == null)
        {
            return;   // is null when first employing, this is for next day(s)
        }

        if (theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    public enum Stage
    {
        IDLE, ARRIVEDATMILL, SCANFORTREE, GOTOTREE, CHOPPINGTREE, RETURNWOOD, SAWING;
    }

    public void resetJob()
    {
        Forest.release(tree);
        tree = null;
        theStage = Stage.IDLE;
        theFolk.isWorking = false;
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();

        if (!SimukraftReloaded.isDayTime())
        {
            theStage = Stage.IDLE;
            Forest.release(tree);
            tree = null;
        }

        super.onUpdateGoingToWork(theFolk);

        if (System.currentTimeMillis() - timeSinceLastRun < runDelay)
        {
            return;
        }

        timeSinceLastRun = System.currentTimeMillis();

        // ////////////////IDLE
        if (theStage == Stage.IDLE && SimukraftReloaded.isDayTime())
        {
            theStage = Stage.SCANFORTREE;
        }
        else if (theStage == Stage.ARRIVEDATMILL)
        {
            theStage = Stage.SCANFORTREE;
        }
        else if (theStage == Stage.SCANFORTREE)
        {
            stageScanForTree();
        }
        else if (theStage == Stage.GOTOTREE)
        {
            pickUpSaplings();
            stageGotoTree();
        }
        else if (theStage == Stage.CHOPPINGTREE)
        {
            stageChoppingTree();
            pickUpSaplings();
        }
        else if (theStage == Stage.RETURNWOOD)
        {
            stageReturnWood();
            pickUpSaplings();
        }
        else if (theStage == Stage.SAWING)
        {
            stageSawing();
        }
    }

    private void stageScanForTree()
    {
        theFolk.action = FolkAction.ATWORK;
        theFolk.isWorking = false;
        lumbermill = Building.getBuilding(theFolk.employedAt);
        if (lumbermill != null && jobWorld != null && sawingWanted())
        {
            theStage = Stage.SAWING;
            step = 1;
            return;
        }
        if (System.currentTimeMillis() < idleUntil)
        {
            return;
        }
        if (lumbermill == null || jobWorld == null)
        {
            return;
        }
        V3 centre = lumbermill.lumbermillMarker != null ? lumbermill.lumbermillMarker : theFolk.employedAt;
        int radius = SimukraftReloadedConfig.configLumberArea;
        if (search == null || !search.isFor(centre, radius))
        {
            search = new Forest.Search(centre, radius);
        }
        tree = search.step(jobWorld, COLUMNS_PER_LOOK);
        if (tree == null)
        {
            if (!search.exhausted)
            {
                theFolk.statusText = "Looking for wild trees";
                return;
            }
            search.exhausted = false;
            if (woodCount() > 0)
            {
                theStage = Stage.RETURNWOOD;
                step = 1;
                return;
            }
            theFolk.statusText = "No wild trees within " + radius + " blocks";
            if (!saidNoTrees)
            {
                saidNoTrees = true;
                SimukraftReloaded.sendChat(theFolk.name + " can't find any wild trees within " + radius
                        + " blocks of the lumbermill" + (lumbermill.lumbermillMarker != null ? "'s marker" : "")
                        + (SimukraftReloadedConfig.configLumberInsideTown ? "" : " outside town") + " - still looking");
            }
            idleUntil = System.currentTimeMillis() + 60000;
            return;
        }
        saidNoTrees = false;
        foundWoodAt = tree.base(jobWorld.provider.dimensionId);
        logIndex = 0;
        theStage = Stage.GOTOTREE;
        onRoute = false;
    }

    private void stageGotoTree()
    {
        theFolk.isWorking = false;
        if (tree == null || search == null)
        {
            theStage = Stage.SCANFORTREE; // the tree was forgotten in a restart
            return;
        }

        if (!onRoute)
        {
            theFolk.statusText = "Going to a tree";
            theFolk.gotoXYZ(foundWoodAt, null);
            startedGoing = System.currentTimeMillis();
            theFolk.updateLocationFromEntity();
            // half a second a block to get there, and a bit
            allowedMs = 20000 + 500L * Math.max(0, theFolk.location.getDistanceTo(foundWoodAt));
            onRoute = true;
            return;
        }
        if (theFolk.gotoMethod == GotoMethod.WALK)
        {
            theFolk.updateLocationFromEntity();
        }

        if (theFolk.location.getDistanceTo(foundWoodAt) < 7)
        {
            startChopping();
        }
        else if (System.currentTimeMillis() - startedGoing > allowedMs)
        {
            if (theFolk.isSpawned())
            {
                // couldn't get through: another tree, and not this one again for now
                search.reject(tree);
                Forest.release(tree);
                tree = null;
                theFolk.destination = null;
                theStage = Stage.SCANFORTREE;
            }
            else
            {
                startChopping(); // nobody's watching: they got there
            }
        }
    }

    private void startChopping()
    {
        theStage = Stage.CHOPPINGTREE;
        theFolk.stayPut = true;
        theFolk.destination = null;
        nextChop = 0;
    }

    /** one log at a time, the trunk up and every branch, whatever wood it is */
    private void stageChoppingTree()
    {
        if (tree == null)
        {
            theStage = Stage.SCANFORTREE;
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextChop)
        {
            return;
        }
        theFolk.isWorking = true;
        while (logIndex < tree.logs.size())
        {
            int[] l = tree.logs.get(logIndex++);
            if (!jobWorld.getChunkProvider().chunkExists(l[0] >> 4, l[2] >> 4))
            {
                logIndex = tree.logs.size(); // that part of the world's gone quiet: leave the rest
                break;
            }
            Block b = jobWorld.getBlock(l[0], l[1], l[2]);
            if (!b.isWood(jobWorld, l[0], l[1], l[2]))
            {
                continue; // gone already
            }
            ArrayList<ItemStack> drops = b.getDrops(jobWorld, l[0], l[1], l[2], jobWorld.getBlockMetadata(l[0], l[1], l[2]), 0);
            jobWorld.playSoundEffect(l[0] + 0.5, l[1] + 0.5, l[2] + 0.5, "dig.wood", 1f, 1f);
            jobWorld.setBlock(l[0], l[1], l[2], Blocks.air, 0, 0x03);
            if (drops != null)
            {
                for (ItemStack d : drops)
                {
                    addToInventory(d);
                }
            }
            theFolk.statusText = "Felling a tree (" + logIndex + " of " + tree.logs.size() + " logs)";
            nextChop = now + CHOP_MS;
            return;
        }

        // felled
        V3 base = foundWoodAt;
        Forest.release(tree);
        tree = null;
        theFolk.stayPut = false;
        if (SimukraftReloadedConfig.configLumberReplant)
        {
            plantSapling(base);
        }
        if (woodCount() >= LOAD)
        {
            theStage = Stage.RETURNWOOD;
            step = 1;
        }
        else
        {
            theStage = Stage.SCANFORTREE;
        }
    }

    private void stageReturnWood()
    {
        theFolk.isWorking = false;

        if (step == 1)
        {
            theFolk.statusText = "Delivering wood back to base";
            theFolk.gotoXYZ(theFolk.employedAt, null);
            step = 2;
        }
        else if (step == 2)
        {
            if (theFolk.gotoMethod == GotoMethod.WALK)
            {
                theFolk.updateLocationFromEntity();
            }

            if (theFolk.location.getDistanceTo(theFolk.employedAt) <= 1)
            {
                step = 3;
            }
        }
        else if (step == 3)
        {
            theFolk.stayPut = true;
            int count = woodCount();
            millChests = millChests();
            // the logs, and the saplings, apples and cocoa beans that came with them
            inventoriesTransferFromFolk(theFolk.inventory, millChests, null);
            pay = (float)count * 0.03f;
            Economy.pay(theFolk, pay);
            SimukraftReloaded.sendChat(theFolk.name + " has delivered " + count + " logs at the lumbermill");
            sawStuck = false;
            theStage = sawingWanted() ? Stage.SAWING : Stage.SCANFORTREE;
            step = 1;
        }
    }

    // ---------------------------------------------------------------- the sawmill

    /** the mill's chests: by its control box (where the warehouse collects), or failing that anywhere close */
    private ArrayList<IInventory> millChests()
    {
        ArrayList<IInventory> c = Workshop.chests(jobWorld, theFolk.employedAt);
        return c.isEmpty() ? inventoriesFindClosest(theFolk.employedAt, 6) : c;
    }

    /** builders (or a smithy) short of planks, fences, doors or anything else that's only wood, which the mill could saw */
    private boolean sawingWanted()
    {
        long now = System.currentTimeMillis();
        if (now - lastOrders > ORDERS_MS || sawOrders == null)
        {
            lastOrders = now;
            ArrayList<IInventory> chests = millChests();
            sawOrders = Workshop.orders(Workshop.Shop.SAWMILL, theFolk, chests, jobWorld);
            if (sawStuck && hasLogs(chests))
            {
                sawStuck = false; // a keeper's brought some
            }
            if (sawOrders.isEmpty())
            {
                sawMissing.clear();
            }
        }
        return !sawOrders.isEmpty() && !sawStuck;
    }

    /** at the mill, one cut at a time from the logs in its chests (their own first); back to the trees when it's done */
    private void stageSawing()
    {
        theFolk.action = FolkAction.ATWORK;
        if (step == 1)
        {
            theFolk.statusText = "Back to the mill to saw";
            theFolk.gotoXYZ(theFolk.employedAt, null);
            step = 2;
            return;
        }
        theFolk.updateLocationFromEntity();
        if (theFolk.isSpawned() && theFolk.location.getDistanceTo(theFolk.employedAt) > 3)
        {
            if (theFolk.destination == null)
            {
                step = 1;
            }
            return;
        }
        theFolk.stayPut = true;
        ArrayList<IInventory> chests = millChests();
        if (chests.isEmpty())
        {
            theFolk.statusText = "The mill needs a chest by its control box";
            theStage = Stage.SCANFORTREE;
            return;
        }
        if (!theFolk.inventory.isEmpty())
        {
            int logs = woodCount();
            inventoriesTransferFromFolk(theFolk.inventory, chests, null);
            Economy.pay(theFolk, logs * 0.03f);
        }
        if (sawOrders == null || sawOrders.isEmpty())
        {
            theStage = Stage.SCANFORTREE;
            return;
        }
        LinkedHashMap<Item, Integer> stuck = new LinkedHashMap<Item, Integer>();
        for (Map.Entry<Item, Integer> o : new ArrayList<Map.Entry<Item, Integer>>(sawOrders.entrySet()))
        {
            Workshop.Step s = Workshop.step(Workshop.Shop.SAWMILL, chests, o.getKey());
            if (s.made != null)
            {
                sawMissing = stuck;
                theFolk.isWorking = true;
                theFolk.statusText = "Sawing " + new ItemStack(o.getKey()).getDisplayName() + " (" + o.getValue() + " wanted)";
                jobWorld.playSoundEffect(theFolk.employedAt.x, theFolk.employedAt.y, theFolk.employedAt.z, "dig.wood", 0.5f, 0.8f);
                if (s.made.getItem() == o.getKey())
                {
                    int left = o.getValue() - s.made.stackSize;
                    if (left > 0)
                    {
                        sawOrders.put(o.getKey(), left);
                    }
                    else
                    {
                        sawOrders.remove(o.getKey());
                    }
                    Economy.pay(theFolk, 0.01f);
                }
                return;
            }
            if (s.missing != null)
            {
                Integer n = stuck.get(s.missing.getItem());
                stuck.put(s.missing.getItem(), Math.min(256, (n == null ? 0 : n) + Math.max(s.missingCount, o.getValue() / 4 + 1)));
            }
        }
        // out of logs: ask the warehouse, and go and cut some meanwhile
        sawMissing = stuck;
        sawStuck = true;
        theFolk.isWorking = false;
        theFolk.stayPut = false;
        theFolk.statusText = "Out of logs to saw";
        theStage = Stage.SCANFORTREE;
    }

    @Override
    public Map<Item, Integer> remainingNeeds()
    {
        return sawMissing == null ? new LinkedHashMap<Item, Integer>() : new LinkedHashMap<Item, Integer>(sawMissing);
    }

    @Override
    public boolean isWorkshop()
    {
        return true;
    }

    @Override
    public boolean workshopOffers(Item item)
    {
        return true; // its logs, and what's sawn from them
    }

    @Override
    public String workshopName()
    {
        return "the lumbermill";
    }

    @Override
    public boolean workshopBusy()
    {
        return theStage == Stage.SAWING || sawOrders != null && !sawOrders.isEmpty();
    }

    private static boolean hasLogs(ArrayList<IInventory> chests)
    {
        for (IInventory inv : chests)
        {
            for (int i = 0; i < inv.getSizeInventory(); i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                if (is != null && is.getItem() != null && isLog(is))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** logs of any kind carried */
    private int woodCount()
    {
        int n = 0;
        for (ItemStack is : theFolk.inventory)
        {
            if (is != null && isLog(is))
            {
                n += is.stackSize;
            }
        }
        return n;
    }

    private static boolean isLog(ItemStack is)
    {
        if (Block.getBlockFromItem(is.getItem()) instanceof BlockLog)
        {
            return true;
        }
        int logWood = OreDictionary.getOreID("logWood");
        for (int id : OreDictionary.getOreIDs(is))
        {
            if (id == logWood)
            {
                return true;
            }
        }
        return false;
    }

    /** onto a stack of the same if there's room, so a big tree isn't hundreds of single logs */
    private void addToInventory(ItemStack add)
    {
        if (add == null || add.stackSize <= 0)
        {
            return;
        }
        for (ItemStack is : theFolk.inventory)
        {
            if (is != null && is.isItemEqual(add) && ItemStack.areItemStackTagsEqual(is, add)
                    && is.stackSize + add.stackSize <= is.getMaxStackSize())
            {
                is.stackSize += add.stackSize;
                return;
            }
        }
        theFolk.inventory.add(add.copy());
    }

    @Override
    public void onArrivedAtWork()
    {
        int dist = 0;
        dist = theFolk.location.getDistanceTo(theFolk.employedAt);

        if (dist <= 1)
        {
            theFolk.action = FolkAction.ATWORK;
            theFolk.stayPut = true;
            theFolk.statusText = "I'm a lumberjack, and I'm ok";
            theStage = Stage.ARRIVEDATMILL;
        }
        else
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    /** saplings dropping from the canopy as it decays, any kind */
    private void pickUpSaplings()
    {
        if (!theFolk.isSpawned())
        {
            return;
        }
        List list = jobWorld.getEntitiesWithinAABBExcludingEntity(theFolk.theEntity,
                theFolk.theEntity.boundingBox.expand(4D, 4D, 4D));
        for (Object o : list)
        {
            if (o instanceof EntityItem && !((EntityItem) o).isDead)
            {
                ItemStack is = ((EntityItem) o).getEntityItem();
                if (is != null && Block.getBlockFromItem(is.getItem()) instanceof BlockSapling)
                {
                    addToInventory(is);
                    ((EntityItem) o).setDead();
                }
            }
        }
    }

    /** one of the saplings carried, where the tree stood (oak if they're not around to have picked any up) */
    private void plantSapling(V3 at)
    {
        int x = at.x.intValue(), y = at.y.intValue(), z = at.z.intValue();
        if (!jobWorld.isAirBlock(x, y, z))
        {
            return;
        }
        for (int i = 0; i < theFolk.inventory.size(); i++)
        {
            ItemStack is = theFolk.inventory.get(i);
            Block b = is == null ? null : Block.getBlockFromItem(is.getItem());
            if (b instanceof BlockSapling)
            {
                if (b.canPlaceBlockAt(jobWorld, x, y, z))
                {
                    jobWorld.setBlock(x, y, z, b, is.getItemDamage(), 0x03);
                    if (--is.stackSize <= 0)
                    {
                        theFolk.inventory.remove(i);
                    }
                }
                return;
            }
        }
        if (!theFolk.isSpawned() && Blocks.sapling.canPlaceBlockAt(jobWorld, x, y, z))
        {
            jobWorld.setBlock(x, y, z, Blocks.sapling, 0, 0x03);
        }
    }
}
