/*
# Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.
# All rights reserved. The contributor(s) of this file has/have agreed to the
# RapidStream Contributor License Agreement.
*/

package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.design.blocks.PBlockRange;
import com.xilinx.rapidwright.design.ConstraintGroup;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.IntentCode;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Tile;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Map;

public class CrossingPBlockNodeCounter {
    private static Set<IntentCode> intentCodesConsidered = new HashSet<>(
        Arrays.asList(                      // color_index
            IntentCode.INTENT_DEFAULT,      // 1
            IntentCode.NODE_HLONG,          // 2
            IntentCode.NODE_VLONG,          // 3
            IntentCode.NODE_SINGLE,         // 4
            IntentCode.NODE_DOUBLE,         // 5
            IntentCode.NODE_HQUAD,          // 6
            IntentCode.NODE_VQUAD,          // 7
            IntentCode.NODE_LOCAL,          // 8
            IntentCode.NODE_PINBOUNCE,      // 9
            IntentCode.NODE_LAGUNA_DATA,    // 10

            IntentCode.NODE_GLOBAL_VDISTR,  // ignored
            IntentCode.NODE_GLOBAL_VROUTE,  // ignored
            IntentCode.NODE_GLOBAL_HDISTR,  // ignored
            IntentCode.NODE_GLOBAL_HROUTE   // ignored
        )
    );
    private static HashMap<Integer, HashMap<Integer, HashMap<String, Integer>>> allPBlockDirectionToNodeCount = new HashMap<>();
    // Check whether a node has a IntentCode of our interest
    // Exclude some IntentCodes including clock and control signals
    private static boolean validNode(Node n) {
        assert intentCodesConsidered.contains(n.getIntentCode()) : "Unexpected IntentCode: " + n.getIntentCode();
        // Exclude IntentCode.NODE_GLOBAL_VROUTE and IntentCode.NODE_GLOBAL_VDISTR
        if (n.getIntentCode().toString().startsWith("NODE_GLOBAL")) {
            assert n.getWireName().startsWith("CLK") : "This GLOBAL node is not a clock node: " + n;
            return false;
        }
        // Filter out IntentCode.INTENT_DEFAULT Nodes
        // ? referring to https://docs.amd.com/v/u/en-US/ug573-ultrascale-memory-resources
        if (n.getIntentCode() == IntentCode.INTENT_DEFAULT) {
            // Exclude CLKOUT_{NORTH\d+|SOUTH\d+} and cascade i/o of BRAMs
            assert n.getWireName().matches("^(CLKOUT|BRAM|HPIO|GND|VCC).*$") : "Unexpected INTENT_DEFAULT node: " + n;
            return false;
        }
        // DSP cascade not extracted by getIntersectingNodes
        // ? referring to https://docs.amd.com/v/u/en-US/ug579-ultrascale-dsp
        assert !n.getWireName().contains("DSP_") : "Unexpected DSP-related node: " + n;
        return true;
    }

    // Check whether a node in Set<Tile> a also exists in Set<Tile> b
    // then, check whether the node has a valid IntentCode of our interest
    // if both satisfied, add the node to the result set
    private static Set<Node> getIntersectingNodes(Set<Tile> a, Set<Tile> b) {
        Set<Node> nodesInA = getNodesInTiles(a);
        Set<Node> nodesInB = getNodesInTiles(b);

        Set<Node> both = new HashSet<>();
        for (Node n : nodesInA) {
            if (nodesInB.contains(n) && validNode(n)) {
                both.add(n);

                // For debugging
                // System.out.println(n + " " + n.getIntentCode()); // + " " + a + " " + b);

                // For highlight_objects in Vivado
                // try {
                //     PrintWriter writer = new PrintWriter(new FileWriter("node_stats.txt", true));
                //     writer.println(n + " " + n.getIntentCode());// + " " + a + " " + b);
                //     writer.close();
                // } catch (IOException e) {
                //     System.out.println("Error: " + e.getMessage());
                //     e.printStackTrace();
                // }
            }
        }
        return both;
    }

    private static String getPBlockNameFromColRow(Device device, int col, int row, int total_col, int total_row) {
        int width = device.getNumOfClockRegionsColumns() / total_col;
        int height = device.getNumOfClockRegionRows() / total_row;
        return "X" + col * width + "Y" + row * height + "X" + ((col + 1) * width - 1) + "Y" + ((row + 1) * height - 1);
    }

    private static String getPBlockNameFromDirection(Device device, int col, int row, int total_col, int total_row, String direction) {
        int width = device.getNumOfClockRegionsColumns() / total_col;
        int height = device.getNumOfClockRegionRows() / total_row;
        String pBlockName = "";
        switch (direction) {
            case "N":
                pBlockName = "X" + col * width + "Y" + ((row + 1) * height) + "X" + ((col + 1) * width - 1) + "Y" + ((row + 2) * height - 1);
                break;
            case "S":
                pBlockName = "X" + col * width + "Y" + ((row - 1) * height) + "X" + ((col + 1) * width - 1) + "Y" + ((row * height - 1));
                break;
            case "E":
                pBlockName = "X" + ((col + 1) * width) + "Y" + row * height + "X" + ((col + 2) * width - 1) + "Y" + ((row + 1) * height - 1);
                break;
            case "W":
                pBlockName = "X" + ((col - 1) * width) + "Y" + row * height + "X" + ((col * width - 1)) + "Y" + ((row + 1) * height - 1);
                break;
            default:
                assert false : "Invalid direction: " + direction;
        }
        return pBlockName;
    }

    // Get the number of nodes crossing the boundary between the current pblock and the adjacent pblock(s)
    // in at most 4 directions (N, S, E, W)
    private static HashMap<String, Integer> getSinglePBlockDirectionToNodeCount(Device device, Map<String, PBlock> pblockMap, int col, int row, int total_col, int total_row) {
        // The current pblock (pCenter).
        String pCenterName = getPBlockNameFromColRow(device, col, row, total_col, total_row);
        assert pblockMap.containsKey(pCenterName) : "pCenterName " + pCenterName + " not found in pblockMap!";
        PBlock pCenter = pblockMap.get(pCenterName);
        // The direction (N, S, E, W) to the number of nodes crossing the boundary between the current pblock and the adjacent pblock(s).
        HashMap<String, Integer> directionToNodeCount = new HashMap<String, Integer>();

        // North
        if (row < total_row - 1) {
            // PBlock pNorth = new PBlock(device, "CLOCKREGION_X" + col + "Y" + (row + 1) + ":CLOCKREGION_X" + col + "Y" + (row + 1));
            String pNorthName = getPBlockNameFromDirection(device, col, row, total_col, total_row, "N");
            assert pblockMap.containsKey(pNorthName) : "pNorthName " + pNorthName + " not found in pblockMap!";
            PBlock pNorth = pblockMap.get(pNorthName);
            Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pNorth.getAllTiles());
            int intersectingNodesCount = intersectingNodes.size();
            directionToNodeCount.put("N", intersectingNodesCount);
        }
        // South
        if (row > 0) {
            if (allPBlockDirectionToNodeCount.containsKey(col) && allPBlockDirectionToNodeCount.get(col).containsKey(row - 1)) {
                directionToNodeCount.put("S", allPBlockDirectionToNodeCount.get(col).get(row - 1).get("N"));
            } else {
                System.err.println("South not found: col " + col + "; row " + row);
                String pSouthName = getPBlockNameFromDirection(device, col, row, total_col, total_row, "S");
                assert pblockMap.containsKey(pSouthName) : "pSouthName " + pSouthName + " not found in pblockMap!";
                PBlock pSouth = pblockMap.get(pSouthName);
                Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pSouth.getAllTiles());
                int intersectingNodesCount = intersectingNodes.size();
                directionToNodeCount.put("S", intersectingNodesCount);
            }
        }
        // East
        if (col < total_col - 1) {
            String pEastName = getPBlockNameFromDirection(device, col, row, total_col, total_row, "E");
            assert pblockMap.containsKey(pEastName) : "pEastName " + pEastName + " not found in pblockMap!";
            PBlock pEast = pblockMap.get(pEastName);
            Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pEast.getAllTiles());
            int intersectingNodesCount = intersectingNodes.size();
            directionToNodeCount.put("E", intersectingNodesCount);
        }
        // West
        if (col > 0) {
            if (allPBlockDirectionToNodeCount.containsKey(col - 1) && allPBlockDirectionToNodeCount.get(col - 1).containsKey(row)) {
                directionToNodeCount.put("W", allPBlockDirectionToNodeCount.get(col - 1).get(row).get("E"));
            } else {
                System.err.println("South not found: col " + col + "; row " + row);
                String pWestName = getPBlockNameFromDirection(device, col, row, total_col, total_row, "W");
                assert pblockMap.containsKey(pWestName) : "pWestName " + pWestName + " not found in pblockMap!";
                PBlock pWest = pblockMap.get(pWestName);
                Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pWest.getAllTiles());
                int intersectingNodesCount = intersectingNodes.size();
                directionToNodeCount.put("W", intersectingNodesCount);
            }
        }
        return directionToNodeCount;
    }

    // Get a set of nodes in a set of tiles
    private static Set<Node> getNodesInTiles(Set<Tile> tiles) {
        Set<Node> nodes = new HashSet<>();
        for (Tile t : tiles) {
            for (PIP p : t.getPIPs()) {
                Node start = p.getStartNode();
                Node end = p.getEndNode();
                nodes.add(start);
                nodes.add(end);
            }
        }
        nodes.remove(null);
        return nodes;
    }

    private static ArrayList<Integer> getXYsFromPBlockName(String pblockName) {
        String pattern = "^.*X(\\d+)Y(\\d+).*X(\\d+)Y(\\d+).*$";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(pblockName);
        assert m.find() : "pblockName " + pblockName + " does not match the pattern '^.*X(\\d+)Y(\\d+).*X(\\d+)Y(\\d+).*$'!";
        ArrayList<Integer> xyList = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            xyList.add(Integer.parseInt(m.group(i)));
        }
        System.out.println(pblockName + " " + xyList);
        return xyList;
    }

    private static String refinePBlockName(String pblockName) {
        String pattern = "^.*X(\\d+)Y(\\d+).*X(\\d+)Y(\\d+).*$";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(pblockName);
        assert m.find() : "pblockName " + pblockName + " does not match the pattern '^.*X(\\d+)Y(\\d+).*X(\\d+)Y(\\d+).*$'!";
        ArrayList<Integer> xyList = new ArrayList<>();
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
                            if (pblockRange.matches("^(RAMB|DSP|URAM).*$")) // BRAM/DSP/URAM cascade not relavant
                                continue;
                            pblock.add(new PBlockRange(device, pblockRange));
                        } else if (part.contains("-remove")) {
                            // TODO: "resize_pblock [...] -remove {...}" not considered yet
                            // System.out.println("WARNING: Not considered yet!");
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

    public static HashMap<Integer, HashMap<Integer, HashMap<String, Integer>>> getAllPBlockCrossingNodeCount(String dcp, int total_col, int total_row) {
        Design design = null;
        Device device = null;
        try {
            design = Design.readCheckpoint(dcp);
            device = design.getDevice();
            // Assume the atomic slot is at least clock-region-level, then they should not exceed CR#
            assert total_col >= 0 && total_col < device.getNumOfClockRegionsColumns() : "Invalid column number";
            assert total_row >= 0 && total_row < device.getNumOfClockRegionRows() : "Invalid row number";
            System.out.println("Device: " + device + "; total_col(X): " + total_col + "; total_row(Y): " + total_row);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        assert design != null : "Design is null";
        assert device != null : "Device is null";
        Map<String, PBlock> pblockMap = getNameToPBlocksFromXDC(design);
        // System.out.println("  pMap: " + pblockMap + "\n");
        // example: "{X0Y12X3Y15=X0Y12:X3Y15, X4Y8X7Y11=SLICE_X144Y540:SLICE_X145Y719 X5Y9:X6Y11 X4Y8:X6Y8}"

        // Create the result map:
        System.out.println("Extracting node counts for pblocks in different directions:");
        for (int c = 0; c < total_col; c++) {
            for (int r = 0; r < total_row; r++) {
                HashMap<String, Integer> directionToNodeCount = getSinglePBlockDirectionToNodeCount(device, pblockMap, c, r, total_col, total_row);
                HashMap<Integer, HashMap<String, Integer>> rowToDirectionToNodeCount = allPBlockDirectionToNodeCount.getOrDefault(c, new HashMap<>());
                rowToDirectionToNodeCount.put(r, directionToNodeCount);
                allPBlockDirectionToNodeCount.put(c, rowToDirectionToNodeCount);

                String pCenterName = getPBlockNameFromColRow(device, c, r, total_col, total_row);
                System.out.println("  PBlock " + pCenterName + "\tdone");
            }
        }
        return allPBlockDirectionToNodeCount;
    }

    public static void main(String[] args) {
        if(args.length != 3) {
            System.out.println("USAGE: rapidwright MetricsExtractor <.dcp> <col (X)> <row (Y)>");
            return;
        }
        HashMap<Integer, HashMap<Integer, HashMap<String, Integer>>> allPBlockCrossingNodeCount = getAllPBlockCrossingNodeCount(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        System.out.println(allPBlockCrossingNodeCount);
    }
}
