/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.emc.storageos.services.util.EnvConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.keystore.TrustedCertificateChanges;
import com.emc.vipr.model.keystore.TrustedCertificates;
import com.emc.vipr.model.keystore.TruststoreSettings;
import com.emc.vipr.model.keystore.TruststoreSettingsChanges;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Tests truststore functionality
 */
public class TrustStoreTest extends ApiTestBase {

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

    private final static String CERTIFICATE = "-----BEGIN CERTIFICATE-----\r\n"
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

    private final static String LDAPS_CERTIFICATE = "-----BEGIN CERTIFICATE-----\r\n"
            + "MIIDeTCCAmGgAwIBAgIJAOX+um16uTH4MA0GCSqGSIb3DQEBBQUAMDExCzAJBgNV\r\n"
            + "BAYTAkFVMRMwEQYDVQQIEwpTb21lLVN0YXRlMQ0wCwYDVQQKEwRyb290MB4XDTEz\r\n"
            + "MDQyNTE0MjYwNVoXDTE2MDQyNDE0MjYwNVowMTELMAkGA1UEBhMCQVUxEzARBgNV\r\n"
            + "BAgTClNvbWUtU3RhdGUxDTALBgNVBAoTBHJvb3QwggEiMA0GCSqGSIb3DQEBAQUA\r\n"
            + "A4IBDwAwggEKAoIBAQDPyeboLSowXuxSW1UjpTmreT9QrEvfnEHUV3ICoXuDRK2Q\r\n"
            + "ERvlA4euWu3Gzbdid880NuShfwy1Lk6Ood1zeObHek4ZV0KzWqeKuEf9x5ZA6l5n\r\n"
            + "m/wLyemHoVNLjhwa9tdxljll3KBCiuTjKdsF6myjXPduKBkdlb4EWKzt2RwskaHx\r\n"
            + "OhyLU/zqZL1QEdfm317EEG88i0HzlTugfvR6v0uXjPkRMg91ruuLRZ2TA7OsWdVN\r\n"
            + "Wv+sbgbg6X2JSrGet6xlylmLiZWHqMw6ewuwT/wiV7mOMaE7KxmXUDsSXbt1f2kt\r\n"
            + "h8XwjpNHv5FXlmFxiZGfgP4AqMw40yXQ5ltb+eyfAgMBAAGjgZMwgZAwHQYDVR0O\r\n"
            + "BBYEFLruWD/S08ORz706k5s5tyPNJlZIMGEGA1UdIwRaMFiAFLruWD/S08ORz706\r\n"
            + "k5s5tyPNJlZIoTWkMzAxMQswCQYDVQQGEwJBVTETMBEGA1UECBMKU29tZS1TdGF0\r\n"
            + "ZTENMAsGA1UEChMEcm9vdIIJAOX+um16uTH4MAwGA1UdEwQFMAMBAf8wDQYJKoZI\r\n"
            + "hvcNAQEFBQADggEBAH7Wcd9bjxB0I1jDB+UEa/a3c5mFU/UU+2DrysHIXt+gmiJJ\r\n"
            + "jI8erYcHHfJ6VENKEz73GV53HGddfMw8r1isDPJ7gBSQ0tsC8YGP1WdxWmu4dEwN\r\n"
            + "pYHP/GW3PWF+V4pWLIRaI7MlyuAVyGBc4hV2BqvyhCce7l2LbTyKyYrkRoED4sIO\r\n"
            + "H0DRWNvRHZWPrP/ryE3n3YuuHdd09LAWekqCyZLsTsQvX1OQ2JJBw/JHK9JqAmPV\r\n"
            + "EgbhUKTYUKDyvltW2L62hGvVD9myzbiXvu7B/3vAQUO+J3W/7UQ2vKHgAGjWTqZI\r\n"
            + "RKVg94/3k7lboynwu9Ec6TNQAzTaY1MClwvm/rM=\r\n"
            + "-----END CERTIFICATE-----";


    List<RestLinkRep> resourcesToRemove;
    int nExistedCert = 0;
    private static String LDAP_SERVER1_IP = EnvConfig.get("sanity", "ldap1.ip");

    @Before
    public void setup() throws NoSuchAlgorithmException {
        initLoadBalancer(true);
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASSWORD, baseUrls);
        rSys.path("/tenant").get(String.class);
        addControllerLicense();
        waitForClusterToBeStable();
        updateADConfig();
        rRootUser2 = createHttpsClient(ROOTUSER2, AD_PASSWORD, baseUrls);
        rRootUser2.path("/tenant").get(String.class);
    }

    @Test
    public void testTrustStore() {
        testDefaultSettings();
        // add some resources while acceptAllCerts = true, this means that the resources
        // should get added successfully
        addResourcesShouldSucceed();
        // remove the resources we just added so we can re-add them later on
        removeAllAddedResources();
        // change truststore setting to acceptAllCerts = false
        changeTruststoreSettingsTest(false);

        // now try again to add the same resources, it should fail this time. 
        addResourcesShouldFail();

        // do general update truststore tests (with good/bad inputs)...
        generalTruststoreTest();
        // now try again to add all resources, should be successful
        // need to add ldap's certs before adding auth provider
        addResourcesCertificates();
        addResourcesShouldSucceed();
        // adding this so that this test can be run multiple times
        removeAllAddedResources();
        removeResourcesCertificate();
        changeTruststoreSettingsTest(true);
    }

    /**
     * 
     */
    private void removeResourcesCertificate() {
        TrustedCertificateChanges changes = new TrustedCertificateChanges();
        changes.setRemove(getResourcesCertList());
        ClientResponse response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());
        TrustedCertificates certs = response.getEntity(TrustedCertificates.class);
        Assert.assertEquals(nExistedCert, certs.getTrustedCertificates().size());

        waitForClusterToBeStable();
    }

    /**
     * @return
     */
    private List<String> getResourcesCertList() {
        List<String> certs = new ArrayList<String>();
        certs.add(LDAPS_CERTIFICATE);
        return certs;
    }

    /**
     *
     */
    private void addResourcesCertificates() {
        TrustedCertificateChanges changes = new TrustedCertificateChanges();
        changes.setAdd(getResourcesCertList());
        ClientResponse response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());
        TrustedCertificates certs = response.getEntity(TrustedCertificates.class);
        // Assert.assertEquals(changes.getAdd().size(), certs.getTrustedCertificates()
        //        .size());

        waitForClusterToBeStable();
    }

    /**
     *
     */
    public void testDefaultSettings() {
        // test GET with a non-privileged user -should fail
        ClientResponse response =
                rRootUser2.path("/vdc/truststore/settings").get(ClientResponse.class);
        Assert.assertEquals(403, response.getStatus());

        // test GET with a security admin user -should succeed
        // and acceptAllCertificates should be true
        response =
                rSys.path("/vdc/truststore/settings").get(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
        TruststoreSettings settings = response.getEntity(TruststoreSettings.class);
        Assert.assertNotNull(settings);
        Assert.assertTrue(settings.isAcceptAllCertificates());
    }

    /**
     * 
     */
    public void changeTruststoreSettingsTest(boolean acceptAllCerts) {
        ClientResponse response;
        TruststoreSettings settings;
        // change the settings to the value of acceptAllCerts
        TruststoreSettingsChanges settingsChanges = new TruststoreSettingsChanges();
        settingsChanges.setAcceptAllCertificates(acceptAllCerts);

        // test PUT with a non-privileged user -should fail
        response =
                rRootUser2.path("/vdc/truststore/settings").put(ClientResponse.class,
                        settingsChanges);
        Assert.assertEquals(403, response.getStatus());

        // test PUT with a security admin user -should succeed
        response =
                rSys.path("/vdc/truststore/settings").put(ClientResponse.class,
                        settingsChanges);
        Assert.assertEquals(200, response.getStatus());
        settings = response.getEntity(TruststoreSettings.class);
        Assert.assertNotNull(settings);
        Assert.assertEquals(acceptAllCerts, settings.isAcceptAllCertificates());

        // a change in the truststore setting causes a reboot.
        waitForClusterToBeStable();

        // do another get to make sure the result is same as acceptAllCerts
        response =
                rSys.path("/vdc/truststore/settings").get(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
        settings = response.getEntity(TruststoreSettings.class);
        Assert.assertNotNull(settings);
        Assert.assertEquals(acceptAllCerts, settings.isAcceptAllCertificates());
    }

    /**
     * 
     */
    public void generalTruststoreTest() {
        ClientResponse response;
        // test GET with a non-privileged user -should fail
        response = rRootUser2.path("/vdc/truststore").get(ClientResponse.class);
        Assert.assertEquals(403, response.getStatus());

        // test GET with a security admin user -should succeed
        response = rSys.path("/vdc/truststore").get(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
        TrustedCertificates certs = response.getEntity(TrustedCertificates.class);
        nExistedCert = certs.getTrustedCertificates().size();

        // should have ca certificates by default
        Assert.assertTrue(certs.getTrustedCertificates().size() > 0);

        TrustedCertificateChanges changes = new TrustedCertificateChanges();
        List<String> add = new ArrayList<String>();
        List<String> remove = new ArrayList<String>();
        changes.setAdd(add);

        // test PUT with a non-privileged user -should fail
        response = rRootUser2.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(403, response.getStatus());

        // test PUT with no changes - should succeed, and not cause a reboot
        response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());

        // test PUT with a bad format certificate in both sections, and a good certificate
        // that doesn't exist in the keystore - should fail
        String certStr = "this is a bad certificate";
        String anotherCertStr = "this is another bad cert";
        changes = new TrustedCertificateChanges();
        changes.setAdd(add);
        add.add(certStr);
        remove.add(anotherCertStr);
        remove.add(TRUSTED_CERTIFICATE);
        changes.setRemove(remove);
        String expectedMessage =
                "Truststore update had some failures. The following certificates could not be parsed: ["
                        + certStr
                        + ", "
                        + anotherCertStr
                        + "], the following certificates in the remove section were not in the truststore: ["
                        + TRUSTED_CERTIFICATE + "]";
        response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        assertExpectedError(response, 400, ServiceCode.API_PARAMETER_INVALID,
                expectedMessage);

        waitForClusterToBeStable();

        // test PUT with adding a good cert - should succeed
        changes = new TrustedCertificateChanges();
        add = new ArrayList<String>();
        add.add(CERTIFICATE);
        changes.setAdd(add);
        response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());
        certs = response.getEntity(TrustedCertificates.class);
        Assert.assertEquals(nExistedCert+1, certs.getTrustedCertificates().size());
        Assert.assertEquals(removeNewLines(CERTIFICATE), removeNewLines(certs
                .getTrustedCertificates().get(0).getCertString()));

        waitForClusterToBeStable();

        // test adding the same certificate, should be successful and the trusted
        // certificates should be the same as before
        response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());
        certs = response.getEntity(TrustedCertificates.class);
        Assert.assertEquals(nExistedCert+1, certs.getTrustedCertificates().size());
        Assert.assertEquals(removeNewLines(CERTIFICATE), removeNewLines(certs
                .getTrustedCertificates().get(0).getCertString()));

        waitForClusterToBeStable();

        add = new ArrayList<String>();
        add.add(TRUSTED_CERTIFICATE);
        changes.setAdd(add);

        remove = new ArrayList<String>();
        remove.add(CERTIFICATE);
        changes.setRemove(remove);

        // test adding and removing in the same operation should succeed, and response
        // should have only newly added cert
        response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());
        certs = response.getEntity(TrustedCertificates.class);
        Assert.assertEquals(nExistedCert+1, certs.getTrustedCertificates().size());
        // Assert.assertEquals(removeNewLines(TRUSTED_CERTIFICATE), removeNewLines(certs
        //        .getTrustedCertificates().get(0).getCertString()));

        // test just remove- should succeed
        remove = new ArrayList<String>();
        remove.add(TRUSTED_CERTIFICATE);
        changes.setRemove(remove);
        changes.setAdd(new ArrayList<String>());

        waitForClusterToBeStable();

        response =
                rSys.path("/vdc/truststore").put(ClientResponse.class, changes);
        Assert.assertEquals(200, response.getStatus());
        certs = response.getEntity(TrustedCertificates.class);
        Assert.assertEquals(nExistedCert, certs.getTrustedCertificates().size());
        waitForClusterToBeStable();
    }

    /**
     * 
     */
    private void addResourcesShouldFail() {
        ClientResponse response = addLDAPSAuthProvider();
        String errorMessage = "The authentication provider could not be added or modified "
                + "because of the following error: Connection to LDAP server [ldaps:\\" + LDAP_SERVER1_IP + "] "
                + "failed. Please, check the scheme, accessibility of the LDAP server and port. "
                + "LDAP error: simple bind failed: " + LDAP_SERVER1_IP + ":636; nested exception is "
                + "javax.naming.CommunicationException: simple bind failed: " + LDAP_SERVER1_IP + ":636 "
                + "[Root exception is javax.net.ssl.SSLHandshakeException: "
                + "sun.security.validator.ValidatorException: No trusted certificate found].";
        assertExpectedError(response, 400, ServiceCode.API_PARAMETER_INVALID, errorMessage);
    }

    /**
     * 
     */
    private void removeAllAddedResources() {
        if (resourcesToRemove != null) {
            for (RestLinkRep link : resourcesToRemove) {
                rSys.path(link.getLinkRef().toString()).delete();
            }
            resourcesToRemove = new ArrayList<RestLinkRep>();
        }

    }

    /**
     * 
     */
    private void addResourcesShouldSucceed() {
        resourcesToRemove = new ArrayList<RestLinkRep>();
        ClientResponse response = addLDAPSAuthProvider();
        Assert.assertEquals(200, response.getStatus());
        AuthnProviderRestRep authnResp = response.getEntity(AuthnProviderRestRep.class);
        Assert.assertNotNull(authnResp);
        resourcesToRemove.add(authnResp.getLink());

    }

    /**
     * @return
     */
    private ClientResponse addLDAPSAuthProvider() {
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("ldaps apitest config");
        param.setDescription("ldaps configuration created by ApiTest.java");
        param.setDisable(false);
        param.getDomains().add("secureldap.com");
        param.setManagerDn("CN=Manager,DC=root,DC=com");
        param.setManagerPassword("secret");
        param.setSearchBase("OU=People,DC=root,DC=com");
        param.setSearchFilter("mail=%u");
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add("ldaps:\\"+LDAP_SERVER1_IP);
        param.setMode("ldap");
        return rSys.path("/vdc/admin/authnproviders").post(ClientResponse.class,
                param);
    }

    /**
     * @param chain
     * @return
     */
    private String removeNewLines(String chain) {
        return chain.replaceAll("\n", "").replaceAll("\r", "");
    }

}
