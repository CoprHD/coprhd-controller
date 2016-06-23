package com.emc.storageos.driver.vmaxv3driver.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/23/16.
 */
public class ListArrayTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(GetVersionTest.class);

    @Test
    public void test() {
        logger.info("Array list = {}", new ListArray().execute(this.getClient()));
    }
}
