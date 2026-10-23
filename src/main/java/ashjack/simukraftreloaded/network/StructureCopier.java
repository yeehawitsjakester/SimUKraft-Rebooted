package ashjack.simukraftreloaded.network;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import ashjack.simukraftreloaded.blocks.BlockMarker;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.core.registry.SimukraftReloaded;
import ashjack.simukraftreloaded.core.registry.SimukraftReloadedBlocks;
import ashjack.simukraftreloaded.proxies.CommonProxy.V3;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The marker's "Copy structure/building": scans the marked area and writes it out as a blueprint in
 * mods/Simukraft/buildings/other. Moved from a client-side thread that read the integrated server's world.
 */
final class StructureCopier
{
    private StructureCopier() {}

    /** loc = the front-left marker that was right-clicked; the player stands facing it with the rear marker beyond */
    static void copy(EntityPlayerMP player, V3 loc)
    {
        if (BlockMarker.serverMarkers.size() < 3)
        {
            ActionHandlers.tell(player, "Place three markers first (front-left, front-right, rear-left).");
            return;
        }
        World world = player.worldObj;
        V3 cxyz = loc;
        V3 Lxyz = BlockMarker.markers().get(1).toV3();
        V3 Bxyz = BlockMarker.markers().get(2).toV3();
        V3 exyz = new V3(Math.floor(player.posX), Math.floor(Lxyz.y+1d)
                         , Math.floor(player.posZ), Bxyz.theDimension);
        int ftbCount = 0, ltrCount = 0;

        if (cxyz.x.intValue() == Lxyz.x.intValue())
        {
            ltrCount = Math.abs(Lxyz.z.intValue() - cxyz.z.intValue()) - 1;
        }
        else
        {
            ltrCount = Math.abs(Lxyz.x.intValue() - cxyz.x.intValue()) - 1;
        }

        if (cxyz.x.intValue() == Bxyz.x.intValue())
        {
            ftbCount = Math.abs(Bxyz.z.intValue() - cxyz.z.intValue()) - 1;
        }
        else
        {
            ftbCount = Math.abs(Bxyz.x.intValue() - cxyz.x.intValue()) - 1;
        }

        if (ftbCount == 0 || ltrCount == 0)
        {
            ActionHandlers.tell(player, "ERROR: Markers not placed correctly, try again.");
            return;
        }

        int cx, cy, cz, ex, ey, ez, bx = 0, by = 0, bz = 0;
        cx = cxyz.x.intValue();
        cy = cxyz.y.intValue();
        cz = cxyz.z.intValue();
        ex = exyz.x.intValue();
        ey = exyz.y.intValue();
        ez = exyz.z.intValue();
        bx = ex;
        by = ey;
        bz = ez;  // b... is initial building xyz, adjusted below

        if (cz == ez)
        {
            if (cx > ex)
            {
                bx = cx + 1;
            }
            else
            {
                bx = cx - 1;
            }
        }
        else if (cx == ex)
        {
            if (cz > ez)
            {
                bz = cz + 1;
            }
            else
            {
                bz = cz - 1;
            }
        }
        else
        {
            ActionHandlers.tell(player, "Please stand facing the primary marker with the rear marker in the distance.");
            SimukraftReloaded.sendChat("Could not copy building, Technical info:cx=" + cx + ", cz=" + cz + ", ex=" + ex + ", ez=" + ez);
            return;
        }

        int xo = 0, zo = 0;
        int iD = 0;
			int meta = 0;
        HashMap key = new HashMap();
        key.put("0:0", "A"); // air block
        ArrayList layerLines = new ArrayList();
        String layerLine;
        int ch = 66;
        boolean allAirBlocks = true;
        String keyString = "A=minecraft:air,0;";

        try
        {
            allDone:

            for (int l = 0; l < 200; l++)
            {
                layerLine = "";

                for (int ftb = 0; ftb < ftbCount; ftb++)
                {
                    for (int ltr = 1; ltr <= ltrCount; ltr++)
                    {
                        if (cz == ez)
                        {
                            if (cx > ex)
                            {
                                xo = ftb;
                                zo = ltr;
                            }
                            else
                            {
                                xo = -ftb;
                                zo = -ltr;
                            }
                        }
                        else if (cx == ex)
                        {
                            if (cz > ez)
                            {
                                xo = -ltr;
                                zo = ftb;
                            }
                            else
                            {
                                xo = ltr;
                                zo = -ftb;
                            }
                        }

                        int xxx = bx + xo;
                        int yyy = (by + l) - 1;
                        int zzz = bz + zo;
                        Block theBlock = world.getBlock(xxx, yyy, zzz);
                        iD = Block.getIdFromBlock(world.getBlock(xxx, yyy, zzz));
                        meta = world.getBlockMetadata(xxx, yyy, zzz);
                        String letter = "";

                        if(theBlock == SimukraftReloadedBlocks.controlBox)
                        {
                            letter = "$";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.livingBlock)
                        {
                        	letter = "!";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.specialBlock && meta > 7)
                        {
                        	letter = "0";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 0)
                        {
                        	letter = "0";
                        }
                        
                        else  if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 1)
                        {
                        	letter = "1";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 2)
                        {
                        	letter = "2";
                        }
                        
                        else  if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 3)
                        {
                        	letter = "3";
                        }
                        
                        else  if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 4)
                        {
                        	letter = "4";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 5)
                        {
                        	letter = "5";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 6)
                        {
                        	letter = "6";
                        }
                        
                        else if(theBlock == SimukraftReloadedBlocks.specialBlock && meta == 7)
                        {
                        	letter = "7";
                        }
                        
                        /*Light Blocks*/
                        /*else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 0)
                        {
                            letter = "À";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 1)
                        {
                            letter = "Æ";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 2)
                        {
                            letter = "Ç";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 3)
                        {
                            letter = "È";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 4)
                        {
                            letter = "Ì";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 5)
                        {
                            letter = "Ð";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 6)
                        {
                            letter = "Ñ";
                        }
                        else if (iD == Block.getIdFromBlock(SimukraftReloadedBlocks.lightBox) && meta == 7)
                        {
                            letter = "Ò";
                        }*/
                        else
                        {
                            letter = (String) key.get(iD + ":" + meta);

                            if (key.get(iD + ":" + meta) == null)
                            {
                                ch++;
                                key.put(iD + ":" + meta, new Character((char)ch).toString());
                                keyString += new Character((char)ch).toString() + "=" + GameRegistry.findUniqueIdentifierFor(theBlock).toString() + "," + meta + ";";
                                letter = new Character((char)ch).toString();
                            }
                        }

                        layerLine += letter;

                        if (iD != 0)
                        {
                            allAirBlocks = false;
                        }
                    }
                }

                if (allAirBlocks)
                {
                    break allDone;
                }
                else
                {
                    layerLines.add(layerLine);
                }

                allAirBlocks = true;
            }

            if (layerLines.size() == 0)
            {
                ActionHandlers.tell(player, "Error, could not capture all blocks, try standing closer to marker and try again");
                return;
            }

            /// captured building
            File check = new File(SimukraftReloaded.getSimukraftFolder() + "/buildings/");

            if (!check.exists())
            {
            	SimukraftReloaded.sendChat(SimukraftReloaded.getSimukraftFolder() + "/buildings/  folder is missing, The mod is not correctly installed, please copy the simukraft folder AND the zip file.");
                return;
            }

            String f = String.valueOf(System.currentTimeMillis());
            int l = f.length();
            f = f.substring(l - 6);
            FileWriter fstream = new FileWriter(SimukraftReloaded.getSimukraftFolder() + "/buildings/other/My Build" + f + ".txt");
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(ltrCount + "x" + ftbCount + "x" + layerLines.size() + "\r\n");
            out.write(keyString + "\r\n");

            for (int q = 0; q < layerLines.size(); q++)
            {
                out.write(layerLines.get(q).toString() + "\r\n");
            }

            out.close();
            ActionHandlers.tell(player, "Building copied and stored as 'My Build" + f + "' in Other buildings.");
            world.playSoundEffect(loc.x, loc.y, loc.z, "ashjacksimukraftreloaded:computer", 1f, 1f);
            Building.initialiseAllBuildings();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
