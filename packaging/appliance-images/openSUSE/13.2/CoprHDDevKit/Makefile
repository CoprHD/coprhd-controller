#
# Copyright 2015-2016 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
export DO_NOT_START := yes
WORKSPACE := $(shell dirname $(MAKEFILE_LIST))
NAME := CoprHDDevKit
VERSION := 2.5.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := openSUSE-13.2-DVD-x86_64.iso
ISO_URL := http://download.opensuse.org/distribution/13.2/iso/$(ISO_FILE)
RAM := 8192

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
OVF := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
VMX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx
ZYPPER_PARAMS := --reposd-dir $(WORKSPACE)/zypper/reposd --cache-dir $(WORKSPACE)/zypper/cache --solv-cache-dir $(WORKSPACE)/zypper/solv --pkg-cache-dir $(WORKSPACE)/zypper/pkg

.DEFAULT: all
.PHONY: downloadISO downloadRPM createREPO ovf vmx appliance devkit clean destroy
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
downloadISO:
	$(call targetLog,+)
	mkdir -p $(WORKSPACE)/iso/mount
	[ ! -f /disks/adgbuild/OPENSUSE13.2/$(ISO_FILE) ] || ln -fs /disks/adgbuild/OPENSUSE13.2/$(ISO_FILE) $(WORKSPACE)/iso/
	[ -s $(WORKSPACE)/iso/$(ISO_FILE) ] || wget $(ISO_URL) --continue --progress=bar --output-document=$(WORKSPACE)/iso/$(ISO_FILE)
	$(call targetLog,-)

downloadRPM:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/zypper/reposd
	mkdir -p $(WORKSPACE)/zypper/{reposd,cache,solv,pkg}
	zypper $(ZYPPER_PARAMS) addrepo --name repo-oss --no-gpgcheck --no-check http://download.opensuse.org/distribution/13.2/repo/oss repo-oss
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download fribidi fbiterm
	$(call targetLog,-)

createREPO: | downloadRPM
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/zypper/repo
	mkdir -p $(WORKSPACE)/zypper/repo
	make --directory=$(WORKSPACE)/../configure-network
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

appliance: | ovf vmx

ovf: | $(OVF)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vmx: | $(VMX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

devkit:
	$(call targetLog,+)
	bash $(WORKSPACE)/configure.sh installRepositories
	bash $(WORKSPACE)/configure.sh installPackages
	bash $(WORKSPACE)/configure.sh installJava
	bash $(WORKSPACE)/configure.sh installNginx
	bash $(WORKSPACE)/configure.sh installStorageOS
	bash $(WORKSPACE)/configure.sh installNetworkConfigurationFile
	bash $(WORKSPACE)/configure.sh installXorg
	$(call targetLog,-)

$(VMDK): | downloadISO createREPO
	$(call targetLog,+)
	bash $(WORKSPACE)/patch.sh
	mkdir -p $(WORKSPACE)/templates/root/tmp
	cp -f $(WORKSPACE)/templates/config.orig.xml $(WORKSPACE)/templates/config.xml
	cp -f $(WORKSPACE)/configure.sh $(WORKSPACE)/templates/root/tmp/
	sed -i "s|__NAME__|$(NAME)|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__ISO__|$(WORKSPACE)/iso/mount|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__REPO__|$(WORKSPACE)/zypper/repo|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__UUID__|$$RANDOM|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__RAM__|$(RAM)|g" $(WORKSPACE)/templates/config.xml
	-! mountpoint -q $(IMAGE_DIR)/var/cache/kiwi || umount $(IMAGE_DIR)/var/cache/kiwi
	-mountpoint -q $(WORKSPACE)/iso/mount || mount $(WORKSPACE)/iso/$(ISO_FILE) $(WORKSPACE)/iso/mount
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
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(OUTPUT_DIR) --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(VMDK)
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(OVF)
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)"
	bash $(WORKSPACE)/configure.sh updateOVF $(OVF)
	$(call targetLog,-)

$(VMX): | $(VMDK)
	$(call targetLog,+)
	[ -f $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk ] || cp $(VMDK) $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	cp $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx $(VMX)
	$(call targetLog,-)
