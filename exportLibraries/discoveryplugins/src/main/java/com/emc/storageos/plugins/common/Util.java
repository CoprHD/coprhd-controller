/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Argument;
import com.emc.storageos.plugins.common.domainmodel.Operation;



/**
 * Class having responsiblities like creating Input Arguments from Operation
 * Node, returning Instance from Map, Finding out Methods using reflection.
 */
public class Util {
    private static final Logger _logger = LoggerFactory.getLogger(Util.class);

    public static enum ENDPOINTS {
        ARGUMENT,
        OPERATION
    }


    /**
     * Return InputArgument Array If argument object has Filter, then compare
     * the metrics values got from Framework against the present in Domain
     * Logic. Remove metrics from framework list if it is not matching Domain
     * Logic. Else if filter is not there, proceed with no change.
     * 
     * @param operation
     *            : Domain Logic operation.
     * @param index
     *            : index of the Director or Port List.
     * @return Object[].
     * @throws SMIPluginException
     *             ex.
     * @throws InvocationTargetException
     *             ex.
     * @throws IllegalArgumentException
     *             ex.
     * @throws IllegalAccessException
     *             ex.
     */
    public final Object[] returnInputArgs(
            final Operation operation, final Map<String, Object> keyMap, int index)
            throws BaseCollectionException, IllegalAccessException, InvocationTargetException {
        final List<Object> args = operation.get_arguments();
        final int nArgs = args.size();
        final Object[] inputArgs1 = new Object[nArgs];
        int count = 0;
        for (Object argobj : args) {
            /**
             * 1. Get the instance on which the method needs to get executed 2.
             * Get the Method 3. Construct Input Argument Array 4. Execute
             */
            Argument arg = (Argument) argobj;
            // final Object instance = returnInstance(arg.gegetInstance(),
            // keyMap);
            final Object instance = arg.get_creator();
            final Method method = getMethod(operation, arg.get_method(), instance,Util.ENDPOINTS.ARGUMENT.toString());
            final Object[] inputArgs = { arg, keyMap, index };
            final Object resultObj = method.invoke(instance, inputArgs);
            inputArgs1[count++] = resultObj;
        }
        return normalizedWriteArgs(keyMap, inputArgs1);
    }
    
    /**
     * Get the Instance on which method needs to be run.
     *  
     * @param operation
     * @param keyMap
     * @throws BaseCollectionException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public final Object returnInstanceToRun(
            final Operation operation, final Map<String, Object> keyMap, int index)
            throws BaseCollectionException, IllegalAccessException,
            InvocationTargetException {
        Object instanceToReturn = null;
        if (operation.getInstance() instanceof Argument) {
            Argument instanceArg = (Argument) operation.getInstance();
            final Object[] inputArgs = { instanceArg, keyMap, index };
            final Object instance = instanceArg.get_creator();
            final Method method = getMethod(operation, instanceArg.get_method(), instance,Util.ENDPOINTS.ARGUMENT.toString());
            instanceToReturn = method.invoke(instanceArg.get_creator(), inputArgs);
        } else
            instanceToReturn = operation.getInstance();
        return instanceToReturn;
    }

    /**
     * get Method to execute If operation.Argument Objects size ==
     * Method.Parameters size, then select the Method.
     * 
     * @param methodName
     *            : Method.
     * @param operation
     *            : Domain logic operation.
     * @param instance
     *            : CIMCLient or Request or any 3rd party..
     * @return Method.
     */
    public final Method getMethod(
            final Operation operation, final String methodName, final Object instance,
            final String endPoint) {
        Method method = null;
        try {
            Class[] parameterTypeClasses = getParameterTypesFromConfiguration(operation,
                    endPoint);
            if (null == parameterTypeClasses || parameterTypeClasses.length == 0) {
                Method[] methods = instance.getClass().getMethods();
                for (Method m : methods) {
                    if (instance instanceof WBEMClient) {
                        if (m.getName().equalsIgnoreCase(methodName)
                                && operation.get_arguments().size() == m
                                        .getParameterTypes().length) {
                            method = m;
                            _logger.debug("Method found :" + m.getName());
                            break;
                        }
                    } else {
                        if (m.getName().equalsIgnoreCase(methodName)) {
                            method = m;
                            break;
                        }
                    }
                }
            } else {
                // find Overloaded Methods by passing expected Argument Classes
                method = instance.getClass().getMethod(methodName, parameterTypeClasses);
                _logger.debug("Method found  :" + method.getName());
            }
        } catch (SecurityException e) {
            _logger.error("Method Not found due to Security Exception   : {} -->{}",
                    methodName, e);
        } catch (NoSuchMethodException e) {
            _logger.error("Method Not found due to NoSuchMethodException   :{} -->{}",
                    methodName, e);
        } catch (ClassNotFoundException e) {
            _logger.error("Method Not found due to ClassNotFoundException :{} -->{}",
                    methodName, e);
        }
        return method;
    }

    /**
     * get Method based on Parameter Types
     * @param operation
     * @param endPoint
     * @return
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("rawtypes")
    private Class[] getParameterTypesFromConfiguration(
            Operation operation, String endPoint) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<Class>();
        Class[] classArray = null;
        if(null == operation) return classArray;
        for (int index = 0; index < operation.get_arguments().size(); index++) {
            String type = getType(operation, endPoint, index);
            if (null == type)
                continue;
            Class expectedClazz = Class.forName(type);
            classes.add(expectedClazz);
        }
        classArray = new Class[classes.size()];
        classArray = classes.toArray(classArray);
        return classArray;
    }

    private String getType(Operation operation, String endPoint, int index) {
        if (ENDPOINTS.ARGUMENT.toString().equalsIgnoreCase(endPoint))
            return ((Argument) operation.get_arguments().get(index)).get_type();
        return operation.get_type();
    }

    /**
     * return the Instance on which the method needs to get executed.
     * 
     * @param instanceKey
     *            : key.
     * @param keyMap
     *            : datastructure to hold data.
     * @return Object.
     */
    public Object returnInstance(
            final String instanceKey, final Map<String, Object> keyMap) {
        Object instance = null;
        if (keyMap.containsKey(instanceKey)) {
            instance = keyMap.get(instanceKey);
        } else {
            _logger.error("Instance : {} not found", instanceKey);
            /**
             * Use reflection to create the instance To-Do
             */
        }
        return instance;
    }

    /**
     * Does operation have filter. To-Do :future use
     * 
     * @param filterValue
     * @param filterValuesFromOp
     * @return boolean.
     */
    public final boolean doesOpFilterHasDiscoveryFilter(
            final Object filterValue, final String[] filterValuesFromOp) {
        boolean bOpHasFilterValue = true;
        if (filterValue instanceof List<?>) {
            @SuppressWarnings("unchecked")
            final List<String> filterValuesFromKeymap = (List<String>) filterValue;
            boolean bMatched = false;
            for (String v : filterValuesFromKeymap) {
                for (String s : filterValuesFromOp) {
                    if (v.equalsIgnoreCase(s)) {
                        bMatched = true;
                        break;
                    }
                }
            }
            if (!bMatched) {
                bOpHasFilterValue = false;
            }
        }
        return bOpHasFilterValue;
    }

    /**
     * Print Map values.
     * 
     * @param keyMap
     *            : common datastructure to hold.
     * @return String.
     */
    @SuppressWarnings("unchecked")
    public final String printMapValues(final Map<String, Object> keyMap) {
        final StringBuilder strbuilder = new StringBuilder();
        for (Entry<String, Object> keyType : keyMap.entrySet()) {
            String key = keyType.getKey();
            strbuilder.append("*****************\n");
            strbuilder.append("key : ");
            strbuilder.append(key);
            strbuilder.append("\n");
            Object result = keyType.getValue();
            if (result instanceof List) {
                List<Object> objList = (List<Object>) result;
                strbuilder.append("Value List : \n");
                for (Object obj : objList) {
                    strbuilder.append(obj.toString());
                    strbuilder.append("\n");
                }
            } else {
                strbuilder.append("Value : ");
                strbuilder.append(result);
                strbuilder.append("\n");
            }
        }
        return strbuilder.toString();
    }

    /**
     * get Message.
     * 
     * @param ex
     *            WBEMException.
     * @return String.
     */
    public String getMessage(final WBEMException ex) {
        String cause = ex.getCause() != null ? ex.getCause().toString() : "";
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        String error = "";
        if (!cause.isEmpty()) {
            error = cause;
        }
        if (!message.isEmpty()) {
            error = error + "." + message;
        }
        return error;
    }
    /**
     * Method used as a ByPasser
     * i.e. if runtime control wants to directly execute a Processor code
     * then use this by Passer
     * @return
     */
    public boolean byPassControlToProcessor(boolean flag) {
        return true;
    }

    /**
     * This routine will take the arguments and transform any CIMObjectPaths that have
     * SMI8.0 delimiters ("-+-") to "+". This will make it so that everything at the ViPR
     * controller level knows things in terms of this delimiter. Anything that has to be
     * communicated back to the provider will translated back to SMI8.0 delimiters.
     *
     * @param keyMap  [in/out] Map of String to Objects. Context used for scanning and discovery
     * @param objects [in] CIM Arguments
     * @return objects, possibly modified.
     */
    public static Object normalizedReadArgs(Map<String, Object> keyMap, Object... objects) {
        int index = 0;
        for (Object object : objects) {
            if (object instanceof CIMObjectPath && object.toString().contains(Constants.SMIS80_DELIMITER)) {
                objects[index] = modifyForViPRConsumption(keyMap, (CIMObjectPath) object);
            }
            index++;
        }
        return objects;
    }

    /**
     * This routine will take the arguments and transform any CIMObjectPaths to SMI8.0 delimiters
     * if the the USING_SMIS80_DELIMITERS context flag is enabled.
     *
     * @param keyMap  [in/out] Map of String to Objects. Context used for scanning and discovery
     * @param objects [in] CIM Arguments
     * @return objects, possibly modified
     */
    public static Object[] normalizedWriteArgs(Map<String, Object> keyMap, Object[] objects) {
        int index = 0;
        Boolean using80Delimiters = (Boolean)keyMap.get(Constants.USING_SMIS80_DELIMITERS);
        if (using80Delimiters != null && using80Delimiters) {
            for (Object object : objects) {
                if (object instanceof CIMObjectPath) {
                    String string = object.toString();
                    if (string.contains(Constants.SMIS80_DELIMITER)) {
                        continue;
                    }
                    if (!string.contains("SE_ReplicationGroup") && string.contains(Constants.PLUS)) {
                        objects[index] = modifyForSMIS80(keyMap, (CIMObjectPath) object);
                    }
                } else if (object instanceof CIMArgument) {
                    // Check any of the argument is CIMObjectPath, if so, it
                    // would need to be fixed for SMI-8.0 providers
                    Object modifiedArgument = modifyAnyCIMObjectPaths(keyMap, object);
                    if (modifiedArgument != null) {
                        objects[index] = modifiedArgument;
                    }
                } else if (object instanceof CIMArgument[]) {
                    // Check any of the arguments that are passed for CIMObjects.
                    // If there are any, fix them for SMI-S 8.0.
                    boolean updated = false;
                    CIMArgument[] arguments = (CIMArgument[]) object;
                    for (int argumentIndex = 0; argumentIndex < arguments.length; argumentIndex++) {
                        CIMArgument currentArgument = arguments[argumentIndex];
                        CIMArgument modifiedArgument = modifyAnyCIMObjectPaths(keyMap, currentArgument);
                        if (modifiedArgument != null) {
                            arguments[argumentIndex] = modifiedArgument;
                            updated = true;
                        }
                    }
                    if (updated) {
                        objects[index] = arguments;
                    }
                }
                index++;
            }
        }
        return objects;
    }

    /**
     * This method will take the 'object', which points to a CIMArgument, and determine
     * if it holds a CIMObjectPath. If it does it, will do a conversion of the object
     * per the expected delimiter for SMI-S 8.0
     *
     * @param keyMap [in] - Contextual object
     * @param object [in] - CIMArgument as an Object
     * @return CIMArgument
     */
    private static CIMArgument modifyAnyCIMObjectPaths(Map<String, Object> keyMap, Object object) {
        if (keyMap == null || object == null) {
            return null;
        }
        CIMArgument modifiedArgument = null;
        CIMArgument argument = (CIMArgument) object;
        // Only process CIMObjectPath CIMArguments
        Object cimPathReferenceObject = argument.getValue();
        if (cimPathReferenceObject == null || !(cimPathReferenceObject instanceof CIMObjectPath)) {
            return null;
        }
        // If the path already has the SMI-S 8.0 delimiter, then don't bother
        String string = cimPathReferenceObject.toString();
        if (string.contains(Constants.SMIS80_DELIMITER)) {
            return null;
        }
        // This has a delimiter in there, so we'll have to modify it
        if (!string.contains("SE_ReplicationGroup") && string.contains(Constants.PLUS)) {
            Object modifiedPath = modifyForSMIS80(keyMap, (CIMObjectPath) cimPathReferenceObject);
            modifiedArgument = new CIMArgument<>(argument.getName(), argument.getDataType(),
                    (CIMObjectPath)modifiedPath);
        }
        return modifiedArgument;
    }

    private static Object modifyForSMIS80(Map<String, Object> keyMap, CIMObjectPath cimObjectPath) {
        return modifyCimObjectPathProperties(keyMap, cimObjectPath, Constants.PLUS, Constants.SMIS_PLUS_REGEX, Constants.SMIS80_DELIMITER);
    }

    private static Object modifyForViPRConsumption(Map<String, Object> keyMap, CIMObjectPath cimObjectPath) {
        return modifyCimObjectPathProperties(keyMap, cimObjectPath, Constants.SMIS80_DELIMITER, Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
    }

    /**
     * Generic routine that will take a cimObjectPath and examine it. It will look through the keys and check their
     * values for the specified 'delimiter'. If it exists, then all instances of 'searchRegex' will be changed to
     * 'replaceWith'.
     *
     * If there was a modification, keyMap will have a Constants.USING_SMIS80_DELIMITERS set to true.
     *
     * @param keyMap [in/out] Map of String to Objects. Context used for scanning and discovery. Can pass null if
     *               this is not necessary to use.
     * @param cimObjectPath [in] - CIMObjectPath
     * @param delimiter [in] - Check if any key values contain this delimiter
     * @param searchRegex [in] - Substring search string
     * @param replaceWith [in] - Replacement string
     * @return CIMObjectPath, that may have been modified.
     */
    private static Object modifyCimObjectPathProperties(Map<String, Object> keyMap, CIMObjectPath cimObjectPath,
                                                        String delimiter, String searchRegex, String replaceWith) {
        CIMObjectPath result = cimObjectPath;
        boolean isModified = false;

        CIMProperty<?>[] properties = cimObjectPath.getKeys();
        for (int index=0; index < properties.length; index++) {
            CIMProperty property = properties[index];
            Object value = cimObjectPath.getKeyValue(property.getName());
            if (value instanceof String) {
                String string = (String) value;
                if (string.contains(delimiter)) {
                    String modified = string.replaceAll(searchRegex, replaceWith);
                    CIMProperty changed =
                            new CIMProperty<>(property.getName(), CIMDataType.STRING_T, modified, true, false, null);
                    properties[index] = changed;
                    isModified = true;
                    if (keyMap != null) {
                        keyMap.put(Constants.USING_SMIS80_DELIMITERS, Boolean.TRUE);
                    }
                }
            }
        }

        if (isModified) {
            result = CimObjectPathCreator.createInstance(cimObjectPath.getObjectName(),
                    cimObjectPath.getNamespace(), properties);
        }
        return result;
    }
}
