package ashjack.simukraftreloaded.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.BlockBed;
import net.minecraft.init.Blocks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.common.jobs.Job.Vocation;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedConfig;
import ashjack.simukraftreloaded.core.town.Economy;
import ashjack.simukraftreloaded.core.town.Town;
import ashjack.simukraftreloaded.core.town.Towns;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.FolkData.FolkAction;
import ashjack.simukraftreloaded.folk.FolkData.GotoMethod;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/**
 * Looking after hurt Sims. Everyone heals slowly by themselves. A Sim who drops below the injured line stops work and
 * walks to the town's nearest clinic or hospital that has a medic and a free bed (or home, to rest, if there isn't one);
 * below the critical line they can't walk and wait where they are for a medic to come out to them (see JobMedic), who
 * patches them up enough to walk in. In a bed, medics treat them - faster at a hospital. Once they're well they rest for
 * a while before going back to work: a short while after a hospital, longer after a clinic, longest with no care at all.
 *
 * Health is kept on their FolkData, so it isn't lost when they're too far from a player to be in the world.
 */
public final class Care
{
    private Care() {}

    public enum Stage
    {
        /** well (or well enough) */
        NONE,
        /** hurt: on their way to a bed, or home to rest */
        HURT,
        /** too hurt to walk: waiting for a medic */
        DOWN,
        /** in a bed at a clinic or hospital */
        IN_CARE,
        /** well again, resting before going back to work */
        RECOVERING;
    }

    private static final long SELF_HEAL_MS = 20000, HOME_HEAL_MS = 6000, BED_HEAL_MS = 8000, DOWN_HEAL_MS = 15000;

    // ---------------------------------------------------------------- the buildings

    public static boolean isMedical(Building b)
    {
        return b != null && b.displayName != null && (isHospital(b) || b.displayName.toLowerCase().contains("clinic"));
    }

    public static boolean isHospital(Building b)
    {
        return b != null && b.displayName != null && b.displayName.toLowerCase().contains("hospital");
    }

    /** how many medics work there */
    public static int medicCapacity(Building b)
    {
        return isHospital(b) ? 6 : 2;
    }

    /** how many patients it takes: a clinic 4 and a hospital 12, or more if it has more beds than that */
    public static int beds(Building b)
    {
        int standard = isHospital(b) ? 12 : 4;
        return Math.max(standard, SimukraftReloaded.isClientThread() ? 0 : bedsIn(b).size());
    }

    private static final Map<String, List<V3>> bedCache = new HashMap<String, List<V3>>();
    private static final Map<String, Long> bedScanned = new HashMap<String, Long>();

    /** the beds in and around the building (their head halves), looked for once a minute */
    public static List<V3> bedsIn(Building b)
    {
        String key = b.primaryXYZ.toString();
        Long when = bedScanned.get(key);
        if (when != null && System.currentTimeMillis() - when < 60000)
        {
            return bedCache.get(key);
        }
        List<V3> beds = new ArrayList<V3>();
        World world = MinecraftServer.getServer().worldServerForDimension(b.primaryXYZ.theDimension);
        int cx = b.primaryXYZ.x.intValue(), cy = b.primaryXYZ.y.intValue(), cz = b.primaryXYZ.z.intValue();
        if (world != null && world.blockExists(cx, cy, cz))
        {
            for (int y = cy - 4; y <= cy + 20; y++)
            {
                for (int x = cx - 14; x <= cx + 14; x++)
                {
                    for (int z = cz - 14; z <= cz + 14; z++)
                    {
                        if (world.getBlock(x, y, z) == Blocks.bed && BlockBed.isBlockHeadOfBed(world.getBlockMetadata(x, y, z)))
                        {
                            beds.add(new V3((double) x, (double) y, (double) z, b.primaryXYZ.theDimension));
                        }
                    }
                }
            }
        }
        bedCache.put(key, beds);
        bedScanned.put(key, System.currentTimeMillis());
        return beds;
    }

    public static List<FolkData> medicsAt(Building b)
    {
        List<FolkData> out = new ArrayList<FolkData>();
        for (FolkData f : FolkData.getFolksByEmployedAt(b.primaryXYZ))
        {
            if (f.vocation == Vocation.MEDIC)
            {
                out.add(f);
            }
        }
        return out;
    }

    public static List<FolkData> patientsAt(Building b)
    {
        List<FolkData> out = new ArrayList<FolkData>();
        String at = b.primaryXYZ.toString();
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.folks()))
        {
            if (at.equals(f.careAt) && (f.care == Stage.HURT || f.care == Stage.IN_CARE))
            {
                out.add(f);
            }
        }
        return out;
    }

    /** the nearest finished clinic or hospital in their town with a medic and a free bed, or null */
    public static Building findCare(FolkData folk)
    {
        folk.updateLocationFromEntity();
        Town town = Towns.of(folk);
        Building best = null;
        double bestDist = Double.MAX_VALUE;
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (!isMedical(b) || !b.buildingComplete || b.primaryXYZ == null || b.primaryXYZ.theDimension != folk.location.theDimension
                    || (town != null && !town.contains(b.primaryXYZ)) || medicsAt(b).isEmpty())
            {
                continue;
            }
            List<FolkData> patients = patientsAt(b);
            if (patients.size() >= beds(b) && !patients.contains(folk))
            {
                continue;
            }
            double d = folk.location.getDistanceTo(b.primaryXYZ);
            if (town == null && d > 160)
            {
                continue;
            }
            if (d < bestDist)
            {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    /** where a patient lies: a bed of their own, if the building has enough, or by the control box */
    /**
     * where a medic works: inside, beside the first bed (a hospital's control box can be out on a corner of its
     * foundation), or the middle of the building if it has none; null if it can't be worked out
     */
    public static V3 workSpot(Building b, net.minecraft.world.World w)
    {
        if (b == null || w == null)
        {
            return null;
        }
        List<V3> beds = bedsIn(b);
        if (!beds.isEmpty())
        {
            V3 bed = beds.get(0);
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
            for (int[] d : around)
            {
                int x = bed.x.intValue() + d[0], z = bed.z.intValue() + d[1];
                int y = WorldUtil.findStandableY(w, x, bed.y.intValue(), z, 2);
                if (y >= 0)
                {
                    return new V3((double) x, (double) y, (double) z, bed.theDimension);
                }
            }
        }
        V3 con = ashjack.simukraftreloaded.common.warehouse.WarehouseStock.conBoxOf(b);
        int[] f = con == null ? null : ashjack.simukraftreloaded.common.warehouse.WarehouseStock.footprint(b, b.layoutBox(con));
        if (f == null)
        {
            return null;
        }
        int x = (f[0] + f[3]) / 2, z = (f[2] + f[5]) / 2;
        int y = WorldUtil.findStandableY(w, x, f[1] + 1, z, 3);
        return y < 0 ? null : new V3((double) x, (double) y, (double) z, b.primaryXYZ.theDimension);
    }

    public static V3 bedFor(FolkData folk, Building b)
    {
        List<V3> beds = bedsIn(b);
        if (!beds.isEmpty())
        {
            int i = Math.max(0, patientsAt(b).indexOf(folk));
            return beds.get(i % beds.size()).clone();
        }
        return b.livingXYZ != null ? b.livingXYZ.clone() : b.primaryXYZ.clone();
    }

    // ---------------------------------------------------------------- health

    public static float maxHealth(FolkData folk)
    {
        return folk.isSpawned() ? folk.theEntity.getMaxHealth() : ashjack.simukraftreloaded.common.life.Transport.maxHealth(folk);
    }

    public static float health(FolkData folk)
    {
        if (folk.isSpawned())
        {
            folk.health = folk.theEntity.getHealth();
        }
        return folk.health < 0 ? maxHealth(folk) : folk.health;
    }

    public static void heal(FolkData folk, float amount)
    {
        float max = maxHealth(folk);
        float h = Math.min(max, health(folk) + amount);
        folk.health = h;
        if (folk.isSpawned())
        {
            folk.theEntity.setHealth(h);
            if (amount >= 1)
            {
                Effects.particles(folk.theEntity.worldObj, "heart", folk.theEntity.posX, folk.theEntity.posY + 2.1,
                        folk.theEntity.posZ, 1, 0.3, 0.02);
            }
        }
    }

    public static float fraction(FolkData folk)
    {
        return health(folk) / maxHealth(folk);
    }

    private static float injuredLine()
    {
        return SimukraftReloadedConfig.configInjuredPercent / 100f;
    }

    private static float criticalLine()
    {
        return SimukraftReloadedConfig.configCriticalPercent / 100f;
    }

    private static long worldTime()
    {
        World w = MinecraftServer.getServer().worldServerForDimension(0);
        return w == null ? 0 : w.getTotalWorldTime();
    }

    // ---------------------------------------------------------------- once a second, for every Sim

    /**
     * Called once a second from FolkData. Returns true while the Sim is being looked after: their usual day (work,
     * going home, wandering) waits until they're better.
     */
    public static boolean tick(FolkData folk)
    {
        long now = System.currentTimeMillis();
        float frac = fraction(folk);

        if (folk.care == null)
        {
            folk.care = Stage.NONE;
        }
        switch (folk.care)
        {
            case NONE:
                if (now - folk.lastHealed > SELF_HEAL_MS && frac < 1)
                {
                    heal(folk, 1);
                    folk.lastHealed = now;
                }
                if (folk.isVisitor || folk.leavingTown || folk.pregnancyStage > 0)
                {
                    return false;
                }
                if (frac <= criticalLine())
                {
                    goDown(folk);
                    return true;
                }
                if (frac <= injuredLine())
                {
                    getHelp(folk, " is hurt");
                    return true;
                }
                return false;

            case DOWN:
                downTick(folk, now, frac);
                return true;

            case HURT:
                hurtTick(folk, now, frac);
                return true;

            case IN_CARE:
                inCareTick(folk, now, frac);
                return true;

            case RECOVERING:
                recoveringTick(folk, now, frac);
                return true;
        }
        return false;
    }

    private static void stopWork(FolkData folk)
    {
        folk.isWorking = false;
        folk.action = FolkAction.INCARE;
        folk.destination = null;
        folk.heldItem = null;
    }

    private static void goDown(FolkData folk)
    {
        stopWork(folk);
        folk.care = Stage.DOWN;
        folk.careAt = null;
        folk.stayPut = true;
        folk.statusText = "Badly hurt - waiting for a medic";
        SimukraftReloaded.sendChat(folk.name + " is badly hurt and can't walk!" + (anyMedic(folk) ? " A medic is on the way."
                : " There's no medic in town - build a clinic and hire one."));
    }

    /** off to the nearest bed, or home to rest */
    private static void getHelp(FolkData folk, String why)
    {
        stopWork(folk);
        folk.care = Stage.HURT;
        folk.stayPut = false;
        Building b = findCare(folk);
        folk.careAt = b == null ? null : b.primaryXYZ.toString();
        if (b != null)
        {
            folk.statusText = "Hurt - going to the " + b.displayNameWithoutPK;
            SimukraftReloaded.sendChat(folk.name + why + " and is going to the " + b.displayNameWithoutPK);
        }
        else
        {
            folk.statusText = "Hurt - resting at home";
            SimukraftReloaded.sendChat(folk.name + why + " and is going home to rest (no clinic with a medic and a free bed)");
        }
    }

    private static boolean anyMedic(FolkData folk)
    {
        Town town = Towns.of(folk);
        for (FolkData f : new ArrayList<FolkData>(SimukraftReloaded.folks()))
        {
            if (f.vocation == Vocation.MEDIC && f.employedAt != null && (town == null || town.contains(f.employedAt)))
            {
                return true;
            }
        }
        return false;
    }

    private static void downTick(FolkData folk, long now, float frac)
    {
        folk.stayPut = true;
        folk.destination = null;
        // with nobody coming, they slowly come round by themselves
        if (!anyMedic(folk) && now - folk.lastHealed > DOWN_HEAL_MS)
        {
            heal(folk, 1);
            folk.lastHealed = now;
        }
        if (fraction(folk) > criticalLine())
        {
            getHelp(folk, " has come round");
            return;
        }
        if (folk.statusText == null || !folk.statusText.startsWith("Badly hurt"))
        {
            folk.statusText = "Badly hurt - waiting for a medic";
        }
    }

    /** a medic has patched them up where they lay: now they can walk to a bed */
    public static void stabilised(FolkData folk, FolkData medic)
    {
        float max = maxHealth(folk);
        float target = max * Math.min(1f, injuredLine() + 0.05f);
        if (health(folk) < target)
        {
            heal(folk, target - health(folk));
        }
        folk.stayPut = false;
        folk.care = Stage.HURT;
        Building b = medic.employedAt == null ? null : Building.getBuilding(medic.employedAt);
        if (b != null && patientsAt(b).size() >= beds(b))
        {
            b = findCare(folk);
        }
        folk.careAt = b == null ? null : b.primaryXYZ.toString();
        folk.statusText = b == null ? "Patched up - resting at home" : "Patched up - going to the " + b.displayNameWithoutPK;
    }

    private static void hurtTick(FolkData folk, long now, float frac)
    {
        if (frac <= criticalLine())
        {
            goDown(folk);
            return;
        }
        folk.action = FolkAction.INCARE;
        Building b = folk.careAt == null ? null : buildingAt(folk.careAt);
        if (b == null || medicsAt(b).isEmpty())
        {
            // look again now and then: a clinic may have opened, or a bed come free
            if (now - folk.careLooked > 30000)
            {
                folk.careLooked = now;
                Building found = findCare(folk);
                if (found != null)
                {
                    folk.careAt = found.primaryXYZ.toString();
                    folk.statusText = "Hurt - going to the " + found.displayNameWithoutPK;
                    return;
                }
            }
            folk.careAt = null;
            restAtHome(folk, now);
            if (frac >= 1)
            {
                startRecovering(folk, SimukraftReloadedConfig.configRecoveryMinutes * 2, "home");
            }
            return;
        }
        V3 bed = bedFor(folk, b);
        folk.updateLocationFromEntity();
        if (folk.location.getDistanceTo(bed) <= 3 || (!folk.isSpawned() && folk.location.getDistanceTo(b.primaryXYZ) <= 8))
        {
            folk.care = Stage.IN_CARE;
            folk.stayPut = true;
            folk.destination = null;
            if (folk.isSpawned() && folk.theEntity.worldObj.getBlock(bed.x.intValue(), bed.y.intValue(), bed.z.intValue()) == Blocks.bed)
            {
                folk.theEntity.setPositionAndUpdate(bed.x + 0.5, bed.y + 0.6, bed.z + 0.5); // into bed
            }
            folk.statusText = "In a bed at the " + b.displayNameWithoutPK;
            // the visit is paid for: by them if they can, or the town
            Economy.purchase(folk, b.primaryXYZ, isHospital(b) ? 6 : 3);
            return;
        }
        if (folk.destination == null)
        {
            folk.gotoXYZ(bed, GotoMethod.WALK);
        }
    }

    private static void restAtHome(FolkData folk, long now)
    {
        Building home = folk.getHome();
        V3 at = home == null ? null : (home.livingXYZ != null ? home.livingXYZ : home.primaryXYZ);
        if (at != null)
        {
            folk.updateLocationFromEntity();
            if (folk.location.getDistanceTo(at) > 2)
            {
                folk.stayPut = false;
                if (folk.destination == null)
                {
                    folk.gotoXYZ(at.clone(), GotoMethod.WALK);
                }
                folk.statusText = "Hurt - going home to rest";
                return;
            }
            folk.stayPut = true;
        }
        folk.statusText = "Hurt - resting" + (at != null ? " at home" : "");
        if (now - folk.lastHealed > HOME_HEAL_MS)
        {
            heal(folk, 1);
            folk.lastHealed = now;
        }
    }

    private static void inCareTick(FolkData folk, long now, float frac)
    {
        Building b = folk.careAt == null ? null : buildingAt(folk.careAt);
        if (b == null)
        {
            folk.care = Stage.HURT;
            folk.careAt = null;
            return;
        }
        folk.stayPut = true;
        folk.action = FolkAction.INCARE;
        // they mend in bed even between a medic's rounds
        if (now - folk.lastHealed > BED_HEAL_MS)
        {
            heal(folk, 1);
            folk.lastHealed = now;
        }
        if (fraction(folk) >= 1)
        {
            startRecovering(folk, isHospital(b) ? SimukraftReloadedConfig.configRecoveryMinutes / 2f
                    : SimukraftReloadedConfig.configRecoveryMinutes, b.displayNameWithoutPK);
            return;
        }
        folk.statusText = "In a bed at the " + b.displayNameWithoutPK + " (" + Math.round(fraction(folk) * 100) + "%)";
    }

    private static void startRecovering(FolkData folk, float minutes, String where)
    {
        folk.care = Stage.RECOVERING;
        folk.careAt = null;
        folk.stayPut = false;
        folk.careUntil = worldTime() + (long) (minutes * 1200);
        if (minutes > 0)
        {
            SimukraftReloaded.sendChat(folk.name + " is better and will rest for " + Math.round(minutes * 10) / 10f
                    + " minutes before going back to work");
        }
    }

    private static void recoveringTick(FolkData folk, long now, float frac)
    {
        long left = folk.careUntil - worldTime();
        if (frac <= injuredLine())
        {
            getHelp(folk, " has been hurt again");
            return;
        }
        if (left <= 0)
        {
            folk.care = Stage.NONE;
            folk.careAt = null;
            folk.stayPut = false;
            folk.action = FolkAction.WANDER; // their day picks up again: work, if it's a working day
            folk.statusText = folk.employedAt != null ? "Back to work" : "Wandering";
            return;
        }
        restAtHome(folk, now);
        folk.statusText = "Recovering - " + (folk.employedAt != null ? "back to work" : "up and about") + " in "
                + Math.max(1, (left + 1199) / 1200) + " min";
    }

    /** a Sim died or left: forget their care */
    public static void forget(FolkData folk)
    {
        folk.care = Stage.NONE;
        folk.careAt = null;
    }

    private static Building buildingAt(String v3)
    {
        for (Building b : new ArrayList<Building>(SimukraftReloaded.serverBuildings))
        {
            if (b.primaryXYZ != null && v3.equals(b.primaryXYZ.toString()))
            {
                return b;
            }
        }
        return null;
    }

    /** whether they're injured enough that their work should wait */
    public static boolean underCare(FolkData folk)
    {
        return folk.care != null && folk.care != Stage.NONE;
    }
}

