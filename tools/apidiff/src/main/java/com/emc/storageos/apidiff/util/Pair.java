/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff.util;

/**
 * General class to record a pair of records, it can be used to save old/new version of same kind of
 * data, or just prepare data as dual tuple.
 */
public class Pair<L, R> {
    private final L left;
    private final R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((left == null) ? 0 : left.hashCode());
        result = prime * result + ((right == null) ? 0 : right.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object pair) {
        if (pair == null)
            return false;
        if (this == pair)
            return true;
        if (this.getClass() != pair.getClass())
            return false;

        return this.left.equals(((Pair<?, ?>) pair).getLeft()) &&
                this.right.equals(((Pair<?, ?>) pair).getRight());
    }

    @Override
    public String toString() {
        return "(" + left + ", " + right + ")";
    }
}
