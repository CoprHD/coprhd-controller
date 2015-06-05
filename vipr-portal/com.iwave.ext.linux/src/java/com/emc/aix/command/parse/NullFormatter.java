/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.aix.command.parse;

public class NullFormatter implements FieldFormatter {

    @Override
    public Object format(Object source) {
        return source;
    }

}
