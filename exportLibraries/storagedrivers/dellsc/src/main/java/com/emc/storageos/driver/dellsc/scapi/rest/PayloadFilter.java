/*
 * Copyright 2016 Dell Inc.
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
package com.emc.storageos.driver.dellsc.scapi.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * API filter parameters.
 */
public class PayloadFilter {

    private Map<String, Map<String, Object>> payload;
    private Map<String, Object> filterData = new HashMap<>(2);
    private List<Map<String, Object>> filters = new ArrayList<>();

    /**
     * Instantiates a new Payload filter.
     *
     * @param filterType the filter type
     */
    public PayloadFilter(FilterType filterType) {
        payload = new HashMap<>();
        payload.put("filter", filterData);
        filterData.put("filterType", filterType.getValue());
        filterData.put("filters", filters);
    }

    /**
     * Instantiates a new Payload filter.
     */
    public PayloadFilter() {
        this(FilterType.AND);
    }

    /**
     * Append a filter.
     *
     * @param name The attribute to filter on.
     * @param val The value.
     * @param filterType The filter type.
     */
    public void append(String name, Object val, ValueFilterType filterType) {
        if (val != null) {
            Map<String, Object> apiFilter = new HashMap<>();
            apiFilter.put("attributeName", name);
            apiFilter.put("attributeValue", val);
            apiFilter.put("filterType", filterType.getValue());
            filters.add(apiFilter);
        }
    }

    /**
     * Append a filter.
     *
     * @param name The attribute to filter on.
     * @param val The value.
     */
    public void append(String name, Object val) {
        this.append(name, val, ValueFilterType.EQUALS);
    }

    /**
     * To json string.
     *
     * @return the string
     */
    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(payload);
    }

    /**
     * The enum Filter type.
     */
    public enum FilterType {
        /**
         * And filter type.
         */
        AND("AND"),
        /**
         * Or filter type.
         */
        OR("OR");

        private String val;

        FilterType(String val) {
            this.val = val;
        }

        /**
         * Gets value.
         *
         * @return the value
         */
        public String getValue() {
            return val;
        }
    }

    /**
     * The enum Value filter type.
     */
    public enum ValueFilterType {
        /**
         * Equals value filter type.
         */
        EQUALS("Equals"),
        GREATERTHAN("GreaterThan"),
        GREATERTHANOREQUAL("GreaterThanOrEqual"),
        LESSTHAN("LessThan"),
        LESSTHANORQUAL("LessThanOrEqual"),
        INCLUDEDINSTRING("IncludedInString"),
        INCLUDESSTRING("IncludesStr");

        private String val;

        ValueFilterType(String val) {
            this.val = val;
        }

        /**
         * Gets value.
         *
         * @return the value
         */
        public String getValue() {
            return val;
        }
    }
}