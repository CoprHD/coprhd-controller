package com.emc.vipr.srm.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.vipr.srm.common.utils.ViprIngestionUtils;

/**
 * Main class to test if Webservice is working fine.
 *
 */
public class ViprSRMService {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ViprSRMService.class);

    private static String springContextFile = "/META-INF/spring/viprSRMApplicationContext.xml";
    private AbstractApplicationContext springAppContext;

    /**
     * The static service instance.
     */
    private static ViprSRMService service;

    /**
     * Initialize Spring Application context
     * 
     * @throws Exception
     */
    public void initSpringContext() throws Exception {
        if (null == springAppContext) {
            LOGGER.info("About to initialize Spring Application Context.");
            this.springAppContext = new ClassPathXmlApplicationContext(
                    springContextFile);
            LOGGER.info("Application Context Initialization was successful.");
        }
    }

    /**
     * Main method to start vipr srm
     * 
     * @param args
     *            Argument List
     */
    public static void main(String[] args) {

        service = new ViprSRMService();
        try {
            service.initSpringContext();

            /*
             * List<Map<String, String>> results =
             * SRMDataAccessor.retrieveDataWithMultipleKey(
             * "datatype='Block' & !sstype='Virtual' & parttype='LUN' & part='0015A' & name='Availability'"
             * , Arrays.asList("partsn", "ismapped", "ismasked", "dgstype",
             * "pooltype", "poolname", "partid", "luntagid", "sgname",
             * "svclevel", "devconf", "alias", "ismetah", "purpose", "isbound",
             * "slosrp", "dgraid", "config", "disktype" , "poolemul"));
             */
            /*
             * List<Map<String, String>> results =
             * SRMDataAccessor.retrieveDataWithMultipleKey(
             * "(devtype='Host' | devtype='Hypervisor') & partsn='60000970000196801612533031313245'"
             * , Arrays.asList("device"));
             */

            /*
             * List<Map<String, String>> results =
             * SRMDataAccessor.retrieveDataWithMultipleKey(
             * "datatype='Block' & !sstype='Virtual' & parttype='LUN' & part='0015A' & ismapped='0' & ismasked='0' & name='ReplicaPath'"
             * , Arrays.asList("partsn", "ismapped", "ismasked", "dgstype",
             * "pooltype", "poolname", "partid", "luntagid", "sgname",
             * "svclevel", "devconf", "alias", "ismetah", "purpose", "isbound",
             * "slosrp", "dgraid", "config", "disktype" , "poolemul"));
             */
            Map<String, String> vmap = new HashMap<>();
            vmap.put("device", "000196801612");
            long start = System.currentTimeMillis();
            System.out.println(ViprIngestionUtils.fetchUnManagedVolumes(vmap).size());// | name=='UsedCapacity'
            System.out.println("Time taken to ingest data from SRM : " + (System.currentTimeMillis() - start));
            /*final String BACKEND_STORAGE_FILTER = "(datatype=='Block' & parttype=='LUN' & part='00F3B') & (name=='UsedCapacity')";
            
            Set<String> capacity = SRMDataAccessor.retrieveDataValues(
                    BACKEND_STORAGE_FILTER, null, "Capacity");
            System.out.println(capacity);*/
        } catch (Exception e) {
            System.out.println(e);
            LOGGER.error("exception occured ", e);
        }

    }

}
