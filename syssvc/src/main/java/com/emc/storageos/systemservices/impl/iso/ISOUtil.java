/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.iso;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Utility class for creating ISO image.
 */
public class ISOUtil {

    private static final byte SPACE = 32;
    private static final byte RESESRVED = 0;
    private static final byte ZERO = 48;

    public static void padWithReserved(ByteBuffer byteBuffer, int length) {
        for (int i = 0; i < length; i++) {
            byteBuffer.put(RESESRVED);
        }
    }

    public static void padWithZeros(ByteBuffer byteBuffer, int length) {
        for (int i = 0; i < length; i++) {
            byteBuffer.put(ZERO);
        }
    }

    public static void putIntLSBMSB(ByteBuffer byteBuffer, int value){
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).putInt(value);
        byteBuffer.order(ByteOrder.BIG_ENDIAN).putInt(value);
    }

    public static void putShortLSBMSB(ByteBuffer byteBuffer, short value){
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).putShort(value);
        byteBuffer.order(ByteOrder.BIG_ENDIAN).putShort(value);
    }

    public static void formatDate(ByteBuffer byteBuffer, Date date) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        // Parse date
        int year = cal.get(Calendar.YEAR) - 1900;
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int gmtOffset = cal.get(Calendar.ZONE_OFFSET) / (15 * 60 * 1000);
        // Create ISO9660 date
        byteBuffer.put((byte) year);
        byteBuffer.put((byte) month);
        byteBuffer.put((byte) day);
        byteBuffer.put((byte) hour);
        byteBuffer.put((byte) minute);
        byteBuffer.put((byte) second);
        if(gmtOffset > 0)
        {
            byteBuffer.put((byte) gmtOffset);
        }
        else {
            byteBuffer.put((byte) 0);
        }
    }

    public static void formatDateAsStr(ByteBuffer byteBuffer, Date date) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        // Parse date
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int hundredthSec = cal.get(Calendar.MILLISECOND) / 10;
        int gmtOffset = cal.get(Calendar.ZONE_OFFSET) / (15 * 60 * 1000);
        // Create ISO9660 date
        byteBuffer.put(padIntToString(year, 4).getBytes());
        byteBuffer.put(padIntToString(month, 2).getBytes());
        byteBuffer.put(padIntToString(day, 2).getBytes());
        byteBuffer.put(padIntToString(hour, 2).getBytes());
        byteBuffer.put(padIntToString(minute, 2).getBytes());
        byteBuffer.put(padIntToString(second, 2).getBytes());
        byteBuffer.put(padIntToString(hundredthSec, 2).getBytes());
        if(gmtOffset > 0){
            byteBuffer.put((byte) gmtOffset);
        }
        else{
            byteBuffer.put((byte) 0);
        }
    }

    private static String padIntToString(int value, int length) {
        String intValue = "" + value;
        StringBuffer buf = new StringBuffer(intValue);
        while (buf.length() < length) {
            buf.insert(0, "0");
        }
        return buf.toString();
    }

    public static void padWithSpaces(ByteBuffer byteBuffer, int length){
        for (int i = 0; i < length; i++) {
            byteBuffer.put(SPACE);
        }
    }
}
