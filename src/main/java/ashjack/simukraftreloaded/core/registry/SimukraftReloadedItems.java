package ashjack.simukraftreloaded.core.registry;

import ashjack.simukraftreloaded.items.ItemSUKDrink;
import ashjack.simukraftreloaded.items.ItemSUKFood;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class SimukraftReloadedItems 
{
	
	//static Block pathConstructor;
    // static int pathConstructorId = 0;
    //public static Item itemGranulesCopper;
    //public static int itemGranulesCopperId;
    //public static Item itemGranulesTin;
    //public static int itemGranulesTinId;
    public static Item itemFood;
    public static Item itemFoodCheese;
    public static Item itemFoodFries;
    public static Item itemFoodBurger;
    public static Item itemFoodCheeseburger;
    
    public static Item itemDrink;
    public static Item itemDrinkBeer;
	
	public static void loadItems()
	{
		itemDrink = new ItemSUKDrink().setUnlocalizedName("SUKdrink");
		
	//  itemGranulesCopper=new ItemGranulesCopper(itemGranulesCopperId); 
	//  itemGranulesTin=new ItemGranulesTin(itemGranulesTinId); 
		
	//	Item.itemsList[lightBox] = new ItemBlockLightBox(lightBox, lightBox);

		registerItems();
		nameItems();
	}
	
	public static void registerItems()
	{
		GameRegistry.registerItem(itemFood = new ItemSUKFood("SUKfood"), "SUKfood");
	}
	
	public static void nameItems()
	{
        
		
		LanguageRegistry.addName(new ItemStack(itemFood, 1, 0), "Cheese Slice");
        LanguageRegistry.addName(new ItemStack(itemFood, 1, 1), "Hamburger");
        LanguageRegistry.addName(new ItemStack(itemFood, 1, 2), "Fries");
        LanguageRegistry.addName(new ItemStack(itemFood, 1, 3), "Cheeseburger");
        
        LanguageRegistry.addName(new ItemStack(itemDrink, 1, 0), "Empty Beer Bottle");
        LanguageRegistry.addName(new ItemStack(itemDrink, 1, 1), "Beer Bottle");
	}
}
