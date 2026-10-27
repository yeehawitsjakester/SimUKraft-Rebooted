package ashjack.simukraftreloaded.common;

import ashjack.simukraftreloaded.proxies.CommonProxy.Commodity;
import java.util.ArrayList;

import ashjack.simukraftreloaded.blocks.functionality.FarmingBox;
import ashjack.simukraftreloaded.blocks.functionality.MiningBox;
import ashjack.simukraftreloaded.blocks.functionality.Marker;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * The client's copy of the server's Sim-U-Kraft data, filled in by sync packets. GUIs and other client-thread
 * code reach it through SimukraftReloaded.folks()/buildings()/states() etc., so the same code reads the real
 * data on the server and this copy on the client. Changing it does nothing - changes go to the server as actions.
 */
public final class ClientMirror
{
    private ClientMirror() {}

    public static ArrayList<FolkData> folks = new ArrayList<FolkData>();
    public static ArrayList<Building> buildings = new ArrayList<Building>();
    public static ArrayList<Relationship> relationships = new ArrayList<Relationship>();
    public static ArrayList<MiningBox> miningBoxes = new ArrayList<MiningBox>();
    public static ArrayList<FarmingBox> farmingBoxes = new ArrayList<FarmingBox>();
    public static ArrayList<CourierTask> courierTasks = new ArrayList<CourierTask>();
    public static ArrayList<V3> courierPoints = new ArrayList<V3>();
    public static ArrayList<Marker> markers = new ArrayList<Marker>();
    public static ArrayList<Commodity> commodities = new ArrayList<Commodity>();
    public static GameStates states = new GameStates();
    /** set once the first sync has arrived for the current world */
    public static boolean synced = false;
    /** counts syncs, so a GUI can tell when fresh data has arrived */
    public static int syncCount = 0;
    /** the server has finished loading Sim-U-Kraft for this world */
    public static boolean ranStartup = false;
    /** from the HUD packet */
    public static int population = 0;
    public static String worldName = "";
    /** the town whose treasury the HUD shows (from the HUD packet) */
    public static String townName = "";
    /** the player's own wallet (from the HUD packet) */
    public static float wallet = 0f;
    /** businesses' funds, by Accounts key */
    public static java.util.HashMap<String, Float> accounts = new java.util.HashMap<String, Float>();
    /** each town's market (by town id), for the market screen */
    public static java.util.HashMap<Integer, ashjack.simukraftreloaded.core.town.TownMarket> markets = new java.util.HashMap<Integer, ashjack.simukraftreloaded.core.town.TownMarket>();
    public static ArrayList<ashjack.simukraftreloaded.core.town.Town> towns = new ArrayList<ashjack.simukraftreloaded.core.town.Town>();

    public static void clear()
    {
        folks = new ArrayList<FolkData>();
        buildings = new ArrayList<Building>();
        relationships = new ArrayList<Relationship>();
        miningBoxes = new ArrayList<MiningBox>();
        farmingBoxes = new ArrayList<FarmingBox>();
        courierTasks = new ArrayList<CourierTask>();
        courierPoints = new ArrayList<V3>();
        markers = new ArrayList<Marker>();
        commodities = new ArrayList<Commodity>();
        states = new GameStates();
        synced = false;
        ranStartup = false;
    }
}
