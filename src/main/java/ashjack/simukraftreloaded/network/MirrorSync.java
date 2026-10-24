package ashjack.simukraftreloaded.network;

import ashjack.simukraftreloaded.common.jobs.JobBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import ashjack.simukraftreloaded.proxies.CommonProxy.Commodity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.blocks.functionality.FarmingBox;
import ashjack.simukraftreloaded.blocks.functionality.MiningBox;
import ashjack.simukraftreloaded.blocks.functionality.Marker;
import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.common.CourierTask;
import ashjack.simukraftreloaded.common.GameStates;
import ashjack.simukraftreloaded.common.Relationship;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import ashjack.simukraftreloaded.packetsNEW.toClient.MirrorSyncPacket;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Sends the server's Sim-U-Kraft data to clients for their GUIs. Everything goes through one object stream so
 * references survive: a relationship's folks are the same objects as the ones in the folk list.
 */
public final class MirrorSync
{
    private MirrorSync() {}

    public static class Bundle implements Serializable
    {
        private static final long serialVersionUID = 1L;
        ArrayList<FolkData> folks;
        ArrayList<Building> buildings;
        ArrayList<Relationship> relationships;
        ArrayList<MiningBox> miningBoxes;
        ArrayList<FarmingBox> farmingBoxes;
        ArrayList<CourierTask> courierTasks;
        ArrayList<V3> courierPoints;
        ArrayList<Marker> markers;
        GameStates states;
        boolean ranStartup;
        ArrayList<CommodityEntry> commodities;
    }

    /** a bank commodity without its ItemStack (which can't be serialised) */
    public static class CommodityEntry implements Serializable
    {
        private static final long serialVersionUID = 1L;
        String item;
        int damage;
        int quantity;
        float priceEach;
    }

    /** server thread: snapshot of everything, gzipped */
    public static byte[] buildPayload() throws IOException
    {
        for (FolkData f : SimukraftReloaded.serverFolks)
        {
            f.syncedJobStage = f.theirJob instanceof JobBuilder ? String.valueOf(((JobBuilder) f.theirJob).theStage) : "";
        }
        for (Building bld : SimukraftReloaded.serverBuildings)
        {
            packRequirements(bld);
        }
        for (FolkData f : SimukraftReloaded.serverFolks)
        {
            packRequirements(f.theBuilding);
        }
        Bundle b = new Bundle();
        b.folks = new ArrayList<FolkData>(SimukraftReloaded.serverFolks);
        b.buildings = new ArrayList<Building>(SimukraftReloaded.serverBuildings);
        b.relationships = new ArrayList<Relationship>(SimukraftReloaded.serverRelationships);
        b.miningBoxes = new ArrayList<MiningBox>(SimukraftReloaded.serverMiningBoxes);
        b.farmingBoxes = new ArrayList<FarmingBox>(SimukraftReloaded.serverFarmingBoxes);
        b.courierTasks = new ArrayList<CourierTask>(SimukraftReloaded.serverCourierTasks);
        b.courierPoints = new ArrayList<V3>(SimukraftReloaded.serverCourierPoints);
        b.markers = new ArrayList<Marker>(BlockMarker.serverMarkers);
        b.states = SimukraftReloaded.serverStates;
        b.ranStartup = ModSimukraft.proxy.ranStartup;
        if (SimukraftReloaded.theCommodities.isEmpty())
        {
            Commodity.refreshAvailableCommoditities();
        }
        b.commodities = new ArrayList<CommodityEntry>();
        for (Commodity c : SimukraftReloaded.theCommodities)
        {
            if (c.theItemStack == null)
            {
                continue;
            }
            CommodityEntry e = new CommodityEntry();
            e.item = Item.itemRegistry.getNameForObject(c.theItemStack.getItem());
            e.damage = c.theItemStack.getItemDamage();
            e.quantity = c.quantity;
            e.priceEach = c.priceEach;
            b.commodities.add(e);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(bytes));
        out.writeObject(b);
        out.close();
        return bytes.toByteArray();
    }

    public static void sendTo(EntityPlayerMP player)
    {
        try
        {
            PacketHandler.net.sendTo(new MirrorSyncPacket(buildPayload()), player);
        }
        catch (Exception e)
        {
            SimukraftReloaded.log.warning("MirrorSync: couldn't send data to " + player.getCommandSenderName() + ": " + e);
        }
    }

    public static void sendToAll()
    {
        if (MinecraftServer.getServer() == null || !ModSimukraft.proxy.ranStartup)
        {
            return;
        }
        byte[] payload;
        try
        {
            payload = buildPayload();
        }
        catch (Exception e)
        {
            SimukraftReloaded.log.warning("MirrorSync: couldn't build data: " + e);
            return;
        }
        PacketHandler.net.sendToAll(new MirrorSyncPacket(payload));
    }

    /** client thread: replace the mirror with what the server sent */
    public static void apply(byte[] payload)
    {
        try
        {
            ObjectInputStream in = new SafeObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(payload)));
            Bundle b = (Bundle) in.readObject();
            in.close();
            for (Building bld : b.buildings)
            {
                unpackRequirements(bld);
            }
            for (FolkData f : b.folks)
            {
                unpackRequirements(f.theBuilding);
            }
            ClientMirror.folks = b.folks;
            ClientMirror.buildings = b.buildings;
            ClientMirror.relationships = b.relationships;
            ClientMirror.miningBoxes = b.miningBoxes;
            ClientMirror.farmingBoxes = b.farmingBoxes;
            ClientMirror.courierTasks = b.courierTasks;
            ClientMirror.courierPoints = b.courierPoints;
            ClientMirror.markers = b.markers;
            ClientMirror.states = b.states;
            ClientMirror.ranStartup = b.ranStartup;
            ArrayList<Commodity> comms = new ArrayList<Commodity>();
            for (CommodityEntry e : b.commodities)
            {
                Item item = (Item) Item.itemRegistry.getObject(e.item);
                if (item != null)
                {
                    comms.add(new Commodity(new ItemStack(item, 1, e.damage), e.quantity, e.priceEach));
                }
            }
            ClientMirror.commodities = comms;
            ClientMirror.synced = true;
            ClientMirror.syncCount++;
        }
        catch (Exception e)
        {
            SimukraftReloaded.log.warning("MirrorSync: couldn't read server data: " + e);
        }
    }

    private static void packRequirements(Building b)
    {
        if (b == null)
        {
            return;
        }
        ArrayList<String> out = new ArrayList<String>();
        if (b.requirements != null)
        {
            for (Map.Entry<ItemStack, Integer> e : b.requirements.entrySet())
            {
                if (e.getKey() != null && e.getKey().getItem() != null && e.getValue() != null)
                {
                    out.add(Item.itemRegistry.getNameForObject(e.getKey().getItem()) + "|" + e.getKey().getItemDamage() + "|" + e.getValue());
                }
            }
        }
        b.syncedRequirements = out;
    }

    /** client: rebuild a building's requirements, which the floating list over its constructor box shows */
    private static void unpackRequirements(Building b)
    {
        if (b == null || b.syncedRequirements == null)
        {
            return;
        }
        HashMap<ItemStack, Integer> req = new HashMap<ItemStack, Integer>();
        for (String line : b.syncedRequirements)
        {
            String[] p = line.split("\\|");
            Item item = p.length == 3 ? (Item) Item.itemRegistry.getObject(p[0]) : null;
            if (item != null)
            {
                req.put(new ItemStack(item, 1, Integer.parseInt(p[1])), Integer.parseInt(p[2]));
            }
        }
        b.requirements = req;
    }

    /** only lets the mod's own classes and plain Java collections/values be read */
    private static class SafeObjectInputStream extends ObjectInputStream
    {
        SafeObjectInputStream(InputStream in) throws IOException
        {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException
        {
            String n = desc.getName();
            while (n.startsWith("["))
            {
                n = n.substring(1);
            }
            if (n.startsWith("L") && n.endsWith(";"))
            {
                n = n.substring(1, n.length() - 1);
            }
            boolean ok = n.length() == 1 // primitive arrays
                    || n.startsWith("ashjack.simukraftreloaded.")
                    || n.startsWith("java.lang.")
                    || n.equals("java.util.ArrayList") || n.equals("java.util.HashMap") || n.equals("java.util.Random")
                    || n.equals("java.util.concurrent.atomic.AtomicLong");
            if (!ok)
            {
                throw new IOException("unexpected class in Sim-U-Kraft sync: " + desc.getName());
            }
            return super.resolveClass(desc);
        }
    }
}
