#-%emc-cr-s-shell-v2%-
#
# Copyright (c) 2012-2015, EMC Corporation. All Rights Reserved.
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.
# This software is protected, without limitation, by copyright law and
# international treaties.
# Use of this software and the intellectual property contained therein
# is expressly limited to the terms and conditions of the License
# Agreement under which it is provided by or on behalf of EMC.
#
#-%emc-cr-e-shell-v2%-
#
# packaging/Makefile
#

SUBDIRS := monolithicSparse

include ../Makefile.subdir
include ../Makefile.common

                 PRODUCT_RELEASE  = $(GIT_REVISION)
                         PRODUCT  = $(PRODUCT_NAME)-$(PRODUCT_VERSION).$(PRODUCT_RELEASE)

                   RPMBUILD_NAME ?= storageos
                  RPMBUILD_FORCE ?= FORCE

               RPMBUILD_RPM_ARCH := x86_64
               RPMBUILD_RPMS_DIR  = $(BUILD_BASE)/RPMS
                    RPM_REVISION := 1
               RPMBUILD_RPM_FILE  = $(RPMBUILD_RPMS_DIR)/$(RPMBUILD_RPM_ARCH)/$(RPMBUILD_NAME)-$(PRODUCT_VERSION).$(PRODUCT_RELEASE)-$(RPM_REVISION).$(RPMBUILD_RPM_ARCH).rpm
              RPMBUILD_SPEC_FILE  = $(SOURCE_BASE)/packaging/$(BUILD_TYPE)/$(RPMBUILD_NAME).spec
               RPMBUILD_BUILDDIR  = $(OBJDIR)/rpmbuild
                RPMBUILD_TMP_DIR  = $(RPMBUILD_BUILDDIR)
              RPMBUILD_BUILDROOT  = $(RPMBUILD_BUILDDIR)/buildroot-$(RPMBUILD_NAME)-$(PRODUCT_VERSION)
                    RPMBUILD_LOG  = $(RPMBUILD_BUILDDIR)/rpmbuild.log
                  RPMBUILD_FLAGS  = -bb \
                                    --buildroot "$(RPMBUILD_BUILDROOT)" \
                                    --define '__spec_build_pre /bin/true' \
                                    --define "_topdir $(RPMBUILD_BUILDDIR)" \
                                    --define "_builddir $(RPMBUILD_BUILDDIR)" \
                                    --define "_tmppath $(RPMBUILD_TMP_DIR)" \
                                    --define "_sourcedir $(RPMBUILD_BUILDDIR)" \
                                    --define "_rpmdir $(RPMBUILD_RPMS_DIR)" \
                                    --define "_prefix $(PRODUCT_PREFIX)" \
                                    --define "product_name $(RPMBUILD_NAME)" \
                                    --define "product_brand $(PRODUCT_BRAND)" \
                                    --define "product_version $(PRODUCT_VERSION).$(PRODUCT_RELEASE)" \
                                    --define "product_release $(RPM_REVISION)"
       RPMBUILD_CONTENT_MD5_FILE  = $(OBJDIR)/rpmcontent.md5
                 DOCKER_BUILDDIR  = $(OBJDIR)/dockerbuild

 OVFBUILD_CONTROLLER_L_VCPU_CNT := 4
OVFBUILD_CONTROLLER_L_MEMORY_MB := 16384
    OVFBUILD_CONTROLLER_L_FLAGS  = vcpu_count=$(OVFBUILD_CONTROLLER_L_VCPU_CNT) memory_mb=$(OVFBUILD_CONTROLLER_L_MEMORY_MB)

 OVFBUILD_CONTROLLER_S_VCPU_CNT := 2
OVFBUILD_CONTROLLER_S_MEMORY_MB := 8192
    OVFBUILD_CONTROLLER_S_FLAGS  = vcpu_count=$(OVFBUILD_CONTROLLER_S_VCPU_CNT) memory_mb=$(OVFBUILD_CONTROLLER_S_MEMORY_MB)

  OVFBUILD_GENTMPL_PRODUCT_FLAGS  = product_brand='$(PRODUCT_BRAND)' \
                                    product_name='$(PRODUCT_NAME)' \
                                    product_release='$(PRODUCT_RELEASE)' \
                                    product_version='$(PRODUCT_VERSION)' \
                                    product_version_uint16='$(PRODUCT_VERSION)' \
                                    product_gitrevision='$(GIT_REVISION)'
                                   
               OVFBUILD_OVAS_DIR  = $(BUILD_BASE)/OVAS
               OVFBUILD_OVFS_DIR  = $(BUILD_BASE)/OVFS
			  
           OVFBUILD_OVAS_README  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT).README
          OVFBUILD_OVAS_MD5SUMS  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT).MD5SUMS
         OVFBUILD_OVAS_SHA1SUMS  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT).SHA1SUMS
       OVFBUILD_OVAS_ISOMD5SUMS  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-iso-md5.dat

              OVFBUILD_OVAS_ISO  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT).iso
              OVFBUILD_OVAS_IMG  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT).img
              
 OVFBUILD_UPGRADE_METADATA_FILE  = $(OVFBUILD_OVAS_DIR)/vipr.md

         OVFBUILD_OVAS_ISOFILES  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-2+1.ova \
                                   $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-3+2.ova

            OVFBUILD_OVAS_FILES  = $(OVFBUILD_OVAS_ISOFILES) \
                                   $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-1+0.ova \
                                   $(OVFBUILD_OVAS_IMG)

     OVFBUILD_OVAS_HYPERV_FILES  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-hyperv.zip

              OVFBUILD_OVFS_IMG  = $(OVFBUILD_OVFS_DIR)/$(PRODUCT).img

        OVFBUILD_OVFS_ENVELOPES  = $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-standalone.ovf \
                                   $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-1+0.ovf \
                                   $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-2+1.ovf \
                                   $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-3+2.ovf

        OVFBUILD_OVFS_MANIFESTS  = $(OVFBUILD_OVFS_ENVELOPES:.ovf=.mf)

            OVFBUILD_OVFS_DISK1  = $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk1.vmdk
            OVFBUILD_OVFS_DISK2  = $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk2.vmdk
            OVFBUILD_OVFS_DISK3  = $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk3.vmdk
            OVFBUILD_OVFS_DISK5  = $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk5.vmdk
            OVFBUILD_OVFS_DISKS  = $(OVFBUILD_OVFS_DISK1) $(OVFBUILD_OVFS_DISK2) $(OVFBUILD_OVFS_DISK3) $(OVFBUILD_OVFS_DISK5)

       OVFBUILD_CONFIG_FILE_NAME = ovf-env.properties
                         UI_FILE = $(SOURCE_BASE)/packaging/$(UI_FILE_NAME)
                 COMMON_UI_FILES = $(SOURCE_BASE)/packaging/common-bottom.js \
                                   $(SOURCE_BASE)/packaging/common-top.js \
                                   $(SOURCE_BASE)/packaging/common.css \
                                   $(SOURCE_BASE)/packaging/config-wizard.html

                    LICENSE_FILE = $(SOURCE_BASE)/packaging/storageos-license.txt
       OVFBUILD_CONFIG_FILE_SIZE = 4096
             OVFBUILD_DISK4_SIZE = 2097152
             OVFBUILD_HYPERV_ZIP = $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-hyperv.zip

            OVFBUILD_HYPERV_DIR  = $(OBJDIR)/$(PRODUCT)-controller-hyperv

       OVFBUILD_HYPERV_MANIFEST  = $(OVFBUILD_HYPERV_DIR)/$(PRODUCT).mf
 OVFBUILD_HYPERV_PS_LICENSE_FILE = $(OVFBUILD_HYPERV_DIR)/storageos-license-ps.txt

          OVFBUILD_HYPERV_VHDX1  = $(OVFBUILD_HYPERV_DIR)/$(PRODUCT)-disk1.vhdx
          OVFBUILD_HYPERV_VHDX2  = $(OVFBUILD_HYPERV_DIR)/$(PRODUCT)-disk2.vhdx
          OVFBUILD_HYPERV_VHDX3  = $(OVFBUILD_HYPERV_DIR)/$(PRODUCT)-disk3.vhdx
          OVFBUILD_HYPERV_VHDXS  = $(OVFBUILD_HYPERV_VHDX1) $(OVFBUILD_HYPERV_VHDX2) $(OVFBUILD_HYPERV_VHDX3)
            PS_COMMON_FUNCTIONS  = $(SOURCE_BASE)/packaging/common-functions.ps1
OVFBUILD_HYPERV_PS_COMMON_FUNCTIONS = $(OVFBUILD_HYPERV_DIR)/common-functions.ps1
    OVFBUILD_HYPERV_SCRIPT_TMPL  = $(SOURCE_BASE)/packaging/hyperv/storageos-deployment-template.ps1
  OVFBUILD_HYPERV_DEPLOY_SCRIPT  = $(OVFBUILD_HYPERV_DIR)/$(PRODUCT)-deployment.ps1
     HYPERV_DISK4_ZIPPED_HEADER  = $(SOURCE_BASE)/packaging/hyperv/disk4-vhdx-header.zip.base64
   OVFBUILD_HYPERV_DISK4_HEADER  = $(OVFBUILD_HYPERV_DIR)/disk4-vhdx-header
    HYPERV_DISK4_ZIPPED_TRAILER  = $(SOURCE_BASE)/packaging/hyperv/disk4-vhdx-trailer.zip.base64
  OVFBUILD_HYPERV_DISK4_TRAILER  = $(OVFBUILD_HYPERV_DIR)/disk4-vhdx-trailer
      OVFBUILD_HYPERV_TMP_FILES  = $(OVFBUILD_HYPERV_DISK4_HEADER) $(OVFBUILD_HYPERV_DISK4_TRAILER) $(OVFBUILD_HYPERV_PS_COMMON_FUNCTIONS)
        OVFBUILD_HYPERV_SCRIPTS  = $(OVFBUILD_HYPERV_DEPLOY_SCRIPT)
             HYPERV_UI_FILE_NAME = config-wizard-hyperv.html
                  HYPERV_UI_FILE = $(SOURCE_BASE)/packaging/hyperv/$(HYPERV_UI_FILE_NAME)
         OVFBUILD_HYPERV_UI_FILE = $(OVFBUILD_HYPERV_DIR)/$(PRODUCT)-$(HYPERV_UI_FILE_NAME)

           OVFBUILD_VSPHERE_DIR  = $(OBJDIR)/$(PRODUCT)-controller-vsphere
                      ISO_HEADER = $(OVFBUILD_VSPHERE_DIR)/iso-header
                     ISO_TRAILER = $(OVFBUILD_VSPHERE_DIR)/iso-trailer
           OVFBUILD_VSPHERE_ZIP  = $(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-vsphere.zip
OVFBUILD_VSPHERE_BASH_SCRIPT_TMPL  = $(SOURCE_BASE)/packaging/vsphere/storageos-deployment-template.sh
   OVFBUILD_VSPHERE_BASH_SCRIPT  = $(OVFBUILD_VSPHERE_DIR)/${PRODUCT}-deployment.sh
OVFBUILD_VSPHERE_PS_COMMON_FUNCTIONS = $(OVFBUILD_VSPHERE_DIR)/common-functions.ps1
       OVFBUILD_VSPHERE_PS_TMPL  = $(SOURCE_BASE)/packaging/vsphere/storageos-deployment-template.ps1
            OVFBUILD_VSPHERE_PS  = $(OVFBUILD_VSPHERE_DIR)/${PRODUCT}-deployment.ps1
       OVFBUILD_VSPHERE_SCRIPTS  = $(OVFBUILD_VSPHERE_BASH_SCRIPT) $(OVFBUILD_VSPHERE_PS)
            VSPHERE_UI_FILE_NAME = config-wizard-vsphere.html
                 VSPHERE_UI_FILE = $(SOURCE_BASE)/packaging/vsphere/$(VSPHERE_UI_FILE_NAME)
        OVFBUILD_VSPHERE_UI_FILE = $(OVFBUILD_VSPHERE_DIR)/$(PRODUCT)-$(VSPHERE_UI_FILE_NAME)

               OVF_VSPHERE_TMPL  = $(SOURCE_BASE)/packaging/vsphere/storageos-vsphere-template.xml
      OVFBUILD_OVF_VSPHERE_TMPL  = $(OVFBUILD_VSPHERE_DIR)/storageos-vsphere-template.xml
           OVF_VSPHERE_VMX_TMPL  = $(SOURCE_BASE)/packaging/vsphere/vmx-template
  OVFBUILD_OVF_VSPHERE_VMX_TMPL  = $(OVFBUILD_VSPHERE_DIR)/vmx-template
        VSPHERE_DESCRIPTOR_TMPL  = $(SOURCE_BASE)/packaging/vsphere/vmdk-descriptor-template
OVFBUILD_VSPHERE_DESCRIPTOR_TMPL = $(OVFBUILD_VSPHERE_DIR)/vmdk-descriptor-template
          OVFBUILD_VSPHERE_TMPLS = $(OVFBUILD_OVF_VSPHERE_TMPL) $(OVFBUILD_OVF_VSPHERE_VMX_TMPL) \
                                   $(OVFBUILD_VSPHERE_DESCRIPTOR_TMPL)

OVFBUILD_VSPHERE_PS_LICENSE_FILE = $(OVFBUILD_VSPHERE_DIR)/storageos-license-ps.txt
OVFBUILD_VSPHERE_BASH_LICENSE_FILE = $(OVFBUILD_VSPHERE_DIR)/storageos-license-sh.txt
         OVFBUILD_VSPHERE_DISK1  = $(OVFBUILD_VSPHERE_DIR)/$(PRODUCT)-disk1.vmdk
         OVFBUILD_VSPHERE_DISK2  = $(OVFBUILD_VSPHERE_DIR)/$(PRODUCT)-disk2.vmdk
         OVFBUILD_VSPHERE_DISK3  = $(OVFBUILD_VSPHERE_DIR)/$(PRODUCT)-disk3.vmdk
         OVFBUILD_VSPHERE_DISKS  = $(OVFBUILD_VSPHERE_DISK1) $(OVFBUILD_VSPHERE_DISK2) $(OVFBUILD_VSPHERE_DISK3)

              OVFBUILD_UI_FILES  = $(OVFBUILD_HYPERV_UI_FILE) $(OVFBUILD_VSPHERE_UI_FILE)

          OVFBUILD_BASE_RELEASE := 2
          OVFBUILD_BASE_URL      = http://lglaf020.lss.emc.com/images/vipr/ViPRBase/2.4.1.0/$(OVFBUILD_BASE_RELEASE)/
            OVFBUILD_BASE_NAME  = base-2.4.1.0.$(OVFBUILD_BASE_RELEASE)
              OVFBUILD_BASE_DIR  = $(OBJDIR)/$(OVFBUILD_BASE_NAME)
              OVFBUILD_BASE_OVF  = $(OVFBUILD_BASE_DIR)/ViPRBase.x86_64-2.4.1.0.$(OVFBUILD_BASE_RELEASE).ovf
             OVFBUILD_BASE_DISK  = $(OVFBUILD_BASE_DIR)/ViPRBase.x86_64-2.4.1.0.$(OVFBUILD_BASE_RELEASE)-disk1.vmdk

        OVFBUILD_BASE_FLAT_NAME  = base-flat-2.4.1.0.$(OVFBUILD_BASE_RELEASE)
         OVFBUILD_BASE_FLAT_DIR  = $(OBJDIR)/$(OVFBUILD_BASE_FLAT_NAME)
         OVFBUILD_BASE_FLAT_VMX  = $(OVFBUILD_BASE_FLAT_DIR)/$(OVFBUILD_BASE_FLAT_NAME).vmx
        OVFBUILD_BASE_FLAT_DISK  = $(OVFBUILD_BASE_FLAT_DIR)/$(OVFBUILD_BASE_FLAT_NAME)-disk1-flat.vmdk

           OVFBUILD_ROOTIMG_DIR  = $(OBJDIR)/rootimg
          OVFBUILD_ROOTIMG_FILE  = $(OVFBUILD_ROOTIMG_DIR)/rootimg.img

      OVFBUILD_BOOTFS_FLAT_NAME := bootfs-flat
       OVFBUILD_BOOTFS_FLAT_DIR  = $(OBJDIR)/$(OVFBUILD_BOOTFS_FLAT_NAME)
       OVFBUILD_BOOTFS_FLAT_VMX  = $(OVFBUILD_BOOTFS_FLAT_DIR)/$(OVFBUILD_BOOTFS_FLAT_NAME).vmx
      OVFBUILD_BOOTFS_FLAT_DISK  = $(OVFBUILD_BOOTFS_FLAT_DIR)/$(OVFBUILD_BOOTFS_FLAT_NAME)-disk1-flat.vmdk

           OVFBUILD_BOOTFS_NAME := bootfs
            OVFBUILD_BOOTFS_DIR  = $(OBJDIR)/$(OVFBUILD_BOOTFS_NAME)
            OVFBUILD_BOOTFS_OVF  = $(OVFBUILD_BOOTFS_DIR)/$(OVFBUILD_BOOTFS_NAME).ovf
           OVFBUILD_BOOTFS_DISK  = $(OVFBUILD_BOOTFS_DIR)/$(OVFBUILD_BOOTFS_NAME)-disk1.vmdk
           OVFBUILD_BOOTFS_VHDX  = $(OVFBUILD_HYPERV_VHDX1)

          OVFBUILD_OVF_BUILDDIR  = $(OBJDIR)/ovfbuild
   OVFBUILD_OVF_STANDALONE_TMPL  = $(OVFBUILD_OVF_BUILDDIR)/storageos-standalone-template.xml

      OVFBUILD_OVF_CLUSTER_TMPL  = $(OVFBUILD_OVF_BUILDDIR)/storageos-cluster-template.xml
             OVFBUILD_OVF_TMPLS  = $(OVFBUILD_OVF_STANDALONE_TMPL) $(OVFBUILD_OVF_CLUSTER_TMPL) $(OVFBUILD_OVF_VSPHERE_TMPL)
 OVFBUILD_OVF_PROPERTY_INCLUDES  = $(OVFBUILD_OVF_BUILDDIR)/storageos-standalone-properties.xml \
                                   $(OVFBUILD_OVF_BUILDDIR)/storageos-cluster-properties.xml
          OVFBUILD_OVF_INCLUDES  = $(OVFBUILD_OVF_BUILDDIR)/storageos-disk2.vmdk.info \
                                   $(OVFBUILD_OVF_BUILDDIR)/storageos-disk3.vmdk.info \
                                   $(OVFBUILD_OVF_BUILDDIR)/storageos-disk5.vmdk.info \
                                   $(OVFBUILD_OVF_BUILDDIR)/storageos-cdrom.info \
                                   $(OVFBUILD_OVF_BUILDDIR)/storageos-license.txt \
                                   $(OVFBUILD_OVF_PROPERTY_INCLUDES)
CONTROLLER_OVF_DEFINITIONS       := storageos-properties-ovfenv.def
               ITER_DEFINITIONS  := storageos-properties-iterable.def
CONTROLLER_PROPERTY_DEFINITIONS  := $(ITER_DEFINITIONS) $(CONTROLLER_OVF_DEFINITIONS) storageos-properties-config.def
    OVF_PROPERTY_METADATA_BEANS   = $(SOURCE_BASE)/syssvc/src/conf/sys-metadata-var.xml \
                                    $(SOURCE_BASE)/syssvc/src/conf/sys-metadata-var-template.xml
                OVFBUILD_SVT_DATA = $(OVFBUILD_OVAS_DIR)/$(PRODUCT).SVTDATA
                 CONFIG_DEFAULTS  = $(BUILD_BASE)/etc/config.defaults
          OVF_CONTROLLER_DEFAULTS = $(BUILD_BASE)/etc/.ovfenv.properties
                    ITER_DEFAULTS = $(BUILD_BASE)/etc/.iterable.properties
                         GENTMPL  = $(SOURCE_BASE)/etc/gentmpl
                      SED_SCRIPT  = $(SED) \
                                   -e 's,@product_brand@,$(PRODUCT_BRAND),g'   \
                                   -e 's,@product_name@,$(PRODUCT_NAME),g'     \
                                   -e 's,@product_caps@,$(PRODUCT_CAPS),g'     \
                                   -e 's,@product_prefix@,$(PRODUCT_PREFIX),g' \
                                   -e 's,@product_version@,$(PRODUCT_VERSION),g'

            OVFBUILD_NATIVE_NAME := native
             OVFBUILD_NATIVE_DIR  = $(OBJDIR)/$(OVFBUILD_NATIVE_NAME)
             OVFBUILD_NATIVE_IMG  = $(OVFBUILD_NATIVE_DIR)/$(PRODUCT)-$(OVFBUILD_NATIVE_NAME).iso

.SUFFIXES:
.SUFFIXES: .mf .ovf .vmdk .vmx .xml .md .ps1

vpath %.mf
vpath %.ovf
vpath %.ova
vpath %.vmdk
vpath %.vmx
vpath %.xml
vpath %.md
vpath %.ps1

# Rules for generating OVAs
#
$(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-%.ova: $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-%.ovf $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-%.mf $(OVFBUILD_OVFS_DISK1) $(OVFBUILD_OVFS_DISK2) $(OVFBUILD_OVFS_DISK3)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_OVFS_DIR) && $(TAR) --format=gnu -cvf $(@).temp $(notdir $(+)) $(TEEOUT) $(OBJDIR)/$(@F).log
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller $(*) OVA:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-1+0.ova: $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-1+0.ovf $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-1+0.mf $(OVFBUILD_OVFS_DISK1) $(OVFBUILD_OVFS_DISK2) $(OVFBUILD_OVFS_DISK5)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_OVFS_DIR) && $(TAR) --format=gnu -cvf $(@).temp $(notdir $(+)) $(TEEOUT) $(OBJDIR)/$(@F).log
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller 1+0 OVA:"
	@echo "***    $(@)"
	@echo "***"

#TODO: add $(OVFBUILD_HYPERV_UI_FILE) back after UI files are stable
$(OVFBUILD_OVAS_DIR)/$(PRODUCT)-controller-hyperv.zip: $(OVFBUILD_HYPERV_SCRIPTS) $(OVFBUILD_HYPERV_VHDXS) $(OVFBUILD_DISK4_ISO_HEADER) $(OVFBUILD_DISK4_ISO_TRAILER)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_HYPERV_DIR)/.. && $(ZIP) -9 $(@).temp $(addprefix $(notdir $(OVFBUILD_HYPERV_DIR))/, $(notdir $(+)) $(+)) $(TEEOUT) $(OBJDIR)/$(@F).log
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller-hyperv ZIP:"
	@echo "***    $(@)"
	@echo "***"

#TODO: add $(OVFBUILD_VSPHERE_UI_FILE) back after UI files are stable
$(OVFBUILD_VSPHERE_ZIP): $(OVFBUILD_VSPHERE_DISKS) $(OVFBUILD_VSPHERE_BASH_SCRIPT) $(OVFBUILD_VSPHERE_PS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_VSPHERE_DIR)/.. && $(ZIP) -9 $(@).temp $(addprefix $(notdir $(OVFBUILD_VSPHERE_DIR))/, $(notdir $(+)) $(+)) $(TEEOUT) $(OBJDIR)/$(@F).log
	$(ATSIGN)$(SUDO) $(CHMOD) 444 $(@).temp
	$(ATSIGN)$(SUDO) $(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller-vsphere ZIP:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_HYPERV_UI_FILE): $(HYPERV_UI_FILE) $(COMMON_UI_FILES)
$(OVFBUILD_VSPHERE_UI_FILE): $(VSPHERE_UI_FILE) $(COMMON_UI_FILES)

$(OVFBUILD_UI_FILES):
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(CP) $(COMMON_UI_FILES) $(@D)
	$(ATSIGN)cd $(@D) && $(GENTMPL) $(OVFBUILD_CONTROLLER_S_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) < $(<) > $(@)

$(OVFBUILD_OVF_VSPHERE_TMPL): $(OVF_VSPHERE_TMPL)
$(OVFBUILD_OVF_VSPHERE_TMPL): $(OVF_VSPHERE_TMPL)
$(OVFBUILD_OVF_VSPHERE_VMX_TMPL): $(OVF_VSPHERE_VMX_TMPL)
$(OVFBUILD_VSPHERE_DESCRIPTOR_TMPL): $(VSPHERE_DESCRIPTOR_TMPL)

$(OVFBUILD_VSPHERE_TMPLS):
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(CP) $(<) $(@)

$(OVFBUILD_VSPHERE_BASH_LICENSE_FILE): $(LICENSE_FILE)
	$(ATSIGN)$(SED) -e 's/&quot;/\\"/g' -e "s/&apos;/'/g" $(<) > $@
	$(ATSIGN)$(CP) $(<) $(@D)

$(OVFBUILD_VSPHERE_BASH_SCRIPT): $(OVFBUILD_VSPHERE_BASH_SCRIPT_TMPL) $(OVFBUILD_VSPHERE_TMPLS) $(OVFBUILD_VSPHERE_BASH_LICENSE_FILE)

$(OVFBUILD_VSPHERE_PS_COMMON_FUNCTIONS): $(PS_COMMON_FUNCTIONS)
	$(ATSIGN)$(CP) $(<) $(@)

$(OVFBUILD_VSPHERE_PS_LICENSE_FILE): $(LICENSE_FILE)
	$(ATSIGN)$(SED) -e 's/&quot;/\`"/g' -e "s/&apos;/\`'/g" $(<) >$(@)

$(OVFBUILD_VSPHERE_PS): $(OVFBUILD_VSPHERE_PS_TMPL) $(OVFBUILD_VSPHERE_TMPLS) $(OVFBUILD_VSPHERE_PS_COMMON_FUNCTIONS) $(OVFBUILD_VSPHERE_PS_LICENSE_FILE)

$(OVFBUILD_VSPHERE_SCRIPTS):
	$(ATSIGN)cd $(OVFBUILD_VSPHERE_DIR) && $(GENTMPL) product_variant=vsphere $(OVFBUILD_CONTROLLER_S_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
        disk1=$(notdir $(OVFBUILD_VSPHERE_DISK1)) $(shell ./getvmdkinfo.sh disk1 $(OVFBUILD_BOOTFS_OVF)) \
        disk2=$(notdir $(OVFBUILD_VSPHERE_DISK2)) $(shell $(CAT) storageos-disk2.vmdk.info) \
        disk3=$(notdir $(OVFBUILD_VSPHERE_DISK3)) $(shell $(CAT) storageos-disk3.vmdk.info) \
        config_file_size="${OVFBUILD_CONFIG_FILE_SIZE}" \
        data_disk_mf='$(shell ./vsphere/get-manifest-template.sh $(OVFBUILD_VSPHERE_DISK1) $(OVFBUILD_VSPHERE_DISK2) $(OVFBUILD_VSPHERE_DISK3))' \
           <$(<) >$(@).temp
	$(ATSIGN)cd $(OVFBUILD_VSPHERE_DIR) && $(GENTMPL) product_variant=vsphere \
        $(OVFBUILD_CONTROLLER_S_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
        <$(@).temp > $(@).temp1

	$(ATSIGN)$(MV) $(@).temp1 $(@)
	$(ATSIGN)$(CHMOD) 755 $(@)

# Rules for generating OVF manifests
#
%.mf: %.ovf $(OVFBUILD_OVFS_DISKS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(@D) && $(OPENSSL) sha1 $(notdir $(+)) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

.SECONDARY: $(OVFBUILD_HYPERV_SCRIPTS) $(OVFBUILD_HYPERV_VHDXS)

$(OVFBUILD_DISK4_ISO_HEADER): $(HYPERV_DISK4_ISO_HEADER)
	$(ATSIGN)$(CP) $(HYPERV_DISK4_ISO_HEADER) $(@)

$(OVFBUILD_DISK4_ISO_TRAILER): $(HYPERV_DISK4_ISO_TRAILER)
	$(ATSIGN)$(CP) $(HYPERV_DISK4_ISO_TRAILER) $(@)

# Rules for generating OVF envelopes for standalone and n-way clusters
#
$(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-standalone.ovf: $(OVFBUILD_OVF_STANDALONE_TMPL)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_OVF_BUILDDIR) && $(GENTMPL) product_variant=standalone node_count=1 $(OVFBUILD_CONTROLLER_S_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
                                disk1=$(notdir $(OVFBUILD_OVFS_DISK1)) $(shell ./getvmdkinfo.sh disk1 $(OVFBUILD_BOOTFS_OVF)) \
                                disk2=$(notdir $(OVFBUILD_OVFS_DISK2)) $(shell $(CAT) storageos-disk2.vmdk.info) \
                                disk3=$(notdir $(OVFBUILD_OVFS_DISK5)) $(shell $(CAT) storageos-disk5.vmdk.info) <$(<) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller standalone OVF:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-%.ovf: $(OVFBUILD_OVF_CLUSTER_TMPL)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_OVF_BUILDDIR) && variant="$(*)" && $(GENTMPL) --foreach:iter:`$(SEQ) -s : $$(($${variant}))` product_variant="$${variant}" node_count=$$(($${variant})) network_vip="" $(OVFBUILD_CONTROLLER_S_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
                                disk1=$(notdir $(OVFBUILD_OVFS_DISK1)) $(shell ./getvmdkinfo.sh disk1 $(OVFBUILD_BOOTFS_OVF)) \
                                disk2=$(notdir $(OVFBUILD_OVFS_DISK2)) $(shell $(CAT) storageos-disk2.vmdk.info) \
                                disk3=$(notdir $(OVFBUILD_OVFS_DISK3)) $(shell $(CAT) storageos-disk3.vmdk.info) <$(<) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller $(*) OVF:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-1+0.ovf: $(OVFBUILD_OVF_CLUSTER_TMPL)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_OVF_BUILDDIR) && variant="1+0" && $(GENTMPL) --foreach:iter:`$(SEQ) -s : $$(($${variant}))` product_variant="$${variant}" node_count=$$(($${variant})) network_vip="" $(OVFBUILD_CONTROLLER_S_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
                                disk1=$(notdir $(OVFBUILD_OVFS_DISK1)) $(shell ./getvmdkinfo.sh disk1 $(OVFBUILD_BOOTFS_OVF)) \
                                disk2=$(notdir $(OVFBUILD_OVFS_DISK2)) $(shell $(CAT) storageos-disk2.vmdk.info) \
                                disk3=$(notdir $(OVFBUILD_OVFS_DISK5)) $(shell $(CAT) storageos-disk5.vmdk.info) <$(<) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller 1+0 OVF:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_OVFS_DIR)/$(PRODUCT)-controller-3+2.ovf: $(OVFBUILD_OVF_CLUSTER_TMPL)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(OVFBUILD_OVF_BUILDDIR) && variant="3+2" && $(GENTMPL) --foreach:iter:`$(SEQ) -s : $$(($${variant}))` product_variant="$${variant}" node_count=$$(($${variant})) network_vip="" $(OVFBUILD_CONTROLLER_L_FLAGS) $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
                                disk1=$(notdir $(OVFBUILD_OVFS_DISK1)) $(shell ./getvmdkinfo.sh disk1 $(OVFBUILD_BOOTFS_OVF)) \
                                disk2=$(notdir $(OVFBUILD_OVFS_DISK2)) $(shell $(CAT) storageos-disk2.vmdk.info) \
                                disk3=$(notdir $(OVFBUILD_OVFS_DISK3)) $(shell $(CAT) storageos-disk3.vmdk.info) <$(<) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND)-controller-3+2 OVF:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_OVFS_ENVELOPES): $(OVFBUILD_BOOTFS_OVF) $(OVFBUILD_OVF_INCLUDES) $(OVFBUILD_OVFS_DISKS)

#
# Hyper-V scripts
#
.PHONY: scripts vhdxs

scripts: $(OVFBUILD_HYPERV_SCRIPTS)
vhdxs: $(OVFBUILD_HYPERV_VHDXS)

$(OVFBUILD_HYPERV_PS_LICENSE_FILE): $(LICENSE_FILE)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(SED) -e 's/&quot;/\`"/g' -e "s/&apos;/\`'/g" $(<) >$(@)
	$(ATSIGN)$(CP) $(<) $(@D)

$(OVFBUILD_HYPERV_PS_COMMON_FUNCTIONS): $(PS_COMMON_FUNCTIONS)
$(OVFBUILD_HYPERV_DISK4_HEADER): $(HYPERV_DISK4_ZIPPED_HEADER)
$(OVFBUILD_HYPERV_DISK4_TRAILER): $(HYPERV_DISK4_ZIPPED_TRAILER)

$(OVFBUILD_HYPERV_TMP_FILES):
	$(ATSIGN)$(CP) $(<) $(@)

$(OVFBUILD_HYPERV_DEPLOY_SCRIPT): $(OVFBUILD_HYPERV_SCRIPT_TMPL) $(OVFBUILD_HYPERV_PS_LICENSE_FILE) $(OVFBUILD_HYPERV_TMP_FILES)
	$(ATSIGN)cd $(OVFBUILD_HYPERV_DIR) && $(GENTMPL) product_variant=hyperv $(OVFBUILD_CONTROLLER_S_FLAGS) \
        $(OVFBUILD_GENTMPL_PRODUCT_FLAGS) \
        config_file_size="${OVFBUILD_CONFIG_FILE_SIZE}" \
           <$(<) >$(@).temp

	$(ATSIGN)$(MV) $(@).temp $(@)
	$(ATSIGN)$(CHMOD) 755 $(@)

# Stream-optimized VMDK images, dynamic VHDX imahe and the Upgrade Image (.img)
#
$(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk1.vmdk: $(OVFBUILD_BOOTFS_DISK)
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(HARD_LN) $(<) $(@)

$(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk%.vmdk: storageos-disk%.vmdk
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(CP) $(<) $(@)
	$(ATSIGN)$(CHMOD) 444 $(@)

$(OVFBUILD_HYPERV_DIR)/$(PRODUCT)-disk%.vhdx: storageos-disk%.vhdx.bz2
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(BZIP2) -dc <$(<) | $(DD) bs=4k | $(CP) --sparse=always /dev/stdin $(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

$(OVFBUILD_VSPHERE_DIR)/$(PRODUCT)-disk1.vmdk: $(OVFBUILD_BOOTFS_DISK)
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(HARD_LN) $(<) $(@)

$(OVFBUILD_VSPHERE_DIR)/$(PRODUCT)-disk%.vmdk: $(OVFBUILD_OVFS_DIR)/$(PRODUCT)-disk%.vmdk
	$(ATSIGN)$(SUDO) $(RM) $(@)
	$(ATSIGN)$(SUDO) $(MKDIR) $(@D)
	$(ATSIGN)$(SUDO) $(CP) $(<) $(@)
	$(ATSIGN)$(SUDO) $(CHMOD) 444 $(@)

$(OVFBUILD_OVFS_IMG): $(OVFBUILD_ROOTIMG_FILE)
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(HARD_LN) $(<) $(@)


# Final OVAs and ISO
#
.PHONY: ova iso isomd5 isosha1 clean-ova

ova:	ovf $(OVFBUILD_OVAS_MD5SUMS) $(OVFBUILD_OVAS_SHA1SUMS) $(OVFBUILD_OVAS_README) $(OVFBUILD_UPGRADE_METADATA_FILE)
iso:	ova isomd5 isosha1

$(OVFBUILD_OVAS_ISOMD5SUMS): $(OVFBUILD_OVAS_ISOFILES)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)cd $(@D) && $(MD5SUM) $(notdir $(+)) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

$(OVFBUILD_OVAS_MD5SUMS): $(OVFBUILD_OVAS_FILES)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)cd $(@D) && $(MD5SUM) $(notdir $(+)) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

isomd5: $(OVFBUILD_OVAS_ISO)
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(CHMOD) a+w $(OVFBUILD_OVAS_MD5SUMS)
	$(ATSIGN)cd $(OVFBUILD_OVAS_DIR) && $(CP) $(OVFBUILD_OVAS_MD5SUMS) $(OVFBUILD_OVAS_MD5SUMS).temp && \
		$(MD5SUM) $(notdir $(+)) >>$(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(CHMOD) 444 $(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(MV) $(OVFBUILD_OVAS_MD5SUMS).temp $(OVFBUILD_OVAS_MD5SUMS)

$(OVFBUILD_OVAS_SHA1SUMS): $(OVFBUILD_OVAS_FILES)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)cd $(@D) && $(SHA1SUM) $(notdir $(+)) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

isosha1: $(OVFBUILD_OVAS_ISO)
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(CHMOD) a+w $(OVFBUILD_OVAS_SHA1SUMS)
	$(ATSIGN)cd $(OVFBUILD_OVAS_DIR) && $(CP) $(OVFBUILD_OVAS_SHA1SUMS) $(OVFBUILD_OVAS_SHA1SUMS).temp && \
		$(SHA1SUM) $(notdir $(+)) >>$(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(CHMOD) 444 $(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(MV) $(OVFBUILD_OVAS_SHA1SUMS).temp $(OVFBUILD_OVAS_SHA1SUMS)

$(OVFBUILD_OVAS_README): storageos-template.README
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(+) >$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

$(OVFBUILD_OVAS_ISO): $(OVFBUILD_OVAS_ISOFILES) $(OVFBUILD_OVAS_ISOMD5SUMS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) "Creating $(@F)"
	$(ATSIGN)cd $(@D) && $(GENISOIMAGE) -o $(@).temp $(notdir $(+))
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND) ISO image:"
	@echo "***    $(@)"
	@echo "***"

$(OVFBUILD_OVAS_IMG): $(OVFBUILD_ROOTIMG_FILE)
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)$(HARD_LN) $(<) $(@)
 
clean-ova:
	$(RM) -r $(OVFBUILD_OVAS_DIR)

# Final OVFs
#
.PHONY: ovf clean-ovf

ovf:		$(OVFBUILD_OVFS_MANIFESTS) $(OVFBUILD_OVFS_ENVELOPES) $(OVFBUILD_OVFS_DISKS) $(OVFBUILD_OVFS_IMG)

clean-ovf:
	$(RM) -r $(OVFBUILD_OVFS_DIR)

# OVF Builddir
#
.PHONY: ovfbuilddir clean-ovfbuilddir

ovfbuilddir: $(OVFBUILD_OVF_TMPLS) $(OVFBUILD_OVF_INCLUDES)

clean-ovfbuilddir:
	$(ATSIGN)$(RM) -r $(OVFBUILD_OVF_BUILDDIR)

$(OVFBUILD_OVF_BUILDDIR)/%: %
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) ln -s $(@F)
	$(ATSIGN)$(LN) $(PWD)/$(*) $(@)

# Bootable installation ISO image for native hardware
#
.PHONY: svt clean-svt

svt: $(OVFBUILD_SVT_DATA)

clean-svt:
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_DIR)/*.SVTDATA

$(OVFBUILD_OVAS_DIR)/$(PRODUCT).SVTDATA: FORCE
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATECHO) gensvtdata.py $(OVFBUILD_OVAS_DIR)/$(@F) $(PRODUCT)
	$(ATSIGN)./gensvtdata.py $(OVFBUILD_OVAS_DIR) $(PRODUCT) >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

# Bootable installation ISO image for native hardware
#
.PHONY: native clean-native

native: $(OVFBUILD_NATIVE_IMG)

clean-native:
	$(ATSIGN)$(RM) -r $(OVFBUILD_NATIVE_DIR)

$(OVFBUILD_NATIVE_IMG):
	$(ATSIGN)$(RM) -r $(@D)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) native
	$(ATSIGN)$(SUDO) ./mkiso.sh $(@) $(OVFBUILD_ROOTIMG_FILE) $(TEEOUT) $(@D)/mkiso.log

# Docker image that has the rpm installed
#
.PHONY: docker clean-docker

docker: Dockerfile $(RPMBUILD_RPM_FILE)
ifeq ($(BUILD_TYPE),emc)
	$(ATECHO) Cannot generate docker image in emc mode, specify BUILD_TYPE=oss instead
else
	$(ATSIGN)$(MKDIR) $(DOCKER_BUILDDIR)
	$(ATSIGN)$(CP) Dockerfile $(DOCKER_BUILDDIR)
	$(ATSIGN)$(CP) $(RPMBUILD_RPM_FILE) $(DOCKER_BUILDDIR)
	$(ATSIGN)docker build -t coprhd-devkit $(DOCKER_BUILDDIR)
endif

clean-docker:
	$(ATSIGN)$(RM) -r $(DOCKER_BUILDDIR)

# Stream-optimized bootfs VMDK
#
.PHONY: bootfs clean-bootfs

bootfs: $(OVFBUILD_BOOTFS_DISK) $(OVFBUILD_BOOTFS_VHDX)

clean-bootfs:
	$(ATSIGN)$(RM) -r $(OVFBUILD_BOOTFS_DIR)
	$(ATSIGN)$(RM)    $(OVFBUILD_BOOTFS_VHDX)

$(OVFBUILD_BOOTFS_OVF): $(OVFBUILD_BOOTFS_FLAT_VMX)
	$(ATSIGN)$(RM) -r $(@D)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) bootfs.vmdk
	$(ATSIGN)$(SUDO) $(OVFTOOL) $(<) $(@) $(TEEOUT) $(@D)/ovftool.log
	$(ATSIGN)$(SUDO) $(CHMOD) 666 $(OVFBUILD_BOOTFS_DISK)
        
$(OVFBUILD_BOOTFS_DISK): $(OVFBUILD_BOOTFS_OVF)

$(OVFBUILD_BOOTFS_VHDX): $(OVFBUILD_BOOTFS_FLAT_VMX)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) bootfs.vhdx
	$(ATSIGN)$(SUDO) $(QEMU_IMG) convert -O vhdx -o subformat=dynamic $(OVFBUILD_BOOTFS_FLAT_DISK) $(@).temp $(TEEOUT) $(@D)/qemu-img.log
	$(ATSIGN)$(SUDO) $(CHMOD) 444 $(@).temp
	$(ATSIGN)$(SUDO) $(MV) $(@).temp $(@)

# Hyper-V installation ZIP
#
.PHONY: hyperv hypervmd5 hypervsha1 clean-hyperv

hyperv: hypervmd5 hypervsha1

hypervmd5: $(OVFBUILD_OVAS_HYPERV_FILES)
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(CHMOD) a+w $(OVFBUILD_OVAS_MD5SUMS)
	$(ATSIGN)cd $(OVFBUILD_OVAS_DIR) && $(CP) $(OVFBUILD_OVAS_MD5SUMS) $(OVFBUILD_OVAS_MD5SUMS).temp && \
		$(MD5SUM) $(notdir $(+)) >>$(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(CHMOD) 444 $(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(MV) $(OVFBUILD_OVAS_MD5SUMS).temp $(OVFBUILD_OVAS_MD5SUMS)

hypervsha1: $(OVFBUILD_OVAS_HYPERV_FILES)
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(CHMOD) a+w $(OVFBUILD_OVAS_SHA1SUMS)
	$(ATSIGN)cd $(OVFBUILD_OVAS_DIR) && $(CP) $(OVFBUILD_OVAS_SHA1SUMS) $(OVFBUILD_OVAS_SHA1SUMS).temp && \
		$(SHA1SUM) $(notdir $(+)) >>$(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(CHMOD) 444 $(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(MV) $(OVFBUILD_OVAS_SHA1SUMS).temp $(OVFBUILD_OVAS_SHA1SUMS)

clean-hyperv:
	$(ATSIGN)$(RM) -r $(OVFBUILD_HYPERV_DIR) $(OVFBUILD_HYPERV_ZIP)

.PHONY: gen-iso-header-trailer vsphere vspheremd5 vspheresha1 clean-vsphere

vsphere: gen-iso-header-trailer vspheremd5 vspheresha1 

gen-iso-header-trailer:
	$(ATSIGN)$(MKDIR) $(OVFBUILD_VSPHERE_DIR)
	$(ATSIGN)$(JAVA) -cp $(BUILD_BASE)/lib/storageos-syssvc.jar:$(BUILD_BASE)/lib/storageos-geniso.jar:$(BUILD_BASE)/lib/storageos-errorhandling.jar com.emc.storageos.geniso.Main --label "CDROM" --header $(ISO_HEADER).temp --trailer $(ISO_TRAILER).temp $(OVFBUILD_CONFIG_FILE_NAME) $(OVFBUILD_CONFIG_FILE_SIZE)
	#append some random data after trailer
	$(ATSING)$(SUDO) ./appendtrailer.sh $(ISO_HEADER).temp $(ISO_TRAILER).temp $(OVFBUILD_DISK4_SIZE)

	$(ATSIGN)$(BASE64) $(ISO_HEADER).temp > $(ISO_HEADER)
	$(ATSIGN)$(BASE64) $(ISO_TRAILER).temp > $(ISO_TRAILER)

vspheremd5: $(OVFBUILD_VSPHERE_ZIP)
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(CHMOD) a+w $(OVFBUILD_OVAS_MD5SUMS)
	$(ATSIGN)cd $(OVFBUILD_OVAS_DIR) && $(CP) $(OVFBUILD_OVAS_MD5SUMS) $(OVFBUILD_OVAS_MD5SUMS).temp && \
		$(MD5SUM) $(notdir $(+)) >>$(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(CHMOD) 444 $(OVFBUILD_OVAS_MD5SUMS).temp
	$(ATSIGN)$(MV) $(OVFBUILD_OVAS_MD5SUMS).temp $(OVFBUILD_OVAS_MD5SUMS)

vspheresha1: $(OVFBUILD_VSPHERE_ZIP)
	$(ATSIGN)$(RM) $(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(CHMOD) a+w $(OVFBUILD_OVAS_SHA1SUMS)
	$(ATSIGN)cd $(OVFBUILD_OVAS_DIR) && $(CP) $(OVFBUILD_OVAS_SHA1SUMS) $(OVFBUILD_OVAS_SHA1SUMS).temp && \
		$(SHA1SUM) $(notdir $(+)) >>$(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(CHMOD) 444 $(OVFBUILD_OVAS_SHA1SUMS).temp
	$(ATSIGN)$(MV) $(OVFBUILD_OVAS_SHA1SUMS).temp $(OVFBUILD_OVAS_SHA1SUMS)

clean-vsphere:
	$(ATSIGN)$(RM) -r $(OVFBUILD_VSPHERE_DIR) $(OVFBUILD_VSPHERE_ZIP)

# Flat bootfs VMDK
#
.PHONY: bootfs-flat clean-bootfs-flat

bootfs-flat: $(OVFBUILD_BOOTFS_FLAT_VMX)

clean-bootfs-flat:
	$(ATSIGN)$(SUDO) $(RM) -r $(OVFBUILD_BOOTFS_FLAT_DIR)

$(OVFBUILD_BOOTFS_FLAT_VMX): $(OVFBUILD_ROOTIMG_FILE)
	$(ATSIGN)$(RM) -r $(@D)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) bootfs-flat
	$(ATSIGN)$(SUDO) ./mkbootfs.sh $(@) $(<) $(TEEOUT) $(@D)/mkbootfs.log

# Rootimg
#
.PHONY: rootimg clean-rootimg

rootimg: $(OVFBUILD_ROOTIMG_FILE)

clean-rootimg:
	$(ATSIGN)$(SUDO) $(RM) -r $(OVFBUILD_ROOTIMG_DIR)
	$(ATSIGN)$(RM) $(OVFBUILD_UPGRADE_METADATA_FILE)

$(OVFBUILD_ROOTIMG_FILE): $(RPMBUILD_RPM_FILE) $(OVFBUILD_BASE_FLAT_DISK) $(OVFBUILD_UPGRADE_METADATA_FILE)
	$(ATSIGN)$(RM) $(@)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) rootimg
	$(ATSIGN)$(SUDO) ./mkrootimg.sh $(@) $(OVFBUILD_BASE_FLAT_DISK) $(OVFBUILD_UPGRADE_METADATA_FILE) $(RPMBUILD_RPM_FILE) $(TEEOUT) $(@D)/mkrootimg.log

$(OVFBUILD_UPGRADE_METADATA_FILE): vipr.md
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATSIGN)echo -e "$$(<$(<))\nversion: $(PRODUCT)" >>$(@).temp
	$(ATSIGN)$(CHMOD) 444 $(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)
	@echo "***"
	@echo "*** $(PRODUCT_BRAND) Upgrade dependency metadata"
	@echo "*** $(@)"
	@echo "***"
   
# Flat Bourne-Base VMDK
#
.PHONY: base-flat clean-base-flat

$(OVFBUILD_BASE_FLAT_DISK): base-flat

base-flat: $(OVFBUILD_BASE_FLAT_VMX)

clean-base-flat:
	$(ATSIGN)$(RM) -r $(OVFBUILD_BASE_FLAT_DIR)

$(OVFBUILD_BASE_FLAT_VMX): $(OVFBUILD_BASE_OVF) $(OVFBUILD_BASE_DISK) $(OBJDIR)/monolithicSparse/monolithicSparse.so
	$(ATSIGN)$(RM) -r $(@D)
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) base-flat
	$(ATSIGN)LD_PRELOAD=$(OBJDIR)/monolithicSparse/monolithicSparse.so $(OVFTOOL) --acceptAllEulas -dm=monolithicFlat $(<) $(@) $(TEEOUT) $(@D)/ovftool.log

$(OBJDIR)/monolithicSparse/monolithicSparse.so: all


# Bourne-Base download
#
$(OVFBUILD_BASE_DIR)/%:
	@$(RM) $(@) $(@).temp
	@$(MKDIR) $(@D)
	$(ATECHO) wget $(@F)
	$(ATSIGN)$(WGET) -q $(OVFBUILD_BASE_URL)/$(*) -O $(@).temp -o $(@).log
	$(ATSIGN)$(MV) $(@).temp $(@)

.PHONY: base clean-base

base:	$(OVFBUILD_BASE_OVF) $(OVFBUILD_BASE_DISK)

clean-base:
	$(ATSIGN)$(RM) -r $(OVFBUILD_BASE_DIR)


# Clean and clobber
#
.PHONY: clean-ovfs clean-ovas

clean-ovfs:
	$(ATSIGN)$(RM) -r $(OVFBUILD_OVFS_DIR)

clean-ovas:
	$(ATSIGN)$(RM) -r $(OVFBUILD_OVAS_DIR)

clean::   clean-ovfs clean-ovas clean-bootfs clean-bootfs-flat clean-rootimg clean-native

clobber:: clean-ovfs clean-ovas
	$(ATSIGN)$(RM) -r $(OVFBUILD_OVFS_DIR)

# Generate files derived from property definition files:
# - $(OVF_PROPERTY_METADATA_SRC_BEAN) is checked in into SVN; It is needed for running services from ant only.
# - $(CONFIG_DEFAULTS) and $(OVF_PROPERTY_METADATA_BEANS) are generated here and included into the RPM.
# - $(OVFBUILD_OVF_PROPERTY_INCLUDES) are used an include files during the OVF envelope generation.

.PHONY: properties clean-properties clobber-properties

# Rule for $(OVF_PROPERTY_METADATA_BEANS) in the source tree
$(SOURCE_BASE)/syssvc/src/conf/sys-metadata-var.xml: ./genprops.py $(CONTROLLER_PROPERTY_DEFINITIONS) FORCE
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATECHO) genprops.py syssvc/src/conf/$(@F)
	$(ATSIGN)./genprops.py --metadata-var $(CONTROLLER_PROPERTY_DEFINITIONS) node_count='1' >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

$(SOURCE_BASE)/syssvc/src/conf/sys-metadata-var-template.xml: ./genprops.py $(CONTROLLER_PROPERTY_DEFINITIONS) FORCE
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATECHO) genprops.py syssvc/src/conf/$(@F)
	$(ATSIGN)./genprops.py --metadata-var-template $(CONTROLLER_PROPERTY_DEFINITIONS) >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

# Rule for $(CONFIG_DEFAULTS) in build/etc
$(CONFIG_DEFAULTS): ./genprops.py $(CONTROLLER_PROPERTY_DEFINITIONS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D) 
	$(ATECHO) genprops.py $(@F)
	$(ATSIGN)./genprops.py --config-defaults $(CONTROLLER_PROPERTY_DEFINITIONS) $(OVFBUILD_GENTMPL_FLAGS) >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@) 

$(OVF_CONTROLLER_DEFAULTS): ./genprops.py $(CONTROLLER_PROPERTY_DEFINITIONS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) genprops.py $(@F)
	$(ATSIGN)./genprops.py --ovf-controller-defaults - $(CONTROLLER_OVF_DEFINITIONS) - node_count='1' >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

$(ITER_DEFAULTS): ./genprops.py $(CONTROLLER_PROPERTY_DEFINITIONS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) genprops.py $(@F)
	$(ATSIGN)./genprops.py --iter-defaults $(CONTROLLER_PROPERTY_DEFINITIONS) node_count='1' >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

# Rule for $(OVFBUILD_OVF_PROPERTY_INCLUDES)
$(OBJDIR)/ovfbuild/storageos-%-properties.xml: ./genprops.py $(CONTROLLER_PROPERTY_DEFINITIONS)
	$(ATSIGN)$(RM) $(@) $(@).temp
	$(ATSIGN)$(MKDIR) $(@D)
	$(ATECHO) genprops.py $(@F)
	$(ATSIGN)./genprops.py --ovf-$(notdir $(*))-properties $(CONTROLLER_PROPERTY_DEFINITIONS) >$(@).temp
	$(ATSIGN)$(MV) $(@).temp $(@)

all:: properties

properties: $(OVF_PROPERTY_METADATA_BEANS) $(CONFIG_DEFAULTS) $(OVF_CONTROLLER_DEFAULTS) $(ITER_DEFAULTS) $(OVFBUILD_OVF_PROPERTY_INCLUDES)

clean-properties:
	$(ATSIGN)$(RM) $(OVFBUILD_OVF_PROPERTY_INCLUDES) $(CONFIG_DEFAULTS) $(OVF_CONTROLLER_DEFAULTS) $(ITER_DEFAULTS)

clobber-properties:
	$(ATSIGN)$(RM) $(OVF_PROPERTY_METADATA_BEANS)

clean:: clean-properties


# RPM section
#
.PHONY: rpm clean-rpm 

rpm: $(RPMBUILD_RPM_FILE)

clean-rpm:
	$(ATSIGN)$(RM) -r $(RPMBUILD_RPM_FILE) $(RPMBUILD_BUILDDIR) $(RPMBUILD_CONTENT_MD5_FILE)

$(RPMBUILD_RPM_FILE): $(RPMBUILD_SPEC_FILE) $(RPMBUILD_CONTENT_MD5_FILE)
	@$(ECHO) "Building storageos RPM package (release $(PRODUCT_VERSION).$(PRODUCT_RELEASE))"
	$(ATSIGN)$(RM)    $(@)
	$(ATSIGN)$(RM) -r       $(RPMBUILD_BUILDDIR)
	$(ATSIGN)$(MKDIR) $(@D) $(RPMBUILD_BUILDDIR)
	$(ATSIGN)$(MAKE) _install INSTALL_PREFIX="$(RPMBUILD_BUILDROOT)"
	$(ATSIGN)$(RPMBUILD) $(RPMBUILD_FLAGS) $(<) $(TEEOUT) $(RPMBUILD_LOG)
	@[ -f "$(@)" ] || { echo "rpmbuild failed!"; exit 1 ; }
	@echo "***"
	@echo "*** $(RPMBUILD_NAME) RPM:"
	@echo "***    $(@)"
	@echo "***"

$(RPMBUILD_CONTENT_MD5_FILE): FORCE
	@old=`$(CAT) $(@) 2>/dev/null`; new=`$(FIND) $(BUILD_BASE)/{etc,connectemc,bin,lib,conf,cli,pylib,platform,play,portal} -exec stat -c "%n %y" {} \; 2>/dev/null| sort | md5sum | cut -d " " -f 1`; [ "$${new}" != "$${old}" ] && echo "$${new}" >$(@) || echo "rpm is up-to-date"

.PHONY: _install _uninstall

_install::
	@echo "Installing into $(INSTALL_PREFIX)$(PRODUCT_PREFIX)"
	$(ATECHO) cp -a etc
	$(ATSIGN)$(MKDIR) $(INSTALL_PREFIX)/etc
	$(ATSIGN)$(CP) -a $(BUILD_BASE)/etc                    $(INSTALL_PREFIX)/
ifeq ($(BUILD_TYPE),emc)
	$(ATECHO) cp -a connectemc
	$(ATSIGN)$(MKDIR) $(INSTALL_PREFIX)/opt/connectemc
	$(ATSIGN)$(CP) -a $(BUILD_BASE)/connectemc             $(INSTALL_PREFIX)/opt/
endif
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)$(PRODUCT_PREFIX)
	$(ATSIGN)$(MKDIR) $(INSTALL_PREFIX)$(PRODUCT_PREFIX)
	$(ATECHO) cp -a bin lib conf cli pylib
	$(ATSIGN)$(CP) -a $(BUILD_BASE)/{bin,lib,conf,cli,pylib} $(INSTALL_PREFIX)$(PRODUCT_PREFIX)
	$(ATSIGN)if [ -e ${BUILD_BASE}/platform ]; then \
				$(ECHO) cp -a platform; \
				$(CP) -a ${BUILD_BASE}/platform $(INSTALL_PREFIX)$(PRODUCT_PREFIX); \
			 fi
	$(ATSIGN)if [ -e ${BUILD_BASE}/play ]; then \
				$(ECHO) cp -a play; \
				$(CP) -a ${BUILD_BASE}/play $(INSTALL_PREFIX)$(PRODUCT_PREFIX); \
			 fi
	$(ATSIGN)if [ -e ${BUILD_BASE}/portal ]; then \
				$(ECHO) cp -a portal; \
				$(CP) -a ${BUILD_BASE}/portal $(INSTALL_PREFIX)$(PRODUCT_PREFIX); \
			 fi				
	$(ATECHO) mkdir logs 
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)$(PRODUCT_PREFIX)/logs
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/data/db
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/data/geodb
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/data/zk
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/object/rmt
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/usr/lib64/apache2
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/var/run/storageos
	$(ATSIGN)$(MKDIR)                                      $(INSTALL_PREFIX)/opt/storageos/etc/
	$(ATSIGN)$(ECHO) "$(GIT_REVISION)"                                       >$(INSTALL_PREFIX)/opt/storageos/etc/gitrevision
	$(ATSIGN)$(ECHO) "$(GIT_REPO)"                                           >$(INSTALL_PREFIX)/opt/storageos/etc/gitrepo
	$(ATSIGN)$(ECHO) "$(GIT_BRANCH)"                                         >$(INSTALL_PREFIX)/opt/storageos/etc/gitbranch
	$(ATSIGN)$(ECHO) "$(PRODUCT_NAME)-$(PRODUCT_VERSION).$(PRODUCT_RELEASE)" >$(INSTALL_PREFIX)/opt/storageos/etc/product
	$(ATSIGN)$(ECHO) "$(OVFBUILD_BASE_NAME)"                                 >$(INSTALL_PREFIX)/opt/storageos/etc/productbase
	$(ATSIGN)$(CHMOD) 444 $(INSTALL_PREFIX)/opt/storageos/etc/{gitrevision,gitrepo,gitbranch,product,productbase}
	$(ATSIGN)$(CHMOD) 770 $(INSTALL_PREFIX)/opt/storageos/play/play
	$(ATSIGN)$(CHMOD) 750 $(INSTALL_PREFIX)/data/db
	$(ATSIGN)$(CHMOD) 750 $(INSTALL_PREFIX)/data/geodb
	$(ATSIGN)$(CHMOD) o-r -R $(INSTALL_PREFIX)/opt/storageos/conf
	$(ATSIGN)$(SETFACL) -m u:1001:r -R $(INSTALL_PREFIX)/opt/storageos/conf   # svcuser(1001) is created during rpm installation in coprhd 

_uninstall:
	@echo "Uninstalling $(INSTALL_PREFIX)$(PRODUCT_PREFIX)"
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)$(PRODUCT_PREFIX)
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)/data
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)/object
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)/opt/connectemc
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)/etc/nginx
	$(ATSIGN)$(RM) -r $(INSTALL_PREFIX)/etc/keepalived
	$(ATSIGN)$(RM)    $(INSTALL_PREFIX)/etc/storageos
	$(ATSIGN)$(RM)    $(INSTALL_PREFIX)/etc/sysconfig/storageos
	$(ATSIGN)$(RM)    $(INSTALL_PREFIX)/{.gitrevision,.gitrepo,.gitbranch,.product,.productbase}

clean:: clean-rpm

clobber::
	$(ATSIGN)$(RM) -r $(RPMBUILD_RPMS_DIR)


# Handy hack
#
.PHONY: clean-most

clean-most: clean-ovfs clean-bootfs clean-bootfs-flat clean-rootimg clean-rpm clean-native clean-docker


# On clober remove Makefile.svnversion
#
clobber::
	$(ATSIGN)$(RM) $(MAKEFILE_VERSION)

# END $Rev: XXX $

