package ashjack.simukraftreloaded.folk.traits;

import net.minecraft.block.Block;
import ashjack.simukraftreloaded.core.Unused;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;

public class Trait 
{
	public String traitName; // was static, so every trait had the last name set and trait generation never finished
	public String traitDesc;
	public Trait traitOpposite;
	public FolkData theFolk;
	
	public Trait()
	{
		
	}
	
	public static Trait getTraitFromName(String searchTerm)
	{
		for (Trait trait : Traits.traitList)
		{
			if (trait.traitName.equals(searchTerm))
			{
				return trait;
			}
		}
		return null;
	}
	
	public static String getTraitName(Trait trait)
	{
		return trait.traitName;
	}
	
	/**
	 * Sets the name of the trait
	 */
	public void setTraitName(String name)
	{
		this.traitName = name;
	}
	
	/**
	 * Sets the description of the trait
	 */
	public void setTraitDescription(String description)
	{
		this.traitDesc = description;
	}
	
	/**
	 * Sets the opposite trait to this one.<br>
	 * If a folk has one trait, he cannot have the opposite one too.
	 */
	public void setTraitOpposite(Trait opposite)
	{
		this.traitOpposite = opposite;
	}
	
	/** true if a folk can't have both (either side may name the other: the first one built sees a null opposite) */
	public boolean isOppositeOf(Trait other)
	{
		return traitOpposite == other || (other != null && other.traitOpposite == this);
	}

	/**
	 * Gets the name of the trait
	 */
	public String getTraitName()
	{
		return traitName;
	}
	
	/**
	 * Gets the description of the trait
	 */
	public String getTraitDescription()
	{
		return traitDesc;
	}
	
	
	/**
	 * Sets the icon of the trait
	 */
	@Unused
	public void setTraitIcon()
	{
		
	}

	/**
     * Tells the folk with this trait where their 'special' building is<br>
     * (if they have one)
     */
	public void hasSpecialBuilding(String buildingName, String visitingText)
	{
		Building specialBuilding = Building.getBuildingBySearch(buildingName);
		
		if(specialBuilding != null)
		{
			theFolk.gotoXYZ(specialBuilding.primaryXYZ, GotoMethod.WALK);
			theFolk.destination.doNotTimeout = true;
			theFolk.statusText = visitingText;
		}
	}
	
	/**
     * Tells the folk with this trait where their 'special' building is<br>
     * (if they have one)
     */
	public void hasSpecialBuilding(String buildingName)
	{
		Building specialBuilding = Building.getBuildingBySearch(buildingName);
		
		theFolk.gotoXYZ(specialBuilding.primaryXYZ, GotoMethod.WALK);
		theFolk.destination.doNotTimeout = true;
		theFolk.statusText = "Visiting the " + specialBuilding.displayName;
	}
	
}
