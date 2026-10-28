package ashjack.simukraftreloaded.packetsNEW.toClient;

import io.netty.buffer.ByteBuf;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.network.MirrorSync;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** server → client: a gzipped snapshot of the Sim-U-Kraft data for the client's GUIs */
public class MirrorSyncPacket implements IMessage
{
    private byte[] payload = new byte[0];

    public MirrorSyncPacket() {}

    public MirrorSyncPacket(byte[] payload)
    {
        this.payload = payload;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        payload = new byte[buf.readInt()];
        buf.readBytes(payload);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(payload.length);
        buf.writeBytes(payload);
    }

    public static class Handler implements IMessageHandler<MirrorSyncPacket, IMessage>
    {
        @Override
        public IMessage onMessage(final MirrorSyncPacket message, MessageContext ctx)
        {
            // unpacked here, on the network thread; only the swap waits for the client thread, so a big town's data
            // doesn't stall a frame every time it comes
            final MirrorSync.Bundle bundle = MirrorSync.decode(message.payload);
            ModSimukraft.proxy.runOnClientThread(new Runnable()
            {
                public void run()
                {
                    MirrorSync.install(bundle);
                }
            });
            return null;
        }
    }
}
