package ashjack.simukraftreloaded.common.power;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import cpw.mods.fml.common.Loader;

/**
 * What the town's power grid needs from EnderIO, reached by reflection so Sim-U-Kraft doesn't depend on it: energy
 * conduits (put into a conduit bundle, disguised as the block they replaced with a facade), Dimensional Transceivers
 * sending or receiving on the town's power channel, and Powered Lights. Written against EnderIO 2.3 for 1.7.10; if
 * anything's missing it says it isn't available and the grid does nothing.
 */
public final class EnderIO
{
    private EnderIO() {}

    private static Boolean ok;
    static Block conduit, transceiver, light, stirling, solar, alloySmelter;
    private static Constructor<?> powerConduit, channel;
    private static Object powerType;
    private static Method addConduit, setFacade, setFacadeMeta, addSend, addReceive, sendChannels, receiveChannels,
            setFace, setInverted, setRequiresPower, registerChannel;

    public static synchronized boolean available()
    {
        if (ok != null)
        {
            return ok;
        }
        ok = false;
        if (!Loader.isModLoaded("EnderIO"))
        {
            return false;
        }
        try
        {
            conduit = Block.getBlockFromName("EnderIO:blockConduitBundle");
            transceiver = Block.getBlockFromName("EnderIO:blockTransceiver");
            light = Block.getBlockFromName("EnderIO:blockElectricLight");
            stirling = Block.getBlockFromName("EnderIO:blockStirlingGenerator");
            solar = Block.getBlockFromName("EnderIO:blockSolarPanel");
            alloySmelter = Block.getBlockFromName("EnderIO:blockAlloySmelter");
            Class<?> bundle = Class.forName("crazypants.enderio.conduit.TileConduitBundle");
            Class<?> iconduit = Class.forName("crazypants.enderio.conduit.IConduit");
            powerConduit = Class.forName("crazypants.enderio.conduit.power.PowerConduit").getConstructor(int.class);
            addConduit = bundle.getMethod("addConduit", iconduit);
            setFacade = bundle.getMethod("setFacadeId", Block.class);
            setFacadeMeta = bundle.getMethod("setFacadeMetadata", int.class);
            Class<?> type = Class.forName("crazypants.enderio.machine.transceiver.ChannelType");
            Class<?> ch = Class.forName("crazypants.enderio.machine.transceiver.Channel");
            powerType = type.getField("POWER").get(null);
            channel = ch.getConstructor(String.class, type);
            Class<?> tt = Class.forName("crazypants.enderio.machine.transceiver.TileTransceiver");
            addSend = tt.getMethod("addSendChanel", ch);
            addReceive = tt.getMethod("addRecieveChanel", ch);
            sendChannels = tt.getMethod("getSendChannels", type);
            receiveChannels = tt.getMethod("getRecieveChannels", type);
            Class<?> reg = Class.forName("crazypants.enderio.machine.transceiver.ServerChannelRegister");
            registerChannel = reg.getMethod("addChannel", ch);
            Class<?> tl = Class.forName("crazypants.enderio.machine.light.TileElectricLight");
            setFace = tl.getMethod("setFace", ForgeDirection.class);
            setInverted = tl.getMethod("setInverted", boolean.class);
            setRequiresPower = tl.getMethod("setRequiresPower", boolean.class);
            ok = conduit != null && transceiver != null && light != null;
        }
        catch (Throwable e)
        {
            SimukraftReloaded.log.warning("Power grid: EnderIO isn't the version it was written for (" + e + ") - no grid");
            ok = false;
        }
        return ok;
    }

    // ---------------------------------------------------------------- conduits

    public static boolean isConduit(World w, int x, int y, int z)
    {
        return available() && w.getBlock(x, y, z) == conduit;
    }

    /** an energy conduit here, looking like the block that was here (unless that was air) */
    public static boolean placeConduit(World w, int x, int y, int z)
    {
        if (!available())
        {
            return false;
        }
        Block was = w.getBlock(x, y, z);
        int wasMeta = w.getBlockMetadata(x, y, z);
        boolean disguise = !was.isAir(w, x, y, z) && was.isOpaqueCube() && w.getTileEntity(x, y, z) == null;
        try
        {
            w.setBlock(x, y, z, conduit, 0, 3);
            TileEntity te = w.getTileEntity(x, y, z);
            if (te == null)
            {
                return false;
            }
            addConduit.invoke(te, powerConduit.newInstance(0));
            if (disguise)
            {
                setFacade.invoke(te, was);
                setFacadeMeta.invoke(te, wasMeta);
            }
            te.markDirty();
            w.markBlockForUpdate(x, y, z);
            return true;
        }
        catch (Throwable e)
        {
            SimukraftReloaded.log.warning("Power grid: couldn't lay a conduit at " + x + "," + y + "," + z + ": " + e);
            w.setBlock(x, y, z, was, wasMeta, 3);
            return false;
        }
    }

    // ---------------------------------------------------------------- transceivers

    public static boolean isTransceiver(World w, int x, int y, int z)
    {
        return available() && w.getBlock(x, y, z) == transceiver;
    }

    public static boolean placeTransceiver(World w, int x, int y, int z)
    {
        if (!available())
        {
            return false;
        }
        w.setBlock(x, y, z, transceiver, 0, 3);
        return w.getTileEntity(x, y, z) != null;
    }

    /** whether this transceiver is on the channel (sending or receiving), putting it on if not; false if it isn't one */
    @SuppressWarnings("unchecked")
    public static boolean tune(World w, int x, int y, int z, String name, boolean send)
    {
        if (!isTransceiver(w, x, y, z))
        {
            return false;
        }
        try
        {
            TileEntity te = w.getTileEntity(x, y, z);
            Object ch = channel.newInstance(name, powerType);
            Set<Object> have = (Set<Object>) (send ? sendChannels : receiveChannels).invoke(te, powerType);
            if (have == null || !have.contains(ch))
            {
                registerChannel.invoke(registry(), ch);
                (send ? addSend : addReceive).invoke(te, ch);
                te.markDirty();
            }
            return true;
        }
        catch (Throwable e)
        {
            SimukraftReloaded.log.warning("Power grid: couldn't tune the transceiver at " + x + "," + y + "," + z + ": " + e);
            return false;
        }
    }

    private static Object registry() throws Exception
    {
        return Class.forName("crazypants.enderio.machine.transceiver.ServerChannelRegister").getField("instance").get(null);
    }

    // ---------------------------------------------------------------- lights

    public static boolean isLight(World w, int x, int y, int z)
    {
        return available() && w.getBlock(x, y, z) == light;
    }

    /** a Powered Light, on whenever it has power (no redstone needed), standing on what's below it */
    public static boolean placeLight(World w, int x, int y, int z)
    {
        if (!available())
        {
            return false;
        }
        try
        {
            w.setBlock(x, y, z, light, 0, 3);
            TileEntity te = w.getTileEntity(x, y, z);
            if (te == null)
            {
                return false;
            }
            setFace.invoke(te, ForgeDirection.DOWN);
            setInverted.invoke(te, true);
            setRequiresPower.invoke(te, true);
            te.markDirty();
            w.markBlockForUpdate(x, y, z);
            return true;
        }
        catch (Throwable e)
        {
            SimukraftReloaded.log.warning("Power grid: couldn't put a light at " + x + "," + y + "," + z + ": " + e);
            return false;
        }
    }

    // ---------------------------------------------------------------- machines

    public static boolean isAlloySmelter(World w, int x, int y, int z)
    {
        return available() && alloySmelter != null && w.getBlock(x, y, z) == alloySmelter;
    }

    public static boolean placeAlloySmelter(World w, int x, int y, int z)
    {
        if (!available() || alloySmelter == null)
        {
            return false;
        }
        w.setBlock(x, y, z, alloySmelter, 0, 3);
        return true;
    }

    public static boolean isStirling(World w, int x, int y, int z)
    {
        return available() && stirling != null && w.getBlock(x, y, z) == stirling;
    }

    // ---------------------------------------------------------------- what they're made of

    /** the item a part is installed from, to take from stock when there's one (null if there isn't such an item) */
    public static ItemStack item(String part)
    {
        if (!available())
        {
            return null;
        }
        if ("conduit".equals(part))
        {
            Item i = (Item) Item.itemRegistry.getObject("EnderIO:itemPowerConduit");
            return i == null ? null : new ItemStack(i, 1, 0);
        }
        Block b = "transceiver".equals(part) ? transceiver : "light".equals(part) ? light : "smelter".equals(part) ? alloySmelter : null;
        Item i = b == null ? null : Item.getItemFromBlock(b);
        // the light the grid puts up is the inverted kind (on unless told off)
        return i == null ? null : new ItemStack(i, 1, "light".equals(part) ? 1 : 0);
    }
}
