import bourne 
import sys


brn = bourne.Bourne()
brn.connect(sys.argv[1])

fabricManager =brn.networksystem_query("CiscoMdsSimulator")
print(fabricManager)
brn.networksystem_deregister(fabricManager)
print(brn.networksystem_delete(fabricManager))


varray = brn.neighborhood_query("varray")
brn.neighborhood_delete(varray)

# # smis = brn.smisprovider_query("VNX-PROVIDER")
# # print(brn.smisprovider_delete(smis))

# storageSystems = brn.storagesystem_bulkgetids()
# print(brn.storagesystem_delete(storageSystems['id'][0]))