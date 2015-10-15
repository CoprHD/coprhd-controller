package com.emc.storageos.api.service.utils;

import org.junit.Before;
import org.junit.Test;

public class ApisvcTest extends ApisvcTestBase {
	
	@Before
	public void setup() {
		startApisvc();
	}
	
	// @Test
	public void test1() {
		printLog("Test1 running");
		try {
			Thread.sleep(180000);
		} catch (InterruptedException ex) {
			// no action
		}
	}
	
	private void printLog(String s) {
		System.out.println(s);
		log.info(s);;
	}

}
