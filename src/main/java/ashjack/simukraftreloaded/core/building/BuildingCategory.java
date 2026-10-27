package ashjack.simukraftreloaded.core.building;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * What a blueprint is for, to list it under, without moving any files: the folders (residential, commercial...)
 * decide how a building works, so they stay. A CAT= entry in a blueprint's key decides; otherwise homes, shops and
 * industry go by their folder, and everything in "other" (roads, railways, churches, mob traps, statues all in one
 * heap) goes by words in its name.
 */
public final class BuildingCategory
{
    private BuildingCategory() {}

    public static final String HOUSING = "Housing", TEMPORARY = "Temporary housing", SHOPS = "Shops & services",
            INDUSTRY = "Industry & farms", CIVIC = "Civic", INFRASTRUCTURE = "City infrastructure",
            LANDMARKS = "Towers & landmarks", UTILITY = "Utility", DECORATIVE_HOMES = "Decorative homes",
            DECORATION = "Decoration", SPECIAL = "Special", MISC = "Miscellaneous";

    /** in the order they're listed */
    public static final List<String> ALL = Arrays.asList(HOUSING, TEMPORARY, SHOPS, INDUSTRY, CIVIC, INFRASTRUCTURE,
            LANDMARKS, UTILITY, DECORATIVE_HOMES, DECORATION, SPECIAL, MISC);

    /** for "other" blueprints: the first category with a word in the name wins */
    private static final String[][] WORDS = {
        {INFRASTRUCTURE, "rail", "road", "street", "highway", "bridge", "station", "ramp", "train", "lane", "tunnel",
            "crossing", "path", "depot"},
        {TEMPORARY, "motel", "hotel", "inn", "hostel", "trailer", "igloo", "camp"},
        {CIVIC, "town hall", "school", "library", "church", "cathedral", "kirche", "chapel", "fire station", "police",
            "hospital", "clinic", "museum", "court"},
        {INDUSTRY, "farm", "barn", "silo", "stable", "grain", "greenhouse", "orchard", "pasture", "coop", "mill",
            "factory", "dock"},
        {SHOPS, "shop", "store", "theatre", "theater", "cinema", "movie", "bowling", "bank", "pub", "cafe",
            "restaurant", "market", "office", "rink"},
        {UTILITY, "trap", "mob ", "destroyer", "minefield", "lava well", "containment", "exploitation", "generator"},
        {LANDMARKS, "tower", "skyscraper", "lighthouse", "palace", "castle", "lair", "pyramid"},
        {DECORATIVE_HOMES, "house", "home", "cabin", "hut", "studio", "apartment", "apts", "cottage", "villa",
            "mansion", "bungalow"},
        {DECORATION, "well", "obelisk", "torch", "logo", "statue", "fountain", "garden", "beach", "graveyard", "park",
            "sign", "pond", "tree", "sky love"},
    };

    public static String of(Building b)
    {
        if (b.category != null && !b.category.trim().isEmpty())
        {
            return b.category.trim();
        }
        String type = b.type == null ? "" : b.type;
        if (type.equals("residential"))
        {
            return HOUSING;
        }
        if (type.equals("commercial"))
        {
            return SHOPS;
        }
        if (type.equals("industrial"))
        {
            return INDUSTRY;
        }
        if (type.equals("special"))
        {
            return SPECIAL;
        }
        String name = " " + (b.displayNameWithoutPK == null ? b.displayName : b.displayNameWithoutPK).toLowerCase() + " ";
        for (String[] rule : WORDS)
        {
            for (int i = 1; i < rule.length; i++)
            {
                if (name.contains(rule[i]))
                {
                    return rule[0];
                }
            }
        }
        return MISC;
    }

    /** the blueprints in one category */
    public static ArrayList<Building> in(List<Building> all, String category)
    {
        ArrayList<Building> out = new ArrayList<Building>();
        for (Building b : all)
        {
            if (of(b).equalsIgnoreCase(category))
            {
                out.add(b);
            }
        }
        return out;
    }

    /**
     * Whether a blueprint matches what was typed in the search box. Every word has to match (so "brick house" finds
     * brick houses), each one against the name, author, description and category, unless it says what it's about:
     * by:satscape (author), uses:glass (one of the blocks it's made of), w:9 d:12 h:5 (width, depth, height).
     */
    public static boolean matches(Building b, String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return true;
        }
        for (String term : query.toLowerCase().trim().split("\\s+"))
        {
            if (!matchesTerm(b, term))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesTerm(Building b, String term)
    {
        int colon = term.indexOf(':');
        String what = colon > 0 ? term.substring(0, colon) : "";
        String value = colon > 0 ? term.substring(colon + 1) : term;
        if (value.isEmpty())
        {
            return true; // still typing
        }
        if (what.equals("w") || what.equals("d") || what.equals("h"))
        {
            try
            {
                int n = Integer.parseInt(value);
                return n == (what.equals("w") ? b.ltrCount : what.equals("d") ? b.ftbCount : b.layerCount);
            }
            catch (NumberFormatException e)
            {
                return false;
            }
        }
        if (what.equals("by"))
        {
            return b.author != null && b.author.toLowerCase().contains(value);
        }
        if (what.equals("uses"))
        {
            if (b.requirements != null)
            {
                for (Map.Entry<ItemStack, Integer> e : b.requirements.entrySet())
                {
                    try
                    {
                        if (e.getKey() != null && e.getKey().getDisplayName().toLowerCase().contains(value))
                        {
                            return true;
                        }
                    }
                    catch (Exception ignored)
                    {
                        // some modded items can't name themselves out of context
                    }
                }
            }
            return false;
        }
        String name = b.displayNameWithoutPK == null ? b.displayName : b.displayNameWithoutPK;
        return contains(name, value) || contains(b.author, value) || contains(b.description, value)
                || contains(of(b), value);
    }

    private static boolean contains(String s, String value)
    {
        return s != null && s.toLowerCase().contains(value);
    }
}
