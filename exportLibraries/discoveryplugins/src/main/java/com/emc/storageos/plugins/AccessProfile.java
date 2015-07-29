/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins;

import java.net.URI;
import java.util.Map;

/**
 * AccessProfiles are designed per protocol basis.i.e. we have an AccessProfile
 * defined for SMI-S , and a new accessProfile for VNXFile.
 * 
 * For each protocol, we need an AccessProfile very specific to it.SMI-S
 * Protocol might need a different set of credentials from XHMP used by NAS. And
 * also, to formalize these different AccessProfiles, we need an Interface
 * IAccessProfile Interface.
 * 
 * Again, the problem here is deciding out the methods need to get included in
 * this interface.Each protocol's behaviour needs a different type of
 * information, for example SMI-S would need SSLEnabled info, and XHMP for NAS
 * doesnt need those. Hence instead create a generic AccessProfile Object
 * 
 * 1. AccessProfile { setUserName() setPassword() setSMI-SXYZ() - very specific
 * for SMI-S setXHMPXYZ() - specific for XHMP }
 * 
 * Even though few methods are very specific to protocols, we put them in a
 * generic Access Profile Class to formalize AccessProfiles for different
 * protocols. In connectStorage Method, create SMI AccessProfile,
 * SMIAccessProfile.setUserName(device.getUserName)
 * 
 * 
 * 2. IAccessProfile { Map<key,Value> getValues() }
 * 
 * Here in this case, in ConnectStorage Method, once you get the Device Object
 * from Cassandra, populate this Map. This Map<key,Value> will be sent to each
 * plugin, and its the plugin Responsibility to extract their respective
 * information from this Map.
 * 
 * What would be the best way to do this from the above. For now, going with the
 * 1st approach.
 * 
 */
public class AccessProfile {
    /**
     * Profile Name
     */
    private String _profileName;
    /**
     * User Name
     */
    private String _userName;
    /**
     * Password
     */
    private String _password;
    /**
     * SSL Enable
     */
    private String _sslEnable;
    /**
     * Provider Port
     */
    private String _providerPort;
    /**
     * Interop Namespace
     */
    private String _interopNamespace;
    /**
     * Protocol
     */
    private String _protocol;
    /**
     * IP address
     */
    private String _ipAddress;
    /**
     * namespace - Performance or Capacity..
     */
    private String _namespace;
    /**
     * ElementType - Switch or Array
     */
    private String _elementType;
    /**
     * Array Serial ID
     */
    private String _serialID;
    /**
     * DeviceType
     */
    private String _systemType;
    /**
     * task Timeout
     * 
     */
    private int _tasktimeOut;
    /**
     * Port Number
     */
    private int _portNumber;
    /**
     * Record URI of any Model
     */
    private URI _systemId;
    /**
     * Record type
     */
    private Class _recordClazz;

    /**
     * Last sample collected timestamp from the array
     */
    private long _lastSampleTime;

    /**
     * Current timestamp for data collection
     */
    private long _currentSampleTime;

    private URI _providerAccessInfo;
    /**
     * Holds the instance of CIMConnectionFactory.
     */
    private Object _cimConnectionFactory;
    /**
     * AutoRegister Devices or Components
     */
    private String _registerType;

    private Map<String, StorageSystemViewObject> _cache;

    private Map<String, String> _props;

    private Object _eventManager;

    /**
     * Constructor
     */
    public AccessProfile() {
    }

    /**
     * Getter for profileName
     * 
     * @return : value of profileName
     */
    public String getProfileName() {
        return _profileName;
    }

    /**
     * Setter for profileName
     * 
     * @param profileName
     *            : value of displayname for profile
     */
    public void setProfileName(String profileName) {
        _profileName = profileName;
    }

    /**
     * Getter for protocol value. If ssl_enable is true, protocol is "https"
     * otherwise "http"
     * 
     * @return : value of protocol
     */
    public String getProtocol() {
        if (_protocol == null) {
            if (_sslEnable == null) {
                return _protocol = "http";
            }
            else if (_sslEnable.equalsIgnoreCase("true")) {
                _protocol = "https";
            } else {
                _protocol = "http";
            }
        }
        return _protocol;
    }

    /**
     * Setter for username credential for AccessProfile
     * 
     * @param userName
     *            : value of userName
     */
    public void setUserName(String userName) {
        _userName = userName;
    }

    /**
     * Setter for password credential for AccessProfile
     * 
     * @param password
     *            : value of userName
     */
    public void setPassword(String password) {
        _password = password;
    }

    /**
     * Setter for ssl_enable i.e., if the provider has SSL enabled.
     * 
     * @param sslEnable
     *            : "true" is ssl enabled otherwise "false"
     */
    public void setSslEnable(String sslEnable) {
        _sslEnable = sslEnable;
    }

    public String getSslEnable() {
        return _sslEnable;
    }

    /**
     * Setter for provider_port
     * 
     * @param providerPort
     *            : port value
     */
    public void setProviderPort(String providerPort) {
        _providerPort = providerPort;
    }

    /**
     * Setter for provider_port
     * 
     * @param port
     *            : port value
     */
    public void setPortNumber(int port) {
        _portNumber = port;
    }

    /**
     * Setter for interop namespace for SMI-S provider.
     * 
     * @param interopNamespace
     *            : The interop namespace for the provider
     */
    public void setInteropNamespace(String interopNamespace) {
        _interopNamespace = interopNamespace;
    }

    /**
     * Getter for userName
     * 
     * @return : value of userName
     */
    public String getUserName() {
        return _userName;
    }

    /**
     * Getter for password
     * 
     * @return : value of password
     */
    public String getPassword() {
        return _password;
    }

    /**
     * Getter for provider_port
     * 
     * @return : value of provider_port
     */
    public String getProviderPort() {
        return _providerPort;
    }

    /**
     * Getter for provider_port
     * 
     * @return : value of provider_port
     */
    public int getPortNumber() {
        return _portNumber;
    }

    /**
     * Getter for interop namespace
     * 
     * @return : value of interop_namespace
     */
    public String getInteropNamespace() {
        return _interopNamespace;
    }

    /**
     * To String()
     */
    public String toString() {
        StringBuilder profile = new StringBuilder();
        profile.append(" IpAddress : ");
        profile.append(getIpAddress());
        profile.append(" UserName : ");
        profile.append(getUserName());
        profile.append(" Password : ");
        profile.append("*******"); // This was getting into the logging, so obfuscate
        profile.append(" InteropNamespace : ");
        profile.append(getInteropNamespace());
        profile.append(" Protocol : ");
        profile.append(getProtocol());
        String port = getProviderPort();
        if (port != null) {
            profile.append(" ProviderPort : ");
            profile.append(getProviderPort());
        }
        else {
            profile.append(" Port : ");
            profile.append(getPortNumber());
        }
        return profile.toString();
    }

    /**
     * IP Address
     * 
     * @return
     */
    public String getIpAddress() {
        return _ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        _ipAddress = ipAddress;
    }

    /**
     * Namespace
     * 
     * @param namespace
     */
    public void setnamespace(String namespace) {
        _namespace = namespace;
    }

    public String getnamespace() {
        return _namespace;
    }

    /**
     * ElementType
     * 
     * @param elementType
     */
    public void setelementType(String elementType) {
        _elementType = elementType;
    }

    public String getelementType() {
        return _elementType;
    }

    /**
     * serial ID
     * 
     * @param serialID
     */
    public void setserialID(String serialID) {
        _serialID = serialID;
    }

    public String getserialID() {
        return _serialID;
    }

    public void setSystemType(String _systemType) {
        this._systemType = _systemType;
    }

    public String getSystemType() {
        return _systemType;
    }

    public void setTaskTimeout(int tasktimeOut) {
        _tasktimeOut = tasktimeOut;
    }

    public int getTaskTimeout() {
        return _tasktimeOut;
    }

    /**
     * Get last sample time on profile
     * 
     * @return
     */
    public long getLastSampleTime() {
        return _lastSampleTime;
    }

    /**
     * Update last sample time
     * 
     * @param time
     */
    public void setLastSampleTime(long time) {
        _lastSampleTime = time;
    }

    /**
     * Get current timestamp for data collection
     * 
     * @return
     */
    public long getCurrentSampleTime() {
        return _currentSampleTime;
    }

    public void setCurrentSampleTime(long _currentSampleTime) {
        this._currentSampleTime = _currentSampleTime;
    }

    /**
     * Get System Id from the profile
     * 
     * @return
     */
    public URI getSystemId() {
        return _systemId;
    }

    /**
     * Set System id to the profile
     * 
     * @param systemId
     */
    public void setSystemId(URI _systemId) {
        this._systemId = _systemId;
    }

    public Class getSystemClass() {
        return _recordClazz;
    }

    public void setSystemClazz(Class clazz) {
        _recordClazz = clazz;
    }

    public void setProviderAccessInfo(URI _providerAccessInfo) {
        this._providerAccessInfo = _providerAccessInfo;
    }

    public URI getProviderAccessInfo() {
        return _providerAccessInfo;
    }

    /**
     * @return the cimConnectionFactory
     */
    public Object getCimConnectionFactory() {
        return _cimConnectionFactory;
    }

    /**
     * set the Controller CIMConnectionFactory.
     * 
     * @param cimConnectionFactory
     *            the cimConnectionFactory to set
     */

    /**
     * set the Controller RecordableEventManager.
     * 
     * @param eventManager
     *            the RecordableEventManager to set
     */
    public void setRecordableEventManager(Object eventManager) {
        _eventManager = eventManager;
    }

    /**
     * @return the RecordableEventManager
     */
    public Object getRecordableEventManager() {
        return _eventManager;
    }

    public void setCimConnectionFactory(Object cimConnectionFactory) {
        _cimConnectionFactory = cimConnectionFactory;
    }

    public void setRegisterType(String registerType) {
        _registerType = registerType;
    }

    public String getRegisterType() {
        return _registerType;
    }

    public void setCache(Map<String, StorageSystemViewObject> cache) {
        _cache = cache;
    }

    public Map<String, StorageSystemViewObject> getCache() {
        return _cache;
    }

    public void setProps(Map<String, String> _configInfo) {
        _props = _configInfo;
    }

    public Map<String, String> getProps() {
        return _props;
    }

}
