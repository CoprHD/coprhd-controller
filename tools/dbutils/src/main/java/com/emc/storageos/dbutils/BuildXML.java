/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbutils;

import java.lang.reflect.Field;

import com.emc.storageos.db.client.model.TimeSeriesSerializer;

/**
 * This Component will describe the properties of an object supplied
 */
public class BuildXML<T extends TimeSeriesSerializer.DataPoint> {
    public static long count = 0;
    public final static String TIMINMILLIS = "_timeInMillis";
    public final static String TIMEOCCURED = "_timeOccurred";

    @SuppressWarnings("rawtypes")
    public synchronized String writeAsXML(T object, String tag) {

        Class cls = object.getClass();
        Field fieldlist[] = cls.getDeclaredFields();
        StringBuilder xmlStr =
                new StringBuilder("\n\t<" + tag + " num='" + (++count) + "'>");

        if (fieldlist.length > 0) {
            for (int i = 0; i < fieldlist.length; i++) {
                try {
                    Field fld = fieldlist[i];
                    fld.setAccessible(true);

                    Object value = fld.get(object);
                    if (value != null && value.toString() != null && value.toString().length() > 0
                            && !value.toString().equalsIgnoreCase("null")) {
                        xmlStr.append("\n\t\t<").append(fld.getName()).append(">");
                        xmlStr.append(fld.get(object));
                        xmlStr.append("</").append(fld.getName()).append(">");
                    }
                    else
                    {
                        xmlStr.append("\n\t\t<").append(fld.getName()).append("/>");
                    }

                } catch (IllegalAccessException e) {
                    System.err.println("Problem inside BuildXML Comp" + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Problem inside BuildXML Comp" + e.getMessage());
                }
            }
        }

        xmlStr = buildTimeInMills(xmlStr, object.getTimeInMillis() + "");
        xmlStr.append("\n\t</" + tag + ">");
        xmlStr.append("\n");
        return xmlStr.toString();
    }

    private static StringBuilder buildTimeInMills(StringBuilder xmlStr, String time) {
        xmlStr.append("\n\t\t<").append(TIMINMILLIS).append(">").append(time).append("</").append(TIMINMILLIS).append(">");
        return xmlStr;
    }

    private static StringBuilder buildTimeOccured(StringBuilder xmlStr, String time) {
        xmlStr.append("\n\t\t<").append(TIMEOCCURED).append(">").append(time).append("</").append(TIMEOCCURED).append(">");
        return xmlStr;
    }
}
