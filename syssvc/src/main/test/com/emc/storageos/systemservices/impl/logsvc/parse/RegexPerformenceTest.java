package com.emc.storageos.systemservices.impl.logsvc.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Ignore;
import org.junit.Test;

public class RegexPerformenceTest {
	private static final String[] logs = {
	"2014-01-16 19:00:01,519 [pool-35-thread-1]  INFO  DefaultSingletonBeanRegistry.java (line 433) Destroying singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@25f13769: defining beans [namespaces,scanner,registeredProfiles,reference-profile,profile-prop,profileProcessor,providerVersionSupport,resultClass-softwareIdentity,softwareIdentity-prop,softwareIdentityProcessor,system,resultClass-system,system-prop,scannerProcessor,model,reference-comp,resultClass-chassis,model-prop,modelProcessor,argscreator,smiutility,cimClient,block,commandgenerator,executor,null,bool,bool-true]; root of factory hierarchy",
	"2014-01-16 18:58:24,025 [pool-10-thread-1]  INFO  ProcessMonitor.java (line 34) ",
	"Memory Usage Metrics",
	"Total  Memory: 379MB;",
	"Available Free Memory: 146MB;",
	"Available Maximum Memory : 910MB;",
	"Used Memory: 233MB;",
	"Max used Memory : 366MB at 2014-01-01 23:03:24.025 UTC;",
	"2014-01-16 07:12:47,336 [pool-44-thread-1]  WARN  StoragePortProcessor.java (line 135) Port Discovery failed for CLARiiON+APM00121500018+SP",
	"java.lang.NullPointerException",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor.checkStoragePortExistsInDB(StorageProcessor.java:230)",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StoragePortProcessor.processResult(StoragePortProcessor.java:126)",
	"at com.emc.storageos.plugins.common.Executor.processResult(Executor.java:147)",
	"at com.emc.storageos.plugins.common.Executor.executeOperation(Executor.java:182)",
	"at com.emc.storageos.plugins.common.Executor.execute(Executor.java:114)",
	"at com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface.discover(SMICommunicationInterface.java:363)",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobInvoker.invoke(DataCollectionJobInvoker.java:132)",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobInvoker.process(DataCollectionJobInvoker.java:86)",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobConsumer.invokeJob(DataCollectionJobConsumer.java:260)",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobConsumer.consumeItem(DataCollectionJobConsumer.java:202)",
	"at com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobConsumer.consumeItem(DataCollectionJobConsumer.java:66)",
	"at com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer$1.run(DistributedQueueConsumer.java:66)",
	"at java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)",
	"at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)",
	"at java.lang.Thread.run(Thread.java:662)",
	"2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0",
	};
	
	private static final Pattern REGEX1 = Pattern
            .compile("^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\,(\\d{3}) (.+?) (.+?) (.+?) ((.+?) (.+?))\\s+(.*)$");
	private static final Pattern REGEX2 = Pattern
			.compile("^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\,(\\d{3}) (\\S+?)\\s{1,2}+(\\S+?)\\s{1,2}+(\\S+?) ((\\S+?) (\\S+?))\\s+(.*)$");
	@Test
//	@Ignore
	public void testRegexPerformance() {
		System.out.println("starting testRegexPerformance");
		long startTime = System.nanoTime();
		for(int i=0;i<10000000;i++) {
			for(String log: logs) {
				REGEX1.matcher(log);
			}
		}
		long endTime = System.nanoTime();
		System.out.println("Total used " + (endTime-startTime) + " nanoseconds");
		System.out.println("done testRegexPerformance");
	}
	
	@Test
//	@Ignore
	public void testRegexPerformance2() {
		System.out.println("starting testRegexPerformance2");
		long startTime = System.nanoTime();
		for(int i=0;i<10000000;i++) {
			for(String log: logs) {
				REGEX2.matcher(log);
			}
		}
		long endTime = System.nanoTime();
		System.out.println("Total used " + (endTime-startTime) + " nanoseconds");
		System.out.println("done testRegexPerformanc2");
	}
	
	
	
	
	@Test
	@Ignore
	public void testRegexCorrectness() {
		System.out.println("starting testRegexCorrectness");
		
		for(String log: logs) {
			Matcher m = REGEX2.matcher(log);
			if (m.matches()) {
				System.out.println("match!");
		        int year = Integer.valueOf(m.group(1));
		        int month = Integer.valueOf(m.group(2));
		        int days = Integer.valueOf(m.group(3));
		        int hours = Integer.valueOf(m.group(4));
		        int mins = Integer.valueOf(m.group(5));
		        int secs = Integer.valueOf(m.group(6));
		        int msecs = Integer.valueOf(m.group(7));
		         System.out.println(year + "-" + month + "-" + days + "-" +
		         hours + "-" + mins + "-" + secs + "-" + msecs);
		        
		        String thread = m.group(8);
		        thread = thread == null ? null : thread.trim();
		        System.out.println("Thread " + thread);
		        String level = m.group(9);
		        level = level == null ? null : level.trim();
		        System.out.println("level " + level);
		        String process = m.group(10);
		        process = process == null ? null : process.trim();
		        System.out.println("process " + process);
		        String lineNo = m.group(13);
		        System.out.println(lineNo);
		        String msg = m.group(14);
		        if (msg == null || msg.trim().length() == 0) {
		           msg = "";
		        } else {
		            msg = msg.trim();
		        }
		        System.out.println("msg " + msg);	
			} else {
				System.out.println("does not match");
			}
		
		}
	}

}
