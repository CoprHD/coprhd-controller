/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
