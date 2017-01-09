package controllers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.Http.Header;
import util.MessagesUtils;
import util.NetworkUtils;
import util.StoragePortUtils;
import util.StorageSystemUtils;
import util.StringOption;
import util.UserPreferencesUtils;

import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.vipr.model.catalog.UserPreferencesRestRep;
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam;

import controllers.security.Security;

public class SRDFConfigurations extends Controller {
	
    protected static final String SAVED = "user.saved";
    private static final String FLASH_REFERER = "flash.userPreferences.referer.url";

    public static void load() {
        SRDFConfigurationForm user = new SRDFConfigurationForm(UserPreferencesUtils.getUserPreferences());
        renderArgs.put("storageArrayList", getSrdfStorageList());
        storeReferer(user);
        render(user);
    }

    public static void save(SRDFConfigurationForm user) {
        storeReferer(user);
        user.validate("user");
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            load();
        }
        else {
            user.save();
            load();
        }
    }

    public static void cancel() {
    	load();
    }

    private static List<StringOption> getSrdfStorageList() {
    	List<StringOption> storageArrayOptions = new ArrayList<StringOption>();
    	for (StorageSystemRestRep storageSystem : StorageSystemUtils.getStorageSystems()) {
    		Set<String> srdfConnected = storageSystem.getRemotelyConnectedTo();
    		if(srdfConnected != null) {
    			storageArrayOptions.add(new StringOption(storageSystem.getId().toString(),storageSystem.getName()));
    		}
		}
    	
    	List<NetworkRestRep> networks = NetworkUtils.getNetworks();
    	int len = networks.size();
    	for (NetworkRestRep network:networks) {
    		URI nets = network.getId();
    		String nname = network.getName();
    		nname = nname + nets.toString();
        }
    	return storageArrayOptions;
    }

    private static void storeReferer(SRDFConfigurationForm user) {
        final String referer = getRefererUrl();
        flash.put(FLASH_REFERER, referer);
        user.referer = referer;
        Logger.debug("Referer stored in Flash: %s", referer);
    }


    private static String getRefererUrl() {
        // if there is a referer URL stored in the flash we should use that
        final String flashReferer = flash.get(FLASH_REFERER);
        if (StringUtils.isNotBlank(flashReferer)) {
            return flashReferer;
        }

        // otherwise look for the referer in the request headers
        final String requestHeaderReferer = getRefererRequestHeader();
        if (StringUtils.isNotBlank(requestHeaderReferer)) {
            return requestHeaderReferer;
        }

        // otherwise we didn't find a referer
        return "/";
    }

    private static String getRefererRequestHeader() {
        for (Entry<String, Header> keys : request.headers.entrySet()) {
            if (StringUtils.equals(keys.getKey(), "referer")) {
                return keys.getValue().toString();
            }
        }
        return null;
    }

    public static class SRDFConfigurationForm {

        public static final String EMAIL_REQUIRED = "user.email.required";

        public String userId;

        @Required
        public Boolean notifyByEmail = Boolean.FALSE;

        public String email;

        public String referer;

        public SRDFConfigurationForm(UserPreferencesRestRep userPrefs) {
            doReadFrom(userPrefs);
        }

        protected void doReadFrom(UserPreferencesRestRep model) {
            this.userId = model.getUsername();
            this.notifyByEmail = model.getNotifyByEmail();
            this.email = model.getEmail();
        }

        public void save() {
            UserPreferencesUpdateParam updateParam = new UserPreferencesUpdateParam();
            updateParam.setNotifyByEmail(this.notifyByEmail);
            updateParam.setEmail(this.email);
            updateParam.setUsername(Security.getUserInfo().getCommonName());
            UserPreferencesUtils.updateUserPreferences(updateParam);
        }

        public void validate(String formName) {
            String emailFieldName = formName + ".email";
            if (notifyByEmail) {
                Validation.required(emailFieldName, email).message(MessagesUtils.get(EMAIL_REQUIRED));
            }

            if (StringUtils.isNotBlank(email)) {
                Validation.email(emailFieldName, email);
            }
        }
    }

}

