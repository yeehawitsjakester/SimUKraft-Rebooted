package ashjack.simukraftreloaded.client.Gui.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;

/**
 * Draws a blueprint as a small 3D model in a GUI, using the normal block renderers. The blueprint stands in for the
 * world (IBlockAccess), so fences connect, stairs shape and hidden faces are skipped like they would be once built.
 * Layer 0 is the bottom; ltr runs along x and ftb along z, as JobBuilder places them before rotating.
 */
public class BuildingPreview implements IBlockAccess
{
    private final Block[] structure;
    private final int[] meta;
    private final int sx, sy, sz;
    /** blocks that throw while rendering (modded renderers expecting a real World) are skipped from then on */
    private final boolean[] broken;

    public float yaw = 45f;

    public BuildingPreview(Building b)
    {
        structure = b.structure;
        meta = b.structureSub;
        sx = b.ltrCount;
        sy = b.layerCount;
        sz = b.ftbCount;
        broken = new boolean[structure == null ? 0 : structure.length];
    }

    public boolean isEmpty()
    {
        return structure == null || sx * sy * sz == 0 || structure.length < sx * sy * sz;
    }

    /** draw centred in the box left,top,width,height (GUI coordinates) */
    public void draw(int left, int top, int width, int height)
    {
        if (isEmpty())
        {
            return;
        }
        float span = Math.max((float) Math.sqrt(sx * sx + sz * sz), sy * 1.3f);
        float scale = Math.min(width, height) / Math.max(span, 1f);

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        RenderHelper.disableStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE); // the y flip below turns faces inside out
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // in front of the screen background, and deep enough for the whole model
        GL11.glTranslatef(left + width / 2f, top + height / 2f, 400f);
        GL11.glScalef(scale, -scale, scale);
        GL11.glRotatef(25f, 1f, 0f, 0f);
        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glTranslatef(-sx / 2f, -sy / 2f, -sz / 2f);

        RenderBlocks rb = new RenderBlocks(this);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int y = 0; y < sy; y++)
        {
            for (int z = 0; z < sz; z++)
            {
                for (int x = 0; x < sx; x++)
                {
                    int i = index(x, y, z);
                    Block b = structure[i];
                    if (b == null || broken[i] || b.getMaterial() == net.minecraft.block.material.Material.air
                            || b == SimukraftReloadedBlocks.livingBlock)
                    {
                        continue;
                    }
                    try
                    {
                        rb.renderBlockByRenderType(b, x, y, z);
                    }
                    catch (Throwable e)
                    {
                        broken[i] = true;
                    }
                }
            }
        }
        t.draw();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private int index(int x, int y, int z)
    {
        return y * sx * sz + z * sx + x;
    }

    private boolean inside(int x, int y, int z)
    {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }

    // ---------------------------------------------------------------- the blueprint as a world

    @Override
    public Block getBlock(int x, int y, int z)
    {
        if (!inside(x, y, z))
        {
            return Blocks.air;
        }
        Block b = structure[index(x, y, z)];
        return b == null || b == SimukraftReloadedBlocks.livingBlock ? Blocks.air : b;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z)
    {
        return null;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minBlockLight)
    {
        return 15 << 20 | 15 << 4; // full daylight everywhere
    }

    @Override
    public int getBlockMetadata(int x, int y, int z)
    {
        return inside(x, y, z) ? meta[index(x, y, z)] : 0;
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side)
    {
        return 0;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z)
    {
        return getBlock(x, y, z).getMaterial() == net.minecraft.block.material.Material.air;
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z)
    {
        return BiomeGenBase.plains;
    }

    @Override
    public int getHeight()
    {
        return 256;
    }

    @Override
    public boolean extendedLevelsInChunkCache()
    {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default)
    {
        return inside(x, y, z) ? getBlock(x, y, z).isSideSolid(this, x, y, z, side) : _default;
    }
}
