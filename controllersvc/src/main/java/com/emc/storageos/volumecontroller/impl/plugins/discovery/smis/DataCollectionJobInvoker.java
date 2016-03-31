/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.storagedriver.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.plugins.ExtendedCommunicationInterface;

/**
 * Loads Spring Context based on Profile & DeviceType i.e. Scanner-block
 * ,Discovery-block,Discovery-vnxfile Invokes Scan or Discover Method in
 * plugins, which in turn updates information on Pools,Ports.
 */
class DataCollectionJobInvoker {
    private static final Logger _logger = LoggerFactory.getLogger(DataCollectionJobInvoker.class);
    private static final String DISCOVERY = "Discovery";
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    private NetworkDeviceController _networkDeviceController;
    private ControllerLockingService _locker;
    private AccessProfile _accessProfile;
    private TaskCompleter _completer;
    private ExtendedCommunicationInterface _commInterface;
    private Map<String, String> _configInfo;
    private String _namespace;
    private Registry _registry;

    public DataCollectionJobInvoker(final AccessProfile accessProfile, final Map<String, String> configInfo,
            final DbClient dbClient, final CoordinatorClient coordinator, NetworkDeviceController networkDeviceController,
            final ControllerLockingService locker, String namespace, final TaskCompleter completer) {
        _accessProfile = accessProfile;
        _configInfo = configInfo;
        _dbClient = dbClient;
        _coordinator = coordinator;
        _networkDeviceController = networkDeviceController;
        _locker = locker;
        _namespace = namespace;
        _completer = completer;
    }

    private static Map<String, ApplicationContext> contextKeyToApplicationContextMap = Collections
            .synchronizedMap(new HashMap<String, ApplicationContext>());

    public void process(ApplicationContext parentApplicationContext) throws BaseCollectionException {
        ApplicationContext context = null;
        String contextDeviceType = fetchDeviceType(_accessProfile.getSystemType());
        try {
            String currentThreadName = String.format("%s|%s|%s|%s|%s", Thread.currentThread().getId(),
                    _accessProfile.getSystemType(), _accessProfile.getProfileName(), _accessProfile.getIpAddress(),
                    _completer.getOpId());
            Thread.currentThread().setName(currentThreadName);

            // Discovery-vnxFile | Discovery-block | Discovery-host |
            // Discovery-vcenter
            String contextkey = _accessProfile.getProfileName() + "-" + contextDeviceType;
            if (ControllerServiceImpl.isDiscoveryJobTypeSupported(_accessProfile.getProfileName())) {
                // Discovery-vnxFile-all | Discovery-block-all |
                // CS_Discovery-host-all | CS_Discovery-vcenter-all
                contextkey = contextkey + "-" + _namespace.toLowerCase();
            }
            if (contextDeviceType.equalsIgnoreCase(DiscoveredDataObject.Type.rp.toString())) {
                _logger.info("{} task Started using protection system {} using Namespace {}",
                        new Object[] { _accessProfile.getProfileName(), _accessProfile.getIpAddress(), contextkey });
            } else {
                _logger.info("{} task Started using Provider {} using Namespace {}",
                        new Object[] { _accessProfile.getProfileName(), _accessProfile.getIpAddress(), contextkey });
            }

            if (Constants.COMPUTE.equals(contextDeviceType) && contextKeyToApplicationContextMap.get(contextkey) != null) {
                context = contextKeyToApplicationContextMap.get(contextkey);
            } else {
                // CTRL-10441 fix
                String contextFile = getContextFile(contextkey);
                if (null == contextFile) {
                    // No entry for context key in configinfo map, default to external device context key
                    String externalDeviceContextKey =
                            _accessProfile.getProfileName() + "-" + Constants.EXTERNALDEVICE + "-" + _namespace.toLowerCase();
                    _logger.info("No entry defined for context key: {} . Default to external device context key: {}",
                            contextkey, externalDeviceContextKey);
                    contextkey = externalDeviceContextKey;
                    contextDeviceType = Constants.EXTERNALDEVICE;
                }
                context = new ClassPathXmlApplicationContext(new String[] { getContextFile(contextkey) },
                        parentApplicationContext);

                if (Constants.COMPUTE.equals(contextDeviceType)) {
                    contextKeyToApplicationContextMap.put(contextkey, context);
                }
            }

            _commInterface = (ExtendedCommunicationInterface) context.getBean(contextDeviceType);

            invoke(_commInterface);
            if (contextDeviceType.equalsIgnoreCase(DiscoveredDataObject.Type.rp.toString())) {
                _logger.info("{} task Completed successfully using protection system {}", _accessProfile.getProfileName(),
                        _accessProfile.getIpAddress());
            } else {
                _logger.info("{} task Completed successfully using Provider {}", _accessProfile.getProfileName(),
                        _accessProfile.getIpAddress());
            }
        } finally {

            if (!Constants.COMPUTE.equals(contextDeviceType) && context != null) {
                ((ConfigurableApplicationContext) context).close();
            }
            _commInterface = null;
        }
    }

    /**
     * Returns the context key based on its devicetype.
     * 
     * @param deviceType
     * @return
     */
    private String fetchDeviceType(String deviceType) {
        if (Constants._Block.equalsIgnoreCase(deviceType)
                || DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(deviceType)
                || DiscoveredDataObject.Type.vmax.toString().equalsIgnoreCase(deviceType)) {
            return Constants._Block;
        } else if (Constants.COMPUTE.equalsIgnoreCase(deviceType)) {
            return Constants.COMPUTE;
        }

        return deviceType;
    }

    /**
     * Invoke Scan or Discover based on the profile.
     * 
     * @param commInterface
     * @throws BaseCollectionException
     */
    private void invoke(ExtendedCommunicationInterface commInterface) throws BaseCollectionException {
        commInterface.injectDBClient(_dbClient);
        commInterface.injectCoordinatorClient(_coordinator);
        commInterface.injectNetworkDeviceController(_networkDeviceController);
        commInterface.injectControllerLockingService(_locker);
        commInterface.injectTaskCompleter(_completer);
        if (_accessProfile.getProfileName().equalsIgnoreCase(ControllerServiceImpl.SCANNER)) {
            commInterface.scan(_accessProfile);
        } else if (ControllerServiceImpl.isDiscoveryJobTypeSupported(_accessProfile.getProfileName())) {
            commInterface.discover(_accessProfile);
        } else if (_accessProfile.getProfileName().equalsIgnoreCase(ControllerServiceImpl.METERING)) {
            invokeMetering();
        } else {
            // To-Do add metering too
            throw new RuntimeException("Unsupported Profile Type :" + _accessProfile.getProfileName());
        }
    }

    /**
     * Inject correct dbUtil instance based on deviceType. It could have been
     * put up as a bean in plugin-context.xml, but it would end up in having a
     * DButil dependency onto export Libraries. Hence, instantiating locally.
     * 
     * @throws BaseCollectionException
     */
    private void invokeMetering() throws BaseCollectionException {
        _commInterface.collectStatisticsInformation(_accessProfile);
    }

    /**
     * get Context File based on Context-key (Scanner-block)
     * 
     * @param contextKey
     * @return
     */
    private String getContextFile(String contextKey) {
        String contextFile = null;
        if (null != _configInfo.get(contextKey)) {
            contextFile = _configInfo.get(contextKey);
        } else {
            _logger.warn("Profile name not defined:" + contextKey);
        }
        return contextFile;
    }

    public void setDBClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

}
