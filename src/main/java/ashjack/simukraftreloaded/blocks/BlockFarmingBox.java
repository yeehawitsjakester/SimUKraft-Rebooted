package ashjack.simukraftreloaded.blocks;

import net.minecraft.util.ChatComponentText;
import net.minecraft.client.gui.GuiScreen;
import ashjack.simukraftreloaded.client.ClientGuiOpener;
import ashjack.simukraftreloaded.blocks.functionality.FarmingBox;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiFarming;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedTabs;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockFarmingBox extends Block
{
    private IIcon icons[];

    public BlockFarmingBox()
    {
        super(Material.wood);
        setStepSound(Block.soundTypeWood);
        setHardness(2F);
        setResistance(1.0F);
        setBlockName("SUKfarming");
		this.setCreativeTab(SimukraftReloadedTabs.SUKTab);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister)
    {
        icons = new IIcon[1];
        icons[0] = iconRegister.registerIcon("ashjacksimukraftreloaded:blockFarming");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta)
    {
        return icons[0];
    }

    @Override
    public void onBlockDestroyedByPlayer(World par1World, int par2,
                                         int par3, int par4, int par5)
    {
        if (par1World.isRemote)
        {
            super.onBlockDestroyedByPlayer(par1World, par2, par3, par4, par5);
            return; // the client only has a copy of the folks and boxes
        }
        for (FolkData theFolk : FolkData.getFolksByEmployedAt(new V3((double)par2, (double)par3, (double)par4
                           , par1World.provider.dimensionId)))
        {
            theFolk.selfFire();
        }

        FarmingBox m;
        m = FarmingBox.getFarmingBlockByBoxXYZ(new V3((double)par2, (double)par3, (double)par4, par1World.provider.dimensionId));
        SimukraftReloaded.farmingBoxes().remove(m);
        par1World.playSoundEffect(par2, par3, par4, "ashjacksimukraftreloaded:powerdown", 1f, 1f);
        super.onBlockDestroyedByPlayer(par1World, par2, par3, par4, par5);
    }

    @Override
    public void onBlockPlacedBy(World par1World, int par2, int par3, int par4, EntityLivingBase player, ItemStack is)
    {
    	if (par1World.isRemote)
    	{
    		return; // farming boxes are created on the server
    	}
    	if (BlockMarker.markers().isEmpty()) {
        	SimukraftReloaded.sendChat("You need to place down 3 markers first to mark out the farming area");
        	return;
        }
    	if (BlockMarker.markers().size() != 3) {
        	SimukraftReloaded.sendChat("You need to place down 3 markers, not "+BlockMarker.markers().size());
        	return;
        }
    	FarmingBox m;
    	SimukraftReloaded.farmingBoxes().add(m = new FarmingBox(new V3((double)par2, (double)par3, (double)par4, par1World.provider.dimensionId)));

        try
        {
            int first = BlockMarker.markers().size() - 3;
            m.marker1XYZ = BlockMarker.markers().get(first).toV3();
            m.marker2XYZ = BlockMarker.markers().get(first + 1).toV3();
            m.marker3XYZ = BlockMarker.markers().get(first + 2).toV3();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        super.onBlockAdded(par1World, par2, par3, par4);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean onBlockActivated(World world, int i, int j, int k,
                                    EntityPlayer entityplayer, int par6, float par7, float par8,
                                    float par9)
    {
        if (!world.isRemote)
        {
            world.playSoundEffect(i, j, k, "ashjacksimukraftreloaded:computer", 1f, 1f);
            return true;
        }
        final V3 box = new V3((double) i, (double) j, (double) k, entityplayer.dimension);
        final EntityPlayer player = entityplayer;
        ClientGuiOpener.open(new ClientGuiOpener.Factory()
        {
            public GuiScreen create()
            {
                FarmingBox farmingBlock = FarmingBox.getFarmingBlockByBoxXYZ(box);
                if (farmingBlock == null)
                {
                    player.addChatComponentMessage(new ChatComponentText("Sorry, there was a problem with this farming box, try replacing it."));
                    return null;
                }
                return new GuiFarming(farmingBlock, FolkData.getFolkByEmployedAt(box));
            }
        });
        return true;
    }
}
