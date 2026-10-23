package ashjack.simukraftreloaded.packetsNEW.toClient;

import io.netty.buffer.ByteBuf;
import ashjack.simukraftreloaded.core.ModSimukraft;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** server → client: this world hasn't picked a Sim-U-Kraft game mode yet, show the choice */
public class AskGameModePacket implements IMessage
{
    public AskGameModePacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<AskGameModePacket, IMessage>
    {
        @Override
        public IMessage onMessage(AskGameModePacket message, MessageContext ctx)
        {
            ModSimukraft.proxy.showGameModeChoice();
            return null;
        }
    }
}
