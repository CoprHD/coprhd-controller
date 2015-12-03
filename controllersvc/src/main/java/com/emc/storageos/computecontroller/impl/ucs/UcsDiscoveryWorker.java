/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl.ucs;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBElement;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.cloud.platform.ucs.out.model.ComputeBlade;
import com.emc.cloud.platform.ucs.out.model.ComputeBoard;
import com.emc.cloud.platform.ucs.out.model.FabricVlan;
import com.emc.cloud.platform.ucs.out.model.FabricVsan;
import com.emc.cloud.platform.ucs.out.model.FcPIo;
import com.emc.cloud.platform.ucs.out.model.LsRequirement;
import com.emc.cloud.platform.ucs.out.model.LsServer;
import com.emc.cloud.platform.ucs.out.model.LsbootDef;
import com.emc.cloud.platform.ucs.out.model.LsbootIScsi;
import com.emc.cloud.platform.ucs.out.model.LsbootLan;
import com.emc.cloud.platform.ucs.out.model.LsbootLanImagePath;
import com.emc.cloud.platform.ucs.out.model.LsbootPolicy;
import com.emc.cloud.platform.ucs.out.model.LsbootSan;
import com.emc.cloud.platform.ucs.out.model.LsbootSanImage;
import com.emc.cloud.platform.ucs.out.model.LsbootSanImagePath;
import com.emc.cloud.platform.ucs.out.model.LsbootStorage;
import com.emc.cloud.platform.ucs.out.model.LsbootVirtualMedia;
import com.emc.cloud.platform.ucs.out.model.ProcessorUnit;
import com.emc.cloud.platform.ucs.out.model.SwFcSanEp;
import com.emc.cloud.platform.ucs.out.model.SwFcSanPc;
import com.emc.cloud.platform.ucs.out.model.SwVsan;
import com.emc.cloud.platform.ucs.out.model.VnicEther;
import com.emc.cloud.platform.ucs.out.model.VnicEtherIf;
import com.emc.cloud.platform.ucs.out.model.VnicFc;
import com.emc.cloud.platform.ucs.out.model.VnicFcIf;
import com.emc.cloud.platform.ucs.out.model.VnicLanConnTempl;
import com.emc.cloud.platform.ucs.out.model.VnicSanConnTempl;
import com.emc.cloud.ucsm.service.UCSMService;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.HostToComputeElementMatcher;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ComputeBootDef;
import com.emc.storageos.db.client.model.ComputeBootPolicy;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeElementHBA;
import com.emc.storageos.db.client.model.ComputeFabricUplinkPort;
import com.emc.storageos.db.client.model.ComputeFabricUplinkPortChannel;
import com.emc.storageos.db.client.model.ComputeLanBoot;
import com.emc.storageos.db.client.model.ComputeLanBootImagePath;
import com.emc.storageos.db.client.model.ComputeSanBoot;
import com.emc.storageos.db.client.model.ComputeSanBootImage;
import com.emc.storageos.db.client.model.ComputeSanBootImagePath;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.ComputeVnic;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UCSServiceProfileTemplate;
import com.emc.storageos.db.client.model.UCSVhbaTemplate;
import com.emc.storageos.db.client.model.UCSVnicTemplate;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class UcsDiscoveryWorker {

    private static final Logger _log = LoggerFactory.getLogger(UcsDiscoveryWorker.class);

    private static final String ASSOCIATED_SERVER_POOL = "associatedServerPool";
    private static final String VHBA_COUNT = "vhbaCount";
    private static final String VNIC_COUNT = "vnicCount";
    private static final String BLADE_REMOVED = "removed";
    private static final String BLADE_CFG_FAILURE = "config-failure";

    private UCSMService ucsmService;
    private DbClient _dbClient;

    public UcsDiscoveryWorker(UCSMService ucsmService, DbClient _dbClient) {
        this.ucsmService = ucsmService;
        this._dbClient = _dbClient;
    }

    public enum ServiceProfileTemplateType {
        INSTANCE("instance"), NON_UPDATING_TEMPLATE("initial-template"), UPDATING_TEMPLATE("updating-template");

        private String type;

        ServiceProfileTemplateType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public enum VnicTemplateType {
        NON_UPDATING_TEMPLATE("initial-template"), UPDATING_TEMPLATE("updating-template");

        private String type;

        VnicTemplateType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public void discoverComputeSystem(URI computeSystemURI) {
        String ucsmVersion;
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemURI);
        _log.info("Inside discoverComputeSystems of class : " + getClass().toString());

        URL ucsmURL = getUcsmURL(cs);

        List<ComputeBlade> computeBlades;
        Map<String, LsServer> associatedLsServers;
        List<LsServer> serviceProfileTemplates;
        List<VnicLanConnTempl> vnicTemplates;
        List<VnicSanConnTempl> vhbaTemplates;
        Map<String, FcPIo> uplinkMap;
        Map<String, SwFcSanEp> fcInterfaceMap;
        List<SwVsan> vsanList;
        Map<String, SwFcSanPc> portChannelMap;
        List<FabricVlan> vlanList;
        List<FabricVsan> vsanFabricList;
        List<com.emc.cloud.platform.ucs.out.model.LsbootPolicy> bootPolicies;

        try {
            ucsmVersion = ucsmService.getDeviceVersion(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            verifyVersion(cs, ucsmVersion);

            computeBlades = ucsmService.getComputeBlades(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            associatedLsServers = ucsmService.getAllAssociatedLsServers(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            serviceProfileTemplates = ucsmService.getServiceProfileTemplates(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            bootPolicies = ucsmService.getBootPolicies(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            vnicTemplates = ucsmService.getVnicTemplates(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            vhbaTemplates = ucsmService.getVhbaTemplates(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            uplinkMap = ucsmService.getFICUplinkPorts(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            fcInterfaceMap = ucsmService.getSwitchFCInterfaces(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            vsanList = ucsmService.getUcsSwitchVSans(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            portChannelMap = ucsmService.getUplinkPortChannels(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            vlanList = ucsmService.getUcsVlans(ucsmURL.toString(), cs.getUsername(), cs.getPassword());
            vsanFabricList = ucsmService.getUcsFabricVsans(ucsmURL.toString(), cs.getUsername(), cs.getPassword());

        } catch (Exception e) {
            _log.error("Failed to pull device data: " + cs.getId(), e);
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                cs.setLastDiscoveryStatusMessage(e.getCause().getMessage());
            } else {
                cs.setLastDiscoveryStatusMessage(e.getMessage());
            }
            _dbClient.persistObject(cs);
            throw ComputeSystemControllerException.exceptions.discoverFailed(computeSystemURI.toString(), e);
        }

        reconcileServiceProfileTemplates(cs, serviceProfileTemplates);
        reconcileComputeBlades(cs, computeBlades, associatedLsServers);
        reconcileVhbas(cs, associatedLsServers, new VhbaHelper(vsanFabricList));
        reconcileServiceProfileTemplatesHBAs(cs, serviceProfileTemplates, new VhbaHelper(vsanFabricList));
        reconcileServiceProfileTemplatesVnics(cs, serviceProfileTemplates);
        reconcileServiceProfileTemplatesBootDefinitions(cs, serviceProfileTemplates);
        reconcileBootPolicies(cs, bootPolicies);
        reconcileVnicTemplates(cs, vnicTemplates);
        reconcileVhbaTemplates(cs, vhbaTemplates);

        Map<String, Set<String>> unpinnedVsans = getUnpinnedVSans(vsanList, fcInterfaceMap);
        reconcileUplinkPorts(cs, uplinkMap, fcInterfaceMap, unpinnedVsans);
        reconcileUplinkPortChannels(cs, portChannelMap, unpinnedVsans);
        reconcileVlans(cs, vlanList);

        matchComputeBladesToHosts(cs);

        cs.setLastDiscoveryRunTime(Calendar.getInstance().getTimeInMillis());
        cs.setSuccessDiscoveryTime(Calendar.getInstance().getTimeInMillis());
        cs.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());
        _dbClient.persistObject(cs);
    }

    private void verifyVersion(ComputeSystem cs, String version) {
        String minimumSupportedVersion;
        try {
            if (version == null) {
                _log.error("Device version is null");
                cs.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                cs.setVersion("");
                throw ComputeSystemControllerException.exceptions.verifyVersionFailedNull(cs.getId().toString());
            }

            minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(DiscoveredDataObject.Type.valueOf(cs.getSystemType()));

            _log.info("Minimum support device version {}, version discovered is {}", minimumSupportedVersion, version);

            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0) {
                cs.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                throw ComputeSystemControllerException.exceptions.versionNotSupported(version, minimumSupportedVersion);
            } else {
                cs.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
            cs.setVersion(version);
        } catch (ComputeSystemControllerException e) {
            cs.setLastDiscoveryStatusMessage(e.getMessage());
            throw ComputeSystemControllerException.exceptions.discoverFailed(cs.getId().toString(), e);
        } finally {
            _dbClient.persistObject(cs);
        }
    }

    private Map<String, Object> getServiceProfileTemplateDetails(LsServer spt) {

        Map<String, Object> serviceProfileTemplateDetails = new HashMap<>();

        int vhbaCount = 0;
        int vnicCount = 0;

        List<VnicEther> vnics = new ArrayList<>();
        List<VnicFc> vhbas = new ArrayList<>();

        if (spt.getContent() != null
                && !spt.getContent().isEmpty()) {
            for (Serializable element : spt.getContent()) {
                if (element instanceof JAXBElement<?>) {
                    if (((JAXBElement) element).getValue() instanceof LsRequirement) {
                        LsRequirement lsRequirement = (LsRequirement) ((JAXBElement) element).getValue();
                        serviceProfileTemplateDetails.put("associatedServerPool", lsRequirement.getName());
                    } else if (((JAXBElement) element).getValue() instanceof VnicEther) {
                        vnics.add(((VnicEther) ((JAXBElement) element).getValue()));
                        vnicCount++;
                    } else if (((JAXBElement) element).getValue() instanceof VnicFc) {
                        vhbas.add(((VnicFc) ((JAXBElement) element).getValue()));
                        vhbaCount++;
                    } else if (((JAXBElement) element).getValue() instanceof LsbootDef) {
                        LsbootDef lsbootDef = (LsbootDef) ((JAXBElement) element).getValue();
                        serviceProfileTemplateDetails.put("associatedBootPolicy", lsbootDef);
                    }
                }
            }
            serviceProfileTemplateDetails.put("vhbaCount", vhbaCount);
            serviceProfileTemplateDetails.put("vnicCount", vnicCount);
            serviceProfileTemplateDetails.put("vhbas", vhbas);
            serviceProfileTemplateDetails.put("vnics", vnics);
        }
        return serviceProfileTemplateDetails;
    }

    private String getProcessorSpeed(ComputeBlade computeBlade) {
        if (computeBlade.getContent() != null
                && !computeBlade.getContent().isEmpty()) {
            for (Serializable contentElement : computeBlade.getContent()) {
                if (contentElement instanceof JAXBElement<?>) {
                    if (((JAXBElement) contentElement).getValue() instanceof ComputeBoard) {
                        ComputeBoard computeBoard = (ComputeBoard) ((JAXBElement) contentElement).getValue();

                        if (computeBoard.getContent() != null
                                && !computeBoard.getContent().isEmpty()) {
                            for (Serializable computeBoardContentElement : computeBoard.getContent()) {
                                if (computeBoardContentElement instanceof JAXBElement<?>) {
                                    if (((JAXBElement) computeBoardContentElement).getValue() instanceof ProcessorUnit) {

                                        ProcessorUnit processorUnit = (ProcessorUnit) ((JAXBElement) computeBoardContentElement).getValue();

                                        if ("equipped".equals(processorUnit.getPresence())) {
                                            return processorUnit.getSpeed();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void reconcileComputeBlades(ComputeSystem cs, List<ComputeBlade> computeBlades, Map<String, LsServer> associatedLsServers) {
        _log.info("reconciling ComputeBlades");
        Map<String, ComputeElement> removeBlades = new HashMap<>();
        Map<String, ComputeElement> updateBlades = new HashMap<>();
        Map<String, ComputeElement> addBlades = new HashMap<>();

        URIQueryResultList uris = new URIQueryResultList();

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeElemetsConstraint(cs.getId()), uris);

        List<ComputeElement> elements = _dbClient.queryObject(ComputeElement.class, uris, true);

        for (ComputeElement element : elements) {
            removeBlades.put(element.getLabel(), element);
        }

        for (ComputeBlade computeBlade : computeBlades) {
            ComputeElement ce = removeBlades.get(computeBlade.getDn());
            LsServer lsServer = associatedLsServers.get(computeBlade.getDn());
            if (ce != null) {
                updateComputeElement(ce, computeBlade, lsServer);
                updateBlades.put(ce.getLabel(), ce);
                removeBlades.remove(computeBlade.getDn());
            } else {
                ce = new ComputeElement();
                createComputeElement(cs, ce, computeBlade, lsServer);
                addBlades.put(computeBlade.getDn(), ce);
            }
        }

        createDataObjects(new ArrayList<DataObject>(addBlades.values()));
        persistDataObjects(new ArrayList<DataObject>(updateBlades.values()));

        for (String name : removeBlades.keySet()) {
            _log.info("Marked for deletion ComputeElement name:" + name);
        }
        deleteDataObjects(new ArrayList<DataObject>(removeBlades.values()));
    }

    private void createComputeElement(ComputeSystem cs, ComputeElement computeElement, ComputeBlade computeBlade, LsServer lsServer) {

        _log.info("Creating ComputeElement label" + computeBlade.getDn());
        URI uri = URIUtil.createId(ComputeElement.class);
        computeElement.setComputeSystem(cs.getId());
        computeElement.setId(uri);
        computeElement.setLabel(computeBlade.getDn());
        computeElement.setCreationTime(Calendar.getInstance());
        computeElement.setInactive(false);
        computeElement.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(cs, computeElement));
        computeElement.setSystemType(cs.getSystemType());
        computeElement.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.UNKNOWN.name());

        if (lsServer != null || !isBladeAvailable(computeBlade)) {
            computeElement.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.UNREGISTERED.name());
        }
        else {
            computeElement.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
        }

        updateComputeElement(computeElement, computeBlade, lsServer);
    }

    private void updateComputeElement(ComputeElement computeElement, ComputeBlade computeBlade, LsServer lsServer) {

        _log.info("Updating ComputeElement id" + computeElement.getId());
        computeElement.setRam(parseNumber(computeBlade.getAvailableMemory()).longValue());
        computeElement.setNumOfCores(parseNumber(computeBlade.getNumOfCores()).intValue());
        computeElement.setNumberOfProcessors(parseNumber(computeBlade.getNumOfCpus()).shortValue());
        computeElement.setNumberOfThreads(parseNumber(computeBlade.getNumOfThreads()).intValue());
        computeElement.setProcessorSpeed(getProcessorSpeed(computeBlade));
        computeElement.setOriginalUuid(computeBlade.getOriginalUuid());
        computeElement.setChassisId(computeBlade.getChassisId());
        computeElement.setSlotId(parseNumber(computeBlade.getSlotId()).longValue());
        computeElement.setModel(computeBlade.getModel());
        computeElement.setLastDiscoveryRunTime(Calendar.getInstance().getTimeInMillis());
        computeElement.setSuccessDiscoveryTime(Calendar.getInstance().getTimeInMillis());
        computeElement.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());

        if (lsServer != null) {
            computeElement.setAvailable(false);
            computeElement.setUuid(lsServer.getUuid());
            computeElement.setDn(lsServer.getDn());
        } else {

            if (isBladeAvailable(computeBlade)) {
                computeElement.setAvailable(true);
            }
            else {
                computeElement.setAvailable(false);
            }

            computeElement.setUuid(computeBlade.getUuid());
            computeElement.setDn(NullColumnValueGetter.getNullStr());
        }
    }

    private void reconcileVhbas(ComputeSystem cs, Map<String, LsServer> associatedLsServers, VhbaHelper lookUpVsan) {
        _log.info("Reconciling Vhbas");

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeElemetsConstraint(cs.getId()), uris);

        List<ComputeElement> elements = _dbClient.queryObject(ComputeElement.class, uris, true);

        for (ComputeElement computeElement : elements) {

            Map<String, ComputeElementHBA> removeVhbas = new HashMap<>();
            Map<String, ComputeElementHBA> addVhbas = new HashMap<>();
            Map<String, ComputeElementHBA> updateVhbas = new HashMap<>();

            URIQueryResultList uriVhbas = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeElementComputeElemetHBAsConstraint(computeElement.getId()), uriVhbas);

            List<ComputeElementHBA> vbhas = _dbClient.queryObject(ComputeElementHBA.class, uriVhbas, true);
            for (ComputeElementHBA hba : vbhas) {
                removeVhbas.put(hba.getLabel(), hba);
            }

            LsServer lsServer = associatedLsServers.get(computeElement.getLabel());

            if (lsServer != null && lsServer.getContent() != null && !lsServer.getContent().isEmpty()) {
                for (Serializable contentElement : lsServer.getContent()) {

                    if (contentElement instanceof JAXBElement<?> && ((JAXBElement) contentElement).getValue() instanceof VnicFc) {
                        VnicFc vnicFc = (VnicFc) ((JAXBElement) contentElement).getValue();
                        ComputeElementHBA hba = removeVhbas.get(vnicFc.getName());
                        if (hba != null) {
                            updateVhbas.put(vnicFc.getName(), hba);
                            removeVhbas.remove(hba.getLabel());
                            updateComputeElementHBA(hba, vnicFc);
                        } else {
                            hba = new ComputeElementHBA();
                            addVhbas.put(vnicFc.getName(), hba);
                            createComputeElementHBA(cs, computeElement, hba, vnicFc);
                        }
                    }
                }
            }

            createDataObjects(new ArrayList<DataObject>(addVhbas.values()));
            persistDataObjects(new ArrayList<DataObject>(updateVhbas.values()));

            // Do not delete vHBAs that are still linked to the ViPR host
            Iterator<Map.Entry<String, ComputeElementHBA>> vhbaIterator = removeVhbas.entrySet().iterator();

            while (vhbaIterator.hasNext()) {

                Map.Entry<String, ComputeElementHBA> entry = vhbaIterator.next();

                if (entry.getValue().getHost() != null) {
                    vhbaIterator.remove();
                } else {
                    _log.info("vHBA is marked for deletion {}", entry.getKey());
                }
            }

            deleteDataObjects(new ArrayList<DataObject>(removeVhbas.values()));
        }
    }

    private void createComputeElementHBA(DiscoveredSystemObject cs, ComputeElement computeElement, ComputeElementHBA computeElementHBA,
            VnicFc vnicFc) {
        computeElementHBA.setComputeElement(computeElement.getId());
        _log.info("Adding ComputeElementHBA name: " + vnicFc.getName());
        computeElementHBA.setId(URIUtil.createId(ComputeElementHBA.class));
        computeElementHBA.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(computeElementHBA, cs.getSystemType()));
        updateComputeElementHBA(computeElementHBA, vnicFc);
    }

    private void createComputeElementHBA(DiscoveredSystemObject cs, UCSServiceProfileTemplate serviceProfile,
            ComputeElementHBA computeElementHBA, VhbaHelper vsanLookupMap, VnicFc vnicFc) {
        computeElementHBA.setServiceProfileTemplate(serviceProfile.getId());
        _log.info("Adding ComputeElementHBA name: " + vnicFc.getName());
        computeElementHBA.setId(URIUtil.createId(ComputeElementHBA.class));
        computeElementHBA.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(computeElementHBA, cs.getSystemType()));
        updateComputeElementHBA(computeElementHBA, vsanLookupMap, vnicFc);
    }

    private void updateComputeElementHBA(ComputeElementHBA computeElementHBA, VnicFc vnicFc) {
        _log.info("Updating ComputeElementHBA id: " + computeElementHBA.getId());
        computeElementHBA.setDn(vnicFc.getDn());
        computeElementHBA.setLabel(vnicFc.getName());
        computeElementHBA.setProtocol(vnicFc.getType());
        computeElementHBA.setNode(vnicFc.getNodeAddr());
        computeElementHBA.setPort(vnicFc.getAddr());
        computeElementHBA.setTemplateName(vnicFc.getNwTemplName());
        computeElementHBA.setVsanId(new VhbaHelper().getVsanId(vnicFc));
    }

    private void updateComputeElementHBA(ComputeElementHBA computeElementHBA, VhbaHelper vhbaHelper, VnicFc vnicFc) {
        _log.info("Updating ComputeElementHBA id: " + computeElementHBA.getId());
        computeElementHBA.setDn(vnicFc.getDn());
        computeElementHBA.setLabel(vnicFc.getName());
        computeElementHBA.setProtocol(vnicFc.getType());
        computeElementHBA.setNode(vnicFc.getNodeAddr());
        computeElementHBA.setPort(vnicFc.getAddr());
        computeElementHBA.setTemplateName(vnicFc.getNwTemplName());
        String vsanId = vhbaHelper.getLookUpVsanId(vnicFc);
        if (vsanId != null) {
            computeElementHBA.setVsanId(vsanId);
        } else {
            computeElementHBA.setVsanId(vhbaHelper.getVsanId(vnicFc));
        }
    }

    private void reconcileVnicTemplates(ComputeSystem cs, List<VnicLanConnTempl> vnicTemplates) {
        _log.info("Reconciling VnicTemplates");
        Map<String, UCSVnicTemplate> removeTemplates = new HashMap<>();
        Map<String, UCSVnicTemplate> updateTemplates = new HashMap<>();
        Map<String, UCSVnicTemplate> addTemplates = new HashMap<>();

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemVnicTemplateConstraint(cs.getId()), uris);

        List<UCSVnicTemplate> templates = _dbClient.queryObject(UCSVnicTemplate.class, uris, true);
        for (UCSVnicTemplate vnicTemplate : templates) {
            removeTemplates.put(vnicTemplate.getDn(), vnicTemplate);
        }

        // discovered data
        for (VnicLanConnTempl vnicTemplate : vnicTemplates) {
            UCSVnicTemplate template = removeTemplates.get(vnicTemplate.getDn());
            if (template != null) {
                updateTemplates.put(vnicTemplate.getDn(), template);
                removeTemplates.remove(template.getDn());
                updateUCSVnicTemplate(template, vnicTemplate);
            } else {
                template = new UCSVnicTemplate();
                addTemplates.put(vnicTemplate.getDn(), template);
                createUCSVnicTemplate(cs, template, vnicTemplate);
            }
        }
        createDataObjects(new ArrayList<DataObject>(addTemplates.values()));
        persistDataObjects(new ArrayList<DataObject>(updateTemplates.values()));

        for (String key : removeTemplates.keySet()) {
            _log.info("Marked for deletion UCSVnicTemplate: " + key);
        }

        deleteDataObjects(new ArrayList<DataObject>(removeTemplates.values()));
    }

    private void createUCSVnicTemplate(ComputeSystem cs, UCSVnicTemplate template, VnicLanConnTempl vnicTemplate) {

        _log.info("Adding UCSSVnicTemplate label: " + vnicTemplate.getDn());
        URI uri = URIUtil.createId(UCSVnicTemplate.class);
        template.setComputeSystem(cs.getId());
        template.setInactive(false);
        template.setId(uri);
        template.setSystemType(cs.getSystemType());
        template.setCreationTime(Calendar.getInstance());
        updateUCSVnicTemplate(template, vnicTemplate);
    }

    private void updateUCSVnicTemplate(UCSVnicTemplate template, VnicLanConnTempl vnicTemplate) {

        _log.info("Updating UCSVnicTemplate id: " + template.getId());
        template.setDn(vnicTemplate.getDn());
        template.setLabel(vnicTemplate.getName());
        template.setUpdating(VnicTemplateType.UPDATING_TEMPLATE.getType().equals(vnicTemplate.getTemplType()));

        template.setLastDiscoveryRunTime(Calendar.getInstance().getTimeInMillis());
        template.setSuccessDiscoveryTime(Calendar.getInstance().getTimeInMillis());
        template.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());
        template.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.UNKNOWN.name());
        template.setTemplateType(vnicTemplate.getTemplType());
    }

    private void reconcileVhbaTemplates(ComputeSystem cs, List<VnicSanConnTempl> vhbaTemplates) {
        _log.info("Reconciling VhbaTemplates");
        Map<String, UCSVhbaTemplate> removeTemplates = new HashMap<>();
        Map<String, UCSVhbaTemplate> updateTemplates = new HashMap<>();
        Map<String, UCSVhbaTemplate> addTemplates = new HashMap<>();

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemVhbaTemplateConstraint(cs.getId()), uris);

        List<UCSVhbaTemplate> templates = _dbClient.queryObject(UCSVhbaTemplate.class, uris, true);
        for (UCSVhbaTemplate vhbaTemplate : templates) {
            removeTemplates.put(vhbaTemplate.getDn(), vhbaTemplate);
        }

        // discovered data
        for (VnicSanConnTempl vhbaTemplate : vhbaTemplates) {
            UCSVhbaTemplate template = removeTemplates.get(vhbaTemplate.getDn());
            if (template != null) {
                updateTemplates.put(vhbaTemplate.getDn(), template);
                removeTemplates.remove(template.getDn());
                updateUCSVhbaTemplate(template, vhbaTemplate);
            } else {
                template = new UCSVhbaTemplate();
                addTemplates.put(vhbaTemplate.getDn(), template);
                createUCSVhbaTemplate(cs, template, vhbaTemplate);
            }
        }
        createDataObjects(new ArrayList<DataObject>(addTemplates.values()));
        persistDataObjects(new ArrayList<DataObject>(updateTemplates.values()));

        for (String key : removeTemplates.keySet()) {
            _log.info("Marked for deletion UCSVhbaTemplate: " + key);
        }

        deleteDataObjects(new ArrayList<DataObject>(removeTemplates.values()));
    }

    private void createUCSVhbaTemplate(ComputeSystem cs, UCSVhbaTemplate template, VnicSanConnTempl vhbaTemplate) {

        _log.info("Adding UCSSVhbaTemplate label: " + vhbaTemplate.getDn());
        URI uri = URIUtil.createId(UCSVhbaTemplate.class);
        template.setComputeSystem(cs.getId());
        template.setInactive(false);
        template.setId(uri);
        template.setSystemType(cs.getSystemType());
        template.setCreationTime(Calendar.getInstance());
        updateUCSVhbaTemplate(template, vhbaTemplate);
    }

    private void updateUCSVhbaTemplate(UCSVhbaTemplate template, VnicSanConnTempl vhbaTemplate) {

        _log.info("Updating UCSVhbaTemplate id: " + template.getId());
        template.setDn(vhbaTemplate.getDn());
        template.setLabel(vhbaTemplate.getName());
        template.setUpdating(VnicTemplateType.UPDATING_TEMPLATE.getType().equals(vhbaTemplate.getTemplType()));

        template.setLastDiscoveryRunTime(Calendar.getInstance().getTimeInMillis());
        template.setSuccessDiscoveryTime(Calendar.getInstance().getTimeInMillis());
        template.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());
        template.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.UNKNOWN.name());
        template.setTemplateType(vhbaTemplate.getTemplType());
    }

    private void reconcileServiceProfileTemplates(ComputeSystem cs, List<LsServer> serviceProfileTemplates) {
        _log.info("Reconciling ServiceProfileTemplates");
        Map<String, UCSServiceProfileTemplate> removeTemplates = new HashMap<>();
        Map<String, UCSServiceProfileTemplate> updateTemplates = new HashMap<>();
        Map<String, UCSServiceProfileTemplate> addTemplates = new HashMap<>();

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemServiceProfileTemplateConstraint(cs.getId()), uris);

        List<UCSServiceProfileTemplate> serviceTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class, uris, true);
        for (UCSServiceProfileTemplate serviceTemplate : serviceTemplates) {
            removeTemplates.put(serviceTemplate.getDn(), serviceTemplate);
        }

        // discovered data
        for (LsServer lsServer : serviceProfileTemplates) {
            UCSServiceProfileTemplate spt = removeTemplates.get(lsServer.getDn());
            if (spt != null) {
                updateTemplates.put(lsServer.getDn(), spt);
                removeTemplates.remove(spt.getDn());
                updateUCSServiceProfileTemplate(spt, lsServer);
            } else {
                spt = new UCSServiceProfileTemplate();
                addTemplates.put(lsServer.getDn(), spt);
                createUCSServiceProfileTemplate(cs, spt, lsServer);
            }
        }
        createDataObjects(new ArrayList<DataObject>(addTemplates.values()));
        persistDataObjects(new ArrayList<DataObject>(updateTemplates.values()));

        for (String profileName : removeTemplates.keySet()) {
            _log.info("Marked for deletion UCSServiceProfileTemplate: " + profileName);
        }

        // Handle SPTs that are removed on the device.
        // Step1: Remove all references to the SPT in the Compute Virtual Pool.
        // Step2: Delete the SPT from the list of existing SPTs.
        removeServiceProfileTemplatesFromComputeVirtualPool(removeTemplates.values());
        deleteDataObjects(new ArrayList<DataObject>(removeTemplates.values()));
    }

    private void createUCSServiceProfileTemplate(ComputeSystem cs, UCSServiceProfileTemplate serviceProfileTemplate, LsServer lsServer) {

        _log.info("Adding UCSServiceProfileTemplate label: " + lsServer.getDn());
        URI uri = URIUtil.createId(UCSServiceProfileTemplate.class);
        serviceProfileTemplate.setComputeSystem(cs.getId());
        serviceProfileTemplate.setInactive(false);
        serviceProfileTemplate.setId(uri);
        serviceProfileTemplate.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
        serviceProfileTemplate.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(serviceProfileTemplate, cs.getSystemType()));
        serviceProfileTemplate.setSystemType(cs.getSystemType());
        serviceProfileTemplate.setCreationTime(Calendar.getInstance());
        updateUCSServiceProfileTemplate(serviceProfileTemplate, lsServer);
    }

    private void updateUCSServiceProfileTemplate(UCSServiceProfileTemplate serviceProfileTemplate, LsServer lsServer) {

        _log.info("Updating UCSServiceProfileTemplate id: " + serviceProfileTemplate.getId());
        serviceProfileTemplate.setDn(lsServer.getDn());
        serviceProfileTemplate.setLabel(lsServer.getName());
        serviceProfileTemplate.setUpdating(ServiceProfileTemplateType.UPDATING_TEMPLATE.getType().equals(lsServer.getType()));
        serviceProfileTemplate.setAssociatedBootPolicy(lsServer.getOperBootPolicyName());
        Map<String, Object> serviceProfileTemplateDetails = getServiceProfileTemplateDetails(lsServer);
        serviceProfileTemplate
                .setAssociatedServerPool(serviceProfileTemplateDetails.get(ASSOCIATED_SERVER_POOL) instanceof String ? (String) serviceProfileTemplateDetails
                        .get(ASSOCIATED_SERVER_POOL)
                        : null);
        serviceProfileTemplate
                .setNumberOfVHBAS(serviceProfileTemplateDetails.get(VHBA_COUNT) instanceof Integer ? (Integer) serviceProfileTemplateDetails
                        .get(VHBA_COUNT)
                        : null);
        serviceProfileTemplate
                .setNumberOfVNICS(serviceProfileTemplateDetails.get(VNIC_COUNT) instanceof Integer ? (Integer) serviceProfileTemplateDetails
                        .get(VNIC_COUNT)
                        : null);
        serviceProfileTemplate.setLastDiscoveryRunTime(Calendar.getInstance().getTimeInMillis());
        serviceProfileTemplate.setSuccessDiscoveryTime(Calendar.getInstance().getTimeInMillis());
        serviceProfileTemplate.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.name());
        serviceProfileTemplate.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.UNKNOWN.name());
        serviceProfileTemplate.setTemplateType(lsServer.getType());
    }

    private void reconcileBootPolicies(ComputeSystem cs, List<LsbootPolicy> lsBootPolicies) {
        _log.info("Reconciling BootPolicies");
        Map<String, ComputeBootPolicy> removeBootPolicies = new HashMap<>();
        Map<String, ComputeBootPolicy> updateBootPolicies = new HashMap<>();
        Map<String, ComputeBootPolicy> addBootPolicies = new HashMap<>();

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemBootPolicyConstraint(cs.getId()), uris);

        List<ComputeBootPolicy> bootPolicies = _dbClient.queryObject(ComputeBootPolicy.class, uris, true);
        for (ComputeBootPolicy bootPolicy : bootPolicies) {
            removeBootPolicies.put(bootPolicy.getDn(), bootPolicy);
        }

        // discovered data
        for (LsbootPolicy lsbootPolicy : lsBootPolicies) {
            ComputeBootPolicy bootPolicy = removeBootPolicies.get(lsbootPolicy.getDn());
            if (bootPolicy != null) {
                updateBootPolicies.put(lsbootPolicy.getDn(), bootPolicy);
                removeBootPolicies.remove(bootPolicy.getDn());
                updateComputeBootPolicy(bootPolicy, lsbootPolicy);
            } else {
                bootPolicy = new ComputeBootPolicy();
                addBootPolicies.put(lsbootPolicy.getDn(), bootPolicy);
                createComputeBootPolicy(cs, bootPolicy, lsbootPolicy);
            }
        }
        createDataObjects(new ArrayList<DataObject>(addBootPolicies.values()));
        persistDataObjects(new ArrayList<DataObject>(updateBootPolicies.values()));

        for (String key : removeBootPolicies.keySet()) {
            _log.info("Marked for deletion BootPolicy: " + key);
        }
        deleteBootPolicies(new ArrayList<ComputeBootPolicy>(removeBootPolicies.values()));
    }

    private void createComputeBootPolicy(ComputeSystem cs, ComputeBootPolicy bootPolicy, LsbootPolicy lsBootPolicy) {
        URI uri = URIUtil.createId(ComputeBootPolicy.class);
        bootPolicy.setId(uri);
        bootPolicy.setComputeSystem(cs.getId());
        bootPolicy.setDn(lsBootPolicy.getDn());
        bootPolicy.setName(lsBootPolicy.getName());
        _dbClient.createObject(bootPolicy);

        updateComputeBootPolicy(bootPolicy, lsBootPolicy);
    }

    private void updateComputeBootPolicy(ComputeBootPolicy bootPolicy, LsbootPolicy lsBootPolicy) {

        bootPolicy.setDn(lsBootPolicy.getDn());
        bootPolicy.setName(lsBootPolicy.getName());
        if (lsBootPolicy.getEnforceVnicName().equals("yes")) {
            bootPolicy.setEnforceVnicVhbaNames(true);
        } else {
            bootPolicy.setEnforceVnicVhbaNames(false);
        }
        _dbClient.persistObject(bootPolicy);

        ComputeSanBoot sanBoot = null;
        ComputeLanBoot lanBoot = null;

        URIQueryResultList sanBootUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeBootPolicyComputeSanBootConstraint(bootPolicy.getId()),
                sanBootUris);
        List<ComputeSanBoot> sanBootList = _dbClient.queryObject(ComputeSanBoot.class, sanBootUris, true);
        if (sanBootList != null && !sanBootList.isEmpty()) {
            sanBoot = sanBootList.get(0);
        }

        URIQueryResultList lanBootUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeBootPolicyComputeLanBootConstraint(bootPolicy.getId()), lanBootUris);
        List<ComputeLanBoot> lanBootList = _dbClient.queryObject(ComputeLanBoot.class, lanBootUris, true);
        if (lanBootList != null && !lanBootList.isEmpty()) {
            lanBoot = lanBootList.get(0);
        }
        boolean hasLanBoot = false;
        boolean hasSanBoot = false;
        Integer nonSanBootOrder = null;
        Integer sanBootOrder = null;

        if (lsBootPolicy.getContent() != null && !lsBootPolicy.getContent().isEmpty()) {
            for (Serializable element : lsBootPolicy.getContent()) {
                if (element instanceof JAXBElement<?>) {
                    if (((JAXBElement) element).getValue() instanceof LsbootLan) {
                        LsbootLan lsbootLan = (LsbootLan) ((JAXBElement) element).getValue();
                        lanBoot = reconcileComputeLanBoot(lsbootLan, lanBoot, null, bootPolicy);
                        hasLanBoot = true;
                        // This looks crazy, but there were some profiles on the UCS, where bootOrder did not start at 1; this should handle
                        // that case too
                        Integer order = Integer.parseInt(lsbootLan.getOrder());
                        if (nonSanBootOrder == null) {
                            nonSanBootOrder = order;
                        } else if (order < nonSanBootOrder) {
                            nonSanBootOrder = order;
                        }

                    } else if (((JAXBElement) element).getValue() instanceof LsbootStorage) {
                        LsbootStorage lsbootStorage = (LsbootStorage) ((JAXBElement) element).getValue();
                        sanBoot = reconcileComputeSanBoot(lsbootStorage, sanBoot, null, bootPolicy);
                        hasSanBoot = true;
                        sanBootOrder = Integer.parseInt(lsbootStorage.getOrder());

                    } else if (((JAXBElement) element).getValue() instanceof LsbootSan) {
                        LsbootSan lsbootSan = (LsbootSan) ((JAXBElement) element).getValue();
                        sanBoot = reconcileComputeSanBoot(lsbootSan, sanBoot, null, bootPolicy);
                        hasSanBoot = true;
                        sanBootOrder = Integer.parseInt(lsbootSan.getOrder());
                    } else if (((JAXBElement) element).getValue() instanceof LsbootVirtualMedia) {
                        LsbootVirtualMedia lsbootVirtualMedia = (LsbootVirtualMedia) ((JAXBElement) element).getValue();
                        Integer order = Integer.parseInt(lsbootVirtualMedia.getOrder());
                        if (nonSanBootOrder == null) {
                            nonSanBootOrder = order;
                        } else if (order < nonSanBootOrder) {
                            nonSanBootOrder = order;
                        }
                    } else if (((JAXBElement) element).getValue() instanceof LsbootIScsi) {
                        LsbootIScsi lsbootIScsi = (LsbootIScsi) ((JAXBElement) element).getValue();
                        Integer order = Integer.parseInt(lsbootIScsi.getOrder());
                        if (nonSanBootOrder == null) {
                            nonSanBootOrder = order;
                        } else if (order < nonSanBootOrder) {
                            nonSanBootOrder = order;
                        }
                    }
                }

            }
        }
        if (hasSanBoot && nonSanBootOrder != null) {
            sanBoot = (ComputeSanBoot) _dbClient.queryObject(sanBoot.getId());
            if (nonSanBootOrder < sanBootOrder) {
                sanBoot.setIsFirstBootDevice(false);
            } else {
                sanBoot.setIsFirstBootDevice(true);
            }
            _dbClient.persistObject(sanBoot);
        }
        if (!hasSanBoot && sanBoot != null) {
            List<ComputeSanBoot> sanBoots = new ArrayList<ComputeSanBoot>();
            sanBoots.add(sanBoot);
            deleteComputeSanBoot(sanBoots);
        }
        if (!hasLanBoot && lanBoot != null) {
            List<ComputeLanBoot> lanBoots = new ArrayList<ComputeLanBoot>();
            lanBoots.add(lanBoot);
            deleteComputeLanBoot(lanBoots);
        }
    }

    private void deleteBootPolicies(List<ComputeBootPolicy> bootPolicies) {
        List<ComputeSanBootImagePath> removeSanBootImagePaths = new ArrayList<ComputeSanBootImagePath>();
        List<ComputeSanBootImage> removeSanBootImages = new ArrayList<ComputeSanBootImage>();
        List<ComputeSanBoot> removeSanBoots = new ArrayList<ComputeSanBoot>();

        List<ComputeLanBootImagePath> removeLanBootImagePaths = new ArrayList<ComputeLanBootImagePath>();
        List<ComputeLanBoot> removeLanBoots = new ArrayList<ComputeLanBoot>();

        for (ComputeBootPolicy bootPolicy : bootPolicies) {

            // Retrieve associated ComputeSanBoot and delete it
            URIQueryResultList sanBootUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeBootDefComputeSanBootConstraint(bootPolicy.getId()),
                    sanBootUris);
            List<ComputeSanBoot> sanBootList = _dbClient.queryObject(ComputeSanBoot.class, sanBootUris, true);
            if (sanBootList != null && !sanBootList.isEmpty()) {
                for (ComputeSanBoot sanBoot : sanBootList) {
                    URIQueryResultList sanImageUris = new URIQueryResultList();
                    _dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getComputeSanBootImageConstraint(sanBoot.getId()), sanImageUris);
                    List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

                    if (sanBootImageList != null && !sanBootImageList.isEmpty()) {
                        for (ComputeSanBootImage computeSanImage : sanBootImageList) {
                            URIQueryResultList sanImagePathUris = new URIQueryResultList();
                            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                                    .getComputeSanBootImagePathConstraint(computeSanImage.getId()), sanImagePathUris);

                            List<ComputeSanBootImagePath> sanBootPathList = _dbClient.queryObject(ComputeSanBootImagePath.class,
                                    sanImagePathUris, true);

                            if (sanBootPathList != null && !sanBootPathList.isEmpty()) {

                                removeSanBootImagePaths.addAll(sanBootPathList);

                            }
                            removeSanBootImages.add(computeSanImage);
                        }
                    }
                    removeSanBoots.add(sanBoot);
                }

            }

            // Retrieve associated ComputeLanBoot and delete it
            URIQueryResultList lanBootUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeBootDefComputeLanBootConstraint(bootPolicy.getId()),
                    lanBootUris);
            List<ComputeLanBoot> lanBootList = _dbClient.queryObject(ComputeLanBoot.class, lanBootUris, true);
            if (lanBootList != null && !lanBootList.isEmpty()) {
                ComputeLanBoot lanBoot = lanBootList.get(0);

                URIQueryResultList lanImageUris = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getComputeLanBootImagePathsConstraint(lanBoot.getId()), lanImageUris);
                List<ComputeLanBootImagePath> lanBootPathList = _dbClient.queryObject(ComputeLanBootImagePath.class, lanImageUris, true);

                if (lanBootPathList != null && !lanBootPathList.isEmpty()) {
                    removeLanBootImagePaths.addAll(lanBootPathList);
                }
                removeLanBoots.add(lanBoot);
            }

        }
        deleteDataObjects(new ArrayList<DataObject>(removeLanBootImagePaths));
        deleteDataObjects(new ArrayList<DataObject>(removeLanBoots));
        deleteDataObjects(new ArrayList<DataObject>(removeSanBootImagePaths));
        deleteDataObjects(new ArrayList<DataObject>(removeSanBootImages));
        deleteDataObjects(new ArrayList<DataObject>(removeSanBoots));
        deleteDataObjects(new ArrayList<DataObject>(bootPolicies));

    }

    private ComputeBootDef reconcileComputeBootDef(LsbootDef lsBootDef, UCSServiceProfileTemplate spt, ComputeSystem cs) {
        ComputeBootDef bootDef = null;
        URIQueryResultList bootDefUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getServiceProfileTemplateComputeBootDefsConstraint(spt.getId()), bootDefUris);

        List<ComputeBootDef> bootDefs = _dbClient.queryObject(ComputeBootDef.class, bootDefUris, true);

        if (!bootDefs.isEmpty()) {
            bootDef = bootDefs.get(0);
            bootDef.setComputeSystem(cs.getId());
            bootDef.setServiceProfileTemplate(spt.getId());
            // bootDef.setDn(lsBootDef.getDn());
            if (lsBootDef.getEnforceVnicName().equals("yes")) {
                bootDef.setEnforceVnicVhbaNames(true);
            } else {
                bootDef.setEnforceVnicVhbaNames(false);
            }
            _dbClient.persistObject(bootDef);
        }
        if (bootDef == null) {
            bootDef = new ComputeBootDef();
            URI uri = URIUtil.createId(ComputeBootDef.class);
            bootDef.setId(uri);
            bootDef.setComputeSystem(cs.getId());
            bootDef.setServiceProfileTemplate(spt.getId());
            // bootDef.setDn(lsBootDef.getDn());
            if (lsBootDef.getEnforceVnicName().equals("yes")) {
                bootDef.setEnforceVnicVhbaNames(true);
            } else {
                bootDef.setEnforceVnicVhbaNames(false);
            }
            _dbClient.createObject(bootDef);
        }

        ComputeSanBoot sanBoot = null;
        ComputeLanBoot lanBoot = null;

        URIQueryResultList sanBootUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeBootDefComputeSanBootConstraint(bootDef.getId()), sanBootUris);
        List<ComputeSanBoot> sanBootList = _dbClient.queryObject(ComputeSanBoot.class, sanBootUris, true);
        if (sanBootList != null && !sanBootList.isEmpty()) {
            sanBoot = sanBootList.get(0);
        }

        URIQueryResultList lanBootUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeBootDefComputeLanBootConstraint(bootDef.getId()), lanBootUris);
        List<ComputeLanBoot> lanBootList = _dbClient.queryObject(ComputeLanBoot.class, lanBootUris, true);
        if (lanBootList != null && !lanBootList.isEmpty()) {
            lanBoot = lanBootList.get(0);
        }
        boolean hasLanBoot = false;
        boolean hasSanBoot = false;
        Integer nonSanBootOrder = null;
        Integer sanBootOrder = null;

        if (lsBootDef.getContent() != null && !lsBootDef.getContent().isEmpty()) {
            for (Serializable element : lsBootDef.getContent()) {
                if (element instanceof JAXBElement<?>) {
                    if (((JAXBElement) element).getValue() instanceof LsbootLan) {
                        LsbootLan lsbootLan = (LsbootLan) ((JAXBElement) element).getValue();
                        lanBoot = reconcileComputeLanBoot(lsbootLan, lanBoot, bootDef, null);
                        hasLanBoot = true;
                        Integer order = Integer.parseInt(lsbootLan.getOrder());
                        if (nonSanBootOrder == null) {
                            nonSanBootOrder = order;
                        } else if (order < nonSanBootOrder) {
                            nonSanBootOrder = order;
                        }
                    } else if (((JAXBElement) element).getValue() instanceof LsbootStorage) {
                        LsbootStorage lsbootStorage = (LsbootStorage) ((JAXBElement) element).getValue();
                        sanBoot = reconcileComputeSanBoot(lsbootStorage, sanBoot, bootDef, null);
                        hasSanBoot = true;
                        sanBootOrder = Integer.parseInt(lsbootStorage.getOrder());
                    } else if (((JAXBElement) element).getValue() instanceof LsbootSan) {
                        LsbootSan lsbootSan = (LsbootSan) ((JAXBElement) element).getValue();
                        sanBoot = reconcileComputeSanBoot(lsbootSan, sanBoot, bootDef, null);
                        hasSanBoot = true;
                        sanBootOrder = Integer.parseInt(lsbootSan.getOrder());
                    } else if (((JAXBElement) element).getValue() instanceof LsbootVirtualMedia) {
                        LsbootVirtualMedia lsbootVirtualMedia = (LsbootVirtualMedia) ((JAXBElement) element).getValue();
                        Integer order = Integer.parseInt(lsbootVirtualMedia.getOrder());
                        if (nonSanBootOrder == null) {
                            nonSanBootOrder = order;
                        } else if (order < nonSanBootOrder) {
                            nonSanBootOrder = order;
                        }
                    } else if (((JAXBElement) element).getValue() instanceof LsbootIScsi) {
                        LsbootIScsi lsbootIScsi = (LsbootIScsi) ((JAXBElement) element).getValue();
                        Integer order = Integer.parseInt(lsbootIScsi.getOrder());
                        if (nonSanBootOrder == null) {
                            nonSanBootOrder = order;
                        } else if (order < nonSanBootOrder) {
                            nonSanBootOrder = order;
                        }
                    }
                }

            }
        }
        if (hasSanBoot && nonSanBootOrder != null) {
            sanBoot = (ComputeSanBoot) _dbClient.queryObject(sanBoot.getId());
            if (nonSanBootOrder < sanBootOrder) {
                sanBoot.setIsFirstBootDevice(false);
            } else {
                sanBoot.setIsFirstBootDevice(true);
            }
            _dbClient.persistObject(sanBoot);
        }
        if (!hasSanBoot && sanBoot != null) {
            List<ComputeSanBoot> sanBoots = new ArrayList<ComputeSanBoot>();
            sanBoots.add(sanBoot);
            deleteComputeSanBoot(sanBoots);
        }
        if (!hasLanBoot && lanBoot != null) {
            List<ComputeLanBoot> lanBoots = new ArrayList<ComputeLanBoot>();
            lanBoots.add(lanBoot);
            deleteComputeLanBoot(lanBoots);
        }

        return bootDef;
    }

    private void deleteComputeBootDefs(List<ComputeBootDef> bootDefs) {

        for (ComputeBootDef bootDef : bootDefs) {

            // Retrieve associated ComputeSanBoot and delete it
            URIQueryResultList sanBootUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeBootDefComputeSanBootConstraint(bootDef.getId()),
                    sanBootUris);
            List<ComputeSanBoot> sanBootList = _dbClient.queryObject(ComputeSanBoot.class, sanBootUris, true);
            if (sanBootList != null && !sanBootList.isEmpty()) {
                deleteComputeSanBoot(sanBootList);
            }

            // Retrieve associated ComputeLanBoot and delete it
            URIQueryResultList lanBootUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeBootDefComputeLanBootConstraint(bootDef.getId()),
                    lanBootUris);
            List<ComputeLanBoot> lanBootList = _dbClient.queryObject(ComputeLanBoot.class, lanBootUris, true);
            if (lanBootList != null && !lanBootList.isEmpty()) {
                deleteComputeLanBoot(lanBootList);
            }

        }
        deleteDataObjects(new ArrayList<DataObject>(bootDefs));

    }

    private void deleteComputeLanBoot(List<ComputeLanBoot> lanBootList) {
        List<ComputeLanBoot> removeLanBoots = new ArrayList<ComputeLanBoot>();

        for (ComputeLanBoot lanBoot : lanBootList) {

            URIQueryResultList lanImageUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeLanBootImagePathsConstraint(lanBoot.getId()), lanImageUris);
            List<ComputeLanBootImagePath> lanBootPathList = _dbClient.queryObject(ComputeLanBootImagePath.class, lanImageUris, true);

            if (lanBootPathList != null && !lanBootPathList.isEmpty()) {
                deleteComputeLanBootImagePaths(lanBootPathList);
            }
            removeLanBoots.add(lanBoot);
        }
        deleteDataObjects(new ArrayList<DataObject>(removeLanBoots));

    }

    private void deleteComputeLanBootImagePaths(List<ComputeLanBootImagePath> lanBootPathList) {
        List<ComputeLanBootImagePath> removeLanBootImagePaths = new ArrayList<ComputeLanBootImagePath>();
        if (lanBootPathList != null && !lanBootPathList.isEmpty()) {
            removeLanBootImagePaths.addAll(lanBootPathList);
        }
        deleteDataObjects(new ArrayList<DataObject>(removeLanBootImagePaths));

    }

    private void deleteComputeSanBoot(List<ComputeSanBoot> sanBootList) {
        List<ComputeSanBoot> removeSanBoots = new ArrayList<ComputeSanBoot>();

        for (ComputeSanBoot sanBoot : sanBootList) {

            URIQueryResultList sanImageUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeSanBootImageConstraint(sanBoot.getId()), sanImageUris);
            List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

            if (sanBootImageList != null && !sanBootImageList.isEmpty()) {
                deleteComputeSanBootImages(sanBootImageList);
            }
            removeSanBoots.add(sanBoot);
        }
        deleteDataObjects(new ArrayList<DataObject>(removeSanBoots));

    }

    private void deleteComputeSanBootImages(List<ComputeSanBootImage> sanBootImageList) {
        List<ComputeSanBootImage> removeSanBootImages = new ArrayList<ComputeSanBootImage>();
        for (ComputeSanBootImage image : sanBootImageList) {
            URIQueryResultList sanImagePathUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeSanBootImagePathConstraint(image.getId()), sanImagePathUris);

            List<ComputeSanBootImagePath> sanBootPathList = _dbClient.queryObject(ComputeSanBootImagePath.class, sanImagePathUris, true);

            if (sanBootPathList != null && !sanBootPathList.isEmpty()) {
                deleteComputeSanBootImagePaths(sanBootPathList);
            }
            removeSanBootImages.add(image);
        }
        deleteDataObjects(new ArrayList<DataObject>(removeSanBootImages));

    }

    private void deleteComputeSanBootImagePaths(List<ComputeSanBootImagePath> sanBootPathList) {
        List<ComputeSanBootImagePath> removeSanBootImagePaths = new ArrayList<ComputeSanBootImagePath>();
        if (sanBootPathList != null && !sanBootPathList.isEmpty()) {
            removeSanBootImagePaths.addAll(sanBootPathList);
        }
        deleteDataObjects(new ArrayList<DataObject>(removeSanBootImagePaths));

    }

    private ComputeSanBoot reconcileComputeSanBoot(LsbootStorage lsbootStorage, ComputeSanBoot sanBoot, ComputeBootDef bootDef,
            ComputeBootPolicy bootPolicy) {

        if (sanBoot != null) {
            sanBoot.setLabel(lsbootStorage.getDn());
            if (lsbootStorage.getOrder() != null) {
                sanBoot.setOrder(Integer.valueOf(lsbootStorage.getOrder()));
            }
            _dbClient.persistObject(sanBoot);
        }
        if (sanBoot == null) {
            sanBoot = new ComputeSanBoot();
            URI uriSanBoot = URIUtil.createId(ComputeSanBoot.class);
            sanBoot.setId(uriSanBoot);
            if (bootDef != null) {
                sanBoot.setComputeBootDef(bootDef.getId());
            }
            if (bootPolicy != null) {
                sanBoot.setComputeBootPolicy(bootPolicy.getId());
            }
            sanBoot.setLabel(lsbootStorage.getDn());
            if (lsbootStorage.getOrder() != null) {
                sanBoot.setOrder(Integer.valueOf(lsbootStorage.getOrder()));
            }
            sanBoot.setIsFirstBootDevice(true);
            _dbClient.createObject(sanBoot);
        }

        Map<String, ComputeSanBootImage> computeSanBootImageMap = new HashMap<String, ComputeSanBootImage>();
        URIQueryResultList sanImageUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSanBootImageConstraint(sanBoot.getId()), sanImageUris);
        List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

        if (sanBootImageList != null && !sanBootImageList.isEmpty()) {
            for (ComputeSanBootImage image : sanBootImageList) {
                computeSanBootImageMap.put(image.getType(), image);
            }
        }

        if (lsbootStorage.getContent() != null && !lsbootStorage.getContent().isEmpty()) {
            for (Serializable e : lsbootStorage.getContent()) {
                if (e instanceof JAXBElement<?>) {
                    if (((JAXBElement) e).getValue() instanceof LsbootSanImage) {
                        LsbootSanImage lsSanImage = (LsbootSanImage) ((JAXBElement) e).getValue();
                        ComputeSanBootImage sanBootImage = computeSanBootImageMap.get(lsSanImage.getType());
                        computeSanBootImageMap.remove(lsSanImage.getType());
                        sanBootImage = reconcileComputeSanBootImage(lsSanImage, sanBootImage, sanBoot);
                    }
                }
            }
        }
        deleteComputeSanBootImages(new ArrayList<ComputeSanBootImage>(computeSanBootImageMap.values()));
        return sanBoot;
    }

    private ComputeSanBoot reconcileComputeSanBoot(LsbootSan lsbootSan, ComputeSanBoot sanBoot, ComputeBootDef bootDef,
            ComputeBootPolicy bootPolicy) {

        if (sanBoot != null) {
            sanBoot.setLabel(lsbootSan.getDn());
            if (lsbootSan.getOrder() != null) {
                sanBoot.setOrder(Integer.valueOf(lsbootSan.getOrder()));
            }
            _dbClient.persistObject(sanBoot);
        }
        if (sanBoot == null) {
            sanBoot = new ComputeSanBoot();
            URI uriSanBoot = URIUtil.createId(ComputeSanBoot.class);
            sanBoot.setId(uriSanBoot);
            sanBoot.setLabel(lsbootSan.getDn());
            if (bootDef != null) {
                sanBoot.setComputeBootDef(bootDef.getId());
            }
            if (bootPolicy != null) {
                sanBoot.setComputeBootPolicy(bootPolicy.getId());
            }
            if (lsbootSan.getOrder() != null) {
                sanBoot.setOrder(Integer.valueOf(lsbootSan.getOrder()));
            }
            sanBoot.setIsFirstBootDevice(true);
            _dbClient.createObject(sanBoot);
        }

        Map<String, ComputeSanBootImage> computeSanBootImageMap = new HashMap<String, ComputeSanBootImage>();
        URIQueryResultList sanImageUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSanBootImageConstraint(sanBoot.getId()), sanImageUris);
        List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

        if (sanBootImageList != null && !sanBootImageList.isEmpty()) {
            for (ComputeSanBootImage image : sanBootImageList) {
                computeSanBootImageMap.put(image.getType(), image);
            }
        }

        if (lsbootSan.getContent() != null && !lsbootSan.getContent().isEmpty()) {
            for (Serializable e : lsbootSan.getContent()) {
                if (e instanceof JAXBElement<?>) {
                    if (((JAXBElement) e).getValue() instanceof LsbootSanImage) {
                        LsbootSanImage lsSanImage = (LsbootSanImage) ((JAXBElement) e).getValue();
                        ComputeSanBootImage sanBootImage = computeSanBootImageMap.get(lsSanImage.getType());
                        computeSanBootImageMap.remove(lsSanImage.getType());
                        sanBootImage = reconcileComputeSanBootImage(lsSanImage, sanBootImage, sanBoot);

                    }
                }
            }
        }
        deleteComputeSanBootImages(new ArrayList<ComputeSanBootImage>(computeSanBootImageMap.values()));
        return sanBoot;
    }

    private ComputeSanBootImage reconcileComputeSanBootImage(LsbootSanImage sanImage, ComputeSanBootImage computeSanImage,
            ComputeSanBoot sanBoot) {

        if (computeSanImage != null) {
            computeSanImage.setDn(sanImage.getDn());
            computeSanImage.setVnicName(sanImage.getVnicName());
            _dbClient.persistObject(computeSanImage);
        }
        if (computeSanImage == null) {
            computeSanImage = new ComputeSanBootImage();
            URI uriSanBootImage = URIUtil.createId(ComputeSanBootImage.class);
            computeSanImage.setId(uriSanBootImage);
            computeSanImage.setType(sanImage.getType());
            computeSanImage.setVnicName(sanImage.getVnicName());
            computeSanImage.setDn(sanImage.getDn());
            computeSanImage.setComputeSanBoot(sanBoot.getId());

            _dbClient.createObject(computeSanImage);
        }

        Map<String, ComputeSanBootImagePath> computeSanBootImagePathMap = new HashMap<String, ComputeSanBootImagePath>();

        URIQueryResultList sanImagePathUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSanBootImagePathConstraint(computeSanImage.getId()), sanImagePathUris);
        List<ComputeSanBootImagePath> sanBootPathList = _dbClient.queryObject(ComputeSanBootImagePath.class, sanImagePathUris, true);
        if (sanBootPathList != null && !sanBootPathList.isEmpty()) {
            for (ComputeSanBootImagePath sanBootImagePath : sanBootPathList) {
                computeSanBootImagePathMap.put(sanBootImagePath.getType(), sanBootImagePath);
            }
        }

        if (sanImage.getContent() != null && !sanImage.getContent().isEmpty()) {
            for (Serializable e2 : sanImage.getContent()) {
                if (e2 instanceof JAXBElement<?>) {
                    if (((JAXBElement) e2).getValue() instanceof LsbootSanImagePath) {

                        LsbootSanImagePath lsSanImagePath = (LsbootSanImagePath) ((JAXBElement) e2).getValue();
                        ComputeSanBootImagePath sanBootImagePath = computeSanBootImagePathMap.get(lsSanImagePath.getType());
                        computeSanBootImagePathMap.remove(lsSanImagePath.getType());
                        sanBootImagePath = reconcileComputeSanBootImagePath(lsSanImagePath, sanBootImagePath, computeSanImage);
                    }
                }
            }
        }
        deleteComputeSanBootImagePaths(new ArrayList<ComputeSanBootImagePath>(computeSanBootImagePathMap.values()));
        return computeSanImage;
    }

    private ComputeSanBootImagePath reconcileComputeSanBootImagePath(LsbootSanImagePath lsSanImagePath,
            ComputeSanBootImagePath sanImagePath, ComputeSanBootImage computeSanImage) {

        if (sanImagePath != null) {
            sanImagePath.setDn(lsSanImagePath.getDn());
            sanImagePath.setType(lsSanImagePath.getType());
            // sanImagePath.setVnicName(lsSanImagePath.);
            if (lsSanImagePath.getLun() != null) {
                sanImagePath.setHlu(Long.parseLong(lsSanImagePath.getLun()));
            }

            sanImagePath.setPortWWN(lsSanImagePath.getWwn());
            sanImagePath.setComputeSanBootImage(computeSanImage.getId());
            _dbClient.persistObject(sanImagePath);

        }
        if (sanImagePath == null) {
            sanImagePath = new ComputeSanBootImagePath();
            URI uriSanBootImage = URIUtil.createId(ComputeSanBootImagePath.class);
            sanImagePath.setComputeSanBootImage(computeSanImage.getId());
            sanImagePath.setId(uriSanBootImage);
            sanImagePath.setDn(lsSanImagePath.getDn());
            sanImagePath.setType(lsSanImagePath.getType());
            // sanImagePath.setVnicName(sanImage.getVnicName());
            if (lsSanImagePath.getLun() != null) {
                sanImagePath.setHlu(Long.parseLong(lsSanImagePath.getLun()));
            }
            sanImagePath.setPortWWN(lsSanImagePath.getWwn());
            sanImagePath.setComputeSanBootImage(computeSanImage.getId());
            _dbClient.createObject(sanImagePath);

        }
        return sanImagePath;
    }

    private ComputeLanBoot reconcileComputeLanBoot(LsbootLan lsbootLan, ComputeLanBoot lanBoot, ComputeBootDef bootDef,
            ComputeBootPolicy bootPolicy) {

        if (lanBoot != null) {
            if (lsbootLan.getOrder() != null) {
                lanBoot.setOrder(Integer.valueOf(lsbootLan.getOrder()));
            }
            lanBoot.setProt(lsbootLan.getProt());
            lanBoot.setLabel(lsbootLan.getDn());
            _dbClient.persistObject(lanBoot);
        }
        if (lanBoot == null) {

            lanBoot = new ComputeLanBoot();
            URI uriLanBoot = URIUtil.createId(ComputeLanBoot.class);
            lanBoot.setId(uriLanBoot);
            if (bootDef != null) {
                lanBoot.setComputeBootDef(bootDef.getId());
            }
            if (bootPolicy != null) {
                lanBoot.setComputeBootPolicy(bootPolicy.getId());
            }
            if (lsbootLan.getOrder() != null) {
                lanBoot.setOrder(Integer.valueOf(lsbootLan.getOrder()));
            }
            lanBoot.setProt(lsbootLan.getProt());
            lanBoot.setLabel(lsbootLan.getDn());
            _dbClient.createObject(lanBoot);

        }

        Map<String, ComputeLanBootImagePath> lanBootImageMap = new HashMap<String, ComputeLanBootImagePath>();
        URIQueryResultList lanImageUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeLanBootImagePathsConstraint(lanBoot.getId()), lanImageUris);
        List<ComputeLanBootImagePath> lanBootPathList = _dbClient.queryObject(ComputeLanBootImagePath.class, lanImageUris, true);

        if (lanBootPathList != null && !lanBootPathList.isEmpty()) {
            for (ComputeLanBootImagePath lanImage : lanBootPathList) {
                lanBootImageMap.put(lanImage.getType(), lanImage);
            }
        }
        if (lsbootLan.getContent() != null && !lsbootLan.getContent().isEmpty()) {
            for (Serializable e : lsbootLan.getContent()) {
                if (e instanceof JAXBElement<?>) {
                    if (((JAXBElement) e).getValue() instanceof LsbootLanImagePath) {
                        LsbootLanImagePath lanImagePath = (LsbootLanImagePath) ((JAXBElement) e).getValue();
                        ComputeLanBootImagePath lanBootImagePath = lanBootImageMap.get(lanImagePath.getType());
                        lanBootImageMap.remove(lanImagePath.getType());
                        lanBootImagePath = reconcileComputeLanBootImagePath(lanImagePath, lanBootImagePath, lanBoot);
                    }
                }
            }
        }
        deleteComputeLanBootImagePaths(new ArrayList<ComputeLanBootImagePath>(lanBootImageMap.values()));

        return lanBoot;
    }

    private ComputeLanBootImagePath reconcileComputeLanBootImagePath(LsbootLanImagePath lanImagePath, ComputeLanBootImagePath lanImage,
            ComputeLanBoot lanBoot) {

        if (lanImage != null) {
            lanImage.setDn(lanImagePath.getDn());
            lanImage.setType(lanImagePath.getType());
            lanImage.setVnicName(lanImagePath.getVnicName());
            _dbClient.persistObject(lanImage);
        } else {

            lanImage = new ComputeLanBootImagePath();
            URI uriLanBootImage = URIUtil.createId(ComputeLanBootImagePath.class);
            lanImage.setComputeLanBoot(lanBoot.getId());
            lanImage.setId(uriLanBootImage);
            lanImage.setDn(lanImagePath.getDn());
            lanImage.setType(lanImagePath.getType());
            lanImage.setVnicName(lanImagePath.getVnicName());
            _dbClient.createObject(lanImage);
        }
        return lanImage;
    }

    private void reconcileServiceProfileTemplatesBootDefinitions(ComputeSystem cs, List<LsServer> lsServers) {

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemServiceProfileTemplateConstraint(cs.getId()), uris);

        Map<String, LsServer> lsServerMap = new HashMap<>();

        for (LsServer lsServer : lsServers) {
            lsServerMap.put(lsServer.getDn(), lsServer);
        }

        List<UCSServiceProfileTemplate> serviceTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class, uris, true);

        for (UCSServiceProfileTemplate serviceProfileTemplate : serviceTemplates) {
            LsServer lsServer = lsServerMap.get(serviceProfileTemplate.getDn());

            if (lsServer == null) {
                continue;
            }
            Map<String, Object> serviceProfileTemplateDetails = getServiceProfileTemplateDetails(lsServer);
            LsbootDef lsbootDef = (LsbootDef) serviceProfileTemplateDetails.get("associatedBootPolicy");
            if (lsbootDef != null) {
                _log.debug("Reconcile bootdef for SPT:" + serviceProfileTemplate.getLabel());
                ComputeBootDef computeBootDef = reconcileComputeBootDef(lsbootDef, serviceProfileTemplate, cs);
            } else {
                // Remove any computeBootDefs that are no longer needed.

                URIQueryResultList bootDefUris = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getServiceProfileTemplateComputeBootDefsConstraint(serviceProfileTemplate.getId()), bootDefUris);

                List<ComputeBootDef> bootDefs = _dbClient.queryObject(ComputeBootDef.class, bootDefUris, true);
                deleteComputeBootDefs(bootDefs);
            }

        }

    }

    private void reconcileServiceProfileTemplatesHBAs(ComputeSystem cs, List<LsServer> lsServers, VhbaHelper vsanLookupMap) {

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemServiceProfileTemplateConstraint(cs.getId()), uris);

        Map<String, LsServer> lsServerMap = new HashMap<>();

        for (LsServer lsServer : lsServers) {
            lsServerMap.put(lsServer.getDn(), lsServer);
        }

        List<UCSServiceProfileTemplate> serviceTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class, uris, true);

        for (UCSServiceProfileTemplate serviceProfileTemplate : serviceTemplates) {

            LsServer lsServer = lsServerMap.get(serviceProfileTemplate.getDn());

            if (lsServer == null) {
                continue;
            }

            Map<String, Object> serviceProfileTemplateDetails = getServiceProfileTemplateDetails(lsServer);
            Map<String, ComputeElementHBA> removeVhbas = new HashMap<>();
            Map<String, ComputeElementHBA> addVhbas = new HashMap<>();
            Map<String, ComputeElementHBA> updateVhbas = new HashMap<>();

            URIQueryResultList uriVhbas = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getServiceProfileTemplateComputeElemetHBAsConstraint(serviceProfileTemplate.getId()), uriVhbas);

            List<ComputeElementHBA> vbhas = _dbClient.queryObject(ComputeElementHBA.class, uriVhbas, true);
            for (ComputeElementHBA hba : vbhas) {
                removeVhbas.put(hba.getLabel(), hba);
            }

            for (VnicFc vnicFc : (List<VnicFc>) serviceProfileTemplateDetails.get("vhbas")) {

                ComputeElementHBA hba = removeVhbas.get(vnicFc.getName());
                if (hba != null) {
                    updateVhbas.put(vnicFc.getName(), hba);
                    removeVhbas.remove(hba.getLabel());
                    updateComputeElementHBA(hba, vsanLookupMap, vnicFc);
                } else {
                    hba = new ComputeElementHBA();
                    addVhbas.put(vnicFc.getName(), hba);
                    createComputeElementHBA(cs, serviceProfileTemplate, hba, vsanLookupMap, vnicFc);
                }
            }

            createDataObjects(new ArrayList<DataObject>(addVhbas.values()));
            persistDataObjects(new ArrayList<DataObject>(updateVhbas.values()));

            for (String name : removeVhbas.keySet()) {
                _log.info("Marked for deletion ComputeElementHBA: " + name);
            }
            deleteDataObjects(new ArrayList<DataObject>(removeVhbas.values()));
        }
    }

    private void reconcileServiceProfileTemplatesVnics(ComputeSystem cs, List<LsServer> lsServers) {

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemServiceProfileTemplateConstraint(cs.getId()), uris);

        Map<String, LsServer> lsServerMap = new HashMap<>();

        for (LsServer lsServer : lsServers) {
            lsServerMap.put(lsServer.getDn(), lsServer);
        }

        List<UCSServiceProfileTemplate> serviceTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class, uris, true);

        for (UCSServiceProfileTemplate serviceProfileTemplate : serviceTemplates) {

            LsServer lsServer = lsServerMap.get(serviceProfileTemplate.getDn());

            if (lsServer == null) {
                continue;
            }

            Map<String, Object> serviceProfileTemplateDetails = getServiceProfileTemplateDetails(lsServer);
            Map<String, ComputeVnic> removeVnics = new HashMap<>();
            Map<String, ComputeVnic> addVnics = new HashMap<>();
            Map<String, ComputeVnic> updateVnics = new HashMap<>();

            URIQueryResultList uriVnics = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getServiceProfileTemplateComputeVnicsConstraint(serviceProfileTemplate.getId()), uriVnics);

            List<ComputeVnic> vnics = _dbClient.queryObject(ComputeVnic.class, uriVnics, true);
            for (ComputeVnic vnic : vnics) {
                removeVnics.put(vnic.getName(), vnic);
            }

            for (VnicEther vnic : (List<VnicEther>) serviceProfileTemplateDetails.get("vnics")) {

                ComputeVnic nic = removeVnics.get(vnic.getName());
                if (nic != null) {
                    updateVnics.put(vnic.getName(), nic);
                    removeVnics.remove(nic.getLabel());
                    updateComputeVnics(nic, vnic);
                } else {
                    nic = new ComputeVnic();
                    addVnics.put(vnic.getName(), nic);
                    createComputeVnics(serviceProfileTemplate, nic, vnic);
                }
            }

            createDataObjects(new ArrayList<DataObject>(addVnics.values()));
            persistDataObjects(new ArrayList<DataObject>(updateVnics.values()));

            for (String name : removeVnics.keySet()) {
                _log.info("Marked for deletion ComputeElementHBA: " + name);
            }
            deleteDataObjects(new ArrayList<DataObject>(removeVnics.values()));
        }
    }

    private void createComputeVnics(UCSServiceProfileTemplate serviceProfile, ComputeVnic computeVnic, VnicEther vnic) {
        _log.info("Adding ComputeVnic name: " + vnic.getName());

        computeVnic.setName(vnic.getName());
        computeVnic.setLabel(vnic.getName());
        computeVnic.setDn(vnic.getDn());
        computeVnic.setServiceProfileTemplate(serviceProfile.getId());
        computeVnic.setId(URIUtil.createId(ComputeVnic.class));
        updateComputeVnics(computeVnic, vnic);
    }

    private void updateComputeVnics(ComputeVnic computeVnic, VnicEther vnic) {
        computeVnic.setMac(vnic.getAddr());
        computeVnic.setMtu(vnic.getMtu());
        computeVnic.setOrder(vnic.getOrder());
        computeVnic.setTemplateName(vnic.getNwTemplName());
        StringSet vlans = new StringSet();
        String nativeVlan = "";

        if (vnic.getContent() != null) {
            for (Object object : vnic.getContent()) {
                if (object instanceof JAXBElement && ((JAXBElement) object).getValue() != null) {
                    if (((JAXBElement) object).getValue() instanceof VnicEtherIf) {
                        VnicEtherIf vlan = (VnicEtherIf) ((JAXBElement) object).getValue();
                        vlans.add(vlan.getVnet());
                        if ("yes".equalsIgnoreCase(vlan.getDefaultNet())) {
                            nativeVlan = vlan.getVnet();
                        }
                    }
                }
            }
        }
        computeVnic.setNativeVlan(nativeVlan);
        computeVnic.setVlans(vlans);
    }

    private void removeServiceProfileTemplatesFromComputeVirtualPool(Collection<UCSServiceProfileTemplate> removeTemplates) {
        List<URI> ids = _dbClient.queryByType(ComputeVirtualPool.class, true);
        Iterator<ComputeVirtualPool> iter = _dbClient.queryIterativeObjects(ComputeVirtualPool.class, ids);

        while (iter.hasNext()) {
            Boolean dbUpdateRequired = false;
            ComputeVirtualPool cvp = iter.next();
            for (UCSServiceProfileTemplate template : removeTemplates) {
                if (cvp.getServiceProfileTemplates() != null
                        && cvp.getServiceProfileTemplates().contains(template.getId().toString())) {
                    _log.info("Removing UCSServiceProfileTemplate {} from ComputePool", template.getDn());
                    cvp.removeServiceProfileTemplate(template.getId().toString());
                    dbUpdateRequired = true;
                }
            }

            if (dbUpdateRequired) {
                _log.info("Persisting ComputeVirtualPool {},after UCSServiceProfileTemplate removal", cvp.getId());
                _dbClient.persistObject(cvp);
            }
        }
    }

    private void reconcileUplinkPorts(ComputeSystem cs, Map<String, FcPIo> uplinkMap, Map<String, SwFcSanEp> fcInterfaceMap,
            Map<String, Set<String>> unpinnedVsans) {
        _log.info("Reconciling FIC uplink ports");
        Map<String, ComputeFabricUplinkPort> removePorts = new HashMap<>();
        Map<String, ComputeFabricUplinkPort> updatePorts = new HashMap<>();
        Map<String, ComputeFabricUplinkPort> addPorts = new HashMap<>();

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeFabricUplinkPortConstraint(cs.getId()), uris);

        List<ComputeFabricUplinkPort> uplinkPorts = _dbClient.queryObject(ComputeFabricUplinkPort.class, uris, true);

        for (ComputeFabricUplinkPort port : uplinkPorts) {
            removePorts.put(port.getDn(), port);
        }

        // discovered data
        for (FcPIo fcPIo : uplinkMap.values()) {
            ComputeFabricUplinkPort cfup = removePorts.get(fcPIo.getDn());

            if (cfup != null) {
                updatePorts.put(fcPIo.getDn(), cfup);
                removePorts.remove(fcPIo.getDn());
                updateUplinkPorts(cfup, fcPIo, fcInterfaceMap, unpinnedVsans);
            } else {
                cfup = new ComputeFabricUplinkPort();
                addPorts.put(fcPIo.getDn(), cfup);
                createUplinkPorts(cs, cfup, fcPIo, fcInterfaceMap, unpinnedVsans);
            }
        }
        createDataObjects(new ArrayList<DataObject>(addPorts.values()));
        persistDataObjects(new ArrayList<DataObject>(updatePorts.values()));
        deleteDataObjects(new ArrayList<DataObject>(removePorts.values()));
    }

    private void createUplinkPorts(ComputeSystem cs, ComputeFabricUplinkPort cfup, FcPIo fcPIo,
            Map<String, SwFcSanEp> fcInterfaceMap, Map<String, Set<String>> unpinnedVsans) {
        URI uri = URIUtil.createId(ComputeFabricUplinkPort.class);
        cfup.setId(uri);
        cfup.setComputeSystem(cs.getId());
        cfup.setDn(fcPIo.getDn());
        cfup.setLabel(fcPIo.getDn());
        updateUplinkPorts(cfup, fcPIo, fcInterfaceMap, unpinnedVsans);
    }

    private void updateUplinkPorts(ComputeFabricUplinkPort cfup, FcPIo fcPIo, Map<String, SwFcSanEp> fcInterfaceMap,
            Map<String, Set<String>> unpinnedVsans) {
        SwFcSanEp fcInterface = fcInterfaceMap.get(fcPIo.getDn());
        if (fcInterface != null) {
            // Set vSanId
            String vsanId = fcInterface.getPortVsanId();
            cfup.setVsanId(vsanId);
            /*
             * If
             * the VSAN ID 1 is the default, then a defaulted port will trunk all non-pinned VSANs
             * else
             * the VSAN will only be available via the pinned port(s)
             */
            if (vsanId.equals("1") && unpinnedVsans.get(fcPIo.getSwitchId()) != null) {
                StringSet vsanStringSet = new StringSet();
                vsanStringSet.add(vsanId);
                cfup.setVsans(vsanStringSet);
                cfup.addVsans(unpinnedVsans.get(fcPIo.getSwitchId()));
            }
            else {
                Set<String> vsanSet = new HashSet<>();
                vsanSet.add(vsanId);
                cfup.addVsans(vsanSet);
            }
            cfup.setPeerDn(fcInterface.getPeerDn());
        }
        cfup.setSwitchId(fcPIo.getSwitchId());
        cfup.setWwpn(fcPIo.getWwn());
    }

    private void
            reconcileUplinkPortChannels(ComputeSystem cs, Map<String, SwFcSanPc> portChannelMap, Map<String, Set<String>> unpinnedVsans) {
        _log.info("Reconciling FIC uplink port channels");

        Map<String, ComputeFabricUplinkPortChannel> removePorts = new HashMap<String, ComputeFabricUplinkPortChannel>();
        Map<String, ComputeFabricUplinkPortChannel> updatePorts = new HashMap<String, ComputeFabricUplinkPortChannel>();
        Map<String, ComputeFabricUplinkPortChannel> addPorts = new HashMap<String, ComputeFabricUplinkPortChannel>();

        /*
         * Build a map with peerDns and discovered uplink ports.
         */
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeFabricUplinkPortConstraint(cs.getId()), uris);

        List<ComputeFabricUplinkPort> uplinkPorts = _dbClient.queryObject(ComputeFabricUplinkPort.class, uris, true);

        Map<String, ComputeFabricUplinkPort> peerDnUplinkPortMap = new HashMap<>();
        for (ComputeFabricUplinkPort port : uplinkPorts) {
            if (port.getPeerDn() != null) {
                String peerDn = port.getPeerDn();
                if (peerDn.endsWith("/")) {
                    peerDn = peerDn.substring(0, peerDn.length() - 1);
                }
                peerDn = peerDn.substring(0, peerDn.lastIndexOf('/'));
                peerDnUplinkPortMap.put(peerDn, port);
            }
        }

        /*
         * Pulling uplink ports from the Database.
         */
        uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeUplinkPortChannelConstraint(cs.getId()), uris);
        List<ComputeFabricUplinkPortChannel> portsChannels = _dbClient.queryObject(ComputeFabricUplinkPortChannel.class, uris, true);

        for (ComputeFabricUplinkPortChannel pc : portsChannels) {
            removePorts.put(pc.getDn(), pc);
        }

        // discovered data
        for (SwFcSanPc pc : portChannelMap.values()) {

            ComputeFabricUplinkPortChannel cfup = removePorts.get(pc.getDn());

            ComputeFabricUplinkPort associatedPort = peerDnUplinkPortMap.get(pc.getPeerDn());
            /*
             * look for the port channel in the peerDn and uplink port map
             * If not found, the uplink port channel is considered inactive.
             * Ignore the object if new. Or simply delete during reconciliation.
             */
            if (associatedPort == null || pc.getPortId() == null || associatedPort.getWwpn() == null) {
                continue;
            }

            if (cfup != null) {
                updatePorts.put(pc.getDn(), cfup);
                removePorts.remove(pc.getDn());
                updateUplinkPortChannels(cfup, pc, unpinnedVsans, associatedPort);
            } else {
                cfup = new ComputeFabricUplinkPortChannel();
                addPorts.put(pc.getDn(), cfup);
                createUplinkPortChannels(cs, cfup, pc, unpinnedVsans, associatedPort);
            }
        }

        createDataObjects(new ArrayList<DataObject>(addPorts.values()));
        persistDataObjects(new ArrayList<DataObject>(updatePorts.values()));
        deleteDataObjects(new ArrayList<DataObject>(removePorts.values()));
    }

    private void createUplinkPortChannels(ComputeSystem cs, ComputeFabricUplinkPortChannel cfup, SwFcSanPc pc,
            Map<String, Set<String>> unpinnedVsans, ComputeFabricUplinkPort associatedPort) {
        URI uri = URIUtil.createId(ComputeFabricUplinkPortChannel.class);
        cfup.setId(uri);
        cfup.setComputeSystem(cs.getId());
        cfup.setDn(pc.getDn());
        cfup.setLabel(pc.getDn());
        updateUplinkPortChannels(cfup, pc, unpinnedVsans, associatedPort);
    }

    private void updateUplinkPortChannels(ComputeFabricUplinkPortChannel cfup, SwFcSanPc pc, Map<String, Set<String>> unpinnedVsans,
            ComputeFabricUplinkPort associatedPort) {
        // Set vSanId
        String vsanId = pc.getPortVsanId();
        cfup.setVsanId(vsanId);
        /*
         * look for the port channel in the peerDn and uplink port map
         * If not found, the uplink port channel needs to be removed.
         */
        if (vsanId.equals("1") && unpinnedVsans.get(pc.getSwitchId()) != null) {
            StringSet vsanStringSet = new StringSet();
            vsanStringSet.add(vsanId);
            cfup.setVsans(vsanStringSet);
            cfup.addVsans(unpinnedVsans.get(pc.getSwitchId()));
        }
        else {
            Set<String> vsanSet = new HashSet<>();
            vsanSet.add(vsanId);
            cfup.addVsans(vsanSet);
        }
        cfup.setPeerDn(pc.getPeerDn());
        cfup.setSwitchId(pc.getSwitchId());

        cfup.setWwpn(generatePortChannelWnn(associatedPort.getWwpn(), pc.getPortId()));
    }

    private String generatePortChannelWnn(String seedWwn, String portChannelId) {
        /*
         * The WWN seed will be the last six hex digits of that port's WWN
         * The port channel's WWN is computed as:
         * '24:' + hex(portchannelId) + ':' + WWN seed
         */
        String wwn = seedWwn.substring(6, seedWwn.length());
        String portChannelIdHex = Long.toHexString(parseNumber(portChannelId).longValue()).toUpperCase();
        return "24:" + StringUtils.leftPad(portChannelIdHex, 2, '0') + ":" + wwn;  // COP-17862 (add leading 0)
    }

    /**
     * Created COPP-38 to track the sonar issue.
     *
     * @param vsanList
     * @param fcInterfaceMap
     * @return
     */
    @SuppressWarnings({ "squid:S2175" })
    private Map<String, Set<String>> getUnpinnedVSans(List<SwVsan> vsanList, Map<String, SwFcSanEp> fcInterfaceMap) {
        Map<String, Set<String>> switchWiseVsan = new HashMap<>();

        for (SwVsan vsan : vsanList) {

            if (switchWiseVsan.containsKey(vsan.getSwitchId())) {
                switchWiseVsan.get(vsan.getSwitchId()).add(vsan.getId());
            } else {
                Set<String> set = new HashSet<>();
                set.add(vsan.getId());
                switchWiseVsan.put(vsan.getSwitchId(), set);
            }
        }

        for (SwFcSanEp swInterfaces : fcInterfaceMap.values()) {
            Set<String> vsanSet = switchWiseVsan.get(swInterfaces.getSwitchId());
            if (vsanSet == null) {
                continue;
            }

            if (vsanSet.contains(swInterfaces.getPortVsanId())) {
                vsanSet.remove(swInterfaces.getPortVsanId());
            }
        }
        return switchWiseVsan;
    }

    private void reconcileVlans(ComputeSystem cs, List<FabricVlan> vlanList) {
        StringSet vlans = new StringSet();
        for (FabricVlan vlan : vlanList) {
            vlans.add(vlan.getId());
        }
        cs.setVlans(vlans);
    }

    private void createDataObjects(List<DataObject> objects) {
        if (!objects.isEmpty()) {
            _dbClient.createObject(objects);
        }
    }

    private void persistDataObjects(List<DataObject> objects) {
        if (!objects.isEmpty()) {
            _dbClient.persistObject(objects);
        }
    }

    private void deleteDataObjects(List<DataObject> objects) {
        if (!objects.isEmpty()) {
            _dbClient.markForDeletion(objects);
        }
    }

    private URL getUcsmURL(ComputeSystem cs) {
        URL ucsmURL;
        try {
            if (cs.getSecure()) {
                ucsmURL = new URL("https", cs.getIpAddress(),
                        cs.getPortNumber(), "/nuova");
            } else {
                ucsmURL = new URL("http", cs.getIpAddress(),
                        cs.getPortNumber(), "/nuova");
            }
        } catch (MalformedURLException e) {
            _log.error(
                    "Invalid IP Address / Hostname / Port for Compute System: "
                            + cs.getId(), e);
            throw DeviceControllerException.exceptions.invalidURI(e);
        }
        return ucsmURL;
    }

    private class VhbaHelper {

        private final String VSAN_NAME = "vsan_name";
        private final String VSAN_SWITCH_ID = "switch_id";
        private final String VSAN_ID = "vsanId";

        private Map<String, List<FabricVsan>> lookUpMap = null;

        public VhbaHelper() {
        }

        public VhbaHelper(List<FabricVsan> vsanFabricList) {
            lookUpMap = new HashMap<>();
            for (FabricVsan fabricVsan : vsanFabricList) {
                if (fabricVsan.getName() == null) {
                    continue;
                }

                if (lookUpMap.get(fabricVsan.getName()) == null) {
                    List<FabricVsan> sanList = new ArrayList<>();
                    sanList.add(fabricVsan);
                    lookUpMap.put(fabricVsan.getName(), sanList);
                } else {
                    lookUpMap.get(fabricVsan.getName()).add(fabricVsan);
                }
            }
        }

        public String getLookUpVsanId(VnicFc vnicFc) {
            FabricVsan vsan = getVsan(vnicFc);
            if (vsan != null) {
                return vsan.getId();
            }
            return null;
        }

        public String getVsanId(VnicFc vnicFc) {
            return getDetailMap(vnicFc).get(VSAN_ID);
        }

        public FabricVsan getVsan(VnicFc vnicFc) {
            if (vnicFc == null) {
                return null;
            }
            Map<String, String> details = getDetailMap(vnicFc);

            String vsanName = details.get(VSAN_NAME);
            String switchId = details.get(VSAN_SWITCH_ID);
            if (vsanName == null) {
                return null;
            }

            List<FabricVsan> candidateVsans = lookUpMap.get(vsanName);
            if (candidateVsans == null) {
                return null;
            }

            // handles single instances
            if (candidateVsans.size() == 1) {
                return candidateVsans.get(0);
            }
            // handles dupicates across switches
            for (FabricVsan fabricVsan : candidateVsans) {
                if (fabricVsan.getSwitchId().equalsIgnoreCase(switchId)) {
                    return fabricVsan;
                }
            }
            return candidateVsans.get(0);
        }

        private Map<String, String> getDetailMap(VnicFc vnicFc) {
            Map<String, String> details = new HashMap<>();
            if (vnicFc != null && vnicFc.getContent() != null) {
                for (Object object : vnicFc.getContent()) {
                    if (object instanceof JAXBElement && ((JAXBElement) object).getValue() != null) {
                        if (((JAXBElement) object).getValue() instanceof VnicFcIf) {
                            VnicFcIf fc = (VnicFcIf) (((JAXBElement) object).getValue());
                            details.put(VSAN_NAME, fc.getName());
                            details.put(VSAN_SWITCH_ID, fc.getSwitchId());
                            details.put(VSAN_ID, fc.getVnet());
                            break;
                        }
                    }
                }

            }
            return details;
        }
    }

    private Boolean isBladeAvailable(ComputeBlade blade) {

        if (BLADE_REMOVED.equalsIgnoreCase(blade.getOperState())) {
            return false;
        }

        // CTRL-8728 check for the blade operstate as config-failure
        if (BLADE_CFG_FAILURE.equalsIgnoreCase(blade.getOperState())) {
            return false;
        }

        return true;
    }

    private Number parseNumber(String number) {
        try {
            return NumberFormat.getInstance().parse(number);
        } catch (Exception e) {
            _log.error("Encountered an parse error for string {} caused by {}", number, e.getMessage());
        }
        return new Integer(0);
    }

    private void matchComputeBladesToHosts(ComputeSystem cs) {
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeElemetsConstraint(cs.getId()), uris);
        HostToComputeElementMatcher.matchComputeElementsToHostsByUuid(uris, _dbClient);
    }

}
