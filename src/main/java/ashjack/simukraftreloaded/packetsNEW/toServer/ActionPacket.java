package ashjack.simukraftreloaded.packetsNEW.toServer;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.common.ServerTasks;
import ashjack.simukraftreloaded.network.ServerActions;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** client → server: a GUI button press. The server looks the action up in ServerActions and runs it on its own thread. */
public class ActionPacket implements IMessage
{
    private String action = "";
    private NBTTagCompound data = new NBTTagCompound();

    public ActionPacket() {}

    public ActionPacket(String action, NBTTagCompound data)
    {
        this.action = action;
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        action = ByteBufUtils.readUTF8String(buf);
        data = ByteBufUtils.readTag(buf);
        if (data == null)
        {
            data = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, action);
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<ActionPacket, IMessage>
    {
        @Override
        public IMessage onMessage(final ActionPacket message, MessageContext ctx)
        {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTasks.schedule(new Runnable()
            {
                public void run()
                {
                    ServerActions.handle(player, message.action, message.data);
                }
            });
            return null;
        }
    }
}
