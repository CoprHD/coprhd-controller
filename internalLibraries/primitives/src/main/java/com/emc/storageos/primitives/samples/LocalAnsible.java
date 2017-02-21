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
package com.emc.storageos.primitives.samples;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.primitives.CustomServicesStaticPrimitive;
import com.emc.storageos.primitives.input.BasicInputParameter.StringParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter.NameValueListParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Class that represents primitive meta data for an ansible script that can be
 * executed locally
 */
@XmlRootElement(name = "primitive")
public class LocalAnsible extends CustomServicesStaticPrimitive {

    private final static URI id = URI.create(String.format(
            "urn:storageos:%1$s:%2$s:", LocalAnsible.class.getSimpleName(),
            LocalAnsible.class.getName()));
    private final static String FRIENDLY_NAME = "Ansible playbook";
    private final static StepType TYPE = StepType.LOCAL_ANSIBLE;
    private final static String DESCRIPTION = "Locally executed ansible playbook";
    private final static String SUCCESS_CRITERIA = "code = 0";

    private final static StringParameter CONTENT = new StringParameter(
            "content", true, null);
    //private final static InputParameter INPUT[] = { CONTENT };
    
    private final static List<InputParameter> INPUT_LIST = ImmutableList.<InputParameter>builder().add(CONTENT).build();
    private final static Map<InputType, List<InputParameter>> INPUT = ImmutableMap.<InputType, List<InputParameter>>builder().put(InputType.INPUT_PARAMS, INPUT_LIST).build();
    private final static NameValueListParameter OUTPUT = new NameValueListParameter(
            "output");
    private final static BasicOutputParameter OUTPUT_LIST[] = { OUTPUT };

    public LocalAnsible() {
        super(id, LocalAnsible.class.getName());
}

    /* (non-Javadoc)
     * @see com.emc.storageos.primitives.Primitive#getInput()
     */
    @Override
    public Map<InputType, List<InputParameter>> getInput() {
        return INPUT;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.primitives.Primitive#getOutput()
     */
    @Override
    public List<OutputParameter> getOutput() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitive#getFriendlyName()
     */
    @Override
    public String getFriendlyName() {
        return FRIENDLY_NAME;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitive#getDescription()
     */
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitive#getSuccessCriteria()
     */
    @Override
    public String getSuccessCriteria() {
        return SUCCESS_CRITERIA;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitive#getType()
     */
    @Override
    public StepType getType() {
        return StepType.LOCAL_ANSIBLE;
    }

}
