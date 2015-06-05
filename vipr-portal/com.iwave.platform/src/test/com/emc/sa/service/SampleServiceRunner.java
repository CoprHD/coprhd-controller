package com.emc.sa.service;

import com.emc.storageos.db.client.model.uimodels.Order;

public class SampleServiceRunner {
    public static void main(String[] args) {
        Order order = ServiceRunner.executeOrder("SampleService", "text=Hello World", "number=42", "choice=No Choice",
                "rollback=false");
        ServiceRunner.dumpOrder(order);
    }
}
