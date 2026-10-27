package ashjack.simukraftreloaded.core.town;

import java.lang.reflect.Method;

import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;

/**
 * Players' wallets in a server-wide economy, when there is one: on a hybrid Forge/Bukkit server (Thermos, Crucible...)
 * with Vault and an economy plugin, a player's Sim-U-Kraft wallet is their Vault balance, so the same money works in
 * shops, plugins and towns. Found by reflection, so nothing changes on a plain Forge server (or in singleplayer): the
 * wallets are then Sim-U-Kraft's own (Accounts).
 */
public final class VaultBridge
{
    private VaultBridge() {}

    private static boolean tried;
    private static Object economy;
    private static Method getOfflinePlayer, getBalance, deposit, withdraw;

    public static boolean available()
    {
        if (!tried)
        {
            tried = true;
            try
            {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                Class<?> econ = Class.forName("net.milkbowl.vault.economy.Economy");
                Class<?> offline = Class.forName("org.bukkit.OfflinePlayer");
                Object server = bukkit.getMethod("getServer").invoke(null);
                Object services = server.getClass().getMethod("getServicesManager").invoke(server);
                Object reg = services.getClass().getMethod("getRegistration", Class.class).invoke(services, econ);
                if (reg != null)
                {
                    economy = reg.getClass().getMethod("getProvider").invoke(reg);
                    getOfflinePlayer = bukkit.getMethod("getOfflinePlayer", String.class);
                    getBalance = econ.getMethod("getBalance", offline);
                    deposit = econ.getMethod("depositPlayer", offline, double.class);
                    withdraw = econ.getMethod("withdrawPlayer", offline, double.class);
                    SimukraftReloaded.log.info("Economy: players' wallets are their Vault balances");
                }
            }
            catch (Throwable e)
            {
                economy = null; // no Bukkit, no Vault, or no economy plugin behind it
            }
        }
        return economy != null;
    }

    public static float balance(String player)
    {
        try
        {
            return ((Number) getBalance.invoke(economy, getOfflinePlayer.invoke(null, player))).floatValue();
        }
        catch (Throwable e)
        {
            SimukraftReloaded.log.warning("Economy: Vault couldn't give " + player + "'s balance: " + e);
            return 0f;
        }
    }

    public static void add(String player, double amount)
    {
        try
        {
            Object p = getOfflinePlayer.invoke(null, player);
            if (amount >= 0)
            {
                deposit.invoke(economy, p, amount);
            }
            else
            {
                withdraw.invoke(economy, p, -amount);
            }
        }
        catch (Throwable e)
        {
            SimukraftReloaded.log.warning("Economy: Vault couldn't change " + player + "'s balance: " + e);
        }
    }
}
