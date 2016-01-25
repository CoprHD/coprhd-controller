/*
 * Copyright 2016 Oregon State University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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

    String SYS_NATIVE_ID = "6ee6d94e5a3517b8";
    String IP_ADDRESS = "10.193.17.88";

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
        // Test edge/invalid cases
        ScaleIORestClient client2 = handleFactory.getClientHandle(null, null, 0, null, null);
        Assert.assertNull(client2);
        String INVALID_IP_ADDRESS = "10.193.17.188";
        ScaleIORestClient client3 = handleFactory.getClientHandle(null, INVALID_IP_ADDRESS, PORT_NUMBER, USER_NAME, PASSWORD);
        Assert.assertNull(client3);
        String INVALID_USERNAME = "something";
        ScaleIORestClient client4 = handleFactory.getClientHandle(null, IP_ADDRESS_B, PORT_NUMBER, INVALID_USERNAME, PASSWORD);
        Assert.assertNull(client4);

    }

}
