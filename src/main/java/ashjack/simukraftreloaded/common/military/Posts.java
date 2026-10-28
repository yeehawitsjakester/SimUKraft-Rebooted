package ashjack.simukraftreloaded.common.military;

import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.building.TowerPlan;

/**
 * Where a soldier is posted decides what they do (see JobSoldier): a barracks' soldiers patrol the town's buildings; a
 * gatehouse's are border guards, walking the town wall; a watch tower's lookout keeps watch from its platform; and an
 * archery range's are in training, practising until they're good enough to be archers. The gatehouse also takes on the
 * wall builders (see TownWall).
 */
public enum Posts
{
    BARRACKS, GATEHOUSE, WATCHTOWER, ARCHERY;

    public static Posts of(Building b)
    {
        String n = b == null || b.displayName == null ? "" : b.displayName.toLowerCase();
        if (n.contains("gatehouse"))
        {
            return GATEHOUSE;
        }
        if (TowerPlan.isGenerated(b == null ? null : b.displayName) || n.contains("watch tower") || n.contains("watchtower"))
        {
            return WATCHTOWER;
        }
        if (n.contains("archery"))
        {
            return ARCHERY;
        }
        return BARRACKS;
    }

    /** soldiers can be posted here (a barracks, or one of these) */
    public static boolean takesSoldiers(Building b)
    {
        return b != null && (of(b) != BARRACKS || b.displayName.contains("Barracks"));
    }

    /** how many soldiers it has room for */
    public static int capacity(Building b)
    {
        switch (of(b))
        {
            case WATCHTOWER:
                return 2;
            case GATEHOUSE:
                return 8;
            case ARCHERY:
                return 6;
            default:
                return b == null ? 10 : b.soldierCapacity();
        }
    }

    /** an archery range trains them up to here: past it, it takes real fighting (Archer rank starts at 4) */
    public static final float TRAINED_TO = 6.5f;
}
