import functools
import traceback
from uuid import uuid4,UUID
import traceback
from twisted.trial.unittest import SynchronousTestCase
#from flocker.node.agents.test.test_blockdevice import make_iblockdeviceapi_tests
from coprhd_flocker_plugin.testtools_emc_coprhd import tidy_coprhd_client_for_test

def emc_coprhd_blockdeviceapi_for_test(test_case=None):
    '''
    This Method Calls tidy_coprhd_client_for_test method in coprhd_flocker_plugin.testtools_emc_coprhd
    And Returns CoprHD Pulgin API Object
    ''' 
    return tidy_coprhd_client_for_test(test_case)


class EMCCoprHDBlockDeviceAPIImplementationTests(SynchronousTestCase):
      global block_device_api
      block_device_api = emc_coprhd_blockdeviceapi_for_test()
      global id
      id ='85b3ca5e-d8d9-4333-8f4b-894df979be4f'
      def test_list_volumes(self):
          '''
          This Method returns all the volumes available   
          '''
          try:
             volumes = None
             #block_device_api = emc_coprhd_blockdeviceapi_for_test(self)
             volumes = block_device_api.list_volumes()
             self.assertIsNotNone(volumes,msg='Volumes are available')
             return volumes
          except Exception as e:
             traceback.print_exc()
             self.fail(e.message)

      def test_create_volume(self):
          '''
          This Method creates a volume with dataset_id = 85b3ca5e-d8d9-4333-8f4b-894df979be4f
          
          And verifies after creation of volume

          '''
	  dataset_id = UUID(id)
          size = 1024*1024*1024
          try:
             create = block_device_api.create_volume(dataset_id, size)
             expected_dataset_id = create.dataset_id
             self.assertEqual(dataset_id,expected_dataset_id)
                     
          except Exception as e:
             traceback.print_exc()
             self.fail(e.message)
    
      def test_attach_volume(self):
          '''
          This Method Attaches the volume to Host 

          And Verifies after attachment 

          '''
           
          blockdevice_id = u'block-85b3ca5e-d8d9-4333-8f4b-894df979be4f'
          attach_to = u'28bd47e5-c806-4bb5-8cbc-647128f1a42d'
          try:
             attach = block_device_api.attach_volume(blockdevice_id, attach_to)
             print attach.attached_to
             self.assertEqual(attach_to,attach.attached_to)
          except Exception as e:
             traceback.print_exc()
             self.fail(e.message)

      def test_detach_volume(self):
          '''
          This Method Dettach the volume from the Host 
          And Verifies
          '''
          blockdevice_id = 'block-'+id
          try:
             detach_to = block_device_api.detach_volume(blockdevice_id)
             volumes = self.test_list_volumes()
             actual_attached_to = u'28bd47e5-c806-4bb5-8cbc-647128f1a42d'
             for volume in volumes:
                 if volume.blockdevice_id == u'block-85b3ca5e-d8d9-4333-8f4b-894df979be4f':
                     actual_attached_to = volume.attached_to
                     break
             self.assertIsNone(actual_attached_to)
                     
             
          except Exception as e:
             traceback.print_exc()
             self.fail(e.message)

      def test_delete_volume(self):

         '''
         This Method Deletes the Test Volume   

         And Verifies whether Volume deleted Successfully or not             
         '''


         del_dataset_id = 'block-85b3ca5e-d8d9-4333-8f4b-894df979be4f'
         try:
            destroy = block_device_api.destroy_volume(del_dataset_id) 
            volumes = self.test_list_volumes()
            dataset_id_list=[]
            for volume in volumes:
                dataset_id_list.append(volume.blockdevice_id)
            print dataset_id_list
            expected_dataset_id = u'block-85b3ca5e-d8d9-4333-8f4b-894df979be4f'
            volume_deleted = False
            if expected_dataset_id not in dataset_id_list:
               volume_deleted = True 
            self.assertTrue(volume_deleted)

         except Exception as e:
            traceback.print_exc()
            self.fail(e.message)

      def test_system(self):
          '''
          This method performs all Operation in Sequence 
          ''' 
          try:

             print 'creating volume'
             self.test_create_volume()
             print 'attaching volume'
             self.test_attach_volume()
             print 'listing volumes'
             self.test_list_volumes()
             print 'detaching volumes'
             self.test_detach_volume()
             print 'deleting volumes'
             self.test_delete_volume()
          except Exception as e:
             traceback.print_exc()
             self.fail(e.message)
