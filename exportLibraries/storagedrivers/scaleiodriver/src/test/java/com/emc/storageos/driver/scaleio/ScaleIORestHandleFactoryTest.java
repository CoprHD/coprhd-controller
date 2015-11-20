package com.emc.storageos.driver.scaleio;

/**
 * Created by shujinwu on 11/17/15.
 */

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ScaleIORestHandleFactoryTest {
    private static Logger log = LoggerFactory.getLogger(ScaleIORestHandleFactoryTest.class);
    private ScaleIORestHandleFactory handleFactory=new ScaleIORestHandleFactory();
    private static Registry registry = new InMemoryRegistryImpl();
    private static ScaleIORestClient restClient;
    String SYS_NATIVE_ID="5a01234257c7cc9c";

    @org.junit.Before
    public void setUp() throws Exception {
        List<String> list=new ArrayList<>();
        list.add("10.193.17.97");
        registry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,SYS_NATIVE_ID,ScaleIOConstants.IP_ADDRESS,list);
        list=new ArrayList<>();
        list.add("443");
        registry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,SYS_NATIVE_ID,ScaleIOConstants.PORT_NUMBER,list);
        list=new ArrayList<>();
        list.add("admin");
        registry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,SYS_NATIVE_ID,ScaleIOConstants.USER_NAME,list);
        list=new ArrayList<>();
        list.add("Scaleio123");
        registry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,SYS_NATIVE_ID,ScaleIOConstants.PASSWORD,list);
        ScaleIORestClientFactory factory = new ScaleIORestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        handleFactory.setScaleIORestClientFactory(factory);

    }

    @org.junit.Test
    public void testGetClientHandle() throws Exception {
        ScaleIORestClient client=handleFactory.getClientHandle(SYS_NATIVE_ID,registry);
        if(client==null){
            System.out.print("no rest client returned!");
        }else{
            System.out.print(client.getSystemId());

        }
    }
}