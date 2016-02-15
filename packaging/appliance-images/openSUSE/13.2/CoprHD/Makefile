#
# Copyright 2015-2016 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
export DO_NOT_START := yes
WORKSPACE := $(shell dirname $(MAKEFILE_LIST))
NAME := CoprHD
VERSION := 3.0.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := openSUSE-13.2-DVD-x86_64.iso
ISO_URL := http://download.opensuse.org/distribution/13.2/iso/$(ISO_FILE)
RAM := 8192
MAC := $(shell printf "00%02X%02X%02X%02X%02X" $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256] $$[RANDOM%256])
SOURCE_RPM := jenkins
STORAGEOS_VERSION := 3.0.0.0
STORAGEOS_STABLE := $(shell wget --no-check-certificate --quiet -O - https://build.coprhd.org/jenkins/job/CH-coprhd-controller-master/lastStableBuild/buildNumber)
ifeq ($(SOURCE_RPM),jenkins)
    JOB := $(STORAGEOS_STABLE)
endif

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
OVF := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
VMX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmx
TGZ := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).tgz
BOX := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).box
VHD := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vhd
VBOX := $(BUILD_DIR)/$(NAME)-$(VERSION).$(JOB).vbox
QCOW2 := $(BUILD_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).qcow2
ZYPPER_PARAMS := --reposd-dir $(WORKSPACE)/zypper/reposd --cache-dir $(WORKSPACE)/zypper/cache --solv-cache-dir $(WORKSPACE)/zypper/solv --pkg-cache-dir $(WORKSPACE)/zypper/pkg

.DEFAULT: all
.PHONY: downloadISO downloadRPM downloadTGZ createREPO ovf vmx tgz box vhd vbox qcow2 appliance clean destroy
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
	zypper $(ZYPPER_PARAMS) addrepo --name suse-13.2-oss --no-gpgcheck http://download.opensuse.org/distribution/13.2/repo/oss/suse suse-13.2-oss
	zypper $(ZYPPER_PARAMS) addrepo --name suse-13.2-monitoring --no-gpgcheck http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 suse-13.2-monitoring
	zypper $(ZYPPER_PARAMS) addrepo --name suse-13.2-seife --no-gpgcheck http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 suse-13.2-seife
	zypper $(ZYPPER_PARAMS) addrepo --name suse-13.2-python --no-gpgcheck http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_13.2 suse-13.2-python
	zypper $(ZYPPER_PARAMS) addrepo --name suse-13.2-network --no-gpgcheck http://download.opensuse.org/repositories/network:/utilities/openSUSE_13.2 suse-13.2-network
	zypper $(ZYPPER_PARAMS) modifyrepo --priority  3 suse-13.2-oss
	zypper $(ZYPPER_PARAMS) modifyrepo --priority  2 suse-13.2-monitoring
	zypper $(ZYPPER_PARAMS) modifyrepo --priority  2 suse-13.2-seife
	zypper $(ZYPPER_PARAMS) modifyrepo --priority  4 suse-13.2-python
	zypper $(ZYPPER_PARAMS) modifyrepo --priority  4 suse-13.2-network
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download atop ca-certificates-cacert fbiterm fribidi GeoIP GeoIP-data java-1_8_0-openjdk-devel keepalived libGeoIP1 libserf-devel ndisc6 openssh-fips perl-Tk python-cjson python-gpgme python-iniparse python-py python-requests python-setools setools-libs sipcalc sshpass strongswan strongswan-ipsec strongswan-libs0 sysstat yum
	zypper $(ZYPPER_PARAMS) addrepo --name suse-13.2-oss-update --no-gpgcheck http://download.opensuse.org/repositories/openSUSE:/13.2:/Update/standard suse-13.2-oss-update
	zypper $(ZYPPER_PARAMS) modifyrepo --priority  1 suse-13.2-oss-update
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download libudev1 lvm2 udev
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

appliance: | ovf vmx box tgz

ovf: | $(OVF)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

vmx: | $(VMX)
	$(call targetLog,+)
	chmod a+r $(BUILD_DIR)/*
	$(call targetLog,-)

tgz: | $(TGZ)
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

$(VMDK): | downloadISO createREPO
	$(call targetLog,+)
	bash $(WORKSPACE)/patch.sh
	mkdir -p $(WORKSPACE)/templates/root/opt/ADG/conf
	cp -f $(WORKSPACE)/templates/config.orig.xml $(WORKSPACE)/templates/config.xml
	cp -f $(WORKSPACE)/configure.sh $(WORKSPACE)/templates/root/opt/ADG/conf/
	sed -i "s|__NAME__|$(NAME)|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__ISO__|$(WORKSPACE)/iso/mount|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__REPO__|$(WORKSPACE)/zypper/repo|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__UUID__|$$RANDOM|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__RAM__|$(RAM)|g" $(WORKSPACE)/templates/config.xml
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

$(TGZ): | $(VMDK)
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
	-chroot $(IMAGE_DIR) rpm -e ant apache2 apache2-mod_perl apache2-prefork apache2-utils autoconf automake btrfsprogs cdrkit-cdrtools-compat checkmedia cpp cpp48 createrepo dbus-1-glib dbus-1-python gcc gcc48 gcc48-c++ gcc-c++ genisoimage girepository-1_0 git git-core glib2-devel glibc-devel gnu-unifont-bitmap-fonts grub2 grub2-i386-pc iceauth icedax kernel-default kernel-default-devel kernel-devel kernel-macros kernel-source keyutils-devel kiwi kiwi-desc-isoboot kiwi-desc-oemboot kiwi-desc-vmxboot kiwi-templates kiwi-tools kpartx krb5-devel libapr1-devel libapr-util1-devel libcom_err-devel libdb-4_8-devel libexpat-devel libgcrypt-devel libgirepository-1_0-1 libgpg-error-devel libopenssl-devel libqt4-x11 libSDL-1_2-0 libserf-devel libstdc++48-devel libstdc++-devel libtool libuuid-devel libX11-xcb1 libXaw7 libXcursor1 libXdamage1 libXfixes3 libXfontcache1 libXft2 libXinerama1 libxkbfile1 libxml2-devel libXmu6 libXmuu1 libXpm4 libXrandr2 libXxf86misc1 libXxf86vm1 linux-glibc-devel make Mesa Mesa-libEGL1 Mesa-libGL1 Mesa-libGL1 multipath-tools ncurses-devel nfs-client openldap2-devel os-prober pam-devel pcre-devel perl-Bootloader perl-Class-Singleton perl-Config-General perl-Config-IniFiles perl-Digest-SHA1 perl-Encode-Locale perl-Error perl-File-Listing perl-File-Slurp perl-HTML-Parser perl-HTML-Tagset perl-HTTP-Cookies perl-HTTP-Daemon perl-HTTP-Date perl-HTTP-Message perl-HTTP-Negotiate perl-IO-HTML perl-JSON perl-libwww-perl perl-Linux-Pid perl-List-MoreUtils perl-LWP-MediaTypes perl-Net-HTTP perl-Readonly perl-solv perl-Tie-IxHash perl-Tk perl-Tk perl-URI perl-WWW-RobotRules perl-XML-LibXML perl-XML-NamespaceSupport perl-XML-SAX perl-XML-SAX-Base plymouth plymouth-branding-openSUSE plymouth-plugin-script plymouth-scripts python-devel python-gobject python-gobject2 python-yum qemu qemu-tools readline-devel rpm-build subversion setxkbmap virtualbox virtualbox-guest-kmp-default virtualbox-host-kmp-default vorbis-tools xauth xbitmaps xconsole xdm xinit xkbcomp xmessage xmodmap xorg-x11-essentials xorg-x11-fonts xorg-x11-fonts-core xorg-x11-server xprop xrdb xset xsetroot xterm xz-devel yum yum-metadata-parser zlib-devel &> /dev/null
	chroot $(IMAGE_DIR) find . -type f -name "*.rpmsave" -exec rm {} +
	tar -czf $(TGZ) -C $(IMAGE_DIR) .
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
