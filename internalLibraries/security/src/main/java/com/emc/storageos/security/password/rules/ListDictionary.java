/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
