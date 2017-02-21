/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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

/**
 * Column family that represents a script file
 */
@Cf("CustomServiceScriptResource")
public class CustomServicesScriptResource extends CustomServicesPrimitiveResource {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean isCustomServiceAnsiblePackage() {
        return false;
    }

    @Override
    public CustomServicesAnsiblePackage asCustomServiceAnsiblePackage() {
        return null;
    }

    @Override
    public boolean isCustomServiceScriptResource() {
        return true;
    }

    @Override
    public CustomServicesScriptResource asCustomServiceScriptResource() {
        return this;
    }

    @Override
    public String suffix() {
        return ".sh";
    }

}
