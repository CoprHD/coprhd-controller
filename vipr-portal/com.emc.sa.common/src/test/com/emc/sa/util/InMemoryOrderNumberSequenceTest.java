package com.emc.sa.util;

import org.junit.Test;
import static org.junit.Assert.*;
import com.emc.sa.zookeeper.InMemoryOrderNumberSequence;

public class InMemoryOrderNumberSequenceTest {
	
	@Test
	public void testIncrement(){
		InMemoryOrderNumberSequence sequence = new InMemoryOrderNumberSequence(null);
		
		assertEquals(1, sequence.nextOrderNumber());
	}

}
