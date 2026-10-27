package ashjack.simukraftreloaded.common.military;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.folk.FolkData;

import com.google.common.collect.Multimap;

/**
 * What a soldier fights with, and what they're trained to fight with. Everything better than the standard-issue stone
 * sword comes from chests in their barracks (the armory): they take the best weapon and bow their rank allows, and
 * arrows, and put back what they were carrying if it's worse. What they carry is in FolkData.inventory, so two soldiers
 * never share a sword, and it wears out as they use it.
 *
 * Ranks, by soldier level (which goes up with every kill):
 *   Recruit   (1-3)  swords, up to iron; no bows, nothing enchanted
 *   Archer    (4-6)  any plain sword, and a bow for anything a few blocks off
 *   Tactician (7-8)  enchanted gear too; creepers, skeletons, witches, blazes and ghasts are shot from a distance, and
 *                    they back off from creepers
 *   Veteran   (9-10) modded weapons too: swords (anything that does more damage in the hand) and bows or crossbows
 */
public final class Armory
{
    private Armory() {}

    public enum Rank
    {
        RECRUIT("Recruit"), ARCHER("Archer"), TACTICIAN("Tactician"), VETERAN("Veteran");

        public final String title;

        Rank(String title)
        {
            this.title = title;
        }

        public static Rank of(float level)
        {
            return level >= 9 ? VETERAN : level >= 7 ? TACTICIAN : level >= 4 ? ARCHER : RECRUIT;
        }

        public boolean atLeast(Rank r)
        {
            return ordinal() >= r.ordinal();
        }
    }

    /** the stone sword every soldier has; it never wears out */
    public static ItemStack standardIssue()
    {
        return new ItemStack(Items.stone_sword);
    }

    public static final int ARROWS_CARRIED = 64;

    // ---------------------------------------------------------------- judging gear

    public static boolean isVanilla(ItemStack is)
    {
        Object name = Item.itemRegistry.getNameForObject(is.getItem());
        return name != null && name.toString().startsWith("minecraft:");
    }

    /** damage in the hand, from the item's attribute modifiers, plus sharpness; 0 for things that aren't weapons */
    public static float meleeScore(ItemStack is)
    {
        float dmg = 0;
        try
        {
            @SuppressWarnings("unchecked")
            Multimap<String, AttributeModifier> mods = is.getAttributeModifiers();
            for (AttributeModifier m : mods.get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName()))
            {
                dmg += m.getAmount();
            }
        }
        catch (Exception e)
        {
            return 0; // some modded items can't answer out of context
        }
        if (dmg <= 0)
        {
            return 0;
        }
        return dmg + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, is) * 1.25f;
    }

    /** a bow (or a modded bow or crossbow: drawn like a bow) */
    public static boolean isBow(ItemStack is)
    {
        if (is.getItem() instanceof ItemBow)
        {
            return true;
        }
        try
        {
            return !isVanilla(is) && is.getItem().getItemUseAction(is) == EnumAction.bow;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static float bowScore(ItemStack is)
    {
        return 1 + EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, is) * 0.5f
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, is) * 0.5f + (isVanilla(is) ? 0 : 1);
    }

    /** whether a soldier of this rank knows how to use it */
    public static boolean allowed(Rank rank, ItemStack is)
    {
        if (is.isItemEnchanted() && !rank.atLeast(Rank.TACTICIAN))
        {
            return false;
        }
        if (!isVanilla(is) && !(rank.atLeast(Rank.VETERAN) && SimukraftReloadedConfig.configSoldiersUseModdedWeapons))
        {
            return false;
        }
        if (isBow(is))
        {
            return rank.atLeast(Rank.ARCHER);
        }
        if (is.getItem() == Items.diamond_sword)
        {
            return rank.atLeast(Rank.ARCHER);
        }
        return is.getItem() instanceof ItemSword || !isVanilla(is) && meleeScore(is) > 0;
    }

    // ---------------------------------------------------------------- carried

    public static ItemStack carriedWeapon(FolkData f)
    {
        ItemStack best = null;
        for (ItemStack is : f.inventory)
        {
            if (is != null && !isBow(is) && meleeScore(is) > 0 && (best == null || meleeScore(is) > meleeScore(best)))
            {
                best = is;
            }
        }
        return best;
    }

    public static ItemStack carriedBow(FolkData f)
    {
        for (ItemStack is : f.inventory)
        {
            if (is != null && isBow(is))
            {
                return is;
            }
        }
        return null;
    }

    public static int carriedArrows(FolkData f)
    {
        int n = 0;
        for (ItemStack is : f.inventory)
        {
            if (is != null && is.getItem() == Items.arrow)
            {
                n += is.stackSize;
            }
        }
        return n;
    }

    /** one arrow gone; false if there wasn't one */
    public static boolean useArrow(FolkData f)
    {
        for (Iterator<ItemStack> it = f.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            if (is != null && is.getItem() == Items.arrow && is.stackSize > 0)
            {
                if (--is.stackSize <= 0)
                {
                    it.remove();
                }
                return true;
            }
        }
        return false;
    }

    /** a weapon that's worn out is thrown away */
    public static void dropBroken(FolkData f)
    {
        for (Iterator<ItemStack> it = f.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            if (is == null || is.stackSize <= 0)
            {
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- the armory

    /**
     * at the barracks: swap for the best gear the armory has that this soldier's rank allows, and top up arrows.
     * Returns a line saying what changed, or null if nothing did.
     */
    public static String equip(FolkData f, World world)
    {
        Building barracks = Building.getBuilding(f.employedAt);
        if (world == null || f.employedAt == null)
        {
            return null;
        }
        List<IInventory> chests = WarehouseStock.chestsInside(barracks, f.employedAt, world);
        if (chests.isEmpty())
        {
            return null;
        }
        Rank rank = Rank.of(f.levelSoldier);
        List<String> changes = new ArrayList<String>();

        // anything they can't use any more (or never could) goes back
        for (Iterator<ItemStack> it = f.inventory.iterator(); it.hasNext();)
        {
            ItemStack is = it.next();
            if (is != null && is.getItem() != Items.arrow && !allowed(rank, is))
            {
                if (WarehouseStock.put(chests, is) == null)
                {
                    it.remove();
                }
            }
        }

        ItemStack weapon = carriedWeapon(f);
        float have = weapon == null ? meleeScore(standardIssue()) : meleeScore(weapon);
        int[] best = find(chests, rank, false);
        if (best != null)
        {
            ItemStack candidate = chests.get(best[0]).getStackInSlot(best[1]);
            if (meleeScore(candidate) > have)
            {
                ItemStack taken = chests.get(best[0]).decrStackSize(best[1], 1);
                if (weapon != null)
                {
                    f.inventory.remove(weapon);
                    WarehouseStock.put(chests, weapon);
                }
                f.inventory.add(taken);
                changes.add("took a " + taken.getDisplayName());
            }
        }

        if (rank.atLeast(Rank.ARCHER))
        {
            ItemStack bow = carriedBow(f);
            int[] bestBow = find(chests, rank, true);
            if (bestBow != null)
            {
                ItemStack candidate = chests.get(bestBow[0]).getStackInSlot(bestBow[1]);
                if (bow == null || bowScore(candidate) > bowScore(bow))
                {
                    ItemStack taken = chests.get(bestBow[0]).decrStackSize(bestBow[1], 1);
                    if (bow != null)
                    {
                        f.inventory.remove(bow);
                        WarehouseStock.put(chests, bow);
                    }
                    f.inventory.add(taken);
                    changes.add("took a " + taken.getDisplayName());
                }
            }
            int want = ARROWS_CARRIED - carriedArrows(f);
            if (want > 0 && carriedBow(f) != null)
            {
                int got = 0;
                for (ItemStack arrows : WarehouseStock.take(chests, Items.arrow, want))
                {
                    f.inventory.add(arrows);
                    got += arrows.stackSize;
                }
                if (got > 0)
                {
                    changes.add("took " + got + " arrows");
                }
            }
        }
        if (changes.isEmpty())
        {
            return null;
        }
        SimukraftReloaded.log.info("Armory: " + f.name + " (" + rank.title + ") " + String.join(", ", changes));
        return String.join(", ", changes);
    }

    /** chest index and slot of the best weapon (or bow) for this rank, or null */
    private static int[] find(List<IInventory> chests, Rank rank, boolean bows)
    {
        int[] best = null;
        float bestScore = 0;
        for (int c = 0; c < chests.size(); c++)
        {
            IInventory inv = chests.get(c);
            for (int s = 0; s < inv.getSizeInventory(); s++)
            {
                ItemStack is = inv.getStackInSlot(s);
                if (is == null || is.getItem() == null || isBow(is) != bows || !allowed(rank, is))
                {
                    continue;
                }
                float score = bows ? bowScore(is) : meleeScore(is);
                if (score > bestScore)
                {
                    bestScore = score;
                    best = new int[] {c, s};
                }
            }
        }
        return best;
    }
}
