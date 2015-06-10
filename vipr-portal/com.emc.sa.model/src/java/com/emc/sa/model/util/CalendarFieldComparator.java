/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Calendar;
import java.util.Comparator;

/**
 * Comparator that compares specific calendar fields.
 * 
 * @author jonnymiller
 */
public class CalendarFieldComparator implements Comparator<Calendar> {
    private final int[] fields;

    public CalendarFieldComparator(int... fields) {
        this.fields = fields;
    }

    @Override
    public int compare(Calendar a, Calendar b) {
        int result = 0;
        for (int i = 0; i < fields.length; i++) {
            if (result != 0) {
                break;
            }
            result = a.get(fields[i]) - b.get(fields[i]);
        }
        return result;
    }
}
