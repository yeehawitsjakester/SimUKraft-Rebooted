package ashjack.simukraftreloaded.client.Gui.blocks;

import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.ActionHandlers;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import org.lwjgl.input.Mouse;

import cpw.mods.fml.common.registry.GameRegistry;
import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.common.jobs.Job;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

public class GuiMarker extends GuiScreen
{
    V3 location;
    String errorText = "";
    GuiTextField theGuiTextField1;
    EntityPlayer thePlayer = null;
    private int mouseCount = 0;

    ///normal constructor
    public GuiMarker(V3 location, EntityPlayer p)
    {
        this.location = location;
        this.thePlayer = p;
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void updateScreen()
    {
        if (theGuiTextField1 != null)
        {
            theGuiTextField1.updateCursorCounter();
        }
    }

    @Override
    public void initGui()
    {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, "Done"));
        buttonList.add(new GuiButton(1, width / 2 - 100, 100, "Copy structure/building"));
        buttonList.add(new GuiButton(2, width / 2 - 100, 160, "Set new Courier/Beaming point"));

        for (int x = 1; x <= 2; x++)
        {
            ((GuiButton)buttonList.get(x)).enabled = false;
        }

        if (BlockMarker.markers().size() == 3)
        {
            ((GuiButton)buttonList.get(1)).enabled = true;
        }
        else if (BlockMarker.markers().size() == 1)
        {
            ((GuiButton)buttonList.get(2)).enabled = true;
            /// courier drop point name
            theGuiTextField1 = new GuiTextField(fontRendererObj, width / 2 - (width / 3 / 2), 138, width / 3, 20);
            theGuiTextField1.setMaxStringLength(23);
        }
    }

    @Override
    public void drawScreen(int i, int j, float f)
    {
        if (mouseCount < 10)
        {
            mouseCount++;
            Mouse.setGrabbed(false);
        }

        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Sim-u-Markers", width / 2, 30, 0xffffff);
        drawCenteredString(fontRendererObj, "Markers can be used to make a copy of a building or you can use them to", width / 2, 40, 0xa0ffff);
        drawCenteredString(fontRendererObj, "mark out a mining or food-based farming area.", width / 2, 55, 0xa0ffff);
        drawCenteredString(fontRendererObj, "A single marker can be used to designate a new lumberjack area and more!", width / 2, 70, 0xa0ffff);
        drawCenteredString(fontRendererObj, "TIP: remove old markers after use, before marking a new area.", width / 2, 85, 0xa0ffff);
        drawCenteredString(fontRendererObj, errorText, width / 2, height - 50, 0xff0000);

        if (theGuiTextField1 != null)
        {
            theGuiTextField1.drawTextBox();
        }

        super.drawScreen(i, j, f);
    }

    @Override
    public void actionPerformed(GuiButton guibutton)
    {
        if (guibutton.id == 0) ///cancel button
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            return;
        }
        else if (guibutton.displayString.contentEquals("Copy structure/building"))
        {
            // the server scans the area and writes the blueprint (this was a client thread reading the server world)
            NBTTagCompound data = new NBTTagCompound();
            ActionHandlers.putV3(data, "marker", location);
            ServerActions.send("copyStructure", data);
            mc.displayGuiScreen(null);
        }
        else if (guibutton.displayString.contentEquals("Set new Courier/Beaming point"))
        {
            String name = theGuiTextField1.getText().trim();

            if (name.length() == 0)
            {
                errorText = "Please type a name for this Courier/Beaming point";
                return;
            }
            // the server checks for a chest and a unique name, and replies in chat
            NBTTagCompound data = new NBTTagCompound();
            data.setString("name", name);
            ServerActions.send("addCourierPoint", data);
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClicked(int i, int j, int k)
    {
        if (theGuiTextField1 != null)
        {
            theGuiTextField1.mouseClicked(i, j, k);
        }

        super.mouseClicked(i, j, k);
    }

    @Override
    protected void keyTyped(char c, int i)
    {
        if (i == 1)
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
        }

        if (theGuiTextField1 != null)
        {
            theGuiTextField1.textboxKeyTyped(c, i);   //was textboxKeyTyped()
        }
    }
}
