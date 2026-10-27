package ashjack.simukraftreloaded.common.jobs;

import ashjack.simukraftreloaded.core.town.Economy;
import net.minecraft.item.Item;
import java.util.LinkedHashMap;
import ashjack.simukraftreloaded.common.Effects;
import net.minecraft.block.material.Material;
import java.io.Serializable;
import java.util.ArrayList;

import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.building.MotelPlan;
import ashjack.simukraftreloaded.network.Placement;
import ashjack.simukraftreloaded.core.building.ApartmentConversion;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.entity.EntityConBox;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public class JobBuilder extends Job implements Serializable
{
    private static final long serialVersionUID = -1177665807904279141L;

    public Stage theStage;
    public FolkData theFolk = null;
    public Vocation vocation = null;

    public int runDelay = 1000;
    public long timeSinceLastRun = 0;

    private transient ArrayList<IInventory> constructorChests = new ArrayList<IInventory>();
    private transient Building theBuilding = null;
    private transient long lastBoxCheck = 0;
    private transient EntityConBox theConBox = null;
    private transient long lastNotifiedOfMaterials = 0;

    /**
     * used to delay the sound effect so it only fires every 2 seconds
     * regardless of build delay
     */
    private transient long soundLastPlayed = 0l;

    /** what they're stuck waiting for (any meta), for the warehouse; null while they have what they need */
    public transient ItemStack wanted = null;

    int l = 0, ftb = 0, ltr = 0; // 3d build loops
    int xo = 0, zo = 0, acount = 0;
    int cx, cy, cz, ex, ey, ez, bx = 0, by = 0, bz = 0;

    public JobBuilder()
    {
        // not used
    }

    public JobBuilder(FolkData folk)
    {
        theFolk = folk;

        if (theStage == null)
        {
            theStage = Stage.IDLE;
        }

        if (theFolk == null)
        {
            return;
        } // is null when first employing, this is for next day(s)

        if (theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }

        this.theBuilding = theFolk.theBuilding;
    }

    public void resetJob()
    {
        theStage = Stage.IDLE;
    }

    @Override
    public void onUpdate()
    {
        if (theFolk == null)
        {
            return;
        }

        super.onUpdate();

        //theFolk.levelBuilder=10;
        //SimukraftReloaded.states().credits=100000;

        if (!SimukraftReloaded.isDayTime())
        {
            theStage = Stage.IDLE;
        }

        super.onUpdateGoingToWork(theFolk);

        if (theStage == Stage.WAITINGFORRESOURCES)
        {
            runDelay = 3000;

            if (theBuilding != null)
            {
            }
        }

        if (theStage == Stage.INPROGRESS)
        {
            if (step == 1)
            {
                runDelay = (int)(2000 / theFolk.levelBuilder);
            }
        }

        if (System.currentTimeMillis() - timeSinceLastRun < runDelay)
        {
            return;
        }

        timeSinceLastRun = System.currentTimeMillis();

        if (theFolk.theirJob != null)
        {
            if (theFolk.vocation != Vocation.BUILDER)
            {
                theFolk.selfFire();
                return;
            }
        }

        theFolk.updateLocationFromEntity();
        int dist = theFolk.location.getDistanceTo(theFolk.employedAt);

        if (dist <= 3 && theStage == Stage.WORKERASSIGNED)
        {
            theFolk.action = FolkAction.ATWORK;
            theFolk.statusText = "Arrived at work";
            theStage = Stage.BLUEPRINT;
        }

        if (dist < 10 && theStage == Stage.WORKERASSIGNED && theFolk.destination == null)
        {
            theFolk.action = FolkAction.ATWORK;
            theFolk.statusText = "Arrived at work";
            theStage = Stage.BLUEPRINT;
        }

        // ////////////////IDLE
        if ((theStage == Stage.IDLE || theStage == Stage.WORKERASSIGNED)
                && SimukraftReloaded.isDayTime())
        {
            if (theFolk.action != FolkAction.ONWAYTOWORK)
            {
                theStage = Stage.WORKERASSIGNED;
            }
        }
        else if (theStage == Stage.WORKERASSIGNED)
        {
        }
        else if (theStage == Stage.BLUEPRINT)
        {
            stageBlueprint();
        }
        else if (theStage == Stage.WAITINGFORRESOURCES)
        {
            stageWaitingForResources();
        }
        else if (theStage == Stage.INPROGRESS)
        {
            stageInProgress();
        }
        else if (theStage == Stage.COMPLETE)
        {
            stageComplete();
        }
    }

    private void stageBlueprint()
    {
        Building saved = Building.getBuildingByConBox(theFolk.employedAt);
        if (saved != null && saved.awaitingAt != null)
        {
            theFolk.statusText = "Waiting for the go-ahead on the " + saved.displayNameWithoutPK + " (shift-right-click the constructor box)";
            return;
        }
        if (theFolk.theBuilding == null)
        {
            if (saved != null && !saved.buildingComplete)
            {
                theFolk.theBuilding = saved;
            }
        }
        else if (saved != null && saved != theFolk.theBuilding && saved.displayName.equals(theFolk.theBuilding.displayName))
        {
            // resumed after a restart with a fresh copy of the blueprint: keep where it was placed
            theFolk.theBuilding.offsetLtr = saved.offsetLtr;
            theFolk.theBuilding.offsetFtb = saved.offsetFtb;
            theFolk.theBuilding.offsetUp = saved.offsetUp;
            if (theFolk.theBuilding.conBoxLocation == null)
            {
                theFolk.theBuilding.conBoxLocation = saved.conBoxLocation;
            }
        }
        theBuilding = theFolk.theBuilding;

        if (theBuilding == null)
        {
            theFolk.statusText = "Please choose which building I should build";
        }
        else
        {
            theFolk.statusText = "Looking through blueprints...";
            /*
            if (theBuilding.structure[theBuilding.structure.length - 1] == null) {
            	theBuilding.loadStructure(true);
            }
             */
            theFolk.updateLocationFromEntity();
            double dist = theFolk.location.getDistanceTo(theFolk.employedAt);

            if (dist < 4)
            {
                theFolk.stayPut = true;
            }

            if (SimukraftReloadedConfig.configFolkTalking)
            {
                if (theFolk.gender == 0)
                {
                    Effects.sound(jobWorld, theFolk.location.x, theFolk.location.y, theFolk.location.z, "ashjacksimukraftreloaded:readym", 1f, 1f);
                }
                else
                {
                    Effects.sound(jobWorld, theFolk.location.x, theFolk.location.y, theFolk.location.z, "ashjacksimukraftreloaded:readyf", 1f, 1f);
                }
            }

            theStage = Stage.WAITINGFORRESOURCES;
            step = 1;

            // create the conBox entity
            if (this.theConBox == null)
            {

                World world = MinecraftServer.getServer()
                              .worldServerForDimension(theFolk.location.theDimension);
                this.theConBox = new EntityConBox(world);
                this.theConBox.theFolk = theFolk;
                //this.theConBox.theFolk.theBuilding.loadStructure(true);
                this.theConBox.setLocationAndAngles(theFolk.employedAt.x + 2, theFolk.employedAt.y, theFolk.employedAt.z, 0f, 0f);

                if (!world.isRemote)
                {
                    world.spawnEntityInWorld(this.theConBox);
                }
            }
        }
    }

    private void stageWaitingForResources()
    {
        theFolk.isWorking = false;

        if (step == 1)
        {
            theFolk.statusText = "Checking building resources...";
            constructorChests = inventoriesFindClosest(theFolk.employedAt, 5);

            if (constructorChests.size() == 0)
            {
                theFolk.statusText = "Please place at least one chest/storage block near to constructor block.";
            }
            else
            {
                try
                {
                    openCloseChest(constructorChests.get(0), 2500);
                }
                catch (Exception e)
                {
                	SimukraftReloaded.log.info("JobBuilder:JobBuilder's chest was null");
                }

                step = 2;
            }

            int dist = theFolk.location.getDistanceTo(theFolk.employedAt);

            if (dist < 5)
            {
                theFolk.stayPut = true;
            }
        }
        else if (step == 2)
        {
            theStage = Stage.INPROGRESS;
            step = 1;
        }
        else if (step == 3)     // this step triggers mid-build - just send them
        {
            // back in to keep checking
            if (theFolk.vocation == Vocation.BUILDER)
            {
                step = 2;
                theStage = Stage.INPROGRESS;

                if (theFolk.isSpawned())
                {
                    theFolk.updateLocationFromEntity();
                }

                int dist = theFolk.location.getDistanceTo(theFolk.employedAt);

                if (dist < 5)
                {
                    theFolk.stayPut = true;
                }
                else
                {
                    theFolk.gotoXYZ(theFolk.employedAt, null);
                }
            }
            else
            {
                theFolk.selfFire();
                return;
            }
        }
    }

    private void stageInProgress()
    {
        Block blockId = null;
        boolean alreadyPlaced = false;
        theFolk.updateLocationFromEntity();
        int dist = theFolk.location.getDistanceTo(theFolk.employedAt);

        if (dist > 5 && theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
            return;
        }

        // no constructor box, no building: it's been broken (or built over) - stop rather than carry on regardless
        if (jobWorld != null && System.currentTimeMillis() - lastBoxCheck > 5000)
        {
            lastBoxCheck = System.currentTimeMillis();
            int x = theFolk.employedAt.x.intValue(), y = theFolk.employedAt.y.intValue(), z = theFolk.employedAt.z.intValue();
            if (jobWorld.blockExists(x, y, z) && jobWorld.getBlock(x, y, z) != SimukraftReloadedBlocks.buildingConstructor)
            {
                SimukraftReloaded.sendChat(theFolk.name + " has stopped building the " + theBuilding.displayNameWithoutPK
                        + ": its constructor box is gone");
                theFolk.selfFire();
                return;
            }
        }

        if (step == 1)
        {
            // laid out from the box, moved however far it was moved when it was placed
            V3 origin = theBuilding.layoutBox(theFolk.employedAt);
            cx = origin.x.intValue();
            cy = origin.y.intValue();
            cz = origin.z.intValue();
            ex = cx;
            ey = cy;
            ez = cz;
            bx = ex;
            by = ey;
            bz = ez;

            if (theBuilding.buildDirection.contentEquals("-x"))
            {
                bx = cx + 1;
            }
            else if (theBuilding.buildDirection.contentEquals("+x"))
            {
                bx = cx - 1;
            }
            else if (theBuilding.buildDirection.contentEquals("-z"))
            {
                bz = cz + 1;
            }
            else if (theBuilding.buildDirection.contentEquals("+z"))
            {
                bz = cz - 1;
            }
            else
            {
                SimukraftReloaded.sendChat("Can't determine the direction to build in, please stand on one of the four sides of the constructor when you right-click it");
                theFolk.selfFire();
                return;
            }

            SimukraftReloaded.sendChat(theFolk.name + " has started building a "
                                  + theBuilding.displayNameWithoutPK);
            theFolk.statusText = "Building " + theBuilding.displayNameWithoutPK;

            if (theBuilding == null || theBuilding.layerCount == 0)
            {
                SimukraftReloaded.sendChat(theFolk.name
                                      + " has misplaced the blueprints, fire them and try someone else.");
                return;
            }

            theFolk.stayPut = true;

            if (theBuilding == null)
            {
                theFolk.selfFire();
                return;
            }

            l = 0;
            ftb = 0;
            ltr = 0;
            acount = 0;
            step = 2;
            theBuilding.blockLocations.clear();
            // a build resumed after a restart gets a fresh Building from its blueprint: keep where its box is, the
            // warehouse works out the building's footprint from it and takes leftovers from the chests by it
            if (theBuilding.conBoxLocation == null)
            {
                theBuilding.conBoxLocation = theFolk.employedAt.clone();
            }
            
        }
        else if (step == 2)     // ///////////////// STEP 2
        {
            do
            {
                theFolk.statusText = "Building "
                                     + theBuilding.displayNameWithoutPK;

                if (theBuilding.buildDirection.contentEquals("+z"))
                {
                    xo = ltr;
                    zo = -ftb;
                }
                else if (theBuilding.buildDirection.contentEquals("-z"))
                {
                    xo = -ltr;
                    zo = ftb;
                }
                else if (theBuilding.buildDirection.contentEquals("+x"))
                {
                    xo = -ftb;
                    zo = -ltr;
                }
                else if (theBuilding.buildDirection.contentEquals("-x"))
                {
                    xo = ftb;
                    zo = ltr;
                }

                if (theBuilding == null)
                {
                    theFolk.selfFire();
                    return;
                }

                Block bl = null;
                int st = 0;

                try
                {
                    bl = theBuilding.structure[acount];
                    st = theBuilding.structureSub[acount];
                }
                catch (Exception e)
                {
                	SimukraftReloaded.log.warning("JobBuilder: NULL block in building, using Air instead: " + theBuilding.structure[acount]);
                    bl = Blocks.air;
                    st = 0;
                }

                blockId = bl;
                int subtype = st;
                // an upgrade leaves what's already built (and whatever players put in it) alone
                boolean keep = bl == null && st == Building.KEEP;

                if (blockId == Blocks.grass)
                {
                    blockId = Blocks.dirt;
                }

                if (theBuilding.type.contentEquals("other") && acount == 0)
                {
                    blockId = SimukraftReloadedBlocks.controlBox;
                    subtype = 2; //control box other
                }

                if (blockId == SimukraftReloadedBlocks.controlBox)
                {
                    try
                    {
                        theBuilding.primaryXYZ = new V3((double)(bx + xo), (double)(by + l),
                                                        (double)(bz + zo), theFolk.employedAt.theDimension);
                        Building.register(theBuilding);
                        theBuilding.saveThisBuilding();
                    }
                    catch (Exception e)
                    {
                    	SimukraftReloaded.log.info("JobBuilder:build is null");
                    }
                }

                if (blockId == SimukraftReloadedBlocks.livingBlock && "residential".equals(theBuilding.type))
                {
                    theBuilding.livingXYZ = new V3((double)(bx + xo), (double)(by + l),
                                                   (double)(bz + zo), theFolk.employedAt.theDimension);
                    blockId = null;
                    subtype = 0;
                } else if (blockId==SimukraftReloadedBlocks.specialBlock && theBuilding.type != "residential") {
                	V3 v3=new V3((double)(bx + xo), (double)(by + l),(double)(bz + zo), theFolk.employedAt.theDimension);
                	v3.meta=subtype;
                	theBuilding.blockSpecial.add(v3);
                	blockId = null;
                    subtype = 0;
                }

                Block currBlockId = null;
                int currBlockMeta = 0;

                try
                {
                    currBlockId = jobWorld.getBlock(bx + xo, by + l, bz + zo);
                    currBlockMeta = jobWorld.getBlockMetadata(bx + xo, by + l, bz + zo);
                    
                    if (blockId == currBlockId || (blockId==Blocks.dirt && currBlockId==Blocks.grass)
                    	|| (blockId==Blocks.grass && currBlockId==Blocks.dirt))
                    {
                        alreadyPlaced = true;
                    }
                    else
                    {
                        alreadyPlaced = false;
                    }
                }
                catch (Exception e)
                {
                    theFolk.selfFire();
                    return;
                }

                if (keep)
                {
                    alreadyPlaced = true;
                }
                // another building's box (constructor, control, room, mining, farming) is never cleared or built over
                if (!alreadyPlaced && currBlockId != null && Placement.isProtected(currBlockId))
                {
                    alreadyPlaced = true;
                    blockId = null;
                }

                String want = "???";
                ItemStack wantIS = new ItemStack(blockId, 1, 0);

                if (wantIS != null && wantIS != null)
                {
                    try
                    {
                        want = wantIS.getDisplayName();
                        // once per block: a builder waiting for materials comes back to the same block every few seconds
                        V3 here = new V3(bx + xo, by + l, bz + zo, theFolk.location.theDimension);
                        int last = theBuilding.blockLocations.size() - 1;
                        if (blockId != null && (last < 0 || !theBuilding.blockLocations.get(last).isSameCoordsAs(here, true, true)))
                        {
                            theBuilding.blockLocations.add(here);
                        }
                        
                    }
                    catch (Exception e)
                    {
                        want = "?";
                        SimukraftReloaded.log.info("JobBuilder:wantItemStack nulled out, wantIS was null, blockID=" + blockId);
                    }
                }
                else
                {
                    want = "???";
                }

                if (!alreadyPlaced)   // air block it first to clear dirt
                {
                    // away
                    if (currBlockId != null && currBlockId.getMaterial() != Material.air)
                    {
                        V3 blockToRemove = new V3(bx + xo, by + l, bz + zo);
                        constructorChests = inventoriesFindClosest(theFolk.employedAt, 5);
                        mineBlockIntoChests(constructorChests, blockToRemove);
                        jobWorld.setBlock(bx + xo, by + l, bz + zo, Blocks.air, 0, 0x03);
                        theFolk.isWorking = true;
                    }
                }

                if (!alreadyPlaced)
                {
                    boolean gotBlock = false;
                    boolean requiredBlocks = isRequiredInNormal(blockId);

                    if (GameMode.gameMode == GameMode.GAMEMODES.NORMAL)
                    {
                        if (requiredBlocks)
                        {
                            constructorChests = inventoriesFindClosest(theFolk.employedAt, 5);
                            ItemStack got = inventoriesGet(constructorChests, new ItemStack(blockId, 1, 0), false,false);

                            if (got != null)
                            {
                                gotBlock = true;
                            }
                            else
                            {
                                gotBlock = false;
                            }
                        }
                        else
                        {
                            gotBlock = true;
                        }
                    }
                    else if (GameMode.gameMode == GameMode.GAMEMODES.CREATIVE)
                    {
                        gotBlock = true;
                    }
                    else if (GameMode.gameMode == GameMode.GAMEMODES.HARDCORE)
                    {
                        if (blockId != null)
                        {
                            // provided blocks in hardcore mode     68=sign
                            if (blockId == Blocks.grass || blockId == Blocks.water ||
                                    blockId == Blocks.water || blockId == Blocks.lava
                                    || blockId == Blocks.lava || blockId == Blocks.wall_sign
                                    || blockId == Blocks.cake || blockId == Blocks.stone_slab
                                    || blockId == Blocks.wooden_slab || blockId == Blocks.double_wooden_slab
                                    || blockId == Blocks.double_stone_slab || blockId == Blocks.farmland
                                    || blockId == Blocks.wooden_door || blockId ==Blocks.iron_door
                                    || blockId == Blocks.bed)
                            	//// TODO: WHEN I RE-WRITE - problem here is it needs to translate blocks to items
                            {
                                gotBlock = true;
                            }
                            else
                            {
                                constructorChests = inventoriesFindClosest(theFolk.employedAt, 5);
                                ItemStack got = inventoriesGet(constructorChests, new ItemStack(blockId, 1, 0), false,false);

                                if (got != null)
                                {
                                    gotBlock = true;
                                }
                                else
                                {
                                    gotBlock = false;
                                }

                                if (blockId == SimukraftReloadedBlocks.controlBox || blockId == SimukraftReloadedBlocks.roomBox)
                                {
                                    gotBlock = true;
                                }
                                
                                if (blockId == SimukraftReloadedBlocks.livingBlock)
                                {
                                    gotBlock = true;
                                }
                            }
                        }
                        else
                        {
                            gotBlock = true;
                        }
                    }

                    wanted = gotBlock ? null : new ItemStack(blockId, 1, 0);

                    if (!gotBlock)
                    {
                        theStage = Stage.WAITINGFORRESOURCES;

                        if (want.toLowerCase().contentEquals("oak wood planks"))
                        {
                            want = "Planks";
                        }

                        if (want.toLowerCase().contentEquals("oak wood"))
                        {
                            want = "Logs";
                        }

                        theFolk.statusText = "Waiting for " + want;

                        if (System.currentTimeMillis() - lastNotifiedOfMaterials > (SimukraftReloadedConfig.configMaterialReminderInterval * 60 * 1000))
                        {
                            lastNotifiedOfMaterials = System.currentTimeMillis();
                            SimukraftReloaded.sendChat(theFolk.name + " (who's building a " + theFolk.theBuilding.displayNameWithoutPK
                                                  + ") needs more " + want);
                        }

                        step = 3;
                        return;
                    }

                    try
                    {
                    	
                        if (!alreadyPlaced)
                        {
                            try
                            {
                                
                                if(blockId == SimukraftReloadedBlocks.livingBlock)
                                {
                                	alreadyPlaced = true;
                                }

                                // bank control boxes
                                else if (blockId == SimukraftReloadedBlocks.controlBox
                                        && theBuilding.displayNameWithoutPK.toLowerCase().contentEquals("sim-u-bank"))
                                {
                                    subtype = 1;
                                }

                                //########### PLACE THE BLOCK
                                if(!alreadyPlaced)
                                {
                                	theFolk.stayPut = true;
                                	// a door's top half first: the bottom half on its own breaks into an item the
                                	// moment the next block goes down beside it. The top half's own turn, a layer
                                	// up, then finds it already there
                                	if (blockId instanceof BlockDoor && (subtype & 8) == 0)
                                	{
                                		int up = acount + theBuilding.ftbCount * theBuilding.ltrCount;
                                		int topMeta = up < theBuilding.structure.length && theBuilding.structure[up] == blockId
                                				? theBuilding.structureSub[up] : 8;
                                		jobWorld.setBlock(bx + xo, by + l + 1, bz + zo, blockId, topMeta | 8, 0x02);
                                	}
                                	jobWorld.setBlock(bx + xo, by + l,
                                			bz + zo, blockId, subtype, 0x03);
                                	jobWorld.markBlockForUpdate(bx + xo, by + l, bz + zo);
                                	if (blockId == SimukraftReloadedBlocks.roomBox)
                                	{
                                		makeRoom(bx + xo, by + l, bz + zo, subtype == 1);
                                	}
                                }

                                

                                int b4 = (int)Math.floor(theFolk.levelBuilder);

                                //theFolk.levelBuilder=10.0f;
                                if (theFolk.levelBuilder < 10.0f)
                                {
                                    theFolk.levelBuilder += (0.001 / b4);
                                }

                                int aft = (int)Math.floor(theFolk.levelBuilder);

                                if (b4 != aft)
                                {
                                    SimukraftReloaded.sendChat(theFolk.name + " has just levelled up to Builder Level " + aft);
                                }

                                // PLAY SOUND EFFECT every 2 seconds
                                /*if (System.currentTimeMillis()
                                        - soundLastPlayed >= 2000)
                                {
                                    Effects.sound(jobWorld, bx + xo, by + l, bz + zo, "ashjacksimukraftreloaded:construction", 1f, 1f);
                                    soundLastPlayed = System
                                                      .currentTimeMillis();
                                }*/

                                Effects.particles(jobWorld, "explode", bx + xo + 0.5, by + l + 0.5, bz + zo + 0.5, 3, 0.3, 0.05);

                                if (blockId != null && GameMode.gameMode != GameMode.GAMEMODES.CREATIVE && blockId != SimukraftReloadedBlocks.livingBlock)
                                {
                                	Economy.pay(theFolk, (0.02f));
                                }
                            }
                            catch (Exception e)
                            {
                            	SimukraftReloaded.log.warning("JobBuilder: Possible non-existant block (from other mod) ID="
                                     + blockId);

                                try
                                {
                                    jobWorld.setBlock(bx + xo, by + l, bz + zo, blockId, 0, 0x03);
                                }
                                catch (Exception e2)
                                {
                                    e2.printStackTrace();
                                }
                            } // this exceptions when another mod's block is placed down
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                //remove from requirements
                /*
                if (!want.contentEquals("???")) {
                	try {
                		Iterator it = theFolk.theBuilding.requirements.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry pairs = (Map.Entry)it.next();
                            ItemStack is=(ItemStack) pairs.getKey();
                            if (is.itemID >0) {
                            	if (is.itemID==wantIS.itemID) {
                            		int left=theFolk.theBuilding.requirements.get(wantIS);
                					left--;
                					if (left>0) {
                						theFolk.theBuilding.requirements.put(wantIS, left);
                					} else {
                						theFolk.theBuilding.requirements.remove(wantIS);
                					}
                            	}
                            }
                        }
                	} catch(Exception e) {
                		SimukraftReloaded.log.info("JobBuilder:Builder requirement was null - no biggie"); }
                }
                */
                acount++;
                ltr++;

                if (ltr == theBuilding.ltrCount)
                {
                    ltr = 0;
                    ftb++;

                    if (ftb == theBuilding.ftbCount)
                    {
                        ftb = 0;
                        l++;

                        if (l == theBuilding.layerCount)
                        {
                            theStage = Stage.COMPLETE;
                            stageComplete();
                            return;
                        }
                    }
                }

                if (blockId == null || alreadyPlaced)
                {
                    runDelay = 0;
                }
                else
                {
                    if (GameMode.gameMode == GameMode.GAMEMODES.CREATIVE)
                    {
                        runDelay = 0;
                    }
                    else
                    {
                        runDelay = (int)(2000 / theFolk.levelBuilder);
                    }
                }

                if (theFolk.theEntity != null)
                {
                    theFolk.theEntity.swingItem();
                }
            }
            while (blockId == null || alreadyPlaced);
        } // end step 2
    }

    /**
     * a room box in a blueprint: a player placing one sets up its room, a builder placing one has to. Hotel rooms (a
     * motel's) are named after the building, numbered in the order they're built.
     */
    private void makeRoom(int x, int y, int z, boolean hotel)
    {
        Building room = Building.createRoom(jobWorld, x, y, z);
        if (hotel && !room.isHotel())
        {
            room.roomMode = Building.ROOM_HOTEL;
            int n = 0;
            for (Building b : SimukraftReloaded.buildings())
            {
                if (b.isHotel() && b.displayName.startsWith("Motel room"))
                {
                    n++;
                }
            }
            room.displayName = "Motel room " + (n + 1);
            room.displayNameWithoutPK = room.displayName;
            room.updateRoomRent();
            room.saveThisBuilding();
        }
    }

    private void stageComplete()
    {
        theFolk.isWorking = false;

        if (theBuilding != null)
        {
            if (theBuilding.buildingComplete)
            {
         //       return;
            }

            if (theBuilding != null)
            {
                theBuilding.buildingComplete = true;
                MotelPlan motel = MotelPlan.parse(theBuilding.displayName);
                if (motel != null && motel.fromA >= 0)
                {
                    theBuilding.displayName = motel.finished().name(); // an upgrade, done: just the motel now
                }
                ApartmentConversion.Name flats = ApartmentConversion.parse(theBuilding.displayName);
                if (flats != null)
                {
                    if (flats.convert)
                    {
                        theBuilding.displayName = ApartmentConversion.name(flats.dir, flats.type, flats.original, false);
                    }
                    ApartmentConversion.settleFlats(theBuilding, jobWorld,
                            theBuilding.conBoxLocation != null ? theBuilding.conBoxLocation : theFolk.employedAt);
                }
                if (motel != null)
                {
                    // its room boxes picked where guests stand before the walls around them were up
                    for (Building room : new ArrayList<Building>(SimukraftReloaded.buildings()))
                    {
                        if (room.isRoom())
                        {
                            for (V3 placed : theBuilding.blockLocations)
                            {
                                if (placed.isSameCoordsAs(room.primaryXYZ, true, true))
                                {
                                    room.refreshLivingSpot(jobWorld);
                                    break;
                                }
                            }
                        }
                    }
                }
                SimukraftReloaded.sendChat(theFolk.name
                                      + " has completed building a "
                                      + theBuilding.displayNameWithoutPK);
                Effects.soundToAllPlayers("ashjacksimukraftreloaded:cash");
                Building.register(theBuilding);
                theBuilding.saveThisBuilding();
                theFolk.theBuilding=null;
            }
            else
            {
            	SimukraftReloaded
                .sendChat("Error: could not set the building that "
                          + theFolk.name
                          + " was building "
                          + "to 'complete', try rebuilding right away (no cost) to try again");
            }
        }

        if (theFolk.theEntity != null)
        {
            theFolk.theEntity.setSneaking(false);
        }

        theFolk.stayPut = false;
        theFolk.selfFire();
        theStage = Stage.IDLE;
        //bodgy fix to make sure buildings are complete - probably no longer needed, original bug caused by V3 class bug
        boolean activeBuilders = false;

        for (int f = 0; f < SimukraftReloaded.folks().size(); f++)
        {
            FolkData fd = SimukraftReloaded.folks().get(f);

            if (fd.vocation == Vocation.BUILDER)
            {
                activeBuilders = true;
            }
        }

        if (!activeBuilders)
        {
            for (int b = 0; b < SimukraftReloaded.buildings().size(); b++)
            {
                Building building = SimukraftReloaded.buildings().get(b);
                building.buildingComplete = true;
            }
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
            theFolk.statusText = "Arrived at the building site";
            theStage = Stage.BLUEPRINT;
        }
        else
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    /** the blocks a builder has to be given in Normal mode; everything else is free */
    public static boolean isRequiredInNormal(Block b)
    {
        return b == Blocks.planks || b == Blocks.cobblestone || b == Blocks.glass || b == Blocks.wool
                || b == Blocks.brick_block || b == Blocks.dirt || b == Blocks.stonebrick || b == Blocks.fence
                || b == Blocks.stone || b == Blocks.log;
    }

    @Override
    public Item waitingFor()
    {
        return wanted == null ? null : wanted.getItem();
    }

    /**
     * what this builder still has to be given, by item (any meta: they take any planks, wool...), counted over the
     * rest of the blueprint. Blocks already standing in the way get counted too, so it can run a little high.
     */
    @Override
    public LinkedHashMap<Item, Integer> remainingNeeds()
    {
        LinkedHashMap<Item, Integer> needs = new LinkedHashMap<Item, Integer>();
        Building b = theBuilding;
        if (b == null || b.structure == null || theStage == Stage.COMPLETE || theStage == Stage.IDLE
                || GameMode.gameMode == GameMode.GAMEMODES.CREATIVE)
        {
            return needs;
        }
        Block onlyThis = GameMode.gameMode == GameMode.GAMEMODES.HARDCORE && wanted != null
                ? Block.getBlockFromItem(wanted.getItem()) : null;
        if (GameMode.gameMode == GameMode.GAMEMODES.HARDCORE && onlyThis == null)
        {
            return needs; // hardcore needs almost everything; only chase what they're actually stuck on
        }
        for (int i = Math.max(0, acount); i < b.structure.length; i++)
        {
            Block block = b.structure[i];
            if (block == null || (onlyThis != null ? block != onlyThis : !isRequiredInNormal(block)))
            {
                continue;
            }
            Item item = Item.getItemFromBlock(block);
            if (item != null)
            {
                Integer n = needs.get(item);
                needs.put(item, n == null ? 1 : n + 1);
            }
        }
        return needs;
    }

    public enum Stage
    {
        IDLE, WORKERASSIGNED, BLUEPRINT, WAITINGFORRESOURCES, INPROGRESS, COMPLETE;

        @Override
        public String toString()
        {
            String ret = "";

            if (this == IDLE)
            {
                ret = "Idle";
            }
            else if (this == WORKERASSIGNED)
            {
                ret = "Builder has been hired and on their way";
            }
            else if (this == BLUEPRINT)
            {
                ret = "Builder is looking though blueprints";
            }
            else if (this == WAITINGFORRESOURCES)
            {
                ret = "Builder is checking the resources for the building";
            }
            else if (this == INPROGRESS)
            {
                ret = "Builder is busy building";
            }
            else if (this == COMPLETE)
            {
                ret = "Building work is complete";
            }

            return ret;
        }
    }
}
