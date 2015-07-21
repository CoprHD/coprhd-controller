/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.bind.annotation.XmlElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.valid.Endpoint;
import com.emc.storageos.model.valid.EnumType;
import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Validates input against JAXB annotations.   JAXB itself doesn't
 * provide a OOB way of validating against annotated classes.  Since
 * we don't want to rug around a schema for validation, I am writing
 * this code...
 */
public class InputValidator {
    private static final Logger _log = LoggerFactory.getLogger(InputValidator.class);

    private static final String JAXB_DEFAULT = "##default";

    // what to validate
    static class Validator {
        static class FieldInfo {
            public String name;
            public boolean required;
            public boolean nillable;
            public Class<? extends Enum> enumType;
            public Long min;
            public Long max;
            public Enum type;

            // Field is set only if this field is a public field
            public Field field;
            // Alternatively this field could be a property with read/write methods
            public PropertyDescriptor descriptor;

            public Object getValue(Object bean) throws IllegalAccessException, InvocationTargetException {
                if (field != null) {
                    return field.get(bean);
                }
                else if (descriptor != null) {
                    return descriptor.getReadMethod().invoke(bean);
                }
                return null;
            }

            public void setValue(Object bean, Object value) throws IllegalAccessException, InvocationTargetException {
                if (field != null) {
                    field.set(bean, value);
                }
                if (descriptor != null) {
                    descriptor.getWriteMethod().invoke(bean, value);
                }
            }

            public Type getGenericType() {
                if (field != null) {
                    return field.getGenericType();
                }
                else if (descriptor != null) {
                    return descriptor.getReadMethod().getGenericReturnType();
                }
                return null;
            }
        }

        private Class clazz;
        private Map<String,FieldInfo> fieldInfo  = new HashMap<String, FieldInfo>();

        /**
         * Constructor
         *
         * @param clazz JAXB annotated input class
         */
        public Validator(Class clazz) {
            this.clazz = clazz;
            processAnnotations();
        }

        /**
         * Process JAXB annotations and stash them for validation use
         */
        private void processAnnotations() {
            Field[] fields = clazz.getFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                Annotation[] annotations = field.getAnnotations();
                for (int j = 0; j < annotations.length; j++) {
                    Annotation a = annotations[j];
                    if (a instanceof XmlElement) {
                        XmlElement e = (XmlElement)a;
                        FieldInfo fi = getField(field);
                        fi.required = e.required();
                        fi.nillable = e.nillable();
                        String name = e.name();
                        if(!JAXB_DEFAULT.equals(name)){
                            fi.name = name;
                        }
                    } else if (a instanceof EnumType) {
                        EnumType e = (EnumType)a;
                        FieldInfo fi = getField(field);
                        fi.enumType = e.value();
                    } else if (a instanceof Length) {
                        Length l = (Length)a;
                        FieldInfo fi = getField(field);
                        fi.min = (long)l.min();
                        fi.max = (long)l.max();
                    } else if (a instanceof Range) {
                        Range l = (Range)a;
                        FieldInfo fi = getField(field);
                        fi.min = l.min();
                        fi.max = l.max();
                    } else if (a instanceof Endpoint) {
                        Endpoint l = (Endpoint)a;
                        FieldInfo fi = getField(field);
                        fi.type = l.type();
                    }
                }
            }

            try {
                PropertyDescriptor[] descriptors = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
                for (PropertyDescriptor descriptor: descriptors) {
                    Annotation[] annotations = descriptor.getReadMethod().getAnnotations();
                    for (Annotation a: annotations) {
                        if (a instanceof XmlElement) {
                            XmlElement e = (XmlElement)a;
                            FieldInfo fi = getField(descriptor);
                            fi.required = e.required();
                            fi.nillable = e.nillable();
                            String name = e.name();
                            if(!JAXB_DEFAULT.equals(name))
                                fi.name = name;
                        } else if (a instanceof EnumType) {
                            EnumType e = (EnumType)a;
                            FieldInfo fi = getField(descriptor);
                            fi.enumType = e.value();
                        } else if (a instanceof Length) {
                            Length l = (Length)a;
                            FieldInfo fi = getField(descriptor);
                            fi.min = (long)l.min();
                            fi.max = (long)l.max();
                        } else if (a instanceof Range) {
                            Range l = (Range)a;
                            FieldInfo fi = getField(descriptor);
                            fi.min = l.min();
                            fi.max = l.max();
                        } else if (a instanceof Endpoint) {
                            Endpoint l = (Endpoint)a;
                            FieldInfo fi = getField(descriptor);
                            fi.type = l.type();
                        }
                    }
                }
            }
            catch (IntrospectionException e) {
                _log.error("Unexpected exception:", e);
                throw APIException.internalServerErrors.genericApisvcError(e.getMessage(),e);
            }
        }

        /**
         * Retrieve validation info for given field.
         *
         * @param field
         * @return
         */
        private FieldInfo getField(Field field) {
            FieldInfo info = fieldInfo.get(field.getName());
            if (info == null) {
                info = new FieldInfo();
                info.name = field.getName();
                fieldInfo.put(info.name, info);
            }
            info.field = field;
            return info;
        }

        /**
         * Retrieve validation info for given field.
         *
         * @param descriptor
         * @return
         */
        private FieldInfo getField(PropertyDescriptor descriptor) {
            FieldInfo info = fieldInfo.get(descriptor.getName());
            if (info == null) {
                info = new FieldInfo();
                info.name = descriptor.getName();
                fieldInfo.put(info.name, info);
            }
            info.descriptor = descriptor;
            return info;
        }

        /**
         * Validates input object and throws BadRequestException if
         * any error is encountered
         *
         * @param val
         */
        @SuppressWarnings("unchecked")
        public void validate(Object val) {
            if (!clazz.isInstance(val)) {
            	throw APIException.badRequests.notAnInstanceOf(clazz.getSimpleName());
            }
            try {
                Iterator<Map.Entry<String, FieldInfo>> it = fieldInfo.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, FieldInfo> field = it.next();
                    FieldInfo fi = field.getValue();
                    Object fieldVal = fi.getValue(val);

                    // required check
                    if (fi.required) {
                        if (fieldVal == null) {
                            throw APIException.badRequests.requiredParameterMissingOrEmpty(fi.name);
                        }
                    }

                    // nillable check
                    if (!fi.nillable) {
                        if (fieldVal instanceof String && ((String)fieldVal).trim().isEmpty()) {
                            throw APIException.badRequests.requiredParameterMissingOrEmpty(fi.name);
                        }
                    }

                    // enum type check
                    if (fi.enumType != null && fieldVal instanceof String) {
                        try {
                            Enum.valueOf(fi.enumType, (String)fieldVal);
                        } catch (IllegalArgumentException e) {
                            throw APIException.badRequests.invalidField(fi.name,
                                    (String) fieldVal, e);
                        }
                    }

                    // endpoint type check
                    if (fi.type != null && fi.type instanceof Endpoint.EndpointType && fieldVal instanceof String) {
                        if (!EndpointUtility.isValidEndpoint((String)fieldVal, (Endpoint.EndpointType)fi.type)) {
                            throw APIException.badRequests.invalidField(fi.name,
                                    (String)fieldVal);
                        }
                    }

                    // trim strings
                    if (fieldVal instanceof String) {
                        fi.setValue(val, ((String)fieldVal).trim());
                    }

                    // length check
                    if (fieldVal instanceof String && (fi.min != null || fi.max != null)) {
                        int length = ((String)fieldVal).length();
                        ArgValidator.checkFieldRange(length, fi.min, fi.max, "length");
                    }

                    // range check
                    if (fieldVal instanceof Number && (fi.min != null || fi.max != null)) {
                        Number value = (Number)fieldVal;
                        ArgValidator.checkFieldRange(value.longValue(), fi.min, fi.max, fi.name);
                    }

                    // collection check
                    if (fieldVal instanceof Collection) {
                        ParameterizedType paramType = (ParameterizedType) fi.getGenericType();
                        Type elementType = paramType.getActualTypeArguments()[0];
                        
                        if (elementType.equals(String.class)) {
                            boolean needNillCheck = false;
                            boolean needLengthCheck = false;

                            if (!fi.nillable)
                                needNillCheck = true;
                            if (fi.min != null || fi.max != null)
                                needLengthCheck = true;

                            // trim all the elements
                            Iterator<String> iterator = ((Collection<String>)fieldVal).iterator();
                            Collection<String> trimmedList = new ArrayList<String>();
                            while (iterator.hasNext()) {
                                String element = iterator.next().trim();

                                // nillable check
                                if (needNillCheck && element.isEmpty()) {
                                    throw APIException.badRequests.requiredParameterMissingOrEmpty(fi.name);
                                }

                                // length check
                                if (needLengthCheck) 
                                    lengthCheck(element, fi);

                                iterator.remove();
                                trimmedList.add(element);
                            }
                            for (String element : trimmedList) {
                                // have to iterator over trimmedList since some Collection 
                                // implementations have overridden addAll(), like StringSet
                                ((Collection<String>)fieldVal).add(element);
                            }

                        } else if (elementType.equals(Number.class)) {
                            // perform range check on the elements
                            if (fi.min != null || fi.max != null) {
                                for (Number element : (Collection<Number>) fieldVal) {
                                    rangeCheck(element, fi);
                                }
                            }
                        }
                    }
                }
            }
            catch (InvocationTargetException e) {
                _log.error("Unexpected exception:", e);
                throw APIException.internalServerErrors.genericApisvcError(e.getMessage(),e);
            }
            catch (IllegalAccessException e) {
                _log.error("Unexpected exception:", e);
                throw APIException.internalServerErrors.genericApisvcError(e.getMessage(),e);
            }
        }

        private void lengthCheck(String fieldVal, FieldInfo fi) {
            int length = fieldVal.length();
            ArgValidator.checkFieldRange(length,  fi.min, fi.max, "length");
        }

        private void rangeCheck(Number value, FieldInfo fi) {
            if (value.longValue() < fi.min || value.longValue() > fi.max) {
                throw APIException.badRequests.parameterNotWithinRange(fi.name,
                        value, fi.min, fi.max, "");
            }
        }
    }

    private static final InputValidator _validator = new InputValidator();

    private ConcurrentMap<Class, Validator> _inputTypeMap =
            new ConcurrentHashMap<Class, Validator>();

    /**
     * Validates a given input object
     *
     * @param obj
     */
    public void validate(Object obj) {
        Class clazz = obj.getClass();
        Validator validator = _inputTypeMap.get(clazz);
        if (validator == null) {
            validator = new Validator(clazz);
            _inputTypeMap.putIfAbsent(clazz, validator);
        }
        validator.validate(obj);
    }

    /**
     * Retrieve singleton input validator
     *
     * @return
     */
    public static InputValidator getInstance() {
        return _validator;
    }
}
