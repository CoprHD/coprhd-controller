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
import com.google.common.collect.Lists;
import util.AuthnProviderUtils;
import util.StringOption;

import java.util.List;

public class TenantSource {
    private static final String OPTION_PREFIX = "TenantSource";
    
    // Tenants sources
    public static final String TENANTS_SOURCE_ALL = "All";
    public static final String TENANTS_SOURCE_OS = "OpenStack";
    public static final String TENANTS_SOURCE_LOCAL = "Local";

    // User Mapping keys
    public static final String USER_MAPPING_KEY_OS = "tenant_id";

    private TenantSource(){
    }

    public static String getTenantSource(List<UserMappingParam> userMappings) {
        if (userMappings == null || userMappings.isEmpty()) {
            return TENANTS_SOURCE_LOCAL;
        }

        for (UserMappingParam userMapping : userMappings) {
            String domain = userMapping.getDomain();
            if (!domain.equals(getDomainFromKeystoneAuthProvider())) {
                return TENANTS_SOURCE_LOCAL;
            }
        }

        for (UserMappingParam userMapping : userMappings) {
            for (UserMappingAttributeParam attribute : userMapping.getAttributes()) {
                String key = attribute.getKey();
                if (key.equals(USER_MAPPING_KEY_OS)) {
                    return TENANTS_SOURCE_OS;
                }
            }
        }
        return TENANTS_SOURCE_LOCAL;
    }

    private static String getDomainFromKeystoneAuthProvider() {
        AuthnProviderRestRep provider = AuthnProviderUtils.getKeystoneAuthProvider();
        if (provider != null) {
            return provider.getDomains().iterator().next();
        }
        return null;
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, OPTION_PREFIX);
    }
}
