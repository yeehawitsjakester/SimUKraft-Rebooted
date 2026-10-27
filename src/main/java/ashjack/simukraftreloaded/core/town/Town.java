package ashjack.simukraftreloaded.core.town;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A town: founded by a player at a City Box, with a name, an owner and members, a square border around the box, and a
 * treasury that pays for everything done inside it (wages, building) and takes the rent. Buildings and Sims belong to
 * the town whose border they're inside (see Towns.at).
 *
 * Saved one file per town in the world's simukraft/Towns folder; sent to clients for the border and the HUD.
 */
public class Town implements Serializable
{
    private static final long serialVersionUID = 1L;

    public int id;
    public String name = "Town";
    /** the founding player's name, or empty for an unclaimed town (one made from a world that had no towns yet) */
    public String owner = "";
    public ArrayList<String> members = new ArrayList<String>();
    /** the City Box, or the middle of town for an unclaimed one */
    public V3 centre;
    /** half the border's width: it runs radius blocks out from the centre each way */
    public int radius = 64;
    public float treasury = 0f;
    public long founded;

    public static final int MIN_RADIUS = 32, MAX_RADIUS = 256;

    public boolean isClaimed()
    {
        return owner != null && !owner.isEmpty();
    }

    public int dimension()
    {
        return centre == null ? 0 : centre.theDimension;
    }

    /** inside the border (any height) */
    public boolean contains(int dim, double x, double z)
    {
        return centre != null && dim == centre.theDimension && Math.abs(x - centre.x) <= radius && Math.abs(z - centre.z) <= radius;
    }

    public boolean contains(V3 v)
    {
        return v != null && contains(v.theDimension, v.x, v.z);
    }

    public boolean isOwner(String player)
    {
        return player != null && player.equalsIgnoreCase(owner);
    }

    public boolean isMember(String player)
    {
        if (isOwner(player))
        {
            return true;
        }
        for (String m : members)
        {
            if (m.equalsIgnoreCase(player))
            {
                return true;
            }
        }
        return false;
    }

    /** may this player hire, fire and build here: the owner, members, and server operators */
    public boolean canManage(EntityPlayer player)
    {
        return player != null && (isMember(player.getCommandSenderName()) || isOp(player));
    }

    public static boolean isOp(EntityPlayer player)
    {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || player == null)
        {
            return false;
        }
        if (server.isSinglePlayer())
        {
            return true; // your own world
        }
        return server.getConfigurationManager().func_152596_g(player.getGameProfile());
    }

    // ---------------------------------------------------------------- saving

    public File saveFile()
    {
        return new File(SimukraftReloaded.getSavesDataFolder() + "Towns" + File.separator + "t" + id + ".sk2");
    }

    public void save()
    {
        if (SimukraftReloaded.isClientThread())
        {
            return;
        }
        saveFile().getParentFile().mkdirs();
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("id|" + id);
        lines.add("name|" + name);
        lines.add("owner|" + (owner == null ? "" : owner));
        lines.add("members|" + String.join(",", members));
        lines.add("centre|" + centre);
        lines.add("radius|" + radius);
        lines.add("treasury|" + treasury);
        lines.add("founded|" + founded);
        SimukraftReloaded.saveSK2(saveFile().getPath(), lines);
    }

    public static Town load(File f)
    {
        Town t = new Town();
        for (String line : SimukraftReloaded.loadSK2(f.getAbsolutePath()))
        {
            int bar = line.indexOf('|');
            if (bar < 0)
            {
                continue;
            }
            String k = line.substring(0, bar), v = line.substring(bar + 1);
            try
            {
                if (k.equals("id")) t.id = Integer.parseInt(v);
                else if (k.equals("name")) t.name = v;
                else if (k.equals("owner")) t.owner = v;
                else if (k.equals("members"))
                {
                    for (String m : v.split(","))
                    {
                        if (!m.trim().isEmpty())
                        {
                            t.members.add(m.trim());
                        }
                    }
                }
                else if (k.equals("centre")) t.centre = new V3(v);
                else if (k.equals("radius")) t.radius = Integer.parseInt(v);
                else if (k.equals("treasury")) t.treasury = Float.parseFloat(v);
                else if (k.equals("founded")) t.founded = Long.parseLong(v);
            }
            catch (Exception e)
            {
                SimukraftReloaded.log.warning("Town: bad line in " + f.getName() + ": " + line);
            }
        }
        return t.centre == null ? null : t;
    }
}
