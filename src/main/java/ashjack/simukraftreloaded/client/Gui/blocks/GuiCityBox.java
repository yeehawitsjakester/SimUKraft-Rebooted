package ashjack.simukraftreloaded.client.Gui.blocks;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Keyboard;

import ashjack.simukraftreloaded.client.TownBorderRenderer;
import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.network.ActionHandlers;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.TownActions;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * The City Box: founds a town where it stands, then runs it - its name, who can run it with you, how far its border
 * reaches, and disbanding it. (Anyone can open it; the server only lets the owner change things.)
 */
public class GuiCityBox extends GuiScreen
{
    private final V3 location;
    private final EntityPlayer player;
    private GuiTextField field;
    private int seenSync = -1;
    private boolean confirmDisband = false;

    public GuiCityBox(V3 location, EntityPlayer thePlayer)
    {
        this.location = location;
        this.player = thePlayer;
    }

    private Town town()
    {
        return Towns.at(location);
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        buttonList.add(new GuiButton(0, 5, 5, 50, 20, "Done"));
        String text = field == null ? "" : field.getText();
        field = new GuiTextField(fontRendererObj, width / 2 - 100, 70, 200, 20);
        field.setMaxStringLength(24);
        field.setText(text);
        field.setFocused(true);
        Town t = town();
        int y = height / 2 + 10;
        if (t == null)
        {
            buttonList.add(new GuiButton(1, width / 2 - 75, 96, 150, 20, "Found the town"));
            return;
        }
        boolean owner = t.isOwner(player.getCommandSenderName()) || !t.isClaimed();
        GuiButton b;
        buttonList.add(b = new GuiButton(2, width / 2 - 151, 96, 100, 20, "Rename to this"));
        b.enabled = owner;
        buttonList.add(b = new GuiButton(3, width / 2 - 50, 96, 100, 20, "Add as member"));
        b.enabled = owner;
        buttonList.add(b = new GuiButton(4, width / 2 + 51, 96, 100, 20, "Remove member"));
        b.enabled = owner && !t.members.isEmpty();
        buttonList.add(b = new GuiButton(5, width / 2 - 151, y, 100, 20, "Border smaller"));
        b.enabled = owner && t.radius > Town.MIN_RADIUS;
        buttonList.add(b = new GuiButton(6, width / 2 - 50, y, 100, 20, "Border bigger"));
        b.enabled = owner && t.radius < Town.MAX_RADIUS;
        buttonList.add(new GuiButton(7, width / 2 + 51, y, 100, 20, "Show border: " + (TownBorderRenderer.alwaysShow ? "on" : "off")));
        buttonList.add(b = new GuiButton(9, width / 2 - 151, y + 24, 100, 20, "Tax down 5%"));
        b.enabled = owner && t.taxRate > 0;
        buttonList.add(b = new GuiButton(10, width / 2 - 50, y + 24, 100, 20, "Tax up 5%"));
        b.enabled = owner && t.taxRate < 0.5f;
        buttonList.add(b = new GuiButton(8, width - 110, height - 30, 100, 20, confirmDisband ? "Really disband?" : "Disband town"));
        b.enabled = owner && t.isClaimed();
    }

    @Override
    public void updateScreen()
    {
        field.updateCursorCounter();
        if (seenSync != ClientMirror.syncCount)
        {
            seenSync = ClientMirror.syncCount;
            initGui(); // the town changed on the server
        }
    }

    @Override
    protected void keyTyped(char c, int key)
    {
        if (key == Keyboard.KEY_ESCAPE)
        {
            mc.displayGuiScreen(null);
            return;
        }
        field.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button)
    {
        super.mouseClicked(x, y, button);
        field.mouseClicked(x, y, button);
    }

    @Override
    protected void actionPerformed(GuiButton b)
    {
        if (b.id == 0)
        {
            mc.displayGuiScreen(null);
            return;
        }
        if (b.id == 7)
        {
            TownBorderRenderer.alwaysShow = !TownBorderRenderer.alwaysShow;
            initGui();
            return;
        }
        if (b.id == 8 && !confirmDisband)
        {
            confirmDisband = true;
            initGui();
            return;
        }
        String[] what = {null, "found", "rename", "add", "remove", "border", "border", null, "disband", "tax", "tax"};
        NBTTagCompound data = new NBTTagCompound();
        ActionHandlers.putV3(data, "box", location);
        data.setString("what", what[b.id]);
        data.setString("arg", field.getText());
        data.setInteger("by", b.id == 9 ? -5 : b.id == 10 ? 5 : b.id == 6 ? TownActions.BORDER_STEP : -TownActions.BORDER_STEP);
        ServerActions.send("townAction", data);
        if (b.id == 8)
        {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float partial)
    {
        drawDefaultBackground();
        Town t = town();
        if (t == null)
        {
            drawCenteredString(fontRendererObj, "Found a town", width / 2, 20, 0xffffff);
            drawCenteredString(fontRendererObj, "Name it, and its border will run " + TownActions.FIRST_RADIUS
                    + " blocks out from this box each way.", width / 2, 40, 0xc0c0c0);
            drawCenteredString(fontRendererObj, "You'll own it: its treasury pays the Sims who work in it, and takes the rent.",
                    width / 2, 52, 0xc0c0c0);
        }
        else
        {
            drawCenteredString(fontRendererObj, t.name, width / 2, 14, 0xffffaa);
            drawCenteredString(fontRendererObj, (t.isClaimed() ? "Owned by " + t.owner : "Unclaimed - stand in it and /town claim")
                    + "   Treasury: " + SimukraftReloaded.displayMoney(t.treasury), width / 2, 28, 0xffffff);
            drawCenteredString(fontRendererObj, t.members.isEmpty() ? "No other members" : "Members: " + String.join(", ", t.members),
                    width / 2, 40, 0xc0c0c0);
            drawCenteredString(fontRendererObj, "Name / member:", width / 2, 58, 0xa0a0a0);
            int next = 2 * Math.min(Town.MAX_RADIUS, t.radius + TownActions.BORDER_STEP) + 1, now = 2 * t.radius + 1;
            float cost = (float) ((Math.pow(next, 2) - Math.pow(now, 2)) * TownActions.COST_PER_BLOCK);
            drawCenteredString(fontRendererObj, "Border: " + now + " x " + now + " blocks. Bigger: " + next + " x " + next
                    + " for " + SimukraftReloaded.displayMoney(cost), width / 2, height / 2 - 4, 0xffffff);
            drawString(fontRendererObj, "Tax: " + Math.round(t.taxRate * 100) + "% of wages and sales", width / 2 + 56, height / 2 + 40, 0xffffff);
        }
        field.drawTextBox();
        super.drawScreen(mx, my, partial);
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }
}
