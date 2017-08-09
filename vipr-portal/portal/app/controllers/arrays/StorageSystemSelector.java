/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package controllers.arrays;

import static util.BourneUtil.getViprClient;

import java.util.List;

import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.util.Models;
import play.Logger;
import play.exceptions.ActionNotFoundException;
import play.mvc.Controller;
import play.mvc.Util;
import util.StringOption;

public class StorageSystemSelector extends Controller {
    private static String STORAGE_SYSTEMS = "storageSystems";

    public static void selectStorageSystem(String storageSystemId, String url) {
        Models.setStorageSystemId(storageSystemId);

        if (url != null) {
            try {
                redirect(Common.toSafeRedirectURL(url));
            } catch (ActionNotFoundException noAction) {
                Logger.error(noAction, "Action not found for %s", url);
                badRequest();
            }
        }
    }

    @Util
    public static void addRenderArgs() {
        List<StringOption> storageSystems = Lists.newArrayList();
        for (StorageSystemRestRep storageSystem : getViprClient().storageSystems().getAll()) {
            storageSystems.add(new StringOption(storageSystem.getId().toString(), storageSystem.getName()));
        }
        renderArgs.put(STORAGE_SYSTEMS, storageSystems);
    }
}
