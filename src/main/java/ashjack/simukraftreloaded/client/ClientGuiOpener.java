package ashjack.simukraftreloaded.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import ashjack.simukraftreloaded.packetsNEW.toServer.SyncRequestPacket;

/**
 * Opens a Sim-U-Kraft screen once fresh data has arrived from the server, so the screen's constructor sees
 * current folks and buildings (a tick or two in singleplayer). Gives up waiting after 3 seconds.
 */
public final class ClientGuiOpener
{
    public interface Factory
    {
        GuiScreen create();
    }

    private static Factory pending = null;
    private static int waitingForSync;
    private static long requestedAt;

    private ClientGuiOpener() {}

    public static void open(Factory factory)
    {
        pending = factory;
        waitingForSync = ClientMirror.syncCount;
        requestedAt = System.currentTimeMillis();
        PacketHandler.net.sendToServer(new SyncRequestPacket());
    }

    /** called every client tick */
    public static void tick()
    {
        if (pending == null)
        {
            return;
        }
        if (ClientMirror.syncCount != waitingForSync || System.currentTimeMillis() - requestedAt > 3000)
        {
            Factory f = pending;
            pending = null;
            Minecraft.getMinecraft().displayGuiScreen(f.create());
        }
    }
}
