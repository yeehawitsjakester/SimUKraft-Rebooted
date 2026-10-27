package ashjack.simukraftreloaded.client.Gui.blocks;

import net.minecraft.util.ChatComponentText;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagCompound;
import ashjack.simukraftreloaded.network.ServerActions;
import ashjack.simukraftreloaded.network.ActionHandlers;
import java.text.NumberFormat;
import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ashjack.simukraftreloaded.common.PricesForBlocks;
import ashjack.simukraftreloaded.common.jobs.Job;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.proxies.CommonProxy.Commodity;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;

public class GuiBankATM extends GuiScreen
{
    private V3 bankLocation = null;
    private EntityPlayer thePlayer = null;
    private int mouseCount = 0;
    private ATMscreen theScreen = ATMscreen.START;
    private ArrayList<Commodity> cart = new ArrayList<Commodity>();
    private String errorText = "";

    private enum ATMscreen
    {
        START, DEPOSIT, COMMODITIES
    }

    public GuiBankATM(V3 location, EntityPlayer player)
    {
        this.bankLocation = location;
        this.thePlayer = player;
    }

    @Override
    public void initGui()
    {
        // checked on the client's own world here (the server checks again before paying out)
        boolean robbed = !vaultNearby(Blocks.diamond_block) || !vaultNearby(Blocks.emerald_block) || !vaultNearby(Blocks.gold_block);

        if (robbed)   // if they've mined the diamond/gold blocks :-)
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            thePlayer.addChatComponentMessage(new ChatComponentText("Looks like you've robbed the bank! Replace the items and we'll let you off and let you use this ATM. Next time we won't be so nice about it!"));
            return;
        }

        buttonList.clear();

        if (theScreen == ATMscreen.START)
        {
            buttonList.add(new GuiButton(0, width / 2 - 50, 50, 100, 20, "Deposit items"));
            buttonList.add(new GuiButton(1, width / 2 - 50, 70, 100, 20, "Buy Commodities"));
        }
        else if (theScreen == ATMscreen.DEPOSIT)
        {
            int offset = 30;

            for (int inv = 0; inv < thePlayer.inventory.getSizeInventory(); inv++)
            {
                ItemStack is = thePlayer.inventory.getStackInSlot(inv);

                if (is != null)
                {
                    if (is.getItem() == Items.diamond)
                    {
                        buttonList.add(new GuiButton(inv + 100, width / 2, offset, 100, 20
                                                     , "Sell 1 for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceDiamond)));
                        buttonList.add(new GuiButton(inv + 500, width / 2 + 100, offset, 100, 20
                                                     , "Sell " + is.stackSize + " for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceDiamond * is.stackSize)));
                        offset += 20;
                    }
                    else if (is.getItem() == Items.emerald)
                    {
                        buttonList.add(new GuiButton(inv + 100, width / 2, offset, 100, 20
                                                     , "Sell 1 for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceEmerald)));
                        buttonList.add(new GuiButton(inv + 500, width / 2 + 100, offset, 100, 20
                                                     , "Sell " + is.stackSize + " for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceEmerald * is.stackSize)));
                        offset += 20;
                    }
                    else if (is.getItem() == Items.redstone)
                    {
                        buttonList.add(new GuiButton(inv + 100, width / 2, offset, 100, 20
                                                     , "Sell 1 for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceRedstone)));
                        buttonList.add(new GuiButton(inv + 500, width / 2 + 100, offset, 100, 20
                                                     , "Sell " + is.stackSize + " for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceRedstone * is.stackSize)));
                        offset += 20;
                    }
                    else if (is.getItem() == Items.glowstone_dust)
                    {
                        buttonList.add(new GuiButton(inv + 100, width / 2, offset, 100, 20
                                                     , "Sell 1 for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceGlowstone)));
                        buttonList.add(new GuiButton(inv + 500, width / 2 + 100, offset, 100, 20
                                                     , "Sell " + is.stackSize + " for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceGlowstone * is.stackSize)));
                        offset += 20;
                    }
                    else if (is.getItem() == Items.gold_ingot)
                    {
                        buttonList.add(new GuiButton(inv + 100, width / 2, offset, 100, 20
                                                     , "Sell 1 for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceGold)));
                        buttonList.add(new GuiButton(inv + 500, width / 2 + 100, offset, 100, 20
                                                     , "Sell " + is.stackSize + " for " + SimukraftReloaded.displayMoney(PricesForBlocks.bankPriceGold * is.stackSize)));
                        offset += 20;
                    }
                }
            }
        }
        else if (theScreen == ATMscreen.COMMODITIES)
        {
            int offset = 30;

            for (int it = 0; it < SimukraftReloaded.commodities().size(); it++)
            {
                buttonList.add(new GuiButton(it + 200, width / 2, offset, 20, 20, "-"));
                buttonList.add(new GuiButton(it + 300, width / 2 + 20, offset, 20, 20, "+"));
                offset += 20;
            }

            buttonList.add(new GuiButton(400, width - 60, height - 30, 50, 20, "Buy"));
        }
    }

    @Override
    public void drawScreen(int i, int j, float f)
    {
        if (mouseCount < 10)
        {
            mouseCount++;
            Mouse.setGrabbed(false);
        }

        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Sim-U-Bank Ltd", width / 2, 5, 0xffffff);

        if (theScreen == ATMscreen.START)
        {
            drawCenteredString(fontRendererObj, "Welcome to Sim-U-Bank, using this ATM you can deposit your gems and stones", width / 2, 15, 0x00ff00);
            drawCenteredString(fontRendererObj, "in exchange for Sim-U-Credits, we offer the best prices for your unwanted", width / 2, 25, 0x00ff00);
            drawCenteredString(fontRendererObj, "Diamonds, Emeralds, Redstones, Glowstones and Gold.", width / 2, 35, 0x00ff00);
        }
        else if (theScreen == ATMscreen.DEPOSIT)
        {
            int offset = 35;
            boolean playerHasItems = false;
            drawCenteredString(fontRendererObj, "Items in your inventory that this bank accepts:", width / 2, 15, 0x00ff00);

            for (int inv = 0; inv < thePlayer.inventory.getSizeInventory(); inv++)
            {
                ItemStack is = thePlayer.inventory.getStackInSlot(inv);

                if (is != null)
                {
                    if (is.getItem() == Items.diamond || is.getItem() == Items.emerald
                            || is.getItem() == Items.redstone || is.getItem() == Items.glowstone_dust
                            || is.getItem() == Items.gold_ingot)
                    {
                        drawString(fontRendererObj, is.stackSize + " x " + is.getDisplayName(), 40, offset, 0x00ff00);
                        playerHasItems = true;
                        offset += 20;
                    }
                }
            }

            if (!playerHasItems)
            {
                drawString(fontRendererObj, "You don't have anything we want to buy, sorry.", 40, offset, 0x00ff00);
            }
        }
        else if (theScreen == ATMscreen.COMMODITIES)
        {
            drawCenteredString(fontRendererObj, "Commodities available to buy today", width / 2, 20, 0x00ff00);
            int offset = 35;

            if (SimukraftReloaded.commodities().size() == 0)
            {
                drawString(fontRendererObj, "Currently no items, come back later.", 20, offset, 0x00ff00);
            }

            for (int it = 0; it < SimukraftReloaded.commodities().size(); it++)
            {
                Commodity item = SimukraftReloaded.commodities().get(it);
                drawString(fontRendererObj, item.quantity + " x " + item.theItemStack.getDisplayName()
                           + " @ " + SimukraftReloaded.displayMoney(item.priceEach) + " each", 20, offset, 0x00ff00);
                int qty = 0;

                for (int ci = 0; ci < cart.size(); ci++)
                {
                    Commodity cartItem = cart.get(ci);

                    if (cartItem.theItemStack.getDisplayName().contentEquals(item.theItemStack.getDisplayName()))
                    {
                        qty = cartItem.quantity;
                    }
                }

                drawString(fontRendererObj, qty + "", (width / 2) - 30, offset, 0x00ff00);
                offset += 20;
            }
        }

        drawCenteredString(fontRendererObj, errorText, width / 2, height - 15, 0xff0000);
        super.drawScreen(i, j, f);
    }

    long fuckingBodge = 0; //stops it clicking a button by accident when switching screens

    @Override
    protected void actionPerformed(GuiButton guibutton)
    {
        if (System.currentTimeMillis() - fuckingBodge < 500)
        {
            return;
        }

        fuckingBodge = System.currentTimeMillis();

        if (guibutton.displayString.contentEquals("Deposit items"))
        {
            theScreen = ATMscreen.DEPOSIT;
            initGui();
        }
        else if (guibutton.displayString.contentEquals("Buy Commodities"))
        {
            theScreen = ATMscreen.COMMODITIES;
            initGui();
        }
        else if (guibutton.id >= 100 && guibutton.id < 200)   //player is selling an item (id-100 is the inventory slot)
        {
            sendSell(guibutton.id - 100, false);
        }
        else if (guibutton.id >= 500 && guibutton.id < 600)  // sell stack of item (deposit)
        {
            sendSell(guibutton.id - 500, true);
        }
        else if (guibutton.id >= 200 && guibutton.id < 300)    // minus qty on commod screen
        {
            Commodity comm = SimukraftReloaded.commodities().get(guibutton.id - 200);

            for (int ci = 0; ci < cart.size(); ci++)
            {
                Commodity cc = cart.get(ci);

                if (cc.theItemStack.getDisplayName().contentEquals(comm.theItemStack.getDisplayName()))
                {
                    if (cc.quantity > 0)
                    {
                        cc.quantity--;
                        break;
                    }
                }

                if (cc.quantity == 0)
                {
                    cart.remove(ci);
                    break;
                }
            }
        }
        else if (guibutton.id >= 300 && guibutton.id < 400)    // plus qty on commod screen
        {
            Commodity comm = SimukraftReloaded.commodities().get(guibutton.id - 300);
            boolean added = false;

            for (int ci = 0; ci < cart.size(); ci++)
            {
                Commodity cc = cart.get(ci);

                if (cc.theItemStack.getDisplayName().contentEquals(comm.theItemStack.getDisplayName()))
                {
                    if (cc.quantity < comm.quantity)
                    {
                        cc.quantity++;
                        added = true;
                        break;
                    }
                    else
                    {
                        return;
                    }
                }
            }

            if (!added)
            {
                cart.add(new Commodity(comm.theItemStack, 1, comm.priceEach));
            }
        }
        else if (guibutton.id == 400)      /// buy button on commodatites
        {
            if (cart.size() == 0)
            {
                errorText = "You've not added any items.";
                return;
            }

            float cost = 0f;

            for (int ci = 0; ci < cart.size(); ci++)
            {
                Commodity cartItem = cart.get(ci);
                ItemStack is = cartItem.theItemStack;
                is.stackSize = cartItem.quantity;
                cost += cartItem.quantity * cartItem.priceEach;
            }

            if (cost > ashjack.simukraftreloaded.common.ClientMirror.wallet)
            {
                errorText = "The cost is " + SimukraftReloaded.displayMoney(cost) + ", but you only have " +
                		SimukraftReloaded.displayMoney(ashjack.simukraftreloaded.common.ClientMirror.wallet);
                return;
            }
            else
            {
                // the server checks the price and stock again, takes the money and hands over the items
                NBTTagList list = new NBTTagList();
                for (Commodity cartItem : cart)
                {
                    NBTTagCompound c = new NBTTagCompound();
                    c.setString("item", Item.itemRegistry.getNameForObject(cartItem.theItemStack.getItem()));
                    c.setInteger("damage", cartItem.theItemStack.getItemDamage());
                    c.setInteger("qty", cartItem.quantity);
                    list.appendTag(c);
                }
                NBTTagCompound data = new NBTTagCompound();
                ActionHandlers.putV3(data, "atm", bankLocation);
                data.setTag("cart", list);
                ServerActions.send("bankBuy", data);
                mc.displayGuiScreen(null);
            }
        }
    }

    private void sendSell(int slot, boolean all)
    {
        NBTTagCompound data = new NBTTagCompound();
        ActionHandlers.putV3(data, "atm", bankLocation);
        data.setInteger("slot", slot);
        data.setBoolean("all", all);
        ServerActions.send("bankSell", data);
    }

    @Override
    public void updateScreen()
    {
        // the server changes the inventory; redraw the deposit buttons when it does
        if (theScreen == ATMscreen.DEPOSIT && mc.thePlayer.ticksExisted % 10 == 0)
        {
            initGui();
        }
    }

    private boolean vaultNearby(Block block)
    {
        int x0 = bankLocation.x.intValue(), y0 = bankLocation.y.intValue(), z0 = bankLocation.z.intValue();
        for (int x = -10; x <= 10; x++)
            for (int y = -10; y <= 10; y++)
                for (int z = -10; z <= 10; z++)
                    if (mc.theWorld.getBlock(x0 + x, y0 + y, z0 + z) == block)
                        return true;
        return false;
    }

    @Override
    protected void keyTyped(char c, int i)
    {
        if (i == 1)    //escape and dont save
        {
            mc.currentScreen = null;
            mc.setIngameFocus();
            return;
        }
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }
}
