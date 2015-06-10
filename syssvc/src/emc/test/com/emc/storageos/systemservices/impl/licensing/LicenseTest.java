/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.licensing;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

import com.emc.vipr.model.sys.licensing.LicenseFeature;

public class LicenseTest {

    /**
     * Positive test for an expired license. Licensed date is current day minus
     * 1 day. Compares date to current day.
     */
    @Test
    public void expiredLicenseTest() {
        LicenseFeature license = new LicenseFeature();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        Date nowMinusOneDay = cal.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(LicenseManager.EXPIRE_DATE_FORMAT);
        license.setDateExpires(sdf.format(nowMinusOneDay));
        license.setExpired(LicenseManagerImpl.isExpired(license.getDateExpires()));
        Assert.assertTrue(license.isExpired());
    }

    /**
     * Positive test for a permanent license.
     */
    @Test
    public void permanentLicenseTest() {
        LicenseFeature license = new LicenseFeature();
        license.setDateExpires(LicenseManager.PERMANENT_LICENSE);
        license.setExpired(LicenseManagerImpl.isExpired(license.getDateExpires()));
        Assert.assertFalse(license.isExpired());
    }

    /**
     * Positive test for a non expired license. License date is current day plus
     * 1 day. Compares date to current day.
     */
    @Test
    public void nonExpiredLicenseTest() {

        LicenseFeature license = new LicenseFeature();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        Date nowPlusOneDay = cal.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(LicenseManager.EXPIRE_DATE_FORMAT);
        license.setDateExpires(sdf.format(nowPlusOneDay));
        license.setExpired(LicenseManagerImpl.isExpired(license.getDateExpires()));
        Assert.assertFalse(license.isExpired());
    }

    @Test
    public void noLicenseDefinedTest() {

        LicenseFeature license = new LicenseFeature();
        Assert.assertFalse(license.isExpired());
        Assert.assertFalse(license.isLicensed());
    }

}
