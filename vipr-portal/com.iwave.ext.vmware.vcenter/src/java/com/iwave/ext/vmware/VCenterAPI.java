/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.vmware;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.StorageResourceManager;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.util.MorUtil;
import com.vmware.vim25.ws.WSClient;

/**
 * Helper API for VCenter to help with traversing the object tree.
 * 
 * @author jonnymiller
 */
public class VCenterAPI {
    /** Constant for the apiType for a vCenter. */
    public static final String VCENTER_API_TYPE = "VirtualCenter";
    private static final int CONNECTION_TIMEOUT = 60 * 60 * 1000;

    private URL url;

    private ServiceInstance service;

    /**
     * Creates a VCenterAPI around an existing service instance.
     * 
     * @param service the service instance.
     */
    public VCenterAPI(ServiceInstance service) {
        this.service = service;
        this.url = service.getServerConnection().getUrl();
    }

    /**
     * Create a VCenterAPI using the given service URL.
     * 
     * @param url the service URL.
     */
    public VCenterAPI(URL url) {
        this.url = url;
    }

    /**
     * Creates a VCenterAPI using the given service URL, and login with the supplied user
     * credentials.
     * 
     * @param url the service URL.
     * @param username the username.
     * @param password the password.
     * 
     * @throws VMWareException if an error occurs.
     */
    public VCenterAPI(URL url, String username, String password) throws VMWareException {
        this(url);
        login(username, password);
    }

    /**
     * Login to the VCenter with the given username and password.
     * 
     * @param username the username.
     * @param password the password.
     * 
     * @throws VMWareException if an error occurs.
     */
    public void login(String username, String password) throws VMWareException {
        if (service != null) {
            logout();
        }
        try {
            service = new ServiceInstance(url, username, password, true);
            WSClient wsclient = service.getServerConnection().getVimService().getWsc();
            wsclient.setConnectTimeout(CONNECTION_TIMEOUT);
            wsclient.setReadTimeout(CONNECTION_TIMEOUT);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        } catch (MalformedURLException e) {
            throw new VMWareException(e);
        } catch (RuntimeException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Logout of the VCenter.
     */
    public void logout() {
        if (service != null) {
            try {
                service.getServerConnection().logout();
            } finally {
                service = null;
            }
        }
    }

    protected void checkConnected() {
        if (service == null) {
            throw new VMWareException("Not logged in");
        }
    }

    /**
     * Gets the about info for the connected VCenter.
     * 
     * @return the about info.
     */
    public AboutInfo getAboutInfo() {
        checkConnected();
        AboutInfo aboutInfo = service.getAboutInfo();
        return aboutInfo;
    }

    /**
     * Gets the root folder.
     * 
     * @return the root folder.
     */
    public Folder getRootFolder() {
        checkConnected();
        Folder rootFolder = service.getRootFolder();
        return rootFolder;
    }

    /**
     * Looks up the managed entity defined by the object reference.
     * 
     * @param mor the object reference.
     * @return the managed entity.
     * 
     * @throws VMWareException if an error occurs.
     */
    public <T extends ManagedEntity> T lookupManagedEntity(ManagedObjectReference mor)
            throws VMWareException {
        checkConnected();
        T entity = (T) MorUtil.createExactManagedEntity(service.getServerConnection(), mor);
        return entity;
    }

    /**
     * Looks up the managed object defined by the object reference.
     * 
     * @param mor the object reference.
     * @return the managed object.
     * 
     * @throws VMWareException if an error occurs.
     */
    public <T extends ManagedObject> T lookupManagedObject(ManagedObjectReference mor) {
        checkConnected();
        T object = (T) MorUtil.createExactManagedObject(service.getServerConnection(), mor);
        return object;
    }

    /**
     * Finds a managed entity by name in the collection.
     * 
     * @param entities the entities.
     * @param name the name of the entity to find.
     * @return the managed entity.
     */
    public <T extends ManagedEntity> T findByName(Collection<T> entities, String name) {
        for (T entity : entities) {
            if (StringUtils.equals(entity.getName(), name)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Finds a managed entity by name in an array (null-safe).
     * 
     * @param entities entities array, may be null.
     * @param name the name.
     * @return the managed entity.
     */
    public <T extends ManagedEntity> T findByName(T[] entities, String name) {
        if (entities != null) {
            for (T entity : entities) {
                if (StringUtils.equals(entity.getName(), name)) {
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * Searches from the root folder for managed entities of the specified type.
     * 
     * @param type the type name.
     * @return the managed entities.
     * 
     * @throws VMWareException if an error occurs.
     */
    public ManagedEntity[] searchManagedEntities(String type) throws VMWareException {
        return searchManagedEntities(getRootFolder(), type, true);
    }

    /**
     * Searches from the root folder for managed entities of the specified type.
     * 
     * @param type the type name.
     * @param recurse whether to recurse.
     * @return the managed entities.
     * 
     * @throws VMWareException if an error occurs.
     */
    public ManagedEntity[] searchManagedEntities(String type, boolean recurse)
            throws VMWareException {
        return searchManagedEntities(getRootFolder(), type, recurse);
    }

    /**
     * Searches from the given parent for managed entities of the specified type.
     * 
     * @param parent the parent entity.
     * @param type the type name.
     * @return the managed entities.
     * 
     * @throws VMWareException if an error occurs.
     */
    public ManagedEntity[] searchManagedEntities(ManagedEntity parent, String type)
            throws VMWareException {
        return searchManagedEntities(parent, type, true);
    }

    /**
     * Searches from the given parent for managed entities of the specified type.
     * 
     * @param parent the parent entity.
     * @param type the type name.
     * @param recurse whether to recurse.
     * @return the managed entities.
     * 
     * @throws VMWareException if an error occurs.
     */
    public ManagedEntity[] searchManagedEntities(ManagedEntity parent, String type, boolean recurse)
            throws VMWareException {
        try {
            String[][] typeInfo = new String[][] { new String[] { type, "name" } };
            InventoryNavigator navigator = new InventoryNavigator(parent);
            ManagedEntity[] entities = navigator.searchManagedEntities(typeInfo, recurse);
            return entities;
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Searches from the root folder for a single managed entity with the specified type and name.
     * 
     * @param parent the parent entity.
     * @param type the type name.
     * @param name the entity name.
     * @return the managed entity.
     * 
     * @throws VMWareException if an error occurs.
     */
    public <T extends ManagedEntity> T searchManagedEntity(String type, String name)
            throws VMWareException {
        return searchManagedEntity(getRootFolder(), type, name);
    }

    /**
     * Searches from the given parent for a single managed entity with the specified type and name.
     * 
     * @param type the type name.
     * @param name the entity name.
     * @return the managed entity.
     * 
     * @throws VMWareException if an error occurs.
     */
    public <T extends ManagedEntity> T searchManagedEntity(ManagedEntity parent, String type,
            String name) throws VMWareException {
        try {
            InventoryNavigator navigator = new InventoryNavigator(parent);
            ManagedEntity entity = navigator.searchManagedEntity(type, name);
            return (T) entity;
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Searches from the given parent for managed entities of the given type.
     * 
     * @param parent the parent entity.
     * @param type the desired type.
     * @param recurse whether to recurse.
     * @return the list of managed entities.
     * 
     * @throws VMWareException if an error occurs.
     */
    protected <T extends ManagedEntity> List<T> searchManagedEntities(ManagedEntity parent,
            Class<T> type, boolean recurse) throws VMWareException {
        String typeName = type.getSimpleName();
        List<T> results = Lists.newArrayList();
        for (ManagedEntity entity : searchManagedEntities(parent, typeName, recurse)) {
            results.add((T) entity);
        }
        return results;
    }

    /**
     * Searches from the given parent for managed entities of the given type.
     * 
     * @param parent the parent entity.
     * @param type the desired type.
     * @return the list of managed entities.
     * 
     * @throws VMWareException if an error occurs.
     */
    protected <T extends ManagedEntity> List<T> searchManagedEntities(ManagedEntity parent,
            Class<T> type) throws VMWareException {
        return searchManagedEntities(parent, type, true);
    }

    /**
     * Searches from the given parent for a managed entity of the given type with the name
     * specified.
     * 
     * @param parent the parent entity.
     * @param type the desired type.
     * @param name the entity name.
     * @return the managed entity.
     * 
     * @throws VMWareException if an error occurs.
     */
    protected <T extends ManagedEntity> T searchManagedEntity(ManagedEntity parent, Class<T> type,
            String name) throws VMWareException {
        String typeName = type.getSimpleName();
        T value = (T) searchManagedEntity(parent, typeName, name);
        return value;
    }

    /**
     * Creates a list from an array of elements (null-safe).
     * 
     * @param array the array, or null.
     * @return a list.
     */
    protected <T> List<T> createList(T[] elements) {
        if (elements != null) {
            return Lists.newArrayList(elements);
        }
        else {
            return Lists.newArrayList();
        }
    }

    /**
     * Lists the folders under the given parent.
     * 
     * @param parent the parent entity.
     * @param recurse whether to recurse.
     * @return the list of folders.
     * 
     * @throws VMWareException if an error occurs.
     */
    public List<Folder> listFolders(ManagedEntity parent, boolean recurse) throws VMWareException {
        return searchManagedEntities(parent, Folder.class, recurse);
    }

    public List<Datacenter> listAllDatacenters() throws VMWareException {
        return searchManagedEntities(getRootFolder(), Datacenter.class);
    }

    public List<ComputeResource> listAllComputeResources() throws VMWareException {
        return searchManagedEntities(getRootFolder(), ComputeResource.class);
    }

    public List<ClusterComputeResource> listAllClusters() throws VMWareException {
        return searchManagedEntities(getRootFolder(), ClusterComputeResource.class);
    }

    public List<HostSystem> listAllHostSystems() throws VMWareException {
        return searchManagedEntities(getRootFolder(), HostSystem.class);
    }

    public List<Datastore> listAllDatastores() throws VMWareException {
        return searchManagedEntities(getRootFolder(), Datastore.class);
    }

    public List<VirtualMachine> listAllVirtualMachines() throws VMWareException {
        return searchManagedEntities(getRootFolder(), VirtualMachine.class);
    }

    public List<ComputeResource> listComputeResources(Datacenter datacenter) throws VMWareException {
        return searchManagedEntities(getHostFolder(datacenter), ComputeResource.class);
    }

    public List<ClusterComputeResource> listClusters(Datacenter datacenter) throws VMWareException {
        return searchManagedEntities(datacenter, ClusterComputeResource.class);
    }

    public List<HostSystem> listHostSystems(Datacenter datacenter) throws VMWareException {
        return searchManagedEntities(getHostFolder(datacenter), HostSystem.class);
    }

    public List<HostSystem> listHostSystems(ComputeResource computeResource) throws VMWareException {
        return createList(computeResource.getHosts());
    }

    public List<Datastore> listDatastores(Datacenter datacenter) throws VMWareException {
        return searchManagedEntities(datacenter.getDatastoreFolder(), Datastore.class);
    }

    public List<Datastore> listDatastores(ComputeResource computeResource) throws VMWareException {
        return createList(computeResource.getDatastores());
    }

    public List<Datastore> listDatastores(HostSystem hostSystem) throws VMWareException {
        try {
            return createList(hostSystem.getDatastores());
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    public List<VirtualMachine> listVirtualMachines(Datacenter datacenter) throws VMWareException {
        return searchManagedEntities(getVmFolder(datacenter), VirtualMachine.class);
    }

    public List<VirtualMachine> listVirtualMachines(Datastore datastore) throws VMWareException {
        return createList(datastore.getVms());
    }

    public Datacenter findDatacenter(String datacenterName) throws VMWareException {
        return searchManagedEntity(getRootFolder(), Datacenter.class, datacenterName);
    }

    public Folder getHostFolder(Datacenter datacenter) throws VMWareException {
        try {
            return datacenter.getHostFolder();
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    public Folder getVmFolder(Datacenter datacenter) throws VMWareException {
        try {
            return datacenter.getVmFolder();
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    public HostSystem findHostSystem(Datacenter datacenter, String name) {
        return searchManagedEntity(getHostFolder(datacenter), HostSystem.class, name);
    }

    public HostSystem findHostSystem(String datacenterName, String hostName) {
        Datacenter datacenter = findDatacenter(datacenterName);
        if (datacenter == null) {
            return null;
        }
        return findHostSystem(datacenter, hostName);
    }

    public ClusterComputeResource findCluster(Datacenter datacenter, String name) {
        return searchManagedEntity(getHostFolder(datacenter), ClusterComputeResource.class, name);
    }

    public ClusterComputeResource findCluster(String datacenterName, String name) {
        Datacenter datacenter = findDatacenter(datacenterName);
        if (datacenter == null) {
            return null;
        }
        return findCluster(datacenter, name);
    }

    public Datastore findDatastore(Datacenter datacenter, String name) {
        return searchManagedEntity(datacenter.getDatastoreFolder(), Datastore.class, name);
    }

    public Datastore findDatastore(String datacenterName, String datastoreName) {
        Datacenter datacenter = findDatacenter(datacenterName);
        if (datacenter == null) {
            return null;
        }
        return findDatastore(datacenter, datastoreName);
    }

    public Datastore findNfsDatastore(String datacenterName, String datastoreName) {
        Datacenter datacenter = findDatacenter(datacenterName);
        if (datacenter == null) {
            throw new VMWareException(String.format("Unable to find Datacenter %s", datacenterName));
        }
        return findDatastore(datacenter, datastoreName);
    }

    public VcenterVersion getVcenterVersion() {
        VcenterVersion result = null;
        AboutInfo info = service.getAboutInfo();
        result = new VcenterVersion(info.getVersion());
        return result;
    }

    public StorageResourceManager getStorageResourceManager() {
        ManagedObjectReference managedStorageResource = service.getServiceContent().getStorageResourceManager();
        return new StorageResourceManager(service.getServerConnection(), managedStorageResource);
    }

    /**
     * Get Esx Version
     * 
     * @return
     */
    public EsxVersion getEsxVersion() {
        EsxVersion result = null;
        AboutInfo info = service.getAboutInfo();
        result = new EsxVersion(info.getVersion());
        return result;
    }
}
