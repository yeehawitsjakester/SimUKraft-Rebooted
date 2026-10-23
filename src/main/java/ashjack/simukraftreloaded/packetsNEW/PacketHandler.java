package ashjack.simukraftreloaded.packetsNEW;

import ashjack.simukraftreloaded.packetsNEW.toClient.AskGameModePacket;
import ashjack.simukraftreloaded.packetsNEW.toServer.ActionPacket;
import ashjack.simukraftreloaded.packetsNEW.toServer.SyncRequestPacket;
import ashjack.simukraftreloaded.packetsNEW.toClient.HudPacket;
import ashjack.simukraftreloaded.packetsNEW.toClient.MirrorSyncPacket;
import ashjack.simukraftreloaded.packetsNEW.toServer.RoomBoxPacket;
import ashjack.simukraftreloaded.core.ModSimukraft;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class PacketHandler
{
  public static SimpleNetworkWrapper net;
  
  public static void initPackets()
  {
    // channel names are limited to 20 characters; the mod id is 24, so every packet on it was rejected
    net = NetworkRegistry.INSTANCE.newSimpleChannel("SUKReloaded");
    
    // ids 0-3 were the old packets (folk position, load building, demolish, generate folk), which passed their
    // data through static fields; GUI actions now go through ActionPacket
	net.registerMessage(RoomBoxPacket.Handler.class, RoomBoxPacket.class, 4, Side.SERVER);
	net.registerMessage(MirrorSyncPacket.Handler.class, MirrorSyncPacket.class, 5, Side.CLIENT);
	net.registerMessage(SyncRequestPacket.Handler.class, SyncRequestPacket.class, 6, Side.SERVER);
	net.registerMessage(ActionPacket.Handler.class, ActionPacket.class, 7, Side.SERVER);
	net.registerMessage(HudPacket.Handler.class, HudPacket.class, 8, Side.CLIENT);
	net.registerMessage(AskGameModePacket.Handler.class, AskGameModePacket.class, 9, Side.CLIENT);
	
  }
  
  private static int nextPacketId = 0;
  
  private static void registerMessage(Class packet, Class message)
  {
    net.registerMessage(packet, message, nextPacketId, Side.CLIENT);
    net.registerMessage(packet, message, nextPacketId, Side.SERVER);
    nextPacketId++;
  }
}