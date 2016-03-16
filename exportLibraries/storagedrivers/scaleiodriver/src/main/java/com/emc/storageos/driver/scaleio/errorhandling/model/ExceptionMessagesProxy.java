/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.model;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;
import com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.utils.MessageUtils;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.currentThread;
import static java.lang.reflect.Proxy.newProxyInstance;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * This is the class that does the actual work of generating the Exceptions and messages.
 * You should only ever create an instance of this class by using {@link #create(Class)} and passing in the interface you want proxy.
 * The specified Interface must be annotated with {@link MessageBundle} and all the methods must be annotated with
 * {@link DeclareServiceCode}.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 * 
 * @author fountt1
 */
public final class ExceptionMessagesProxy implements InvocationHandler {

    private static final ConcurrentHashMap<Class<?>, Object> proxyMap = new ConcurrentHashMap<Class<?>, Object>();

    @SuppressWarnings("unchecked")
    public static synchronized <T> T create(final Class<T> interfaze) {
        if (!proxyMap.containsKey(interfaze)) {
            final ClassLoader loader = currentThread().getContextClassLoader();
            final Class<?>[] interfaces = new Class<?>[] { interfaze };
            final InvocationHandler handler = new ExceptionMessagesProxy();
            final T instance = (T) newProxyInstance(loader, interfaces, handler);
            proxyMap.put(interfaze, instance);
        }
        return (T) proxyMap.get(interfaze);
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
            throws Throwable {
        final Class<?> type = type(method);
        final String detailBase = detailBase(method);
        final String detailKey = method.getName();
        final ServiceCode serviceCode = serviceCode(method);
        final Throwable cause = cause(args);
        if (ServiceError.class.isAssignableFrom(type)) {
            return contructServiceError(serviceCode, detailBase, detailKey, convertThrowableMessages(args));
        }
        Exception exception = contructException(type, serviceCode, cause, detailBase,
                detailKey, convertThrowableMessages(args));
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // removing the following 3 stack trace elements from the trackstrace:
        // 0: Thread#getStackTrace
        // 1: ExceptionMessagesProxy#invoke
        // 2: com.sun.proxy.$ProxyXX#<methodName>
        exception.setStackTrace(Arrays.copyOfRange(stackTrace, 3, stackTrace.length));
        return exception;
    }

    /**
     * Converts all Throwable arguments to its message if the message is not null
     * 
     * @param args array of arguments
     * @return array of arguments
     */
    private static Object[] convertThrowableMessages(final Object[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Throwable) {
                    Throwable t = (Throwable) args[i];
                    if (t.getMessage() != null) {
                        args[i] = t.getMessage();
                    }
                }
            }
        }
        return args;
    }

    /**
     * Get the return type of the method and check if it is a valid type
     * 
     * @param method
     * @return
     */
    private Class<?> type(final Method method) {
        final Class<?> type = method.getReturnType();

        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalStateException("Cannot create instances of an abstract class");
        }
        if (!ServiceCoded.class.isAssignableFrom(type)
                || !(Exception.class.isAssignableFrom(type) || ServiceError.class
                        .isAssignableFrom(type))) {
            throw new IllegalStateException(
                    "Return type must be of type ServiceCoded and may also be an Exception");
        }
        return type;
    }

    /**
     * Get the specified {@link ServiceCode} from the {@link DeclareServiceCode} annotation
     * 
     * @param method
     * @return
     */
    private ServiceCode serviceCode(final Method method) {
        final DeclareServiceCode declaredServiceCode = method
                .getAnnotation(DeclareServiceCode.class);
        if (declaredServiceCode == null) {
            throw new IllegalStateException(
                    "A service code must be provided via @DeclareServiceCode");
        }
        final ServiceCode serviceCode = declaredServiceCode.value();
        return serviceCode;
    }

    /**
     * Get the name of the message bundle to use when getting the message for the specified method
     * 
     * @param method
     * @return
     */
    private String detailBase(final Method method) {
        final Class<?> clazz = method.getDeclaringClass();
        final String detailBase = MessageUtils.bundleNameForClass(clazz);
        if (isBlank(detailBase)) {
            throw new IllegalStateException("no bundle name defined for " + clazz);
        }
        return detailBase;
    }

    /**
     * Find the cause argument if it has been past into the method
     * 
     * @param args
     * @return
     */
    private Throwable cause(final Object[] args) {
        Throwable t = null;
        if (args != null) {
            for (final Object arg : args) {
                if (arg instanceof Throwable) {
                    t = (Throwable) arg;
                    break;
                }
            }
        }
        return t;
    }

    /**
     * Construct the Exception being returned by the method, using a known standard constructor
     * 
     * @param type
     * @param serviceCode
     * @param cause
     * @param detailBase
     * @param detailKey
     * @param args
     * @return
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Exception contructException(final Class<?> type, final ServiceCode serviceCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] args) throws NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        final Constructor<?> constructor = type.getDeclaredConstructor(ServiceCode.class,
                Throwable.class, String.class, String.class, Object[].class);
        constructor.setAccessible(true);
        return (Exception) constructor.newInstance(serviceCode, cause, detailBase, detailKey, args);
    }

    private ServiceError contructServiceError(final ServiceCode serviceCode,
            final String detailBase, final String detailKey, final Object[] args)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {
        final Constructor<ServiceError> constructor = ServiceError.class.getDeclaredConstructor(
                ServiceCode.class, String.class, String.class, Object[].class);
        constructor.setAccessible(true);
        return constructor.newInstance(serviceCode, detailBase, detailKey, args);
    }
}
