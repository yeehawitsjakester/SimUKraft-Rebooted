package ashjack.simukraftreloaded.network;

import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
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

    /**
     * the town an action is about: the box (or ATM, or Sim) it's for. Only a town's owner and members (and ops) may
     * hire, fire, build, trade and so on in it; an unclaimed town is anyone's until someone claims it.
     */
    private static Town townConcerned(NBTTagCompound data)
    {
        for (String key : new String[] {"box", "atm"})
        {
            if (data.hasKey(key) || data.hasKey(key + "X") || data.hasKey(key + "x"))
            {
                V3 v = ActionHandlers.getV3(data, key);
                if (v != null)
                {
                    return Towns.at(v);
                }
            }
        }
        for (String key : new String[] {"name", "folk"})
        {
            FolkData f = data.hasKey(key) ? FolkData.getFolkByName(data.getString(key)) : null;
            if (f != null)
            {
                return Towns.of(f);
            }
        }
        return null;
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
        Town town = townConcerned(data);
        if (town != null && town.isClaimed() && !town.canManage(player))
        {
            ActionHandlers.tell(player, "That's in " + town.name + ", " + town.owner + "'s town - only its owner and members can do that");
            MirrorSync.sendTo(player);
            return;
        }
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
