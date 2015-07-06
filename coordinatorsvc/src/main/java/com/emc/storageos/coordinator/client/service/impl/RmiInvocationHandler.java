/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.RemoteLookupFailureException;
import org.springframework.remoting.rmi.RmiProxyFactoryBean;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;

/**
 * Invocation handler implementation for RMI endpoints.  This implementation fails over
 * (if more than one endpoint is available) on connection failure.
 */
public class RmiInvocationHandler implements InvocationHandler {
    private static final Logger _log = LoggerFactory.getLogger(RmiInvocationHandler.class);

    private CoordinatorClient _client;
    private String _name, _version, _tag, _endpointKey;
    private Class _endpointInterface;

    private final ConcurrentMap<URI, Object> _proxyMap = new ConcurrentHashMap<URI, Object>();

    public void setCoordinator(CoordinatorClient client) {
        _client = client;
    }

    public void setName(String name) {
        _name = name;
    }

    public void setVersion(String version) {
        _version = version;
    }

    public void setTag(String tag) {
        _tag = tag;
    }

    public void setEndpointInterface(Class endpointInterface) {
        _endpointInterface = endpointInterface;
    }

    public void setEndpointKey(String endpointKey) {
        _endpointKey = endpointKey;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<Service> services = _client.locateAllServices(_name, _version, _tag, _endpointKey);
        if (services == null || services.isEmpty()) {
            throw CoordinatorException.fatals.endPointUnavailable();
        }
        _log.info("Invoking task {}: {} ", method, args);
        Throwable lastError = null;
        for (int index = 0; index < services.size(); index++) {
            Service svc = services.get(index);
            URI endpoint = null;
            if (_endpointKey != null) {
                endpoint = svc.getEndpoint(_endpointKey);
            } else {
                endpoint = svc.getEndpoint();
            }
            Object rmiProxy = _proxyMap.get(endpoint);
            try {
                if (rmiProxy == null) {
                    rmiProxy = createRmiProxy(endpoint);
                }
                _log.info("Sending RMI request to {} ", endpoint);
                return method.invoke(rmiProxy, args);
            } catch (RemoteLookupFailureException e) {
                lastError = e;
                _log.warn("Unable to lookup registry at {}", endpoint);
                continue;
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target instanceof RemoteException ||
                    target instanceof RemoteLookupFailureException) {
                    // fail over to next host
                    lastError = target;
                    _log.warn("Remote exception trying to reach {}", endpoint, target);
                    continue;
                }
                throw target;
            }
        }
        throw CoordinatorException.fatals.unableToConnectToEndpoint(lastError);
    }

    /**
     * Creates and caches RMI proxy
     *
     * @param endpoint
     * @return
     */
    private Object createRmiProxy(URI endpoint) {
        RmiProxyFactoryBean proxyFactory = new RmiProxyFactoryBean();
        proxyFactory.setServiceInterface(_endpointInterface);
        proxyFactory.setServiceUrl(endpoint.toString());
        proxyFactory.setRefreshStubOnConnectFailure(true);
        proxyFactory.setCacheStub(false);
        proxyFactory.afterPropertiesSet();
        Object rmiProxy = proxyFactory.getObject();
        _proxyMap.putIfAbsent(endpoint, rmiProxy);
        return rmiProxy;
    }
}
