package ashjack.simukraftreloaded.packetsNEW.toServer;

import ashjack.simukraftreloaded.common.ServerTasks;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Room Box settings, sent from the GUI and applied on the server thread.
 * Unlike the older packets this one carries its own data instead of passing it through static fields.
 */
public class RoomBoxPacket implements IMessage
{
    public static final byte RENAME = 0;
    public static final byte SET_MODE = 1;
    public static final byte SET_SIZE = 2;
    public static final byte EVICT = 3;

    private int x, y, z, dim;
    private byte action;
    private String value = "";

    public RoomBoxPacket() {}

    public RoomBoxPacket(V3 box, byte action, String value)
    {
        this.x = box.x.intValue();
        this.y = box.y.intValue();
        this.z = box.z.intValue();
        this.dim = box.theDimension;
        this.action = action;
        this.value = value == null ? "" : value;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        dim = buf.readInt();
        action = buf.readByte();
        value = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(dim);
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, value);
    }

    public static class Handler implements IMessageHandler<RoomBoxPacket, IMessage>
    {
        @Override
        public IMessage onMessage(final RoomBoxPacket message, MessageContext ctx)
        {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            // packet handlers run on the network thread; change game state on the server thread
            ServerTasks.schedule(new Runnable()
            {
                public void run()
                {
                    message.apply(player);
                }
            });
            return null;
        }
    }

    private void apply(EntityPlayerMP player)
    {
        if (player.dimension != dim || player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64 * 64)
        {
            return;
        }
        Building room = Building.getBuilding(new V3((double) x, (double) y, (double) z, dim));
        if (room == null || !room.isRoom())
        {
            return;
        }

        if (action == RENAME)
        {
            String name = value.trim();
            if (name.length() > 32)
            {
                name = name.substring(0, 32);
            }
            if (!name.isEmpty())
            {
                room.displayName = name;
                room.displayNameWithoutPK = name;
            }
        }
        else if (action == SET_MODE)
        {
            String mode = Building.ROOM_HOTEL.equals(value) ? Building.ROOM_HOTEL : Building.ROOM_APARTMENT;
            if (!mode.equals(room.roomMode))
            {
                // switching between apartment and hotel empties the room first
                evictAll(room);
                room.roomMode = mode;
                room.updateRoomRent();
                World world = MinecraftServer.getServer().worldServerForDimension(dim);
                world.setBlockMetadataWithNotify(x, y, z, room.isHotel() ? 1 : 0, 3);
            }
        }
        else if (action == SET_SIZE)
        {
            int size;
            try
            {
                size = Integer.parseInt(value);
            }
            catch (NumberFormatException e)
            {
                return;
            }
            if (size != 1 && size != 2 && size != 4)
            {
                return;
            }
            if (room.tennants.size() > size)
            {
                player.addChatComponentMessage(new ChatComponentText(room.displayNameWithoutPK + " has " + room.tennants.size()
                        + " people living in it - evict them before making it smaller"));
                return;
            }
            room.roomSize = size;
            room.updateRoomRent();
        }
        else if (action == EVICT)
        {
            evictAll(room);
        }

        room.saveThisBuilding();
    }

    private static void evictAll(Building room)
    {
        if (room.isHotel())
        {
            room.checkOut();
            return;
        }
        for (String name : new ArrayList<String>(room.tennants))
        {
            room.removeTennant(name);
            FolkData folk = FolkData.getFolkByName(name);
            if (folk != null)
            {
                folk.action = FolkAction.WANDER;
                folk.actionArrival = null;
                folk.stayPut = false;
                folk.statusText = "Evicted";
            }
        }
    }
}
