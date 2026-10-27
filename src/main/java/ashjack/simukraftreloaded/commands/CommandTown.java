package ashjack.simukraftreloaded.commands;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.network.TownActions;

/**
 * /town info | list | claim | rename <name> | add <player> | remove <player> | border <blocks across>
 * Everyone can use it; what it changes is checked against the town you're standing in (see TownActions).
 */
public class CommandTown extends CommandBase
{
    @Override
    public String getCommandName()
    {
        return "town";
    }

    @Override
    public String getCommandUsage(ICommandSender sender)
    {
        return "/town info | list | claim | rename <name> | add <player> | remove <player> | border <blocks across>";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender)
    {
        return sender instanceof EntityPlayer;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args)
    {
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        String rest = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
        Town here = Towns.at(player);
        String said;
        if (sub.equals("info"))
        {
            if (here == null)
            {
                List<Town> mine = Towns.of(player.getCommandSenderName());
                said = "You're in the wilds" + (mine.isEmpty() ? "" : " (your towns: " + names(mine) + ")");
            }
            else
            {
                said = here.name + " - " + (here.isClaimed() ? "owned by " + here.owner : "unclaimed: /town claim to take it on")
                        + (here.members.isEmpty() ? "" : ", run with " + String.join(", ", here.members))
                        + ". Border " + (2 * here.radius + 1) + " blocks across, treasury " + SimukraftReloaded.displayMoney(here.treasury);
            }
        }
        else if (sub.equals("list"))
        {
            said = Towns.serverTowns.isEmpty() ? "There are no towns yet - found one with a City Box" : "Towns: " + names(Towns.serverTowns);
        }
        else if (sub.equals("claim"))
        {
            said = TownActions.claim(player);
        }
        else if (sub.equals("rename"))
        {
            said = TownActions.rename(player, here, rest);
        }
        else if (sub.equals("add") || sub.equals("remove"))
        {
            said = TownActions.member(player, here, rest, sub.equals("add"));
        }
        else if (sub.equals("border"))
        {
            said = TownActions.border(player, here, (parseInt(sender, rest.trim()) - 1) / 2);
        }
        else
        {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (said != null)
        {
            sender.addChatMessage(new ChatComponentText(said));
        }
    }

    private static String names(List<Town> towns)
    {
        StringBuilder sb = new StringBuilder();
        for (Town t : towns)
        {
            sb.append(sb.length() == 0 ? "" : ", ").append(t.name).append(t.isClaimed() ? " (" + t.owner + ")" : " (unclaimed)");
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, "info", "list", "claim", "rename", "add", "remove", "border");
        }
        return null;
    }
}
