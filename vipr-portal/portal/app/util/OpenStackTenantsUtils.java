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
package util;

import static util.BourneUtil.getViprClient;

import java.util.List;

import com.emc.storageos.model.keystone.CoprhdOsTenant;
import com.emc.storageos.model.keystone.CoprhdOsTenantListRestRep;
import com.emc.storageos.model.keystone.OpenStackTenantListParam;
import com.emc.storageos.model.keystone.OpenStackTenantParam;

public class OpenStackTenantsUtils {

    public static List<OpenStackTenantParam> getOpenStackTenants() {
        return getViprClient().openStackTenants().getAll();
    }

    public static List<CoprhdOsTenant> getOpenStackTenantsFromDataBase() {
        return getViprClient().openStackTenants().getOpenStackTenants().getCoprhdOsTenants();
    }

    public static void updateOpenStackTenants(CoprhdOsTenantListRestRep list) {
        getViprClient().openStackTenants().updateOpenStackTenants(list);
    }

    public static OpenStackTenantParam getOpenStackTenant(String id) {
        return getViprClient().openStackTenants().get(id);
    }

    public static void addOpenStackTenants(OpenStackTenantListParam list) {
        getViprClient().openStackTenants().registerOpenStackTenants(list);
    }

    public static void synchronizeOpenStackTenants() {
        getViprClient().openStackTenants().synchronizeOpenStackTenants();
    }

}
