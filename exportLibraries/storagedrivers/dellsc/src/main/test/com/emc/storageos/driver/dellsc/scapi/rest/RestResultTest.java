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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the RestResult class.
 */
public class RestResultTest {

    private static final String TEST_URL = "http://example.com";
    private static final int TEST_CODE = 999;
    private static final String TEST_MSG = "Test";
    private static final String TEST_RESULT = "TestResult";

    @Test
    public void testRestResult() {
        RestResult rr = new RestResult(TEST_URL, TEST_CODE, TEST_MSG, TEST_RESULT);
        Assert.assertTrue(TEST_URL.equals(rr.getUrl()));
        Assert.assertTrue(TEST_CODE == rr.getResponseCode());
        Assert.assertTrue(TEST_MSG.equals(rr.getErrorMsg()));
        Assert.assertTrue(TEST_RESULT.equals(rr.getResult()));
    }

}
