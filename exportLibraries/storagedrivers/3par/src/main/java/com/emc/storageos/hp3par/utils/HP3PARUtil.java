/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.utils;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.PortCommandResult;
import com.emc.storageos.hp3par.command.PortMembers;
import com.emc.storageos.hp3par.command.PortStatMembers;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.connection.HP3PARApiFactory;
import com.emc.storageos.hp3par.impl.HP3PARApi;
import com.emc.storageos.hp3par.impl.HP3PARException;
import com.emc.storageos.hp3par.impl.HP3PARIngestHelper;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StoragePort.TransportType;

public class HP3PARUtil {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARUtil.class);
	
	private HP3PARApiFactory hp3parApiFactory;
	
	public HP3PARApi getHP3PARDevice(StorageSystem hp3parSystem) throws HP3PARException {
		URI deviceURI;
		_log.info("3PARDriver:getHP3PARDevice input storage system");

		try {
			deviceURI = new URI("https", null, hp3parSystem.getIpAddress(), hp3parSystem.getPortNumber(), "/", null,
					null);
			return hp3parApiFactory.getRESTClient(deviceURI, hp3parSystem.getUsername(), hp3parSystem.getPassword());
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("3PARDriver:Error in getting 3PAR device, with StorageSystem");
			throw new HP3PARException("Error in getting 3PAR device");
		}
	}

	public HP3PARApi getHP3PARDevice(String ip, String port, String user, String pass) throws HP3PARException {
		URI deviceURI;
		_log.info("3PARDriver:getHP3PARDevice input full details");

		try {
			deviceURI = new URI("https", null, ip, Integer.parseInt(port), "/", null, null);
			return hp3parApiFactory.getRESTClient(deviceURI, user, pass);
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("3PARDriver:Error in getting 3PAR device with details");
			throw new HP3PARException("Error in getting 3PAR device");
		}
	}

	public HP3PARApi getHP3PARDeviceFromNativeId(String nativeId, Registry driverRegistry) throws HP3PARException {
		try {
			Map<String, List<String>> connectionInfo = driverRegistry
					.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, nativeId);
			List<String> ipAddress = connectionInfo.get(HP3PARConstants.IP_ADDRESS);
			List<String> portNumber = connectionInfo.get(HP3PARConstants.PORT_NUMBER);
			List<String> userName = connectionInfo.get(HP3PARConstants.USER_NAME);
			List<String> password = connectionInfo.get(HP3PARConstants.PASSWORD);
			HP3PARApi hp3parApi = getHP3PARDevice(ipAddress.get(0), portNumber.get(0), userName.get(0),
					password.get(0));
			return hp3parApi;
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("3PARDriver:Error in getting 3PAR device with nativeId");
			throw new HP3PARException("Error in getting 3PAR device");
		}
	}

	/**
	 * Get storage port information
	 * @throws Exception 
	 */	
	public void discoverStoragePortsById(String storageSystemId, List<StoragePort> storagePorts, Registry driverRegistery) throws Exception {
        //For this 3PAR system       
		try {
            // get Api client
            HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystemId, driverRegistery);

            // get storage port details
            PortCommandResult portResult = hp3parApi.getPortDetails();
            PortStatisticsCommandResult portStatResult = hp3parApi.getPortStatisticsDetail();

            // for each ViPR Storage port = 3PAR host port
            for (PortMembers currMember:portResult.getMembers()) {
                StoragePort port = new StoragePort();

                // Consider online target ports 
                if (currMember.getMode() != HP3PARConstants.MODE_TARGET ||
                        currMember.getLinkState() != HP3PARConstants.LINK_READY) {
                    continue;
                }
                
                if (currMember.getLabel() == null) {
                    String label = String.format("port:%s:%s:%s", currMember.getPortPos().getNode(),
                            currMember.getPortPos().getSlot(), currMember.getPortPos().getCardPort());
                    port.setPortName(label);
                } else {
                    port.setPortName(currMember.getLabel());
                    }
                
                port.setStorageSystemId(storageSystemId);

                switch(currMember.getProtocol()) {
                    case 1:
                        port.setTransportType(TransportType.FC);
                        break;
                    case 3:
                        port.setTransportType(TransportType.Ethernet);
                        break;
                    case 2:
                        port.setTransportType(TransportType.IP);
                        break;
                    default:
                        _log.warn("3PARDriver: discoverStoragePorts Invalid port {}", port.getPortName());
                        break;
                }


                for (PortStatMembers currStat:portStatResult.getMembers()) {
               		if (currMember.getPortPos().getNode() == currStat.getNode() && 
               			currMember.getPortPos().getSlot() == currStat.getSlot() && 
               			currMember.getPortPos().getCardPort() == currStat.getCardPort()) {
                 	    port.setPortSpeed(currStat.getSpeed() * HP3PARConstants.MEGA_BYTE);
               		}
               	}

                // grouping with cluster node and slot
                port.setPortGroup(currMember.getPortPos().getNode().toString());
                port.setPortSubGroup(currMember.getPortPos().getSlot().toString());

                // set specific properties based on protocol
                if (port.getTransportType().equals(TransportType.FC.toString())) {
                    port.setPortNetworkId(SanUtils.formatWWN(currMember.getPortWWN()));
                    // rest of the values
                    port.setEndPointID(port.getPortNetworkId());
                    port.setTcpPortNumber((long)0);
                } else if (port.getTransportType().equals(TransportType.IP.toString())){
                    port.setIpAddress(currMember.getIPAddr());
                    port.setPortNetworkId(currMember.getiSCSINmae());
                    port.setTcpPortNumber(currMember.getiSCSIPortInfo().getiSNSPort());
                    // rest of the values                    
                    port.setEndPointID(port.getPortNetworkId());
                }
               
                port.setAvgBandwidth(port.getPortSpeed());
                port.setPortHAZone(String.format("Group-%s", currMember.getPortPos().getNode()));
                
                String id = String.format("%s:%s:%s", currMember.getPortPos().getNode(),
                        currMember.getPortPos().getSlot(), currMember.getPortPos().getCardPort());
    
                // Storage object properties
                port.setNativeId(id);
                port.setDeviceLabel(port.getPortName());
                port.setDisplayName(port.getPortName());                
                port.setOperationalStatus(StoragePort.OperationalStatus.OK);  
                _log.info("3PARDriver: added storage port {}, native id {}",  port.getPortName(), port.getNativeId());
                storagePorts.add(port);
            } //for each storage pool

        } catch (Exception e) {
            throw e;
        }
       
    }

	public void saveInRegistry(String key, Map<String, List<String>> attributes, Registry driverRegistry){
		driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, key, attributes);
	}
	
	public Map<String, List<String>> loadFromRegistry(String key, Registry driverRegistry){
		return driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, key);
	}

	
	public HP3PARApiFactory getHp3parApiFactory() {
		return hp3parApiFactory;
	}

	public void setHp3parApiFactory(HP3PARApiFactory hp3parApiFactory) {
		this.hp3parApiFactory = hp3parApiFactory;
	}

}
