package comet.vipr;

import java.net.URI;
import java.util.List;

import com.emc.vipr.client.ViPRCoreClient;

public class ViperCaller {

    private ViPRCoreClient client;
    public ViperCaller(){
        client = MigrateClient.getViprClient();
    }
    public List<URI>  getVolumes(){
        
       return client.blockVolumes().listBulkIds();
    }
    
    public List<URI> getHosts(){
        return client.hosts().listBulkIds();
    }
}
