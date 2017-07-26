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

[ -z "$( pidof udevd )" ] && udevd &

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
elif [ $FileVersion == "7.03.92" ] && [ ! -f $FileBoot.bak ]; then
  echo "Modifying to fix on container builds for $FileKIWIBoot version $patch"
  linenum=$( grep -nr "\$loaderTarget = \$diskname;" $FileBoot | cut -d ':' -f 1 )
  if [ "$linenum" != "" ]; then
    linerem=$(( ${linenum}-1 ))
    cp $FileBoot $FileBoot.bak
    sed -i "${linenum}i\            } elsif (\$chainload) {" $FileBoot
    sed -i "${linenum}i\                \$targetMessage= \"On disk partition\";" $FileBoot
    sed -i "${linenum}i\                \$grubtoolopts.= \"--root-directory=\$mount --force --no-nvram \";" $FileBoot
    sed -i "${linenum}i\                \$grubtoolopts.= \"-d \$stages \";" $FileBoot
    sed -i "${linenum}i\                \$grubtoolopts = \"--grub-mkdevicemap=\$dmfile \";" $FileBoot
    sed -i "${linenum}i\                \$grubtool = \$locator -> getExecPath ('grub2-install');" $FileBoot
    sed -i "${linenum}i\                \$loaderTarget = \$this->{loop};" $FileBoot
    sed -i "${linenum}i\            if (\$result == 0) {" $FileBoot
    sed -i "${linenum}i\            \$result = \$? >> 8;" $FileBoot
    sed -i "${linenum}i\            \$status = KIWIQX::qxx ( \"ls /.dockerinit &>/dev/null\" );" $FileBoot
    sed -i "${linenum}i\            # Fix /usr/sbin/grub2-bios-setup: error: failed to get canonical path" $FileBoot
    sed -i "${linerem}d" $FileBoot
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
