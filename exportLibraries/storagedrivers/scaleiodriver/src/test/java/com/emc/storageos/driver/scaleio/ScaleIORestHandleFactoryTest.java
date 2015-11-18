package com.emc.storageos.driver.scaleio;

/**
 * Created by shujinwu on 11/17/15.
 */
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;

public class ScaleIORestHandleFactoryTest {
    ScaleIORestHandleFactory factory=new ScaleIORestHandleFactory();

    @org.junit.Before
    public void setUp() throws Exception {

    }

    @org.junit.Test
    public void testGetClientHandle() throws Exception {
        System.out.print("Get!");
        ScaleIORestClient client=factory.getClientHandle("system_native_id");
        if(client==null){
            System.out.print("no rest client returned!");
        }else{
            System.out.print("Get!");
        }
    }
}