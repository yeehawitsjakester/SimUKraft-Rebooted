package ashjack.simukraftreloaded.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;

/**
 * Keeps a little of the world loaded where Sims are working far from any player - a railway's next stretch, the town
 * wall's next length - so the work goes on there instead of waiting (or skipping ahead) until someone comes near. Each
 * job asks for a few chunks by a name (its depot, its gatehouse); asking again moves them; letting go frees them. A
 * light touch: a handful of chunks per job (config KeepChunksPerJob), none kept over a restart.
 */
public final class ChunkKeeper
{
    private ChunkKeeper() {}

    private static final Map<String, Ticket> tickets = new HashMap<String, Ticket>();
    private static boolean registered;

    /** at startup: Forge hands back last session's tickets, which are let go (the jobs ask again as they need) */
    public static void register()
    {
        if (registered)
        {
            return;
        }
        registered = true;
        ForgeChunkManager.setForcedChunkLoadingCallback(ModSimukraft.instance, new ForgeChunkManager.LoadingCallback()
        {
            public void ticketsLoaded(List<Ticket> loaded, World world)
            {
                for (Ticket t : loaded)
                {
                    ForgeChunkManager.releaseTicket(t);
                }
            }
        });
    }

    /** keeps these chunks loaded for this job (and only these: any it had before that aren't in the list go) */
    public static synchronized void keep(String job, World w, Set<ChunkCoordIntPair> chunks)
    {
        if (w == null || w.isRemote || SimukraftReloadedConfig.configKeepChunksPerJob <= 0)
        {
            return;
        }
        Ticket t = tickets.get(job);
        if (t == null || t.world != w)
        {
            release(job);
            t = ForgeChunkManager.requestTicket(ModSimukraft.instance, w, ForgeChunkManager.Type.NORMAL);
            if (t == null)
            {
                return; // Forge's limit for the mod is reached
            }
            t.getModData().setString("job", job);
            tickets.put(job, t);
        }
        Set<ChunkCoordIntPair> want = new HashSet<ChunkCoordIntPair>();
        for (ChunkCoordIntPair c : chunks)
        {
            if (want.size() >= Math.min(SimukraftReloadedConfig.configKeepChunksPerJob, t.getChunkListDepth()))
            {
                break;
            }
            want.add(c);
        }
        for (ChunkCoordIntPair c : new HashSet<ChunkCoordIntPair>(t.getChunkList()))
        {
            if (!want.contains(c))
            {
                ForgeChunkManager.unforceChunk(t, c);
            }
        }
        for (ChunkCoordIntPair c : want)
        {
            if (!t.getChunkList().contains(c))
            {
                ForgeChunkManager.forceChunk(t, c);
            }
        }
    }

    public static synchronized void release(String job)
    {
        Ticket t = tickets.remove(job);
        if (t != null)
        {
            try
            {
                ForgeChunkManager.releaseTicket(t);
            }
            catch (Exception e)
            {
                SimukraftReloaded.log.warning("ChunkKeeper: couldn't let go of " + job + ": " + e);
            }
        }
    }

    public static synchronized void releaseAll()
    {
        for (String job : new HashSet<String>(tickets.keySet()))
        {
            release(job);
        }
    }

    /** the chunk a block's in */
    public static ChunkCoordIntPair chunkOf(int x, int z)
    {
        return new ChunkCoordIntPair(x >> 4, z >> 4);
    }
}
