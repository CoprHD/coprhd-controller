#!/usr/bin/python
#
# Copyright 2015 EMC Corporation
# All Rights Reserved
#
# Parse OVF Script
#

import os
import sys
import libxml2


confDir = "/opt/ADG/conf"
propFileName = "ovf.properties"
properties = [];
vmNames = {}

# This function will take a NodeList and turn its entries into properties
# in the properties object. It will replace any '.' with '_' in the key.
# It also attaches a suffix to the property based on the count which is passed.
def nodeListToProperties(nodeList, count):
  suffix = ""
  if count > 0:
    suffix = "_" + str(count)

  for node in nodeList:
    key   = replaceChars(node.prop("key")) + suffix
    value = node.prop("value")

    propertyText = ("%s=\"%s\"\n" % (key, value))
    if not propertyText in properties:
      properties.append(propertyText)

# This function will write to the property file, it will also create
# the directory structure if it does not already exist.
def writePropFile():
  if not os.path.exists(confDir):
    os.makedirs(confDir)

  propertiesFile = open(confDir + "/" + propFileName, "w+")
  propertiesFile.writelines(properties)

def getNode(xPathQuery, xPathCtx):
  nodeList = xPathCtx.xpathEval(xPathQuery)
  for node in nodeList:
    return node
  raise NameError("Unable to find node for query \n[" + xPathQuery + "]")

def indexVmName(vmName):
  if vmName in vmNames:
    vmNames[vmName] = vmNames[vmName] + 1
  else:
    vmNames[vmName] = 0

def replaceChars(text):
  return text.replace(".", "_").replace("-", "_")


 # The main method
def main():
  if sys.argv.__len__() <= 1:
    print "No file to parse!"
    sys.exit(1)

  xmlDoc = libxml2.parseDoc(open(sys.argv[1], 'r').read())

  xpathCtx = xmlDoc.xpathNewContext()
  xpathCtx.xpathRegisterNs("ns", "http://schemas.dmtf.org/ovf/environment/1")
  xpathCtx.xpathRegisterNs("oe", "http://schemas.dmtf.org/ovf/environment/1")

  # this VMs nodes
  try:
    vmName = getNode('//ns:Environment/ns:PropertySection/ns:Property[@oe:key="vm.vmname"]', xpathCtx).prop("value")
    indexVmName(vmName)
  except NameError:
    sys.exit(2)

  xpathQuery = '//ns:Environment/ns:PropertySection/ns:Property'
  vmProps = xpathCtx.xpathEval(xpathQuery)
  nodeListToProperties(vmProps, 0)

  xpathQuery = '//ns:Environment/ns:Entity'
  entityNodeList = xpathCtx.xpathEval(xpathQuery)
  for entity in entityNodeList:
    xpathCtx.setContextNode(entity)

    entityId = "Unknown"
    for prop in entity.properties:
      if prop.name == "id" and prop.content != "":
        entityId = prop.content

    try:
      entityVmName = getNode('ns:PropertySection/ns:Property[@oe:key="vm.vmname"]', xpathCtx).prop("value")
      indexVmName(entityVmName)

      xpathQuery = 'ns:PropertySection/ns:Property[contains(@oe:key, \'' + entityVmName + '\')]'
      entityVmProps = xpathCtx.xpathEval(xpathQuery)
      nodeListToProperties(entityVmProps, vmNames[entityVmName])
    except NameError:
      print "\nUnable to parse the entity: %s, due to missing VM name property. Skipping..." % entityId

  # Adding a new property called key_vmname with the "." and "-" in the vm_vmname value
  # replaced with "_"
  propertyText = ("%s=\"%s\"\n" % ("key_vmname", replaceChars(vmName)))
  properties.append(propertyText)

  writePropFile()
  xmlDoc.freeDoc()

main()
