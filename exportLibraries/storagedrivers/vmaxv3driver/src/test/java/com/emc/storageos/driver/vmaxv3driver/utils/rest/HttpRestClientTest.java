package com.emc.storageos.driver.vmaxv3driver.utils.rest;

import com.emc.storageos.driver.vmaxv3driver.rest.BaseRestTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by gang on 6/16/16.
 */
public class HttpRestClientTest extends BaseRestTest {

    private static Logger logger = LoggerFactory.getLogger(HttpRestClientTest.class);

    HttpRestClient client = this.getClient();
    String symId = this.getDefaultArrayId();

    @Test
    public void testGetVersion() {
        String response = client.request("/univmax/restapi/system/version");
        logger.info("response = {}", response);
    }

    @Test
    public void testCreateStorageGroup() {
        String body = "{\"srpId\": \"none\", \"storageGroupId\": \"test1\", " +
            "\"sloBasedStorageGroupParam\": [{\"num_of_vols\": 2, \"volumeAttribute\": " +
            "{\"volume_size\": \"10\", \"capacityUnit\": \"GB\"}}]}";
        logger.info("body = {}", body);
        String response = client.request("/univmax/restapi/sloprovisioning/symmetrix/" + symId + "/storagegroup",
            HttpRequestType.POST, body);
        logger.info("response = {}", response);
    }

    @Test(dependsOnMethods = {"testCreateStorageGroup"})
    public void testUpdateStorageGroup() {
        String body = "{\"editStorageGroupActionParam\": {\"expandStorageGroupParam\": {" +
            "\"num_of_vols\": 1, \"volumeAttribute\": {\"volume_size\": \"10\", \"capacityUnit\": \"GB\"}, \"create_new_volumes\": true}}}";
        logger.info("body = {}", body);
        String response = client.request("/univmax/restapi/sloprovisioning/symmetrix/" + symId + "/storagegroup/test1",
            HttpRequestType.PUT, body);
        logger.info("response = {}", response);
    }

    @Test(dependsOnMethods = {"testUpdateStorageGroup"})
    public void testDeleteStorageGroup() {
        String response = client.request("/univmax/restapi/sloprovisioning/symmetrix/" + symId + "/storagegroup/test1",
            HttpRequestType.DELETE);
        logger.info("response = {}", response);
    }
}
