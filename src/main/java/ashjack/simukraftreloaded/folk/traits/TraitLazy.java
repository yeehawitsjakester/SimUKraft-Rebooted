package ashjack.simukraftreloaded.folk.traits;

public class TraitLazy extends Trait
{
	public TraitLazy()
	{
		super();
		setTraitName("Lazy");
		setTraitDescription("Lazy folk go to whatever's nearest and ride rather than walk. "
				+ "It doesn't bother them, but sitting about wears down their fitness over time.");
		setTraitOpposite(Traits.traitActive);
	}
}
