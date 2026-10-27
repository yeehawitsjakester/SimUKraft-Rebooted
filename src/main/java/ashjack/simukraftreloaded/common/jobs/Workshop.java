package ashjack.simukraftreloaded.common.jobs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Making things to order, from the game's own crafting recipes (any mod's), out of the chests by a workplace. A shop
 * says what it can work with and what it makes: a sawmill turns logs into planks, sticks, fences, doors and anything
 * else made only of wood; a smithy forges swords; a fletcher makes bows and arrows. What it's asked for is whatever the
 * town's workers are short of - the builders' and soldiers' needs, less what the warehouses and the shop already have
 * - so nothing is made for the sake of it. One craft at a time; what's missing for a craft is asked for in turn (made
 * here if the shop can, like sticks from planks, otherwise brought by a warehouse keeper).
 */
public final class Workshop
{
    private Workshop() {}

    /** what a shop works with, and what it's for */
    public enum Shop
    {
        SAWMILL("sawmill"), SMITHY("smithy"), FLETCHER("fletcher's");

        public final String title;

        Shop(String title)
        {
            this.title = title;
        }

        /** an input it knows what to do with */
        boolean material(ItemStack is)
        {
            Item item = is.getItem();
            if (isWood(is))
            {
                return true;
            }
            switch (this)
            {
                case SMITHY:
                    return item == Items.iron_ingot || item == Items.gold_ingot || item == Items.diamond
                            || item == Item.getItemFromBlock(Blocks.cobblestone);
                case FLETCHER:
                    return item == Items.string || item == Items.feather || item == Items.flint;
                default:
                    return false;
            }
        }

        /** something it makes when asked (and what it makes on the way, like sticks, comes from the materials) */
        boolean makes(Item item)
        {
            switch (this)
            {
                case SAWMILL:
                    return item != Items.bow && item != Items.arrow && !(item instanceof ItemSword);
                case SMITHY:
                    return item instanceof ItemSword && item != Items.wooden_sword;
                case FLETCHER:
                    return item == Items.bow || item == Items.arrow;
                default:
                    return false;
            }
        }

        /** what's always fetched for it rather than made here */
        boolean raw(ItemStack is)
        {
            // logs, and anything that isn't wood (ingots, string...): planks and sticks are made from the logs here
            return isLog(is) || material(is) && !isWood(is);
        }
    }

    /** a recipe as amounts of things, any of which will do for each */
    public static class Recipe
    {
        public final ItemStack output;
        /** each ingredient: the stacks any one of which will do (meta 32767 for any), and how many */
        final List<ItemStack[]> ins = new ArrayList<ItemStack[]>();
        final List<Integer> counts = new ArrayList<Integer>();

        Recipe(ItemStack output)
        {
            this.output = output;
        }
    }

    /** what happened in a step */
    public static class Step
    {
        /** what got made (null if nothing) */
        public ItemStack made;
        /** a material that's missing and can't be made here: to be brought (null if none) */
        public ItemStack missing;
        public int missingCount;
    }

    private static Map<Item, List<Recipe>> recipes;

    // ---------------------------------------------------------------- materials

    public static boolean isLog(ItemStack is)
    {
        Block b = Block.getBlockFromItem(is.getItem());
        return b instanceof BlockLog || hasOre(is, "logWood");
    }

    public static boolean isWood(ItemStack is)
    {
        Item item = is.getItem();
        return isLog(is) || item == Item.getItemFromBlock(Blocks.planks) || hasOre(is, "plankWood") || item == Items.stick
                || hasOre(is, "stickWood") || item == Item.getItemFromBlock(Blocks.wooden_slab) || hasOre(is, "slabWood");
    }

    private static boolean hasOre(ItemStack is, String ore)
    {
        try
        {
            int want = OreDictionary.getOreID(ore);
            for (int id : OreDictionary.getOreIDs(is))
            {
                if (id == want)
                {
                    return true;
                }
            }
        }
        catch (Exception e)
        {
            // an item the dictionary can't take
        }
        return false;
    }

    // ---------------------------------------------------------------- recipes

    /** the recipes this shop can use to make this */
    static List<Recipe> recipesFor(Shop shop, Item item)
    {
        List<Recipe> out = new ArrayList<Recipe>();
        List<Recipe> all = recipes().get(item);
        if (all == null)
        {
            return out;
        }
        for (Recipe r : all)
        {
            boolean ok = true;
            for (ItemStack[] options : r.ins)
            {
                boolean any = false;
                for (ItemStack o : options)
                {
                    if (o != null && o.getItem() != null && shop.material(o))
                    {
                        any = true;
                        break;
                    }
                }
                if (!any)
                {
                    ok = false;
                    break;
                }
            }
            if (ok)
            {
                out.add(r);
            }
        }
        return out;
    }

    /** whether this shop can make it (on its own, from its materials) */
    public static boolean canMake(Shop shop, Item item)
    {
        return item != null && shop.makes(item) && !recipesFor(shop, item).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static synchronized Map<Item, List<Recipe>> recipes()
    {
        if (recipes != null)
        {
            return recipes;
        }
        recipes = new IdentityHashMap<Item, List<Recipe>>();
        for (Object o : CraftingManager.getInstance().getRecipeList())
        {
            try
            {
                IRecipe ir = (IRecipe) o;
                ItemStack out = ir.getRecipeOutput();
                if (out == null || out.getItem() == null)
                {
                    continue;
                }
                List<Object> raw = new ArrayList<Object>();
                if (ir instanceof ShapedRecipes)
                {
                    raw.addAll(Arrays.asList((Object[]) ((ShapedRecipes) ir).recipeItems));
                }
                else if (ir instanceof ShapelessRecipes)
                {
                    raw.addAll(((ShapelessRecipes) ir).recipeItems);
                }
                else if (ir instanceof ShapedOreRecipe)
                {
                    raw.addAll(Arrays.asList(((ShapedOreRecipe) ir).getInput()));
                }
                else if (ir instanceof ShapelessOreRecipe)
                {
                    raw.addAll(((ShapelessOreRecipe) ir).getInput());
                }
                else
                {
                    continue;
                }
                Recipe r = new Recipe(out.copy());
                // the same ingredient in several squares counts up
                Map<Object, Integer> index = new HashMap<Object, Integer>();
                for (Object in : raw)
                {
                    ItemStack[] options;
                    Object k;
                    if (in == null)
                    {
                        continue;
                    }
                    if (in instanceof ItemStack)
                    {
                        ItemStack is = (ItemStack) in;
                        options = new ItemStack[] {is};
                        k = Item.itemRegistry.getNameForObject(is.getItem()) + "@" + is.getItemDamage();
                    }
                    else if (in instanceof List)
                    {
                        options = ((List<ItemStack>) in).toArray(new ItemStack[0]);
                        k = System.identityHashCode(in);
                    }
                    else
                    {
                        r = null;
                        break;
                    }
                    if (options.length == 0)
                    {
                        r = null;
                        break;
                    }
                    Integer at = index.get(k);
                    if (at == null)
                    {
                        index.put(k, r.ins.size());
                        r.ins.add(options);
                        r.counts.add(1);
                    }
                    else
                    {
                        r.counts.set(at, r.counts.get(at) + 1);
                    }
                }
                if (r == null || r.ins.isEmpty())
                {
                    continue;
                }
                List<Recipe> list = recipes.get(out.getItem());
                if (list == null)
                {
                    list = new ArrayList<Recipe>();
                    recipes.put(out.getItem(), list);
                }
                list.add(r);
            }
            catch (Exception e)
            {
                // a recipe that won't describe itself
            }
        }
        return recipes;
    }

    // ---------------------------------------------------------------- making

    /**
     * one craft towards making this here: the thing itself if everything's in the chests, otherwise one of the things
     * it takes (made from what's here, if this shop can), otherwise says what's missing.
     */
    public static Step step(Shop shop, List<IInventory> chests, Item want)
    {
        Step s = new Step();
        make(shop, chests, want, 0, s);
        return s;
    }

    private static boolean make(Shop shop, List<IInventory> chests, Item want, int depth, Step s)
    {
        List<Recipe> options = recipesFor(shop, want);
        if (options.isEmpty() || depth > 3)
        {
            return false;
        }
        // one that can be made right now
        for (Recipe r : options)
        {
            if (have(chests, r))
            {
                for (int i = 0; i < r.ins.size(); i++)
                {
                    take(chests, r.ins.get(i), r.counts.get(i));
                }
                ItemStack made = r.output.copy();
                ItemStack left = WarehouseStock.put(chests, made);
                if (left != null)
                {
                    s.missing = null;
                    return false; // the chests are full: nowhere to put it
                }
                s.made = r.output.copy();
                return true;
            }
        }
        // the first that's short of something we can make here: make that
        for (Recipe r : options)
        {
            for (int i = 0; i < r.ins.size(); i++)
            {
                int short_ = r.counts.get(i) - count(chests, r.ins.get(i));
                if (short_ <= 0)
                {
                    continue;
                }
                for (ItemStack o : r.ins.get(i))
                {
                    if (o != null && o.getItem() != want && shop.material(o) && !shop.raw(o) && make(shop, chests, o.getItem(), depth + 1, s))
                    {
                        return true;
                    }
                }
                if (s.missing == null)
                {
                    // nothing here makes it: it's to be brought. The first option the shop takes as a raw material
                    for (ItemStack o : r.ins.get(i))
                    {
                        if (o != null && shop.material(o))
                        {
                            s.missing = new ItemStack(o.getItem(), 1, o.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0 : o.getItemDamage());
                            s.missingCount = short_;
                            break;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean matches(ItemStack have, ItemStack option)
    {
        return have != null && option != null && have.getItem() == option.getItem()
                && (option.getItemDamage() == OreDictionary.WILDCARD_VALUE || option.getItemDamage() == have.getItemDamage());
    }

    private static int count(List<IInventory> chests, ItemStack[] options)
    {
        int n = 0;
        for (IInventory inv : chests)
        {
            for (int i = 0; i < inv.getSizeInventory(); i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                for (ItemStack o : options)
                {
                    if (matches(is, o))
                    {
                        n += is.stackSize;
                        break;
                    }
                }
            }
        }
        return n;
    }

    private static boolean have(List<IInventory> chests, Recipe r)
    {
        for (int i = 0; i < r.ins.size(); i++)
        {
            if (count(chests, r.ins.get(i)) < r.counts.get(i))
            {
                return false;
            }
        }
        return true;
    }

    private static void take(List<IInventory> chests, ItemStack[] options, int n)
    {
        for (IInventory inv : chests)
        {
            for (int i = 0; i < inv.getSizeInventory() && n > 0; i++)
            {
                ItemStack is = inv.getStackInSlot(i);
                for (ItemStack o : options)
                {
                    if (matches(is, o))
                    {
                        int got = Math.min(n, is.stackSize);
                        inv.decrStackSize(i, got);
                        n -= got;
                        break;
                    }
                }
            }
            inv.markDirty();
        }
    }

    // ---------------------------------------------------------------- orders

    /**
     * what this shop should make, most wanted first: everything the town's workers still need (and can be made here),
     * less what's in their chests, on its way, in the warehouses and in this shop's own chests already
     */
    public static LinkedHashMap<Item, Integer> orders(Shop shop, FolkData worker, List<IInventory> own, World world)
    {
        Map<Item, Integer> need = new IdentityHashMap<Item, Integer>();
        int dim = worker.employedAt.theDimension;
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.theirJob == null || f == worker || f.employedAt == null || f.employedAt.theDimension != dim)
            {
                continue;
            }
            if (f.theirJob instanceof JobCraftsman && ((JobCraftsman) f.theirJob).shop() == shop
                    || f.theirJob instanceof JobLumberjack && shop == Shop.SAWMILL)
            {
                continue; // what another of the same kind of shop is short of: they'll make it themselves
            }
            Map<Item, Integer> needs;
            try
            {
                needs = f.theirJob.remainingNeeds();
            }
            catch (Exception e)
            {
                continue;
            }
            if (needs == null || needs.isEmpty())
            {
                continue;
            }
            ArrayList<IInventory> theirs = null;
            for (Map.Entry<Item, Integer> e : needs.entrySet())
            {
                if (!canMake(shop, e.getKey()))
                {
                    continue;
                }
                if (theirs == null)
                {
                    theirs = WarehouseStock.storageNear(world, f.employedAt, 4);
                }
                int shortBy = e.getValue() - WarehouseStock.count(theirs, e.getKey()) - JobWarehouse.onTheWay(f.employedAt, e.getKey());
                if (shortBy > 0)
                {
                    Integer n = need.get(e.getKey());
                    need.put(e.getKey(), (n == null ? 0 : n) + shortBy);
                }
            }
        }
        LinkedHashMap<Item, Integer> out = new LinkedHashMap<Item, Integer>();
        if (need.isEmpty())
        {
            return out;
        }
        List<WarehouseStock> stocks = JobWarehouse.latestStocks(dim);
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<Map.Entry<Item, Integer>>(need.entrySet());
        java.util.Collections.sort(sorted, new java.util.Comparator<Map.Entry<Item, Integer>>()
        {
            public int compare(Map.Entry<Item, Integer> a, Map.Entry<Item, Integer> b)
            {
                return b.getValue() - a.getValue();
            }
        });
        for (Map.Entry<Item, Integer> e : sorted)
        {
            int n = e.getValue() - WarehouseStock.count(own, e.getKey());
            for (WarehouseStock st : stocks)
            {
                n -= st.total(e.getKey());
            }
            if (n > 0)
            {
                out.put(e.getKey(), n);
            }
        }
        return out;
    }

    /** a smithy, where blacksmiths and a fletcher are hired */
    public static boolean isSmithy(ashjack.simukraftreloaded.core.building.Building b)
    {
        String n = b == null || b.displayName == null ? "" : b.displayName.toLowerCase();
        return (n.contains("blacksmith") || n.contains("smithy")) && !"residential".equals(b.type);
    }

        /** the chests a shop works from: around its control box */
    public static ArrayList<IInventory> chests(World world, V3 at)
    {
        return WarehouseStock.storageNear(world, at, 4);
    }
}
