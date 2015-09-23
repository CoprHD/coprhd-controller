README.properties
-----------------

1. Introduction

   Basic configuration parameters for Bourne clusters are defined as OVF properties. The properties are
   defined in two property definition files files:

     packaging/storageos-propeperties-simple.def
     packaging/storageos-propeperties-iterable.def

   The first file defines simple properties that have identical keys and values for all the cluster nodes.
   For example: the property "network.ntpserver", which contains a list of NTP servers, is common for all
   the nodes. The second file defines properties which values may be unique for each node, and which names
   are canonically expanded. For example, the property "network.${node}.eth0.ipaddr", contaiing node's IP
   address will be expanded to multiple instances: "network.node1.eth0.ipaddr", "network.node2.eth0.ipaddr",
   etc., and each instance may be assigned a different value. Both files use JSON-like syntax to define
   property names, types, labels, etc. The property definition files combine all property attributes in one
   place. In particular all attributes needed in the OVF, such as label, description, type and type qualifiers,
   as well as, the extended metadata used by the syssvc (e.g userMutable, rebootRequired), and additional
   tags and indicators thats syssvc makes available for third party apps.

2. Generated files

   The Bourne build uses several files generated using the "genprops.py" script from the property definition
   files:

      syssvc/src/conf/sys-metadata-var.xml
      syssvc/src/conf/sys-metadata-var-template.xml
      $(BUILD_BASE)/etc/config.defaults
      $(BUILD_BASE)/obj/packaging/ovfbuild/storageos-standalone-properties.xml
      $(BUILD_BASE)/obj/packaging/ovfbuild/storageos-cluster-properties.xml
      $(BUILD_BASE)/obj/packaging/ovfbuild/storageos-dataservice-properties.xml

   The first two files contain Spring Framework beans used by the syssvc to instantiate the map of extended
   metadata. The up to date versions of these files should be checked in to the source code repository,
   so that one can build and run Java code without running make. Thus, if you change the property definition
   files, remember to run:

      make -C packaging properties

   The remaining files are placed in the build tree, and are used, respectively,  to inject full set of
   required properties with default values when running Bourne on BourneDevKit (the BourneDevKit OVF has
   network configuration properties only), and for defining properties in the generated OVF envelope files.

3. Property default values

   The properties defined in the property definition files

         packaging/storageos-propeperties-simple.def
         packaging/storageos-propeperties-iterable.def

   may or may not have default values. The default values must fit into the constraints defined by the type,
   minLen, maxLen and allowedValues. Note, all special characters that are considered special in XML, have
   to be encoded. Note, that the properties without must have userConfigurable=true, as the user must be to
   configure them upon deployment. Also, they require special handling in /etc/genconfig since they will not
   be defined on BourneDevKit.

4. Property attributes

   The following property attributes are used:

   key              - property name
   label            - property label
   description      - property description
   type             - property type
   minLen           - minimum length of the value (string only)
   maxLen           - maximum length of the value (string only)
   allowedValues    - a list of values to select from (string and int types only)
   tag              - property group name (string)
   advanced         - advanced configuration only (bool)
   hidden           - private internal property (bool)
   userMutable      - user configurable through the REST APIs
   userConfigurable - user configurable on deployment
   rebootRequired   - reboot is required to propagate the property change
   reconfigRequired - reconfiguration or reboot is required to propagate the property change
   controlNodeOnly  - if true, the property is masked out on datanodes (obsolete)
   value            - default value
   notifiers        -
   siteSpecific     - only applied to current site. for DR
   poweroffAgreementRequired - all nodes need reboot simultaneously to propagate the property change
 
   The following types are supported: uint8, int8, uint16, int16, uint32, int32, uint64, int64, real32,
   real64, string, boolean, encryptedstring, text, encryptedtext, ipv6addr, iplist, emaillist.

