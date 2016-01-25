/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.ComputeMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.resource.utils.PurgeRunnable;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.computecontroller.ComputeController;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ComputeBootDef;
import com.emc.storageos.db.client.model.ComputeBootPolicy;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeElementHBA;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeImageServer.ComputeImageServerStatus;
import com.emc.storageos.db.client.model.ComputeLanBoot;
import com.emc.storageos.db.client.model.ComputeLanBootImagePath;
import com.emc.storageos.db.client.model.ComputeSanBoot;
import com.emc.storageos.db.client.model.ComputeSanBootImage;
import com.emc.storageos.db.client.model.ComputeSanBootImagePath;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.ComputeVnic;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UCSServiceProfileTemplate;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemBulkRep;
import com.emc.storageos.model.compute.ComputeSystemCreate;
import com.emc.storageos.model.compute.ComputeSystemList;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.compute.ComputeSystemUpdate;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * ComputeSystemService manages the lifecycle of a Compute System and Compute Elements that belong to a ComputeSystem.
 *
 * Operations (Compute System):
 * Get All
 * Get/Fetch
 * Create
 * Update
 * Delete
 * Register/Unregister
 *
 * Operations (Compute Element):
 * Get All
 *
 * @author Dranov,Vladislav
 * @author Prabhakara,Janardhan
 *
 */
@Path("/vdc/compute-systems")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeSystemService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(ComputeSystemService.class);

    private static final String EVENT_SERVICE_TYPE = "ComputeSystem";
    private static final String EVENT_SERVICE_SOURCE = "ComputeSystemService";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private ComputeVirtualPoolService computeVirtualPoolService;

    /**
     * Gets a detailed representation of the Compute System
     *
     * @param id
     *            the URN of a ViPR Compute System
     * @brief Show compute system
     * @return A detailed representation of the Compute System
     * @throws DatabaseException
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ComputeSystemRestRep getComputeSystem(@PathParam("id") URI id) throws DatabaseException {
        ArgValidator.checkFieldUriType(id, ComputeSystem.class, "id");
        ComputeSystem cs = queryResource(id);
        return new mapComputeSystemWithServiceProfileTemplates().apply(cs);

    }

    public List<NamedRelatedResourceRep> getServiceProfileTemplatesForComputeSystem(ComputeSystem cs, DbClient dbClient) {
        List<NamedRelatedResourceRep> serviceProfileTemplates = new ArrayList<NamedRelatedResourceRep>();

        URIQueryResultList sptIdList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemServiceProfileTemplateConstraint(cs.getId()), sptIdList);

        List<UCSServiceProfileTemplate> profileTemplateList = dbClient.queryObject(UCSServiceProfileTemplate.class, sptIdList, true);

        for (UCSServiceProfileTemplate serviceProfileTemplate : profileTemplateList) {
            if (!serviceProfileTemplate.getUpdating()) {
                NamedRelatedResourceRep sptNamedRelatedResource = new NamedRelatedResourceRep();
                sptNamedRelatedResource.setId(serviceProfileTemplate.getId());
                sptNamedRelatedResource.setName(serviceProfileTemplate.getLabel());
                serviceProfileTemplates.add(sptNamedRelatedResource);
            } else {
                _log.info(" updating service profile template:" + serviceProfileTemplate.getLabel() + " id:"
                        + serviceProfileTemplate.getId().toString());
                boolean valid = isUpdatingSPTValid(serviceProfileTemplate, dbClient);

                if (valid) {
                    NamedRelatedResourceRep sptNamedRelatedResource = new NamedRelatedResourceRep();
                    sptNamedRelatedResource.setId(serviceProfileTemplate.getId());
                    sptNamedRelatedResource.setName(serviceProfileTemplate.getLabel());
                    serviceProfileTemplates.add(sptNamedRelatedResource);
                } else {
                    _log.info("invalid uSPT");
                }
            }
            // TODO: Check if SPT uses updating vnic templates here. If so, it is invalid for use.
        }

        return serviceProfileTemplates;
    }

    public boolean isUpdatingSPTValid(UCSServiceProfileTemplate serviceProfileTemplate, DbClient dbClient) {
        boolean valid = false;
        // Check whether boot definition or boot policy is specified
        // If so, check if enforce vnic names is specified and if so validate vnic names.
        URIQueryResultList uriBootPolicies = new URIQueryResultList();

        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getComputeSystemBootPolicyConstraint(serviceProfileTemplate.getComputeSystem()),
                uriBootPolicies);
        List<ComputeBootPolicy> bootPolicyList = dbClient.queryObject(ComputeBootPolicy.class, uriBootPolicies, true);

        ComputeBootDef bootDef = null;
        URIQueryResultList bootDefUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getServiceProfileTemplateComputeBootDefsConstraint(serviceProfileTemplate.getId()), bootDefUris);

        List<ComputeBootDef> bootDefs = _dbClient.queryObject(ComputeBootDef.class, bootDefUris, true);

        if (!bootDefs.isEmpty()) {
            _log.debug("has boot def");
            bootDef = bootDefs.get(0);
            valid = isSPTBootDefinitionValid(serviceProfileTemplate, bootDef);
        } else if (serviceProfileTemplate.getAssociatedBootPolicy() != null) {
            _log.debug("has boot policy:" + serviceProfileTemplate.getAssociatedBootPolicy());
            for (ComputeBootPolicy bootPolicy : bootPolicyList) {
                if (bootPolicy.getDn().equals(serviceProfileTemplate.getAssociatedBootPolicy())) {
                    valid = isSPTBootPolicyValid(serviceProfileTemplate, bootPolicy);
                }
            }
        } else {
            _log.info("Updating SPT with no boot policy or boot def set is invalid");

        }
        return valid;

    }

    private boolean isSPTBootPolicyValid(UCSServiceProfileTemplate serviceProfileTemplate, ComputeBootPolicy bootPolicy) {
        boolean valid = true;
        _log.debug("validating SPT Boot Policy" + bootPolicy.getId().toString());
        if (bootPolicy.getEnforceVnicVhbaNames() == true) {
            _log.debug("enforce vhba vnic names -- true");
            URIQueryResultList uriVhbas = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getServiceProfileTemplateComputeElemetHBAsConstraint(serviceProfileTemplate.getId()), uriVhbas);

            List<ComputeElementHBA> vhbas = _dbClient.queryObject(ComputeElementHBA.class, uriVhbas, true);

            URIQueryResultList uriSanBoot = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeBootPolicyComputeSanBootConstraint(bootPolicy.getId()), uriSanBoot);

            List<ComputeSanBoot> computeSanBoots = _dbClient.queryObject(ComputeSanBoot.class, uriSanBoot, true);
            if (computeSanBoots == null || computeSanBoots.isEmpty()) {
                _log.error("SPT: " + serviceProfileTemplate.getLabel() + " no san boot policy specified");
                valid = false;
            }
            for (ComputeSanBoot computeSanBoot : computeSanBoots) {

                if (!computeSanBoot.getIsFirstBootDevice()) {
                    _log.error("SPT: " + serviceProfileTemplate.getLabel() + " san is not the first boot device");
                    valid = false;
                }

                URIQueryResultList sanImageUris = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getComputeSanBootImageConstraint(computeSanBoot.getId()), sanImageUris);
                List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

                for (ComputeSanBootImage image : sanBootImageList) {
                    boolean matched = false;
                    for (ComputeElementHBA hba : vhbas) {
                        if (hba.getLabel().equals(image.getVnicName())) {
                            matched = true;
                        }
                    }
                    if (!matched) {
                        _log.error("SPT: "
                                + serviceProfileTemplate.getLabel()
                                + " enforces vnic and vhba names,but hba names in san boot policy do not match the names of the hbas on the template");
                        valid = false;
                    }
                }
            }

            URIQueryResultList uriVnics = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getServiceProfileTemplateComputeVnicsConstraint(serviceProfileTemplate.getId()), uriVnics);

            List<ComputeVnic> vnics = _dbClient.queryObject(ComputeVnic.class, uriVnics, true);

            URIQueryResultList uriLanBoot = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeBootPolicyComputeLanBootConstraint(bootPolicy.getId()), uriLanBoot);

            List<ComputeLanBoot> computeLanBoots = _dbClient.queryObject(ComputeLanBoot.class, uriLanBoot, true);
            for (ComputeLanBoot computeLanBoot : computeLanBoots) {

                URIQueryResultList lanImageUris = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getComputeLanBootImagePathsConstraint(computeLanBoot.getId()), lanImageUris);
                List<ComputeLanBootImagePath> lanBootImageList = _dbClient.queryObject(ComputeLanBootImagePath.class, lanImageUris, true);

                for (ComputeLanBootImagePath image : lanBootImageList) {
                    boolean matched = false;
                    for (ComputeVnic nic : vnics) {
                        if (nic.getName().equals(image.getVnicName())) {
                            matched = true;
                        }
                    }
                    if (!matched) {
                        _log.error("SPT: "
                                + serviceProfileTemplate.getLabel()
                                + " enforces vnic and vhba names,but vnic names in lan boot policy do not match the names of the hbas on the template");
                        valid = false;
                    }
                }
            }

        }

        return valid;
    }

    private boolean isSPTBootDefinitionValid(UCSServiceProfileTemplate serviceProfileTemplate, ComputeBootDef bootDef) {
        boolean valid = true;
        _log.debug("validating SPT Boot Def " + bootDef.getId().toString());

        if (bootDef.getEnforceVnicVhbaNames() == true) {
            _log.debug("enforce vhba vnic names -- true");
            URIQueryResultList uriVhbas = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getServiceProfileTemplateComputeElemetHBAsConstraint(serviceProfileTemplate.getId()), uriVhbas);

            List<ComputeElementHBA> vhbas = _dbClient.queryObject(ComputeElementHBA.class, uriVhbas, true);

            URIQueryResultList uriSanBoot = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeBootDefComputeSanBootConstraint(bootDef.getId()), uriSanBoot);

            List<ComputeSanBoot> computeSanBoots = _dbClient.queryObject(ComputeSanBoot.class, uriSanBoot, true);
            if (computeSanBoots == null || computeSanBoots.isEmpty()) {
                _log.error("SPT: " + serviceProfileTemplate.getLabel() + " no san boot policy specified");
                valid = false;
            }
            for (ComputeSanBoot computeSanBoot : computeSanBoots) {
                if (!computeSanBoot.getIsFirstBootDevice()) {
                    _log.error("SPT: " + serviceProfileTemplate.getLabel() + " san is not the first boot device");
                    valid = false;
                }
                URIQueryResultList sanImageUris = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getComputeSanBootImageConstraint(computeSanBoot.getId()), sanImageUris);
                List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

                for (ComputeSanBootImage image : sanBootImageList) {
                    boolean matched = false;
                    for (ComputeElementHBA hba : vhbas) {
                        if (hba.getLabel().equals(image.getVnicName())) {
                            matched = true;
                        }
                    }
                    if (!matched) {
                        _log.error("SPT: "
                                + serviceProfileTemplate.getLabel()
                                + " enforces vnic and vhba names,but hba names in san boot policy do not match the names of the hbas on the template");
                        valid = false;
                    }
                }
            }

            URIQueryResultList uriVnics = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getServiceProfileTemplateComputeVnicsConstraint(serviceProfileTemplate.getId()), uriVnics);

            List<ComputeVnic> vnics = _dbClient.queryObject(ComputeVnic.class, uriVnics, true);

            URIQueryResultList uriLanBoot = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeBootDefComputeLanBootConstraint(bootDef.getId()), uriLanBoot);

            List<ComputeLanBoot> computeLanBoots = _dbClient.queryObject(ComputeLanBoot.class, uriLanBoot, true);
            for (ComputeLanBoot computeLanBoot : computeLanBoots) {
                URIQueryResultList lanImageUris = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getComputeLanBootImagePathsConstraint(computeLanBoot.getId()), lanImageUris);
                List<ComputeLanBootImagePath> lanBootImageList = _dbClient.queryObject(ComputeLanBootImagePath.class, lanImageUris, true);

                for (ComputeLanBootImagePath image : lanBootImageList) {
                    boolean matched = false;
                    for (ComputeVnic nic : vnics) {
                        if (nic.getName().equals(image.getVnicName())) {
                            matched = true;
                        }
                    }
                    if (!matched) {
                        _log.error("SPT: "
                                + serviceProfileTemplate.getLabel()
                                + " enforces vnic and vhba names,but vnic names in lan boot policy do not match the names of the hbas on the template");
                        valid = false;
                    }
                }
            }

        }

        return valid;
    }

    /*
     * Check whether the SPT's vsans are in atleast one of the varrays' networks. If yes, valid.
     */
    public boolean isServiceProfileTemplateValidForVarrays(StringSet varrayIds, URI sptId) {
        boolean isValid = true;
        UCSServiceProfileTemplate template = (UCSServiceProfileTemplate) _dbClient.queryObject(sptId);
        Set<String> networkVsanIds = new HashSet<String>();
        URIQueryResultList uriVhbas = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getServiceProfileTemplateComputeElemetHBAsConstraint(sptId), uriVhbas);
        List<ComputeElementHBA> vhbas = _dbClient.queryObject(ComputeElementHBA.class, uriVhbas, true);

        // Filter out SPTs without any vhbas
        if ((vhbas == null) || vhbas.isEmpty()) {
            isValid = false;
            _log.info("ServiceProfileTemplate " + template.getLabel() + " does not have any vhbas and hence is not valid for use.");
            return isValid;
        }

        for (String varrayId : varrayIds) {
            // get varray networks
            List<Network> networks = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, URI.create(varrayId), Network.class,
                    "connectedVirtualArrays");
            // collect network vsanIds
            Set<String> varrayNetworkVsanIds = new HashSet<String>();
            for (Network network : networks) {
                if (StorageProtocol.Transport.FC.name().equalsIgnoreCase(network.getTransportType())) {
                    varrayNetworkVsanIds.add(network.getNativeId());
                    // _log.debug("varray vsan :"+ network.getNativeId());
                }
            }
            if (networkVsanIds.isEmpty()) {
                networkVsanIds.addAll(varrayNetworkVsanIds);
            } else {
                networkVsanIds.retainAll(varrayNetworkVsanIds);
            }
            for (ComputeElementHBA vhba : vhbas) {
                String vsanId = vhba.getVsanId();
                _log.debug("vhba vsan:" + vsanId);
                if (!networkVsanIds.contains(vsanId)) {
                    isValid = false;
                    _log.error("SPT " + template.getLabel() + " has hba on vsan " + vsanId + " not included in varray " + varrayId);
                    return isValid;
                }
            }
        }

        if (template.getUpdating()) {
            isValid = isUpdatingSPTValidForVarrays(varrayIds, template);
        }

        return isValid;
    }

    private boolean isUpdatingSPTValidForVarrays(StringSet varrayIds, UCSServiceProfileTemplate serviceProfileTemplate) {
        boolean isValid = true;
        _log.debug("Is uSPT:" + serviceProfileTemplate.getLabel() + " valid for varrays");

        URIQueryResultList uriBootPolicies = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getComputeSystemBootPolicyConstraint(serviceProfileTemplate.getComputeSystem()),
                uriBootPolicies);
        List<ComputeBootPolicy> bootPolicyList = _dbClient.queryObject(ComputeBootPolicy.class, uriBootPolicies, true);

        ComputeBootDef bootDef = null;
        URIQueryResultList bootDefUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getServiceProfileTemplateComputeBootDefsConstraint(serviceProfileTemplate.getId()), bootDefUris);

        List<ComputeBootDef> bootDefs = _dbClient.queryObject(ComputeBootDef.class, bootDefUris, true);

        if (!bootDefs.isEmpty()) {
            _log.debug("has boot def");
            bootDef = bootDefs.get(0);
            URIQueryResultList uriSanBoot = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeBootDefComputeSanBootConstraint(bootDef.getId()), uriSanBoot);

            List<ComputeSanBoot> computeSanBoots = _dbClient.queryObject(ComputeSanBoot.class, uriSanBoot, true);
            for (ComputeSanBoot computeSanBoot : computeSanBoots) {
                isValid = isSanBootValidForVarrays(varrayIds, computeSanBoot);
            }
        } else if (serviceProfileTemplate.getAssociatedBootPolicy() != null) {
            _log.debug("has boot policy:" + serviceProfileTemplate.getAssociatedBootPolicy());
            for (ComputeBootPolicy bootPolicy : bootPolicyList) {
                if (bootPolicy.getDn().equals(serviceProfileTemplate.getAssociatedBootPolicy())) {
                    URIQueryResultList uriSanBoot = new URIQueryResultList();
                    _dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getComputeBootPolicyComputeSanBootConstraint(bootPolicy.getId()), uriSanBoot);

                    List<ComputeSanBoot> computeSanBoots = _dbClient.queryObject(ComputeSanBoot.class, uriSanBoot, true);
                    for (ComputeSanBoot computeSanBoot : computeSanBoots) {
                        isValid = isSanBootValidForVarrays(varrayIds, computeSanBoot);
                    }
                }
            }
        } else {
            _log.info("Updating SPT with no boot policy or boot def set is invalid");

        }
        _log.info("SPT:" + serviceProfileTemplate.getLabel() + "isValid:" + isValid);
        return isValid;
    }

    private boolean isSanBootValidForVarrays(StringSet varrayIds, ComputeSanBoot computeSanBoot) {
        _log.debug("validate San Boot For Varrays");
        boolean isValid = true;
        if (computeSanBoot.getOrder() > 1) {
            _log.error("San boot should be the first boot device");
            isValid = false;
            return isValid;
        }
        URIQueryResultList sanImageUris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSanBootImageConstraint(computeSanBoot.getId()), sanImageUris);
        List<ComputeSanBootImage> sanBootImageList = _dbClient.queryObject(ComputeSanBootImage.class, sanImageUris, true);

        for (ComputeSanBootImage image : sanBootImageList) {
            URIQueryResultList sanImagePathUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getComputeSanBootImagePathConstraint(image.getId()), sanImagePathUris);

            List<ComputeSanBootImagePath> sanBootImagePathList = _dbClient.queryObject(ComputeSanBootImagePath.class, sanImagePathUris,
                    true);
            // Validate the sanboot targets

            StringSet portWWPNs = new StringSet();
            for (ComputeSanBootImagePath imagePath : sanBootImagePathList) {
                String portWWPN = imagePath.getPortWWN();
                portWWPNs.add(portWWPN);

            }
            if (!portWWPNs.isEmpty()) {
                isValid = isSanBootTargetValidForVarrays(varrayIds, portWWPNs);
                if (!isValid) {
                    return isValid;
                }
            } else {
                _log.debug("No san boot targets specified");
            }
        }
        return isValid;
    }

    private boolean isSanBootTargetValidForVarrays(StringSet varrayIds, StringSet portWWPNs) {
        _log.debug("validate San Boot targets for varrays");
        boolean isValid = true;
        for (String varrayId : varrayIds) {
            List<URI> validStorageSystems = new ArrayList<URI>();
            Map<URI, List<URI>> storageSystemToPortMap = new HashMap<URI, List<URI>>();
            List<URI> vsanURIs = new ArrayList<URI>();
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePortsConstraint(varrayId.toString()), storagePortURIs);
            Map<URI, List<StoragePort>> vsanToPortMap = new HashMap<URI, List<StoragePort>>();
            for (URI uri : storagePortURIs) {
                StoragePort storagePort = _dbClient.queryObject(StoragePort.class, uri);
                URI storageSystemURI = storagePort.getStorageDevice();
                List<URI> portList = storageSystemToPortMap.get(storageSystemURI);
                if (portList == null) {
                    portList = new ArrayList<URI>();
                }
                portList.add(storagePort.getId());
                storageSystemToPortMap.put(storageSystemURI, portList);

                URI networkURI = storagePort.getNetwork();
                List<StoragePort> vsanPorts = vsanToPortMap.get(networkURI);
                if (vsanPorts == null) {
                    vsanPorts = new ArrayList<StoragePort>();
                }
                vsanPorts.add(storagePort);
                vsanToPortMap.put(networkURI, vsanPorts);

                if (portWWPNs.contains(storagePort.getPortNetworkId())) {
                    validStorageSystems.add(storageSystemURI);

                    vsanURIs.add(networkURI);

                }
            }
            if (validStorageSystems.isEmpty()) {
                _log.error("San boot target wwpns do not belong to any storage systems associated to the varray: " + varrayId);
                isValid = false;
                return isValid;
            }

            for (URI vsanURI : vsanURIs) {
                List<StoragePort> connectedPorts = vsanToPortMap.get(vsanURI);
                Network currentVsan = _dbClient.queryObject(Network.class, vsanURI);

                for (StoragePort port : connectedPorts) {
                    if (!portWWPNs.contains(port.getPortNetworkId())) {
                        _log.error("Virtual array " + varrayId + " has ports other than those in the template connected to vsan:"
                                + currentVsan.getLabel());
                        isValid = false;
                        return isValid;
                    }
                }

            }

            URIQueryResultList storagePoolURIs = new URIQueryResultList();

            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePoolsConstraint(varrayId.toString()), storagePoolURIs);

            Map<URI, List<URI>> storageSystemToPoolMap = new HashMap<URI, List<URI>>();

            for (URI uri : storagePoolURIs) {
                StoragePool storagePool = _dbClient.queryObject(StoragePool.class, uri);
                if ((storagePool != null)
                        && (CompatibilityStatus.COMPATIBLE.toString().equals(storagePool
                                .getCompatibilityStatus()))
                        && (RegistrationStatus.REGISTERED.toString().equals(storagePool
                                .getRegistrationStatus()))
                        && DiscoveryStatus.VISIBLE.toString().equals(storagePool.getDiscoveryStatus())) {
                    URI storageSystemURI = storagePool.getStorageDevice();
                    List<URI> storagePoolList = storageSystemToPoolMap.get(storageSystemURI);
                    if (storagePoolList == null) {
                        storagePoolList = new ArrayList<URI>();

                    }
                    storagePoolList.add(storagePool.getId());
                    storageSystemToPoolMap.put(storageSystemURI, storagePoolList);
                }
            }

            for (URI uri : storageSystemToPoolMap.keySet()) {
                if (!validStorageSystems.contains(uri)) {
                    _log.error("virtual array "
                            + varrayId
                            + " has storage pools from storage systems other than the storage system whose ports are in the SPT's san boot target");
                    isValid = false;
                    return isValid;
                }
            }
        }
        return isValid;
    }

    /**
     * Gets a list of all (active) ViPR Compute Systems.
     *
     * @brief List compute systems
     * @return A list of Name/Id pairs representing active Compute Systems. The
     *         IDs returned can be used to fetch more detailed information about
     *         Compute Systems
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ComputeSystemList getComputeSystemList() {
        List<URI> ids = _dbClient.queryByType(ComputeSystem.class, true);
        ComputeSystemList list = new ComputeSystemList();
        Iterator<ComputeSystem> iter = _dbClient.queryIterativeObjects(ComputeSystem.class, ids);
        while (iter.hasNext()) {
            list.getComputeSystems().add(toNamedRelatedResource(iter.next()));
        }
        return list;
    }

    /**
     * Creates and discovers a Compute System in ViPR
     *
     * @param param
     *            An instance of {@link ComputeSystemCreate} representing a
     *            Compute System to be created, not null
     * @see ComputeSystemCreate
     * @brief Create compute system
     * @return Returns an instance of {@link TaskResourceRep} which represents
     *         the Task created for Discovery. The task can then be queried to
     *         know status and progress
     * @throws DatabaseException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep createComputeSystem(ComputeSystemCreate param)
            throws DatabaseException {
        ArgValidator.checkFieldValueFromEnum(param.getSystemType(), "system_type",
                EnumSet.of(ComputeSystem.Type.ucs));
        ComputeSystem.Type deviceType = ComputeSystem.Type.valueOf(param.getSystemType());

        ArgValidator.checkFieldNotNull(param.getName(), "name");
        ArgValidator.checkFieldValidIP(param.getIpAddress(), "ip_address");
        ArgValidator.checkFieldNotNull(param.getPortNumber(), "port_number");
        ArgValidator.checkFieldNotNull(param.getUserName(), "user_name");
        ArgValidator.checkFieldNotNull(param.getPassword(), "password");

        // Check for existing device.
        checkForDuplicateDevice(null, param.getIpAddress(), param.getPortNumber(), param.getName());

        ComputeSystem cs = new ComputeSystem();
        URI id = URIUtil.createId(ComputeSystem.class);
        cs.setId(id);
        cs.setLabel(param.getName());
        cs.setIpAddress(param.getIpAddress());
        cs.setPortNumber(param.getPortNumber());
        cs.setSecure(param.getUseSSL());
        cs.setUsername(param.getUserName());
        cs.setPassword(param.getPassword());
        cs.setSystemType(deviceType.name());
        if (param.getOsInstallNetwork() != null) {
            cs.setOsInstallNetwork(param.getOsInstallNetwork());
        }
        URI imageServerURI = param.getComputeImageServer();
        associateImageServerToComputeSystem(imageServerURI, cs);
        cs.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(cs));
        cs.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());

        _dbClient.createObject(cs);

        recordAndAudit(cs, OperationTypeEnum.CREATE_COMPUTE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN);

        return doDiscoverComputeSystem(cs);
    }

    /**
     * Discovers an already created Compute System
     *
     * @param id
     *            the URN of a ViPR Compute System
     * @brief Discover compute system
     * @return Returns an instance of {@link TaskResourceRep} which represents
     *         the Task created for Discovery. The task can then be queried to
     *         know status and progress
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/discover")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep discoverComputeSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ComputeSystem.class, "id");
        ComputeSystem cs = queryObject(ComputeSystem.class, id, true);

        return doDiscoverComputeSystem(cs);
    }

    /**
     * Updates the Compute System, and (re)discovers it
     *
     * @param id
     *            the URN of a ViPR Compute System to be updated
     * @param param
     *            An instance of {@link ComputeSystemUpdate} that represents the
     *            attributes of a Compute Systemt that are to be updated.
     * @brief Update compute system
     * @return Returns an instance of {@link TaskResourceRep} which represents
     *         the Task created for update (as it triggers rediscovery). The
     *         task can then be queried to know status and progress.
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateComputeSystem(@PathParam("id") URI id, ComputeSystemUpdate param) throws InternalException {

        ArgValidator.checkFieldUriType(id, ComputeSystem.class, "id");
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, id);
        ArgValidator.checkEntityNotNull(cs, id, isIdEmbeddedInURL(id));
        // ipAddress is not allowed for update
        if (param.getIpAddress() != null) {
            throw APIException.badRequests.changesNotSupportedFor("IP Address", "Compute Systems");
        }
        // Check for existing device.
        checkForDuplicateDevice(cs.getId(), param.getIpAddress(), param.getPortNumber(), param.getName());

        if (param.getName() != null) {
            cs.setLabel(param.getName());
        }
        if (param.getIpAddress() != null) {
            cs.setIpAddress(param.getIpAddress());
        }
        if (param.getPortNumber() != null) {
            cs.setPortNumber(param.getPortNumber());
        }
        if (param.getUseSSL() != null) {
            cs.setSecure(param.getUseSSL());
        }
        if (param.getUserName() != null) {
            cs.setUsername(param.getUserName());
        }
        if (param.getPassword() != null) {
            cs.setPassword(param.getPassword());
        }
        if (param.getOsInstallNetwork() != null) {
            if (StringUtils.isBlank(param.getOsInstallNetwork())) {
                cs.setOsInstallNetwork("");
            } else {
                if (cs.getVlans() != null) {
                    if (cs.getVlans().contains(param.getOsInstallNetwork())) {
                        cs.setOsInstallNetwork(param.getOsInstallNetwork());
                    } else {
                        throw APIException.badRequests
                                .invalidParameterOsInstallNetworkDoesNotExist(param.getOsInstallNetwork());
                    }
                } else {
                    cs.setOsInstallNetwork(param.getOsInstallNetwork()); // allow vlan to be set without successful discovery
                }
            }
        }
        URI imageServerURI = param.getComputeImageServer();
        associateImageServerToComputeSystem(imageServerURI, cs);

        cs.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(cs));
        _dbClient.updateObject(cs);

        recordAndAudit(cs, OperationTypeEnum.UPDATE_COMPUTE_SYSTEM, true, null);

        return doDiscoverComputeSystem(cs);
    }

    /**
     * Deletes a Compute System and the discovered information about it from
     * ViPR
     *
     * @param id
     *            the URN of a ViPR Compute System to be deleted
     * @brief Delete compute system
     * @return TaskResourceRep (asynchronous call)
     * @throws DatabaseException
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteComputeSystem(@PathParam("id") URI id)
            throws DatabaseException {
        ComputeSystem cs = queryObject(ComputeSystem.class, id, true);
        ArgValidator.checkEntity(cs, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.UNREGISTERED.toString().equals(cs.getRegistrationStatus())) {
            throw APIException.badRequests.invalidParameterCannotDeactivateRegisteredComputeSystem(cs.getId());
        }
        List<String> provHosts = getProvisionedBlades(cs);
        if (!provHosts.isEmpty()) {
            throw APIException.badRequests.unableToDeactivateProvisionedComputeSystem(cs.getLabel(),
                    org.springframework.util.StringUtils.collectionToCommaDelimitedString(provHosts));
        }

        ComputeController controller = getController(ComputeController.class, cs.getSystemType());
        controller.clearDeviceSession(cs.getId());

        String taskId = UUID.randomUUID().toString();

        Operation op = _dbClient.createTaskOpStatus(ComputeSystem.class,
                cs.getId(), taskId, ResourceOperationTypeEnum.DELETE_COMPUTE_SYSTEM);

        PurgeRunnable.executePurging(_dbClient, _dbPurger,
                _asynchJobService.getExecutorService(), cs,
                0/* _retry_attempts */, taskId, 60);

        recordAndAudit(cs, OperationTypeEnum.DELETE_COMPUTE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN);

        return toTask(cs, taskId, op);
    }

    /**
     * Registers a previously de-registered Compute System. (Creation and Discovery of the Compute System marks the Compute System
     * "Registered" by default)
     *
     * @param id the URN of a ViPR Compute System
     * @brief Register compute system
     * @return TaskResourceRep (asynchronous call)
     * @throws ControllerException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/register")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ComputeSystemRestRep registerComputeSystem(@PathParam("id") URI id) throws ControllerException {
        // Validate the Compute system.
        ArgValidator.checkUri(id);
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, id);
        ArgValidator.checkEntity(cs, id, isIdEmbeddedInURL(id));

        // If not already registered, register it now.
        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(cs.getRegistrationStatus())) {
            cs.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            _dbClient.persistObject(cs);

            List<URI> cvpIds = _dbClient.queryByType(ComputeVirtualPool.class, true);
            Iterator<ComputeVirtualPool> iter = _dbClient.queryIterativeObjects(ComputeVirtualPool.class, cvpIds);
            while (iter.hasNext()) {
                ComputeVirtualPool cvp = iter.next();
                if (cvp.getUseMatchedElements()) {
                    _log.debug("Compute pool " + cvp.getLabel() + " configured to use dynamic matching -- refresh matched elements");
                    computeVirtualPoolService.getMatchingCEsforCVPAttributes(cvp);
                    _dbClient.updateAndReindexObject(cvp);
                }
            }

            recordAndAudit(cs, OperationTypeEnum.REGISTER_COMPUTE_SYSTEM, true, null);
        }
        return getComputeSystem(id);
    }

    /**
     * De-registers a previously registered Compute System. (Creation and
     * Discovery of the Compute System marks the Compute System "Registered" by
     * default)
     *
     * @param id
     *            the URN of a ViPR Compute System
     * @brief Deregister compute system
     * @return A detailed representation of the Compute System
     * @throws ControllerException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ComputeSystemRestRep deregisterComputeSystem(@PathParam("id") URI id) throws ControllerException {
        // Validate the storage system.
        ArgValidator.checkUri(id);
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, id);
        ArgValidator.checkEntity(cs, id, isIdEmbeddedInURL(id));
        if (!RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(cs.getRegistrationStatus())) {
            cs.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(cs);

            // Fetch all unprovisioned blades for this CS; remove them from any CVPs they are part of
            ComputeElementListRestRep result = getComputeElements(cs.getId());
            List<ComputeElementRestRep> blades = result.getList();
            List<URI> unprovisionedBlades = new ArrayList<URI>();
            for (ComputeElementRestRep ce : blades) {
                if (ce.getAvailable()) {
                    unprovisionedBlades.add(ce.getId());
                    _log.debug("Found unprovisioned blade:" + ce.getName());
                }
            }

            List<URI> cvpIds = _dbClient.queryByType(ComputeVirtualPool.class, true);
            Iterator<ComputeVirtualPool> iter = _dbClient.queryIterativeObjects(ComputeVirtualPool.class, cvpIds);
            while (iter.hasNext()) {
                ComputeVirtualPool cvp = iter.next();
                _log.debug("Remove unprovisioned blades from cvp: " + cvp.getLabel());
                StringSet currentElements = new StringSet();
                if (cvp.getMatchedComputeElements() != null) {
                    currentElements.addAll(cvp.getMatchedComputeElements());
                    for (URI bladeId : unprovisionedBlades) {
                        currentElements.remove(bladeId.toString());
                    }
                }
                cvp.setMatchedComputeElements(currentElements);
                _dbClient.updateAndReindexObject(cvp);
                _log.debug("Removed ces from cvp");
            }
            recordAndAudit(cs, OperationTypeEnum.DEREGISTER_COMPUTE_SYSTEM, true, null);
        }
        return getComputeSystem(id);
    }

    /**
     * Gets a list of all the Compute Systems in ViPR with detailed
     * representations for the given Id list
     *
     * @brief List data of compute systems
     * @return List of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ComputeSystemBulkRep getBulkResources(BulkIdParam param) {
        return (ComputeSystemBulkRep) super.getBulkResources(param);
    }

    /**
     * Fetches all the Compute Elements belonging to a Compute System in ViPR
     *
     * @param id
     *            the URN of a ViPR Compute System
     * @brief Show compute elements
     * @return A detailed representation of compute elements
     * @throws InternalException
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/compute-elements")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ComputeElementListRestRep getComputeElements(@PathParam("id") URI id) throws InternalException {
        ComputeElementListRestRep result = new ComputeElementListRestRep();
        ArgValidator.checkFieldUriType(id, ComputeSystem.class, "id");
        ComputeSystem cs = queryResource(id);
        URIQueryResultList ceUriList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeElemetsConstraint(cs.getId()), ceUriList);
        Iterator<URI> iterator = ceUriList.iterator();
        while (iterator.hasNext()) {
            ComputeElement ce = _dbClient.queryObject(ComputeElement.class, iterator.next());
            ComputeElementRestRep rest = map(ce);
            if (rest != null) {
                result.getList().add(rest);
            }
        }
        return result;
    }

    private List<String> getProvisionedBlades(ComputeSystem cs) {
        List<String> provHostList = new ArrayList<String>();

        ComputeElementListRestRep result = getComputeElements(cs.getId());
        List<ComputeElementRestRep> blades = result.getList();

        for (ComputeElementRestRep ce : blades) {
            URIQueryResultList uris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getHostComputeElementConstraint(ce.getId()), uris);
            List<Host> hosts = _dbClient.queryObject(Host.class, uris, true);
            if (!hosts.isEmpty()) {
                provHostList.add(hosts.get(0).getHostName());
            }
        }
        return provHostList;
    }

    private TaskResourceRep doDiscoverComputeSystem(ComputeSystem cs) {
        ComputeController controller = getController(ComputeController.class, cs.getSystemType());
        DiscoveredObjectTaskScheduler scheduler =
                new DiscoveredObjectTaskScheduler(_dbClient, new ComputeSystemJobExec(controller));
        String taskId = UUID.randomUUID().toString();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        tasks.add(new AsyncTask(ComputeSystem.class, cs.getId(), taskId));

        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
        return taskList.getTaskList().iterator().next();
    }

    private void checkForDuplicateDevice(URI id, String ip_address, Integer port_number, String name) {
        List<URI> existingDevices = _dbClient.queryByType(ComputeSystem.class, false);
        for (URI uri : existingDevices) {
            ComputeSystem existing = _dbClient.queryObject(ComputeSystem.class, uri);
            if (existing == null || existing.getInactive() || existing.getId().equals(id)) {
                continue;
            }
            if (existing.getIpAddress() != null && existing.getPortNumber() != null
                    && ip_address != null && port_number != null
                    && existing.getIpAddress().equalsIgnoreCase(ip_address)
                    && existing.getPortNumber().equals(port_number)) {
                throw APIException.badRequests.computeSystemExistsAtIPAddress(ip_address);
            }
            if (existing.getLabel() != null && name != null
                    && existing.getLabel().equalsIgnoreCase(name)) {
                throw APIException.badRequests.resourceExistsWithSameName(ComputeSystem.class.getSimpleName());
            }
        }
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.COMPUTE_SYSTEM;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ComputeSystem> getResourceClass() {
        return ComputeSystem.class;
    }

    @Override
    protected ComputeSystem queryResource(URI id) {
        return queryObject(ComputeSystem.class, id, false);
    }

    @Override
    public ComputeSystemBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<ComputeSystem> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ComputeSystemBulkRep(BulkList.wrapping(_dbIterator, new mapComputeSystemWithServiceProfileTemplates()));
    }

    private class mapComputeSystemWithServiceProfileTemplates implements Function<ComputeSystem, ComputeSystemRestRep> {

        @Override
        public ComputeSystemRestRep apply(ComputeSystem input) {
            ComputeSystemRestRep rep = map(input);
            rep.setServiceProfileTemplates(getServiceProfileTemplatesForComputeSystem(input, _dbClient));
            return rep;
        }

    }

    @Override
    public ComputeSystemBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    private static class ComputeSystemJobExec implements AsyncTaskExecutorIntf {

        private final ComputeController _controller;

        ComputeSystemJobExec(ComputeController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.discoverComputeSystems(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.DISCOVER_COMPUTE_SYSTEM;
        }
    }

    /**
     * Record ViPR Event for the completed operations
     *
     * @param computeSystem
     * @param type
     * @param description
     */
    private void recordComputeEvent(ComputeSystem computeSystem, OperationTypeEnum typeEnum, boolean status) {
        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */typeEnum.getEvType(status),
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* CoS */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */computeSystem.getId(),
                /* description */typeEnum.getDescription(),
                /* timestamp */System.currentTimeMillis(),
                /* extensions */null,
                /* native guid */computeSystem.getNativeGuid(),
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",
                    typeEnum.getDescription(), ex);
        }
    }

    private void recordAndAudit(ComputeSystem cs, OperationTypeEnum typeEnum, boolean status, String operationalStage) {

        recordComputeEvent(cs, typeEnum, status);

        auditOp(typeEnum, status, operationalStage,
                cs.getId().toString(), cs.getLabel(), cs.getPortNumber(), cs.getUsername(), cs.getIpAddress());
    }

    /**
     * Associate's a given imageServer URI to the computeSystem.
     * @param imageServerURI
     * @param cs
     */
    private void associateImageServerToComputeSystem(URI imageServerURI,
            ComputeSystem cs) {
        if (!NullColumnValueGetter.isNullURI(imageServerURI)) {
            ComputeImageServer imageServer = _dbClient.queryObject(
                    ComputeImageServer.class, imageServerURI);
            if (imageServer != null) {
                cs.setComputeImageServer(imageServerURI);
            } else {
                throw APIException.badRequests.invalidParameter(
                        "compute image server", imageServerURI.toString());
            }
        } else {
            List<URI> imageServerURIList = _dbClient.queryByType(
                    ComputeImageServer.class, true);
            ArrayList<URI> tempList = Lists.newArrayList(imageServerURIList
                    .iterator());

            if (tempList.size() == 1) {
                Iterator<ComputeImageServer> imageServerItr = _dbClient
                        .queryIterativeObjects(ComputeImageServer.class,
                                tempList);
                while (imageServerItr.hasNext()) {
                    ComputeImageServer imageSvr = imageServerItr
                            .next();
                    if (imageSvr != null
                            && imageSvr.getComputeImageServerStatus().equals(
                                    ComputeImageServerStatus.AVAILABLE
                                            .toString())) {
                        _log.info(
                                "Automatically associating compute System {} with available image Server {}.",
                                cs.getLabel(), imageSvr.getLabel());
                        cs.setComputeImageServer(imageSvr.getId());
                    }
                }
            } else {
                cs.setComputeImageServer(NullColumnValueGetter.getNullURI());
            }
        }
    }
}
