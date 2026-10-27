package ashjack.simukraftreloaded.client.Gui.folk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import ashjack.simukraftreloaded.client.RenderFolk;
import ashjack.simukraftreloaded.common.Relationship;
import ashjack.simukraftreloaded.entity.EntityFolk;
import ashjack.simukraftreloaded.folk.FolkData;
import ashjack.simukraftreloaded.folk.traits.Trait;

/**
 * A Sim's screen, laid out like the player inventory: their avatar, who they are, a meter per need and skill,
 * and their traits in slots. Hovering a meter or slot explains why it's at that value.
 */
public class GuiEntityFolk extends GuiScreen
{
    private static final int PANEL_W = 256, PANEL_H = 202;
    private static final int COL_PANEL = 0xFFC6C6C6, COL_LIGHT = 0xFFFFFFFF, COL_DARK = 0xFF555555,
                             COL_SLOT = 0xFF8B8B8B, COL_SLOT_DARK = 0xFF373737, COL_TEXT = 0x404040;

    /** an item to stand in as each trait's icon */
    private static final HashMap<String, ItemStack> TRAIT_ICONS = new HashMap<String, ItemStack>();
    static
    {
        TRAIT_ICONS.put("Workaholic", new ItemStack(Items.iron_pickaxe));
        TRAIT_ICONS.put("Brave", new ItemStack(Items.iron_sword));
        TRAIT_ICONS.put("Dwarven Heritage", new ItemStack(Blocks.iron_ore));
        TRAIT_ICONS.put("Friendly", new ItemStack(Items.cake));
        TRAIT_ICONS.put("Green Thumb", new ItemStack(Blocks.sapling));
        TRAIT_ICONS.put("Hates the Outdoors", new ItemStack(Items.bed));
        TRAIT_ICONS.put("Night Owl", new ItemStack(Items.clock));
        TRAIT_ICONS.put("Strong", new ItemStack(Blocks.anvil));
        TRAIT_ICONS.put("Loves the Outdoors", new ItemStack(Blocks.red_flower));
        TRAIT_ICONS.put("Religious", new ItemStack(Items.book));
    }

    private int currentPage = 0;
    private int mouseCount = 0;
    private FolkData theFolk;
    private EntityFolk theEntity;
    private EntityPlayer entityplayer;
    private ArrayList<Relationship> folksRelationships;
    private int relOffset = 0;
    private int gx, gy;

    /** areas you can hover this frame, and what their tooltip says */
    private final ArrayList<int[]> hoverBoxes = new ArrayList<int[]>();
    private final ArrayList<List<String>> hoverText = new ArrayList<List<String>>();

    public GuiEntityFolk(FolkData f, EntityPlayer entityplayer)
    {
        this(f, null, entityplayer);
    }

    /** entity may be null (opened from a list rather than by clicking them): it's looked up if they're nearby */
    public GuiEntityFolk(FolkData f, EntityFolk entity, EntityPlayer entityplayer)
    {
        theFolk = f;
        theEntity = entity;
        this.entityplayer = entityplayer;
        folksRelationships = Relationship.getRelationshipsFor(theFolk);
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        gx = (width - PANEL_W) / 2;
        gy = Math.max(2, (height - PANEL_H) / 2);
        if (theEntity == null && mc.theWorld != null)
        {
            for (Object o : mc.theWorld.loadedEntityList)
            {
                if (o instanceof EntityFolk && ((EntityFolk) o).theData != null
                        && theFolk.name.equals(((EntityFolk) o).theData.name))
                {
                    theEntity = (EntityFolk) o;
                    break;
                }
            }
        }
        showPage();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial)
    {
        if (mouseCount < 10)
        {
            mouseCount++;
            Mouse.setGrabbed(false);
        }
        hoverBoxes.clear();
        hoverText.clear();

        drawDefaultBackground();
        drawBevel(gx, gy, PANEL_W, PANEL_H, COL_PANEL, COL_LIGHT, COL_DARK);

        if (currentPage == 0)
        {
            drawOverview(mouseX, mouseY);
        }
        else
        {
            drawRelationships();
        }

        super.drawScreen(mouseX, mouseY, partial);

        for (int i = 0; i < hoverBoxes.size(); i++)
        {
            int[] b = hoverBoxes.get(i);
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3])
            {
                func_146283_a(hoverText.get(i), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawOverview(int mouseX, int mouseY)
    {
        FolkData f = theFolk;
        boolean adult = f.age >= 18;
        String job = !adult ? "Child" : f.employedAt == null || f.vocation == null ? "Unemployed" : niceName(f.vocation.toString());
        fontRendererObj.drawString(f.name + " - " + job, gx + 8, gy + 6, COL_TEXT);

        // avatar window, like the player inventory's
        int ax = gx + 8, ay = gy + 18;
        drawRect(ax - 1, ay - 1, ax + 55, ay + 77, COL_SLOT_DARK);
        drawRect(ax, ay, ax + 56, ay + 78, COL_LIGHT);
        drawRect(ax, ay, ax + 55, ay + 77, 0xFF000000);
        if (theEntity != null)
        {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            RenderFolk.drawingAvatar = true;
            try
            {
                GuiInventory.func_147046_a(ax + 28, ay + 70, f.age >= 18 ? 30 : 40, ax + 28 - mouseX, ay + 20 - mouseY, theEntity);
            }
            finally
            {
                RenderFolk.drawingAvatar = false;
            }
        }
        else
        {
            drawCenteredString(fontRendererObj, "(far away)", ax + 28, ay + 34, 0xAAAAAA);
        }

        // who they are
        int ix = gx + 70, iy = gy + 20;
        String ageText = f.age > 1 ? f.age + " years old" : f.age == 1 ? "1 year old" : "Under 1 year";
        infoRow(ix, iy, "Age", ageText + ", " + (f.gender == 0 ? "male" : "female"));
        String home = !adult ? "Lives with parents" : f.getHome() == null ? "Homeless" : f.getHome().displayName;
        infoRow(ix, iy + 11, "Home", home);
        String partner = "Single";
        if (Relationship.isFolkLivingWithSomeone(f))
        {
            FolkData who = Relationship.isFolkLivingWithSomeone(f, true);
            partner = "Living with " + (who == null ? "you" : who.name);
        }
        infoRow(ix, iy + 22, "Partner", partner);
        infoRow(ix, iy + 33, "Doing", f.statusText);
        if (f.pregnancyStage > 0.0f)
        {
            int days = (int) (f.pregnancyStage * 9);
            infoRow(ix, iy + 44, "Medical", days == 0 ? "Pregnant" : days == 1 ? "1 day pregnant" : days + " days pregnant");
        }
        else
        {
            infoRow(ix, iy + 44, "Money", ashjack.simukraftreloaded.core.registry.SimukraftReloaded.displayMoney(f.money) + " Sim-u-credits");
        }
        drawHappinessMeter(ix, gy + 82, gx + PANEL_W - 8 - ix);

        // needs on the left, skills on the right
        int my = gy + 104;
        fontRendererObj.drawString("Needs", gx + 8, my, COL_TEXT);
        fontRendererObj.drawString("Skills", gx + 134, my, COL_TEXT);
        my += 11;
        needMeter(gx + 8, my, "Hunger", f.levelFood, 0, 10, f.status4, hungerWhy());
        needMeter(gx + 8, my + 13, "Fun", f.levelFun, 0, 10, f.funStatus, funWhy());
        needMeter(gx + 8, my + 26, "Social", f.levelSocial, 0, 10, f.socialStatus, socialWhy());
        needMeter(gx + 8, my + 39, "Surround", f.levelEnvironment, 1, 10, f.environmentStatus, environmentWhy());
        skillMeter(gx + 134, my, "Building", f.levelBuilder, "Goes up as they build. Higher builds faster.");
        skillMeter(gx + 134, my + 13, "Mining", f.levelMiner, "Goes up as they mine. Higher mines faster.");
        skillMeter(gx + 134, my + 26, "Soldier", Math.max(1.0f, f.levelSoldier), "Goes up with kills. Higher waits less between kills.");

        // traits in slots, like the hotbar
        int ty = gy + PANEL_H - 26;
        fontRendererObj.drawString("Traits", gx + 8, ty + 5, COL_TEXT);
        String[] traits = {f.trait1, f.trait2, f.trait3, f.trait4};
        for (int t = 0; t < 4; t++)
        {
            int sx = gx + 42 + t * 20;
            drawSlot(sx, ty);
            String name = traits[t];
            if (name == null || name.isEmpty() || name.equals("None"))
            {
                continue;
            }
            ItemStack icon = TRAIT_ICONS.containsKey(name) ? TRAIT_ICONS.get(name) : new ItemStack(Items.paper);
            drawItem(icon, sx + 1, ty + 1);
            hover(sx, ty, sx + 18, ty + 18, traitWhy(name));
        }
    }

    private void drawHappinessMeter(int x, int y, int w)
    {
        int h = theFolk.happiness;
        fontRendererObj.drawString("Happiness", x, y + 2, COL_TEXT);
        int bx = x + 54, bw = w - 54;
        drawBar(bx, y, bw, 12, h / 100f, 0, 100, h + " / 100");

        List<String> lines = new ArrayList<String>();
        lines.add("§eHappiness: " + h + " / 100 (" + theFolk.happinessStatus + ")");
        int base = (int) Math.round((theFolk.levelFood + theFolk.levelFun + theFolk.levelSocial + theFolk.levelEnvironment) * 2.5);
        lines.add("§7Needs give " + base + " (hunger, fun, social, surroundings)");
        for (String r : theFolk.happinessReasons)
        {
            lines.add((r.startsWith("+") ? "§a" : "§c") + r);
        }
        if (theFolk.happinessReasons.isEmpty())
        {
            lines.add("§7Nothing else is bothering or cheering them");
        }
        hover(x, y, x + w, y + 12, lines);
    }

    private void needMeter(int x, int y, String label, int value, int min, int max, String status, List<String> why)
    {
        fontRendererObj.drawString(label, x, y + 1, COL_TEXT);
        drawBar(x + 48, y, 66, 10, (value - min) / (float) (max - min), min, max, value + "/" + max);
        List<String> lines = new ArrayList<String>();
        lines.add("§e" + label + ": " + value + " / " + max + (status == null || status.isEmpty() ? "" : " (" + status + ")"));
        lines.add("§7Range " + min + " to " + max);
        lines.addAll(why);
        hover(x, y, x + 114, y + 10, lines);
    }

    private void skillMeter(int x, int y, String label, float level, String how)
    {
        fontRendererObj.drawString(label, x, y + 1, COL_TEXT);
        drawBar(x + 48, y, 66, 10, Math.min(1f, (level - 1f) / 9f), 1, 10, String.format("%.1f/10", level));
        hover(x, y, x + 114, y + 10, Arrays.asList("§e" + label + " skill: " + String.format("%.1f", level) + " / 10",
                "§7Range 1 to 10", how));
    }

    private List<String> hungerWhy()
    {
        List<String> l = new ArrayList<String>();
        if (!theFolk.lastMeal.isEmpty())
        {
            l.add("Last meal: " + theFolk.lastMeal);
        }
        l.add("§7Gets a little hungrier as the days go by.");
        l.add("§7They eat at a grocer, bakery, butcher, fisherman");
        l.add("§7or burger bar, or pick up food thrown to them.");
        return l;
    }

    private List<String> funWhy()
    {
        List<String> l = new ArrayList<String>();
        if (!theFolk.funNote.isEmpty())
        {
            l.add(theFolk.funNote);
        }
        l.add("§7Work and idle wandering wear fun down.");
        l.add("§7Shopping, visiting and hanging out bring it back.");
        return l;
    }

    private List<String> socialWhy()
    {
        List<String> l = new ArrayList<String>();
        if (!theFolk.socialNote.isEmpty())
        {
            l.add(theFolk.socialNote);
        }
        l.add("§7Drops when they go a while without company.");
        l.add("§7Hanging out with other Sims fills it back up.");
        return l;
    }

    private List<String> environmentWhy()
    {
        List<String> l = new ArrayList<String>();
        if (!theFolk.environmentNote.isEmpty())
        {
            l.add(theFolk.environmentNote);
        }
        l.add("§aNice: flowers, trees, water, carpet, bookshelves");
        l.add("§cUgly: lava, fire, cobwebs, gravel, dead bushes");
        l.add("§7A dark home with no torches counts against it.");
        return l;
    }

    private List<String> traitWhy(String name)
    {
        List<String> l = new ArrayList<String>();
        l.add("§e" + name);
        Trait trait = Trait.getTraitFromName(name);
        if (trait != null && trait.getTraitDescription() != null)
        {
            for (Object line : fontRendererObj.listFormattedStringToWidth(trait.getTraitDescription(), 180))
            {
                l.add("§7" + line);
            }
        }
        for (String r : theFolk.happinessReasons)
        {
            if (r.endsWith("(" + name + ")"))
            {
                l.add((r.startsWith("+") ? "§a" : "§c") + "Right now: " + r.substring(0, r.lastIndexOf(" (")) + " happiness");
            }
        }
        return l;
    }

    private void infoRow(int x, int y, String label, String value)
    {
        fontRendererObj.drawString(label, x, y, COL_TEXT);
        int vx = x + 46, room = gx + PANEL_W - 8 - vx;
        String shown = fontRendererObj.trimStringToWidth(value, room);
        fontRendererObj.drawString(shown, vx, y, 0x000080);
        if (!shown.equals(value))
        {
            hover(vx, y, vx + room, y + 9, Arrays.asList(value));
        }
    }

    /** a sunken bar filled red to green by how full it is, with the value written on it */
    private void drawBar(int x, int y, int w, int h, float frac, int min, int max, String text)
    {
        drawRect(x, y, x + w, y + h, COL_LIGHT);
        drawRect(x, y, x + w - 1, y + h - 1, COL_SLOT_DARK);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, COL_SLOT);
        frac = Math.max(0f, Math.min(1f, frac));
        int fill = (int) ((w - 2) * frac);
        if (fill > 0)
        {
            int r = frac < 0.5f ? 0xD0 : (int) (0xD0 * (1f - frac) * 2f);
            int g = frac > 0.5f ? 0xC0 : (int) (0xC0 * frac * 2f);
            int col = 0xFF000000 | (r << 16) | (g << 8) | 0x20;
            drawRect(x + 1, y + 1, x + 1 + fill, y + h - 1, col);
        }
        int tx = x + (w - fontRendererObj.getStringWidth(text)) / 2;
        fontRendererObj.drawStringWithShadow(text, tx, y + (h - 8) / 2, 0xFFFFFF);
    }

    private void drawSlot(int x, int y)
    {
        drawRect(x, y, x + 18, y + 18, COL_LIGHT);
        drawRect(x, y, x + 17, y + 17, COL_SLOT_DARK);
        drawRect(x + 1, y + 1, x + 17, y + 17, COL_SLOT);
    }

    private void drawBevel(int x, int y, int w, int h, int fill, int light, int dark)
    {
        drawRect(x, y, x + w, y + h, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, dark);
        drawRect(x + 1, y + 1, x + w - 2, y + h - 2, light);
        drawRect(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }

    private void drawItem(ItemStack stack, int x, int y)
    {
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    private void hover(int x1, int y1, int x2, int y2, List<String> lines)
    {
        hoverBoxes.add(new int[] {x1, y1, x2, y2});
        hoverText.add(lines);
    }

    /** CROPFARMER -> Cropfarmer */
    private static String niceName(String enumName)
    {
        String s = enumName.replace('_', ' ').toLowerCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void drawRelationships()
    {
        int left = gx + 8;
        int disoffset = gy + 22;
        fontRendererObj.drawString(theFolk.name + "'s Relationships", gx + 8, gy + 6, COL_TEXT);

        for (int r = relOffset; r < folksRelationships.size(); r++)
        {
            try
            {
                Relationship rel = folksRelationships.get(r);
                String[] sp = rel.toStringPersepctive(theFolk).split(": ");
                fontRendererObj.drawString(sp[0], left, disoffset, 0x000000);
                fontRendererObj.drawString(sp[1], gx + PANEL_W / 2, disoffset, 0x000080);
                disoffset += 10;

                if (disoffset > gy + PANEL_H - 34)
                {
                    break;
                }
            }
            catch (Exception e) {}
        }
    }

    private void showPage()
    {
        buttonList.clear();
        int by = gy + PANEL_H - 26;

        if (currentPage == 0)
        {
            buttonList.add(new GuiButton(1, gx + 130, by - 1, 76, 20, "Relationships"));
            buttonList.add(new GuiButton(0, gx + 208, by - 1, 40, 20, "Bye!"));
        }
        else
        {
            buttonList.add(new GuiButton(4, gx + 8, by - 1, 50, 20, "Back"));

            if (relOffset > 0)
            {
                buttonList.add(new GuiButton(2, gx + PANEL_W - 52, gy + 3, 20, 14, "<"));
            }

            if (folksRelationships.size() - relOffset > 14)
            {
                buttonList.add(new GuiButton(3, gx + PANEL_W - 28, gy + 3, 20, 14, ">"));
            }
        }
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton guibutton)
    {
        if (!guibutton.enabled)
        {
            return;
        }

        switch (guibutton.id)
        {
            case 0:
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                return;
            case 1:
                currentPage = 1;
                break;
            case 2:
                relOffset = Math.max(0, relOffset - 14);
                break;
            case 3:
                relOffset += 14;
                break;
            case 4:
                currentPage = 0;
                break;
        }
        showPage();
    }
}
