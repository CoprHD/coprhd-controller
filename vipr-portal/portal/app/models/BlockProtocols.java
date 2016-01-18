/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class BlockProtocols {
    public static final String FC = "FC";
    public static final String FCoE = "FCoE";
    public static final String iSCSI = "iSCSI";
    public static final String ScaleIO = "ScaleIO";
    public static final String RBD = "RBD";

    public static boolean isFC(String type) {
        return FC.equals(type);
    }

    public static boolean isFCoE(String type) {
        return FCoE.equals(type);
    }

    public static boolean isISCSI(String type) {
        return iSCSI.equals(type);
    }

    public static boolean isScaleIO(String type) {
        return ScaleIO.equals(type);
    }

    public static boolean isRBD(String type) {
        return RBD.equals(type);
    }

    public static boolean isBlockProtocol(String type) {
        return isFC(type) || isFCoE(type) || isISCSI(type) || isScaleIO(type) || isRBD(type);
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
        return StringOption.getDisplayValue(type, "BlockStorageProtocol");
    }
}
