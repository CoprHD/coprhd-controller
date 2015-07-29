/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListDictionary implements Dictionary {

    private List<String> words = new ArrayList(Arrays.asList(
            "password", "12345678", "abc123"
            ));

    public void setWords(List<String> words) {
        this.words = words;
    }

    public boolean search(String word) {
        return words.contains(word);
    }
}
