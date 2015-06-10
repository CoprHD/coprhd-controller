/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import java.util.HashMap;
import java.util.Map;

import play.Logger;
import play.inject.BeanSource;
import play.inject.Injector;
import play.jobs.Job;
import plugin.StorageOsPlugin;

import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;

public class DependencyInjectionJob extends Job implements BeanSource {
    private Map<Class, Object> beans = new HashMap<Class, Object>();

    public void doJob() {
        AuthSvcEndPointLocator authSvcEndPointLocator = null;
        if (StorageOsPlugin.isEnabled()) {
            authSvcEndPointLocator = StorageOsPlugin.getInstance().getAuthSvcEndPointLocator();
        }
        else {
            Logger.info("WARNING - Service Descriptors are unavailable!");
        }

        beans.put(AuthSvcEndPointLocator.class, authSvcEndPointLocator);

        Injector.inject(this);
    }

    public <T> T getBeanOfType(Class<T> clazz) {
        return (T) beans.get(clazz);
    }

}
