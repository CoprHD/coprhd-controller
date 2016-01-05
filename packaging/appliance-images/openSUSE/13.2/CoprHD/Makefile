#
# Copyright 2015 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
export DO_NOT_START := yes
WORKSPACE := $(shell dirname $(MAKEFILE_LIST))
NAME := CoprHD
VERSION := 2.5.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := openSUSE-13.2-DVD-x86_64.iso
ISO_URL := http://download.opensuse.org/distribution/13.2/iso/$(ISO_FILE)
SOURCE_RPM := jenkins
RAM := 8192
MAC := $(shell printf "00%02X%02X%02X%02X%02X" $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256])

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
VBOX := $(BUILD_DIR)/$(NAME)-$(VERSION).$(JOB).vbox
OVF := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
VHD := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vhd
VMX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx
BOX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).box
TBZ := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).tbz
QCOW2 := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).qcow2
STORAGEOS_VERSION := 2.5.0.0
STORAGEOS_STABLE := $(shell wget --no-check-certificate --quiet -O - https://build.coprhd.org/jenkins/job/CH-coprhd-controller-master/lastStableBuild/buildNumber)
ZYPPER_PARAMS := --reposd-dir $(WORKSPACE)/zypper/reposd --cache-dir $(WORKSPACE)/zypper/cache --solv-cache-dir $(WORKSPACE)/zypper/solv --pkg-cache-dir $(WORKSPACE)/zypper/pkg

.DEFAULT: all
.PHONY: downloadISO downloadRPM downloadTGZ createREPO vbox ovf vhd vmx box tbz qcow2 appliance clean destroy
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
	@# openSUSE's OSS packages
	zypper $(ZYPPER_PARAMS) addrepo --name repo-oss --no-gpgcheck --no-check http://download.opensuse.org/distribution/13.2/repo/oss repo-oss.repo
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download python-py python-requests python-setools python-iniparse python-gpgme
	zypper $(ZYPPER_PARAMS) --non-interactive download libserf-devel fribidi fbiterm openssh-fips perl-Tk setools-libs sshpass sysstat ca-certificates-cacert yum keepalived strongswan strongswan-ipsec strongswan-libs0 java-1_8_0-openjdk-devel
	@# openSUSE's non-OSS packages
	zypper $(ZYPPER_PARAMS) addrepo --name devel-languages-python --no-gpgcheck --no-check http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_13.2 devel-languages-python.repo
	zypper $(ZYPPER_PARAMS) addrepo --name server-monitoring --no-gpgcheck --no-check http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 server-monitoring.repo
	zypper $(ZYPPER_PARAMS) addrepo --name home-seife-testing --no-gpgcheck --no-check http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 home-seife-testing.repo
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download atop GeoIP-data libGeoIP1 GeoIP sipcalc python-cjson
	$(call targetLog,-)

downloadTGZ:
	$(call targetLog,+)
	wget http://nginx.org/download/nginx-1.6.2.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/nginx-1.6.2.tar.gz
	wget --no-check-certificate https://github.com/yaoweibin/nginx_upstream_check_module/archive/v0.3.0.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/v0.3.0.tar.gz
	wget --no-check-certificate https://github.com/openresty/headers-more-nginx-module/archive/v0.25.tar.gz --continue --progress=bar --output-document=$(WORKSPACE)/templates/v0.25.tar.gz
	$(call targetLog,-)

createREPO: | downloadRPM downloadTGZ
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
	cp -fr $(WORKSPACE)/

qcow2: | $(QCOW2)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

$(VMDK): | downloadISO createREPO
	$(call targetLog,+)
	bash $(WORKSPACE)/patch.sh
	cp -f $(WORKSPACE)/templates/config.orig.xml $(WORKSPACE)/templates/config.xml
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
	bash $(WORKSPACE)/configure.sh $(OVF)
	$(call targetLog,-)

$(VHD): | $(VMDK)
	$(call targetLog,+)
	VBoxManage clonehd $(VMDK) $(VHD) -format VHD
	$(call targetLog,-)

$(VMX): | $(VMDK)
	$(call targetLog,+)
	[ -f $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk ] || cp $(VMDK) $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	cp $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx $(VMX)
	$(call targetLog,-)

$(BOX): | $(VMDK)
	$(call targetLog,+)
	chroot $(IMAGE_DIR) groupadd vagrant
	chroot $(IMAGE_DIR) useradd -m -g vagrant -s /bin/bash -d /home/vagrant vagrant
	chroot $(IMAGE_DIR) mkdir -p /home/vagrant/.ssh
	echo "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA6NF8iallvQVp22WDkTkyrtvp9eWW6A8YVr+kz4TjGYe7gHzIw+niNltGEFHzD8+v1I2YJ6oXevct1YeS0o9HZyN1Q9qgCgzUFtdOKLv6IedplqoPkcmF0aYet2PkEDo3MlTBckFXPITAMzF8dJSIFo9D8HfdOV0IAdx4O7PtixWKn5y2hMNG0zQPyUecp4pzC6kivAIhyfHilFR61RGL+GPXQ2MWZWFYbAGjyiYJnAmCP3NOTd0jMZEnDkbUvxhMmBYSdETk1rRgm+R4LOzFUGaHqHDLKLX+FIPKcF96hrucXzcWyLbIbEgE98OHlnVYCzRdK8jlqm8tehUc9c9WhQ== vagrant insecure public key" > $(IMAGE_DIR)/home/vagrant/.ssh/authorized_keys
	echo "vagrant ALL=(ALL) NOPASSWD: ALL" >> $(IMAGE_DIR)/etc/sudoers
	echo 'SUBSYSTEM=="net", ACTION=="add", DRIVERS=="?*", ATTR{address}=="?*", ATTR{type}=="1", KERNEL=="eth0", NAME="eth0"' >> $(IMAGE_DIR)/etc/udev/rules.d/70-persistent-net.rules
	echo 'SUBSYSTEM=="net", ACTION=="add", DRIVERS=="?*", ATTR{address}=="?*", ATTR{type}=="1", KERNEL=="eth1", NAME="eth1"' >> $(IMAGE_DIR)/etc/udev/rules.d/70-persistent-net.rules
	echo "BOOTPROTO='dhcp'" >> $(IMAGE_DIR)/etc/sysconfig/network/ifcfg-eth0
	echo "STARTMODE='auto'" >> $(IMAGE_DIR)/etc/sysconfig/network/ifcfg-eth0
	echo "BOOTPROTO='dhcp'" >> $(IMAGE_DIR)/etc/sysconfig/network/ifcfg-eth1
	echo "STARTMODE='auto'" >> $(IMAGE_DIR)/etc/sysconfig/network/ifcfg-eth1
	chroot $(IMAGE_DIR) chown -R vagrant:vagrant /home/vagrant
	-! mountpoint -q $(IMAGE_DIR)/var/cache/kiwi || umount $(IMAGE_DIR)/var/cache/kiwi
	-mountpoint -q $(WORKSPACE)/iso/mount || mount $(WORKSPACE)/iso/$(ISO_FILE) $(WORKSPACE)/iso/mount
	kiwi --verbose 3 --yes --create $(IMAGE_DIR) --add-profile appliance --type vmx --destdir $(OUTPUT_DIR)/box
	-umount $(WORKSPACE)/iso/mount
	chroot $(IMAGE_DIR) userdel -r vagrant
	chroot $(IMAGE_DIR) groupdel vagrant
	sed -i "/^vagrant/d" $(IMAGE_DIR)/etc/sudoers
	rm $(IMAGE_DIR)/etc/udev/rules.d/70-persistent-net.rules
	rm $(IMAGE_DIR)/etc/sysconfig/network/ifcfg-eth0
	rm $(IMAGE_DIR)/etc/sysconfig/network/ifcfg-eth1
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --basefolder $(OUTPUT_DIR)/box --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory $(RAM) --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)" --delete
	bash $(WORKSPACE)/configure.sh $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	mv $(OUTPUT_DIR)/box/$(NAME).x86_64-$(VERSION).$(JOB).ovf $(OUTPUT_DIR)/box/box.ovf
	@echo '{ "provider" : "virtualbox", "format" : "vmdk" }' > $(OUTPUT_DIR)/box/metadata.json
	@echo '{"versions":[{"version":"$(VERSION).$(JOB)","providers":[{"url":"$(NAME).x86_64-$(VERSION).$(JOB).box","name":"virtualbox"}]}],"description": "$(NAME) - appliance","name":"$(NAME)"}' > $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).json
	tar -czf $(BOX) -C $(OUTPUT_DIR)/box box.ovf metadata.json $(NAME).x86_64-$(VERSION).$(JOB)-disk1.vmdk
	cp -fr $(WORKSPACE)/templates/Vagrantfile.orig $(BUILD_DIR)/Vagrantfile
	sed -i "s|__NAME__|$(NAME)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__MAC__|$(MAC)|g" $(BUILD_DIR)/Vagrantfile
	sed -i "s|__RAM__|$(RAM)|g" $(BUILD_DIR)/Vagrantfile

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
	[ ! -x /usr/bin/pigz ] || tar -c -f $(TBZ) -C $(IMAGE_DIR) -I pigz .
	[ -x /usr/bin/pigz ] || tar -c -f $(TBZ) -C $(IMAGE_DIR) -J .
	$(call targetLog,-)

$(QCOW2): | $(VMDK)
	$(call targetLog,+)
	qemu-img convert -O qcow2 $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).raw $(QCOW2)
	$(call targetLog,-)
