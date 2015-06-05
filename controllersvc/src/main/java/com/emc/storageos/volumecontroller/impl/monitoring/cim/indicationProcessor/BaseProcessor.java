/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor;

import java.util.Hashtable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.MonitoringPropertiesLoader;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public abstract class BaseProcessor implements ApplicationContextAware {
    /**
     * Reference to MonitoringPropertiesLoader
     */
    @Autowired
    private MonitoringPropertiesLoader _monitoringPropertiesLoader;

    /**
     * A reference to hold RecordableEventManager instance - Auto injected
     */
    @Autowired
    protected RecordableEventManager _recordableEventManager;

    /**
     * Reference to Spring Application Context
     */
    private ApplicationContext _applicationContext;
    
    /**
     * Getter method of recordable event manager
     * @return
     */
    public RecordableEventManager getRecordableEventManager() {
        return _recordableEventManager;
    }

    /**
     * Setter of recordable event manager
     * @param recordableEventManager
     */
    public void setRecordableEventManager(
            RecordableEventManager recordableEventManager) {
        _recordableEventManager = recordableEventManager;
    }
 
    /**
     * Getter of ApplicationContext
     * @return
     */
    public ApplicationContext getApplicationContext() {
        return _applicationContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        _applicationContext = applicationContext;
        
    }
    /**
     * @return
     */
    public MonitoringPropertiesLoader getMonitoringPropertiesLoader() {
        return _monitoringPropertiesLoader;
    }

    /**
     * Setter for propertiesLoader
     * @param monitoringPropertiesLoader
     */
    public void setMonitoringPropertiesLoader(
            MonitoringPropertiesLoader monitoringPropertiesLoader) {
        _monitoringPropertiesLoader = monitoringPropertiesLoader;
    }
    
    /**
     * Retrieves the event type from indication
     * 
     * @param notification
     * @return
     */
    protected String getEventType(Hashtable<String, String> notification) {
        String instanceEventType = notification.get(CIMConstants.SOURCE_INSTANCE_MODEL_PATH_CLASS_SUFFIX_TAG);
        return instanceEventType;
    }

    /**
     * Process the key value pairs of indications passed as parameter
     * 
     * @param notification
     */
    public abstract void processIndication(Hashtable<String, String> notification);
}
