package ashjack.simukraftreloaded.client.Gui.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/** what a warehouse has in stock and where, and the deliveries under way (sent by the keeper, see JobWarehouse) */
public class GuiWarehouse extends GuiScreen
{
    private static final String[] COLUMNS = {"Warehouse", "Mines", "Leftovers", "ME"};
    private static final int ROW = 18, TOP = 62;

    private final V3 location;
    private int scroll;

    private static class Row
    {
        ItemStack stack;
        String name;
        int[] counts;
        int total;
    }

    public GuiWarehouse(V3 location)
    {
        this.location = location;
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void initGui()
    {
        buttonList.clear();
        buttonList.add(new GuiButton(0, 5, 5, 50, 20, "Done"));
        buttonList.add(new GuiButton(1, 57, 5, 50, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton b)
    {
        if (b.id == 0)
        {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
        else if (b.id == 1)
        {
            mc.displayGuiScreen(new GuiControlBox(location, (net.minecraft.entity.player.EntityPlayer) mc.thePlayer));
        }
    }

    /** the keeper with the newest stock list */
    private FolkData keeper()
    {
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.folks()))
        {
            if (f.vocation == Vocation.WAREHOUSE && f.employedAt != null && f.employedAt.isSameCoordsAs(location, true, true)
                    && f.warehouseStock != null && !f.warehouseStock.isEmpty())
            {
                return f;
            }
        }
        return null;
    }

    @Override
    public void drawScreen(int mx, int my, float partial)
    {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Warehouse Stock", width / 2, 12, 0xffffff);

        FolkData k = keeper();
        if (k == null)
        {
            drawCenteredString(fontRendererObj, "Hire a warehouse keeper: they take stock when they get to work.", width / 2, height / 2, 0xffffa0);
            super.drawScreen(mx, my, partial);
            return;
        }

        List<Row> rows = new ArrayList<Row>();
        String summary = "";
        for (String line : k.warehouseStock)
        {
            String[] p = line.split("\\|");
            if (p[0].equals("#") && p.length >= 3)
            {
                int me = Integer.parseInt(p[2]);
                summary = p[1] + " chests" + (me >= 0 ? ", ME network with " + me + " item types (building materials shown)" : ", no ME network");
                continue;
            }
            if (p.length < 2 + COLUMNS.length)
            {
                continue;
            }
            Item item = (Item) Item.itemRegistry.getObject(p[0]);
            if (item == null)
            {
                continue;
            }
            Row r = new Row();
            boolean anyMeta = p[1].equals("*");
            r.stack = new ItemStack(item, 1, anyMeta ? 0 : Integer.parseInt(p[1]));
            r.name = r.stack.getDisplayName() + (anyMeta ? " (any)" : "");
            r.counts = new int[COLUMNS.length];
            for (int i = 0; i < COLUMNS.length; i++)
            {
                r.counts[i] = Integer.parseInt(p[2 + i]);
                r.total += r.counts[i];
            }
            rows.add(r);
        }
        Collections.sort(rows, new Comparator<Row>()
        {
            public int compare(Row a, Row b)
            {
                return b.total - a.total;
            }
        });

        drawCenteredString(fontRendererObj, "Kept by " + k.name + " - " + summary, width / 2, 28, 0xa0a0a0);

        List<String> trips = k.warehouseTrips == null ? new ArrayList<String>() : k.warehouseTrips;
        int tripsHeight = 14 + Math.min(trips.size(), 4) * 10;
        int visible = Math.max(1, (height - TOP - tripsHeight - 10) / ROW);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));

        int nameX = 32, colW = 58, colX = width - 12 - colW * (COLUMNS.length + 1);
        for (int i = 0; i < COLUMNS.length; i++)
        {
            drawString(fontRendererObj, COLUMNS[i], colX + i * colW, TOP - 14, 0xffff80);
        }
        drawString(fontRendererObj, "Total", colX + COLUMNS.length * colW, TOP - 14, 0xffff80);
        drawString(fontRendererObj, "Item", nameX, TOP - 14, 0xffff80);

        if (rows.isEmpty())
        {
            drawCenteredString(fontRendererObj, "Nothing in stock yet", width / 2, TOP + 10, 0xa0a0a0);
        }
        String tooltip = null;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++)
        {
            Row r = rows.get(scroll + i);
            int y = TOP + i * ROW;
            if (i % 2 == 0)
            {
                drawRect(8, y - 1, width - 8, y + ROW - 1, 0x20ffffff);
            }
            drawItem(r.stack, 10, y);
            String name = fontRendererObj.trimStringToWidth(r.name, colX - nameX - 6);
            drawString(fontRendererObj, name, nameX, y + 4, 0xffffff);
            if (!name.equals(r.name) && my >= y && my < y + ROW && mx >= nameX && mx < colX)
            {
                tooltip = r.name;
            }
            for (int c = 0; c < COLUMNS.length; c++)
            {
                drawString(fontRendererObj, r.counts[c] == 0 ? "-" : String.valueOf(r.counts[c]), colX + c * colW, y + 4,
                        r.counts[c] == 0 ? 0x606060 : 0xe0e0e0);
            }
            drawString(fontRendererObj, String.valueOf(r.total), colX + COLUMNS.length * colW, y + 4, 0xffffff);
        }
        if (rows.size() > visible)
        {
            drawString(fontRendererObj, (scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " of " + rows.size() + " (scroll)",
                    10, TOP + visible * ROW + 1, 0x808080);
        }

        int ty = height - tripsHeight;
        drawString(fontRendererObj, trips.isEmpty() ? "No deliveries under way" : "Deliveries under way:", 10, ty, 0xffff80);
        for (int i = 0; i < trips.size() && i < 4; i++)
        {
            String[] p = trips.get(i).split("\\|");
            if (p.length < 5)
            {
                continue;
            }
            Item item = (Item) Item.itemRegistry.getObject(p[2]);
            String what = item == null ? p[2] : new ItemStack(item).getDisplayName();
            drawString(fontRendererObj, p[0] + ": " + p[1] + " " + what + " from " + p[4] + " to " + p[3], 18, ty + 12 + i * 10, 0xe0e0e0);
        }

        super.drawScreen(mx, my, partial);
        if (tooltip != null)
        {
            List<String> l = new ArrayList<String>();
            l.add(tooltip);
            func_146283_a(l, mx, my);
        }
    }

    private void drawItem(ItemStack stack, int x, int y)
    {
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    public void handleMouseInput()
    {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
        {
            scroll += wheel > 0 ? -3 : 3;
        }
    }
}
