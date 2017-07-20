package com.emc.storageos.vmax;

import java.net.URI;
import java.net.URISyntaxException;

import com.emc.storageos.db.client.model.StorageProvider;

public class VMAXRestUtils {
	
	
	public static URI getUnisphereRestServerInfo(StorageProvider storageProvider) throws URISyntaxException {

        String protocol = VMAXConstants.HTTP_URL;
        if (Boolean.TRUE.equals(storageProvider.getUseSSL())) {
            protocol = VMAXConstants.HTTPS_URL;
        }

        String ipAddress = storageProvider.getIPAddress();
        int portNumber = storageProvider.getPortNumber();
        URI uri = new URI(protocol, null, ipAddress, portNumber, VMAXConstants.UNIVMAX_BASE_URI, null, null);
        return uri;
    }
	
}
