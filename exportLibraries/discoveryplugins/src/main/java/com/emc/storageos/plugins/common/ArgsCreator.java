/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger16;
import javax.cim.UnsignedInteger32;
import javax.cim.UnsignedInteger64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.plugins.common.domainmodel.Argument;

/**
 * Responsibility of this ArgsCreator is to provide methods to return the
 * argument value based on the arguments specified in Domain Logic. Say if
 * "getStringValue" is the method used in Argument1 Node of Domain Logic
 * <Argument1 Method="getStringValue" Instance="ArgsCreator" > then at runtime,
 * "getStringValue" will get called. Say if a new Argument1 needs a IntegerArray
 * as an value, then create a new method "getIntArrayValue" add own logic here
 * and in Domain Logic <Argument1 method="getIntArrayValue"
 * Instance="ArgsCreator" >
 * 
 */
public class ArgsCreator {
    protected Util _util;
    protected static final Logger _logger = LoggerFactory.getLogger(ArgsCreator.class);

    public ArgsCreator(Util util) {
        _util = util;
    }

    /**
     * get String Value. <Argument1 Name="" Method="getStringValue">
     * <Value>XYZ<Value> <Argument1>
     * 
     * @param arg
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * @param commandIndex
     *            : Index of the Generated Command Objects.
     * @return string object. Exceptions are thrown only on developer
     *         mistakes.Hence not having any.
     */
    public final Object getStringValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object returnobj = null;
        returnobj = arg.getValue();
        return returnobj;
    }

    /**
     * create CIMObjectPath based on Arguments.
     * 
     * @param arg
     * @param keyMap
     * @param index
     * @return Object CIMObjectPath.
     */
    public final Object createCIMPath(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        String[] items = arg.getValue().toString().split(":");
        CIMObjectPath path = CimObjectPathCreator.createInstance(items[1], items[0]);
        return path;
    }

    public final Object getIntValue32(final Argument arg, final Map<String, Object> keyMap, int index) {
        return getUnsignedInteger32(arg);
    }

    /**
     * empty Out Array to hold return values.
     * 
     * @param arg
     * @param keyMap
     * @param commandIndex
     * @return Object
     */
    public final Object getCIMArrayValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        CIMArgument<?>[] outputArgs = new CIMArgument<?>[5];
        return outputArgs;
    }

    /**
     * keyMap has a key "XYZ". This method uses key "XYZ" to return the value.
     * Value could be anything can be a java ref, CIM Object Path.. <Argument1
     * Name="" Method="getReferenceValue"> <Value>XYZ<Value> <Argument1>
     * 
     * @param argument
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * @param Index
     *            : Index of the Generated Command Objects.
     * @return string object.
     */
    public final Object getReferenceValue(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        Object referenceobj = null;
        final String objectpath = (String) argument.getValue();
        if (keyMap.containsKey(objectpath)) {
            if (keyMap.get(objectpath) instanceof List<?>) {
                @SuppressWarnings("unchecked")
                final List<Object> objList = (List<Object>) keyMap.get(objectpath);
                referenceobj = objList.get(index);
            } else {
                referenceobj = keyMap.get(objectpath);
            }
        } else {
            _logger.debug("Reference key  {} not present in Component Map", objectpath);
        }
        return referenceobj;
    }

    /**
     * get List Value. <Argument1 Name="" Method="getListValue">
     * <Value>XYZ<Value> <Argument1>
     * 
     * @param arg
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * @param index
     *            : Index of the Generated Command Objects.
     * @return list object. Exceptions are thrown only on developer
     *         mistakes.Hence not having any.
     */
    public final Object getListValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object listObj = null;
        String value = arg.getValue().toString();
        if (keyMap.containsKey(value)) {
            listObj = keyMap.get(value);
        }
        return listObj;
    }

    public final Object getReferenceValue1(String computerSystem) {
        return new Object();
    }

    /**
     * get Bool Value. <Argument1 Name="" Method="getBoolValue">
     * <Value>false<Value> <Argument1>
     * 
     * @param arg
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * 
     * @param commandIndex
     *            : Index of the Generated Command Objects.
     * @return string object.
     */
    public final Boolean getBoolValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        return Boolean.parseBoolean((String) arg.getValue());
    }

    /**
     * get StringArray Value. This method will get the comma seperated values
     * and gets converted into String Array. <Argument1 Name=""
     * Method="getStringArrayValue"> <Value>XYZ,ABC,DEF<Value> <Argument1>
     * 
     * @param arg
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * 
     * @param commandIndex
     *            : Index of the Generated Command Objects.
     * @return string object.
     */
    public final Object getStringArrayValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        String[] strArray = null;
        if (arg.getValue() != null) {
            String val = (String) arg.getValue();
            strArray = val.split(",");
        }
        return strArray;
    }

    /**
     * get Integer Value. This method will convert the String to integer.
     * <Argument1 Name="" Method="getIntegerValue"> <Value>2<Value> <Argument1>
     * 
     * @param arg
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * 
     * @param commandIndex
     *            : Index of the Generated Command Objects.
     * @return string object.
     */
    public final Object getIntegerValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        return Integer.parseInt((String) arg.getValue());
    }

    /**
     * get IntegerArray Value. This method will convert the comma seperated
     * Strings to integer Array. <Argument1 Name=""
     * Method="getIntegerArrayValue"> <Value>2,5,4<Value> <Argument1>
     * 
     * @param arg
     *            : Argument1 object.
     * @param keyMap
     *            : Common data structure used in plugin.
     * 
     * @param commandIndex
     *            : Index of the Generated Command Objects.
     * @return string object.
     */
    public final Object getIntegerArrayValue(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        String[] arr = null;
        if (arg.getValue() != null) {
            String val = (String) arg.getValue();
            arr = val.split(",");
        }
        int[] intarr = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            intarr[i] = Integer.parseInt(arr[i]);
        }
        return intarr;
    }

    /**
     * In Performance collection scenarios, we need to create the value of an
     * Argument1 to be a "Array of CIMArgument type Values ". Value of an
     * Argument1 : Array of [ CIMArg[String], CIMArg[Integer] ] ; getCIMArgArray
     * is used in creating this value format. <Arg Name=""
     * Method="getCIMArgument" Instance="ArgsCreator"> <ValueList> <Arg
     * Name="ElementName" Method="getStringValue" Instance="ArgCreator">
     * <Value>SRM_SYSTEM</Value> </Arg> <Arg Name="ElementType"
     * Method="getIntegerValue" Instance="ArgCreator"> <Value>2</Value> </Arg>
     * </ValueList> </Arg>
     * 
     * @param arg
     * @param keyMap
     * @param commandIndex
     * @return Object
     */
    public final Object getCIMArgArray(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        CIMArgument<?>[] inputArgs = null;
        List<Object> nameList = new ArrayList<Object>();
        populateNameList(arg, nameList);
        inputArgs = new CIMArgument[nameList.size()];
        @SuppressWarnings("unchecked")
        List<Object> valueObjList = (List<Object>) arg.getValue(); // need to
        // change
        int argCount = 0; // implementation
        for (Object valargobj : valueObjList) {
            Argument valarg = (Argument) valargobj;
            // final Object instance = _util.returnInstance(valarg.getCreator(),
            // keyMap);
            final Object instance = valarg.getCreator();
            final Method method = _util.getMethod(null, valarg.getMethod(), instance,
                    Util.ENDPOINTS.ARGUMENT.toString());
            final Object[] Args = { valarg, keyMap, index };
            try {
                inputArgs[argCount] = (CIMArgument<?>) method.invoke(instance, Args);
            } catch (IllegalArgumentException e) {
                // Exceptions would arise, only in developer environments.
                _logger.debug("Domain Logic XML creation problem");
            } catch (IllegalAccessException e) {
                _logger.debug("Domain Logic XML creation problem");
            } catch (InvocationTargetException e) {
                _logger.debug("Domain Logic XML creation problem");
            }
            argCount++;
        }
        return inputArgs;
    }

    public final Object getIntegerArrayValue16(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        String[] arr = null;
        if (arg.getValue() != null) {
            String val = (String) arg.getValue();
            arr = val.split(",");
        }
        UnsignedInteger16[] intarr = new UnsignedInteger16[arr.length];
        for (int i = 0; i < arr.length; i++) {
            intarr[i] = new UnsignedInteger16(Integer.parseInt(arr[i]));
        }
        return intarr;
    }

    /**
     * CIMArg wrapper over a Reference
     * 
     * @param arg
     * @return Object
     */
    public final Object getReferenceValueCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object value = getReferenceValue(arg, keyMap, index);
        if (null != value) {
            CIMObjectPath path = (CIMObjectPath) value;
            return new CIMArgument<Object>(arg.getName(), CIMDataType.getDataType(path),
                    value);
        }
        return value;
    }

    /**
     * CIMArg wrapper over a String
     * 
     * @param arg
     * @return Object
     */
    public final Object getStringValueCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object value = getStringValue(arg, null, index);
        if (null != value) {
            return new CIMArgument<Object>(arg.getName(),
                    CIMDataType.getDataType(value), value);
        }
        return value;
    }

    public final Object getStringValueFromKey(
            final Argument arg, final Map<String, Object> keyMap, final int index) {
        if (keyMap.containsKey(arg.getName())) {
            List<String> names = (List<String>) keyMap.get(arg.getName());
            return names.get(index);
        }
        return null;
    }

    /**
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     */
    public final InputStream getStreamValue(
            final Argument argument, final Map<String, Object> keyMap, int index) {
        InputStream iStream = getClass().getResourceAsStream(
                argument.getValue().toString());
        // set the proxy or any other configuration parameters here.
        return iStream;
    }

    /**
     * CIMArg wrapper over a StringArray
     * 
     * @param arg
     * @return Object
     */
    public final Object getStringArrayValueCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object value = getStringArrayValue(arg, null, index);
        if (null != value) {
            return new CIMArgument<Object>(arg.getName(), CIMDataType.STRING_ARRAY_T,
                    value);
        }
        return value;
    }

    /**
     * CIMArg wrapper over a StringArray
     * 
     * @param arg
     * @return Object
     */
    public final Object getBooleanValueCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object value = getBoolValue(arg, null, index);
        if (null != value) {
            return new CIMArgument<Object>(arg.getName(), CIMDataType.BOOLEAN_T, value);
        }
        return value;
    }

    /**
     * CIMArg wrapper over an Integer
     * 
     * @param arg
     * @return Object
     */
    public final Object getIntValueCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object value = getUnsignedInteger16(arg);
        if (null != value) {
            return new CIMArgument<Object>(arg.getName(), CIMDataType.UINT16_T, value);
        }
        return value;
    }

    /**
     * CIMArg wrapper over an Integer Array
     * 
     * @param arg
     * @return Object
     */
    public final Object getIntegerArrayValueCIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        Object value = getIntegerArrayValue16(arg, null, index);
        if (null != value) {
            // workaround
            _logger.info(CIMDataType.getDataType(value).toString());
            return new CIMArgument<Object>(arg.getName(), CIMDataType.UINT16_ARRAY_T,
                    value);
        }
        return value;
    }

    /**
     * CIMArg wrapper over an Integer
     * 
     * @param arg
     * @return Object
     */
    public final Object getIntValue16CIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        UnsignedInteger16 value = getUnsignedInteger16(arg);
        if (null != value) {
            return new CIMArgument<Object>(arg.getName(), CIMDataType.UINT16_T, value);
        }
        return value;
    }

    /**
     * CIMArg wrapper over an Integer
     * 
     * @param arg
     * @return Object
     */
    public final Object getIntValue64CIMWrapper(
            final Argument arg, final Map<String, Object> keyMap, int index) {
        UnsignedInteger64 value = getUnsignedInteger64(arg);

        if (null != value) {
            UnsignedInteger64 val = new UnsignedInteger64(value.toString());
            return new CIMArgument<Object>(arg.getName(), CIMDataType.UINT64_T, val);
        }
        return value;
    }

    /**
     * populate Name List from the Arguments inside ValueList
     * 
     * @param arg
     * @param nameList
     */
    private final void populateNameList(final Argument arg, final List<Object> nameList) {
        List<Object> valueObjList = (List<Object>) arg.getValue();
        for (Object valargobj : valueObjList) {
            Argument valarg = (Argument) valargobj;
            nameList.add(valarg.getName());
        }
    }

    /**
     * Creates UnsignedInteger16 instance for the give Argument.value.
     * 
     * @param arg
     * @return
     */
    public final UnsignedInteger16 getUnsignedInteger16(final Argument arg) {
        UnsignedInteger16 value = null;
        if (arg.getValue() != null) {
            value = new UnsignedInteger16((String) arg.getValue());
        }
        return value;
    }

    /**
     * Creates UnsignedInteger32 instance for the give Argument.value.
     * 
     * @param arg
     * @return
     */
    public final UnsignedInteger32 getUnsignedInteger32(final Argument arg) {
        UnsignedInteger32 value = null;
        if (arg.getValue() != null) {
            value = new UnsignedInteger32((String) arg.getValue());
        }
        return value;
    }

    /**
     * Creates UnsignedInteger64 instance for the give Argument.value.
     * 
     * @param arg
     * @return
     */
    public final UnsignedInteger64 getUnsignedInteger64(final Argument arg) {
        UnsignedInteger64 value = null;
        if (arg.getValue() != null) {
            value = new UnsignedInteger64((String) arg.getValue());
        }
        return value;
    }
}
