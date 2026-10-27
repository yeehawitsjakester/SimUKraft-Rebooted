package ashjack.simukraftreloaded.core.town;

import net.minecraft.item.ItemStack;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Who pays whom. Money sits with towns (their treasury), businesses (funds, by the box their staff work at), Sims
 * (FolkData.money) and players (their wallet) - see Accounts.
 *
 *   wages     - a business pays its staff from its funds; while it hasn't earned enough (a new one), and for public
 *               work (builders, terraformers, the warehouse, soldiers), the town's treasury does
 *   spending  - Sims pay for food at the shops (to the shop), and their rent (to the treasury)
 *   selling   - a business's goods are bought by the town when the warehouse collects them, at market prices
 *   taxes     - the town takes its tax rate of every wage and sale
 *
 * Outside every town (and in a world with no towns yet) the old shared credits stand in for a treasury.
 */
public final class Economy
{
    private Economy() {}

    // ---------------------------------------------------------------- wages

    /** a Sim's pay for work done: from their workplace's funds, or their town when those run out; taxed */
    public static void pay(FolkData folk, double amount)
    {
        if (amount <= 0)
        {
            return;
        }
        Town town = Towns.of(folk);
        if (folk.employedAt != null)
        {
            String biz = Accounts.business(folk.employedAt);
            double fromBiz = Math.min(amount, Math.max(0, Accounts.get(biz)));
            Accounts.add(biz, -fromBiz);
            spend(town, amount - fromBiz);
        }
        else
        {
            spend(town, amount);
        }
        double tax = amount * taxRate(town);
        earn(town, tax);
        folk.money += (float) (amount - tax);
    }

    // ---------------------------------------------------------------- Sims spending

    /**
     * a Sim buys something from the business at this box. If they can't afford it (no job, or broke) the town pays
     * for it - nobody starves. The business gets the price, less the town's tax.
     */
    public static void purchase(FolkData buyer, V3 seller, double amount)
    {
        if (amount <= 0)
        {
            return;
        }
        Town town = Towns.at(seller);
        double own = Math.min(amount, Math.max(0, buyer.money));
        buyer.money -= (float) own;
        spend(town, amount - own);
        double tax = amount * taxRate(town);
        earn(town, tax);
        if (seller != null)
        {
            Accounts.add(Accounts.business(seller), amount - tax);
        }
    }

    /** a Sim buys goods from the business at this box, at the town's price; it counts towards demand */
    public static void purchase(FolkData buyer, V3 seller, ItemStack goods, float markup)
    {
        Town town = Towns.at(seller);
        Market.want(town, goods, goods.stackSize);
        purchase(buyer, seller, Market.price(town, goods) * goods.stackSize * markup);
    }

    /** rent from a tenant to their town: what they can manage; returns what they paid */
    public static float rent(FolkData tenant, Town town, double amount)
    {
        float paid = (float) Math.min(amount, Math.max(0, tenant.money));
        tenant.money -= paid;
        earn(town, paid);
        return paid;
    }

    // ---------------------------------------------------------------- businesses selling

    /** the town buys a business's goods (collected by the warehouse) at its market price; returns what it paid */
    public static float townBuys(V3 business, ItemStack goods)
    {
        Town town = Towns.at(business);
        float price = Market.price(town, goods) * goods.stackSize;
        if (price <= 0)
        {
            return 0;
        }
        spend(town, price);
        double tax = price * taxRate(town);
        earn(town, tax);
        Accounts.add(Accounts.business(business), price - tax);
        return price;
    }

    public static float funds(V3 business)
    {
        return business == null ? 0 : Accounts.get(Accounts.business(business));
    }

    // ---------------------------------------------------------------- treasuries

    public static double taxRate(Town town)
    {
        return town == null ? 0 : town.taxRate;
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

    // ---------------------------------------------------------------- players

    /** a player's own money: their Vault balance on a server with one (VaultBridge), or Sim-U-Kraft's wallet */
    public static float wallet(String player)
    {
        if (!SimukraftReloaded.isClientThread() && VaultBridge.available())
        {
            return VaultBridge.balance(player);
        }
        return Accounts.get(Accounts.player(player));
    }

    public static void addToWallet(String player, double amount)
    {
        if (VaultBridge.available())
        {
            VaultBridge.add(player, amount);
            return;
        }
        Accounts.add(Accounts.player(player), amount);
    }
}
