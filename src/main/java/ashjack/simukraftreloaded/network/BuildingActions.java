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
import ashjack.simukraftreloaded.blocks.functionality.Marker;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.common.Effects;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobBuilder;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.building.MotelPlan;
import ashjack.simukraftreloaded.core.building.ApartmentConversion;
import ashjack.simukraftreloaded.core.building.ApartmentPlan;
import ashjack.simukraftreloaded.core.building.TowerPlan;
import ashjack.simukraftreloaded.core.building.Basement;
import ashjack.simukraftreloaded.core.building.Upgrades;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
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

    /** the builders work from the constructor box: put it back if it's gone; false if something's in its place */
    private static boolean putBackConstructor(EntityPlayerMP player, World world, V3 con)
    {
        int cx = con.x.intValue(), cy = con.y.intValue(), cz = con.z.intValue();
        Block there = world.getBlock(cx, cy, cz);
        if (there != SimukraftReloadedBlocks.buildingConstructor)
        {
            if (!there.isAir(world, cx, cy, cz) && !there.isReplaceable(world, cx, cy, cz))
            {
                ActionHandlers.tell(player, "Clear the spot where its constructor box was (" + con + ") first");
                return false;
            }
            world.setBlock(cx, cy, cz, SimukraftReloadedBlocks.buildingConstructor);
        }
        return true;
    }

    /**
     * a building chosen at this constructor box. A new one is first shown where it'll go (Placement), to be moved and
     * given the go-ahead; an addition to one already standing (a motel's rooms, flats) starts straight away.
     */
    private static void startBuilding(EntityPlayerMP player, V3 box, Building chosen, boolean inspect)
    {
        chosen.conBoxLocation = box.clone();
        // anything else waiting at this box is replaced
        for (Building other : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (other != chosen && box.toString().equals(other.awaitingAt))
            {
                SimukraftReloaded.serverBuildings.remove(other);
            }
        }
        SimukraftReloaded.serverBuildings.remove(chosen);
        SimukraftReloaded.serverBuildings.add(chosen);
        if (inspect)
        {
            chosen.awaitingAt = box.toString();
            Placement.check(chosen);
            ActionHandlers.tell(player, "Here's where the " + chosen.displayNameWithoutPK + " will go (the outline). "
                    + chosen.placementStatus + ". Move it from the constructor box if you need to, then shift-right-click"
                    + " the box (empty hand) or press Start building.");
            return;
        }
        assignBuilders(player, box, chosen);
    }

    /**
     * the builders at this constructor box start on this building; if there are none yet, the first one hired here
     * picks it up (JobBuilder.stageBlueprint looks for an unfinished building by its constructor box)
     */
    static void assignBuilders(EntityPlayerMP player, V3 box, Building chosen)
    {
        chosen.awaitingAt = null;
        chosen.saveThisBuilding();

        Town grew = Towns.takeIn(box.theDimension, WarehouseStock.footprint(chosen, chosen.layoutBox(box)), player.getCommandSenderName());
        if (grew != null)
        {
            ActionHandlers.tell(player, grew.name + "'s border moves out to " + (2 * grew.radius + 1) + " blocks across to take in the "
                    + chosen.displayNameWithoutPK);
        }

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
                ? chosen.displayNameWithoutPK + " is chosen. Hire a builder at the constructor box and they'll start on it."
                : String.join(" and ", builders) + (builders.size() == 1 ? " is" : " are") + " going to build the " + chosen.displayNameWithoutPK + ".");
    }

    static void register()
    {
        // GuiBuildingConstructor "Build it!": the builders at this constructor box start on the chosen blueprint
        ServerActions.register("buildIt", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                String name = data.getString("name");
                Building chosen = ApartmentConversion.isGenerated(name) ? Building.fromGenerated(name, data.getString("type"))
                        : Building.getFromAllBuildings(name, data.getString("type"));
                if (box == null || chosen == null)
                {
                    ActionHandlers.tell(player, "Couldn't find that building - try choosing it again");
                    return;
                }
                chosen.buildDirection = data.getString("dir");
                startBuilding(player, box, chosen, true);
            }
        });

        // GuiBuildingConstructor's motel page: a motel made to order, if there's room for it
        ServerActions.register("buildMotel", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                if (box == null)
                {
                    return;
                }
                MotelPlan plan = new MotelPlan(data.getString("shape").isEmpty() ? 'S' : data.getString("shape").charAt(0),
                        data.getInteger("floors"), data.getInteger("roomsA"), data.getInteger("roomsB"), data.getString("dir"));
                startBuilding(player, box, Building.fromMotelPlan(plan), true);
            }
        });

        // the apartment designer's "Build it!": apartments, condos or a tower made to order
        ServerActions.register("buildApartments", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                if (box == null)
                {
                    return;
                }
                ApartmentPlan plan = new ApartmentPlan(data.getString("kind").isEmpty() ? 'A' : data.getString("kind").charAt(0),
                        data.getInteger("across"), data.getInteger("rows"), data.getInteger("floors"), data.getString("dir"));
                startBuilding(player, box, Building.fromGenerated(plan.name(), "other"), true);
            }
        });

        // the constructor box's "Watch tower": a lookout tower, generated facing the right way
        ServerActions.register("buildTower", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                if (box != null)
                {
                    TowerPlan plan = new TowerPlan(data.getInteger("height"), data.getString("dir"));
                    startBuilding(player, box, Building.fromGenerated(plan.name(), "industrial"), true);
                }
            }
        });

        // a decorative building's control box "Convert to apartments": the builders at its constructor box add floors
        // (if it's hollow), a ladder and the flats' Room Boxes, leaving the rest of it as it is
        ServerActions.register("convertApartments", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = buildingAt(data);
                if (!ApartmentConversion.eligible(b) || !b.buildingComplete || b.buildDirection == null || b.buildDirection.isEmpty())
                {
                    ActionHandlers.tell(player, "Only a finished decorative building can be turned into flats");
                    return;
                }
                V3 con = WarehouseStock.conBoxOf(b);
                World world = con == null ? null : MinecraftServer.getServer().worldServerForDimension(con.theDimension);
                if (world == null)
                {
                    ActionHandlers.tell(player, "Couldn't work out where this building's constructor box was");
                    return;
                }
                Building flats = Building.fromGenerated(ApartmentConversion.name(b.buildDirection, b.type, b.displayName, true), b.type);
                if (ApartmentConversion.flats(flats) == 0)
                {
                    ActionHandlers.tell(player, "There's nowhere in it to live");
                    return;
                }
                if (!putBackConstructor(player, world, con))
                {
                    return;
                }
                flats.primaryXYZ = b.primaryXYZ.clone();
                flats.offsetLtr = b.offsetLtr;
                flats.offsetFtb = b.offsetFtb;
                flats.offsetUp = b.offsetUp;
                flats.livingXYZ = b.livingXYZ;
                flats.tennants = b.tennants;
                flats.buildingComplete = false;
                Building.register(flats); // in place of the building as it was, by its control box
                startBuilding(player, con, flats, false);
            }
        });

        // a motel's control box "Add rooms": checks there's room, then the builders at its constructor box add them
        ServerActions.register("upgradeMotel", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building motel = buildingAt(data);
                MotelPlan plan = motel == null ? null : MotelPlan.parse(motel.displayName);
                if (plan == null || plan.fromA >= 0 || !motel.buildingComplete)
                {
                    ActionHandlers.tell(player, "Finish building the motel before adding to it");
                    return;
                }
                int a = Math.min(MotelPlan.MAX_ROOMS, Math.max(plan.roomsA, data.getInteger("roomsA")));
                int b = plan.shape == 'S' ? 0 : Math.min(MotelPlan.MAX_ROOMS, Math.max(plan.roomsB, data.getInteger("roomsB")));
                if (a == plan.roomsA && b == plan.roomsB)
                {
                    ActionHandlers.tell(player, "It's as big as it gets (" + MotelPlan.MAX_ROOMS + " rooms a wing)");
                    return;
                }
                V3 con = WarehouseStock.conBoxOf(motel);
                World world = con == null ? null : MinecraftServer.getServer().worldServerForDimension(con.theDimension);
                if (world == null)
                {
                    ActionHandlers.tell(player, "Couldn't work out where this motel's constructor box was");
                    return;
                }
                V3 origin = motel.layoutBox(con);
                int cx = origin.x.intValue(), cy = origin.y.intValue(), cz = origin.z.intValue();
                MotelPlan bigger = plan.grownTo(a, b);
                String blocked = bigger.obstruction(world, cx, cy, cz);
                if (blocked != null)
                {
                    ActionHandlers.tell(player, "There isn't room to add those rooms: " + blocked);
                    return;
                }
                if (!putBackConstructor(player, world, con))
                {
                    return;
                }

                Building upgrade = Building.fromMotelPlan(bigger);
                upgrade.primaryXYZ = motel.primaryXYZ.clone(); // its control box stays where it is
                upgrade.offsetLtr = motel.offsetLtr;
                upgrade.offsetFtb = motel.offsetFtb;
                upgrade.offsetUp = motel.offsetUp;
                upgrade.livingXYZ = motel.livingXYZ;
                upgrade.tennants = motel.tennants;
                upgrade.buildingComplete = false;
                Building.register(upgrade); // in place of the finished motel, by its control box
                startBuilding(player, con, upgrade, false);
            }
        });

        // a warehouse's control box "Add a basement": the builders at its constructor box dig a storage basement under it
        ServerActions.register("addBasement", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building wh = buildingAt(data);
                if (!Upgrades.isWarehouse(wh) || !wh.buildingComplete || wh.buildDirection == null || wh.buildDirection.isEmpty()
                        || MotelPlan.isGenerated(wh.displayName) || ApartmentConversion.isGenerated(wh.displayName))
                {
                    ActionHandlers.tell(player, "Only a finished warehouse can have a basement dug");
                    return;
                }
                int n = Basement.count(wh);
                if (n >= Basement.MAX)
                {
                    ActionHandlers.tell(player, "It has as many basements as it can (" + Basement.MAX + ")");
                    return;
                }
                V3 con = WarehouseStock.conBoxOf(wh);
                World world = con == null ? null : MinecraftServer.getServer().worldServerForDimension(con.theDimension);
                if (world == null)
                {
                    ActionHandlers.tell(player, "Couldn't work out where this warehouse's constructor box was");
                    return;
                }
                Building deeper = Building.fromGenerated(Basement.name(n + 1, wh.buildDirection, wh.type, Basement.original(wh)), wh.type);
                if (deeper.structure == null)
                {
                    ActionHandlers.tell(player, "There's nowhere inside it, against a wall, for a ladder down");
                    return;
                }
                if (!putBackConstructor(player, world, con))
                {
                    return;
                }
                deeper.primaryXYZ = wh.primaryXYZ.clone();
                deeper.offsetLtr = wh.offsetLtr;
                deeper.offsetFtb = wh.offsetFtb;
                deeper.offsetUp = wh.offsetUp - Basement.HEIGHT; // laid out from a basement further down
                deeper.livingXYZ = wh.livingXYZ;
                deeper.tennants = wh.tennants;
                deeper.level = wh.level;
                deeper.buildingComplete = false;
                Building.register(deeper); // in place of the warehouse as it was, by its control box
                startBuilding(player, con, deeper, false);
                ActionHandlers.tell(player, "The builders at the constructor box will dig basement " + (n + 1)
                        + ": " + wh.ltrCount + "x" + wh.ftbCount + ", lined with chests, with a ladder down from the warehouse floor");
            }
        });

        ServerActions.register("upgradeChests", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building wh = buildingAt(data);
                if (!Upgrades.isWarehouse(wh) || wh.primaryXYZ == null)
                {
                    return;
                }
                ActionHandlers.tell(player, Upgrades.upgradeChests(wh,
                        MinecraftServer.getServer().worldServerForDimension(wh.primaryXYZ.theDimension)));
            }
        });

        ServerActions.register("upgradeMasonry", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building masonry = buildingAt(data);
                if (masonry != null)
                {
                    ActionHandlers.tell(player, Upgrades.upgradeMasonry(masonry));
                }
            }
        });

        ServerActions.register("upgradeSmithy", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building smithy = buildingAt(data);
                if (smithy != null)
                {
                    ActionHandlers.tell(player, Upgrades.upgradeSmithy(smithy));
                }
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
                // the marker nearest the mill (it was the first one in the world, wherever that was)
                V3 nearest = null;
                for (Marker m : BlockMarker.serverMarkers)
                {
                    V3 v = m.toV3();
                    if (v.theDimension == b.primaryXYZ.theDimension
                            && (nearest == null || v.getDistanceTo(b.primaryXYZ) < nearest.getDistanceTo(b.primaryXYZ)))
                    {
                        nearest = v;
                    }
                }
                if (nearest == null)
                {
                    return;
                }
                b.lumbermillMarker = nearest;
                b.saveThisBuilding();
                ActionHandlers.tell(player, "Lumberjacks here now look for wild trees within "
                        + SimukraftReloadedConfig.configLumberArea + " blocks of the marker at " + nearest.x.intValue() + ", "
                        + nearest.z.intValue());
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

        // "Demolish!": staff are fired, and every block of it, and what was in its chests, goes to the town's warehouses
        ServerActions.register("demolish", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                Building b = buildingAt(data);
                if (b == null || b.primaryXYZ == null)
                {
                    return;
                }
                World world = MinecraftServer.getServer().worldServerForDimension(b.primaryXYZ.theDimension);
                Effects.sound(world, b.primaryXYZ.x, b.primaryXYZ.y, b.primaryXYZ.z, "random.explode");
                ActionHandlers.tell(player, ashjack.simukraftreloaded.core.building.Demolition.demolish(b, world));
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
