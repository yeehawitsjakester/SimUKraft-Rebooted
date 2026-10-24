package ashjack.simukraftreloaded.network;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.common.Effects;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobBuilder;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/** server side of the control box and building constructor buttons */
final class BuildingActions
{
    private BuildingActions() {}

    private static Building buildingAt(NBTTagCompound data)
    {
        V3 box = ActionHandlers.getV3(data, "box");
        return box == null ? null : Building.getBuilding(box);
    }

    static void register()
    {
        // GuiBuildingConstructor "Build it!": the builders at this constructor box start on the chosen blueprint
        ServerActions.register("buildIt", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                Building chosen = Building.getFromAllBuildings(data.getString("name"), data.getString("type"));
                if (box == null || chosen == null)
                {
                    ActionHandlers.tell(player, "Couldn't find that building - try choosing it again");
                    return;
                }
                chosen.buildDirection = data.getString("dir");
                chosen.conBoxLocation = box.clone();
                SimukraftReloaded.serverBuildings.remove(chosen);
                SimukraftReloaded.serverBuildings.add(chosen);
                chosen.saveThisBuilding();

                ArrayList<String> builders = new ArrayList<String>();
                for (FolkData worker : SimukraftReloaded.serverFolks)
                {
                    if (box.isSameCoordsAs(worker.employedAt, true, true))
                    {
                        worker.theBuilding = chosen;
                        builders.add(worker.name);
                        if (worker.vocation == Vocation.BUILDER && worker.theirJob instanceof JobBuilder)
                        {
                            JobBuilder job = (JobBuilder) worker.theirJob;
                            if (job.theStage == JobBuilder.Stage.IDLE)
                            {
                                job.theStage = JobBuilder.Stage.WORKERASSIGNED;
                            }
                        }
                        worker.saveThisFolk();
                    }
                }
                ActionHandlers.tell(player, builders.isEmpty()
                        ? chosen.displayNameWithoutPK + " is chosen. Hire a builder here and they'll start on it."
                        : String.join(" and ", builders) + (builders.size() == 1 ? " is" : " are") + " going to build the " + chosen.displayNameWithoutPK + ".");
            }
        });

        // "Fix House": re-register a house whose building record was lost
        ServerActions.register("fixHouse", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                if (box == null || Building.getBuilding(box) != null)
                {
                    return;
                }
                Building b = new Building("Repaired House", "residential", box, box.clone(), true);
                b.capacity = -1;
                b.author = "Satscape";
                b.displayNameWithoutPK = b.displayName;
                SimukraftReloaded.serverBuildings.add(b);
                b.saveThisBuilding();
            }
        });

        // "Set Lumber area": the lumberjack starts looking for trees at the first marker
        ServerActions.register("setLumberArea", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = buildingAt(data);
                if (b == null || BlockMarker.serverMarkers.isEmpty())
                {
                    return;
                }
                b.lumbermillMarker = BlockMarker.serverMarkers.get(0).toV3();
                b.saveThisBuilding();
            }
        });

        ServerActions.register("rotateStairs", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = buildingAt(data);
                if (b != null)
                {
                    rotateStairs(b);
                }
            }
        });

        // "Demolish!": staff are fired, the blocks are removed and come back as falling items
        ServerActions.register("demolish", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = buildingAt(data);
                if (b == null)
                {
                    return;
                }
                World world = MinecraftServer.getServer().worldServerForDimension(b.primaryXYZ.theDimension);
                for (V3 blockLoc : new ArrayList<V3>(b.blockLocations))
                {
                    int x = blockLoc.x.intValue(), y = blockLoc.y.intValue(), z = blockLoc.z.intValue();
                    Block block = world.getBlock(x, y, z);
                    if (block.getMaterial() == Material.air)
                    {
                        continue;
                    }
                    V3 debris = new V3((double) x, (double) y, (double) z, b.primaryXYZ.theDimension);
                    debris.blockID = Block.getIdFromBlock(block);
                    debris.meta = world.getBlockMetadata(x, y, z);
                    SimukraftReloaded.demolishBlocks.add(debris);
                    world.setBlockToAir(x, y, z);
                    if (SimukraftReloaded.demolishBlocks.size() % 8 == 0)
                    {
                        Effects.particles(world, "explode", x + 0.5, y + 0.5, z + 0.5, 1, 0.3, 0.05);
                    }
                }
                Effects.sound(world, b.primaryXYZ.x, b.primaryXYZ.y, b.primaryXYZ.z, "random.explode");
                // removing the control box above already removed the building; this covers buildings without one
                Building.removeBuilding(b, "demolished");
            }
        });
    }

    // Stairs, torches, redstone torches, beds, pistons, signs, ladders, buttons, fence gates
    static void rotateStairs(Building theBuilding)
    {
        World theWorld = MinecraftServer.getServer().worldServerForDimension(theBuilding.primaryXYZ.theDimension);
        theWorld.playSoundEffect(theBuilding.primaryXYZ.x,theBuilding.primaryXYZ.y,theBuilding.primaryXYZ.z, "ashjacksimukraftreloaded:computer", 1f, 2f);

        for (V3 blockLoc : theBuilding.blockLocations)
        {
            Block blk = theWorld.getBlock(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue());
            int meta = theWorld.getBlockMetadata(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue());

            if (blk == Blocks.acacia_stairs
            		|| blk == Blocks.birch_stairs
            		||blk == Blocks.dark_oak_stairs
            		||blk == Blocks.spruce_stairs
            		||blk == Blocks.stone_stairs
            		||blk == Blocks.oak_stairs
            		||blk == Blocks.brick_stairs
            		||blk == Blocks.stone_brick_stairs
            		||blk == Blocks.nether_brick_stairs
            		||blk == Blocks.sandstone_stairs
            		||blk == Blocks.stone_stairs
            		||blk == Blocks.jungle_stairs
            		||blk == Blocks.quartz_stairs)
                        //static method of BlockStairs
            {
                int newmeta = meta; // 0 > 2 > 1 > 3 > 0

                if (newmeta == 0)
                {
                    newmeta = 2;
                }
                else if (newmeta == 1)
                {
                    newmeta = 3;
                }
                else if (newmeta == 2)
                {
                    newmeta = 1;
                }
                else if (newmeta == 3)
                {
                    newmeta = 0;
                }

                theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),
                                                    newmeta, 0x03);
            }//end of stairs
            //// torches and redstone torches
            else if (blk==Blocks.torch || blk==Blocks.redstone_torch || blk==Blocks.redstone_torch) {
            	int newmeta = meta;
            	if (newmeta==1) {
            		newmeta=3;
            	} else if(newmeta==3) {
            		newmeta=2;
            	} else if (newmeta==2) {
            		newmeta=4;
            	} else if (newmeta==4) {
            		newmeta=1;
            	}
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x03);
            
            // beds
            } else if(blk==Blocks.bed) {
            	int newmeta = meta;
            	newmeta++;
            	if (newmeta==4) { newmeta=0; }
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x02);

            //pistons	
            } else if(blk==Blocks.piston || blk==Blocks.piston_extension
            	|| blk==Blocks.piston_head) {
            	int newmeta = meta;
            	if (newmeta==2) { newmeta=5; }
            	else if(newmeta==5) { newmeta=3; }
            	else if(newmeta==3) { newmeta=4; }
            	else if(newmeta==4) { newmeta=2; }
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x03);

            //signs in floor or attached
            } else if(blk==Blocks.wall_sign) {
            	int newmeta = meta;
            	if (newmeta==0) {newmeta=4;}
            	else if(newmeta==4) {newmeta=8;}
            	else if(newmeta==8) {newmeta=12;}
            	else if(newmeta==12) {newmeta=0;}
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x02);
            
            //signs on wall, ladders
            } else if(blk==Blocks.wall_sign || blk==Blocks.ladder) {
            	int newmeta = meta;
            	if (newmeta==2) {newmeta=5;}
            	else if(newmeta==5) {newmeta=3;}
            	else if(newmeta==3) {newmeta=4;}
            	else if(newmeta==4) {newmeta=2;}
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x03);
            
            //buttons
            } else if(blk==Blocks.stone_button || blk==Blocks.wooden_button) {
            	int newmeta = meta;
            	if (newmeta==1) {newmeta=3;}
            	else if(newmeta==3) {newmeta=2;}
            	else if(newmeta==2) {newmeta=4;}
            	else if(newmeta==4) {newmeta=1;}
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x03);
            
            // fence gates
            } else if(blk==Blocks.fence_gate) {
            	int newmeta = meta;
            	newmeta++;
            	if(newmeta>3) {newmeta=0;}
            	theWorld.setBlockMetadataWithNotify(blockLoc.x.intValue(), blockLoc.y.intValue(), blockLoc.z.intValue(),newmeta, 0x03);
            
            }
        }
    }
}
