import os

workingDir="/workspace/coprhd-controller/build/OVAS/"
version="vipr-2.4.0.0.57e0cb1"
urlRoot="http://lglaf020.lss.emc.com/ovf/ViPR/release/appliance/"

with open(workingDir+version+".MD5SUMS", 'r') as f:
    for row in f:
        if "1+0" in row:
            continue
        md5, name = row.split()
        ext = os.path.splitext(workingDir+name)[1].split('.')[1]
        size = os.path.getsize(workingDir+name) >> 20
        url = urlRoot+version+"/"+name
        print name,ext,size,md5,url

with open(workingDir+"vipr.md", 'r') as f:
    versions=next(f)
    print(versions.replace('upgrade_from','upgradeFromVersions').replace(',',';').replace(':','='))
