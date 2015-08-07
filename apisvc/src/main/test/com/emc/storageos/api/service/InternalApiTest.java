/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.api.service.impl.resource.utils.InternalFileServiceClient;
import com.emc.storageos.api.service.impl.resource.utils.InternalNetworkClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.model.file.FileShareBulkRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.auth.*;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.helpers.ClientRequestHelper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.annotation.XmlRootElement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests internal api - requires some manual configuration
 * depends on device setup from sanity test
 */
@SuppressWarnings("deprecation")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:coordinatorclient-var.xml" })
public class InternalApiTest extends ApiTestBase {
    private String _server = "localhost";
    private String _apiServer = "https://" + _server + ":8443";
    private URI _cosId;
    private URI _nhId;
    private URI _networkId;
    private URI _rootTenantId;
    private String _rootToken;
    private InternalFileServiceClient _internalFileClient;
    private InternalNetworkClient _internalNetworkClient;
    private ClientRequestHelper _requestHelper;
    private Client _client;

    @Autowired
    private CoordinatorClientImpl _coordinatorClient;

    @XmlRootElement(name = "results")
    public static class Resources {
        public List<SearchResultResourceRep> resource;
    }

    @Before
    public void setup() throws Exception {
        _requestHelper = new ClientRequestHelper(_coordinatorClient);

        _client = _requestHelper.createClient();

        _internalFileClient = new InternalFileServiceClient();
        _internalFileClient.setCoordinatorClient(_coordinatorClient);
        _internalFileClient.setServer(_server);

        _internalNetworkClient = new InternalNetworkClient();
        _internalNetworkClient.setCoordinatorClient(_coordinatorClient);
        _internalNetworkClient.setServer(_server);

        List<String> urls = new ArrayList<String>();
        urls.add(_apiServer);
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, urls);
        TenantResponse tenantResp = rSys.path("/tenant")
                .get(TenantResponse.class);
        _rootTenantId = tenantResp.getTenant();
        _rootToken = (String) _savedTokens.get("root");

        // find a CoS to use
        Resources results = rSys.path("/file/vpools/search").queryParam("name", "cosisi")
                .get(Resources.class);
        Assert.assertTrue(results.resource.iterator().hasNext());
        _cosId = results.resource.iterator().next().getId();
        String cosAclUrl = "/file/vpools/" + _cosId.toString() + "/acl";
        ACLAssignmentChanges changes = new ACLAssignmentChanges();
        ACLEntry entry1 = new ACLEntry();
        entry1.setTenant(_rootTenantId.toString());
        entry1.getAces().add("USE");
        changes.getAdd().add(entry1);
        ClientResponse resp = rSys.path(cosAclUrl)
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        // find a nh to use
        results = rSys.path("/vdc/varrays/search").queryParam("name", "nh")
                .get(Resources.class);
        Assert.assertTrue(results.resource.iterator().hasNext());
        _nhId = results.resource.iterator().next().getId();
        String nhAclUrl = "/vdc/varrays/" + _nhId.toString() + "/acl";
        resp = rSys.path(nhAclUrl)
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());

        // find a network to use
        results = rSys.path("/vdc/networks/search").queryParam("name", "iptz")
                .get(Resources.class);
        Assert.assertTrue(results.resource.iterator().hasNext());
        _networkId = results.resource.iterator().next().getId();
    }

    @Test
    /**
     * This test exercises only the server side functionaly, not the internal client
     * @throws Exception
     */
    public void testInternalFileService() throws Exception {
        // create fs
        FileSystemParam fsparam = new FileSystemParam();
        fsparam.setVpool(_cosId);
        fsparam.setLabel("test-internalapi-" + System.currentTimeMillis());
        fsparam.setVarray(_nhId);
        fsparam.setSize("20971520");
        URI path = URI.create(_apiServer).resolve("/internal/file/filesystems");
        WebResource rRoot = _client.resource(path);
        WebResource.Builder rBuilder = _requestHelper.addSignature(rRoot);
        TaskResourceRep resp = _requestHelper.addToken(rBuilder, _rootToken)
                .post(TaskResourceRep.class, fsparam);
        Assert.assertTrue(resp != null);
        Assert.assertNotNull(resp.getOpId());
        Assert.assertNotNull(resp.getResource());
        String fsId = resp.getResource().getId().toString();
        String opId = resp.getOpId();
        // GET filesystem
        path = URI.create(_apiServer).resolve("/internal/file/filesystems/" + fsId);
        rRoot = _client.resource(path);
        rBuilder = _requestHelper.addSignature(rRoot);
        ClientResponse response = _requestHelper.addToken(rBuilder, _rootToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response != null);
        Assert.assertEquals(200, response.getStatus());
        // wait for the create to finish
        path = URI.create(_apiServer).resolve("/internal/file/filesystems/" + fsId + "/tasks/" + opId);
        int checkCount = 1200;
        String status;
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            rRoot = _client.resource(path);
            TaskResourceRep fsResp = _requestHelper.addSignature(rRoot)
                    .get(TaskResourceRep.class);
            status = fsResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare create timed out", false);
        }
        // export
        path = URI.create(_apiServer).resolve("/internal/file/filesystems/" + fsId + "/exports");
        FileSystemExportParam export = new FileSystemExportParam();
        export.setPermissions("root");
        export.setRootUserMapping("root");
        export.setProtocol("NFS");
        export.setEndpoints(new ArrayList<String>());
        export.getEndpoints().add("www.ford.com");
        rRoot = _client.resource(path);
        rBuilder = _requestHelper.addSignature(rRoot);
        resp = _requestHelper.addToken(rBuilder, _rootToken)
                .post(TaskResourceRep.class, export);
        opId = resp.getOpId();
        // wait for the export to finish
        path = URI.create(_apiServer).resolve("/internal/file/filesystems/" + fsId + "/tasks/" + opId);
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            rRoot = _client.resource(path);
            TaskResourceRep fsResp = _requestHelper.addSignature(rRoot)
                    .get(TaskResourceRep.class);
            status = fsResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare export timed out", false);
        }
        // unexport
        String unexportPath = String.format("/internal/file/filesystems/%s/exports/%s,%s,%s,%s",
                fsId, export.getProtocol(), export.getSecurityType(), export.getPermissions(), export.getRootUserMapping());
        path = URI.create(_apiServer).resolve(unexportPath);
        rRoot = _client.resource(path);
        rBuilder = _requestHelper.addSignature(rRoot);
        resp = _requestHelper.addToken(rBuilder, _rootToken)
                .delete(TaskResourceRep.class, export);
        opId = resp.getOpId();
        // wait for the unexport to finish
        path = URI.create(_apiServer).resolve("/internal/file/filesystems/" + fsId + "/tasks/" + opId);
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            rRoot = _client.resource(path);
            TaskResourceRep fsResp = _requestHelper.addSignature(rRoot)
                    .get(TaskResourceRep.class);
            status = fsResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare unexport timed out", false);
        }

        // delete
        path = URI.create(_apiServer).resolve("/internal/file/filesystems/" + fsId + "/deactivate");
        FileSystemDeleteParam deleteParam = new FileSystemDeleteParam();
        deleteParam.setForceDelete(false);
        rRoot = _client.resource(path);
        rBuilder = _requestHelper.addSignature(rRoot);
        resp = _requestHelper.addToken(rBuilder, _rootToken)
                .post(TaskResourceRep.class, deleteParam);
        Assert.assertTrue(resp != null);
    }

    @Test
    /**
     * This test exercises both server side and internal client
     * @throws Exception
     */
    public void testFileServiceUsingInternalClient() throws Exception {
        // create fs
        FileSystemParam fsparam = new FileSystemParam();
        fsparam.setVpool(_cosId);
        fsparam.setLabel("test-internalapi-" + System.currentTimeMillis());
        fsparam.setVarray(_nhId);
        fsparam.setSize("20971520");
        TaskResourceRep resp = _internalFileClient.createFileSystem(fsparam, _rootToken);
        Assert.assertTrue(resp != null);
        Assert.assertNotNull(resp.getOpId());
        Assert.assertNotNull(resp.getResource());

        URI fsId = resp.getResource().getId();
        String opId = resp.getOpId();
        // GET filesystem - no method on the client for this?

        WebResource rRoot = _requestHelper.createRequest(_client, _apiServer, "/internal/file/filesystems/" + fsId);
        WebResource.Builder rBuilder = _requestHelper.addSignature(rRoot);
        ClientResponse response = _requestHelper.addToken(rBuilder, _rootToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response != null);
        Assert.assertEquals(200, response.getStatus());

        // wait for the create to finish
        int checkCount = 1200;
        String status;
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            TaskResourceRep fsResp = _internalFileClient.getTaskStatus(fsId, opId);
            status = fsResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare create timed out", false);
        }
        // export
        FileSystemExportParam export = new FileSystemExportParam();
        export.setPermissions("root");
        export.setRootUserMapping("root");
        export.setProtocol("NFS");
        export.setEndpoints(new ArrayList<String>());
        export.getEndpoints().add("www.ford.com");
        resp = _internalFileClient.exportFileSystem(fsId, export);
        opId = resp.getOpId();
        // wait for the export to finish
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            TaskResourceRep fsResp = _internalFileClient.getTaskStatus(fsId, opId);
            status = fsResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare export timed out", false);
        }
        // unexport
        resp = _internalFileClient.unexportFileSystem(fsId, export.getProtocol(), export.getSecurityType(), export.getPermissions(),
                export.getRootUserMapping(), null);
        opId = resp.getOpId();
        // wait for the unexport to finish
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            TaskResourceRep fsResp = _internalFileClient.getTaskStatus(fsId, opId);
            status = fsResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare unexport timed out", false);
        }

        // delete
        FileSystemDeleteParam deleteParam = new FileSystemDeleteParam();
        deleteParam.setForceDelete(false);
        resp = _internalFileClient.deactivateFileSystem(fsId, _rootToken, deleteParam);
        Assert.assertTrue(resp != null);
    }

    @Test
    public void testFSReleaseUsingInternalClient() throws Exception {

        // get tenant
        TenantResponse tenant = rSys.path("/tenant").get(TenantResponse.class);
        Assert.assertNotNull(tenant);

        // create a project to host a normal file system
        ProjectParam projectParam = new ProjectParam();
        projectParam.setName("test-internalapi-" + System.currentTimeMillis());
        ProjectElement projectResp = rSys.path("/tenants/" + tenant.getTenant().toString() + "/projects")
                .post(ProjectElement.class, projectParam);
        Assert.assertNotNull(projectResp);

        // create a normal file system which we can then release
        FileSystemParam fsparam = new FileSystemParam();
        fsparam.setVpool(_cosId);
        fsparam.setLabel("test-internalapi-" + System.currentTimeMillis());
        fsparam.setVarray(_nhId);
        fsparam.setSize("20971520");
        TaskResourceRep taskResp = rSys.path("/file/filesystems").queryParam("project", projectResp.getId().toString())
                .post(TaskResourceRep.class, fsparam);
        Assert.assertTrue(taskResp != null);
        Assert.assertNotNull(taskResp.getOpId());
        Assert.assertNotNull(taskResp.getResource());
        URI fsId = taskResp.getResource().getId();
        String opId = taskResp.getOpId();

        // get the file system object we just created
        ClientResponse response = rSys.path("/file/filesystems/" + fsId.toString())
                .get(ClientResponse.class);
        Assert.assertTrue(response != null);
        Assert.assertEquals(200, response.getStatus());

        // wait for for the file system create to complete
        int checkCount = 1200;
        String status;
        do {
            // wait upto ~2 minute for fs creation
            Thread.sleep(100);
            taskResp = rSys.path("/file/filesystems/" + fsId + "/tasks/" + opId)
                    .get(TaskResourceRep.class);
            status = taskResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);
        if (!status.equals("ready")) {
            Assert.assertTrue("Fileshare create timed out", false);
        }

        // a normal file system should be present in the bulk results
        BulkIdParam bulkIds = rSys.path("/file/filesystems/bulk")
                .get(BulkIdParam.class);
        Assert.assertNotNull("bulk ids should not be null", bulkIds);
        FileShareBulkRep bulkFileShares = rSys.path("/file/filesystems/bulk")
                .post(FileShareBulkRep.class, bulkIds);
        Assert.assertNotNull("bulk response should not be null", bulkFileShares);
        boolean found = false;
        for (FileShareRestRep fs : bulkFileShares.getFileShares()) {
            if (fs.getId().equals(fsId)) {
                found = true;
            }
        }
        Assert.assertTrue("unable to find public FileShare in the bulk results", found);

        // only token is used in release file system operation and hence
        // setting dummy strings for username and tenant ID do not matter
        StorageOSUser user = new StorageOSUser("dummyUserName", "dummyTeneatId");
        user.setToken(_rootToken);
        FileShareRestRep fileShareResponse = _internalFileClient.releaseFileSystem(fsId, user);
        Assert.assertNotNull(fileShareResponse);

        // after release, the file system should no longer be present in the bulk results
        bulkFileShares = rSys.path("/file/filesystems/bulk")
                .post(FileShareBulkRep.class, bulkIds);
        Assert.assertNotNull("bulk response should not be null", bulkFileShares);
        found = false;
        for (FileShareRestRep fs : bulkFileShares.getFileShares()) {
            if (fs.getId().equals(fsId)) {
                found = true;
            }
        }
        Assert.assertFalse("found internal FileShare in the bulk results", found);

        // undo the release of the file system
        fileShareResponse = _internalFileClient.undoReleaseFileSystem(fsId);
        Assert.assertNotNull(fileShareResponse);

        // release it again
        fileShareResponse = _internalFileClient.releaseFileSystem(fsId, user);
        Assert.assertNotNull(fileShareResponse);

        // delete the file system via the internal api
        FileSystemDeleteParam deleteParam = new FileSystemDeleteParam();
        deleteParam.setForceDelete(false);
        taskResp = _internalFileClient.deactivateFileSystem(fsId, _rootToken, deleteParam);
        Assert.assertNotNull(taskResp);
    }

    @Test
    public void testNetworkUsingInternalClient() {
        // first add a new endpoint
        String newEndpoint = "www.networktest-" + System.currentTimeMillis() + ".com";
        NetworkEndpointParam endpointParam = new NetworkEndpointParam();
        endpointParam.setEndpoints(Arrays.asList(newEndpoint));
        endpointParam.setOp(NetworkEndpointParam.EndpointOp.add.name());
        NetworkRestRep network = _internalNetworkClient.updateNetworkEndpoints(_networkId, endpointParam);
        Assert.assertNotNull("update should not return a null response", network);
        Assert.assertTrue("response should contain the new endpoint " + newEndpoint, network.getEndpoints().contains(newEndpoint));

        // then remove it again
        endpointParam.setOp(NetworkEndpointParam.EndpointOp.remove.name());
        network = _internalNetworkClient.updateNetworkEndpoints(_networkId, endpointParam);
        Assert.assertNotNull("update should not return a null response", network);
        Assert.assertFalse("response should not contain the new endpoint " + newEndpoint, network.getEndpoints().contains(newEndpoint));
    }

    @After
    public void teardown() throws Exception {
        _internalFileClient.shutdown();
        _internalNetworkClient.shutdown();
        _client.destroy();
    }
}
