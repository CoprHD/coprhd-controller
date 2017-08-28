import groovy.transform.Field

String portalPropsPath = "conf/messages"
String defaultCatalogPropsPath = "../com.emc.sa.common/src/java/com/emc/sa/catalog/default-catalog.properties"
String serviceDescriptorsPropsPath = "../com.emc.sa.common/src/java/com/emc/sa/descriptor/ServiceDescriptors.properties"
String assetOptionsPropsPath = "../com.iwave.isa.content/src/java/com/emc/sa/asset/AssetProviders.properties"  
String servicePropsPath = "../com.emc.sa.engine/src/java/com/emc/sa/engine/ViPRService.properties"
String customServicesPropsPath = "../com.emc.sa.common/src/java/com/emc/sa/customservices/custom-services-builtin.properties"

String javaZuluSuffix = "_zu"
String playZuluSuffix = ".zu"

processProperties(portalPropsPath, playZuluSuffix)
processProperties(defaultCatalogPropsPath, javaZuluSuffix)
processProperties(serviceDescriptorsPropsPath, javaZuluSuffix)
processProperties(assetOptionsPropsPath, javaZuluSuffix)
processProperties(servicePropsPath, javaZuluSuffix)
processProperties(customServicesPropsPath, javaZuluSuffix)

def processProperties(String path, String newSuffix) {
    println "Processing $path..."
    Properties props = loadPropertiesFile(path)
    Properties newProps = translateProps(props)
    storePropertiesFile(newProps, getNewPropsPath(path, newSuffix))
    println "Done processing $path."
    println ""
}


def getNewPropsPath(String path, String suffix) {
    if (path.contains(".")) {
        return path.reverse().replaceFirst("\\.", "."+suffix.reverse()).reverse()        
    }
    else {
        return path+suffix
    }
}


def translateProps(Properties props) {
    Properties newProps = new Properties();
    print "Processing messages file..."
    for ( String prop : props.stringPropertyNames()) {
        String propValue = props[prop]
        String newPropValue = newPropValue(props, prop)
        newProps.setProperty(prop, newPropValue)
        //println sprintf("%s=%s [%s]", prop, propValue, newPropValue)
        
    }
    println "done."
    return newProps
}


def storePropertiesFile(Properties props, String path) {
    print "Storing properties file $path..."
    FileOutputStream out = new FileOutputStream(path)
    try {
        props.store(out, null)
    }
    finally {
        out.close()
    }
    println "done."
}


def loadPropertiesFile(String path) {
    print "Loading properties file $path..."
    FileInputStream stream = new FileInputStream(path)
    Properties props = new Properties()
    try {
        props.load(stream)
    }
    finally {
        stream.close()
    }
    println "done."
    return props
}


def newPropValue(Properties props, String propKey) {
    
    def charTable = getCharTable() 
                         
    def propValue = props[propKey]
            
    // if the property is an 'image' property we don't want to do anything to it.
    if ( propKey.contains(".image") && !propValue.equals("Icon")) {
        return propValue
    }
    
    def newPropValue = []
    for (int i=0; i < propValue.length() ; i++) {
        String c = propValue.charAt(i)
        if (c.equals("<")) {
            boolean done = false
            int level = 1
            newPropValue[i] = c
            while (!done) {
                i++
                c = propValue.charAt(i)
                if (c.equals("<")) level++
                if (c.equals(">")) level--
                if (level == 0) done = true
                newPropValue[i] = c
            }
            
        }
        String r = charTable[c]
        if (r == null || r.empty) {
            r = c
        }
        newPropValue[i] = r
    }
    
    return newPropValue.join("")
}


def getCharTable() {
    return [
            "A" : "\u0202", 
            "a" : "\u0203", 
            "B" : "\u00DF", 
            "b" : "\u00FE", 
            "C" : "\u0187", 
            "c" : "\u00A2", 
            "D" : "\u010E", 
            "d" : "", // we can't change 'd' because it's used for formatting 
            "E" : "\u0112", 
            "e" : "\u0113", 
            "F" : "\u0191", 
            "f" : "\u0192",
            "G" : "\u0122",
            "g" : "\u011D", 
            "H" : "\u0126",
            "h" : "\u0125",
            "I" : "\u0128", 
            "i" : "\u012B", 
            "J" : "\u0134",
            "j" : "\u0135", 
            "K" : "\u0136", 
            "k" : "\u0137", 
            "L" : "\u013B", 
            "l" : "\u0140", 
            "M" : "", 
            "m" : "", 
            "N" : "\u0145", 
            "n" : "\u0146", 
            "O" : "\u01FE", 
            "o" : "\u01FF", 
            "P" : "\u01F7", 
            "p" : "\u01A5", 
            "Q" : "", 
            "q" : "",
            "R" : "\u01A6", 
            "r" : "\u0211", 
            "S" : "\u0218", 
            "s" : "", // we can't change 's' because it's used for formatting
            "T" : "", 
            "t" : "\u021B", 
            "U" : "\u0172", 
            "u" : "\u0171", 
            "V" : "", 
            "v" : "", 
            "W" : "", 
            "w" : "", 
            "X" : "",
            "x" : "", 
            "Y" : "\u0232", 
            "y" : "\u0233", 
            "Z" : "\u0224", 
            "z" : "\u0225"
        ]
}

