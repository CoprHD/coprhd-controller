package com.emc.storageos.driver.scaleio;

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
    String SYS_NATIVE_ID = "6ee6d94e5a3517b8";
    String IP_ADDRESS = "10.193.17.88";
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
        ScaleIORestClient client = handleFactory.getClientHandle(null, IP_ADDRESS, PORT_NUMBER, USER_NAME, PASSWORD);
        if (client == null) {
            System.out.print("no rest client returned!");
        } else {
            System.out.print("----1-----" + client.getSystemId() + "-------------");
        }
        // Test fetching client in the cache
        ScaleIORestClient client1 = handleFactory.getClientHandle(SYS_NATIVE_ID, IP_ADDRESS, PORT_NUMBER, USER_NAME, PASSWORD);
        if (client == null) {
            System.out.print("no rest client returned!");
        } else {
            System.out.print("----2-----" + client1.getSystemId() + "-------------");
        }
    }

}
