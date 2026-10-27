package ashjack.simukraftreloaded.packetsNEW.toClient;

import io.netty.buffer.ByteBuf;
import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.game.GameMode;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** server → client once a second: what the HUD line shows */
public class HudPacket implements IMessage
{
    public boolean ranStartup;
    public int gameModeNumber;
    public float credits;
    public int population;
    public int dayOfWeek;
    public String worldName = "";
    /** the town whose treasury credits is, or empty for the wilds */
    public String townName = "";
    public float wallet;

    public HudPacket() {}

    @Override
    public void fromBytes(ByteBuf buf)
    {
        ranStartup = buf.readBoolean();
        gameModeNumber = buf.readInt();
        credits = buf.readFloat();
        population = buf.readInt();
        dayOfWeek = buf.readInt();
        worldName = ByteBufUtils.readUTF8String(buf);
        townName = ByteBufUtils.readUTF8String(buf);
        wallet = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(ranStartup);
        buf.writeInt(gameModeNumber);
        buf.writeFloat(credits);
        buf.writeInt(population);
        buf.writeInt(dayOfWeek);
        ByteBufUtils.writeUTF8String(buf, worldName);
        ByteBufUtils.writeUTF8String(buf, townName);
        buf.writeFloat(wallet);
    }

    public static class Handler implements IMessageHandler<HudPacket, IMessage>
    {
        @Override
        public IMessage onMessage(final HudPacket m, MessageContext ctx)
        {
            ModSimukraft.proxy.runOnClientThread(new Runnable()
            {
                public void run()
                {
                    ClientMirror.ranStartup = m.ranStartup;
                    ClientMirror.states.gameModeNumber = m.gameModeNumber;
                    ClientMirror.states.credits = m.credits;
                    ClientMirror.states.dayOfWeek = m.dayOfWeek;
                    ClientMirror.population = m.population;
                    ClientMirror.worldName = m.worldName;
                    ClientMirror.townName = m.townName;
                    ClientMirror.wallet = m.wallet;
                    try
                    {
                        GameMode.setGameModeFromNumber(m.gameModeNumber);
                    }
                    catch (Exception e) {}
                }
            });
            return null;
        }
    }
}
