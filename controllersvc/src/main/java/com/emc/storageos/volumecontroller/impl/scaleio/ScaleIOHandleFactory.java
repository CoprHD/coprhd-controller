/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.scaleio.api.ScaleIOCLI;
import com.emc.storageos.scaleio.api.ScaleIOContants;
import com.emc.storageos.scaleio.api.ScaleIOHandle;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScaleIOHandleFactory {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOHandleFactory.class);
    private final Map<String, ScaleIOHandle> scaleIOCLIMap = new ConcurrentHashMap<String, ScaleIOHandle>();
    private final Map<ScaleIOHandle, String> scaleIOCLIVersionMap =
            new ConcurrentHashMap<>();
    private final Object syncObject = new Object();

    private DbClient dbClient;
    private static final String SIO_V1_3X_REGEX = ".*?1_3\\d+.*";
    private ScaleIORestClientFactory scaleIORestClientFactory;

    public void setDbClient(DbClient client) {
        dbClient = client;
    }

    public ScaleIORestClientFactory getScaleIORestClientFactory() {
        return scaleIORestClientFactory;
    }

    public void setScaleIORestClientFactory(
            ScaleIORestClientFactory scaleIORestClientFactory) {
        this.scaleIORestClientFactory = scaleIORestClientFactory;
    }



    public ScaleIOHandle getCLI(StorageSystem storageSystem)  throws Exception{
        ScaleIOHandle handle;
        synchronized (syncObject) {
            URI activeProviderURI = storageSystem.getActiveProviderURI();
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, activeProviderURI);
            String providerId = provider.getProviderID();
            handle = scaleIOCLIMap.get(providerId);
            handle = getHandle(handle, provider);
        }
        return handle;
    }

    public ScaleIOHandle getCLI(StorageProvider provider) throws Exception {
        ScaleIOHandle handle;
        synchronized (syncObject) {
            String providerId = provider.getProviderID();
            handle = scaleIOCLIMap.get(providerId);
            handle = getHandle(handle, provider);
        }
        return handle;
    }

    private ScaleIOHandle getHandle(ScaleIOHandle handle, StorageProvider provider) throws Exception {
        if (StorageProvider.InterfaceType.scaleio.toString().equals(provider.getInterfaceType())) {
            ScaleIOCLI cliHandle = (ScaleIOCLI) handle;
            if (handle == null) {
                handle = newCLIBasedOnStorageProvider(provider);
            } else {
                handle = handlePossibleSIOVersionChange(provider, cliHandle);
            }
            refreshUsernameAndPassword(cliHandle, provider);
        } else {
            if (handle == null) {
                URI baseURI = URI.create(ScaleIOContants.getAPIBaseURI(provider.getIPAddress(), provider.getPortNumber()));
                handle = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI, provider.getUserName(), provider.getPassword(), true);
                ScaleIORestClient client = (ScaleIORestClient) handle;
                String version = client.init();
                scaleIOCLIMap.put(provider.getProviderID(), client);
                scaleIOCLIVersionMap.put(client, version);
            }
            ScaleIORestClient client = (ScaleIORestClient) handle;
            if (!Strings.isNullOrEmpty(provider.getUserName())) {
                client.setUsername(provider.getUserName());
            }
            if (!Strings.isNullOrEmpty(provider.getPassword())) {
                client.setPassword(provider.getPassword());
            }
        }
        return handle;
    }
    
    public ScaleIOHandleFactory using(DbClient dbClient) {
        setDbClient(dbClient);
        return this;
    }

    /**
     * Routine will check if the SIO instance has changed versions. This is to support the use-case
     * where an existing SIO instance is upgraded.
     *
     * @param provider   [in] - StorageProvider object representing the primary MDM
     * @param currentCLI [in] - ScaleIOCLI object representing the current CLI in use
     * @return ScaleIOCLI - Either a new one (if the version changed) or same as currentCLI
     */
    public ScaleIOCLI handlePossibleSIOVersionChange(StorageProvider provider, ScaleIOCLI currentCLI) {
        ScaleIOCLI cli = currentCLI;
        String storedVersion = scaleIOCLIVersionMap.get(cli);
        // Update the cli with the latest and greatest username/password
        refreshUsernameAndPassword(cli, provider);
        refreshMdmUsernameAndPassword(cli, provider);
        String cliVersion = cli.getVersion();
        if (!storedVersion.equals(cliVersion)) {
            scaleIOCLIVersionMap.remove(cli);
            scaleIOCLIMap.remove(provider.getProviderID());
            cli = newCLIBasedOnStorageProvider(provider);
        }
        if (cliVersion.matches(SIO_V1_3X_REGEX)) {
            if (provider.getSecondaryUsername() == null || provider.getSecondaryPassword() == null) {
                log.error(String.format("ScaleIO CLI for %s is v1.30, but the provider %s does not have secondary credentials specified",
                        provider.getIPAddress(), provider.getId().toString()));
            }
        }
        return cli;
    }

    /**
     * Routine will create a new ScaleIOCLI instance with all the appropriate attributes
     * filled in based on the StorageProvider object passed in.
     *
     * @param provider [in] - StorageProvider object representing the primary MDM
     * @return ScaleIOCLI object - newly created
     */
    private ScaleIOCLI newCLIBasedOnStorageProvider(StorageProvider provider) {
        ScaleIOCLI cli = new ScaleIOCLI(provider.getIPAddress(), provider.getPortNumber(),
                provider.getUserName(), provider.getPassword());
        String sioCLI = provider.getKeyValue(StorageProvider.GlobalKeys.SIO_CLI.name());
        if (NullColumnValueGetter.isNotNullValue(sioCLI)) {
            cli.setCustomInvocation(sioCLI);
        }
        if (cli.getVersion().matches(SIO_V1_3X_REGEX)) {
            cli.setMdmUsername(provider.getSecondaryUsername());
            cli.setMdmPassword(provider.getSecondaryPassword());
        }
        String version = cli.init();
        scaleIOCLIMap.put(provider.getProviderID(), cli);
        scaleIOCLIVersionMap.put(cli, version);
        log.info(String.format("Created a new ScaleIOCLI instance for %s. SIO Version is %s",
                provider.getIPAddress(), cli.getVersion()));
        return cli;
    }

    /**
     * Updates the cli object with the username/password from the StorageProvider.
     *
     * @param cli ScaleIOCLI object to update
     * @param provider StorageProvider to get username/password
     */
    private void refreshUsernameAndPassword(ScaleIOCLI cli, StorageProvider provider) {
        if (cli == null || provider == null) {
            return;
        }
        if (!Strings.isNullOrEmpty(provider.getUserName())) {
            cli.setUsername(provider.getUserName());
        }
        if (!Strings.isNullOrEmpty(provider.getPassword())) {
            cli.setPassword(provider.getPassword());
        }
    }

    /**
     * Updates the cli object with the MDM (Secondary)
     * username/password from the StorageProvider.
     *
     * @param cli ScaleIOCLI object to update
     * @param provider StorageProvider to get secondary username/password
     */
    private void refreshMdmUsernameAndPassword(ScaleIOCLI cli, StorageProvider provider) {
        if (cli == null || provider == null) {
            return;
        }
        if (!Strings.isNullOrEmpty(provider.getSecondaryUsername())) {
            cli.setMdmUsername(provider.getSecondaryUsername());
        }
        if (!Strings.isNullOrEmpty(provider.getSecondaryPassword())) {
            cli.setMdmPassword(provider.getSecondaryPassword());
        }
    }
}
