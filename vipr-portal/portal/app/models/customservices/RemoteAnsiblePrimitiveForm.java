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
package models.customservices;

import play.data.validation.Required;

import java.io.File;

public class RemoteAnsiblePrimitiveForm {
    private String id;
    @Required
    private String name;
    private String description;
    @Required
    private String playbookPath;
    @Required
    private String ansibleBinPath;
    private String inputs; // comma separated list of inputs
    private String outputs; // comma separated list of ouputs
    private String wfDirID;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPlaybookPath() {
        return playbookPath;
    }

    public void setPlaybookPath(String playbookPath) {
        this.playbookPath = playbookPath;
    }

    public String getAnsibleBinPath() {
        return ansibleBinPath;
    }

    public void setAnsibleBinPath(String ansibleBinPath) {
        this.ansibleBinPath = ansibleBinPath;
    }

    public String getInputs() {
        return inputs;
    }

    public void setInputs(String inputs) {
        this.inputs = inputs;
    }

    public String getOutputs() {
        return outputs;
    }

    public void setOutputs(String outputs) {
        this.outputs = outputs;
    }

    public String getWfDirID() {
        return wfDirID;
    }

    public void setWfDirID(String wfDirID) {
        this.wfDirID = wfDirID;
    }
}
