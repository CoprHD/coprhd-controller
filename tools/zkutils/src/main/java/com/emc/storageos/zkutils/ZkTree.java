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

/**
 * Represent a tree, which usually be constructed from a list of node_path(String)
 */
public class ZkTree {
    ZkNode root;

    public ZkTree() {
        root = new ZkNode("/");
    }

    /**
     * Insert the node and its ancestors, if they are not there.
     */
    public void insert(String path) {
        String[] elements = path.split("/");

        ZkNode tempNode = root;
        for (String element : elements) {
            if (element == null || element.trim().length() == 0)
                continue;

            if (!tempNode.hasChild(element)) {
                tempNode.addChild(element);
            }
            tempNode = tempNode.getChild(element);
        }
    }

}
