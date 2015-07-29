/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.test;

public class ExampleProtoBean {

    private static int count = 0;

    String text;

    public ExampleProtoBean() {

    }

    public ExampleProtoBean(String text) {
        this.text = text;
        count++;
        System.out.println("Number of created instances : " + count);
    }

    public String getText() {
        return this.text;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ExampleProtoBean [text=" + text + "]";
    }

}
