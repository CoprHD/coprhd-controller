/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.vipr.model.keystore.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.core.filters.HostTypeFilter;

public class ViPRClientApp {

    private final static String TRUSTED_CERTIFICATE = "-----BEGIN CERTIFICATE-----\r\n"
            + "MIIE/zCCA+egAwIBAgIRAJ9si9NLc1lAY+R202n9/fowDQYJKoZIhvcNAQEFBQAw\r\n"
            + "gZcxCzAJBgNVBAYTAlVTMRYwFAYDVQQIEw1NYXNzYWNodXNldHRzMRAwDgYDVQQH\r\n"
            + "EwdCZWRmb3JkMRkwFwYDVQQKExBSU0EgU2VjdXJpdHkgTExDMSUwIwYDVQQLExxH\r\n"
            + "bG9iYWwgU2VjdXJpdHkgT3JnYW5pemF0aW9uMRwwGgYDVQQDExNSU0EgQ29ycG9y\r\n"
            + "YXRlIENBIHYyMB4XDTExMDMxMDIxNDA1N1oXDTE5MDIyODIxNTYzM1owgZ4xCzAJ\r\n"
            + "BgNVBAYTAlVTMRYwFAYDVQQIEw1NYXNzYWNodXNldHRzMRAwDgYDVQQHEwdCZWRm\r\n"
            + "b3JkMRkwFwYDVQQKExBSU0EgU2VjdXJpdHkgTExDMSUwIwYDVQQLExxHbG9iYWwg\r\n"
            + "U2VjdXJpdHkgT3JnYW5pemF0aW9uMSMwIQYDVQQDExpSU0EgQ29ycG9yYXRlIFNl\r\n"
            + "cnZlciBDQSB2MjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMlEfyTA\r\n"
            + "hnX8JlErtRFUAIougscUT91SFwxYsDoqjuw1jOQPASUPcJDq4Axjje8kHwSlcpeB\r\n"
            + "23lehX+yutvWBXKRsr4Exu2ObkSYkrli2dpgl+LpLVAEnZaOikZLjHzXIeH6O79u\r\n"
            + "UsB0JZbvQ9B3X5q2IFrjLiB55Mc1IBNJY/Ebr4OU/HkvxB3GWmqeHL9uH2yC15CE\r\n"
            + "5iM+Za83+nuGulthVguBSeQWyAodvAKW5BE9W4XoYpMYuIzL5haiOz0fvgf2PbGo\r\n"
            + "44EVhrN1sxyi9qGEslRy4poXGXD3WQltVbOk6QlssKBTG9wOcVIiXO0t6RyuzXIn\r\n"
            + "sGX8pV3csrJdsDECAwEAAaOCATswggE3MA8GA1UdEwQIMAYBAf8CAQIwgZEGA1Ud\r\n"
            + "IASBiTCBhjCBgwYJKoZIhvcNBQcCMHYwLgYIKwYBBQUHAgEWImh0dHA6Ly9jYS5y\r\n"
            + "c2FzZWN1cml0eS5jb20vQ1BTLmh0bWwwRAYIKwYBBQUHAgIwODAXFhBSU0EgU2Vj\r\n"
            + "dXJpdHkgTExDMAMCAQEaHUNQUyBJbmNvcnBvcmF0ZWQgYnkgcmVmZXJlbmNlMEAG\r\n"
            + "A1UdHwQ5MDcwNaAzoDGGL2h0dHA6Ly9jcmwucnNhc2VjdXJpdHkuY29tL1JTQUNv\r\n"
            + "cnBvcmF0ZUNBdjIuY3JsMA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUKfPCY9Px\r\n"
            + "9Qulv7Jd32EQlDTPRwwwHwYDVR0jBBgwFoAUcxs4SyXLWo69AuzfXSn2EHQO2Jgw\r\n"
            + "DQYJKoZIhvcNAQEFBQADggEBAB7jJkSi8fSAIWG9bqsNzC0/6F3Vsism5BizSxtU\r\n"
            + "X8nTRHaCzYOLY2PnjieySxqVOofsCKrnGQpIeax2Vre8UHvIhU9fhzj2+n4LbmfJ\r\n"
            + "GcWCGk75CKTn/tWc8jemllyT/5pSQOtt+Qw6LJ6+sprJtnQ7st/e+PzG8MkLjNVl\r\n"
            + "U7WIrxCns2ZEbqHO/easHZ3rMu3jG4RfNa44r6zrU58TPQ3y3Tnwbo3vRrOvVOTG\r\n"
            + "2zJiPPbNMuFlAKmc2TYhODc0aDFUtdeskbc/SKcb5PvlQesG8J2PkktKAhoTxeFj\r\n"
            + "pvsXSNCQ5DpPyB/uGozgI8tgoNjDm11O57DCxZFQ6qPsIwI=\r\n"
            + "-----END CERTIFICATE-----";

    private final ViPRCoreClient client;

    public ViPRClientApp(ViPRCoreClient client) {
        this.client = client;
    }

    public HostRestRep chooseHost(List<HostRestRep> hosts) {
        if (hosts.isEmpty()) {
            throw new IllegalArgumentException("No hosts");
        }
        return hosts.get(0);
    }

    public VirtualArrayRestRep chooseVirtualArray(List<VirtualArrayRestRep> virtualArrays) {
        if (virtualArrays.isEmpty()) {
            throw new IllegalArgumentException("No virtualArrays");
        }
        return virtualArrays.get(0);
    }

    public BlockVirtualPoolRestRep chooseVirtualPool(List<BlockVirtualPoolRestRep> virtualPools) {
        if (virtualPools.isEmpty()) {
            throw new IllegalArgumentException("No virtualPools");
        }
        return virtualPools.get(0);
    }

    public ProjectRestRep chooseProject(List<ProjectRestRep> projects) {
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("No projects");
        }
        return projects.get(0);
    }

    public static void main(String[] args) {
        Logger.getRootLogger().setLevel(Level.INFO);
        ViPRCoreClient client =
                new ViPRCoreClient("localhost", true).withLogin("root", "ChangeMe");
        try {
            ViPRClientApp application = new ViPRClientApp(client);

            application.updateTrustStore();

            application.createBlockVolumeForHost();

            application.changeKeyAndCert();
        } finally {
            client.auth().logout();
        }
    }

    private void updateTrustStore() {
        TruststoreSettings settings = client.truststore().getTruststoreSettings();
        TruststoreSettingsChanges settingsChanges = new TruststoreSettingsChanges();
        settingsChanges.setAcceptAllCertificates(!settings.isAcceptAllCertificates());
        TruststoreSettings newSettings =
                client.truststore().updateTruststoreSettings(settingsChanges);
        if (newSettings.isAcceptAllCertificates() == settings.isAcceptAllCertificates()) {
            throw new IllegalStateException("trust store settings were not changed");
        }
        System.out.println("truststore settings changed to acceptAllCertificates="
                + newSettings.isAcceptAllCertificates());
        List<TrustedCertificate> certs = client.truststore().getTrustedCertificates();
        TrustedCertificateChanges certsChanges = new TrustedCertificateChanges();
        List<String> changes = new ArrayList<String>();
        if (certs.isEmpty() || checkForCert(certs)) {
            changes.add(TRUSTED_CERTIFICATE);
            certsChanges.setAdd(changes);
            List<TrustedCertificate> newCerts =
                    client.truststore().updateTrustedCertificate(certsChanges);
            if (certs.size() + 1 != newCerts.size()) {
                throw new IllegalStateException("new cert was not added");
            }
        } else {
            changes.add(TRUSTED_CERTIFICATE);
            certsChanges.setRemove(changes);
            List<TrustedCertificate> newCerts =
                    client.truststore().updateTrustedCertificate(certsChanges);
            if (checkForCert(newCerts)) {
                throw new IllegalStateException("trusted cert was not removed");
            }
        }
    }

    private boolean checkForCert(List<TrustedCertificate> certs) {
        for (TrustedCertificate cert : certs) {
            if (removeNewLines(cert.getCertString()).equals(removeNewLines(TRUSTED_CERTIFICATE))) {
                return true;
            }
        }
        return false;
    }

    private void changeKeyAndCert() {
        CertificateChain chain = client.keystore().getCertificateChain();
        CertificateChain newChain = client.keystore().regenerateKeyAndCertificate();
        if (chain.getChain().equals(newChain.getChain())) {
            throw new IllegalStateException("Certificate hasn't changed");
        }
        System.out.println("key and certificate updated. New Certificate:"
                + newChain.getChain());
    }

    private String removeNewLines(String withNewLines) {
        return withNewLines.replaceAll("\n", "").replaceAll("\r", "");
    }

    public void createBlockVolumeForHost() {
        List<HostRestRep> hosts = client.hosts().getByUserTenant(HostTypeFilter.ESX.not());
        // User choice
        HostRestRep selectedHost = chooseHost(hosts);

        List<VirtualArrayRestRep> virtualArrays = client.varrays().findByConnectedHost(selectedHost);
        // User choice
        VirtualArrayRestRep selectedVirtualArray = chooseVirtualArray(virtualArrays);

        List<BlockVirtualPoolRestRep> virtualPools = client.blockVpools().getByVirtualArray(selectedVirtualArray.getId());
        // User choice
        BlockVirtualPoolRestRep selectedVirtualPool = chooseVirtualPool(virtualPools);

        List<ProjectRestRep> projects = client.projects().getByUserTenant();
        // User choice
        ProjectRestRep selectedProject = chooseProject(projects);

        URI volumeId = createVolume(selectedVirtualArray, selectedVirtualPool, selectedProject);

        List<ExportGroupRestRep> exports = new ArrayList<>();
        if (exports.isEmpty()) {
            createExport(volumeId, selectedHost, selectedVirtualArray, selectedProject);
        }
        else {
            addVolumeToExport(volumeId, exports.get(0));
        }
    }

    public URI createVolume(VirtualArrayRestRep virtualArray, BlockVirtualPoolRestRep virtualPool, ProjectRestRep project) {
        VolumeCreate input = new VolumeCreate();
        input.setName("SDSClientApp_Volume_" + System.currentTimeMillis());
        input.setVarray(virtualArray.getId());
        input.setVpool(virtualPool.getId());
        input.setSize("2GB");
        input.setCount(1);
        input.setProject(project.getId());

        Task<VolumeRestRep> task = client.blockVolumes().create(input).firstTask();
        VolumeRestRep volume = task.get();
        System.out.println("Created Volume: " + volume.getId());
        return volume.getId();
    }

    public URI createExport(URI volumeId, HostRestRep host, VirtualArrayRestRep virtualArray,
            ProjectRestRep project) {
        ExportCreateParam input = new ExportCreateParam();
        input.setName("SDSClientApp_Export");
        input.setType("Host");
        input.addHost(host.getId());
        input.setVarray(virtualArray.getId());
        input.addVolume(volumeId);
        input.setProject(project.getId());

        ExportGroupRestRep export = client.blockExports().create(input).get();
        System.out.println("Created Export Group: " + export.getId());
        return export.getId();
    }

    public void addVolumeToExport(URI volumeId, ExportGroupRestRep export) {
        ExportUpdateParam input = new ExportUpdateParam();
        input.addVolume(volumeId);
        client.blockExports().update(export.getId(), input);
    }
}
