package com.emc.storageos.driver.scaleio;

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;

@RunWith(value = SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/scaleio-driver-prov.xml" })
public class ScaleIORestHandleFactoryTest {
    private static Logger log = LoggerFactory.getLogger(ScaleIORestHandleFactoryTest.class);
    String SYS_NATIVE_ID_B = "3eb4708d2b3ea454";
    String IP_ADDRESS_B = "10.193.17.88";
    int PORT_NUMBER = 443;
    String USER_NAME = "admin";
    String PASSWORD = "Scaleio123";
    @Autowired
    private ScaleIORestHandleFactory handleFactory;

    public void setHandleFactory(ScaleIORestHandleFactory handleFactory) {
        this.handleFactory = handleFactory;
    }

    @org.junit.Test
    public void testGetClientHandle() throws Exception {
        ScaleIORestClient client = handleFactory.getClientHandle(null, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        Assert.assertNotNull(client);
        // Test fetching client in the cache
        ScaleIORestClient client1 = handleFactory.getClientHandle(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        Assert.assertNotNull(client1);
    }

}