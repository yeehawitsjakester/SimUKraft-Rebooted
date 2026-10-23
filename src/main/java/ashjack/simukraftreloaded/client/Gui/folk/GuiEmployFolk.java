package ashjack.simukraftreloaded.client.Gui.folk;

import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.client.ClientGuiOpener;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.ActionHandlers;
import java.util.ArrayList;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import ashjack.simukraftreloaded.blocks.functionality.FarmingBox;
import ashjack.simukraftreloaded.blocks.functionality.MiningBox;
import ashjack.simukraftreloaded.blocks.functionality.PathBox;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiBuildingConstructor;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiControlBox;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiFarming;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiMining;
import ashjack.simukraftreloaded.client.Gui.blocks.GuiPathBox;
import ashjack.simukraftreloaded.common.jobs.Job;
import ashjack.simukraftreloaded.common.jobs.JobBaker;
import ashjack.simukraftreloaded.common.jobs.JobBuilder;
import ashjack.simukraftreloaded.common.jobs.JobBuildersMerchant;
import ashjack.simukraftreloaded.common.jobs.JobButcher;
import ashjack.simukraftreloaded.common.jobs.JobCourier;
import ashjack.simukraftreloaded.common.jobs.JobCropFarmer;
import ashjack.simukraftreloaded.common.jobs.JobGlassMaker;
import ashjack.simukraftreloaded.common.jobs.JobGrocer;
import ashjack.simukraftreloaded.common.jobs.JobLivestockFarmer;
import ashjack.simukraftreloaded.common.jobs.JobLumberjack;
import ashjack.simukraftreloaded.common.jobs.JobMiner;
import ashjack.simukraftreloaded.common.jobs.JobShepherd;
import ashjack.simukraftreloaded.common.jobs.JobSoldier;
import ashjack.simukraftreloaded.common.jobs.JobTerraformer;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

public class GuiEmployFolk extends GuiScreen
{
    FolkData theFolk;
    V3 controlBoxLocation;
    String buildDirection = "";
    MiningBox miningBox;
    FarmingBox farmingBox;
    PathBox pathBox;
    Vocation vocation;
    private int mouseCount = 0;

    /** folks selected to be employed */
    private ArrayList<GuiButton> selectedFolks = new ArrayList<GuiButton>();
    private int maxEmployees = 1;

    public GuiEmployFolk(V3 controlBoxLocation, String dir, Vocation vocation)
    {
        this.controlBoxLocation = controlBoxLocation;
        buildDirection = dir;
        this.vocation = vocation;

        if (this.vocation == Vocation.BUILDER)
        {
            maxEmployees = 1;
        }
    }

    public GuiEmployFolk(MiningBox b, Vocation v)
    {
        try
        {
            controlBoxLocation = b.location;
            vocation = v;
            miningBox = b;
            maxEmployees = 1;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public GuiEmployFolk(FarmingBox b, Vocation v)
    {
        try
        {
            controlBoxLocation = b.location;
            vocation = v;
            farmingBox = b;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public GuiEmployFolk(PathBox thePathBox, Vocation v)
    {
        try
        {
            controlBoxLocation = thePathBox.location;
            vocation = v;
            pathBox = thePathBox;
            maxEmployees = 1;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void initGui()
    {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 200, height - 30, "Cancel"));
        buttonList.add(new GuiButton(1000, width / 2 , height - 30, "OK"));
        ArrayList folks = FolkData.getFolkUnemployed(false);

        try
        {
            int x = 10, y = 40, idx = 1;

            for (int f = 0; f < folks.size(); f++)
            {
                FolkData folk = (FolkData) folks.get(f);
                String xp = "";
                int ixp = 0;

                if (this.vocation == Vocation.BUILDER)
                {
                    ixp = (int)Math.floor(folk.levelBuilder);
                    xp = " (" + ixp + ")";
                }
                else if (this.vocation == Vocation.MINER)
                {
                    ixp = (int)Math.floor(folk.levelMiner);
                    xp = " (" + ixp + ")";
                }
                else if (this.vocation == Vocation.SOLDIER)
                {
                    ixp = (int)Math.floor(folk.levelSoldier);
                    xp = " (" + ixp + ")";
                }

                buttonList.add(new GuiButton(idx, x, y, 110, 20, folk.name + xp));
                idx++;
                x += 110;

                if ((x + 110) > width)
                {
                    x = 10;
                    y += 20;
                }

                if ((y + 20) > (height - 50))
                {
                    break;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void drawScreen(int i, int j, float f)
    {
        drawDefaultBackground();

        try
        {
            if (mouseCount < 10)
            {
                mouseCount++;
                Mouse.setGrabbed(false);
            }

            drawCenteredString(fontRendererObj, "Choose who you'd like to Employ as a " + this.vocation.toString(), width / 2, 17, 0xffffff);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        super.drawScreen(i, j, f);
    }

    @Override
    public void actionPerformed(GuiButton guibutton)
    {
        if (!guibutton.enabled)
        {
            return;
        }

        /** cancel button */
        if (guibutton.id == 0)
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            return;
        }

        /** clicked on a folk */
        if (guibutton.id > 0 && guibutton.id < 1000)
        {
            if (selectedFolks.size() < maxEmployees)
            {
                selectedFolks.add(guibutton);
                guibutton.enabled = false;
            }
        }

        /** ok, hire the selected folk - the server does the hiring (and the money/creative checks) */
        if (guibutton.id == 1000)
        {
            final ArrayList<String> names = new ArrayList<String>();

            for (int w = 0; w < selectedFolks.size(); w++)
            {
                GuiButton button = selectedFolks.get(w);
                String folkname = button.displayString;

                if (folkname.contains("("))
                {
                    folkname = button.displayString.substring(0, button.displayString.indexOf(" (")).trim();
                }
                names.add(folkname);
            }
            if (names.isEmpty())
            {
                return;
            }

            NBTTagCompound data = new NBTTagCompound();
            ActionHandlers.putV3(data, "box", controlBoxLocation);
            data.setString("vocation", vocation.name());
            ActionHandlers.putNames(data, "folks", names);
            ServerActions.send("hire", data);

            // open the next screen once the server has sent the result back
            ClientGuiOpener.open(new ClientGuiOpener.Factory()
            {
                public GuiScreen create()
                {
                    return nextScreen(names);
                }
            });
        }
    }

    /** the screen that follows hiring, built from the freshly synced folks */
    private GuiScreen nextScreen(ArrayList<String> names)
    {
        ArrayList<FolkData> efolks = new ArrayList<FolkData>();
        for (String name : names)
        {
            FolkData f = FolkData.getFolkByName(name);
            if (f != null && f.employedAt != null)
            {
                efolks.add(f);
            }
        }
        if (efolks.isEmpty())
        {
            return null; // the server refused (it says why in chat)
        }

        if (this.vocation == Vocation.BUILDER || this.vocation == Vocation.TERRAFORMER)
        {
            return new GuiBuildingConstructor(this.controlBoxLocation, this.buildDirection, efolks);
        }
        else if (this.vocation == Vocation.MINER)
        {
            MiningBox box = MiningBox.getMiningBlockByBoxXYZ(this.controlBoxLocation);
            return new GuiMining(box != null ? box : this.miningBox, efolks);
        }
        else if (this.vocation == Vocation.CROPFARMER)
        {
            FarmingBox box = FarmingBox.getFarmingBlockByBoxXYZ(this.controlBoxLocation);
            return new GuiFarming(box != null ? box : this.farmingBox, efolks.get(0));
        }
        else if (this.vocation == Vocation.PATHBUILDER)
        {
            return new GuiPathBox(this.pathBox, efolks);
        }
        return new GuiControlBox(this.controlBoxLocation, efolks.get(0));
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void keyTyped(char c, int i)
    {
        if (i == 1) //escape
        {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }

        //((GuiButton)buttonList.get(0)).enabled = theGuiTextField1.getText().trim().length() > 0;
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void updateScreen()
    {
        // theGuiTextField1.updateCursorCounter();
    }
}
