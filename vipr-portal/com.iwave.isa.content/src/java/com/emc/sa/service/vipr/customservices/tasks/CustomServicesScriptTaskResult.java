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

package com.emc.sa.service.vipr.customservices.tasks;

public class CustomServicesScriptTaskResult extends CustomServicesTaskResult {

    private final String scriptOut;

    public CustomServicesScriptTaskResult(final String scriptOut, final String out, final String err, final int retCode) {
        super(out, err, retCode, null);
        this.scriptOut = scriptOut;
    }

    public String getScriptOut() {
        return scriptOut;
    }
}
