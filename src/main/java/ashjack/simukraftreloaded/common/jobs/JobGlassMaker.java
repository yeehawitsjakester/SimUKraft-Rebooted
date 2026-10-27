package ashjack.simukraftreloaded.common.jobs;

import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.common.Effects;
import java.io.Serializable;
import java.util.ArrayList;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import java.util.Map;
import java.util.LinkedHashMap;

import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobMiner.Stage;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

public class JobGlassMaker extends Job implements Serializable
{
    private static final long serialVersionUID = 1177111222904279141L;

    public Vocation vocation = null;
    public FolkData theFolk = null;
    public Stage theStage;
    transient public int runDelay = 1000;
    transient public long timeSinceLastRun = 0;

    private transient V3 blockOfSand = null;
    private transient ArrayList<IInventory> factoryChests = new ArrayList<IInventory>();
    private transient TileEntityFurnace factoryFurnace = null;

    public JobGlassMaker() {}

    public JobGlassMaker(FolkData folk)
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
        IDLE, SCANFORSAND, GOTOSANDBLOCK, COLLECTSAND, RETURNSAND, USEFURNACE, CANTWORK;
    }

    public void resetJob()
    {
        theStage = Stage.IDLE;
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();

        if (!SimukraftReloaded.isDayTime())
        {
            theStage = Stage.IDLE;
        }

        super.onUpdateGoingToWork(theFolk);

        if (theFolk.action != FolkAction.ATWORK)
        {
            return;
        }
        runDelay = 4000;
        if (System.currentTimeMillis() - timeSinceLastRun < runDelay)
        {
            return;
        }
        timeSinceLastRun = System.currentTimeMillis();

        // sand and fuel come from the warehouse now (miners bring coal back to it); nobody digs a pit behind the
        // factory any more. The keeper also takes the glass away, to stock or to whichever builder needs it.
        theStage = Stage.USEFURNACE;
        stageUseFurnace();
    }

    /** what a warehouse keeper should bring: sand to keep the furnace going, and coal if there's no fuel to hand */
    @Override
    public Map<Item, Integer> remainingNeeds()
    {
        Map<Item, Integer> needs = new LinkedHashMap<Item, Integer>();
        if (theFolk == null || theFolk.employedAt == null || jobWorld == null)
        {
            return needs;
        }
        ArrayList<IInventory> chests = WarehouseStock.storageNear(jobWorld, theFolk.employedAt, 4);
        TileEntityFurnace furnace = findFurnace(theFolk.employedAt);
        Item sand = Item.getItemFromBlock(Blocks.sand);
        int haveSand = WarehouseStock.count(chests, sand) + slotCount(furnace, 0, sand);
        int haveFuel = WarehouseStock.count(chests, Items.coal) + slotCount(furnace, 1, Items.coal)
                + WarehouseStock.count(chests, Item.getItemFromBlock(Blocks.log)) + WarehouseStock.count(chests, Item.getItemFromBlock(Blocks.log2))
                + WarehouseStock.count(chests, Items.lava_bucket) * 12;
        if (haveSand < SAND_WANTED)
        {
            needs.put(sand, SAND_WANTED - haveSand);
        }
        if (haveFuel < FUEL_WANTED)
        {
            needs.put(Items.coal, FUEL_WANTED - haveFuel);
        }
        return needs;
    }

    @Override
    public Item waitingFor()
    {
        return outOfFuel ? Items.coal : outOfSand ? Item.getItemFromBlock(Blocks.sand) : null;
    }

    private static final int SAND_WANTED = 128, FUEL_WANTED = 24;
    private transient boolean outOfSand, outOfFuel;

    private static int slotCount(TileEntityFurnace furnace, int slot, Item item)
    {
        ItemStack is = furnace == null ? null : furnace.getStackInSlot(slot);
        return is != null && is.getItem() == item ? is.stackSize : 0;
    }

    ////  0 = sand (top)   1=fuel coal/wood/lava    2=output
    private void stageUseFurnace()
    {
        factoryFurnace = findFurnace(theFolk.employedAt);
        factoryChests = inventoriesFindClosest(theFolk.employedAt, 5);

        if (factoryFurnace == null)
        {
       
            SimukraftReloaded.sendChat(theFolk.name + ": Where's my furnace gone!");
            return;
        }

        if (step == 1)
        {
            theFolk.statusText = "Checking furnace fuel";
            ItemStack currentFuel = factoryFurnace.getStackInSlot(1);
            ItemStack gotFuel = null;

            if (currentFuel == null)
            {
                gotFuel = inventoriesGet(factoryChests, new ItemStack(Items.coal, 64), false,false, new ItemStack(Items.coal, 64));

                if (gotFuel == null)
                {
                    gotFuel = inventoriesGet(factoryChests, new ItemStack(Items.lava_bucket, 1), false,false, new ItemStack(Items.lava_bucket, 1));
                }

                if (gotFuel == null)
                {
                    gotFuel = inventoriesGet(factoryChests, new ItemStack(Item.getItemFromBlock(Blocks.log), 64), false,false,new ItemStack(Item.getItemFromBlock(Blocks.log), 64));
                }

                if (gotFuel == null)
                {
                    gotFuel = inventoriesGet(factoryChests, new ItemStack(Item.getItemFromBlock(Blocks.planks), 64), false,false,new ItemStack(Item.getItemFromBlock(Blocks.planks), 1));
                }

                if (gotFuel == null)
                {
                    outOfFuel = true;
                    theFolk.statusText = "Waiting for coal from the warehouse";
                    step = 2; // still take the glass out and top up the sand
                    return;
                }
                else
                {
                    outOfFuel = false;
                    //put the fuel in the bottom slot
                    factoryFurnace.setInventorySlotContents(1, gotFuel);
                    step = 2;
                    return;
                }
            }
            else
            {
                outOfFuel = false;
                step = 2;
            }
        }
        else if (step == 2)
        {
            theFolk.statusText = "Adding sand to the furnace";

            if (factoryFurnace != null)
            {
                ItemStack currentSand = factoryFurnace.getStackInSlot(0);
                ItemStack gotSand = null;

                if (currentSand == null)
                {
                    gotSand = inventoriesGet(factoryChests, new ItemStack(Item.getItemFromBlock(Blocks.sand), 64), false,false,new ItemStack(Blocks.sand, 64));

                    if (gotSand != null)
                    {
                        //place sand in top slot
                        factoryFurnace.setInventorySlotContents(0, gotSand);
                    }
                    outOfSand = gotSand == null;
                    if (outOfSand)
                    {
                        theFolk.statusText = "Waiting for sand from the warehouse";
                    }

                    step = 3;
                    return;
                }
                else
                {
                    gotSand = inventoriesGet(factoryChests, new ItemStack(Item.getItemFromBlock(Blocks.sand), 64 - currentSand.stackSize), false,false,new ItemStack(Blocks.sand, 64 - currentSand.stackSize));

                    if (gotSand != null)
                    {
                        // top up the sand
                        currentSand.stackSize += gotSand.stackSize;
                        factoryFurnace.setInventorySlotContents(0, currentSand);
                  
                    }

                    step = 3;
                    return;
                }
            }
        }
        else if (step == 3)
        {
            ItemStack currentGlass = factoryFurnace.getStackInSlot(2);

            if (currentGlass != null)
            {
                theFolk.statusText = "Putting glass into storage";
                boolean placedOk = inventoriesPut(factoryChests, currentGlass, true);

                SimukraftReloaded.states().credits -= (0.005 * currentGlass.stackSize);
                factoryFurnace.setInventorySlotContents(2, null);
            }
            else if (!outOfSand && !outOfFuel)
            {
                theFolk.statusText = "Making glass";
            }

            step = 1;
        }
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
            theFolk.statusText = "Arrived at the factory";
            theStage = Stage.USEFURNACE;
            step = 1;
        }
        else
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }
}
