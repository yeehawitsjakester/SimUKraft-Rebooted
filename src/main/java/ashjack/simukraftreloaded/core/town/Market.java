package ashjack.simukraftreloaded.core.town;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import ashjack.simukraftreloaded.common.PricesForBlocks;

/**
 * What things cost in a town, per item. Everything that changes hands for money asks here, so prices can follow
 * supply and demand in one place. For now it's each item's base price: building blocks from the merchant's list,
 * gems from the bank's, food by how filling it is, and a little for anything else.
 */
public final class Market
{
    private Market() {}

    private static final Map<Item, Float> BASE = new HashMap<Item, Float>();
    private static final float DEFAULT = 0.1f;

    private static void base(Item item, float price)
    {
        BASE.put(item, price);
    }

    private static void base(Block block, float price)
    {
        BASE.put(Item.getItemFromBlock(block), price);
    }

    private static boolean ready;

    private static void init()
    {
        if (ready)
        {
            return;
        }
        ready = true;
        // the merchant's blocks: its price for 64, per block
        for (Block b : new Block[] {Blocks.planks, Blocks.log, Blocks.cobblestone, Blocks.stone, Blocks.glass, Blocks.wool,
                Blocks.brick_block, Blocks.stonebrick, Blocks.fence})
        {
            try
            {
                base(b, PricesForBlocks.getPrice(b, false) / 64f);
            }
            catch (Exception e)
            {
                base(b, 0.05f);
            }
        }
        base(Items.diamond, PricesForBlocks.bankPriceDiamond);
        base(Items.emerald, PricesForBlocks.bankPriceEmerald);
        base(Items.redstone, PricesForBlocks.bankPriceRedstone);
        base(Items.glowstone_dust, PricesForBlocks.bankPriceGlowstone);
        base(Items.gold_ingot, PricesForBlocks.bankPriceGold);
        base(Items.iron_ingot, 1.5f);
        base(Items.coal, 0.12f);
        base(Blocks.sand, 0.02f);
        base(Blocks.dirt, 0.005f);
        base(Blocks.gravel, 0.01f);
        base(Blocks.brick_block, 0.08f);
        base(Items.brick, 0.02f);
        base(Items.wheat, 0.03f);
        base(Items.egg, 0.03f);
        base(Items.milk_bucket, 0.4f);
        base(Items.leather, 0.15f);
        base(Items.arrow, 0.05f);
        base(Items.bow, 1.0f);
        base(Items.iron_sword, 3.0f);
        base(Items.stone_sword, 0.3f);
    }

    /** the base price of one of these */
    public static float basePrice(ItemStack is)
    {
        init();
        if (is == null || is.getItem() == null)
        {
            return 0;
        }
        Float f = BASE.get(is.getItem());
        if (f != null)
        {
            return f;
        }
        if (is.getItem() instanceof ItemFood)
        {
            // food by how much it fills you: bread (5) about a quarter of a credit
            return Math.max(0.02f, ((ItemFood) is.getItem()).func_150905_g(is) * 0.05f);
        }
        return DEFAULT;
    }

    /** the price of one of these in a town (null for the wilds) */
    public static float price(Town town, ItemStack is)
    {
        return basePrice(is);
    }
}
