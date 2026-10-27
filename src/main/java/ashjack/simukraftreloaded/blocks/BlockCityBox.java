package ashjack.simukraftreloaded.blocks;

import ashjack.simukraftreloaded.core.registry.SimukraftReloadedTabs;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.core.town.Town;
import net.minecraft.client.gui.GuiScreen;
import ashjack.simukraftreloaded.client.ClientGuiOpener;
import java.util.Random;

import ashjack.simukraftreloaded.client.Gui.blocks.GuiBankATM;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiCityBox;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiControlBox;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockCityBox extends Block
{
	@SideOnly(Side.CLIENT)
    private IIcon[] icons;
	
	public BlockCityBox() 
	 {
		super(Material.wood);
		setStepSound(Block.soundTypeWood);
        setHardness(10F);
        setResistance(1.0F);
        setBlockName("SUKcitybox");
        setCreativeTab(SimukraftReloadedTabs.SUKTab);
	}
	 
	 @SideOnly(Side.CLIENT)
	  @Override
	    public void registerBlockIcons(IIconRegister iconRegister)
	    {
	        icons = new IIcon[1];
	        icons[0] = iconRegister.registerIcon("ashjacksimukraftreloaded:blockPath");
	    }

	    @Override
	    @SideOnly(Side.CLIENT)
	    public IIcon getIcon(int side, int meta)      // getBlockTextureFromSideAndMetadata
	    {
	        return icons[0];
	    }
	    
	    
	    // the server has to run this too, or it doesn't know the click was handled and uses the held item on the box
	    @Override
	    public boolean onBlockActivated(World world, int i, int j, int k, EntityPlayer entityplayer,
	                                    int par6, float par7, float par8, float par9)
	    {
	        if (!world.isRemote)
	        {
	            world.playSoundEffect(i, j, k, "ashjacksimukraftreloaded:computer", 1f, 1f);
	            return true;
	        }
	        final V3 loc = new V3((double)i, (double)j, (double)k, entityplayer.dimension);
	        final EntityPlayer player = entityplayer;
	        ClientGuiOpener.open(new ClientGuiOpener.Factory()
	        {
	            public GuiScreen create()
	            {
	                return new GuiCityBox(loc, player);
	            }
	        });
	        return true;
	    }

	    /**
	     * the centre of a town can't be broken (or a town lost by a stray pickaxe): its owner disbands the town from
	     * the City Box screen, which gives the box back
	     */
	    @Override
	    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z)
	    {
	        Town town = Towns.at(world.provider.dimensionId, x, z);
	        if (town != null && town.centre != null && town.centre.x.intValue() == x && town.centre.y.intValue() == y
	                && town.centre.z.intValue() == z)
	        {
	            return 0F;
	        }
	        return super.getPlayerRelativeBlockHardness(player, world, x, y, z);
	    }

}
