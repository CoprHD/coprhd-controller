/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

/**
 * Map "terms" can either be:
 * a Set<URI>, or a Set<T>, or a List<T> where <T extends DataObject>.
 * 
 * @author watson
 * 
 */
enum MapBuilderTermType {
    URI, SET, LIST
};
