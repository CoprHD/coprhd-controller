/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.asset;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.emc.sa.util.StringComparator;
import com.emc.vipr.model.catalog.AssetOption;

public class AssetOptionsUtils {

    public static void sortOptionsByLabel(List<AssetOption> options) {
        sortOptionsByLabel(options, false);
    }

    public static void sortOptionsByLabel(List<AssetOption> options, boolean descending) {
        sortOptionsByLabel(options, new StringComparator(false, descending));
    }

    public static void sortOptionsByLabel(List<AssetOption> options, final Comparator<String> labelComparator) {
        Collections.sort(options, new Comparator<AssetOption>() {
            @Override
            public int compare(AssetOption a, AssetOption b) {
                return labelComparator.compare(a.value, b.value);
            }
        });
    }

    public static void sortOptionsByKey(List<AssetOption> options) {
        sortOptionsByKey(options, false);
    }

    public static void sortOptionsByKey(List<AssetOption> options, boolean descending) {
        sortOptionsByKey(options, new StringComparator(false, descending));
    }

    public static void sortOptionsByKey(List<AssetOption> options, final Comparator<String> valueComparator) {
        Collections.sort(options, new Comparator<AssetOption>() {
            @Override
            public int compare(AssetOption a, AssetOption b) {
                return valueComparator.compare(a.key, b.key);
            }
        });
    }

}
