/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class PoolTypes {
    public static final String BLOCK_FILE = "block_file";
    public static final String BLOCK = "block";
    public static final String FILE = "file";
    public static final String OBJECT = "object";
    public static final String UNKNOWN = "UNKNOWN";

    public static String fromStorageSystemType(String type) {
        if (StorageSystemTypes.isBlockStorageSystem(type) && StorageSystemTypes.isFileStorageSystem(type)) {
            return BLOCK_FILE;
        }
        else if (StorageSystemTypes.isFileStorageSystem(type)) {
            return FILE;
        }
        else {
            return BLOCK;
        }
    }

    public static boolean isBlock(String type) {
        return BLOCK.equals(type);
    }

    public static boolean isFile(String type) {
        return FILE.equals(type);
    }

    public static boolean isObject(String type) {
        return OBJECT.equals(type);
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
        return StringOption.getDisplayValue(type, "PoolType");
    }
}
