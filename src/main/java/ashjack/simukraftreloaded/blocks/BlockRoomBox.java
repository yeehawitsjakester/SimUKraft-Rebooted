package ashjack.simukraftreloaded.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiRoomBox;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedTabs;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Turns the room it's placed in into a home of its own: an apartment for permanent tenants,
 * or a hotel room for short stays. Metadata 0 = apartment, 1 = hotel (for the door texture).
 */
public class BlockRoomBox extends Block
{
    public BlockRoomBox()
    {
        super(Material.wood);
        setHardness(2F);
        setResistance(5F);
        setStepSound(soundTypeWood);
        setBlockName("SUKroombox");
        setCreativeTab(SimukraftReloadedTabs.SUKTab);
    }

    @SideOnly(Side.CLIENT)
    private IIcon top, side, sideHotel;

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg)
    {
        top = reg.registerIcon("ashjacksimukraftreloaded:blockRoomBoxTop");
        side = reg.registerIcon("ashjacksimukraftreloaded:blockRoomBoxSide");
        sideHotel = reg.registerIcon("ashjacksimukraftreloaded:blockRoomBoxSideHotel");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int face, int meta)
    {
        if (face == 0 || face == 1)
        {
            return top;
        }
        return meta == 1 ? sideHotel : side;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack)
    {
        if (!world.isRemote)
        {
            Building room = Building.createRoom(world, x, y, z);
            if (placer instanceof EntityPlayer)
            {
                ((EntityPlayer) placer).addChatComponentMessage(new ChatComponentText(
                        room.displayNameWithoutPK + " is ready for tenants - right-click it to make it a hotel room or change its size"));
            }
        }
    }

    /** however the box goes, the room goes with it (tenants are told and look for a new home) */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta)
    {
        if (!world.isRemote)
        {
            Building room = Building.getBuilding(new V3((double) x, (double) y, (double) z, world.provider.dimensionId));
            if (room != null && room.isRoom())
            {
                Building.removeBuilding(room, "room box broken");
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz)
    {
        if (world.isRemote)
        {
            openGui(new V3((double) x, (double) y, (double) z, world.provider.dimensionId));
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    private void openGui(V3 box)
    {
        Minecraft.getMinecraft().displayGuiScreen(new GuiRoomBox(box));
    }
}
