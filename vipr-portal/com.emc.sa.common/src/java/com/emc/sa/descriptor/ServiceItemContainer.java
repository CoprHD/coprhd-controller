/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.Map;

public interface ServiceItemContainer {
    public Map<String, ? extends ServiceItem> getItems();
}
