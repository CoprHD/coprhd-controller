#
# Copyright 2015 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
export DO_NOT_START := yes
WORKSPACE := $(shell pwd)
NAME := CoprHD
VERSION := 2.5.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := openSUSE-13.2-DVD-x86_64.iso
ISO_URL := http://download.opensuse.org/distribution/13.2/iso/$(ISO_FILE)
SOURCE_RPM := jenkins

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
OVF := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
STORAGEOS_VERSION := 2.5.0.0
STORAGEOS_STABLE := $(shell wget --no-check-certificate --quiet -O - https://build.coprhd.org/jenkins/job/CH-coprhd-controller-master/lastStableBuild/buildNumber)
ZYPPER_PARAMS := --reposd-dir $(WORKSPACE)/zypper/reposd --cache-dir $(WORKSPACE)/zypper/cache --solv-cache-dir $(WORKSPACE)/zypper/solv --pkg-cache-dir $(WORKSPACE)/zypper/pkg

.DEFAULT: all
.PHONY: downdloadISO downdloadRPM downdloadTGZ createREPO appliance clean destroy
all: appliance

##################################################
#  Functions
##################################################
define targetLog
	@echo "###############################################";
	@echo "$(1)[$@] on $$( date )";
	@echo "###############################################";
endef

##################################################
#  Targets
##################################################
downdloadISO:
	$(call targetLog,+)
	mkdir -p $(WORKSPACE)/iso/mount
	[ ! -f /disks/adgbuild/OPENSUSE13.2/$(ISO_FILE) ] || ln -fs /disks/adgbuild/OPENSUSE13.2/$(ISO_FILE) $(WORKSPACE)/iso/
	[ -s $(WORKSPACE)/iso/$(ISO_FILE) ] || wget $(ISO_URL) --continue --progress=bar --output-document=$(WORKSPACE)/iso/$(ISO_FILE)
	$(call targetLog,-)

downdloadRPM:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/zypper/reposd
	mkdir -p $(WORKSPACE)/zypper/{reposd,cache,solv,pkg}
	@# openSUSE's OSS packages
	zypper $(ZYPPER_PARAMS) addrepo --name repo-oss --no-gpgcheck --no-check http://download.opensuse.org/distribution/13.2/repo/oss repo-oss.repo
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download python-py python-requests python-setools python-iniparse python-gpgme
	zypper $(ZYPPER_PARAMS) --non-interactive download libserf-devel fribidi fbiterm openssh-fips perl-Tk setools-libs sshpass sysstat ca-certificates-cacert yum keepalived strongswan strongswan-ipsec strongswan-libs0
	@# openSUSE's non-OSS packages
	zypper $(ZYPPER_PARAMS) addrepo --name devel-languages-python --no-gpgcheck --no-check http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_13.2 devel-languages-python.repo
	zypper $(ZYPPER_PARAMS) addrepo --name server-monitoring --no-gpgcheck --no-check http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 server-monitoring.repo
	zypper $(ZYPPER_PARAMS) addrepo --name home-seife-testing --no-gpgcheck --no-check http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 home-seife-testing.repo
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download atop GeoIP-data libGeoIP1 GeoIP sipcalc python-cjson
	$(call targetLog,-)

downdloadTGZ:
	$(call targetLog,+)
	wget http://nginx.org/download/nginx-1.6.2.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/nginx-1.6.2.tar.gz
	wget --no-check-certificate https://github.com/yaoweibin/nginx_upstream_check_module/archive/v0.3.0.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/v0.3.0.tar.gz
	wget --no-check-certificate https://github.com/openresty/headers-more-nginx-module/archive/v0.25.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/v0.25.tar.gz
	$(call targetLog,-)

createREPO: | downdloadRPM downdloadTGZ
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/zypper/repo
	mkdir -p $(WORKSPACE)/zypper/repo
	if [ $(SOURCE_RPM) == "jenkins" ]; then \
		wget --no-check-certificate https://build.coprhd.org/jenkins/job/CH-coprhd-controller-master/lastSuccessfulBuild/artifact/CH-coprhd-controller-master/build/RPMS/x86_64/storageos-$(STORAGEOS_VERSION).$(STORAGEOS_STABLE)-1.x86_64.rpm --continue --progress=bar --output-document=$(WORKSPACE)/zypper/repo/storageos-$(STORAGEOS_VERSION).$(STORAGEOS_STABLE)-1.x86_64.rpm; \
	fi
	if [ $(SOURCE_RPM) == "git" ]; then \
		cd $(WORKSPACE)/../../../../../; \
		make clobber BUILD_TYPE=oss rpm; \
		cp -fr build/RPMS/x86_64/* $(WORKSPACE)/zypper/repo/; \
	fi
	if [ -f $(SOURCE_RPM) ]; then \
		cp -fr $(SOURCE_RPM) $(WORKSPACE)/zypper/repo/; \
	fi
	make -f $(WORKSPACE)/../configure-network/Makefile
	cp -f $(WORKSPACE)/../configure-network/RPMS/noarch/* $(WORKSPACE)/zypper/repo/
	find $(WORKSPACE)/zypper/pkg -iname \*.rpm -exec cp -f {} $(WORKSPACE)/zypper/repo/ \;
	createrepo $(WORKSPACE)/zypper/repo
	$(call targetLog,-)

clean:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/{build/$(NAME)-$(VERSION).$(JOB),image/$(JOB),output,zypper/repo,zypper/reposd}
	$(call targetLog,-)

destroy:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/{iso,build,image,output,zypper}
	$(call targetLog,-)

appliance: | $(OVF)

$(VMDK): | downdloadISO createREPO
	$(call targetLog,+)
	bash $(WORKSPACE)/patch.sh
	cp -f $(WORKSPACE)/templates/config.orig.xml $(WORKSPACE)/templates/config.xml
	sed -i "s|__NAME__|$(NAME)|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__ISO__|$(WORKSPACE)/iso/mount|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__REPO__|$(WORKSPACE)/zypper/repo|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(WORKSPACE)/templates/config.xml
	-! mountpoint -q $(IMAGE_DIR)/var/cache/kiwi || umount $(IMAGE_DIR)/var/cache/kiwi
	-mountpoint -q $(WORKSPACE)/iso/mount || mount $(WORKSPACE)/iso/$(ISO_FILE) $(WORKSPACE)/iso/mount
	rm -fr $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	mkdir -p $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	mkdir -p $(WORKSPACE)/image
	mkdir -p $(WORKSPACE)/build
	mkdir -p $(WORKSPACE)/output
	kiwi --verbose 3 --yes --prepare $(WORKSPACE)/templates --add-profile appliance --force-new-root --root $(IMAGE_DIR)
	kiwi --verbose 3 --yes --create $(IMAGE_DIR) --add-profile appliance --type vmx --destdir $(OUTPUT_DIR)
	-umount $(WORKSPACE)/iso/mount
	$(call targetLog,-)

$(OVF): | $(VMDK)
	$(call targetLog,+)
	cp -f $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk.bak
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory 8192 --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)" --delete
	mv -f $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk.bak $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	bash $(WORKSPACE)/configure.sh $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	$(call targetLog,-)
