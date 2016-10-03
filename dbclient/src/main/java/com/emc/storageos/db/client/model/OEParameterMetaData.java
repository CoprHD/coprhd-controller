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
package com.emc.storageos.db.client.model;

public class OEParameterMetaData {

    private String _value;
    private String _listName;
    private String _friendlyName;
    private boolean _locked;
    private boolean _required;
    protected String _type;

    public String getValue() {
        return _value;
    };

    public void setValue(final String value) {
        _value = value;
    };

    public String getType() {
        return _type;
    }

    public void setType(final String type) {
        _type = type;
    }

    public String getListName() {
        return _listName;
    }

    public void setListName(final String listName) {
        _listName = listName;

    }

    public boolean isLocked() {
        return _locked;
    }

    public void setLocked(final boolean locked) {
        _locked = locked;
    }

    public boolean isRequired() {
        return _required;
    }

    public void setRequired(final boolean required) {
        _required = required;
    }

    public String getFriendlyName() {
        return _friendlyName;
    }

    public void setFriendlyName(final String friendlyName) {
        _friendlyName = friendlyName;
    }
}
