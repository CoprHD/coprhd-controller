package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScaleIORestHandleFactory {
    private static final Logger log = LoggerFactory.getLogger(ScaleIORestHandleFactory.class);
    private final Map<String, ScaleIORestClient> ScaleIORestClientMap = new ConcurrentHashMap<String, ScaleIORestClient>();
    private final Object syncObject = new Object();

    private ScaleIORestClientFactory scaleIORestClientFactory;

    public ScaleIORestClientFactory getScaleIORestClientFactory() {
        return scaleIORestClientFactory;
    }

    public void setScaleIORestClientFactory(
            ScaleIORestClientFactory scaleIORestClientFactory) {
        this.scaleIORestClientFactory = scaleIORestClientFactory;
    }

    /**
     * Get Rest client handle for a scaleIO storage system
     *
     * @param systemNativeId storage system native id (Optional)
     * @param ipAddr object native id
     * @param port class instance
     * @param username class instance
     * @param password class instance
     * @return scaleIO handle
     */
    public ScaleIORestClient getClientHandle(String systemNativeId, String ipAddr, int port, String username, String password)
            throws Exception {
        ScaleIORestClient handle = null;
        synchronized (syncObject) {
            if (systemNativeId != null && systemNativeId.trim().length() > 0) {
                handle = ScaleIORestClientMap.get(systemNativeId);
            }
            if (handle == null) {
                URI baseURI = URI.create(ScaleIOConstants.getAPIBaseURI(ipAddr, port));
                handle = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI, username,
                        password, true);
                if (handle == null) {
                    log.error("Failed to get Rest Handle");
                } else if (systemNativeId == null || systemNativeId.trim().length() == 0) {
                    systemNativeId = handle.getSystemId();
                }
                ScaleIORestClientMap.put(systemNativeId, handle);
            }
        }
        return handle;
    }

}
