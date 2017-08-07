/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * 
 */
package com.emc.cloud.ucsm.service;

import java.util.List;
import java.util.Map;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.ucs.out.model.ComputeBlade;
import com.emc.cloud.platform.ucs.out.model.FabricFcSanEp;
import com.emc.cloud.platform.ucs.out.model.FabricVlan;
import com.emc.cloud.platform.ucs.out.model.FabricVsan;
import com.emc.cloud.platform.ucs.out.model.FcPIo;
import com.emc.cloud.platform.ucs.out.model.LsServer;
import com.emc.cloud.platform.ucs.out.model.LsbootPolicy;
import com.emc.cloud.platform.ucs.out.model.SwFcSanEp;
import com.emc.cloud.platform.ucs.out.model.SwFcSanPc;
import com.emc.cloud.platform.ucs.out.model.SwVsan;
import com.emc.cloud.platform.ucs.out.model.VnicLanConnTempl;
import com.emc.cloud.platform.ucs.out.model.VnicSanConnTempl;

/**
 * @author prabhj
 * 
 */
public interface UCSMService {

    public List<ComputeBlade> getComputeBlades(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public Map<String, LsServer> getAllAssociatedLsServers(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<LsServer> getAllServiceProfiles(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public LsServer getLsServer(String ucsmURL, String username, String password, String uuid)
            throws ClientGeneralException;

    public LsServer setLsServerPowerState(String ucsmURL, String username, String password, String lsServerDN,
            String powerState, StringBuilder errorMessage) throws ClientGeneralException;

    public List<LsbootPolicy> getBootPolicies(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<VnicLanConnTempl> getVnicTemplates(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<VnicSanConnTempl> getVhbaTemplates(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<LsServer> getServiceProfileTemplates(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public LsServer createServiceProfileFromTemplate(String ucsmURL, String username, String password,
            String serviceProfileTemplateDn, String serviceProfileName, StringBuffer errorMessage) throws ClientGeneralException;

    public LsServer bindSPToComputeElement(String ucsmURL, String username, String password, String serviceProfileDn,
            String computeElementDn, StringBuilder errorMessage) throws ClientGeneralException;

    public LsServer unbindServiceProfile(String ucsmURL, String username, String password, String spDn, StringBuilder errorMessage)
            throws ClientGeneralException;

    public LsServer bindSPToTemplate(String ucsmURL, String username, String password, String serviceProfileDn,
            String sptDn, StringBuilder errorMessage) throws ClientGeneralException;

    public LsServer unbindSPFromTemplate(String ucsmURL, String username, String password, String spDn, StringBuilder errorMessage)
            throws ClientGeneralException;

    public FabricVlan getVlanById(String ucsmURL, String username, String password, String vlanId)
            throws ClientGeneralException;

    public LsServer setServiceProfileToLanBoot(String ucsmURL, String username, String password, String spDn, StringBuilder errorMessage)
            throws ClientGeneralException;

    public Map<String, Boolean> setOsInstallVlan(String ucsmURL, String username, String password, String spDn,
            String osInstallVlanId) throws ClientGeneralException;

    public LsServer setServiceProfileToSanBoot(String ucsmURL, String username, String password, String spDn,
            Map<String, Map<String, Integer>> hbaToStoragePortMap, StringBuilder errorMessage) throws ClientGeneralException;

    public void removeOsInstallVlan(String ucsmURL, String username, String password, String spDn,
            String osInstallVlanId, Map<String, Boolean> vlanMap) throws ClientGeneralException;

    public Map<String, FcPIo> getFICUplinkPorts(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public Map<String, FabricFcSanEp> getUplinkFCInterfaces(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<SwVsan> getUcsSwitchVSans(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<FabricVlan> getUcsVlans(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public List<FabricVsan> getUcsFabricVsans(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public Map<String, SwFcSanEp> getSwitchFCInterfaces(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public Map<String, SwFcSanPc> getUplinkPortChannels(String ucsmURL, String username, String password)
            throws ClientGeneralException;

    public String getDeviceVersion(String ucsmURL, String username, String password) throws ClientGeneralException;

    public void clearDeviceSession(String ucsmURL, String username, String password) throws ClientGeneralException;

    public void deleteServiceProfile(String ucsmURL, String username, String password, String spDn, StringBuilder errorMessage)
            throws ClientGeneralException;

    LsServer setServiceProfileToNoBoot(String ucsmURL, String username, String password, String spDn, StringBuilder errorMessage)
            throws ClientGeneralException;

    <T> T getManagedObject(String ucsmURL, String username, String password, String dn, boolean hierarchical,
            Class<T> returnType) throws ClientGeneralException;

    /**
     * verify power state of blade/LsServer
     * @param lsServer {@link LsServer} instance
     * @param powerState {@link String} desired powerstate
     * @return boolean
     * @throws ClientGeneralException
     */
    public boolean verifyLsServerPowerState(LsServer lsServer, String powerState) throws ClientGeneralException;

}
