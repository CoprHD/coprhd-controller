/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model.mapper;

import com.emc.storageos.db.client.model.PropertyListDataObject;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.CapacityResourceType;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.net.URI;
import java.util.Calendar;

public class PropertyListDataObjectMapper {

    public static <T> T map(PropertyListDataObject from, Class<T> clazz) {
        if (from == null) {
            return null;
        }

        T to;
        try {
            to = clazz.newInstance();

            BeanInfo bInfo = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();

            for (int i = 0; i < pds.length; i++) {
                PropertyDescriptor pd = pds[i];
                // skip class property
                String pdName = pd.getName();
                if (pd.getName().equals("class") )  {
                    continue;
                }
                if( pdName.equals("id")) {
                    if(URI.class.isAssignableFrom(pd.getPropertyType())){
                        URI value = from.getId();
                        pd.getWriteMethod().invoke(to,value);
                    }
                }
                else if( pdName.equals("creationTime") )  {
                    if(Calendar.class.isAssignableFrom(pd.getPropertyType())){
                        Calendar value = from.getCreationTime();
                        pd.getWriteMethod().invoke(to,value);
                    }
                }
                else {
                    String strValue = from.getResourceData().get(pdName);
                    Object value = valueFromString(strValue,pd);
                    pd.getWriteMethod().invoke(to,value);
                }
            }
            return to;
        }
        catch ( Exception ex ) {
            throw DatabaseException.fatals.deserializationFailed(clazz, ex);
        }
    }


    public static <T> PropertyListDataObject map(T from, String type) {
        if (from == null) {
            return null;
        }

        PropertyListDataObject to = new PropertyListDataObject();
        to.setResourceType(type);
        try {
            BeanInfo bInfo = Introspector.getBeanInfo(from.getClass());
            PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();

            for (int i = 0; i < pds.length; i++) {
                PropertyDescriptor pd = pds[i];
                // skip class property
                String pdName = pd.getName();
                if (pd.getName().equals("class") )  {
                    continue;
                }
                if( pdName.equals("Id")) {
                    if(URI.class.isAssignableFrom(pd.getPropertyType())){
                        URI value = (URI)pd.getReadMethod().invoke(from);
                        to.setId(value);
                    }
                }
                if( pdName.equals("creationTime")) {
                    if(Calendar.class.isAssignableFrom(pd.getPropertyType())){
                        Calendar value = (Calendar)pd.getReadMethod().invoke(from);
                        to.setCreationTime(value);
                    }
                }
                else {
                    Object value = pd.getReadMethod().invoke(from);
                    to.getResourceData().put(pdName,value.toString());
                }
            }
            return to;
        }
        catch ( Exception ex ) {
            throw DatabaseException.fatals.deserializationFailed(from.getClass(), ex);
        }
    }

    private static Object valueFromString(String strValue, PropertyDescriptor pd) throws Exception {
        Object obj = null;
        if( Integer.TYPE == pd.getPropertyType() ) {
           obj = Integer.valueOf(strValue);
        }
        if( Long.TYPE == pd.getPropertyType() ) {
            obj = Long.valueOf(strValue);
        }
        else if( Double.TYPE == pd.getPropertyType() ) {
            obj = Double.valueOf(strValue);
        }
        else if( Float.TYPE == pd.getPropertyType() ) {
            obj = Float.valueOf(strValue);
        }
        else if( Short.TYPE == pd.getPropertyType() ) {
            obj = Short.valueOf(strValue);
        }
        else if(  String.class  == pd.getPropertyType() ) {
            obj = strValue;
        }
        else if( CapacityResourceType.class == pd.getPropertyType() )  {
             obj = CapacityResourceType.valueOf(strValue);
        }
        else {
            throw new Exception("Failed to convert from string the field of type : " + pd.getPropertyType().getSimpleName());
        }
        return  obj;
    }

}
