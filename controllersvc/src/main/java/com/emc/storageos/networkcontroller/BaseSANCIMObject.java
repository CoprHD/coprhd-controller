/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller;

import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseSANCIMObject {

    private static final Logger _log = LoggerFactory.getLogger(BaseSANCIMObject.class);

    protected String cimStringProperty(CIMInstance ins, String field) {
        CIMProperty prop = ins.getProperty(field);
        if (prop == null)
            return null;
        Object obj = prop.getValue();
        if (obj == null)
            return null;
        return obj.toString();
    }

    protected Boolean cimBooleanProperty(CIMInstance ins, String field) {
        CIMProperty prop = ins.getProperty(field);
        if (prop == null)
            return null;
        if (prop.getDataType() != CIMDataType.BOOLEAN_T)
            return null;
        return (Boolean) prop.getValue();
    }

    protected Integer cimIntegerProperty(CIMInstance ins, String field) {
        CIMProperty prop = ins.getProperty(field);
        if (prop == null)
            return null;
        return new Integer(prop.getValue().toString());
    }

    protected void printAllAssociatedClasses(WBEMClient client, CIMObjectPath path) {
        CloseableIterator<CIMInstance> zns = null;
        try {
            zns = client.associatorInstances(path,
                    null, null, null, null, false, null);
            while (zns.hasNext()) {
                // CIMClass cl = zns.next();
                // System.out.println("class: " + cl.getName());
                CIMInstance ins = zns.next();
                _log.info(ins.toString());

            }
        } catch (WBEMException ex) {
            _log.error("Exception: " + ex.getLocalizedMessage());
        } finally {
            if (zns != null) {
                zns.close();
            }
        }
    }

    protected String formatWWN(String wwn) {
        char[] chars = wwn.toUpperCase().toCharArray();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < chars.length;) {
            buf.append(chars[i++]);
            if (i < chars.length && (i % 2) == 0)
                buf.append(":");
        }
        return buf.toString();
    }

    // private void printPropertiesInInstance(CIMInstance ins) {
    // String name = cimStringProperty(ins, "Name");
    // CIMProperty[] topprops = ins.getProperties();
    // for (CIMProperty p : topprops) {
    // String key = p.getName();
    // String value = p.getValue() != null ? p.getValue().toString() : "<null>";
    // System.out.println(name + ": key: " + key + " value: " + value);
    // }
    // System.out.println("*********************************");
    // }

    // private String getCimProperty(CIMInstance ins, String propName) {
    // CIMProperty prop = ins.getProperty(propName);
    // if (prop == null) return "";
    // Object value = prop.getValue();
    // if (value == null) return "";
    // return value.toString();
    // }

}
