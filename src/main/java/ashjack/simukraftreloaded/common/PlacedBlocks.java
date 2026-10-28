package ashjack.simukraftreloaded.common;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Which blocks someone put there. Minecraft doesn't remember, so this does from now on: every block a player (or a
 * machine acting as one) places is noted, and forgotten when it's broken. The notes live in each chunk's own save, so
 * they go wherever the world goes. A sapling someone planted is noted too, and the trunk that grows from it stands on
 * that very spot - so a tree someone planted counts as theirs (a Sim's replanting doesn't).
 *
 * Sims working out in the world (lumberjacks, wall and rail builders, linemen) leave anything noted here alone.
 */
public final class PlacedBlocks
{
    private PlacedBlocks() {}

    private static final String TAG = "SimUKraftPlaced";
    /** dimension and chunk -> which of its 65536 blocks were placed */
    private static final Map<Long, BitSet> chunks = new ConcurrentHashMap<Long, BitSet>();
    /** chunks unloading: forgotten once they've been saved (1.7.10 unloads a chunk, then saves it) */
    private static final Map<Long, Boolean> leaving = new ConcurrentHashMap<Long, Boolean>();

    private static long key(int dim, int cx, int cz)
    {
        return ((long) dim << 48) ^ ((long) (cx & 0xFFFFFF) << 24) ^ (cz & 0xFFFFFF);
    }

    private static int index(int x, int y, int z)
    {
        return (y & 0xFF) << 8 | (z & 15) << 4 | (x & 15);
    }

    /** someone put this block here */
    public static boolean placed(World w, int x, int y, int z)
    {
        if (w == null || y < 0 || y > 255)
        {
            return false;
        }
        BitSet b = chunks.get(key(w.provider.dimensionId, x >> 4, z >> 4));
        if (b == null)
        {
            return false;
        }
        synchronized (b)
        {
            return b.get(index(x, y, z));
        }
    }

    private static void set(World w, int x, int y, int z, boolean placed)
    {
        if (w == null || w.isRemote || y < 0 || y > 255)
        {
            return;
        }
        long k = key(w.provider.dimensionId, x >> 4, z >> 4);
        BitSet b = chunks.get(k);
        if (b == null)
        {
            if (!placed)
            {
                return;
            }
            b = new BitSet();
            chunks.put(k, b);
        }
        synchronized (b)
        {
            b.set(index(x, y, z), placed);
        }
    }

    // ---------------------------------------------------------------- listening

    public static class Listener
    {
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
        public void onPlace(BlockEvent.PlaceEvent event)
        {
            set(event.world, event.x, event.y, event.z, true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
        public void onBreak(BlockEvent.BreakEvent event)
        {
            set(event.world, event.x, event.y, event.z, false);
        }

        /** read with the chunk */
        @SubscribeEvent
        public void onLoad(ChunkDataEvent.Load event)
        {
            Chunk c = event.getChunk();
            if (c.worldObj == null || c.worldObj.isRemote || !event.getData().hasKey(TAG))
            {
                return;
            }
            byte[] bits = event.getData().getByteArray(TAG);
            if (bits.length > 0)
            {
                chunks.put(key(c.worldObj.provider.dimensionId, c.xPosition, c.zPosition), BitSet.valueOf(bits));
            }
        }

        /** written with the chunk */
        @SubscribeEvent
        public void onSave(ChunkDataEvent.Save event)
        {
            Chunk c = event.getChunk();
            if (c.worldObj == null || c.worldObj.isRemote)
            {
                return;
            }
            long k = key(c.worldObj.provider.dimensionId, c.xPosition, c.zPosition);
            BitSet b = chunks.get(k);
            NBTTagCompound data = event.getData();
            if (leaving.remove(k) != null)
            {
                chunks.remove(k); // written now, and the chunk's gone
            }
            if (b == null)
            {
                data.removeTag(TAG);
                return;
            }
            synchronized (b)
            {
                if (b.isEmpty())
                {
                    data.removeTag(TAG);
                }
                else
                {
                    data.setByteArray(TAG, b.toByteArray());
                }
            }
        }

        /** forgotten once the chunk's saved on its way out */
        @SubscribeEvent
        public void onUnload(ChunkEvent.Unload event)
        {
            Chunk c = event.getChunk();
            if (c.worldObj != null && !c.worldObj.isRemote)
            {
                leaving.put(key(c.worldObj.provider.dimensionId, c.xPosition, c.zPosition), Boolean.TRUE);
            }
        }

        /** loaded again before it was saved: it isn't leaving after all */
        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load event)
        {
            Chunk c = event.getChunk();
            if (c.worldObj != null && !c.worldObj.isRemote)
            {
                leaving.remove(key(c.worldObj.provider.dimensionId, c.xPosition, c.zPosition));
            }
        }
    }
}
