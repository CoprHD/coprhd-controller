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
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;

/**
 *
 */
@Cf("AnsiblePackage")
public class AnsiblePackage extends AnsibleMetadata {

    private static final long serialVersionUID = 1L;

    public static final String ARCHIVE = "archive";

    private byte[] archive;

    @Name(ARCHIVE)
    public byte[] getArchive() {
        return archive;
    }

    public void setArchive(final byte[] archive) {
        this.archive = archive;
        setChanged(ARCHIVE);
    }

    @Override
    public boolean isAnsiblePackage() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public AnsiblePackage asAnsiblePackage() {
        // TODO Auto-generated method stub
        return this;
    }

}
