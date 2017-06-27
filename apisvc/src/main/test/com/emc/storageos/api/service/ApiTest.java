/*
 * Copyright (c) 2011-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.crypto.SecretKey;

import com.emc.storageos.services.util.EnvConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TagAssignment;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderList;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.auth.RoleAssignments;
import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.model.file.FileSystemReduceParam;
import com.emc.storageos.model.file.FileSystemSnapshotParam;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.project.ProjectUpdateParam;
import com.emc.storageos.model.search.Tags;
import com.emc.storageos.model.smis.SMISProviderCreateParam;
import com.emc.storageos.model.smis.SMISProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemList;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgList;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingChanges;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.model.user.UserInfo;
import com.emc.storageos.model.varray.BlockSettings;
import com.emc.storageos.model.varray.NetworkCreate;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.storageos.model.varray.VirtualArrayList;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterAddParam;
import com.emc.storageos.model.vdc.VirtualDataCenterList;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterSecretKeyRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.storageos.model.vpool.StoragePoolAssignmentChanges;
import com.emc.storageos.model.vpool.StoragePoolAssignments;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.storageos.security.SignatureHelper;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.resource.UserInfoPage.UserTenant;
import com.emc.storageos.security.resource.UserInfoPage.UserTenantList;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.keystore.CertificateChain;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;
import com.emc.vipr.model.keystore.RotateKeyAndCertParam;
import com.sun.jersey.api.client.ClientResponse;

/**
 * 
 * ApiTest class to exercise the core api functionality (tenants, AD, role assignments, isilon, volumes...)
 */
public class ApiTest extends ApiTestBase {

    @Before
    public void setUp() throws Exception {
        setupHttpsResources();
    }

    private static String STR144 = "abcdefghijklmnopqrstuvwxyz0123456789" +
            "abcdefghijklmnopqrstuvwxyz0123456789" + "abcdefghijklmnopqrstuvwxyz0123456789" +
            "abcdefghijklmnopqrstuvwxyz0123456789";
    private final int _maxRoleAclEntries = 100;
    private final static String RSA_KEY_2048 = "-----BEGIN RSA PRIVATE KEY-----\r\n"
            + "MIIEogIBAAKCAQEAt1uYybrxbA5ApP+ZWbxBLeZLn6tl0UWxB0M/uar7S38QWpk3\r\n"
            + "Qf+MuCJV9mRIP+LMeLJan2n7V+cmGn3nmNto22hQ9EqwSFnOCzhWf5GhOEuQ9J5v\r\n"
            + "tGyWLLg9434xF16DpjMvdGfC9P2ELPyWKhRTjJ1r7X2tDalH44SXKgNULIGLIT7m\r\n"
            + "q6QFRnxK3AXlX19g3D4kFdd5B1MmEXnSE9bJ35WA+9TBgy7/5+TrP6hoIYoo81YD\r\n"
            + "wxcyVsRy0jLE0ufDNU8D8VbHtnhJN5bO54uykCIY8ZHWYoxOCHFvDeZr/NqCpIP8\r\n"
            + "ONtJX/9Iqoc+VtHehZhED2BegbEKnq/19SvcewIDAQABAoIBAEPP/ovtNciO5N2h\r\n"
            + "ImgLtddx6tocm8VwDu7usizKzbG4RqYbMFKaXsLjAAPmRspJ6PFilR2MJsb12CPI\r\n"
            + "GNVxoDA1Pmt7DANWI1wG9AauJ4AYgn3V8t45orjbUxhF3YYVEH9xQsW8cmAFOtMg\r\n"
            + "f7EEX3oL5pSo/E2nI81DhlylaoYiqq7NdhXQSqdg6WBNcU72ieCSoRkxgIK2/GyO\r\n"
            + "+lGnUW/YNhDNwUxOXu4UdBUgqG740DeRhhKQ4jp/dNI5UbN/figBbQPGn0sxSYDR\r\n"
            + "bVUN9TpZpEdsGkrfQt8DbJADL+ULa8s9YRUMLmfFSDq/Pj40PI4e2QD61D5vYhRY\r\n"
            + "jnTeDcECgYEA3rRjeH9G2tSB6KLxHFMxLIrzz+MUUBa6d7c5HxVTYfp/g9+r89ad\r\n"
            + "HZI1xhbXYHk8paqlvlRsVWzWFjXowI3rXM2lpbnnymRhs0UV3KFV9eKoAYDHVG9B\r\n"
            + "RNrg8vcvA/uSbIcUOQZzedW9Looq4D+fOt63WYaGNlosjjAUsO5UnYsCgYEA0sVH\r\n"
            + "s62uIOv+eUcDqHL97W9twc/VPrzDh4K2Ez+5+nYFN6Irx+KxUxFlsh+T3CAFBjoI\r\n"
            + "BgR9JHzqCd8qb/NEKVHt97CVK5APOi8j9SRbGiW/OSoFqE0NltpP6eAXwM6nqbi0\r\n"
            + "pNvIy805YL4+/rN891HXGlGNO2KFOE97/3NpetECgYBxEfE26pgU3rQeYyw7j1l2\r\n"
            + "Hg5vzAEyMHf39ETCLVeqdT5svBFXue0HaIZ4znwHdUZ/bka8fayLKrj/idtkeCm5\r\n"
            + "cofZvquarKCWHktdO2SjdLKMINATZHEk/mQbt7hdM0tCYsq3sTjL8OMeT/Q46tRz\r\n"
            + "VUSN5akay2m3v12h/z9ixQKBgD/ZLWBb06z7cIoyngQQWaXspHYazIGF00GgsiFg\r\n"
            + "o6kgyXHR+atCm+8LDSCJelQrivoY6EdSYsqD4K3+4a8qJVLTE+B9qKKasFIy55Si\r\n"
            + "X8qq9qONfEtAlEZHef/iN6/bqmS6pFZwkgJS9/e7if/ERa3yJ9Q8Mil0LeEiCvEW\r\n"
            + "eDSRAoGAawKiSYC69vLi4yE7PVzT7aSbhv+qV2ZIHVBzbZ2Fka/0ZUw2ApXyOJ9X\r\n"
            + "NKnrSi/RNcA9OsZ1Tl6mhO2/LBw3piY6RIwBhG78f7c83At6SfYtR6rVYvrXJROg\r\n"
            + "VQ/xGSSBsagM/0k9ACZpM13eNazRAVzpn3FX1DaUByzIDFcfImk=\r\n"
            + "-----END RSA PRIVATE KEY-----";
    private final static String CERTIFICATE_2048 = "-----BEGIN CERTIFICATE-----\r\n"
            + "MIIDRzCCAi+gAwIBAgIIbG1mWcur1ZEwDQYJKoZIhvcNAQELBQAwHzEdMBsGA1UE\r\n"
            + "AxMUbGdsdzMxNTMubHNzLmVtYy5jb20wHhcNMTQwNTI3MTgzMTU4WhcNMjQwNTI0\r\n"
            + "MTgzMTU4WjAfMR0wGwYDVQQDExRsZ2x3MzE1My5sc3MuZW1jLmNvbTCCASIwDQYJ\r\n"
            + "KoZIhvcNAQEBBQADggEPADCCAQoCggEBALdbmMm68WwOQKT/mVm8QS3mS5+rZdFF\r\n"
            + "sQdDP7mq+0t/EFqZN0H/jLgiVfZkSD/izHiyWp9p+1fnJhp955jbaNtoUPRKsEhZ\r\n"
            + "zgs4Vn+RoThLkPSeb7Rsliy4PeN+MRdeg6YzL3RnwvT9hCz8lioUU4yda+19rQ2p\r\n"
            + "R+OElyoDVCyBiyE+5qukBUZ8StwF5V9fYNw+JBXXeQdTJhF50hPWyd+VgPvUwYMu\r\n"
            + "/+fk6z+oaCGKKPNWA8MXMlbEctIyxNLnwzVPA/FWx7Z4STeWzueLspAiGPGR1mKM\r\n"
            + "Tghxbw3ma/zagqSD/DjbSV//SKqHPlbR3oWYRA9gXoGxCp6v9fUr3HsCAwEAAaOB\r\n"
            + "hjCBgzAfBgNVHSMEGDAWgBSUgojM0wSrCa9lwGqW/x+9LHfPOjBBBgNVHREEOjA4\r\n"
            + "ghRsZ2x3MzE1My5sc3MuZW1jLmNvbYcECvdnmYIUbGdsdzMxNTIubHNzLmVtYy5j\r\n"
            + "b22HBAr3Z5gwHQYDVR0OBBYEFJSCiMzTBKsJr2XAapb/H70sd886MA0GCSqGSIb3\r\n"
            + "DQEBCwUAA4IBAQCo6XtB1kMXUt9WjoUmkrOZJfgfnps3jaB0N14wnO6EhdOXqHnm\r\n"
            + "CXFiTVAFcju/y4k2HqSlnkrgbcp3mjzY5CKUBBYTsi8MNgjL43+AkOvAOeDu3hen\r\n"
            + "+t5koeZ8dvpO39kR2u2eXsUBeaCo3ZKtOfDyrstiAtalrRDBWQl2IDO6AZ0ZjBy6\r\n"
            + "x9zn0sj9ahtPCSF6scG5UPyVKQzXREmDGLxXSxuKXlewljyMZwPDKH6GrhoO1yqo\r\n"
            + "6OOuAXPNzN5vuAUVj3Qjvs5lRWMrBMDH0XkjoEX2AAqs1pFfuAO17rgEnFO9iBgh\r\n"
            + "aSHrpqdkEwwx/9p/lDJ4wJDgDD/GrpLvl7s0\r\n" + "-----END CERTIFICATE-----";
    private static final String RSA_KEY_1024 = "-----BEGIN RSA PRIVATE KEY-----\r\n"
            + "MIICXAIBAAKBgQDJYd1wxB87o/tq+1MZdIzveINYXjPDUmgjT5ZCZlZjjV2HdG1w\r\n"
            + "h9Pp6Y6cav45c7BcvQq4fsmT5pZVzOG5U5cJTEdjPJ9nOJrh11gQJVenOXqGRI73\r\n"
            + "pZzhiG4uWNFV84skxU4ObPTsernN8kTrNQoygR8wTsZziwAaY2zhvyuuXQIDAQAB\r\n"
            + "AoGAa/s65s1yxeMO2/V5QIv7Sii/nPGeJdyZFF4HfwEqz2SswwYN7KoYWjOvEXZZ\r\n"
            + "bOr4pTGEfxsU8WZSNB2Q53PH5vMQ1B1/72QZUnLoKLOGR/EOX5RA1PhM6Ea5u18P\r\n"
            + "wzJ9TcvHsH1QIxEH0pep2qhWIl8D6JjdYaIliPynCGOONeECQQD2+fwAIUPZa5Wg\r\n"
            + "s3t/z6azFOlKdnITdkqm1B0erwl9ZgN5ZiS9P5BS30PNXxJ0EiZdXJzPcBIZFuY2\r\n"
            + "NJZAsvylAkEA0L1uAkdGroFYDp1xu3HJcO0JGkrO3Rs6W6G697damYgYB4pfTTng\r\n"
            + "pUd9FgLu4HLb6Y0muGpB8sXirb2aB1YlWQJBAMxUDZTd8JBUXbpSQ35+gV/vkQK1\r\n"
            + "87L+Tsyu+FiGX8eLOpyZURPxHqoxZJroaQ/2ZB8hm+pSweZX96Yo45YrfrECQFaE\r\n"
            + "HQNuvVn4nCG6mfgB6mcWp64xEVpNPbva5Z5kbXWzFZqSfHuKoJSAc9TatF1s3b8I\r\n"
            + "VOMcj2brI8+1BRFDYEkCQFbKUFyfFOjVZczK2NdbXSrO3iSTIZFOGETa9dxrchRP\r\n"
            + "/TXoqhkTuzu9y/E8QVQXBdCEXD72v5sn1kl1hd8Pgro=\r\n"
            + "-----END RSA PRIVATE KEY-----";
    private final static String CERTIFICATE_1024 = "-----BEGIN CERTIFICATE-----\r\n"
            + "MIICZDCCAc2gAwIBAgIJAKeFkwH41qufMA0GCSqGSIb3DQEBBQUAMB8xHTAbBgNV\r\n"
            + "BAMTFGxnbHc4MDk1Lmxzcy5lbWMuY29tMCAXDTcwMDEwMTAwMDAwMFoYDzIwNzAw\r\n"
            + "MTAxMDAwMDAwWjAfMR0wGwYDVQQDExRsZ2x3ODA5NS5sc3MuZW1jLmNvbTCBnzAN\r\n"
            + "BgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAyWHdcMQfO6P7avtTGXSM73iDWF4zw1Jo\r\n"
            + "I0+WQmZWY41dh3RtcIfT6emOnGr+OXOwXL0KuH7Jk+aWVczhuVOXCUxHYzyfZzia\r\n"
            + "4ddYECVXpzl6hkSO96Wc4YhuLljRVfOLJMVODmz07Hq5zfJE6zUKMoEfME7Gc4sA\r\n"
            + "GmNs4b8rrl0CAwEAAaOBpTCBojAdBgNVHQ4EFgQUnFPfhlGJixUjlDnxDPguQr5a\r\n"
            + "Ux0wTwYDVR0jBEgwRoAUnFPfhlGJixUjlDnxDPguQr5aUx2hI6QhMB8xHTAbBgNV\r\n"
            + "BAMTFGxnbHc4MDk1Lmxzcy5lbWMuY29tggkAp4WTAfjWq58wCQYDVR0SBAIwADAl\r\n"
            + "BgNVHREEHjAcghRsZ2x3ODA5NS5sc3MuZW1jLmNvbYcECvdiXzANBgkqhkiG9w0B\r\n"
            + "AQUFAAOBgQAxd5VQA31X01gXTNSUhXTQ6y7VWox7OCGAhINhtdpLBp54CL30W8oK\r\n"
            + "qQnIlpEP+GB4CJYfdSa8ltNUx4yRJjzG8QiPVkJV1b88Uba+gn4/xlHLLH3PqPDX\r\n"
            + "TzLGkt6+Prz+1w/ZAMKAXr6KAi4I0pnduqVRJ++GPmYBhPZre5auvw==\r\n"
            + "-----END CERTIFICATE-----";

    // right now, this only test one particular bad parameter (search filter).
    // We can enhance this to test out all the precheckConditions present in the AuthnConfigurationService
    private void addBadADConfig() throws NoSuchAlgorithmException {
        // Test that a config without a proper filter (key=%u) results in 400
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("ad apitest config bad");
        param.setDescription("ad configuration created by ApiTest.java");
        param.setDisable(false);
        param.getDomains().add("sanity2.local");
        param.setGroupAttribute("CN");
        param.setGroupWhitelistValues(new HashSet<String>());
        param.getGroupWhitelistValues().add("*Admins*");
        param.getGroupWhitelistValues().add("*Test*");
        param.getGroupWhitelistValues().add("*Users*");
        param.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        param.setManagerPassword(AD_PASS_WORD);
        param.setSearchBase("CN=Users,DC=sanity,DC=local");
        // %u is there but not on the right side of the "=". Adding this config should fail
        param.setSearchFilter("%u=userPrincipalName");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        param.setMode("ad");
        ClientResponse resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, param);
        Assert.assertEquals(400, resp.getStatus());

        // Test that adding two profiles with the same domain name results in 400
        String label = "ad apitest config duplicate 1";
        AuthnCreateParam duplicateConfig1 = new AuthnCreateParam();
        duplicateConfig1.setLabel(label);
        duplicateConfig1.setDescription("ad configuration created by ApiTest.java");
        duplicateConfig1.setDisable(false);
        duplicateConfig1.getDomains().add("mydomain.com");
        duplicateConfig1.setGroupAttribute("CN");
        duplicateConfig1.setGroupWhitelistValues(new HashSet<String>());
        duplicateConfig1.getGroupWhitelistValues().add("*Admins*");
        duplicateConfig1.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        duplicateConfig1.setManagerPassword(AD_PASS_WORD);
        duplicateConfig1.setSearchBase("CN=Users,DC=sanity,DC=local");
        duplicateConfig1.setSearchFilter("userPrincipalName=%u");
        duplicateConfig1.setServerUrls(new HashSet<String>());
        duplicateConfig1.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        duplicateConfig1.setMode("ad");
        AuthnProviderRestRep authnResp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, duplicateConfig1);
        Assert.assertNotNull(authnResp);
        URI firstCreatedConfig = authnResp.getId();

        AuthnCreateParam duplicateConfig2 = new AuthnCreateParam();
        duplicateConfig2.setLabel("ad apitest config duplicate 2");
        duplicateConfig2.setDescription("ad configuration created by ApiTest.java");
        duplicateConfig2.setDisable(false);
        duplicateConfig2.getDomains().add("mydomain.com");
        duplicateConfig2.setGroupAttribute("CN");
        duplicateConfig2.setGroupWhitelistValues(new HashSet<String>());
        duplicateConfig2.getGroupWhitelistValues().add("*Admins*");
        duplicateConfig2.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        duplicateConfig2.setManagerPassword(AD_PASS_WORD);
        duplicateConfig2.setSearchBase("CN=Users,DC=sanity,DC=local");
        duplicateConfig2.setSearchFilter("userPrincipalName=%u");
        duplicateConfig2.setServerUrls(new HashSet<String>());
        duplicateConfig2.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        duplicateConfig2.setMode("ad");
        resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, duplicateConfig2);
        Assert.assertEquals(400, resp.getStatus());

        // Test for duplicate name check (post)
        duplicateConfig2.setLabel(label);
        duplicateConfig2.getDomains().add("mydomain2.com");
        resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, duplicateConfig2);
        Assert.assertEquals(400, resp.getStatus());

        // Test that you cannot update an existing with a domain name that exists somewhere else
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.getDomainChanges().getAdd().add("sanity.local");
        String myDomainComauthnProvidersUrlFormat = String.format("/vdc/admin/authnproviders/%s",
                firstCreatedConfig.toString());
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParam);
        Assert.assertEquals(400, resp.getStatus());

        // test that updating the config with the same name as itself is fine (no op)
        AuthnUpdateParam updateParamSameName = new AuthnUpdateParam();
        updateParamSameName.getDomainChanges().getAdd().add("mydomain.com");
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParamSameName);
        Assert.assertEquals(200, resp.getStatus());

        // test that trying to update a config with a name too short causes 400
        AuthnUpdateParam updateParamNameTooShort = new AuthnUpdateParam();
        updateParamNameTooShort.setLabel("a");
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParamNameTooShort);
        Assert.assertEquals(400, resp.getStatus());

        // test that trying to update a config with a name too long causes 400
        AuthnUpdateParam updateParamNameTooLong = new AuthnUpdateParam();
        updateParamNameTooLong.setLabel("authn" + STR144);
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParamNameTooLong);
        Assert.assertEquals(400, resp.getStatus());

        // test that trying to update a config with the same name doesn't cause an error
        AuthnUpdateParam updateParam2 = new AuthnUpdateParam();
        updateParam2.setLabel(label);
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParam2);
        Assert.assertEquals(200, resp.getStatus());

        // test that the String payload will be trimmed
        updateParam2 = new AuthnUpdateParam();
        updateParam2.setLabel(" " + label + " ");
        authnResp = rSys.path(myDomainComauthnProvidersUrlFormat).put(AuthnProviderRestRep.class, updateParam2);
        Assert.assertTrue(authnResp.getName().equals(label));

        // Mark the mydomain.com provider as disabled. Try to add a conflicting domain provider.
        // Should still fail. Because even though disabled the provider can eventually be renabled.
        AuthnUpdateParam updateParam3 = new AuthnUpdateParam();
        updateParam3.setDisable(true);
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParam3);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).put(ClientResponse.class, updateParam);
        Assert.assertEquals(400, resp.getStatus());

        // Now delete that mydomain.com provider and re-add it, see that
        // it is now allowed because the conflicting provider has been deleted
        resp = rSys.path(myDomainComauthnProvidersUrlFormat).delete(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        authnResp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, duplicateConfig1);
        Assert.assertNotNull(authnResp);

        // Test that updating a config with a MaxPageSize=0 fails
        AuthnUpdateParam pageSizeUpdateParam = new AuthnUpdateParam();
        pageSizeUpdateParam.setMaxPageSize(0);
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", authnResp.getId().toString())).put(ClientResponse.class,
                pageSizeUpdateParam);
        Assert.assertEquals(400, resp.getStatus());

        // Set the page size and verify that it is successful.
        pageSizeUpdateParam.setMaxPageSize(500);
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", authnResp.getId().toString())).put(ClientResponse.class,
                pageSizeUpdateParam);
        Assert.assertEquals(200, resp.getStatus());

        // Get the provider and verify that it has the new page size
        authnResp = rSys.path(String.format("/vdc/admin/authnproviders/%s", authnResp.getId().toString())).get(AuthnProviderRestRep.class);
        Assert.assertEquals(pageSizeUpdateParam.getMaxPageSize().intValue(), authnResp.getMaxPageSize().intValue());
        // Test that a bad search scope gets rejected.
        // Missing scope is tested by all the other tests above which do not
        // supply scope.
        AuthnCreateParam badScopeParam = new AuthnCreateParam();
        badScopeParam.setLabel("ad apitest config with bad scope");
        badScopeParam.setDescription("ad configuration created by ApiTest.java");
        badScopeParam.setDisable(false);
        badScopeParam.getDomains().add("mydomain4.com");
        badScopeParam.setGroupAttribute("CN");
        badScopeParam.setGroupWhitelistValues(new HashSet<String>());
        badScopeParam.getGroupWhitelistValues().add("*Admins*");
        badScopeParam.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        badScopeParam.setManagerPassword(AD_PASS_WORD);
        badScopeParam.setSearchBase("CN=Users,DC=sanity,DC=local");
        badScopeParam.setSearchFilter("userPrincipalName=%u");
        badScopeParam.setServerUrls(new HashSet<String>());
        badScopeParam.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        badScopeParam.setSearchScope("bad scope"); // BAD SCOPE
        badScopeParam.setMode("ad");
        resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, badScopeParam);
        Assert.assertEquals(400, resp.getStatus());

        // Test that a good search scope works
        AuthnCreateParam goodScopeParam = new AuthnCreateParam();
        String goodScopeName = "ad apitest config with good scope";
        goodScopeParam.setLabel(goodScopeName);
        goodScopeParam.setDescription("ad configuration created by ApiTest.java");
        goodScopeParam.setDisable(false);
        goodScopeParam.getDomains().add("mydomain5.com");
        goodScopeParam.setGroupAttribute("CN");
        goodScopeParam.setGroupWhitelistValues(new HashSet<String>());
        goodScopeParam.getGroupWhitelistValues().add("*Admins*");
        goodScopeParam.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        goodScopeParam.setManagerPassword(AD_PASS_WORD);
        goodScopeParam.setSearchBase("CN=Users,DC=sanity,DC=local");
        goodScopeParam.setSearchFilter("userPrincipalName=%u");
        goodScopeParam.setServerUrls(new HashSet<String>());
        goodScopeParam.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        goodScopeParam.setSearchScope(AuthnProvider.SearchScope.SUBTREE.toString());
        goodScopeParam.setMode("ad");
        resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, goodScopeParam);
        Assert.assertEquals(200, resp.getStatus());

        // create a config, then try to modify its name to one that exists.
        AuthnCreateParam randomConfig = new AuthnCreateParam();
        randomConfig.setLabel("random");
        randomConfig.setDescription("random provider");
        randomConfig.setDisable(false);
        randomConfig.getDomains().add("mydomain6.com");
        randomConfig.setGroupAttribute("CN");
        randomConfig.setGroupWhitelistValues(new HashSet<String>());
        randomConfig.getGroupWhitelistValues().add("*Admins*");
        randomConfig.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        randomConfig.setManagerPassword(AD_PASS_WORD);
        randomConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        randomConfig.setSearchFilter("userPrincipalName=%u");
        randomConfig.setServerUrls(new HashSet<String>());
        randomConfig.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        randomConfig.setSearchScope(AuthnProvider.SearchScope.SUBTREE.toString());
        randomConfig.setMode("ad");
        AuthnProviderRestRep authnResp2 = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, randomConfig);
        Assert.assertNotNull(authnResp2);
        AuthnUpdateParam updateParam4 = new AuthnUpdateParam();
        updateParam4.setLabel(goodScopeName);
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", authnResp2.getId().toString()))
                .put(ClientResponse.class, updateParam4);
        Assert.assertEquals(400, resp.getStatus());

        // attempt to delete the only url in the config. should fail with 400
        AuthnUpdateParam lastUrl = new AuthnUpdateParam();
        lastUrl.getServerUrlChanges().setRemove(new HashSet<String>());
        lastUrl.getServerUrlChanges().getRemove().add("ldap:\\" + AD_SERVER1_IP);
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", _goodADConfig)).put(ClientResponse.class, lastUrl);
        Assert.assertEquals(400, resp.getStatus());

        // modify the main config with a bad group CN. Verify you get 400
        AuthnUpdateParam badCN = new AuthnUpdateParam();
        badCN.setGroupAttribute("garbage");
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", _goodADConfig)).
                queryParam("allow_group_attr_change", "true").put(ClientResponse.class, badCN);
        String errorMessage = String
                .format("The authentication provider could not be added or modified because of the following error: The group attribute %s could not be found in AD schema at server [%s].",
                        badCN.getGroupAttribute(), "ldap:\\" + AD_SERVER1_IP);
        assertExpectedError(resp, 400, ServiceCode.API_PARAMETER_INVALID, errorMessage);

        _savedTokens.remove(ROOTTENANTADMIN);
        // put the config back.
        AuthnUpdateParam goodCN = new AuthnUpdateParam();
        goodCN.setGroupAttribute("CN");
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", _goodADConfig)).queryParam("allow_group_attr_change", "true")
                .put(ClientResponse.class, goodCN);
        Assert.assertEquals(200, resp.getStatus());

        // modify the group attribute. Should fail.
        AuthnUpdateParam changeCN = new AuthnUpdateParam();
        changeCN.setGroupAttribute("objectSid");
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", _goodADConfig)).put(ClientResponse.class, changeCN);
        Assert.assertEquals(400, resp.getStatus());
        // modify the group attribute with force flag. Should succeed.
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", _goodADConfig)).queryParam("allow_group_attr_change", "true")
                .put(ClientResponse.class, changeCN);
        Assert.assertEquals(200, resp.getStatus());
        // put the original group attribute back for the rest of the tests.
        changeCN.setGroupAttribute("CN");
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", _goodADConfig)).queryParam("allow_group_attr_change", "true")
                .put(ClientResponse.class, changeCN);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void authProvidersConnectivityTests() {
        // Test that a config invalid server url results in 400
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("ad apitest config bad url");
        param.setDescription("ad apitest config bad url");
        param.setDisable(false);
        param.getDomains().add("domain1.com");
        param.setGroupAttribute("CN");
        param.setGroupWhitelistValues(new HashSet<String>());
        param.getGroupWhitelistValues().add("*Admins*");
        param.getGroupWhitelistValues().add("*Test*");
        param.getGroupWhitelistValues().add("*Users*");
        param.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        param.setManagerPassword(AD_PASS_WORD);
        param.setSearchBase("CN=Users,DC=sanity,DC=local");
        param.setSearchFilter("userPrincipalName=%u");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldap://" + EnvConfig.get("sanity", "ad.bogus.ip"));
        param.setMode("ad");
        ClientResponse resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, param);
        Assert.assertEquals(400, resp.getStatus());

        // Test that a config invalid manager DN results in 400
        param.setManagerDn("xxxxxministrator,CN=Users,DC=sanity,DC=local");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, param);
        Assert.assertEquals(400, resp.getStatus());

        // Test that a config invalid manager password results in 400
        param.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        param.setManagerPassword("bad");
        resp = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, param);
        Assert.assertEquals(400, resp.getStatus());

        // test that the same invalid config as above succeeds if disable is set to true
        // (validation skipped)
        param.setDisable(true);
        AuthnProviderRestRep authnResp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, param);
        Assert.assertNotNull(authnResp);

        // test that trying to enable that bad disabled config fails with 400
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.setDisable(false);
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", authnResp.getId().toString()))
                .put(ClientResponse.class, updateParam);
        Assert.assertEquals(400, resp.getStatus());

        // fix what was wrong (password), and disable = false from above, validation should rerun and be ok
        updateParam.setManagerPassword(AD_PASS_WORD);
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", authnResp.getId().toString()))
                .put(ClientResponse.class, updateParam);
        Assert.assertEquals(200, resp.getStatus());

        // test basic ldap mode connectivity
        AuthnCreateParam ldapParam = new AuthnCreateParam();
        ldapParam.setLabel("ldap connectivity test");
        ldapParam.setDescription("ldap connectivity test");
        ldapParam.setDisable(false);
        ldapParam.getDomains().add("domain22.com");
        ldapParam.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        ldapParam.setManagerPassword(AD_PASS_WORD);
        ldapParam.setSearchBase("CN=Users,DC=sanity,DC=local");
        ldapParam.setSearchFilter("userPrincipalName=%u");
        ldapParam.setServerUrls(new HashSet<String>());
        ldapParam.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        ldapParam.setGroupAttribute("CN");
        ldapParam.setMode("ldap");
        AuthnProviderRestRep goodAuthnResp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, ldapParam);
        Assert.assertNotNull(goodAuthnResp);

        // test that modifying the good config by adding one bad url still works. The good url that
        // is left in the set makes the url set valid.
        AuthnUpdateParam updateParamBadUrl = new AuthnUpdateParam();
        updateParamBadUrl.getServerUrlChanges().setAdd(new HashSet<String>());
        updateParamBadUrl.getServerUrlChanges().getAdd().add("ldap://garbage");
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", goodAuthnResp.getId().toString())).put(ClientResponse.class,
                updateParamBadUrl);
        Assert.assertEquals(200, resp.getStatus());

        // update the good config above with a bad search base which won't be found. Should fail.
        AuthnUpdateParam updateParamBadSearchBase = new AuthnUpdateParam();
        updateParamBadSearchBase.setSearchBase("CN=garbage");
        resp = rSys.path(String.format("/vdc/admin/authnproviders/%s", goodAuthnResp.getId().toString())).put(ClientResponse.class,
                updateParamBadSearchBase);
        Assert.assertEquals(400, resp.getStatus());
    }

    private void adConfigListTests() {
        AuthnProviderList resp = rSys.path("/vdc/admin/authnproviders").get(AuthnProviderList.class);
        int sz = resp.getProviders().size();

        // Add one more, then one with no name field. The new total should be sz + 2.
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("ad apitest config one");
        param.setDescription("ad configuration created by ApiTest.java");
        param.setDisable(false);
        param.getDomains().add("sanity3.local");
        param.setGroupAttribute("CN");
        param.setGroupWhitelistValues(new HashSet<String>());
        param.getGroupWhitelistValues().add("*Admins*");
        param.getGroupWhitelistValues().add("*Test*");
        param.getGroupWhitelistValues().add("*Users*");
        param.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        param.setManagerPassword(AD_PASS_WORD);
        param.setSearchBase("CN=Users,DC=sanity,DC=local");
        param.setSearchFilter("userPrincipalName=%u");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldap:\\" + AD_SERVER1_IP);
        param.getServerUrls().add("ldap:\\" + AD_SERVER1_HOST);
        param.setMode("ad");
        ClientResponse resp2 = rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class, param);
        Assert.assertEquals(200, resp2.getStatus());
        param.setLabel("ad apitest config two");
        param.getDomains().remove("sanity3.local");
        param.getDomains().add("another.com");
        AuthnProviderRestRep authnResp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, param);
        Assert.assertNotNull(authnResp);

        resp = rSys.path("/vdc/admin/authnproviders").get(AuthnProviderList.class);
        int sz2 = resp.getProviders().size();
        Assert.assertEquals(sz2, sz + 2);

        // update test
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.setLabel("ad apitest config two");
        updateParam.getDomainChanges().setRemove(new HashSet<String>());
        updateParam.getDomainChanges().getRemove().add("another.com");
        updateParam.getGroupWhitelistValueChanges().setRemove(new HashSet<String>());
        updateParam.getGroupWhitelistValueChanges().getRemove().add("*Admins*");
        updateParam.getGroupWhitelistValueChanges().getRemove().add("*Test*");
        updateParam.getGroupWhitelistValueChanges().getRemove().add("*Users*");
        updateParam.getServerUrlChanges().setRemove(new HashSet<String>());
        updateParam.getServerUrlChanges().getRemove().add("ldap:\\" + AD_SERVER1_HOST);

        AuthnProviderRestRep authnResp2 = rSys.path("/vdc/admin/authnproviders/" + authnResp.getId().toString() + "/")
                .put(AuthnProviderRestRep.class, updateParam);
        Assert.assertNotNull(authnResp2);
        Assert.assertEquals(0, authnResp2.getDomains().size());
        Assert.assertEquals(0, authnResp2.getGroupWhitelistValues().size());
        Assert.assertEquals(1, authnResp2.getServerUrls().size());
    }

    @Test
    public void HttpsTest() throws Exception {
        testAll();
    }

    private void proxyTokenTests() {
        // Login as root
        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        rootTenantId = tenantResp.getTenant();

        // Get a proxy token for root
        ClientResponse resp = rSys.path("/proxytoken").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        String proxyToken = (String) _savedProxyTokens.get("root");
        Assert.assertNotNull(proxyToken);

        // try to access tenant/id as proxy user. Does not work because proxy token was not passed in.
        // Proxy user by itself doesn't have TENANT_ADMIN.
        resp = rProxyUser.path("/tenants/" + rootTenantId.toString()).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        // try to access tenant/id as proxy user with proxy token this time.
        resp = rProxyUser.path("/tenants/" + rootTenantId.toString()).header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, proxyToken)
                .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // negative tests
        // proxy token, but a user without PROXY_USER role
        resp = rZAdmin.path("/tenants/" + rootTenantId.toString()).header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, proxyToken)
                .get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // check that the root when proxied does not have SECURITY_ADMIN in it.
        UserInfo info = rProxyUser.path("/user/whoami")
                .header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, proxyToken)
                .get(UserInfo.class);
        Assert.assertEquals("root", info.getCommonName());
        Assert.assertTrue(!info.getVdcRoles().contains(Role.SECURITY_ADMIN.toString()));

        // zone admin, when proxied, can not do role assignments
        resp = rZAdmin.path("/proxytoken").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String zAdminProxyToken = (String) _savedProxyTokens.get(ZONEADMIN);
        Assert.assertNotNull(zAdminProxyToken);
        resp = rProxyUser.path("/vdc/role-assignments")
                .header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, zAdminProxyToken)
                .put(ClientResponse.class, new RoleAssignmentChanges());

        Assert.assertEquals(403, resp.getStatus());

        // logout issuer of the proxy token with the force option. This should wipe out
        // all tokens including proxy tokens. Consequently, proxyuser should no longer be able
        // to access the tenants/id call with that proxy token anymore.
        // ( added .xml and used mixed cases to test that the logout filter forwards the request
        // appropriately)
        // resp = rSys.path("/loGout.XmL").queryParam("force", "true").get(ClientResponse.class);
        resp = rSys.path("/logout.xml").queryParam("force", "true").queryParam("proxytokens", "true").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rProxyUser.path("/tenants/" + rootTenantId.toString()).header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, proxyToken)
                .get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }

    private void logoutTests() throws Exception {
        BalancedWebResource rootUser = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
        ClientResponse resp = rootUser.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get("root"));
        resp = rootUser.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // logout normally
        resp = rootUser.path("/logout").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // logout again with an aleady logged out token
        resp = rootUser.path("/logout").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // logout again with a bad token
        _savedTokens.put("root", "invalid");
        resp = rootUser.path("/logout").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // login again, logout with .xml and mixed case
        _savedTokens.remove("root");
        resp = rootUser.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get("root"));
        resp = rootUser.path("/logOut.xMl").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // login again, logout with .json and mixed case
        _savedTokens.remove("root");
        resp = rootUser.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get("root"));
        resp = rootUser.path("/logOut.json").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        resp = rootUser.path("/logout.junk").get(ClientResponse.class);
        Assert.assertEquals(404, resp.getStatus());
    }

    private void testAll() throws Exception {

        adConfigTests();
        proxyTokenTests();
        userInfoTests();
        // uncomment the following line when CQ606655 has been fixed
        // logoutTests();
        tenantTests();
        testOtherBadParameterErrors();
        testOtherEntityNotFoundErrors();
        projectTests();
        usageAclTests();
        // commenting this out, since its becoming hard to keep up with the changes in controller area
        // we can revive these once we have stabilized the api changes in this area
        // projectResourceTests();
        testKeystore();
        testVDCSecretKey();
        prepareVdcTest();
        groupSuffixTest();
        disabledAuthnProviderTest();
        loneAuthnProviderDeleteTest();
        authnProviderAddDomainTest();
    }

    /**
     * 
     */
    private void testKeystore() {
        /*
         * GET THE CERTIFICATE CHAIN
         */
        // test with a security admin -should succeed
        ClientResponse response = rZAdmin.path("/vdc/keystore").get(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
        CertificateChain previousChain =
                rZAdmin.path("/vdc/keystore").get(CertificateChain.class);
        // test with a non-privileged user user -should succeed
        response = rRootUser2.path("/vdc/keystore").get(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());

        /*
         * REGENERATE THE KEY AND CERTIFICATE
         */
        // test with a non-privileged user -should fail
        RotateKeyAndCertParam rotateKeyAndCertParam = new RotateKeyAndCertParam();
        rotateKeyAndCertParam.setSystemSelfSigned(true);

        response = rRootUser2.path("/vdc/keystore").put(ClientResponse.class, rotateKeyAndCertParam);
        Assert.assertEquals(403, response.getStatus());
        // test with a security admin -should succeed
        CertificateChain currChain =
                rZAdmin.path("/vdc/keystore").put(CertificateChain.class, rotateKeyAndCertParam);
        Assert.assertNotSame(removeNewLines(previousChain.getChain()),
                removeNewLines(currChain.getChain()));

        waitForClusterToBeStable();

        previousChain = currChain;

        /*
         * SET THE KEY AND CERTIFICATE
         */
        // test with a non-privileged user -should fail
        rotateKeyAndCertParam.setSystemSelfSigned(false);

        KeyAndCertificateChain keyAndCertificateChain = new KeyAndCertificateChain();
        keyAndCertificateChain.setCertificateChain(CERTIFICATE_2048);
        keyAndCertificateChain.setPrivateKey(RSA_KEY_2048);

        rotateKeyAndCertParam.setKeyCertChain(keyAndCertificateChain);
        response =
                rRootUser2.path("/vdc/keystore").put(ClientResponse.class,
                        rotateKeyAndCertParam);
        Assert.assertEquals(403, response.getStatus());

        // test with a security admin -should succeed
        currChain =
                rZAdmin.path("/vdc/keystore").put(CertificateChain.class,
                        rotateKeyAndCertParam);
        Assert.assertNotSame(removeNewLines(previousChain.getChain()),
                removeNewLines(currChain.getChain()));

        waitForClusterToBeStable();

        // test with the same key and certificate - should fail
        String expectedError =
                "The specified certificate is already being used. Please specify a new key and certificate pair.";
        response =
                rZAdmin.path("/vdc/keystore").put(ClientResponse.class,
                        rotateKeyAndCertParam);
        assertExpectedError(response, 400, ServiceCode.API_BAD_REQUEST,
                expectedError);

        // test with a mismatched key and certificate
        keyAndCertificateChain.setPrivateKey(RSA_KEY_2048);
        keyAndCertificateChain.setCertificateChain(CERTIFICATE_1024);

        rotateKeyAndCertParam.setKeyCertChain(keyAndCertificateChain);
        response =
                rZAdmin.path("/vdc/keystore").put(ClientResponse.class,
                        rotateKeyAndCertParam);
        expectedError = "The provided key and certificate do not match";
        assertExpectedError(response, 400, ServiceCode.API_PARAMETER_INVALID,
                expectedError);

        // test with bad key
        keyAndCertificateChain = new KeyAndCertificateChain();
        keyAndCertificateChain.setCertificateChain(CERTIFICATE_1024);
        keyAndCertificateChain.setPrivateKey("this is a bad key");
        rotateKeyAndCertParam.setKeyCertChain(keyAndCertificateChain);
        response =
                rZAdmin.path("/vdc/keystore").put(ClientResponse.class,
                        rotateKeyAndCertParam);
        expectedError = "Failed to load the private key.";
        assertExpectedError(response, 400, ServiceCode.API_PARAMETER_INVALID,
                expectedError);

        // test with bad certificate
        keyAndCertificateChain = new KeyAndCertificateChain();
        String badCert = "this is a bad certificate";
        keyAndCertificateChain.setCertificateChain(badCert);
        keyAndCertificateChain.setPrivateKey(RSA_KEY_1024);
        rotateKeyAndCertParam.setKeyCertChain(keyAndCertificateChain);
        response =
                rZAdmin.path("/vdc/keystore").put(ClientResponse.class,
                        rotateKeyAndCertParam);
        expectedError = "Failed to load the following certificate(s): " + badCert;
        assertExpectedError(response, 400, ServiceCode.API_PARAMETER_INVALID,
                expectedError);

        // test with a key that's less than 2048 bits long
        keyAndCertificateChain = new KeyAndCertificateChain();
        keyAndCertificateChain.setCertificateChain(CERTIFICATE_1024);
        keyAndCertificateChain.setPrivateKey(RSA_KEY_1024);
        rotateKeyAndCertParam.setKeyCertChain(keyAndCertificateChain);
        response =
                rZAdmin.path("/vdc/keystore").put(ClientResponse.class,
                        rotateKeyAndCertParam);
        expectedError =
                "Invalid parameter private_key was 1,024bits but minimum is 2,048bits";
        assertExpectedError(response, 400, ServiceCode.API_PARAMETER_INVALID_RANGE,
                expectedError);

    }

    /**
     * @param chain
     * @return
     */
    private String removeNewLines(String chain) {
        return chain.replaceAll("\n", "").replaceAll("\r", "");
    }

    /**
     * UserInfo
     * 
     * @throws Exception
     */
    private void userInfoTests() throws Exception {
        UserInfo info = rSys.path("/user/whoami").get(UserInfo.class);
        Assert.assertEquals(SYSADMIN, info.getCommonName());
        Assert.assertEquals(4, info.getVdcRoles().size());  // no tenant role since 2.0

        // check the root user's default vdc roles.
        userInfoCheckRoles(rSys, new ArrayList<String>(Arrays.asList("SECURITY_ADMIN", "SYSTEM_ADMIN",
                "SYSTEM_MONITOR", "SYSTEM_AUDITOR")));

        info = rZAdmin.path("/user/whoami").get(UserInfo.class);
        Assert.assertEquals(ZONEADMIN, info.getCommonName());
        Assert.assertEquals(0, info.getVdcRoles().size());
    }

    /**
     * Checks if the user passed in has all the roles in the provided roles list using the whoami api.
     * 
     * @throws Exception
     */
    private void userInfoCheckRoles(BalancedWebResource user, List<String> roles) throws Exception {
        UserInfo info = user.path("/user/whoami").get(UserInfo.class);
        // since 2.0, tenant role in home tenant roles.
        List<String> allRoles = new ArrayList<>();
        allRoles.addAll(info.getHomeTenantRoles());
        allRoles.addAll(info.getVdcRoles());
        Assert.assertTrue(allRoles.containsAll(roles));
    }

    private void adConfigTests() throws Exception {
        addBadADConfig();
        adConfigListTests();
        authProvidersConnectivityTests();
    }

    /**
     * tenant api tests
     * 
     * @throws Exception
     */
    private void tenantTests() throws Exception {

        /*
         * GET MY TENANT ID
         */
        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        rootTenantId = tenantResp.getTenant();

        /*
         * GET root tenant info
         */
        ClientResponse resp = rUnAuth.path("/tenants/" + rootTenantId.toString()).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        TenantOrgRestRep tenant = rSys.path("/tenants/" + rootTenantId.toString()).get(TenantOrgRestRep.class);
        Assert.assertTrue(tenant != null);
        Assert.assertTrue(tenant.getId().equals(rootTenantId));
        Assert.assertFalse(tenant.getUserMappings().isEmpty());
        // ensure the tenent org name is the same name as the tenant and the tenant's link points to
        // the appropriate refs
        Assert.assertTrue(tenant.getName().equals(tenantResp.getName()));
        Assert.assertTrue(("/tenants/" + tenant.getId()).equals(tenantResp.getSelfLink().getLinkRef().toString()));

        // Remove the mapping and add a domain only mapping to make sure it works
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(tenant.getUserMappings());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootDomainMapping = new UserMappingParam();
        rootDomainMapping.setDomain("sanity.local");
        tenantUpdate.getUserMappingChanges().getAdd().add(rootDomainMapping);
        tenantUpdate.setLabel(ROOTTENANT_NAME);

        String rootTenantBaseUrl = "/tenants/" + rootTenantId.toString();

        resp = rSys.path(rootTenantBaseUrl).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
        tenant = rSys.path(rootTenantBaseUrl).get(TenantOrgRestRep.class);
        Assert.assertFalse(tenant.getUserMappings().isEmpty());

        // as sysmonitor, verify we can access tenant quota
        resp = rMon.path(rootTenantBaseUrl + "/quota").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // try to delete the auth provider that has the sanity.local domain.
        // should fail with 400.
        resp = rSys.path("/vdc/admin/authnproviders/" + _goodADConfig.toString()).delete(ClientResponse.class);
        Assert.assertEquals(400, resp.getStatus());

        // try to add another domain to that provider and remove the sanity.local domain which is used. Should fail.
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.getDomainChanges().getAdd().add("someotherdomain2.com");
        updateParam.getDomainChanges().getRemove().add("sanity.local");
        resp = rSys.path("/vdc/admin/authnproviders/" + _goodADConfig.toString()).put(ClientResponse.class, updateParam);
        Assert.assertEquals(400, resp.getStatus());

        // Make sure that all mappings can be cleared from root
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(tenant.getUserMappings());
        resp = rSys.path(rootTenantBaseUrl).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
        tenant = rSys.path(rootTenantBaseUrl).get(TenantOrgRestRep.class);
        Assert.assertEquals(0, tenant.getUserMappings().size());

        // test that updating the tenant with its own name doesn't cause problems (no op)
        TenantUpdateParam tenantUpdateNameOnly = new TenantUpdateParam();
        tenantUpdateNameOnly.setLabel(tenant.getName());
        resp = rSys.path(rootTenantBaseUrl).put(ClientResponse.class, tenantUpdateNameOnly);
        Assert.assertEquals(200, resp.getStatus());

        // test adding attribute with a value, then modifying that value (add/remove in same call)
        // jira 4220 had a problem, this would return 400
        // start by adding an attribute
        tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        rootDomainMapping = new UserMappingParam();
        rootDomainMapping.setDomain("sanity.local");
        UserMappingAttributeParam rootAttr = new UserMappingAttributeParam();
        rootAttr.setKey("ou");
        rootAttr.setValues(Collections.singletonList("attri1"));
        rootDomainMapping.setAttributes(Collections.singletonList(rootAttr));
        tenantUpdate.getUserMappingChanges().getAdd().add(rootDomainMapping);
        tenantUpdate.setLabel(ROOTTENANT_NAME);
        resp = rSys.path(rootTenantBaseUrl).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
        // now modify (add and remove) this attribute
        tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(new ArrayList<UserMappingParam>());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        rootDomainMapping = new UserMappingParam();
        rootDomainMapping.setDomain("sanity.local");
        rootAttr = new UserMappingAttributeParam();
        rootAttr.setKey("ou");
        rootAttr.setValues(Collections.singletonList("attri1"));
        rootDomainMapping.setAttributes(Collections.singletonList(rootAttr));
        tenantUpdate.getUserMappingChanges().getRemove().add(rootDomainMapping);
        UserMappingParam rootDomainMapping2 = new UserMappingParam();
        rootDomainMapping2.setDomain("sanity.local");
        rootAttr = new UserMappingAttributeParam();
        rootAttr.setKey("ou");
        rootAttr.setValues(Collections.singletonList("attri1b"));
        rootDomainMapping2.setAttributes(Collections.singletonList(rootAttr));
        tenantUpdate.getUserMappingChanges().getAdd().add(rootDomainMapping2);
        tenantUpdate.setLabel(ROOTTENANT_NAME);
        resp = rSys.path(rootTenantBaseUrl).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
        // remove what we did
        tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(new ArrayList<UserMappingParam>());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        rootDomainMapping = new UserMappingParam();
        rootDomainMapping.setDomain("sanity.local");
        rootAttr = new UserMappingAttributeParam();
        rootAttr.setKey("ou");
        rootAttr.setValues(Collections.singletonList("attri1b"));
        rootDomainMapping.setAttributes(Collections.singletonList(rootAttr));
        tenantUpdate.getUserMappingChanges().getRemove().add(rootDomainMapping);
        tenantUpdate.setLabel(ROOTTENANT_NAME);
        resp = rSys.path(rootTenantBaseUrl).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());

        // put the mapping back
        updateRootTenantAttrs();

        /*
         * GET, PUT Zone Roles
         */
        RoleAssignments assignments = rSys.path("/vdc/role-assignments").
                get(RoleAssignments.class);
        Assert.assertTrue(assignments.getAssignments().isEmpty());
        // full update - bad role
        RoleAssignmentChanges changes = new RoleAssignmentChanges();
        RoleAssignmentEntry entry1 = new RoleAssignmentEntry();
        entry1.setSubjectId(ZONEADMIN);
        entry1.getRoles().add("SECURITY_ADMIN");
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry1);
        RoleAssignmentEntry entry2 = new RoleAssignmentEntry();
        entry2.setSubjectId(ROOTUSER2);
        entry2.getRoles().add("SYSTEM_ADMIN");
        changes.getAdd().add(entry2);
        RoleAssignmentEntry entry_bad = new RoleAssignmentEntry();
        entry_bad.setSubjectId(ZONEADMIN);
        entry_bad.getRoles().add("INVALID_ROLE");
        changes.getAdd().add(entry_bad);
        resp = rSys.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        changes.getAdd().remove(2);
        entry_bad.setSubjectId("bad");
        entry_bad.getRoles().add("SECURITY_ADMIN");
        changes.getAdd().add(entry_bad);
        resp = rSys.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        changes.getAdd().remove(2);

        // all good
        assignments = rSys.path("/vdc/role-assignments").put(RoleAssignments.class, changes);
        Assert.assertEquals(assignments.getAssignments().size(), 2);
        RoleAssignments readAssignments =
                rSys.path("/vdc/role-assignments").get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(), readAssignments.getAssignments()));

        // check with whoami, that zadmin at this point, has security_admin
        userInfoCheckRoles(rZAdmin, new ArrayList<String>(Collections.singletonList("SECURITY_ADMIN")));

        RoleAssignmentEntry entry3 = new RoleAssignmentEntry();
        entry3.setSubjectId(ZONEADMIN);
        entry3.getRoles().add("SYSTEM_ADMIN");

        RoleAssignmentEntry entry4 = new RoleAssignmentEntry();
        entry4.setGroup(ZONEADMINS_GROUP);
        entry4.getRoles().add("SYSTEM_ADMIN");
        entry4.getRoles().add("TENANT_ADMIN");
        // partial update
        changes = new RoleAssignmentChanges();

        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().add(entry2);
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry3);
        changes.getAdd().add(entry4);
        resp = rZAdmin.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        changes.getAdd().remove(entry4);
        entry4.getRoles().remove("TENANT_ADMIN");
        changes.getAdd().add(entry4);
        readAssignments = rZAdmin.path("/vdc/role-assignments").put(RoleAssignments.class, changes);
        assignments = new RoleAssignments();
        entry3.getRoles().add("SECURITY_ADMIN");
        assignments.getAssignments().add(entry3);
        assignments.getAssignments().add(entry4);
        Assert.assertTrue(checkEqualsRoles(assignments.getAssignments(), readAssignments.getAssignments()));
        // try an update with missing role in the entry
        RoleAssignmentEntry entry4b = new RoleAssignmentEntry();
        entry4b.setGroup(ZONEADMINS_GROUP);
        changes.getAdd().add(entry4b);
        resp = rZAdmin.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());

        // try to modify zone roles for local users, make sure that fails with 400
        RoleAssignmentChanges changes2 = new RoleAssignmentChanges();
        RoleAssignmentEntry rootTenantAdminUserEntry = new RoleAssignmentEntry();
        rootTenantAdminUserEntry.setSubjectId(SYSADMIN);
        rootTenantAdminUserEntry.getRoles().add("SECURITY_ADMIN");
        changes2.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes2.getAdd().add(rootTenantAdminUserEntry);
        resp = rSys.path("/vdc/role-assignments").put(ClientResponse.class, changes2);
        Assert.assertEquals(400, resp.getStatus());
        changes2 = new RoleAssignmentChanges();
        changes2.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes2.getRemove().add(rootTenantAdminUserEntry);
        resp = rSys.path("/vdc/role-assignments").put(ClientResponse.class, changes2);
        Assert.assertEquals(400, resp.getStatus());

        /*
         * GET/PUT/POST tenant roles
         */
        String roles_url_format = "/tenants/%s/role-assignments";
        assignments = rZAdmin.path(String.format(roles_url_format, rootTenantId.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(assignments.getAssignments().size() == 1);

        // - bad role
        entry1 = new RoleAssignmentEntry();
        entry1.setSubjectId(ROOTTENANTADMIN_FORASSIGNMENT);
        entry1.getRoles().add("TENANT_ADMIN");
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry1);
        entry2 = new RoleAssignmentEntry();
        entry2.setSubjectId(ROOTUSER2);
        entry2.getRoles().add("TENANT_ADMIN");
        changes.getAdd().add(entry2);
        entry_bad = new RoleAssignmentEntry();
        entry_bad.setSubjectId("bad");
        entry_bad.getRoles().add("INVALID_ROLE");
        changes.getAdd().add(entry_bad);
        resp = rSys.path(String.format(roles_url_format, rootTenantId.toString()))
                .put(ClientResponse.class, assignments);
        Assert.assertEquals(400, resp.getStatus());
        changes.getAdd().remove(2);

        // zone system admin can not do tenant admin stuff
        resp = rZAdminGr.path(String.format(roles_url_format, rootTenantId.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(403, resp.getStatus());
        // - all good
        // this will remove sysadmin's tenant_admin role on root
        rootTenantAdminUserEntry = new RoleAssignmentEntry();
        rootTenantAdminUserEntry.setSubjectId(SYSADMIN);
        rootTenantAdminUserEntry.getRoles().add("TENANT_ADMIN");
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().add(rootTenantAdminUserEntry);
        readAssignments = rZAdmin.path(String.format(roles_url_format, rootTenantId.toString()))
                .put(RoleAssignments.class, changes);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(), readAssignments.getAssignments()));

        // check with whoami, that root user2 at this point, has tenant_admin
        userInfoCheckRoles(rRootUser2, new ArrayList<String>(Collections.singletonList("TENANT_ADMIN")));

        // partial update
        resp = rTAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        RoleAssignmentEntry entry3t = new RoleAssignmentEntry();
        entry3t.setSubjectId(ROOTTENANTADMIN_FORASSIGNMENT);
        entry3t.getRoles().add("PROJECT_ADMIN");
        RoleAssignmentEntry entry4t = new RoleAssignmentEntry();
        entry4t.setGroup(TENANT_ADMINS_GROUP);
        entry4t.getRoles().add("TENANT_ADMIN");
        changes = new RoleAssignmentChanges();
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().add(entry2);
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry3t);
        changes.getAdd().add(entry4t);
        readAssignments = rTAdmin.path(String.format(roles_url_format, rootTenantId.toString()))
                .put(RoleAssignments.class, changes);
        assignments = new RoleAssignments();
        entry3t.getRoles().add("TENANT_ADMIN");
        assignments.getAssignments().add(entry3t);
        assignments.getAssignments().add(entry4t);
        Assert.assertTrue(checkEqualsRoles(assignments.getAssignments(), readAssignments.getAssignments()));

        // verify Tenant Admin permission for admins on root tenant
        readAssignments = rTAdminGr.path(String.format(roles_url_format, rootTenantId.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(assignments.getAssignments(), readAssignments.getAssignments()));

        RoleAssignments vdcReadAssignments = rZAdmin.path("/vdc/role-assignments").get(
                RoleAssignments.class);

        // try to add more than 100 roles - this should fail (quickly, because
        // it's not validating)
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());

        // since 2.0, should exclude the vdc roles here since they are in NOT in tenant db.
        int currRolesCount = readAssignments.getAssignments().size();
        int rolesToAdd = _maxRoleAclEntries + 1 - currRolesCount;
        for (int i = 0; i < rolesToAdd; i++) {
            entry_bad = new RoleAssignmentEntry();
            entry_bad.setRoles(new ArrayList<String>());
            entry_bad.getRoles().add(Role.TENANT_ADMIN.toString());
            entry_bad.setSubjectId("invalidUser" + i + "@invalidDomain.com");
            changes.getAdd().add(entry_bad);
        }

        resp = rTAdminGr.path(String.format(roles_url_format, rootTenantId.toString()))
                .put(ClientResponse.class, changes);

        final String message = String.format(
                "Exceeding limit of %d role assignments with %d", _maxRoleAclEntries,
                _maxRoleAclEntries + 1);
        assertExpectedError(resp, 400, ServiceCode.API_EXCEEDING_ASSIGNMENT_LIMIT,
                message);

        // verify zone roles here, to test out role filtering code
        assignments = new RoleAssignments();
        assignments.getAssignments().add(entry3);
        assignments.getAssignments().add(entry4);
        readAssignments = rZAdmin.path("/vdc/role-assignments").get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(assignments.getAssignments(), readAssignments.getAssignments()));

        /*
         * Before creating subtenant try to log in
         * as subtenant user and verify that it fails with
         * 403 because the user does not map to a tenant
         */

        resp = rSTAdmin1.path("/login").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        /*
         * CREATE subtenants
         */
        String subtenant_url = rootTenantBaseUrl + "/subtenants";
        TenantCreateParam tenantParam = new TenantCreateParam();
        String subtenant1_label = "subtenant1";
        tenantParam.setLabel(subtenant1_label);
        tenantParam.setDescription("first subtenant");
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());
        UserMappingParam tenantMapping1 = new UserMappingParam();
        tenantMapping1.setDomain("bad_domain.com");
        // add user mapping with domain only. Should fail because it conflicts with an existing mapping
        resp = rTAdminGr.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        tenantMapping1.setDomain("sanity.LOCAL");
        // add user mapping with domain only. Should fail because it conflicts with an existing mapping
        resp = rTAdminGr.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        // Add an attribute scope to the mapping
        UserMappingAttributeParam tenantAttr = new UserMappingAttributeParam();
        tenantAttr.setKey("departMent");
        tenantAttr.setValues(Collections.singletonList(SUBTENANT1_ATTR));
        tenantMapping1.setAttributes(Collections.singletonList(tenantAttr));

        // create a second mapping with no domain
        // should fail to add it
        UserMappingAttributeParam tenantAttr2 = new UserMappingAttributeParam();
        tenantAttr2.setKey("Company");
        tenantAttr2.setValues(Collections.singletonList(SUBTENANT1_ATTR));
        UserMappingParam tenantMapping2 = new UserMappingParam();
        tenantMapping2.setAttributes(Collections.singletonList(tenantAttr2));
        tenantParam.getUserMappings().add(tenantMapping1);
        tenantParam.getUserMappings().add(tenantMapping2);
        resp = rTAdminGr.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());
        // Add the domain to the second mapping
        tenantMapping2.setDomain("Sanity.Local");

        // Should fail with 403
        resp = rSys.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(403, resp.getStatus());

        // Add the mappings
        TenantOrgRestRep subtenant1 = rTAdminGr.path(subtenant_url).post(TenantOrgRestRep.class, tenantParam);
        Assert.assertTrue(subtenant1.getName().equals(subtenant1_label));
        Assert.assertEquals(2, subtenant1.getUserMappings().size());
        for (UserMappingParam mapping : subtenant1.getUserMappings()) {
            Assert.assertEquals(1, mapping.getAttributes().size());
            UserMappingAttributeParam attribute = mapping.getAttributes().get(0);
            if (attribute.getKey().equalsIgnoreCase("department") || attribute.getKey().equalsIgnoreCase("company")) {
                Assert.assertEquals(1, attribute.getValues().size());
                Assert.assertEquals(SUBTENANT1_ATTR, attribute.getValues().get(0));
            } else {
                Assert.fail("Attribute key unexpected " + attribute.getKey());
            }
        }
        subtenant1Id = subtenant1.getId();

        // Try to remove all of the mappings
        // should fail for non-root tenant
        tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(new ArrayList<UserMappingParam>());
        tenantUpdate.getUserMappingChanges().getRemove().add(tenantMapping1);
        tenantUpdate.getUserMappingChanges().getRemove().add(tenantMapping2);

        resp = rTAdminGr.path("/tenants/" + subtenant1Id.toString()).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(400, resp.getStatus());

        subtenant1 = rTAdminGr.path("/tenants/" + subtenant1Id.toString()).get(TenantOrgRestRep.class);
        Assert.assertTrue(subtenant1.getId().equals(subtenant1Id));
        Assert.assertTrue(subtenant1.getName().equals(subtenant1_label));
        Assert.assertEquals(2, subtenant1.getUserMappings().size());
        for (UserMappingParam mapping : subtenant1.getUserMappings()) {
            Assert.assertEquals(1, mapping.getAttributes().size());
            UserMappingAttributeParam attribute = mapping.getAttributes().get(0);
            if (attribute.getKey().equalsIgnoreCase("department") || attribute.getKey().equalsIgnoreCase("company")) {
                Assert.assertEquals(1, attribute.getValues().size());
                Assert.assertEquals(SUBTENANT1_ATTR, attribute.getValues().get(0));
            } else {
                Assert.fail("Attribute key unexpected " + attribute.getKey());
            }
        }

        // Add a zone role for the subtenant1 admins group
        // verify that a subtenant user in that group does not get the zone role
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        RoleAssignmentEntry entry_subtenant = new RoleAssignmentEntry();
        entry_subtenant.setGroup(SUBTENANT1_ADMINS_GROUP);
        entry_subtenant.getRoles().add("SECURITY_ADMIN");
        changes.getAdd().add(entry_subtenant);
        assignments = rSys.path("/vdc/role-assignments").put(RoleAssignments.class, changes);
        Assert.assertEquals(3, assignments.getAssignments().size());

        resp = rSTAdminGr1.path("/vdc/role-assignments").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        String subtenant2_label = "subtenant2";
        tenantParam.setLabel(subtenant2_label);
        tenantParam.setDescription("second subtenant");
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());
        UserMappingParam tenant2UserMapping = new UserMappingParam();
        tenant2UserMapping.setDomain("sanity.local");
        UserMappingAttributeParam tenant2Attr = new UserMappingAttributeParam();
        tenant2Attr.setKey("COMPANY");
        tenant2Attr.setValues(Collections.singletonList(SUBTENANT1_ATTR.toLowerCase()));
        tenant2UserMapping.setAttributes(Collections.singletonList(tenant2Attr));
        tenantParam.getUserMappings().add(tenant2UserMapping);

        // duplicate attribute - should fail
        resp = rTAdmin.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        // create second subtenant
        tenant2Attr.setValues(Collections.singletonList(SUBTENANT2_ATTR));

        UserMappingAttributeParam tenant2Attr2 = new UserMappingAttributeParam();
        tenant2Attr2.setKey("department");
        tenant2Attr2.setValues(Collections.singletonList(SUBTENANT2_ATTR.toLowerCase()));
        List<UserMappingAttributeParam> attributes = new ArrayList<UserMappingAttributeParam>();
        attributes.add(tenant2Attr);
        attributes.add(tenant2Attr2);
        tenant2UserMapping.setAttributes(attributes);

        // duplicate name check for tenants
        tenantParam.setLabel(subtenant1_label);
        resp = rTAdmin.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());
        tenantParam.setLabel(subtenant2_label);

        TenantOrgRestRep subtenant2 = rTAdmin.path(subtenant_url).post(TenantOrgRestRep.class, tenantParam);

        subtenant2Id = subtenant2.getId();
        // Add a mapping with less scope then remove the mapping
        // with more scope to verify that it works.
        UserMappingParam tenant2UserMapping2 = new UserMappingParam();
        tenant2UserMapping2.setDomain("sanity.local");
        UserMappingAttributeParam tenant2Attr3 = new UserMappingAttributeParam();
        tenant2Attr3.setKey("company");
        tenant2Attr3.setValues(Collections.singletonList(SUBTENANT2_ATTR));
        tenant2UserMapping2.setAttributes(Collections.singletonList(tenant2Attr3));
        tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());

        // Create a third mapping equal to the first mapping
        // with differences in case and order of attributes
        UserMappingParam tenant2UserMapping3 = new UserMappingParam();
        tenantUpdate.setLabel(subtenant2_label);
        tenant2UserMapping3.setDomain("Sanity.Local");
        List<UserMappingAttributeParam> attributes2 = new ArrayList<UserMappingAttributeParam>();
        attributes2.add(tenant2Attr3);
        attributes2.add(tenant2Attr2);
        tenant2UserMapping3.setAttributes(attributes2);

        tenantUpdate.setLabel(subtenant2_label);
        tenantUpdate.getUserMappingChanges().getAdd().add(tenant2UserMapping2);
        resp = rTAdmin.path("/tenants/" + subtenant2Id.toString()).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());

        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        tenantUpdate.getUserMappingChanges().setRemove(new ArrayList<UserMappingParam>());
        tenantUpdate.getUserMappingChanges().getRemove().add(tenant2UserMapping3);
        resp = rTAdmin.path("/tenants/" + subtenant2Id.toString()).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());

        subtenant2 = rTAdmin.path("/tenants/" + subtenant2Id.toString()).get(TenantOrgRestRep.class);
        Assert.assertTrue(subtenant2.getId().equals(subtenant2Id));
        Assert.assertTrue(subtenant2.getName().equals(subtenant2_label));
        Assert.assertEquals(1, subtenant2.getUserMappings().size());
        for (UserMappingParam mapping : subtenant2.getUserMappings()) {
            Assert.assertEquals(1, mapping.getAttributes().size());
            UserMappingAttributeParam attribute = mapping.getAttributes().get(0);
            if (attribute.getKey().equalsIgnoreCase("company")) {
                Assert.assertEquals(1, attribute.getValues().size());
                Assert.assertEquals(SUBTENANT2_ATTR, attribute.getValues().get(0));
            } else {
                Assert.fail("Attribute key unexpected " + attribute.getKey());
            }
        }

        // test that updating this tenant with the second tenant's name
        tenantUpdateNameOnly = new TenantUpdateParam();
        tenantUpdateNameOnly.setLabel(subtenant2_label);
        resp = rTAdminGr.path("/tenants/" + subtenant1Id.toString()).put(ClientResponse.class, tenantUpdateNameOnly);
        Assert.assertEquals(400, resp.getStatus());

        // as sysmonitor, verify we can access sub tenant quota
        resp = rMon.path("/tenants/" + subtenant2Id.toString() + "/quota").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // create second level tenant - should fail
        tenantParam.setLabel("bad");
        tenantParam.setDescription("bad subtenant");
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());
        UserMappingAttributeParam tenantAttrBad = new UserMappingAttributeParam();
        tenantAttrBad.setKey("company");
        tenantAttrBad.setValues(Collections.singletonList("subtenant_bad"));
        UserMappingParam tenantMappingBad = new UserMappingParam();
        tenantMappingBad.setAttributes(Collections.singletonList(tenantAttrBad));
        tenantParam.getUserMappings().add(tenantMappingBad);
        resp = rTAdminGr.path("/tenants/" + subtenant2Id.toString() + "/subtenants")
                .post(ClientResponse.class, tenantParam);
        Assert.assertEquals(403, resp.getStatus());
        resp = rTAdmin.path("/tenants/" + subtenant2Id.toString() + "/subtenants")
                .post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        // TODO - Refactor these tests to meet one of the good code property "one method is responsible for one work"
        // TODO - and make each tests as individual and simple unittests as possible that just runs in very few milliseconds.
        // But, still the process of understanding the whole test architecture, hence adding
        // my new test also here for now.

        // Create subtenants with duplicate groups. But API should
        // be able to find the duplicate groups and remove them
        // and create only the distinct groups to the userMappings.

        String dupGroupSubtenant_url = rootTenantBaseUrl + "/subtenants";
        String dupTenantParam_label = "DupGroupSubTenant";

        TenantCreateParam dupTenantParam = new TenantCreateParam();
        dupTenantParam.setLabel(dupTenantParam_label);
        dupTenantParam.setDescription("first subtenant with duplicate groups in the user mapping.");
        dupTenantParam.setUserMappings(new ArrayList<UserMappingParam>());

        String key1 = "Attr1";
        String key2 = "Attr2";
        String key3 = "Attr3";

        List<String> values1 = new ArrayList<String>();
        values1.add("one");
        values1.add("two");
        values1.add("three");
        values1.add("four");
        values1.add("two"); // Duplicate one, so should be removed in the expected list.
        values1.add("three"); // Duplicate one, so should be removed in the expected list.

        List<String> expectedValues1 = new ArrayList<String>();
        expectedValues1.add("one");
        expectedValues1.add("two");
        expectedValues1.add("three");
        expectedValues1.add("four");

        List<String> values2 = new ArrayList<String>();
        values2.add("one");
        values2.add("two");
        values2.add("three");
        values2.add("four");
        values2.add("two"); // Duplicate one, so should be removed in the expected list.
        values2.add("three"); // Duplicate one, so should be removed in the expected list.
        values2.add("five"); // One additional value that is not there in value1. So, not a duplicate.

        List<String> expectedValues2 = new ArrayList<String>();
        expectedValues2.add("one");
        expectedValues2.add("two");
        expectedValues2.add("three");
        expectedValues2.add("four");
        expectedValues2.add("five");

        // Validating the duplicate removal code added in the UserMappingAttributeParam() constructor.
        UserMappingAttributeParam userMappingAttributeParam1 = new UserMappingAttributeParam(key1, values1);
        UserMappingAttributeParam userMappingAttributeParam2 = new UserMappingAttributeParam(key1, values1); // Duplicate Attribute.
        UserMappingAttributeParam userMappingAttributeParam3 = new UserMappingAttributeParam(key1, values2);
        UserMappingAttributeParam userMappingAttributeParam4 = new UserMappingAttributeParam(key2, values2);
        UserMappingAttributeParam userMappingAttributeParam5 = new UserMappingAttributeParam(key3, values2);

        Assert.assertArrayEquals(expectedValues1.toArray(), userMappingAttributeParam1.getValues().toArray());
        Assert.assertArrayEquals(expectedValues1.toArray(), userMappingAttributeParam2.getValues().toArray());
        Assert.assertArrayEquals(expectedValues2.toArray(), userMappingAttributeParam3.getValues().toArray());
        Assert.assertArrayEquals(expectedValues2.toArray(), userMappingAttributeParam4.getValues().toArray());
        Assert.assertArrayEquals(expectedValues2.toArray(), userMappingAttributeParam5.getValues().toArray());

        // Validating the duplicate removal code added in the UserMappingAttributeParam.setValues() method.
        userMappingAttributeParam1.setValues(values1);
        userMappingAttributeParam2.setValues(values1);
        userMappingAttributeParam3.setValues(values2);
        userMappingAttributeParam4.setValues(values2);
        userMappingAttributeParam5.setValues(values2);

        Assert.assertArrayEquals(expectedValues1.toArray(), userMappingAttributeParam1.getValues().toArray());
        Assert.assertArrayEquals(expectedValues1.toArray(), userMappingAttributeParam2.getValues().toArray());
        Assert.assertArrayEquals(expectedValues2.toArray(), userMappingAttributeParam3.getValues().toArray());
        Assert.assertArrayEquals(expectedValues2.toArray(), userMappingAttributeParam4.getValues().toArray());
        Assert.assertArrayEquals(expectedValues2.toArray(), userMappingAttributeParam5.getValues().toArray());

        List<UserMappingAttributeParam> attributeList = new ArrayList<UserMappingAttributeParam>();
        attributeList.add(userMappingAttributeParam1);
        attributeList.add(userMappingAttributeParam2); // Duplicate one, so should be removed in the expected list.
        attributeList.add(userMappingAttributeParam3);
        attributeList.add(userMappingAttributeParam4);

        List<UserMappingAttributeParam> expectedAttributeList = new ArrayList<UserMappingAttributeParam>();
        expectedAttributeList.add(userMappingAttributeParam1);
        expectedAttributeList.add(userMappingAttributeParam3);
        expectedAttributeList.add(userMappingAttributeParam4);

        List<UserMappingAttributeParam> additionalAttributeList = new ArrayList<UserMappingAttributeParam>();
        additionalAttributeList.add(userMappingAttributeParam1);
        additionalAttributeList.add(userMappingAttributeParam2); // Duplicate one, so should be removed in the expected list.
        additionalAttributeList.add(userMappingAttributeParam5);
        additionalAttributeList.add(userMappingAttributeParam3);
        additionalAttributeList.add(userMappingAttributeParam4);

        List<UserMappingAttributeParam> expectedAdditionalAttributeList = new ArrayList<UserMappingAttributeParam>();
        expectedAdditionalAttributeList.add(userMappingAttributeParam1);
        expectedAdditionalAttributeList.add(userMappingAttributeParam5);
        expectedAdditionalAttributeList.add(userMappingAttributeParam3);
        expectedAdditionalAttributeList.add(userMappingAttributeParam4);

        List<String> groups = new ArrayList<String>();
        groups.add(ZONEADMINS_GROUP);
        groups.add(TENANT_ADMINS_GROUP);
        groups.add(SUBTENANT1_ADMINS_GROUP);
        groups.add(SUBTENANT1_USERS_GROUP);
        groups.add(ZONEADMINS_GROUP); // Duplicate one, so should be removed in the expected list.
        groups.add(TENANT_ADMINS_GROUP); // Duplicate one, so should be removed in the expected list.
        groups.add(SUBTENANT1_USERS_GROUP); // Duplicate one, so should be removed in the expected list.

        List<String> expectedGroups = new ArrayList<String>();
        expectedGroups.add(ZONEADMINS_GROUP);
        expectedGroups.add(TENANT_ADMINS_GROUP);
        expectedGroups.add(SUBTENANT1_ADMINS_GROUP);
        expectedGroups.add(SUBTENANT1_USERS_GROUP);

        List<String> additionalGroups = new ArrayList<String>();
        additionalGroups.add(ZONEADMINS_GROUP);
        additionalGroups.add(TENANT_ADMINS_GROUP);
        additionalGroups.add(SUBTENANT2_ADMINS_GROUP);
        additionalGroups.add(ASUBSETOFUSERS_GROUP);
        additionalGroups.add(SUBTENANT1_ADMINS_GROUP);
        additionalGroups.add(SUBTENANT1_USERS_GROUP);
        additionalGroups.add(ZONEADMINS_GROUP); // Duplicate one, so should be removed in the expected list.
        additionalGroups.add(TENANT_ADMINS_GROUP); // Duplicate one, so should be removed in the expected list.
        additionalGroups.add(SUBTENANT1_USERS_GROUP); // Duplicate one, so should be removed in the expected list.

        List<String> expectedAdditionalGroups = new ArrayList<String>();
        expectedAdditionalGroups.add(ZONEADMINS_GROUP);
        expectedAdditionalGroups.add(TENANT_ADMINS_GROUP);
        expectedAdditionalGroups.add(SUBTENANT2_ADMINS_GROUP);
        expectedAdditionalGroups.add(ASUBSETOFUSERS_GROUP);
        expectedAdditionalGroups.add(SUBTENANT1_ADMINS_GROUP);
        expectedAdditionalGroups.add(SUBTENANT1_USERS_GROUP);

        UserMappingParam dupTenantMapping1 = new UserMappingParam("sanity.LOCAL", attributeList, groups);
        UserMappingParam dupTenantMapping2 = new UserMappingParam("sanity.LOCAL", attributeList, groups);

        // Validate against the expected list. This is to validate the new code added to remove the
        // duplicates in the UserMappingParam() constructor. For UserMappingParam1.
        List<UserMappingAttributeParam> retAttributeList = dupTenantMapping1.getAttributes();
        List<String> retGroups = dupTenantMapping1.getGroups();

        Assert.assertArrayEquals(expectedAttributeList.toArray(), retAttributeList.toArray());
        Assert.assertArrayEquals(expectedGroups.toArray(), retGroups.toArray());

        // For UserMappingParam2.
        retAttributeList = dupTenantMapping2.getAttributes();
        retGroups = dupTenantMapping2.getGroups();

        Assert.assertArrayEquals(expectedAttributeList.toArray(), retAttributeList.toArray());
        Assert.assertArrayEquals(expectedGroups.toArray(), retGroups.toArray());

        // Validate against the expected list. This is to validate the new code added to remove the
        // duplicates in the UserMappingParam.setAttributes() and UserMappingParam.setGroups().
        // For UserMappingParam1.
        dupTenantMapping1.setGroups(additionalGroups);
        dupTenantMapping1.setAttributes(additionalAttributeList);

        retAttributeList = dupTenantMapping1.getAttributes();
        retGroups = dupTenantMapping1.getGroups();

        Assert.assertArrayEquals(expectedAdditionalAttributeList.toArray(), retAttributeList.toArray());
        Assert.assertArrayEquals(expectedAdditionalGroups.toArray(), retGroups.toArray());

        // For UserMappingParam2.
        dupTenantMapping2.setGroups(additionalGroups);
        dupTenantMapping2.setAttributes(additionalAttributeList);

        retAttributeList = dupTenantMapping2.getAttributes();
        retGroups = dupTenantMapping2.getGroups();

        Assert.assertArrayEquals(expectedAdditionalAttributeList.toArray(), retAttributeList.toArray());
        Assert.assertArrayEquals(expectedAdditionalGroups.toArray(), retGroups.toArray());

        List<UserMappingParam> dupUserMappings = new ArrayList<UserMappingParam>();
        dupUserMappings.add(dupTenantMapping1);
        dupUserMappings.add(dupTenantMapping2); // Adding the same userMapping here, just make sure the duplicate will be removed..

        // Execute the API /tenants/{id}/subtenants with duplicate entries in the payload
        // and validate the response to make sure no duplicates are actually added to the resource.
        dupTenantParam.setUserMappings(dupUserMappings);

        TenantOrgRestRep dupTenantResp = rTAdminGr.path(dupGroupSubtenant_url).post(TenantOrgRestRep.class, dupTenantParam);

        Assert.assertTrue(dupTenantResp.getName().equals(dupTenantParam_label));
        Assert.assertEquals(1, dupTenantResp.getUserMappings().size());

        // Since, both the UserMapping in the list is same, doing the validation in the loop with
        // same expected values.
        for (UserMappingParam retUserMapping : dupTenantResp.getUserMappings()) {
            // Unique groups are only ZONEADMINS_GROUP, TENANT_ADMINS_GROUP, SUBTENANT1_ADMINS_GROUP, SUBTENANT1_USERS_GROUP,
            // SUBTENANT2_ADMINS_GROUP, ASUBSETOFUSERS_GROUP. So, count is 6.

            List<String> actualGroupsWithoutDomainUserMapping = new ArrayList<>(retUserMapping.getGroups());
            List<String> actualGroupsWithDomainUserMapping = new ArrayList<>();

            for (String group : actualGroupsWithoutDomainUserMapping) {
                String groupWithDomain = group + "@sanity.local";
                actualGroupsWithDomainUserMapping.add(groupWithDomain);
            }

            Assert.assertEquals(6, retUserMapping.getGroups().size());
            Assert.assertArrayEquals(expectedAdditionalGroups.toArray(), actualGroupsWithDomainUserMapping.toArray());

            // Unique attributes are only userMappingAttributeParam1, userMappingAttributeParam3, userMappingAttributeParam4,
            // userMappingAttributeParam5. So, count is 4.
            Assert.assertEquals(4, retUserMapping.getAttributes().size());
            Assert.assertArrayEquals(expectedAdditionalAttributeList.toArray(), retUserMapping.getAttributes().toArray());
        }

        // Delete the create the subtenant to avoid the confusions in the further test cases that runs after this.
        ClientResponse dupTenantDeleteResp = rTAdminGr.path("/tenants/" + dupTenantResp.getId().toString() + "/deactivate").post(
                ClientResponse.class);
        Assert.assertEquals(200, dupTenantDeleteResp.getStatus());

        /*
         * create subtenant unauthorized
         */
        // no perms
        resp = rUnAuth.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(403, resp.getStatus());
        // sysadmin
        resp = rSys.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(403, resp.getStatus());

        subtenant2Id = subtenant2.getId();
        subtenant2 = rTAdmin.path("/tenants/" + subtenant2Id.toString()).get(TenantOrgRestRep.class);
        Assert.assertTrue(subtenant2.getId().equals(subtenant2Id));
        Assert.assertTrue(subtenant2.getName().equals(subtenant2_label));
        Assert.assertEquals(1, subtenant2.getUserMappings().size());
        for (UserMappingParam mapping : subtenant2.getUserMappings()) {
            Assert.assertEquals(1, mapping.getAttributes().size());
            UserMappingAttributeParam attribute = mapping.getAttributes().get(0);
            if (attribute.getKey().equalsIgnoreCase("company")) {
                Assert.assertEquals(1, attribute.getValues().size());
                Assert.assertEquals(SUBTENANT2_ATTR, attribute.getValues().get(0));
            } else {
                Assert.fail("Attribute key unexpected " + attribute.getKey());
            }
        }

        // create third subtenant
        String subtenant3_label = "subtenant3";
        tenantParam.setLabel(subtenant3_label);
        tenantParam.setDescription("third subtenant");
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());

        UserMappingParam tenant3Mapping = new UserMappingParam();

        // Try a group without the domain. Expect 400
        tenant3Mapping.setGroups(Collections.singletonList("Test Group"));
        resp = rTAdmin.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        tenant3Mapping.setDomain("SANITY.local");
        // Set the group to a user and expect 400
        tenant3Mapping.setGroups(Collections.singletonList(SUBTENANT3_ADMIN));
        tenantParam.getUserMappings().add(tenant3Mapping);
        resp = rTAdmin.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        // Try a non-whitelist group expect 400
        tenant3Mapping.setGroups(Collections.singletonList("NotOnWhitelist"));
        resp = rTAdmin.path(subtenant_url).post(ClientResponse.class, tenantParam);
        Assert.assertEquals(400, resp.getStatus());

        // Finally attempt the successful case
        tenant3Mapping.setGroups(Collections.singletonList("Test Group"));

        TenantOrgRestRep subtenant3 = rTAdmin.path(subtenant_url).post(TenantOrgRestRep.class, tenantParam);
        Assert.assertTrue(subtenant3.getName().equals(subtenant3_label));
        Assert.assertEquals(1, subtenant3.getUserMappings().size());
        Assert.assertEquals(1, subtenant3.getUserMappings().get(0).getGroups().size());
        Assert.assertEquals("test group", subtenant3.getUserMappings().get(0).getGroups().get(0).toLowerCase());
        subtenant3Id = subtenant3.getId();

        // login in with a user that should map to more than one tenant and expect a 403
        _savedTokens.remove(SUBTENANT13_USER);
        resp = rST13User.path("/login").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        /*
         * list subtenants - sys monitor, tenant admin and group
         */

        TenantOrgList list = rSys.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(3, list.getSubtenants().size());
        list = rTAdmin.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(3, list.getSubtenants().size());
        list = rTAdminGr.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(3, list.getSubtenants().size());
        // unauth
        resp = rUnAuth.path(subtenant_url).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // system admin only user, verify it doesn't have permision to list subtenants
        String SYSTEM_ADMIN_ONLY = "sysadminonly@sanity.local";
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        roleAssignmentEntry.setSubjectId(SYSTEM_ADMIN_ONLY);
        roleAssignmentEntry.setRoles(new ArrayList<String>(Arrays.asList("SYSTEM_ADMIN")));
        List<RoleAssignmentEntry> add = new ArrayList<RoleAssignmentEntry>();
        add.add(roleAssignmentEntry);
        RoleAssignmentChanges roleAssignmentChanges = new RoleAssignmentChanges();
        roleAssignmentChanges.setAdd(add);
        resp = rSys.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        BalancedWebResource rSysadminOnly = createHttpsClient(SYSTEM_ADMIN_ONLY, AD_PASS_WORD, baseUrls);
        resp = rSysadminOnly.path(subtenant_url).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        RoleAssignments previousAssignments =
                rZAdmin.path(String.format(roles_url_format, rootTenantId.toString()))
                        .get(RoleAssignments.class);
        // re-add the tenant admin role to root
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(rootTenantAdminUserEntry);
        previousAssignments.getAssignments().add(rootTenantAdminUserEntry);
        readAssignments =
                rZAdmin.path(String.format(roles_url_format, rootTenantId.toString()))
                        .put(RoleAssignments.class, changes);
        Assert.assertTrue(checkEqualsRoles(previousAssignments.getAssignments(),
                readAssignments.getAssignments()));

        /*
         * ROLE ASSIGNMENT - subtenant
         */
        RoleAssignmentEntry entry5 = new RoleAssignmentEntry();
        entry5.setSubjectId(SUBTENANT1_ADMIN);
        entry5.getRoles().add("TENANT_ADMIN");
        entry5.getRoles().add("PROJECT_ADMIN");
        resp = rTAdmin.path(String.format(roles_url_format, subtenant1Id.toString()))
                .get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        readAssignments = rTAdminGr.path(String.format(roles_url_format, subtenant1Id.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(readAssignments.getAssignments().size() == 1);

        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry5);
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().addAll(readAssignments.getAssignments());
        resp = rTAdminGr.path(String.format(roles_url_format, subtenant1Id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        readAssignments = rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(), readAssignments.getAssignments()));

        // batch role assignment changes
        RoleAssignments assignmentToHaveWhenImDone = readAssignments;

        changes =
                new RoleAssignmentChanges(new ArrayList<RoleAssignmentEntry>(),
                        readAssignments.getAssignments());
        entry1 = new RoleAssignmentEntry();
        entry1.setSubjectId(ROOTUSER);
        entry1.getRoles().add("TENANT_ADMIN");
        changes.getAdd().add(entry1);
        entry2 = new RoleAssignmentEntry();
        entry2.setSubjectId(SUBTENANT1_ADMIN);
        entry2.getRoles().add("TENANT_ADMIN");
        entry2.getRoles().add("PROJECT_ADMIN");
        changes.getAdd().add(entry2);
        entry3 = new RoleAssignmentEntry();
        entry3.setSubjectId(SUBTENANT1_USER);
        entry3.getRoles().add("PROJECT_ADMIN");
        changes.getAdd().add(entry3);
        entry4 = new RoleAssignmentEntry();
        entry4.setSubjectId(SUBTENANT1_READER);
        entry4.getRoles().add("TENANT_APPROVER");
        changes.getAdd().add(entry4);
        entry5 = new RoleAssignmentEntry();
        entry5.setGroup(SUBTENANT1_ADMINS_GROUP);
        entry5.getRoles().add("TENANT_ADMIN");
        entry5.getRoles().add("PROJECT_ADMIN");
        changes.getAdd().add(entry5);
        RoleAssignmentEntry entry6 = new RoleAssignmentEntry();
        entry6.setGroup(SUBTENANT1_USERS_GROUP);
        entry6.getRoles().add("TENANT_APPROVER");
        changes.getAdd().add(entry6);

        resp =
                rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString()))
                        .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        readAssignments =
                rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString()))
                        .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(),
                readAssignments.getAssignments()));

        // reverting back to the way it was before the batch role assignment changes
        changes =
                new RoleAssignmentChanges(assignmentToHaveWhenImDone.getAssignments(),
                        readAssignments.getAssignments());
        resp =
                rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString()))
                        .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        readAssignments =
                rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString()))
                        .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(),
                readAssignments.getAssignments()));

        // check with whoami, that SUBTENANT1_ADMIN at this point, has tenant_admin and project admin
        ArrayList<String> lookFor = new ArrayList<String>();
        Collections.addAll(lookFor, "TENANT_ADMIN", "PROJECT_ADMIN");
        userInfoCheckRoles(rSTAdmin1, lookFor);

        entry6 = new RoleAssignmentEntry();
        entry6.setGroup(SUBTENANT2_ADMINS_GROUP);
        entry6.getRoles().add("TENANT_ADMIN");
        RoleAssignmentEntry entry7 = new RoleAssignmentEntry();
        entry7.setSubjectId(SUBTENANT2_ADMIN);
        entry7.getRoles().add("TENANT_ADMIN");
        readAssignments = rTAdmin.path(String.format(roles_url_format, subtenant2Id.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(readAssignments.getAssignments().size() == 1);

        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry6);
        changes.getAdd().add(entry7);
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().add(entry5);
        changes.getRemove().addAll(readAssignments.getAssignments());

        resp = rTAdmin.path(String.format(roles_url_format, subtenant2Id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        readAssignments = rSTAdminGr2.path(String.format(roles_url_format, subtenant2Id.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(), readAssignments.getAssignments()));
        readAssignments = rSTAdmin2.path(String.format(roles_url_format, subtenant2Id.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(), readAssignments.getAssignments()));

        /*
         * LIST subtenants
         */
        // tenant admins on root gets the full list
        list = rTAdmin.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(3, list.getSubtenants().size());
        // tenant admin on the child get only the child
        list = rSTAdmin1.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(1, list.getSubtenants().size());
        Assert.assertEquals(subtenant1Id, list.getSubtenants().get(0).getId());
        list = rSTAdminGr2.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(1, list.getSubtenants().size());
        Assert.assertEquals(subtenant2Id, list.getSubtenants().get(0).getId());
        list = rSTAdmin2.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(1, list.getSubtenants().size());
        Assert.assertEquals(subtenant2Id, list.getSubtenants().get(0).getId());

        /*
         * Changing subtenant roles
         */
        RoleAssignmentEntry entry8 = new RoleAssignmentEntry();
        entry8.setGroup(SUBTENANT2_ADMINS_GROUP);
        entry8.getRoles().add("PROJECT_ADMIN");
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry8);
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().add(entry6);
        resp = rSTAdmin2.path(String.format(roles_url_format, subtenant2Id.toString())).put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        changes.getAdd().add(entry7);
        readAssignments = rSTAdmin2.path(String.format(roles_url_format, subtenant2Id.toString()))
                .get(RoleAssignments.class);
        Assert.assertTrue(checkEqualsRoles(changes.getAdd(), readAssignments.getAssignments()));
        resp = rSTAdminGr2.path(String.format(roles_url_format, subtenant2Id.toString())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        RoleAssignmentEntry entry9 = new RoleAssignmentEntry();
        entry9.setGroup(SUBTENANT1_ADMINS_GROUP);
        entry9.getRoles().add("PROJECT_ADMIN");
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry9);
        resp = rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString())).put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSTAdminGr1.path(String.format(roles_url_format, subtenant1Id.toString())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        resp = rSTAdminGr2.path(String.format(roles_url_format, subtenant1Id.toString())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        // add a user from root tenant to be an TENANT_ADMIN in subtenant1
        entry_subtenant = new RoleAssignmentEntry();
        entry_subtenant.setSubjectId(ROOTUSER2);
        entry_subtenant.getRoles().add("TENANT_ADMIN");
        changes.getAdd().add(entry_subtenant);
        resp = rSTAdmin1.path(String.format(roles_url_format, subtenant1Id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());

        // test out that rootuser2 is able to use his tenant admin in the subtenant even though that is not
        // his home tenant.
        resp = rRootUser2.path("/tenants/" + subtenant1Id.toString()).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        // Assign a role to a recursive group
        RoleAssignmentEntry recursiveGroupEntry = new RoleAssignmentEntry();
        recursiveGroupEntry.setGroup("Domain Users@sanity.local");
        recursiveGroupEntry.getRoles().add("PROJECT_ADMIN");
        changes.getAdd().add(recursiveGroupEntry);
        // Try to assign a role to a group not on the whitelist
        RoleAssignmentEntry nonWhiteListGroupEntry = new RoleAssignmentEntry();
        nonWhiteListGroupEntry.setGroup("NotOnWhitelist@sanity.local");
        nonWhiteListGroupEntry.getRoles().add("PROJECT_ADMIN");
        changes.getAdd().add(nonWhiteListGroupEntry);
        // Assign a role to a user in this tenant
        RoleAssignmentEntry st3AdminEntry = new RoleAssignmentEntry();
        st3AdminEntry.setSubjectId(SUBTENANT3_ADMIN);
        st3AdminEntry.getRoles().add("TENANT_ADMIN");
        changes.getAdd().add(st3AdminEntry);

        resp = rTAdmin.path(String.format(roles_url_format, subtenant3Id.toString()))
                .put(ClientResponse.class, changes);
        // Should fail with a 400 due to the non-whitelist group
        Assert.assertEquals(400, resp.getStatus());

        changes.getAdd().remove(nonWhiteListGroupEntry);
        // Adding this to the remove list should have no effect
        changes.getRemove().add(nonWhiteListGroupEntry);
        resp = rTAdmin.path(String.format(roles_url_format, subtenant3Id.toString()))
                .put(ClientResponse.class, changes);
        // Should succeed now
        Assert.assertEquals(200, resp.getStatus());
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        // STAdmin3 should be able to read roles now
        readAssignments = rSTAdmin3.path(String.format(roles_url_format, subtenant3Id.toString()))
                .get(RoleAssignments.class);
        // Try to assign a role to a user in a different tenant
        RoleAssignmentEntry st2AdminEntry = new RoleAssignmentEntry();
        st2AdminEntry.setSubjectId(SUBTENANT2_ADMIN);
        st2AdminEntry.getRoles().add("TENANT_ADMIN");

        changes.getAdd().add(st2AdminEntry);
        resp = rSTAdmin3.path(String.format(roles_url_format, subtenant3Id.toString()))
                .put(ClientResponse.class, changes);
        // Should fail with a 400 due to user in wrong tenant
        Assert.assertEquals(400, resp.getStatus());

        // Try to assign a role with the valid group as username
        RoleAssignmentEntry groupAsSidEntry = new RoleAssignmentEntry();
        groupAsSidEntry.setSubjectId("Domain Users");
        groupAsSidEntry.getRoles().add("TENANT_ADMIN");
        changes.getAdd().remove(0);
        changes.getAdd().add(groupAsSidEntry);
        resp = rSTAdmin3.path(String.format(roles_url_format, subtenant3Id.toString()))
                .put(ClientResponse.class, changes);
        // Should fail with a 400 due to user with that name not existing
        Assert.assertEquals(400, resp.getStatus());

        // Try to assign a role with a valid username as the group
        RoleAssignmentEntry sidAsGroupEntry = new RoleAssignmentEntry();
        sidAsGroupEntry.setGroup(SUBTENANT3_ADMIN);
        sidAsGroupEntry.getRoles().add("TENANT_ADMIN");
        changes.getAdd().remove(0);
        changes.getAdd().add(sidAsGroupEntry);
        resp = rSTAdmin3.path(String.format(roles_url_format, subtenant3Id.toString()))
                .put(ClientResponse.class, changes);
        // Should fail with a 400 due to group with that name not existing
        Assert.assertEquals(400, resp.getStatus());

        /*
         * Test the user tenant troubleshooting API
         */
        String userTenantURL = "/user/tenant";
        resp = rUnAuth.path(userTenantURL).queryParam("username", "sanity_user@sanity.local").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        resp = rZAdmin.path(userTenantURL).queryParam("username", "sanity_user@baddomain.com").get(ClientResponse.class);
        Assert.assertEquals(400, resp.getStatus());

        resp = rZAdmin.path(userTenantURL).queryParam("username", "sanity_user").get(ClientResponse.class);
        Assert.assertEquals(400, resp.getStatus());

        resp = rZAdmin.path(userTenantURL).queryParam("username", "nouser@sanity.local").get(ClientResponse.class);
        Assert.assertEquals(400, resp.getStatus());

        resp = rZAdmin.path(userTenantURL).get(ClientResponse.class);
        Assert.assertEquals(400, resp.getStatus());

        UserTenantList userTenants = rZAdmin.path(userTenantURL).queryParam("username", "sanity_user@sanity.local")
                .get(UserTenantList.class);
        Assert.assertEquals(userTenants._userTenantList.size(), 1);
        UserTenant userTenant = userTenants._userTenantList.get(0);
        Assert.assertEquals(rootTenantId, userTenant._id);
        Assert.assertEquals("sanity.local", userTenant._userMapping.getDomain());
        Assert.assertEquals(1, userTenant._userMapping.getAttributes().size());
        Assert.assertEquals("ou", userTenant._userMapping.getAttributes().get(0).getKey());
        Assert.assertArrayEquals(new String[] { ROOTTENANT_ATTR },
                userTenant._userMapping.getAttributes().get(0).getValues().toArray(new String[0]));

        userTenants = rZAdmin.path(userTenantURL).queryParam("username", SUBTENANT13_USER).get(UserTenantList.class);
        Assert.assertEquals(userTenants._userTenantList.size(), 2);
        for (UserTenant userTenantEntry : userTenants._userTenantList) {
            if (userTenantEntry._id.equals(subtenant1Id)) {
                Assert.assertEquals(1, userTenantEntry._userMapping.getAttributes().size());
                Assert.assertEquals("company", userTenantEntry._userMapping.getAttributes().get(0).getKey().toLowerCase());
                Assert.assertArrayEquals(new String[] { SUBTENANT1_ATTR }, userTenantEntry._userMapping.getAttributes().get(0).getValues()
                        .toArray(new String[0]));
            } else if (userTenantEntry._id.equals(subtenant3Id)) {
                Assert.assertEquals(1, userTenantEntry._userMapping.getGroups().size());
                Assert.assertArrayEquals(new String[] { SUBTENANT3_ATTR }, userTenantEntry._userMapping.getGroups().toArray(new String[0]));
            } else {
                Assert.fail("Unexpected tenant ID: " + userTenantEntry._id);
            }
        }

        /*
         * CREATE, LIST projects
         */
        // as zone sysadmin
        expectedProjListResults.put("root", new ArrayList<ProjectEntry>());
        expectedProjListResults.put("st1", new ArrayList<ProjectEntry>());
        expectedProjListResults.put("st2", new ArrayList<ProjectEntry>());
        ProjectParam paramProj = new ProjectParam();
        resp = rZAdminGr.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(403, resp.getStatus());

        // as tenant admin of root
        paramProj = new ProjectParam("root project1");
        ProjectEntry createResp = rTAdmin.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .post(ProjectEntry.class, paramProj);

        Assert.assertTrue(createResp.name.equals(paramProj.getName()));
        Assert.assertTrue(createResp.id != null);
        ProjectEntry projEl = new ProjectEntry(createResp);
        expectedProjListResults.get("root").add(projEl);

        // as subtenant admins and project admins
        paramProj = new ProjectParam("subtenant1 project1");
        createResp = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(createResp.name.equals(paramProj.getName()));
        Assert.assertTrue(createResp.id != null);
        expectedProjListResults.get("st1").add(new ProjectEntry(createResp));
        paramProj.setName("subtenant1 project2");
        createResp = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(createResp.name.equals(paramProj.getName()));
        Assert.assertTrue(createResp.id != null);
        expectedProjListResults.get("st1").add(new ProjectEntry(createResp));

        paramProj.setName("subtenant2 project1");
        createResp = rSTAdmin2.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(createResp.name.equals(paramProj.getName()));
        Assert.assertTrue(createResp.id != null);
        expectedProjListResults.get("st2").add(new ProjectEntry(createResp));
        paramProj.setName("subtenant2 project2");
        createResp = rSTAdminGr2.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(createResp.name.equals(paramProj.getName()));
        Assert.assertTrue(createResp.id != null);
        expectedProjListResults.get("st2").add(new ProjectEntry(createResp));

        // negative - create
        paramProj = new ProjectParam("bad");
        // tenant admin on root, can not create projects on subtenants
        resp = rTAdmin.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(403, resp.getStatus());
        // tenant admin at subtenant level can not create project on root tenant
        resp = rSTAdmin1.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(403, resp.getStatus());
        // tenant admin at subtenant2 can not create project on subtenant1
        resp = rSTAdmin2.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(403, resp.getStatus());
        // project admin at subtenant1 can not create project on subtenant2
        resp = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(403, resp.getStatus());
        // create project on deleted tenant
        tenantParam.setLabel("toremove");
        tenantParam.setDescription("toremove subtenant");
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());

        UserMappingParam tenantMappingToRemove = new UserMappingParam();
        tenantMappingToRemove.setDomain("sanity.local");
        UserMappingAttributeParam tenantAttr3 = new UserMappingAttributeParam();

        tenantAttr3.setKey("company");
        tenantAttr3.setValues(Collections.singletonList("toremove"));
        tenantMappingToRemove.setAttributes(Collections.singletonList(tenantAttr3));
        tenantParam.getUserMappings().add(tenantMappingToRemove);

        TenantOrgRestRep stDeleted = rTAdminGr.path(subtenant_url).post(TenantOrgRestRep.class, tenantParam);
        rTAdminGr.path("/tenants/" + stDeleted.getId() + "/deactivate").post();
        resp = rTAdminGr.path(String.format(_projectsUrlFormat, stDeleted.getId()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(404, resp.getStatus());

        // list and compare
        ProjectList projList = rSys.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .get(ProjectList.class);
        Assert.assertTrue(checkEqualsList(projList._projects, expectedProjListResults.get("root")));
        projList = rTAdmin.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .get(ProjectList.class);
        Assert.assertTrue(checkEqualsList(projList._projects, expectedProjListResults.get("root")));
        projList = rSys.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .get(ProjectList.class);
        Assert.assertTrue(checkEqualsList(projList._projects, expectedProjListResults.get("st1")));
        projList = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .get(ProjectList.class);
        Assert.assertTrue(checkEqualsList(projList._projects, expectedProjListResults.get("st1")));
        projList = rSTAdmin2.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .get(ProjectList.class);
        Assert.assertTrue(checkEqualsList(projList._projects, expectedProjListResults.get("st2")));

        projList = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .get(ProjectList.class);
        Assert.assertEquals(1, projList._projects.size());
        Assert.assertTrue(projList._projects.get(0).name.equals("subtenant1 project2"));

        resp = rSTAdmin1.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        resp = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // negative test:
        // 1. with TENANT_ADMIN user from root tenant, create subtenant
        // 2. with same user, create project in subtenant
        // 3. with same user, remove TENANT_ADMIN on himself in subtenant
        // 4. with same user, do get /projects/id/acl so show his project ownership
        // is still honored. (cq605248)
        String crossUserSubtenantUrl = rootTenantBaseUrl + "/subtenants";
        TenantCreateParam tenantParam2 = new TenantCreateParam();
        tenantParam2.setLabel("subtenantwithuserfromroottenant");
        tenantParam2.setDescription("subtenant where user from root tenant owns projects");
        tenantParam2.setUserMappings(new ArrayList<UserMappingParam>());
        UserMappingParam tenantMapping3 = new UserMappingParam();
        tenantMapping3.setDomain("sanity.local");
        UserMappingAttributeParam crossTenantAttr = new UserMappingAttributeParam();
        crossTenantAttr.setKey("COMPANY");
        crossTenantAttr.setValues(Collections.singletonList("crosstenant"));
        tenantMapping3.setAttributes(Collections.singletonList(crossTenantAttr));
        tenantParam2.getUserMappings().add(tenantMapping3);
        TenantOrgRestRep crossUserSubtenant = rTAdmin.path(crossUserSubtenantUrl).
                post(TenantOrgRestRep.class, tenantParam2);
        Assert.assertNotNull(crossUserSubtenant);
        paramProj = new ProjectParam();
        paramProj.setName("crosstenantuserproject");
        ProjectEntry project = rTAdmin.path(String.format(_projectsUrlFormat,
                crossUserSubtenant.getId().toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertNotNull(project);
        RoleAssignmentEntry rtTenantAdminUserEntry = new RoleAssignmentEntry();
        rtTenantAdminUserEntry.setSubjectId(ROOTTENANTADMIN);
        rtTenantAdminUserEntry.getRoles().add("TENANT_ADMIN");
        changes = new RoleAssignmentChanges();
        changes.setRemove(new ArrayList<RoleAssignmentEntry>());
        changes.getRemove().add(rtTenantAdminUserEntry);
        resp = rTAdmin.path(String.format(roles_url_format, crossUserSubtenant.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        resp = rTAdmin.path(String.format(_projectAclUrl, project.id.toString())).
                get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // negative test:
        // 1. assign TENANT_ADMIN to a group called ASubSetOfUsers in the root tenant
        // 2. Login with cross1@sanity.local. That user is part of the group above however is mapped
        // to the crosssubtenant by his attribute company=crosstenant. Therefore he should not be allowed
        // to perform a TENANT_ADMIN call in the root tenant.
        entry1 = new RoleAssignmentEntry();
        entry1.setGroup(ASUBSETOFUSERS_GROUP);
        entry1.getRoles().add("TENANT_ADMIN");
        changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry1);
        resp = rSys.path(String.format(roles_url_format, rootTenantId.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSTCross.path("/tenants/" + rootTenantId.toString()).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // list auth providers tests (CTRL-4314)

        // SECURITY_ADMIN can access auth providers
        resp = rZAdmin.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // root tenant TENANT_ADMIN can access auth providers
        resp = rTAdmin.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // subtenant TENANT_ADMIN can access auth providers
        resp = rSTAdmin2.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // regular user can't access auth providers
        resp = rUnAuth.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // make the user the admin of a subtenant (not their home tenant)
        assignTenantRole(subtenant3Id.toString(), ROOTUSER, "TENANT_ADMIN");

        // subtenant TENANT_ADMIN can access auth providers, even if they aren't admin of their home tenant
        resp = rUnAuth.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // cleanup
        removeTenantRole(subtenant3Id.toString(), ROOTUSER, "TENANT_ADMIN");

        // verify the role was removed
        resp = rUnAuth.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
    }

    /**
     * test for API /vdc/prepare-vdc, which will remove all root's tenant roles and project ownerships
     * 
     * before calling the API, prepare root to be:
     * 1. Provider Tenant's tenant admin
     * 2. owner of a project of Provider Tenant
     * 3. Tenant Admin of a subtenant
     * 4. owner of a project from subtenant
     */
    public void prepareVdcTest() throws Exception {
        ClientResponse resp = null;

        BalancedWebResource rootUser = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
        UserInfo info = rootUser.path("/user/whoami").get(UserInfo.class);
        String rootTenantId = info.getTenant();
        String rootToken = (String) _savedTokens.get(SYSADMIN);

        BalancedWebResource superSanity = createHttpsClient(SUPERUSER, AD_PASS_WORD, baseUrls);
        superSanity.path("/tenant").get(TenantResponse.class);
        String superSanityToken = (String) _savedTokens.get(SUPERUSER);

        // prepare tenant roles and project ownership
        // also assign TenantAdmin to superuser, so it can be used to verify afterwards
        boolean bRootHasProviderTenantAdmin = true;
        if (info.getHomeTenantRoles().isEmpty()) {
            bRootHasProviderTenantAdmin = false;
            resp = assignTenantRole(rootTenantId, SYSADMIN, "TENANT_ADMIN");
            Assert.assertEquals(200, resp.getStatus());
            resp = assignTenantRole(rootTenantId, SUPERUSER, "TENANT_ADMIN");
            Assert.assertEquals(200, resp.getStatus());
        }

        // create a project of Provider Tenant by root, root will be its owner.
        ProjectParam paramProj = new ProjectParam("project_" + new Random().nextInt());
        ProjectEntry rootProject1 = rootUser.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .header(AUTH_TOKEN_HEADER, rootToken)
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(rootProject1.name.equals(paramProj.getName()));
        Assert.assertTrue(rootProject1.id != null);

        // create a subtenant by root, root will be its TenantAdmin
        String tenantLabel = "tenant_" + new Random().nextInt();
        TenantOrgRestRep subtenant = createTenant(tenantLabel, "sanity.local", "key", tenantLabel);
        resp = assignTenantRole(subtenant.getId().toString(), SUPERUSER, "TENANT_ADMIN");
        Assert.assertEquals(200, resp.getStatus());

        // create a project under the subtenant created above, root will be its owner
        paramProj = new ProjectParam("project_" + new Random().nextInt());
        ProjectEntry rootProject2 = rootUser.path(String.format(_projectsUrlFormat, subtenant.getId().toString()))
                .header(AUTH_TOKEN_HEADER, rootToken)
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(rootProject2.name.equals(paramProj.getName()));
        Assert.assertTrue(rootProject2.id != null);

        // call /vdc/prepare-vdc
        ClientResponse response = rootUser.path("/vdc/prepare-vdc")
                .header(AUTH_TOKEN_HEADER, rootToken)
                .post(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());

        // verify root's tenant roles and project ownership be removed
        resp = rootUser.path("/user/whoami").get(ClientResponse.class);
        String output = resp.getEntity(String.class);
        Assert.assertFalse(output.contains("TENANT_ADMIN"));

        resp = superSanity.path(String.format(_projectUrl, rootProject1.id.toString())).get(ClientResponse.class);
        output = resp.getEntity(String.class);
        Assert.assertFalse(output.contains(SYSADMIN));

        resp = superSanity.path(String.format(_projectUrl, rootProject2.id.toString())).get(ClientResponse.class);
        output = resp.getEntity(String.class);
        Assert.assertFalse(output.contains(SYSADMIN));

        // test done, restore root's tenant role and remove the project
        if (bRootHasProviderTenantAdmin) {
            assignTenantRole(rootTenantId, SYSADMIN, "TENANT_ADMIN");
        }
        if (rootProject1 != null) {
            superSanity.path(String.format(_projectUrl + "/deactivate", rootProject1.id.toString()))
                    .header(AUTH_TOKEN_HEADER, superSanityToken)
                    .post(ClientResponse.class);
        }
        if (rootProject2 != null) {
            superSanity.path(String.format(_projectUrl + "/deactivate", rootProject2.id.toString()))
                    .header(AUTH_TOKEN_HEADER, superSanityToken)
                    .post(ClientResponse.class);
        }
        if (subtenant != null) {
            superSanity.path("/tenants/" + subtenant.getId() + "/deactivate")
                    .header(AUTH_TOKEN_HEADER, superSanityToken)
                    .post();
        }
    }

    private TenantOrgRestRep createTenant(String label, String domain, String attrKey, String attrValue) throws Exception {
        BalancedWebResource rootUser = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
        UserInfo info = rootUser.path("/user/whoami").get(UserInfo.class);
        String rootTenantId = info.getTenant();
        String rootToken = (String) _savedTokens.get(SYSADMIN);

        TenantCreateParam tenantParam = new TenantCreateParam();
        tenantParam.setLabel(label);
        tenantParam.setDescription("description for " + label);
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());
        UserMappingParam tenant2UserMapping = new UserMappingParam();
        tenant2UserMapping.setDomain(domain);
        UserMappingAttributeParam tenant2Attr = new UserMappingAttributeParam();
        tenant2Attr.setKey(attrKey);
        tenant2Attr.setValues(Collections.singletonList(attrValue));
        tenant2UserMapping.setAttributes(Collections.singletonList(tenant2Attr));
        tenantParam.getUserMappings().add(tenant2UserMapping);

        String subtenant_url = "/tenants/" + rootTenantId + "/subtenants";
        TenantOrgRestRep tenantOrg = rootUser.path(subtenant_url)
                .header(AUTH_TOKEN_HEADER, rootToken)
                .post(TenantOrgRestRep.class, tenantParam);

        return tenantOrg;
    }

    private ClientResponse assignTenantRole(String tenantId, String subjectId, String role) throws Exception {
        return changeTenantRoles(tenantId, subjectId, Arrays.asList(role), new ArrayList<String>());
    }

    private ClientResponse removeTenantRole(String tenantId, String subjectId, String role) throws Exception {
        return changeTenantRoles(tenantId, subjectId, new ArrayList<String>(), Arrays.asList(role));
    }

    private ClientResponse changeTenantRoles(String tenantId, String subjectId, List<String> addRoles, List<String> removeRoles)
            throws Exception {
        BalancedWebResource rootUser = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
        rootUser.path("/user/whoami").get(UserInfo.class);
        String rootToken = (String) _savedTokens.get(SYSADMIN);

        RoleAssignmentEntry roleAssignmentEntry;
        RoleAssignmentChanges roleAssignmentChanges = new RoleAssignmentChanges();

        if (!addRoles.isEmpty()) {
            List<RoleAssignmentEntry> add = new ArrayList<>();
            roleAssignmentEntry = new RoleAssignmentEntry();
            roleAssignmentEntry.setSubjectId(subjectId);
            roleAssignmentEntry.setRoles(addRoles);
            add.add(roleAssignmentEntry);
            roleAssignmentChanges.setAdd(add);
        }

        if (!removeRoles.isEmpty()) {
            List<RoleAssignmentEntry> remove = new ArrayList<>();
            roleAssignmentEntry = new RoleAssignmentEntry();
            roleAssignmentEntry.setSubjectId(subjectId);
            roleAssignmentEntry.setRoles(removeRoles);
            remove.add(roleAssignmentEntry);
            roleAssignmentChanges.setRemove(remove);
        }

        return rootUser.path("/tenants/" + tenantId + "/role-assignments")
                .header(AUTH_TOKEN_HEADER, rootToken)
                .put(ClientResponse.class, roleAssignmentChanges);
    }

    /**
     * test when creating Tenant, group's correct domain name can be trimmed automatically.
     */
    public void groupSuffixTest() throws Exception {

        String groupName = "fredTestGroup";
        String domain = "sanity.local";
        String groupNameWithDomain = groupName + "@sanity.local";
        ClientResponse response = createTenant("subTenantForGroupSuffixTest_"
                + new Random().nextInt(), domain, groupName);
        Assert.assertEquals(200, response.getStatus());

        response = createTenant("subTenantForGroupSuffixTest_"
                + new Random().nextInt(), domain, groupNameWithDomain);
        Assert.assertEquals(400, response.getStatus());
        String output = response.getEntity(String.class);
        Assert.assertTrue(output.contains("duplicated"));
    }

    private ClientResponse createTenant(String label, String domain, String groupName) throws Exception {
        BalancedWebResource rootUser = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
        UserInfo info = rootUser.path("/user/whoami").get(UserInfo.class);
        String rootTenantId = info.getTenant();
        String rootToken = (String) _savedTokens.get(SYSADMIN);

        TenantCreateParam tenantParam = new TenantCreateParam();
        tenantParam.setLabel(label);
        tenantParam.setDescription("description for " + label);
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());
        UserMappingParam tenant2UserMapping = new UserMappingParam();
        tenant2UserMapping.setDomain(domain);
        tenant2UserMapping.setGroups(new ArrayList<String>(Arrays.asList(groupName)));
        tenantParam.getUserMappings().add(tenant2UserMapping);

        String subtenant_url = "/tenants/" + rootTenantId + "/subtenants";
        ClientResponse response = rootUser.path(subtenant_url)
                .header(AUTH_TOKEN_HEADER, rootToken)
                .post(ClientResponse.class, tenantParam);

        return response;
    }

    /**
     * test tenantCreation will fail, if the authn provider is disabled
     * 
     * @throws Exception
     */
    public void disabledAuthnProviderTest() throws Exception {

        // create a disabled authn provider
        String domain = "secqe.com";
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("secqe.com");
        param.setDescription("ad apitest disabled auth provider");
        param.setDisable(true);
        param.getDomains().add(domain);
        param.setGroupAttribute("CN");
        param.setManagerDn("CN=Administrator,CN=Users,DC=secqe,DC=com");
        param.setManagerPassword(AD_PASS_WORD);
        param.setSearchBase("CN=Users,DC=secqe,DC=com");
        param.setSearchFilter("userPrincipalName=%u");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldap:\\" + AD_SERVER2_IP);
        param.setMode("ad");
        AuthnProviderRestRep resp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, param);
        Assert.assertNotNull(resp.getId());

        // create tenant against the disabled authn provider, should fail
        String groupName = "e2egroup";
        ClientResponse response = createTenant("disabled_tenant" + new Random().nextInt(), domain, groupName);
        Assert.assertEquals(400, response.getStatus());

        // enable the authn provider
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.setDisable(false);
        response = rSys.path("/vdc/admin/authnproviders/" + resp.getId()).put(ClientResponse.class, updateParam);
        Assert.assertEquals(200, response.getStatus());

        // create the tenant again, should success
        response = createTenant("disabled_tenant" + new Random().nextInt(), domain, groupName);
        Assert.assertEquals(200, response.getStatus());
    }

    // quick test to see that one can create and delete
    // a provider with no errors if there are no tenants associated
    public void loneAuthnProviderDeleteTest() throws Exception {
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("ldaps apitest config");
        param.setDescription("ldaps configuration created by ApiTest.java");
        param.setDisable(false);
        param.getDomains().add("secureldap.com");
        param.getDomains().add("someotherdomain2.com");
        param.setManagerDn("CN=Manager,DC=root,DC=com");
        param.setManagerPassword("secret");
        param.setSearchBase("OU=People,DC=root,DC=com");
        param.setSearchFilter("mail=%u");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldaps:\\" + LDAP_SERVER1_IP);
        param.setMode("ldap");
        AuthnProviderRestRep resp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class,
                param);
        Assert.assertNotNull(resp);

        // update by removing a domain should work because neither are used by any tenants
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.getDomainChanges().getRemove().add("someotherdomain2.com");
        ClientResponse response = rSys.path("/vdc/admin/authnproviders/" + resp.getId()).put(ClientResponse.class, updateParam);
        Assert.assertEquals(200, response.getStatus());

        // disable, delete, should work, because there are no tenants associated
        // with it.

        // disable it
        updateParam = new AuthnUpdateParam();
        updateParam.setDisable(true);
        response = rSys.path("/vdc/admin/authnproviders/" + resp.getId()).put(ClientResponse.class, updateParam);
        Assert.assertEquals(200, response.getStatus());

        // delete it
        response = rSys.path("/vdc/admin/authnproviders/" + resp.getId()).delete(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());

    }

    // quick test to see if the added domain of AP server is converted to all lowercase
    public void authnProviderAddDomainTest() throws Exception {
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("domain test AP server");
        param.setDescription("AP server configuration created by ApiTest.java");
        param.setDisable(false);
        param.getDomains().add("asd.locl");
        param.setManagerDn("CN=Manager,DC=root,DC=com");
        param.setManagerPassword("secret");
        param.setSearchBase("OU=People,DC=root,DC=com");
        param.setSearchFilter("mail=%u");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldaps:\\" + LDAP_SERVER1_IP);
        param.setMode("ldap");
        AuthnProviderRestRep resp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class,
                param);
        Assert.assertNotNull(resp);

        // update the AP server by adding a domain name with mixed case
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        Set<String> toAddSet = new HashSet<String>();
        toAddSet.add("sAnItY2.local");
        updateParam.getDomainChanges().setAdd(toAddSet);
        ClientResponse response = rSys.path("/vdc/admin/authnproviders/" + resp.getId()).put(ClientResponse.class, updateParam);
        Assert.assertEquals(200, response.getStatus());

        // verify the added domain name is converted to lower case
        response = rSys.path("/vdc/admin/authnproviders/" + resp.getId()).get(ClientResponse.class);
        AuthnProviderRestRep responseRestRep = response.getEntity(AuthnProviderRestRep.class);
        Assert.assertFalse(responseRestRep.getDomains().contains("sAnItY2.local"));
        Assert.assertTrue(responseRestRep.getDomains().contains("sanity2.local"));

        // use the added domain to create a subtenant, verify it's successful
        TenantCreateParam tenantParam = new TenantCreateParam();
        tenantParam.setLabel("sub2");
        tenantParam.setDescription("My sub tenant 2");

        UserMappingParam tenantMapping1 = new UserMappingParam();
        tenantMapping1.setDomain("sAnItY2.local");
        UserMappingAttributeParam attriParam = new UserMappingAttributeParam("department", Collections.singletonList("ASD"));
        tenantMapping1.getAttributes().add(attriParam);
        tenantParam.getUserMappings().add(tenantMapping1);

        response = rSys.path("/tenants/" + rootTenantId + "/subtenants").post(ClientResponse.class, tenantParam);
        Assert.assertEquals(200, response.getStatus());
    }

    /**
     * projects api tests
     */
    public void projectTests() {
        ProjectParam paramProj = new ProjectParam("aclstestproject1");
        ProjectEntry project1 = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(project1.name.equals(paramProj.getName()));
        Assert.assertTrue(project1.id != null);
        expectedProjListResults.get("st1").add(new ProjectEntry(project1));
        paramProj.setName("aclstestproject2");
        ProjectEntry project2 = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, paramProj);
        Assert.assertTrue(project2.name.equals(paramProj.getName()));
        Assert.assertTrue(project2.id != null);
        expectedProjListResults.get("st1").add(new ProjectEntry(project2));

        ACLAssignments read_assignments = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString())).get(ACLAssignments.class);
        Assert.assertTrue(read_assignments.getAssignments().isEmpty());
        ClientResponse resp = rSTAdmin2
                .path(String.format(_projectAclUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // name duplicate tests for PUTs.
        // add temp project 1
        ProjectParam tempProject = new ProjectParam("temproject");
        ProjectEntry projectTemp = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, tempProject);
        Assert.assertTrue(projectTemp.id != null);
        expectedProjListResults.get("st1").add(new ProjectEntry(projectTemp));
        // add temp project 2
        ProjectParam tempProject2 = new ProjectParam("temproject2");
        ProjectEntry projectTemp2 = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, tempProject2);
        Assert.assertTrue(projectTemp2.id != null);
        expectedProjListResults.get("st1").add(new ProjectEntry(projectTemp2));
        // attempt to modify the first project with the same name as itself. should be fine.
        ProjectUpdateParam projectUpdate1 = new ProjectUpdateParam(tempProject.getName());
        resp = rSTAdminGr1.path(String.format(_projectUrl, projectTemp.id.toString()))
                .put(ClientResponse.class, projectUpdate1);
        Assert.assertEquals(200, resp.getStatus());
        // attempt to modify the first project with the same name as itself. upper case. should be fine.
        ProjectUpdateParam projectUpdate1b = new ProjectUpdateParam(tempProject.getName().toUpperCase());
        resp = rSTAdminGr1.path(String.format(_projectUrl, projectTemp.id.toString()))
                .put(ClientResponse.class, projectUpdate1b);
        Assert.assertEquals(200, resp.getStatus());
        // put it back how it was
        ProjectUpdateParam projectUpdate1c = new ProjectUpdateParam(tempProject.getName());
        resp = rSTAdminGr1.path(String.format(_projectUrl, projectTemp.id.toString()))
                .put(ClientResponse.class, projectUpdate1c);
        Assert.assertEquals(200, resp.getStatus());

        // attempt to modify the first project with the name of the second one. Should fail.
        ProjectUpdateParam projectUpdate2 = new ProjectUpdateParam(tempProject2.getName());
        resp = rSTAdminGr1.path(String.format(_projectUrl, projectTemp.id.toString()))
                .put(ClientResponse.class, projectUpdate2);
        Assert.assertEquals(400, resp.getStatus());
        // attempt to modify the first project with the name of the second one, but upper case.
        // This should fail also, as the names are case insensitive. ( proj1 == pRoJ1 )
        ProjectUpdateParam projectUpdate3 = new ProjectUpdateParam(tempProject2.getName().toUpperCase());
        resp = rSTAdminGr1.path(String.format(_projectUrl, projectTemp.id.toString()))
                .put(ClientResponse.class, projectUpdate3);
        Assert.assertEquals(400, resp.getStatus());

        ACLAssignmentChanges changes = new ACLAssignmentChanges();
        ACLEntry entry1 = new ACLEntry();
        entry1.setSubjectId(SUBTENANT1_READER);
        entry1.setAces(new ArrayList<String>());
        entry1.getAces().add("backup");
        entry1.getAces().add("all");
        ACLEntry entry2 = new ACLEntry();
        entry2.setSubjectId(SUBTENANT1_USER);
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("all");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry1);
        changes.getAdd().add(entry2);
        resp = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        resp = rProjRead
                .path(String.format(_projectAclUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        resp = rProjRead
                .path(String.format(_projectUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        read_assignments = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString())).get(ACLAssignments.class);
        Assert.assertTrue(checkEqualsAcls(changes.getAdd(), read_assignments.getAssignments()));

        // try to add more than 100 acls - this should fail (quickly, because
        // it's not validating)

        ACLAssignments assignements = rSTAdminGr1.path(
                String.format(_projectAclUrl, project1.id.toString())).get(
                ACLAssignments.class);
        ACLAssignmentChanges tooMuchChanges = new ACLAssignmentChanges();
        tooMuchChanges.setAdd(new ArrayList<ACLEntry>());
        for (int i = 0; i < _maxRoleAclEntries + 1 - assignements.getAssignments().size() - 1; i++) {
            ACLEntry invalidEntry = new ACLEntry();
            invalidEntry.setAces(new ArrayList<String>());
            invalidEntry.getAces().add("backup");
            invalidEntry.setSubjectId("invalidUser" + i + "@invalidDomain.com");
            tooMuchChanges.getAdd().add(invalidEntry);
        }

        resp = rSTAdminGr1.path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, tooMuchChanges);

        final String message = String.format(
                "Exceeding limit of %d role assignments with %d", _maxRoleAclEntries,
                _maxRoleAclEntries + 1);
        assertExpectedError(resp, 400, ServiceCode.API_EXCEEDING_ASSIGNMENT_LIMIT,
                message);

        // full update
        entry1.getAces().remove("backup");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry1);
        changes.setRemove(new ArrayList<ACLEntry>());
        changes.getRemove().addAll(read_assignments.getAssignments());
        resp = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        read_assignments = rSTAdmin1
                .path(String.format(_projectAclUrl, project1.id.toString())).get(ACLAssignments.class);
        Assert.assertTrue(checkEqualsAcls(changes.getAdd(), read_assignments.getAssignments()));
        resp = rProjRead
                .path(String.format(_projectUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // partial update
        entry1 = new ACLEntry();
        entry1.setSubjectId(SUBTENANT1_READER);
        entry1.setAces(new ArrayList<String>());
        entry1.getAces().add("all");
        entry2 = new ACLEntry();
        entry2.setSubjectId(SUBTENANT1_READER);
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("backup");
        ACLEntry entry3 = new ACLEntry();
        entry3.setGroup(SUBTENANT1_USERS_GROUP);
        entry3.setAces(new ArrayList<String>());
        entry3.getAces().add("all");
        changes = new ACLAssignmentChanges();
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        changes.getAdd().add(entry3);
        changes.setRemove(new ArrayList<ACLEntry>());
        changes.getRemove().add(entry1);
        resp = rSTAdmin1
                .path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        read_assignments = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString())).get(ACLAssignments.class);
        ACLAssignments assignments = new ACLAssignments();
        assignments.getAssignments().add(entry2);
        entry3.setGroup(SUBTENANT1_USERS_GROUP);
        assignments.getAssignments().add(entry3);
        Assert.assertTrue(checkEqualsAcls(assignments.getAssignments(), read_assignments.getAssignments()));
        resp = rProjRead
                .path(String.format(_projectUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rProjUserGr
                .path(String.format(_projectUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // Check that a subtenant2 user who happens to be in the
        // subtenant1 users group does not have access to the project
        // in subtenant1
        resp = rSTAdminGr2
                .path(String.format(_projectUrl, project1.id.toString())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        changes = new ACLAssignmentChanges();
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry3);
        changes.setRemove(new ArrayList<ACLEntry>());
        changes.getRemove().addAll(read_assignments.getAssignments());
        resp = rSTAdmin1
                .path(String.format(_projectAclUrl, project2.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        read_assignments = rSys
                .path(String.format(_projectAclUrl, project2.id.toString())).get(ACLAssignments.class);
        Assert.assertTrue(checkEqualsAcls(changes.getAdd(), read_assignments.getAssignments()));

        // negatives - assign invalid acl
        ACLEntry entryBad = new ACLEntry();
        entryBad.setSubjectId("bad");
        entryBad.setAces(new ArrayList<String>());
        entryBad.getAces().add("bad");
        changes = new ACLAssignmentChanges();
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entryBad);
        entry1 = new ACLEntry();
        entry1.setSubjectId(SUBTENANT1_READER);
        entry1.setAces(new ArrayList<String>());
        entry1.getAces().add("backup");
        entry1.getAces().add("all");
        changes.getAdd().add(entry1);
        resp = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        entryBad.getAces().clear();
        entryBad.getAces().add("own");
        resp = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        entryBad.getAces().clear();
        entryBad.getAces().add("any");
        resp = rSTAdminGr1
                .path(String.format(_projectAclUrl, project1.id.toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());

        // batch acl assignment test - 2 users and 2 groups added at the same time
        ACLAssignments assignmentsToHaveWhenImDone =
                rSTAdmin1.path(String.format(_projectAclUrl, project1.id.toString()))
                        .get(ACLAssignments.class);
        changes = new ACLAssignmentChanges();
        changes.setRemove(assignmentsToHaveWhenImDone.getAssignments());
        entry2 = new ACLEntry();
        entry2.setSubjectId(SUBTENANT1_USER);
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("all");
        entry3 = new ACLEntry();
        entry3.setGroup(SUBTENANT1_USERS_GROUP);
        entry3.setAces(new ArrayList<String>());
        entry3.getAces().add("backup");
        ACLEntry entry4 = new ACLEntry();
        entry4.setGroup(SUBTENANT1_ADMINS_GROUP);
        entry4.setAces(new ArrayList<String>());
        entry4.getAces().add("all");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry1);
        changes.getAdd().add(entry2);
        changes.getAdd().add(entry3);
        changes.getAdd().add(entry4);

        resp =
                rSTAdmin1.path(String.format(_projectAclUrl, project1.id.toString()))
                        .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        read_assignments =
                rSTAdminGr1.path(String.format(_projectAclUrl, project1.id.toString()))
                        .get(ACLAssignments.class);
        Assert.assertTrue(checkEqualsAcls(changes.getAdd(),
                read_assignments.getAssignments()));

        // reverting all the batch acl assignment changes back to how it was
        changes =
                new ACLAssignmentChanges(assignmentsToHaveWhenImDone.getAssignments(),
                        read_assignments.getAssignments());
        resp =
                rSTAdmin1.path(String.format(_projectAclUrl, project1.id.toString()))
                        .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());

        // test lists
        ProjectList projList = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .get(ProjectList.class);
        Assert.assertTrue(checkEqualsList(projList._projects, expectedProjListResults.get("st1")));
        // read - only one project
        //
        projList = rProjRead.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .get(ProjectList.class);
        Assert.assertEquals(1, projList._projects.size());
        Assert.assertEquals(project1.id, projList._projects.get(0).id);
        Assert.assertEquals(project1.name, projList._projects.get(0).name);

        // use set on both, so we should see both
        projList = rProjUserGr.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .get(ProjectList.class);
        ArrayList<ProjectEntry> expected = new ArrayList<ProjectEntry>();
        expected.add(new ProjectEntry(project1));
        expected.add(new ProjectEntry(project2));
        Assert.assertTrue(checkEqualsList(projList._projects, expected));

        resp = rProjUserGr.path(String.format(_projectUrl + "/deactivate", project2.id.toString())).post(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        resp = rProjRead.path(String.format(_projectUrl + "/deactivate", project1.id.toString())).post(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // project update - change owner
        ProjectUpdateParam project1Updated = new ProjectUpdateParam();
        project1Updated.setOwner(SUBTENANT1_USER);
        resp = rSTAdmin1.path(String.format(_projectUrl, project1.id.toString()))
                .put(ClientResponse.class, project1Updated);
        Assert.assertEquals(200, resp.getStatus());

        // project update - change owner to a user that is not part of the project's tenant. Should fail with 400.
        ProjectUpdateParam project1UpdatedBadOwner = new ProjectUpdateParam();
        project1UpdatedBadOwner.setOwner(SUBTENANT2_ADMIN);
        resp = rSTAdmin1.path(String.format(_projectUrl, project1.id.toString()))
                .put(ClientResponse.class, project1UpdatedBadOwner);
        Assert.assertEquals(403, resp.getStatus());

        resp = rProjUserGr.path(String.format(_projectUrl + "/deactivate", project1.id.toString())).post(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // Test bad parameter is returned if the name in the project is not specified
        paramProj = new ProjectParam(null);
        resp = rTAdmin.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(400, resp.getStatus());

        // URL with bad project id
        resp = rSTAdmin1.path("/projects/null.xml").get(ClientResponse.class);
        Assert.assertEquals(404, resp.getStatus());

        // Test entity not found is returned if we try to retrieve a project that does not exist
        String getProjectUrl = "/tenants/%s/projects/%s";
        resp = rTAdmin.path(
                String.format(getProjectUrl, rootTenantId.toString(), "urn:storageos:Project:815b507c-26eb-4124-bc96-9d0400a16596:"))
                .get(ClientResponse.class);
        Assert.assertEquals(404, resp.getStatus());

        // Tests for duplicate name checks for projects
        paramProj = new ProjectParam("root project1");
        resp = rTAdmin.path(String.format(_projectsUrlFormat, rootTenantId.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(400, resp.getStatus());
        paramProj = new ProjectParam("subtenant project for name check");
        resp = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSTAdmin1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(400, resp.getStatus());
        resp = rSTAdmin2.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSTAdmin2.path(String.format(_projectsUrlFormat, subtenant2Id.toString()))
                .post(ClientResponse.class, paramProj);
        Assert.assertEquals(400, resp.getStatus());
    }

    /**
     * Cos and VirtualArray acls tests
     */
    public void usageAclTests() {
        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        rootTenantId = tenantResp.getTenant();
        String subtenant_url = "/tenants/" + rootTenantId.toString() + "/subtenants";
        TenantOrgList list = rSys.path(subtenant_url).get(TenantOrgList.class);
        Assert.assertEquals(4, list.getSubtenants().size());
        NamedRelatedResourceRep st1 = list.getSubtenants().get(0);
        NamedRelatedResourceRep st2 = list.getSubtenants().get(1);

        // create neighborhoods for test
        VirtualArrayCreateParam neighborhoodParam = new VirtualArrayCreateParam();
        neighborhoodParam.setLabel("n1");
        VirtualArrayRestRep n1 = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, neighborhoodParam);
        Assert.assertNotNull(n1.getId());
        neighborhoodParam.setLabel("n2");
        VirtualArrayRestRep n2 = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, neighborhoodParam);
        Assert.assertNotNull(n2.getId());

        // test open to all by default
        ClientResponse resp = rSTAdmin1.path("/vdc/varrays/" + n1.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSTAdmin2.path("/vdc/varrays/" + n1.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // set usage acl for st1 on n1
        String neighborAclUrl = "/vdc/varrays/%s/acl";
        ACLAssignmentChanges changes = new ACLAssignmentChanges();
        ACLEntry entry1 = new ACLEntry();
        entry1.setTenant(st1.getId().toString());
        entry1.setAces(new ArrayList<String>());
        entry1.getAces().add("USE");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry1);
        resp = rSys.path(String.format(neighborAclUrl, n1.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        VirtualArrayRestRep nRead = rSTAdmin1.path("/vdc/varrays/" + n1.getId().toString()).get(VirtualArrayRestRep.class);
        Assert.assertEquals(nRead.getId(), n1.getId());
        Assert.assertEquals(nRead.getName(), n1.getName());
        resp = rSTAdmin2.path("/vdc/varrays/" + n1.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // set usage acl for st2 on n2
        changes = new ACLAssignmentChanges();
        ACLEntry entry2 = new ACLEntry();
        entry2.setTenant(st2.getId().toString());
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("USE");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        resp = rSys.path(String.format(neighborAclUrl, n2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        nRead = rSTAdmin2.path("/vdc/varrays/" + n2.getId().toString()).get(VirtualArrayRestRep.class);
        Assert.assertEquals(nRead.getId(), n2.getId());
        Assert.assertEquals(nRead.getName(), n2.getName());
        resp = rSTAdmin1.path("/vdc/varrays/" + n2.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // negative test - invalid tenant id
        changes = new ACLAssignmentChanges();
        entry2 = new ACLEntry();
        entry2.setTenant("invalid");
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("USE");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        resp = rSys.path(String.format(neighborAclUrl, n2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());

        // negative test - missing ace
        changes = new ACLAssignmentChanges();
        entry2 = new ACLEntry();
        entry2.setTenant(st2.getId().toString());
        entry2.setAces(new ArrayList<String>());
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        resp = rSys.path(String.format(neighborAclUrl, n2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());

        // negative test - choice of tenant/group/subject_id (multiple present)
        changes = new ACLAssignmentChanges();
        entry2 = new ACLEntry();
        entry2.setTenant(st2.getId().toString());
        entry2.setGroup("TEST");
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("USE");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        resp = rSys.path(String.format(neighborAclUrl, n2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        changes = new ACLAssignmentChanges();
        entry2 = new ACLEntry();
        entry2.setTenant(st2.getId().toString());
        entry2.setSubjectId("TEST");
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("USE");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        resp = rSys.path(String.format(neighborAclUrl, n2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());
        changes = new ACLAssignmentChanges();
        entry2 = new ACLEntry();
        entry2.setTenant(st2.getId().toString());
        entry2.setGroup("TEST");
        entry2.setSubjectId("TEST");
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("USE");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        resp = rSys.path(String.format(neighborAclUrl, n2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());

        // list neighborhoods
        VirtualArrayList nList = rSTAdminGr1.path("/vdc/varrays/")
                .get(VirtualArrayList.class);
        Assert.assertEquals(1, nList.getVirtualArrays().size());
        Assert.assertEquals(n1.getId(), nList.getVirtualArrays().get(0).getId());

        // newly created varray, accessible for all
        neighborhoodParam = new VirtualArrayCreateParam();
        neighborhoodParam.setLabel("n3");
        VirtualArrayRestRep n3 = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, neighborhoodParam);
        Assert.assertNotNull(n3.getId());

        nList = rSTAdminGr1.path("/vdc/varrays/")
                .get(VirtualArrayList.class);
        Assert.assertEquals(2, nList.getVirtualArrays().size());
        Assert.assertTrue(nList.getVirtualArrays().get(0).getId().equals(n1.getId()) ||
                nList.getVirtualArrays().get(1).getId().equals(n1.getId()));
        Assert.assertTrue(nList.getVirtualArrays().get(0).getId().equals(n3.getId()) ||
                nList.getVirtualArrays().get(1).getId().equals(n3.getId()));

        // delete nh3
        rSys.path("/vdc/varrays/" + n3.getId().toString() + "/deactivate").post();

        // create vpool
        BlockVirtualPoolParam paramCosBlock = new BlockVirtualPoolParam();

        paramCosBlock.setName("foobar-block");
        paramCosBlock.setDescription("foobar-block description");
        paramCosBlock.setProtocols(new HashSet<String>());
        paramCosBlock.getProtocols().add(StorageProtocol.Block.FC.name());
        paramCosBlock.setMaxPaths(2);
        paramCosBlock.setProvisionType("Thick");
        BlockVirtualPoolRestRep cos1 = rZAdmin.path("/block/vpools").post(BlockVirtualPoolRestRep.class, paramCosBlock);
        Assert.assertNotNull(cos1.getId());
        resp = rZAdmin.path("/block/vpools").post(ClientResponse.class, paramCosBlock);
        Assert.assertEquals(400, resp.getStatus());
        resp = rSTAdmin1.path("/block/vpools/" + cos1.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSTAdmin2.path("/block/vpools/" + cos1.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // negative test: assign an empty storage pool
        VirtualPoolPoolUpdateParam paramPoolUpdate = new VirtualPoolPoolUpdateParam();
        paramPoolUpdate.setStoragePoolAssignmentChanges(new StoragePoolAssignmentChanges());
        paramPoolUpdate.getStoragePoolAssignmentChanges().setAdd(new StoragePoolAssignments());
        paramPoolUpdate.getStoragePoolAssignmentChanges().getAdd().setStoragePools(new HashSet<String>());
        paramPoolUpdate.getStoragePoolAssignmentChanges().getAdd().getStoragePools().add("");
        resp = rZAdmin.path("/block/vpools/" + cos1.getId().toString() + "/assign-matched-pools/")
                .put(ClientResponse.class, paramPoolUpdate);
        Assert.assertEquals(400, resp.getStatus());

        // Set Cos acl
        changes = new ACLAssignmentChanges();
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry1);
        resp = rSys.path(String.format(_blockCosAclUrl, cos1.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSys.path(String.format(_fileCosAclUrl, cos1.getId().toString()))
                .get(ClientResponse.class);
        Assert.assertEquals(400, resp.getStatus());
        BlockVirtualPoolRestRep cRead = rSTAdmin1.path("/block/vpools/" + cos1.getId().toString()).get(BlockVirtualPoolRestRep.class);
        Assert.assertEquals(cRead.getId(), cos1.getId());
        Assert.assertEquals(cRead.getName(), cos1.getName());
        resp = rSTAdmin2.path("/block/vpools/" + cos1.getId().toString()).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        // create second CoS
        paramCosBlock = new BlockVirtualPoolParam();
        paramCosBlock.setName("foobar-block2");
        paramCosBlock.setDescription("foobar-block2 description");
        paramCosBlock.setProtocols(new HashSet<String>());
        paramCosBlock.getProtocols().add(StorageProtocol.Block.FC.name());
        paramCosBlock.setProvisionType("Thick");
        BlockVirtualPoolRestRep cos2 = rZAdminGr.path("/block/vpools").post(BlockVirtualPoolRestRep.class, paramCosBlock);
        Assert.assertNotNull(cos2.getId());

        // list vpool
        VirtualPoolList cList = rSTAdminGr1.path("/block/vpools/")
                .get(VirtualPoolList.class);
        Assert.assertEquals(2, cList.getVirtualPool().size());
        Assert.assertTrue(cList.getVirtualPool().get(0).getId().equals(cos1.getId()) ||
                cList.getVirtualPool().get(1).getId().equals(cos1.getId()));
        Assert.assertTrue(cList.getVirtualPool().get(0).getId().equals(cos2.getId()) ||
                cList.getVirtualPool().get(1).getId().equals(cos2.getId()));
        cList = rSTAdmin2.path("/block/vpools/")
                .get(VirtualPoolList.class);
        Assert.assertEquals(1, cList.getVirtualPool().size());
        Assert.assertEquals(cos2.getId(), cList.getVirtualPool().get(0).getId());

        // test limits
        for (int i = 0; i < 100; i++) {
            changes = new ACLAssignmentChanges();
            entry1.setTenant(st2.getId().toString());
            changes.setAdd(new ArrayList<ACLEntry>());
            changes.getAdd().add(entry1);
            resp = rSys.path(String.format(_blockCosAclUrl, cos2.getId().toString()))
                    .put(ClientResponse.class, changes);
            Assert.assertEquals(200, resp.getStatus());
        }
        changes = new ACLAssignmentChanges();
        entry1.setTenant("tenant_invalid");
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry1);
        resp = rSys.path(String.format(_blockCosAclUrl, cos2.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(400, resp.getStatus());

        // testing tags
        String cosTagUrl = "/block/vpools/%s/tags";
        TagAssignment tags = new TagAssignment();
        tags.setAdd(new StringSet());
        tags.getAdd().add("testtag1");
        resp = rSTAdmin2.path(String.format(cosTagUrl, cos1.getId())).put(ClientResponse.class, tags);
        Assert.assertEquals(403, resp.getStatus());
        Tags tagsResp = rSys.path(String.format(cosTagUrl, cos1.getId())).put(Tags.class, tags);
        Assert.assertTrue(tagsResp.getTag().equals(tags.getAdd()));
        tags.setRemove(new StringSet());
        tags.getRemove().addAll(new HashSet(tags.getAdd()));
        tags.getAdd().add("t"); // invalid tag, too short
        resp = rSys.path(String.format(cosTagUrl, cos1.getId())).put(ClientResponse.class, tags);
        Assert.assertEquals(400, resp.getStatus());
        tags.getAdd().clear();
        tags.getAdd().add("tag" + STR144); // invalid tag, too long
        resp = rSys.path(String.format(cosTagUrl, cos1.getId())).put(ClientResponse.class, tags);
        Assert.assertEquals(400, resp.getStatus());
        tags.getAdd().clear();
        tags.getAdd().add(" testtag  "); // tags should be trimmed
        tagsResp = rSys.path(String.format(cosTagUrl, cos1.getId())).put(Tags.class, tags);
        Assert.assertTrue(tagsResp.getTag().equals(new StringSet() {
            {
                add("testtag");
            }
        }));
        resp = rSTAdmin2.path(String.format(cosTagUrl, cos1.getId())).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        resp = rSTAdmin1.path(String.format(cosTagUrl, cos1.getId())).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // Test bad parameter is returned if we add an invalid varray while creating the VirtualPool
        FileVirtualPoolParam paramFileCos = new FileVirtualPoolParam();
        paramFileCos.setName("Generic File VirtualPool");
        paramFileCos.setProtocols(new HashSet<String>());
        paramFileCos.getProtocols().add(StorageProtocol.File.NFS.name());
        paramFileCos.getProtocols().add(StorageProtocol.File.CIFS.name());
        paramFileCos.setVarrays(new HashSet<String>());
        paramFileCos.getVarrays().add("IDontExist");
        resp = rZAdmin.path("/file/vpools").post(ClientResponse.class, paramFileCos);
        Assert.assertEquals(400, resp.getStatus());

        // below is vpool restricted to tenant test

        /*
         * test setup:
         * create a varray and vpool and associate the vpool with the varray
         * restrict the vpool to the tenant
         */

        String vaLabel = "va-testTenantRestrictAccess-" + Calendar.getInstance().getTime().getTime();
        String vpLabel = "vp-testTenantRestrictAccess-" + Calendar.getInstance().getTime().getTime();

        // create a varray
        VirtualArrayCreateParam vaParam = new VirtualArrayCreateParam();

        vaParam.setLabel(vaLabel);
        BlockSettings bs = new BlockSettings();
        bs.setAutoSanZoning(true);
        vaParam.setBlockSettings(bs);

        VirtualArrayRestRep va1 = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, vaParam);

        // create a vpool associated with the varray
        BlockVirtualPoolParam vpParam = new BlockVirtualPoolParam();
        vpParam.setName(vpLabel);
        vpParam.setDescription(vpLabel);

        Set<String> vas = new HashSet<String>();
        vas.add(va1.getId().toString());
        vpParam.setVarrays(vas);

        vpParam.setProvisionType("Thin");

        Set<String> protos = new HashSet();
        protos.add("FC");
        vpParam.setProtocols(protos);

        BlockVirtualPoolRestRep vp1 = rSys.path("/block/vpools").post(BlockVirtualPoolRestRep.class, vpParam);

        // restrict the vpool to a tenant

        ACLAssignmentChanges aclChange = new ACLAssignmentChanges();
        List<ACLEntry> acls = new ArrayList<>();
        ACLEntry acl = new ACLEntry();
        acl.setTenant(subtenant2Id.toString());
        acl.setAces(new ArrayList<String>(Arrays.asList("USE")));
        acls.add(acl);
        aclChange.setAdd(acls);

        String uri = String.format("/block/vpools/%s/acl", vp1.getId());
        ACLAssignments aclAssignments = rSys.path(uri).put(ACLAssignments.class, aclChange);

        // test1: sysadmin can see vpool
        // test2: sysmonitor can see vpool
        String vpUri = String.format("/vdc/varrays/%s/vpools", va1.getId().toString());
        VirtualPoolList vpoolList = rSys.path(vpUri).get(VirtualPoolList.class);
        List<NamedRelatedVirtualPoolRep> vpools = vpoolList.getVirtualPool();

        boolean foundVpool = false;

        for (NamedRelatedVirtualPoolRep vpool : vpools) {
            if (vpool.getId().equals(vp1.getId())) {
                foundVpool = true;
                _log.info("user root can see the vpool {}", vp1.getName());
            }
        }

        Assert.assertTrue(foundVpool);

        // test3: tenant user can see vpool
        VirtualPoolList vpoolList2 = rST2User.path(vpUri).get(VirtualPoolList.class);
        List<NamedRelatedVirtualPoolRep> vpools2 = vpoolList2.getVirtualPool();

        foundVpool = false;

        for (NamedRelatedVirtualPoolRep vpool : vpools2) {
            if (vpool.getId().equals(vp1.getId())) {
                foundVpool = true;
                _log.info("user st2user can see the vpool {}", vp1.getName());
            }
        }

        Assert.assertTrue(foundVpool);

    }

    private void zoneCosSetup() {
        VirtualArrayList nList = rSTAdmin1.path("/vdc/varrays/")
                .get(VirtualArrayList.class);
        Assert.assertEquals(1, nList.getVirtualArrays().size());
        _nh = nList.getVirtualArrays().get(0).getId();
        _log.info("varray: " + _nh.toString());
        NetworkCreate param = new NetworkCreate();
        param.setTransportType("IP");
        param.setLabel("iptz");
        _iptzone = rZAdmin
                .path(String.format("/vdc/varrays/%s/networks", _nh).toString())
                .post(NetworkRestRep.class, param);

        NetworkCreate fctzone = new NetworkCreate();
        fctzone.setTransportType("FC");
        fctzone.setLabel("fctz");
        _fctzone = rZAdmin
                .path(String.format("/vdc/varrays/%s/networks", _nh).toString())
                .post(NetworkRestRep.class, fctzone);

        FileVirtualPoolParam paramCosFile = new FileVirtualPoolParam();
        paramCosFile.setName("isilon-file");
        paramCosFile.setProtocols(new HashSet<String>());
        paramCosFile.getProtocols().add(StorageProtocol.File.NFS.name());
        _cosFile = rZAdmin.path("/file/vpools").post(FileVirtualPoolRestRep.class, paramCosFile);

        BlockVirtualPoolParam paramCosBlock = new BlockVirtualPoolParam();

        paramCosBlock.setName("vnx-block");
        paramCosBlock.setProtocols(new HashSet<String>());
        paramCosBlock.getProtocols().add(StorageProtocol.Block.FC.name());
        paramCosBlock.setProvisionType("Thick");
        paramCosBlock.setMaxPaths(2);
        _cosBlock = rZAdminGr.path("/block/vpools").post(BlockVirtualPoolRestRep.class, paramCosBlock);

        ACLAssignmentChanges changes = new ACLAssignmentChanges();
        changes.setAdd(new ArrayList<ACLEntry>());
        ACLEntry entry1 = new ACLEntry();
        entry1.setTenant(subtenant1Id.toString());
        entry1.setAces(new ArrayList<String>());
        entry1.getAces().add("USE");
        changes.getAdd().add(entry1);
        ClientResponse resp = rSys.path(String.format(_blockCosAclUrl, _cosBlock.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
        resp = rSys.path(String.format(_fileCosAclUrl, _cosFile.getId().toString()))
                .put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void projectSetup() {
        ProjectParam paramProj = new ProjectParam("resourcetestproject");
        ProjectElement project1 = rSTAdminGr1.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectElement.class, paramProj);
        _testProject = project1.getId();

        ACLEntry entry = new ACLEntry();
        entry.setSubjectId(SUBTENANT1_READER);
        entry.setAces(new ArrayList<String>());
        entry.getAces().add("backup");
        ACLEntry entry2 = new ACLEntry();
        entry2.setGroup(SUBTENANT1_USERS_GROUP);
        entry2.setAces(new ArrayList<String>());
        entry2.getAces().add("all");
        ACLAssignmentChanges changes = new ACLAssignmentChanges();
        changes.setAdd(new ArrayList<ACLEntry>());
        changes.getAdd().add(entry2);
        changes.getAdd().add(entry);
        ClientResponse resp = rSTAdmin1
                .path(String.format(_projectAclUrl, _testProject.toString()))
                .post(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void deviceSetup() throws InterruptedException {
        // Create a isilon device - file
        StorageSystemRestRep isilonDevice = createIsilonDevice();

        // Update the discovered Isilon storage ports to set the transport zone.
        updateAllIsilonPorts(isilonDevice);

        // Create a VNXBlock SMISProvider
        SMISProviderRestRep provider = createSMISProvider();

        // Update the discovered VNX/VMAX storage ports to set the transport zone.
        updateAllVnxAndVmaxPorts(provider);
    }

    /**
     * Update the discovered VNX/VMAX storage ports to set the transport zone.
     * 
     * @param provider : Provider.
     */
    private void updateAllVnxAndVmaxPorts(SMISProviderRestRep provider) {
        StorageSystemList systemList = rZAdmin
                .path("/vdc/storage-systems")
                .get(StorageSystemList.class);
        for (RelatedResourceRep resRep : systemList.getStorageSystems()) {
            StorageSystemRestRep system = rZAdmin
                    .path(String.format("/vdc/storage-systems/%s",
                            resRep.getId()).toString()).get(StorageSystemRestRep.class);
            if (system.getSystemType().equals(Type.vnxblock.toString()) ||
                    system.getSystemType().equals(Type.vmax.toString())) {

                // Register all the discovered storage ports .
                StoragePortList vnxBlockPortList = rZAdmin
                        .path(String.format("/vdc/storage-systems/%s/storage-ports",
                                system.getId()).toString())
                        .get(StoragePortList.class);
                List<NamedRelatedResourceRep> vnxBlockPortURIList = vnxBlockPortList.getPorts();
                for (RelatedResourceRep portURI : vnxBlockPortURIList) {
                    updateStoragePortTZ(resRep.getId(), portURI);
                }
            }
        }
    }

    /**
     * Create a VNXBlock SMISProvider.
     * 
     * @return SMISProviderRestRep : provider.
     * @throws InterruptedException
     */
    private SMISProviderRestRep createSMISProvider() throws InterruptedException {
        // vnxblock
        SMISProviderCreateParam providerParam = new SMISProviderCreateParam();
        providerParam.setName("VNXBlock_Provider");
        providerParam.setIpAddress(EnvConfig.get("sanity", "smis.ip"));
        providerParam.setPortNumber(5988);
        providerParam.setUserName("admin");
        providerParam.setPassword("#1Password");
        providerParam.setUseSSL(false);
        TaskResourceRep task = rZAdmin.path("/vdc/smis-providers")
                .post(TaskResourceRep.class, providerParam);
        String opId = task.getOpId();
        Assert.assertNotNull(opId);
        NamedRelatedResourceRep providerLink = task.getResource();
        Assert.assertNotNull(providerLink);

        // wait upto ~3 minute for SMIS provider scan
        int checkCount = 18;
        String status;
        do {
            Thread.sleep(10000);
            TaskResourceRep taskResp = rZAdmin.path(String.format("/vdc/smis-providers/%s/tasks/%s", providerLink.getId(), opId))
                    .get(TaskResourceRep.class);
            status = taskResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);

        if (!status.equals("ready")) {
            Assert.assertTrue("Failed to create SMIS provider: time out", false);
        }
        SMISProviderRestRep provider = rZAdmin.path(String.format("/vdc/smis-providers/%s", providerLink.getId()))
                .get(SMISProviderRestRep.class);
        Assert.assertNotNull(provider);
        _log.info("Scanned SMI-S Provider : " + providerLink.getId());

        // Discover SMIS Provider:
        TaskList tasks = rZAdmin.path("/vdc/storage-systems/discover")
                .post(TaskList.class, providerParam);
        // wait upto ~10 minute for discover
        checkCount = 60;
        for (TaskResourceRep taskRep : tasks.getTaskList()) {
            opId = taskRep.getOpId();
            Assert.assertNotNull(opId);
            providerLink = taskRep.getResource();
            Assert.assertNotNull(providerLink);

            boolean success = monitorDiscoveredObjectTask(StorageSystemRestRep.class, taskRep);
            if (!success) {
                Assert.assertTrue("Failed to discover system : " + providerLink.getId().toString(), false);
            }

        }
        _log.info(" Discover for all devices is completed");
        return provider;
    }

    private <T extends DiscoveredSystemObjectRestRep>
            boolean monitorDiscoveredObjectTask(Class<T> clazz, TaskResourceRep taskRep)
                    throws InterruptedException {
        int checkCount = 60;
        boolean ready = false;
        boolean success = true;

        for (; checkCount > 0;) {

            TaskResourceRep curTask = rZAdmin.path(taskRep.getLink().getLinkRef().toString())
                    .get(TaskResourceRep.class);
            String status = curTask.getState();
            if (status.equals("pending")) {
                Thread.sleep(10000);
                checkCount--;
            }
            else if (status.equals("error")) {
                // first check if the discovery already ran for this object
                do {
                    T resource = rZAdmin.path(taskRep.getResource().getLink().getLinkRef().toString()).
                            get(clazz);
                    String resourceStatus = resource.getDiscoveryJobStatus();
                    if (resourceStatus.equalsIgnoreCase("IN_PROGRESS")) {
                        Thread.sleep(10000);
                        checkCount--;
                    }
                    else if (resourceStatus.equalsIgnoreCase("COMPLETE")) {
                        ready = true;
                        break;
                    }
                    else {
                        ready = true;
                        success = false;
                        break;
                    }
                } while (checkCount > 0);
            }
            else {
                ready = true;
            }

            if (ready) {
                break;
            }
        }
        return success;
    }

    /**
     * Update the discovered Isilon storage ports to set the transport zone.
     * 
     * @param isilonDevice : Isilon Device.
     */
    private void updateAllIsilonPorts(StorageSystemRestRep isilonDevice) {

        // Register all the discovered storage ports .
        StoragePortList portList = rZAdmin
                .path(String.format("/vdc/storage-systems/%s/storage-ports",
                        isilonDevice.getId()).toString())
                .get(StoragePortList.class);
        List<NamedRelatedResourceRep> portURIList = portList.getPorts();

        for (RelatedResourceRep portURI : portURIList) {
            updateStoragePortTZ(isilonDevice.getId(), portURI);
        }
    }

    /**
     * create a Isilon device.
     * 
     * @return
     * @throws InterruptedException
     */
    private StorageSystemRestRep createIsilonDevice() throws InterruptedException {
        StorageSystemRequestParam param = new StorageSystemRequestParam();
        param.setSystemType("isilon");
        param.setIpAddress(EnvConfig.get("sanity", "isilon.ip"));
        param.setPortNumber(8080);
        param.setUserName("root");
        param.setPassword("a");
        param.setSerialNumber("6805ca00ad441c3ca650a5087c0bd1674ce2");

        TaskResourceRep task = rZAdmin.path("/vdc/storage-systems")
                .post(TaskResourceRep.class, param);
        String opId = task.getOpId();
        Assert.assertNotNull(opId);
        NamedRelatedResourceRep deviceLink = task.getResource();
        Assert.assertNotNull(deviceLink);

        // wait upto ~3 minute for SMIS provider scan
        int checkCount = 18;
        String status;
        do {
            Thread.sleep(10000);
            TaskResourceRep taskResp = rZAdmin.path(String.format("/vdc/storage-systems/%s/tasks/%s", deviceLink.getId(), opId))
                    .get(TaskResourceRep.class);
            status = taskResp.getState();
        } while (status.equals("pending") && checkCount-- > 0);

        if (!status.equals("ready")) {
            Assert.assertTrue("Failed to create isilon device: time out", false);
        }
        StorageSystemRestRep dev1 = rZAdmin.path(String.format("/vdc/storage-systems/%s", deviceLink.getId()))
                .get(StorageSystemRestRep.class);
        Assert.assertNotNull(dev1);
        _log.info("Discover for device is complete : " + deviceLink.getId());
        return dev1;
    }

    /**
     * Updates the storage port to set the transport zone.
     * 
     * @param systemURI The storage system URI
     * @param portURI The storage port URI
     */
    private void updateStoragePortTZ(URI systemURI, RelatedResourceRep portURI) {

        StoragePortRestRep portRep = rZAdmin.path(
                String.format("/vdc/storage-systems/%s/storage-ports/%s", systemURI,
                        portURI.getId()).toString()).get(StoragePortRestRep.class);

        StoragePortUpdate updateParam = new StoragePortUpdate();
        if (portRep.getTransportType().equals(Block.FC.toString())) {
            updateParam.setNetwork(_fctzone.getId());
        } else if (portRep.getTransportType().equals("IP")) {
            updateParam.setNetwork(_iptzone.getId());
        }

        ClientResponse resp = rZAdmin.path(
                String.format("/vdc/storage-ports/%s", portRep.getId()).toString()).put(
                ClientResponse.class, updateParam);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void checkFSCreate(BalancedWebResource user, boolean good, boolean dup)
            throws Exception {
        FileSystemParam fsparam = new FileSystemParam();
        fsparam.setVpool(_cosFile.getId());
        fsparam.setLabel("test-fs-" + System.currentTimeMillis());
        fsparam.setVarray(_nh);
        fsparam.setSize("20480000");
        if (good) {
            TaskResourceRep resp = user.path("/file/filesystems/")
                    .queryParam("project", _testProject.toString())
                    .post(TaskResourceRep.class, fsparam);
            Assert.assertNotNull(resp.getOpId());
            Assert.assertNotNull(resp.getResource());

            _fs = resp.getResource().getId();
            String fsId = _fs.toString();
            String opId = resp.getOpId();
            int checkCount = 1200;
            String status;
            do {
                // wait upto ~2 minute for fs creation
                Thread.sleep(100);
                resp = user.path(String.format("/file/filesystems/%s/tasks/%s", fsId, opId))
                        .get(TaskResourceRep.class);
                status = resp.getState();
            } while (status.equals("pending") && checkCount-- > 0);
            if (!status.equals("ready")) {
                Assert.assertTrue("Fileshare create timed out", false);
            }
            if (dup) {
                ClientResponse response = user.path("/file/filesystems")
                        .queryParam("project", _testProject.toString())
                        .post(ClientResponse.class, fsparam);
                Assert.assertEquals(400, response.getStatus());
            }
        } else {
            ClientResponse resp = user.path("/file/filesystems")
                    .queryParam("project", _testProject.toString())
                    .post(ClientResponse.class, fsparam);
            Assert.assertEquals(403, resp.getStatus());
        }
    }

    private void checkFSCreate(BalancedWebResource user, boolean good) throws Exception {
        checkFSCreate(user, good, false);
    }

    private void checkSnapCreate(BalancedWebResource user, boolean good) {
        FileSystemSnapshotParam param = new FileSystemSnapshotParam();
        param.setLabel("test-fs-snap-" + System.currentTimeMillis());
        String snapCreateURL = String.format("/file/filesystems/%s/snapshots", _fs);
        if (good) {
            TaskResourceRep resp = user.path(snapCreateURL)
                    .post(TaskResourceRep.class, param);
            Assert.assertNotNull(resp.getOpId());
            Assert.assertNotNull(resp.getResource());
        } else {
            ClientResponse resp = user.path(snapCreateURL)
                    .post(ClientResponse.class, param);
            Assert.assertEquals(403, resp.getStatus());
        }
    }
    
    private void checkFsReduce(BalancedWebResource user, boolean good) {
    	FileSystemReduceParam param = new FileSystemReduceParam();
    	
    	param.setNewSize("1GB");
        
    	String fsReduceURL = String.format("/file/filesystems/%s/reduce", _fs);
        if (good) {
            TaskResourceRep resp = user.path(fsReduceURL)
                    .post(TaskResourceRep.class, param);
            Assert.assertNotNull(resp.getOpId());
            Assert.assertNotNull(resp.getResource());
        } else {
            ClientResponse resp = user.path(fsReduceURL)
                    .post(ClientResponse.class, param);
            Assert.assertEquals(403, resp.getStatus());
        }
    }
    
    private void checkFSReduce(BalancedWebResource user, boolean good) throws Exception {
    	checkFsReduce(user, good);
    }

    private void fileTests() throws Exception {
        // tenant admin, project owner and project user can create fs
        checkFSCreate(rSTAdmin1, true);
        checkFSCreate(rSTAdmin1, true, true);
        checkFSCreate(rSTAdminGr1, true);
        checkFSCreate(rProjUserGr, true);

        // root tenant admin, secadmin, unauth can not create fs or snap
        checkFSCreate(rProjRead, false);
        checkFSCreate(rZAdmin, false);
        checkFSCreate(rSys, false);
        checkFSCreate(rUnAuth, false);
        
        
        checkFSReduce(rSTAdmin1, true);
        checkFSReduce(rSTAdminGr1, true);
        checkFSReduce(rProjUserGr, true);
        
        checkFSReduce(rProjRead, false);
        checkFSReduce(rZAdmin, false);
        
        

        // tenant admin, project owner and project user/backup can create snapshot
        checkSnapCreate(rSTAdmin1, true);
        checkSnapCreate(rSTAdminGr1, true);
        checkSnapCreate(rProjUserGr, true);
        checkSnapCreate(rProjRead, true);
        checkSnapCreate(rZAdmin, false);
        checkSnapCreate(rSys, false);
        checkSnapCreate(rUnAuth, false);
    }

    private void checkVolumeCreate(BalancedWebResource user, boolean good, boolean dup) throws Exception {
        VolumeCreate param = new VolumeCreate();
        param.setVpool(_cosBlock.getId());
        param.setName("test_volume_" + System.currentTimeMillis());
        param.setVarray(_nh);
        param.setSize("307200000");
        param.setProject(_testProject);
        if (_group != null) {
            param.setConsistencyGroup(_group);
        }
        if (good) {
            TaskList resp = user.path("/block/volumes")
                    .post(TaskList.class, param);

            Assert.assertTrue(resp.getTaskList().size() == 1);
            Assert.assertNotNull(resp.getTaskList().get(0).getOpId());
            Assert.assertNotNull(resp.getTaskList().get(0).getResource().getId());
            _volume = resp.getTaskList().get(0).getResource().getId();
            String volumeId = _volume.toString();
            String opId = resp.getTaskList().get(0).getOpId();
            int checkCount = 1200;
            String status;
            do {
                // wait upto ~2 minute for volume creation
                Thread.sleep(100);
                TaskResourceRep taskResp = user.path(String.format("/block/volumes/%s/tasks/%s", volumeId, opId))
                        .get(TaskResourceRep.class);
                status = taskResp.getState();
            } while (status.equals("pending") && checkCount-- > 0);
            if (!status.equals("ready")) {
                Assert.assertTrue("Volume create timed out", false);
            }
            if (dup) {
                // create the volume with same name again.
                ClientResponse response = user.path("/block/volumes")
                        .post(ClientResponse.class, param);
                Assert.assertEquals(400, response.getStatus());
            }
        } else {
            ClientResponse resp = user.path("/block/volumes")
                    .post(ClientResponse.class, param);
            Assert.assertEquals(403, resp.getStatus());
        }
    }

    private void checkVolumeCreate(BalancedWebResource user, boolean good) throws Exception {
        checkVolumeCreate(user, good, false);
    }

    private void checkCreateConsistencyGroup(BalancedWebResource user, boolean good)
            throws InterruptedException {
        final BlockConsistencyGroupCreate param = new BlockConsistencyGroupCreate();
        param.setName("test_group_" + System.currentTimeMillis());
        param.setProject(_testProject);
        if (good) {
            TaskList resp = user.path("/block/consistency-groups").post(TaskList.class, param);

            Assert.assertTrue(resp.getTaskList().size() == 1);
            Assert.assertNotNull(resp.getTaskList().get(0).getOpId());
            Assert.assertNotNull(resp.getTaskList().get(0).getResource().getId());
            _group = resp.getTaskList().get(0).getResource().getId();
            String groupId = _group.toString();
            String opId = resp.getTaskList().get(0).getOpId();
            int checkCount = 1200;
            String status;
            do {
                // wait upto ~2 minute for group creation
                Thread.sleep(100);
                TaskResourceRep taskResp = user.path(
                        String.format("/block/consistency-groups/%s/tasks/%s", groupId, opId))
                        .get(TaskResourceRep.class);
                status = taskResp.getState();
            } while (status.equals("pending") && checkCount-- > 0);
            if (!status.equals("ready")) {
                Assert.assertTrue("ConsistencyGroup create timed out", false);
            }
        } else {
            ClientResponse resp = user.path("/block/consistency-groups")
                    .post(ClientResponse.class, param);
            Assert.assertEquals(403, resp.getStatus());
        }
    }

    private void blockTests() throws Exception {
        // try creating the consistency group first, then create and add volume
        // to the group

        // tenant admin, project owner and project user can create fs
        checkCreateConsistencyGroup(rSTAdmin1, true);
        checkVolumeCreate(rSTAdmin1, true);
        checkVolumeCreate(rSTAdmin1, true, true);
        checkCreateConsistencyGroup(rSTAdminGr1, true);
        checkVolumeCreate(rSTAdminGr1, true);
        checkCreateConsistencyGroup(rProjUserGr, true);
        checkVolumeCreate(rProjUserGr, true);

        // root tenant admin, secadmin, unauth can not create fs or snap
        checkCreateConsistencyGroup(rProjRead, false);
        checkVolumeCreate(rProjRead, false);
        checkCreateConsistencyGroup(rZAdmin, false);
        checkVolumeCreate(rZAdmin, false);
        checkCreateConsistencyGroup(rSys, false);
        checkVolumeCreate(rSys, false);
        checkCreateConsistencyGroup(rUnAuth, false);
        checkVolumeCreate(rUnAuth, false);
    }

    /**
     * Project resource tests
     */
    public void projectResourceTests() throws Exception {
        zoneCosSetup();
        projectSetup();
        deviceSetup();
        // The storage pools and devices are not set yet to create files/volumes.
        // Still needs to be fixed.
        // fileTests();
        // blockTests();
    }

    /*
     * the secret key service is no longer at this address
     * commenting out for now
     * private void testSecretKeysTest() throws Exception {
     * BalancedWebResource keyUser = rSys;
     * String keyServicePath = "/secret-keys/stadmin@subtenant1.com";
     * // first clear old keys (if there are such keys
     * 
     * ClientResponse resp = keyUser.path(keyServicePath + "/all")
     * .delete(ClientResponse.class);
     * Assert.assertEquals(200, resp.getStatus());
     * 
     * // Verify that the user "stadmin" does not have a key
     * resp = keyUser.path(keyServicePath)
     * .get(ClientResponse.class);
     * Assert.assertEquals(400, resp.getStatus());
     * 
     * //create the first key for the user stadmin
     * SecretKeyInfoRep keyInfo1 = keyUser.path(keyServicePath)
     * .post(SecretKeyInfoRep.class);
     * Assert.assertFalse(keyInfo1._secreteKey.equals(""));
     * SecretKeyRestRep userKeys = keyUser.path(keyServicePath)
     * .get(SecretKeyRestRep.class);
     * Assert.assertEquals(keyInfo1._secreteKey,userKeys._secreteKey1);
     * Assert.assertEquals(keyInfo1._secreteKeyTimestamp,userKeys._secreteKeyTimestamp1);
     * Assert.assertEquals(userKeys._secreteKey2,"");
     * 
     * //create additional key
     * SecretKeyInfoRep keyInfo2 = keyUser.path(keyServicePath)
     * .post(SecretKeyInfoRep.class);
     * Assert.assertFalse(keyInfo1._secreteKey.equals(""));
     * userKeys = keyUser.path(keyServicePath)
     * .get(SecretKeyRestRep.class);
     * Assert.assertEquals(keyInfo2._secreteKey,userKeys._secreteKey2);
     * Assert.assertEquals(keyInfo2._secreteKeyTimestamp,userKeys._secreteKeyTimestamp2);
     * 
     * // No more keys can be create for the user
     * resp = keyUser.path(keyServicePath)
     * .post(ClientResponse.class);
     * Assert.assertEquals(400, resp.getStatus());
     * 
     * // Delete the first key and check that only 1 left in the DB
     * SecretKeyService.KeyDeleteParam deleteParam = new SecretKeyService.KeyDeleteParam();
     * deleteParam.key_timestamp = keyInfo1._secreteKeyTimestamp;
     * resp = keyUser.path(keyServicePath)
     * .delete(ClientResponse.class,deleteParam);
     * Assert.assertEquals(200, resp.getStatus());
     * 
     * userKeys = keyUser.path(keyServicePath)
     * .get(SecretKeyRestRep.class);
     * Assert.assertEquals(userKeys._secreteKeyTimestamp1,keyInfo2._secreteKeyTimestamp);
     * Assert.assertEquals(userKeys._secreteKey2,"");
     * 
     * //delete all key again
     * resp = keyUser.path(keyServicePath + "/all")
     * .delete(ClientResponse.class);
     * Assert.assertEquals(200, resp.getStatus());
     * }
     */
    private void testOtherBadParameterErrors() {
        ClientResponse resp = null;

        // Test bad parameter is returned if we attempt to create a StorageSystem of an unsupported type
        StorageSystemRequestParam storageSystemParam = new StorageSystemRequestParam();
        storageSystemParam.setSystemType("AMadeUpOne");
        storageSystemParam.setIpAddress("10.64.213.226");
        storageSystemParam.setPortNumber(8080);
        storageSystemParam.setUserName("MyUser");
        storageSystemParam.setPassword("MyPassword");
        storageSystemParam.setSerialNumber("1");
        resp = rZAdmin.path("/vdc/storage-systems").post(ClientResponse.class, storageSystemParam);
        Assert.assertEquals(400, resp.getStatus());
    }

    private void testOtherEntityNotFoundErrors() {
        ClientResponse resp = null;

        // Test entity not found is returned if we try to deactivate a snapshot that does not exist
        String deactivateSnapshotUrl = "/block/snapshots/%s/deactivate";
        resp = rTAdmin.path(String.format(deactivateSnapshotUrl, "urn:storageos:Snapshot:815b507c-26eb-4124-bc96-9d0400a16596:")).post(
                ClientResponse.class);
        Assert.assertEquals(404, resp.getStatus());

        // Test entity not found is returned if we request the list of storage pools for a vpool that does not exist
        String getStoragePoolsUrl = "/block/vpools/%s/storage-pools";
        resp = rZAdmin.path(String.format(getStoragePoolsUrl, "urn:storageos:VirtualPool:815b507c-26eb-4124-bc96-9d0400a16596:")).get(
                ClientResponse.class);
        Assert.assertEquals(404, resp.getStatus());
    }

    // TODO: to be moved in another test suite
    public void testVDCs() {
        // This disables vdc test altogether on devkit
        // TODO: once devkit gets switched to 1+0 appliance, we should enable it again.
        if (System.getenv("APP_HOST_NAMES").equals("localhost")) {
            return;
        }

        VirtualDataCenterAddParam addParam = new VirtualDataCenterAddParam();
        addParam.setApiEndpoint("http://apitest");
        addParam.setSecretKey("apitestSecret");
        addParam.setCertificateChain("apitestCertchain");
        addParam.setName("apitestName" + System.currentTimeMillis());

        // TODO: enhance to track task progress
        // root should NOT do this.
        ClientResponse rsp = rSys.path("/vdc").post(ClientResponse.class, addParam);
        Assert.assertEquals(403, rsp.getStatus());

        // use super admin with geo securityAdmin role to do post vdc

        // assign geo securityadmin to superuser.
        RoleAssignmentChanges changes = new RoleAssignmentChanges();
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        RoleAssignmentEntry entry1 = new RoleAssignmentEntry();
        entry1.setSubjectId(SUPERUSER);
        entry1.getRoles().add("SECURITY_ADMIN");

        changes.getAdd().add(entry1);

        ClientResponse rsp1 = rSys.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(200, rsp1.getStatus());

        // then do post VDC using superuser. should pass.
        TaskResourceRep taskRep = rZAdminGr.path("/vdc").post(TaskResourceRep.class, addParam);
        Assert.assertNotNull("vdc create task should not be null", taskRep);

        VirtualDataCenterList vdcList = rSys.path("/vdc").get(VirtualDataCenterList.class);
        Assert.assertNotNull("vdcList should not be null", vdcList);
        Assert.assertNotNull("vdcList.getVirtualDataCenters should not be null", vdcList.getVirtualDataCenters());

        // boolean found = false;
        // for (NamedRelatedResourceRep vdcResource : vdcList.getVirtualDataCenters()) {
        // if (vdcResource.getName().equals(addParam.getName())) {
        // found = true;
        // }
        // }
        // Assert.assertTrue("newly created vdc could not be found in vdc list", found);

        VirtualDataCenterRestRep vdc = rZAdminGr.path("/vdc/" + taskRep.getResource().getId()).get(VirtualDataCenterRestRep.class);
        Assert.assertNotNull("created vdc object can't be retrieved", vdc);
        Assert.assertTrue("vdc name does not match", vdc.getName().equals(addParam.getName()));

        // TODO: enhance to track task progress

        ClientResponse response = rZAdminGr.path("/vdc/" + vdc.getId() + "/disconnect").post(ClientResponse.class);
        Assert.assertEquals(405, response.getStatus());

        // TODO: enhance to track task progress
        response = rZAdminGr.path("/vdc/" + vdc.getId() + "/reconnect").post(ClientResponse.class);
        Assert.assertEquals(405, response.getStatus());

        // TODO: enhance to track task progress
        taskRep = rZAdminGr.path("/vdc/" + vdc.getId()).delete(TaskResourceRep.class);
        Assert.assertNotNull("vdc delete task should not be null", taskRep);
    }

    /**
     * Basic test for vdc secret key api
     */
    public void testVDCSecretKey() {
        VirtualDataCenterSecretKeyRestRep resp = rSys.path("/vdc/secret-key").get(VirtualDataCenterSecretKeyRestRep.class);
        Assert.assertNotNull(resp);
        String encodedKey = resp.getSecretKey();
        SecretKey decodedKey = SignatureHelper.createKey(encodedKey, "HmacSHA256");
        Assert.assertNotNull(decodedKey);
        Assert.assertTrue(decodedKey.getAlgorithm().equals("HmacSHA256"));
        String data = "some data";
        String signature = SignatureHelper.sign2(data, decodedKey, decodedKey.getAlgorithm());

        // do it again. Make sure this is the same key.
        resp = rSys.path("/vdc/secret-key").get(VirtualDataCenterSecretKeyRestRep.class);
        Assert.assertNotNull(resp);
        String encodedKey2 = resp.getSecretKey();
        Assert.assertTrue(encodedKey.equals(encodedKey2));
        SecretKey decodedKey2 = SignatureHelper.createKey(encodedKey2, "HmacSHA256");
        Assert.assertNotNull(encodedKey2);
        String signature2 = SignatureHelper.sign2(data, decodedKey2, decodedKey2.getAlgorithm());
        Assert.assertTrue(signature.equals(signature2));
    }
}
