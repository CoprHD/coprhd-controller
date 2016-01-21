
# Copyright (c) 2012-2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# etc/Makefile
#

include ../Makefile.common

CONNECTEMC_DIR = $(BUILD_BASE)/connectemc

all:: install_etc

ETCDIR   = $(BUILD_BASE)/etc
# The files that should be added into the rpm for both emc and oss builds
ETCFILES_COMMON = \
    $(ETCDIR)/getovfproperties \
    $(ETCDIR)/getplatform \
    $(ETCDIR)/genconfig \
    $(ETCDIR)/genconfig.d/genconfig-core \
    $(ETCDIR)/genconfig.d/api \
    $(ETCDIR)/genconfig.d/auth \
    $(ETCDIR)/genconfig.d/controller \
    $(ETCDIR)/genconfig.d/coordinator \
    $(ETCDIR)/genconfig.d/db \
    $(ETCDIR)/genconfig.d/dbclient \
    $(ETCDIR)/genconfig.d/geo \
    $(ETCDIR)/genconfig.d/geodb \
    $(ETCDIR)/genconfig.d/nginx \
    $(ETCDIR)/genconfig.d/password \
    $(ETCDIR)/genconfig.d/portal \
    $(ETCDIR)/genconfig.d/security \
    $(ETCDIR)/genconfig.d/ssh \
    $(ETCDIR)/genconfig.d/ssh_auth_key \
    $(ETCDIR)/genconfig.d/ssl \
    $(ETCDIR)/genconfig.d/syssvc \
    $(ETCDIR)/genconfig.d/ipsec \
    $(ETCDIR)/genconfig.d/test \
    $(ETCDIR)/gentmpl \
    $(ETCDIR)/diagtool \
    $(ETCDIR)/powerofftool \
    $(ETCDIR)/ipsectool \
    $(ETCDIR)/gatherheapdumps \
    $(ETCDIR)/patch-props.defaults \
    $(ETCDIR)/ovf-env.README \
    ${ETCDIR}/getnic \
    $(ETCDIR)/cron.hourly/cleanup_logs \
    $(ETCDIR)/storageos/storageos \
    $(ETCDIR)/storageos/syncntp \
    $(ETCDIR)/storageos/installer \
    $(ETCDIR)/storageos/ipchecktool \
    $(ETCDIR)/logrotate.d/storageserver \
    $(ETCDIR)/sysconfig/storageos \
    $(ETCDIR)/sysconfig/connectemc \
    $(ETCDIR)/sysconfig/ntp \
    $(ETCDIR)/sysconfig/SuSEfirewall2-template \
    $(ETCDIR)/sysconfig/scripts/FWiptables-template \
    $(ETCDIR)/sysconfig/SuSEfirewall2.d/services/storageos \
    $(ETCDIR)/keepalived/keepalived-IPv4-template.conf \
    $(ETCDIR)/keepalived/keepalived-IPv6-template.conf \
    $(ETCDIR)/keepalived/keepalived-dual-template.conf \
    $(ETCDIR)/nginx/nginx-dual-template.conf \
    $(ETCDIR)/nginx/nginx-IPv4-template.conf \
    $(ETCDIR)/nginx/nginx-IPv6-template.conf \
    $(ETCDIR)/nginx/nginx.wrapper \
    $(ETCDIR)/nginx/locations.conf \
    $(ETCDIR)/nginx/upstream-template.conf \
    $(ETCDIR)/nginx/upstream.conf \
    $(ETCDIR)/nginx/portal-error.conf \
    $(ETCDIR)/nginx/api-error.conf \
    $(ETCDIR)/nginx/error/html/400.html \
    $(ETCDIR)/nginx/error/html/401.html \
    $(ETCDIR)/nginx/error/html/402.html \
    $(ETCDIR)/nginx/error/html/404.html \
    $(ETCDIR)/nginx/error/html/405.html \
    $(ETCDIR)/nginx/error/html/406.html \
    $(ETCDIR)/nginx/error/html/409.html \
    $(ETCDIR)/nginx/error/html/410.html \
    $(ETCDIR)/nginx/error/html/411.html \
    $(ETCDIR)/nginx/error/html/412.html \
    $(ETCDIR)/nginx/error/html/413.html \
    $(ETCDIR)/nginx/error/html/414.html \
    $(ETCDIR)/nginx/error/html/415.html \
    $(ETCDIR)/nginx/error/html/416.html \
    $(ETCDIR)/nginx/error/html/494.html \
    $(ETCDIR)/nginx/error/html/495.html \
    $(ETCDIR)/nginx/error/html/496.html \
    $(ETCDIR)/nginx/error/html/497.html \
    $(ETCDIR)/nginx/error/html/500.html \
    $(ETCDIR)/nginx/error/html/501.html \
    $(ETCDIR)/nginx/error/html/503.html \
    $(ETCDIR)/nginx/error/html/507.html \
    $(ETCDIR)/nginx/error/json/400.json \
    $(ETCDIR)/nginx/error/json/401.json \
    $(ETCDIR)/nginx/error/json/402.json \
    $(ETCDIR)/nginx/error/json/404.json \
    $(ETCDIR)/nginx/error/json/405.json \
    $(ETCDIR)/nginx/error/json/406.json \
    $(ETCDIR)/nginx/error/json/409.json \
    $(ETCDIR)/nginx/error/json/410.json \
    $(ETCDIR)/nginx/error/json/411.json \
    $(ETCDIR)/nginx/error/json/412.json \
    $(ETCDIR)/nginx/error/json/413.json \
    $(ETCDIR)/nginx/error/json/414.json \
    $(ETCDIR)/nginx/error/json/415.json \
    $(ETCDIR)/nginx/error/json/416.json \
    $(ETCDIR)/nginx/error/json/494.json \
    $(ETCDIR)/nginx/error/json/495.json \
    $(ETCDIR)/nginx/error/json/496.json \
    $(ETCDIR)/nginx/error/json/497.json \
    $(ETCDIR)/nginx/error/json/500.json \
    $(ETCDIR)/nginx/error/json/501.json \
    $(ETCDIR)/nginx/error/json/503.json \
    $(ETCDIR)/nginx/error/json/507.json \
    $(ETCDIR)/nginx/error/xml/400.xml \
    $(ETCDIR)/nginx/error/xml/401.xml \
    $(ETCDIR)/nginx/error/xml/402.xml \
    $(ETCDIR)/nginx/error/xml/404.xml \
    $(ETCDIR)/nginx/error/xml/405.xml \
    $(ETCDIR)/nginx/error/xml/406.xml \
    $(ETCDIR)/nginx/error/xml/409.xml \
    $(ETCDIR)/nginx/error/xml/410.xml \
    $(ETCDIR)/nginx/error/xml/411.xml \
    $(ETCDIR)/nginx/error/xml/412.xml \
    $(ETCDIR)/nginx/error/xml/413.xml \
    $(ETCDIR)/nginx/error/xml/414.xml \
    $(ETCDIR)/nginx/error/xml/415.xml \
    $(ETCDIR)/nginx/error/xml/416.xml \
    $(ETCDIR)/nginx/error/xml/494.xml \
    $(ETCDIR)/nginx/error/xml/495.xml \
    $(ETCDIR)/nginx/error/xml/496.xml \
    $(ETCDIR)/nginx/error/xml/497.xml \
    $(ETCDIR)/nginx/error/xml/500.xml \
    $(ETCDIR)/nginx/error/xml/501.xml \
    $(ETCDIR)/nginx/error/xml/503.xml \
    $(ETCDIR)/nginx/error/xml/507.xml \
    $(ETCDIR)/sudoers.d/storageos \
    $(ETCDIR)/systemd/system/boot-ovfenv.service \
    $(ETCDIR)/systemd/system/nginx.service \
    $(ETCDIR)/systemd/system/keepalived.service \
    $(ETCDIR)/systemd/system/connectemc.service \
    $(ETCDIR)/systemd/system/syncntp.service \
    $(ETCDIR)/systemd/system/ipchecktool.service \
    $(ETCDIR)/systemd/system/storageos-installer.service \
    $(ETCDIR)/systemd/system/ipsec.service


SYSTEMD_DIR = ${BUILD_BASE}/etc/systemd/system
SYSTEMD_TMPL = $(SYSTEMD_DIR)/storageos-template.service
SYSTEMD_STORAGEOS = coordinator db geodb geo auth api controller portal sa sys

# The files that should be added into the rpm for emc builds only
ETCFILES_APPLIANCE = diskresizetool \
    mkdisk.sh \
    genconfig.d/connectemc \
    genconfig.d/dns \
    genconfig.d/firewall \
    genconfig.d/network \
    genconfig.d/ntp \
    genconfig.d/system \
    genconfig.d/vasa \
    genconfig.d/boot.manifest \
    nginx/vasasvc-server-common.conf \
    nginx/vasasvc-server-dual-template.conf \
    nginx/vasasvc-server-IPv4-template.conf \
    nginx/vasasvc-server-IPv6-template.conf \
    nginx/storageos-vasasvc.conf \
    storageos/boot-ovfenv \
    systemd/system/auditd.service \
    systool

# The files that should be added into the rpm for oss builds only
ETCFILES_LINUX = \
    storageos/boot-ovfenv \
    genconfig.d/boot.manifest \
    systool

ifeq ($(BUILD_TYPE),emc)
    SYSTEMD_STORAGEOS += vasa
    ETCFILES_ADDITIONAL = $(ETCFILES_APPLIANCE)
    CONNECTEMC_FILES = \
        $(CONNECTEMC_DIR)/ConnectEMC_storageos.ini \
        $(CONNECTEMC_DIR)/ConnectEMC_config-noauth-template.xml \
        $(CONNECTEMC_DIR)/ConnectEMC_config-auth-template.xml \
        $(CONNECTEMC_DIR)/ConnectEMC_config-ftps-template.xml
    NGINX_VASASVC_INCLUDE = include storageos-vasasvc.conf;
    NGINX_VASASVC_INCLUDE2 = include vasasvc-server.conf;
else
    ETCFILES_ADDITIONAL = $(ETCFILES_LINUX)
endif

SED_SCRIPT = $(SED) \
    -e 's,@product_brand@,$(PRODUCT_BRAND),g' \
    -e 's,@product_name@,$(PRODUCT_NAME),g' \
    -e 's,@product_caps@,$(PRODUCT_CAPS),g' \
    -e 's,@product_prefix@,$(PRODUCT_PREFIX),g' \
    -e 's,@product_version@,$(PRODUCT_VERSION),g' \
    -e 's,@systemd_storageos@,$(SYSTEMD_STORAGEOS),g' \
    -e 's,@nignx_vasasvc@,$(NGINX_VASASVC_INCLUDE),g' \
    -e 's,@nignx_vasasvc2@,$(NGINX_VASASVC_INCLUDE2),g' \
    -e 's,@build_type@,$(BUILD_TYPE),g' \

install_etc: $(ETCFILES_COMMON) $(CONNECTEMC_FILES) $(SYSTEMD_STORAGEOS) $(ETCFILES_ADDITIONAL)

$(CONNECTEMC_DIR)/%: connectemc/%
	@echo "cp connectemc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(CP) $(<) $(@)
	$(ATSIGN)$(CHMOD) 444 $(@)

$(ETCDIR)/sysconfig/%: sysconfig/%
	@echo "sed etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(<) >$(@)
	$(ATSIGN)$(CHMOD) 644 $(@)

$(ETCDIR)/nginx/%: nginx/%
	@echo "sed etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(<) >$(@)
	$(ATSIGN)$(CHMOD) 444 $(@)

$(ETCDIR)/keepalived/%: keepalived/%
	@echo "sed etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(<) >$(@)
	$(ATSIGN)$(CHMOD) 644 $(@)

$(ETCDIR)/init.d/%: init.d/%
	@echo "sed etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(<) >$(@)
	$(ATSIGN)$(CHMOD) 755 $(@)

$(ETCDIR)/logrotate.d/%: logrotate.d/%
	@echo "sed etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(<) >$(@)
	$(ATSIGN)$(CHMOD) 644 $(@)

$(SYSTEMD_STORAGEOS): $(SYSTEMD_TMPL)
	@echo  "sed systemd service storageos-$(@)"
	$(ATSIGN)$(SED_SCRIPT) -e 's,@svcname@,$(@),g' \
             <$(SYSTEMD_TMPL) >$(SYSTEMD_DIR)/storageos-$(@).service
	$(ATSIGN)$(CHMOD) 644 $(SYSTEMD_DIR)/storageos-$(@).service

$(ETCFILES_ADDITIONAL): 
	@echo "cp etc/$(BUILD_TYPE)/$(@)"
	@$(RM) $(ETCDIR)/$(@)
	@$(MKDIR) $(ETCDIR)/$(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(BUILD_TYPE)/$(@) >$(ETCDIR)/$(@)
	$(ATSIGN)$(CHMOD) 755 $(ETCDIR)/$(@)

$(ETCDIR)/genconfig.d/%: genconfig.d/%
	@echo "cp etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(CP) -d $(<) $(@)
	$(ATSIGN)$(CHMOD) 755 $(@)

$(ETCDIR)/%: %
	@echo "sed etc/$(<)"
	@$(RM) $(@)
	@$(MKDIR) $(@D)
	$(ATSIGN)$(SED_SCRIPT) <$(<) >$(@)
	$(ATSIGN)$(CHMOD) 755 $(@)

clean clobber::
	$(ATSIGN)$(RM) -r $(ETCDIR) $(CONNECTEMC_DIR)

# END $Rev: XXX $

