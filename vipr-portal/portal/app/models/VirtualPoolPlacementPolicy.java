/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class VirtualPoolPlacementPolicy {
    public static final String DEFAULT = "default_policy";
    public static final String ARRAY_AFFINITY = "array_affinity";

    public static boolean isDefaultPolicy(String policy) {
        return DEFAULT.equals(policy);
    }

    public static boolean isArrayAffinityPolicy(String policy) {
        return ARRAY_AFFINITY.equals(policy);
    }

    public static StringOption option(String policy) {
        return new StringOption(policy, getDisplayValue(policy));
    }

    public static List<StringOption> options(String... policies) {
        List<StringOption> options = Lists.newArrayList();
        for (String policy : policies) {
            options.add(option(policy));
        }
        return options;
    }

    public static String getDisplayValue(String policy) {
        return StringOption.getDisplayValue(policy, "VirtualPoolPlacementPolicy");
    }
}
