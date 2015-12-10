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
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import javax.ws.rs.core.Response;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
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
    
    public static TaskList updateApplication(String name, String description, String id) {
        ApplicationUpdateParam update = new ApplicationUpdateParam();
        if(!name.isEmpty()) {
            update.setName(name);
        }
        if(!description.isEmpty()) {
            update.setDescription(description);
        }
        return BourneUtil.getSysClient().application().updateApplication(uri(id), update);
    }
    
    public static ApplicationRestRep getApplication(URI id) {
        return BourneUtil.getSysClient().application().getApplication(id);
    }
}