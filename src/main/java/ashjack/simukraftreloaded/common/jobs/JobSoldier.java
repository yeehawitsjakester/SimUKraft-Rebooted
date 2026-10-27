package ashjack.simukraftreloaded.common.jobs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import ashjack.simukraftreloaded.common.MineSafety;
import ashjack.simukraftreloaded.common.WorldUtil;
import ashjack.simukraftreloaded.common.military.Armory;
import ashjack.simukraftreloaded.common.military.Armory.Rank;
import ashjack.simukraftreloaded.common.military.Crime;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.entity.EntityBandit;
import ashjack.simukraftreloaded.entity.EntityFolk;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

import com.mojang.authlib.GameProfile;

/**
 * Keeps the town safe, day and night. Soldiers walk a loop of the town's buildings (each starting at a different
 * point, so they spread out), and go after hostile mobs near them or near anyone in town, bandits, and wanted players
 * (see Crime) - no more than two to one mob, so the rest keep patrolling.
 *
 * How they fight depends on their rank (see Armory): recruits just use a sword; archers shoot anything a few blocks
 * off; tacticians shoot creepers, skeletons, witches, blazes and ghasts from a distance and back off from creepers;
 * veterans can use modded weapons too. Swords hit through a Forge fake player, so a weapon's damage, enchantments and
 * mod effects all count and it wears out; arrows come out of what they carry. Players are never killed: soldiers wear
 * them down with blunt blows and arrest them.
 */
public class JobSoldier extends Job implements Serializable
{
    public Vocation vocation = null;
    public FolkData theFolk = null;
    public Stage theStage;
    transient public int runDelay = 1000;
    transient public long timeSinceLastRun = 0;

    /** today's hostile mob kills, and bounties for bandits and arrests: paid in the morning */
    public transient int kills = 0;
    public transient float bounty = 0;

    private transient Entity target;
    private transient long targetSince, lastHit, lastShot, lastRepath, lastScan, lastHeal, lingerUntil;
    private transient double goalX, goalZ;
    private transient int waypoint = -1;
    private transient boolean needEquip = true;
    private transient List<V3> route;
    private transient long routeBuilt;

    /** how many soldiers are after each entity (by entity id), so they don't all chase one mob */
    private static final Map<Integer, Integer> claims = new HashMap<Integer, Integer>();
    /** modded bows that threw an exception when a soldier tried them, so nobody tries again */
    private static final Set<String> unusable = new HashSet<String>();
    private static final GameProfile SOLDIER_PROFILE = new GameProfile(UUID.nameUUIDFromBytes("SimUKraftSoldier".getBytes()), "[Sim-U-Soldier]");

    public JobSoldier()
    {
    }

    public JobSoldier(FolkData folk)
    {
        theFolk = folk;

        if (theStage == null)
        {
            theStage = Stage.IDLE;
        }

        if (theFolk == null)
        {
            return;
        } // is null when first employing, this is for next day(s)

        if (theFolk.destination == null)
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }

    public enum Stage
    {
        IDLE, ONPATROL, ATTACKING;
    }

    public void resetJob()
    {
        release();
        theStage = Stage.IDLE;
    }

    public Rank rank()
    {
        return Rank.of(theFolk.levelSoldier);
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();

        if (System.currentTimeMillis() - timeSinceLastRun < runDelay)
        {
            return;
        }
        timeSinceLastRun = System.currentTimeMillis();

        if (theFolk.employedAt == null)
        {
            return;
        }
        if (jobWorld == null)
        {
            jobWorld = MinecraftServer.getServer().worldServerForDimension(theFolk.employedAt.theDimension);
        }
        if (jobWorld == null || !theFolk.isSpawned())
        {
            return; // nobody to fight with while they're not in a loaded part of the world
        }

        long now = System.currentTimeMillis();
        EntityFolk me = theFolk.theEntity;
        // they patch themselves up between fights
        if (theStage != Stage.ATTACKING && now - lastHeal > 4000 && me.getHealth() < me.getMaxHealth())
        {
            me.heal(1.0F);
            lastHeal = now;
        }

        if (theStage == null || theStage == Stage.IDLE)
        {
            theStage = Stage.ONPATROL;
            waypoint = 0; // report to the barracks first
        }
        if (theStage == Stage.ONPATROL)
        {
            runDelay = 750;
            patrol(now);
        }
        else if (theStage == Stage.ATTACKING)
        {
            runDelay = 250;
            fight(now);
        }
    }

    // ---------------------------------------------------------------- patrol

    private void patrol(long now)
    {
        theFolk.isWorking = false;
        theFolk.action = FolkAction.ATWORK;
        ItemStack weapon = Armory.carriedWeapon(theFolk);
        theFolk.heldItem = weapon;

        if (now - lastScan > 1500)
        {
            lastScan = now;
            Entity found = findTarget();
            if (found != null)
            {
                target = found;
                targetSince = now;
                lastRepath = 0;
                claims.put(found.getEntityId(), claimed(found) + 1);
                theStage = Stage.ATTACKING;
                theFolk.statusText = "Going after " + nameOf(found);
                return;
            }
        }

        List<V3> stops = route(now);
        if (stops.isEmpty())
        {
            return;
        }
        // back to the barracks when they're out of arrows, or just promoted and due better gear
        ItemStack bow = Armory.carriedBow(theFolk);
        if ((bow != null && Armory.carriedArrows(theFolk) < 8) || (needEquip && waypoint != 0 && rank().atLeast(Rank.ARCHER)))
        {
            waypoint = 0;
        }
        if (waypoint < 0 || waypoint >= stops.size())
        {
            waypoint = startingStop(stops.size());
        }
        V3 stop = stops.get(waypoint);
        theFolk.updateLocationFromEntity();
        if (theFolk.location.getDistanceTo(stop) <= 3)
        {
            if (lingerUntil == 0)
            {
                lingerUntil = now + 8000; // a look around at each stop
                if (waypoint == 0)
                {
                    String took = Armory.equip(theFolk, jobWorld);
                    needEquip = false;
                    if (took != null)
                    {
                        theFolk.statusText = "At the armory: " + took;
                        return;
                    }
                }
            }
            if (now > lingerUntil)
            {
                lingerUntil = 0;
                waypoint = (waypoint + 1) % stops.size();
            }
            theFolk.statusText = rank().title + " on patrol";
            return;
        }
        theFolk.statusText = rank().title + " on patrol" + (waypoint == 0 ? " (back to the barracks)" : "");
        if (theFolk.destination == null)
        {
            theFolk.gotoXYZ(stop, GotoMethod.WALK);
        }
    }

    /** the town's buildings near the barracks, in a loop around it, starting with the barracks */
    private List<V3> route(long now)
    {
        if (route != null && now - routeBuilt < 60000)
        {
            return route;
        }
        routeBuilt = now;
        final V3 base = theFolk.employedAt;
        List<V3> stops = new ArrayList<V3>();
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b.primaryXYZ != null && !b.isRoom() && b.primaryXYZ.theDimension == base.theDimension
                    && !b.primaryXYZ.isSameCoordsAs(base, true, true)
                    && base.getDistanceTo(b.primaryXYZ) <= SimukraftReloadedConfig.configSoldierPatrolRadius)
            {
                V3 at = b.primaryXYZ.clone();
                int y = WorldUtil.findStandableY(jobWorld, at.x.intValue(), at.y.intValue(), at.z.intValue(), 6);
                if (y >= 0)
                {
                    at.y = (double) y;
                }
                stops.add(at);
            }
        }
        Collections.sort(stops, new Comparator<V3>()
        {
            public int compare(V3 a, V3 b)
            {
                return Double.compare(Math.atan2(a.z - base.z, a.x - base.x), Math.atan2(b.z - base.z, b.x - base.x));
            }
        });
        stops.add(0, base.clone());
        route = stops;
        return route;
    }

    /** where on the loop this soldier starts, so the barracks' soldiers are spread round it */
    private int startingStop(int stops)
    {
        List<String> squad = new ArrayList<String>();
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.serverFolks))
        {
            if (f.vocation == Vocation.SOLDIER && theFolk.employedAt.isSameCoordsAs(f.employedAt, true, true))
            {
                squad.add(f.name);
            }
        }
        Collections.sort(squad);
        int i = Math.max(0, squad.indexOf(theFolk.name));
        return squad.isEmpty() ? 0 : (i * stops / squad.size()) % stops;
    }

    // ---------------------------------------------------------------- finding trouble

    private Entity findTarget()
    {
        EntityFolk me = theFolk.theEntity;
        V3 base = theFolk.employedAt;
        int radius = SimukraftReloadedConfig.configSoldierPatrolRadius;
        List<?> near = jobWorld.getEntitiesWithinAABBExcludingEntity(me, me.boundingBox.expand(32, 10, 32));
        List<Entity> townsfolk = new ArrayList<Entity>();
        for (Object o : jobWorld.loadedEntityList)
        {
            Entity e = (Entity) o;
            if ((e instanceof EntityFolk || e instanceof EntityPlayer) && e.getDistance(base.x, base.y, base.z) <= radius)
            {
                townsfolk.add(e);
            }
        }
        // anything bothering people elsewhere in town, too
        List<Object> candidates = new ArrayList<Object>(near);
        for (Entity t : townsfolk)
        {
            candidates.addAll(jobWorld.getEntitiesWithinAABBExcludingEntity(t, t.boundingBox.expand(10, 4, 10)));
        }

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Object o : candidates)
        {
            Entity e = (Entity) o;
            if (e.isDead || !(e instanceof EntityLivingBase) || ((EntityLivingBase) e).getHealth() <= 0
                    || e.getDistance(base.x, base.y, base.z) > radius
                    || MineSafety.inMine(e.dimension, e.posX, e.posY, e.posZ, 1))
            {
                continue;
            }
            double score = me.getDistanceToEntity(e);
            int limit;
            if (e instanceof EntityBandit)
            {
                score -= 16;
                limit = 3;
            }
            else if (e instanceof EntityPlayer)
            {
                if (!Crime.isWanted((EntityPlayer) e))
                {
                    continue;
                }
                score -= 8;
                limit = 3;
            }
            else if (e instanceof IMob)
            {
                limit = 2;
            }
            else
            {
                continue;
            }
            if (claimed(e) >= limit || score >= bestScore)
            {
                continue;
            }
            best = e;
            bestScore = score;
        }
        return best;
    }

    private static int claimed(Entity e)
    {
        Integer n = claims.get(e.getEntityId());
        return n == null ? 0 : n;
    }

    private void release()
    {
        if (target != null)
        {
            int n = claimed(target) - 1;
            if (n <= 0)
            {
                claims.remove(target.getEntityId());
            }
            else
            {
                claims.put(target.getEntityId(), n);
            }
        }
        target = null;
        if (theFolk != null)
        {
            theFolk.stayPut = false;
        }
    }

    private void backToPatrol(String status)
    {
        release();
        theStage = Stage.ONPATROL;
        theFolk.isWorking = false;
        theFolk.destination = null;
        if (status != null)
        {
            theFolk.statusText = status;
        }
    }

    // ---------------------------------------------------------------- fighting

    private void fight(long now)
    {
        EntityFolk me = theFolk.theEntity;
        V3 base = theFolk.employedAt;
        if (target == null || target.worldObj != jobWorld)
        {
            backToPatrol(null);
            return;
        }
        boolean dead = target.isDead || target instanceof EntityLivingBase && ((EntityLivingBase) target).getHealth() <= 0;
        if (dead)
        {
            killed();
            return;
        }
        if (target.getDistance(base.x, base.y, base.z) > SimukraftReloadedConfig.configSoldierPatrolRadius + 24
                || now - targetSince > 60000)
        {
            backToPatrol("Lost " + nameOf(target));
            return;
        }

        double dist = me.getDistanceToEntity(target);
        boolean canSee = me.canEntityBeSeen(target);
        theFolk.isWorking = true;

        // players: worn down with blunt blows and arrested, never killed
        if (target instanceof EntityPlayer)
        {
            EntityPlayer p = (EntityPlayer) target;
            if (!Crime.isWanted(p))
            {
                backToPatrol("Stood down");
                return;
            }
            theFolk.heldItem = Armory.carriedWeapon(theFolk);
            if (dist <= 3 && p.getHealth() <= 6)
            {
                Crime.arrest(p, theFolk.name);
                bounty += 1.0f;
                gainLevel(0.2f);
                backToPatrol("Arrested " + p.getCommandSenderName());
                return;
            }
            melee(now, dist);
            return;
        }

        Rank rank = rank();
        ItemStack bow = Armory.carriedBow(theFolk);
        boolean hasBow = bow != null && Armory.carriedArrows(theFolk) > 0 && rank.atLeast(Rank.ARCHER);
        boolean creeper = target instanceof EntityCreeper;
        boolean shootsBack = target instanceof IRangedAttackMob || target instanceof EntityBlaze || target instanceof EntityGhast;
        boolean ranged = false;
        if (hasBow)
        {
            if (rank.atLeast(Rank.TACTICIAN) && (creeper || shootsBack))
            {
                ranged = true; // never get close to those
            }
            else
            {
                ranged = dist >= 4 && canSee;
            }
        }

        if (ranged && creeper && dist < 6)
        {
            // too close to a creeper: back off, then shoot
            theFolk.heldItem = bow;
            theFolk.statusText = "Backing away from a Creeper";
            if (now - lastRepath > 800)
            {
                lastRepath = now;
                double dx = me.posX - target.posX, dz = me.posZ - target.posZ, len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                int x = (int) Math.floor(me.posX + dx / len * 8), z = (int) Math.floor(me.posZ + dz / len * 8);
                int y = WorldUtil.findStandableY(jobWorld, x, (int) me.posY, z, 4);
                if (y >= 0)
                {
                    theFolk.gotoXYZ(new V3((double) x, (double) y, (double) z, me.dimension), GotoMethod.WALK);
                }
            }
            return;
        }

        if (ranged)
        {
            theFolk.heldItem = bow;
            if (!canSee || dist > 20)
            {
                approach(now);
                theFolk.statusText = "Closing in on " + nameOf(target);
                return;
            }
            theFolk.stayPut = true;
            me.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
            theFolk.statusText = "Shooting at " + nameOf(target);
            if (now - lastShot >= 2000 - (long) (theFolk.levelSoldier * 100))
            {
                lastShot = now;
                shoot(bow);
            }
            return;
        }

        theFolk.heldItem = Armory.carriedWeapon(theFolk);
        melee(now, dist);
    }

    private void melee(long now, double dist)
    {
        EntityFolk me = theFolk.theEntity;
        if (dist > 2.3 || Math.abs(target.posY - me.posY) > 1.5)
        {
            approach(now);
            theFolk.statusText = "Going after " + nameOf(target);
            return;
        }
        theFolk.stayPut = true;
        me.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        theFolk.statusText = "Fighting " + nameOf(target);
        if (now - lastHit >= 1300 - (long) (theFolk.levelSoldier * 80))
        {
            lastHit = now;
            hit();
        }
    }

    /** walk at them, finding a new way only when they've moved */
    private void approach(long now)
    {
        theFolk.stayPut = false;
        double moved = Math.abs(target.posX - goalX) + Math.abs(target.posZ - goalZ);
        if (now - lastRepath > 1000 && (moved > 2 || theFolk.destination == null))
        {
            lastRepath = now;
            goalX = target.posX;
            goalZ = target.posZ;
            theFolk.gotoXYZ(new V3(target.posX, target.posY, target.posZ, target.dimension), GotoMethod.WALK);
        }
    }

    private FakePlayer hand()
    {
        EntityFolk me = theFolk.theEntity;
        FakePlayer fp = FakePlayerFactory.get((WorldServer) jobWorld, SOLDIER_PROFILE);
        double dx = target.posX - me.posX, dz = target.posZ - me.posZ;
        double dy = target.posY + target.getEyeHeight() * 0.7 - (me.posY + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        fp.setLocationAndAngles(me.posX, me.posY, me.posZ, yaw, pitch);
        fp.rotationYawHead = yaw;
        return fp;
    }

    /** a swing with what they're holding, through a fake player so it hits exactly like a player with it would */
    private void hit()
    {
        EntityFolk me = theFolk.theEntity;
        me.swingItem();
        if (target instanceof EntityPlayer)
        {
            target.attackEntityFrom(DamageSource.causeMobDamage(me), 3.0F); // the flat of the blade
            return;
        }
        ItemStack carried = Armory.carriedWeapon(theFolk);
        ItemStack weapon = carried != null ? carried : Armory.standardIssue();
        try
        {
            FakePlayer fp = hand();
            fp.inventory.mainInventory[fp.inventory.currentItem] = weapon;
            fp.getAttributeMap().applyAttributeModifiers(weapon.getAttributeModifiers());
            try
            {
                fp.attackTargetEntityWithCurrentItem(target);
            }
            finally
            {
                fp.getAttributeMap().removeAttributeModifiers(weapon.getAttributeModifiers());
                fp.inventory.mainInventory[fp.inventory.currentItem] = null;
            }
        }
        catch (Exception e)
        {
            // some modded weapon didn't like it: hit for what it's worth instead
            target.attackEntityFrom(DamageSource.causeMobDamage(me), Math.max(2, Armory.meleeScore(weapon)));
        }
        if (carried != null && carried.stackSize <= 0)
        {
            theFolk.statusText = "Their " + carried.getDisplayName() + " broke";
            Armory.dropBroken(theFolk);
            needEquip = true;
        }
        turnOnMe();
    }

    /** an arrow at the target: drawn by the soldier for a vanilla bow, by a fake player for a modded one */
    private void shoot(ItemStack bow)
    {
        EntityFolk me = theFolk.theEntity;
        me.swingItem();
        String name = String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(bow.getItem()));
        if (!Armory.isVanilla(bow) && !unusable.contains(name) && target instanceof EntityLivingBase)
        {
            if (shootModded(bow, name))
            {
                turnOnMe();
                return;
            }
        }
        if (!(target instanceof EntityLivingBase))
        {
            return;
        }
        EntityArrow arrow = new EntityArrow(jobWorld, me, (EntityLivingBase) target, 1.6F, Math.max(2F, 14F - theFolk.levelSoldier));
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, bow);
        int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, bow);
        arrow.setDamage(2.0D + (power > 0 ? power * 0.5D + 0.5D : 0) + theFolk.levelSoldier * 0.1D);
        if (punch > 0)
        {
            arrow.setKnockbackStrength(punch);
        }
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, bow) > 0)
        {
            arrow.setFire(100);
        }
        arrow.canBePickedUp = 0;
        jobWorld.playSoundAtEntity(me, "random.bow", 1.0F, 1.0F / (jobWorld.rand.nextFloat() * 0.4F + 0.8F));
        jobWorld.spawnEntityInWorld(arrow);
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, bow) == 0)
        {
            Armory.useArrow(theFolk);
        }
        bow.damageItem(1, me);
        if (bow.stackSize <= 0)
        {
            theFolk.statusText = "Their " + bow.getDisplayName() + " broke";
            Armory.dropBroken(theFolk);
            needEquip = true;
        }
    }

    /** a veteran with a modded bow or crossbow: drawn and loosed as a player would. False if it can't be used. */
    private boolean shootModded(ItemStack bow, String name)
    {
        FakePlayer fp = hand();
        int before = Armory.carriedArrows(theFolk);
        int slot = fp.inventory.currentItem, ammo = (slot + 1) % 9;
        try
        {
            fp.inventory.mainInventory[slot] = bow;
            fp.inventory.mainInventory[ammo] = new ItemStack(Items.arrow, Math.min(64, before));
            ItemStack after = bow.getItem().onItemRightClick(bow, jobWorld, fp);
            int duration = bow.getItem().getMaxItemUseDuration(bow);
            if (duration > 0)
            {
                bow.getItem().onPlayerStoppedUsing(after != null ? after : bow, jobWorld, fp, Math.max(0, duration - 20));
            }
            ItemStack left = fp.inventory.mainInventory[ammo];
            int used = before > 0 ? Math.min(64, before) - (left == null ? 0 : left.stackSize) : 0;
            for (int i = 0; i < used; i++)
            {
                Armory.useArrow(theFolk);
            }
            return true;
        }
        catch (Exception e)
        {
            unusable.add(name);
            SimukraftReloaded.log.warning("JobSoldier: " + name + " can't be used by a soldier (" + e + "), using it as a plain bow");
            return false;
        }
        finally
        {
            fp.inventory.mainInventory[slot] = null;
            fp.inventory.mainInventory[ammo] = null;
            if (bow.stackSize <= 0)
            {
                Armory.dropBroken(theFolk);
                needEquip = true;
            }
        }
    }

    /** a mob that's been hit (by the fake player) goes for the soldier, not off after someone who isn't there */
    private void turnOnMe()
    {
        EntityFolk me = theFolk.theEntity;
        if (target instanceof EntityLiving && !target.isDead)
        {
            ((EntityLiving) target).setAttackTarget(me);
            ((EntityLiving) target).setRevengeTarget(me);
        }
        if (target instanceof EntityCreature && !target.isDead)
        {
            ((EntityCreature) target).setTarget(me);
        }
    }

    private void killed()
    {
        String what = nameOf(target);
        if (target instanceof EntityBandit)
        {
            bounty += 2.0f;
            gainLevel(0.3f);
        }
        else
        {
            kills++;
            gainLevel(0.06f);
        }
        backToPatrol("Dealt with " + what);
    }

    private void gainLevel(float by)
    {
        Rank before = rank();
        theFolk.levelSoldier = Math.min(10.0f, theFolk.levelSoldier + by);
        Rank after = rank();
        if (after != before)
        {
            SimukraftReloaded.sendChat(theFolk.name + " has been promoted to " + after.title + "!");
            needEquip = true;
        }
    }

    private static String nameOf(Entity e)
    {
        if (e instanceof EntityPlayer)
        {
            return ((EntityPlayer) e).getCommandSenderName();
        }
        String n = e instanceof EntityBandit ? "a bandit" : e.getCommandSenderName();
        return n.startsWith("a ") ? n : "a " + n;
    }

    @Override
    public void onArrivedAtWork()
    {
        theFolk.updateLocationFromEntity();
        if (theFolk.location.getDistanceTo(theFolk.employedAt) <= 2)
        {
            theFolk.action = FolkAction.ATWORK;
            theFolk.statusText = "Reporting for duty";
            theStage = Stage.ONPATROL;
            waypoint = 0;
        }
        else
        {
            theFolk.gotoXYZ(theFolk.employedAt, null);
        }
    }
}
