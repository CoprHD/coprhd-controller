/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import com.emc.storageos.model.smis.StorageProviderRestRep;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import play.mvc.Controller;
import play.mvc.With;
import util.MessagesUtils;
import util.StorageProviderUtils;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN") })
public class ScaleIO extends Controller {
    public static void dashboard(String id) {
        StorageProviderRestRep provider = StorageProviderUtils.getStorageProvider(uri(id));
        if (provider == null) {
            flash.error(MessagesUtils.get(StorageProviders.UNKNOWN, id));
            StorageProviders.list();
        }

        response.contentType = "application/x-java-jnlp-file";
        request.format = "jnlp";
        render(provider);
    }
}
