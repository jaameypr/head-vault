package it.pruefert.headvault.economy;

/**
 * Vanilla Minecraft experience math, used to convert between levels and total points (e.g. to
 * validate an {@code XP_LEVELS} purchase against a player's total points, or to display costs).
 * Pure functions — unit-tested against known wiki values.
 *
 * <p>Cumulative total points required to reach a given level:
 * <ul>
 *   <li>0–16: {@code level² + 6·level}</li>
 *   <li>17–31: {@code 2.5·level² − 40.5·level + 360}</li>
 *   <li>32+: {@code 4.5·level² − 162.5·level + 2220}</li>
 * </ul>
 */
public final class XpMath {

    private XpMath() {
    }

    /** Total experience points required to reach {@code level} (from level 0). */
    public static int totalPointsForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        double points;
        if (level <= 16) {
            points = level * level + 6.0 * level;
        } else if (level <= 31) {
            points = 2.5 * level * level - 40.5 * level + 360.0;
        } else {
            points = 4.5 * level * level - 162.5 * level + 2220.0;
        }
        return (int) Math.round(points);
    }

    /** Points needed to advance from {@code level} to {@code level + 1}. */
    public static int pointsToNextLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    /** The (whole) level a player with {@code totalPoints} total experience has reached. */
    public static int levelForTotalPoints(int totalPoints) {
        if (totalPoints <= 0) {
            return 0;
        }
        int level = 0;
        int remaining = totalPoints;
        while (remaining >= pointsToNextLevel(level)) {
            remaining -= pointsToNextLevel(level);
            level++;
        }
        return level;
    }
}
