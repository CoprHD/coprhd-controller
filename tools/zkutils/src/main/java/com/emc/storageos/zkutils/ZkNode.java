/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.zkutils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent a Node in zookeeper, 
 * only need to care the 2 member variables: path and children list.
 */
public class ZkNode {
    String path;
    List<ZkNode> children = new ArrayList<ZkNode>();

    public ZkNode(String path) {
        this.path = path;
    }

    public String getName() {
        if (path.equals("/"))
            return "/";

        String[] names = path.split("/");
        return names[names.length - 1];
    }

    /**
     * Input is child name, not full path
     */
    public void addChild(String child) {
        if (child == null) {
            return;
        }

        String childPath = path.endsWith("/") ? path + child : path + "/" + child;
        children.add(new ZkNode(childPath));
    }

    public boolean hasChild(String child) {
        boolean bHas = false;
        if (children == null)
            return bHas;

        for (ZkNode childNode : children) {
            if (childNode.getName().equals(child)) {
                bHas = true;
                break;
            }
        }

        return bHas;
    }

    public ZkNode getChild(String child) {
        ZkNode childNode = null;
        for (ZkNode tempNode : children) {
            if (tempNode.getName().equals(child)) {
                childNode = tempNode;
                break;
            }
        }
        return childNode;
    }
}
