/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.cim.UnsignedInteger32;
import javax.cim.UnsignedInteger64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating CIMProperty objects
 */
public class CIMPropertyFactory {
    private final static Logger _log = LoggerFactory.getLogger(CIMPropertyFactory.class);

    public CIMProperty<String> string(String name, String value) {
        CIMProperty<String> string;
        try {
            string = new CIMProperty<String>(name, CIMDataType.STRING_T, value, true, false, null);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return string;
    }

    public CIMArgument<String[]> stringArray(String name, String[] value) {
        CIMArgument<String[]> argument;
        try {
            argument = new CIMArgument<String[]>(name, CIMDataType.STRING_ARRAY_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMProperty<CIMObjectPath> reference(String name, CIMObjectPath value) {
        CIMProperty<CIMObjectPath> property;
        try {
            property = new CIMProperty<CIMObjectPath>(name, CIMDataType.getDataType(value), value, true, false, null);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return property;
    }

    public CIMProperty<UnsignedInteger16> uint16(String name, int value) {
        CIMProperty<UnsignedInteger16> argument;
        try {
            argument = new CIMProperty<UnsignedInteger16>(name, CIMDataType.UINT16_T,
                    new UnsignedInteger16(value));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<UnsignedInteger16[]> uint16Array(String name, UnsignedInteger16[] value) {
        CIMArgument<UnsignedInteger16[]> argument;
        try {
            argument = new CIMArgument<UnsignedInteger16[]>(name, CIMDataType.UINT16_ARRAY_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMProperty<UnsignedInteger32> uint32(String name, int value) {
        CIMProperty<UnsignedInteger32> argument;
        try {
            argument = new CIMProperty<UnsignedInteger32>(name, CIMDataType.UINT32_T,
                    new UnsignedInteger32(value));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMProperty<UnsignedInteger64> uint64(String name, String value) {
        CIMProperty<UnsignedInteger64> argument;
        try {
            argument = new CIMProperty<UnsignedInteger64>(name, CIMDataType.UINT64_T,
                    new UnsignedInteger64(value));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMProperty<UnsignedInteger64> uint64(String name, Long value) {
        CIMProperty<UnsignedInteger64> argument;
        try {
            argument = new CIMProperty<UnsignedInteger64>(name, CIMDataType.UINT64_T,
                    new UnsignedInteger64(value.toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMProperty<CIMObjectPath[]> referenceArray(String name, CIMObjectPath[] path) {
        CIMProperty<CIMObjectPath[]> argument;
        try {
            argument = new CIMProperty<CIMObjectPath[]>(name,
                    CIMDataType.getDataType(path), path);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMProperty<Boolean> bool(String name, Boolean value) {
        CIMProperty<Boolean> bool;
        try {
            bool = new CIMProperty<Boolean>(name, CIMDataType.BOOLEAN_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return bool;
    }

    public static String getPropertyValue(CIMInstance instance, String propertyName) {
        _log.debug("instance :{}", instance);
        _log.debug("propertyName :{}", propertyName);
        String propertyValue = SmisConstants.EMPTY_STRING;
        if (instance != null && propertyName != null) {
            CIMProperty property = instance.getProperty(propertyName);
            if (property != null) {
                Object value = property.getValue();
                if (value != null && value.toString() != null) {
                    propertyValue = value.toString();
                } else {
                    _log.warn(String.format(
                            "CIMInstance %s does not have a '%s' property or the property value is null",
                            instance.toString(), propertyName));
                }
            }
        }
        return propertyValue;
    }

    public static String[] getPropertyArray(CIMInstance instance, String propertyName) {
        _log.debug("instance :{}", instance);
        _log.debug("propertyName :{}", propertyName);
        String[] propertyValue = { SmisConstants.EMPTY_STRING };
        if (instance != null && propertyName != null) {
            CIMProperty property = instance.getProperty(propertyName);
            if (property != null) {
                Object value = property.getValue();
                if (value != null && value instanceof String[]) {
                    propertyValue = (String[]) value;
                } else {
                    _log.warn(String.format(
                            "CIMInstance %s does not have a '%s' property or the property value is null",
                            instance.toString(), propertyName));
                }
            }
        }
        return propertyValue;
    }
}
