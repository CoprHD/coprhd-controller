/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.model.dao;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;

import java.net.URI;
import java.util.List;

public class WFDirectoryFinder extends ModelFinder<WFDirectory> {

    protected static final String PARENT_COLUMN_NAME = "parent";
    public WFDirectoryFinder(DBClientWrapper client) {
        super(WFDirectory.class, client);
    }

    public List<WFDirectory> getChildren(URI parentID) {
        List<NamedElement> childIDs = client.findBy(WFDirectory.class, PARENT_COLUMN_NAME, parentID);
        return findByIds(toURIs(childIDs));
    }
}
