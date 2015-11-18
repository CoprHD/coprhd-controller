package com.emc.storageos.driver.scaleio;


import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Created by shujinwu on 11/17/15.
 */
public class ScaleIORestHandleFactory {
    private  static final Logger log= LoggerFactory.getLogger(ScaleIORestHandleFactory.class);
    private final Map<String, ScaleIORestClient> ScaleIORestClientMap = new ConcurrentHashMap<String, ScaleIORestClient>();
    private final Object syncObject = new Object();
    private ScaleIORestClientFactory scaleIORestClientFactory;

    /*
    * Get Rest client handle for a scaleIO storage system
    * @param systemNativeId
    */
    public ScaleIORestClient getClientHandle(String systemNativeId)throws Exception {
        ScaleIORestClient handle;
        Map<String,List<String>> attributes;

        synchronized (syncObject){
            handle=ScaleIORestClientMap.get(systemNativeId);
            if(handle==null){
                Registry registry = new InMemoryRegistryImpl();
                attributes=registry.getDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME,systemNativeId);
                if(attributes!=null) {
                    URI baseURI = URI.create(ScaleIOConstants.getAPIBaseURI(attributes.get(ScaleIOConstants.IP_ADDRESS).get(0),
                            Integer.parseInt(attributes.get(ScaleIOConstants.PORT_NUMBER).get(0))));
                    handle = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI, attributes.get(ScaleIOConstants.USER_NAME).get(0),
                            attributes.get(ScaleIOConstants.PASSWORD).get(0), true);
                    ScaleIORestClientMap.put(systemNativeId, handle);
                }else{
                    //no connection Info found
                    handle=null;
                }
            }
        }
        return handle;
    }
}
