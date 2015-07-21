/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Map "term" represents a single push operation.
 * @author watson
 *
 */
class MapBuilderTerm {
    MapBuilderTermType type;
    JClass jclass;
    String alias;
    List<JClass> joinPath;
    Map<URI, Set<URI>> duples;
    Map<URI, Map> map;
}
