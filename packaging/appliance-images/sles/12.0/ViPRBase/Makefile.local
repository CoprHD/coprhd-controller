RUN_DIR=`pwd`
NAME=ViPRBase
VERSION=4.0.0.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
ICAF_DIR=/opt/ADG/ICAF
ICAF_TOOL=/usr/bin/perl ${ICAF_DIR}/bin/icaf.pl
JOB_DIR=${ICAF_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
SIMULATORS_BRANCH=master
DEBUG=d

all: prepare appliance
appliance: prepare copySimulatorCode createAppliance
prepare: clean 

createAppliance:
	cat ${RUN_DIR}/../configurations.sh > config.sh
	cat ViPRBase_config.sh >> config.sh
	udevd -d &
	${ICAF_TOOL} ${CONF} ${JOB} -l -c -p appliance

copySimulatorCode:
	rm -rf root/aio-simulators
	mkdir -p root/aio-simulators
	git clone https://review.coprhd.org/scm/ce/aio-simulators.git -b ${SIMULATORS_BRANCH}
	cp -pr aio-simulators/openSUSE/13.2/templates/root/AIO_scripts root/aio-simulators
	cp -p simulatorInstall.sh root/aio-simulators/AIO_scripts/.
	chmod 755 root/aio-simulators/AIO_scripts/simulatorInstall.sh
	rm -rf aio-simulators
	mkdir -p root/aio-simulators/binaries
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/cisco-sim/*.zip root/aio-simulators/binaries/cisco-sim.zip
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/smis-sim/*.zip root/aio-simulators/binaries/smis-simulator.zip
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/hds-sim/*.zip  root/aio-simulators/binaries/hds-sim.zip
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/ldap-sim/*.zip  root/aio-simulators/binaries/ldap-sim.zip
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/rp-sim/*.zip root/aio-simulators/binaries/.
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/vplex-sim/*.zip  root/aio-simulators/binaries/.
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/win-sim/*.zip  root/aio-simulators/binaries/win-sim.zip
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/xio-sim/*.zip  root/aio-simulators/binaries/.
	sshpass -p 'dangerous' scp -o 'StrictHostKeychecking=no' root@10.247.98.129:/var/www/simulators/latest/vmware-sim/*.zip  root/aio-simulators/binaries/.
	mkdir -p root/etc/init.d
	cp boot.InstallSimulator root/etc/init.d/.
	chmod 755 root/etc/init.d/boot.InstallSimulator

bless:
	${ICAF_TOOL} ${CONF} ${JOB} -b

clean:
	rm -rf config.sh ViPRBase.list aio-simulators root/etc root/aio-simulators
	rm -rf ${JOB_DIR}

destroy: clean
	rm -rf ${OUTPUT_DIR}

