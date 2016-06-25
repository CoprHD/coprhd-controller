/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.createNamedRef;
import static com.emc.vipr.client.core.util.ResourceUtils.findRef;
import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.collections.CollectionUtils;

import models.ConnectivityTypes;
import play.cache.Cache;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.varray.VirtualArrayConnectivityRestRep;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.storageos.model.varray.VirtualArrayResourceRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.varray.VirtualArrayUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolAvailableAttributesResourceRep;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.security.Security;

public class VirtualArrayUtils {
    public static final String ATTRIBUTE_EXPIRATION = "2min";
    public static final String ATTRIBUTE_RAID_LEVELS = "raid_levels";
    public static final String ATTRIBUTE_DRIVE_TYPES = "drive_type";
    public static final String ATTRIBUTE_SYSTEM_TYPES = "system_type";
    public static final String ATTRIBUTE_PROTOCOLS = "protocols";
    public static final String[] ATTRIBUTES = { ATTRIBUTE_RAID_LEVELS, ATTRIBUTE_DRIVE_TYPES, ATTRIBUTE_SYSTEM_TYPES,
            ATTRIBUTE_PROTOCOLS };

    public static boolean canUpdateACLs() {
        return Security.hasAnyRole(Security.SECURITY_ADMIN, Security.SYSTEM_ADMIN, Security.RESTRICTED_SYSTEM_ADMIN);
    }

    public static CachedResources<VirtualArrayRestRep> createCache() {
        return new CachedResources<VirtualArrayRestRep>(getViprClient().varrays());
    }

    public static VirtualArrayRestRep getVirtualArray(String id) {
        return getVirtualArray(uri(id));
    }

    public static VirtualArrayRestRep getVirtualArray(URI id) {
        try {
            return getViprClient().varrays().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static NamedRelatedResourceRep getVirtualArrayRef(RelatedResourceRep ref) {
        return getVirtualArrayRef(id(ref));
    }

    public static NamedRelatedResourceRep getVirtualArrayRef(URI id) {
        if (Security.hasAnyRole(Security.SYSTEM_ADMIN, Security.SYSTEM_MONITOR)) {
            return createNamedRef(getViprClient().varrays().get(id));
        }
        else {
            return findRef(getViprClient().varrays().list(), id);
        }
    }

    public static List<VirtualArrayRestRep> getVirtualArrays() {
        return getViprClient().varrays().getAll();
    }

    public static List<VirtualArrayRestRep> getVirtualArrays(Collection<URI> ids) {
        return getViprClient().varrays().getByIds(ids);
    }

    /**
     * Determines if the resource is assigned to the given virtual array.
     * 
     * @param resource
     *            the resource.
     * @param virtualArrayId
     *            the virtual array ID.
     * @return true if the resource is assigned to the virtual array.
     */
    public static boolean isAssigned(VirtualArrayResourceRestRep resource, String virtualArrayId) {
        return (resource.getAssignedVirtualArrays() != null)
                && resource.getAssignedVirtualArrays().contains(virtualArrayId);
    }

    public static List<VirtualArrayConnectivityRestRep> getConnectivity(String id) {
        return getViprClient().varrays().getConnectivity(uri(id));
    }

    public static List<VirtualArrayConnectivityRestRep> getConnectivity(VirtualArrayRestRep virtualArray) {
        return getViprClient().varrays().getConnectivity(id(virtualArray));
    }

    public static Map<String, Set<NamedRelatedResourceRep>> getConnectivityMap(String id) {
        Map<String, Set<NamedRelatedResourceRep>> connectivityMap = Maps.newTreeMap();
        for (VirtualArrayConnectivityRestRep connectivity : getConnectivity(id)) {
            for (String type : connectivity.getConnectionType()) {
                Set<NamedRelatedResourceRep> virtualArrays = connectivityMap.get(type);
                if (virtualArrays == null) {
                    virtualArrays = Sets.newTreeSet(new NamedRelatedResourceComparator());
                    connectivityMap.put(type, virtualArrays);
                }
                virtualArrays.add(connectivity.getVirtualArray());
            }
        }
        return connectivityMap;
    }

    public static VirtualArrayRestRep create(String name, boolean autoSanZoning) {
        VirtualArrayCreateParam virtualArray = new VirtualArrayCreateParam();
        virtualArray.setLabel(name);
        virtualArray.getBlockSettings().setAutoSanZoning(autoSanZoning);
        return getViprClient().varrays().create(virtualArray);
    }

    public static VirtualArrayRestRep create(String name, boolean autoSanZoning, boolean noNetwork) {
        VirtualArrayCreateParam virtualArray = new VirtualArrayCreateParam();
        virtualArray.setLabel(name);
        virtualArray.getBlockSettings().setAutoSanZoning(autoSanZoning);
        virtualArray.getBlockSettings().setNoNetwork(noNetwork);
        return getViprClient().varrays().create(virtualArray);
    }

    public static VirtualArrayRestRep update(String id, String name, boolean autoSanZoning) {
        VirtualArrayUpdateParam virtualArray = new VirtualArrayUpdateParam();
        virtualArray.setLabel(name);
        virtualArray.getBlockSettings().setAutoSanZoning(autoSanZoning);
        return getViprClient().varrays().update(uri(id), virtualArray);
    }

    public static VirtualArrayRestRep update(String id, String name, boolean autoSanZoning, boolean noNetwork) {
        VirtualArrayUpdateParam virtualArray = new VirtualArrayUpdateParam();
        virtualArray.setLabel(name);
        virtualArray.getBlockSettings().setAutoSanZoning(autoSanZoning);
        virtualArray.getBlockSettings().setNoNetwork(noNetwork);
        return getViprClient().varrays().update(uri(id), virtualArray);
    }

    public static void deactivate(URI id) {
        getViprClient().varrays().deactivate(id);
    }

    @SuppressWarnings("unchecked")
    public static List<VirtualPoolAvailableAttributesResourceRep> getAvailableAttributes(URI id) {
        String cacheKey = String.format("varray.%s.attributes", id);
        List<VirtualPoolAvailableAttributesResourceRep> attributes = (List<VirtualPoolAvailableAttributesResourceRep>) Cache.get(cacheKey);
        if (attributes == null) {
            attributes = getViprClient().varrays().getAvailableAttributes(id);
            Cache.set(cacheKey, attributes, ATTRIBUTE_EXPIRATION);
        }
        return attributes;
    }

    public static Map<String, Set<String>> getAvailableAttributes(List<URI> varrayIds) {

        // get the available attributes for the given virtual arrays
        Map<URI, List<VirtualPoolAvailableAttributesResourceRep>> availableAttributes = getViprClient().varrays().getAvailableAttributes(
                varrayIds);

        // cycle through the available attributes, adding them to the allAttributes list
        Map<String, Set<String>> allAttributes = Maps.newTreeMap();
        for (Entry<URI, List<VirtualPoolAvailableAttributesResourceRep>> varrayId : availableAttributes.entrySet()) {
            List<VirtualPoolAvailableAttributesResourceRep> attributes = varrayId.getValue();
            for (VirtualPoolAvailableAttributesResourceRep attribute : attributes) {
                String attributesName = attribute.getName();
                Set<String> values = allAttributes.get(attributesName);
                // ensure the map has a valid set for this attribute entry
                if (values == null) {
                    values = Sets.newTreeSet();
                    allAttributes.put(attributesName, values);
                }

                // if we have some values for this attribute type, add them to the values list
                if (CollectionUtils.size(attribute.getAttributeValues()) > 0) {
                    values.addAll(attribute.getAttributeValues());
                }
            }
        }

        // ensure all attributes have a valid set in the map
        for (String name : ATTRIBUTES) {
            if (!allAttributes.containsKey(name)) {
                allAttributes.put(name, new TreeSet<String>());
            }
        }

        return allAttributes;
    }

    public static List<ACLEntry> getACLs(String id) {
        return getViprClient().varrays().getACLs(uri(id));
    }

    public static List<ACLEntry> updateACLs(String id, ACLAssignmentChanges changes) {
        return getViprClient().varrays().updateACLs(uri(id), changes);
    }

    /**
     * Checks whether a virtual array supports high availability.
     * 
     * @param varray
     *            the virtual array.
     * @return true if the virtual array supports high availability.
     */
    public static boolean isHighAvailability(VirtualArrayRestRep varray) {
        return isHighAvailability(stringId(varray));
    }

    /**
     * Checks whether a virtual array supports high availability.
     * 
     * @param id
     *            the virtual array ID.
     * @return true if the virtual array supports high availability.
     */
    public static boolean isHighAvailability(String id) {
        for (VirtualArrayConnectivityRestRep connectivity : getConnectivity(id)) {
            if (connectivity.getConnectionType().contains(ConnectivityTypes.VPLEX)) {
                return true;
            }
        }
        return false;
    }

    public static List<ComputeSystemRestRep> getComputeSystems(URI id) {
        return getViprClient().varrays().getComputeSystems(id);
    }
}
