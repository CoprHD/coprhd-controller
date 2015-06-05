/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.UUID;

/**
 * Convenience implementation of QueryResultList for named element
 */
public class NamedElementQueryResultList extends QueryResultList<NamedElementQueryResultList.NamedElement> {
    public static class NamedElement {
        public URI id;
        public String name;

        public static NamedElement createElement(URI id) {
            NamedElement e = new NamedElement();
            e.id = id;
            return e;
        }

        public static NamedElement createElement(URI id, String name) {
            NamedElement e = new NamedElement();
            e.id = id;
            e.name = name;
            return e;
        }
    }

    @Override
    public NamedElement createQueryHit(URI uri) {
        return NamedElement.createElement(uri);
    }

    @Override
    public NamedElement createQueryHit(URI uri, String name, UUID timestamp) {
        return NamedElement.createElement(uri, name);
    }

}
