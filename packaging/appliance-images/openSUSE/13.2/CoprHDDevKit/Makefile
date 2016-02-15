#
# Copyright 2015-2016 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
WORKSPACE := $(shell dirname $(MAKEFILE_LIST))
NAME := CoprHDDevKit
VERSION := 3.0.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := openSUSE-13.2-DVD-x86_64.iso
ISO_URL := http://download.opensuse.org/distribution/13.2/iso/$(ISO_FILE)
RAM := 8192
MAC := $(shell printf "00%02X%02X%02X%02X%02X" $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256])

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
OVF := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
VMX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx
TBZ := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).tbz
BOX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).box
VHD := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vhd
VBOX := $(BUILD_DIR)/$(NAME)-$(VERSION).$(JOB).vbox
QCOW2 := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).qcow2
ZYPPER_PARAMS := --reposd-dir $(WORKSPACE)/zypper/reposd --cache-dir $(WORKSPACE)/zypper/cache --solv-cache-dir $(WORKSPACE)/zypper/solv --pkg-cache-dir $(WORKSPACE)/zypper/pkg

.DEFAULT: all
.PHONY: downloadISO downloadRPM createREPO ovf vmx tbz box vhd vbox qcow2 appliance devkit clean destroy
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
	rm -fr $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	rm -fr $(WORKSPACE)/image/$(JOB)
	rm -fr $(WORKSPACE)/output
	rm -fr $(WORKSPACE)/zypper/repo
	rm -fr $(WORKSPACE)/zypper/reposd

destroy:
	rm -fr $(WORKSPACE)/iso
	rm -fr $(WORKSPACE)/build
	rm -fr $(WORKSPACE)/image
	rm -fr $(WORKSPACE)/output
	rm -fr $(WORKSPACE)/zypper

appliance: | ovf vmx box tbz

ovf: | $(OVF)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vmx: | $(VMX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

tbz: | $(TBZ)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

box: | $(BOX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vhd: | $(VHD)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vbox: | $(VBOX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

qcow2: | $(QCOW2)
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
	mkdir -p $(WORKSPACE)/templates/root/opt/ADG/conf
	cp -f $(WORKSPACE)/templates/config.orig.xml $(WORKSPACE)/templates/config.xml
	cp -f $(WORKSPACE)/configure.sh $(WORKSPACE)/templates/root/opt/ADG/conf/
	chmod u+x $(WORKSPACE)/templates/root/opt/ADG/conf/configure.sh
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
	@[ -x $(IMAGE_DIR)/usr/sbin/nginx ] || ( echo "[ERROR] Failed to download/install nginx" && exit -1 )
	kiwi --verbose 3 --yes --create $(IMAGE_DIR) --add-profile appliance --type vmx --destdir $(OUTPUT_DIR)
	-umount $(WORKSPACE)/iso/mount
	$(call targetLog,-)

$(OVF): | $(VMDK)
	$(call targetLog,+)
	ln -fs $(NAME).x86_64-$(VERSION).$(JOB).vmdk $(OUTPUT_DIR)/disk.vmdk
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(OUTPUT_DIR) --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(OUTPUT_DIR)/disk.vmdk
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(OVF)
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)" --delete
	bash $(WORKSPACE)/configure.sh updateOVF $(OVF)
	$(call targetLog,-)

$(VMX): | $(VMDK)
	$(call targetLog,+)
	[ -f $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk ] || cp $(VMDK) $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	cp $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx $(VMX)
	$(call targetLog,-)

$(TBZ): | $(VMDK)
	$(call targetLog,+)
	echo '' > $(IMAGE_DIR)/etc/fstab
	echo 'LOADER_TYPE="none"' > $(IMAGE_DIR)/etc/sysconfig/bootloader
	echo 'LOADER_LOCATION="none"' >> $(IMAGE_DIR)/etc/sysconfig/bootloader
	echo 'ROOTFS_FSCK="0"' > $(IMAGE_DIR)/etc/sysconfig/boot
	echo 'ROOTFS_BLKDEV="/dev/null"' >> $(IMAGE_DIR)/etc/sysconfig/boot
	echo 'console"' >> $(IMAGE_DIR)/etc/securetty
	rm -fr $(IMAGE_DIR)/run/*
	rm -fr $(IMAGE_DIR)/.kconfig
	rm -fr $(IMAGE_DIR)/.profile
	rm -fr $(IMAGE_DIR)/kiwi-hooks
	rm -fr $(IMAGE_DIR)/image
	rm -fr $(IMAGE_DIR)/Swap
	sed -i s/"udev_sync = 1"/"udev_sync = 0"/g $(IMAGE_DIR)/etc/lvm/lvm.conf
	sed -i s/"udev_rules = 1"/"udev_rules = 0"/g $(IMAGE_DIR)/etc/lvm/lvm.conf
	[ ! -x /usr/bin/pigz ] || tar -c -f $(TBZ) -C $(IMAGE_DIR) -I pigz .
	[ -x /usr/bin/pigz ] || tar -c -f $(TBZ) -C $(IMAGE_DIR) -J .
	$(call targetLog,-)

$(BOX): | $(VMDK)
	$(call targetLog,+)
	chroot $(IMAGE_DIR) /opt/ADG/conf/configure.sh installVagrant
	-! mountpoint -q $(IMAGE_DIR)/var/cache/kiwi || umount $(IMAGE_DIR)/var/cache/kiwi
	-mountpoint -q $(WORKSPACE)/iso/mount || mount $(WORKSPACE)/iso/$(ISO_FILE) $(WORKSPACE)/iso/mount
	kiwi --verbose 3 --yes --create $(IMAGE_DIR) --add-profile appliance --type vmx --destdir $(OUTPUT_DIR)/box
	-umount $(WORKSPACE)/iso/mount
	chroot $(IMAGE_DIR) userdel -r vagrant
	chroot $(IMAGE_DIR) groupdel vagrant
	sed -i "/^vagrant/d" $(IMAGE_DIR)/etc/sudoers
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(OUTPUT_DIR)/box --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(OUTPUT_DIR)/box/box.ovf
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)" --delete
	bash $(WORKSPACE)/configure.sh updateOVF $(OUTPUT_DIR)/box/box.ovf
	@echo '{ "provider" : "virtualbox", "format" : "vmdk" }' > $(OUTPUT_DIR)/box/metadata.json
	tar -czf $(BOX) -C $(OUTPUT_DIR)/box box.ovf metadata.json box-disk1.vmdk
	cp -fr $(WORKSPACE)/templates/Vagrantfile.orig $(BUILD_DIR)/Vagrantfile
	sed -i "s|__NAME__|$(NAME)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__MAC__|$(MAC)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__RAM__|$(RAM)|g" $(BUILD_DIR)/Vagrantfile
	$(call targetLog,-)

$(VHD): | $(VMDK)
	$(call targetLog,+)
	VBoxManage clonehd $(VMDK) $(VHD) -format VHD
	$(call targetLog,-)

$(VBOX): | $(VMDK)
	$(call targetLog,+)
	[ -f $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk ] || cp $(VMDK) $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(WORKSPACE)/build  --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)"
	rm -fr "$${HOME}/.config/VirtualBox/$(OUTPUT_DIR)
	sed -i 's|$(BUILD_DIR)/||g' $(VBOX)
	rm $(VBOX)-prev
	$(call targetLog,-)

$(QCOW2): | $(VMDK)
	$(call targetLog,+)
	qemu-img convert -O qcow2 $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).raw $(QCOW2)
	$(call targetLog,-)
