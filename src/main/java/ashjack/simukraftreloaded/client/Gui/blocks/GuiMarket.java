package ashjack.simukraftreloaded.client.Gui.blocks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.TownMarket;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.network.ActionHandlers;
import ashjack.simukraftreloaded.network.MarketActions;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A town's market, at its bank: everything it holds or wants, with today's price (and how it's moved since
 * yesterday), how many are in stock, how much they're wanted, and the last two weeks' prices. Pick one, then buy from
 * the town's stock or sell into its warehouse with your wallet.
 *
 * The Sell tab shows your inventory instead: hover over anything for what one fetches now and what the stack, and all
 * of it you're carrying, would fetch (each one sold lowers the price a little); click a stack to sell it, shift-click
 * to sell every one of that item you have.
 */
public class GuiMarket extends GuiScreen
{
    private final V3 atm;
    private int scroll = 0, selected = -1, seenSync = -1;
    private boolean selling;
    private static final int SLOT = 18;
    private List<String> rows = new ArrayList<String>();
    private static final int TOP = 48, ROW = 18;

    public GuiMarket(V3 atm)
    {
        this.atm = atm;
    }

    private Town town()
    {
        return Towns.at(atm);
    }

    private TownMarket market()
    {
        return TownMarket.of(town());
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
        buttonList.add(new GuiButton(30, width - 135, 5, 130, 20, "Sell what I'm holding"));
        buttonList.add(new GuiButton(40, 60, 5, 40, 20, "Buy"));
        buttonList.add(new GuiButton(41, 102, 5, 40, 20, "Sell"));
        int[] qty = {1, 8, 64};
        for (int i = 0; i < 3; i++)
        {
            buttonList.add(new GuiButton(10 + i, width / 2 - 155 + i * 52, height - 26, 50, 20, "Buy " + qty[i]));
            buttonList.add(new GuiButton(20 + i, width / 2 + 2 + i * 52, height - 26, 50, 20, "Sell " + qty[i]));
        }
        refresh();
    }

    private void refresh()
    {
        TownMarket m = market();
        rows = m == null ? new ArrayList<String>() : m.listed();
        boolean chosen = selected >= 0 && selected < rows.size();
        for (Object o : buttonList)
        {
            GuiButton b = (GuiButton) o;
            if (b.id >= 10 && b.id < 30)
            {
                b.enabled = chosen;
                b.visible = !selling;
            }
            if (b.id == 30)
            {
                b.visible = !selling;
            }
            if (b.id == 40 || b.id == 41)
            {
                b.enabled = selling == (b.id == 40); // the other tab
            }
        }
    }

    @Override
    public void updateScreen()
    {
        if (seenSync != ClientMirror.syncCount)
        {
            seenSync = ClientMirror.syncCount;
            refresh();
        }
    }

    @Override
    protected void actionPerformed(GuiButton b)
    {
        if (b.id == 0)
        {
            mc.displayGuiScreen(null);
            return;
        }
        if (b.id == 40 || b.id == 41)
        {
            selling = b.id == 41;
            refresh();
            return;
        }
        if (b.id == 30)
        {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null)
            {
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "atm", atm);
                data.setString("item", String.valueOf(Item.itemRegistry.getNameForObject(held.getItem())));
                data.setInteger("qty", held.stackSize);
                ServerActions.send("marketSell", data);
            }
            return;
        }
        if (selected < 0 || selected >= rows.size())
        {
            return;
        }
        int[] qty = {1, 8, 64};
        NBTTagCompound data = new NBTTagCompound();
        ActionHandlers.putV3(data, "atm", atm);
        data.setString("item", rows.get(selected));
        data.setInteger("qty", qty[(b.id % 10)]);
        ServerActions.send(b.id < 20 ? "marketBuy" : "marketSell", data);
    }

    @Override
    protected void mouseClicked(int x, int y, int button)
    {
        super.mouseClicked(x, y, button);
        if (selling)
        {
            int slot = slotAt(x, y);
            ItemStack is = slot < 0 ? null : mc.thePlayer.inventory.mainInventory[slot];
            if (is != null && button == 0)
            {
                boolean all = isShiftKeyDown();
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "atm", atm);
                data.setString("item", String.valueOf(Item.itemRegistry.getNameForObject(is.getItem())));
                data.setInteger("qty", all ? carried(is.getItem()) : is.stackSize);
                if (!all)
                {
                    data.setInteger("slot", slot);
                }
                ServerActions.send("marketSell", data);
            }
            return;
        }
        if (y >= TOP && y < height - 34 && x > 8 && x < width - 8)
        {
            int i = scroll + (y - TOP) / ROW;
            if (i < rows.size())
            {
                selected = i;
                refresh();
            }
        }
    }

    @Override
    public void handleMouseInput()
    {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w != 0)
        {
            scroll = Math.max(0, scroll + (w > 0 ? -1 : 1));
        }
    }

    // ---------------------------------------------------------------- the sell tab

    /** where the inventory grid starts: three rows of the bag, then the hotbar a little apart */
    private int gridX()
    {
        return width / 2 - 9 * SLOT / 2;
    }

    private int gridY()
    {
        return height / 2 - 30;
    }

    /** the inventory slot under the mouse, or -1 */
    private int slotAt(int x, int y)
    {
        int col = (x - gridX()) / SLOT;
        if (x < gridX() || col > 8)
        {
            return -1;
        }
        int dy = y - gridY();
        if (dy >= 0 && dy < 3 * SLOT)
        {
            return 9 + (dy / SLOT) * 9 + col;
        }
        int hot = dy - 3 * SLOT - 6;
        return hot >= 0 && hot < SLOT ? col : -1;
    }

    private int[] slotPos(int slot)
    {
        return slot < 9 ? new int[] {gridX() + slot * SLOT, gridY() + 3 * SLOT + 6}
                : new int[] {gridX() + (slot % 9) * SLOT, gridY() + ((slot - 9) / 9) * SLOT};
    }

    private int carried(Item item)
    {
        int n = 0;
        for (ItemStack is : mc.thePlayer.inventory.mainInventory)
        {
            if (is != null && is.getItem() == item)
            {
                n += is.stackSize;
            }
        }
        return n;
    }

    private void drawSelling(Town t, TownMarket m, int mx, int my)
    {
        drawCenteredString(fontRendererObj, "Hover for prices. Click a stack to sell it - shift-click to sell all of that item",
                width / 2, gridY() - 24, 0xa0a0a0);
        int hovered = -1;
        for (int slot = 0; slot < 36; slot++)
        {
            int[] p = slotPos(slot);
            boolean over = slotAt(mx, my) == slot;
            if (over)
            {
                hovered = slot;
            }
            drawRect(p[0], p[1], p[0] + SLOT - 1, p[1] + SLOT - 1, over ? 0x80ffffff : 0x40000000);
            drawRect(p[0] + 1, p[1] + 1, p[0] + SLOT - 2, p[1] + SLOT - 2, over ? 0x60a0a0a0 : 0x608b8b8b);
            ItemStack is = mc.thePlayer.inventory.mainInventory[slot];
            if (is != null)
            {
                drawItem(is, p[0] + 1, p[1] + 1);
                GL11.glDisable(GL11.GL_LIGHTING);
                itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), is, p[0] + 1, p[1] + 1);
            }
        }
        ItemStack is = hovered < 0 ? null : mc.thePlayer.inventory.mainInventory[hovered];
        if (is == null || m == null)
        {
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(is.getDisplayName());
        float now = m.price(is);
        if (now <= 0)
        {
            lines.add("\u00a77" + (t == null ? "" : t.name + " ") + "won't pay for this");
        }
        else
        {
            int all = carried(is.getItem());
            lines.add("\u00a77Price " + SimukraftReloaded.displayMoney(now) + " - one sells for \u00a7a"
                    + SimukraftReloaded.displayMoney(m.sellValue(is, 1)));
            if (is.stackSize > 1)
            {
                lines.add("\u00a77This stack of " + is.stackSize + ": \u00a7a" + SimukraftReloaded.displayMoney(m.sellValue(is, is.stackSize)));
            }
            if (all > is.stackSize)
            {
                lines.add("\u00a77All " + all + " you're carrying: \u00a7a" + SimukraftReloaded.displayMoney(m.sellValue(is, all)));
            }
            lines.add("\u00a78In stock " + m.supplyOf(is.getItem()) + ", wanted " + Math.round(m.demandOf(is.getItem()))
                    + " - each one sold lowers the price");
            if (t != null && m.sellValue(is, all) > t.treasury)
            {
                lines.add("\u00a7cThe treasury (" + SimukraftReloaded.displayMoney(t.treasury) + ") can't pay for all of it");
            }
        }
        drawHoveringText(lines, mx, my, fontRendererObj);
    }

    @Override
    public void drawScreen(int mx, int my, float partial)
    {
        drawDefaultBackground();
        Town t = town();
        TownMarket m = market();
        if (selling)
        {
            drawCenteredString(fontRendererObj, (t == null ? "No town" : t.name) + " Market - selling", width / 2, 10, 0xffffff);
            drawCenteredString(fontRendererObj, "Your wallet: " + SimukraftReloaded.displayMoney(ClientMirror.wallet)
                    + "   Treasury: " + (t == null ? "-" : SimukraftReloaded.displayMoney(t.treasury)), width / 2, 22, 0xa0a0a0);
            super.drawScreen(mx, my, partial);
            drawSelling(t, m, mx, my);
            return;
        }
        drawCenteredString(fontRendererObj, (t == null ? "No town" : t.name) + " Market", width / 2, 10, 0xffffff);
        drawCenteredString(fontRendererObj, "Your wallet: " + SimukraftReloaded.displayMoney(ClientMirror.wallet)
                + "   Treasury: " + (t == null ? "-" : SimukraftReloaded.displayMoney(t.treasury))
                + "   (you sell at " + Math.round(MarketActions.SELL_SHARE * 100) + "% of the price)", width / 2, 22, 0xa0a0a0);
        if (m == null || rows.isEmpty())
        {
            drawCenteredString(fontRendererObj, t == null ? "This bank isn't in a town"
                    : "Nothing in stock yet - the market counts what's in the town's warehouses", width / 2, height / 2, 0xa0a0a0);
            super.drawScreen(mx, my, partial);
            return;
        }

        int nameX = 30, priceX = width - 300, changeX = width - 240, stockX = width - 180, wantX = width - 130, chartX = width - 84;
        drawString(fontRendererObj, "Item", nameX, TOP - 12, 0xffff80);
        drawString(fontRendererObj, "Price", priceX, TOP - 12, 0xffff80);
        drawString(fontRendererObj, "Change", changeX, TOP - 12, 0xffff80);
        drawString(fontRendererObj, "Stock", stockX, TOP - 12, 0xffff80);
        drawString(fontRendererObj, "Wanted", wantX, TOP - 12, 0xffff80);
        drawString(fontRendererObj, "14 days", chartX, TOP - 12, 0xffff80);

        int visible = Math.max(1, (height - 34 - TOP) / ROW);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++)
        {
            int index = scroll + i, y = TOP + i * ROW;
            Item item = (Item) Item.itemRegistry.getObject(rows.get(index));
            if (item == null)
            {
                continue;
            }
            ItemStack is = new ItemStack(item);
            if (index == selected)
            {
                drawRect(8, y - 1, width - 8, y + ROW - 1, 0x50ffffff);
            }
            else if (i % 2 == 0)
            {
                drawRect(8, y - 1, width - 8, y + ROW - 1, 0x18ffffff);
            }
            drawItem(is, 10, y);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(is.getDisplayName(), priceX - nameX - 6), nameX, y + 4, 0xffffff);
            float price = m.price(is), before = m.yesterday(is);
            drawString(fontRendererObj, SimukraftReloaded.displayMoney(price), priceX, y + 4, 0xffffff);
            float change = before <= 0 ? 0 : (price - before) / before * 100;
            drawString(fontRendererObj, Math.abs(change) < 0.5f ? "-" : (change > 0 ? "+" : "") + Math.round(change) + "%",
                    changeX, y + 4, change > 0.5f ? 0x60ff60 : change < -0.5f ? 0xff6060 : 0x909090);
            drawString(fontRendererObj, String.valueOf(m.supplyOf(item)), stockX, y + 4, 0xe0e0e0);
            drawString(fontRendererObj, String.valueOf(Math.round(m.demandOf(item))), wantX, y + 4, 0xe0e0e0);
            chart(m.history.get(rows.get(index)), price, chartX, y + 1, 74, ROW - 4);
        }
        super.drawScreen(mx, my, partial);
    }

    /** the last fortnight's prices, and today's, as a line */
    private void chart(List<Float> history, float now, int x, int y, int w, int h)
    {
        List<Float> points = new ArrayList<Float>();
        if (history != null)
        {
            points.addAll(history.subList(Math.max(0, history.size() - 14), history.size()));
        }
        points.add(now);
        if (points.size() < 2)
        {
            return;
        }
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float p : points)
        {
            lo = Math.min(lo, p);
            hi = Math.max(hi, p);
        }
        float span = Math.max(hi - lo, hi * 0.05f + 0.0001f);
        boolean up = points.get(points.size() - 1) >= points.get(0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(up ? 0.4f : 1f, up ? 1f : 0.4f, 0.4f, 1f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i < points.size(); i++)
        {
            float px = x + w * i / (float) (points.size() - 1);
            float py = y + h - (points.get(i) - lo) / span * h;
            GL11.glVertex2f(px, py);
        }
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
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
}
