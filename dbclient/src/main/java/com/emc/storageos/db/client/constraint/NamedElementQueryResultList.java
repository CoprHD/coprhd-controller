/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.UUID;

/**
 * Convenience implementation of QueryResultList for named element
 */
public class NamedElementQueryResultList extends QueryResultList<NamedElementQueryResultList.NamedElement> {
    public static class NamedElement {
        private URI id;
        private String name;

        public static NamedElement createElement(URI id) {
            NamedElement e = new NamedElement();
            e.setId(id);
            return e;
        }

        public static NamedElement createElement(URI id, String name) {
            NamedElement e = new NamedElement();
            e.setId(id);
            e.setName(name);
            return e;
        }

		public URI getId() {
			return id;
		}

		public void setId(URI id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
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
