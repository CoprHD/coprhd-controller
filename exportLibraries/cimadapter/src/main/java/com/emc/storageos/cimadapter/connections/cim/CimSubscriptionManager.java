/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.connections.cim;

// java imports
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.ConnectionManagerException;

/**
 * CIM subscription manager that serves a CIM connection. It handles the
 * creation and destruction of indication filter, destination and subscription
 * objects on the CIMOM.
 */
public class CimSubscriptionManager {

    // The connection associated with the subscription manager.
    private CimConnection _connection;
    
    // An identifier that is used in the filter and handler names of subscriptions
    // that provides a means for subscriptions to identified.
    private String _subscriptionsIdentifier = null;

    // The CIM object path for the indication handler.
    private CIMObjectPath _handlerPath = null;

    // The CIM object paths for the subscribed filters.
    private Set<CIMObjectPath> _filterPaths = new HashSet<CIMObjectPath>();

    // The CIM object paths for the managed subscriptions.
    private Set<CIMObjectPath> _subscriptionPaths = new HashSet<CIMObjectPath>();

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(CimSubscriptionManager.class);

    /**
     * Constructs a CIM subscription manager for the given CIM connection.
     * 
     * @param connection The CIM connection.
     * @param subscriptionsIdentifier The identifier that is used in the filter
     *        and handler names of created subscriptions.
     */
    public CimSubscriptionManager(CimConnection connection, String subscriptionsIdentifier) {
        _connection = connection;
        _subscriptionsIdentifier = subscriptionsIdentifier;
    }

    /**
     * Creates subscription objects using the CIM connection's indication filter
     * map and listener configuration.
     * 
     * @throws WBEMException, Exception
     */
    public void subscribe() throws WBEMException, Exception {
        s_logger.info("Subscribing to indications");

        CimFilterMap filterMap = _connection.getIndicationFilterMap();
        try {
            getHandler();
            for (CimFilterInfo filterInfo : filterMap.getFilters().values()) {
                try {
                    createSubscription(filterInfo);
                } catch (WBEMException e) {
                    if (e.getID() != WBEMException.CIM_ERR_ALREADY_EXISTS) {
                        throw e;
                    }
                    s_logger.debug("Subscription for filter {} already exists", filterInfo.getName());
                }
            }
        } catch (WBEMException e) {
            if (e.getID() != WBEMException.CIM_ERR_ALREADY_EXISTS) {
                throw e;
            }
            s_logger.debug("Subscription handler already exists.");
        }
    }

    /**
     * Destroys subscription objects. 
     */
    public void unsubscribe() {
        s_logger.info("Unsubscribing to indications");

        Set<CIMObjectPath> subscriptionsPaths = _subscriptionPaths;

        // Find and destroy all subscriptions that look like they were created
        // by this manager's connection. Doing this instead of just using the
        // manager's list cleans up stale objects on the CIMOM.
        try {
            subscriptionsPaths = enumerateSubscriptions();
        } catch (Exception e) {
            s_logger.warn("Failure enumerating all subscriptions for {}", _connection.getConnectionName(), e);
        }

        Iterator<CIMObjectPath> pathsIter = subscriptionsPaths.iterator();
        while (pathsIter.hasNext()) {
            deleteInstance(pathsIter.next());
        }

        // Destroy filters.
        pathsIter = _filterPaths.iterator();
        while (pathsIter.hasNext()) {
            deleteInstance(pathsIter.next());
        }

        // Destroy the handler path.
        if (_handlerPath != null) {
            deleteInstance(_handlerPath);
        }
    }
    
    /**
     * Deletes stale instances on the CIM provider that are associated with
     * stale subscriptions. The connection manager can be configured to
     * optionally call this function when establishing a connection to the
     * provider for the purpose of cleaning up old subscriptions from previous
     * connections to the provider that were not properly cleaned up.
     * 
     * @throws WBEMException Enumerating the subscription instances on the
     *         provider.
     */
    public void deleteStaleSubscriptions() throws WBEMException {

        CIMInstance subscription;
        CIMProperty<?> property;
        CIMObjectPath subscriptionHandlerPath;
        CIMObjectPath subscriptionFilterPath;
        String subscriptionFilterName;
        String subscriptionHandlerName;
        Set<CIMObjectPath> staleSubscriptionSet = new HashSet<CIMObjectPath>();
        Map<String, CIMObjectPath> staleFilterMap = new HashMap<String, CIMObjectPath>();
        Map<String, CIMObjectPath> staleHandlerMap = new HashMap<String, CIMObjectPath>();

        // Get and loop over all subscriptions.
        WBEMClient cimClient = _connection.getCimClient();
        CIMObjectPath subscriptionPath = CimObjectPathCreator.createInstance(CimConstants.CIM_SUBSCRIPTION_NAME,
            _connection.getInteropNamespace());
        CloseableIterator<CIMInstance> subscriptionIter = null;
        try {
            subscriptionIter =
                    cimClient.enumerateInstances(subscriptionPath, true, true,false,
                            null);
            while (subscriptionIter.hasNext()) {
                subscription = subscriptionIter.next();

                // Get the handler for the subscription.
                property = subscription.getProperty(CimConstants.SUBSCRIPTION_PROP_HANDLER);
                subscriptionHandlerPath = (CIMObjectPath) property.getValue();

                // If the name of the handler contains the passed subscription
                // identifier then this is a stale subscription to be deleted.
                // Note that subscriptions themselves are not assigned names
                // when they are created. However, both the handler and filter
                // associated with the subscription are assigned names, and the
                // name contains the subscription identifier configured for
                // the connection manager. Therefore, we could check either the
                // handler or the filter.
                subscriptionHandlerName = subscriptionHandlerPath.getKey(CimConstants.NAME_KEY).getValue().toString();
                if (subscriptionHandlerName.contains(_subscriptionsIdentifier)) {
                    // Add the subscription to the stale subscription set.
                    staleSubscriptionSet.add(subscription.getObjectPath());

                    // Add the handler to the stale handler map, if not already
                    // added. Multiple subscriptions can reference the same
                    // handler.
                    if (!staleHandlerMap.keySet().contains(subscriptionHandlerName)) {
                        staleHandlerMap.put(subscriptionHandlerName, subscriptionHandlerPath);
                    }

                    // Now get and add the filter for the subscription to the stale
                    // filters map. Again, it is possible that multiple
                    // subscriptions reference the same filter.
                    property = subscription.getProperty(CimConstants.SUBSCRIPTION_PROP_FILTER);
                    subscriptionFilterPath = (CIMObjectPath) property.getValue();
                    subscriptionFilterName = subscriptionFilterPath.getKey(CimConstants.NAME_KEY).getValue().toString();
                    if (!staleFilterMap.keySet().contains(subscriptionFilterName)) {
                        staleFilterMap.put(subscriptionFilterName, subscriptionFilterPath);
                    }
                }
            }

            // Delete the stale subscriptions first, which reference the filters and
            // handlers.
            Iterator<CIMObjectPath> pathsIter = staleSubscriptionSet.iterator();
            while (pathsIter.hasNext()) {
                deleteInstance(pathsIter.next());
            }

            // Next delete the stale filters.
            pathsIter = staleFilterMap.values().iterator();
            while (pathsIter.hasNext()) {
                deleteInstance(pathsIter.next());
            }

            // Finally, delete the stale handlers.
            pathsIter = staleHandlerMap.values().iterator();
            while (pathsIter.hasNext()) {
                deleteInstance(pathsIter.next());
            }
        } finally {
            if (subscriptionIter != null) {
                subscriptionIter.close();
            }
        }
    }
    
    /**
     * Finds all of the CIM_Subscription objects that look like they were
     * created for this manager's connection. This is useful for rounding up and
     * removing stale subscriptions in the CIMOM.
     * 
     * This works by looking for subscriptions that are attached to the handler
     * for this connection. The name of the handler is fixed by its destination
     * properties, so all subscriptions for a destination are going to be
     * attached to the same handler whose name is more or less predictable (if
     * created by this class).
     * 
     * @return The set of subscriptions that look like they were created for
     *         this manager's connection.
     * 
     * @throws WBEMException, ConnectionManagerException
     */
    private Set<CIMObjectPath> enumerateSubscriptions() throws WBEMException, ConnectionManagerException {
        CIMInstance subscription;
        CIMProperty<?> property;
        CIMObjectPath subscriptionHandlerPath;
        Object subscriptionHandlerName;
        Set<CIMObjectPath> subscriptionSet = new HashSet<CIMObjectPath>();
        WBEMClient cimClient = _connection.getCimClient();
        String interopNS = _connection.getInteropNamespace();
        CIMObjectPath subscriptionPath = CimObjectPathCreator.createInstance(CimConstants.CIM_SUBSCRIPTION_NAME, interopNS);
        CloseableIterator<CIMInstance> subscriptionIter = null;
        try {
            subscriptionIter =
                    cimClient.enumerateInstances(subscriptionPath, true, true, false,
                            null);
            while (subscriptionIter.hasNext()) {
                subscription = subscriptionIter.next();
                property = subscription.getProperty(CimConstants.SUBSCRIPTION_PROP_HANDLER);
                subscriptionHandlerPath = (CIMObjectPath) property.getValue();
                subscriptionHandlerName = subscriptionHandlerPath.getKey(CimConstants.NAME_KEY).getValue();
                if (subscriptionHandlerName.equals(getHandler().getKey(CimConstants.NAME_KEY).getValue())) {
                    subscriptionPath = subscription.getObjectPath();
                    s_logger.debug("Found: {}", subscriptionPath);
                    subscriptionSet.add(subscriptionPath);
                }
            }
        } finally {
            if (subscriptionIter != null) {
                subscriptionIter.close();
            }
        }
        return subscriptionSet;
    }

    /**
     * Creates an indication subscription in the CIMOM for the given filter.
     * 
     * @param filterInfo the filter information.
     * 
     * @return the CIM object path of the subscription
     * 
     * @throws WBEMException, ConnectionManagerException
     */
    protected CIMObjectPath createSubscription(CimFilterInfo filterInfo) throws WBEMException,
        ConnectionManagerException {
        CIMObjectPath filterPath;
        if (filterInfo instanceof CimManagedFilterInfo) {
            filterPath = createFilter((CimManagedFilterInfo) filterInfo);
        } else {
            filterPath = getInstance(CimConstants.CIM_FILTER_NAME, filterInfo.getName()).getObjectPath();
        }
        s_logger.trace("filterPath :{}",filterPath);
        CIMProperty<?> filterProp = new CIMProperty<CIMObjectPath>(CimConstants.SUBSCRIPTION_PROP_FILTER,
            new CIMDataType(CimConstants.CIM_FILTER_NAME), filterPath);

        CIMProperty<?> handlerProp = new CIMProperty<CIMObjectPath>(CimConstants.SUBSCRIPTION_PROP_HANDLER,
            new CIMDataType(CimConstants.CIM_HANDLER_NAME), getHandler());
        s_logger.trace("filterProp :{}",filterProp);
        s_logger.trace("handlerProp :{}",handlerProp);
        CIMProperty<?>[] subscriptionProperties = new CIMProperty[] { filterProp, handlerProp };
        CIMObjectPath subscriptionPath = createInstance(CimConstants.CIM_SUBSCRIPTION_NAME, subscriptionProperties);
        _subscriptionPaths.add(subscriptionPath);
        s_logger.trace("subscriptionPath :{}",subscriptionPath);
        return subscriptionPath;
    }

    /**
     * Creates an indication filter in the CIMOM for the given configuration.
     * 
     * @param filterInfo The filter information.
     * 
     * @return The CIM object path of the filter object.
     * 
     * @throws WBEMException
     */
    private CIMObjectPath createFilter(CimManagedFilterInfo filterInfo) throws WBEMException {
        StringBuilder filterNameBuilder = new StringBuilder();
        filterNameBuilder.append(_subscriptionsIdentifier);
        filterNameBuilder.append(CimConstants.PATH_NAME_DELIMITER);
        filterNameBuilder.append(filterInfo.getName());
        String filterName = filterNameBuilder.toString();
        String implNS = _connection.getImplNamespace();
        CIMProperty<?> nameProperty = new CIMProperty<String>(CimConstants.NAME_KEY, CIMDataType.STRING_T, filterName);
        CIMProperty<?> srcNamespaceProp = new CIMProperty<String>(CimConstants.FILTER_PROP_SRC_NAMESPACE,
            CIMDataType.STRING_T, implNS);
        CIMProperty<?> srcNamespacesProp = new CIMProperty<String[]>(CimConstants.FILTER_PROP_SRC_NAMESPACES,
            CIMDataType.STRING_ARRAY_T, new String[] { implNS });
        CIMProperty<?> queryLangProp = new CIMProperty<String>(CimConstants.FILTER_PROP_QUERY_LANGUAGE,
            CIMDataType.STRING_T, filterInfo.getQueryLanguage());
        CIMProperty<?> queryProp = new CIMProperty<String>(CimConstants.FILTER_PROP_QUERY, CIMDataType.STRING_T,
            filterInfo.getQuery());

        CIMProperty<?>[] filterProperties = new CIMProperty[] { nameProperty, srcNamespaceProp, srcNamespacesProp,
            queryLangProp, queryProp };

        CIMObjectPath filterPath = createInstance(CimConstants.CIM_FILTER_NAME, filterName, filterProperties);
        _filterPaths.add(filterPath);
        return filterPath;
    }

    /**
     * Creates an indication handler in the CIMOM from the indication listener's
     * configuration.
     * 
     * The WBEM listener interface does not provide a mechanism for getting the
     * source IP address of a received indication. To match indications with
     * connections, the connection name is put in the handler's destination URL
     * as the path component.
     * 
     * @return the CIM object path of the handler
     * @throws WBEMException, ConnectionManagerException
     */
    private CIMObjectPath createHandler() throws WBEMException, ConnectionManagerException {
        CimListener listener = _connection.getIndicationListener();
        URL listenerURL = listener.getURL();
        if (listenerURL == null) {
            // Verify that the listener URL has been set.
            throw new ConnectionManagerException("Listener URL is not set, Subscription handler cannot be set.");
        }

        StringBuffer handlerNameBuff = new StringBuffer();
        handlerNameBuff.append(_subscriptionsIdentifier);
        handlerNameBuff.append(CimConstants.PATH_NAME_DELIMITER);
        handlerNameBuff.append(listenerURL.getHost());
        handlerNameBuff.append(CimConstants.PATH_NAME_DELIMITER);
        handlerNameBuff.append(listenerURL.getPort());
        String handlerName = handlerNameBuff.toString();

        CIMProperty<?> destinationProperty = new CIMProperty<String>(CimConstants.HANLDER_PROP_DESTINATION,
            CIMDataType.STRING_T, listenerURL.toString() + '/' + _connection.getConnectionName());
        CIMProperty<?>[] handlerProperties = new CIMProperty[] { destinationProperty };
        return createInstance(CimConstants.CIM_HANDLER_NAME, handlerName, handlerProperties);
    }

    /**
     * Gets the indication handler in the CIMOM.
     * 
     * If the handler does not exist, this method creates it.
     * 
     * @return the CIM object path of the handler.
     * 
     * @throws WBEMException, ConnectionManagerException
     */
    private CIMObjectPath getHandler() throws WBEMException, ConnectionManagerException {
        if (_handlerPath == null) {
            _handlerPath = createHandler();
        }

        return _handlerPath;
    }

    /**
     * Creates an instance in the CIMOM.
     * 
     * If the instance already exists, its object path is returned.
     * 
     * @param className The CIM class name.
     * @param name The instance name.
     * @param Properties The CIM properties.
     * 
     * @return The CIM object path of the instance.
     * 
     * @throws WBEMException
     */
    private CIMObjectPath createInstance(String className, String name, CIMProperty<?>[] properties)
        throws WBEMException {
        CIMProperty<?>[] array = new CIMProperty<?>[properties.length + 1];
        System.arraycopy(properties, 0, array, 0, properties.length);
        array[properties.length] = new CIMProperty<String>(CimConstants.NAME_KEY, CIMDataType.STRING_T, name);
        properties = array;
        try {
            return createInstance(className, properties);
        } catch (WBEMException e) {
            if (e.getID() == WBEMException.CIM_ERR_ALREADY_EXISTS) {
                CIMInstance instance = getInstance(className, name);
                if (instance != null) {
                    return instance.getObjectPath();
                }
            }
            throw e;
        }
    }

    /**
     * Creates an instance in the CIMOM.
     * 
     * The instance name is included as one of the properties and/or derived by
     * the CIMOM.
     * 
     * @param className The CIM class name.
     * @param properties The CIM properties.
     * 
     * @return The CIM object path of the instance.
     * 
     * @throws WBEMException
     */
    private CIMObjectPath createInstance(String className, CIMProperty<?>[] properties) throws WBEMException {
        s_logger.trace("className :{}",className);
        s_logger.trace("properties :{}",properties);
        WBEMClient cimClient = _connection.getCimClient();
        String interopNS = _connection.getInteropNamespace();
        CIMObjectPath path = CimObjectPathCreator.createInstance(className, interopNS);
        CIMInstance instance = new CIMInstance(path, properties);
       s_logger.trace("interopNS :{}",interopNS);
       s_logger.trace("path :{}",path);
        path = cimClient.createInstance(instance);
        s_logger.debug("Created: {}", path);
        return path;
    }

    /**
     * Gets the named instance from the CIMOM.
     * 
     * @param className The CIM class name.
     * @param name The CIM Name property value.
     * 
     * @return The CIM instance or null
     * 
     * @throws javax.wbem.WBEMException
     */
    private CIMInstance getInstance(String className, String name) throws WBEMException {
        CIMInstance instance = null;
        WBEMClient cimClient = _connection.getCimClient();
        String interopNS = _connection.getInteropNamespace();
        CIMObjectPath path = CimObjectPathCreator.createInstance(className, interopNS);
        CloseableIterator<CIMInstance> instanceIter = null;
        CIMProperty<?> property;
        try {
            instanceIter = cimClient.enumerateInstances(path, true, true, false, null);
            while (instanceIter.hasNext()) {
                instance = instanceIter.next();
                property = instance.getProperty(CimConstants.NAME_KEY);
                if (property.getValue().toString().equals(name)) {
                    s_logger.debug("Found: {}", instance.getObjectPath());
                    break;
                }
                instance = null;
            }
        } finally {
            if (instanceIter != null) {
                instanceIter.close();
            }
        }
        return instance;
    }

    /**
     * Deletes the instance with the given object path from the CIMOM.
     * 
     * @param path The CIM object path.
     */
    protected void deleteInstance(CIMObjectPath path) {
        try {
            _connection.getCimClient().deleteInstance(path);
            s_logger.info("Deleted: {}", path);
        } catch (WBEMException e) {
            s_logger.error("Failed to delete {}", path, e);
        }
    }
}