package ashjack.simukraftreloaded.client.Gui.blocks;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.packetsNEW.PacketHandler;
import ashjack.simukraftreloaded.packetsNEW.toServer.RoomBoxPacket;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

/** settings for one Room Box: name, apartment or hotel, size, and who's staying */
public class GuiRoomBox extends GuiScreen
{
    private static final int BTN_DONE = 0, BTN_RENAME = 1, BTN_MODE = 2, BTN_SIZE = 3, BTN_EVICT = 4;

    private final V3 box;
    private GuiTextField nameField;
    private GuiButton modeButton, sizeButton, evictButton;

    public GuiRoomBox(V3 box)
    {
        this.box = box;
    }

    /** read fresh every time: the server changes the room as packets arrive */
    private Building room()
    {
        Building b = Building.getBuilding(box);
        return b != null && b.isRoom() ? b : null;
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int cx = width / 2;
        nameField = new GuiTextField(fontRendererObj, cx - 100, 40, 140, 20);
        nameField.setMaxStringLength(32);
        Building room = room();
        nameField.setText(room != null ? room.displayNameWithoutPK : "");

        buttonList.add(new GuiButton(BTN_RENAME, cx + 45, 40, 55, 20, "Rename"));
        buttonList.add(modeButton = new GuiButton(BTN_MODE, cx - 100, 70, 200, 20, ""));
        buttonList.add(sizeButton = new GuiButton(BTN_SIZE, cx - 100, 95, 200, 20, ""));
        buttonList.add(evictButton = new GuiButton(BTN_EVICT, cx - 100, height - 55, 200, 20, ""));
        buttonList.add(new GuiButton(BTN_DONE, cx - 100, height - 30, 200, 20, "Done"));
        refreshButtons();
    }

    private void refreshButtons()
    {
        Building room = room();
        if (room == null)
        {
            return;
        }
        modeButton.displayString = room.isHotel() ? "Mode: Hotel room (short stays)" : "Mode: Apartment (permanent home)";
        sizeButton.displayString = "Size: " + Building.roomSizeName(room.roomSize) + " (" + room.roomSize + (room.roomSize == 1 ? " folk)" : " folks)");
        evictButton.displayString = room.isHotel() ? "Check guests out" : "Evict tenants";
        evictButton.enabled = !room.tennants.isEmpty();
    }

    @Override
    public void updateScreen()
    {
        nameField.updateCursorCounter();
        refreshButtons();
    }

    @Override
    public void drawScreen(int mx, int my, float f)
    {
        drawDefaultBackground();
        Building room = room();
        int cx = width / 2;

        if (room == null)
        {
            drawCenteredString(fontRendererObj, "This Room Box isn't registered - break it and place it again", cx, 60, 0xff8080);
            super.drawScreen(mx, my, f);
            return;
        }

        drawCenteredString(fontRendererObj, "Room Box - " + room.displayNameWithoutPK, cx, 15, 0xffffff);
        nameField.drawTextBox();

        int y = 125;
        String money = SimukraftReloaded.displayMoney(room.rent);
        drawCenteredString(fontRendererObj, room.isHotel() ? "Nightly rate: " + money + " Sim-U-credits" : "Rent: " + money + " Sim-U-credits a day", cx, y, 0xffff80);
        y += 16;

        if (room.tennants.isEmpty())
        {
            drawCenteredString(fontRendererObj, room.isHotel() ? "No guests" : "Vacant", cx, y, 0xc0c0c0);
        }
        else
        {
            drawCenteredString(fontRendererObj, room.isHotel() ? "Guests:" : "Tenants:", cx, y, 0xffffff);
            for (String name : room.tennants)
            {
                y += 11;
                drawCenteredString(fontRendererObj, name, cx, y, 0xa0ffa0);
            }
            if (room.isHotel())
            {
                y += 14;
                drawCenteredString(fontRendererObj, room.stayNightsLeft + (room.stayNightsLeft == 1 ? " night" : " nights") + " left", cx, y, 0xc0c0c0);
            }
        }
        super.drawScreen(mx, my, f);
    }

    @Override
    protected void actionPerformed(GuiButton button)
    {
        Building room = room();
        if (button.id == BTN_DONE || room == null)
        {
            mc.displayGuiScreen(null);
            return;
        }

        if (button.id == BTN_RENAME)
        {
            send(RoomBoxPacket.RENAME, nameField.getText());
        }
        else if (button.id == BTN_MODE)
        {
            send(RoomBoxPacket.SET_MODE, room.isHotel() ? Building.ROOM_APARTMENT : Building.ROOM_HOTEL);
        }
        else if (button.id == BTN_SIZE)
        {
            int next = room.roomSize == 1 ? 2 : room.roomSize == 2 ? 4 : 1;
            send(RoomBoxPacket.SET_SIZE, Integer.toString(next));
        }
        else if (button.id == BTN_EVICT)
        {
            send(RoomBoxPacket.EVICT, "");
        }
    }

    private void send(byte action, String value)
    {
        PacketHandler.net.sendToServer(new RoomBoxPacket(box, action, value));
    }

    @Override
    protected void keyTyped(char c, int key)
    {
        if (nameField.textboxKeyTyped(c, key))
        {
            return;
        }
        if (key == Keyboard.KEY_RETURN)
        {
            send(RoomBoxPacket.RENAME, nameField.getText());
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button)
    {
        super.mouseClicked(x, y, button);
        nameField.mouseClicked(x, y, button);
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }
}
