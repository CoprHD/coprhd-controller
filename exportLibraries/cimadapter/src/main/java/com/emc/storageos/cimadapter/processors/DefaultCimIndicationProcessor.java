/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.processors;

// Java imports
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;

/**
 * The DefaultCimIndicationProcessor performs default processing of the
 * indication which transforms the indication into a set of name/value pairs
 * returned to the caller in the form of a Hashtable<String, String>.
 */
public class DefaultCimIndicationProcessor extends CimIndicationProcessor {

    // A reference to the CIM connection associated with this processor.
    protected CimConnection _connection = null;

    // A reference to a logger.
    protected static final Logger s_logger = LoggerFactory.getLogger(DefaultCimIndicationProcessor.class);

    /**
     * Constructor
     * 
     * @param connection The CIM connection associated with this processor.
     */
    public DefaultCimIndicationProcessor(CimConnection connection) {
        _connection = connection;
    }

    /**
     * {@inheritDoc}
     */
    public Object process(Object indication) {
        if (!(indication instanceof CIMInstance)) {
            s_logger.debug("Default CIM indication processor expects an instance of CIMInstance.");
            return new Hashtable<String, String>();
        }

        CimIndicationSet result = processIndication((CIMInstance) indication);
        return result.getMap();
    }

    /**
     * Constructs name-value pairs from the given CIM indication.
     * 
     * @param indication The CIM indication.
     * 
     * @return An instance of CimIndicationSet containing the name-value pairs.
     */
    protected CimIndicationSet processIndication(CIMInstance indication) {
        CimIndicationSet eventData;
        eventData = new CimIndicationSet(indication);
        eventData.set(CimConstants.INDICATION_SOURCE_KEY, _connection.getHost());
        setIndicationType(eventData);
        return eventData;
    }

    /**
     * Sets the type for the passed indication.
     * 
     * @param eventData The CIM indication as name/value pairs.
     */
    protected void setIndicationType(CimIndicationSet eventData) {
        String indicationType;
        if (eventData.isAlertIndication()) {
            indicationType = CimConstants.CIM_ALERT_INDICATION_TYPE;
        } else if (eventData.isInstIndication()) {
            indicationType = CimConstants.CIM_INST_INDICATION_TYPE;
        } else {
            indicationType = CimConstants.CIM_INDICATION_TYPE;
        }
        eventData.set(CimConstants.CIM_INDICATION_TYPE_KEY, indicationType);
    }

    /**
     * Returns the reference instance.
     * 
     * @param indication The CIM indication.
     * @param pName The property name that holds the CIM object path.
     * 
     * @return The CIM instance or null it that instance cannot be found.
     */
    protected CIMInstance getInstance(CIMInstance indication, String pName) {
        CIMInstance instance = null;
        CIMProperty<?> property = indication.getProperty(pName);
        if (property != null) {
            String value = property.getValue().toString();
            try {
                CIMObjectPath path = CimObjectPathCreator.createInstance(value);
                instance = _connection.getCimClient().getInstance(path, true, false, null);
            } catch (Exception e) {
                s_logger.debug("Failed getting CIM instance: {}", value, e);
            }
        }
        return instance;
    }

    /**
     * Returns instances associated with a reference object in the given
     * indication.
     * 
     * @param indication The CIM indication.
     * @param pName The property name that holds the CIM object path.
     * @param className The CIM class name of the associated instances.
     * 
     * @return The CIM instance set.
     */
    protected Set<CIMInstance> getAssociatedInstances(CIMInstance indication, String pName, String className) {
        Set<CIMInstance> set = new HashSet<CIMInstance>();
        CIMProperty<?> property = indication.getProperty(pName);
        if (property != null) {
            String value = property.getValue().toString();
            try {
                CIMObjectPath path = CimObjectPathCreator.createInstance(value);
                set = getAssociatedInstances(path, className);
            } catch (Exception e) {
                s_logger.debug(
                        MessageFormatter.arrayFormat("Failed getting associated {} objects for {}",
                                new Object[] { className, value }).getMessage(), e);
            }
        }
        return set;
    }

    /**
     * Returns the instances associated with the given instance.
     * 
     * @param instance The CIM instance.
     * @param className The CIM class name of the associated instances.
     * 
     * @return the CIM instance set.
     */
    protected Set<CIMInstance> getAssociatedInstances(CIMInstance instance, String className) {
        CIMObjectPath path = instance.getObjectPath();
        return getAssociatedInstances(path, className);
    }

    /**
     * Returns the instances associated with the given object path.
     * 
     * @param path The CIM object path.
     * @param className The CIM class name of the associated instances.
     * 
     * @return The CIM instance set.
     */
    protected Set<CIMInstance> getAssociatedInstances(CIMObjectPath path, String className) {
        s_logger.info("Retrieving associated {} objects for {}", new Object[] { className, path });

        Set<CIMInstance> set = new HashSet<CIMInstance>();
        CloseableIterator<CIMInstance> instanceIter = null;
        try {
            instanceIter = _connection.getCimClient().associatorInstances(path, null, className, null, null, false,
                    null);
        } catch (Exception e) {
            s_logger.debug(
                    MessageFormatter.arrayFormat("Failed getting associated {} objects for {}",
                            new Object[] { className, path }).getMessage(), e);
        }

        if (instanceIter == null) {
            return set;
        }

        try {
            while (instanceIter.hasNext()) {
                CIMInstance instance = instanceIter.next();
                s_logger.info("Found instance: {}", instance);
                set.add(instance);
            }
        } catch (RuntimeException e) {
            s_logger.debug(
                    MessageFormatter.arrayFormat("Runtime exception getting associated {} object for {}",
                            new Object[] { className, path }).getMessage(), instanceIter.getWBEMException());
        } finally {
            instanceIter.close();
        }

        return set;
    }
}
