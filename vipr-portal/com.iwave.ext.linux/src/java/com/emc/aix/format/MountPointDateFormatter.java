/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.format;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.emc.aix.command.parse.MultiFieldFormatter;

public final class MountPointDateFormatter implements MultiFieldFormatter {

    private static final String DATE_FORMAT = "MMMddhh:mm";

    private SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);

    @Override
    public Object format(Object... sources) {

        try {

            if (sources.length < 3) {
                throw new com.emc.aix.command.parse.ParseException("Date format requires three input fields in the format " + DATE_FORMAT);
            }

            String dateString = sources[0].toString() + sources[1].toString() + sources[2].toString();
            return formatter.parse(dateString);
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            throw new com.emc.aix.command.parse.ParseException(e);
        }
    }

}