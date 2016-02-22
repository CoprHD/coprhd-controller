#!/bin/bash      
#title           :simulatorInstall.sh
#description     :This script will downloadsimulators from share and configure the vm accordingly.
#author			 :Jean Pierre
#date            :09-22-2015
#usage		 	 :bash simulatorInstall.sh
#==============================================================================


#Verify zips has proper configs

AIO_SCRIPTS_LOCATION=$1/AIO_scripts
INIT_SCRIPTS_LOCATION=$AIO_SCRIPTS_LOCATION/intitScripts/*
UNIT_FILES_LOCATION=$AIO_SCRIPTS_LOCATION/unit_files/*
UPDATE_SCRIPTS_LOCATION=$AIO_SCRIPTS_LOCATION/bin/*
DB_FILE_LOCATION=$AIO_SCRIPTS_LOCATION/dbFiles/*

SIMULATOR_BUILD_LOCATION="https://build.coprhd.org/jenkins/userContent/simulators/"


#####################################################################
# Install intit scritps                                             #
#####################################################################

echo "Installing intit scritps"


INIT_SCRIPTS_FOLDER=/etc/init.d/
chmod -R 777 $INIT_SCRIPTS_LOCATION
cp $INIT_SCRIPTS_LOCATION $INIT_SCRIPTS_FOLDER || exit 1

#####################################################################
# Install UNIT files for systemd                                    #
#####################################################################

echo "Installing UNIT files for systemd"

UNIT_FILES_FOLDER=/usr/lib/systemd/system
for file in `/bin/ls $UNIT_FILES_LOCATION`; do
	fileName=${file##*/}
	cp $file $UNIT_FILES_FOLDER/$fileName || exit 1
	chmod 777 $UNIT_FILES_FOLDER/$fileName
	/usr/bin/systemctl enable ${fileName%.*} || exit 1
done

sed -i "s/#DefaultTimeoutStartSec=90s/DefaultTimeoutStartSec=180s/" /etc/systemd/system.conf 

/usr/bin/systemctl daemon-reload

#####################################################################
# Install Simulators binaries                                       #
#####################################################################

echo "Installing Simulators binaries"


					#############################
					# CISCO                     #
					#############################
echo "Installing CISCO"


LATEST_URL=$SIMULATOR_BUILD_LOCATION"cisco-sim/cisco-sim-1.0.0.0.1453093200.zip"
echo "Downloading $LATEST_URL"
wget $LATEST_URL -O cisco-sim.zip || exit 1

unzip cisco-sim.zip
chmod -R 777 cisco-sim
mkdir -p /data/simulators/cisco-sim/
cp -r cisco-sim/* /data/simulators/cisco-sim/
cp $DB_FILE_LOCATION /data/simulators/cisco-sim/db/
ln -s /data/simulators/cisco-sim/ /cisco-sim 
rm -r cisco-sim
rm  cisco-sim*.zip


					#############################
					# ECOM                      #
					#############################
echo "Installing ECOM"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"smis-sim/smis-sim-1.0.0.0.1453438800.zip"
echo "Downloading $LATEST_URL"
wget $LATEST_URL -O smis-simulator.zip || exit 1

unzip smis-simulator.zip
mkdir -p /data/simulators/ecom80/
mkdir -p /data/simulators/ecom462/
cp -r ecom/* /data/simulators/ecom80/
cp -r ecom/* /data/simulators/ecom462/
rm -r ecom
rm smis-simulator.zip

cat > /data/simulators/ecom80/bin/ECOM80 <<EOF
#!/bin/bash

trap 'kill -TERM $PID' TERM INT
/data/simulators/ecom80/bin/ECOM &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EOF
chmod 777 /data/simulators/ecom80/bin/ECOM80

cat > /data/simulators/ecom462/bin/ECOM462 <<EOF
#!/bin/bash

trap 'kill -TERM $PID' TERM INT
/data/simulators/ecom462/bin/ECOM &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EOF
chmod 777 /data/simulators/ecom462/bin/ECOM462
					#############################
					# HDS                       #
					#############################
echo "Installing HDS"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"hds-sim/hds-sim-1.0.0.0.1438920000.zip" 
echo "Downloading $LATEST_URL"
wget $LATEST_URL -O hds-sim.zip || exit 1

unzip hds-sim.zip -d hds-sim
mkdir -p /data/simulators/hds-sim/
cp -r hds-sim/* /data/simulators/hds-sim/
rm -r hds-sim
rm hds-sim.zip

					#############################
					# LDAP                      #
					#############################

echo "Installing LDAP"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"ldap-sim/ldap-simulators-1.0.0.0.1-bin.zip" 
echo "Downloading $LATEST_URL"
wget $LATEST_URL -O ldap-sim.zip || exit 1

unzip ldap-sim.zip
mkdir -p /data/simulators/ldap-sim/
cp -r ldap-simulators-*/* /data/simulators/ldap-sim/
rm -r ldap*

cat > /data/simulators/ldap-sim/bin/run.sh <<EOF

rm -rf build

./ldap &
DELAY=60
while [ \$DELAY -gt 0 ]; do
    if [ -f "build/logs/ldapsvc.log" ] ; then
            START=\`grep "Started LDAPServiceApplication" build/logs/ldapsvc.log\`
        if [ "\${START}" != "" ] ; then
             curl -X POST -H "Content-Type: application/json" -d "{\"listener_name\": \"COPRHDLDAPSanity\"}" http://0.0.0.0:8082/ldap-service/start
        exit
        fi
        fi
        sleep 1
        DELAY=\$((DELAY -1))
    done
EOF

chmod 777 /data/simulators/ldap-sim/bin/run.sh

					#############################
					# RP                        #
					#############################
echo "Installing RP"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"rp-sim/rp-simulators-1.0.0.0.29-bin.zip" 
echo "Downloading $LATEST_URL"
wget $LATEST_URL || exit 1

unzip -j rp-simulators-*.zip -d rp-sim
mkdir -p /data/simulators/rp-sim/
cp -r rp-*/* /data/simulators/rp-sim/
sed -i "s/\(^\|#\)*NUM_CLUSTERS=.*/NUM_CLUSTERS=2/" /data/simulators/rp-sim/rp_config.properties

rm -r rp-*
rm -f rp-simulators-*.zip

					#############################
					# VPLEX                     #
					#############################
echo "Installing VPLEX"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"vplex-sim/vplex-simulators-1.0.0.0.41-bin.zip" 
VERSION=${LATEST_URL##*simulators-}
VERSION=${VERSION%%-*}
echo "Downloading $LATEST_URL"
wget $LATEST_URL || exit 1

unzip -j vplex-simulators-*.zip -d vplex-sim
mkdir -p /data/simulators/vplex-sim/
mkdir -p /data/simulators/vplex-sim_2/
cp -r vplex-*/* /data/simulators/vplex-sim/
cp -r vplex-*/* /data/simulators/vplex-sim_2/
mv /data/simulators/vplex-sim_2/vplex-simulators-*.jar /data/simulators/vplex-sim_2/vplex-simulators2-$VERSION.jar
./bin/setupSim --setup-default
rm -r vplex-*
rm -f vplex-simulators-*.zip

					#############################
					# WINDOWS                   #
					#############################
echo "Installing WINDOWS"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"win-sim/win-sim-1.0.0.0.1442808000.zip" 
echo "Downloading $LATEST_URL"
wget $LATEST_URL -O win-sim.zip || exit 1

unzip win-sim.zip
mkdir -p /data/simulators/win-sim/
cp -r win-sim/* /data/simulators/win-sim/
rm -r win-sim
rm  win-sim.zip

					#############################
					# XIO                       #
					#############################
echo "Installing XIO"

LATEST_URL=$SIMULATOR_BUILD_LOCATION"xio-sim/xio-simulators-1.0.0.0.35-bin.zip" 
VERSION=${LATEST_URL##*simulators-}
VERSION=${VERSION%%-*}
echo "Downloading $LATEST_URL"
wget $LATEST_URL || exit 1

unzip -j xio-simulators-*.zip -d xio-sim
mkdir -p /data/simulators/xio/
mkdir -p /data/simulators/xio_2/
cp -r xio-*/* /data/simulators/xio/
cp -r xio-*/* /data/simulators/xio_2/
mv /data/simulators/xio_2/xio-simulators-*.jar /data/simulators/xio_2/xio-simulators2-$VERSION 
rm -r xio-*
rm -f xio-simulators-*.zip

					#############################
					# VM                       #
					#############################
echo "Installing XIO"

LATEST_URL="http://lglw8129.lss.emc.com/simulators/vmware/vm_sim.zip" 
echo "Downloading $LATEST_URL"
wget $LATEST_URL || exit 1

unzip -j vm_sim.zip -d vm-sim
mkdir -p /data/simulators/vm/
cp -r vm-*/* /data/simulators/xio/
rm -r vm-*
rm -f vm_sim.zip

#####################################################################
# Install update scripts                                            #
#####################################################################

echo "Installing update scripts"

UPDATE_SCRIPTS_FOLDER=/usr/local/bin/
chmod -R 777 $UPDATE_SCRIPTS_LOCATION
cp $UPDATE_SCRIPTS_LOCATION $UPDATE_SCRIPTS_FOLDER || exit 1

#####################################################################
# Starting simulators                                               #
#####################################################################

echo "Starting services please be patient..."
