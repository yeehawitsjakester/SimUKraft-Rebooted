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
import ashjack.simukraftreloaded.core.building.BuildingCategory;
import ashjack.simukraftreloaded.core.building.MotelPlan;
import ashjack.simukraftreloaded.core.building.ApartmentPlan;
import ashjack.simukraftreloaded.network.Placement;
import ashjack.simukraftreloaded.core.building.ApartmentConversion;
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

    /** the building each button on a list page stands for */
    private HashMap<Integer, Building> listedByButton = new HashMap<Integer, Building>();
    /** page 12 lists this category, or every building matching the search when null */
    private String listCategory = null;
    /** the motel being designed on page 13 */
    private char motelShape = 'S';
    private int motelFloors = 1, motelRoomsA = 4, motelRoomsB = 2;
    private String motelSpace = "";
    /** the apartments being designed on page 14 */
    private char aptKind = 'A';
    private int aptAcross = 3, aptRows = 2, aptFloors = 3;
    /** on page 10 with "As apartments" on: the building as it is without */
    private Building withoutFlats = null;
    private String flatsNote = "";
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
            if (isListPage() && tfSearch != null)
            {
                drawString(fontRendererObj, "Search:", width / 2 - 92, height - 24, 0xaaaaaa);
                if (tfSearch.getText().isEmpty())
                {
                    drawString(fontRendererObj, "\u00a78name, by:author, uses:glass, w:9", width / 2 + 54, height - 24, 0xffffff);
                }
            }
            drawCenteredString(fontRendererObj, "Building: " + t , width / 2, 40, 0xaaffff);

            if (currentPage == 0)
            {
                Building placing = Placement.waitingAt(constructorLoc);
                if (placing != null)
                {
                    drawCenteredString(fontRendererObj, "Placing the " + placing.displayNameWithoutPK + " - see the outline", width / 2, 55, 0xffffaa);
                    drawCenteredString(fontRendererObj, placing.placementStatus, width / 2, 66,
                            placing.placementStatus.startsWith("It would destroy") || placing.placementStatus.startsWith("Couldn't")
                            || placing.placementStatus.startsWith("Part") ? 0xff8080 : 0xaaffaa);
                    drawCenteredString(fontRendererObj, "\u00a77Start it here, or shift-right-click the constructor box with an empty hand", width / 2, 136, 0xffffff);
                }
                else
                {
                    drawCenteredString(fontRendererObj, "Please choose a task for this building constructor", width / 2, 100, 0xffffaa);
                }
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
            else if (currentPage == 11)
            {
                drawCenteredString(fontRendererObj, "Choose a category", width / 2, 50, 0xffffaa);
            }
            else if (currentPage == 12)
            {
                drawCenteredString(fontRendererObj, listCategory == null ? "Search every building" : listCategory,
                        width / 2, 50, 0xffffaa);
                tfSearch.drawTextBox();
            }
            else if (currentPage == 13)
            {
                drawMotelPage();
            }
            else if (currentPage == 14)
            {
                drawApartmentPage();
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
            	if (withoutFlats != null)
            	{
            		y = detail("Flats", ApartmentConversion.flats(selectedBuilding) + ", for singles, couples and families (the ground floor is the lobby)", y, colW);
            	}
            	if (!flatsNote.isEmpty())
            	{
            		y = detail("Flats", flatsNote, y, colW);
            	}
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

            if (Placement.waitingAt(constructorLoc) != null)
            {
                // move it before anyone builds it (left/right and nearer/further as seen from the box)
                String[] moves = {"< Left", "Right >", "Closer", "Further", "Up", "Down"};
                for (int m = 0; m < moves.length; m++)
                {
                    buttonList.add(new GuiButton(40 + m, width / 2 - 183 + m * 61, 80, 60, 20, moves[m]));
                }
                buttonList.add(new GuiButton(46, width / 2 - 101, 104, 100, 20, "Start building"));
                buttonList.add(new GuiButton(47, width / 2 + 1, 104, 100, 20, "Cancel it"));
            }

        }
        else if (currentPage == 1)  	///choose type of building
        {
            buttonList.add(new GuiButton(5, (width / 2) - 200, 150, 100, 20, "Residential"));
            buttonList.add(new GuiButton(6, (width / 2) - 100, 150, 100, 20, "Commercial"));
            buttonList.add(new GuiButton(7, (width / 2), 150, 100, 20, "Industrial"));
            buttonList.add(new GuiButton(8, (width / 2) + 100, 150, 100, 20, "Other"));
            buttonList.add(new GuiButton(9, (width / 2) - 200, 180, 100, 20, "Special"));
            buttonList.add(new GuiButton(10, (width / 2) - 100, 180, 100, 20, "By category"));
            buttonList.add(new GuiButton(11, (width / 2), 180, 100, 20, "Search all"));
            buttonList.add(new GuiButton(12, (width / 2) + 100, 180, 100, 20, "Motel..."));
            buttonList.add(new GuiButton(13, (width / 2) + 100, 210, 100, 20, "Apartments..."));
            buttonList.add(new GuiButton(14, (width / 2), 210, 100, 20, "Watch tower"));
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
            // decorative buildings can be built as flats (with floors added if they're only a shell)
            if (withoutFlats != null || ApartmentConversion.eligible(selectedBuilding))
            {
                GuiButton flats = new GuiButton(970, 20, height - 50, 180, 20, withoutFlats == null ? "As apartments: no"
                        : "As apartments: yes (" + ApartmentConversion.flats(selectedBuilding) + " flats)");
                flats.enabled = buildDirection != null && !buildDirection.isEmpty();
                buttonList.add(flats);
            }
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
        else if (isListPage()) //what r/c/i/o building, a category, or a search of everything
        {
            tfSearch = new GuiTextField(fontRendererObj, width / 2 - 50, height - 30, 100, 20);
            tfSearch.setMaxStringLength(40);
            tfSearch.setText(search);
            tfSearch.setFocused(true);
            String q = tfSearch.getText().trim();
            ArrayList<Building> houses;
            if (currentPage == 12)
            {
                houses = Building.searchAllBlueprints(q);
                if (listCategory != null)
                {
                    houses = BuildingCategory.in(houses, listCategory);
                }
            }
            else
            {
                houses = Building.getBuildingBlueprints(pageType(currentPage), q);
            }
            showList(houses);
        }
        else if (currentPage == 11)    // categories, with how many of each
        {
            ArrayList<Building> all = Building.searchAllBlueprints("");
            int x = width / 2 - 205, y = 65, idx = 1;
            categoryButtons.clear();
            for (String cat : BuildingCategory.ALL)
            {
                int n = BuildingCategory.in(all, cat).size();
                if (n == 0)
                {
                    continue;
                }
                categoryButtons.put(idx, cat);
                buttonList.add(new GuiButton(idx++, x, y, 135, 20, cat + " (" + n + ")"));
                x += 137;
                if (x + 135 > width / 2 + 210)
                {
                    x = width / 2 - 205;
                    y += 22;
                }
            }
        }
        else if (currentPage == 13)    // design a motel
        {
            int x = 20, y = 70;
            buttonList.add(new GuiButton(20, x, y, 150, 20, "Shape: " + new MotelPlan(motelShape, 1, 1, 1, "-x").shapeName()));
            buttonList.add(new GuiButton(21, x, y + 22, 150, 20, "Floors: " + motelFloors));
            buttonList.add(new GuiButton(22, x, y + 44, 20, 20, "-"));
            buttonList.add(new GuiButton(23, x + 130, y + 44, 20, 20, "+"));
            GuiButton less = new GuiButton(24, x, y + 66, 20, 20, "-"), more = new GuiButton(25, x + 130, y + 66, 20, 20, "+");
            less.enabled = more.enabled = motelShape != 'S';
            buttonList.add(less);
            buttonList.add(more);
            buttonList.add(new GuiButton(1001, (width / 2) - 100, height - 25, 100, 20, "Go Back"));
            buttonList.add(new GuiButton(969, (width / 2), height - 25, 100, 20, "Requirements"));
            updateMotel();
        }
        else if (currentPage == 14)    // design apartments
        {
            int x = 20, y = 70;
            buttonList.add(new GuiButton(30, x, y, 150, 20, "Kind: " + new ApartmentPlan(aptKind, 1, 1, 1, "-x").kindName()));
            buttonList.add(new GuiButton(31, x, y + 22, 150, 20, aptRows == 2 ? "Flats both sides of the hall" : "Flats one side of the hall"));
            buttonList.add(new GuiButton(32, x, y + 44, 20, 20, "-"));
            buttonList.add(new GuiButton(33, x + 130, y + 44, 20, 20, "+"));
            buttonList.add(new GuiButton(34, x, y + 66, 20, 20, "-"));
            buttonList.add(new GuiButton(35, x + 130, y + 66, 20, 20, "+"));
            buttonList.add(new GuiButton(1001, (width / 2) - 100, height - 25, 100, 20, "Go Back"));
            buttonList.add(new GuiButton(969, (width / 2), height - 25, 100, 20, "Requirements"));
            updateApartments();
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

        if (currentPage == 0 && guibutton.id >= 40 && guibutton.id <= 47)
        {
            NBTTagCompound data = new NBTTagCompound();
            ActionHandlers.putV3(data, "box", constructorLoc);
            if (guibutton.id <= 45)
            {
                int[][] step = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
                data.setInteger("ltr", step[guibutton.id - 40][0]);
                data.setInteger("ftb", step[guibutton.id - 40][1]);
                data.setInteger("up", step[guibutton.id - 40][2]);
                ServerActions.send("movePlacement", data);
                return;
            }
            ServerActions.send(guibutton.id == 46 ? "confirmPlacement" : "cancelPlacement", data);
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
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
            else if (guibutton.id == 10)
            {
                currentPage = 11;
                showPage();
            }
            else if (guibutton.id == 11)
            {
                listCategory = null;
                currentPage = 12;
                showPage();
            }
            else if (guibutton.id == 12)
            {
                currentPage = 13;
                showPage();
            }
            else if (guibutton.id == 13)
            {
                currentPage = 14;
                showPage();
            }
            else if (guibutton.id == 14)
            {
                if (buildDirection == null || buildDirection.isEmpty())
                {
                    mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                            "Stand in line with the constructor box, facing where the tower should go, and open it again"));
                    return;
                }
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "box", constructorLoc);
                data.setInteger("height", 12);
                data.setString("dir", buildDirection);
                ServerActions.send("buildTower", data);
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                return;
            }
        }
        else if (isListPage()) //chosen building to build
        {
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
            Building clicked = listedByButton.get(guibutton.id);
            if (clicked == null)
            {
                return;
            }
            previousPage = currentPage;
            withoutFlats = null;
            flatsNote = "";
            selectedBuilding = Building.getFromAllBuildings(clicked.displayName, clicked.type);
            try {
            	selectedBuilding.buildDirection = this.buildDirection;
            } catch(Exception e) {} //probably not stood in the right place, live-ware issue!

            preview = selectedBuilding == null ? null : new BuildingPreview(selectedBuilding);
            lastFrame = 0;
            currentPage = 10;
            showPage();
        }
        else if (currentPage == 11)    // a category
        {
            String cat = categoryButtons.get(guibutton.id);
            if (cat != null)
            {
                listCategory = cat;
                buildingOffset = 0;
                fixedBuildingCount = -1;
                search = "";
                currentPage = 12;
                showPage();
            }
        }
        else if (currentPage == 13)    // the motel designer
        {
            if (guibutton.id == 20)
            {
                motelShape = motelShape == 'S' ? 'L' : motelShape == 'L' ? 'U' : 'S';
            }
            else if (guibutton.id == 21)
            {
                motelFloors = motelFloors == 1 ? 2 : 1;
            }
            else if (guibutton.id == 22 || guibutton.id == 23)
            {
                motelRoomsA = Math.max(MotelPlan.minA(motelShape), Math.min(MotelPlan.MAX_ROOMS, motelRoomsA + (guibutton.id == 23 ? 1 : -1)));
            }
            else if (guibutton.id == 24 || guibutton.id == 25)
            {
                motelRoomsB = Math.max(1, Math.min(MotelPlan.MAX_ROOMS, motelRoomsB + (guibutton.id == 25 ? 1 : -1)));
            }
            else if (guibutton.id == 969)
            {
                previousPage = 13;
                currentPage = 8;
            }
            else if (guibutton.id == 1001)
            {
                goBack();
                return;
            }
            showPage();
        }
        else if (currentPage == 14)    // the apartment designer
        {
            if (guibutton.id == 30)
            {
                aptKind = ApartmentPlan.nextKind(aptKind);
                aptFloors = Math.min(aptFloors, ApartmentPlan.maxFloors(aptKind));
            }
            else if (guibutton.id == 31)
            {
                aptRows = aptRows == 2 ? 1 : 2;
            }
            else if (guibutton.id == 32 || guibutton.id == 33)
            {
                aptAcross = Math.max(1, Math.min(ApartmentPlan.MAX_ACROSS, aptAcross + (guibutton.id == 33 ? 1 : -1)));
            }
            else if (guibutton.id == 34 || guibutton.id == 35)
            {
                aptFloors = Math.max(1, Math.min(ApartmentPlan.maxFloors(aptKind), aptFloors + (guibutton.id == 35 ? 1 : -1)));
            }
            else if (guibutton.id == 969)
            {
                previousPage = 14;
                currentPage = 8;
            }
            else if (guibutton.id == 1001)
            {
                goBack();
                return;
            }
            showPage();
        }
        else if (currentPage == 8)   // building requirements (what blocks are need)
        {
            if (guibutton.displayString.contentEquals("Build it!") && ApartmentPlan.isGenerated(selectedBuilding.displayName))
            {
                ApartmentPlan plan = ApartmentPlan.parse(selectedBuilding.displayName);
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "box", constructorLoc);
                data.setString("kind", String.valueOf(plan.kind));
                data.setInteger("across", plan.across);
                data.setInteger("rows", plan.rows);
                data.setInteger("floors", plan.floors);
                data.setString("dir", plan.dir);
                ServerActions.send("buildApartments", data);
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                return;
            }
            if (guibutton.displayString.contentEquals("Build it!") && MotelPlan.isGenerated(selectedBuilding.displayName))
            {
                MotelPlan plan = MotelPlan.parse(selectedBuilding.displayName);
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "box", constructorLoc);
                data.setString("shape", String.valueOf(plan.shape));
                data.setInteger("floors", plan.floors);
                data.setInteger("roomsA", plan.roomsA);
                data.setInteger("roomsB", plan.roomsB);
                data.setString("dir", plan.dir);
                ServerActions.send("buildMotel", data);
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                return;
            }
            else if (guibutton.displayString.contentEquals("Build it!"))
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
                goBack();
            }
        }
        else if (currentPage == 10)
        {
        	if(guibutton.id == 969)
        	{
        		currentPage = 8;
        		showPage();
        	}
        	else if (guibutton.id == 970)
        	{
        		if (withoutFlats == null)
        		{
        			Building flats = Building.fromGenerated(ApartmentConversion.name(buildDirection, selectedBuilding.type,
        					selectedBuilding.displayName, false), selectedBuilding.type);
        			if (ApartmentConversion.flats(flats) == 0)
        			{
        				flatsNote = "\u00a7cThere's nowhere in it to live";
        			}
        			else
        			{
        				withoutFlats = selectedBuilding;
        				selectedBuilding = flats;
        				flatsNote = "";
        			}
        		}
        		else
        		{
        			selectedBuilding = withoutFlats;
        			withoutFlats = null;
        			flatsNote = "";
        		}
        		preview = new BuildingPreview(selectedBuilding);
        		lastFrame = 0;
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
            String chosen = selectedBuilding == null ? null : selectedBuilding.displayName;
            currentPage = MotelPlan.isGenerated(chosen) ? 13 : ApartmentPlan.isGenerated(chosen) ? 14 : 10;
        }
        else if (isListPage())
        {
            currentPage = currentPage == 12 && listCategory != null ? 11 : 1;
            buildingOffset = 0;
            fixedBuildingCount = -1;
            search = "";
            tfSearch = null;
        }
        else if (currentPage == 11 || currentPage == 13 || currentPage == 14)
        {
            currentPage = 1;
        }
        else
        {
            currentPage = 0;
        }
        showPage();
    }

    private final HashMap<Integer, String> categoryButtons = new HashMap<Integer, String>();

    /** the pages that list buildings to pick from */
    private boolean isListPage()
    {
        return currentPage == 2 || currentPage == 5 || currentPage == 6 || currentPage == 7 || currentPage == 9
                || currentPage == 12;
    }

    private static String pageType(int page)
    {
        return page == 2 ? "residential" : page == 5 ? "commercial" : page == 6 ? "industrial" : page == 7 ? "other" : "special";
    }

    /** a page of buildings as buttons: name, size, cost and author */
    private void showList(ArrayList<Building> houses)
    {
        listedByButton.clear();
        buildingsOnPage = 0;
        if (houses.isEmpty())
        {
            buttonList.add(new GuiButton(1, 10, 60, 300, 20, "Nothing found, go back and choose another"));
            return;
        }
        if (buildingOffset < 0 || buildingOffset >= houses.size())
        {
            buildingOffset = 0;
        }
        if (buildingOffset > 0)
        {
            buttonList.add(new GuiButton(501, 5, height - 20, 75, 20, "< Page"));
        }
        int x = 10, y = 60, idx = 1;
        for (int b = buildingOffset; b < houses.size(); b++)
        {
            Building building = houses.get(b);
            String realCost = "";
            if (theWorkers.size() > 1)
            {
                realCost = " (" + SimukraftReloaded.displayMoney((float)(building.blocksInBuilding * 0.02f) * theWorkers.size()) + ")";
            }
            GuiButton b1, b2, b3;
            buttonList.add(b3 = new GuiButton(idx + 300, x, y + 48, 120, 20, building.author));
            buttonList.add(b2 = new GuiButton(idx + 200, x, y + 32, 120, 20, SimukraftReloaded.displayMoney((float)(building.blocksInBuilding * 0.02f)) + realCost));
            buttonList.add(b1 = new GuiButton(idx + 100, x, y + 16, 120, 20, building.ltrCount + " x " + building.ftbCount + " x " + building.layerCount));
            b1.enabled = b2.enabled = b3.enabled = false;
            listedByButton.put(idx, building);
            buttonList.add(new GuiButton(idx, x, y, 120, 20, building.displayNameWithoutPK));
            idx++;
            buildingsOnPage++;
            x += 120;
            if ((x + 120) > width)
            {
                x = 10;
                y += 71;
            }
            if ((y + 20 + 20 + 20 + 20) > height)
            {
                if (b + 1 < houses.size())
                {
                    buttonList.add(new GuiButton(500, width - 80, height - 20, 75, 20, "Page >"));
                }
                break;
            }
        }
        if (fixedBuildingCount == -1)
        {
            fixedBuildingCount = buildingsOnPage;
        }
    }

    /** the motel as set on page 13: its blueprint, preview and whether there's room for it here */
    private void updateMotel()
    {
        motelRoomsA = Math.max(MotelPlan.minA(motelShape), motelRoomsA);
        if (buildDirection == null || buildDirection.isEmpty())
        {
            selectedBuilding = null;
            preview = null;
            motelSpace = "\u00a7cStand in line with the constructor box, facing where the motel should go, and open it again";
            return;
        }
        MotelPlan plan = new MotelPlan(motelShape, motelFloors, motelRoomsA, motelRoomsB, buildDirection);
        selectedBuilding = Building.fromMotelPlan(plan);
        preview = new BuildingPreview(selectedBuilding);
        try
        {
            String blocked = plan.obstruction(mc.theWorld, constructorLoc.x.intValue(), constructorLoc.y.intValue(), constructorLoc.z.intValue());
            motelSpace = blocked == null ? "\u00a7aThere's room for it here" : "\u00a7c" + blocked;
        }
        catch (Exception e)
        {
            motelSpace = "";
        }
    }

    private void drawMotelPage()
    {
        drawCenteredString(fontRendererObj, "Design a motel", width / 2, 55, 0xffffaa);
        drawCenteredString(fontRendererObj, "Main wing: " + motelRoomsA + " rooms", 95, 70 + 44 + 6, 0xffffff);
        drawCenteredString(fontRendererObj, motelShape == 'S' ? "\u00a78No side wings" : (motelShape == 'U' ? "Each side wing: " : "Side wing: ") + motelRoomsB + " rooms",
                95, 70 + 66 + 6, 0xffffff);
        int y = 165, colW = width / 2 - 30;
        if (selectedBuilding != null)
        {
            MotelPlan plan = MotelPlan.parse(selectedBuilding.displayName);
            y = detail("Rooms", plan.rooms() + " (guests from out of town pay to stay)", y, colW);
            y = detail("Size", selectedBuilding.ltrCount + " wide, " + selectedBuilding.ftbCount + " deep, " + selectedBuilding.layerCount + " high", y, colW);
            y = detail("Cost", SimukraftReloaded.displayMoney((float)(selectedBuilding.blocksInBuilding * 0.02f)), y, colW);
        }
        for (Object line : fontRendererObj.listFormattedStringToWidth(motelSpace, colW))
        {
            drawString(fontRendererObj, (String) line, 20, y, 0xffffff);
            y += 10;
        }
        drawString(fontRendererObj, "\u00a78Add more rooms later from its control box", 20, y + 4, 0xffffff);

        int px = width / 2 + 10, py = 65, pw = width / 2 - 20, ph = height - 65 - 32;
        drawRect(px, py, px + pw, py + ph, 0x40000000);
        if (preview != null && !preview.isEmpty())
        {
            long now = System.currentTimeMillis();
            if (!draggingPreview && lastFrame != 0)
            {
                preview.yaw += (now - lastFrame) * 0.02f;
            }
            lastFrame = now;
            preview.draw(px + 6, py + 6, pw - 12, ph - 12);
            drawCenteredString(fontRendererObj, "Drag to turn it", px + pw / 2, py + ph - 10, 0x808080);
        }
    }

    /** the apartments as set on page 14: the blueprint and its preview */
    private void updateApartments()
    {
        if (buildDirection == null || buildDirection.isEmpty())
        {
            selectedBuilding = null;
            preview = null;
            motelSpace = "\u00a7cStand in line with the constructor box, facing where the building should go, and open it again";
            return;
        }
        ApartmentPlan plan = new ApartmentPlan(aptKind, aptAcross, aptRows, aptFloors, buildDirection);
        selectedBuilding = Building.fromGenerated(plan.name(), "other");
        preview = new BuildingPreview(selectedBuilding);
        motelSpace = "\u00a77Its outline shows where it goes before the builders start, and what's in the way";
    }

    private void drawApartmentPage()
    {
        drawCenteredString(fontRendererObj, "Design apartments", width / 2, 55, 0xffffaa);
        drawCenteredString(fontRendererObj, "Flats along the hall: " + aptAcross, 95, 70 + 44 + 6, 0xffffff);
        drawCenteredString(fontRendererObj, "Floors: " + aptFloors + " (up to " + ApartmentPlan.maxFloors(aptKind) + ")", 95, 70 + 66 + 6, 0xffffff);
        int y = 165, colW = width / 2 - 30;
        if (selectedBuilding != null)
        {
            ApartmentPlan plan = ApartmentPlan.parse(selectedBuilding.displayName);
            y = detail("Flats", plan.flats() + " (" + (plan.flatSize() >= 4 ? "families" : "couples") + "; Sims move in and pay rent)", y, colW);
            y = detail("Size", selectedBuilding.ltrCount + " wide, " + selectedBuilding.ftbCount + " deep, " + selectedBuilding.layerCount + " high", y, colW);
            y = detail("Cost", SimukraftReloaded.displayMoney((float)(selectedBuilding.blocksInBuilding * 0.02f)), y, colW);
        }
        for (Object line : fontRendererObj.listFormattedStringToWidth(motelSpace, colW))
        {
            drawString(fontRendererObj, (String) line, 20, y, 0xffffff);
            y += 10;
        }
        int px = width / 2 + 10, py = 65, pw = width / 2 - 20, ph = height - 65 - 32;
        drawRect(px, py, px + pw, py + ph, 0x40000000);
        if (preview != null && !preview.isEmpty())
        {
            long now = System.currentTimeMillis();
            if (!draggingPreview && lastFrame != 0)
            {
                preview.yaw += (now - lastFrame) * 0.02f;
            }
            lastFrame = now;
            preview.draw(px + 6, py + 6, pw - 12, ph - 12);
            drawCenteredString(fontRendererObj, "Drag to turn it", px + pw / 2, py + ph - 10, 0x808080);
        }
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

        draggingPreview = (currentPage == 10 || currentPage == 13 || currentPage == 14) && k == 0 && i > width / 2 + 10 && j > 65 && j < height - 32;
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
