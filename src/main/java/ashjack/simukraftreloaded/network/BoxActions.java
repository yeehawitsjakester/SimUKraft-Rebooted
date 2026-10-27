package ashjack.simukraftreloaded.network;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.blocks.functionality.FarmingBox;
import ashjack.simukraftreloaded.blocks.functionality.FarmingBox.FarmType;
import ashjack.simukraftreloaded.blocks.functionality.MiningBox;
import ashjack.simukraftreloaded.common.CourierTask;
import ashjack.simukraftreloaded.common.Effects;
import ashjack.simukraftreloaded.common.jobs.Job;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobTerraformer;
import ashjack.simukraftreloaded.common.jobs.JobTerraformer.TerraformerType;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/** server side of the farming box, mining box, terraform, courier, marker and beam-to screens */
final class BoxActions
{
    private BoxActions() {}

    static void register()
    {
        ServerActions.register("farmSettings", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                FarmingBox box = FarmingBox.getFarmingBlockByBoxXYZ(ActionHandlers.getV3(data, "box"));
                if (box == null)
                {
                    return;
                }
                try
                {
                    box.farmType = FarmType.valueOf(data.getString("type"));
                }
                catch (IllegalArgumentException e)
                {
                    return;
                }
                if (box.level == 0)
                {
                    box.level = 1;
                }
                FarmingBox.saveFarmingBoxes();
            }
        });

        // "Upgrade to Level N": costs (length x width) / 15 credits, at least a 4x4 farm
        ServerActions.register("upgradeFarm", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                FarmingBox box = FarmingBox.getFarmingBlockByBoxXYZ(ActionHandlers.getV3(data, "box"));
                if (box == null || SimukraftReloaded.farmToUpgrade != null)
                {
                    return;
                }
                float cost = (box.getSizeLength() * box.getSizeWidth()) / 15f;
                boolean creative = GameMode.gameMode == GameMode.GAMEMODES.CREATIVE;
                if (!creative && cost > SimukraftReloaded.serverStates.credits)
                {
                    ActionHandlers.tell(player, "You don't have enough Sim-U-credits to upgrade this farm");
                    return;
                }
                if (box.getSizeLength() < 4 || box.getSizeWidth() < 4)
                {
                    ActionHandlers.tell(player, "This farm is too small to upgrade");
                    return;
                }
                if (!creative)
                {
                    SimukraftReloaded.serverStates.credits -= cost;
                }
                SimukraftReloaded.farmToUpgradeCounter = 0;
                SimukraftReloaded.farmToUpgrade = box;
            }
        });

        ServerActions.register("mineSettings", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                MiningBox box = MiningBox.getMiningBlockByBoxXYZ(ActionHandlers.getV3(data, "box"));
                if (box == null)
                {
                    return;
                }
                box.discards = Math.max(0, Math.min(4, data.getInteger("discards")));
                box.addGlassCover = data.getBoolean("glass");
                box.size = Math.max(1, Math.min(64, data.getInteger("size")));
                MiningBox.saveMiningBoxes();
            }
        });

        ServerActions.register("terraform", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                FolkData folk = FolkData.getFolkByName(data.getString("name"));
                if (folk == null || folk.vocation != Vocation.TERRAFORMER)
                {
                    ActionHandlers.tell(player, "You must hire a terraformer, not a builder");
                    return;
                }
                try
                {
                    folk.terraformerType = TerraformerType.valueOf(data.getString("type"));
                }
                catch (IllegalArgumentException e)
                {
                    return;
                }
                folk.terraformerRadius = Math.max(1, Math.min(60, data.getInteger("radius")));
                folk.saveThisFolk();
                if (folk.theirJob instanceof JobTerraformer)
                {
                    ((JobTerraformer) folk.theirJob).optionChosen();
                }
            }
        });

        // courier task: pick up at one courier point, drop off at another (or back at the depot)
        ServerActions.register("addCourierTask", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                FolkData folk = FolkData.getFolkByName(data.getString("folk"));
                V3 pickup = CourierTask.getCourierPoint(data.getString("pickup"));
                String dropName = data.getString("dropoff");
                V3 dropoff = "Depot".equals(dropName) ? ActionHandlers.getV3(data, "box") : CourierTask.getCourierPoint(dropName);
                if (folk == null || pickup == null || dropoff == null)
                {
                    return;
                }
                CourierTask task = new CourierTask();
                task.pickup.setVals(pickup);
                task.pickup.name = data.getString("pickup");
                task.dropoff.setVals(dropoff);
                task.dropoff.name = dropName;
                task.folkname = folk.name;
                task.name = "Task " + (SimukraftReloaded.serverCourierTasks.size() + 1);
                SimukraftReloaded.serverCourierTasks.add(task);
                CourierTask.saveCourierTasksAndPoints();
            }
        });

        // matched by contents, not by list index (the client's copy may be out of date)
        ServerActions.register("deleteCourierTask", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                String folk = data.getString("folk"), pickup = data.getString("pickup"), dropoff = data.getString("dropoff");
                for (int i = 0; i < SimukraftReloaded.serverCourierTasks.size(); i++)
                {
                    CourierTask t = SimukraftReloaded.serverCourierTasks.get(i);
                    if (t.folkname.equals(folk) && t.pickup.name.equals(pickup) && t.dropoff.name.equals(dropoff))
                    {
                        SimukraftReloaded.serverCourierTasks.remove(i);
                        CourierTask.saveCourierTasksAndPoints();
                        return;
                    }
                }
            }
        });

        // marker "Set new Courier/Beaming point": the first marker becomes a named point (needs a chest next to it)
        ServerActions.register("addCourierPoint", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                String name = data.getString("name").trim();
                if (name.isEmpty() || BlockMarker.serverMarkers.isEmpty())
                {
                    return;
                }
                V3 point = BlockMarker.serverMarkers.get(0).toV3();
                point.theDimension = player.dimension;
                ArrayList<IInventory> chests = Job.inventoriesFindClosest(point, 5);
                if (chests.isEmpty())
                {
                    ActionHandlers.tell(player, "Error: Place at least one chest near the marker.");
                    return;
                }
                for (V3 existing : SimukraftReloaded.serverCourierPoints)
                {
                    if (existing.name.contentEquals(name))
                    {
                        ActionHandlers.tell(player, "Error: The name must be unique, '" + name + "' is already used.");
                        return;
                    }
                }
                point.name = name;
                SimukraftReloaded.serverCourierPoints.add(point);
                CourierTask.saveCourierTasksAndPoints();
                ActionHandlers.tell(player, "Courier/Beaming point '" + name + "' has been added.");
            }
        });

        ServerActions.register("copyStructure", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 marker = ActionHandlers.getV3(data, "marker");
                if (marker != null)
                {
                    StructureCopier.copy(player, marker);
                }
            }
        });

        // "Beam me to..": teleport the player to a courier/beaming point (same dimension)
        ServerActions.register("beamPlayer", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 point = CourierTask.getCourierPoint(data.getString("point"));
                if (point == null)
                {
                    return;
                }
                if (point.theDimension != player.dimension)
                {
                    ActionHandlers.tell(player, "That point is in another dimension");
                    return;
                }
                Effects.sound(player.worldObj, player.posX, player.posY, player.posZ, "ashjacksimukraftreloaded:beamdown");
                Effects.particles(player.worldObj, "portal", player.posX, player.posY, player.posZ, 40, 0.5, 0.5);
                player.setPositionAndUpdate(point.x + 0.5, point.y + 1, point.z + 0.5);
                Effects.sound(player.worldObj, point.x, point.y + 1, point.z, "ashjacksimukraftreloaded:beamdowntwo");
                Effects.particles(player.worldObj, "portal", point.x + 0.5, point.y + 1, point.z + 0.5, 40, 0.5, 0.5);
                ActionHandlers.tell(player, "Beaming you to " + data.getString("point"));
            }
        });
    }
}
