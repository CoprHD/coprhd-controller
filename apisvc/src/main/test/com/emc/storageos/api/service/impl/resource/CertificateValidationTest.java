package com.emc.storageos.api.service.impl.resource;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;


public class CertificateValidationTest {

    private static final Logger _log = LoggerFactory.getLogger(CertificateValidationTest.class);

    /**
     *  the certificate is a vipr auto-generated one, and it is for 1+0 cluster.
     *  VIP: 10.247.101.102 (lglw1102.lss.emc.com)
     *  vipr1: 10.247.101.103 (lglw1103.lss.emc.com)
     */
    @Test
    public void validateSelfSignedCert() throws Exception {

        InputStream is = ClassLoader.class.getResourceAsStream("/certificate-lglw1102.txt");
        String certStr = convertStreamToString(is);

        VirtualDataCenterService.verifyVdcCert(certStr, "lglw1102.lss.emc.com", true);
        VirtualDataCenterService.verifyVdcCert(certStr, "lglw1103.lss.emc.com", true);
        VirtualDataCenterService.verifyVdcCert(certStr, "10.247.101.102", true);
        VirtualDataCenterService.verifyVdcCert(certStr, "10.247.101.103", true);

        // negative tests
        try {
            VirtualDataCenterService.verifyVdcCert(certStr, "lglw1104.lss.emc.com", true);
            Assert.fail("should fail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not match any subject names in certificate"));
        }

        try {
            VirtualDataCenterService.verifyVdcCert(certStr, "10.247.101.104", true);
            Assert.fail("should fail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not match any subject names in certificate"));
        }
    }

    /**
     * this is a CA signed certification, the subject is lglw2051.lss.emc.com (10.247.102.51)
     *
     * @throws Exception
     */
    @Test
    public void validateCASignedCert() throws Exception {
        InputStream is = ClassLoader.class.getResourceAsStream("/ca-certificate-lglw2051.pem");
        String certStr = convertStreamToString(is);

        VirtualDataCenterService.verifyVdcCert(certStr, "lglw2051.lss.emc.com", true);
        VirtualDataCenterService.verifyVdcCert(certStr, "10.247.102.51", true);

        // negative tests
        try {
            VirtualDataCenterService.verifyVdcCert(certStr, "lglw1104.lss.emc.com", true);
            Assert.fail("should fail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not match any subject names in certificate"));
        }

        try {
            VirtualDataCenterService.verifyVdcCert(certStr, "10.247.101.104", true);
            Assert.fail("should fail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not match any subject names in certificate"));
        }
    }


    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


}
