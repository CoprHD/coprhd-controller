/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.response.ResourceTypeMapping;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.TenantResource;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.DiscoveredDataObjectRestRep;
import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TypedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.customconfig.CustomConfigRestRep;
import com.emc.storageos.model.customconfig.RelatedConfigTypeRep;
import com.emc.storageos.model.customconfig.ScopeParam;
import com.emc.storageos.model.host.TenantResourceRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.google.common.collect.Lists;

public class DbObjectMapper {
    private static final Logger _log = LoggerFactory.getLogger(DbObjectMapper.class);

    // NamedRelatedResourceRep
    public static NamedRelatedResourceRep toNamedRelatedResource(NamedURI resource) {
        ResourceTypeEnum resourceType = null;

        try {
            resourceType = ResourceTypeMapping.getResourceType(URIUtil.getModelClass(resource.getURI()));
        } catch (Exception e) {
            _log.error("Resource Type not found for " + resource.getURI(), e);
        }

        return new NamedRelatedResourceRep(resource.getURI(), toLink(resourceType, resource.getURI()), resource.getName());
    }

    public static NamedRelatedResourceRep toNamedRelatedResource(DataObject resource) {
        return new NamedRelatedResourceRep(resource.getId(), toLink(resource), resource.getLabel());
    }

    public static NamedRelatedResourceRep toNamedRelatedResource(DataObject resource, String alternativeName) {
        return new NamedRelatedResourceRep(resource.getId(), toLink(resource), alternativeName);
    }

    public static NamedRelatedResourceRep toNamedRelatedResource(ResourceTypeEnum type, URI id, String name) {
        return new NamedRelatedResourceRep(id, toLink(type, id), name);
    }

    public static NamedRelatedResourceRep toNamedRelatedResource(ResourceTypeEnum type, URI id, URI parentId, String name) {
        return new NamedRelatedResourceRep(id, toLink(type, id, parentId), name);
    }

    // TypedRelatedResourceRep

    public static TypedRelatedResourceRep toTypedRelatedResource(DataObject resource) {
        return new TypedRelatedResourceRep(resource.getId(), toLink(resource),
                resource.getLabel(), ResourceTypeMapping.getResourceType(resource));
    }

    // Links

    public static RestLinkRep toLink(DataObject resource) {
        return new RestLinkRep("self", RestLinkFactory.newLink(resource));
    }

    public static RestLinkRep toLink(ResourceTypeEnum type, URI id) {
        return new RestLinkRep("self", RestLinkFactory.newLink(type, id));
    }

    public static RestLinkRep toLink(ResourceTypeEnum type, URI id, URI parentId) {
        return new RestLinkRep("self", RestLinkFactory.newLink(type, id, parentId));
    }

    // RelatedResourceRep

    public static RelatedResourceRep toRelatedResource(ResourceTypeEnum type, URI id) {
        if (NullColumnValueGetter.isNullURI(id)) {
            return null;
        }
        return new RelatedResourceRep(id, toLink(type, id));
    }

    public static RelatedResourceRep toRelatedResource(ResourceTypeEnum type, URI id, URI parentId) {
        if (NullColumnValueGetter.isNullURI(id)) {
            return null;
        }
        return new RelatedResourceRep(id, toLink(type, id, parentId));
    }

    public static List<NamedRelatedResourceRep> map(ResourceTypeEnum type, List<NamedElementQueryResultList.NamedElement> from) {
        List<NamedRelatedResourceRep> to = Lists.newArrayList();
        for (NamedElementQueryResultList.NamedElement el : from) {
            to.add(new NamedRelatedResourceRep(el.getId(), toLink(type, el.getId()), el.getName()));
        }
        return to;
    }

    public static List<NamedRelatedResourceRep>
            map(ResourceTypeEnum type, URI parentId, List<NamedElementQueryResultList.NamedElement> from) {
        List<NamedRelatedResourceRep> to = Lists.newArrayList();
        for (NamedElementQueryResultList.NamedElement el : from) {
            to.add(new NamedRelatedResourceRep(el.getId(), toLink(type, el.getId(), parentId), el.getName()));
        }
        return to;
    }

    public static void mapDataObjectFields(DataObject from, DataObjectRestRep to) {
        to.setLink(new RestLinkRep("self", RestLinkFactory.newLink(from)));
        mapDataObjectFieldsNoLink(from, to);
    }

    public static void mapDataObjectFieldsNoLink(DataObject from, DataObjectRestRep to) {
        to.setId(from.getId());
        to.setName(from.getLabel());
        to.setCreationTime(from.getCreationTime());
        to.setInactive(from.getInactive());
        to.setGlobal(from.isGlobal());
        to.setRemote(to.getGlobal() ? null : VdcUtil.isRemoteObject(from));
        if (from.getTag() != null) {
            for (ScopedLabel tag : from.getTag()) {
                to.getTags().add(tag.getLabel());
            }
        }
        to.setVdc(toRelatedResource(ResourceTypeEnum.VDC, VdcUtil.getVdcUrn(VdcUtil.getVdcId(from.getClass(), from.getId()).toString())));

        if (from.checkInternalFlags(DataObject.Flag.INTERNAL_OBJECT)) {
            to.setInternal(true);
        } else {
            to.setInternal(false);
        }
    }

    public static void mapDiscoveredDataObjectFields(DiscoveredDataObject from, DiscoveredDataObjectRestRep to) {
        mapDataObjectFields(from, to);
        to.setNativeGuid(from.getNativeGuid());
    }

    public static void mapDiscoveredSystemObjectFields(DiscoveredSystemObject from, DiscoveredSystemObjectRestRep to) {
        mapDiscoveredDataObjectFields(from, to);
        to.setSystemType(from.getSystemType());
        to.setDiscoveryJobStatus(from.getDiscoveryStatus());
        to.setMeteringJobStatus(from.getMeteringStatus());
        to.setLastDiscoveryRunTime(from.getLastDiscoveryRunTime());
        to.setNextDiscoveryRunTime(from.getNextDiscoveryRunTime());
        to.setLastMeteringRunTime(from.getLastMeteringRunTime());
        to.setNextMeteringRunTime(from.getNextMeteringRunTime());
        to.setSuccessDiscoveryTime(from.getSuccessDiscoveryTime());
        to.setSuccessMeteringTime(from.getSuccessMeteringTime());
        to.setCompatibilityStatus(from.getCompatibilityStatus());
        to.setRegistrationStatus(from.getRegistrationStatus());
        to.setLastDiscoveryStatusMessage(from.getLastDiscoveryStatusMessage());
    }

    public static ProjectRestRep map(Project from) {
        if (from == null) {
            return null;
        }
        ProjectRestRep to = new ProjectRestRep();
        mapDataObjectFields(from, to);
        if (from.getTenantOrg() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenantOrg().getURI()));
        }
        to.setOwner(from.getOwner());
        if (from.getAssignedVNasServers() != null && !from.getAssignedVNasServers().isEmpty()) {
            to.setAssignedVNasServers(from.getAssignedVNasServers());
        }

        return to;
    }

    public static TenantOrgRestRep map(TenantOrg from) {
        if (from == null) {
            return null;
        }
        TenantOrgRestRep to = new TenantOrgRestRep();
        mapDataObjectFields(from, to);
        if (from.getParentTenant() != null) {
            if (!TenantOrg.isRootTenant(from)) {
                to.setParentTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getParentTenant().getURI()));
            }
        }
        to.setDescription(from.getDescription());
        if (from.getUserMappings() != null) {
            for (AbstractChangeTrackingSet<String> userMappingSet : from.getUserMappings().values()) {
                for (String existingMapping : userMappingSet) {
                    to.getUserMappings().add(BasePermissionsHelper.UserMapping.toParam(
                            BasePermissionsHelper.UserMapping.fromString(existingMapping)));
                }
            }
        }
        if (from.getNamespace() != null) {
            to.setNamespace(from.getNamespace());
        }
        return to;
    }

    public static void mapTenantResource(TenantResource from, TenantResourceRestRep to) {
        mapDataObjectFields(from.findDataObject(), to);
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant()));
    }

    public static CustomConfigRestRep map(CustomConfig from) {
        if (from == null) {
            return null;
        }
        CustomConfigRestRep to = new CustomConfigRestRep();

        to.setLink(new RestLinkRep("self", RestLinkFactory.newLink(from)));
        // build the config type Link
        String service = ResourceTypeEnum.CONFIG_TYPE.getService();
        StringBuilder build = (new StringBuilder(service)).
                append('/').append(from.getConfigType());
        try {
            RelatedConfigTypeRep type = new RelatedConfigTypeRep();
            type.setConfigName(from.getConfigType());
            type.setSelfLink(new RestLinkRep("self", new URI(build.toString())));
            to.setConfigType(type);
        } catch (URISyntaxException e) {
            // it should not happen
        }
        to.setId(from.getId());
        to.setName(from.getLabel());
        StringMap scopeMap = from.getScope();
        ScopeParam scopeParm = new ScopeParam();
        for (Map.Entry<String, String> entry : scopeMap.entrySet()) {
            scopeParm.setType(entry.getKey());
            scopeParm.setValue(entry.getValue());
        }
        to.setScope(scopeParm);
        to.setValue(from.getValue());
        to.setRegistered(from.getRegistered());
        to.setSystemDefault(from.getSystemDefault());
        return to;
    }

    /**
     * Map an VolumeGroup to VolumeGroupRestRep
     * 
     * @param from VolumeGroup
     * @return VolumeGroupRestRep
     */
    public static VolumeGroupRestRep map(VolumeGroup from) {
        if (from == null) {
            return null;
        }
        VolumeGroupRestRep rep = new VolumeGroupRestRep();
        mapDataObjectFields(from, rep);
        rep.setDescription(from.getDescription());
        rep.setRoles(from.getRoles());
        rep.setParent(toRelatedResource(ResourceTypeEnum.VOLUME_GROUP, from.getParent()));
        return rep;
    }

    public static SchedulePolicyRestRep map(SchedulePolicy from) {
        if (from == null) {
            return null;
        }
        SchedulePolicyRestRep to = new SchedulePolicyRestRep();
        if (from.getTenantOrg() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenantOrg().getURI()));
        }
        to.setPolicyId(from.getId());
        to.setPolicyType(from.getPolicyType());
        to.setPolicyName(from.getPolicyName());
        if (from.getScheduleFrequency() != null) {
            to.setScheduleFrequency(from.getScheduleFrequency());
            to.setScheduleRepeat(from.getScheduleRepeat());
            to.setScheduleTime(from.getScheduleTime());
        }
        if (from.getScheduleDayOfWeek() != null) {
            to.setScheduleDayOfWeek(from.getScheduleDayOfWeek());
        }
        if (from.getScheduleDayOfMonth() != null) {
            to.setScheduleDayOfMonth(from.getScheduleDayOfMonth());
        }
        if (from.getSnapshotExpireType() != null) {
            to.setSnapshotExpireType(from.getSnapshotExpireType());
        }
        if (from.getSnapshotExpireTime() != null) {
            to.setSnapshotExpireTime(from.getSnapshotExpireTime());
        }
        return to;
    }

}
