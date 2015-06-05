/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.engine.bind;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.emc.sa.util.TextUtils;

/**
 * Utilities for binding parameters into request objects.
 * 
 * @author Chris Dail
 */
public class BindingUtils {
    private static Logger log = Logger.getLogger(BindingUtils.class);

    private static ConvertUtilsBean convertUtilsBean;

    static {
        convertUtilsBean = new ConvertUtilsBean();
        convertUtilsBean.register(new CommaSeparatedListConverter(), List.class);
        convertUtilsBean.register(new URIConverter(), URI.class);
        convertUtilsBean.register(new IntegerConverter(null), Integer.class);
    }

    public static void bind(Object target, Map<String, Object> parameters) {
        bind(target, new MapParameterAccess(parameters));
    }

    public static void bind(Object target, ParameterAccess parameters) {
        for (Field field : getAllDeclaredFields(target.getClass())) {
            bindField(target, field, parameters);
        }
    }

    /**
     * Gets all declared fields on the given type and its super classes.
     * 
     * @param type
     *        the type.
     * @return the list of declared fields.
     */
    private static List<Field> getAllDeclaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> superClass = type;
        while (!superClass.equals(Object.class)) {
            fields.addAll(Arrays.asList(superClass.getDeclaredFields()));
            superClass = superClass.getSuperclass();
        }
        return fields;
    }

    /**
     * Binds a parameter to the field if applicable.
     * 
     * @param target
     *        the target object.
     * @param field
     *        the field to bind.
     * @param parameters
     *        the available parameters.
     */
    private static void bindField(Object target, Field field, ParameterAccess parameters) {
        if (field.isAnnotationPresent(Param.class)) {
            Param param = field.getAnnotation(Param.class);
            bindParam(param, target, field, parameters);
        }
        else if (field.isAnnotationPresent(Bindable.class)) {
            Bindable bindable = field.getAnnotation(Bindable.class);
            boolean simpleBinding = Void.class.equals(bindable.itemType());
            if (simpleBinding) {
                Object newTarget = getBindTarget(target, field);
                bind(newTarget, parameters);
            }
            else {
                bindList(bindable.itemType(), target, field, parameters);
            }
        }
    }

    /**
     * Binds a given parameters onto the target object.
     * 
     * @param param
     *        the paramter annotation.
     * @param target
     *        the target object.
     * @param field
     *        the target field of the parameter.
     * @param parameters
     *        the available parameters.
     */
    private static void bindParam(Param param, Object target, Field field, ParameterAccess parameters) {
        String fieldName = field.getName();
        Class<?> fieldType = field.getType();

        // Use the field name if no value is specified
        String paramName = StringUtils.defaultIfBlank(param.value(), fieldName);
        Object value = parameters.get(paramName);

        if (value == null && param.required()) {
            String message = String.format("Required parameter '%s' is missing for field %s on %s", paramName,
                    fieldName, field.getDeclaringClass().getName());
            throw new BindingException(message);
        }
        else if (value != null) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Binding parameter '%s' to field '%s'", paramName, fieldName));
            }

            try {
                field.setAccessible(true);
                field.set(target, convert(value, fieldType));
            }
            catch (Exception e) {
                String message = String.format("Error binding parameter '%s' to field '%s'", paramName, fieldName);
                throw new BindingException(message, e);
            }
        }
    }

    /**
     * Gets a new target for binding to the field value. If the field is null, a
     * new instance is created and set as the field value.
     * 
     * @param target
     *        the target object.
     * @param field
     *        the bind field.
     * @return the bind target.
     */
    private static Object getBindTarget(Object target, Field field) {
        try {
            field.setAccessible(true);
            Object fieldValue = field.get(target);
            if (fieldValue == null) {
                fieldValue = field.getType().newInstance();
                field.set(target, fieldValue);
            }
            return fieldValue;
        }
        catch (Exception e) {
            String message = String.format("Error getting bind target for field '%s'", field.getName());
            throw new BindingException(message, e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object convert(Object value, Class type) {
        if (value instanceof String && !(type.isArray() || type.isAssignableFrom(List.class))) {
            List<String> parsedValue = TextUtils.parseCSV((String)value);
            if (parsedValue.isEmpty()) {
                value = "";
            }
            else if (parsedValue.size() == 1) {
                value = parsedValue.get(0);
            }
            else {
                String message = String.format(
                        "Value '%s' produced %d values when parsed from CSV. Should be 1", value, parsedValue.size());
                throw new BindingException(message);
            }
        }
        Object convertedValue = convertUtilsBean.convert(value, type);
        // Handle the general purpose enum conversion
        if (type.isEnum() && (value != null) && !type.isInstance(convertedValue)) {
            convertedValue = Enum.valueOf(type, value.toString());
        }
        return convertedValue;
    }

    /**
     * Binds a list value into the given field.
     * 
     * @param itemType
     *        the item type of the list.
     * @param target
     *        the target object.
     * @param field
     *        the target field.
     * @param parameters
     *        the input parameters.
     */
    private static void bindList(Class<?> itemType, Object target, Field field, ParameterAccess parameters) {
        String fieldName = field.getName();
        Class<?> fieldType = field.getType();

        List<ParameterAccess> itemParameters = createItemParameters(itemType, parameters);
        Object array = Array.newInstance(itemType, itemParameters.size());
        for (int i = 0; i < itemParameters.size(); i++) {
            Object itemValue = createAndBindItem(itemParameters.get(i), itemType);
            Array.set(array, i, itemValue);
        }

        Object targetValue = convertArray(array, fieldType, itemType);
        try {
            field.setAccessible(true);
            field.set(target, targetValue);
        }
        catch (Exception e) {
            String message = String.format("Error binding list to field '%s'", fieldName);
            throw new BindingException(message, e);
        }
    }

    /**
     * Converts the array to the appropriate type for binding.
     * 
     * @param array
     *        the array to convert.
     * @param fieldType
     *        the type of the target field.
     * @param itemType
     *        the item type of the array.
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object convertArray(Object array, Class<?> fieldType, Class<?> itemType) {
        int len = Array.getLength(array);
        // Convert to a list
        if (fieldType.equals(List.class)) {
            List list = new ArrayList();
            for (int i = 0; i < len; i++) {
                list.add(Array.get(array, i));
            }
            return list;
        }
        // Array of an assignment compatible type
        else if (fieldType.isArray() && fieldType.getComponentType().isAssignableFrom(itemType)) {
            // Check for exact type match
            if (itemType.equals(fieldType.getComponentType())) {
                return array;
            }
            Object targetArray = Array.newInstance(fieldType.getComponentType(), len);
            for (int i = 0; i < len; i++) {
                Array.set(targetArray, i, Array.get(array, i));
            }
            return targetArray;
        }
        else {
            String message = String.format("Cannot convert array of %s to %s", itemType, fieldType);
            throw new BindingException(message);
        }
    }

    /**
     * Creates and binds the parameters to a new item.
     * 
     * @param parameters
     *        the parameters.
     * @param itemType
     *        the item type.
     * @return the new item.
     */
    private static Object createAndBindItem(ParameterAccess parameters, Class<?> itemType) {
        try {
            Object value = itemType.newInstance();
            bind(value, parameters);
            return value;
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new BindingException("Failed to instantiate new instance of " + itemType, e);
        }
    }

    /**
     * Creates a list of parameters for each item based on its type. The
     * provided parameters are treated as comma-separated columns and converted
     * into matching rows for each item in the list.
     * 
     * @param itemType
     *        the type of each item, the {@link Param}s of the type determine
     *        what parameters are used to convert into items.
     * @param parameters
     *        the input parameters.
     * @return the list of item parameters.
     */
    private static List<ParameterAccess> createItemParameters(Class<?> itemType, ParameterAccess parameters) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : getParameterNames(itemType)) {
            Object value = parameters.get(name);
            List<String> values = TextUtils.parseCSV(value != null ? value.toString() : null);
            for (int i = 0; i < values.size(); i++) {
                getOrCreateRow(rows, i).put(name, values.get(i));
            }
        }

        // Converts the row maps into parameters
        List<ParameterAccess> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            results.add(new MapParameterAccess(row));
        }
        return results;
    }

    /**
     * Gets the names of the parameters in a given type.
     * 
     * @param type
     *        the type.
     * @return the set of parameter names.
     */
    private static Set<String> getParameterNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field field : getAllDeclaredFields(type)) {
            Param param = field.getAnnotation(Param.class);
            if (param != null) {
                names.add(StringUtils.defaultIfBlank(param.value(), field.getName()));
            }
        }
        return names;
    }

    private static Map<String, Object> getOrCreateRow(List<Map<String, Object>> rows, int index) {
        while (rows.size() <= index) {
            rows.add(new HashMap<String, Object>());
        }
        return rows.get(index);
    }

    private static final class MapParameterAccess implements ParameterAccess {
        private Map<String, Object> data;

        public MapParameterAccess(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public Set<String> getNames() {
            return data.keySet();
        }

        @Override
        public Object get(String name) {
            return data.get(name);
        }

        @Override
        public void set(String name, Object value) {
            data.put(name, value);
        }
    }
}
