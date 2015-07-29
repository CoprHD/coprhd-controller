/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command.parse;

public class NullMultiFieldFormatter implements MultiFieldFormatter {

    @Override
    public Object format(Object... sources) {
        String output = "";
        for (Object s : sources) {
            output += s.toString();
        }
        return output;
    }

}