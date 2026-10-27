package ashjack.simukraftreloaded.core.building;

import java.util.Random;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.common.WorldUtil;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.infrastructure.Infrastructure;
import ashjack.simukraftreloaded.infrastructure.Infrastructure.infrastructures;
import ashjack.simukraftreloaded.infrastructure.InfrastructureElectricity;
import ashjack.simukraftreloaded.infrastructure.InfrastructureWater;
import ashjack.simukraftreloaded.proxies.CommonProxy;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

//NOTE: Buildings are stored in .minecraft/mods/simukraft/buildings/...

public class Building implements Serializable
{
    private static final long serialVersionUID = -1132989807904279141L;
    public String displayName;
    public String type; // / residential commercial (folder name)
    public transient Block[] structure; // / holds a 3D array of block IDs for this
    public transient int[] structureSub;
    public String description = "None";
    /** CAT= in the blueprint, or null to work it out from the name (see BuildingCategory) */
    public String category = null;
    /**
     * how far the building is moved from where its constructor box would put it: left/right, away from/towards the
     * box, up/down (blueprint directions, so they turn with the build direction). Set while it's being placed.
     */
    public int offsetLtr = 0, offsetFtb = 0, offsetUp = 0;
    /**
     * the constructor box it's waiting at (V3 text) while its placement is checked, before anyone builds it; null
     * once it's been given the go-ahead. A string so it reaches clients (conBoxLocation doesn't).
     */
    public String awaitingAt = null;
    /** what the placement check last found, for the constructor box screen */
    public String placementStatus = "";

    /**
     * where the building is laid out from: its constructor box, moved by the offset. Everything that works out where
     * a building's blocks are (the builder, the warehouse's footprint, the motel and flats maths) goes from here.
     */
    public V3 layoutBox(V3 box)
    {
        if (box == null || (offsetLtr == 0 && offsetFtb == 0 && offsetUp == 0) || buildDirection == null || buildDirection.isEmpty())
        {
            return box;
        }
        int[] step = MotelPlan.stepToWorld(buildDirection, offsetLtr, offsetFtb);
        return new V3(box.x + step[0], box.y + offsetUp, box.z + step[1], box.theDimension);
    }

    /** structureSub for a cell the builder leaves as it is (with a null structure entry) */
    public static final int KEEP = -1;
    public transient Infrastructure infrastructureRequirement = null;
    public transient Infrastructure infrastructureProducer = null;
    // building
    public int layerCount = 0;
    public int ftbCount = 0;
    public int ltrCount = 0;
    public String dimensions = "";
    public int elevationLevel = 0;
    public V3 primaryXYZ; // first block placed and where control panel block is
    // located
    public V3 livingXYZ; // / where folks should walkto to 'live' in the house
    // or work in workplace building
    public boolean buildingComplete = false;
    public int capacity = -1; // single person or mixed couple by default (-1)
    public String buildDirection = "";
    public V3 lumbermillMarker = null; // Lumbermill only used as starting point
    // to search for trees, blank means use
    // primaryXYZ
    public int blocksInBuilding = 0; // calculated when loadBuilding called
    public String pk = "0"; // unique ID, used for the store id (Primary key)
    public String author = "Satscape"; // author of the building (blank if it's
    // a built-in building)
    public String displayNameWithoutPK = "";
    public Float rent = 0f;
    public ArrayList<String> tennants = new ArrayList<String>();
    // transient: saved explicitly in toSaveLines(), and too big to send to clients
    public transient ArrayList<V3> blockLocations = new ArrayList<V3>();

    public transient HashMap<ItemStack, Integer> requirements = new HashMap<ItemStack, Integer>();
    /** requirements as "item|damage|count", for MirrorSync: ItemStacks can't be serialised */
    public ArrayList<String> syncedRequirements;
    public transient V3 conBoxLocation = null; // transient for MirrorSync; saved in toSaveLines()
    private transient static ArrayList<Building> buildingsRes = new ArrayList<Building>();
    private transient static ArrayList<Building> buildingsCom = new ArrayList<Building>();
    private transient static ArrayList<Building> buildingsInd = new ArrayList<Building>();
    private transient static ArrayList<Building> buildingsOth = new ArrayList<Building>();
    private transient static ArrayList<Building> buildingsSpec = new ArrayList<Building>();

    /** null for a normal building; ROOM_APARTMENT or ROOM_HOTEL when this "building" is a single Room Box unit */
    public String roomMode = null;
    /** how many folks the room is for: 1 single, 2 couple, 4 family */
    public int roomSize = 2;
    /** hotel rooms: nights left for the current guests */
    public int stayNightsLeft = 0;

    public static final String ROOM_APARTMENT = "apartment";
    public static final String ROOM_HOTEL = "hotel";
    public static final float APARTMENT_RENT_PER_FOLK = 0.75f;
    public static final float HOTEL_RATE_PER_FOLK = 1.5f;

    /** special blocks inside the building, used by the mod */
    public ArrayList<V3> blockSpecial = new ArrayList<V3>();
    
    public Building()
    {
      //sk2 uses this constructor
    }

    /** transient fields arrive as null when a building is sent to a client; give them empty values */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        blockLocations = new ArrayList<V3>();
        requirements = new HashMap<ItemStack, Integer>();
    }

    /** constructor for making new building */
    public Building(String fname, String theType)
    {
        type = theType;
        displayName = fname;
        buildingComplete = false;

        if (requirements == null)
        {
            requirements = new HashMap<ItemStack, Integer>();
        }
    }

    public Building(String fname, String theType, V3 pxyz, V3 lxyz,
                    boolean isComplete)
    {
        type = theType;
        displayName = fname; // no path, just the filename without the .txt on
        // the end
        primaryXYZ = pxyz;
        livingXYZ = lxyz;
        buildingComplete = isComplete;

        if (requirements == null)
        {
            requirements = new HashMap<ItemStack, Integer>();
        }
    }

    @Override
    public Building clone() {
    	Building ret=new Building();
    	ret.displayName=this.displayName;
    	ret.type=this.type;
    	ret.layerCount=this.layerCount;
    	ret.infrastructureRequirement=this.infrastructureRequirement;
    	ret.infrastructureProducer=this.infrastructureProducer;
    	ret.ftbCount=this.ftbCount;
    	ret.ltrCount=this.ltrCount;
    	ret.dimensions=this.dimensions;
    	ret.elevationLevel=this.elevationLevel;
    	ret.description=this.description;
    	try {ret.primaryXYZ=this.primaryXYZ.clone();}catch(Exception e){ret.primaryXYZ=null;}
    	try {ret.livingXYZ=this.livingXYZ.clone();} catch(Exception e) {ret.livingXYZ=null;}
    	ret.buildingComplete=this.buildingComplete;
    	ret.capacity=-1;
    	ret.buildDirection=this.buildDirection;
    	ret.blocksInBuilding=this.blocksInBuilding;
    	ret.pk=this.pk;
    	ret.author=this.author;
    	ret.displayNameWithoutPK=this.displayNameWithoutPK;
    	ret.rent=this.rent;
    	ret.tennants=new ArrayList<String>();
    	ret.blockLocations=new ArrayList<V3>();
    	ret.blockSpecial=new ArrayList<V3>();
        ret.loadStructure();
    	return ret;
    }
    
    /** get an ArrayList<V3> of all special blocks in this building that have the specified meta */
    public ArrayList<V3> getSpecialBlocks(int meta) {
    	ArrayList<V3> ret=new ArrayList<V3>();
    	for(V3 v3: blockSpecial) {
    		if (v3.meta == meta) {
    			ret.add(v3);
    		}
    	}
    	return ret;
    }
    
    // ** evicts/removes tennant from house (died or summat) */
    public void removeTennant(String tennant)
    {
        for (int t = 0; t < tennants.size(); t++)
        {
            String ten = tennants.get(t);

            if (ten.contentEquals(tennant))
            {
                tennants.remove(t);
                break;
            }
        }
    }

    /**
     * only used by Building now to load structures in a thread to reduce lag
     */
    private void loadStructure()
    {    	
        try
        {
            if (requirements != null)
            {
                requirements.clear();
            }
            else
            {
                requirements = new HashMap<ItemStack, Integer>();
            }

            displayNameWithoutPK = displayName;

            if (displayName.startsWith("PKID"))   // PKID123-my house.txt
            {
                int hyphen = displayName.indexOf("-");
                this.pk = displayName.substring(4, hyphen);
                displayNameWithoutPK = displayName.substring(hyphen + 1);
            }

            blocksInBuilding = 0;
            ApartmentConversion.Name flats = ApartmentConversion.parse(displayName);
            if (flats != null)
            {
                loadAsFlats(flats);
                return;
            }
            BufferedReader br;
            MotelPlan motel = MotelPlan.parse(displayName);
            if (motel != null)
            {
                // made to order, not from a file: generated again from its name
                br = new BufferedReader(new StringReader(motel.blueprint()));
            }
            else
            {
                File f = new File(SimukraftReloaded.getSimukraftFolder() + "/buildings/"
                                  + type + "/" + displayName + ".txt");

                if (!f.exists())
                {
                    return;
                }
                br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
            }
            String strLine;
            strLine = br.readLine().toString().toLowerCase().trim(); // dimensions
            String[] d = strLine.split("x");
            int[] di = { 0, 0, 0 };
            di[0] = Integer.parseInt(d[0]);
            di[1] = Integer.parseInt(d[1]);
            di[2] = Integer.parseInt(d[2]);
            structure = new Block[di[0] * di[1] * di[2]];
            structureSub = new int[di[0] * di[1] * di[2]];
            this.ltrCount = di[0];
            this.ftbCount = di[1];
            this.layerCount = di[2];
            this.dimensions = d[0] + "x" + d[1] + "x" + d[2];
            strLine = br.readLine().toString().trim(); // key P=5:0;A=23:2; etc
            HashMap thekey = new HashMap();
            d = strLine.split(";");

            for (int i = 0; i < d.length; i++)
            {
                String[] k = d[i].split("=", 2);
                if (k.length < 2)
                {
                    continue;
                }
                if(!k[0].toUpperCase().contentEquals("AU") && !k[0].toUpperCase().contentEquals("DESC") && !k[0].toUpperCase().contentEquals("ELEV") && !k[0].toUpperCase().contentEquals("REQ") && !k[0].toUpperCase().contentEquals("INFREQ") && !k[0].toUpperCase().contentEquals("CAT"))
                {
                	 thekey.put(k[0], parseKeyBlock(k[1]));
                }
                
                if (k[0].toUpperCase().contentEquals("AU"))   // Author of this
                {
                    // building
                    // stored in key
                    // AU=me;
                    this.author = k[1].trim();
                }
                else if (k[0].toUpperCase().contentEquals("CAT"))   // CAT=Temporary housing: which category to list it under
                {
                    this.category = k[1].trim();
                }
                else if (k[0].toUpperCase().contentEquals("DESC"))   // Description of this
                {
                    // building
                    // stored in key
                    // DESC=building description;
                    this.description = k[1];
                }
                else if (k[0].toUpperCase().contentEquals("ELEV"))   // Elevation constructor box should be at
                {
                    // building
                    // stored in key
                    // ELEV=-1
                    this.elevationLevel = Integer.parseInt(k[1]);
                }
                else if (k[0].toUpperCase().contentEquals("REQ"))   // Elevation constructor box should be at
                {
                    // building
                    // stored in key
                    // REQ=farm.wheat
                    this.elevationLevel = Integer.parseInt(k[1]);
                }
                else if (k[0].toUpperCase().contentEquals("INFREQ"))   // Infrastructure that the building requires to be able to function
                {
                    // building
                    // stored in key
                    // INF=water
                	
                	if(k[1].toLowerCase().contentEquals("water"))
                	{
                		this.infrastructureRequirement = new InfrastructureWater();
                	}
                	
                	if(k[1].toLowerCase().contentEquals("electricity"))
                	{
                		this.infrastructureRequirement = new InfrastructureElectricity();
                	}
                	
                }
            }

            int acount = 0, bcount = 0;
            
            for (int i = 0; i < this.layerCount; i++)
            {
                strLine = br.readLine().trim(); // /read one layer
                // AAAABBBBCCCCaaaabbbccc
                bcount = 0;

                for (int ftb = 0; ftb < ftbCount; ftb++)
                {
                    for (int ltr = 0; ltr < ltrCount; ltr++)
                    {
                        try
                        {
                            String ch = strLine.substring(bcount,
                                                          bcount + 1);
                            char cha=ch.charAt(0);

                            if (cha == MotelPlan.KEEP_CHAR)   // leave whatever is there (an upgrade's existing part)
                            {
                                structure[acount] = null;
                                structureSub[acount] = KEEP;
                                acount++;
                                bcount++;
                                continue;
                            }
                            else if (cha == MotelPlan.HOTEL_ROOM_CHAR)   // a room box, as a hotel room
                            {
                                structure[acount] = SimukraftReloadedBlocks.roomBox;
                                structureSub[acount] = 1;
                            }
                            else if (ch.contentEquals("!"))   // living block
                            {
                                structure[acount] = SimukraftReloadedBlocks.livingBlock;
                                structureSub[acount] = 0;
                            	//structure[acount] = "999:999";
                            }
                            else if (ch.contentEquals("$"))     // /control
                            {
                                // block
                                structure[acount] = SimukraftReloadedBlocks.controlBox;
                                structureSub[acount] = 0;
                            }
                            else if (ch.contentEquals("*"))     // /
                            {
                                // sim-u-lightbox
                                structure[acount] = SimukraftReloadedBlocks.lightBox;
                                structureSub[acount] = 0;
                            }
                            else if (ch.contentEquals("+"))     // /
                            {
                                // sim-u-lightbox
                                structure[acount] = SimukraftReloadedBlocks.lightBox;
                                structureSub[acount] = 3;
                            }
                            else if (ch.contentEquals("-"))     // /
                            {
                                // sim-u-lightbox
                                structure[acount] = SimukraftReloadedBlocks.lightBox;
                                structureSub[acount] = 5;
                            }
                            else if (ch.contentEquals("0"))     // /
                            {
                                // Special Block: 3
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 0;
                            }
                           
                            else if (ch.contentEquals("1"))     // /
                            {
                                // Special Block: 3
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 1;
                            }
                            
                            else if (ch.contentEquals("2"))     // /
                            {
                                // Special Block: 3
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 2;
                            }
                            
                            else if (ch.contentEquals("3"))     // /
                            {
                                // Special Block: 3
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 3;
                            }
                            
                            else if (ch.contentEquals("4"))     // /
                            {
                                // Special Block: 3
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 4;
                            }
                            
                            else if (ch.contentEquals("5"))     // /
                            {
                                // Special Block: 5
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 5;
                            }
                            
                            else if (ch.contentEquals("6"))     // /
                            {
                                // Special Block: 5
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 6;
                            }
                            
                            else if (ch.contentEquals("7"))     // /
                            {
                                // Special Block: 5
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 7;
                            }
                            else if (ch.contentEquals("8"))     // /
                            {
                                // Special Block: 5
                                structure[acount] = SimukraftReloadedBlocks.specialBlock;
                                structureSub[acount] = 8;
                            }
                            else if ((int)cha >=48 && (int)cha <=57) { //special block (air, but special!)
                            	structure[acount] = SimukraftReloadedBlocks.specialBlock;
                            	structureSub[acount] = cha;
                            }
                            
                            /*Light Blocks - Extended ASCII - FOR REFERENCE, DO NOT DELETE*/
                            /*else if(ch.contentEquals("Ã€"))
                            {
                            	structure[acount] = Block.getBlockById(Integer.parseInt(Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":0";
                            }
                            else if(ch.contentEquals("Ã†"))
                            {
                            	structure[acount] = Block.getBlockById(Integer.parseInt(Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":1";
                            }
                            else if(ch.contentEquals("Ã‡"))
                            {
                            	structure[acount] = Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":2";
                            }
                            else if(ch.contentEquals("Ãˆ"))
                            {
                            	structure[acount] = Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":3";
                            }
                            else if(ch.contentEquals("ÃŒ"))
                            {
                            	structure[acount] = Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":4";
                            }
                            else if(ch.contentEquals("Ã�"))
                            {
                            	structure[acount] = Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":5";
                            }
                            else if(ch.contentEquals("Ã‘"))
                            {
                            	structure[acount] = Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":6";
                            }
                            else if(ch.contentEquals("Ã’"))
                            {
                            	structure[acount] = Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox)
                            						+ ":7";
                            }*/
                            
                            else
                            {
                            	//BOOKMARK
                            	String value = (String)thekey.get(ch);
                            	//String[] v = value.split(",");
                            	String[] blockInfo = value.split(":");
                                structure[acount] = Block.getBlockById(Integer.parseInt(blockInfo[0]));
                                structureSub[acount] = Integer.parseInt(blockInfo[1]);
                                //SimukraftReloaded.log.info(ch);
                                Block sbid = structure[acount];
                                int sbidMeta = structureSub[acount];
                                int bid = Block.getIdFromBlock(sbid);
                                // SimukraftReloaded.log("BID="+bid);
                                addToRequirements(Block.getBlockById(bid), sbidMeta, 1);
                            }

                            acount++;
                            bcount++;

                            if (!ch.contentEquals("A"))
                            {
                                blocksInBuilding++;
                            }
                        }
                        catch (Exception e)
                        {
                            // keep counting, or every block after this one lands one place early and the end goes missing
                            if (acount < structure.length)
                            {
                                structure[acount] = Blocks.air;
                                structureSub[acount] = 0;
                            }
                            acount++;
                            bcount++;
                        }
                    }
                }
            }

            br.close();
            if (motel != null)
            {
                displayNameWithoutPK = motel.title();
                author = "Sim-U-Kraft";
            }
            this.rent = blocksInBuilding * 0.01f;
        }
        catch (Exception e)
        {
            SimukraftReloaded.log.warning("Building loadStructure() "+e.toString());
        }
    }

    /**
     * one blueprint key entry as "id:meta". Copied structures write "minecraft:stone,0"; the blueprints that ship
     * with the mod are older and write the numeric "1:0" (vanilla block ids never change in 1.7.10).
     */
    private String parseKeyBlock(String value)
    {
        String v = value.trim();
        Block b;
        int meta = 0;
        int comma = v.lastIndexOf(',');
        try
        {
            if (comma > 0)
            {
                b = Block.getBlockFromName(v.substring(0, comma));
                meta = Integer.parseInt(v.substring(comma + 1).trim());
            }
            else if (v.matches("\\d+:\\d+"))
            {
                String[] p = v.split(":");
                int id = Integer.parseInt(p[0]);
                b = Block.getBlockById(id);
                meta = Integer.parseInt(p[1]);
                if (id != 0 && b == Blocks.air)
                {
                    b = null;
                }
            }
            else
            {
                b = Block.getBlockFromName(v);
            }
        }
        catch (NumberFormatException e)
        {
            b = null;
        }
        if (b == null)
        {
            SimukraftReloaded.log.warning("Building: " + displayName + " uses a block this game doesn't have (" + v + "), leaving it empty");
            return "0:0";
        }
        return Block.getIdFromBlock(b) + ":" + meta;
    }

    private void addToRequirements(Block block, int meta, int amount)
    {
        int val = 0;
        ItemStack theBlock = new ItemStack(block, 1, meta); // id, size, meta

        if (GameMode.gameMode == GameMode.GAMEMODES.NORMAL)
        {
            String name = "";

            try
            {
                name = theBlock.getDisplayName().toLowerCase();
            }
            catch (Exception e)
            {
                name = "????";
            }

            if (name.contains("planks") || name.contentEquals("cobblestone")
                    || name.contentEquals("glass") || name.contains("wool")
                    || name.contentEquals("bricks")
                    || name.contentEquals("dirt")
                    || name.contentEquals("stone bricks")
                    || name.contentEquals("fence")
                    || name.contentEquals("stone")
                    || (name.contains("wood") && !name.contains("slab") && !name.contains("door") 
                    		&& !name.contains("stairs") && !name.contains("grass")))
            {
            	
              /*  if(name.contains("planks"))
            	{
            		planksCount += 1;
            	}
            	
            	if(name.contains("cobblestone"))
            	{
            		cobbleStoneCount += 1;
            	}
            	
            	if(name.contains("glass"))
            	{
            		glassCount += 1;
            	}
            	
            	if(name.contains("wool"))
            	{
            		woolCount += 1;
            	}
            	
            	if(name.contains("bricks"))
            	{
            		bricksCount += 1;
            	}
            	
            	if(name.contains("dirt"))
            	{
            		dirtCount += 1;
            	}
            	
            	if(name.contains("stone bricks"))
            	{
            		stoneBricksCount += 1;
            	}
            	
            	if(name.contains("fence"))
            	{
            		fenceCount += 1;
            	}
            	
            	if(name.contains("stone"))
            	{
            		stoneCount += 1;
            	}
            	
            	if(name.contains("wood"))
            	{
            		woodCount += 1;
            	}
            	*/
            	
                Iterator it = requirements.entrySet().iterator();
                boolean got = false;

                while (it.hasNext())
                {
                    Map.Entry pairs = (Map.Entry) it.next();
                    ItemStack is = (ItemStack) pairs.getKey();

                    if (is.getItem() == theBlock.getItem())
                    {
                        val = (Integer) pairs.getValue();
                        val++;
                        pairs.setValue(val);
                        got = true;
                        break;
                    }
                }

                if (!got)
                {
                    requirements.put(theBlock, 1); 
                }
            }
        }
        else if (GameMode.gameMode == GameMode.GAMEMODES.CREATIVE)
        {
            return;
        }
        else if (GameMode.gameMode == GameMode.GAMEMODES.HARDCORE)
        {
        	String name="";
        	try
            {
                name = theBlock.getDisplayName().toLowerCase();
            }
            catch (Exception e)
            {
                name = "????";
            }
        	if (!name.contains("grass") && !name.contains("bed")) {
	        	Iterator it = requirements.entrySet().iterator();
	            boolean got = false;
	
	            while (it.hasNext())
	            {
	                Map.Entry pairs = (Map.Entry) it.next();
	                ItemStack is = (ItemStack) pairs.getKey();
	                
	
	                if (is.getItem() == theBlock.getItem())
	                {
	                    val = (Integer) pairs.getValue();
	                    val++;
	                    pairs.setValue(val);
	                    got = true;
	                    break;
	                }
	            }
	            
	            if (!got)
	            {
	                requirements.put(theBlock, 1); 
	            }
        	}
        }
    }

    private static void copyArrayList(ArrayList<Building> from, ArrayList<Building> to)
    {
        for (int i = 0; i < from.size(); i++)
        {
            to.add(from.get(i));
        }
    }

    /** returns list of buildings available for specific type eg. Small house */
    /** every blueprint of every type that matches the search (see BuildingCategory.matches) */
    public static ArrayList<Building> searchAllBlueprints(String searchWords)
    {
        ArrayList<Building> all = new ArrayList<Building>();
        for (String type : new String[] {"residential", "commercial", "industrial", "other", "special"})
        {
            all.addAll(getBuildingBlueprints(type, searchWords));
        }
        return all;
    }

    public static ArrayList<Building> getBuildingBlueprints(String theType, String searchWords)
    {
        ArrayList<Building> retBuildings = new ArrayList<Building>();
        
        try
        {

            if (theType.contentEquals("residential"))
            {
                copyArrayList(buildingsRes, retBuildings);
            }
            else if (theType.contentEquals("commercial"))
            {
                copyArrayList(buildingsCom, retBuildings);
            }
            else if (theType.contentEquals("industrial"))
            {
                copyArrayList(buildingsInd, retBuildings);
            }
            else if (theType.contentEquals("other"))
            {
                copyArrayList(buildingsOth, retBuildings);
            }
            else if (theType.contentEquals("special"))
            {
                copyArrayList(buildingsSpec, retBuildings);
            }

            for (int i = retBuildings.size() - 1; i >= 0; i--)
            {
                if (!BuildingCategory.matches(retBuildings.get(i), searchWords))
                {
                    retBuildings.remove(i);
                }
            }
        }
        catch (Exception e) {}

        return retBuildings;
    }

    /**
     * returns reference to building based on its primaryXYZ or null if it can't
     * find it
     */
    public static Building getBuilding(V3 primaryXYZ)
    {
        Building b = null;

        // the server's list, loaded from the save; the client's is synced from the server and can't be loaded from disk
        if (!SimukraftReloaded.isClientThread() && SimukraftReloaded.buildings().size() == 0)
        {
            Building.loadAllBuildings();
        }

        for (int x = 0; x < SimukraftReloaded.buildings().size(); x++)
        {
        	try {
	        	b = (Building) SimukraftReloaded.buildings().get(x);
	
	            if (b.primaryXYZ.isSameCoordsAs(primaryXYZ, false, true))
	            {
	                return b;
	            }
        	} catch(Exception e){}
        }

        return null;
    }
    
    /**
     * returns reference to building based on its primaryXYZ or null if it can't
     * find it
     */
    public static Building getNearestBuilding(V3 primaryXYZ)
    {
        Building b = null;
        Building nearestBuilding = null;

        // the server's list, loaded from the save; the client's is synced from the server and can't be loaded from disk
        if (!SimukraftReloaded.isClientThread() && SimukraftReloaded.buildings().size() == 0)
        {
            Building.loadAllBuildings();
        }

        for (int x = 0; x < SimukraftReloaded.buildings().size(); x++)
        {
        	try 
        	{
	        	b = (Building) SimukraftReloaded.buildings().get(x);
	
	        	if(nearestBuilding == null && b != null)
	        	{
	        		nearestBuilding = b;
	        	}
	        	
	        	if(primaryXYZ.getDistanceTo(b.primaryXYZ) < primaryXYZ.getDistanceTo(nearestBuilding.primaryXYZ))
	        	{
	        		nearestBuilding = b;
	        	}
        	}
        	catch(Exception e){}
        }

        return nearestBuilding;
    }

    /**
     * returns reference to building based on search for name. Eg. 'depot' will
     * return first depot in buildings arraylist
     */
    public static Building getBuildingBySearch(String searchWord)
    {
        Building b = null;

        for (int x = 0; x < SimukraftReloaded.buildings().size(); x++)
        {
            b = (Building) SimukraftReloaded.buildings().get(x);

            if (b.displayName.contains(searchWord))
            {
                return b;
            }
        }

        return null;
    }

    /** overload will return an ArrayList<Building> of all buildings in world that it finds */
    public static ArrayList<Building> getBuildingBySearch(String searchWord,boolean findAll) {
        ArrayList<Building> ret=new ArrayList<Building>();

        for (int x = 0; x < SimukraftReloaded.buildings().size(); x++)
        {
            Building b = (Building) SimukraftReloaded.buildings().get(x);

            if (b.displayName.toLowerCase().contains(searchWord.toLowerCase()))
            {
                ret.add(b);
            }
        }

        return ret;
    }
    
    /**
     * returns reference to building based on it's building constructor's
     * location
     */
    public static Building getBuildingByConBox(V3 conBoxLoc)
    {
        Building b = null;

        for (int x = 0; x < SimukraftReloaded.buildings().size(); x++)
        {
            b = (Building) SimukraftReloaded.buildings().get(x);

            try
            {
                if (b.conBoxLocation.isSameCoordsAs(conBoxLoc, true, true))
                {
                    return b;
                }
            }
            catch (Exception e)
            {
            }
        }

        return null;
    }

    /** the lines written to this building's .sk2 file */
    private ArrayList<String> toSaveLines()
    {
        ArrayList<String> strings = new ArrayList<String>();
        strings.add("displayname|" + this.displayName);
        strings.add("type|" + this.type);
        strings.add("primaryxyz|" + this.primaryXYZ.toString());
        strings.add("livingxyz|" + (this.livingXYZ == null ? "null" : this.livingXYZ.toString()));
        strings.add("buildingcomplete|" + this.buildingComplete);
        strings.add("capacity|" + this.capacity);
        strings.add("builddir|" + this.buildDirection);
        strings.add("lmarker|" + (this.lumbermillMarker == null ? "null" : this.lumbermillMarker.toString()));
        strings.add("blocksinbuilding|" + this.blocksInBuilding);
        if (this.conBoxLocation != null)
        {
            strings.add("conbox|" + this.conBoxLocation.toString()); // the warehouse takes leftovers from chests by it
        }
        if (offsetLtr != 0 || offsetFtb != 0 || offsetUp != 0)
        {
            strings.add("offset|" + offsetLtr + "," + offsetFtb + "," + offsetUp);
        }
        if (awaitingAt != null)
        {
            strings.add("awaiting|" + awaitingAt);
        }
        if (this.roomMode != null)
        {
            strings.add("roommode|" + this.roomMode);
            strings.add("roomsize|" + this.roomSize);
            strings.add("staynights|" + this.stayNightsLeft);
            strings.add("rent|" + this.rent);
        }

        StringBuilder temp = new StringBuilder("tennants|");
        for (String tennant : new ArrayList<String>(this.tennants))
        {
            if (!tennant.trim().isEmpty())
            {
                temp.append(tennant.trim()).append(',');
            }
        }
        strings.add(temp.toString());

        // copies, so a job adding blocks mid-save can't throw ConcurrentModificationException
        temp = new StringBuilder("blocklocs|");
        for (V3 block : new ArrayList<V3>(this.blockLocations))
        {
            if (block != null)
            {
                temp.append(block.toString()).append('B');
            }
        }
        strings.add(temp.toString());

        if (this.blockSpecial.size() > 0)
        {
            temp = new StringBuilder("blockspecial|");
            for (V3 block : new ArrayList<V3>(this.blockSpecial))
            {
                if (block != null)
                {
                    temp.append(block.toString()).append(',').append(block.meta).append('B');
                }
            }
            strings.add(temp.toString());
        }
        return strings;
    }

    private File saveFile()
    {
        String xyz = "b" + this.primaryXYZ.toString().replaceAll(",", "_");
        return new File(SimukraftReloaded.getSavesDataFolder() + "Buildings" + File.separator + xyz + ".sk2");
    }

    public void saveThisBuilding() {
        if (this.primaryXYZ != null) {
        	SimukraftReloaded.saveSK2(saveFile().getPath(), toSaveLines());
        }
    }

    public boolean isRoom()
    {
        return roomMode != null;
    }

    public boolean isHotel()
    {
        return ROOM_HOTEL.equals(roomMode);
    }

    /** a permanent home: a house, or an apartment room */
    public boolean isPermanentHome()
    {
        return "residential".equals(type) && buildingComplete && !isHotel();
    }

    /** whether count more folks can move in. Houses never limited this; rooms go by their size, hotels take no extra partners */
    public boolean hasSpaceFor(int count)
    {
        if (!isRoom())
        {
            return true;
        }
        if (isHotel())
        {
            return tennants.isEmpty() && count <= roomSize;
        }
        return tennants.size() + count <= roomSize;
    }

    /** what the room charges per day: rent for apartments, a nightly rate for hotel rooms */
    public void updateRoomRent()
    {
        if (isRoom())
        {
            rent = roomSize * (isHotel() ? HOTEL_RATE_PER_FOLK : APARTMENT_RENT_PER_FOLK);
        }
    }

    public static String roomSizeName(int size)
    {
        return size <= 1 ? "Single" : size == 2 ? "Couple" : "Family";
    }

    /** creates the building record for a Room Box placed in the world */
    public static Building createRoom(World world, int x, int y, int z)
    {
        V3 box = new V3((double) x, (double) y, (double) z, world.provider.dimensionId);
        Building existing = getBuilding(box);
        if (existing != null)
        {
            return existing;
        }

        Building room = new Building("Apartment", "residential", box, findRoomLivingSpot(world, x, y, z), true);
        room.roomMode = ROOM_APARTMENT;
        room.roomSize = 2;
        room.displayNameWithoutPK = room.displayName;
        room.author = "";
        room.updateRoomRent();
        SimukraftReloaded.buildings().add(room);
        room.saveThisBuilding();
        return room;
    }

    /** works out a room's standing spot again, now the walls around it are built */
    public void refreshLivingSpot(World world)
    {
        if (isRoom() && primaryXYZ != null)
        {
            livingXYZ = findRoomLivingSpot(world, primaryXYZ.x.intValue(), primaryXYZ.y.intValue(), primaryXYZ.z.intValue());
            saveThisBuilding();
        }
    }

    /** where folks stand when "at home" in a room: a free spot beside the box, or on top of it */
    private static V3 findRoomLivingSpot(World world, int x, int y, int z)
    {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around)
        {
            for (int dy = 0; dy >= -1; dy--)
            {
                if (WorldUtil.canStandAt(world, x + d[0], y + dy, z + d[1]))
                {
                    return new V3(x + d[0] + 0.5d, (double) (y + dy), z + d[1] + 0.5d, world.provider.dimensionId);
                }
            }
        }
        return new V3(x + 0.5d, (double) (y + 1), z + 0.5d, world.provider.dimensionId);
    }

    /**
     * Once a day (morning): hotel bookings count down a night, and empty hotel rooms may get visitors from out of
     * town. Guests check themselves out when their nights run out (FolkData.checkOutOfHotel). Hotel income is
     * collected with the rent in evolveFolks().
     */
    public static void dailyRoomUpdate(World world)
    {
        Random rand = new Random();

        for (Building room : new ArrayList<Building>(SimukraftReloaded.buildings()))
        {
            if (!room.isHotel())
            {
                continue;
            }

            if (!room.tennants.isEmpty())
            {
                if (room.stayNightsLeft > 0)
                {
                    room.stayNightsLeft--;
                }
            }
            else if (rand.nextInt(100) < SimukraftReloadedConfig.configHotelVisitorChance)
            {
                FolkData.createVisitor(world, room, rand.nextInt(4) + 1);
            }
        }
    }

    /** manual check-out (GUI button / stuck guests): everyone in the room checks out now */
    public void checkOut()
    {
        for (String name : new ArrayList<String>(tennants))
        {
            FolkData folk = FolkData.getFolkByName(name);
            if (folk != null)
            {
                folk.checkOutOfHotel(this);
            }
            else
            {
                removeTennant(name);
            }
        }
        stayNightsLeft = 0;
        saveThisBuilding();
    }

    /**
     * Call when a building no longer exists in the world (control box broken, exploded or demolished).
     * Fires its staff, turns its tenants out to find a new home, and forgets it, in memory and on disk.
     * Before this, breaking a control box left the building in memory: tenants kept "living" there and
     * workers kept working at it until the game was restarted.
     */
    public static void removeBuilding(Building building, String reason)
    {
        if (building == null || !SimukraftReloaded.buildings().remove(building))
        {
            return;
        }
        SimukraftReloaded.log.info("Building: removed " + building.displayNameWithoutPK + " at " + building.primaryXYZ + " (" + reason + ")");
        if (building.primaryXYZ != null)
        {
            ashjack.simukraftreloaded.core.town.Accounts.close(building.primaryXYZ); // its funds go to its town
        }

        for (FolkData folk : new ArrayList<FolkData>(SimukraftReloaded.folks()))
        {
            boolean worksHere = building.primaryXYZ != null && building.primaryXYZ.isSameCoordsAs(folk.employedAt, true, true);
            boolean buildingIt = folk.theBuilding == building;
            if (worksHere || buildingIt)
            {
                folk.selfFire();
            }

            if (building.tennants.contains(folk.name) && folk.isVisitor)
            {
                SimukraftReloaded.sendChat(folk.name + " has left town - their hotel room is gone");
                folk.removeFromWorld();
            }
            else if (building.tennants.contains(folk.name))
            {
                if (folk.action == FolkAction.ATHOME || folk.action == FolkAction.GOINGHOME || folk.action == FolkAction.STAYINGHOME)
                {
                    folk.action = FolkAction.WANDER;
                    folk.actionArrival = null;
                    folk.destination = null;
                    folk.stayPut = false;
                    folk.statusText = "Wandering";
                }
                SimukraftReloaded.sendChat(folk.name + " has lost their home (" + building.displayNameWithoutPK + ")");
            }
        }
        building.tennants.clear();

        if (building.primaryXYZ != null)
        {
            File f = building.saveFile();
            if (f.exists())
            {
                f.delete();
            }
        }
    }

    /**
     * Puts a building a builder is working on into the list, in place of an older copy of it. Builders only ever saved
     * their building to disk, so one finished since the last restart wasn't in the list at all (its control box had
     * nothing to show, the warehouse never saw its chests), and one resumed after a restart sat there as the stale copy
     * loaded from the save while the builder worked on a fresh one from the blueprint.
     */
    public static void register(Building b)
    {
        if (b == null || b.primaryXYZ == null || SimukraftReloaded.isClientThread())
        {
            return;
        }
        ArrayList<Building> list = SimukraftReloaded.buildings();
        for (int i = 0; i < list.size(); i++)
        {
            Building old = list.get(i);
            if (old == b)
            {
                return;
            }
            if (old != null && b.primaryXYZ.isSameCoordsAs(old.primaryXYZ, false, true))
            {
                // keep what the old copy knew that this one doesn't
                if (b.conBoxLocation == null)
                {
                    b.conBoxLocation = old.conBoxLocation;
                }
                ArrayList<V3> blocks = new ArrayList<V3>(old.blockLocations);
                blocks.addAll(b.blockLocations);
                b.blockLocations = blocks;
                for (String t : old.tennants)
                {
                    if (!b.tennants.contains(t))
                    {
                        b.tennants.add(t);
                    }
                }
                list.set(i, b);
                return;
            }
        }
        list.add(b);
    }

    public static void saveAllBuildings()
    {
        ArrayList<Building> gone = new ArrayList<Building>();

        for (Building building : new ArrayList<Building>(SimukraftReloaded.buildings()))
        {
            if (building == null || building.primaryXYZ == null)
            {
                continue;
            }
            V3 pxyz = building.primaryXYZ;
            World buildingWorld = MinecraftServer.getServer().worldServerForDimension(pxyz.theDimension);
            int x = pxyz.x.intValue(), y = pxyz.y.intValue(), z = pxyz.z.intValue();

            // only judge buildings whose chunk is loaded - getBlock on an unloaded chunk loads it
            if (buildingWorld != null && buildingWorld.blockExists(x, y, z))
            {
                Block id = buildingWorld.getBlock(x, y, z);
                if (id != SimukraftReloadedBlocks.controlBox && id != SimukraftReloadedBlocks.buildingConstructor
                        && id != SimukraftReloadedBlocks.roomBox)
                {
                    gone.add(building);   //no control block, building destroyed in world
                    continue;
                }
            }
            building.saveThisBuilding();
        }

        for (Building building : gone)
        {
            removeBuilding(building, "control box missing");
        }
        SimukraftReloaded.log.info("Building.saveAllBuildings "+SimukraftReloaded.buildings().size()+" buildings");
        
        /*
        for (int b = 0; b < ModSimukraft.theBuildings.size(); b++)
        {
            Building building = (Building) ModSimukraft.theBuildings.get(b);

            if (building != null)
            {
                if (building.primaryXYZ != null)
                {
                    V3 pxyz = building.primaryXYZ;
                    World buildingWorld = ModLoader
                                          .getMinecraftServerInstance()
                                          .worldServerForDimension(
                                              building.primaryXYZ.theDimension);
                    id = buildingWorld.getBlockId(pxyz.x.intValue(),
                                                  pxyz.y.intValue(), pxyz.z.intValue());
                    String xyz = "b"
                                 + building.primaryXYZ.toString().replaceAll(",",
                                         "_");

                    // primaryxyz will be a constructor block until the control
                    // block has been placed
                    if (id == ModSimukraft.controlBlockId
                            || id == ModSimukraft.constructorBlockId)
                    {
                        ModSimukraft.proxy.saveObject(
                            ModSimukraft.getSavesDataFolder() + "Buildings"
                            + File.separator + xyz + ".suk",
                            building);
                    }
                }

                // SimukraftReloaded.log("Saved building at "+ building.primaryXYZ
                // +" ("+building.displayNameWithoutPK+")");
            }
            else
            {
                SimukraftReloaded.log("NOT saved building, as element null");
            }
        }
        */
        
    }

    /* loads buildings in this world */
    public static void loadAllBuildings()
    {
    	
    	File buildingsFolder = new File(SimukraftReloaded.getSavesDataFolder() + "Buildings" + File.separator);	
    	buildingsFolder.mkdirs();

    	boolean useNewFormat=false;
        //check for new file format
        for (File f : buildingsFolder.listFiles())
        {
            if (f.getName().endsWith(".sk2")){
            	useNewFormat=true;
            	break;
            }
        }

        if (useNewFormat) {
        	SimukraftReloaded.buildings().clear();
        	
        	for (File f : buildingsFolder.listFiles())
	        {
	            if (f.getName().endsWith(".sk2"))
	            {
	            	ArrayList<String> strings=SimukraftReloaded.loadSK2(f.getAbsoluteFile().toString());
	            	Building build=new Building();
	            	for(String line:strings) {
	            		if (line.contains("|")) {
	            			int m1=line.indexOf("|");
	        				String name=line.substring(0,m1);
	        				String value=line.substring(m1+1);
	        				
	        				if (name.contentEquals("displayname")) {
	        					build.displayName=value;
	        				} else if (name.contentEquals("type")) {
	        					build.type=value;
	        				} else if (name.contentEquals("primaryxyz")) {
	        					build.primaryXYZ=new V3(value);
	        				} else if (name.contentEquals("livingxyz")) {
	        					if (!value.contentEquals("null")) {
	        						build.livingXYZ=new V3(value);
	        					}
	        				} else if (name.contentEquals("conbox")) {
	        					build.conBoxLocation=new V3(value);
	        				} else if (name.contentEquals("offset")) {
	        					String[] o = value.split(",");
	        					build.offsetLtr = Integer.parseInt(o[0].trim());
	        					build.offsetFtb = Integer.parseInt(o[1].trim());
	        					build.offsetUp = Integer.parseInt(o[2].trim());
	        				} else if (name.contentEquals("awaiting")) {
	        					build.awaitingAt = value;
	        				} else if (name.contentEquals("buildingcomplete")) {
	        					build.buildingComplete=Boolean.parseBoolean(value);
	        				} else if (name.contentEquals("capacity")) {
	        					build.capacity=Integer.parseInt(value);
	        				} else if (name.contentEquals("builddir")) {
	        					build.buildDirection=value;
	        				} else if (name.contentEquals("lmarker")) {
	        					if (!value.contentEquals("null")) {
	        						build.lumbermillMarker=new V3(value);
	        					}
	        				} else if (name.contentEquals("blocksinbuilding")) {
	        					build.blocksInBuilding=Integer.parseInt(value);
	        				} else if (name.contentEquals("roommode")) {
	        					build.roomMode=value;
	        				} else if (name.contentEquals("roomsize")) {
	        					build.roomSize=Integer.parseInt(value);
	        				} else if (name.contentEquals("staynights")) {
	        					build.stayNightsLeft=Integer.parseInt(value);
	        				} else if (name.contentEquals("rent")) {
	        					build.rent=Float.parseFloat(value);
	        				} else if (name.contentEquals("tennants")) {
	        					if (value.trim().contentEquals("")) {
	        						build.tennants.clear();
	        					} else {
	        						String[] tennants=value.split(",");
	        						for(String tennant:tennants) {
	        							if (!tennant.trim().contentEquals("")) {
	        								build.tennants.add(tennant);
	        							}
	        						}
	        					}
	        				} else if (name.contentEquals("blocklocs")) {
	        					if (value.contains("B") && value.contains(",")) {
	        						String[] blocks=value.split("B");
	        						for(String block:blocks) {
	        							if (block.contains(",")) {
	        								build.blockLocations.add(new V3(block));
	        							}
	        						}
	        					}
	        				} else if (name.contentEquals("blockspecial")) {
	        					if (value.contains("B") && value.contains(",")) {
	        						String[] blocks=value.split("B");
	        						for(String block:blocks) {
	        							if (block.contains(",")) {
	        								int p1=block.lastIndexOf(",");
	        								String v=block.substring(0,p1);
	        								String meta=block.substring(p1+1);
	        								V3 v3=new V3(v);
	        								v3.meta=Integer.parseInt(meta);
	        								build.blockSpecial.add(v3);
	        							}
	        						}
	        					}
	        				}	
	            		}
	            	}
	            	
	            	if (build.roomMode == null) {
	            		build.loadStructure();  //fills in several more fields about this building
	            	} else {
	            		build.displayNameWithoutPK = build.displayName;
	            	}
	            	SimukraftReloaded.buildings().add(build);
	            }
	        }
	            	
	            	
	    /// load old format     	
        } else {
    	
	        SimukraftReloaded.buildings().clear();
	
	        for (File f : buildingsFolder.listFiles())
	        {
	            if (f.getName().endsWith(".suk"))
	            {
	                Building building = (Building) ModSimukraft.proxy.loadObject(f
	                                    .getAbsoluteFile().toString());
	
	                if (building != null)
	                {
	                    V3 xyz = building.primaryXYZ;
	                    World buildingWorld = MinecraftServer
	                                          .getServer()
	                                          .worldServerForDimension(
	                                              building.primaryXYZ.theDimension);
	                    Block id = buildingWorld.getBlock(xyz.x.intValue(),
	                                                      xyz.y.intValue(), xyz.z.intValue());
	                    Building dupe = null;
	
	                    if (SimukraftReloaded.buildings().size() > 0)
	                    {
	                        dupe = Building.getBuilding(xyz);
	                    }
	
	                    if (id == SimukraftReloadedBlocks.controlBox && dupe == null)
	                    {
	                        building.loadStructure();
	                        SimukraftReloaded.buildings().add(building);
	                    }
	                    else
	                    {
	                        f.delete();
	                        SimukraftReloaded.log.info("Building: Deleted building as id=" + id
	                                         + " or dupe");
	                    }
	                }
	            }
	        }
        }
    }

    /** checks that the folks (tennants) actually exist now */
    public static void checkTennants()
    {
        for (int b = 0; b < SimukraftReloaded.buildings().size(); b++)
        {
            Building building = SimukraftReloaded.buildings().get(b);

            for (int t = 0; t < building.tennants.size(); t++)
            {
                try
                {
                    String tennant = building.tennants.get(t);
                    boolean exists = false;

                    for (int f = 0; f < SimukraftReloaded.folks().size(); f++)
                    {
                        FolkData folk = SimukraftReloaded.folks().get(f);

                        if (folk.name.contentEquals(tennant))
                        {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists)
                    {
                        building.tennants.remove(tennant);
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /* loads in all buildings files into an ArrayList for quicker access (slowly in a thread) */
    /** blueprints this rewrite adds, shipped in the jar and copied into mods/Simukraft/buildings when missing */
    private static final String[] BUNDLED_BLUEPRINTS = {"industrial/Warehouse.txt"};

    private static void installBundledBlueprints()
    {
        for (String path : BUNDLED_BLUEPRINTS)
        {
            File target = new File(SimukraftReloaded.getSimukraftFolder() + File.separator + "buildings" + File.separator
                    + path.replace('/', File.separatorChar));
            if (target.exists())
            {
                continue;
            }
            java.io.InputStream in = Building.class.getResourceAsStream("/assets/ashjacksimukraftreloaded/buildings/" + path);
            if (in == null)
            {
                continue;
            }
            try
            {
                target.getParentFile().mkdirs();
                java.nio.file.Files.copy(in, target.toPath());
                SimukraftReloaded.log.info("Building: installed the " + target.getName() + " blueprint");
            }
            catch (Exception e)
            {
                SimukraftReloaded.log.warning("Building: couldn't install the " + path + " blueprint: " + e);
            }
            finally
            {
                try { in.close(); } catch (Exception e) {}
            }
        }
    }

    public static void initialiseAllBuildings()
    {
    	if (runningInitThread) {return;}
    	installBundledBlueprints();
    	
        Thread t = new Thread(new Runnable()
        {
            public void run()
            {
            	runningInitThread=true;
            	buildingsRes.clear();
            	buildingsCom.clear();
            	buildingsInd.clear();
            	buildingsOth.clear();
            	buildingsSpec.clear();
                initBuildingsOfType("residential");
                initBuildingsOfType("commercial");
                initBuildingsOfType("industrial");
                initBuildingsOfType("other");
                initBuildingsOfType("special");
                runningInitThread=false;
                SimukraftReloaded.log.info("Building: Thread Done Initialising all buildings from disk");
            }
        });
        t.start();
    }

    private static boolean runningInitThread=false;
    
    /** used when loading folks, to get the building they are currently building
     * partialFile name should be just "PK123-buildingname.txt" */
    /**
     * a building turned into flats (see ApartmentConversion): its own blueprint, fitted out. A conversion of one
     * already standing leaves all of its own blocks alone and only asks for what's added.
     */
    private void loadAsFlats(ApartmentConversion.Name n)
    {
        Building orig = new Building(n.original, n.type);
        orig.loadStructure();
        if (orig.structure == null || orig.layerCount == 0)
        {
            return;
        }
        ltrCount = orig.ltrCount;
        ftbCount = orig.ftbCount;
        layerCount = orig.layerCount;
        dimensions = orig.dimensions;
        author = orig.author;
        description = orig.description;
        elevationLevel = orig.elevationLevel;
        pk = orig.pk;
        category = BuildingCategory.HOUSING;
        displayNameWithoutPK = orig.displayNameWithoutPK + " (apartments)";
        structure = orig.structure.clone();
        structureSub = orig.structureSub.clone();
        if (!n.convert)
        {
            requirements.putAll(orig.requirements);
            blocksInBuilding = orig.blocksInBuilding;
        }
        java.util.List<Integer> added = ApartmentConversion.fitOut(this, n.dir);
        for (int i : added)
        {
            addToRequirements(structure[i], structureSub[i], 1);
            blocksInBuilding++;
        }
        if (n.convert)
        {
            java.util.HashSet<Integer> keepThese = new java.util.HashSet<Integer>(added);
            for (int i = 0; i < structure.length; i++)
            {
                if (!keepThese.contains(i))
                {
                    structure[i] = null;
                    structureSub[i] = KEEP;
                }
            }
        }
        rent = blocksInBuilding * 0.01f;
    }

    /** a generated building (a motel, or a building as flats) by its name */
    public static Building fromGenerated(String name, String type)
    {
        Building b = new Building(name, type);
        MotelPlan motel = MotelPlan.parse(name);
        ApartmentConversion.Name flats = ApartmentConversion.parse(name);
        b.buildDirection = motel != null ? motel.dir : flats != null ? flats.dir : null;
        b.loadStructure();
        return b;
    }

    public static boolean isGenerated(String name)
    {
        return MotelPlan.isGenerated(name) || ApartmentConversion.isGenerated(name);
    }

    /** how many soldiers a barracks can train: one per so much floor (config BarracksAreaPerSoldier) */
    public int soldierCapacity()
    {
        return Math.max(1, Math.min(60, ltrCount * ftbCount / SimukraftReloadedConfig.configBarracksAreaPerSoldier));
    }

    /** a made-to-order motel, ready to preview or build */
    public static Building fromMotelPlan(MotelPlan plan)
    {
        Building b = new Building(plan.name(), "commercial");
        b.buildDirection = plan.dir;
        b.loadStructure();
        return b;
    }

    public static Building getBuildingForFolk(String partialFilename, String type) {
    	String generated = partialFilename.endsWith(".txt") ? partialFilename.substring(0, partialFilename.length() - 4) : partialFilename;
    	if (isGenerated(generated)) {
    		return fromGenerated(generated, type);
    	}
    	File f = new File(SimukraftReloaded.getSimukraftFolder() + "/buildings/" + type+"/"+partialFilename);
    	if (f.exists()) {
    		String name = f.getName().substring(0, f.getName().length() - 4);
            Building build = new Building(name, type);
            build.loadStructure();
            return build;
    	} else {
    		return null;
    	}
    }
    
    private static void initBuildingsOfType(String type)
    {
    	
    	File f = new File(SimukraftReloaded.getSimukraftFolder() + "/buildings/" + type);
    	if (f.list() == null)
    	{
    		return; // no folder for this type (e.g. nobody has made a "special" building)
    	}

        for (int i = 0; i < f.list().length; i++)
        {
            String name = f.list()[i];
            name = name.substring(0, name.length() - 4);
            Building build = new Building(name, type);
            build.loadStructure();

            if (type.contentEquals("residential"))
            {
                buildingsRes.add(build);
            }
            else if (type.contentEquals("commercial"))
            {
                buildingsCom.add(build);
            }
            else if (type.contentEquals("industrial"))
            {
                buildingsInd.add(build);
            }
            else if (type.contentEquals("other"))
            {
                buildingsOth.add(build);
            }
             else if (type.contentEquals("special"))
            {
                buildingsSpec.add(build);
            }

            try
            {
                Thread.sleep(30);
            }
            catch (Exception e) {}
        }
       
    }

    /** whether the blueprint catalogue has been read (a client playing on a server reads its own copy for the GUIs) */
    public static boolean isCatalogueLoaded()
    {
        return runningInitThread || !buildingsRes.isEmpty() || !buildingsCom.isEmpty() || !buildingsInd.isEmpty() || !buildingsOth.isEmpty();
    }

    public static Building getFromAllBuildings(String fullname, String type)
    {
        if (type.contentEquals("residential"))
        {
            for (Building build: buildingsRes)
            {
                if (build.displayName.contentEquals(fullname))
                {
                    return build.clone();
					
                }
            }
        }
        else if (type.contentEquals("commercial"))
        {
            for (Building build: buildingsCom)
            {
                if (build.displayName.contentEquals(fullname))
                {
                    return build.clone();
                }
            }
        }
        else if (type.contentEquals("industrial"))
        {
            for (Building build: buildingsInd)
            {
                if (build.displayName.contentEquals(fullname))
                {
                    return build.clone();
                }
            }
        }
        else if (type.contentEquals("other"))
        {
            for (Building build: buildingsOth)
            {
                if (build.displayName.contentEquals(fullname))
                {
                    return build.clone();
                }
            }
        }
        
        else if (type.contentEquals("special"))
        {
            for (Building build: buildingsSpec)
            {
                if (build.displayName.contentEquals(fullname))
                {
                    return build.clone();
                }
            }
        }

        return null;
    }
}
