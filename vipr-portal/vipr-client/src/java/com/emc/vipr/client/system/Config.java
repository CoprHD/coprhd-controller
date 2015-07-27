/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;
import static com.emc.vipr.client.system.impl.PathConstants.CONFIG_CONNECT_EMC_EMAIL_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONFIG_CONNECT_EMC_FTPS_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONFIG_PROPERTIES_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONFIG_PROP_METADATA_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONFIG_PROP_RESET_URL;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.storageos.model.property.PropertyList;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.eventhandler.ConnectEmcEmail;
import com.emc.vipr.model.sys.eventhandler.ConnectEmcFtps;

public class Config {
    private static final String PROPERTY_CATEGORY = "category";
    private static final String REMOVE_OBSOLETE_PARAM = "removeObsolete";
    private static final String REMOVE_OBSOLETE = "1";

    private RestClient client;

    public Config(RestClient client) {
        this.client = client;
    }	

    /**
     * Get system configuration properties.
     * <p>
     * API Call: GET /config/properties
     * 
     * @return Property information
     */
    public PropertyInfoRestRep getProperties() {
        return getProperties(null);
    }

    /**
     * Get system configuration properties.
     * <p>
     * API Call: GET /config/properties[?category={category}]
     * 
     * @return Property information
     */
    public PropertyInfoRestRep getProperties(String category) {
        UriBuilder builder = client.uriBuilder(CONFIG_PROPERTIES_URL);
        if ((category != null) && !category.isEmpty()) {
            addQueryParam(builder, PROPERTY_CATEGORY, category);
        }
        return client.getURI(PropertyInfoRestRep.class, builder.build());
    }
	
	/**
	 * Update system configuration properties
	 * <p>
	 * API Call: PUT /config/properties
	 * 
	 * @param setProperty Property's key value pair.
	 * @return Cluster information
	 */
	public ClusterInfo setProperties(PropertyInfoUpdate setProperty) {
		return client.put(ClusterInfo.class, setProperty, CONFIG_PROPERTIES_URL);
	}
	
	/**
	 * Show metadata of system configuration properties.
	 * <p>
	 * API Call: GET /config/properties/metadata
	 * 
	 * @return Properties Metadata
	 */
	public PropertiesMetadata getPropMetadata() {
		return client.get(PropertiesMetadata.class, CONFIG_PROP_METADATA_URL);
	}
	
	/**
	 * Configure ConnectEMC FTPS transport related properties.
	 * <p>
	 * API Call: POST /config/connectemc/ftps
	 * 
	 * @param ftpsParams ConnectEMC FTPS transport related properties
	 * @return The cluster information
	 */
	public ClusterInfo configureConnectEmcFtpsParams(ConnectEmcFtps ftpsParams) {
		return client.post(ClusterInfo.class, ftpsParams, CONFIG_CONNECT_EMC_FTPS_URL);
	}
	
	/**
	 * Configure ConnectEMC SMTP/Email transport related properties.
	 * <p>
	 * API Call: POST /config/connectemc/email
	 * 
	 * @param emailParams ConnectEMC SMTP/Email transport related properties
	 * @return The cluster information
	 */
	public ClusterInfo configureConnectEmcEmailParams(ConnectEmcEmail emailParams) {
		return client.post(ClusterInfo.class, emailParams, CONFIG_CONNECT_EMC_EMAIL_URL);
	}
	
	/**
	 * Reset configuration properties to their default values. Properties with 
	 * no default values will remain unchanged.
	 * <p>
	 * API Call: POST /config/properties/reset
	 * 
	 * @param propertyList Configuration properties to reset
	 * @param removeObsoleteProps If true, removes obsolete properties
	 * @return The cluster information
	 */
	public ClusterInfo resetProps(PropertyList propertyList, boolean removeObsoleteProps) {
    	UriBuilder builder = client.uriBuilder(CONFIG_PROP_RESET_URL);
    	if (removeObsoleteProps) {
    		addQueryParam(builder, REMOVE_OBSOLETE_PARAM, REMOVE_OBSOLETE);
    	} 

		return client.postURI(ClusterInfo.class, propertyList, builder.build());		
	}
	
	/**
	 * Reset configuration properties to their default values. Properties with 
	 * no default values will remain unchanged. Removes obsolete properties.
	 * 
	 * @param propertyList Configuration properties to reset
	 * @return The cluster information
	 */
	public ClusterInfo resetProps(PropertyList propertyList) {
		return resetProps(propertyList, true);
	}
}
