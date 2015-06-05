/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import static util.BourneUtil.getCatalogClient;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.UserPreferencesRestRep;
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam;

public class UserPreferencesUtils {

    public static UserPreferencesRestRep getUserPreferences() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.userPreferences().getPreferences();
    }
    
    public static UserPreferencesRestRep getUserPreferences(String username) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.userPreferences().getPreferences(username);
    }    
    
    public static UserPreferencesRestRep updateUserPreferences(UserPreferencesUpdateParam updateParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.userPreferences().updatePreferences(updateParam);
    }
    
    public static boolean getNotifyByEmail() {
        return getUserPreferences().getNotifyByEmail() != null ? getUserPreferences().getNotifyByEmail() : false; 
    }
    
    public static String getEmail() {
        return getUserPreferences().getEmail();
    }

}
