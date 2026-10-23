package ashjack.simukraftreloaded.common;

import java.util.concurrent.ConcurrentLinkedQueue;

import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;

/**
 * Work handed from the network thread to the server thread. 1.7.10's MinecraftServer has no
 * addScheduledTask, and packet handlers run on Netty threads, so they queue changes here instead.
 */
public final class ServerTasks
{
    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<Runnable>();

    private ServerTasks() {}

    public static void schedule(Runnable task)
    {
        queue.add(task);
    }

    /** called from the server tick */
    public static void runPending()
    {
        Runnable task;
        while ((task = queue.poll()) != null)
        {
            try
            {
                task.run();
            }
            catch (Exception e)
            {
                SimukraftReloaded.log.warning("ServerTasks: task failed: " + e);
                e.printStackTrace();
            }
        }
    }
}
