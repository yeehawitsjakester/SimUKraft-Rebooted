package ashjack.simukraftreloaded.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Town borders as a faint wall, fading in as you get near one (or always, switched on from a City Box): blue for your
 * own towns, orange for other people's, grey for unclaimed ones. Only the stretch near you is drawn. Crossing a border
 * says which town you're entering or leaving, above the hotbar.
 */
@SideOnly(Side.CLIENT)
public class TownBorderRenderer
{
    public static boolean alwaysShow = false;

    /** how near a border has to be before it shows, and how much of it is drawn either side of you */
    private static final double SHOW = 20, SPAN = 48;

    private int lastTown = -1;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || ClientMirror.towns.isEmpty())
        {
            return;
        }
        float pt = event.partialTicks;
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;

        boolean begun = false;
        for (Town t : ClientMirror.towns)
        {
            if (t.centre == null || t.dimension() != p.dimension)
            {
                continue;
            }
            double x0 = t.centre.x.intValue() - t.radius, x1 = t.centre.x.intValue() + t.radius + 1;
            double z0 = t.centre.z.intValue() - t.radius, z1 = t.centre.z.intValue() + t.radius + 1;
            double d = distanceToBorder(px, pz, x0, x1, z0, z1);
            if (!alwaysShow && d > SHOW)
            {
                continue;
            }
            float alpha = alwaysShow ? 0.22f : (float) (0.35 * (1 - d / SHOW));
            float r, g, b;
            if (!t.isClaimed())
            {
                r = 0.8f; g = 0.8f; b = 0.8f;
            }
            else if (t.isMember(p.getCommandSenderName()))
            {
                r = 0.3f; g = 0.75f; b = 1.0f;
            }
            else
            {
                r = 1.0f; g = 0.6f; b = 0.2f;
            }
            if (!begun)
            {
                begin(px, py, pz);
                begun = true;
            }
            double y0 = py - 12, y1 = py + 28;
            wall(x0, z0, x1, z0, px, pz, y0, y1, r, g, b, alpha);
            wall(x0, z1, x1, z1, px, pz, y0, y1, r, g, b, alpha);
            wall(x0, z0, x0, z1, px, pz, y0, y1, r, g, b, alpha);
            wall(x1, z0, x1, z1, px, pz, y0, y1, r, g, b, alpha);
        }
        if (begun)
        {
            end();
        }
    }

    private static double distanceToBorder(double x, double z, double x0, double x1, double z0, double z1)
    {
        if (x >= x0 && x <= x1 && z >= z0 && z <= z1)
        {
            return Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
        }
        double dx = Math.max(Math.max(x0 - x, 0), x - x1), dz = Math.max(Math.max(z0 - z, 0), z - z1);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void begin(double px, double py, double pz)
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
    }

    private static void end()
    {
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /** the part of one side near the player: a sheet, with a line every four blocks so it reads as a wall */
    private static void wall(double ax, double az, double bx, double bz, double px, double pz, double y0, double y1,
                             float r, float g, float b, float alpha)
    {
        boolean alongX = az == bz;
        double from = alongX ? Math.max(ax, px - SPAN) : Math.max(az, pz - SPAN);
        double to = alongX ? Math.min(bx, px + SPAN) : Math.min(bz, pz + SPAN);
        if (from >= to)
        {
            return;
        }
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(r, g, b, alpha);
        if (alongX)
        {
            t.addVertex(from, y0, az);
            t.addVertex(to, y0, az);
            t.addVertex(to, y1, az);
            t.addVertex(from, y1, az);
        }
        else
        {
            t.addVertex(ax, y0, from);
            t.addVertex(ax, y0, to);
            t.addVertex(ax, y1, to);
            t.addVertex(ax, y1, from);
        }
        t.draw();

        GL11.glLineWidth(1.5f);
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(r, g, b, Math.min(1f, alpha * 2.2f));
        for (double s = Math.ceil(from / 4) * 4; s <= to; s += 4)
        {
            if (alongX)
            {
                t.addVertex(s, y0, az);
                t.addVertex(s, y1, az);
            }
            else
            {
                t.addVertex(ax, y0, s);
                t.addVertex(ax, y1, s);
            }
        }
        for (double y = Math.ceil(y0 / 4) * 4; y <= y1; y += 4)
        {
            if (alongX)
            {
                t.addVertex(from, y, az);
                t.addVertex(to, y, az);
            }
            else
            {
                t.addVertex(ax, y, from);
                t.addVertex(ax, y, to);
            }
        }
        t.draw();
    }

    /** "Entering ..." / "Leaving ..." above the hotbar when you cross a border */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.ingameGUI == null)
        {
            return;
        }
        Town t = Towns.at(mc.thePlayer);
        int id = t == null ? 0 : t.id;
        if (id == lastTown)
        {
            return;
        }
        if (lastTown != -1)
        {
            if (t != null)
            {
                mc.ingameGUI.func_110326_a("Entering " + t.name + (t.isClaimed() ? " (" + t.owner + "'s town)" : " (unclaimed)"), false);
            }
            else
            {
                Town left = Towns.byId(lastTown);
                mc.ingameGUI.func_110326_a("Leaving " + (left == null ? "town" : left.name) + " - the wilds", false);
            }
        }
        lastTown = id;
    }
}
