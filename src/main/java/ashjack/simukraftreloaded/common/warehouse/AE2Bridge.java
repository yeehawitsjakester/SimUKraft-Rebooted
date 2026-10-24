package ashjack.simukraftreloaded.common.warehouse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;

/**
 * Everything that touches Applied Energistics 2. Only call this when {@link WarehouseStock#hasAE2()} says AE2 is
 * installed: without it, loading this class fails. Networks are passed around as Object so callers never name AE2
 * types.
 */
final class AE2Bridge
{
    private AE2Bridge() {}

    private static final BaseActionSource SOURCE = new BaseActionSource();

    /** the item storage of a powered ME network with a cable, terminal, drive... somewhere in the box, or null */
    static Object findNetwork(World w, int x0, int y0, int z0, int x1, int y1, int z1)
    {
        for (int y = y0; y <= y1; y++)
        {
            for (int x = x0; x <= x1; x++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (!w.blockExists(x, y, z))
                    {
                        continue;
                    }
                    TileEntity te = w.getTileEntity(x, y, z);
                    if (!(te instanceof IGridHost))
                    {
                        continue;
                    }
                    IGridNode node = nodeOf((IGridHost) te);
                    if (node != null && node.isActive() && node.getGrid() != null)
                    {
                        IStorageGrid storage = node.getGrid().getCache(IStorageGrid.class);
                        if (storage != null)
                        {
                            return storage.getItemInventory();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static IGridNode nodeOf(IGridHost host)
    {
        IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
        for (int i = 0; node == null && i < ForgeDirection.VALID_DIRECTIONS.length; i++)
        {
            node = host.getGridNode(ForgeDirection.VALID_DIRECTIONS[i]);
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static IMEMonitor<IAEItemStack> inv(Object network)
    {
        return (IMEMonitor<IAEItemStack>) network;
    }

    /** how many of an item (any meta, no NBT) the network holds */
    static long count(Object network, Item item)
    {
        long n = 0;
        for (IAEItemStack s : inv(network).getStorageList())
        {
            if (s.getItem() == item && !s.hasTagCompound())
            {
                n += s.getStackSize();
            }
        }
        return n;
    }

    /** how many different items are stored */
    static int typeCount(Object network)
    {
        return inv(network).getStorageList().size();
    }

    /** take up to qty of an item (any meta, no NBT) out of the network, as normal-sized stacks */
    static List<ItemStack> extract(Object network, Item item, int qty)
    {
        List<ItemStack> out = new ArrayList<ItemStack>();
        IMEMonitor<IAEItemStack> inv = inv(network);
        List<IAEItemStack> matches = new ArrayList<IAEItemStack>();
        for (IAEItemStack s : inv.getStorageList())
        {
            if (s.getItem() == item && !s.hasTagCompound() && s.getStackSize() > 0)
            {
                matches.add(s.copy());
            }
        }
        for (IAEItemStack s : matches)
        {
            if (qty <= 0)
            {
                break;
            }
            IAEItemStack got = inv.extractItems(s.setStackSize(Math.min(qty, s.getStackSize())), Actionable.MODULATE, SOURCE);
            if (got == null || got.getStackSize() <= 0)
            {
                continue;
            }
            qty -= (int) got.getStackSize();
            ItemStack is = got.getItemStack();
            int left = (int) got.getStackSize();
            while (left > 0)
            {
                ItemStack part = is.copy();
                part.stackSize = Math.min(left, is.getMaxStackSize());
                left -= part.stackSize;
                out.add(part);
            }
        }
        return out;
    }

    /** put a stack into the network; returns what didn't fit, or null */
    static ItemStack insert(Object network, ItemStack stack)
    {
        IAEItemStack left = inv(network).injectItems(AEApi.instance().storage().createItemStack(stack), Actionable.MODULATE, SOURCE);
        return left == null || left.getStackSize() <= 0 ? null : left.getItemStack();
    }
}
