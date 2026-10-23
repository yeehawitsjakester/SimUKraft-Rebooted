package ashjack.simukraftreloaded.client.Gui.blocks;

import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.ActionHandlers;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Mouse;

import ashjack.simukraftreloaded.common.jobs.JobTerraformer;
import ashjack.simukraftreloaded.common.jobs.JobTerraformer.TerraformerType;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;

public class GuiTerraform extends GuiScreen
{
    FolkData theFolk;
    GuiTextField tfRadius;
    private int mouseCount = 0;

    public GuiTerraform(FolkData folk)
    {
        theFolk = folk;
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    public void initGui()
    {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, "Cancel Request"));
        buttonList.add(new GuiButton(1, width / 2 - 200, 30, 200, 20, "'Sealand' (water to land)"));
        buttonList.add(new GuiButton(2, width / 2 - 200, 50, 200, 20, "'Nature' (plants saplings)"));
        buttonList.add(new GuiButton(3, width / 2 - 200, 70, 200, 20, "'Lawnmower' (cuts all long grass)"));
        buttonList.add(new GuiButton(4, width / 2 - 200, 90, 200, 20, "'Flattenizer' (flatten area)"));
        buttonList.add(new GuiButton(5, width / 2 - 200, 110, 200, 20, "'Value Pack' (single layer of dirt)"));
        buttonList.add(new GuiButton(6, width / 2 , 30, 200, 20, "'Glacial' (Freeze water, add snow)"));
        buttonList.add(new GuiButton(7, width / 2 , 50, 200, 20, "'Moisturizer' (Adds water to lava)"));
        buttonList.add(new GuiButton(8, width / 2 , 70, 200, 20, "'Thermalizer' (Collects lava)"));
        buttonList.add(new GuiButton(9, width / 2 , 90, 200, 20, "'De-icer' (Removes snow)"));
        
        tfRadius = new GuiTextField(fontRendererObj, width / 2 - 50, height - 55, 100, 20);
        tfRadius.setMaxStringLength(5);
        tfRadius.setText("30");
    }

    private String errorText = "";
    public void drawScreen(int i, int j, float f)
    {
        if (mouseCount < 10)
        {
            mouseCount++;
            Mouse.setGrabbed(false);
        }

        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Terraforming - Please choose a Terraforming theme", width / 2, 17, 0xffffff);
        drawCenteredString(fontRendererObj, "Radius (1 to 60)", width / 2, height - 70, 0xffffff);
        drawCenteredString(fontRendererObj, errorText, width / 2, height - 80, 0xff8080);
        tfRadius.drawTextBox();
        super.drawScreen(i, j, f);
    }

    public void updateScreen()
    {
        tfRadius.updateCursorCounter();
    }

    private static final TerraformerType[] TYPES = {null, TerraformerType.WATERTODIRT, TerraformerType.NATURE,
        TerraformerType.LAWNMOWER, TerraformerType.FLATTENIZER, TerraformerType.VALUEPACK, TerraformerType.GLACIAL,
        TerraformerType.MOISTURIZER, TerraformerType.THERMALIZER, TerraformerType.DEICER};

    public void actionPerformed(GuiButton guibutton)
    {
        if (guibutton.id == 0) ///cancel button
        {
            mc.displayGuiScreen(null);
            return;
        }
        if (theFolk.vocation != Vocation.TERRAFORMER)
        {
            errorText = "You must hire a terraformer, not a builder";
            return;
        }
        int radius;
        try
        {
            radius = Integer.parseInt(tfRadius.getText().trim());
        }
        catch (NumberFormatException e)
        {
            errorText = "ERROR: the radius must be a number";
            return;
        }
        if (radius < 1 || radius > 60)
        {
            errorText = "ERROR: the radius must be 1 to 60";
            return;
        }
        if (guibutton.id < 1 || guibutton.id >= TYPES.length)
        {
            return;
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setString("name", theFolk.name);
        data.setString("type", TYPES[guibutton.id].name());
        data.setInteger("radius", radius);
        ServerActions.send("terraform", data);
        mc.displayGuiScreen(null);
    }

    protected void mouseClicked(int i, int j, int k)
    {
        tfRadius.mouseClicked(i, j, k);
        super.mouseClicked(i, j, k);
    }

    protected void keyTyped(char c, int i)
    {
        if (i == 1)  //escape and dont save
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            return;
        }

        if ((i >= 2 && i <= 11) || i == 14)
        {
            try
            {
                if (tfRadius.isFocused())      //was isFocused()
                {
                    tfRadius.textboxKeyTyped(c, i);
                }
            }
            catch (Exception e)
            {
                tfRadius.textboxKeyTyped(c, i);
            }
        }
    }
}
