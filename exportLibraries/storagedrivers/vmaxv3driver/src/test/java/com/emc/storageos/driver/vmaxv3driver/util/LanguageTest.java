/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;

/**
 * Created by gang on 6/21/16.
 */
public class LanguageTest {

    private static final Logger logger = LoggerFactory.getLogger(LanguageTest.class);

    @Test
    public void testSplit() {
        String expression = "lglw7150.lss.emc.com$000196801612";
        String[] tokens = expression.split("\\$");
        Assert.assertEquals(tokens[0], "lglw7150.lss.emc.com");
        Assert.assertEquals(tokens[1], "000196801612");
        expression = "lglw7150.lss.emc.com";
        tokens = expression.split("\\$");
        Assert.assertEquals(tokens[0], "lglw7150.lss.emc.com");
    }

    @Test
    public void format() {
        String value = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_ID, "100");
        logger.info("value = {}", value);
        Assert.assertEquals(value, "/univmax/restapi/sloprovisioning/symmetrix/100");
    }
}
