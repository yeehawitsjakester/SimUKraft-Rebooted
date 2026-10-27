package ashjack.simukraftreloaded.client;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.core.building.Building;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where a building waiting for the go-ahead will go (Placement): its outline as a wireframe box, green if it can go
 * there and red if it can't, a red cross-box on each block it would clear away (some of them) and a thick red box on
 * each other building's box it would destroy. Drawn faintly through terrain too, so the far side shows.
 */
@SideOnly(Side.CLIENT)
public class PlacementRenderer
{
    private static final double RANGE = 160;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null)
        {
            return;
        }
        float pt = event.partialTicks;
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;

        boolean begun = false;
        for (Building b : new ArrayList<Building>(ClientMirror.buildings))
        {
            int[] f = b.placementBounds;
            if (b.awaitingAt == null || f == null || f.length < 6 || dimensionOf(b.awaitingAt) != p.dimension
                    || Math.abs(px - (f[0] + f[3]) / 2.0) > RANGE || Math.abs(pz - (f[2] + f[5]) / 2.0) > RANGE)
            {
                continue;
            }
            if (!begun)
            {
                GL11.glPushMatrix();
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
                GL11.glTranslated(-px, -py, -pz);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false);
                begun = true;
            }
            float r = b.placementBlocked ? 1f : 0.3f, g = b.placementBlocked ? 0.25f : 1f, bl = 0.3f;
            // twice: faint through anything in the way, then solid where it's in view
            for (int pass = 0; pass < 2; pass++)
            {
                boolean seen = pass == 1;
                if (seen)
                {
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
                else
                {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                }
                float a = seen ? 0.9f : 0.25f;
                GL11.glLineWidth(seen ? 3f : 2f);
                GL11.glColor4f(r, g, bl, a);
                box(f[0], f[1], f[2], f[3] + 1, f[4] + 1, f[5] + 1);
                // a line every four blocks along the ground, so the footprint reads at a glance
                GL11.glLineWidth(1f);
                GL11.glColor4f(r, g, bl, a * 0.5f);
                grid(f[0], f[1] + 0.02, f[2], f[3] + 1, f[5] + 1);

                if (b.placementCleared != null)
                {
                    GL11.glLineWidth(seen ? 2f : 1f);
                    GL11.glColor4f(1f, 0.35f, 0.2f, seen ? 0.7f : 0.2f);
                    for (int[] c : b.placementCleared)
                    {
                        box(c[0] + 0.1, c[1] + 0.1, c[2] + 0.1, c[0] + 0.9, c[1] + 0.9, c[2] + 0.9);
                    }
                }
                if (b.placementBoxes != null)
                {
                    GL11.glLineWidth(seen ? 5f : 3f);
                    GL11.glColor4f(1f, 0.1f, 0.1f, seen ? 1f : 0.5f);
                    for (int[] c : b.placementBoxes)
                    {
                        box(c[0] - 0.05, c[1] - 0.05, c[2] - 0.05, c[0] + 1.05, c[1] + 1.05, c[2] + 1.05);
                    }
                }
            }
        }
        if (begun)
        {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static int dimensionOf(String v3)
    {
        try
        {
            return Integer.parseInt(v3.substring(v3.lastIndexOf(',') + 1).trim());
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /** the twelve edges of a box */
    private static void box(double x0, double y0, double z0, double x1, double y1, double z1)
    {
        GL11.glBegin(GL11.GL_LINES);
        double[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges)
        {
            GL11.glVertex3d(c[e[0]][0], c[e[0]][1], c[e[0]][2]);
            GL11.glVertex3d(c[e[1]][0], c[e[1]][1], c[e[1]][2]);
        }
        GL11.glEnd();
    }

    private static void grid(double x0, double y, double z0, double x1, double z1)
    {
        GL11.glBegin(GL11.GL_LINES);
        for (double x = x0 + 4; x < x1; x += 4)
        {
            GL11.glVertex3d(x, y, z0);
            GL11.glVertex3d(x, y, z1);
        }
        for (double z = z0 + 4; z < z1; z += 4)
        {
            GL11.glVertex3d(x0, y, z);
            GL11.glVertex3d(x1, y, z);
        }
        GL11.glEnd();
    }
}
