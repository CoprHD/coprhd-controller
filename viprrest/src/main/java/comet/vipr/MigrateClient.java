package comet.vipr;

import com.emc.vipr.client.AuthClient;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;

public class MigrateClient {
    
    private static ViPRCoreClient viprCoreClient;
    private static MigrateClient clientObj;
    
//    private final String VIPR_IP = "lglbv223.lss.emc.com";
    private final String VIPR_IP = "lglou196.lss.emc.com";
    private final String VIPR_USER= "root";
    private final String VIPR_PASSWORD = "ChangeMe1!";
    
    
    private MigrateClient(){
        init();
    }
    private void  init(){
        try{
         initializeViprClient(VIPR_IP, 
                VIPR_USER, VIPR_PASSWORD);
        }catch(Exception e){
            e.printStackTrace();
        }

    }
    
    private void initializeViprClient(String viprIP,  String userName, String password) {
        ClientConfig clientConfig = new ClientConfig().withHost(viprIP)
                .withRequestLoggingDisabled().withMaxRetries(10).withMediaType("application/json")
                .withIgnoringCertificates(true);
        viprCoreClient = new ViPRCoreClient(clientConfig);
        AuthClient auth = viprCoreClient.auth();
        String token = auth.login(userName, password);
        viprCoreClient.setAuthToken(token);
    }
    
    public static ViPRCoreClient getViprClient(){
        if(viprCoreClient==null){
            clientObj= new MigrateClient();
        }
        return viprCoreClient;
    }
    
}
