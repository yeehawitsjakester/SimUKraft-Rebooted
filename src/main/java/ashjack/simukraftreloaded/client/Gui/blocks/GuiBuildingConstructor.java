package ashjack.simukraftreloaded.client.Gui.blocks;

import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.ActionHandlers;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import ashjack.simukraftreloaded.client.Gui.folk.GuiEmployFolk;
import ashjack.simukraftreloaded.client.Gui.folk.GuiShowEmployees;
import ashjack.simukraftreloaded.common.jobs.JobBuilder;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.jobs.JobBuilder.Stage;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class GuiBuildingConstructor extends GuiScreen
{
    private int mouseCount = 0;

    private int currentPage = 0;
    private ArrayList<FolkData> theWorkers = new ArrayList<FolkData>();
    V3 constructorLoc;
    String buildDirection = "";
    private int buildingOffset = 0;
    private int buildingsOnPage = 0; //how many building are currently displayed, used for offset
    private int fixedBuildingCount = -1;

    private HashMap pkIndex = new HashMap();
    private GuiTextField tfSearch;
    private String search = "";
    private Building selectedBuilding = null;
    private int previousPage = 1;
    private BuildingPreview preview;
    private boolean draggingPreview;
    private int lastDragX;
    private long lastFrame;

    public GuiBuildingConstructor(V3 location, String buildDirection, ArrayList<FolkData> theFolks)
    {
    	
        constructorLoc = location;
        this.buildDirection = buildDirection;
        if (!Building.isCatalogueLoaded())
        {
            Building.initialiseAllBuildings(); // only the server had read the blueprints
        }

        if (theFolks != null)      // it's passed in
        {
            theWorkers = theFolks;
        }
        else                 //not passed in, so see who is employed here
        {
            theWorkers.clear();

            for (int f = 0; f < SimukraftReloaded.folks().size(); f++)
            {
                FolkData folk = (FolkData) SimukraftReloaded.folks().get(f);

                if (folk.employedAt != null)
                {
                    if (folk.employedAt.isSameCoordsAs(constructorLoc, true, true))
                    {
                        // (the server sets idle builders working when a building is chosen - see "buildIt")
                        theWorkers.add(folk);
                    }
                }
            }
        }
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void updateScreen()
    {
        mc.setIngameNotInFocus();

        if (tfSearch != null)
        {
            tfSearch.updateCursorCounter();
        }

        super.updateScreen();
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        showPage();  ///mainly make buttons
        super.initGui();
    }

    @Override
    public void drawScreen(int i, int j, float f)
    {
        try
        {
            if (mouseCount < 10)
            {
                mouseCount++;
                Mouse.setGrabbed(false);
            }

            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Building Constructor", width / 2, 17, 0xffffff);
            String s = "Idle";
            String t = "Not chosen yet";

            try
            {
                if (theWorkers.size() > 0)
                {
                    s = theWorkers.get(0).syncedJobStage;

                    if (theWorkers.get(0).theBuilding != null)
                    {
                        t = theWorkers.get(0).theBuilding.displayName;
                    }
                }
            }
            catch (Exception e)
            {
                s = "on their way";
                t = "";
            }

            drawCenteredString(fontRendererObj, "Current status: " + s , width / 2, 30, 0xaaffff);
            drawCenteredString(fontRendererObj, "Building: " + t , width / 2, 40, 0xaaffff);

            if (currentPage == 0)
            {
                drawCenteredString(fontRendererObj, "Please choose a task for this building constructor", width / 2, 100, 0xffffaa);
            }
            else if (currentPage == 1)
            {
                drawCenteredString(fontRendererObj, "Please choose a type of building", width / 2, 100, 0xffffaa);
            }
            else if (currentPage == 2)
            {
                drawCenteredString(fontRendererObj, "Now choose the residential building to build", width / 2, 50, 0xffffaa);
                tfSearch.drawTextBox();
            }
            else if (currentPage == 3)
            {
                drawCenteredString(fontRendererObj, "Choose an unemployed Sim-U-Folk you want to hire", width / 2, 50, 0xffffaa);
            }
            else if (currentPage == 4)
            {
                drawCenteredString(fontRendererObj, "Here are all your employees", width / 2, 50, 0xffffaa);
            }
            else if (currentPage == 5)
            {
                drawCenteredString(fontRendererObj, "Now choose the commercial building to build", width / 2, 50, 0xffffaa);
                tfSearch.drawTextBox();
            }
            else if (currentPage == 6)
            {
                drawCenteredString(fontRendererObj, "Now choose the industrial building to build", width / 2, 50, 0xffffaa);
                tfSearch.drawTextBox();
            }
            else if (currentPage == 7)
            {
                drawCenteredString(fontRendererObj, "Now choose the other type of building to build", width / 2, 50, 0xffffaa);
                tfSearch.drawTextBox();
            }
            else if (currentPage == 9)
            {
                drawCenteredString(fontRendererObj, "Now choose the special type of building to build", width / 2, 50, 0xffffaa);
                tfSearch.drawTextBox();
            }
            else if (currentPage == 10)
            {
            	// details on the left, the blueprint turning on the right
            	drawCenteredString(fontRendererObj, selectedBuilding.displayNameWithoutPK, width / 2, 55, 0xffffaa);
            	int colW = width / 2 - 30;
            	int y = 75;
            	y = detail("Author", selectedBuilding.author, y, colW);
            	y = detail("Cost", SimukraftReloaded.displayMoney((float)(selectedBuilding.blocksInBuilding * 0.02f)), y, colW);
            	y = detail("Size", selectedBuilding.ltrCount + " wide, " + selectedBuilding.ftbCount + " deep, " + selectedBuilding.layerCount + " high", y, colW);
            	y = detail("Blocks", String.valueOf(selectedBuilding.blocksInBuilding), y, colW);
            	y = detail("Constructor box", selectedBuilding.elevationLevel == 0 ? "at ground level"
            			: selectedBuilding.elevationLevel + " block(s) underground", y, colW);
            	if (selectedBuilding.description != null && !selectedBuilding.description.trim().isEmpty()
            			&& !selectedBuilding.description.trim().equalsIgnoreCase("none"))
            	{
            		detail("About", selectedBuilding.description, y, colW);
            	}

            	int px = width / 2 + 10, py = 65, pw = width / 2 - 20, ph = height - 65 - 32;
            	drawRect(px, py, px + pw, py + ph, 0x40000000);
            	if (preview != null && !preview.isEmpty())
            	{
            		long now = System.currentTimeMillis();
            		if (!draggingPreview && lastFrame != 0)
            		{
            			preview.yaw += (now - lastFrame) * 0.02f; // 20 degrees a second
            		}
            		lastFrame = now;
            		preview.draw(px + 6, py + 6, pw - 12, ph - 12);
            		drawCenteredString(fontRendererObj, "Drag to turn it", px + pw / 2, py + ph - 10, 0x808080);
            	}
            	else
            	{
            		drawCenteredString(fontRendererObj, "No preview for this building", px + pw / 2, py + ph / 2, 0x808080);
            	}
            }
            else if (currentPage == 8)
            {
                drawCenteredString(fontRendererObj, "Building requirements for " + selectedBuilding.displayNameWithoutPK, width / 2, 50, 0xffffaa);
                int y = 70;
                Iterator it = selectedBuilding.requirements.entrySet().iterator();

                while (it.hasNext())
                {
                    Map.Entry pairs = (Map.Entry)it.next();
                    ItemStack is = (ItemStack) pairs.getKey();

                    if (is != null)
                    {
                        if (y + 20 > (height - 20))
                        {
                            drawString(fontRendererObj, "...and several more block types", 90, y, 0xffffff);
                        }
                        else
                        {
                            String itemName = is.getDisplayName();

                            if (itemName.toLowerCase().contentEquals("oak wood"))
                            {
                                itemName = "Logs";
                            }

                            if (itemName.toLowerCase().contains("oak wood planks"))
                            {
                                itemName = "Planks";
                            }

                            displayReq(itemName, (Integer) pairs.getValue(), y);
                            y += 15;
                        }
                    }
                }
            }
            
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        //theGuiTextField1.drawTextBox();
        super.drawScreen(i, j, f);
    }

    
    /** a "Label: value" line in the details column, wrapped; returns the y for the next one */
    private int detail(String label, String value, int y, int w)
    {
        for (Object line : fontRendererObj.listFormattedStringToWidth("\u00a77" + label + ": \u00a7f" + value, w))
        {
            drawString(fontRendererObj, (String) line, 20, y, 0xffffff);
            y += 10;
        }
        return y + 4;
    }

    private void displayReq(String block, int qty, int y)
    {
        double stacks = Math.floor(qty / 64);
        drawString(fontRendererObj, qty + "", 90, y, 0xffffff);
        drawString(fontRendererObj, "x", 125, y, 0xffffff);
        drawString(fontRendererObj, block, 150, y, 0xffffff);
        String st = "";

        if (qty < 64)
        {
            st = "(less than one stack)";
        }
        else if (qty == 64)
        {
            st = "(exactly 1 stack)";
        }
        else if (qty >= 64 && qty < 128)
        {
            st = "(about 2 stacks)";
        }
        else
        {
            st = "(about " + (int)(stacks + 1) + " stacks)";
        }

        drawString(fontRendererObj, st, 250, y, 0xffffff);
    }

    private void showPage()
    {
        mc.setIngameNotInFocus();
        buttonList.clear();
        buttonList.add(new GuiButton(0, 2, 12, 50, 20, "Done"));
        if (currentPage != 0)
        {
            buttonList.add(new GuiButton(1002, 54, 12, 50, 20, "Back"));
        }

        if (selectedBuilding == null)
        {
        	selectedBuilding = Building.getBuildingByConBox(this.constructorLoc);
        }

        if (currentPage == 0)
        {
            buttonList.add(new GuiButton(1, (width / 2) - 60, 150, 120, 20, "Choose building"));
            buttonList.add(new GuiButton(2, (width / 2) - 180, 150, 120, 20, "Hire builder"));
            String w = "worker";

            if (theWorkers.size() == 1)
            {
                w = theWorkers.get(0).name;
            }
            else if (theWorkers.size() > 1)
            {
                w = "Staff (" + theWorkers.size() + ")";
            }

            buttonList.add(new GuiButton(3, (width / 2) + 60, 150, 120, 20, "Fire " + w));
            buttonList.add(new GuiButton(4, (width / 2) + 60, 170, 120, 20, "Show Employees"));
            buttonList.add(new GuiButton(5, -600, 170, 120, 20, "-"));
            buttonList.add(new GuiButton(6, (width / 2) - 60, 170, 120, 20, "Terraform area"));
            buttonList.add(new GuiButton(7, (width / 2) - 180, 170, 120, 20, "Hire terraformer"));

            if (theWorkers.size() == 0)
            {
                ((GuiButton)buttonList.get(1)).enabled = false;
                ((GuiButton)buttonList.get(2)).enabled = true;
                ((GuiButton)buttonList.get(3)).enabled = false;
                ((GuiButton)buttonList.get(6)).enabled = false;
                ((GuiButton)buttonList.get(7)).enabled = true;
            }
            else      //all other stages of building the building
            {
                ((GuiButton)buttonList.get(1)).enabled = true;
                ((GuiButton)buttonList.get(2)).enabled = false;
                ((GuiButton)buttonList.get(3)).enabled = true;
                ((GuiButton)buttonList.get(6)).enabled = true;
                ((GuiButton)buttonList.get(7)).enabled = false;
            }

        }
        else if (currentPage == 1)  	///choose type of building
        {
            buttonList.add(new GuiButton(5, (width / 2) - 200, 150, 100, 20, "Residential"));
            buttonList.add(new GuiButton(6, (width / 2) - 100, 150, 100, 20, "Commercial"));
            buttonList.add(new GuiButton(7, (width / 2), 150, 100, 20, "Industrial"));
            buttonList.add(new GuiButton(8, (width / 2) + 100, 150, 100, 20, "Other"));
            buttonList.add(new GuiButton(9, (width / 2) -50, 180, 100, 20, "Special"));
        }
        else if (currentPage == 3)    //choose worker
        {
        }
        else if (currentPage == 10)
        {
        	GuiButton bname;
        	GuiButton bdesc;
        	GuiButton bauthor;
        	GuiButton bdim;
        	GuiButton bgroundlevel;
        	//BOOKMARK
        	/*buttonList.add(bname = new GuiButton(25, (width / 2)-200, 50, 400, 20, "Name: " + selectedBuilding.displayNameWithoutPK));
        	buttonList.add(bdesc = new GuiButton(26, (width / 2)-200, 80, 400, 30, "Description: " + selectedBuilding.description));
        	buttonList.add(bauthor = new GuiButton(27, (width / 2)-200, 110, 400, 20, "Author: " + selectedBuilding.author));
        	buttonList.add(bdim = new GuiButton(28, (width / 2)-200, 140, 400, 20, "Dimensions: " + selectedBuilding.dimensions));
        	buttonList.add(bgroundlevel = new GuiButton(29, (width / 2)-200, 170, 400, 20, "Best Constructor Box Elevation: " + selectedBuilding.elevationLevel));*/
        	buttonList.add(new GuiButton(1001, (width / 2) - 100, height - 25, 100, 20, "Go Back"));
            buttonList.add(new GuiButton(969, (width / 2), height - 25, 100, 20, "Requirements"));
        }
        else if (currentPage == 4)    //fire worker
        {

            try
            {
                int x = 10, y = 65, idx = 1;

                for (int f = 0; f < SimukraftReloaded.folks().size(); f++)
                {
                    FolkData folk = (FolkData) SimukraftReloaded.folks().get(f);
                    buttonList.add(new GuiButton(idx, x, y, 100, 20, "Fire " + folk.name));
                    idx++;
                    x += 100;

                    if ((x + 100) > width)
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
        else if (currentPage == 2 || currentPage == 5 || currentPage == 6 || currentPage == 7 || currentPage == 9) //what r/c/i/o building
        {
            ArrayList<Building> houses = new ArrayList<Building>();
            String theType = "";
            buildingsOnPage = 0;
            tfSearch = new GuiTextField(fontRendererObj, width / 2 - 50, height - 30, 100, 20);
            tfSearch.setText(search);
            tfSearch.setFocused(true);
            tfSearch.setMaxStringLength(10);

            if (currentPage == 2)
            {
                houses = Building.getBuildingBlueprints("residential", tfSearch.getText().trim());
                theType = "residential";
            }
            else if (currentPage == 5)
            {
                houses = Building.getBuildingBlueprints("commercial", tfSearch.getText().trim());
                theType = "commercial";
            }
            else if (currentPage == 6)
            {
                houses = Building.getBuildingBlueprints("industrial", tfSearch.getText().trim());
                theType = "industrial";
            }
            else if (currentPage == 7)
            {
                houses = Building.getBuildingBlueprints("other", tfSearch.getText().trim());
                theType = "other";
            }
            else if (currentPage == 9)
            {
                houses = Building.getBuildingBlueprints("special", tfSearch.getText().trim());
                theType = "special";
            }

            int x = 10, y = 60, idx = 1;

            if (houses != null)
            {
                for (int b = 0; b <= houses.size(); b++)
                {
                    int boff = b + buildingOffset;

                    if (boff < 0)
                    {
                        boff = 0;
                        buildingOffset = 0;
                    }

                    if ((boff) < houses.size())
                    {
                        if (houses.get(boff) != null)
                        {
                            String line2, line3 = "", line4;
                            Building building = houses.get(boff);
                            String realCost = "";

                            if (theWorkers.size() > 1)
                            {
                                realCost = " (" + SimukraftReloaded.displayMoney((float)(building.blocksInBuilding * 0.02f) * theWorkers.size()) + ")";
                            }//BOOKMARK

                            line2 = building.ltrCount + " x " + building.ftbCount + " x " + building.layerCount;
                            line3 = SimukraftReloaded.displayMoney((float)(building.blocksInBuilding * 0.02f)) + realCost;
                            line4 = building.author;
                            GuiButton b1;
                            GuiButton b2;
                            GuiButton b3;
                            buttonList.add(b3 = new GuiButton(idx + 300, x, y + 48, 120, 20, line4));
                            buttonList.add(b2 = new GuiButton(idx + 200, x, y + 32, 120, 20, line3));
                            buttonList.add(b1 = new GuiButton(idx + 100, x, y + 16, 120, 20, line2));
                            b1.enabled = false;
                            b2.enabled = false;
                            b3.enabled = false;
                            String pk = "";

                            if (building.displayName.startsWith("PKID"))        /// PKID123-title
                            {
                                int hyphen = building.displayName.indexOf("-");
                                pk = building.displayName.substring(0, hyphen + 1); // PKID123-
                            }

                            pkIndex.put(idx, pk);
                            buttonList.add(new GuiButton(idx, x, y, 120, 20, building.displayNameWithoutPK));
                            x += 120;

                            if ((x + 120) > width)
                            {
                                x = 10;
                                y += 71;
                            }

                            idx++;
                            buildingsOnPage++;

                            if (buildingOffset > 0)
                            {
                                buttonList.add(new GuiButton(501, 5, height - 20, 75, 20, "< Page"));
                            }

                            if ((y + 20 + 20 + 20 + 20) > height)
                            {
                                buttonList.add(new GuiButton(500, width - 80, height - 20, 75, 20, "Page >"));
                                break;
                            }
                        }
                    }
                    else
                    {
                        buttonList.add(new GuiButton(501, 5, height - 20, 75, 20, "< Page!"));
                    }
                }

                if (fixedBuildingCount == -1)
                {
                    fixedBuildingCount = buildingsOnPage;
                }
            }
            else
            {
                buttonList.add(new GuiButton(1, 10, 60, 300, 20, "Nothing found, go back and choose another"));
            }
        }
        else if (currentPage == 8)     ///building requirements
        {
            buttonList.add(new GuiButton(1001, (width / 2) - 100, height - 25, 100, 20, "Go Back"));
            buttonList.add(new GuiButton(1000, (width / 2), height - 25, 100, 20, "Build it!"));
        }
    }

    long fingBodge = 0; // I don't know why I need to do this to stop it from fucking up.

    @Override
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void actionPerformed(GuiButton guibutton)
    {
        if (System.currentTimeMillis() - fingBodge < 100)
        {
            return;
        }

        fingBodge = System.currentTimeMillis();

        if (!guibutton.enabled)
        {
            return;
        }

        if (guibutton.id == 0) ///cancel button
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            return;
        }

        if (guibutton.id == 1002)
        {
            goBack();
            return;
        }

        if (currentPage == 0)
        {
            if (guibutton.displayString.contentEquals("Choose building"))   //chosen to construct a building
            {
                currentPage = 1;
                showPage();
            }
            else if (guibutton.id == 2) ///chosen to hire worker
            {
                GuiScreen gui = new GuiEmployFolk(this.constructorLoc, this.buildDirection, Vocation.BUILDER);
                mc.displayGuiScreen(null);
                mc.displayGuiScreen(gui);
            }
            else if (guibutton.id == 3) ////chosen to fire worker
            {
                fireAllFolksForThisBuilding();
                currentPage = 0;
                showPage();
            }
            else if (guibutton.id == 4)  //show employees
            {
                GuiScreen gui = new GuiShowEmployees();
                mc.displayGuiScreen(null);
                mc.displayGuiScreen(gui);
            }
            else if (guibutton.id == 5) //// Buildings manager
            {
                //Removed building manager, use this for something else.
            }
            else if (guibutton.id == 6) //// Terraform area
            {
                GuiScreen gui = new GuiTerraform(theWorkers.get(0));
                mc.displayGuiScreen(null);
                mc.displayGuiScreen(gui);
            }
            else if (guibutton.id == 7) // employ terraform
            {
                GuiScreen gui = new GuiEmployFolk(this.constructorLoc, "N/A", Vocation.TERRAFORMER);
                mc.displayGuiScreen(null);
                mc.displayGuiScreen(gui);
            }

        }
        else if (currentPage == 1)    //chosen what type of build to build
        {
            if (guibutton.id == 5)    //residential
            {
                currentPage = 2;
                showPage();
            }
            else if (guibutton.id == 6)    //commercial
            {
                currentPage = 5;
                showPage();
            }
            else if (guibutton.id == 7)   ///industrial
            {
                currentPage = 6;
                showPage();
            }
            else if (guibutton.id == 8)   ///other
            {
                currentPage = 7;
                showPage();
            }
            else if (guibutton.id == 9)   ///special
            {
            	currentPage = 9;
            	showPage();
            }
        }
        else if (currentPage == 2 || currentPage == 5 || currentPage == 6 || currentPage == 7 || currentPage == 9) //chosen building to build
        {
            previousPage = currentPage;

            if (guibutton.id == 1 && guibutton.displayString.startsWith("Nothing found"))
            {
                goBack();
                return;
            }
            else if (guibutton.id == 500)  //next page of buildings
            {
                buildingOffset += fixedBuildingCount;
                showPage();
                return;
            }
            else if (guibutton.id == 501)   //previous page of buildings
            {
                buildingOffset -= fixedBuildingCount;
                showPage();
                return;
            }
            else       //they've clicked a building
            {
                String type = "";

                if (currentPage == 2)
                {
                    type = "residential";
                }

                if (currentPage == 5)
                {
                    type = "commercial";
                }

                if (currentPage == 6)
                {
                    type = "industrial";
                }

                if (currentPage == 7)
                {
                    type = "other";
                }
                if (currentPage == 9)
                {
                    type = "special";
                }

                String pkPrefix = "";
                pkPrefix = (String) pkIndex.get(guibutton.id); //  PKID123-
                selectedBuilding = Building.getFromAllBuildings(pkPrefix + guibutton.displayString, type);
                try {
                	selectedBuilding.buildDirection = this.buildDirection;
                } catch(Exception e) {} //probably not stood in the right place, live-ware issue!
                
                preview = selectedBuilding == null ? null : new BuildingPreview(selectedBuilding);
                lastFrame = 0;
                currentPage = 10;
                showPage();
            }
        }
        else if (currentPage == 8)   // building requirements (what blocks are need)
        {
            if (guibutton.displayString.contentEquals("Build it!"))
            {
                // the server sets the builders working on it (it looks the blueprint up in its own catalogue)
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "box", constructorLoc);
                data.setString("name", selectedBuilding.displayName);
                data.setString("type", selectedBuilding.type);
                data.setString("dir", buildDirection);
                ServerActions.send("buildIt", data);

                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                return;
            }
            else if (guibutton.id == 1001)    //go back
            {
                currentPage = 10;
                showPage();
            }
        }
        else if (currentPage == 10)
        {
        	if(guibutton.id == 969)
        	{
        		currentPage = 8;
        		showPage();
        	}
        	else if (guibutton.id == 1001)    //go back
            {
                currentPage = previousPage;
                showPage();
            }
        }
        else if (currentPage == 3)    //chosen worker
        {
            //page removed - guiemployfolk
        }
        else if (currentPage == 4)   ///fire worker
        {
            fireAllFolksForThisBuilding();
            currentPage = 0;
            showPage();
        }
    }

   
    /** one step back towards the first page */
    private void goBack()
    {
        if (currentPage == 10)
        {
            currentPage = previousPage;
        }
        else if (currentPage == 8)
        {
            currentPage = 10;
        }
        else if (currentPage == 2 || currentPage == 5 || currentPage == 6 || currentPage == 7 || currentPage == 9)
        {
            currentPage = 1;
            buildingOffset = 0;
            search = "";
            tfSearch = null;
        }
        else
        {
            currentPage = 0;
        }
        showPage();
    }

    public void fireAllFolksForThisBuilding()
    {
        NBTTagCompound data = new NBTTagCompound();
        ActionHandlers.putV3(data, "box", constructorLoc);
        ServerActions.send("fireAllAt", data);
        theWorkers.clear();
    }

    @Override
    public void keyTyped(char c, int i)
    {
        if (i == 1)
        {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        else
        {
            if (tfSearch != null)
            {
                tfSearch.textboxKeyTyped(c, i);
                search = tfSearch.getText();

                if (!search.endsWith(":"))
                {
                    buildingOffset = 0;
                    showPage();
                }
            }
        }
    }

    @Override
    public void mouseClicked(int i, int j, int k)
    {
        if (tfSearch != null)
        {
            tfSearch.mouseClicked(i, j, k);
        }

        draggingPreview = currentPage == 10 && k == 0 && i > width / 2 + 10 && j > 65 && j < height - 32;
        lastDragX = i;
        super.mouseClicked(i, j, k);
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long timeHeld)
    {
        if (draggingPreview && preview != null)
        {
            preview.yaw += (x - lastDragX) * 2f;
            lastDragX = x;
        }
        super.mouseClickMove(x, y, button, timeHeld);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button)
    {
        if (button == 0)
        {
            draggingPreview = false;
        }
        super.mouseMovedOrUp(x, y, button);
    }
}
