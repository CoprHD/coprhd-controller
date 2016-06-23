/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/21/16.
 */
public class LanguageTest {

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

}
