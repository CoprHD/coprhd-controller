/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * 
 */
package com.emc.cloud.ucsm.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.clientlib.ClientMessageKeys;
import com.emc.cloud.platform.ucs.in.model.ConfigConfMo;
import com.emc.cloud.platform.ucs.in.model.ConfigConfig;
import com.emc.cloud.platform.ucs.in.model.ConfigResolveClass;
import com.emc.cloud.platform.ucs.in.model.ConfigResolveDn;
import com.emc.cloud.platform.ucs.in.model.DnSet;
import com.emc.cloud.platform.ucs.in.model.DnSet.Dn;
import com.emc.cloud.platform.ucs.in.model.EqFilter;
import com.emc.cloud.platform.ucs.in.model.FilterFilter;
import com.emc.cloud.platform.ucs.in.model.LsBinding;
import com.emc.cloud.platform.ucs.in.model.LsInstantiateNNamedTemplate;
import com.emc.cloud.platform.ucs.in.model.LsPower;
import com.emc.cloud.platform.ucs.in.model.LsbootDef;
import com.emc.cloud.platform.ucs.in.model.LsbootLan;
import com.emc.cloud.platform.ucs.in.model.LsbootLanImagePath;
import com.emc.cloud.platform.ucs.in.model.LsbootSan;
import com.emc.cloud.platform.ucs.in.model.LsbootSanCatSanImage;
import com.emc.cloud.platform.ucs.in.model.LsbootSanCatSanImagePath;
import com.emc.cloud.platform.ucs.in.model.LsbootSanImage;
import com.emc.cloud.platform.ucs.in.model.LsbootSanImagePath;
import com.emc.cloud.platform.ucs.in.model.LsbootStorage;
import com.emc.cloud.platform.ucs.in.model.NamingClassId;
import com.emc.cloud.platform.ucs.in.model.ObjectFactory;
import com.emc.cloud.platform.ucs.in.model.OrFilter;
import com.emc.cloud.platform.ucs.in.model.VnicEther;
import com.emc.cloud.platform.ucs.in.model.VnicEtherIf;
import com.emc.cloud.platform.ucs.out.model.ComputeBlade;
import com.emc.cloud.platform.ucs.out.model.ConfigSet;
import com.emc.cloud.platform.ucs.out.model.FabricFcSanEp;
import com.emc.cloud.platform.ucs.out.model.FabricVlan;
import com.emc.cloud.platform.ucs.out.model.FabricVsan;
import com.emc.cloud.platform.ucs.out.model.FcPIo;
import com.emc.cloud.platform.ucs.out.model.LsServer;
import com.emc.cloud.platform.ucs.out.model.SwFcSanEp;
import com.emc.cloud.platform.ucs.out.model.SwFcSanPc;
import com.emc.cloud.platform.ucs.out.model.SwVsan;
import com.emc.cloud.platform.ucs.out.model.VnicLanConnTempl;
import com.emc.cloud.platform.ucs.out.model.VnicSanConnTempl;

/**
 * @author prabhj
 * 
 */
public class UCSMServiceImpl implements UCSMService {

    /**
     * In the UCSM XML API the only way to delete a managed object, is to set
     * it's status value to "deleted" - hence this constant is used in many
     * decommissioning operations
     */
    private static final String MO_DELETED_STATUS = "deleted";

    ComputeSessionManager sessionManager;

    ObjectFactory factory = new ObjectFactory();

    public static final Logger log = LoggerFactory.getLogger(UCSMServiceImpl.class);

    public void setSessionManager(ComputeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public List<ComputeBlade> getComputeBlades(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        List<ComputeBlade> blades = new ArrayList<ComputeBlade>();

        try {
            ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

            ConfigResolveClass configResolveClass = new ConfigResolveClass();
            configResolveClass.setClassId(NamingClassId.COMPUTE_ITEM);
            configResolveClass.setInHierarchical("true");

            com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                    factory.createConfigResolveClass(configResolveClass),
                    com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

            ConfigSet configSet = null;
            if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

                for (Object object : configResolveClassOut.getContent()) {
                    if (object instanceof JAXBElement<?>) {
                        if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                            continue;
                        }
                        configSet = ((JAXBElement<ConfigSet>) object).getValue();
                        if (configSet != null && configSet.getManagedObject() != null
                                && !configSet.getManagedObject().isEmpty()) {
                            for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                                if (managedObject.getValue() instanceof ComputeBlade) {
                                    blades.add((ComputeBlade) managedObject.getValue());
                                }
                            }
                        }

                    }
                }
            }
        } catch (ClientGeneralException e) {
            log.warn("Unable to get compute elements", e);
            throw e;
        }

        return blades;

    }

    @Override
    public <T> T getManagedObject(String ucsmURL, String username, String password, String dn, boolean hierarchical,
            Class<T> returnType) throws ClientGeneralException {

        T managedObject = null;

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveDn configResolveDn = new ConfigResolveDn();
        configResolveDn.setDn(dn);
        configResolveDn.setInHierarchical(new Boolean(hierarchical).toString());

        com.emc.cloud.platform.ucs.out.model.ConfigResolveDn configResolveClassOut = computeSession.execute(
                factory.createConfigResolveDn(configResolveDn),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveDn.class);

        com.emc.cloud.platform.ucs.out.model.ConfigConfig configConfig = null;
        if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

            for (Object object : configResolveClassOut.getContent()) {
                if (object instanceof JAXBElement<?>) {
                    if (!(((JAXBElement) object).getValue() instanceof com.emc.cloud.platform.ucs.out.model.ConfigConfig)) {
                        continue;
                    }
                    configConfig = ((JAXBElement<com.emc.cloud.platform.ucs.out.model.ConfigConfig>) object).getValue();
                    if (configConfig != null && configConfig.getManagedObject() != null) {

                        if (returnType.isInstance(configConfig.getManagedObject().getValue())) {
                            managedObject = returnType.cast(configConfig.getManagedObject().getValue());

                            /**
                             * Short circuit.... No need to run through the
                             * other elements, as only one element is expected!
                             */
                            return managedObject;
                        }
                    }

                }
            }
        }
        return managedObject;

    }

    public Map<String, LsServer> getAllAssociatedLsServers(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        Map<String, LsServer> associatedLsServers = Collections.synchronizedMap(new HashMap<String, LsServer>());
        ;

        try {
            ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

            ConfigResolveClass configResolveClass = new ConfigResolveClass();
            configResolveClass.setClassId(NamingClassId.LS_SERVER);
            configResolveClass.setInHierarchical("true");

            // configResolveClass.getContent()

            FilterFilter inFilter = new FilterFilter();

            EqFilter eqFilter = new EqFilter();

            eqFilter.setProperty("assocState");
            eqFilter.setClazz(NamingClassId.LS_SERVER);
            eqFilter.setValue("associated");

            inFilter.setAbstractFilter(factory.createEq(eqFilter));

            configResolveClass.getContent().add(
                    new JAXBElement<FilterFilter>(new QName("inFilter"), FilterFilter.class, inFilter));

            com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                    factory.createConfigResolveClass(configResolveClass),
                    com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

            ConfigSet configSet = null;
            if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

                for (Object object : configResolveClassOut.getContent()) {
                    if (object instanceof JAXBElement<?>) {
                        if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                            continue;
                        }
                        configSet = ((JAXBElement<ConfigSet>) object).getValue();
                        if (configSet != null && configSet.getManagedObject() != null
                                && !configSet.getManagedObject().isEmpty()) {
                            for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                                if (managedObject.getValue() instanceof LsServer) {
                                    LsServer lsServer = (LsServer) managedObject.getValue();
                                    associatedLsServers.put(lsServer.getPnDn(), lsServer);
                                }
                            }
                        }

                    }
                }
            }
        } catch (ClientGeneralException e) {
            log.error("Unable to get all associated lsServers", e);
            throw e;
        }

        return associatedLsServers;
    }

    public List<LsServer> getAllLsServers(String ucsmURL, String username, String password)
            throws ClientGeneralException {
        List<LsServer> lsServers = Collections.synchronizedList(new ArrayList<LsServer>());

        try {
            ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

            ConfigResolveClass configResolveClass = new ConfigResolveClass();
            configResolveClass.setClassId(NamingClassId.LS_SERVER);
            configResolveClass.setInHierarchical("true");

            // configResolveClass.getContent();

            com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                    factory.createConfigResolveClass(configResolveClass),
                    com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

            ConfigSet configSet = null;
            if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

                for (Object object : configResolveClassOut.getContent()) {
                    if (object instanceof JAXBElement<?>) {
                        if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                            continue;
                        }
                        configSet = ((JAXBElement<ConfigSet>) object).getValue();
                        if (configSet != null && configSet.getManagedObject() != null
                                && !configSet.getManagedObject().isEmpty()) {
                            for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                                if (managedObject.getValue() instanceof LsServer) {
                                    LsServer lsServer = (LsServer) managedObject.getValue();
                                    lsServers.add(lsServer);
                                }
                            }
                        }

                    }
                }
            }
        } catch (ClientGeneralException e) {
            log.error("Unable to get all lsServers", e);
            throw e;
        }

        return lsServers;
    }

    /**
     * 
     * @param ucsmURL
     * @param username
     * @param password
     * @param lsServerDN
     *            - is the DN of the lsServer (unique for the UCSM that's
     *            represented by ucsmURL)
     * @param powerState
     *            - is "up" or "down"
     * @return
     * @throws ClientGeneralException
     */
    public LsServer setLsServerPowerState(String ucsmURL, String username, String password, String lsServerDN,
            String powerState) throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigConfMo configConfMo = new ConfigConfMo();
        configConfMo.setInHierarchical("true");

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(lsServerDN);

        LsPower lsPower = new LsPower();
        lsPower.setRn("power");
        lsPower.setState(powerState);

        lsServer.getContent().add(factory.createLsPower(lsPower));

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        configConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        return pushLsServer(computeSession, factory, configConfMo);

    }

    @Override
    public List<com.emc.cloud.platform.ucs.out.model.LsbootPolicy> getBootPolicies(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        List<com.emc.cloud.platform.ucs.out.model.LsbootPolicy> bootPolicies = Collections
                .synchronizedList(new ArrayList<com.emc.cloud.platform.ucs.out.model.LsbootPolicy>());

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.LSBOOT_POLICY);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof com.emc.cloud.platform.ucs.out.model.LsbootPolicy) {
                bootPolicies.add((com.emc.cloud.platform.ucs.out.model.LsbootPolicy) managedObject.getValue());
            }
        }
        return bootPolicies;
    }

    @Override
    public List<VnicLanConnTempl> getVnicTemplates(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        List<VnicLanConnTempl> vnicTemplates = Collections.synchronizedList(new ArrayList<VnicLanConnTempl>());

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.VNIC_LAN_CONN_TEMPL);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof VnicLanConnTempl) {
                vnicTemplates.add((VnicLanConnTempl) managedObject.getValue());
            }
        }
        return vnicTemplates;
    }

    @Override
    public List<VnicSanConnTempl> getVhbaTemplates(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        List<VnicSanConnTempl> vhbaTemplates = Collections.synchronizedList(new ArrayList<VnicSanConnTempl>());

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.VNIC_SAN_CONN_TEMPL);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof VnicSanConnTempl) {
                vhbaTemplates.add((VnicSanConnTempl) managedObject.getValue());
            }
        }
        return vhbaTemplates;
    }

    @Override
    public List<LsServer> getServiceProfileTemplates(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        List<LsServer> serviceProfileTemplates = Collections.synchronizedList(new ArrayList<LsServer>());

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.LS_SERVER);
        configResolveClass.setInHierarchical("true");

        FilterFilter inFilter = new FilterFilter();

        OrFilter orFilter = new OrFilter();

        EqFilter eqFilterInitTemplate = new EqFilter();

        eqFilterInitTemplate.setProperty("type");
        eqFilterInitTemplate.setClazz(NamingClassId.LS_SERVER);
        eqFilterInitTemplate.setValue("initial-template");

        EqFilter eqFilterUpdatingTemplate = new EqFilter();

        eqFilterUpdatingTemplate.setProperty("type");
        eqFilterUpdatingTemplate.setClazz(NamingClassId.LS_SERVER);
        eqFilterUpdatingTemplate.setValue("updating-template");

        orFilter.getAbstractFilter().add(factory.createEq(eqFilterUpdatingTemplate));
        orFilter.getAbstractFilter().add(factory.createEq(eqFilterInitTemplate));

        inFilter.setAbstractFilter(factory.createOr(orFilter));

        configResolveClass.getContent().add(
                new JAXBElement<FilterFilter>(new QName("inFilter"), FilterFilter.class, inFilter));

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        ConfigSet configSet = null;
        if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

            for (Object object : configResolveClassOut.getContent()) {
                if (object instanceof JAXBElement<?>) {
                    if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                        continue;
                    }
                    configSet = ((JAXBElement<ConfigSet>) object).getValue();
                    if (configSet != null && configSet.getManagedObject() != null
                            && !configSet.getManagedObject().isEmpty()) {
                        for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                            if (managedObject.getValue() instanceof LsServer) {
                                serviceProfileTemplates.add((LsServer) managedObject.getValue());
                                LsServer spt = (LsServer) managedObject.getValue();
                                log.info("SPT Name:" + spt.getName());
                            }
                        }
                    }

                }
            }
        }

        return serviceProfileTemplates;
    }

    /**
     * Create Service Profile from ServiceProfileTemplate
     * 
     * @param ucsmURL
     * @param username
     * @param password
     * @param serviceProfileDns
     * @return
     * @throws ClientGeneralException
     */
    public LsServer createServiceProfileFromTemplate(String ucsmURL, String username, String password,
            String serviceProfileTemplateDn, String serviceProfileName) throws ClientGeneralException {

        if (serviceProfileTemplateDn == null || serviceProfileTemplateDn.isEmpty()) {
            throw new ClientGeneralException(ClientMessageKeys.EXPECTED_PARAMETER_WAS_NULL,
                    new String[] { "serviceProfileTemplateDn" });
        }

        LsServer createdServiceProfile = null;

        List<LsServer> existingLsServers = getAllLsServers(ucsmURL, username, password);

        if (StringUtils.isNotBlank(serviceProfileName)) {
            String serviceProfileNameToUse = serviceProfileName;
            int index = 0;
            boolean serviceProfileNameIsDuplicate = isServiceProfileDuplicate(
                    existingLsServers, serviceProfileNameToUse);
            if (!serviceProfileNameIsDuplicate) {
                if (serviceProfileNameToUse.length() > 32) {
                    serviceProfileNameToUse = StringUtils.substringBefore(
                            serviceProfileName, ".");
                    if (serviceProfileNameToUse.length() > 32) {
                        serviceProfileNameToUse = StringUtils.substring(
                                serviceProfileNameToUse, 0, 32);
                    }
                    serviceProfileNameIsDuplicate = isServiceProfileDuplicate(
                            existingLsServers, serviceProfileNameToUse);
                }
            }
            while (serviceProfileNameIsDuplicate) {
                index++;
                serviceProfileNameToUse = serviceProfileName + "_"
                        + Integer.toString(index);
                if (serviceProfileNameToUse.length() > 32) {
                    serviceProfileNameToUse = StringUtils.substringBefore(
                            serviceProfileName, ".")
                            + "_"
                            + Integer.toString(index);
                    if (serviceProfileNameToUse.length() > 32) {
                        serviceProfileNameToUse = StringUtils.substring(
                                serviceProfileNameToUse, 0, 32 - (Integer
                                        .toString(index).length() + 1));
                    }
                }
                serviceProfileNameIsDuplicate = isServiceProfileDuplicate(
                        existingLsServers, serviceProfileNameToUse);
            }

            try {
                ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

                LsInstantiateNNamedTemplate namedTemplate = new LsInstantiateNNamedTemplate();
                namedTemplate.setDn(serviceProfileTemplateDn);
                namedTemplate.setInHierarchical(Boolean.toString(false));
                String orgName = serviceProfileTemplateDn.substring(0, serviceProfileTemplateDn.lastIndexOf("/"));
                namedTemplate.setInTargetOrg(orgName);

                Dn dn = new Dn();
                dn.setValue(serviceProfileNameToUse);
                DnSet dnSet = new DnSet();
                dnSet.getDn().add(dn);

                namedTemplate.getContent().add(factory.createLsInstantiateNNamedTemplateInNameSet(dnSet));

                com.emc.cloud.platform.ucs.out.model.LsInstantiateNNamedTemplate namedTemplateOut = computeSession
                        .execute(factory.createLsInstantiateNNamedTemplate(namedTemplate),
                                com.emc.cloud.platform.ucs.out.model.LsInstantiateNNamedTemplate.class);

                if (namedTemplateOut != null && namedTemplateOut.getContent() != null) {
                    if (!namedTemplateOut.getContent().isEmpty()) {

                        /*
                         * Expecting only one element to be returned!
                         */
                        for (Serializable contentElement : namedTemplateOut.getContent()) {
                            if (contentElement instanceof JAXBElement<?>
                                    && ((JAXBElement<?>) contentElement).getValue() != null) {
                                if (((JAXBElement<?>) contentElement).getValue() instanceof ConfigSet) {
                                    ConfigSet configSet = (ConfigSet) ((JAXBElement<?>) contentElement).getValue();
                                    for (JAXBElement<?> contentElement2 : configSet.getManagedObject()) {
                                        if (contentElement2.getValue() != null
                                                && contentElement2.getValue() instanceof LsServer) {
                                            return (LsServer) contentElement2.getValue();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (ClientGeneralException e) {
                log.error("Unable to create service profile : " + serviceProfileName + " From SPT : "
                        + serviceProfileTemplateDn, e);
                throw e;
            }

        } else {
            throw new ClientGeneralException(ClientMessageKeys.EXPECTED_PARAMETER_WAS_NULL,
                    new String[] { "serviceProfileName" });
        }

        return createdServiceProfile;
    }

    public LsServer bindSPToComputeElement(String ucsmURL, String username, String password, String serviceProfileDn,
            String computeElementDn) throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);
        ConfigConfMo bindSPToCEConfigConfMo = new ConfigConfMo();
        bindSPToCEConfigConfMo.setInHierarchical(Boolean.toString(true));
        // bindSPToCEConfigConfMo.

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(serviceProfileDn);

        LsBinding lsBinding = new LsBinding();
        lsBinding.setPnDn(computeElementDn);

        lsServer.getContent().add(factory.createLsBinding(lsBinding));

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        bindSPToCEConfigConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        return pushLsServer(computeSession, factory, bindSPToCEConfigConfMo);
    }

    public LsServer unbindSPFromTemplate(String ucsmURL, String username, String password, String serviceProfileDn)
            throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);
        ConfigConfMo unbindSPFromSPTConfigConfMo = new ConfigConfMo();
        unbindSPFromSPTConfigConfMo.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(serviceProfileDn);
        lsServer.setSrcTemplName("");

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        unbindSPFromSPTConfigConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        return pushLsServer(computeSession, factory, unbindSPFromSPTConfigConfMo);
    }

    public LsServer bindSPToTemplate(String ucsmURL, String username, String password, String serviceProfileDn,
            String sptDn) throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);
        ConfigConfMo bindSPToSPTConfigConfMo = new ConfigConfMo();
        bindSPToSPTConfigConfMo.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(serviceProfileDn);
        lsServer.setSrcTemplName(sptDn);

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        bindSPToSPTConfigConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        return pushLsServer(computeSession, factory, bindSPToSPTConfigConfMo);
    }

    private LsServer pushLsServer(ComputeSession computeSession, ObjectFactory factory, ConfigConfMo configConfMo)
            throws ClientGeneralException {

        if (configConfMo == null || configConfMo.getContent().isEmpty()) {
            throw new ClientGeneralException(ClientMessageKeys.BAD_REQUEST, new String[] { "Unable to push lsServer : "
                    + configConfMo.getDn() });
        }

        com.emc.cloud.platform.ucs.out.model.ConfigConfMo configConfMoOut = computeSession.execute(
                factory.createConfigConfMo(configConfMo), com.emc.cloud.platform.ucs.out.model.ConfigConfMo.class);
        if (configConfMoOut != null && !configConfMoOut.getContent().isEmpty()) {

            for (Serializable object : configConfMoOut.getContent()) {
                if (object instanceof JAXBElement<?>) {
                    if (((JAXBElement) object).getValue() instanceof com.emc.cloud.platform.ucs.out.model.ConfigConfig) {
                        com.emc.cloud.platform.ucs.out.model.ConfigConfig configConfigOut = ((JAXBElement<com.emc.cloud.platform.ucs.out.model.ConfigConfig>) object)
                                .getValue();

                        if (configConfigOut != null && configConfigOut.getManagedObject() != null
                                && configConfigOut.getManagedObject().getValue() instanceof LsServer) {
                            return (LsServer) configConfigOut.getManagedObject().getValue();
                        }

                    }
                }
            }
        }
        return null;

    }

    @Override
    public FabricVlan getVlanById(String ucsmURL, String username, String password, String vlanId)
            throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.FABRIC_VLAN);
        configResolveClass.setInHierarchical("true");

        FilterFilter inFilter = new FilterFilter();

        EqFilter eqFilter = new EqFilter();

        eqFilter.setProperty("id");
        eqFilter.setClazz(NamingClassId.FABRIC_VLAN);
        eqFilter.setValue(vlanId);

        inFilter.setAbstractFilter(factory.createEq(eqFilter));

        configResolveClass.getContent().add(
                new JAXBElement<FilterFilter>(new QName("inFilter"), FilterFilter.class, inFilter));

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        ConfigSet configSet = null;
        if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

            for (Object object : configResolveClassOut.getContent()) {
                if (object instanceof JAXBElement<?>) {
                    if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                        continue;
                    }
                    configSet = ((JAXBElement<ConfigSet>) object).getValue();
                    if (configSet != null && configSet.getManagedObject() != null
                            && !configSet.getManagedObject().isEmpty()) {
                        for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                            if (managedObject.getValue() instanceof FabricVlan) {
                                return (FabricVlan) managedObject.getValue();
                            }
                        }
                    }

                }
            }
        }

        return null;

    }

    @Override
    public LsServer setServiceProfileToLanBoot(String ucsmURL, String username, String password, String spDn)
            throws ClientGeneralException {

        return setLsBootDefOnLsServer(ucsmURL, username, password, spDn, BootType.LAN, null);

    }

    @Override
    public LsServer setServiceProfileToSanBoot(String ucsmURL, String username, String password, String spDn,
            Map<String, Map<String, Integer>> hbaToStoragePortMap)
            throws ClientGeneralException {

        return setLsBootDefOnLsServer(ucsmURL, username, password, spDn, BootType.SAN, hbaToStoragePortMap);

    }

    @Override
    public LsServer setServiceProfileToNoBoot(String ucsmURL, String username, String password, String spDn)
            throws ClientGeneralException {
        /*
         * This first call makes sure that any boot policy that might have been
         * inherited from the Service Profile Template will be removed. However,
         * doing this makes the Service Profile use a UCS "default" boot policy,
         * which might not be desirable depending on what's in the default UCS
         * boot policy, and the blade might fail to bind to the Service Profile
         */
        setLsBootDefOnLsServer(ucsmURL, username, password, spDn, BootType.NONE, null);
        /*
         * This second call to setLsBootDefOnLsServer in fact sets up an "empty"
         * boot policy, which would certainly not interfere with the blade to
         * Service Profile binding - and appropriate boot policies are can be
         * setup later on
         */
        return setLsBootDefOnLsServer(ucsmURL, username, password, spDn, BootType.EMPTY, null);

    }

    private LsServer setLsBootDefOnLsServer(String ucsmURL, String username, String password, String spDn,
            BootType bootType, Map<String, Map<String, Integer>> hbaToStoragePortMap) throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        String version = getDeviceVersion(ucsmURL, username, password);
        LsServer lsServerCurrent = getManagedObject(ucsmURL, username, password, spDn, true, LsServer.class);

        ConfigConfMo lsbootDefConfigMo = new ConfigConfMo();
        lsbootDefConfigMo.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(spDn);

        lsServer.getContent().add(factory.createLsbootDef(createLsBootDef(bootType, spDn, version, lsServerCurrent, hbaToStoragePortMap)));

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        lsbootDefConfigMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        return pushLsServer(computeSession, factory, lsbootDefConfigMo);
    }

    private enum BootType {
        LAN, SAN, NONE, EMPTY;
    }

    private LsbootDef createLsBootDef(BootType bootType, String spDN, String version, LsServer lsServerCurrent,
            Map<String, Map<String, Integer>> hbaToStoragePortMap) {

        LsbootDef lsbootDef = new LsbootDef();
        lsbootDef.setRn("boot-policy");
        lsbootDef.setRebootOnUpdate("yes");

        switch (bootType) {
            case NONE:
                lsbootDef.setStatus(MO_DELETED_STATUS);
                return lsbootDef;
            case EMPTY:
                lsbootDef.setRebootOnUpdate("no");
                return lsbootDef;
            case LAN:
                lsbootDef.getContent().add(factory.createLsbootLan(createLsbootLan(lsServerCurrent, "1")));
                break;
            case SAN:

                if (UcsmVersionChecker.verifyVersionDetails("2.2", version) < 0) {
                    lsbootDef.getContent()
                            .add(factory.createLsbootStorage(createLsbootStorage(spDN, hbaToStoragePortMap, lsServerCurrent)));
                } else {
                    lsbootDef.getContent().add(factory.createLsbootSan(createLsbootSan(spDN, hbaToStoragePortMap, lsServerCurrent)));
                }
                lsbootDef.getContent().add(factory.createLsbootLan(createLsbootLan(lsServerCurrent, "2")));
                break;
        }
        return lsbootDef;

    }

    private LsbootSan createLsbootSan(String spDN, Map<String, Map<String, Integer>> hbaToStoragePortMap, LsServer lsServerCurrent) {

        Map<String, String> hbaToSwitchIdMap = getHBAToSwitchIdMap(lsServerCurrent);
        LsbootSan lsbootSan = new LsbootSan();
        lsbootSan.setOrder("1");
        lsbootSan.setRn("san");

        for (String hba : hbaToStoragePortMap.keySet()) {
            Map<String, Integer> ports = hbaToStoragePortMap.get(hba);
            LsbootSanCatSanImage lsbootSanCatSanImage = createLsbootSanCatSanImage(ports, hba, hbaToSwitchIdMap);
            lsbootSan.getContent().add(factory.createLsbootSanCatSanImage(lsbootSanCatSanImage));
        }

        return lsbootSan;
    }

    private LsbootSanCatSanImage createLsbootSanCatSanImage(Map<String, Integer> ports, String hba, Map<String, String> hbaToSwitchIdMap) {
        LsbootSanCatSanImage lsbootSanCatSanImage = new LsbootSanCatSanImage();
        lsbootSanCatSanImage.setType(BootType.SAN.toString().toLowerCase());

        if (SwitchId.A.name().equals(hbaToSwitchIdMap.get(hba))) {
            lsbootSanCatSanImage.setRn("sanimg-" + SanImagePathType.primary.toString());
            lsbootSanCatSanImage.setType(SanImagePathType.primary.toString());
            lsbootSanCatSanImage.setVnicName(hba);
        } else if (SwitchId.B.name().equals(hbaToSwitchIdMap.get(hba))) {
            lsbootSanCatSanImage.setRn("sanimg-" + SanImagePathType.secondary.toString());
            lsbootSanCatSanImage.setType(SanImagePathType.secondary.toString());
            lsbootSanCatSanImage.setVnicName(hba);
        }

        /**
         * Only interested in first 2 ports - or just the one port if that's all
         * that exists
         */

        if (ports != null && ports.size() > 0) {

            Iterator<String> portIterator = ports.keySet().iterator();

            if (portIterator.hasNext()) {
                String port = portIterator.next();
                LsbootSanCatSanImagePath lsbootSanImagePath = createLsbootSanCatSanImagePath(SanImagePathType.primary,
                        port, ports.get(port));
                lsbootSanCatSanImage.getContent().add(factory.createLsbootSanCatSanImagePath(lsbootSanImagePath));

            }
            if (portIterator.hasNext()) {
                String port = portIterator.next();
                LsbootSanCatSanImagePath lsbootSanImagePath = createLsbootSanCatSanImagePath(
                        SanImagePathType.secondary, port, ports.get(port));
                lsbootSanCatSanImage.getContent().add(factory.createLsbootSanCatSanImagePath(lsbootSanImagePath));

            }
        }

        return lsbootSanCatSanImage;

    }

    private LsbootSanCatSanImagePath createLsbootSanCatSanImagePath(SanImagePathType sanImagePathType, String storagePort, Integer hlu) {
        LsbootSanCatSanImagePath lsbootSanCatSanImagePath = new LsbootSanCatSanImagePath();
        lsbootSanCatSanImagePath.setRn("sanimgpath-" + sanImagePathType.toString());
        lsbootSanCatSanImagePath.setType(sanImagePathType.toString());
        lsbootSanCatSanImagePath.setLun(hlu.longValue());
        lsbootSanCatSanImagePath.setWwn(storagePort);
        return lsbootSanCatSanImagePath;
    }

    private LsbootLan createLsbootLan(LsServer lsServerCurrent, String order) {
        LsbootLan lsbootLan = new LsbootLan();
        lsbootLan.setRn(BootType.LAN.toString().toLowerCase());
        lsbootLan.setProt("pxe");
        lsbootLan.setOrder(order);

        List<com.emc.cloud.platform.ucs.out.model.VnicEther> vnics = getVnics(lsServerCurrent);

        Collections.sort(vnics, new Comparator<com.emc.cloud.platform.ucs.out.model.VnicEther>() {

            @Override
            public int compare(com.emc.cloud.platform.ucs.out.model.VnicEther o1,
                    com.emc.cloud.platform.ucs.out.model.VnicEther o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        if (vnics != null && !vnics.isEmpty()) {
            lsbootLan.getContent().add(factory.createLsbootLanImagePath(createLsbootLanImagePath(LanImagePathType.primary, vnics.get(0))));
        }
        if (vnics != null && vnics.size() >= 2) {
            lsbootLan.getContent()
                    .add(factory.createLsbootLanImagePath(createLsbootLanImagePath(LanImagePathType.secondary, vnics.get(1))));
        }

        return lsbootLan;
    }

    private LsbootLanImagePath createLsbootLanImagePath(LanImagePathType lanImagePathType,
            com.emc.cloud.platform.ucs.out.model.VnicEther vnic) {
        LsbootLanImagePath lsbootLanImagePath = new LsbootLanImagePath();
        lsbootLanImagePath.setType(lanImagePathType.toString());
        lsbootLanImagePath.setRn("path-" + lanImagePathType.toString());
        lsbootLanImagePath.setVnicName(vnic.getName());
        return lsbootLanImagePath;
    }

    @Override
    public Map<String, Boolean> setOsInstallVlan(String ucsmURL, String username, String password, String spDn,
            String osInstallVlanId) throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        FabricVlan fabricVlan = getVlanById(ucsmURL, username, password, osInstallVlanId);

        LsServer lsServerOut = getManagedObject(ucsmURL, username, password, spDn, true, LsServer.class);

        String interfaceName = getFirstVnic(lsServerOut).getName();
        log.info("Selecting OS install interface " + interfaceName + " on " + lsServerOut.getName());

        /**
         * This is the list of vlans that were set on the interface that
         */
        Map<String, Boolean> vlanMap = getVlansSetOnInterface(lsServerOut, interfaceName);

        ConfigConfMo setOsInstallVlanConfMo = new ConfigConfMo();
        setOsInstallVlanConfMo.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(spDn);

        VnicEther vnicEther = new VnicEther();

        vnicEther.setDn(spDn + "/ether-" + interfaceName);
        // Unbind vnic from vnic template
        vnicEther.setNwTemplName("");

        for (String vlan : vlanMap.keySet()) {

            if (vlan.equals(fabricVlan.getName())) {
                continue;
            }

            VnicEtherIf vnicEtherIfToBeDeleted = new VnicEtherIf();
            vnicEtherIfToBeDeleted.setRn("if-" + vlan);
            vnicEtherIfToBeDeleted.setStatus(MO_DELETED_STATUS);
            vnicEtherIfToBeDeleted.setName(vlan);
            vnicEther.getContent().add(factory.createVnicEtherIf(vnicEtherIfToBeDeleted));
            log.info("Removing VLAN " + vlan + " from interface " + interfaceName +
                    " temporarily for OS installation on " + lsServerOut.getName());
        }

        VnicEtherIf vnicEtherIf = new VnicEtherIf();
        vnicEtherIf.setRn("if-" + fabricVlan.getName());
        vnicEtherIf.setDefaultNet("yes");
        vnicEtherIf.setStatus("");
        vnicEtherIf.setName(fabricVlan.getName());
        vnicEther.getContent().add(factory.createVnicEtherIf(vnicEtherIf));
        log.info("Adding OS install VLAN " + fabricVlan.getName() + " temporarily to interface " + interfaceName +
                " on " + lsServerOut.getName());

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createVnicEther(vnicEther));

        setOsInstallVlanConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        computeSession.execute(factory.createConfigConfMo(setOsInstallVlanConfMo),
                com.emc.cloud.platform.ucs.out.model.ConfigConfMo.class);

        return vlanMap;

    }

    private com.emc.cloud.platform.ucs.out.model.VnicEther getFirstVnic(LsServer lsServer) throws ClientGeneralException {
        List<com.emc.cloud.platform.ucs.out.model.VnicEther> vnics = getVnics(lsServer);

        if ((vnics == null) || vnics.isEmpty()) {
            String[] s = { "No vNIC available on " + lsServer.getName() };
            throw new ClientGeneralException(ClientMessageKeys.UNEXPECTED_FAILURE, s);
        }

        Collections.sort(vnics, new Comparator<com.emc.cloud.platform.ucs.out.model.VnicEther>() {
            @Override
            public int compare(com.emc.cloud.platform.ucs.out.model.VnicEther o1,
                    com.emc.cloud.platform.ucs.out.model.VnicEther o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        return vnics.get(0);
    }

    private enum SwitchId {
        A, B;
    }

    private enum SanImagePathType {
        primary, secondary;
    }

    private enum LanImagePathType {
        primary, secondary;
    }

    private LsbootStorage createLsbootStorage(String spDn, Map<String, Map<String, Integer>> hbaToStoragePortMap, LsServer lsServer) {

        Map<String, String> hbaToSwitchIdMap = getHBAToSwitchIdMap(lsServer);
        LsbootStorage lsbootStorage = new LsbootStorage();
        lsbootStorage.setOrder("1");
        lsbootStorage.setRn("storage");

        for (String hba : hbaToStoragePortMap.keySet()) {
            Map<String, Integer> ports = hbaToStoragePortMap.get(hba);
            LsbootSanImage lsbootSanImage = createLsbootSanImage(ports, hba, hbaToSwitchIdMap);
            lsbootStorage.getContent().add(factory.createLsbootSanImage(lsbootSanImage));
        }

        return lsbootStorage;

    }

    private LsbootSanImage createLsbootSanImage(Map<String, Integer> ports, String hba,
            Map<String, String> hbaToSwitchIdMap) {

        LsbootSanImage lsbootSanImage = new LsbootSanImage();

        if (SwitchId.A.name().equals(hbaToSwitchIdMap.get(hba))) {
            lsbootSanImage.setRn("san-primary");
            lsbootSanImage.setType("primary");
            lsbootSanImage.setVnicName(hba);
        }
        else if (SwitchId.B.name().equals(hbaToSwitchIdMap.get(hba))) {
            lsbootSanImage.setRn("san-secondary");
            lsbootSanImage.setType("secondary");
            lsbootSanImage.setVnicName(hba);
        }

        /**
         * Only interested in first 2 ports - or just the one port if that's all
         * that exists
         */

        if (ports != null && ports.size() > 0) {

            Iterator<String> portIterator = ports.keySet().iterator();

            if (portIterator.hasNext()) {
                String port = portIterator.next();
                LsbootSanImagePath lsbootSanImagePath = createLsbootSanImagePath(SanImagePathType.primary, port,
                        ports.get(port));
                lsbootSanImage.getContent().add(factory.createLsbootSanImagePath(lsbootSanImagePath));

            }
            if (portIterator.hasNext()) {
                String port = portIterator.next();
                LsbootSanImagePath lsbootSanImagePath = createLsbootSanImagePath(SanImagePathType.secondary, port,
                        ports.get(port));
                lsbootSanImage.getContent().add(factory.createLsbootSanImagePath(lsbootSanImagePath));

            }
        }

        return lsbootSanImage;

    }

    private LsbootSanImagePath createLsbootSanImagePath(SanImagePathType sanImagePathType, String storagePort, Integer hlu) {

        LsbootSanImagePath lsbootSanImagePath = new LsbootSanImagePath();
        lsbootSanImagePath.setRn("path-" + sanImagePathType.name());
        lsbootSanImagePath.setType(sanImagePathType.name());
        lsbootSanImagePath.setLun(hlu.longValue());
        lsbootSanImagePath.setWwn(storagePort);
        return lsbootSanImagePath;

    }

    private Map<String, String> getHBAToSwitchIdMap(LsServer lsServer) {

        Map<String, String> hbaToSwitchIdMap = new HashMap<String, String>();

        for (Serializable contentElement : lsServer.getContent()) {

            if (contentElement instanceof JAXBElement<?>) {

                if (((JAXBElement<?>) contentElement).getValue() instanceof com.emc.cloud.platform.ucs.out.model.VnicFc) {
                    com.emc.cloud.platform.ucs.out.model.VnicFc vnicFc = (com.emc.cloud.platform.ucs.out.model.VnicFc) ((JAXBElement<?>) contentElement)
                            .getValue();
                    hbaToSwitchIdMap.put(vnicFc.getName(), vnicFc.getSwitchId());

                }

            }
        }

        return hbaToSwitchIdMap;
    }

    @Override
    public void removeOsInstallVlan(String ucsmURL, String username, String password, String spDn,
            String osInstallVlanId, Map<String, Boolean> vlanMap) throws ClientGeneralException {

        FabricVlan fabricVlan = getVlanById(ucsmURL, username, password, osInstallVlanId);
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigConfMo setOsInstallVlanConfMo = new ConfigConfMo();
        setOsInstallVlanConfMo.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(spDn);

        LsServer lsServerOut = getManagedObject(ucsmURL, username, password, spDn, true, LsServer.class);
        com.emc.cloud.platform.ucs.out.model.VnicEther firstVnic = getFirstVnic(lsServerOut);

        String interfaceName = firstVnic.getName();
        log.info("Restoring VLANs on " + lsServerOut.getName() + " after OS install on interface: " + interfaceName);

        Map<String, Boolean> vlansOnInterface = getVlansSetOnInterface(lsServerOut, interfaceName);
        if ((vlansOnInterface.size() != 1) || (vlansOnInterface.get(fabricVlan.getName()) == null)) {
            String[] s = { "Error restoring VLANs after OS Installation on " + lsServerOut.getName() +
                    ".  VNICs were modified during OS install.  Interface " + interfaceName +
                    " does not contain just the OS install VLAN.  It contains " + vlansOnInterface.keySet() };
            throw new ClientGeneralException(ClientMessageKeys.UNEXPECTED_FAILURE, s);
        }

        VnicEther vnicEther = new VnicEther();
        vnicEther.setDn(spDn + "/ether-" + interfaceName);
        vnicEther.setNwTemplName(firstVnic.getNwTemplName());

        boolean shouldDeleteOsInstallVlan = true;

        for (String vlan : vlanMap.keySet()) {

            if (vlan.equals(fabricVlan.getName())) {
                shouldDeleteOsInstallVlan = false;
            }

            VnicEtherIf vnicEtherIfToAdded = new VnicEtherIf();
            vnicEtherIfToAdded.setRn("if-" + vlan);
            vnicEtherIfToAdded.setName(vlan);
            vnicEtherIfToAdded.setStatus("");
            if (vlanMap.get(vlan)) {
                vnicEtherIfToAdded.setDefaultNet(DEFAULT_NETWORK_VALUES.YES.getValue());
            }
            vnicEther.getContent().add(factory.createVnicEtherIf(vnicEtherIfToAdded));
            log.info("  Adding VLAN " + vlan + " to be restored to interface " + interfaceName +
                    " of " + lsServerOut.getName());
        }

        if (shouldDeleteOsInstallVlan) {
            VnicEtherIf vnicEtherIf = new VnicEtherIf();
            vnicEtherIf.setRn("if-" + fabricVlan.getName());
            vnicEtherIf.setStatus(MO_DELETED_STATUS);
            vnicEtherIf.setName(fabricVlan.getName());
            vnicEther.getContent().add(factory.createVnicEtherIf(vnicEtherIf));
            log.info("  Adding VLAN " + fabricVlan.getName() + " to be removed from interface " + interfaceName +
                    " of " + lsServerOut.getName());
        }

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createVnicEther(vnicEther));

        setOsInstallVlanConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        computeSession.execute(factory.createConfigConfMo(setOsInstallVlanConfMo),
                com.emc.cloud.platform.ucs.out.model.ConfigConfMo.class);
    }

    private Map<String, Boolean> getVlansSetOnInterface(LsServer lsServer, String interfaceName) {

        if (lsServer == null || interfaceName == null) {
            return null;
        }

        Map<String, Boolean> vlanMap = new HashMap<String, Boolean>();

        for (com.emc.cloud.platform.ucs.out.model.VnicEther vnicEther : getVnics(lsServer)) {
            if (interfaceName.equals(vnicEther.getName())) {

                for (Serializable contentElement2 : vnicEther.getContent()) {
                    if (contentElement2 instanceof JAXBElement<?>) {

                        if (((JAXBElement<?>) contentElement2).getValue() instanceof com.emc.cloud.platform.ucs.out.model.VnicEtherIf) {
                            com.emc.cloud.platform.ucs.out.model.VnicEtherIf vnicEtherIf = (com.emc.cloud.platform.ucs.out.model.VnicEtherIf) ((JAXBElement<?>) contentElement2)
                                    .getValue();
                            if (DEFAULT_NETWORK_VALUES.YES.getValue().equals(vnicEtherIf.getDefaultNet())) {
                                vlanMap.put(vnicEtherIf.getName(), true);
                            }
                            else {
                                vlanMap.put(vnicEtherIf.getName(), false);
                            }
                        }

                    }

                }

            }
        }

        return vlanMap;

    }

    private List<com.emc.cloud.platform.ucs.out.model.VnicEther> getVnics(LsServer lsServer) {

        if (lsServer == null) {
            return null;
        }
        List<com.emc.cloud.platform.ucs.out.model.VnicEther> list = new ArrayList<com.emc.cloud.platform.ucs.out.model.VnicEther>();

        for (Serializable contentElement : lsServer.getContent()) {

            if (contentElement instanceof JAXBElement<?>) {

                if (((JAXBElement<?>) contentElement).getValue() instanceof com.emc.cloud.platform.ucs.out.model.VnicEther) {
                    com.emc.cloud.platform.ucs.out.model.VnicEther vnicEther = (com.emc.cloud.platform.ucs.out.model.VnicEther) ((JAXBElement<?>) contentElement)
                            .getValue();
                    list.add(vnicEther);

                }

            }
        }

        return list;
    }

    private enum DEFAULT_NETWORK_VALUES {
        YES("yes"), NO("no");

        String value;

        private DEFAULT_NETWORK_VALUES(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Override
    public Map<String, FcPIo> getFICUplinkPorts(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        Map<String, FcPIo> uplinkMap = new HashMap<String, FcPIo>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.FC_P_IO);
        configResolveClass.setInHierarchical("false");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof FcPIo) {
                uplinkMap.put(((FcPIo) managedObject.getValue()).getDn(), (FcPIo) managedObject.getValue());
            }
        }
        return uplinkMap;
    }

    @Override
    public Map<String, FabricFcSanEp> getUplinkFCInterfaces(String ucsmURL, String username, String password)
            throws ClientGeneralException {
        Map<String, FabricFcSanEp> uplinkFcInterfaceMap = new HashMap<String, FabricFcSanEp>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.FABRIC_FC_SAN_EP);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof FabricFcSanEp) {
                uplinkFcInterfaceMap.put(((FabricFcSanEp) managedObject.getValue()).getDn(),
                        (FabricFcSanEp) managedObject.getValue());
            }
        }
        return uplinkFcInterfaceMap;
    }

    public String getDeviceVersion(String ucsmURL, String username, String password) throws ClientGeneralException {
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);
        ConfigResolveDn configResolveDn = new ConfigResolveDn();
        configResolveDn.setInHierarchical("false");
        configResolveDn.setDn("sys/mgmt/fw-system");
        com.emc.cloud.platform.ucs.out.model.ConfigResolveDn configreSolveDnOut = (com.emc.cloud.platform.ucs.out.model.ConfigResolveDn) computeSession
                .execute(factory.createConfigResolveDn(configResolveDn), Object.class);
        if (configreSolveDnOut != null && !configreSolveDnOut.getContent().isEmpty()) {
            for (Object object : configreSolveDnOut.getContent()) {
                if (!(object instanceof JAXBElement<?>)) {
                    continue;
                }
                JAXBElement jaxbObject = (JAXBElement) object;

                if (jaxbObject.getValue() == null
                        || !(jaxbObject.getValue() instanceof com.emc.cloud.platform.ucs.out.model.ConfigConfig)) {
                    continue;
                }

                com.emc.cloud.platform.ucs.out.model.ConfigConfig confObject = (com.emc.cloud.platform.ucs.out.model.ConfigConfig) jaxbObject
                        .getValue();

                if (confObject.getManagedObject() == null || confObject.getManagedObject().getValue() == null) {
                    continue;
                }

                if (!(confObject.getManagedObject().getValue() instanceof com.emc.cloud.platform.ucs.out.model.FirmwareRunning)) {
                    continue;
                }
                return ((com.emc.cloud.platform.ucs.out.model.FirmwareRunning) confObject.getManagedObject().getValue())
                        .getVersion();
            }
        }
        return null;
    }

    @Override
    public Map<String, SwFcSanEp> getSwitchFCInterfaces(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        Map<String, SwFcSanEp> switchFcInterfaceMap = new HashMap<String, SwFcSanEp>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.SW_FC_SAN_EP);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof SwFcSanEp) {
                switchFcInterfaceMap.put(((SwFcSanEp) managedObject.getValue()).getEpDn(),
                        (SwFcSanEp) managedObject.getValue());
            }
        }
        return switchFcInterfaceMap;
    }

    public Map<String, SwFcSanPc> getUplinkPortChannels(String ucsmURL, String username, String password)
            throws ClientGeneralException {

        Map<String, SwFcSanPc> switchFcInterfaceMap = new HashMap<String, SwFcSanPc>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.SW_FC_SAN_PC);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof SwFcSanPc) {
                switchFcInterfaceMap.put(((SwFcSanPc) managedObject.getValue()).getDn(),
                        (SwFcSanPc) managedObject.getValue());
            }
        }
        return switchFcInterfaceMap;
    }

    @Override
    public List<SwVsan> getUcsSwitchVSans(String ucsmURL, String username, String password)
            throws ClientGeneralException {
        List<SwVsan> vSanList = new ArrayList<SwVsan>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.SW_VSAN);
        configResolveClass.setInHierarchical("false");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof SwVsan) {
                vSanList.add((SwVsan) managedObject.getValue());
            }
        }
        return vSanList;

    }

    @Override
    public List<FabricVlan> getUcsVlans(String ucsmURL, String username, String password) throws ClientGeneralException {
        List<FabricVlan> vlanList = new ArrayList<FabricVlan>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.FABRIC_VLAN);
        configResolveClass.setInHierarchical("false");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof FabricVlan) {
                vlanList.add((FabricVlan) managedObject.getValue());
            }
        }
        return vlanList;
    }

    @Override
    public List<FabricVsan> getUcsFabricVsans(String ucsmURL, String username, String password) throws ClientGeneralException {
        List<FabricVsan> vsanList = new ArrayList<>();
        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigResolveClass configResolveClass = new ConfigResolveClass();
        configResolveClass.setClassId(NamingClassId.FABRIC_VSAN);
        configResolveClass.setInHierarchical("true");

        com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                factory.createConfigResolveClass(configResolveClass),
                com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

        for (JAXBElement<?> managedObject : getConfigSetManagedObjects(configResolveClassOut)) {
            if (managedObject.getValue() instanceof FabricVsan) {
                vsanList.add((FabricVsan) managedObject.getValue());
            }
        }
        return vsanList;
    }

    @Override
    public void clearDeviceSession(String ucsmURL, String username, String password) throws ClientGeneralException {
        sessionManager.getSession(ucsmURL, username, password).clearSession();
    }

    @Override
    public LsServer unbindServiceProfile(String ucsmURL, String username, String password, String spDn)
            throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigConfMo disAssocSPFromBladeMO = new ConfigConfMo();
        disAssocSPFromBladeMO.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(spDn);

        LsBinding lsBinding = new LsBinding();
        lsBinding.setPnDn("");
        lsBinding.setStatus(MO_DELETED_STATUS);

        lsServer.getContent().add(factory.createLsBinding(lsBinding));

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        disAssocSPFromBladeMO.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        LsServer updatedLsServer = pushLsServer(computeSession, factory, disAssocSPFromBladeMO);

        log.info("The new Oper State of the Service Profile is : " + updatedLsServer.getOperState());
        return updatedLsServer;

    }

    public LsServer getLsServer(String ucsmURL, String username, String password, String uuid)
            throws ClientGeneralException {
        try {
            ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

            ConfigResolveClass configResolveClass = new ConfigResolveClass();
            configResolveClass.setClassId(NamingClassId.LS_SERVER);
            configResolveClass.setInHierarchical("true");

            // configResolveClass.getContent()

            FilterFilter inFilter = new FilterFilter();

            EqFilter eqFilter = new EqFilter();

            eqFilter.setClazz(NamingClassId.LS_SERVER);
            eqFilter.setProperty("uuid");
            eqFilter.setValue(uuid);

            inFilter.setAbstractFilter(factory.createEq(eqFilter));

            configResolveClass.getContent().add(
                    new JAXBElement<>(new QName("inFilter"), FilterFilter.class, inFilter));

            com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut = computeSession.execute(
                    factory.createConfigResolveClass(configResolveClass),
                    com.emc.cloud.platform.ucs.out.model.ConfigResolveClass.class);

            ConfigSet configSet = null;
            if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

                for (Object object : configResolveClassOut.getContent()) {
                    if (object instanceof JAXBElement<?>) {
                        if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                            continue;
                        }
                        configSet = ((JAXBElement<ConfigSet>) object).getValue();
                        if (configSet != null && configSet.getManagedObject() != null
                                && !configSet.getManagedObject().isEmpty()) {
                            for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                                if (managedObject.getValue() instanceof LsServer) {
                                    return (LsServer) managedObject.getValue();
                                }
                            }
                        }

                    }
                }
            }
        } catch (ClientGeneralException e) {
            log.error("Unable to get all associated lsServers", e);
            throw e;
        }

        return null;
    }

    @Override
    public void deleteServiceProfile(String ucsmURL, String username, String password, String spDn)
            throws ClientGeneralException {

        ComputeSession computeSession = sessionManager.getSession(ucsmURL, username, password);

        ConfigConfMo deleteSPMO = new ConfigConfMo();
        deleteSPMO.setInHierarchical(Boolean.toString(true));

        com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
        lsServer.setDn(spDn);
        lsServer.setStatus(MO_DELETED_STATUS);

        ConfigConfig configConfig = new ConfigConfig();
        configConfig.setManagedObject(factory.createLsServer(lsServer));

        deleteSPMO.getContent().add(factory.createConfigConfMoInConfig(configConfig));

        pushLsServer(computeSession, factory, deleteSPMO);

        log.info("Deleted the Service Profile with dn : " + spDn);

    }

    private List<JAXBElement<?>> getConfigSetManagedObjects(
            com.emc.cloud.platform.ucs.out.model.ConfigResolveClass configResolveClassOut) {
        ConfigSet configSet = null;
        if (configResolveClassOut.getContent() != null && !configResolveClassOut.getContent().isEmpty()) {

            for (Object object : configResolveClassOut.getContent()) {
                if (object instanceof JAXBElement<?>) {
                    if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                        continue;
                    }
                    configSet = ((JAXBElement<ConfigSet>) object).getValue();
                    if (configSet != null && configSet.getManagedObject() != null
                            && !configSet.getManagedObject().isEmpty()) {
                        return configSet.getManagedObject();
                    }
                }
            }
        }
        return new ArrayList<JAXBElement<?>>();
    }

    /**
     * This method checks to see if a service profile by the given name exists already
     * @param existingLsServers {@link List} of LsServer instances
     * @param serviceProfileNameToUse {@link String} serviceprofile name that has to be checked if it already exists
     * @return true if a duplicate is found else false
     */
    private boolean isServiceProfileDuplicate(List<LsServer> existingLsServers, String serviceProfileNameToUse) {
        boolean serviceProfileNameIsDuplicate = false;
        for (LsServer lsServer : existingLsServers) {
            if (lsServer.getName().equals(serviceProfileNameToUse)) {
                serviceProfileNameIsDuplicate = true;
                break;
            }
        }
        return serviceProfileNameIsDuplicate;
    }
}
