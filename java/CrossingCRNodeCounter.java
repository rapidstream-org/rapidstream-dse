package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.IntentCode;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Tile;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class CrossingCRNodeCounter {
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
    public static Set<Node> getIntersectingNodes(Set<Tile> a, Set<Tile> b) {
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

    // Get the number of nodes crossing the boundary between the current pblock and the adjacent pblock(s)
    // in at most 4 directions (N, S, E, W)
    public static HashMap<String, Integer> getSinglePBlockDirectionToNodeCount(Device device, int col, int row) {
        // The current pblock (pCenter).
        PBlock pCenter = new PBlock(device, "CLOCKREGION_X" + col + "Y" + row + ":CLOCKREGION_X" + col + "Y" + row);
        // The direction (N, S, E, W) to the number of nodes crossing the boundary between the current pblock and the adjacent pblock(s).
        HashMap<String, Integer> directionToNodeCount = new HashMap<String, Integer>();

        // North
        if (row < device.getNumOfClockRegionRows() - 1) {
            PBlock pNorth = new PBlock(device, "CLOCKREGION_X" + col + "Y" + (row + 1) + ":CLOCKREGION_X" + col + "Y" + (row + 1));
            Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pNorth.getAllTiles());
            int intersectingNodesCount = intersectingNodes.size();
            directionToNodeCount.put("N", intersectingNodesCount);
        }
        // South
        if (row > 0) {
            if (allPBlockDirectionToNodeCount.containsKey(col) && allPBlockDirectionToNodeCount.get(col).containsKey(row - 1)) {
                directionToNodeCount.put("S", allPBlockDirectionToNodeCount.get(col).get(row - 1).get("N"));
            } else {
                assert false : "col " + col + "; row " + row;
                PBlock pSouth = new PBlock(device, "CLOCKREGION_X" + col + "Y" + (row - 1) + ":CLOCKREGION_X" + col + "Y" + (row - 1));
                Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pSouth.getAllTiles());
                int intersectingNodesCount = intersectingNodes.size();
                directionToNodeCount.put("S", intersectingNodesCount);
            }
        }
        // East
        if (col < device.getNumOfClockRegionsColumns() - 1) {
            PBlock pEast = new PBlock(device, "CLOCKREGION_X" + (col + 1) + "Y" + row + ":CLOCKREGION_X" + (col + 1) + "Y" + row);
            Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pEast.getAllTiles());
            int intersectingNodesCount = intersectingNodes.size();
            directionToNodeCount.put("E", intersectingNodesCount);
        }
        // West
        if (col > 0) {
            if (allPBlockDirectionToNodeCount.containsKey(col - 1) && allPBlockDirectionToNodeCount.get(col - 1).containsKey(row)) {
                directionToNodeCount.put("W", allPBlockDirectionToNodeCount.get(col - 1).get(row).get("E"));
            } else {
                assert false : "col " + col + "; row " + row;
                PBlock pWest = new PBlock(device, "CLOCKREGION_X" + (col - 1) + "Y" + row + ":CLOCKREGION_X" + (col - 1) + "Y" + row);
                Set<Node> intersectingNodes = getIntersectingNodes(pCenter.getAllTiles(), pWest.getAllTiles());
                int intersectingNodesCount = intersectingNodes.size();
                directionToNodeCount.put("W", intersectingNodesCount);
            }
        }
        return directionToNodeCount;
    }
    
    // Get a set of nodes in a set of tiles
    public static Set<Node> getNodesInTiles(Set<Tile> tiles) {
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

    public static HashMap<String, Integer> getSinglePBlockCrossingCRNodeCount(String dcp, int col, int row) {
        Design design = null;
        Device device = null;
        try {
            design = Design.readCheckpoint(dcp);
            device = design.getDevice();    // TODO: directly get device bypassing design?
            if (row < 0 || row >= device.getNumOfClockRegionRows() || col < 0 || col >= device.getNumOfClockRegionsColumns()) {
                System.out.println("Invalid row or column number");
                return null;
            }
            else {
                System.out.println("Device: " + device + "; col(X): " + col + "/" + device.getNumOfClockRegionsColumns() + "; row(Y): " + row + "/" + device.getNumOfClockRegionRows());
                // e.g., xcu250, col(X): 4/8, row(Y): 3/16
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        assert design != null : "Design is null";
        assert device != null : "Device is null";

        // Create the result map: 
        HashMap<String, Integer> directionToNodeCount = getSinglePBlockDirectionToNodeCount(device, col, row);
        return directionToNodeCount;
    }

    public static HashMap<Integer, HashMap<Integer, HashMap<String, Integer>>> getAllPBlockCrossingCRNodeCount(String dcp) {
        Design design = null;
        Device device = null;
        try {
            design = Design.readCheckpoint(dcp);
            device = design.getDevice();    // TODO: directly get device bypassing design?
            System.out.println("Device: " + device + "; col(X): " + device.getNumOfClockRegionsColumns() + "; row(Y): " + device.getNumOfClockRegionRows());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        assert design != null : "Design is null";
        assert device != null : "Device is null";
        
        // Create the result map: 
        for (int col = 0; col < device.getNumOfClockRegionsColumns(); col++) {
            for (int row = 0; row < device.getNumOfClockRegionRows(); row++) {
                HashMap<String, Integer> directionToNodeCount = getSinglePBlockDirectionToNodeCount(device, col, row);
                HashMap<Integer, HashMap<String, Integer>> rowToDirectionToNodeCount = allPBlockDirectionToNodeCount.getOrDefault(col, new HashMap<>());
                rowToDirectionToNodeCount.put(row, directionToNodeCount);
                allPBlockDirectionToNodeCount.put(col, rowToDirectionToNodeCount);
                System.out.println("CLOCKREGION: X " + col + "/" + device.getNumOfClockRegionsColumns() + "; Y " + row + "/" + device.getNumOfClockRegionRows() + " done");
            }
        }
        return allPBlockDirectionToNodeCount;
    }

    public static void main(String[] args) {
        if(args.length != 3) {
            System.out.println("USAGE: rapidwright MetricsExtractor <.dcp> <col (X)> <row (Y)>");
            return;
        }
        Design design = null;
        Device device = null;
        int col = -1, row = -1;
        try {
            design = Design.readCheckpoint(args[0]);
            col = Integer.parseInt(args[1]);
            row = Integer.parseInt(args[2]);
            device = design.getDevice();    // TODO: directly get device bypassing design?
            if (row < 0 || row >= device.getNumOfClockRegionRows() || col < 0 || col >= device.getNumOfClockRegionsColumns()) {
                System.out.println("Invalid row or column number");
                return;
            }
            else {
                System.out.println("Device: " + device + "; col(X): " + col + "/" + device.getNumOfClockRegionsColumns() + "; row(Y): " + row + "/" + device.getNumOfClockRegionRows());
                // e.g., xcu250, col(X): 4/8, row(Y): 3/16
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        assert design != null : "Design is null";
        assert device != null : "Device is null";

        // Create the result map: 
        HashMap<String, Integer> directionToNodeCount = getSinglePBlockDirectionToNodeCount(device, col, row);
        // System.out.println("CLOCKREGION_X" + col + "Y" + row + ":CLOCKREGION_X" + col + "Y" + row);
        System.out.println(directionToNodeCount);
    }
}