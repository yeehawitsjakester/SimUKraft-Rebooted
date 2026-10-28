package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.military.TownWall;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Builds the town wall (see TownWall) from a gatehouse, a column at a time: takes stone bricks from the chests by the
 * gatehouse's control box (a warehouse keeper keeps them stocked - a masonry makes them), walks to the next stretch of
 * wall and lays it, then the next. When the border has moved, they take the old wall down first and bring its bricks
 * back.
 */
public class JobWallBuilder extends Job implements Serializable
{
    private static final long serialVersionUID = 1L;

    public Vocation vocation = null;
    public FolkData theFolk = null;

    private transient TownWall.Task task;
    private transient long lastRun, taskSince, idleUntil, nextBlock;
    private transient int laid;
    private transient boolean fetching, returning;

    private static final long RUN_MS = 500, BLOCK_MS = 900, GIVE_UP_WALKING_MS = 30000;
    private static final int CARRY = 128;

    public JobWallBuilder() {}

    public JobWallBuilder(FolkData folk)
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
            TownWall.release(task, theFolk.name);
        }
        task = null;
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.action = FolkAction.ATWORK;
        theFolk.statusText = "Looking over the plans for the wall";
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
            theFolk.statusText = "The gatehouse needs to be inside a town";
            return;
        }

        // carrying a load of old wall back, or going for bricks
        if (returning || fetching)
        {
            atGatehouse(town);
            return;
        }
        if (task == null)
        {
            if (now < idleUntil)
            {
                return;
            }
            task = TownWall.next(town, jobWorld, theFolk.name);
            if (task == null)
            {
                ashjack.simukraftreloaded.common.ChunkKeeper.release("wall@" + theFolk.employedAt);
                int[] p = TownWall.progress(town);
                theFolk.statusText = "The wall's done (" + p[0] + " of " + p[1] + " lengths up; the rest are gates and gaps)";
                theFolk.isWorking = false;
                idleUntil = now + 30000;
                return;
            }
            taskSince = now;
            laid = 0;
            java.util.Set<net.minecraft.world.ChunkCoordIntPair> here = new java.util.HashSet<net.minecraft.world.ChunkCoordIntPair>();
            here.add(ashjack.simukraftreloaded.common.ChunkKeeper.chunkOf(task.x, task.z));
            ashjack.simukraftreloaded.common.ChunkKeeper.keep("wall@" + theFolk.employedAt, jobWorld, here);
            if (!task.takeDown && !creative() && bricks() < TownWall.HEIGHT + 1)
            {
                drop();
                fetching = true;
                theFolk.gotoXYZ(theFolk.employedAt, null);
                return;
            }
            V3 stand = standingSpot(town);
            theFolk.gotoXYZ(stand, GotoMethod.WALK);
        }
        work(town, now);
    }

    /** at the wall: walking there, then a block at a time */
    private void work(Town town, long now)
    {
        theFolk.updateLocationFromEntity();
        double dx = theFolk.location.x - task.x, dz = theFolk.location.z - task.z;
        boolean near = dx * dx + dz * dz <= 36;
        if (!near)
        {
            theFolk.isWorking = false;
            theFolk.statusText = task.takeDown ? "Off to take down the old wall" : "Off to build the wall";
            if (now - taskSince > GIVE_UP_WALKING_MS)
            {
                if (theFolk.isSpawned())
                {
                    V3 stand = standingSpot(town);
                    theFolk.theEntity.setPositionAndUpdate(stand.x + 0.5, stand.y, stand.z + 0.5); // round whatever's in the way
                }
                near = true;
            }
            else
            {
                if (theFolk.destination == null)
                {
                    theFolk.gotoXYZ(standingSpot(town), GotoMethod.WALK);
                }
                return;
            }
        }
        theFolk.destination = null;
        theFolk.stayPut = true;
        theFolk.isWorking = true;
        if (now < nextBlock)
        {
            return;
        }
        nextBlock = now + BLOCK_MS;

        if (task.takeDown)
        {
            for (int y = task.top; y > task.ground; y--)
            {
                if (jobWorld.getBlock(task.x, y, task.z) == TownWall.BLOCK)
                {
                    jobWorld.setBlockToAir(task.x, y, task.z);
                    add(new ItemStack(TownWall.BLOCK));
                    theFolk.statusText = "Taking down the old wall";
                    return; // one a go
                }
            }
            TownWall.takenDown(town, task, theFolk.name);
            task = null;
            theFolk.stayPut = false;
            if (bricks() >= CARRY * 2)
            {
                returning = true;
                theFolk.gotoXYZ(theFolk.employedAt, null);
            }
            return;
        }

        if (laid == 0 && !TownWall.prepare(town, jobWorld, task))
        {
            task = null; // something's built there: it's a gap
            theFolk.stayPut = false;
            return;
        }
        int y = task.ground + 1 + laid;
        if (y > task.top)
        {
            TownWall.built(town, task, theFolk.name);
            task = null;
            theFolk.stayPut = false;
            return;
        }
        if (!creative() && !takeBrick())
        {
            drop();
            fetching = true;
            theFolk.gotoXYZ(theFolk.employedAt, null);
            return;
        }
        jobWorld.setBlock(task.x, y, task.z, TownWall.BLOCK, 0, 3);
        jobWorld.playSoundEffect(task.x + 0.5, y + 0.5, task.z + 0.5, "dig.stone", 0.6f, 1f);
        laid++;
        Economy.pay(theFolk, 0.02f);
        theFolk.statusText = "Building the wall";
    }

    /** where to stand: a couple of blocks inside the wall, on the ground */
    private V3 standingSpot(Town town)
    {
        int cx = town.centre.x.intValue(), cz = town.centre.z.intValue();
        int x = task.x + Integer.signum(cx - task.x) * 2, z = task.z + Integer.signum(cz - task.z) * 2;
        int g = TownWall.ground(jobWorld, x, z);
        return new V3((double) x, (double) (g < 0 ? theFolk.employedAt.y.intValue() : g + 1), (double) z, theFolk.employedAt.theDimension);
    }

    /** at the gatehouse: old bricks put away, new ones taken */
    private void atGatehouse(Town town)
    {
        theFolk.updateLocationFromEntity();
        if (theFolk.location.getDistanceTo(theFolk.employedAt) > 3)
        {
            theFolk.statusText = returning ? "Bringing the old wall's bricks back" : "Going for more stone bricks";
            if (theFolk.destination == null)
            {
                theFolk.gotoXYZ(theFolk.employedAt, null);
            }
            return;
        }
        ArrayList<IInventory> chests = Workshop.chests(jobWorld, theFolk.employedAt);
        if (chests.isEmpty())
        {
            theFolk.statusText = "The gatehouse needs a chest by its control box";
            return;
        }
        for (Iterator<ItemStack> it = theFolk.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            if (is != null && is.getItem() == Item.getItemFromBlock(TownWall.BLOCK) && bricksOver(is))
            {
                ItemStack left = WarehouseStock.put(chests, is.copy());
                if (left == null)
                {
                    it.remove();
                }
                else
                {
                    is.stackSize = left.stackSize;
                }
            }
        }
        returning = false;
        if (fetching)
        {
            int want = CARRY - bricks();
            for (ItemStack is : WarehouseStock.take(chests, Item.getItemFromBlock(TownWall.BLOCK), Math.max(0, want)))
            {
                add(is);
            }
            if (bricks() < TownWall.HEIGHT + 1)
            {
                theFolk.statusText = "Waiting for stone bricks";
                theFolk.isWorking = false;
                return;
            }
            fetching = false;
        }
    }

    /** more than a load's worth: the rest goes in the chests */
    private boolean bricksOver(ItemStack is)
    {
        return bricks() > CARRY;
    }

    private boolean creative()
    {
        return GameMode.gameMode == GameMode.GAMEMODES.CREATIVE;
    }

    private int bricks()
    {
        int n = 0;
        for (ItemStack is : theFolk.inventory)
        {
            if (is != null && is.getItem() == Item.getItemFromBlock(TownWall.BLOCK))
            {
                n += is.stackSize;
            }
        }
        return n;
    }

    private boolean takeBrick()
    {
        for (Iterator<ItemStack> it = theFolk.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            if (is != null && is.getItem() == Item.getItemFromBlock(TownWall.BLOCK) && is.stackSize > 0)
            {
                if (--is.stackSize <= 0)
                {
                    it.remove();
                }
                return true;
            }
        }
        return false;
    }

    private void add(ItemStack add)
    {
        for (ItemStack is : theFolk.inventory)
        {
            if (is != null && is.isItemEqual(add) && is.stackSize + add.stackSize <= is.getMaxStackSize())
            {
                is.stackSize += add.stackSize;
                return;
            }
        }
        theFolk.inventory.add(add.copy());
    }

    /** the bricks the rest of the wall needs (less what they carry): a keeper brings them to the gatehouse */
    @Override
    public Map<Item, Integer> remainingNeeds()
    {
        Map<Item, Integer> out = new LinkedHashMap<Item, Integer>();
        Town town = theFolk == null || theFolk.employedAt == null ? null : Towns.at(theFolk.employedAt);
        if (town == null || creative())
        {
            return out;
        }
        // one builder speaks for the gatehouse, so it's asked for once
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.WALLBUILDER && theFolk.employedAt.isSameCoordsAs(f.employedAt, true, true))
            {
                if (f != theFolk)
                {
                    return out;
                }
                break;
            }
        }
        int n = Math.min(256, TownWall.bricksWanted(town)) - bricks();
        if (n > 0)
        {
            out.put(Item.getItemFromBlock(TownWall.BLOCK), n);
        }
        return out;
    }

    @Override
    public Item waitingFor()
    {
        return fetching ? Item.getItemFromBlock(TownWall.BLOCK) : null;
    }
}
