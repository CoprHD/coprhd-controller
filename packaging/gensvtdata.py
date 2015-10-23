#!/usr/bin/python

import os,sys

workingDir=sys.argv[1]+'/'
version=sys.argv[2]
urlRoot="http://lglaf020.lss.emc.com/ovf/ViPR/release/appliance/"

#
# Required SVT data {file name, file extension, file size, file MD5, file url}
# Applies to all files in the vipr-version.MD5SUMS file except 1+0
#
with open(workingDir+version+".MD5SUMS", 'r') as f:
    for row in f:
        if "1+0" in row:
            continue
        md5, name = row.split()
        ext = os.path.splitext(workingDir+name)[1].split('.')[1]
        size = os.path.getsize(workingDir+name) >> 20
        url = urlRoot+version+"/"+name
        print name,ext,size,md5,url

#
# convert vipr.md file to required format for SVT (ex: upgradeFromVersions=verion1;version2)
#
with open(workingDir+"vipr.md", 'r') as f:
    versions=next(f)
    print(versions.replace('upgrade_from','upgradeFromVersions').replace(',',';').replace(':','='))
