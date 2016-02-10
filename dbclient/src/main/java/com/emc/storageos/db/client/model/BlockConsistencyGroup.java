/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

@Cf("BlockConsistencyGroup")
public class BlockConsistencyGroup extends DataObject implements ProjectResource {

    /**
     * Storage system where this consistency group is located
     */
    private URI _storageController;

    /**
     * Virtual array where this consistency group exists
     */
    private URI _virtualArray;

    /**
     * Project within this consistency group exists
     */
    private NamedURI _project;

    /**
     * Tenant within this consistency group exists
     */
    private NamedURI _tenant;

    /**
     * Unique name created by the NameGenerator Class
     */
    @Deprecated
    private String _deviceName;

    /**
     * SRDF CGs or Local.
     */
    @Deprecated
    private String type;

    /**
     * Types that this BlockConsistencyGroup maps to. A single
     * BlockConsistencyGroup can map to multiple different consistency
     * group types. This set is not filled in until the corresponding provisioning
     * for each type has been completed.
     */
    private StringSet types;

    /**
     * Requested types. This is initialized when we are first creating the CG, and
     * then used to determine if the CG creation has completed by comparing it
     * with types.
     */
    private StringSet requestedTypes;

    /**
     * Alternate label used in SRDF.
     */
    private String alternateLabel;

    /**
     * Maps the StorageSystem (for VPlex, VNX, VMAX) and/or ProtectionSystem (for RP)
     * to the corresponding consistency groups that have been created.
     *
     * Here are the different permutations that can exist based on types:
     * RP: [protectionSystem->cg1]
     * LOCAL: [storageSystem->cg1]
     * SRDF: [storageSystem->cg1]
     * VPLEX: [storageSystem->[cluster-1:cg1,cluster-2:cg1]]
     * RP,VPLEX: [protectionSystem->cg1,storageSystem->[cluster-1:cg1,cluster-2:cg1]]
     */
    private StringSetMap systemConsistencyGroups;

    public static enum Types {
        /* RecoverPoint consistency group type. */
        RP,
        /* SRDF consistency group type. */
        SRDF,
        /* VPlex consistency group type. */
        VPLEX,
        /* Array-based consistency group type. */
        LOCAL
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageController() {
        return _storageController;
    }

    public void setStorageController(URI storageController) {
        _storageController = storageController;
        setChanged("storageDevice");
    }

    @Name("varray")
    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @Override
    @Name("project")
    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @Override
    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return _tenant;
    }

    public void setTenant(NamedURI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    @Name("deviceName")
    @Deprecated
    public String getDeviceName() {
        return _deviceName;
    }

    @Deprecated
    public void setDeviceName(String deviceName) {
        _deviceName = deviceName;
        setChanged("deviceName");
    }

    @AlternateId("AltIdIndex")
    @Name("alternateLabel")
    public String getAlternateLabel() {
        return alternateLabel;
    }

    public void setAlternateLabel(String alternateLabel) {
        this.alternateLabel = alternateLabel;
        setChanged("alternateLabel");
    }

    // alternate label set only for target SRDF Groups
    public boolean srdfTarget() {
        return null != alternateLabel;
    }

    @Name("type")
    @Deprecated
    public String getType() {
        return type;
    }

    @Deprecated
    public void setType(String type) {
        this.type = type;
        setChanged("type");
    }

    @Name("types")
    public StringSet getTypes() {
        if (types == null) {
            types = new StringSet();
        }
        return types;
    }

    public void setTypes(StringSet types) {
        this.types = types;
        setChanged("types");
    }

    @Name("requestedTypes")
    public StringSet getRequestedTypes() {
        // Handle migration case where we did not record requestedTypes by initializing it to Types
        if (this.requestedTypes == null && this.types != null && !this.types.isEmpty()) {
            requestedTypes = new StringSet();
            requestedTypes.addAll(getTypes());
            setRequestedTypes(requestedTypes);
            return requestedTypes;
        }
        if (this.requestedTypes == null) {
            requestedTypes = new StringSet();
        }
        return requestedTypes;
    }

    public boolean isProtectedCG() {
        if (requestedTypes == null || requestedTypes.isEmpty()) {
            return false;
        }
        return requestedTypes.contains(Types.RP.toString()) || requestedTypes.contains(Types.VPLEX.toString());
    }

    public void setRequestedTypes(StringSet requestedTypes) {
        this.requestedTypes = requestedTypes;
        setChanged("requestedTypes");
    }

    public void addRequestedTypes(Collection<String> addedTypes) {
        if (this.requestedTypes == null) {
            setRequestedTypes(new StringSet());
        }
        for (String type : addedTypes) {
            getRequestedTypes().add(type);
        }
    }

    @Name("systemConsistencyGroups")
    public StringSetMap getSystemConsistencyGroups() {
        return systemConsistencyGroups;
    }

    public void setSystemConsistencyGroups(
            StringSetMap systemConsistencyGroups) {
        this.systemConsistencyGroups = systemConsistencyGroups;
        setChanged("systemConsistencyGroups");
    }

    /**
     * Check whether this Consistency Group has been created. For each
     * of the requested BlockConsistencyGroup types, we must have
     * associated consistency groups that have been created,
     * which is indicated by the presence in types. If nothing has been
     * requested yet, we haven't created the consistencyGroup.
     *
     * @return true if the ConsistencyGroup has been created, false otherwise.
     */
    public boolean created() {
        return !getRequestedTypes().isEmpty() && getTypes().containsAll(getRequestedTypes());
    }

    /**
     * Returns true if CG creation has been initiated or even completed
     * as given by something was recorded in requestedTypes().
     *
     * @return true if CG creation has been initiated.
     */
    public boolean creationInitiated() {
        return !getRequestedTypes().isEmpty();
    }

    /**
     * Check to see if the consistency group has been created for the given
     * storage system.
     *
     * @param storageSystemUri The storage system URI
     * @return true if the consistency group has been created, false otherwise.
     */
    public boolean created(URI storageSystemUri) {
        if (storageSystemUri != null &&
                systemConsistencyGroups != null && !systemConsistencyGroups.isEmpty()) {
            StringSet cgNames = systemConsistencyGroups.get(storageSystemUri.toString());
            if (cgNames != null && !cgNames.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Convenience method to add a consistency group type.
     *
     * @param types The types to add to this consistency group.
     */
    public void addConsistencyGroupTypes(String... cgTypes) {
        if (types == null) {
            setTypes(new StringSet());
        }

        for (String type : cgTypes) {
            types.add(type);
        }
    }

    /**
     * Determines if the consistency group has the passed type.
     *
     * @param type The type to check.
     *
     * @return true when the consistency group has the type, false otherwise.
     */
    public boolean checkForType(Types type) {
        if (types == null) {
            return false;
        } else {
            return types.contains(type.toString());
        }
    }

    /**
     * Add a mapping of storage systems to consistency group names.
     *
     * @param systemUri The StorageSystem or ProtectionSystem URI string.
     * @param cgName The consistency group name.
     */
    public void addSystemConsistencyGroup(String systemUri, String cgName) {
        if (systemConsistencyGroups == null) {
            setSystemConsistencyGroups(new StringSetMap());
        }

        if (!systemConsistencyGroups.containsKey(systemUri)) {
            systemConsistencyGroups.put(systemUri, new StringSet());
        }

        StringSet systemCgNames = systemConsistencyGroups.get(systemUri);
        systemCgNames.add(cgName);
    }

    /**
     * Remove a mapping of storage system to consistency group name.
     *
     * @param systemUri The StorageSystem or ProtectionSystem URI string.
     * @param cgName The consistency group name.
     */
    public void removeSystemConsistencyGroup(String systemUri, String cgName) {
        if ((systemConsistencyGroups != null) && (systemConsistencyGroups.containsKey(systemUri))) {
            StringSet systemCgNames = systemConsistencyGroups.get(systemUri);
            systemCgNames.remove(cgName);
            if (systemCgNames.isEmpty()) {
                systemConsistencyGroups.remove(systemUri);
            }
        }
    }

    /**
     * Gets the first CG name that corresponds to the provided storage system URI.
     * A BlockConsistencyGroup can only map to a single consistency group on a single
     * storage system so that's why we only return the first entry.
     * 
     * @param storageSystemUri
     * @return
     */
    public String fetchArrayCgName(URI storageSystemUri) {
        if (storageSystemUri == null) {
            return null;
        }

        if (systemConsistencyGroups != null && !systemConsistencyGroups.isEmpty()) {
            StringSet cgNames = systemConsistencyGroups.get(storageSystemUri.toString());
            if (cgNames != null && cgNames.iterator().hasNext()) {
                return cgNames.iterator().next();
            }
        }

        return null;
    }

    /**
     * checks to see if the CG name matches the name ViPR has stored for the storage system
     *
     * @param cgId id of the CG in the DB
     * @param storageSystemId storage system to check the CG name
     * @param cgParams cg params with CG name
     * @return true if the CG name exists in the ViPR db
     */
    public boolean nameExistsForStorageSystem(URI storageSystemId, String cgName) {
        return cgName != null ? cgName.equals(getCgNameOnStorageSystem(storageSystemId))
                : false;
    }

    /**
     * Returns the name of the CG on the storage system if ViPR has created this CG on the storage system in the past.
     *
     * @param cgId id of the CG in the ViPR db
     * @param storageSystemId storage system to get the name for
     * @return the CG name or null if ViPR has not created the CG on the storage system
     */
    public String getCgNameOnStorageSystem(URI storageSystemId) {
        String cgName = null;
        StringSetMap ssm = this.getSystemConsistencyGroups();
        if (ssm != null) {
            StringSet ss = ssm.get(storageSystemId.toString());
            if (ss != null) {
                Iterator<String> itr = ss.iterator();
                if (itr.hasNext()) {
                    cgName = itr.next();
                }
            }
        }
        return cgName;
    }

}
