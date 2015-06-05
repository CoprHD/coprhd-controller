# Copyright 2015 EMC Corporation
# All Rights Reserved

#
# Updates the local instance to the copy of a remote one
#
DIR=`dirname $0`

# Default to the Downloads directory, allowing it to be overriden in setEnv.sh
DOWNLOAD_DIR=~/Downloads

. ${DIR}/setEnv.sh

FETCH=true

#
# USAGE : Normally the file will be backed and fetched, but by specifying the --nofetch option, an existing
# /$DOWNLOAD_DIR/storageos.tar.gz file will be used instead
#

case $1 in
--nofetch)
    FETCH=false
    ;;
esac

echo SERVER=$OVF_SERVER
echo DOWNLOAD_DIR=$DOWNLOAD_DIR
echo INSTALL_HOME=$INSTALL_HOME

# Make sure target directory does not exist, is empty, or a previous installation
# (to stop potentially deleting something like the users HOME directory!
if [ -e $INSTALL_HOME ] ; then
    if [ ! -z "$(ls  $INSTALL_HOME)" ] && [ ! -e "$INSTALL_HOME/storageos" ]; then
        echo
        echo "ERROR : INSTALL_HOME directory is not empty, and doesn't contain storageos files"
        echo
        exit 2
    fi
fi

if $FETCH ; then
    echo
    echo "** Packing Up Remote Server $OVF_SERVER"
    echo NOTE : When prompted for password, enter ChangeMe

    ssh root@$OVF_SERVER 'while [ -f /opt/tarlock ]; do
                          	echo "Another client is currently taring storageos... waiting"
                          	sleep 5
                          done;

                          if [ ! -f /opt/storageos.tar.gz ] ; then
                          	touch /opt/tarlock
                          	/etc/systool --list > /opt/storageos/version.txt;
                          	cp /etc/ovf-env.properties /opt/storageos;
                          	cp /etc/nginx/locations.conf /opt/storageos;
                          	cd /opt;
                          	echo "Please Wait...Creating Tar....";
                          	tar zcf /opt/storageos.tar.gz storageos --exclude **/*.log*
                          	rm /opt/tarlock
                          else
                          	echo "Tar already exist"

                           fi'
    sshError=$?
    if [ $sshError == 255 ]; then 
        echo
        echo "SSH to Remote Server $OVF_SERVER failed. Are you connected to the EMC VPN?"
        echo
        exit 3
    fi
    
    echo

    echo
    echo "** Fetching Tar file from Server $OVF_SERVER"
    echo "NOTE : When prompted for password, enter ChangeMe (yes, again!)"
    scp root@$OVF_SERVER:/opt/storageos.tar.gz $DOWNLOAD_DIR

else
    if [ ! -f $DOWNLOAD_DIR/storageos.tar.gz ]; then
        echo "--nofetch specified, but no $DOWNLOAD_DIR/storageos.tar.gz file found to unpack!"
    fi
fi

echo
echo "** Removing current contents from $INSTALL_HOME"
if [ -h $INSTALL_HOME ]; then
    # INSTALL_HOME is a symbolic link, just remove the link
    rm $INSTALL_HOME
else
    # Recursively delete INSTALL_HOME
    rm -rf $INSTALL_HOME
fi
mkdir -p $INSTALL_HOME

echo
echo "** Unpacking Server File"
tar xf $DOWNLOAD_DIR/storageos.tar.gz -C $INSTALL_HOME

# On the VM the apisvc is executed as the StorageOS user (444) and it's home directory set to /opt/storageos
# We don't have this so we just copy it to our own home directory
echo "** Copying .keystore file to home directory"
echo "** Copying /etc/systool"

if [ "$(md5 -q $RUNTIME_ROOT/bin/systool )" != "$(md5 -q /etc/systool)" ]; then
  sudo cp $RUNTIME_ROOT/bin/systool /etc/systool
  sudo chown root /etc/systool
  sudo chmod 755 /etc/systool
fi

# On the VM the apisvc is executed as the StorageOS user (444) and it's home directory set to /opt/storageos
# We don't have this so we just copy it to our own home directory
echo "** Copying .keystore file to home directory"
if [ ! -d ~/conf ] ; then
    mkdir ~/conf
fi
cp $INSTALL_HOME/storageos/conf/keystore ~/conf/keystore

echo "** Converting scripts"
${DIR}/convert.sh

echo "Setting up NGINX"

sudo cp $INSTALL_HOME/storageos/locations.conf /usr/local/etc/nginx
sudo nginx -s stop
sudo nginx

echo
echo Update Complete!
