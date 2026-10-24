package ashjack.simukraftreloaded.network;

import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import ashjack.simukraftreloaded.packetsNEW.toServer.ActionPacket;

/**
 * Server-side handlers for GUI buttons. A GUI calls ServerActions.send("name", data); the server runs the handler
 * registered under that name, on the server thread, and then re-syncs the player so the GUI shows the result.
 */
public final class ServerActions
{
    public interface Action
    {
        void run(EntityPlayerMP player, NBTTagCompound data);
    }

    private static final HashMap<String, Action> actions = new HashMap<String, Action>();

    private ServerActions() {}

    public static void register(String name, Action action)
    {
        actions.put(name, action);
    }

    /** client side: ask the server to run an action */
    public static void send(String name, NBTTagCompound data)
    {
        PacketHandler.net.sendToServer(new ActionPacket(name, data));
    }

    public static void send(String name)
    {
        send(name, new NBTTagCompound());
    }

    /** server thread */
    public static void handle(EntityPlayerMP player, String name, NBTTagCompound data)
    {
        Action action = actions.get(name);
        if (action == null)
        {
            SimukraftReloaded.log.warning("ServerActions: unknown action '" + name + "' from " + player.getCommandSenderName());
            return;
        }
        SimukraftReloaded.log.info("ServerActions: " + player.getCommandSenderName() + " -> " + name + " " + data);
        try
        {
            action.run(player, data);
        }
        catch (Exception e)
        {
            SimukraftReloaded.log.warning("ServerActions: '" + name + "' failed: " + e);
            e.printStackTrace();
        }
        MirrorSync.sendTo(player);
    }
}
