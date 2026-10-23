package ashjack.simukraftreloaded.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Sounds and particles from server-side code. The old code used the client world (Minecraft.theWorld,
 * proxy.getClientWorld()) or World.playSound, which does nothing on a server world; these are broadcast to the
 * players nearby instead.
 */
public final class Effects
{
    private Effects() {}

    /** a sound at a position, heard by players near it */
    public static void sound(World world, double x, double y, double z, String sound)
    {
        sound(world, x, y, z, sound, 1f, 1f);
    }

    public static void sound(World world, double x, double y, double z, String sound, float volume, float pitch)
    {
        if (world != null && !world.isRemote)
        {
            world.playSoundEffect(x, y, z, sound, volume, pitch);
        }
    }

    /** a sound played to every player at their own position (money in, rooster in the morning, etc.) */
    public static void soundToAllPlayers(String sound)
    {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null)
        {
            return;
        }
        for (Object o : server.getConfigurationManager().playerEntityList)
        {
            EntityPlayer p = (EntityPlayer) o;
            p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, sound, 1f, 1f);
        }
    }

    /** particles sent to nearby players (WorldServer.func_147487_a is 1.7.10's spawnParticle for servers) */
    public static void particles(World world, String name, double x, double y, double z, int count, double spread, double speed)
    {
        if (world instanceof WorldServer)
        {
            ((WorldServer) world).func_147487_a(name, x, y, z, count, spread, spread, spread, speed);
        }
    }
}
