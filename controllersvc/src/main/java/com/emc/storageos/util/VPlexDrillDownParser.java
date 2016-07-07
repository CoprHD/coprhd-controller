package com.emc.storageos.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements a recursive descent parser for the drill-down text
 * Typical examples:
 * virtual-volume: dd_V01758738113-5-47ec7c4fa123_V11498275522-a-6b81e8590ae3_vol (cluster-1)
   distributed-device: dd_V01758738113-5-47ec7c4fa123_V11498275522-a-6b81e8590ae3
      distributed-device-component: device_V01758738113-5-47ec7c4fa123 (cluster-1)
         extent: extent_V01758738113-5-47ec7c4fa123_1
            storage-volume: V01758738113-5-47ec7c4fa123 (blocks: 0 - 524287)
      distributed-device-component: device_V11498275522-a-6b81e8590ae3 (cluster-2)
         extent: extent_V11498275522-a-6b81e8590ae3_1
            storage-volume: V11498275522-a-6b81e8590ae3 (blocks: 0 - 524287)
 * virtual-volume: device_VAPM00144755987-c50c5200cd2_vol (cluster-1)
   local-device: device_VAPM00144755987-c50c5200cd2 (cluster-1)
      extent: extent_VAPM00144755987-c50c5200cd2_1
         storage-volume: VAPM00144755987-c50c5200cd2 (blocks: 0 - 262399)
 * virtual-volume: device_VAPM00140801303-00218_vol (cluster-1)
   local-device: device_VAPM00140801303-00218 (cluster-1)
      local-device-component: device_VAPM00140801303-002182016Jul07_163513
         extent: extent_VAPM00140801303-00218_1
            storage-volume: VAPM00140801303-00218 (blocks: 0 - 262143)
      local-device-component: device_VAPM00140844981-00512
         extent: extent_VAPM00140844981-00512_1
            storage-volume: VAPM00140844981-00512 (blocks: 0 - 262143)

 */
public class VPlexDrillDownParser {
    
    public enum NodeType {
        VIRT("virtual-volume"),
        DIST("distributed-device:"),
        DISTCOMP("distributed-device-component:"),
        LOCAL("local-device:"),
        LOCALCOMP("local-device-component"),
        EXT("extent:"),
        SVOL("storage-volume:");
        
        // The first part of the line matches "match"
        private String match; 
        NodeType(String match) {
            this.match = match;
        }
        public String getMatch() {
            return match;
        }
    };
    public class Node {
        NodeType type;
        String arg1;
        String arg2;
        List<Node> children = new ArrayList<Node>();
        Node(NodeType type, String [] lineArgs) {
            this.type = type;
            if (lineArgs.length > 1) {
                setArg1(lineArgs[1]);
            }
            if (lineArgs.length > 2) {
                setArg2(lineArgs[2]);
            }
        }
        public String getArg1() {
            return arg1;
        }
        public void setArg1(String arg1) {
            this.arg1 = new String(arg1);
        }
        public String getArg2() {
            return arg2;
        }
        public void setArg2(String arg2) {
            this.arg2 = new String(arg2);
            if (this.arg2.startsWith("(") && this.arg2.endsWith(")")) {
                this.arg2 = this.arg2.substring(1, arg2.length()-1);
            }
        }
        public NodeType getType() {
            return type;
        }
        public List<Node> getChildren() {
            return children;
        }
        
        /** Returns all the nodes of a given type in the current subtree.
         * @param type NodeType to be matched
         * @return list of Nodes matching the requested type
         */
        public List<Node> getNodesOfType(NodeType type) {
            List<Node> matchingNodes = new ArrayList<Node>();
            getNodesOfType(matchingNodes, type);
            return matchingNodes;
        }

        void getNodesOfType(List<Node> matchingNodes, NodeType type) {
            if (this.getType() == type) {
                matchingNodes.add(this);
            }
            for (Node child : getChildren()) {
                child.getNodesOfType(matchingNodes, type);
            }
        }
    }
    

    int currentLine = 0;
    // drill-down text split up by lines
    String[] lines;
    // arguments within the current line
    String[] lineargs;
    public VPlexDrillDownParser(String drillDownString) {
        if (drillDownString == null) {
            return;
        }
        lines = drillDownString.split("\n");
        for (int i = 0; i < lines.length; i++) {
           lines[i] = lines[i].trim();
        }
    }
    
    private String next() {
        currentLine++;
        return line();
    }

    private String line() {
        if (currentLine < lines.length) {
            lineargs = lines[currentLine].split(" ");
            return lines[currentLine];
        }
        else return null;
    }
    
    public Node parse() {
        // We don't care about virtual volume if present, skip it
        if (line().startsWith(NodeType.VIRT.getMatch())) {
            currentLine++;
        }
        if (line().startsWith(NodeType.DIST.getMatch())) {
            return dist();
        } else if (line().startsWith(NodeType.LOCAL.getMatch())){
            return local();
        }
        return null;
    }
    
    /**
     * Parses a distributed node. arg1 is the device name, arg2 is the cluster.
     * @return Node of type DIST; line pointer at next line
     */
    private Node dist() {
        Node node = new Node(NodeType.DIST, lineargs);
        next();
        while(line() != null && line().startsWith(NodeType.DISTCOMP.getMatch())) {
            node.getChildren().add(distcomp());
        }
        return node;   
    }
    
    /**
     * Parses a distributed component. Used only for telling which cluster the
     * subtree will be for. arg1 is device name, arg2 is cluster
     * @return Node of type DISTCOMP; currentLine at next line
     */
    private Node distcomp() {
        Node node = new Node(NodeType.DISTCOMP, lineargs);
        while (next() != null && line().startsWith(NodeType.EXT.getMatch())) {
            node.getChildren().add(extent());
        }
        return node;
    }
    
    /**
     * Parses a local node. arg1 is device name, arg2 is cluster.
     * @return Node of type LOCAL; currentLine at next line
     */
    private Node local() {
        Node node = new Node(NodeType.LOCAL, lineargs);
        while (next() != null && 
            (line().startsWith(NodeType.LOCALCOMP.getMatch()) || line().startsWith(NodeType.EXT.getMatch()) ) ) {
            if (line().startsWith(NodeType.LOCALCOMP.getMatch())) {
                // We ignore local device components for now as they have no additional information
                next();
            }
            node.getChildren().add(extent());
        }
        return node;
    }
    
    /**
     * We don't care to receive EXTENT nodes.. pass through the underling SVOLS.
     * @return Node of type SVOL, or null; currentLine at returned node
     */
    private Node extent() {
        if (next() != null && line().startsWith(NodeType.SVOL.getMatch())) {
            return svol();
        }
        return null;
    }
    
    /**
     * Parses StorageVolume node. arg1 is volume name, arg2 is cluster.
     * @return Node of type SVOL; currentLine at returned node
     */
    private Node svol() {
        Node node = new Node(NodeType.SVOL, lineargs);
        return node;
    }

}
