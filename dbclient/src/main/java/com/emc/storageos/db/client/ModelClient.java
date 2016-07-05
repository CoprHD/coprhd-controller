/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client;

import java.net.URI;
import java.util.Calendar;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.dao.ClusterFinder;
import com.emc.storageos.db.client.model.dao.DataAccessException;
import com.emc.storageos.db.client.model.dao.DatacenterFinder;
import com.emc.storageos.db.client.model.dao.ESXHostFinder;
import com.emc.storageos.db.client.model.dao.HMCManagementStatiionFinder;
import com.emc.storageos.db.client.model.dao.HostFinder;
import com.emc.storageos.db.client.model.dao.InitiatorFinder;
import com.emc.storageos.db.client.model.dao.IpInterfaceFinder;
import com.emc.storageos.db.client.model.dao.ModelFinder;
import com.emc.storageos.db.client.model.dao.VCenterFinder;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * @author Chris Dail
 */
public abstract class ModelClient {

    // JM -- Don't attempt to instantiate the DAO fields directly, this will be called before the client field
    // is initialized in the constructor causing NPEs
    private DatacenterFinder datacenterDAO;
    private ESXHostFinder esxHostDAO;
    private VCenterFinder vcenterDAO;
    private HMCManagementStatiionFinder hmcControlStationDAO;
    private HostFinder hostDAO;
    private InitiatorFinder initiatorDAO;
    private IpInterfaceFinder ipInterfaceDAO;
    private ClusterFinder clusterDAO;

    public abstract <T extends DataObject> List<URI> findAllIds(Class<T> clazz, boolean activeOnly);

    public abstract <T extends DataObject> T findById(Class<T> clazz, URI id);

    public abstract <T extends DataObject> Iterable<T> findByIds(Class<T> clazz, List<URI> ids, boolean activeOnly);

    public abstract <T extends DataObject> List<NamedElement> findBy(Class<T> clazz, String columnField, URI id);

    public abstract <T extends DataObject> List<NamedElement> findByPrefix(Class<T> clazz, String columnField, String prefix);

    public abstract <T extends DataObject> List<NamedElement> findByContainmentAndPrefix(Class<T> clazz, String columnField, URI id,
            String labelPrefix);

    public abstract <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value);

    public abstract <T extends DataObject> void create(T model);

    public abstract <T extends DataObject> void update(T model);

    public abstract <T extends DataObject> void delete(T model);

    public abstract <T extends DataObject> void delete(List<T> models);

    public <T extends DataObject> ModelFinder<T> of(final Class<T> clazz) {
        return new ModelFinder<T>(clazz, this);
    }

    /**
     * Finds an object by ID.
     * 
     * @param id
     *            the ID of the object.
     * @return the object.
     */
    public <T extends DataObject> T findById(URI id) {
        if (id == null) {
            throw new DataAccessException("ID provided was null");
        }
        Class<T> modelClass = getModelClass(id);
        if (modelClass != null) {
            return of(modelClass).findById(id);
        } else {
            return null;
        }
    }

    public <T extends DataObject> List<URI> findByType(Class<T> clazz) {
        return this.findAllIds(clazz, true);
    }

    public static String getTypeName(URI id) {
        return getTypeName(id.toString());
    }

    public static String getTypeName(String id) {
        return URIUtil.getTypeName(id);
    }

    public DatacenterFinder datacenters() {
        if (datacenterDAO == null) {
            datacenterDAO = new DatacenterFinder(this);
        }
        return datacenterDAO;
    }

    public ESXHostFinder esxHosts() {
        if (esxHostDAO == null) {
            esxHostDAO = new ESXHostFinder(this);
        }
        return esxHostDAO;
    }

    public VCenterFinder vcenters() {
        if (vcenterDAO == null) {
            vcenterDAO = new VCenterFinder(this);
        }
        return vcenterDAO;
    }

    public HMCManagementStatiionFinder hmcControlSations() {
        if (hmcControlStationDAO == null) {
            hmcControlStationDAO = new HMCManagementStatiionFinder(this);
        }
        return hmcControlStationDAO;
    }

    public HostFinder hosts() {
        if (hostDAO == null) {
            hostDAO = new HostFinder(this);
        }
        return hostDAO;
    }

    public InitiatorFinder initiators() {
        if (initiatorDAO == null) {
            initiatorDAO = new InitiatorFinder(this);
        }
        return initiatorDAO;
    }

    public IpInterfaceFinder ipInterfaces() {
        if (ipInterfaceDAO == null) {
            ipInterfaceDAO = new IpInterfaceFinder(this);
        }
        return ipInterfaceDAO;
    }

    public ClusterFinder clusters() {
        if (clusterDAO == null) {
            clusterDAO = new ClusterFinder(this);
        }
        return clusterDAO;
    }

    private <T extends DataObject> boolean generateIdIfNew(T model) {
        if (model != null && model.getId() == null) {
            model.setId(URIUtil.createId(model.getClass()));
            return true;
        }
        return false;
    }

    private <T extends DataObject> void setCreationTimeIfRequired(T model) {
        if (model != null && model.getCreationTime() == null) {
            model.setCreationTime(Calendar.getInstance());
        }
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T extends DataObject> Class<T> getModelClass(URI id) {
        String typeName = URIUtil.getTypeName(id);
        Class<T> clazz = null;
        try {
            clazz = (Class<T>) Class.forName(String.format("com.emc.storageos.db.client.model.%1$s", typeName));
        } catch (ClassNotFoundException e) {
            DatabaseException.fatals.unableToFindClass(typeName);
        }
        return clazz;
    }

    public <T extends DataObject> void save(T model) {
        boolean isNew = generateIdIfNew(model);
        setCreationTimeIfRequired(model);

        if (isNew) {
            create(model);
        } else {
            update(model);
        }
    }
}
