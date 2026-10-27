package ashjack.simukraftreloaded.client.Gui.blocks;

import ashjack.simukraftreloaded.core.building.MotelPlan;
import ashjack.simukraftreloaded.common.military.Armory;
import ashjack.simukraftreloaded.core.building.ApartmentConversion;
import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.ActionHandlers;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.client.Gui.folk.GuiCourierTasks;
import ashjack.simukraftreloaded.client.Gui.folk.GuiEmployFolk;
import ashjack.simukraftreloaded.client.Gui.folk.GuiMerchant;
import ashjack.simukraftreloaded.client.Gui.folk.GuiShowEmployees;
import ashjack.simukraftreloaded.client.Gui.other.GuiBeamPlayerTo;
import ashjack.simukraftreloaded.common.ClientMirror;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.common.Care;
import ashjack.simukraftreloaded.common.jobs.Workshop;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

public class GuiControlBox extends GuiScreen
{
    private int mouseCount = 0;

    /** location of this control box */
    public V3 location;
    /** reference to the building object this control box is attached to */
    public Building theBuilding = null;
    /** hold a reference to a folk that is passed in */
    public FolkData theFolk = null;
    /** used to limit amount of people that can be employed at this building (workplaces only) */
    public int employeeCount = 0; // used to limit amount of people that can be employed if this is a workplace building
    private HashMap employees = new HashMap();
    private EntityPlayer playerWhoClickedIt = null;

    /** regular constructor */
    public GuiControlBox(V3 location, EntityPlayer thePlayer)
    {
        this.location = location.clone();
        // No reload from disk: it replaced the live building list, losing unsaved tenants and orphaning Building references
        theBuilding = Building.getBuilding(location);
        playerWhoClickedIt = thePlayer;
    }

    /** constructor called after choosing to hire staff */
    public GuiControlBox(V3 location, FolkData folk)     //called after choosing hired staff
    {
        this.location = location;
        // No reload from disk: it replaced the live building list, losing unsaved tenants and orphaning Building references
        theBuilding = Building.getBuilding(location);
        theFolk = folk;
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void updateScreen()
    {
        // opened before the server's sync brought this building over (a box placed a moment ago): pick it up when it does
        if (theBuilding == null && ClientMirror.syncCount != seenSync)
        {
            seenSync = ClientMirror.syncCount;
            theBuilding = Building.getBuilding(location);
            if (theBuilding != null)
            {
                initGui();
            }
        }
    }

    private int seenSync = ClientMirror.syncCount;
    /** how many flats this building would make, worked out once (-1 not yet) */
    private int flatsHere = -1;

    @Override
    public void initGui()
    {
        buttonList.clear();
        buttonList.add(new GuiButton(0, 5, 5, 50, 20, "Done"));

        if (theBuilding == null)
        {
            buttonList.add(new GuiButton(1, 10, height - 30, 100, 20, "Fix House"));
            return;
        }

        if (theBuilding.buildingComplete)
        {
            buttonList.add(new GuiButton(1000, width - 110, 5, 100, 20, "Demolish!"));
            buttonList.add(new GuiButton(1001, width - 110,25,100,20,"Rotate Stairs"));
        }

        buttonList.add(new GuiButton(21, width - 110, height - 30, 100, 20, "Show Employees"));
        buttonList.add(new GuiButton(30, width - 110, height - 50, 100, 20, "Beam me to.."));

        // a decorative building can be turned into flats
        if (ApartmentConversion.eligible(theBuilding) && theBuilding.buildingComplete && theBuilding.buildDirection != null && !theBuilding.buildDirection.isEmpty())
        {
            if (flatsHere < 0)
            {
                flatsHere = ApartmentConversion.flats(Building.fromGenerated(ApartmentConversion.name(theBuilding.buildDirection,
                        theBuilding.type, theBuilding.displayName, true), theBuilding.type));
            }
            GuiButton b = new GuiButton(1102, 10, height - 30, 200, 20, flatsHere > 0
                    ? "Convert to apartments (" + flatsHere + " flats)" : "Nowhere in it to live");
            b.enabled = flatsHere > 0;
            buttonList.add(b);
        }

        // a motel grows away from its constructor box: the main wing longer, the side wings deeper
        MotelPlan motel = MotelPlan.parse(theBuilding.displayName);
        if (motel != null && theBuilding.buildingComplete)
        {
            if (motel.shape != 'U')
            {
                GuiButton b = new GuiButton(1100, 10, height - 30, 160, 20, motel.shape == 'S' ? "Add a room" : "Add a room: main wing");
                b.enabled = motel.roomsA < MotelPlan.MAX_ROOMS;
                buttonList.add(b);
            }
            if (motel.shape != 'S')
            {
                GuiButton b = new GuiButton(1101, 10, height - 52, 160, 20, motel.shape == 'U' ? "Add a room to each side wing" : "Add a room: side wing");
                b.enabled = motel.roomsB < MotelPlan.MAX_ROOMS;
                buttonList.add(b);
            }
        }

        boolean medical = Care.isMedical(theBuilding);
        if (theBuilding.type.contentEquals("commercial") || theBuilding.type.contentEquals("industrial") || theBuilding.type.contentEquals("special") || medical)
        {
            int down = 70;
            int idx = 2;    ///display list of employees and their fire buttons
            employeeCount = 0;
            employees.clear();

            for (int fc = 0; fc < SimukraftReloaded.folks().size(); fc++)
            {
                FolkData folk = (FolkData) SimukraftReloaded.folks().get(fc);

                if (theBuilding.primaryXYZ.isSameCoordsAs(folk.employedAt, true, true))
                {
                	buttonList.add(new GuiButton(idx, width - 140, down - 6, 130, 20, "Fire " + folk.name));
                    
                    employees.put((idx + 100), folk.name);

                    if (theBuilding.displayName.contains("Depot"))
                    {
                        buttonList.add(new GuiButton((idx + 100), width - 190, down - 6, 50, 20, "Tasks"));
                    }

                    down += 20;
                    employeeCount++;
                    idx++;
                }
            }
        }

        // clinics and hospitals: medics
        if (medical)
        {
            GuiButton b;
            buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Medic"));
            if (employeeCount >= Care.medicCapacity(theBuilding))
            {
                b.enabled = false;
            }
        }

        // a smithy: blacksmiths at the forge, and a fletcher
        if (Workshop.isSmithy(theBuilding))
        {
            int smiths = 0, fletchers = 0;
            for (FolkData folk : SimukraftReloaded.folks())
            {
                if (theBuilding.primaryXYZ.isSameCoordsAs(folk.employedAt, true, true))
                {
                    if (folk.vocation == Vocation.FLETCHER)
                    {
                        fletchers++;
                    }
                    else if (folk.vocation == Vocation.BLACKSMITH)
                    {
                        smiths++;
                    }
                }
            }
            GuiButton b;
            if (theBuilding.buildingComplete && theBuilding.level < 2)
            {
                buttonList.add(new GuiButton(1043, 10, height - 52, 205, 20, "Upgrade forge for diamond ("
                        + SimukraftReloaded.displayMoney(ashjack.simukraftreloaded.core.building.Upgrades.SMITHY_COST) + ")"));
            }
            buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Blacksmith"));
            b.enabled = smiths < 2;
            buttonList.add(b = new GuiButton(1, 115, height - 30, 100, 20, "Hire Fletcher"));
            b.enabled = fletchers < 1;
        }

        // a masonry: stonemasons
        if (Workshop.isMasonry(theBuilding))
        {
            GuiButton b;
            buttonList.add(b = new GuiButton(1, 10, height - 30, 110, 20, "Hire Stonemason"));
            b.enabled = employeeCount < 2;
        }

        ////commercial buildings
        if (theBuilding.type.contentEquals("commercial"))
        {
            if (theBuilding.displayName.contains("Bakery"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Baker"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            if (theBuilding.displayName.contains("Grocery Store"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Grocer"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            if (theBuilding.displayName.contains("Butchers"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Butcher"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }
            if (theBuilding.displayName.contains("Burgers"))
            {
            	ArrayList<FolkData> employees=FolkData.getFolksByEmployedAt(theBuilding.primaryXYZ);
            	Boolean flag=false;
            	
            	GuiButton b1;
                buttonList.add(b1 = new GuiButton(1, 10, height - 30, 100, 20, "Hire Manager"));
                for(FolkData folk:employees) {
                	if(folk.employedAt !=null && folk.employedAt.isSameCoordsAs(theBuilding.primaryXYZ, true, true)
                			&& folk.vocation==Vocation.BURGERSMANAGER) {
                		flag=true; break;
                	}
                		
                }
                if (flag) {b1.enabled=false;}
                
                GuiButton b2; flag=false;
                buttonList.add(b2 = new GuiButton(2, 10, height - 50, 100, 20, "Hire Fry Cook"));
                for(FolkData folk:employees) {
                	if(folk.employedAt !=null && folk.employedAt.isSameCoordsAs(theBuilding.primaryXYZ, true, true)
                			&& folk.vocation==Vocation.BURGERSFRYCOOK) {
                		flag=true; break;
                	}
                		
                }
                if (flag) {b2.enabled=false;}
                
                GuiButton b3; flag=false;
                buttonList.add(b3 = new GuiButton(3, 10, height - 70, 100, 20, "Hire Waiter"));
                for(FolkData folk:employees) {
                	if(folk.employedAt !=null && folk.employedAt.isSameCoordsAs(theBuilding.primaryXYZ, true, true)
                			&& folk.vocation==Vocation.BURGERSWAITER) {
                		flag=true; break;
                	}
                		
                }
                if (flag) {b3.enabled=false;}

            }
            
        }

        /////industrial buildings
        if (theBuilding.type.contentEquals("industrial"))
        {
            ////lumbermill control panel
            if (theBuilding.displayName.contains("Lumbermill"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Lumberjack"));

                if (employeeCount > 4)
                {
                    b.enabled = false;
                }

                if (BlockMarker.markers().size() == 1)
                {
                    buttonList.add(new GuiButton(20, width / 2 + 100, height - 30, 100, 20, "Set Lumber area"));
                }
            }

            //// Builder's merchant
            if (theBuilding.displayName.contains("Builders Merchant"))
            {
                GuiButton b;
                GuiButton b2;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Merchant"));
                buttonList.add(b2 = new GuiButton(25, 10, height - 50, 100, 20, "Buy/Sell"));

                if (!SimukraftReloaded.isDayTime() || employeeCount == 0)
                {
                    b2.enabled = false;
                }

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            //barracks control panel
            if (theBuilding.displayName.contains("Barracks"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Train Soldier"));

                if (employeeCount >= theBuilding.soldierCapacity())
                {
                    b.enabled = false;
                }
            }

            //sheep farm control panel
            if (theBuilding.displayName.contains("Sheep Farm"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Shepherd"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }
            
            if (theBuilding.displayName.contains("Egg Farm"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Egg Farmer"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            //cattle farm control panel
            if (theBuilding.displayName.contains("Cattle Farm"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Cattle farmer"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            //pig farm control panel
            if (theBuilding.displayName.contains("Pig Farm"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Pig farmer"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            //chicken farm control panel
            if (theBuilding.displayName.contains("Chicken Farm"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Chicken farmer"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            /////depot
            if (theBuilding.displayName.contains("Depot"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Courier"));

                if (employeeCount > 3)
                {
                    b.enabled = false;
                }
            }

            /// glass factory
            if (theBuilding.displayName.contains("Glass Factory"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Glass maker"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }
            
            if (theBuilding.displayName.contains("Brick Factory"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Brick maker"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }

            //fishing dock
            if (theBuilding.displayName.contains("Fishing Dock"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Fisherman"));

                if (employeeCount > 1)
                {
                    b.enabled = false;
                }
            }
            
            //dairy farm
            if (theBuilding.displayName.contains("Dairy Farm"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Dairy farmer"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }
            
            if (theBuilding.displayName.contains("Warehouse"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 130, 20, "Hire Warehouse keeper"));

                if (employeeCount > 1)
                {
                    b.enabled = false;
                }

                buttonList.add(new GuiButton(1040, 145, height - 30, 60, 20, "Stock"));
                if (theBuilding.buildingComplete)
                {
                    int dug = ashjack.simukraftreloaded.core.building.Basement.count(theBuilding);
                    GuiButton dig = new GuiButton(1041, 10, height - 52, 130, 20, "Add a basement (" + dug + "/"
                            + ashjack.simukraftreloaded.core.building.Basement.MAX + ")");
                    dig.enabled = dug < ashjack.simukraftreloaded.core.building.Basement.MAX;
                    buttonList.add(dig);
                    String chests = ashjack.simukraftreloaded.core.building.Upgrades.nextChests(theBuilding);
                    if (ashjack.simukraftreloaded.core.building.Upgrades.hasIronChests() && chests != null)
                    {
                        buttonList.add(new GuiButton(1042, 145, height - 52, 100, 20, chests));
                    }
                }
            }

            //Cheese Factory
            if (theBuilding.displayName.contains("Cheese Factory"))
            {
                GuiButton b;
                buttonList.add(b = new GuiButton(1, 10, height - 30, 100, 20, "Hire Cheesemaker"));

                if (employeeCount > 0)
                {
                    b.enabled = false;
                }
            }



            int down = 70;
            int idx = 2;

            for (int fc = 0; fc < SimukraftReloaded.folks().size(); fc++)
            {
                FolkData folk = (FolkData) SimukraftReloaded.folks().get(fc);

                if (theBuilding.primaryXYZ.isSameCoordsAs(folk.employedAt, true, true))
                {
                    if (theBuilding.displayName.contains("Barracks"))
                    {
                        buttonList.add(new GuiButton(idx, width - 210, down - 6, 200, 20, "Dismiss " + folk.name));
                    }
                    else if (theBuilding.displayName.contains("Burgers")) {
                    	buttonList.add(new GuiButton(idx,width-210,down-6,200,20,"Fire "+folk.vocation.toString()));
                    }
                    else
                    {
                        buttonList.add(new GuiButton(idx, width - 140, down - 6, 130, 20, "Fire " + folk.name));
                    }

                    down += 20;
                }
            }
        }
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
            drawCenteredString(fontRendererObj, "Building Control Panel", width / 2, 17, 0xffffff);

            if (theBuilding != null)
            {
                String author = "";

                if (theBuilding.author != null && !theBuilding.author.contentEquals(""))
                {
                    author = " by " + theBuilding.author;
                }

                String isComplete = "Under construction";

                if (theBuilding.buildingComplete)
                {
                    isComplete = "Active Building";
                }

                fontRendererObj.drawString("Building : " + theBuilding.displayNameWithoutPK + author, 5, 37, 0xffff80);
                fontRendererObj.drawString("Type : " + theBuilding.type + " (" + isComplete + ")"
                        + (theBuilding.type.equals("commercial") || theBuilding.type.equals("industrial")
                           ? "   Business funds: " + SimukraftReloaded.displayMoney(ashjack.simukraftreloaded.core.town.Economy.funds(theBuilding.primaryXYZ)) : ""),
                        5, 47, 0xffff80);

                if (theBuilding.type.contentEquals("residential"))
                {
                    String s = "";

                    if (theBuilding.tennants.size() > 1 || theBuilding.tennants.size() == 0)
                    {
                        s = "s";
                    }

                    fontRendererObj.drawString(theBuilding.tennants.size() + " Resident" + s + " :", 5, 57, 0xffff80);
                    int down = 70;

                    for (int t = 0; t < theBuilding.tennants.size(); t++)
                    {
                        String folkname = theBuilding.tennants.get(t);
                        fontRendererObj.drawString(folkname , 20, down, 0xffffa0);
                        down += 20;
                    }
                }
                else if (MotelPlan.isGenerated(theBuilding.displayName))
                {
                    MotelPlan motel = MotelPlan.parse(theBuilding.displayName);
                    int booked = 0, rooms = 0;
                    for (Building b : SimukraftReloaded.buildings())
                    {
                        if (b.isHotel() && b.displayName.startsWith("Motel room"))
                        {
                            rooms++;
                            booked += b.tennants.isEmpty() ? 0 : 1;
                        }
                    }
                    fontRendererObj.drawString(motel.rooms() + " rooms, " + motel.shapeName().toLowerCase() + ", "
                            + (motel.floors == 1 ? "1 floor" : motel.floors + " floors"), 5, 57, 0xffff80);
                    fontRendererObj.drawString(booked + " of the town's " + rooms + " motel rooms have guests", 5, 70, 0xffffa0);
                    if (theBuilding.buildingComplete)
                    {
                        fontRendererObj.drawString("Adding rooms checks there's space first, then the builder at the", 5, 90, 0xaaaaaa);
                        fontRendererObj.drawString("constructor box builds only the new part", 5, 100, 0xaaaaaa);
                    }
                }
                else if (theBuilding.type.contentEquals("industrial") || theBuilding.type.contentEquals("commercial") || theBuilding.type.contentEquals("special")
                         || Care.isMedical(theBuilding))
                {
                    if (Care.isMedical(theBuilding))
                    {
                        int patients = 0;
                        for (FolkData p : SimukraftReloaded.folks())
                        {
                            if (theBuilding.primaryXYZ.toString().equals(p.careAt))
                            {
                                patients++;
                            }
                        }
                        fontRendererObj.drawString("Patients : " + patients + " of " + Care.beds(theBuilding) + " beds", 120, 57, 0xffffa0);
                    }
                    fontRendererObj.drawString(theBuilding.displayName.contains("Barracks")
                            ? "Soldiers : " + employeeCount + " of " + theBuilding.soldierCapacity()
                              + "   (armory: chests in the barracks)"
                            : Care.isMedical(theBuilding) ? "Medics : " + employeeCount + " of " + Care.medicCapacity(theBuilding)
                            : "Employees :", 5, 57, 0xffff80);
                    int down = 70;

                    for (int fc = 0; fc < SimukraftReloaded.folks().size(); fc++)
                    {
                        FolkData folk = (FolkData) SimukraftReloaded.folks().get(fc);

                        if (theBuilding.primaryXYZ.isSameCoordsAs(folk.employedAt, true, true))
                        {
                            fontRendererObj.drawString(folk.name + " (" + folk.age + ") - "
                                                    + (folk.vocation == Vocation.SOLDIER ? Armory.Rank.of(folk.levelSoldier).title
                                                       + ", level " + (int) folk.levelSoldier : folk.vocation.toString()), 20, down, 0xffffa0);
                            down += 20;
                        }
                    }
                }
                else if (theBuilding.type.contentEquals("other"))
                {
                }
            }
            else
            {
                fontRendererObj.drawString("ERROR: lost info on this building (" + this.location.toString() + ")", 5, 77, 0xff0000);
                fontRendererObj.drawString("only click 'fix house' below if this Building IS a house", 5, 97, 0xff0000);
            }

            super.drawScreen(i, j, f);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(GuiButton guibutton)
    {
        if (!guibutton.enabled)
        {
            return;
        }

        if (guibutton.id == 0) // /cancel button
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            return;
        }
        else if (guibutton.id == 1102)
        {
            NBTTagCompound data = new NBTTagCompound();
            ActionHandlers.putV3(data, "box", location);
            ServerActions.send("convertApartments", data);
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        if (guibutton.id == 1041 || guibutton.id == 1042 || guibutton.id == 1043)
        {
            NBTTagCompound data = new NBTTagCompound();
            ActionHandlers.putV3(data, "box", location);
            ServerActions.send(guibutton.id == 1041 ? "addBasement" : guibutton.id == 1042 ? "upgradeChests" : "upgradeSmithy", data);
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        else if (guibutton.id == 1100 || guibutton.id == 1101)
        {
            MotelPlan motel = MotelPlan.parse(theBuilding.displayName);
            NBTTagCompound data = new NBTTagCompound();
            ActionHandlers.putV3(data, "box", location);
            data.setInteger("roomsA", motel.roomsA + (guibutton.id == 1100 ? 1 : 0));
            data.setInteger("roomsB", motel.roomsB + (guibutton.id == 1101 ? 1 : 0));
            ServerActions.send("upgradeMotel", data);
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        else if (guibutton.displayString.contentEquals("Hire Lumberjack"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.LUMBERJACK);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Baker"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.BAKER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Train Soldier"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.SOLDIER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Shepherd"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.SHEPHERD);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Grocer"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.GROCER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Courier"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.COURIER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Merchant"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.MERCHANT);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Butcher"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.BUTCHER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Egg Farmer"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.EGGFARMER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Pig farmer"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.PIGFARMER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Cattle farmer"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.CATTLEFARMER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Chicken farmer"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.CHICKENFARMER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Glass maker"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.GLASSMAKER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Brick maker"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.BRICKMAKER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Fisherman"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.FISHERMAN);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Dairy farmer"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.DAIRYFARMER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Blacksmith"))
        {
            mc.currentScreen = null;
            mc.displayGuiScreen(new GuiEmployFolk(this.location, "", Vocation.BLACKSMITH));
        }
        else if (guibutton.displayString.contentEquals("Hire Stonemason"))
        {
            mc.currentScreen = null;
            mc.displayGuiScreen(new GuiEmployFolk(this.location, "", Vocation.STONEMASON));
        }
        else if (guibutton.displayString.contentEquals("Hire Fletcher"))
        {
            mc.currentScreen = null;
            mc.displayGuiScreen(new GuiEmployFolk(this.location, "", Vocation.FLETCHER));
        }
        else if (guibutton.displayString.contentEquals("Hire Medic"))
        {
            mc.currentScreen = null;
            mc.displayGuiScreen(new GuiEmployFolk(this.location, "", Vocation.MEDIC));
        }
        else if (guibutton.displayString.contentEquals("Hire Warehouse keeper"))
        {
            mc.currentScreen = null;
            mc.displayGuiScreen(new GuiEmployFolk(this.location, "", Vocation.WAREHOUSE));
        }
        else if (guibutton.id == 1040)
        {
            mc.displayGuiScreen(new GuiWarehouse(this.location));
        }
        else if (guibutton.displayString.contentEquals("Hire Cheesemaker"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.CHEESEMAKER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Manager"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.BURGERSMANAGER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Fry Cook"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.BURGERSFRYCOOK);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contentEquals("Hire Waiter"))
        {
            mc.currentScreen = null;
            GuiEmployFolk ui = new GuiEmployFolk(this.location, "", Vocation.BURGERSWAITER);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.displayString.contains("Fire ") || guibutton.displayString.contains("Dismiss "))
        {
            String fname = guibutton.displayString.substring(guibutton.displayString.indexOf(" ")).trim();
            guibutton.enabled = false;

            if (theBuilding.displayName.contains("Depot"))
            {
                for (int x = 0; x < buttonList.size(); x++)
                {
                    GuiButton but = (GuiButton) buttonList.get(x);

                    if (but.yPosition == guibutton.yPosition)
                    {
                        but.enabled = false;
                    }
                }
            }

            ActionHandlers.sendFire(fname);
        }
        else if (guibutton.displayString.contentEquals("Fix House"))
        {
            sendForBuilding("fixHouse", location);
            mc.displayGuiScreen(null);
        }
        else if (guibutton.displayString.contentEquals("Set Lumber area"))
        {
            sendForBuilding("setLumberArea", theBuilding.primaryXYZ);
            guibutton.enabled = false;
        }
        else if (guibutton.id == 21)     // show employees button pressed
        {
            GuiScreen gui = new GuiShowEmployees();
            mc.displayGuiScreen(null);
            mc.displayGuiScreen(gui);
        }
        else if (guibutton.displayString.contentEquals("Tasks"))
        {
            String folkname = (String) employees.get(guibutton.id);
            GuiScreen gui = new GuiCourierTasks(this.location, folkname, playerWhoClickedIt);
            mc.displayGuiScreen(null);
            mc.displayGuiScreen(gui);
            //// Builder's merchant buysell button
        }
        else if (guibutton.displayString.contentEquals("Buy/Sell"))
        {
            GuiScreen ui = new GuiMerchant();
            mc.displayGuiScreen(null);
            mc.displayGuiScreen(ui);
        }
        else if (guibutton.id == 30)   //Beam me to...
        {
            GuiScreen ui = new GuiBeamPlayerTo(playerWhoClickedIt);
            mc.displayGuiScreen(null);
            mc.displayGuiScreen(ui);
        } 
        else if (guibutton.displayString.contentEquals("Rotate Stairs")) {
        	sendForBuilding("rotateStairs", theBuilding.primaryXYZ);
        }
        else if (guibutton.displayString.contentEquals("Demolish!"))
        {
            guibutton.displayString = "Really? Click again"; // it can't be undone
        }
        else if (guibutton.displayString.contentEquals("Really? Click again"))
        {
            sendForBuilding("demolish", theBuilding.primaryXYZ);
            mc.displayGuiScreen(null);
        }
    }

    private void sendForBuilding(String action, V3 box)
    {
        NBTTagCompound data = new NBTTagCompound();
        ActionHandlers.putV3(data, "box", box);
        ServerActions.send(action, data);
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
        mc.setIngameFocus();
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
}
