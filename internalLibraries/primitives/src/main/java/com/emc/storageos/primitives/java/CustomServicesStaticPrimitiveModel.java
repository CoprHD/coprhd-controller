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
package com.emc.storageos.primitives.java;


import java.net.URI;

import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveModel;

/**
 * Class representing a primitive that cannot be changed
 *
 */
public class CustomServicesStaticPrimitiveModel extends CustomServicesPrimitiveModel {
    
    private static final long serialVersionUID = 1L;
    
    public CustomServicesStaticPrimitiveModel(final URI id, final String name) {
        super.setId(id);
        super.setLabel(name);
    }

    @Override
    public void setLabel(final String label) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void setId(final URI id) {
        throw new UnsupportedOperationException();
    }
    

    
}
