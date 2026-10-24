package ashjack.simukraftreloaded.network;

import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.building.Building;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.core.game.GameMode;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Server-side handlers for every GUI button that changes the game. These are the old GUI code paths moved to
 * the server: the GUIs only send the action name and what was chosen.
 */
public final class ActionHandlers
{
    private ActionHandlers() {}

    // ---- helpers for reading/writing action data ----

    public static void putV3(NBTTagCompound data, String key, V3 v)
    {
        data.setString(key, v.toString());
    }

    public static V3 getV3(NBTTagCompound data, String key)
    {
        String s = data.getString(key);
        return s.isEmpty() ? null : new V3(s);
    }

    public static void putNames(NBTTagCompound data, String key, Iterable<String> names)
    {
        NBTTagList list = new NBTTagList();
        for (String n : names)
        {
            list.appendTag(new NBTTagString(n));
        }
        data.setTag(key, list);
    }

    public static ArrayList<String> getNames(NBTTagCompound data, String key)
    {
        ArrayList<String> out = new ArrayList<String>();
        NBTTagList list = data.getTagList(key, 8);
        for (int i = 0; i < list.tagCount(); i++)
        {
            out.add(list.getStringTagAt(i));
        }
        return out;
    }

    static void tell(EntityPlayerMP player, String text)
    {
        player.addChatComponentMessage(new ChatComponentText(text));
    }

    /** called once at startup */
    public static void registerAll()
    {
        // first-run menu: 10 = don't run, 0 = normal, 1 = creative, 2 = hardcore
        ServerActions.register("setGameMode", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                int mode = data.getInteger("mode");
                if (SimukraftReloaded.serverStates.gameModeNumber != -1 || !(mode == 0 || mode == 1 || mode == 2 || mode == 10))
                {
                    return; // already chosen (another player answered first)
                }
                SimukraftReloaded.serverStates.gameModeNumber = mode;
                SimukraftReloaded.serverStates.saveStates();
                SimukraftReloaded.awaitingGameMode = false;
                if (mode == 10)
                {
                    ModSimukraft.proxy.ranStartup = true;
                    tell(player, "Sim-U-Kraft is switched off for this world");
                    return;
                }
                GameMode.setGameModeFromNumber(mode);
                SimukraftReloaded.finishStartup();
                if (mode == 0)
                {
                    FolkData.generateNewFolk(player.worldObj);
                }
            }
        });

        // GuiEmployFolk "OK": hire the chosen folks for a job at a control box / mining box / farming box
        ServerActions.register("hire", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = getV3(data, "box");
                Vocation vocation;
                try
                {
                    vocation = Vocation.valueOf(data.getString("vocation"));
                }
                catch (IllegalArgumentException e)
                {
                    return;
                }
                if (box == null)
                {
                    return;
                }
                if (SimukraftReloaded.serverStates.credits <= 0 && GameMode.gameMode != GameMode.GAMEMODES.CREATIVE)
                {
                    tell(player, "You need some Sim-u-Credits to employ folks.");
                    return;
                }
                if (GameMode.gameMode == GameMode.GAMEMODES.CREATIVE && vocation == Vocation.MERCHANT)
                {
                    tell(player, "Builder's merchant cannot be hired in creative mode");
                    return;
                }
                for (String name : getNames(data, "folks"))
                {
                    FolkData folk = FolkData.getFolkByName(name);
                    if (folk == null || folk.employedAt != null || folk.isVisitor || folk.age < 18)
                    {
                        continue;
                    }
                    folk.statusText = "Going to my new job...";
                    folk.employedAt = box.clone();
                    folk.setTheirJob(vocation);
                    Building pending = vocation == Vocation.BUILDER ? Building.getBuildingByConBox(box) : null;
                    if (pending != null && !pending.buildingComplete)
                    {
                        folk.theBuilding = pending; // the building was chosen before anyone was hired here
                    }
                    if (SimukraftReloaded.isDayTime())
                    {
                        folk.gotoXYZ(folk.employedAt, null);
                    }
                    folk.saveThisFolk();
                }
            }
        });

        // any "Fire <name>" button
        ServerActions.register("fireFolk", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                FolkData folk = FolkData.getFolkByName(data.getString("name"));
                if (folk != null && folk.employedAt != null)
                {
                    folk.selfFire();
                }
            }
        });

        // fire everyone working at a box
        ServerActions.register("fireAllAt", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = getV3(data, "box");
                if (box == null)
                {
                    return;
                }
                for (FolkData folk : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
                {
                    if (box.isSameCoordsAs(folk.employedAt, true, true))
                    {
                        folk.selfFire();
                    }
                }
            }
        });

        BuildingActions.register();
        BoxActions.register();
        ShopActions.register();
    }

    /** client side: ask the server to fire one folk */
    public static void sendFire(String folkName)
    {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("name", folkName);
        ServerActions.send("fireFolk", data);
    }
}
