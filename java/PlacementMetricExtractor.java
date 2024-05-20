/*
# Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.
# All rights reserved. The contributor(s) of this file has/have agreed to the
# RapidStream Contributor License Agreement.
*/

package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.design.blocks.PBlockRange;
import com.xilinx.rapidwright.design.blocks.UtilizationType;
import com.xilinx.rapidwright.design.tools.LUTTools;
import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.ConstraintGroup;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.util.Utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PlacementMetricExtractor {

    private static HashSet<String> stopElements;
    private static HashSet<String> lutElements;
    private static HashSet<String> regElements;
    static {
        stopElements = new HashSet<String>();
        // CONFIG
        stopElements.add("BSCAN1");
        stopElements.add("BSCAN2");
        stopElements.add("BSCAN3");
        stopElements.add("BSCAN4");
        stopElements.add("DCIRESET");
        stopElements.add("DNAPORT");
        stopElements.add("EFUSE_USR");
        stopElements.add("FRAME_ECC");
        stopElements.add("ICAP_BOT");
        stopElements.add("ICAP_TOP");
        stopElements.add("MASTER_JTAG");
        stopElements.add("STARTUP");
        stopElements.add("USR_ACCESS");

        // BRAM
        stopElements.add("RAMBFIFO36E2");

        // IOB
        stopElements.add("IBUFCTRL");
        stopElements.add("INBUF");
        stopElements.add("OUTBUF");
        stopElements.add("PADOUT");

        // MMCM
        stopElements.add("MMCME3_ADV");

        // DSP
        stopElements.add("DSP_PREADD_DATA");
        stopElements.add("DSP_A_B_DATA");
        stopElements.add("DSP_C_DATA");
        stopElements.add("DSP_OUTPUT");

        lutElements = new HashSet<String>();
        regElements = new HashSet<String>();

        for (String letter : Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H")) {
            regElements.add(letter +"FF");
            regElements.add(letter +"FF2");
            for (String size : Arrays.asList("5", "6")) {
                lutElements.add(letter + size + "LUT");
            }
        }
    }


    public static boolean isBELAReg(String elementName) {
        return regElements.contains(elementName);
    }

    private static boolean isCellLutMemory(Cell c) {
        if (c == null) return false;
        if (c.getType().contains("SRL") || c.getType().contains("RAM")) return true;
        return false;
    }

    private static void incrementUtilType(Map<UtilizationType, Integer> map, UtilizationType ut) {
        Integer val = map.get(ut);
        val++;
        map.put(ut, val);
    }

    public static Map<UtilizationType, Integer> calculateUtilization(Design d, PBlock pblock) {
        Set<Site> sites = pblock.getAllSites(null);
        List<SiteInst> siteInsts = d.getSiteInsts().stream().filter(s -> sites.contains(s.getSite()))
                .collect(Collectors.toList());
        // System.out.println(sites);
        for (SiteInst si : siteInsts) {
            if (si.getCells().size() == 0)
                System.out.println("SiteInst: " + si + "; Cell #: " + si.getCells().size());
        }
        return calculateUtilization(siteInsts);
    }


    // For an Alveo U250 design, if we use `show_objects [get_pblocks <right half slrs like CR_X4Y12_To_CR_X7Y15>]`, and see the utilization in gui,
    // we can see that gui statistics is smaller than the following calculation, this is because some
    // site instances or cells belong to the memory subsystem (DDR controller) instead of the dynamic region of user logic.
    // TODO: Hence, the correct way to use this calculation is to use the difference between total available sites/cells and the following calculation
    //       as the budget for the next-step floorplan revision.
    public static Map<UtilizationType, Integer> calculateUtilization(Collection<SiteInst> siteInsts) {
        Map<UtilizationType, Integer> map = new HashMap<UtilizationType, Integer>();

        for (UtilizationType ut : UtilizationType.values()) {
            map.put(ut, 0);
        }
        for (SiteInst si : siteInsts) {
            SiteTypeEnum s = si.getSite().getSiteTypeEnum();
            if (Utils.isSLICE(si)) {    // done
                incrementUtilType(map, UtilizationType.CLBS);
                if (s == SiteTypeEnum.SLICEL) {
                    // corresponding TCL: get_sites -of_objects [get_pblocks CR_X0Y12_To_CR_X3Y15] -filter { IS_USED == "TRUE" && SITE_TYPE == "SLICEL" }.
                    // ? Vivado failing to include one column (e.g., as tall as the height of a clock region in Alveo U250) of SLICELs beside the laguna column, reason unknown yet -- for example, `get_pblocks -of_objects [get_sites SLICE_X112Y741]` wouldn't show "CR_X0Y12_To_CR_X3Y15", which was created by "create_pblock CR_X0Y12_To_CR_X3Y15; resize_pblock CR_X0Y12_To_CR_X3Y15 -add CLOCKREGION_X0Y12:CLOCKREGION_X3Y15"
                        // ! Partially solved: https://github.com/Xilinx/RapidWright/issues/989
                    incrementUtilType(map, UtilizationType.CLBLS);
                    // System.out.println("mark_objects [get_sites " + si.getSite() + "]"); // debug
                } else if (s == SiteTypeEnum.SLICEM) {
                    incrementUtilType(map, UtilizationType.CLBMS);
                    // corresponding TCL: get_sites -of_objects [get_pblocks CR_X0Y12_To_CR_X3Y15] -filter { IS_USED == "TRUE" && SITE_TYPE == "SLICEM" }
                }
            } else if (Utils.isDSP(si)) {
                incrementUtilType(map, UtilizationType.DSPS);
            } else if (Utils.isBRAM(si)) {
                if (s == SiteTypeEnum.RAMBFIFO36) {
                    incrementUtilType(map, UtilizationType.RAMB36S_FIFOS);
                } else if (s == SiteTypeEnum.RAMB181 || s == SiteTypeEnum.RAMBFIFO18) {
                    incrementUtilType(map, UtilizationType.RAMB18S);
                }
            } else if (Utils.isURAM(si)) {
                incrementUtilType(map, UtilizationType.URAMS);
            }
            for (Cell c : si.getCells()) {
                /* As in UtilizationType:
                CLB_LUTS("CLB LUTs"),
                LUTS_AS_LOGIC("LUTs as Logic"),
                LUTS_AS_MEMORY("LUTs as Memory"),
                CLB_REGS("CLB Regs"),
                REGS_AS_FFS("Regs as FF"),
                REGS_AS_LATCHES("Regs as Latch"),
                CARRY8S("CARRY8s"),
                //F7_MUXES("F7 Muxes"),
                //F8_MUXES("F8 Muxes"),
                //F9_MUXES("F9 Muxes"),
                CLBS("CLBs"),
                CLBLS("CLBLs"),
                CLBMS("CLBMs"),
                //LUT_FF_PAIRS("Lut/FF Pairs"),
                RAMB36S_FIFOS("RAMB36s/FIFOs"),
                RAMB18S("RAMB18s"),
                URAMS("URAMs"),
                DSPS("DSPs");
                */
                if (c.getBELName() == null) {
                    // e.g., Alveo U250, DSP48E2_{X28|X29} cell: <LOCKED>(BEL: (unplaced)) -> c.getBELName() == null -> exception
                    // System.out.println("null Name! site: " + si.getName() + " cell: " + c); // debug
                    continue;
                }
                if (isBELAReg(c.getBELName())) {
                    incrementUtilType(map, UtilizationType.CLB_REGS);
                    incrementUtilType(map, UtilizationType.REGS_AS_FFS);
                } else if (c.getBELName().contains("CARRY")) {
                    incrementUtilType(map, UtilizationType.CARRY8S);
                }

            }
            for (char letter : LUTTools.lutLetters) {
                Cell c5 = si.getCell(letter +"5LUT");
                Cell c6 = si.getCell(letter +"6LUT");
                if (c5 != null && c5.isRoutethru()) {
                    c5 = null;
                } else if (c6 != null && c6.isRoutethru()) {
                    c6 = null;
                }
                if (c5 != null || c6 != null) {
                    incrementUtilType(map, UtilizationType.CLB_LUTS);

                    if (isCellLutMemory(c5) || isCellLutMemory(c6)) {
                        incrementUtilType(map, UtilizationType.LUTS_AS_MEMORY);
                    } else {
                        incrementUtilType(map, UtilizationType.LUTS_AS_LOGIC);
                    }
                }
            }
        }
        return map;
    }

    private static String refinePBlockName(String pblockName) {
        String pattern = "^.*X(\\d+)Y(\\d+).*X(\\d+)Y(\\d+).*$";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(pblockName);
        assert m.find() : "pblockName " + pblockName + " does not match the pattern '^.*X(\\d+)Y(\\d+).*X(\\d+)Y(\\d+).*$'!";
        String newPBlockName = "X" + m.group(1) + "Y" + m.group(2) + "X" + m.group(3) + "Y" + m.group(4);
        return newPBlockName;
    }

    private static Map<String, PBlock> getNameToPBlocksFromXDC(Design design){
        Device device = design.getDevice();
        Map<String, PBlock> pblockMap = new HashMap<>();
        for(ConstraintGroup cg : ConstraintGroup.values()) {
            for(String line : design.getXDCConstraints(cg)) {
                // rebuild island pblocks from create_clock and resize_pblock
                if (line.trim().startsWith("create_pblock") && line.matches("^.*X\\d+Y\\d+.*X\\d+Y\\d+.*$")) {
                    String[] parts = line.split("\\s+");
                    String pblockName = refinePBlockName(parts[1]);
                    PBlock pblock = new PBlock();
                    pblockMap.put(pblockName, pblock);
                }
                if(line.trim().startsWith("resize_pblock") && line.matches("^.*X\\d+Y\\d+.*X\\d+Y\\d+.*-.*$")) {
                    String[] parts = line.split("\\s+");
                    String pblockName = null;
                    String pblockRange = null;
                    boolean nextIsName = false;
                    boolean nextIsRange = false;
                    PBlock pblock = null;
                    System.out.println(line.trim());
                    for (String part : parts) {
                        if (part.contains("get_pblocks")) {
                            nextIsName = true;
                        } else if (nextIsName) {
                            nextIsName = false;
                            pblockName = refinePBlockName(part.replace("]", "").replace("}", ""));
                            assert pblockMap.containsKey(pblockName) : "pblockName " + pblockName + " not found in pblockMap!";
                            pblock = pblockMap.get(pblockName);
                        } else if (part.contains("-add")) {
                            nextIsRange = true;
                        } else if (nextIsRange) {
                            pblockRange = part.replace("{", "").replace("}", "").replace("]", "");
                            // if (pblockRange.matches("^(RAMB|DSP|URAM).*$")) // BRAM/DSP/URAM cascade not relavant
                            //     continue;
                            pblock.add(new PBlockRange(device, pblockRange));
                        } else if (part.contains("-remove")) {
                            // TODO: "resize_pblock [...] -remove {...}" not considered yet.
                            // ? It seems that all removes are converted to adds for the current design.
                            // System.out.println("WARNING: Not considered yet!");
                            assert false : "resize_pblock [...] -remove {...} not considered yet!";
                            break;
                        }
                    }
                    assert pblock != null && pblock.size() != 0;
                    pblockMap.put(pblockName, pblock);
                }
            }
        }
        return pblockMap;
    }

    public static void getAllPBlockPlacementMetrics(String dcp) {
        Design design = null;
        Device device = null;
        try {
            design = Design.readCheckpoint(dcp);
            device = design.getDevice();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        assert design != null : "Design is null";
        assert device != null : "Device is null";
        Map<String, PBlock> pblockMap = getNameToPBlocksFromXDC(design);
        for (String pblockName : pblockMap.keySet()) {
            PBlock pblock = pblockMap.get(pblockName);
            Map<UtilizationType, Integer> utilizationMap = calculateUtilization(design, pblock);    // or DesignTools.calculateUtilization(design, pblock);
            System.out.println("PBlock: " + pblockName + "; Utilization: " + utilizationMap);
            // break;
        }
        return;
    }

    public static void main(String[] args) {
        if(args.length != 1) {
            System.out.println("USAGE: rapidwright MetricsExtractor <.dcp>");
            return;
        }
        getAllPBlockPlacementMetrics(args[0]);
    }
}
