package com.emc.sa.service.vipr.oe;

public class JsonSamples {

	public static String msg="Hello world.";

	public static String singleTask = "{ " +
			"   \"name\":\"CREATE EXPORT GROUP\"," +
			"   \"id\":\"urn:storageos:Task:8d9fe442-540e-4b31-9604-837c5181bcb6:vdc1\"," +
			"   \"link\":{ " +
			"      \"rel\":\"self\"," +
			"      \"href\":\"/vdc/tasks/urn:storageos:Task:8d9fe442-540e-4b31-9604-837c5181bcb6:vdc1\"" +
			"   }," +
			"   \"inactive\":false," +
			"   \"global\":false," +
			"   \"remote\":false," +
			"   \"vdc\":{ " +
			"      \"id\":\"urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"," +
			"      \"link\":{ " +
			"         \"rel\":\"self\"," +
			"         \"href\":\"/vdc/urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"" +
			"      }" +
			"   }," +
			"   \"tags\":[ " +
			" " +
			"   ]," +
			"   \"internal\":false," +
			"   \"resource\":{ " +
			"      \"id\":\"urn:storageos:ExportGroup:3b9d0a49-fb22-4541-83ff-75e9b91955a6:vdc1\"," +
			"      \"name\":\"MyExport\"," +
			"      \"link\":{ " +
			"         \"rel\":\"self\"," +
			"         \"href\":\"/block/exports/urn:storageos:ExportGroup:3b9d0a49-fb22-4541-83ff-75e9b91955a6:vdc1\"" +
			"      }" +
			"   }," +
			"   \"tenant\":{ " +
			"      \"id\":\"urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"," +
			"      \"link\":{ " +
			"         \"rel\":\"self\"," +
			"         \"href\":\"/tenants/urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"" +
			"      }" +
			"   }," +
			"   \"state\":\"pending\"," +
			"   \"description\":\"create export group operation\"," +
			"   \"progress\":0," +
			"   \"creation_time\":1471981023875," +
			"   \"op_id\":\"6cf8a12c-a000-4480-9d42-116e34d7b2dd\"," +
			"   \"associated_resources\":[ " +
			" " +
			"   ]," +
			"   \"start_time\":1471981023874" +
			"}";

	public static String listOfTasks = "{ " +
			"   \"task\":[ " +
			"      { " +
			"         \"name\":\"CREATE VOLUME\"," +
			"         \"id\":\"urn:storageos:Task:ed8647ab-6cf3-4189-a284-146227f40fd2:vdc1\"," +
			"         \"link\":{ " +
			"            \"rel\":\"self\"," +
			"            \"href\":\"/vdc/tasks/urn:storageos:Task:ed8647ab-6cf3-4189-a284-146227f40fd2:vdc1 \"" +
			"         }," +
			"         \"inactive\":false," +
			"         \"global\":false," +
			"         \"remote\":false," +
			"         \"vdc\":{ " +
			"            \"id\":\"urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"," +
			"            \"link\":{ " +
			"               \"rel\":\"self\"," +
			"               \"href\":\"/vdc/urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"" +
			"            }" +
			"         }," +
			"         \"tags\":[ " +
			" " +
			" " +
			"         ]," +
			"         \"internal\":false," +
			"         \"resource\":{ " +
			"            \"id\":\"urn:storageos:Volume:8d1f7483-b125-44aa-9c50-3b4c1da1d8ec:vdc1\"," +
			"            \"name\":\"mendes-vol-test-1\"," +
			"            \"link\":{ " +
			"               \"rel\":\"self\"," +
			"               \"href\":\"/block/volumes/urn:storageos:Volume:8d1f7483-b125-44aa-9c50-3b4c1da1d8ec:vdc1\"" +
			"            }" +
			"         }," +
			"         \"tenant\":{ " +
			"            \"id\":\"urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"," +
			"            \"link\":{ " +
			"               \"rel\":\"self\"," +
			"               \"href\":\"/tenants/urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"" +
			"            }" +
			"         }," +
			"         \"state\":\"pending\"," +
			"         \"description\":\"create volume operation\"," +
			"         \"progress\":0," +
			"         \"creation_time\":1471980068816," +
			"         \"op_id\":\"e2430440-f609-4547-bef7-3bb139120763\"," +
			"         \"associated_resources\":[ " +
			" " +
			" " +
			"         ]," +
			"         \"start_time\":1471980068815" +
			"      }," +
			"      { " +
			"         \"name\":\"CREATE VOLUME\"," +
			"         \"id\":\"urn:storageos:Task:f09f5def-1a5f-4422-a58b-1db367025e23:vdc1\"," +
			"         \"link\":{ " +
			"            \"rel\":\"self\"," +
			"            \"href\":\"/vdc/tasks/urn:storageos:Task:f09f5def-1a5f-4422-a58b-1db367025e23:vdc1\"" +
			"         }," +
			"         \"inactive\":false," +
			"         \"global\":false," +
			"         \"remote\":false," +
			"         \"vdc\":{ " +
			"            \"id\":\"urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"," +
			"            \"link\":{ " +
			"               \"rel\":\"self\"," +
			"               \"href\":\"/vdc/urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"" +
			"            }" +
			"         }," +
			"         \"tags\":[ " +
			" " +
			" " +
			"         ]," +
			"         \"internal\":false," +
			"         \"resource\":{ " +
			"            \"id\":\"urn:storageos:Volume:e982750e-7d3f-462e-96d8-77d6e1fc3c19:vdc1\"," +
			"            \"name\":\"mendes-vol-test-2\"," +
			"            \"link\":{ " +
			"               \"rel\":\"self\"," +
			"               \"href\":\"/block/volumes/urn:storageos:Volume:e982750e-7d3f-462e-96d8-77d6e1fc3c19:vdc1\"" +
			"            }" +
			"         }," +
			"         \"tenant\":{ " +
			"            \"id\":\"urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"," +
			"            \"link\":{ " +
			"               \"rel\":\"self\"," +
			"               \"href\":\"/tenants/urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"" +
			"            }" +
			"         }," +
			"         \"state\":\"pending\"," +
			"         \"description\":\"create volume operation\"," +
			"         \"progress\":0," +
			"         \"creation_time\":1471980068826," +
			"         \"op_id\":\"e2430440-f609-4547-bef7-3bb139120763\"," +
			"         \"associated_resources\":[ " +
			" " +
			" " +
			"         ]," +
			"         \"start_time\":1471980068825" +
			"      }" +
			"   ]" +
			"}";


}
