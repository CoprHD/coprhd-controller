/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.bind;

import java.util.Set;

/**
 * Interface to encapsulate parameter access. This way we can support Map, Transaction or
 * other access types to properties.
 *
 * @author Chris Dail
 */
public interface ParameterAccess {
    public Set<String> getNames();

    public Object get(String name);

    public void set(String name, Object value);
}
