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

import com.google.common.collect.Lists;
import util.StringOption;

import java.util.List;

public class TenantsSynchronizationOptions {
    public static final String ADDITION = "ADDITION";
    public static final String DELETION = "DELETION";
    public static final String INTERVAL = "";

    public static boolean isAutomaticAddition(String type) {
        return ADDITION.equals(type);
    }

    public static boolean isAutomaticDeletion(String type) {
        return DELETION.equals(type);
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
        return StringOption.getDisplayValue(type, "TenantsSynchronizationOptions");
    }
}
