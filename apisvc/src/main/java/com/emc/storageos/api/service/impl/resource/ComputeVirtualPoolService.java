/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.ComputeMapper.map;
import static com.emc.storageos.api.mapper.ComputeVirtualPoolMapper.toComputeVirtualPool;
import static com.emc.storageos.api.mapper.ComputeVirtualPoolMapper.toUriList;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.NamedRelatedResourceRep;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.GeoVisibilityHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemBulkRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.vpool.ComputeVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolCreateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolElementUpdateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolList;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ServiceProfileTemplateAssignmentChanges;
import com.emc.storageos.model.vpool.ServiceProfileTemplateAssignments;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.google.common.base.Function;

@Path("/compute/vpools")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeVirtualPoolService extends TaggedResource {

    protected static final String EVENT_SERVICE_TYPE = "COMPUTE_VPOOL";
    protected static final String EVENT_SERVICE_SOURCE = "ComputeVirtualPoolService";
    protected static final String VPOOL_CREATED_DESCRIPTION = "Compute Virtual Pool Created";
    protected static final String VPOOL_UPDATED_DESCRIPTION = "Compute Virtual Pool Updated";
    protected static final String VPOOL_DELETED_DESCRIPTION = "Compute Virtual Pool Deleted";

    private static final Logger _log = LoggerFactory.getLogger(ComputeVirtualPoolService.class);

    @Autowired
    protected GeoVisibilityHelper _geoHelper;

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private ComputeSystemService computeSystemService;

    @Autowired
    private VirtualArrayService virtualArrayService;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected DataObject queryResource(URI id) {
        ComputeVirtualPool cvp = _permissionsHelper.getObjectById(id, ComputeVirtualPool.class);
        ArgValidator.checkEntityNotNull(cvp, id, isIdEmbeddedInURL(id));
        return cvp;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.COMPUTE_VPOOL;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ComputeVirtualPool> getResourceClass() {
        return ComputeVirtualPool.class;
    }

    /**
     * Get compute virtual pool by ID
     * 
     * @brief Get compute virtual pool by ID
     * @param id the URN of a Compute Virtual Pool
     * @return ComputeVirtualPoolRestRep representation of Compute Virtual Pool
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ComputeVirtualPoolRestRep getComputeVirtualPool(@PathParam("id") URI id) {
        ArgValidator.checkUri(id);
        ComputeVirtualPool cvp = _permissionsHelper.getObjectById(id, ComputeVirtualPool.class);
        ArgValidator.checkEntityNotNull(cvp, id, isIdEmbeddedInURL(id));
        return toComputeVirtualPool(_dbClient, cvp, isComputeVirtualPoolInUse(cvp));
    }

    /**
     * Get all compute virtual pools
     * 
     * @brief Get all compute virtual pools
     * @return ComputeVirtualPoolList representations of Compute Virtual Pools
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ComputeVirtualPoolList getComputeVirtualPool(
            @DefaultValue("") @QueryParam(TENANT_ID_QUERY_PARAM) String tenantId) {
        List<URI> ids = _dbClient.queryByType(ComputeVirtualPool.class, true);
        ComputeVirtualPoolList list = new ComputeVirtualPoolList();

        // if input tenant is not empty, but user have no access to it, an exception will be thrown.
        TenantOrg tenant_input = null;
        if (!StringUtils.isEmpty(tenantId)) {
            tenant_input = getTenantIfHaveAccess(tenantId);
        }

        StorageOSUser user = getUserFromContext();
        Iterator<ComputeVirtualPool> iter = _dbClient.queryIterativeObjects(ComputeVirtualPool.class, ids);
        List<ComputeVirtualPool> vpoolObjects = new ArrayList<>();
        while (iter.hasNext()) {
            vpoolObjects.add(iter.next());
        }

        // full list if role is {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR} AND no tenant restriction from input
        // else only return the list, which input tenant has access.
        if (_permissionsHelper.userHasGivenRole(user,
                null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
            for (ComputeVirtualPool virtualPool : vpoolObjects) {
                if (tenant_input == null || _permissionsHelper.tenantHasUsageACL(tenant_input.getId(), virtualPool)) {
                    list.getComputeVirtualPool().add(toNamedRelatedResource(virtualPool));
                }
            }
        } else {
            // otherwise, filter by only authorized to use
            URI tenant = null;
            if (tenant_input == null) {
                tenant = URI.create(user.getTenantId());
            } else {
                tenant = tenant_input.getId();
            }

            for (ComputeVirtualPool virtualPool : vpoolObjects) {
                if (_permissionsHelper.tenantHasUsageACL(tenant, virtualPool)) {
                    list.getComputeVirtualPool().add(toNamedRelatedResource(virtualPool));

                }
            }

            // if no tenant specified in request, also adding vpools which sub-tenants of the user have access to.
            if (tenant_input == null) {
                List<URI> subtenants = _permissionsHelper.getSubtenantsWithRoles(user);
                for (ComputeVirtualPool virtualPool : vpoolObjects) {
                    if (_permissionsHelper.tenantHasUsageACL(subtenants, virtualPool)) {
                        list.getComputeVirtualPool().add(toNamedRelatedResource(virtualPool));
                    }
                }
            }
        }

        return list;
    }

    private ComputeVirtualPool constructAndValidateComputeVirtualPool(ComputeVirtualPoolCreateParam param) throws DatabaseException {
        // Initial Validations
        if (param.getSystemType() != null) {
            ArgValidator.checkFieldValueFromEnum(param.getSystemType(), "system_type",
                    ComputeVirtualPool.SupportedSystemTypes.class);
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("system_type");
        }

        validateMinMaxIntValues(param.getMinProcessors(), param.getMaxProcessors(), "min_processors", "max_processors");
        validateMinMaxIntValues(param.getMinTotalCores(), param.getMaxTotalCores(), "min_total_cores", "max_total_cores");
        validateMinMaxIntValues(param.getMinTotalThreads(), param.getMaxTotalThreads(), "min_total_threads", "max_total_threads");
        validateMinMaxIntValues(param.getMinCpuSpeed(), param.getMaxCpuSpeed(), "min_cpu_speed", "max_cpu_speed");
        validateMinMaxIntValues(param.getMinMemory(), param.getMaxMemory(), "min_memory", "max_memory");
        validateMinMaxIntValues(param.getMinNics(), param.getMaxNics(), "min_nics", "max_nics");
        validateMinMaxIntValues(param.getMinHbas(), param.getMaxHbas(), "min_hbas", "max_hbas");

        // Create Compute Virtual Pool
        ComputeVirtualPool cvp = new ComputeVirtualPool();

        // Populate Virtual Pool
        cvp.setId(URIUtil.createId(ComputeVirtualPool.class));
        cvp.setLabel(param.getName());
        cvp.setDescription(param.getDescription());
        cvp.setSystemType(param.getSystemType());

        cvp.setMinProcessors(param.getMinProcessors());
        cvp.setMaxProcessors(param.getMaxProcessors());
        cvp.setMinTotalCores(param.getMinTotalCores());
        cvp.setMaxTotalCores(param.getMaxTotalCores());
        cvp.setMinTotalThreads(param.getMinTotalThreads());
        cvp.setMaxTotalThreads(param.getMaxTotalThreads());
        cvp.setMinCpuSpeed(param.getMinCpuSpeed());
        cvp.setMaxCpuSpeed(param.getMaxCpuSpeed());
        cvp.setMinMemory(param.getMinMemory());
        cvp.setMaxMemory(param.getMaxMemory());
        cvp.setMinNics(param.getMinNics());
        cvp.setMaxNics(param.getMaxNics());
        cvp.setMinHbas(param.getMinHbas());
        cvp.setMaxHbas(param.getMaxHbas());

        // Validate and set Virtual Arrays
        Set<String> addVarrays = param.getVarrays();
        if (addVarrays != null && !addVarrays.isEmpty()) {
            cvp.setVirtualArrays(new StringSet());
            for (String vArray : addVarrays) {
                URI vArrayURI = URI.create(vArray);
                ArgValidator.checkUri(vArrayURI);
                this.queryObject(VirtualArray.class, vArrayURI, true);
                cvp.getVirtualArrays().add(vArray);
            }
        }

        cvp.setUseMatchedElements(param.getUseMatchedElements());

        validateAndSetSpts(cvp, param.getServiceProfileTemplates());

        if (cvp.getUseMatchedElements()) {
            _log.debug("Compute pool " + cvp.getLabel() + " configured to use dynamic matching");
            getMatchingCEsforCVPAttributes(cvp);
        }

        return cvp;
    }

    private ComputeElementListRestRep extractComputeElements(ComputeVirtualPool cvp) {
        ComputeElementListRestRep result = new ComputeElementListRestRep();
        if (cvp.getMatchedComputeElements() != null) {
            Collection<ComputeElement> computeElements = _dbClient.queryObject(ComputeElement.class,
                    toUriList(cvp.getMatchedComputeElements()));
            for (ComputeElement computeElement : computeElements) {
                ComputeElementRestRep rest = map(computeElement);
                if (rest != null) {
                    result.getList().add(rest);
                }
            }
        }
        return result;
    }

    /**
     * Get compute elements that match the Compute Virtual Pool criteria
     * 
     * @brief Get compute elements that match the Compute Virtual Pool criteria
     * @param param The Compute Virtual Pool create spec containing the criteria
     * @return ComputeElementListRestRep collection of Compute Elements
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/matching-compute-elements")
    public ComputeElementListRestRep getMatchingComputeElements(ComputeVirtualPoolCreateParam param) throws DatabaseException {
        ComputeVirtualPool cvp = constructAndValidateComputeVirtualPool(param);
        return extractComputeElements(cvp);
    }

    /**
     * Create a Compute Virtual Pool
     * 
     * @brief Create a compute virtual pool
     * @param param The Compute Virtual Pool create spec
     * @return ComputeVirtualPoolRestRep The created Compute Virtual Pool
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ComputeVirtualPoolRestRep createComputeVirtualPool(ComputeVirtualPoolCreateParam param) throws DatabaseException {
        checkForDuplicateName(param.getName(), ComputeVirtualPool.class);
        ComputeVirtualPool cvp = constructAndValidateComputeVirtualPool(param);
        _dbClient.createObject(cvp);

        recordOperation(OperationTypeEnum.CREATE_COMPUTE_VPOOL, VPOOL_CREATED_DESCRIPTION, cvp);
        return toComputeVirtualPool(_dbClient, cvp, isComputeVirtualPoolInUse(cvp));
    }

    private List<URI> getURIs(Collection<ComputeElement> elements) throws APIException {
        List<URI> uriList = new ArrayList<URI>();
        if (elements == null || elements.isEmpty()) {
            return uriList;
        }
        for (ComputeElement element : elements) {
            URI uri = element.getId();
            uriList.add(uri);
        }
        return uriList;
    }

    private void validateAndSetSpts(ComputeVirtualPool cvp, Set<String> addSpts) {

        _log.debug("System type = " + cvp.getSystemType());

        if (cvp.getSystemType().contentEquals(ComputeVirtualPool.SupportedSystemTypes.Cisco_UCSM.toString())) {
            // Process Service Profile Templates
            _log.debug("Processing Service Profile Templates");

            if (addSpts != null && !addSpts.isEmpty()) {
                _log.debug("Found SPTs to add");
                for (String spt : addSpts) {
                    URI sptURI = URI.create(spt);
                    ArgValidator.checkUri(sptURI);
                    this.queryObject(UCSServiceProfileTemplate.class, sptURI, true);
                }

                // Initialize set of compute systems used
                Set<URI> sptComputeSystems = new HashSet<URI>();

                cvp.setServiceProfileTemplates(new StringSet());

                // Iterate over all SPTs in returned in the param stringset
                Collection<UCSServiceProfileTemplate> templates = _dbClient
                        .queryObject(UCSServiceProfileTemplate.class, toUriList(addSpts));
                if (addSpts.size() != templates.size()) {
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Invalid service profile template specified.");
                }
                for (UCSServiceProfileTemplate template : templates) {
                    _log.debug("Adding SPT : " + template.getId().toString());
                    // verify the SPT exists in the db
                    ArgValidator.checkEntity(template, template.getId(), isIdEmbeddedInURL(template.getId()));

                    // verify that there was not already an SPT with the same compute system (check set)
                    if (sptComputeSystems.contains(template.getComputeSystem())) {
                        throw APIException.badRequests
                                .changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                        "Duplicate assignment of service profile template for compute system. Only one service profile template per compute system is permitted.");
                    }

                    // verify the number of nic and hbas match the associated ranges (if set)
                    if (cvp.getMinNics() != null) {
                        ArgValidator.checkFieldMinimum(template.getNumberOfVNICS(), cvp.getMinNics(), "service_profile_template");
                    }
                    if (cvp.getMaxNics() != null && (cvp.getMaxNics() != -1)) {
                        ArgValidator.checkFieldMaximum(template.getNumberOfVNICS(), cvp.getMaxNics(), "service_profile_template");
                    }
                    if (cvp.getMinHbas() != null) {
                        ArgValidator.checkFieldMinimum(template.getNumberOfVHBAS(), cvp.getMinHbas(), "service_profile_template");
                    }
                    if (cvp.getMaxHbas() != null && (cvp.getMaxHbas() != -1)) {
                        ArgValidator.checkFieldMaximum(template.getNumberOfVHBAS(), cvp.getMaxHbas(), "service_profile_template");
                    }

                    if (template.getUpdating() == true) {
                        if (!computeSystemService.isUpdatingSPTValid(template, _dbClient)) {
                            throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                    "Nic or hba names in updating service profile template " + template.getLabel()
                                            + " do not match those in its boot policy.");
                        }
                    }

                    if (!computeSystemService.isServiceProfileTemplateValidForVarrays(cvp.getVirtualArrays(), template.getId())) {
                        throw APIException.badRequests.sptIsNotValidForVarrays(template.getLabel());
                    }

                    // add the spt to the VCP
                    cvp.getServiceProfileTemplates().add(template.getId().toString());

                    // update the set of compute systems used
                    sptComputeSystems.add(template.getComputeSystem());
                }
            }
        }
    }

    private List<URI> findAllStaticallyAssignedComputeElements() {
        Collection<String> staticallyAssignedComputeElementUriStrings = new HashSet<String>();
        List<URI> computeVirtualPoolUris = _dbClient.queryByType(ComputeVirtualPool.class, true);
        Collection<ComputeVirtualPool> computeVirtualPools = _dbClient.queryObject(ComputeVirtualPool.class, computeVirtualPoolUris);
        for (ComputeVirtualPool computeVirtualPool : computeVirtualPools) {
            if (!computeVirtualPool.getUseMatchedElements()) {
                _log.debug("Compute pool " + computeVirtualPool.getLabel() + " using static matching");
                if (computeVirtualPool.getMatchedComputeElements() != null) {
                    _log.debug("Compute pool " + computeVirtualPool.getLabel() + " has "
                            + computeVirtualPool.getMatchedComputeElements().size() + " statically assigned compute elements");
                    staticallyAssignedComputeElementUriStrings.addAll(computeVirtualPool.getMatchedComputeElements());
                }
            }
        }

        _log.debug("Found " + computeVirtualPools.size()
                + " compute pools using static matching containing the following compute elements: "
                + staticallyAssignedComputeElementUriStrings);
        return toUriList(staticallyAssignedComputeElementUriStrings);
    }

    private List<URI> findAllStaticallyAssignedComputeElementsInOtherPools(ComputeVirtualPool computeVirtualPool) {
        List<URI> staticallyAssignedComputeElements = findAllStaticallyAssignedComputeElements();
        List<URI> poolUris = toUriList(computeVirtualPool.getMatchedComputeElements());

        if (!poolUris.isEmpty() && !staticallyAssignedComputeElements.isEmpty()) {
            _log.debug("Remove " + poolUris.size() + " previously assigned compute elements from list of "
                    + staticallyAssignedComputeElements.size() + " static elements");
            for (URI computeElementId : poolUris) {
                boolean removed = staticallyAssignedComputeElements.remove(computeElementId);
                if (removed) {
                    _log.debug("Compute element " + computeElementId + " from pool " + computeVirtualPool.getId()
                            + " removed from staticallyAssignedComputeElements");
                }
            }
        }
        return staticallyAssignedComputeElements;
    }

    private List<URI> findComputeElementsFromDeviceAssociations(ComputeVirtualPool computeVirtualPool) {
        // Get CEs from associated varrays
        List<URI> ceList = new ArrayList<URI>();
        if (computeVirtualPool.getVirtualArrays() != null) {
            for (String virtualArrayId : computeVirtualPool.getVirtualArrays()) {
                URI virtualArrayURI = URI.create(virtualArrayId);
                ArgValidator.checkUri(virtualArrayURI);
                this.queryObject(VirtualArray.class, virtualArrayURI, true);
                _log.debug("Look up compute systems for virtual array " + virtualArrayURI);
                ComputeSystemBulkRep computeSystemBulkRep = virtualArrayService.getComputeSystems(virtualArrayURI);
                if (computeSystemBulkRep.getComputeSystems() != null) {
                    for (ComputeSystemRestRep computeSystemRestRep : computeSystemBulkRep.getComputeSystems()) {
                        _log.debug("Found compute system " + computeSystemRestRep.getId() + " for virtual array " + virtualArrayURI);
                        ComputeElementListRestRep computeElementListRestRep = computeSystemService.getComputeElements(computeSystemRestRep
                                .getId());
                        if (computeElementListRestRep.getList() != null) {
                            for (ComputeElementRestRep computeElementRestRep : computeElementListRestRep.getList()) {
                                _log.debug("Compute system contains compute element " + computeElementRestRep.getId());
                                ceList.add(computeElementRestRep.getId());
                            }
                        }
                    }
                }
            }
        }
        return ceList;
    }

    private boolean isAvailable(ComputeElement computeElement) {
        return RegistrationStatus.REGISTERED.name().equals(computeElement.getRegistrationStatus()) && computeElement.getAvailable();
    }

    public void getMatchingCEsforCVPAttributes(ComputeVirtualPool cvp) {

        // TODO : first iterate over Compute Systems and find ones with association to the vArrays
        // New search function to be added to the Compute Systems to search for associated vArrays

        if (cvp.getMatchedComputeElements() != null) {
            cvp.getMatchedComputeElements().clear();
        }

        String sysType = null;
        if (cvp.getSystemType() != null) {
            if (cvp.getSystemType().contentEquals(ComputeVirtualPool.SupportedSystemTypes.Cisco_UCSM.toString())) {
                sysType = "ucs";
            }
        }
        if (sysType != null) {
            _log.debug("Iterating over all CEs");

            List<URI> ceList = findComputeElementsFromDeviceAssociations(cvp);
            List<URI> staticallyAssignedComputeElements = findAllStaticallyAssignedComputeElementsInOtherPools(cvp);

            StringSet ceIds = new StringSet();

            Collection<ComputeElement> computeElements = _dbClient.queryObject(ComputeElement.class, ceList);
            for (ComputeElement ce : computeElements) {

                if (ce.getSystemType() == null) {
                    continue;
                }
                if (!ce.getSystemType().contentEquals((CharSequence) sysType)) {
                    continue;
                }

                if (!isAvailable(ce)) {
                    continue;
                }

                if (staticallyAssignedComputeElements.contains(ce.getId())) {
                    _log.debug("Compute element " + ce.getId() + " has been statically assigned and will be filtered out");
                    continue;
                }

                if (isParamSet(cvp.getMinTotalCores()) && (ce.getNumOfCores() < cvp.getMinTotalCores())) {
                    continue;
                }
                if (isParamSet(cvp.getMaxTotalCores()) && (cvp.getMaxTotalCores() != -1) && (ce.getNumOfCores() > cvp.getMaxTotalCores())) {
                    continue;
                }
                if (isParamSet(cvp.getMinProcessors()) && (ce.getNumberOfProcessors() < cvp.getMinProcessors())) {
                    continue;
                }
                if (isParamSet(cvp.getMaxProcessors()) && (cvp.getMaxProcessors() != -1)
                        && (ce.getNumberOfProcessors() > cvp.getMaxProcessors())) {
                    continue;
                }
                if (isParamSet(cvp.getMinTotalThreads()) && (ce.getNumberOfThreads() < cvp.getMinTotalThreads())) {
                    continue;
                }
                if (isParamSet(cvp.getMaxTotalThreads()) && (cvp.getMaxTotalThreads() != -1)
                        && (ce.getNumberOfThreads() > cvp.getMaxTotalThreads())) {
                    continue;
                }

                float ceSpeed = Float.parseFloat(ce.getProcessorSpeed());
                if (isParamSet(cvp.getMinCpuSpeed())) {
                    if (ceSpeed < (float) cvp.getMinCpuSpeed()) {
                        continue;
                    }
                }
                if (isParamSet(cvp.getMaxCpuSpeed()) && (cvp.getMaxCpuSpeed() != -1)) {
                    if (ceSpeed > (float) cvp.getMaxCpuSpeed()) {
                        continue;
                    }
                }

                if (isParamSet(cvp.getMinMemory()) && (ce.getRam() / 1024 < cvp.getMinMemory())) {
                    continue;
                }
                if (isParamSet(cvp.getMaxMemory()) && (cvp.getMaxMemory() != -1) && (ce.getRam() / 1024 > cvp.getMaxMemory())) {
                    continue;
                }

                ceIds.add(ce.getId().toASCIIString());
            }
            cvp.addMatchedComputeElements(ceIds);
            Integer size = cvp.getMatchedComputeElements() != null ? cvp.getMatchedComputeElements().size() : 0;
            _log.debug("putting CEs in the pool, cnt: " + size);
        }
    }

    private boolean isParamSet(Integer param) {
        return param != null && param != 0;
    }

    private Integer getParamValue(Integer param) {
        return isParamSet(param) ? param : 0;
    }

    private Integer getParamMaxValue(Integer param) {
        return isParamSet(param) ? param : -1;
    }

    /**
     * Update a Compute Virtual Pool
     * 
     * @brief Update a compute virtual pool
     * @param param The Compute Virtual Pool update spec
     * @return ComputeVirtualPoolRestRep The updated Compute Virtual Pool
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ComputeVirtualPoolRestRep updateComputeVirtualPool(@PathParam("id") URI id, ComputeVirtualPoolUpdateParam param) {
        ComputeVirtualPool cvp = null;

        _log.debug("Update Parameters:\n" + param.toString());

        // Validate that Virtual Pool exists
        ArgValidator.checkFieldUriType(id, ComputeVirtualPool.class, "id");
        cvp = this.queryObject(ComputeVirtualPool.class, id, true);
        boolean nicOrHbaRangeChanges = false;
        boolean moreRestrictiveChange = false; // If current value not set OR if param is more restrictive then change is more restrictive

        // Process the update parameters
        // If a name is specified on request and that value is different that current name
        boolean nameChange = (param.getName() != null && !(cvp.getLabel().equals(param.getName())));
        if (nameChange) {
            checkForDuplicateName(param.getName(), ComputeVirtualPool.class);
            cvp.setLabel(param.getName());
        }
        if (null != param.getDescription()) {
            cvp.setDescription(param.getDescription());
        }
        if (null != param.getSystemType()) {
            ArgValidator.checkFieldValueFromEnum(param.getSystemType(), "system_type",
                    ComputeVirtualPool.SupportedSystemTypes.class);

            // Don't allow changes of system type if there are service profile templates already set
            if (cvp.getServiceProfileTemplates() != null) {
                if (!cvp.getServiceProfileTemplates().isEmpty()) {
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Cannot change system type when Service Profile Temples are associated");
                }
            }
            cvp.setSystemType(param.getSystemType());
        }

        if (isParamSet(param.getMinProcessors())
                && ((cvp.getMinProcessors() == null) || (cvp.getMinProcessors() < param.getMinProcessors()))) {
            moreRestrictiveChange = true;
            _log.debug("Min Processors increased from " + cvp.getMinProcessors() + " to " + param.getMinProcessors());
        }
        cvp.setMinProcessors(getParamValue(param.getMinProcessors()));

        if (isParamSet(param.getMaxProcessors())
                && ((cvp.getMaxProcessors() == null) || (cvp.getMaxProcessors() == -1) || (cvp.getMaxProcessors() > param
                        .getMaxProcessors()))) {
            moreRestrictiveChange = true;
            _log.debug("Max Processors decreased from " + cvp.getMaxProcessors() + " to " + param.getMaxProcessors());
        }
        cvp.setMaxProcessors(getParamMaxValue(param.getMaxProcessors()));

        validateMinMaxIntValues(cvp.getMinProcessors(), cvp.getMaxProcessors(), "min_processors", "max_processors");

        if (isParamSet(param.getMinTotalCores())
                && ((cvp.getMinTotalCores() == null) || (cvp.getMinTotalCores() < param.getMinTotalCores()))) {
            moreRestrictiveChange = true;
            _log.debug("Min TotalCores increased from " + cvp.getMinTotalCores() + " to " + param.getMinTotalCores());
        }
        cvp.setMinTotalCores(getParamValue(param.getMinTotalCores()));

        if (isParamSet(param.getMaxTotalCores())
                && ((cvp.getMaxTotalCores() == null) || (cvp.getMaxTotalCores() == -1) || (cvp.getMaxTotalCores() > param
                        .getMaxTotalCores()))) {
            moreRestrictiveChange = true;
            _log.debug("Max TotalCores decreased from " + cvp.getMaxTotalCores() + " to " + param.getMaxTotalCores());
        }
        cvp.setMaxTotalCores(getParamMaxValue(param.getMaxTotalCores()));

        validateMinMaxIntValues(cvp.getMinTotalCores(), cvp.getMaxTotalCores(), "min_total_cores", "max_total_cores");

        if (isParamSet(param.getMinTotalThreads())
                && ((cvp.getMinTotalThreads() == null) || (cvp.getMinTotalThreads() < param.getMinTotalThreads()))) {
            moreRestrictiveChange = true;
            _log.debug("Min TotalThreads increased from " + cvp.getMinTotalThreads() + " to " + param.getMinTotalThreads());
        }
        cvp.setMinTotalThreads(getParamValue(param.getMinTotalThreads()));

        if (isParamSet(param.getMaxTotalThreads())
                && ((cvp.getMaxTotalThreads() == null) || (cvp.getMaxTotalThreads() == -1) || (cvp.getMaxTotalThreads() > param
                        .getMaxTotalThreads()))) {
            moreRestrictiveChange = true;
            _log.debug("Max TotalThreads decreased from " + cvp.getMaxTotalThreads() + " to " + param.getMaxMemory());
        }
        cvp.setMaxTotalThreads(getParamMaxValue(param.getMaxTotalThreads()));

        validateMinMaxIntValues(cvp.getMinTotalThreads(), cvp.getMaxTotalThreads(), "min_total_threads", "max_total_threads");

        if (isParamSet(param.getMinCpuSpeed()) && ((cvp.getMinCpuSpeed() == null) || (cvp.getMinCpuSpeed() < param.getMinCpuSpeed()))) {
            moreRestrictiveChange = true;
            _log.debug("Min CpuSpeed increased from " + cvp.getMinCpuSpeed() + " to " + param.getMinCpuSpeed());
        }
        cvp.setMinCpuSpeed(getParamValue(param.getMinCpuSpeed()));

        if (isParamSet(param.getMaxCpuSpeed())
                && ((cvp.getMaxCpuSpeed() == null) || (cvp.getMaxCpuSpeed() == -1) || (cvp.getMaxCpuSpeed() > param.getMaxCpuSpeed()))) {
            moreRestrictiveChange = true;
            _log.debug("Max CpuSpeed decreased from " + cvp.getMaxCpuSpeed() + " to " + param.getMaxCpuSpeed());
        }
        cvp.setMaxCpuSpeed(getParamMaxValue(param.getMaxCpuSpeed()));

        validateMinMaxIntValues(cvp.getMinCpuSpeed(), cvp.getMaxCpuSpeed(), "min_processor_speed", "max_processor_speed");

        if (isParamSet(param.getMinMemory()) && ((cvp.getMinMemory() == null) || (cvp.getMinMemory() < param.getMinMemory()))) {
            moreRestrictiveChange = true;
            _log.debug("Min Memory increased from " + cvp.getMinMemory() + " to " + param.getMinMemory());
        }
        cvp.setMinMemory(getParamValue(param.getMinMemory()));

        if (isParamSet(param.getMaxMemory())
                && ((cvp.getMaxMemory() == null) || (cvp.getMaxMemory() == -1) || (cvp.getMaxMemory() > param.getMaxMemory()))) {
            moreRestrictiveChange = true;
            _log.debug("Max Memory decreased from " + cvp.getMaxMemory() + " to " + param.getMaxMemory());
        }
        cvp.setMaxMemory(getParamMaxValue(param.getMaxMemory()));

        validateMinMaxIntValues(cvp.getMinMemory(), cvp.getMaxMemory(), "min_memory", "max_memory");

        boolean moreRestrictiveNicHbaChange = false; // If current value not set OR if param is more restrictive then change is more
                                                     // restrictive

        if (isParamSet(param.getMinNics()) && ((cvp.getMinNics() == null) || (cvp.getMinNics() < param.getMinNics()))) {
            moreRestrictiveNicHbaChange = true;
            _log.debug("Min nic increased from " + cvp.getMinNics() + " to " + param.getMinNics());
        }
        cvp.setMinNics(getParamValue(param.getMinNics()));

        if (isParamSet(param.getMaxNics())
                && ((cvp.getMaxNics() == null) || (cvp.getMaxNics() == -1) || (cvp.getMaxNics() > param.getMaxNics()))) {
            moreRestrictiveNicHbaChange = true;
            _log.debug("Max nic decreased from " + cvp.getMaxNics() + " to " + param.getMaxNics());
        }
        cvp.setMaxNics(getParamMaxValue(param.getMaxNics()));

        validateMinMaxIntValues(cvp.getMinNics(), cvp.getMaxNics(), "min_nics", "max_nics");

        if (isParamSet(param.getMinHbas()) && ((cvp.getMinHbas() == null) || (cvp.getMinHbas() < param.getMinHbas()))) {
            moreRestrictiveNicHbaChange = true;
            _log.debug("Min hba increased from " + cvp.getMinHbas() + " to " + param.getMinHbas());
        }
        cvp.setMinHbas(getParamValue(param.getMinHbas()));

        if (isParamSet(param.getMaxHbas())
                && ((cvp.getMaxHbas() == null) || (cvp.getMaxHbas() == -1) || (cvp.getMaxHbas() > param.getMaxHbas()))) {
            moreRestrictiveNicHbaChange = true;
            _log.debug("Max hba decreased from " + cvp.getMaxHbas() + " to " + param.getMaxHbas());
        }
        cvp.setMaxHbas(getParamMaxValue(param.getMaxHbas()));

        validateMinMaxIntValues(cvp.getMinHbas(), cvp.getMaxHbas(), "min_hbas", "max_hbas");

        boolean changeToStaticAssignment = false;
        boolean changeToDynamicAssignment = false;
        Collection<ComputeElement> staticElements = new HashSet<ComputeElement>();
        if (!cvp.getUseMatchedElements() && cvp.getMatchedComputeElements() != null && !cvp.getMatchedComputeElements().isEmpty()) {
            staticElements.addAll(_dbClient.queryObject(ComputeElement.class, toUriList(cvp.getMatchedComputeElements())));
            _log.debug("static elements count:" + staticElements.size());
        }
        if (null != param.getUseMatchedElements()) {
            // Will need to clear current matches when changing to static assignment
            changeToStaticAssignment = (param.getUseMatchedElements() == false)
                    && (param.getUseMatchedElements() != cvp.getUseMatchedElements());
            changeToDynamicAssignment = (param.getUseMatchedElements() == true)
                    && (param.getUseMatchedElements() != cvp.getUseMatchedElements());

            cvp.setUseMatchedElements(param.getUseMatchedElements());
        }
        if (changeToStaticAssignment) { // Clear dynamic matches when changing to static to get ready for upcoming assignments
            if (cvp.getMatchedComputeElements() != null) {
                cvp.getMatchedComputeElements().clear();
            }
        }

        if (null != param.getVarrayChanges()) {
            updateVirtualArrays(cvp, param.getVarrayChanges());
        }

        if (null != param.getSptChanges()) {
            if (cvp.getSystemType().contentEquals(ComputeVirtualPool.SupportedSystemTypes.Cisco_UCSM.toString())) {
                updateServiceProfileTemplates(cvp, param.getSptChanges());
            }
        }

        // Check SPTs meet criteria after updates above
        if (moreRestrictiveNicHbaChange) {
            if (isComputeVirtualPoolInUse(cvp)) {
                _log.warn("VCP is in use; more restrictive Nic or Hba change is not allowed");
                throw APIException.badRequests
                        .changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                "More restrictive updates to network adapter and hba range not allowed because compute virtual pool is already in use.");

            }
            Set<String> sptsNotMeetingCriteria = new HashSet<String>();
            Collection<UCSServiceProfileTemplate> templates = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                    toUriList(cvp.getServiceProfileTemplates()));
            for (UCSServiceProfileTemplate template : templates) {
                boolean inUse = isServiceProfileTemplateInUse(cvp, template);
                try {
                    validateServiceProfileTemplate(cvp, template);
                } catch (APIException e) {
                    _log.warn("SPT " + template.getLabel() + ":" + template.getDn() + " is in use(" + inUse
                            + ") and does not meet criteria " + e.toString());
                    /*
                     * Since we are disallowing more restrictive changes if the VCP is in use, the if block below will not be used for the
                     * 2.2 release.
                     */
                    if (inUse) {
                        throw APIException.badRequests
                                .changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                        "Updates to pool not allowed because service profile template(s) already in use do not meet requested criteria.");
                    }
                    sptsNotMeetingCriteria.add(template.getId().toString()); // if spt not in use then simply remove
                    _log.warn("SPT does not meet criteria; so being removed");
                }
            }
            cvp.removeServiceProfileTemplates(sptsNotMeetingCriteria);
        }

        if (cvp.getUseMatchedElements()) {
            _log.debug("Compute pool " + cvp.getLabel() + " configured to use dynamic matching");
            getMatchingCEsforCVPAttributes(cvp);
        }

        if (changeToDynamicAssignment && !staticElements.isEmpty()) { // Release static assignments and update other pools dynamic matches
                                                                      // to possibly include them
            for (ComputeElement computeElement : staticElements) {
                if (!isAvailable(computeElement)) {
                    _log.error("Cannot change to dynamic matching because statically assigned compute element(s) have been used in pool "
                            + cvp.getId());
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Cannot change to automatic matching because manually assigned compute element(s) already in use.");
                }
            }
            updateOtherPoolsComputeElements(cvp);
        }

        if (moreRestrictiveChange) {
            if (isComputeVirtualPoolInUse(cvp)) {
                _log.warn("VCP is in use; more restrictive change is not allowed");
                throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                        "More restrictive updates to qualifiers not allowed because compute virtual pool is already in use.");

            }
            // VCP is not in use. So check if there are statically assigned members that need to be removed from vcp membership
            _log.info("VCP is not in use. So check if there are statically assigned members that need to be removed from vcp membership");

            if (!cvp.getUseMatchedElements() && !staticElements.isEmpty()) {
                Set<ComputeElement> cesNotMeetingCriteria = new HashSet<ComputeElement>();
                Collection<ComputeElement> computeElements = _dbClient.queryObject(ComputeElement.class, getURIs(staticElements));
                for (ComputeElement element : computeElements) {
                    _log.debug("Blade:" + element.getChassisId() + "/" + element.getSlotId());
                    boolean inUse = (element.getAvailable() == false);
                    try {
                        validateComputeElement(cvp, element);
                    } catch (APIException e) {
                        _log.warn("Compute Element " + element.getLabel() + ":" + element.getDn() + " is in use(" + inUse
                                + ") and does not meet criteria " + e.toString());
                        /*
                         * Since we are disallowing more restrictive changes if the VCP is in use, the if block below will not be used for
                         * the 2.2 release.
                         */
                        if (inUse) {
                            throw APIException.badRequests
                                    .changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                            "Updates to pool not allowed because compute element(s) already in use do not meet requested criteria.");
                        }
                        cesNotMeetingCriteria.add(element); // if ces not in use then simply remove
                        _log.warn("Compute Element does not meet criteria; so being removed");
                    }
                }

                StringSet cesMeetingCriteria = new StringSet();
                for (ComputeElement staticElement : staticElements) {
                    _log.debug("Blade:" + staticElement.getChassisId() + ":" + staticElement.getSlotId());
                    boolean invalid = false;
                    for (ComputeElement element : cesNotMeetingCriteria) {
                        if (element.getId().toString().equals(staticElement.getId().toString())) {
                            invalid = true;
                        }
                    }
                    if (!invalid) {
                        _log.debug("added");
                        cesMeetingCriteria.add(staticElement.getId().toString());
                    }
                }
                cvp.getMatchedComputeElements().clear();
                cvp.setMatchedComputeElements(cesMeetingCriteria);
            }

        }
        _dbClient.updateAndReindexObject(cvp);

        recordOperation(OperationTypeEnum.UPDATE_COMPUTE_VPOOL, VPOOL_UPDATED_DESCRIPTION, cvp);
        return toComputeVirtualPool(_dbClient, cvp, isComputeVirtualPoolInUse(cvp));
    }

    /**
     * Updates the virtual arrays to which the compute virtual pool is assigned.
     * 
     * @param cvp - A reference to the compute virtual pool.
     * 
     * @return true if there was a virtual array assignment change, false otherwise.
     */
    private boolean updateVirtualArrays(ComputeVirtualPool cvp,
            VirtualArrayAssignmentChanges varrayAssignmentChanges) {

        // Validate that the Virtual Arrays to be assigned to the Compute Virtual Pool
        // reference existing Virtual Arrays in the database and add them to
        // to the CVP.
        boolean varraysForCvpUpdated = false;
        Set<String> varraysAddedToCvp = new HashSet<String>();
        Set<String> varraysRemovedFromCvp = new HashSet<String>();
        if (varrayAssignmentChanges != null) {
            _log.debug("Update request has virtual array assignment changes for compute virtual pool {}",
                    cvp.getId());
            // Verify the assignment changes in the request.
            verifyAssignmentChanges(cvp, varrayAssignmentChanges);
            _log.debug("Requested virtual array assignment changes verified.");

            VirtualArrayAssignments addAssignments = varrayAssignmentChanges.getAdd();
            if (addAssignments != null) {
                Set<String> addVArrays = addAssignments.getVarrays();
                if ((addVArrays != null) && (!addVArrays.isEmpty())) {
                    _log.debug("Request specifies virtual arrays to be added.");
                    // Validate the requested URIs.
                    VirtualArrayService.checkVirtualArrayURIs(addVArrays, _dbClient);

                    // Iterate over the virtual arrays and assign them to the CVP.
                    StringSet currentAssignments = cvp.getVirtualArrays();
                    Iterator<String> addVArraysIter = addVArrays.iterator();
                    while (addVArraysIter.hasNext()) {
                        String addVArrayId = addVArraysIter.next();
                        if ((currentAssignments != null) && (currentAssignments.contains(addVArrayId))) {
                            // Just ignore those already assigned
                            _log.debug("Compute Virtual Pool already assigned to virtual array {}",
                                    addVArrayId);
                            continue;
                        }

                        // Verify that the Virtual Array is active
                        URI virtualArrayURI = null;
                        virtualArrayURI = URI.create(addVArrayId);
                        this.queryObject(VirtualArray.class, virtualArrayURI, true);

                        varraysAddedToCvp.add(addVArrayId);
                        varraysForCvpUpdated = true;
                        _log.debug("Compute Virtual Pool will be assigned to virtual array {}", addVArrayId);
                    }
                }
            }

            // Validate that the Virtual Arrays to be unassigned from the
            // Compute Virtual Pool reference existing Virtual Arrays in the database
            // and remove them from the CVP.
            VirtualArrayAssignments removeAssignments = varrayAssignmentChanges.getRemove();
            if (removeAssignments != null) {
                Set<String> removeVArrays = removeAssignments.getVarrays();
                // If the vcp is in use, varrays cannot be removed from the vcp.
                if (isComputeVirtualPoolInUse(cvp)) {
                    throw APIException.badRequests.cannotRemoveVarraysFromCVP(cvp.getLabel());
                }

                if ((removeVArrays != null) && (!removeVArrays.isEmpty())) {
                    _log.debug("Request specifies virtual arrays to be removed.");

                    // Iterate over the virtual arrays and unassign from the CVP
                    StringSet currentAssignments = cvp.getVirtualArrays();
                    Iterator<String> removeVArraysIter = removeVArrays.iterator();
                    while (removeVArraysIter.hasNext()) {
                        String removeVArrayId = removeVArraysIter.next();
                        if ((currentAssignments == null) || (!currentAssignments.contains(removeVArrayId))) {
                            // Just ignore those not assigned.
                            _log.debug("Compute Virtual Pool is not assigned to virtual array {}",
                                    removeVArrayId);
                            continue;
                        }

                        varraysRemovedFromCvp.add(removeVArrayId);
                        varraysForCvpUpdated = true;
                        _log.debug("Compute Virtual Pool will be unassigned from virtual array {}",
                                removeVArrayId);
                    }
                }
            }
        }

        // Persist virtual array changes for the Compute Virtual Pool, if any.
        if (varraysForCvpUpdated) {
            if (!varraysAddedToCvp.isEmpty()) {
                cvp.addVirtualArrays(varraysAddedToCvp);
            }

            if (!varraysRemovedFromCvp.isEmpty()) {
                cvp.removeVirtualArrays(varraysRemovedFromCvp);
            }
        }
        return varraysForCvpUpdated;
    }

    /**
     * Verifies the virtual array assignment changes in the update request are
     * valid, else throws a bad request exception.
     * 
     * @param cvp - A reference to a Compute Virtual Pool.
     * @param varrayAssignmentChanges The virtual array assignment changes in a
     *            compute virtual pool update request.
     */
    private void verifyAssignmentChanges(ComputeVirtualPool cvp,
            VirtualArrayAssignmentChanges varrayAssignmentChanges) {
        // Verify the add/remove sets do not overlap.
        VirtualArrayAssignments addAssignments = varrayAssignmentChanges.getAdd();
        VirtualArrayAssignments removeAssignments = varrayAssignmentChanges.getRemove();
        if ((addAssignments != null) && (removeAssignments != null)) {
            Set<String> addVArrays = addAssignments.getVarrays();
            Set<String> removeVArrays = removeAssignments.getVarrays();
            if ((addVArrays != null) && (removeVArrays != null)) {
                Set<String> addSet = new HashSet<String>(addVArrays);
                Set<String> removeSet = new HashSet<String>(removeVArrays);
                addSet.retainAll(removeSet);
                if (!addSet.isEmpty()) {
                    _log.error("Request specifies the same virtual array(s) in both the add and remove lists {}", addSet);
                    throw APIException.badRequests.sameVirtualArrayInAddRemoveList();
                }
            }
        }
    }

    public boolean isComputeVirtualPoolInUse(ComputeVirtualPool cvp) {
        boolean inUse = false;
        _log.debug("Checking if vcp is in use: " + inUse);
        if (cvp.getId() != null) {
            List<Host> hosts = getHostsProvisionedFromPool(cvp);
            if (hosts != null && !hosts.isEmpty()) {
                inUse = true;
            }
        }
        return inUse;
    }

    /*
     * TODO - Implement with logic to determine if SPT is provisioned or in use
     * Remove cvp from method signature since its just there for mock testing
     * But shouldn't this method say whether the SPT has been used to provision a host from this vcp?
     */
    public boolean isServiceProfileTemplateInUse(ComputeVirtualPool cvp, UCSServiceProfileTemplate ucsServiceProfileTemplate) {
        _log.debug("Check if SPT " + ucsServiceProfileTemplate.getDn() + " is in use");
        if (cvp.getDescription() != null && cvp.getDescription().contains("provisioned")) {
            return true;
        } else {
            return false;
        }
    }

    private void validateServiceProfileTemplate(ComputeVirtualPool cvp, UCSServiceProfileTemplate template) throws APIException {
        // verify the number of nic and hbas match the associated ranges (if set)
        if (cvp.getMinNics() != null) {
            ArgValidator.checkFieldMinimum(template.getNumberOfVNICS(), cvp.getMinNics(), "service_profile_template number of nics");
        }
        if (cvp.getMaxNics() != null && (cvp.getMaxNics() != -1)) {
            ArgValidator.checkFieldMaximum(template.getNumberOfVNICS(), cvp.getMaxNics(), "service_profile_template number of nics");
        }
        if (cvp.getMinHbas() != null) {
            ArgValidator.checkFieldMinimum(template.getNumberOfVHBAS(), cvp.getMinHbas(), "service_profile_template number of hbas");
        }
        if (cvp.getMaxHbas() != null && (cvp.getMaxHbas() != -1)) {
            ArgValidator.checkFieldMaximum(template.getNumberOfVHBAS(), cvp.getMaxHbas(), "service_profile_template number of hbas");
        }
    }

    private void validateComputeElement(ComputeVirtualPool cvp, ComputeElement ce) {
        _log.debug("in validateComputeElement");
        if (isParamSet(cvp.getMinProcessors())) {
            ArgValidator.checkFieldMinimum(ce.getNumberOfProcessors(), cvp.getMinProcessors(), "compute element number of processors");
        }
        if (isParamSet(cvp.getMaxProcessors()) && (cvp.getMaxProcessors() != -1)) {
            ArgValidator.checkFieldMaximum(ce.getNumberOfProcessors(), cvp.getMaxProcessors(), "compute element number of processors");
        }

        if (ce.getProcessorSpeed() != null) {
            try {
                float processorSpeed = Float.parseFloat(ce.getProcessorSpeed());
                if (isParamSet(cvp.getMinCpuSpeed())) {
                    if (processorSpeed < cvp.getMinCpuSpeed()) {
                        throw APIException.badRequests.invalidFloatParameterBelowMinimum("compute element processor speed", processorSpeed,
                                cvp.getMinCpuSpeed(), " ");
                    }
                }
                if (isParamSet(cvp.getMaxCpuSpeed()) && (cvp.getMaxCpuSpeed() != -1)) {
                    if (processorSpeed > cvp.getMaxCpuSpeed()) {
                        throw APIException.badRequests.invalidFloatParameterAboveMaximum("compute element processor speed", processorSpeed,
                                cvp.getMaxCpuSpeed(), " ");
                    }
                }
            } catch (NumberFormatException e) {
                // processorSpeed not specified. ignore
            }

        }
        if (isParamSet(cvp.getMinMemory())) {
            ArgValidator.checkFieldMinimum(ce.getRam(), cvp.getMinMemory() * 1024, "compute element memory");
        }
        if (isParamSet(cvp.getMaxMemory()) && (cvp.getMaxMemory() != -1)) {
            ArgValidator.checkFieldMaximum(ce.getRam(), cvp.getMaxMemory() * 1024, "compute element memory");
        }
        if (isParamSet(cvp.getMinTotalCores())) {
            ArgValidator.checkFieldMinimum(ce.getNumOfCores(), cvp.getMinTotalCores(), "compute element total cores");
        }
        if (isParamSet(cvp.getMaxTotalCores()) && (cvp.getMaxTotalCores() != -1)) {
            ArgValidator.checkFieldMaximum(ce.getNumOfCores(), cvp.getMaxTotalCores(), "compute element total cores");
        }
        if (isParamSet(cvp.getMinTotalThreads())) {
            ArgValidator.checkFieldMinimum(ce.getNumberOfThreads(), cvp.getMinTotalThreads(), "compute element total threads");
        }
        if (isParamSet(cvp.getMaxTotalThreads()) && (cvp.getMaxTotalThreads() != -1)) {
            ArgValidator.checkFieldMaximum(ce.getNumberOfThreads(), cvp.getMaxTotalThreads(), "compute element total threads");
        }

    }

    /**
     * Updates the service profile templates to which the compute virtual pool is assigned.
     * 
     * @param cvp - A reference to the compute virtual pool.
     * 
     * @return true if there was a service profile template assignment change, false otherwise.
     */
    private boolean updateServiceProfileTemplates(ComputeVirtualPool cvp,
            ServiceProfileTemplateAssignmentChanges sptAssignmentChanges) {

        // Validate that the SPTs to be assigned to the Compute Virtual Pool
        // reference existing SPTs in the database and add them to
        // to the CVP.
        boolean sptsForCvpUpdated = false;
        Set<String> sptsAddedToCvp = new HashSet<String>();
        Set<String> sptsRemovedFromCvp = new HashSet<String>();
        if (sptAssignmentChanges != null) {
            _log.debug("Update request has service profile template assignment changes for compute virtual pool {}",
                    cvp.getId());
            // Verify the assignment changes in the request.
            verifySptAssignmentChanges(cvp, sptAssignmentChanges);
            _log.debug("Requested service profile template assignment changes verified.");

            ServiceProfileTemplateAssignments addAssignments = sptAssignmentChanges.getAdd();
            if (addAssignments != null) {
                Set<String> addSpts = addAssignments.getServiceProfileTemplates();
                if ((addSpts != null) && (!addSpts.isEmpty())) {
                    _log.debug("Request specifies service profile templates to be added.");
                    // Validate the requested URIs.
                    checkServiceProfileTemplateURIs(addSpts, _dbClient);

                    // Load a Set of the Compute Systems associated to current SPTs
                    Map<URI, UCSServiceProfileTemplate> computeSystemToTemplateMap = new HashMap<URI, UCSServiceProfileTemplate>();

                    // Iterate over all SPTs currently associated to the CVP
                    if (cvp.getServiceProfileTemplates() != null && !cvp.getServiceProfileTemplates().isEmpty()) {
                        Collection<UCSServiceProfileTemplate> templates = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                                toUriList(cvp.getServiceProfileTemplates()));
                        for (UCSServiceProfileTemplate template : templates) {
                            // verify the SPT exists in the db
                            ArgValidator.checkEntity(template, template.getId(), isIdEmbeddedInURL(template.getId()));
                            computeSystemToTemplateMap.put(template.getComputeSystem(), template);
                        }
                    }

                    // Iterate over the service profile templates and assign them to the CVP.
                    StringSet currentAssignments = cvp.getServiceProfileTemplates();
                    Collection<UCSServiceProfileTemplate> addedTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                            toUriList(addSpts));
                    for (UCSServiceProfileTemplate addedTemplate : addedTemplates) {
                        if ((currentAssignments != null) && (currentAssignments.contains(addedTemplate.getId().toString()))) {
                            // Just ignore those already assigned
                            _log.info("Compute Virtual Pool already assigned to service profile template {}", addedTemplate.getId()
                                    .toString());
                            continue;
                        }

                        // verify that the SPT is not associated to a Compute System already in use
                        ArgValidator.checkEntity(addedTemplate, addedTemplate.getId(), isIdEmbeddedInURL(addedTemplate.getId()));

                        UCSServiceProfileTemplate existingTemplate = computeSystemToTemplateMap.get(addedTemplate.getComputeSystem());
                        if (existingTemplate != null) {
                            _log.debug("Compute system " + addedTemplate.getComputeSystem() + " already contains a spt " + existingTemplate);
                            /*
                             * TODO: For 2.2 release, we will not check if SPT is in use but will only check if vcp is in use
                             * if(isServiceProfileTemplateInUse(cvp,existingTemplate)) {
                             * _log.info("SPT " + existingTemplate +
                             * " is already in use and cannot be disassociated and replaced with requested SPT " +
                             * addedTemplate.getId().toString());
                             * throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getId(),
                             * "Cannot replace service profile template that is already in use.");
                             * } else {
                             * _log.info("SPT " + existingTemplate +
                             * " is not in use and will be disassociated and replaced with requested SPT " +
                             * addedTemplate.getId().toString());
                             * sptsRemovedFromCvp.add(existingTemplate.getId().toString());
                             * }
                             */
                            if (isComputeVirtualPoolInUse(cvp)) {
                                _log.info("compute virtual pool is already in use and so SPT cannot be disassociated and replaced with requested SPT "
                                        + addedTemplate.getId().toString());
                                throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                        "Cannot replace service profile template that is already in use.");
                            } else {
                                _log.info("compute virtual pool is not in use and so SPT will be disassociated and replaced with requested SPT "
                                        + addedTemplate.getId().toString());
                                sptsRemovedFromCvp.add(existingTemplate.getId().toString());
                            }
                        }
                        if (addedTemplate.getUpdating() == true) {
                            _log.info("selected spt is an updating template. So validate...");
                            if (!computeSystemService.isUpdatingSPTValid(addedTemplate, _dbClient)) {
                                throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                        "Nic or hba names in updating service profile template " + addedTemplate.getLabel()
                                                + " do not match those in its boot policy.");
                            }

                        }
                        if (!computeSystemService.isServiceProfileTemplateValidForVarrays(cvp.getVirtualArrays(), addedTemplate.getId())) {
                            throw APIException.badRequests.sptIsNotValidForVarrays(addedTemplate.getLabel());
                        }
                        validateServiceProfileTemplate(cvp, addedTemplate);
                        sptsAddedToCvp.add(addedTemplate.getId().toString());
                        sptsForCvpUpdated = true;
                        _log.debug("Compute Virtual Pool will be assigned to service profile template {}", addedTemplate.getId()
                                .toASCIIString());
                    }
                }
            }

            // Validate that the Service Profile Templates to be unassigned from the
            // Compute Virtual Pool reference existing Service Profile Templates in the database
            // and remove them from the CVP.
            ServiceProfileTemplateAssignments removeAssignments = sptAssignmentChanges.getRemove();
            if (removeAssignments != null) {
                Collection<UCSServiceProfileTemplate> removedTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                        toUriList(removeAssignments.getServiceProfileTemplates()));
                if ((removedTemplates != null) && (!removedTemplates.isEmpty())) {
                    _log.debug("Request specifies service profile templates to be removed.");
                    // Validate the requested URIs.

                    // If vcp is in use, SPTs cannot be removed - for the 2.2 release
                    if (isComputeVirtualPoolInUse(cvp)) {
                        throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                "Cannot remove service profile template since virtual compute pool is already in use.");
                    }

                    // Iterate over the service profile templates and unassign from the CVP
                    StringSet currentAssignments = cvp.getServiceProfileTemplates();
                    for (UCSServiceProfileTemplate removedTemplate : removedTemplates) {
                        if ((currentAssignments == null) || (!currentAssignments.contains(removedTemplate.getId().toString()))) {
                            // Just ignore those not assigned.
                            _log.debug("Compute Virtual Pool is not assigned to service profile template {}", removedTemplate.getId()
                                    .toString());
                            continue;
                        }

                        if (isServiceProfileTemplateInUse(cvp, removedTemplate)) {
                            throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                    "Cannot remove service profile template already in use.");
                        }

                        sptsRemovedFromCvp.add(removedTemplate.getId().toString());
                        sptsForCvpUpdated = true;
                        _log.info("Compute Virtual Pool will be unassigned from service profile template {}", removedTemplate.getId()
                                .toString());
                    }
                }
            }
            // At this point all spt's being added have been verified
            // now make sure that only one spt per compute system
            Set<String> sptsCurrentAfterRemove = cvp.getServiceProfileTemplates();
            if (removeAssignments != null) {
                Collection<UCSServiceProfileTemplate> removedTempls = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                        toUriList(removeAssignments.getServiceProfileTemplates()));
                Set<String> removedIDs = new HashSet<String>();
                for (UCSServiceProfileTemplate rmvdTempl : removedTempls) {
                    removedIDs.add(rmvdTempl.getId().toString());
                }
                sptsCurrentAfterRemove.removeAll(removedIDs);
            }
            Set<URI> sptComputeSystems = new HashSet<URI>();

            // Iterate over all SPTs in returned in the param stringset
            Collection<UCSServiceProfileTemplate> addedTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                    toUriList(sptsAddedToCvp));
            for (UCSServiceProfileTemplate template : addedTemplates) {
                _log.debug("Adding SPT : " + template.getId().toString());
                if (sptComputeSystems.contains(template.getComputeSystem())) {
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Service profile template already in use and associated to compute system.");
                } else {
                    sptComputeSystems.add(template.getComputeSystem());
                }
            }
            Collection<UCSServiceProfileTemplate> existingTemplates = _dbClient.queryObject(UCSServiceProfileTemplate.class,
                    toUriList(sptsCurrentAfterRemove));
            for (UCSServiceProfileTemplate template : existingTemplates) {
                _log.debug("Adding SPT : " + template.getId().toString());
                if (sptComputeSystems.contains(template.getComputeSystem())) {
                    throw APIException.badRequests
                            .changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                    "Duplicate compute system association.  Only one service profile template can be associated to a compute system in a Compute Virtual Pool.");
                } else {
                    sptComputeSystems.add(template.getComputeSystem());
                }
            }
        }

        // Persist virtual array changes for the Compute Virtual Pool, if any.
        if (sptsForCvpUpdated) {
            if (!sptsAddedToCvp.isEmpty()) {
                cvp.addServiceProfileTemplates(sptsAddedToCvp);
            }

            if (!sptsRemovedFromCvp.isEmpty()) {
                cvp.removeServiceProfileTemplates(sptsRemovedFromCvp);
            }
        }
        return sptsForCvpUpdated;
    }

    /**
     * Verifies the service profile template assignment changes in the update request are
     * valid, else throws a bad request exception.
     * 
     * @param cvp - A reference to a Compute Virtual Pool.
     * @param sptAssignmentChanges The service profile template assignment changes in a
     *            compute virtual pool update request.
     */
    private void verifySptAssignmentChanges(ComputeVirtualPool cvp,
            ServiceProfileTemplateAssignmentChanges sptAssignmentChanges) {
        // Verify the add/remove sets do not overlap.
        ServiceProfileTemplateAssignments addAssignments = sptAssignmentChanges.getAdd();
        ServiceProfileTemplateAssignments removeAssignments = sptAssignmentChanges.getRemove();
        if ((addAssignments != null) && (removeAssignments != null)) {
            Set<String> addSpts = addAssignments.getServiceProfileTemplates();
            Set<String> removeSpts = removeAssignments.getServiceProfileTemplates();
            if ((addSpts != null) && (removeSpts != null)) {
                Set<String> addSet = new HashSet<String>(addSpts);
                Set<String> removeSet = new HashSet<String>(removeSpts);
                addSet.retainAll(removeSet);
                if (!addSet.isEmpty()) {
                    _log.error("Request specifies the same service profile templates (s) in both the add and remove lists {}", addSet);
                    // TODO: add more specific exception
                    throw APIException.badRequests.sameVirtualArrayInAddRemoveList();
                }
            }
        }
    }

    /**
     * Validates that each of the passed virtual array ids reference an existing
     * virtual array in the database and throws a bad request exception when
     * an invalid id is found.
     * 
     * @param dbClient A reference to a DB client.
     */
    private void checkServiceProfileTemplateURIs(Set<String> sptIds, DbClient dbClient) {
        Set<String> invalidIds = new HashSet<String>();

        if ((sptIds != null) && (!sptIds.isEmpty())) {
            Iterator<String> sptIdsIter = sptIds.iterator();
            while (sptIdsIter.hasNext()) {
                URI sptURI = null;
                try {
                    sptURI = URI.create(sptIdsIter.next());
                    UCSServiceProfileTemplate serviceProfileTemplate = dbClient.queryObject(UCSServiceProfileTemplate.class,
                            sptURI);
                    if (serviceProfileTemplate == null) {
                        invalidIds.add(sptURI.toString());
                    }
                } catch (DatabaseException e) {
                    if (sptURI != null) {
                        invalidIds.add(sptURI.toString());
                    }
                }
            }
        }

        if (!invalidIds.isEmpty()) {
            throw APIException.badRequests.theURIsOfParametersAreNotValid("service profile templates", invalidIds);
        }
    }

    /**
     * Delete a Compute Virtual Pool
     * 
     * @brief Delete a compute virtual pool
     * @param id The ID of Compute Virtual Pool
     * @return Response result
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteComputeVirtualPool(@PathParam("id") URI id) {
        ArgValidator.checkUri(id);
        ComputeVirtualPool cvp = _dbClient.queryObject(ComputeVirtualPool.class, id);
        ArgValidator.checkEntityNotNull(cvp, id, isIdEmbeddedInURL(id));

        // make sure cvp is unused by CE
        ArgValidator.checkReference(ComputeVirtualPool.class, id, checkForDelete(cvp));
        if (isComputeVirtualPoolInUse(cvp)) {
            throw APIException.badRequests.cannotRemoveVCP(cvp.getLabel());
        }

        _dbClient.markForDeletion(cvp);

        recordOperation(OperationTypeEnum.DELETE_COMPUTE_VPOOL, VPOOL_DELETED_DESCRIPTION, cvp);
        return Response.ok().build();
    }

    /**
     * Get collection of compute elements that match criteria in an existing Compute Virtual Pool
     * 
     * @brief Get collection of compute elements that match an existing compute virtual pool
     * @param id The Compute Virtual Pool ID
     * @return ComputeElementListRestRep Collection of Compute Elements
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/compute-elements")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ComputeElementListRestRep getComputeElementsByPool(@PathParam("id") URI id) {
        ArgValidator.checkUri(id);
        ComputeVirtualPool cvp = _permissionsHelper.getObjectById(id, ComputeVirtualPool.class);
        ArgValidator.checkEntityNotNull(cvp, id, isIdEmbeddedInURL(id));
        return extractComputeElements(cvp);
    }

    /**
     * List all instances of compute virtual pools
     * 
     * @brief List all instances of compute virtual pools
     * @prereq none
     * @brief List all instances of compute virtual pools
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ComputeVirtualPoolBulkRep getBulkResources(BulkIdParam param) {
        return (ComputeVirtualPoolBulkRep) super.getBulkResources(param);
    }

    @Override
    public ComputeVirtualPoolBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ComputeVirtualPool> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.ComputeVirtualPoolFilter(getUserFromContext(), _permissionsHelper);
        return new ComputeVirtualPoolBulkRep(BulkList.wrapping(_dbIterator, COMPUTE_VPOOL_MAPPER, filter));
    }

    private final ComputeVirtualPoolMapper COMPUTE_VPOOL_MAPPER = new ComputeVirtualPoolMapper();

    private class ComputeVirtualPoolMapper implements Function<ComputeVirtualPool, ComputeVirtualPoolRestRep> {
        @Override
        public ComputeVirtualPoolRestRep apply(final ComputeVirtualPool vpool) {
            boolean inUse = isComputeVirtualPoolInUse(vpool);
            return toComputeVirtualPool(_dbClient, vpool, inUse);
        }
    }

    @Override
    protected ComputeVirtualPoolBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        return queryBulkResourceReps(ids);
    }

    private void validateMinMaxIntValues(Integer minVal, Integer maxVal, String minField, String maxField) {

        if (minVal != null && minVal != 0) {
            ArgValidator.checkFieldMinimum(minVal, 1, minField);
        }

        if (maxVal != null && maxVal != 0 && maxVal != -1) {
            if (minVal != null) {
                // Make sure Max is greater than or equal to the Min
                ArgValidator.checkFieldMinimum(maxVal, minVal, maxField);
            } else {
                ArgValidator.checkFieldMinimum(maxVal, 1, maxField);
            }
        }
    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param type
     * @param type
     * @param description
     * @param vpool
     */
    private void recordVirtualPoolEvent(String type, String description, URI vpool) {
        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */type,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* VirtualPool */vpool,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */vpool,
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */"",
                /* native guid */null,
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.", description, ex);
        }
    }

    public void recordOperation(OperationTypeEnum opType, String evDesc, Object... extParam) {
        String evType;
        evType = opType.getEvType(true);

        _log.info("opType: {} detail: {}", opType.toString(), evType + ':' + evDesc);

        ComputeVirtualPool computeVpool = (ComputeVirtualPool) extParam[0];

        recordVirtualPoolEvent(evType, evDesc, computeVpool.getId());

        StringBuilder vArrays = new StringBuilder();
        if (computeVpool.getVirtualArrays() != null) {
            for (String varray : computeVpool.getVirtualArrays()) {
                vArrays.append(" ");
                vArrays.append(varray);
            }
        }

        switch (opType) {
            case CREATE_COMPUTE_VPOOL:
                auditOp(opType, true, null, computeVpool.getId().toString(), computeVpool.getLabel(), computeVpool.getSystemType(),
                        vArrays.toString());
                break;
            case UPDATE_COMPUTE_VPOOL:
                auditOp(opType, true, null, computeVpool.getId().toString(), computeVpool.getLabel(), computeVpool.getSystemType(),
                        vArrays.toString());
                break;
            case DELETE_COMPUTE_VPOOL:
                auditOp(opType, true, null, computeVpool.getId().toString(), computeVpool.getLabel(), computeVpool.getSystemType());
                break;
            default:
                _log.error("unrecognized compute vpool operation type");
        }
    }

    /**
     * Get compute virtual pool ACL
     * 
     * @prereq none
     * @param id the URN of a ViPR VirtualPool
     * @brief Show ACL assignment for compute virtual pool
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnVirtualPool(id);
    }

    /**
     * Add or remove individual compute virtual pool ACL entry(s). Request body must include at least one add or remove operation.
     * 
     * @prereq none
     * @param id the URN of a ViPR VirtualPool
     * @param changes ACL assignment changes
     * @brief Add or remove compute virtual pool ACL entries
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        ComputeVirtualPool vpool = (ComputeVirtualPool) queryResource(id);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));

        _permissionsHelper.updateACLs(vpool, changes,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper, vpool.getSystemType()));
        _dbClient.updateAndReindexObject(vpool);

        auditOp(OperationTypeEnum.MODIFY_VPOOL_ACL, true, null, vpool.getId().toString(), vpool.getLabel(), vpool.getSystemType());
        return getAclsOnVirtualPool(id);
    }

    private ACLAssignments getAclsOnVirtualPool(URI id) {
        ComputeVirtualPool vpool = (ComputeVirtualPool) queryResource(id);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));

        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(vpool.getAcls()));
        return response;
    }

    private void updateOtherPoolsComputeElements(ComputeVirtualPool computeVirtualPoolToIgnore) {
        List<URI> computeVirtualPoolUris = _dbClient.queryByType(ComputeVirtualPool.class, true);
        Collection<ComputeVirtualPool> computeVirtualPools = _dbClient.queryObject(ComputeVirtualPool.class, computeVirtualPoolUris);
        for (ComputeVirtualPool computeVirtualPool : computeVirtualPools) {
            if (computeVirtualPool.getId().equals(computeVirtualPoolToIgnore.getId())) {
                continue;
            }

            if (computeVirtualPool.getUseMatchedElements()) {
                getMatchingCEsforCVPAttributes(computeVirtualPool);
                _dbClient.updateAndReindexObject(computeVirtualPool);
            }
        }
    }

    /**
     * Assign Compute Elements to the Compute Virtual Pool
     * 
     * @brief Assign Compute Elements to the compute virtual pool
     * @param param The Compute Virtual Pool Compute Elements to be added and removed
     * @return ComputeVirtualPoolRestRep The updated Compute Virtual Pool
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/assign-matched-elements")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ComputeVirtualPoolRestRep assignMatchedElements(@PathParam("id") URI id, ComputeVirtualPoolElementUpdateParam param)
            throws APIException {
        // Validate that Virtual Pool exists
        ArgValidator.checkFieldUriType(id, ComputeVirtualPool.class, "id");
        ComputeVirtualPool cvp = this.queryObject(ComputeVirtualPool.class, id, true);
        _log.debug("Assign compute elements to compute pool " + cvp.getLabel());

        if (cvp.getUseMatchedElements()) {
            _log.error("Cannot assign compute elements when pool is set to use automatic matching");
            throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                    "Cannot assign compute elements when pool is set to use automatic matching.");
        }

        StringSet currentElements = new StringSet();
        if (cvp.getMatchedComputeElements() != null) {
            currentElements.addAll(cvp.getMatchedComputeElements());
        }
        _log.debug("Currently " + currentElements.size() + " existing compute elements: " + currentElements);

        boolean addRequest = param.getComputeVirtualPoolAssignmentChanges().getAdd() != null
                && param.getComputeVirtualPoolAssignmentChanges().getAdd().getComputeElements() != null;
        if (addRequest) {
            Set<String> addElementsUris = param.getComputeVirtualPoolAssignmentChanges().getAdd().getComputeElements();
            _log.debug("Add " + addElementsUris.size() + " compute elements: " + addElementsUris);
            Collection<ComputeElement> addElements = _dbClient.queryObject(ComputeElement.class, toUriList(addElementsUris)); // Validate
                                                                                                                              // object
                                                                                                                              // exists
            if (addElementsUris.size() != addElements.size()) {
                _log.error("Invalid add compute element(s) specified - Requested " + addElementsUris.size() + " but only "
                        + addElements.size() + " found");
                throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                        "Invalid add compute element(s) specified.");
            }

            List<URI> staticCeUris = findAllStaticallyAssignedComputeElementsInOtherPools(cvp);
            for (ComputeElement computeElement : addElements) {
                if (!isAvailable(computeElement)) {
                    _log.error("Compute element " + computeElement.getId()
                            + " is not available and thus may not be moved into different pool");
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Cannot reassign compute element(s) already used.");
                }
                if (staticCeUris.contains(computeElement.getId())) {
                    _log.error("Compute element " + computeElement.getId() + " already statically assigned to a different pool");
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Cannot assign compute element(s) already manually assigned to different pool(s).");
                }
            }

            // Add against the current collection of compute elements
            for (String computeElementUriString : addElementsUris) {
                boolean added = currentElements.add(computeElementUriString);
                _log.info("Compute pool " + cvp.getLabel() + " already contained compute element " + computeElementUriString + ": " + added);
            }
        }

        boolean removeRequest = param.getComputeVirtualPoolAssignmentChanges().getRemove() != null
                && param.getComputeVirtualPoolAssignmentChanges().getRemove().getComputeElements() != null;
        if (removeRequest) {
            Set<String> removeElementsUris = param.getComputeVirtualPoolAssignmentChanges().getRemove().getComputeElements();
            _log.debug("Remove " + removeElementsUris.size() + " compute elements: " + removeElementsUris);
            Collection<ComputeElement> removeElements = _dbClient.queryObject(ComputeElement.class, toUriList(removeElementsUris)); // Validate
                                                                                                                                    // object
                                                                                                                                    // exists
            if (removeElementsUris.size() != removeElements.size()) {
                _log.error("Invalid remove compute element(s) specified - Requested " + removeElementsUris.size() + " but only "
                        + removeElements.size() + " found");
                throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                        "Invalid remove compute element(s) specified.");
            }
            for (ComputeElement computeElement : removeElements) {
                if (!isAvailable(computeElement)) {
                    _log.error("Compute element " + computeElement.getId() + " is not available and thus may not be removed");
                    throw APIException.badRequests.changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                            "Cannot remove compute element(s) already used.");
                }
            }

            // Remove against the current collection of compute elements
            for (String computeElementUriString : removeElementsUris) {
                boolean removed = currentElements.remove(computeElementUriString);
                _log.debug("Compute pool " + cvp.getLabel() + " needed removal of compute element " + computeElementUriString + ": "
                        + removed);
            }
        }
        Collection<ComputeElement> assignedElements = _dbClient.queryObject(ComputeElement.class, toUriList(currentElements)); // Validate
                                                                                                                               // object
                                                                                                                               // exists
        for (ComputeElement element : assignedElements) {
            boolean inUse = false;
            if (!element.getAvailable()) {
                inUse = true;
            }
            // validate that this is element matches the current vcp criteria
            try {
                validateComputeElement(cvp, element);
            } catch (APIException e) {
                _log.warn("Compute Element " + element.getLabel() + ":" + element.getDn() + " is in use(" + inUse
                        + ") and does not meet criteria " + e.toString());
                /*
                 * Since we are disallowing more restrictive changes if the VCP is in use, the if block below will not be used for the 2.2
                 * release.
                 */
                if (inUse) {
                    throw APIException.badRequests
                            .changeToComputeVirtualPoolNotSupported(cvp.getLabel(),
                                    "Updates to pool not allowed because compute virtual pool is already in use and some compute elements being assigned do not meet criteria.");
                }
                currentElements.remove(element.getId().toString()); // if ces not in use then simply remove
                _log.warn("Compute Element does not meet criteria; so being removed");
            }

        }
        cvp.setMatchedComputeElements(currentElements);
        _dbClient.updateAndReindexObject(cvp);

        // Crucial that we save the static assignments before running updateOtherPoolsComputeElements
        // so that the dynamic matching reassignments happen with latest static assignments
        updateOtherPoolsComputeElements(cvp);

        return toComputeVirtualPool(_dbClient, cvp, isComputeVirtualPoolInUse(cvp));
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new ComputeVirtualPoolResRepFilter(user, permissionsHelper);
    }

    private List<Host> getHostsProvisionedFromPool(ComputeVirtualPool cvp) {
        List<Host> hostList = new ArrayList<Host>();
        URIQueryResultList hostURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getVirtualComputePoolHostConstraint(cvp.getId()), hostURIs);
        Iterator<URI> iter = hostURIs.iterator();
        while (iter.hasNext()) {
            URI hostURI = iter.next();
            Host host = _dbClient.queryObject(Host.class, hostURI);
            if (host != null && !host.getInactive()) {
                hostList.add(host);
            } else {
                _log.error("Can't find host {} in the database " +
                        "or the host is marked for deletion",
                        hostURI);
            }
        }

        return hostList;
    }

    public static class ComputeVirtualPoolResRepFilter<E extends RelatedResourceRep> extends ResRepFilter<E> {

        public ComputeVirtualPoolResRepFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            URI id = resrep.getId();
            ComputeVirtualPool resource = _permissionsHelper.getObjectById(id, ComputeVirtualPool.class);
            if (resource == null) {
                return false;
            }
            return isComputeVirtualPoolAccessible(resource);
        }
    }

}
