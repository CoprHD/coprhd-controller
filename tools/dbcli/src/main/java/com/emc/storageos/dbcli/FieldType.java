/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.TimeZone;

import com.emc.storageos.dbcli.wrapper.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.w3c.dom.Node;

public class FieldType {
    private static final Logger log = LoggerFactory.getLogger(FieldType.class);

    private static final boolean DEBUG = false;

    public static Calendar toCalendar(String str) {
        Calendar calendar = Calendar.getInstance();
        try {
            String[] calendarStrs = str.split(",");
            String timeZoneStr = null;
            String timeInMillisStr = null;
            for (String s : calendarStrs) {
                if (s.matches(".*id=\"[a-zA-Z]+[/]*[a-zA-Z]*\"")) {   // match time zone
                    if (DEBUG) {
                        System.out.println(s.split("=")[2]);
                    }
                    timeZoneStr = s.split("=")[2];
                }
                if (s.matches(".*time=.*")) {    // match time in millions
                    if (DEBUG) {
                        System.out.println(s.split("=")[1]);
                    }
                    timeInMillisStr = s.split("=")[1];
                }
            }
            calendar.setTimeZone(TimeZone.getTimeZone(timeZoneStr));
            calendar.setTimeInMillis(Long.parseLong(timeInMillisStr));
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Exception: ", e);
            return null;
        }
        return calendar;
    }

    public static Integer toInteger(String str) {
        Integer intNum = null;
        try {
            intNum = Integer.valueOf(str);
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Exception: ", e);
            return null;
        }
        return intNum;
    }

    public static Long toLong(String str) {
        Long longNum = null;
        try {
            longNum = new Long(str);
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Exception in: ", e);
            return null;
        }
        return longNum;
    }

    public static Boolean toBoolean(String str) {
        Boolean boolVal = null;
        if (str.equals("true") || str.equals("false")) {
            boolVal = new Boolean(str);
            return boolVal;
        }
        return null;
    }

    public static <V, T extends Wrapper<V>> void marshall(V value, Node node, Class clazz) {
        T wrapper = null;
        try {
            wrapper = (T) clazz.newInstance();
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Exception in: ", e);
        }
        wrapper.setValue(value);
        try {
            JAXBContext jc = JAXBContext.newInstance(clazz);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            marshaller.marshal(wrapper, node);
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Exception in: ", e);
        }
    }

    public static <V, T extends Wrapper<V>> V convertType(Node node, Class clazz) {
        try {
            JAXBContext jc = JAXBContext.newInstance(clazz);
            Unmarshaller unmarshaller = jc.createUnmarshaller();
            T object = (T) unmarshaller.unmarshal(node);
            return object.getValue();
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Exception in: ", e);
        }
        return null;
    }
}
