#
# Copyright 2015 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
export KERNEL := 4.2.0-16-generic
WORKSPACE := $(shell pwd)
SHELL := /bin/bash
NAME := CoprHDDevKit-Ubuntu
VERSION := 2.5.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := ubuntu-15.10-server-amd64.iso
ISO_URL := http://releases.ubuntu.com/15.10/$(ISO_FILE)
RAM := 8192
MAC := $(shell printf "00%02X%02X%02X%02X%02X" $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256])

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
VBOX := $(BUILD_DIR)/$(NAME)-$(VERSION).$(JOB).vbox
OVF := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
VHD := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vhd
VMX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx
BOX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).box
TBZ := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).tbz
QCOW2 := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).qcow2

.DEFAULT: all
.PHONY: downloadISO downloadDEB downloadTGZ createREPO vbox ovf vhd vmx box tbz qcow2 appliance clean destroy
all: appliance

##################################################
#  Custom properties
##################################################
PACKAGES_BOOT := linux-image-$(KERNEL) linux-headers-$(KERNEL)
PACKAGES_LIST := build-essential dkms keyutils kpartx memtest86+ nfs-common openssh-client openssh-server parted psmisc qemu-system-common qemu-utils vim squashfs-tools
PACKAGES_LIST := $(PACKAGES_LIST) alien ant apparmor arping atop auditd ca-certificates ca-certificates-java createrepo expect gcc-4.8 geoip-bin geoip-database git git-core gradle keepalived libpcre3 libpcre3-dev libserf-dev libtool libssl-dev ndisc6 ntp openjdk-7-jdk openjdk-7-jre openjdk-7-jre-headless p7zip perl perl-base perl-tk python-cjson python-gpgme python-iniparse python-requests python-setools readline-common rpm sipcalc subversion sysstat virtualbox-5.0 xfsprogs xmlstarlet yum strongswan libstrongswan

ISO_MOUNT := $(WORKSPACE)/iso/mount
DIR_MOUNT := $(WORKSPACE)/dir/mount
APT_MOUNT := $(WORKSPACE)/apt

DISK_QEMU := $(shell mkdir -p $(IMAGE_DIR) && echo -n $(IMAGE_DIR)/image.$(JOB))
DISK_SIZE := 60
SWAP_SIZE := 4
SECTOR_SIZE := 512
SECTOR_INIT := 2048

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
	mkdir -p $(ISO_MOUNT)
	[ ! -f /disks/adgbuild/UBUNTU15.10/$(ISO_FILE) ] || ln -fs /disks/adgbuild/UBUNTU15.10/$(ISO_FILE) $(WORKSPACE)/iso/
	[ -s $(WORKSPACE)/iso/$(ISO_FILE) ] || wget $(ISO_URL) --continue --progress=bar --output-document=$(WORKSPACE)/iso/$(ISO_FILE)
	$(call targetLog,-)

downloadDEB:
	$(call targetLog,+)
	mkdir -p $(APT_MOUNT) $(ISO_MOUNT)
	-mountpoint -q $(ISO_MOUNT) || mount $(WORKSPACE)/iso/$(ISO_FILE) $(ISO_MOUNT)
	bash functions.sh createCache $(APT_MOUNT) $(ISO_MOUNT) "$(PACKAGES_BOOT) $(PACKAGES_LIST)"
	-umount $(ISO_MOUNT)
	$(call targetLog,-)

downloadTGZ:
	$(call targetLog,+)
	wget http://nginx.org/download/nginx-1.6.2.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/nginx-1.6.2.tar.gz
	wget https://github.com/yaoweibin/nginx_upstream_check_module/archive/v0.3.0.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/v0.3.0.tar.gz
	wget https://github.com/openresty/headers-more-nginx-module/archive/v0.25.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/v0.25.tar.gz
	$(call targetLog,-)

createREPO: | downloadISO downloadDEB downloadTGZ

clean:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	rm -fr $(WORKSPACE)/image/$(JOB)
	rm -fr $(WORKSPACE)/output
	rm -fr $(WORKSPACE)/dir
	$(call targetLog,-)

destroy:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/iso
	rm -fr $(WORKSPACE)/build
	rm -fr $(WORKSPACE)/image
	rm -fr $(WORKSPACE)/output
	rm -fr $(WORKSPACE)/dir
	rm -fr $(WORKSPACE)/apt
	$(call targetLog,-)

#appliance: | vbox ovf vhd vmx box tbz qcow2
appliance: | ovf vmx box
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vbox: | $(VBOX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

ovf: | $(OVF)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vhd: | $(VHD)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vmx: | $(VMX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

box: | $(BOX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

tbz: | $(TBZ)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

qcow2: | $(QCOW2)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

$(VMDK): | downloadISO createREPO
	$(call targetLog,+)
	mkdir -p $(DIR_MOUNT) $(ISO_MOUNT)
	-mountpoint -q $(ISO_MOUNT) || mount $(WORKSPACE)/iso/$(ISO_FILE) $(ISO_MOUNT)

	rm -fr $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	mkdir -p $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	mkdir -p $(WORKSPACE)/image
	mkdir -p $(WORKSPACE)/build
	mkdir -p $(WORKSPACE)/output/$(JOB)

	qemu-img create -f raw $(DISK_QEMU) $(DISK_SIZE)G
	echo -e "\x55\xaa" | dd bs=1 count=2 seek=510 of=$(DISK_QEMU) conv=notrunc
	bash functions.sh formatDisk $(DISK_QEMU) $(DISK_SIZE) $(SWAP_SIZE) $(SECTOR_INIT) $(SECTOR_SIZE)
	bash functions.sh createBootstrap $(DISK_QEMU) $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(APT_MOUNT) $(ISO_MOUNT) $(WORKSPACE)
	bash functions.sh installPackages $(DISK_QEMU) $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(APT_MOUNT) $(ISO_MOUNT) "$(PACKAGES_BOOT)"
	bash functions.sh installPackages $(DISK_QEMU) $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(APT_MOUNT) $(ISO_MOUNT) "$(PACKAGES_LIST)"
	bash functions.sh installBootloader $(DISK_QEMU) $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(ISO_MOUNT) $(NAME)
	bash functions.sh installOverlay $(DISK_QEMU) $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(WORKSPACE)
	bash functions.sh installConfiguration $(DISK_QEMU) $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(ISO_MOUNT) $(NAME) $(VERSION) $(JOB)
	qemu-img convert -p -O vmdk $(DISK_QEMU) $(VMDK)

	-umount $(ISO_MOUNT)
	$(call targetLog,-)

$(VBOX): | $(VMDK)
	$(call targetLog,+)
	[ -f $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk ] || cp $(VMDK) $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(WORKSPACE)/build  --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)"
	sed -i 's|$(BUILD_DIR)/||g' $(VBOX)
	rm $(VBOX)-prev
	$(call targetLog,-)


$(OVF): | $(VMDK)
	$(call targetLog,+)
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(OUTPUT_DIR) --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(VMDK)
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(OVF)
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)"
	bash functions.sh updateOVF $(OVF)
	$(call targetLog,-)

$(VHD): | $(VMDK)
	$(call targetLog,+)
	VBoxManage clonehd $(VMDK) $(VHD) -format VHD
	$(call targetLog,-)

$(VMX): | $(VMDK)
	$(call targetLog,+)
	cp $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	rm -fr $(VMX)
	@echo '#!/usr/bin/env vmware'  >> $(VMX)
	@echo 'config.version = "8"'  >> $(VMX)
	@echo 'tools.syncTime = "true"'  >> $(VMX)
	@echo 'uuid.action = "create"' >> $(VMX)
	@echo 'virtualHW.version = "9"'  >> $(VMX)
	@echo 'displayName = "$(NAME).x86_64-$(VERSION).$(JOB)"' >> $(VMX)
	@echo 'memsize = "$(RAM)"'  >> $(VMX)
	@echo 'guestOS = "ubuntu"' >> $(VMX)
	@echo 'scsi0.present = "true"' >> $(VMX)
	@echo 'scsi0.sharedBus = "none"' >> $(VMX)
	@echo 'scsi0.virtualDev = "lsilogic"'  >> $(VMX)
	@echo 'scsi0:0.present = "true"' >> $(VMX)
	@echo 'scsi0:0.fileName = "$(NAME).x86_64-$(VERSION).$(JOB).vmdk"' >> $(VMX)
	@echo 'scsi0:0.deviceType = "scsi-hardDisk"'  >> $(VMX)
	@echo 'ethernet1.present = "true"' >> $(VMX)
	@echo 'ethernet1.addressType = "generated"' >> $(VMX)
	@echo 'ethernet1.connectionType = "bridged"' >> $(VMX)
	@echo 'powerType.powerOff = "soft"' >> $(VMX)
	@echo 'priority.ungrabbed = "normal"' >> $(VMX)
	@echo 'powerType.suspend = "soft"'  >> $(VMX)
	@echo 'powerType.powerOn = "soft"' >> $(VMX)
	@echo 'usb.present = "true"'  >> $(VMX)
	@echo 'priority.grabbed = "normal"' >> $(VMX)
	@echo 'powerType.reset = "soft"' >> $(VMX)
	$(call targetLog,-)

$(BOX): | $(VMDK)
	$(call targetLog,+)
	mkdir -p $(OUTPUT_DIR)/box
	cp $(DISK_QEMU) $(DISK_QEMU).vagrant
	bash functions.sh installVagrant $(DISK_QEMU).vagrant $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(WORKSPACE)
	qemu-img convert -p -O vmdk $(DISK_QEMU).vagrant $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(OUTPUT_DIR)/box --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)" --delete
	bash functions.sh updateOVF $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	mv $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).ovf $(OUTPUT_DIR)/box/box.ovf
	echo '{ "provider" : "virtualbox", "format" : "vmdk" }' > $(OUTPUT_DIR)/box/metadata.json
	echo '{"versions":[{"version":"$(VERSION).$(JOB)","providers":[{"url":"$(NAME).x86_64-$(VERSION).$(JOB).box","name":"virtualbox"}]}],"description": "$(NAME) - appliance","name":"$(NAME)"}' > $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).json
	tar -czf $(BOX) -C $(OUTPUT_DIR)/box box.ovf metadata.json $(NAME).x86_64-$(VERSION).$(JOB)-disk1.vmdk
	cp -fr $(WORKSPACE)/templates/Vagrantfile.orig $(BUILD_DIR)/Vagrantfile
	sed -i "s|__NAME__|$(NAME)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__MAC__|$(MAC)|g" $(BUILD_DIR)/Vagrantfile
	$(call targetLog,-)

$(TBZ): | $(VMDK)
	$(call targetLog,+)
	cp $(DISK_QEMU) $(DISK_QEMU).container
	bash functions.sh installContainer $(DISK_QEMU).container $(SECTOR_INIT) $(SECTOR_SIZE) $(DIR_MOUNT) $(TBZ)
	$(call targetLog,-)

$(QCOW2): | $(VMDK)
	$(call targetLog,+)
	qemu-img convert -O qcow2 $(DISK_QEMU) $(QCOW2)
	$(call targetLog,-)
