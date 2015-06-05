package com.emc.sa.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;

import com.google.common.collect.Maps;

/**
 * This is a utility class for testing bean getters and setters. Any 'standard' properties can be tested automatically
 * using {@link #testGettersAndSetters(Object)} using random input values. Any other properties can be tested by calling
 * {@link #testGetterAndSetter(Object, String, Object)} with an appropriate value. The types supported for random value
 * testing are:
 * <ul>
 * <li>String</li>
 * <li>Integer (int)</li>
 * <li>Long (long)</li>
 * <li>Short (short)</li>
 * <li>Byte (byte)</li>
 * <li>Double (double)</li>
 * <li>Float (float)</li>
 * <li>Boolean (boolean)
 * <li>
 * <li>Date</li>
 * </ul>
 * 
 * @author jonnymiller
 */
public class BeanTestUtils {
    private static final Map<Class<?>, ValueGenerator> TYPES = createTypes();

    private static final Map<Class<?>, ValueGenerator> createTypes() {
        Map<Class<?>, ValueGenerator> types = Maps.newHashMap();
        types.put(String.class, new StringGenerator());
        types.put(Integer.class, new IntegerGenerator());
        types.put(int.class, new IntegerGenerator());
        types.put(Long.class, new LongGenerator());
        types.put(long.class, new LongGenerator());
        types.put(Short.class, new ShortGenerator());
        types.put(short.class, new ShortGenerator());
        types.put(Byte.class, new ByteGenerator());
        types.put(byte.class, new ByteGenerator());
        types.put(Double.class, new DoubleGenerator());
        types.put(double.class, new DoubleGenerator());
        types.put(Float.class, new FloatGenerator());
        types.put(float.class, new FloatGenerator());
        types.put(Boolean.class, new BooleanGenerator());
        types.put(boolean.class, new BooleanGenerator());
        types.put(Date.class, new DateGenerator());
        return types;
    }

    private static boolean isSupportedType(Class<?> type) {
        return TYPES.containsKey(type);
    }

    private static Object randomValue(Class<?> type) {
        return TYPES.get(type).randomValue();
    }

    public static void testGettersAndSetters(Object bean) {
        Assert.assertNotNull(bean);
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
            PropertyDescriptor[] properties = beanInfo.getPropertyDescriptors();
            Assert.assertNotNull("No properties for " + bean.getClass(), properties);

            for (PropertyDescriptor property : properties) {
                Class<?> type = property.getPropertyType();

                if (isSupportedType(type)) {
                    boolean canRead = property.getReadMethod() != null;
                    boolean canWrite = property.getWriteMethod() != null;

                    if (canRead && canWrite) {
                        testGetterAndSetter(bean, property.getName(), type);
                    }
                }
            }
        }
        catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    public static void testGetterAndSetter(Object bean, String property, Class<?> type) {
        for (int i = 0; i < 3; i++) {
            Object value = randomValue(type);
            testGetterAndSetter(bean, property, value);
        }
    }

    public static void testGetterAndSetter(Object bean, String property, Object value) {
        try {
            PropertyUtils.setProperty(bean, property, value);
            Object newValue = PropertyUtils.getProperty(bean, property);
            Assert.assertEquals("get/set property " + property, value, newValue);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static interface ValueGenerator {
        public Object randomValue();
    }

    private static class StringGenerator implements ValueGenerator {
        public Object randomValue() {
            return RandomStringUtils.random(10);
        }
    }

    private static class IntegerGenerator implements ValueGenerator {
        public Object randomValue() {
            int value = (int) (Math.random() * Integer.MAX_VALUE);
            return (Math.random() > 0.5) ? value : -value;
        }
    }

    private static class LongGenerator implements ValueGenerator {
        public Object randomValue() {
            long value = (long) (Math.random() * Long.MAX_VALUE);
            return (Math.random() > 0.5) ? value : -value;
        }
    }

    private static class ShortGenerator implements ValueGenerator {
        public Object randomValue() {
            short value = (short) (Math.random() * Short.MAX_VALUE);
            return (Math.random() > 0.5) ? value : -value;
        }
    }

    private static class ByteGenerator implements ValueGenerator {
        public Object randomValue() {
            byte value = (byte) (Math.random() * Byte.MAX_VALUE);
            return (Math.random() > 0.5) ? value : -value;
        }
    }

    private static class DoubleGenerator implements ValueGenerator {
        public Object randomValue() {
            double value = Math.random() * Double.MAX_VALUE;
            return (Math.random() > 0.5) ? value : -value;
        }
    }

    private static class FloatGenerator implements ValueGenerator {
        public Object randomValue() {
            float value = (float) (Math.random() * Float.MAX_VALUE);
            return (Math.random() > 0.5) ? value : -value;
        }
    }

    private static class BooleanGenerator implements ValueGenerator {
        public Object randomValue() {
            return Math.random() > 0.5 ? true : false;
        }
    }

    private static class DateGenerator implements ValueGenerator {
        public Object randomValue() {
            long value = (int) (Math.random() * (Long.MAX_VALUE / 2));
            return new Date(value);
        }
    }
}