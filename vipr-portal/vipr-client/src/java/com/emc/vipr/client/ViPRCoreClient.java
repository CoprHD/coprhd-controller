/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import java.net.URI;

import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.user.UserInfo;
import com.emc.vipr.client.core.*;
import com.emc.vipr.client.impl.RestClient;

public class ViPRCoreClient {
    protected RestClient client;

    /**
     * Convenience method for calling constructor with new ClientConfig().withHost(host)
     * 
     * @param host Hostname or IP address for the Virtual IP of the target environment.
     */
    public ViPRCoreClient(String host) {
        this(new ClientConfig().withHost(host));
    }

    /**
     * Convenience method for calling constructor with new ClientConfig().withHost(host).withIgnoringCertificates(ignoreCertificates)
     * 
     * @param host Hostname or IP address for the Virtual IP of the target environment.
     * @param ignoreCertificates True if SSL certificates should be ignored.
     */
    public ViPRCoreClient(String host, boolean ignoreCertificates) {
        this(new ClientConfig().withHost(host).withIgnoringCertificates(ignoreCertificates));
    }

    public ViPRCoreClient(ClientConfig config) {
        this.client = config.newClient();
    }

    /**
     * Sets the authentication token to be used for this client.
     * 
     * @param authToken
     *            The authentication token to set.
     */
    public void setAuthToken(String authToken) {
        client.setAuthToken(authToken);
    }

    /**
     * Sets the proxy token to be used for this client.
     * 
     * @param proxyToken
     *            The authentication token to set.
     */
    public void setProxyToken(String proxyToken) {
        client.setProxyToken(proxyToken);
    }

    public AuthClient auth() {
        return new AuthClient(client);
    }

    /**
     * Performs an authentication login and returns the updated client.
     * 
     * @see AuthClient#login(String, String)
     * @param username
     *            The username.
     * @param password
     *            The password.
     * @return The updated client.
     */
    public ViPRCoreClient withLogin(String username, String password) {
        auth().login(username, password);
        return this;
    }

    /**
     * Sets the authentication token and returns the updated client.
     * 
     * @see #setAuthToken(String)
     * @param token
     *            The authentication token to set.
     * @return The updated client.
     */
    public ViPRCoreClient withAuthToken(String token) {
        setAuthToken(token);
        return this;
    }

    /**
     * Sets the proxy token and returns the updated client.
     * 
     * @see #setProxyToken(String)
     * @param token
     *            The proxy token to set.
     * @return The updated client.
     */
    public ViPRCoreClient withProxyToken(String token) {
        setProxyToken(token);
        return this;
    }

    public TenantResponse getUserTenant() {
        TenantResponse tenant = client.get(TenantResponse.class, "/tenant");
        return tenant;
    }

    public URI getUserTenantId() {
        return getUserTenant().getTenant();
    }

    public UserInfo getUserInfo() {
        return client.get(UserInfo.class, "/user/whoami");
    }

    public Projects projects() {
        return new Projects(this, client);
    }

    public VirtualDataCenters vdcs() {
        return new VirtualDataCenters(this, client);
    }

    public Hosts hosts() {
        return new Hosts(this, client);
    }

    public Clusters clusters() {
        return new Clusters(this, client);
    }

    public Vcenters vcenters() {
        return new Vcenters(this, client);
    }

    public VcenterDataCenters vcenterDataCenters() {
        return new VcenterDataCenters(this, client);
    }

    public Initiators initiators() {
        return new Initiators(this, client);
    }

    public IpInterfaces ipInterfaces() {
        return new IpInterfaces(this, client);
    }

    public BlockVolumes blockVolumes() {
        return new BlockVolumes(this, client);
    }

    public BlockFullCopies blockFullCopies() {
        return new BlockFullCopies(this, client);
    }

    public BlockExports blockExports() {
        return new BlockExports(this, client);
    }

    public BlockVirtualPools blockVpools() {
        return new BlockVirtualPools(this, client);
    }

    public FileVirtualPools fileVpools() {
        return new FileVirtualPools(this, client);
    }

    public ObjectVirtualPools objectVpools() {
        return new ObjectVirtualPools(this, client);
    }
    
    public ObjectBuckets objectBuckets() {
        return new ObjectBuckets(this, client);
    }
    
    public ComputeVirtualPools computeVpools() {
        return new ComputeVirtualPools(this, client);
    }

    public ComputeImageServers computeImageServers() {
        return new ComputeImageServers(this, client);
    }

    public ComputeImages computeImages() {
        return new ComputeImages(this, client);
    }

    public VirtualArrays varrays() {
        return new VirtualArrays(this, client);
    }

    public BlockConsistencyGroups blockConsistencyGroups() {
        return new BlockConsistencyGroups(this, client);
    }

    public BlockMigrations blockMigrations() {
        return new BlockMigrations(this, client);
    }

    public BlockSnapshots blockSnapshots() {
        return new BlockSnapshots(this, client);
    }

    public FileSystems fileSystems() {
        return new FileSystems(this, client);
    }

    public QuotaDirectories quotaDirectories() {
        return new QuotaDirectories(this, client);
    }

    public FileSnapshots fileSnapshots() {
        return new FileSnapshots(this, client);
    }

    public Networks networks() {
        return new Networks(this, client);
    }

    public NetworkSystems networkSystems() {
        return new NetworkSystems(this, client);
    }

    public ProtectionSystems protectionSystems() {
        return new ProtectionSystems(this, client);
    }

    @Deprecated
    public StorageProviders smisProviders() {
        return new StorageProviders(this, client);
    }

    public StorageProviders storageProviders() {
        return new StorageProviders(this, client);
    }

    public StoragePools storagePools() {
        return new StoragePools(this, client);
    }

    public StoragePorts storagePorts() {
        return new StoragePorts(this, client);
    }

    public StorageSystems storageSystems() {
        return new StorageSystems(this, client);
    }
    
    public VirtualNasServers virtualNasServers() {
        return new VirtualNasServers(this, client);
    }

    public StorageTiers storageTiers() {
        return new StorageTiers(this, client);
    }

    public UnManagedFileSystems unmanagedFileSystems() {
        return new UnManagedFileSystems(this, client);
    }

    public UnManagedVolumes unmanagedVolumes() {
        return new UnManagedVolumes(this, client);
    }

    public UnManagedExportMasks unmanagedExports() {
        return new UnManagedExportMasks(this, client);
    }

    public AuthnProviders authnProviders() {
        return new AuthnProviders(this, client);
    }

    public AutoTieringPolicies autoTierPolicies() {
        return new AutoTieringPolicies(this, client);
    }

    public Tenants tenants() {
        return new Tenants(this, client);
    }

    public Workflows workflows() {
        return new Workflows(this, client);
    }

    public VirtualDataCenter vdc() {
        return new VirtualDataCenter(client);
    }

    public Audit audit() {
        return new Audit(client);
    }

    public Monitoring monitoring() {
        return new Monitoring(client);
    }

    public Metering metering() {
        return new Metering(client);
    }

    public ComputeSystems computeSystems() {
        return new ComputeSystems(this, client);
    }

    public ComputeElements computeElements() {
        return new ComputeElements(this, client);
    }

    public Keystore keystore() {
        return new Keystore(client);
    }

    public Truststore truststore() {
        return new Truststore(client);
    }

    public TasksResources tasks() {
        return new TasksResources(client);
    }

    public CustomConfigs customConfigs() {
        return new CustomConfigs(client);
    }

    public UserGroup getUserGroup() {
        return new UserGroup(this, client);
    }
}
