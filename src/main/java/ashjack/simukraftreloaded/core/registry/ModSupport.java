package ashjack.simukraftreloaded.core.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Sim-U-Kraft features that need another mod. Without the mod the feature is switched off (its buildings aren't
 * offered, its jobs say so, its upgrades are greyed out), and the server says what's missing: in the log when it
 * starts, and in chat to each player who joins. Each warning can be silenced in the config (Mods section), or all of
 * them at once.
 */
public final class ModSupport
{
    private ModSupport() {}

    public enum Feature
    {
        POWER("EnderIO", "EnderIO", "the power grid: power stations, linemen, powered lights and electric furnaces"),
        IRON_CHESTS("IronChest", "Iron Chests", "warehouse chest upgrades to iron, gold and diamond chests"),
        ME("appliedenergistics2", "Applied Energistics 2", "warehouses reading and stocking an ME network");

        public final String modId, modName, what;
        private Boolean present;

        Feature(String modId, String modName, String what)
        {
            this.modId = modId;
            this.modName = modName;
            this.what = what;
        }

        /** the mod's here, so the feature's on */
        public boolean on()
        {
            if (present == null)
            {
                present = Loader.isModLoaded(modId);
            }
            return present;
        }

        /** the config key that silences this one's warning */
        public String configKey()
        {
            return "Warn" + modName.replaceAll("[^A-Za-z0-9]", "");
        }
    }

    /** config: warn about missing mods at all, and about each */
    public static boolean warn = true;
    public static final Map<Feature, Boolean> warnEach = new EnumMap<Feature, Boolean>(Feature.class);

    /** the features switched off for want of their mod, that aren't silenced */
    public static List<Feature> missing()
    {
        List<Feature> out = new ArrayList<Feature>();
        if (!warn)
        {
            return out;
        }
        for (Feature f : Feature.values())
        {
            Boolean w = warnEach.get(f);
            if (!f.on() && (w == null || w))
            {
                out.add(f);
            }
        }
        return out;
    }

    /** to the log, when the server starts */
    public static void report()
    {
        for (Feature f : missing())
        {
            SimukraftReloaded.log.info("Sim-U-Kraft: " + f.modName + " isn't installed, so " + f.what + " is switched off"
                    + " (silence this with Mods/" + f.configKey() + "=false in the config)");
        }
    }

    /** to each player as they join */
    public static class LoginWarning
    {
        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event)
        {
            tell(event.player);
        }
    }

    public static void tell(EntityPlayer player)
    {
        List<Feature> off = missing();
        if (off.isEmpty() || player == null)
        {
            return;
        }
        player.addChatMessage(new ChatComponentText("§e[Sim-U-Kraft]§7 Some features are off because their mod isn't installed:"));
        for (Feature f : off)
        {
            player.addChatMessage(new ChatComponentText("§7 - " + f.modName + ": " + f.what));
        }
        player.addChatMessage(new ChatComponentText("§8 (silence these in the Sim-U-Kraft config, Mods section)"));
    }
}
