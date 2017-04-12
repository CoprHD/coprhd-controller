package com.emc.vipr.sanity.setup

import org.apache.commons.configuration.CompositeConfiguration
import org.apache.commons.configuration.SystemConfiguration

import com.emc.vipr.sanity.Sanity;

import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.configuration.ConfigurationConverter

class SanityProperties {
    static final USE_IPV6 = "sanity.use.ipv6"
    static final SANITY_ENVIRONMENT = "sanity.environment"
    // Setting this to true will skip the system stable check.
    // Useful for devkit or local where reboot is not required
    static final SKIP_STABLE = "sanity.skip.wait.stable"

    static def loadProperties() {
        CompositeConfiguration configuration = new CompositeConfiguration();
        configuration.addConfiguration(new SystemConfiguration());
        configuration.addConfiguration(loadProperties("environment"))

        if (configuration.getBoolean(USE_IPV6, false)) {
            println "Using IPv6 Environment"
            configuration.addConfiguration(loadProperties("environment-ipv6"))
        }
        if (configuration.hasProperty(SANITY_ENVIRONMENT)) {
            String filename = configuration.getString(SANITY_ENVIRONMENT)
            println "Using Environment From File: $filename"
            configuration.addConfiguration(new PropertiesConfiguration(filename))
        }
        ConfigurationConverter.getExtendedProperties(configuration)
    }

    static def loadProperties(String name) {
        return new PropertiesConfiguration(Sanity.class.getResource("/${name}.properties"))
    }
}
