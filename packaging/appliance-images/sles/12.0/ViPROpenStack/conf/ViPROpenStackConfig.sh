#!/bin/bash
#
#-%emc-cr-s-shell-v2%-
#
# Copyright (c) 2016, EMC Corporation. All Rights Reserved.
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.
# This software is protected, without limitation, by copyright law and
# international treaties.
# Use of this software and the intellectual property contained therein
# is expressly limited to the terms and conditions of the License
# Agreement under which it is provided by or on behalf of EMC.
#
#-%emc-cr-e-shell-v2%-
#
# Created 02/09/2016: Amulya Lokesha
#

######################################################
# Variable declaration
######################################################
# Executables
mysqlExec="/usr/bin/mysql"
opensslExec="/usr/bin/openssl"
sedExec="/usr/bin/sed"
echoExec="/usr/bin/echo"
shExec="/bin/sh"
suExec="/usr/bin/su"
catExec="/usr/bin/cat"
cpExec="/usr/bin/cp"
a2enmodExec="/usr/sbin/a2enmod"
serviceExec="/sbin/service"
chownExec="/usr/bin/chown"
chmodExec="/usr/bin/chmod"
openstackExec="/usr/bin/openstack"
rabbitmqExec="/usr/sbin/rabbitmqctl"

# Files
logFile="/opt/ADG/firstboot/logs/ViPROpenStackConfig.log"
keystoneDir="/etc/keystone"
keystoneConfFile="$keystoneDir/keystone.conf"
hostsFile="/etc/hosts"
apache2ConfFile="/etc/sysconfig/apache2"
cinderModifiedFile="/opt/ADG/conf/cinder.conf"
cinderConfFile="/etc/cinder/cinder.conf"
adminOpenrcFile="/root/admin-openrc.sh"

# credentials
mysqlUser="root"
mysqlPasswd="password"
keystoneDB="keystone"
keystoneUser="keystone"
keystonePasswd="password"
cinderDB="cinder"
cinderUser="cinder"
cinderPasswd="password"

hostName="controller"


# Message Log
LogMessage()
{
  prefix=$1
  message=$2
  fullTime=`date +%T`

  $echoExec "$fullTime [$prefix]: $message" >> $logFile
  if [ $prefix = "INFO" -o $prefix = "ERROR" ]; then
    $echoExec "$fullTime [$prefix]: $message"
  fi
}

#######################################################
# Configure Firewall ports
#######################################################

$sedExec -i 's/FW_SERVICES_EXT_TCP=".*"/FW_SERVICES_EXT_TCP="ssh 8776 5000 35357"/' /etc/sysconfig/SuSEfirewall2
$serviceExec SuSEfirewall2 restart
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start SuSEfirewall2 service"
fi

######################################################
# Start the required services
######################################################
LogMessage "INFO" "Starting the mysql service"
$serviceExec mysql start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start mysql service"
fi

LogMessage "INFO" "Starting the apache2 service"
$serviceExec apache2 start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start apache2 service"
fi

LogMessage "INFO" "Starting the rabbitmq-server service"
$serviceExec rabbitmq-server start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start rabbitmq-server service"
fi

LogMessage "INFO" "Starting the memcached service"
$serviceExec memcached start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start memcached service"
fi

######################################################
# Secure MySql - mysql_secure_installation
######################################################
LogMessage "INFO" "Executing mysql_secure_installation"

$mysqlExec -u $mysqlUser << EOF
UPDATE mysql.user SET Password=PASSWORD('$mysqlPasswd') WHERE User='$mysqlUser';
DELETE FROM mysql.user WHERE User='$mysqlUser' AND Host NOT IN ('localhost', '127.0.0.1', '::1');
DELETE FROM mysql.user WHERE User='';
DELETE FROM mysql.db WHERE Db='test' OR Db='test\_%';
FLUSH PRIVILEGES;
EOF
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "mysql_secure_installation failed"
fi

######################################################
# Install And Configure Keystone
######################################################
# Pre-Requisites
LogMessage "INFO" "Installing And Configuring Keystone"
$mysqlExec -u $mysqlUser -p"$mysqlPasswd" << EOF
CREATE DATABASE $keystoneDB;
GRANT ALL PRIVILEGES ON $keystoneDB.* TO '$keystoneUser'@'localhost' IDENTIFIED BY '$keystonePasswd';
GRANT ALL PRIVILEGES ON $keystoneDB.* TO '$keystoneUser'@'%' IDENTIFIED BY '$keystonePasswd';
exit
EOF
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Keystone configuration failed"
fi

######################################################
# Edit the /etc/keystone/keystone.conf 
######################################################
# Generate Random Token
LogMessage "INFO" "Generating random token number"
randNumber=$($opensslExec rand -hex 10);

LogMessage "INFO" "Modifying the keystone.conf file"
$sedExec -i '/^\[DEFAULT\]/,/^\[assignment\]/{s/\#admin_token = ADMIN/admin_token = '$randNumber'/}' $keystoneConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to update the random token number in keystone.conf"
fi

# Configure database access
$sedExec -i "/^\[database\]/,/^\[domain_config\]/{s/connection = sqlite\:\/\/\/\/var\/lib\/keystone\/keystone.db/connection = mysql\:\/\/$keystoneUser\:$keystonePasswd\@$hostName\/keystone/}" $keystoneConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure the database access in keystone.conf"
fi

#Configure the Memcached service:
$sedExec -i "/^\[memcache\]/,/^\[oauth1\]/{s/\#servers = localhost\:11211/servers = localhost\:11211/}" $keystoneConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure the memcached service in keystone.conf"
fi

#Configure the UUID token provider and Memcached driver:
$sedExec -i "/^\[token\]/,/^\[tokenless_auth\]/{s/\#provider = uuid/provider = uuid/}" $keystoneConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure the UUID token provider in keystone.conf"
fi

$sedExec -i "/^\[token\]/,/^\[tokenless_auth\]/{s/\#driver = sql/driver = memcache/}" $keystoneConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure the Memcached driver in keystone.conf"
fi

# configure the SQL revocation driver:
$sedExec -i "/^\[revoke\]/,/^\[role\]/{s/\#driver = sql/driver = sql/}" $keystoneConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure the SQL revocation driver in keystone.conf"
fi

######################################################
# Populate the Identity service database
######################################################
LogMessage "INFO" "Populating the Identity service database"
$echoExec "127.0.0.1	      $hostName" >> $hostsFile

$suExec -s $shExec -c "keystone-manage db_sync" keystone
if [ $? -eq 0 ]; then
  LogMessage "INFO" "Keystone db-sync successful"
else
  LogMessage "ERROR" "Keystone db-sync failed ..."
  exit 1
fi

#######################################################
#Configure Apache HTTP server
#######################################################
LogMessage "INFO" "Configuring Apache HTTP server"
$sedExec -i 's/APACHE_SERVERNAME=""/APACHE_SERVERNAME="$hostName"/' $apache2ConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure the Apache HTTP server name"
fi

$catExec > /etc/apache2/conf.d/wsgi-keystone.conf <<EOF
Listen 5000
Listen 35357

<VirtualHost *:5000>
    WSGIDaemonProcess keystone-public processes=5 threads=1 user=$keystoneUser group=keystone display-name=%{GROUP}
    WSGIProcessGroup keystone-public
    WSGIScriptAlias / /usr/bin/keystone-wsgi-public
    WSGIApplicationGroup %{GLOBAL}
    WSGIPassAuthorization On
    <IfVersion >= 2.4>
      ErrorLogFormat "%{cu}t %M"
    </IfVersion>
    ErrorLog /var/log/apache2/keystone.log
    CustomLog /var/log/apache2/keystone_access.log combined

    <Directory /usr/bin>
        <IfVersion >= 2.4>
            Require all granted
        </IfVersion>
        <IfVersion < 2.4>
            Order allow,deny
            Allow from all
        </IfVersion>
    </Directory>
</VirtualHost>

<VirtualHost *:35357>
    WSGIDaemonProcess keystone-admin processes=5 threads=1 user=$keystoneUser group=keystone display-name=%{GROUP}
    WSGIProcessGroup keystone-admin
    WSGIScriptAlias / /usr/bin/keystone-wsgi-admin
    WSGIApplicationGroup %{GLOBAL}
    WSGIPassAuthorization On
    <IfVersion >= 2.4>
      ErrorLogFormat "%{cu}t %M"
    </IfVersion>
    ErrorLog /var/log/apache2/keystone.log
    CustomLog /var/log/apache2/keystone_access.log combined

    <Directory /usr/bin>
        <IfVersion >= 2.4>
            Require all granted
        </IfVersion>
        <IfVersion < 2.4>
            Order allow,deny
            Allow from all
        </IfVersion>
    </Directory>
</VirtualHost>
EOF

#################################################################
# Recursively change the ownership of the /etc/keystone directory
#################################################################
LogMessage "INFO" "Recursively changing the ownership of the $keystoneDir directory"
$chownExec -R keystone:keystone $keystoneDir
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to change the ownership of $keystoneDir directory"
fi

#################################################################
# Activate the Apache module mod_version
#################################################################
LogMessage "INFO" "Activating the Apache module mod_version"
$a2enmodExec version
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to activate the Apache module mod_version"
fi

LogMessage "INFO" "Restarting the apache2 service"
$serviceExec apache2 reload
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to restart the apache2 service"
fi

#################################################################
#Create the Identity service entity and API endpoints
#################################################################
LogMessage "INFO" "Creating the Identity service entity and API endpoints"
export OS_TOKEN=$randNumber
export OS_URL=http://$hostName:35357/v2.0
export OS_IDENTITY_API_VERSION=2

#################################################################
# Create the service entity and API endpoints for keystone
#################################################################
LogMessage "INFO" "Creating the service entity and API endpoints for keystone"
$openstackExec service create --name keystone --description "OpenStack Identity" identity
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the service entity for keystone"
  exit 1
fi

$openstackExec endpoint create --region RegionOne --publicurl http://$hostName:5000/v2.0  --internalurl http://$hostName:5000/v2.0 --adminurl http://$hostName:35357/v2.0 identity
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the API endpoints for keystone"
  exit 1
fi

#################################################################
# Create Projects, Users, and roles
#################################################################
LogMessage "INFO" "Create Projects, Users, and roles"
$openstackExec project create --description "Admin Project" admin
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the Admin Project for keystone"
  exit 1
fi

$openstackExec user create --password password admin
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the user for keystone"
  exit 1
fi

$openstackExec role create admin
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the user role for keystone"
  exit 1
fi

$openstackExec role add --project admin --user admin admin
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the project for keystone"
  exit 1
fi

$openstackExec project create --description "Service Project" service
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the Service Project for keystone"
  exit 1
fi

#################################################################
# Add openstack user to rabbitmq
#################################################################
$rabbitmqExec add_user openstack password
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to add openstack user to rabbitmq"
  exit 1
fi

$rabbitmqExec set_permissions openstack ".*" ".*" ".*"
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to set permissions for openstack user"
  exit 1
fi

#################################################################
# Verify Keystone Installation
#################################################################
LogMessage "INFO" "Verify Keystone Installation"
# disable the temporary authentication token mechanism
$sedExec  -i 's/ admin_token_auth//g' $keystoneDir/keystone-paste.ini
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to disable the temporary authentication token mechanism for keystone"
  exit 1
fi

# Unset the temporary OS_TOKEN and OS_URL environment variables:
LogMessage "INFO" "Unset the temporary OS_TOKEN and OS_URL environment variables"
unset OS_TOKEN OS_URL

$openstackExec --os-auth-url http://$hostName:35357/v2.0 --os-project-name admin --os-username admin --os-auth-type password token issue --os-password password
if [ $? -eq 0 ]; then
  LogMessage "INFO" "Keystone install successful"
else
  LogMessage "ERROR" "Keystone install verification failed"
fi

#################################################################
# Create the admin-openrc.sh file 
#################################################################
$catExec > $adminOpenrcFile <<EOF
export OS_PROJECT_NAME=admin
export OS_TENANT_NAME=admin
export OS_USERNAME=admin
export OS_PASSWORD=password
export OS_AUTH_URL=http://$hostName:35357/v2.0
export OS_IDENTITY_API_VERSION=2
export OS_VOLUME_API_VERSION=2
EOF
$chmodExec 700 $adminOpenrcFile

#################################################################
# Install cinder
#################################################################
# Pre-Requisites
LogMessage "INFO" "Installing cinder"
$mysqlExec -u $mysqlUser -p"$mysqlPasswd" << EOF
CREATE DATABASE $cinderDB;
GRANT ALL PRIVILEGES ON $cinderDB.* TO '$cinderUser'@'localhost' IDENTIFIED BY '$cinderPasswd';
GRANT ALL PRIVILEGES ON $cinderDB.* TO '$cinderUser'@'%' IDENTIFIED BY '$cinderPasswd';
EOF

if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to configure cinder through mysql"
  exit 1
fi

source $adminOpenrcFile

# Create User and Add role
LogMessage "INFO" "Creating cinder User and Adding role"
$openstackExec user create --password password cinder
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create the cinder user"
  exit 1
fi

$openstackExec role add --project service --user cinder admin
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to add the project for cinder"
  exit 1
fi

# Create Cinder Services
LogMessage "INFO" "Creating Cinder Services"
$openstackExec service create --name cinder --description "OpenStack Block Storage" volume
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create cinder service volume"
  exit 1
fi

$openstackExec service create --name cinderv2 --description "OpenStack Block Storage" volumev2
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create cinder service volumev2"
  exit 1
fi

# Create Cinder Endpoints
LogMessage "INFO" "Creating Cinder Endpoints"
$openstackExec endpoint create --region RegionOne --publicurl http://$hostName:8776/v1/%\(tenant_id\)s --internalurl http://$hostName:8776/v1/%\(tenant_id\)s --adminurl http://$hostName:8776/v1/%\(tenant_id\)s volume
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create cinder endpoint volume"
  exit 1
fi

$openstackExec endpoint create --region RegionOne  --publicurl http://$hostName:8776/v2/%\(tenant_id\)s --internalurl http://$hostName:8776/v2/%\(tenant_id\)s --adminurl http://$hostName:8776/v2/%\(tenant_id\)s volumev2
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to create cinder endpoint volumev2"
  exit 1
fi

#######################################################
# Install and configure components
#######################################################
LogMessage "INFO" "Updating the cinder.conf file"
$cpExec -f $cinderModifiedFile $cinderConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to copy the cinder.conf file"
  exit 1
fi

########################################################
# Sourcing the ovf.properties file to read the VM values
########################################################
if [ -f /opt/ADG/conf/ovf.properties ]; then
  source /opt/ADG/conf/ovf.properties
else
  LogMessage "ERROR" "Unable to determine the VM values"
  exit 1
fi

LogMessage "INFO" "Updating cinder conf with host IP address: $network_ipv40_ViPROpenStack"
$sedExec -i "s/__CONTROLLER__/$network_ipv40_ViPROpenStack/" $cinderConfFile
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to update cinder.conf file with the ip address"
fi
#######################################################
# Populate the Block Storage database
LogMessage "INFO" "Populate the Block Storage database"
$suExec -s $shExec -c "cinder-manage db sync" cinder
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to populate the Block Storage database"
  exit 1
fi
#######################################################

#######################################################
# Starting the openstack services

LogMessage "INFO" "Starting the openstack-cinder-api service"
$serviceExec openstack-cinder-api start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start the openstack-cinder-api service"
fi

LogMessage "INFO" "Starting the openstack-cinder-scheduler service"
$serviceExec openstack-cinder-scheduler start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start the openstack-cinder-scheduler service"
fi

LogMessage "INFO" "Starting the openstack-cinder-volume service"
$serviceExec openstack-cinder-volume start
if [ $? -ne 0 ]; then
  LogMessage "ERROR" "Failed to start the openstack-cinder-volume service"
fi

