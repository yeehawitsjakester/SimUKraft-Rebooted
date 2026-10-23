package ashjack.simukraftreloaded.packetsNEW.toServer;

import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import ashjack.simukraftreloaded.common.ServerTasks;
import ashjack.simukraftreloaded.network.MirrorSync;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** client → server: "send me fresh data", sent when a Sim-U-Kraft screen opens and while it stays open */
public class SyncRequestPacket implements IMessage
{
    private static final Map<EntityPlayerMP, Long> lastSent = new WeakHashMap<EntityPlayerMP, Long>();

    public SyncRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<SyncRequestPacket, IMessage>
    {
        @Override
        public IMessage onMessage(SyncRequestPacket message, MessageContext ctx)
        {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTasks.schedule(new Runnable()
            {
                public void run()
                {
                    long now = System.currentTimeMillis();
                    Long last = lastSent.get(player);
                    if (last != null && now - last < 500)
                    {
                        return; // at most twice a second per player
                    }
                    lastSent.put(player, now);
                    MirrorSync.sendTo(player);
                }
            });
            return null;
        }
    }
}
