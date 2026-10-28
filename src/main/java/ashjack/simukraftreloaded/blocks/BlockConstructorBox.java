package ashjack.simukraftreloaded.blocks;

import ashjack.simukraftreloaded.network.Placement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import ashjack.simukraftreloaded.client.ClientGuiOpener;
import java.util.List;

import ashjack.simukraftreloaded.client.Gui.blocks.GuiBuildingConstructor;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedTabs;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockConstructorBox extends Block
{

    /** string containing +x -x +z -z  */
    public String buildDirection = "";

    /** int is the Block Id */
    public BlockConstructorBox()
    {
        super(Material.wood);
        setStepSound(Block.soundTypeWood);
        setHardness(2F);
        setResistance(1.0F);
        setBlockName("SUKconstructorBox");
		this.setCreativeTab(SimukraftReloadedTabs.SUKTab);
    }
    
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister par1IconRegister)
    {
        icons = new IIcon[1];
        icons[0] = par1IconRegister.registerIcon("ashjacksimukraftreloaded:blockConstruction");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta)      // getBlockTextureFromSideAndMetadata
    {
        return icons[0];
    }

    
    
    
    
    
    
    @Override
    public void onBlockDestroyedByPlayer(World par1World, int par2, int par3,
                                         int par4, int par5)
    {
        if (!par1World.isRemote)
        {
            par1World.playSoundEffect(par2, par3, par4, "ashjacksimukraftreloaded:powerdown", 1f, 1f);
        }

        if (!par1World.isRemote) // the client only has a copy of the folks
        {
            for (FolkData theFolk : FolkData.getFolksByEmployedAt(new V3((double)par2, (double)par3, (double)par4
                               , par1World.provider.dimensionId)))
            {
                theFolk.selfFire();
            }
        }

        super.onBlockDestroyedByPlayer(par1World, par2, par3, par4, par5);
    }

    /** however it goes (broken, blown up, replaced): a building waiting to be placed here goes with it */
    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta)
    {
        if (!world.isRemote)
        {
            ashjack.simukraftreloaded.network.Placement.boxGone(new V3((double) x, (double) y, (double) z, world.provider.dimensionId));
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public void onBlockAdded(World par1World, int par2, int par3, int par4)
    {
        if (!par1World.isRemote)
        {
            par1World.playSoundEffect(par2, par3, par4, "ashjacksimukraftreloaded:constructoractivated", 1f, 1f);
        }

        super.onBlockAdded(par1World, par2, par3, par4);
    }

    // the server has to run this too, or it doesn't know the click was handled and uses the held item on the box
    @Override
    public boolean onBlockActivated(World par1World, int par2, int par3,
                                    int par4, EntityPlayer thePlayer, int par6, float par7,
                                    float par8, float par9)
    {
        V3 here = new V3((double) par2, (double) par3, (double) par4, par1World.provider.dimensionId);
        if (thePlayer.isSneaking())
        {
            // shift-right-click: the go-ahead for a building waiting to be placed here
            if (!par1World.isRemote && thePlayer instanceof EntityPlayerMP)
            {
                if (Placement.confirm((EntityPlayerMP) thePlayer, here))
                {
                    par1World.playSoundEffect(par2, par3, par4, "ashjacksimukraftreloaded:constructoractivated", 1f, 1f);
                }
            }
            return true;
        }
        if (!par1World.isRemote)
        {
            par1World.playSoundEffect(par2, par3, par4, "ashjacksimukraftreloaded:computer", 1f, 1f);
            return true;
        }
        int px = (int) Math.floor(thePlayer.posX);
        int py = (int) Math.floor(thePlayer.posY);
        int pz = (int) Math.floor(thePlayer.posZ);
        int ex = par2;
        int ey = par3;
        int ez = par4;

        if (par4 == pz)  //first block should be on X axis
        {
            if (px < par2)
            {
                buildDirection = "-x";
            }
            else
            {
                buildDirection = "+x";
            }
        }
        else if (par2 == px)    //first block should be on Z axis
        {
            if (pz < par4)
            {
                buildDirection = "-z";
            }
            else
            {
                buildDirection = "+z";
            }
        }

        final V3 loc = new V3((double)par2, (double)par3, (double)par4, thePlayer.dimension);
        final String dir = buildDirection;
        ClientGuiOpener.open(new ClientGuiOpener.Factory()
        {
            public GuiScreen create()
            {
                return new GuiBuildingConstructor(loc, dir, null); // xyz of box and xyz of 1st build block
            }
        });
        return true;
    }
}
