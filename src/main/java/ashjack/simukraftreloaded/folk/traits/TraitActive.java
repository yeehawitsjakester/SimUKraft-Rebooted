package ashjack.simukraftreloaded.folk.traits;

public class TraitActive extends Trait
{
	public TraitActive()
	{
		super();
		setTraitName("Active");
		setTraitDescription("Active folk enjoy a good walk and will go further for what they like. "
				+ "Walking keeps them fit, and they'd rather not ride.");
		setTraitOpposite(Traits.traitLazy);
	}
}
