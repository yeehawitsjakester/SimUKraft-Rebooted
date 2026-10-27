package ashjack.simukraftreloaded.network;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Founding and running towns, for the City Box screen and the /town command. Each returns what to tell the player.
 * Only a town's owner (or an op) changes who's in it, its border, its name, or disbands it.
 */
public final class TownActions
{
    private TownActions() {}

    public static final int FIRST_RADIUS = 64, BORDER_STEP = 16;
    /** what a bigger border costs the treasury, per block of new ground */
    public static final float COST_PER_BLOCK = 0.01f;

    public static String found(EntityPlayer player, V3 box, String name)
    {
        name = clean(name);
        if (name.isEmpty())
        {
            return "Give the town a name";
        }
        if (Towns.byName(name) != null)
        {
            return "There's already a town called " + name;
        }
        Town here = Towns.at(box);
        if (here != null)
        {
            return "This is already part of " + here.name;
        }
        Town near = Towns.overlapping(box, FIRST_RADIUS, null);
        if (near != null)
        {
            return "Too close to " + near.name + " - its border is in the way";
        }
        Town t = Towns.found(name, player.getCommandSenderName(), box, FIRST_RADIUS);
        SimukraftReloaded.sendChat(player.getCommandSenderName() + " has founded the town of " + t.name + "!");
        return null;
    }

    /** a town from before towns existed (no owner): the first to claim it, standing in it, owns it */
    public static String claim(EntityPlayer player)
    {
        Town t = Towns.at(player);
        if (t == null)
        {
            return "You're not in a town";
        }
        if (t.isClaimed())
        {
            return t.name + " belongs to " + t.owner;
        }
        t.owner = player.getCommandSenderName();
        t.save();
        SimukraftReloaded.sendChat(player.getCommandSenderName() + " has claimed the town of " + t.name);
        return null;
    }

    public static String rename(EntityPlayer player, Town t, String name)
    {
        String why = ownerOnly(player, t);
        if (why != null)
        {
            return why;
        }
        name = clean(name);
        if (name.isEmpty() || Towns.byName(name) != null && Towns.byName(name) != t)
        {
            return name.isEmpty() ? "Give the town a name" : "There's already a town called " + name;
        }
        String old = t.name;
        t.name = name;
        t.save();
        SimukraftReloaded.sendChat(old + " is now called " + name);
        return null;
    }

    public static String member(EntityPlayer player, Town t, String who, boolean add)
    {
        String why = ownerOnly(player, t);
        if (why != null)
        {
            return why;
        }
        who = who.trim();
        if (who.isEmpty() || t.isOwner(who))
        {
            return null;
        }
        if (add)
        {
            if (!t.isMember(who))
            {
                t.members.add(who);
            }
        }
        else
        {
            for (int i = t.members.size() - 1; i >= 0; i--)
            {
                if (t.members.get(i).equalsIgnoreCase(who))
                {
                    t.members.remove(i);
                }
            }
        }
        t.save();
        return who + (add ? " can now run " : " can no longer run ") + t.name;
    }

    /** grows or shrinks the border; growing it costs the treasury for the new ground */
    public static String border(EntityPlayer player, Town t, int radius)
    {
        String why = ownerOnly(player, t);
        if (why != null)
        {
            return why;
        }
        radius = Math.max(Town.MIN_RADIUS, Math.min(Town.MAX_RADIUS, radius));
        if (radius == t.radius)
        {
            return "The border's already " + (2 * radius + 1) + " blocks across";
        }
        Town near = Towns.overlapping(t.centre, radius, t);
        if (near != null)
        {
            return "That would run into " + near.name;
        }
        float cost = radius > t.radius ? (float) ((Math.pow(2 * radius + 1, 2) - Math.pow(2 * t.radius + 1, 2)) * COST_PER_BLOCK) : 0;
        if (cost > Economy.balance(t))
        {
            return "Moving the border out costs " + SimukraftReloaded.displayMoney(cost) + " - the treasury has "
                    + SimukraftReloaded.displayMoney(Economy.balance(t));
        }
        Economy.spend(t, cost);
        t.radius = radius;
        t.save();
        return t.name + "'s border is now " + (2 * radius + 1) + " blocks across" + (cost > 0 ? " (" + SimukraftReloaded.displayMoney(cost) + ")" : "");
    }

    /** the town is gone (its buildings and Sims carry on, in the wilds); the City Box comes back */
    public static String disband(EntityPlayer player, Town t)
    {
        String why = ownerOnly(player, t);
        if (why != null)
        {
            return why;
        }
        Towns.disband(t);
        Economy.earn((Town) null, t.treasury); // what's left goes to the wilds
        World world = MinecraftServer.getServer().worldServerForDimension(t.dimension());
        int x = t.centre.x.intValue(), y = t.centre.y.intValue(), z = t.centre.z.intValue();
        if (world != null && world.getBlock(x, y, z) == SimukraftReloadedBlocks.cityBox)
        {
            world.setBlock(x, y, z, Blocks.air);
            world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, new ItemStack(SimukraftReloadedBlocks.cityBox)));
        }
        SimukraftReloaded.sendChat(t.name + " has been disbanded");
        return null;
    }

    private static String ownerOnly(EntityPlayer player, Town t)
    {
        if (t == null)
        {
            return "There's no town here";
        }
        if (!t.isOwner(player.getCommandSenderName()) && !Town.isOp(player) && t.isClaimed())
        {
            return "Only " + t.owner + " can do that";
        }
        return null;
    }

    private static String clean(String name)
    {
        name = name == null ? "" : name.replaceAll("[|,\\n]", "").trim();
        return name.length() > 24 ? name.substring(0, 24) : name;
    }

    static void register()
    {
        ServerActions.register("townAction", new ServerActions.Action()
        {
            public void run(EntityPlayerMP player, NBTTagCompound data)
            {
                V3 box = ActionHandlers.getV3(data, "box");
                Town t = Towns.at(box);
                String what = data.getString("what"), arg = data.getString("arg"), said;
                if (what.equals("found"))
                {
                    said = found(player, box, arg);
                }
                else if (what.equals("rename"))
                {
                    said = rename(player, t, arg);
                }
                else if (what.equals("add") || what.equals("remove"))
                {
                    said = member(player, t, arg, what.equals("add"));
                }
                else if (what.equals("border"))
                {
                    said = border(player, t, t == null ? 0 : t.radius + data.getInteger("by"));
                }
                else if (what.equals("disband"))
                {
                    said = disband(player, t);
                }
                else
                {
                    return;
                }
                if (said != null)
                {
                    ActionHandlers.tell(player, said);
                }
            }
        });
    }
}
