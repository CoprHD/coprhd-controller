/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import java.util.List;

import com.google.common.collect.Lists;

import util.StringOption;

public class FileProtocols {
    public static final String NFS = "NFS";
    public static final String NFSV4 = "NFSv4";
    public static final String CIFS = "CIFS";

    public static boolean isNFS(String type) {
        return NFS.equals(type);
    }

    public static boolean isNFSv4(String type) {
        return NFSV4.equals(type);
    }

    public static boolean isCIFS(String type) {
        return CIFS.equals(type);
    }

    public static boolean isFileProtocol(String type) {
        return isNFS(type) || isNFSv4(type) || isCIFS(type);
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
        return StringOption.getDisplayValue(type, "FileStorageProtocol");
    }
}
