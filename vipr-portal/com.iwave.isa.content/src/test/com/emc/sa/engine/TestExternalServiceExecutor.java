package com.emc.sa.engine;

import java.io.IOException;








import org.xeustechnologies.jcl.test.TestInterface;

import com.emc.sa.catalog.ExtentionClassLoader;
import com.emc.sa.descriptor.TestExternalInterface;
import com.emc.sa.engine.service.ExternalTaskApdapterInterface;
import com.emc.sa.service.vipr.block.tasks.CreateBlockVolume;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeRestRep;

public class TestExternalServiceExecutor {

	
    public static void main(String[] args) throws Exception {
    	
        TestInterface obj1 = (TestInterface) ExtentionClassLoader.getProxyObject("org.xeustechnologies.jcl.test.Test" );
    	System.out.println("Messge from extension loader "+obj1.sayHello());

    	TestExternalInterface obj2 = (TestExternalInterface) ExtentionClassLoader.getProxyObject("com.emc.sa.service.vipr.plugins.object.TestExternalInterfaceImpl" );
    	System.out.println("Messge from extension loader "+obj2.sayHello());
 
    	ExternalTaskApdapterInterface obj3 = (ExternalTaskApdapterInterface) ExtentionClassLoader.getProxyObject("com.emc.sa.service.vipr.plugins.tasks.CustomSample" );
    	//System.out.println("Messge from extension loader "+obj3.executeExternal().toDisplayString());
    	
    	Class<?>  c=Class.forName("com.emc.sa.service.vipr.block.tasks.CreateBlockVolume");  
    	WaitForTasks<VolumeRestRep> s = (WaitForTasks<VolumeRestRep>)c.newInstance();  
    	System.out.println("CreateBlockVolume "+ s.executeTask());
    	
    	//new CreateBlockVolume();
		
	}
}
