package ashjack.simukraftreloaded.core.town;

import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Whose money pays for what. Everything done inside a town is paid from (or into) that town's treasury: wages for the
 * Sims who work there, building, shop sales, rent. Outside every town (and in a world with no towns yet) it's the old
 * shared credits. All money changes go through here, so who pays can change in one place.
 */
public final class Economy
{
    private Economy() {}

    /** a Sim's pay for work done, from their town */
    public static void pay(FolkData folk, double amount)
    {
        spend(Towns.of(folk), amount);
    }

    /** spent by whoever runs the place (a town, or the wilds' shared credits) */
    public static void spend(V3 where, double amount)
    {
        spend(Towns.at(where), amount);
    }

    public static void earn(V3 where, double amount)
    {
        spend(Towns.at(where), -amount);
    }

    public static void spend(Town town, double amount)
    {
        if (town != null)
        {
            town.treasury -= (float) amount;
        }
        else
        {
            SimukraftReloaded.states().credits -= (float) amount;
        }
    }

    public static void earn(Town town, double amount)
    {
        spend(town, -amount);
    }

    /** what the place can spend */
    public static float balance(V3 where)
    {
        return balance(Towns.at(where));
    }

    public static float balance(Town town)
    {
        return town != null ? town.treasury : SimukraftReloaded.states().credits;
    }

    public static void set(Town town, float amount)
    {
        if (town != null)
        {
            town.treasury = amount;
        }
        else
        {
            SimukraftReloaded.states().credits = amount;
        }
    }
}
