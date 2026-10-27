package ashjack.simukraftreloaded.common.military;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import ashjack.simukraftreloaded.common.WorldUtil;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.entity.EntityBandit;
import ashjack.simukraftreloaded.entity.EntityFolk;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The town's criminals, for soldiers to deal with:
 *   wanted players - hurt a Sim and you're wanted for a few minutes (longer for killing one). Soldiers chase you and
 *                    arrest you once you're worn down: they never kill a player.
 *   bandits        - some nights a few turn up from out of town, rob a chest and run for it (EntityBandit).
 */
public class Crime
{
    /** player name -> when they stop being wanted (ms) */
    private static final Map<String, Long> wanted = new HashMap<String, Long>();

    private final Random rand = new Random();
    private long lastNight = -1;

    public static boolean isWanted(EntityPlayer p)
    {
        Long until = wanted.get(p.getCommandSenderName());
        return until != null && until > System.currentTimeMillis() && !p.capabilities.isCreativeMode;
    }

    /** a soldier has worn them down: no longer wanted, and slowed down for a while to think about it */
    public static void arrest(EntityPlayer p, String soldier)
    {
        wanted.remove(p.getCommandSenderName());
        p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 30 * 20, 1));
        p.addPotionEffect(new PotionEffect(Potion.weakness.id, 30 * 20, 0));
        SimukraftReloaded.sendChat(soldier + " has arrested " + p.getCommandSenderName());
    }

    private static void makeWanted(EntityPlayer p, String why, int minutes)
    {
        if (minutes <= 0 || p.capabilities.isCreativeMode)
        {
            return;
        }
        boolean already = isWanted(p);
        wanted.put(p.getCommandSenderName(), System.currentTimeMillis() + minutes * 60000L);
        if (!already)
        {
            SimukraftReloaded.sendChat(p.getCommandSenderName() + " " + why + " - the soldiers are after them!");
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event)
    {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof EntityFolk)
                || !(event.source.getEntity() instanceof EntityPlayerMP))
        {
            return;
        }
        EntityFolk folk = (EntityFolk) event.entityLiving;
        String who = folk.theData == null ? "a Sim" : folk.theData.name;
        makeWanted((EntityPlayer) event.source.getEntity(), "attacked " + who, SimukraftReloadedConfig.configWantedMinutes);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event)
    {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof EntityFolk)
                || !(event.source.getEntity() instanceof EntityPlayerMP))
        {
            return;
        }
        EntityFolk folk = (EntityFolk) event.entityLiving;
        String who = folk.theData == null ? "a Sim" : folk.theData.name;
        makeWanted((EntityPlayer) event.source.getEntity(), "killed " + who, SimukraftReloadedConfig.configWantedMinutes * 3);
    }

    // ---------------------------------------------------------------- the town

    /** the middle of town (the average of its buildings) in a dimension, or null if there's no town there */
    public static V3 townCentre(int dim)
    {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b.primaryXYZ != null && b.primaryXYZ.theDimension == dim && !b.isRoom())
            {
                x += b.primaryXYZ.x;
                y += b.primaryXYZ.y;
                z += b.primaryXYZ.z;
                n++;
            }
        }
        return n < 3 ? null : new V3(x / n, y / n, z / n, dim);
    }

    // ---------------------------------------------------------------- bandits

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        MinecraftServer server = MinecraftServer.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCounter() % 40 != 0)
        {
            return;
        }
        for (Iterator<Long> it = wanted.values().iterator(); it.hasNext();)
        {
            if (it.next() < System.currentTimeMillis())
            {
                it.remove();
            }
        }

        WorldServer world = server.worldServerForDimension(0);
        if (world == null)
        {
            return;
        }
        long time = world.getWorldTime();
        long day = time / 24000;
        // once a night, a little after dusk
        if (time % 24000 < 13000 || time % 24000 > 14000 || day == lastNight)
        {
            return;
        }
        lastNight = day;
        if (rand.nextInt(100) >= SimukraftReloadedConfig.configBanditNightChance || SimukraftReloaded.serverFolks.size() < 4)
        {
            return;
        }
        V3 centre = townCentre(0);
        if (centre != null && world.getClosestPlayer(centre.x, centre.y, centre.z, 128) != null)
        {
            spawnBandits(world, centre, 1 + rand.nextInt(SimukraftReloadedConfig.configBanditGroupSize));
        }
    }

    /** a group of bandits at the edge of town; how many it managed to place */
    public static int spawnBandits(World world, V3 centre, int count)
    {
        Random r = world.rand;
        double angle = r.nextDouble() * Math.PI * 2;
        int placed = 0;
        for (int i = 0; i < count * 8 && placed < count; i++)
        {
            double a = angle + (r.nextDouble() - 0.5) * 0.6, dist = 44 + r.nextDouble() * 10;
            int x = (int) Math.floor(centre.x + Math.cos(a) * dist), z = (int) Math.floor(centre.z + Math.sin(a) * dist);
            int y = WorldUtil.findStandableY(world, x, centre.y.intValue(), z, 20);
            if (y < 0)
            {
                continue;
            }
            EntityBandit bandit = new EntityBandit(world);
            bandit.setLocationAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0f);
            bandit.equip();
            world.spawnEntityInWorld(bandit);
            placed++;
        }
        if (placed > 0)
        {
            SimukraftReloaded.sendChat(placed == 1 ? "A bandit has been seen near town!" : placed + " bandits have been seen near town!");
        }
        return placed;
    }

    /** bandits and wanted players near a point (for soldiers to go after) */
    public static List<EntityPlayer> wantedNear(World world, V3 at, double range)
    {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        for (Object o : world.playerEntities)
        {
            EntityPlayer p = (EntityPlayer) o;
            if (isWanted(p) && p.getDistance(at.x, at.y, at.z) <= range)
            {
                out.add(p);
            }
        }
        return out;
    }
}
