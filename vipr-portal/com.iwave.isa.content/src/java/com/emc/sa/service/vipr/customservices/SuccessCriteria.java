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

package com.emc.sa.service.vipr.customservices;

import java.util.ArrayList;
import java.util.List;

public class SuccessCriteria {

    //Variables for Evaluation of SuccessCriteria and Output
    private String eval;
    private final List<String> evaluateVal = new ArrayList<String>();
    private int code;

    public String getEval() {
        return eval;
    }

    public List<String> getEvaluateVal() {
        return evaluateVal;
    }

    public void setEval(String eval) {
        this.eval = eval;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setEvaluateVal(String value, int pos) {
        evaluateVal.add(pos, value);
    }
}
