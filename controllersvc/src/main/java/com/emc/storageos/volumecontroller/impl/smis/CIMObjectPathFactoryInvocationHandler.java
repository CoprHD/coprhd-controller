/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Invocation handler for the CIMObjectPathFactoryAdapter internal proxy instance. When a method is invoked
 * on the proxy instance, the method invocation is encoded and dispatched to the invoke method where we
 * decide which factory to delegate to.
 */
public class CIMObjectPathFactoryInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(CIMObjectPathFactoryInvocationHandler.class);
    private static final String MSG = "Delegating %s to %s";

    private enum Factory {
        query, creator
    }

    private Map<Factory, CIMObjectPathFactory> factories;
    private Factory defaultFactory = Factory.creator;
    private Set<String> queryMethods;
    private Set<String> creatorMethods;

    public void setQueryFactory(CIMObjectPathFactory queryFactory) {
        addFactory(Factory.query, queryFactory);
    }

    public void setCreatorFactory(CIMObjectPathFactory creatorFactory) {
        addFactory(Factory.creator, creatorFactory);
    }

    public void setDefaultFactory(String defaultFactory) {
        this.defaultFactory = Factory.valueOf(defaultFactory);
    }

    public void setQueryMethods(Set<String> queryMethods) {
        this.queryMethods = queryMethods;
    }

    public void setCreatorMethods(Set<String> creatorMethods) {
        this.creatorMethods = creatorMethods;
    }

    /**
     * The invoke method is passed the CIMObjectPathFactory method invocation details which
     * we inspect to determine where to delegate to.
     * 
     * @param proxy The dynamic proxy.
     * @param method The encoded method.
     * @param args The arguments passed to the method.
     * @return Based on the CIMObjectPathFactory method.
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Factory useFactory = null;

        if (isQueryMethod(method)) {
            useFactory = Factory.query;
        } else if (isCreatorMethod(method)) {
            useFactory = Factory.creator;
        } else {
            useFactory = defaultFactory;
        }

        return delegate(method, args, factories.get(useFactory));
    }

    private boolean isQueryMethod(Method method) {
        return queryMethods != null && !queryMethods.isEmpty() && queryMethods.contains(method.getName());
    }

    private boolean isCreatorMethod(Method method) {
        return creatorMethods != null && !creatorMethods.isEmpty() && creatorMethods.contains(method.getName());
    }

    private Object delegate(Method method, Object[] args, CIMObjectPathFactory factory)
            throws InvocationTargetException, IllegalAccessException {
        log.info(String.format(MSG, method.getName(), factory.getClass().getSimpleName()));
        return method.invoke(factory, args);
    }

    private void addFactory(Factory name, CIMObjectPathFactory factory) {
        if (factories == null) {
            factories = new HashMap<>();
        }
        factories.put(name, factory);
    }
}
