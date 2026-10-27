package ashjack.simukraftreloaded.commands;

import net.minecraft.entity.player.EntityPlayer;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Economy;
import java.util.ArrayList;
import java.util.List;

import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

public class CommandChangeCredits implements ICommand{
	
	private final List aliases;
	
	public CommandChangeCredits()
	{
		aliases = new ArrayList(); 
        aliases.add("credits"); 
	}

	@Override
	public int compareTo(Object arg0) 
	{
		return 0;
	}

	@Override
	public String getCommandName() 
	{
		return "credits";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) 
	{
		return "changecredits <amount>";
	}

	@Override
	public List getCommandAliases() 
	{
		return this.aliases;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] argString) 
	{
		if(argString.length == 0)
		{
			SimukraftReloaded.sendChat("Invalid arguments, should be: /credits <amount>");
			return;
		}
		
		try
		{
			if(argString.length < 2)
			{
				// the treasury of the town you're standing in (the shared credits out in the wilds)
				Town town = sender instanceof EntityPlayer ? Towns.at((EntityPlayer) sender) : null;
				Economy.set(town, Float.parseFloat(argString[0]));
				if (town != null)
				{
					town.save();
				}
				SimukraftReloaded.states().saveStates();
			}
			else
			{
				SimukraftReloaded.sendChat("Invalid arguments, should be: /credits <amount>");
				return;
			}
		}
		catch(Exception e)
		{
			SimukraftReloaded.sendChat("The amount must be a number!");
			return;
		}
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender p_71519_1_) 
	{
		return true;
	}

	@Override
	public List addTabCompletionOptions(ICommandSender p_71516_1_, String[] p_71516_2_) 
	{
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) 
	{
		return false;
	}

}
