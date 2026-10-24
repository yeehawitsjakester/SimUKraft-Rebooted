package ashjack.simukraftreloaded.client.Gui.other;

import ashjack.simukraftreloaded.common.ClientMirror;

import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.network.ServerActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import cpw.mods.fml.common.FMLCommonHandler;

public class GuiRunMod extends GuiScreen
{
    public boolean running = true;
    private int mouseCount = 0;

    /**
     * Must not pause: a paused singleplayer server reads no packets, and the server keeps re-sending "choose a mode"
     * every second, so each click's brief unpause reopened the menu before the server read the choice.
     */
    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    /** close once the server has started Sim-U-Kraft (someone chose), in case a leftover prompt reopened us */
    @Override
    public void updateScreen()
    {
        if (ClientMirror.ranStartup)
        {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
    }

    public GuiRunMod()
    {
    }

    @Override
    public void initGui()
    {   System.out.println("Initializing GUI");
        buttonList.add(new GuiButton(0, (width / 2 - 75) , 40, "Do NOT run Sim-U-Kraft"));
        buttonList.add(new GuiButton(1, (width / 2 - 75) , 90, "Normal Mode"));
        buttonList.add(new GuiButton(2, (width / 2 - 75) , 140, "Creative Mode"));
        buttonList.add(new GuiButton(3, (width / 2 - 75) , 190, "Hardcore Mode"));
    }

    @Override
    public void drawScreen(int i, int j, float f)
    {
    	//System.out.println("Preparing to draw screen");
        try
        {
            if (mouseCount < 10)
            {
                mouseCount++;
                Mouse.setGrabbed(false);
            }

            drawDefaultBackground();
            //System.out.println("Drawing Strings");
            drawCenteredString(fontRendererObj, "Please choose the game mode for Sim-U-Kraft", width / 2, 20, 0xffffff);
            drawCenteredString(fontRendererObj, "This mode switches off Sim-U-kraft for this world", width / 2, 60, 0xffff00);
            drawCenteredString(fontRendererObj, "Ideal for beginners and experts. Not too challenging.", width / 2, 110, 0xffff00);
            drawCenteredString(fontRendererObj, "No money needed, everything free, no blocks required, be creative!", width / 2, 160, 0xffff00);
            drawCenteredString(fontRendererObj, "Builders require ALL blocks, harder gameplay", width / 2, 210, 0xffff00);
            
            
            
        }
        catch (Exception e)
        {
        	System.out.println("Caught Exception while drawing strings/screen");
        }

        super.drawScreen(i, j, f);
    }

    @Override
    protected void actionPerformed(GuiButton guibutton)
    {
    	// 10 = don't run, 0 = normal, 1 = creative, 2 = hardcore
    	int[] modes = {10, 0, 1, 2};
    	if (guibutton.id < 0 || guibutton.id >= modes.length)
    	{
    		return;
    	}
    	NBTTagCompound data = new NBTTagCompound();
    	data.setInteger("mode", modes[guibutton.id]);
    	ServerActions.send("setGameMode", data);
    	this.running = false;
    	mc.displayGuiScreen(null);
    }
}
