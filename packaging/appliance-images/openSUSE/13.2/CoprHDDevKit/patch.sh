#!/bin/bash
#
# Copyright 2015 EMC Corporation
# All Rights Reserved
#

#######################################################
# KIWI Modifications
#######################################################
kiwiPath="/usr/share/kiwi/modules"

#######################################################
# Skip this configuration if KIWI is not installed
#######################################################
rpm -q --quiet kiwi
if [ $? -ne 0 ]; then
  exit 0
fi

#######################################################
# Changes for generic bootloader installation
#######################################################
# Generic grub probing: https://groups.google.com/d/msg/kiwi-images/vPnpDHN-B9I/ykeVFuKM8E4J
FileVersion=$( rpm -q kiwi --queryformat "%{RPMTAG_VERSION}" )
FileBoot="$kiwiPath/KIWIBoot.pm"
if [ $FileVersion == "7.01.16" ] && [ ! -f $FileBoot.bak ]; then
  echo "Patching KIWI bootloader container installations"
  line=$( grep -nr "\$loaderTarget = \$diskname;" $FileBoot | cut -d ':' -f 1 )
  if [ ! -z "$line" ]; then
    cp $FileBoot $FileBoot.bak
    sed -i "$((line+0)),$((line+2))d" $FileBoot
    sed -i "${line}i\                \$targetMessage= \"On disk target\";" $FileBoot
    sed -i "${line}i\                \$grubtoolopts.= \"--root-directory=/mnt --force --no-nvram \";" $FileBoot
    sed -i "${line}i\                \$grubtoolopts.= \"-d \$stages \";" $FileBoot
    sed -i "${line}i\                \$grubtoolopts = \"--grub-mkdevicemap=\$dmfile \";" $FileBoot
    sed -i "${line}i\                \$grubtool = \$locator -> getExecPath ('grub2-install');" $FileBoot
    sed -i "${line}i\                \$loaderTarget = \$this->{loop};" $FileBoot
    sed -i "${line}i\                # KIWIBoot.pm bootloader generic patch" $FileBoot
  fi
fi

#######################################################
# Changes for 3 digits to 5 digits version
#######################################################
FileValidator="$kiwiPath/KIWIXMLValidator.pm"
FilePreference="$kiwiPath/KIWIXMLPreferenceData.pm"
# Verify first if 3 digits mod is needed again
if [ -f $FileValidator ] && [ -f $FileValidator.bak ]; then
  grep --quiet '\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+' $FileValidator
  if [ $? -ne 0 ]; then
    rm $FileValidator.bak
  fi
fi
if [ -f $FilePreference ] && [ -f $FilePreference.bak ]; then
  grep --quiet '\\d+?\\.\\d+?\\.\\d+?\\.\\d+?\\.\\d+?' $FilePreference
  if [ $? -ne 0 ]; then
    rm $FilePreference.bak
  fi
fi
# Apply modifications when needed
if [ -f $FileValidator ] && [ ! -f $FileValidator.bak ]; then
 echo "Modifying to fix version check for $FileValidator"
 sed -i."bak" 's/\($version !~ \/^\\d+\\.\\d+\\.\\d+$\/\)/\($version !~ \/^\\d+\\.\\d+\\.\\d+$\/\) \&\& \($version !~ \/^\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+$\/\)/g' $FileValidator
fi
if [ -f $FilePreference ] && [ ! -f $FilePreference.bak ]; then
 echo "Modifying to fix version check for $FilePreference"
 sed -i."bak" 's/\( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?$\/smx \)/\( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?$\/smx \) \&\& \( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?\\.\\d+?\\.\\d+?$\/smx \)/g' $FilePreference
fi
