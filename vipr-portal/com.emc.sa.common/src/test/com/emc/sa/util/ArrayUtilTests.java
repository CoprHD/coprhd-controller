package com.emc.sa.util;

import static com.emc.sa.util.ArrayUtil.safeArrayCopy;
import static org.junit.Assert.*;

import org.junit.Test;

public class ArrayUtilTests {

    @Test
    public void test() {

        String[] strings = { "one", "two", "three" };

        String[] newStrings = safeArrayCopy(strings);

        assertNotEquals(strings, newStrings);

    }

}
