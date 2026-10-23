package ashjack.simukraftreloaded.blocks;

import net.minecraft.util.ChatComponentText;
import net.minecraft.client.gui.GuiScreen;
import ashjack.simukraftreloaded.client.ClientGuiOpener;
import ashjack.simukraftreloaded.core.building.Building;
import java.util.Random;

import ashjack.simukraftreloaded.client.Gui.blocks.GuiBankATM;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiControlBox;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedTabs;
import ashjack.simukraftreloaded.proxies.CommonProxy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockControlBox extends Block
{
   

    public BlockControlBox()
    {
        super(Material.wood);
        setStepSound(Block.soundTypeWood);
        setHardness(10F);
        setResistance(1.0F);
        setBlockName("SUKcontrol");
		this.setCreativeTab(SimukraftReloadedTabs.SUKTab);
    }

   
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    
     
    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister par1IconRegister)
    {
    icons = new IIcon[5];
    icons[0] = par1IconRegister.registerIcon("ashjacksimukraftreloaded:blockControlTop");
    icons[1] = par1IconRegister.registerIcon("ashjacksimukraftreloaded:blockControlSide");
    icons[2] = par1IconRegister.registerIcon("ashjacksimukraftreloaded:blockATM");
    icons[3] = par1IconRegister.registerIcon("ashjacksimukraftreloaded:blockControlTopOther");
    }
     
    /*for (int i = 0; i < icons.length; i++)
    {
    icons[i] = par1IconRegister.registerIcon(ModSimukraft.modid + ":" + (this.getUnlocalizedName().substring(5)) + i);
    }
    }*/
     
    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int par1, int par2)
    {
    switch (par2)
    {
    case 0:
    return icons[0];
    case 1:
    {
    switch (par1)
    {
    case 0:
    return icons[1];
    case 1:
    return icons[2];
    case 84:
    return icons[0];
    default:
    return icons[3];
    }
    }
    default:
    {
    //System.out.println("Invalid metadata for " + this.getUnlocalizedName());
    return icons[0];
    }
    }
    }

    @Override
    @SideOnly(Side.CLIENT)
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
    	final boolean isBank = !(world.getBlockMetadata(i, j, k) == 0 || world.getBlockMetadata(i, j, k) == 2);

    	if (isBank && GameMode.gameMode == GameMode.GAMEMODES.CREATIVE)
    	{
    		player.addChatComponentMessage(new ChatComponentText("The Bank is not active when in Creative Mode (as there's no money!)"));
    		return true;
    	}
    	ClientGuiOpener.open(new ClientGuiOpener.Factory()
    	{
    		public GuiScreen create()
    		{
    			return isBank ? new GuiBankATM(loc, player) : new GuiControlBox(loc, player);
    		}
    	});
        return true;
    }

    /** however the control box goes (player, explosion, demolish), the building goes with it */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta)
    {
        if (!world.isRemote)
        {
            Building building = Building.getBuilding(new V3((double) x, (double) y, (double) z, world.provider.dimensionId));
            Building.removeBuilding(building, "control box broken");
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public int quantityDropped(Random random)
    {
        return 0;   // no recipe and no drop
    }
}
