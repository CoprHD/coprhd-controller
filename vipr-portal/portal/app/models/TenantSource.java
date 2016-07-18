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
package models;

import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import util.AuthnProviderUtils;

import java.util.List;

public class TenantSource {
    // Tenants sources
    public static final String TENANTS_SOURCE_OS = "OpenStack";
    public static final String TENANTS_SOURCE_LOCAL = "Local";

    // User Mapping keys
    public static final String USER_MAPPING_KEY_OS = "tenant_id";

    public static String getTenantSource(List<UserMappingParam> userMappings) {
        String source = TENANTS_SOURCE_LOCAL;
        for (UserMappingParam userMapping : userMappings) {
            for (UserMappingAttributeParam attribute : userMapping.getAttributes()) {
                String key = attribute.getKey();
                if (key.equals(USER_MAPPING_KEY_OS)) {
                    source = TENANTS_SOURCE_OS;
                    break;
                }
            }
        }
        return source;
    }

    public static String getDomainFromKeystoneAuthProvider() {
        AuthnProviderRestRep provider = AuthnProviderUtils.getKeystoneAuthProvider();
        if (provider != null) {
            return provider.getDomains().iterator().next();
        }
        return null;
    }
}
