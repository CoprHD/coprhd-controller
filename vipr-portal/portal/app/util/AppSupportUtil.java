/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.ApplicationCreateParam;
import com.emc.storageos.model.application.ApplicationRestRep;
import com.emc.storageos.model.application.ApplicationUpdateParam;


/**
 * Utility for application support.
 * 
 * @author hr2
 */

public class AppSupportUtil {
    
    public static List<NamedRelatedResourceRep> getApplications() {
        return BourneUtil.getSysClient().application().getApplications().getApplications();
    }
    
    public static ApplicationRestRep createApplication(String name, String description, Set<String> roles){
        ApplicationCreateParam create = new ApplicationCreateParam();
        create.setName(name);
        create.setDescription(description);
        create.setRoles(roles);
        return BourneUtil.getSysClient().application().createApplication(create);
    }
    
    public static void deleteApplication(URI id) {
        BourneUtil.getSysClient().application().deleteApplication(id);
    }
    
    public static ApplicationRestRep updateApplication(String name, String description, URI id) {
        ApplicationUpdateParam update = new ApplicationUpdateParam();
        update.setName(name);
        update.setDescription(description);
        return BourneUtil.getSysClient().application().updateApplication(update, id);
    }
    
    public static ApplicationRestRep getApplication(URI id) {
        return BourneUtil.getSysClient().application().getApplication(id);
    }
}