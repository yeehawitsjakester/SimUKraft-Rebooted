package ashjack.simukraftreloaded.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import ashjack.simukraftreloaded.entity.EntityBandit;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** a bandit: one of the Sim faces, under a dark cap (their armour), sword in hand */
@SideOnly(Side.CLIENT)
public class RenderBandit extends RenderBiped
{
    public RenderBandit()
    {
        super(new ModelBiped(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity)
    {
        int skin = entity instanceof EntityBandit ? ((EntityBandit) entity).getSkin() : 1;
        return new ResourceLocation("ashjacksimukraftreloaded", "skins/male" + skin + ".png");
    }
}
