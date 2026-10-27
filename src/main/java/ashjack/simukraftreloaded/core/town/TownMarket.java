package ashjack.simukraftreloaded.core.town;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock.Source;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;

/**
 * One town's market. Supply is everything the town holds - its warehouses' chests, its mines', the leftovers by its
 * finished buildings, its factories' output (WarehouseStock) - counted every minute or so. Demand is what's been
 * wanted lately: builders' and workers' needs, Sims buying food, players buying; it fades a fifth each day. A price is
 * the item's base price times the square root of demand over supply, kept between a quarter and four times base, so
 * plenty makes things cheap and scarcity dear. Each day's prices are kept, for the market screen's charts.
 *
 * Items are counted by item, any metadata (all wool together), by registry name.
 */
public class TownMarket implements Serializable
{
    private static final long serialVersionUID = 1L;

    public int townId;
    /** item name -> how many the town holds (last count) */
    public HashMap<String, Integer> supply = new HashMap<String, Integer>();
    /** item name -> recent demand */
    public HashMap<String, Float> demand = new HashMap<String, Float>();
    /** item name -> the last days' prices, oldest first */
    public HashMap<String, ArrayList<Float>> history = new HashMap<String, ArrayList<Float>>();
    public long counted;

    public static final int HISTORY_DAYS = 30;
    private static final float SOFTEN = 16f, MIN = 0.25f, MAX = 4f, DECAY = 0.8f;

    private static final Map<Integer, TownMarket> markets = new HashMap<Integer, TownMarket>();

    public static TownMarket of(Town town)
    {
        if (town == null)
        {
            return null;
        }
        if (SimukraftReloaded.isClientThread())
        {
            return ashjack.simukraftreloaded.common.ClientMirror.markets.get(town.id);
        }
        TownMarket m = markets.get(town.id);
        if (m == null)
        {
            m = load(town.id);
            markets.put(town.id, m);
        }
        return m;
    }

    public static String key(Item item)
    {
        Object n = Item.itemRegistry.getNameForObject(item);
        return n == null ? "" : n.toString();
    }

    public int supplyOf(Item item)
    {
        Integer n = supply.get(key(item));
        return n == null ? 0 : n;
    }

    public float demandOf(Item item)
    {
        Float d = demand.get(key(item));
        return d == null ? 0 : d;
    }

    /** this town's price for one of these */
    public float price(ItemStack is)
    {
        float base = Market.basePrice(is);
        float ratio = (float) Math.sqrt((demandOf(is.getItem()) + SOFTEN) / (supplyOf(is.getItem()) + SOFTEN));
        return base * Math.max(MIN, Math.min(MAX, ratio));
    }

    /** the price of one more of these once this many more are in stock */
    public float priceWith(ItemStack is, int moreInStock)
    {
        float base = Market.basePrice(is);
        float ratio = (float) Math.sqrt((demandOf(is.getItem()) + SOFTEN) / (supplyOf(is.getItem()) + moreInStock + SOFTEN));
        return base * Math.max(MIN, Math.min(MAX, ratio));
    }

    /**
     * what a player gets for selling this many: each one at the sell share of the price as it stands with the ones
     * before it already in stock, so a big pile fetches less a piece than one. Shown before a sale and paid by it.
     */
    public float sellValue(ItemStack is, int qty)
    {
        float total = 0;
        for (int k = 0; k < qty; k++)
        {
            total += priceWith(is, k);
        }
        return total * ashjack.simukraftreloaded.network.MarketActions.SELL_SHARE;
    }

    /** sellValue for n more, after `already` have gone in this same sale */
    public float sellValueAfter(ItemStack is, int already, int n)
    {
        float total = 0;
        for (int k = 0; k < n; k++)
        {
            total += priceWith(is, already + k);
        }
        return total * ashjack.simukraftreloaded.network.MarketActions.SELL_SHARE;
    }

    /** someone wanted this many: Sims eating, builders needing, players buying */
    public void want(Item item, double qty)
    {
        if (item != null && qty > 0)
        {
            String k = key(item);
            Float d = demand.get(k);
            demand.put(k, (d == null ? 0 : d) + (float) qty);
        }
    }

    // ---------------------------------------------------------------- counting

    /** the storage the town can sell from: its warehouses' sources (warehouse, mines, leftovers, factories) */
    public static List<Source> storage(Town town, World world)
    {
        List<Source> out = new ArrayList<Source>();
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b.primaryXYZ != null && b.buildingComplete && b.displayNameWithoutPK != null
                    && b.displayNameWithoutPK.toLowerCase().contains("warehouse") && town.contains(b.primaryXYZ))
            {
                for (Source s : WarehouseStock.scan(b, b.primaryXYZ, world).sources)
                {
                    if (s.kind != WarehouseStock.Kind.ME && town.contains(s.at))
                    {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    /** counts what the town holds now */
    public void count(Town town)
    {
        World world = MinecraftServer.getServer().worldServerForDimension(town.dimension());
        if (world == null)
        {
            return;
        }
        HashMap<String, Integer> now = new HashMap<String, Integer>();
        for (Source s : storage(town, world))
        {
            for (Map.Entry<Item, Integer> e : s.counts.entrySet())
            {
                String k = key(e.getKey());
                Integer n = now.get(k);
                now.put(k, (n == null ? 0 : n) + e.getValue());
            }
        }
        supply = now;
        counted = System.currentTimeMillis();
    }

    /** the end of a day: today's prices into the history, and demand fades */
    public void endOfDay(Town town)
    {
        java.util.HashSet<String> items = new java.util.HashSet<String>(supply.keySet());
        items.addAll(demand.keySet());
        for (String k : items)
        {
            Item item = (Item) Item.itemRegistry.getObject(k);
            if (item == null)
            {
                continue;
            }
            ArrayList<Float> h = history.get(k);
            if (h == null)
            {
                history.put(k, h = new ArrayList<Float>());
            }
            h.add(price(new ItemStack(item)));
            while (h.size() > HISTORY_DAYS)
            {
                h.remove(0);
            }
        }
        for (Map.Entry<String, Float> e : new ArrayList<Map.Entry<String, Float>>(demand.entrySet()))
        {
            float d = e.getValue() * DECAY;
            if (d < 0.5f)
            {
                demand.remove(e.getKey());
            }
            else
            {
                e.setValue(d);
            }
        }
        save();
    }

    /** yesterday's price, or the price now if there's no history yet */
    public float yesterday(ItemStack is)
    {
        ArrayList<Float> h = history.get(key(is.getItem()));
        return h == null || h.isEmpty() ? price(is) : h.get(h.size() - 1);
    }

    // ---------------------------------------------------------------- saving

    private static File file(int id)
    {
        return new File(SimukraftReloaded.getSavesDataFolder() + "Markets" + File.separator + "t" + id + ".sk2");
    }

    public void save()
    {
        if (SimukraftReloaded.isClientThread())
        {
            return;
        }
        file(townId).getParentFile().mkdirs();
        ArrayList<String> lines = new ArrayList<String>();
        for (Map.Entry<String, Float> e : demand.entrySet())
        {
            lines.add("d|" + e.getKey() + "|" + e.getValue());
        }
        for (Map.Entry<String, ArrayList<Float>> e : history.entrySet())
        {
            StringBuilder sb = new StringBuilder("h|" + e.getKey() + "|");
            for (Float f : e.getValue())
            {
                sb.append(f).append(',');
            }
            lines.add(sb.toString());
        }
        SimukraftReloaded.saveSK2(file(townId).getPath(), lines);
    }

    private static TownMarket load(int id)
    {
        TownMarket m = new TownMarket();
        m.townId = id;
        File f = file(id);
        if (!f.exists())
        {
            return m;
        }
        for (String line : SimukraftReloaded.loadSK2(f.getAbsolutePath()))
        {
            String[] p = line.split("\\|");
            try
            {
                if (p[0].equals("d") && p.length == 3)
                {
                    m.demand.put(p[1], Float.parseFloat(p[2]));
                }
                else if (p[0].equals("h") && p.length >= 2)
                {
                    ArrayList<Float> h = new ArrayList<Float>();
                    if (p.length == 3)
                    {
                        for (String v : p[2].split(","))
                        {
                            if (!v.isEmpty())
                            {
                                h.add(Float.parseFloat(v));
                            }
                        }
                    }
                    m.history.put(p[1], h);
                }
            }
            catch (NumberFormatException e)
            {
                SimukraftReloaded.log.warning("TownMarket: bad line " + line);
            }
        }
        return m;
    }

    public static void saveAll()
    {
        for (TownMarket m : markets.values())
        {
            m.save();
        }
    }

    public static void clear()
    {
        markets.clear();
    }

    /** copies to send to clients (for the market screen) */
    public static HashMap<Integer, TownMarket> snapshot()
    {
        HashMap<Integer, TownMarket> copy = new HashMap<Integer, TownMarket>();
        for (Town t : Towns.serverTowns)
        {
            TownMarket m = of(t);
            if (m != null)
            {
                copy.put(t.id, m);
            }
        }
        return copy;
    }

    /** for the market screen: everything with a price worth showing, most in stock first */
    public List<String> listed()
    {
        java.util.HashSet<String> items = new java.util.HashSet<String>(supply.keySet());
        items.addAll(demand.keySet());
        List<String> out = new ArrayList<String>(items);
        java.util.Collections.sort(out, new java.util.Comparator<String>()
        {
            public int compare(String a, String b)
            {
                int sa = supply.get(a) == null ? 0 : supply.get(a), sb = supply.get(b) == null ? 0 : supply.get(b);
                return sb - sa;
            }
        });
        return out;
    }
}
