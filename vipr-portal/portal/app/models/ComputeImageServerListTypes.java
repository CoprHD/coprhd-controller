/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class ComputeImageServerListTypes {

    public static String NO_COMPUTE_IMAGE_SERVER_NONE = "None";

    public static StringOption option(String type) {
        return new StringOption(type, type);
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

}
