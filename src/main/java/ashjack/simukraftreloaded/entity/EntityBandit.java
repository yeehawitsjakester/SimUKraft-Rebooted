package ashjack.simukraftreloaded.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIOpenDoor;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.military.Crime;
import ashjack.simukraftreloaded.common.warehouse.WarehouseStock;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * A bandit from out of town (see Crime). Walks to a chest in town, takes a few stacks, and runs for it; if they get
 * far enough away they're gone with the loot, and if they're beaten they drop it. They only fight whoever attacks
 * them - soldiers, mostly - and never hang around past daybreak.
 */
public class EntityBandit extends EntityMob
{
    private static final int DW_SKIN = 20;

    /** what they've stolen, dropped if they're beaten */
    private final List<ItemStack> loot = new ArrayList<ItemStack>();
    private String robbed = null;

    public EntityBandit(World world)
    {
        super(world);
        setSize(0.6F, 1.8F);
        getNavigator().setBreakDoors(false);
        getNavigator().setEnterDoors(true);
        getNavigator().setAvoidsWater(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIAttackOnCollide(this, EntityLivingBase.class, 1.2D, false));
        tasks.addTask(2, new Steal());
        tasks.addTask(3, new EntityAIOpenDoor(this, true));
        tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(5, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        setCustomNameTag("Bandit");
        experienceValue = 10;
    }

    @Override
    protected void entityInit()
    {
        super.entityInit();
        dataWatcher.addObject(DW_SKIN, Integer.valueOf(1));
    }

    /** a sword and a dark cap, and one of the Sim faces */
    public void equip()
    {
        setCurrentItemOrArmor(0, new ItemStack(rand.nextInt(3) == 0 ? Items.iron_sword : Items.stone_sword));
        ItemStack cap = new ItemStack(Items.leather_helmet);
        NBTTagCompound display = new NBTTagCompound();
        display.setInteger("color", 0x1c1c1c);
        cap.setTagInfo("display", display);
        setCurrentItemOrArmor(4, cap);
        setEquipmentDropChance(0, 0.1F);
        setEquipmentDropChance(4, 0.0F);
        dataWatcher.updateObject(DW_SKIN, Integer.valueOf(1 + rand.nextInt(60)));
    }

    public int getSkin()
    {
        return dataWatcher.getWatchableObjectInt(DW_SKIN);
    }

    @Override
    protected void applyEntityAttributes()
    {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(24.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(3.0D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(40.0D);
    }

    @Override
    protected boolean isAIEnabled()
    {
        return true;
    }

    @Override
    protected boolean canDespawn()
    {
        return false; // they leave by running off, with or without the loot
    }

    @Override
    protected String getHurtSound()
    {
        return "game.player.hurt";
    }

    @Override
    protected String getDeathSound()
    {
        return "game.player.die";
    }

    @Override
    protected String getLivingSound()
    {
        return null;
    }

    @Override
    public void onDeath(DamageSource source)
    {
        super.onDeath(source);
        if (!worldObj.isRemote)
        {
            int n = 0;
            for (ItemStack is : loot)
            {
                entityDropItem(is, 0.5F);
                n += is.stackSize;
            }
            loot.clear();
            SimukraftReloaded.sendChat(n > 0 ? "A bandit was stopped - " + n + " stolen items are back on the ground"
                    : "A bandit was stopped");
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag)
    {
        super.writeEntityToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (ItemStack is : loot)
        {
            list.appendTag(is.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("Loot", list);
        tag.setInteger("Skin", getSkin());
        if (robbed != null)
        {
            tag.setString("Robbed", robbed);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag)
    {
        super.readEntityFromNBT(tag);
        loot.clear();
        NBTTagList list = tag.getTagList("Loot", 10);
        for (int i = 0; i < list.tagCount(); i++)
        {
            ItemStack is = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (is != null)
            {
                loot.add(is);
            }
        }
        dataWatcher.updateObject(DW_SKIN, Integer.valueOf(Math.max(1, tag.getInteger("Skin"))));
        robbed = tag.hasKey("Robbed") ? tag.getString("Robbed") : null;
    }

    /** go to a chest in town, take a few stacks, run off; gone once far enough away */
    private class Steal extends EntityAIBase
    {
        private int[] chest;
        private String chestOf;
        private long started, lastPath;
        private boolean fleeing;

        Steal()
        {
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute()
        {
            return getAttackTarget() == null;
        }

        @Override
        public boolean continueExecuting()
        {
            return getAttackTarget() == null;
        }

        @Override
        public void startExecuting()
        {
            if (started == 0)
            {
                started = worldObj.getTotalWorldTime();
            }
            fleeing = fleeing || !loot.isEmpty() || worldObj.isDaytime();
            if (!fleeing && chest == null)
            {
                pickChest();
            }
        }

        private void pickChest()
        {
            List<int[]> chests = new ArrayList<int[]>();
            List<String> owners = new ArrayList<String>();
            for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
            {
                if (b.primaryXYZ == null || b.primaryXYZ.theDimension != dimension || b.isRoom()
                        || getDistance(b.primaryXYZ.x, b.primaryXYZ.y, b.primaryXYZ.z) > 96)
                {
                    continue;
                }
                int bx = b.primaryXYZ.x.intValue(), by = b.primaryXYZ.y.intValue(), bz = b.primaryXYZ.z.intValue();
                for (int x = bx - 6; x <= bx + 6; x++)
                {
                    for (int y = by - 2; y <= by + 4; y++)
                    {
                        for (int z = bz - 6; z <= bz + 6; z++)
                        {
                            IInventory inv = WarehouseStock.storageAt(worldObj, x, y, z);
                            if (inv != null && hasItems(inv))
                            {
                                chests.add(new int[] {x, y, z});
                                owners.add(b.displayNameWithoutPK);
                            }
                        }
                    }
                }
            }
            if (chests.isEmpty())
            {
                fleeing = true; // nothing worth taking
                return;
            }
            int i = rand.nextInt(chests.size());
            chest = chests.get(i);
            chestOf = owners.get(i);
        }

        @Override
        public void updateTask()
        {
            long now = worldObj.getTotalWorldTime();
            if (!fleeing && (worldObj.isDaytime() || now - started > 20 * 150))
            {
                fleeing = true; // daybreak, or it's taking too long
            }
            if (fleeing)
            {
                flee(now);
                return;
            }
            if (chest == null)
            {
                pickChest();
                return;
            }
            double d = getDistance(chest[0] + 0.5, chest[1], chest[2] + 0.5);
            if (d <= 2.5)
            {
                rob();
                fleeing = true;
            }
            else if (now - lastPath > 40 || getNavigator().noPath())
            {
                lastPath = now;
                moveTowards(chest[0] + 0.5, chest[1], chest[2] + 0.5, 1.0D);
            }
        }

        private void rob()
        {
            IInventory inv = WarehouseStock.storageAt(worldObj, chest[0], chest[1], chest[2]);
            if (inv == null)
            {
                chest = null;
                return;
            }
            List<Integer> slots = new ArrayList<Integer>();
            for (int s = 0; s < inv.getSizeInventory(); s++)
            {
                if (inv.getStackInSlot(s) != null)
                {
                    slots.add(s);
                }
            }
            int n = 0;
            for (int k = 0; k < 3 && !slots.isEmpty(); k++)
            {
                int s = slots.remove(rand.nextInt(slots.size()));
                ItemStack taken = inv.decrStackSize(s, inv.getStackInSlot(s).stackSize);
                if (taken != null)
                {
                    loot.add(taken);
                    n += taken.stackSize;
                }
            }
            inv.markDirty();
            worldObj.playSoundAtEntity(EntityBandit.this, "random.chestopen", 0.5F, 1.0F);
            if (n > 0)
            {
                robbed = chestOf;
                SimukraftReloaded.sendChat("A bandit is robbing the " + chestOf + "!");
            }
        }

        private void flee(long now)
        {
            V3 centre = Crime.townCentre(dimension);
            if (centre == null || getDistance(centre.x, posY, centre.z) > 72 || now - started > 20 * 400)
            {
                int n = 0;
                for (ItemStack is : loot)
                {
                    n += is.stackSize;
                }
                if (n > 0)
                {
                    SimukraftReloaded.sendChat("A bandit got away with " + n + " items" + (robbed != null ? " from the " + robbed : ""));
                }
                setDead();
                return;
            }
            if (now - lastPath > 40 || getNavigator().noPath())
            {
                lastPath = now;
                double dx = posX - centre.x, dz = posZ - centre.z, len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                moveTowards(posX + dx / len * 24, posY, posZ + dz / len * 24, 1.3D);
            }
        }

        /** vanilla paths only reach so far: head for a spot partway there */
        private void moveTowards(double x, double y, double z, double speed)
        {
            if (getDistance(x, y, z) > 28)
            {
                Vec3 v = RandomPositionGenerator.findRandomTargetBlockTowards(EntityBandit.this, 16, 7, Vec3.createVectorHelper(x, y, z));
                if (v != null)
                {
                    getNavigator().tryMoveToXYZ(v.xCoord, v.yCoord, v.zCoord, speed);
                    return;
                }
            }
            getNavigator().tryMoveToXYZ(x, y, z, speed);
        }
    }

    private static boolean hasItems(IInventory inv)
    {
        for (int s = 0; s < inv.getSizeInventory(); s++)
        {
            if (inv.getStackInSlot(s) != null)
            {
                return true;
            }
        }
        return false;
    }
}
