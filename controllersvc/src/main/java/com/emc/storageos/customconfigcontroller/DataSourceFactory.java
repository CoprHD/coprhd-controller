/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.google.common.base.Strings;

/**
 * This factory creates instances of {@link DataSource} from a list
 * of {@link DataObject} or properties. This is a utility class.
 * 
 */
public class DataSourceFactory {

    private CustomConfigTypeProvider configTypeProvider;
    private DbModelClient dbModelClient;

    /**
     * An instance of DbModelClient to handle db queries
     * 
     * @return instance of DbModelClient to handle db queries
     */
    public DbModelClient getDbModelClient() {
        return dbModelClient;
    }

    /**
     * An instance of DbModelClient to handle db queries
     * 
     * @param dbModelClient
     */
    public void setDbModelClient(DbModelClient dbModelClient) {
        this.dbModelClient = dbModelClient;
    }

    /**
     * Returns an instance of the provider that holds the configType definitions
     * 
     * @return an instance of the provider that holds the configType definitions
     */
    public CustomConfigTypeProvider getConfigTypeProvider() {
        return configTypeProvider;
    }

    public void setConfigTypeProvider(CustomConfigTypeProvider configTypeProvider) {
        this.configTypeProvider = configTypeProvider;
    }

    /**
     * Creates a data source from a collection of model objects for a given
     * customizable configuration.
     * 
     * @param configName the name of the customizable configuration
     * @param objs a collection of objects. It is expected that all objects
     *            required to fully populate the source with all the properties
     *            needed is provided. The factory does not ensure that the data
     *            source is fully populated.
     * @return a data source populated with the properties needed to resolve
     *         a name mask. It is the responsibility of the caller
     *         to ensure all needed objects to resolve a name mask are supplied.
     */
    public DataSource createDataSource(String configName, DataObject[] objs) {
        CustomConfigType item = configTypeProvider
                .getCustomConfigType(configName);
        Map<Class<? extends DataObject>, DataObject> objectsMap = toMap(objs);
        DataSource dataSource = new DataSource();
        DataObject object = null;
        Object val = null;
        for (DataSourceVariable prop : item.getDataSourceVariables().keySet()) {
            object = objectsMap.get(prop.getSourceClass());
            if (object != null) {
                val = DataObjectUtils.getPropertyValue(object.getClass(),
                        object, prop.getPropertyName());
                dataSource.addProperty(prop.getDisplayName(),
                        val == null ? "" : val.toString());
            }
        }
        return dataSource;
    }

    /**
     * Creates a data source from a collection of model objects and map of
     * calculated values for a given customizable configuration.
     * 
     * @param configName the name of the customizable configuration
     * @param objs a collection of objects. It is expected that all objects
     *            required to fully populate the source with all the properties
     *            needed is provided. The factory does not ensure that the data
     *            source is fully populated.
     * @param computedValueMap a map of values which is calculated and not directly
     *            retrievable from the objects.
     * @return a data source populated with the properties needed to resolve
     *         a name mask. It is the responsibility of the caller
     *         to ensure all needed objects to resolve a name mask are supplied.
     */
    public DataSource createDataSource(String configName, DataObject[] objs, Map<String, String> computedValueMap) {
        CustomConfigType item = configTypeProvider
                .getCustomConfigType(configName);
        Map<Class<? extends DataObject>, DataObject> objectsMap = toMap(objs);
        DataSource dataSource = new DataSource();
        DataObject object = null;
        Object val = null;
        for (DataSourceVariable prop : item.getDataSourceVariables().keySet()) {
            if (prop instanceof ComputedDataSourceVariable) {
                val = computedValueMap.get(((ComputedDataSourceVariable) prop).getComputedPropertyName());
            } else {
                object = objectsMap.get(prop.getSourceClass());
                if (object != null) {
                    val = DataObjectUtils.getPropertyValue(object.getClass(),
                            object, prop.getPropertyName());
                }
            }

            dataSource.addProperty(prop.getDisplayName(),
                    val == null ? "" : val.toString());
        }
        return dataSource;
    }

    /**
     * Creates a data source for resolving a zone name using the provided
     * parameters. This is a utility function created for the purpose of
     * reducing clutter in the export code.
     * 
     * @param host the instance of the host for which the zone will be created
     * @param initiator the instance of the initiator for which the zone will be created
     * @param port the instance of the port for which the zone will be created
     * @param network the instance of the network where the zone will be created
     * @param storageSystem the instance of the storage system of the zone's port
     * @return a data source populated with the properties needed to resolve
     *         a zone name
     */
    public DataSource createZoneNameDataSource(Host host, Initiator initiator,
            StoragePort port, Network network, StorageSystem storageSystem) {
        DataSource source = createDataSource(CustomConfigConstants.ZONE_MASK_NAME,
                new DataObject[] { host, initiator, port, network, storageSystem });
        return source;
    }

    /**
     * Creates a data source for resolving a zone name using the provided
     * parameters. This is a utility function created for the purpose of
     * reducing clutter in the export code.
     * 
     * @param hostName the name of the host for which the zone will be created
     * @param initiator the initiator for which the zone will be created
     * @param port the instance of the port for which the zone will be created
     * @param storageSystem the instance of the storage system of the zone's port
     * @param nativeId the nativeId of the network where the zone will be created
     * @return a data source populated with the properties needed to resolve
     *         a zone name
     */
    public DataSource createZoneNameDataSource(String hostName, Initiator initiator,
            StoragePort port, String nativeId, StorageSystem storageSystem) {
        // we want to avoid looking up the network because of all the endpoints
        Network network = new Network();
        network.setNativeId(nativeId);
        return createZoneNameDataSource(getHostByName(hostName), initiator, port, network, storageSystem);
    }

    /**
     * Creates a datasource used for export operations
     * 
     * @param configName - name of the config for which the export datasource should be created
     * @param host - host instance
     * @param cluster - cluster instance
     * @param storageSystem - storage system instance
     * @return a data source populated with the properties needed
     */
    public DataSource createExportMaskDataSource(String configName, Host host, Cluster cluster, StorageSystem storageSystem) {
        return createDataSource(configName, new DataObject[] { host, cluster, storageSystem });
    }

    /**
     * Creates a datasource used for export operations
     * 
     * @param configName - name of the config for which the export datasource should be created
     * @param hostName - host name
     * @param clusterName - cluster name
     * @param storageSystem - storage system instance
     * @return a data source populated with the properties needed
     */
    public DataSource createExportMaskDataSource(String configName, String hostName, String clusterName, StorageSystem storageSystem) {
        // for host, get the object because the name may query for host type
        Host host = getHostByName(hostName);

        // for cluster, just create an in-memory object, cluster has no other attributes
        Cluster cluster = new Cluster();
        if (!Strings.isNullOrEmpty(clusterName)) {
            cluster.setLabel(clusterName);
        }

        return createExportMaskDataSource(configName, host, cluster, storageSystem);

    }

    /**
     * Creates a datasource for resolving HSD's nickname
     * 
     * @param hostName - host name
     * @param storagePortNumber - HDS storage port number
     * @return a data source populated with the properties needed
     */
    public DataSource createHSDNickNameDataSource(String hostName, String storagePortNumber, StorageSystem storageSystem) {
        Host host = getHostByName(hostName);
        Map<String, String> computedValueMap = new HashMap<String, String>();
        computedValueMap.put(CustomConfigConstants.HDS_STORAGE_PORT_NUMBER, storagePortNumber);

        return createDataSource(CustomConfigConstants.HDS_HOST_STORAGE_DOMAIN_NICKNAME_MASK_NAME, new DataObject[] { host, storageSystem },
                computedValueMap);
    }

    /**
     * Create a data source object for resolving XtremIO Volume folder names
     * 
     * @param project the project that the volume will be created on
     * @param storageSystem the storageSystem that the volume will be created on.
     * @return a data source populated with the properties
     */
    public DataSource createXtremIOVolumeFolderNameDataSource(Project project, StorageSystem storageSystem) {

        return createDataSource(CustomConfigConstants.XTREMIO_VOLUME_FOLDER_NAME,
                new DataObject[] { project, storageSystem });

    }

    /**
     * Create a data source object for resolving XtremIO initiator group names
     * 
     * @param Host the Host that the initiators belong to
     * @param storageSystem the storageSystem that the volume will be created on.
     * @return a data source populated with the properties
     */
    public DataSource createXtremIOInitiatorGroupNameDataSource(String hostName, StorageSystem storageSystem) {
        Host host = getHostByName(hostName);
        return createDataSource(CustomConfigConstants.XTREMIO_INITIATOR_GROUP_NAME,
                new DataObject[] { host, storageSystem });

    }

    /**
     * Create a data source object for resolving XtremIO initiator group folder names
     * 
     * @param host the host that the initiator group folder belong to
     * @param storageSystem the storageSystem that the volume will be created on.
     * @return a data source populated with the properties
     */
    public DataSource createXtremIOHostInitiatorGroupFolderNameDataSource(String hostName, StorageSystem storageSystem) {
        Host host = getHostByName(hostName);
        return createDataSource(CustomConfigConstants.XTREMIO_HOST_INITIATOR_GROUP_FOLDER_NAME,
                new DataObject[] { host, storageSystem });

    }

    /**
     * Create a data source object for resolving XtremIO initiator group folder names
     * 
     * @param cluster the cluster that the initiator group folder belong to
     * @param storageSystem the storageSystem that the volume will be created on.
     * @return a data source populated with the properties
     */
    public DataSource createXtremIOClusterInitiatorGroupFolderNameDataSource(String clusterName, StorageSystem storageSystem) {

        Cluster cluster = new Cluster();
        if (!Strings.isNullOrEmpty(clusterName)) {
            cluster.setLabel(clusterName);
        }
        return createDataSource(CustomConfigConstants.XTREMIO_CLUSTER_INITIATOR_GROUP_FOLDER_NAME,
                new DataObject[] { cluster, storageSystem });

    }

    /**
     * Creates a data source objects for a customizable configuration
     * using the sample data stored in the configType definition.
     * 
     * @param configName the name of the customizable configuration
     * @return a data source objects populated with sample data for the properties
     *         required to resolve a name mask for a customizable configuration.
     */
    public DataSource getSampleDataSource(String configName) {
        CustomConfigType item = configTypeProvider
                .getCustomConfigType(configName);
        DataSource dataSource = new DataSource();

        for (DataSourceVariable prop : item.getDataSourceVariables().keySet()) {
            dataSource.addProperty(prop.getDisplayName(), prop.getSample());
        }

        return dataSource;
    }

    /**
     * A utility function to create a map of class-to-object from a
     * list of objects.
     * 
     * @param objects the list of objects.
     * @return a map of class-to-object
     */
    private Map<Class<? extends DataObject>, DataObject> toMap(DataObject[] objects) {
        Map<Class<? extends DataObject>, DataObject> objectsMap = new HashMap<Class<? extends DataObject>, DataObject>();
        for (DataObject object : objects) {
            if (object != null) {
                objectsMap.put(object.getClass(), object);
            }
        }
        return objectsMap;
    }

    private Host getHostByName(String hostName) {
        // for host, get the object because the name may query for host type
        Host host = null;
        if (hostName != null && !hostName.isEmpty()) {
            host = dbModelClient.findByUniqueAlternateId(Host.class,
                    Host.ALTER_ID_FIELD, hostName);
            if (host == null) {
                host = new Host();
                host.setHostName(hostName);
                host.setLabel(hostName);
                // Internal hosts such as Vplex cannot be discovered,
                // so Other is the correct type
                host.setType(Host.HostType.Other.name());
            }
        }
        return host;
    }
}
