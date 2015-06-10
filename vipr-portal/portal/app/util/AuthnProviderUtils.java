/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

public class AuthnProviderUtils {
    public static AuthnProviderRestRep getAuthnProvider(String id) {
        try {
            return getViprClient().authnProviders().get(uri(id));
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<AuthnProviderRestRep> getAuthnProviders() {
        return getViprClient().authnProviders().getAll();
    }

    public static List<AuthnProviderRestRep> getAuthnProvider(List<URI> ids) {
        return getViprClient().authnProviders().getByIds(ids);
    }
    
    public static List<AuthnProviderRestRep> getAuthProvidersByDomainName(String domain) {
    	List<AuthnProviderRestRep> results = Lists.newArrayList();
    	if (StringUtils.isNotBlank(domain)) {
	    	List<AuthnProviderRestRep> authnProviderRestReps = AuthnProviderUtils.getAuthnProviders();
	    	for (AuthnProviderRestRep authnProviderRestRep : authnProviderRestReps) {
	    		for (String authProviderDomain : authnProviderRestRep.getDomains()) {
	    			if (StringUtils.equalsIgnoreCase(authProviderDomain, domain)) {
	    				results.add(authnProviderRestRep);
	    			}
	    		}
	    	}
    	}    	
    	return results;
    }

    public static AuthnProviderRestRep create(AuthnCreateParam authnProvider) {
        return getViprClient().authnProviders().create(authnProvider);
    }

    public static AuthnProviderRestRep update(String id, AuthnUpdateParam authnProvider) {
        return getViprClient().authnProviders().update(uri(id), authnProvider);
    }

    public static AuthnProviderRestRep forceUpdate(String id, AuthnUpdateParam authnProvider) {
        return getViprClient().authnProviders().forceUpdate(uri(id), authnProvider);
    }

    public static void delete(URI id) {
        getViprClient().authnProviders().delete(id);
    }
}
