/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

public abstract class Wrapper<T> {

    public abstract T getValue();

    public abstract void setValue(T object);

}
