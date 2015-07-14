package com.emc.sa.util;

import static com.emc.sa.util.ArrayUtil.safeArray;
import static org.junit.Assert.*;

import org.junit.Test;

public class ArrayUtilTests {

	@Test
	public void test() {
		
		String[] strings = {"one", "two", "three"};
		
		String[] newStrings = safeArray(strings);
		
		assertNotEquals(strings, newStrings);
		
	}

}
