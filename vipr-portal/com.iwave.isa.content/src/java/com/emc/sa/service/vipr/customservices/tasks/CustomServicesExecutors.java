package com.emc.sa.service.vipr.customservices.tasks;

import com.google.common.collect.ImmutableMap;

import java.util.List;

public class CustomServicesExecutors {
    private final ImmutableMap<String, MakeCustomServicesExecutor> typeMap;

    public CustomServicesExecutors(final List<MakeCustomServicesExecutor> tasks) {
        final ImmutableMap.Builder<String, MakeCustomServicesExecutor> typeMapBuilder = ImmutableMap.<String, MakeCustomServicesExecutor>builder();
        for( final MakeCustomServicesExecutor task : tasks ) {
            typeMapBuilder.put(task.getType(), task);
        }

        typeMap = typeMapBuilder.build();
    }

    public MakeCustomServicesExecutor get(final String type) {
        return typeMap.get(type);
    }

}
