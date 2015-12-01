package com.emc.storageos.driver.scaleio;


import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(value = SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/scaleio-driver-prov.xml"})
public class ScaleIORestHandleFactoryTest {

   private static Logger log = LoggerFactory.getLogger(ScaleIORestHandleFactoryTest.class);
    @Autowired
    private ScaleIORestHandleFactory handleFactory;
    private static Registry registry = new InMemoryRegistryImpl();
    String SYS_NATIVE_ID="5a01234257c7cc9c";

    public void setHandleFactory(ScaleIORestHandleFactory handleFactory) {
        this.handleFactory = handleFactory;
    }


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

    }

    @org.junit.Test
    public void testGetClientHandle() throws Exception {

        ScaleIORestClient client=handleFactory.getClientHandle(SYS_NATIVE_ID,registry);

        if(client==null){
            System.out.print("no rest client returned!");
        }else{
            System.out.print("---------"+client.getSystemId()+"-------------");

        }
    }


}