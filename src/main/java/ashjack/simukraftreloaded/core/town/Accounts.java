package ashjack.simukraftreloaded.core.town;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * The money that isn't a town's treasury or a Sim's own (FolkData.money): each business's funds, kept by the box its
 * staff work at, and each player's wallet. Saved in the world's simukraft/Accounts.sk2.
 */
public final class Accounts
{
    private Accounts() {}

    private static final Map<String, Float> balances = new LinkedHashMap<String, Float>();

    public static String business(V3 box)
    {
        return "biz:" + box;
    }

    public static String player(String name)
    {
        return "player:" + name.toLowerCase();
    }

    public static float get(String key)
    {
        if (SimukraftReloaded.isClientThread())
        {
            Float c = ashjack.simukraftreloaded.common.ClientMirror.accounts.get(key); // the server's, as last synced
            return c == null ? 0f : c;
        }
        Float f = balances.get(key);
        if (f == null && key.startsWith("player:"))
        {
            f = (float) SimukraftReloadedConfig.configStartingMoney; // a new player's first pocket money
            balances.put(key, f);
        }
        return f == null ? 0f : f;
    }

    public static void add(String key, double amount)
    {
        balances.put(key, get(key) + (float) amount);
    }

    public static void set(String key, float amount)
    {
        balances.put(key, amount);
    }

    /** a business that's gone: what it had goes to its town */
    public static void close(V3 box)
    {
        Float f = balances.remove(business(box));
        if (f != null && f > 0)
        {
            Economy.earn(Towns.at(box), f);
        }
    }

    /** a copy to send to clients (businesses' funds for their control boxes) */
    public static java.util.HashMap<String, Float> snapshot()
    {
        java.util.HashMap<String, Float> copy = new java.util.HashMap<String, Float>();
        for (Map.Entry<String, Float> e : balances.entrySet())
        {
            if (e.getKey().startsWith("biz:"))
            {
                copy.put(e.getKey(), e.getValue());
            }
        }
        return copy;
    }

    private static File file()
    {
        return new File(SimukraftReloaded.getSavesDataFolder() + "Accounts.sk2");
    }

    public static void load()
    {
        balances.clear();
        File f = file();
        if (!f.exists())
        {
            return;
        }
        for (String line : SimukraftReloaded.loadSK2(f.getAbsolutePath()))
        {
            int bar = line.lastIndexOf('|');
            if (bar > 0)
            {
                try
                {
                    balances.put(line.substring(0, bar), Float.parseFloat(line.substring(bar + 1)));
                }
                catch (NumberFormatException e)
                {
                    SimukraftReloaded.log.warning("Accounts: bad line " + line);
                }
            }
        }
    }

    public static void save()
    {
        ArrayList<String> lines = new ArrayList<String>();
        for (Map.Entry<String, Float> e : balances.entrySet())
        {
            lines.add(e.getKey() + "|" + e.getValue());
        }
        SimukraftReloaded.saveSK2(file().getPath(), lines);
    }

    public static void clear()
    {
        balances.clear();
    }
}
