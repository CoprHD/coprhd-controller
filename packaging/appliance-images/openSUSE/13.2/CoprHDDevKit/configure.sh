#!/bin/bash
#
# Copyright 2015-2016 EMC Corporation
# All Rights Reserved
#

function installRepositories
{
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-oss \
         --no-gpgcheck http://download.opensuse.org/distribution/13.2/repo/oss/suse suse-13.2-oss
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-non-oss \
         --no-gpgcheck http://download.opensuse.org/distribution/13.2/repo/non-oss/suse suse-13.2-non-oss
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-monitoring \
         --no-gpgcheck http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 suse-13.2-monitoring
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-seife \
         --no-gpgcheck http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 suse-13.2-seife
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-python \
         --no-gpgcheck http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_13.2 suse-13.2-python
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-building \
         --no-gpgcheck http://download.opensuse.org/repositories/devel:/tools:/building/openSUSE_13.2 suse-13.2-building
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-appliances \
         --no-gpgcheck http://download.opensuse.org/repositories/Virtualization:/Appliances/openSUSE_13.2 suse-13.2-appliances
  zypper --non-interactive --no-gpg-checks addrepo --no-check --name suse-13.2-containers \
         --no-gpgcheck http://download.opensuse.org/repositories/Virtualization:/containers/openSUSE_13.2 suse-13.2-containers

  return 0
}

function installPackages
{
  mkdir -p /tmp/coprhd.d
  cp -f /etc/zypp/repos.d/suse-13.2-oss.repo /tmp/coprhd.d/
  cp -f /etc/zypp/repos.d/suse-13.2-non-oss.repo /tmp/coprhd.d/
  cp -f /etc/zypp/repos.d/suse-13.2-monitoring.repo /tmp/coprhd.d/
  cp -f /etc/zypp/repos.d/suse-13.2-python.repo /tmp/coprhd.d/
  cp -f /etc/zypp/repos.d/suse-13.2-seife.repo /tmp/coprhd.d/

  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks modifyrepo --priority 1 suse-13.2-monitoring
  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks modifyrepo --priority 1 suse-13.2-seife
  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks modifyrepo --priority 2 suse-13.2-oss
  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks modifyrepo --priority 3 suse-13.2-python
  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks modifyrepo --priority 4 suse-13.2-non-oss

  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks refresh
  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks install --details --no-recommends --force-resolution ant apache2-mod_perl apache2-prefork atop bind-libs bind-utils ca-certificates-cacert ca-certificates-mozilla curl createrepo dhcpcd expect fontconfig fonts-config gcc-c++ GeoIP GeoIP-data git git-core glib2-devel gpgme grub2 ifplugd inst-source-utils iproute2 iputils java-1_7_0-openjdk java-1_7_0-openjdk-devel java-1_8_0-openjdk java-1_8_0-openjdk-devel keepalived kernel-default kernel-default-devel kernel-source kiwi kiwi-desc-isoboot kiwi-desc-oemboot kiwi-desc-vmxboot kiwi-templates libaudiofile1 libesd0 libgcrypt-devel libGeoIP1 libgpg-error-devel libmng2 libopenssl-devel libpcrecpp0 libpcreposix0 libqt4 libqt4-sql libqt4-x11 libSDL-1_2-0 libserf-devel libtool libuuid-devel libvpx1 libxml2-devel libXmu6 lvm2 make mkfontdir mkfontscale mozilla-nss-certs netcfg net-tools nfs-client openssh openssh-fips p7zip pam-devel parted pcre-devel perl-Config-General perl-Error perl-Tk plymouth python-cjson python-devel python-gpgme python-iniparse python-libxml2 python-py python-requests python-setools qemu qemu-tools readline-devel regexp rpm-build setools-libs sipcalc sshpass strongswan strongswan-ipsec strongswan-libs0 subversion sudo SuSEfirewall2 sysconfig sysconfig-netconfig syslinux sysstat systemd-logger tar telnet unixODBC vim virtualbox virtualbox-guest-kmp-default virtualbox-host-kmp-default wget xbitmaps xfsprogs xml-commons-jaxp-1.3-apis xmlstarlet xorg-x11-essentials xorg-x11-fonts xorg-x11-server xz-devel yum zlib-devel
  zypper --reposd-dir=/tmp/coprhd.d --non-interactive --no-gpg-checks clean

  rm -fr /tmp/coprhd.d
}

function installJava
{
  update-alternatives --set java /usr/lib64/jvm/jre-1.7.0-openjdk/bin/java
  update-alternatives --set javac /usr/lib64/jvm/java-1.7.0-openjdk/bin/javac
}

function installNginx
{
  mkdir -p /tmp/nginx
  wget --continue --output-document=/tmp/nginx/nginx-1.6.2.tar.gz http://nginx.org/download/nginx-1.6.2.tar.gz
  wget --continue --output-document=/tmp/nginx/v0.3.0.tar.gz https://github.com/yaoweibin/nginx_upstream_check_module/archive/v0.3.0.tar.gz
  wget --continue --output-document=/tmp/nginx/v0.25.tar.gz https://github.com/openresty/headers-more-nginx-module/archive/v0.25.tar.gz
  tar --directory=/tmp/nginx -xzf /tmp/nginx/nginx-1.6.2.tar.gz
  tar --directory=/tmp/nginx -xzf /tmp/nginx/v0.3.0.tar.gz
  tar --directory=/tmp/nginx -xzf /tmp/nginx/v0.25.tar.gz
  patch --directory=/tmp/nginx/nginx-1.6.2 -p1 < /tmp/nginx/nginx_upstream_check_module-0.3.0/check_1.5.12+.patch
  bash -c "cd /tmp/nginx/nginx-1.6.2; ./configure --add-module=/tmp/nginx/nginx_upstream_check_module-0.3.0 --add-module=/tmp/nginx/headers-more-nginx-module-0.25 --with-http_ssl_module --prefix=/usr --conf-path=/etc/nginx/nginx.conf"
  make --directory=/tmp/nginx/nginx-1.6.2
  make --directory=/tmp/nginx/nginx-1.6.2 install
  rm -fr /tmp/nginx
}

function installStorageOS
{
  getent group storageos || groupadd -g 444 storageos
  getent passwd storageos || useradd -r -d /opt/storageos -c "StorageOS" -g 444 -u 444 -s /bin/bash storageos
  [ ! -d /opt/storageos ] || chown -R storageos:storageos /opt/storageos
  [ ! -d /data ] || chown -R storageos:storageos /data
}

function installVagrant
{
  getent group vagrant || groupadd vagrant
  getent passwd vagrant || useradd -m -g vagrant -s /bin/bash -d /home/vagrant vagrant
  mkdir -p /home/vagrant/.ssh
  echo "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA6NF8iallvQVp22WDkTkyrtvp9eWW6A8YVr+kz4TjGYe7gHzIw+niNltGEFHzD8+v1I2YJ6oXevct1YeS0o9HZyN1Q9qgCgzUFtdOKLv6IedplqoPkcmF0aYet2PkEDo3MlTBckFXPITAMzF8dJSIFo9D8HfdOV0IAdx4O7PtixWKn5y2hMNG0zQPyUecp4pzC6kivAIhyfHilFR61RGL+GPXQ2MWZWFYbAGjyiYJnAmCP3NOTd0jMZEnDkbUvxhMmBYSdETk1rRgm+R4LOzFUGaHqHDLKLX+FIPKcF96hrucXzcWyLbIbEgE98OHlnVYCzRdK8jlqm8tehUc9c9WhQ== vagrant insecure public key" > /home/vagrant/.ssh/authorized_keys
  grep --quiet vagrant /etc/sudoers || echo "vagrant ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers
  echo "BOOTPROTO='dhcp'"  > /etc/sysconfig/network/ifcfg-eth0
  echo "STARTMODE='auto'" >> /etc/sysconfig/network/ifcfg-eth0
  echo "USERCONTROL='no'" >> /etc/sysconfig/network/ifcfg-eth0
  chown -R vagrant:vagrant /home/vagrant
  ln -fs /dev/null /etc/udev/rules.d/80-net-name-slot.rules
  ln -fs /dev/null /etc/udev/rules.d/80-net-setup-link.rules
}

function installNetworkConfigurationFile
{
  eth=$2
  gateway=$3
  netmask=$4
  [ ! -z "${eth}" ] || eth=1
  [ ! -z "${gateway}" ] || gateway=$(route -n | grep 'UG[ \t]' | awk '{print $2}')
  [ ! -z "${netmask}" ] || netmask='255.255.255.0'
  ipaddr=$(ifconfig | awk '/inet addr/{print substr($2,6)}' | head -n ${eth} | tail -n 1)
  cat > /etc/ovfenv.properties <<EOF
network_1_ipaddr6=::0
network_1_ipaddr=${ipaddr}
network_gateway6=::0
network_gateway=${gateway}
network_netmask=${netmask}
network_prefix_length=64
network_vip6=::0
network_vip=${ipaddr}
node_count=1
node_id=vipr1
EOF
}

function installXorg
{
  cat > /etc/X11/xorg.conf <<EOF
Section "ServerLayout"
        Identifier     "X.org Configured"
        Screen      0  "Screen0" 0 0
        InputDevice    "Mouse0" "CorePointer"
        InputDevice    "Keyboard0" "CoreKeyboard"
EndSection

Section "Files"
        ModulePath   "/usr/lib64/xorg/modules"
        FontPath     "/usr/share/fonts/misc:unscaled"
        FontPath     "/usr/share/fonts/Type1/"
        FontPath     "/usr/share/fonts/100dpi:unscaled"
        FontPath     "/usr/share/fonts/75dpi:unscaled"
        FontPath     "/usr/share/fonts/ghostscript/"
        FontPath     "/usr/share/fonts/cyrillic:unscaled"
        FontPath     "/usr/share/fonts/misc/sgi:unscaled"
        FontPath     "/usr/share/fonts/truetype/"
        FontPath     "built-ins"
EndSection

Section "Module"
        Load  "glx"
EndSection

Section "InputDevice"
        Identifier  "Keyboard0"
        Driver      "kbd"
EndSection

Section "InputDevice"
        Identifier  "Mouse0"
        Driver      "mouse"
        Option      "Protocol" "auto"
        Option      "Device" "/dev/input/mice"
        Option      "ZAxisMapping" "4 5 6 7"
EndSection

Section "Monitor"
        Identifier   "Monitor0"
        VendorName   "Monitor Vendor"
        ModelName    "Monitor Model"
EndSection

Section "Device"
        Identifier  "Card0"
        Driver      "vmware"
        BusID       "PCI:0:15:0"
EndSection

Section "Screen"
        Identifier "Screen0"
        Device     "Card0"
        Monitor    "Monitor0"
        SubSection "Display"
                Viewport   0 0
                Depth     1
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     4
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     8
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     15
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     16
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     24
        EndSubSection
EndSection
EOF
}

function updateOVF
{
  OVF=$2

  cat ${OVF} | head -n -2 > ${OVF}.tmp
  sed -i "s|<VirtualHardwareSection>|<VirtualHardwareSection ovf:transport=\"iso\" ovf:required=\"false\">|g" ${OVF}.tmp
  sed -i "s|<vssd:VirtualSystemType>virtualbox-[0-9a-z.]\{1,\}</vssd:VirtualSystemType>|<vssd:VirtualSystemType>vmx-07</vssd:VirtualSystemType>|g" ${OVF}.tmp
  cat >> ${OVF}.tmp <<EOF
    <ProductSection ovf:class="vm" ovf:required="false">
      <Info>VM specific properties</Info>
      <Property ovf:key="vmname" ovf:type="string" ovf:value="SetupVM"/>
    </ProductSection>
    <ProductSection ovf:class="network" ovf:instance="SetupVM" ovf:required="false" xmlns:vmw="http://www.vmware.com/schema/ovf">
      <Info>Network Properties</Info>
      <Category>Network Properties</Category>
      <Property ovf:key="hostname" ovf:type="string" ovf:userConfigurable="true" ovf:value="" ovf:qualifiers="MinLen(0),MaxLen(65535)">
        <Label>Appliance fully qualified hostname</Label>
        <Description>e.g. host.example.com</Description>
      </Property>
      <Property ovf:key="DOM" ovf:type="string" ovf:userConfigurable="true" ovf:value="" ovf:qualifiers="MinLen(0),MaxLen(65535)">
        <Label>Search Domain(s) (separated by spaces) [optional]</Label>
        <Description>e.g. emc.com tools.emc.com</Description>
      </Property>
      <Property ovf:key="ipv40" ovf:userConfigurable="true" ovf:type="string" vmw:qualifiers="Ip">
        <Label>Network 1 IP Address</Label>
        <Description>The IPv4 address for this interface.</Description>
      </Property>
      <Property ovf:key="ipv4gateway" ovf:userConfigurable="true" ovf:type="string" vmw:qualifiers="Ip">
        <Label>Default Gateway</Label>
        <Description>The default IPv4 gateway address for this VM.</Description>
      </Property>
      <Property ovf:key="ipv4netmask0" ovf:value="255.255.255.0" ovf:userConfigurable="true" ovf:type="string">
        <Label>Network 1 Netmask</Label>
        <Description>The netmask or prefix for this interface.</Description>
      </Property>
      <Property ovf:key="ipv4dns" ovf:userConfigurable="true" ovf:type="string">
        <Label>DNS Server(s) (comma separated)</Label>
        <Description>The IPv4 domain name servers for this VM.</Description>
      </Property>
      <Property ovf:key="vip" ovf:userConfigurable="true" ovf:type="string">
        <Label>It will be used as the virtual IP (vip) address for CoprHDDevKit (comma separated)</Label>
        <Description>The IPv4 address for this interface.</Description>
      </Property>
    </ProductSection>
    <ProductSection ovf:class="system" ovf:required="false">
      <Info>System Properties</Info>
      <Category>System Properties</Category>
      <Property ovf:key="timezone" ovf:type="string" ovf:userConfigurable="true" ovf:value="US/Eastern" ovf:qualifiers="ValueMap{&quot;Africa/Addis_Ababa&quot;,&quot;Africa/Algiers&quot;,&quot;Africa/Blantyre&quot;,&quot;Africa/Brazzaville&quot;,&quot;Africa/Bujumbura&quot;,&quot;Africa/Cairo&quot;,&quot;Africa/Casablanca&quot;,&quot;Africa/Ceuta&quot;,&quot;Africa/Conakry&quot;,&quot;Africa/Dakar&quot;,&quot;Africa/Dar_es_Salaam&quot;,&quot;Africa/Djibouti&quot;,&quot;Africa/Douala&quot;,&quot;Africa/El_Aaiun&quot;,&quot;Africa/Freetown&quot;,&quot;Africa/Gaborone&quot;,&quot;Africa/Harare&quot;,&quot;Africa/Johannesburg&quot;,&quot;Africa/Kampala&quot;,&quot;Africa/Khartoum&quot;,&quot;Africa/Kigali&quot;,&quot;Africa/Kinshasa&quot;,&quot;Africa/Lagos&quot;,&quot;Africa/Libreville&quot;,&quot;Africa/Lome&quot;,&quot;Africa/Luanda&quot;,&quot;Africa/Lubumbashi&quot;,&quot;Africa/Lusaka&quot;,&quot;Africa/Malabo&quot;,&quot;Africa/Maputo&quot;,&quot;Africa/Maseru&quot;,&quot;Africa/Mbabane&quot;,&quot;Africa/Mogadishu&quot;,&quot;Africa/Monrovia&quot;,&quot;Africa/Nairobi&quot;,&quot;Africa/Ndjamena&quot;,&quot;Africa/Niamey&quot;,&quot;Africa/Nouakchott&quot;,&quot;Africa/Ouagadougou&quot;,&quot;Africa/Porto-Novo&quot;,&quot;Africa/Sao_Tome&quot;,&quot;Africa/Timbuktu&quot;,&quot;Africa/Tripoli&quot;,&quot;Africa/Tunis&quot;,&quot;Africa/Windhoek&quot;,&quot;America/Adak&quot;,&quot;America/Anchorage&quot;,&quot;America/Anguilla&quot;,&quot;America/Antigua&quot;,&quot;America/Araguaina&quot;,&quot;America/Argentina/Buenos_Aires&quot;,&quot;America/Argentina/Catamarca&quot;,&quot;America/Argentina/ComodRivadavia&quot;,&quot;America/Argentina/Cordoba&quot;,&quot;America/Argentina/Jujuy&quot;,&quot;America/Argentina/La_Rioja&quot;,&quot;America/Blanc-Sablon&quot;,&quot;America/Boa_Vista&quot;,&quot;America/Bogota&quot;,&quot;America/Boise&quot;,&quot;America/Buenos_Aires&quot;,&quot;America/Cambridge_Bay&quot;,&quot;America/Campo_Grande&quot;,&quot;America/Cancun&quot;,&quot;America/Caracas&quot;,&quot;America/Catamarca&quot;,&quot;America/Cayenne&quot;,&quot;America/Cayman&quot;,&quot;America/Chicago&quot;,&quot;America/Chihuahua&quot;,&quot;America/Coral_Harbour&quot;,&quot;America/Cordoba&quot;,&quot;America/Costa_Rica&quot;,&quot;America/Cuiaba&quot;,&quot;America/Curacao&quot;,&quot;America/Danmarkshavn&quot;,&quot;America/Dawson&quot;,&quot;America/Dawson_Creek&quot;,&quot;America/Denver&quot;,&quot;America/Detroit&quot;,&quot;America/Dominica&quot;,&quot;America/Edmonton&quot;,&quot;America/Eirunepe&quot;,&quot;America/El_Salvador&quot;,&quot;America/Ensenada&quot;,&quot;America/Fortaleza&quot;,&quot;America/Fort_Wayne&quot;,&quot;America/Glace_Bay&quot;,&quot;America/Godthab&quot;,&quot;America/Goose_Bay&quot;,&quot;America/Grand_Turk&quot;,&quot;America/Grenada&quot;,&quot;America/Guadeloupe&quot;,&quot;America/Guatemala&quot;,&quot;America/Guayaquil&quot;,&quot;America/Guyana&quot;,&quot;America/Halifax&quot;,&quot;America/Havana&quot;,&quot;America/Hermosillo&quot;,&quot;America/Indiana/Indianapolis&quot;,&quot;America/Indiana/Knox&quot;,&quot;America/Indiana/Marengo&quot;,&quot;America/Indiana/Petersburg&quot;,&quot;America/Indianapolis&quot;,&quot;America/Indiana/Vevay&quot;,&quot;America/Indiana/Vincennes&quot;,&quot;America/Inuvik&quot;,&quot;America/Iqaluit&quot;,&quot;America/Jamaica&quot;,&quot;America/Jujuy&quot;,&quot;America/Juneau&quot;,&quot;America/Kentucky/Louisville&quot;,&quot;America/Kentucky/Monticello&quot;,&quot;America/Knox_IN&quot;,&quot;America/La_Paz&quot;,&quot;America/Lima&quot;,&quot;America/Los_Angeles&quot;,&quot;America/Louisville&quot;,&quot;America/Maceio&quot;,&quot;America/Managua&quot;,&quot;America/Manaus&quot;,&quot;America/Martinique&quot;,&quot;America/Mazatlan&quot;,&quot;America/Mendoza&quot;,&quot;America/Menominee&quot;,&quot;America/Merida&quot;,&quot;America/Mexico_City&quot;,&quot;America/Miquelon&quot;,&quot;America/Moncton&quot;,&quot;America/Monterrey&quot;,&quot;America/Montevideo&quot;,&quot;America/Montreal&quot;,&quot;America/Montserrat&quot;,&quot;America/Nassau&quot;,&quot;America/New_York&quot;,&quot;America/Nipigon&quot;,&quot;America/Nome&quot;,&quot;America/Noronha&quot;,&quot;America/North_Dakota/Center&quot;,&quot;America/North_Dakota/New_Salem&quot;,&quot;America/Panama&quot;,&quot;America/Pangnirtung&quot;,&quot;America/Paramaribo&quot;,&quot;America/Phoenix&quot;,&quot;America/Port-au-Prince&quot;,&quot;America/Porto_Acre&quot;,&quot;America/Port_of_Spain&quot;,&quot;America/Porto_Velho&quot;,&quot;America/Puerto_Rico&quot;,&quot;America/Rainy_River&quot;,&quot;America/Rankin_Inlet&quot;,&quot;America/Recife&quot;,&quot;America/Regina&quot;,&quot;America/Rio_Branco&quot;,&quot;America/Rosario&quot;,&quot;America/Santiago&quot;,&quot;America/Santo_Domingo&quot;,&quot;America/Sao_Paulo&quot;,&quot;America/Scoresbysund&quot;,&quot;America/Shiprock&quot;,&quot;America/St_Johns&quot;,&quot;America/St_Kitts&quot;,&quot;America/St_Lucia&quot;,&quot;America/St_Thomas&quot;,&quot;America/St_Vincent&quot;,&quot;America/Tegucigalpa&quot;,&quot;America/Thule&quot;,&quot;America/Thunder_Bay&quot;,&quot;America/Tijuana&quot;,&quot;America/Toronto&quot;,&quot;America/Tortola&quot;,&quot;America/Vancouver&quot;,&quot;America/Virgin&quot;,&quot;America/Whitehorse&quot;,&quot;America/Winnipeg&quot;,&quot;America/Yakutat&quot;,&quot;America/Yellowknife&quot;,&quot;Asia/Aden&quot;,&quot;Asia/Almaty&quot;,&quot;Asia/Amman&quot;,&quot;Asia/Anadyr&quot;,&quot;Asia/Aqtau&quot;,&quot;Asia/Aqtobe&quot;,&quot;Asia/Ashgabat&quot;,&quot;Asia/Ashkhabad&quot;,&quot;Asia/Baghdad&quot;,&quot;Asia/Bahrain&quot;,&quot;Asia/Baku&quot;,&quot;Asia/Bangkok&quot;,&quot;Asia/Beirut&quot;,&quot;Asia/Bishkek&quot;,&quot;Asia/Brunei&quot;,&quot;Asia/Calcutta&quot;,&quot;Asia/Choibalsan&quot;,&quot;Asia/Chongqing&quot;,&quot;Asia/Chungking&quot;,&quot;Asia/Colombo&quot;,&quot;Asia/Dacca&quot;,&quot;Asia/Damascus&quot;,&quot;Asia/Dhaka&quot;,&quot;Asia/Dili&quot;,&quot;Asia/Dubai&quot;,&quot;Asia/Dushanbe&quot;,&quot;Asia/Gaza&quot;,&quot;Asia/Harbin&quot;,&quot;Asia/Hong_Kong&quot;,&quot;Asia/Hovd&quot;,&quot;Asia/Irkutsk&quot;,&quot;Asia/Istanbul&quot;,&quot;Asia/Jakarta&quot;,&quot;Asia/Jayapura&quot;,&quot;Asia/Jerusalem&quot;,&quot;Asia/Kabul&quot;,&quot;Asia/Kamchatka&quot;,&quot;Asia/Karachi&quot;,&quot;Asia/Kashgar&quot;,&quot;Asia/Katmandu&quot;,&quot;Asia/Krasnoyarsk&quot;,&quot;Asia/Kuala_Lumpur&quot;,&quot;Asia/Kuching&quot;,&quot;Asia/Kuwait&quot;,&quot;Asia/Macao&quot;,&quot;Asia/Macau&quot;,&quot;Asia/Magadan&quot;,&quot;Asia/Makassar&quot;,&quot;Asia/Manila&quot;,&quot;Asia/Muscat&quot;,&quot;Asia/Nicosia&quot;,&quot;Asia/Novosibirsk&quot;,&quot;Asia/Omsk&quot;,&quot;Asia/Oral&quot;,&quot;Asia/Phnom_Penh&quot;,&quot;Asia/Pontianak&quot;,&quot;Asia/Pyongyang&quot;,&quot;Asia/Qatar&quot;,&quot;Asia/Qyzylorda&quot;,&quot;Asia/Rangoon&quot;,&quot;Asia/Riyadh&quot;,&quot;Asia/Riyadh87&quot;,&quot;Asia/Riyadh88&quot;,&quot;Asia/Riyadh89&quot;,&quot;Asia/Saigon&quot;,&quot;Asia/Sakhalin&quot;,&quot;Asia/Samarkand&quot;,&quot;Asia/Seoul&quot;,&quot;Asia/Shanghai&quot;,&quot;Asia/Singapore&quot;,&quot;Asia/Taipei&quot;,&quot;Asia/Tashkent&quot;,&quot;Asia/Tbilisi&quot;,&quot;Asia/Tehran&quot;,&quot;Asia/Tel_Aviv&quot;,&quot;Asia/Thimbu&quot;,&quot;Asia/Thimphu&quot;,&quot;Asia/Tokyo&quot;,&quot;Asia/Ujung_Pandang&quot;,&quot;Asia/Ulaanbaatar&quot;,&quot;Asia/Ulan_Bator&quot;,&quot;Asia/Urumqi&quot;,&quot;Asia/Vientiane&quot;,&quot;Asia/Vladivostok&quot;,&quot;Asia/Yakutsk&quot;,&quot;Asia/Yekaterinburg&quot;,&quot;Asia/Yerevan&quot;,&quot;Australia/ACT&quot;,&quot;Australia/Adelaide&quot;,&quot;Australia/Brisbane&quot;,&quot;Australia/Broken_Hill&quot;,&quot;Australia/Canberra&quot;,&quot;Australia/Currie&quot;,&quot;Australia/Darwin&quot;,&quot;Australia/Hobart&quot;,&quot;Australia/LHI&quot;,&quot;Australia/Lindeman&quot;,&quot;Australia/Lord_Howe&quot;,&quot;Australia/Melbourne&quot;,&quot;Australia/North&quot;,&quot;Australia/NSW&quot;,&quot;Australia/Perth&quot;,&quot;Australia/Queensland&quot;,&quot;Australia/South&quot;,&quot;Australia/Sydney&quot;,&quot;Australia/Tasmania&quot;,&quot;Australia/Victoria&quot;,&quot;Australia/West&quot;,&quot;Australia/Yancowinna&quot;,&quot;Brazil/Acre&quot;,&quot;Brazil/DeNoronha&quot;,&quot;Brazil/East&quot;,&quot;Brazil/West&quot;,&quot;Canada/Atlantic&quot;,&quot;Canada/Central&quot;,&quot;Canada/Eastern&quot;,&quot;Canada/East-Saskatchewan&quot;,&quot;Canada/Mountain&quot;,&quot;Canada/Newfoundland&quot;,&quot;Canada/Pacific&quot;,&quot;Canada/Saskatchewan&quot;,&quot;Canada/Yukon&quot;,&quot;Chile/Continental&quot;,&quot;Chile/EasterIsland&quot;,&quot;Etc/GMT&quot;,&quot;Etc/Greenwich&quot;,&quot;Etc/UCT&quot;,&quot;Etc/Universal&quot;,&quot;Etc/UTC&quot;,&quot;Etc/Zulu&quot;,&quot;Europe/Amsterdam&quot;,&quot;Europe/Andorra&quot;,&quot;Europe/Athens&quot;,&quot;Europe/Belfast&quot;,&quot;Europe/Belgrade&quot;,&quot;Europe/Berlin&quot;,&quot;Europe/Bratislava&quot;,&quot;Europe/Brussels&quot;,&quot;Europe/Bucharest&quot;,&quot;Europe/Budapest&quot;,&quot;Europe/Chisinau&quot;,&quot;Europe/Copenhagen&quot;,&quot;Europe/Dublin&quot;,&quot;Europe/Gibraltar&quot;,&quot;Europe/Guernsey&quot;,&quot;Europe/Helsinki&quot;,&quot;Europe/Isle_of_Man&quot;,&quot;Europe/Istanbul&quot;,&quot;Europe/Jersey&quot;,&quot;Europe/Kaliningrad&quot;,&quot;Europe/Kiev&quot;,&quot;Europe/Lisbon&quot;,&quot;Europe/Ljubljana&quot;,&quot;Europe/London&quot;,&quot;Europe/Luxembourg&quot;,&quot;Europe/Madrid&quot;,&quot;Europe/Malta&quot;,&quot;Europe/Mariehamn&quot;,&quot;Europe/Minsk&quot;,&quot;Europe/Monaco&quot;,&quot;Europe/Moscow&quot;,&quot;Europe/Nicosia&quot;,&quot;Europe/Oslo&quot;,&quot;Europe/Paris&quot;,&quot;Europe/Podgorica&quot;,&quot;Europe/Prague&quot;,&quot;Europe/Riga&quot;,&quot;Europe/Rome&quot;,&quot;Europe/Samara&quot;,&quot;Europe/San_Marino&quot;,&quot;Europe/Sarajevo&quot;,&quot;Europe/Simferopol&quot;,&quot;Europe/Skopje&quot;,&quot;Europe/Sofia&quot;,&quot;Europe/Stockholm&quot;,&quot;Europe/Tallinn&quot;,&quot;Europe/Tirane&quot;,&quot;Europe/Tiraspol&quot;,&quot;Europe/Uzhgorod&quot;,&quot;Europe/Vaduz&quot;,&quot;Europe/Vatican&quot;,&quot;Europe/Vienna&quot;,&quot;Europe/Vilnius&quot;,&quot;Europe/Volgograd&quot;,&quot;Europe/Warsaw&quot;,&quot;Europe/Zagreb&quot;,&quot;Europe/Zaporozhye&quot;,&quot;Europe/Zurich&quot;,&quot;GB&quot;,&quot;GB-Eire&quot;,&quot;GMT&quot;,&quot;GMT0&quot;,&quot;GMT-0&quot;,&quot;GMT+0&quot;,&quot;Greenwich&quot;,&quot;Hongkong&quot;,&quot;Iceland&quot;,&quot;Iran&quot;,&quot;Israel&quot;,&quot;Jamaica&quot;,&quot;Japan&quot;,&quot;Kwajalein&quot;,&quot;Libya&quot;,&quot;Mexico/BajaNorte&quot;,&quot;Mexico/BajaSur&quot;,&quot;Mexico/General&quot;,&quot;Pacific/Apia&quot;,&quot;Pacific/Auckland&quot;,&quot;Pacific/Chatham&quot;,&quot;Pacific/Easter&quot;,&quot;Pacific/Efate&quot;,&quot;Pacific/Enderbury&quot;,&quot;Pacific/Fakaofo&quot;,&quot;Pacific/Fiji&quot;,&quot;Pacific/Funafuti&quot;,&quot;Pacific/Galapagos&quot;,&quot;Pacific/Gambier&quot;,&quot;Pacific/Guadalcanal&quot;,&quot;Pacific/Guam&quot;,&quot;Pacific/Honolulu&quot;,&quot;Pacific/Johnston&quot;,&quot;Pacific/Kiritimati&quot;,&quot;Pacific/Kosrae&quot;,&quot;Pacific/Kwajalein&quot;,&quot;Pacific/Majuro&quot;,&quot;Pacific/Marquesas&quot;,&quot;Pacific/Midway&quot;,&quot;Pacific/Nauru&quot;,&quot;Pacific/Niue&quot;,&quot;Pacific/Norfolk&quot;,&quot;Pacific/Noumea&quot;,&quot;Pacific/Pago_Pago&quot;,&quot;Pacific/Palau&quot;,&quot;Pacific/Pitcairn&quot;,&quot;Pacific/Ponape&quot;,&quot;Pacific/Port_Moresby&quot;,&quot;Pacific/Rarotonga&quot;,&quot;Pacific/Saipan&quot;,&quot;Pacific/Samoa&quot;,&quot;Pacific/Tahiti&quot;,&quot;Pacific/Tarawa&quot;,&quot;Pacific/Tongatapu&quot;,&quot;Pacific/Truk&quot;,&quot;Pacific/Wake&quot;,&quot;Pacific/Wallis&quot;,&quot;Pacific/Yap&quot;,&quot;Poland&quot;,&quot;Portugal&quot;,&quot;Singapore&quot;,&quot;US/Alaska&quot;,&quot;US/Aleutian&quot;,&quot;US/Arizona&quot;,&quot;US/Central&quot;,&quot;US/Eastern&quot;,&quot;US/East-Indiana&quot;,&quot;US/Hawaii&quot;,&quot;US/Indiana-Starke&quot;,&quot;US/Michigan&quot;,&quot;US/Mountain&quot;,&quot;US/Pacific&quot;,&quot;US/Samoa&quot;,&quot;UTC&quot;,&quot;WET&quot;,&quot;Zulu&quot;}">
        <Label>Timezone:</Label>
        <Description/>
      </Property>
    </ProductSection>
  </VirtualSystem>
</Envelope>
EOF

  LINE=$( grep -n "</VirtualHardwareSection>" ${OVF}.tmp | cut -f1 -d: )
  HEAD=$(( LINE-1 ))
  TAIL=$(( LINE+0 ))
  cat ${OVF}.tmp | head -n ${HEAD} > ${OVF}
  cat >> ${OVF} <<EOF
      <Item>
        <rasd:AddressOnParent>1</rasd:AddressOnParent>
        <rasd:AutomaticAllocation>true</rasd:AutomaticAllocation>
        <rasd:ElementName>CD/DVD Drive 1</rasd:ElementName>
        <rasd:InstanceID>8</rasd:InstanceID>
        <rasd:Parent>3</rasd:Parent>
        <rasd:ResourceType>15</rasd:ResourceType>
      </Item>
EOF

  cat ${OVF}.tmp | tail -n +${TAIL} >> ${OVF}
  rm ${OVF}.tmp
}

$1 "$@"
