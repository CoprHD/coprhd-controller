/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package controllers.tenant;

import java.util.*;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import controllers.Common;
import controllers.util.ViprResourceController;
import models.TenantsSynchronizationOptions;
import org.apache.commons.lang.StringUtils;
import util.AuthnProviderUtils;
import util.MessagesUtils;

import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;

import play.data.binding.As;
import play.mvc.With;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class TenantsSynchronization extends ViprResourceController {

    private static String authnProviderName = "";
    protected static final String SAVED = "LDAPsources.saved";
    protected static final String UPDATED = "keystoneProvider.updated";
    protected static final String INTERVAL_ERROR = "ldapSources.synchronizationInterval.integerRequired";

    public static void edit() {
        AuthnProviderRestRep authnProvider = AuthnProviderUtils.getKeystoneAuthProvider();
        authnProviderName = authnProvider.getName();
        Form keystoneProvider = new Form();
        keystoneProvider.readFrom(authnProvider);
        renderArgs.put("tenantsOptions",
                TenantsSynchronizationOptions.options(TenantsSynchronizationOptions.ADDITION, TenantsSynchronizationOptions.DELETION));
        renderArgs.put("defaultInterval", getInterval(authnProvider));
        render(keystoneProvider);
    }

    @FlashException("edit")
    public static void save(Form keystoneProvider) {
        String interval = keystoneProvider.synchronizationInterval;
        if (!StringUtils.isNumeric(interval) || (StringUtils.isNumeric(interval) && (Integer.parseInt(interval) < 10))) {
            flash.error(MessagesUtils.get(INTERVAL_ERROR, keystoneProvider.synchronizationInterval));
        } else {
            keystoneProvider.update();
            flash.success(MessagesUtils.get(SAVED, authnProviderName));
        }
        edit();
    }

    public static class Form {
        public String id;
        public String name;
        public List<String> tenantsSynchronizationOptions;
        public String synchronizationInterval;

        public void readFrom(AuthnProviderRestRep keystoneProvider) {
            this.id = stringId(keystoneProvider);
            this.name = keystoneProvider.getName();
            this.tenantsSynchronizationOptions = Lists.newArrayList(keystoneProvider.getTenantsSynchronizationOptions());
        }

        private void update() {
            AuthnUpdateParam param = new AuthnUpdateParam();
            AuthnProviderRestRep provider = AuthnProviderUtils.getKeystoneAuthProvider();

            AuthnUpdateParam.TenantsSynchronizationOptionsChanges tenantsSynchronizationOptionsChanges = getTenantsSynchronizationOptionsChanges(
                    provider);
            if (!(tenantsSynchronizationOptionsChanges.getAdd().isEmpty() && tenantsSynchronizationOptionsChanges.getRemove().isEmpty())) {
                param.setTenantsSynchronizationOptionsChanges(tenantsSynchronizationOptionsChanges);
                param.setLabel(this.name);
                AuthnProviderUtils.update(provider.getId().toString(), param);
            }
        }

        private AuthnUpdateParam.TenantsSynchronizationOptionsChanges
        getTenantsSynchronizationOptionsChanges(AuthnProviderRestRep provider) {

            Set<String> newValues;
            if (this.tenantsSynchronizationOptions != null) {
                newValues = Sets.newHashSet(tenantsSynchronizationOptions);
                newValues.add(synchronizationInterval);
            } else {
                newValues = Sets.newHashSet(synchronizationInterval);
            }

            Set<String> oldValues = provider.getTenantsSynchronizationOptions();

            AuthnUpdateParam.TenantsSynchronizationOptionsChanges changes = new AuthnUpdateParam.TenantsSynchronizationOptionsChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }
    }

    public static String getInterval(AuthnProviderRestRep authnProvider) {
        String interval = "";
        for (String option : authnProvider.getTenantsSynchronizationOptions()) {
            // There is only ADDITION, DELETION and interval in this StringSet.
            if (!AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString().equals(option)
                    && !AuthnProvider.TenantsSynchronizationOptions.DELETION.toString().equals(option)) {
                interval = option;
            }
        }
        return interval;
    }

    public static boolean isKeystoneAuthnProviderCreated() {
        boolean isKeystoneAuthnProviderCreated = false;
        AuthnProviderRestRep authnProvider = AuthnProviderUtils.getKeystoneAuthProvider();
        if (authnProvider.getId() != null && authnProvider.getAutoRegCoprHDNImportOSProjects() == true) {
            isKeystoneAuthnProviderCreated = true;
        }
        return isKeystoneAuthnProviderCreated;
    }

}
