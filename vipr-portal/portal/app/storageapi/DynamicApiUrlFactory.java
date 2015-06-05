/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package storageapi;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Picks one of the registered SASVC endpoints at random
 *
 * @author dmaddison
 */
public class DynamicApiUrlFactory implements ApiUrlFactory{
    private static final Logger LOG = Logger.getLogger(DynamicApiUrlFactory.class);
    private CoordinatorClient coordinator;
    private Random random = new Random();

    private final static String SA_SVC_NAME = "sasvc";
    private final static String SA_SVC_VERSION = "1";

    public DynamicApiUrlFactory(CoordinatorClient coordinatorClient) {
        this.coordinator = coordinatorClient;
    }

    @Override
    public String getUrl() {
        List<Service> services = getServiceInfoListInternal();

        int endpointToUse = random.nextInt(services.size());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Returning "+SA_SVC_NAME+" Endpoint "+ services.get(endpointToUse).getEndpoint().toString());
        }

        return services.get(endpointToUse).getEndpoint().toString();
    }

    private List<Service> getServiceInfoListInternal() {
        LOG.debug("Retrieving " + SA_SVC_NAME + " service info from coordinator service");
        try {
            List<Service> services = coordinator.locateAllServices(SA_SVC_NAME, SA_SVC_VERSION, null, null);

            if (services.isEmpty()) {
                throw new RuntimeException("No endpoint found for "+SA_SVC_NAME, null);
            }

            return services;
        }
        catch(Exception e) {
            throw new RuntimeException("Error whilst fetch "+SA_SVC_NAME+" information",e);
        }
    }
}
