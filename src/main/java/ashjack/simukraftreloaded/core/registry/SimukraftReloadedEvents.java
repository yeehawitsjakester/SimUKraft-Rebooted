package ashjack.simukraftreloaded.core.registry;

import ashjack.simukraftreloaded.core.UpdateChecker;
import cpw.mods.fml.common.FMLCommonHandler;

public class SimukraftReloadedEvents 
{
	public static void loadEvents()
	{
		// The update checker downloaded from a dead Dropbox URL, synchronously, on every login
		//FMLCommonHandler.instance().bus().register(new UpdateChecker());
	}
}
