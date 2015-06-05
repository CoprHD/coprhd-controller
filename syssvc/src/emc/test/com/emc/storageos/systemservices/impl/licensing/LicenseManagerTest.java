package com.emc.storageos.systemservices.impl.licensing;

import org.junit.Assert;

import org.junit.Test;

import com.emc.vipr.model.sys.licensing.License;

public class LicenseManagerTest {

    @Test
    public void testForNoLicense() {
        LicenseManager manager = new LicenseManagerImpl() {
            public License getLicense() {
                return new License();
            }
        };
        try {
            License license = manager.getLicense();
            Assert.assertEquals(license.getLicenseFeatures().size(), 0);
        }catch(Exception e) {
            
        }
    }
   
  
//    @Test
//    public void testForPermanentLicense() {
//        
//        LicenseManager manager = new LicenseManager() {
//        protected ELMLicenseProps configureLicenseProps() {
//            ELMLicenseProps licProps = new ELMLicenseProps();
//            licProps.setLicPath("./src/main/test/StorageOS_SWID.lic");
//            return licProps;
//        }
//    };
//
//        License license = manager.getLicense();
//        Assert.assertFalse(license.isExpired());
//        Assert.assertTrue(license.isLicensed());
//    }
//   
//    
//    @Test
//    public void testValidLicenseWithSN() {
//
//        LicenseManager manager = new LicenseManager() {
//            protected ELMLicenseProps configureLicenseProps() {
//                ELMLicenseProps licProps = new ELMLicenseProps();
//                licProps.setLicPath("./src/main/test/StorageOS.lic");
//                return licProps;
//            }
//        };
//
//        License license = manager.getLicense();
//        Assert.assertFalse(license.isExpired());
//        Assert.assertTrue(license.isLicensed());
//        Assert.assertTrue(license.getDateIssued().equals("12/12/2012"));
//        Assert.assertTrue(license.getIssuer().equals("EMC"));
//        Assert.assertTrue(license.getModelId().equals("StorageOS_STD"));
//        Assert.assertTrue(license.getSerial().equals("1234567"));
//        Assert.assertTrue(license.getSiteId().equals("UKNOWN"));
//        Assert.assertTrue(license.getVersion().equals("1.0"));
//        Assert.assertTrue(license.getIssuer().equals("EMC"));
//        Assert.assertTrue(license.getLicenseIdIndicator().equals(LicenseManager.LAC));
//    }   
}
