/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.asset;

import java.util.Collection;
import java.util.Iterator;

public abstract class AssetFilter<T> {
    protected abstract boolean accept(AssetOptionsContext context, T value);

    public void filter(AssetOptionsContext context, Collection<T> values) {
        Iterator<T> iter = values.iterator();
        while (iter.hasNext()) {
            T value = iter.next();
            if (!accept(context, value)) {
                iter.remove();
            }
        }
    }
}
