/**
 * 
 */

//////////////////////////////////////////////////////////////////////////////////////////////////////
// This gets run once as soon as the js file is included.  This is the initialization
// area.  Yep, guessing there is a better way of initializing these globals for the script.
//////////////////////////////////////////////////////////////////////////////////////////////////////
var XMLHttpRequestObject = false;

if (window.XMLHttpRequest)
{
    XMLHttpRequestObject = new XMLHttpRequest();
}
else if (window.ActiveXObject)
{
    XMLHttpRequestObject = new ActiveXObject("Microsoft.XMLHTTP");
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// function nextpage(pageName)
//
////////Ob/////////////////////////////////////////////////////////////////////////////////////////////////////////
function sendRequestToServlet()
{
	
	var url = "./ContactFetch6.java";
	
	if(XMLHttpRequestObject)
		{
			XMLHttpRequestObject.open("POST", url);
				XMLHttpRequestObject.setRequestHeader("Content-Type", "text/xml");
				
				XMLHttpRequestObject.onreadystatechange= function()
				{
					if(XMLHttpRequestObject.readyState== 4 && XMLHttpRequestObject.status == 200)
						{
							if((XMLHttpRequestObject.responseText).substring(0, 4)=="http")
								{
									alert("http"+XMLHttpRequestObject.responseText);
								}
						}
				}
				
		}
}
	
	/*$ajax({
		
			url:"ContactFetch6.java",
			type: "POST",
			dataType: "json",
			data: $("#submit").serialize(),
			success: function(data){
				if(data.isValid){
					alert("here");
				}
			}
		
	});*/





function collectInformation() 
{	
	var version = document.getElementById('version1').value;
	var viprNodes = document.getElementById('viprNodes1').value;
	var ViPRSolPak = document.getElementById('ViPRSolPak1').value;
	var ViPRArrays = document.getElementById('ViPRArrays1').value;
	var VMAXVNX = document.getElementById('VMAXVNX1').value;
	var IBMXIV = document.getElementById('IBMXIV1').value;
	var scaleIO = document.getElementById('scaleIO1').value;
	var NumberofServers = document.getElementById('NumberofServers1').value;
	var vcenters = document.getElementById('vcenters1').value;
	var Fabrics = document.getElementById('Fabrics1').value;
	var brocadeCisco = document.getElementById('brocadeCisco1').value;
	var vplex = document.getElementById('vplex1').value;
	var filesytems = document.getElementById('filesytems1').value;
	var NFS = document.getElementById('NFS1').value;
	var CIFS = document.getElementById('CIFS1').value;

	
	var ArraySelection = document.getElementById("servers1");
	var SupportedVersion = document.getElementById("supportedVersions");
	var ServerSelection = document.getElementById("serversSelection1");
	var ServerSupport = document.getElementById("serverSupportedVersions");
	var FabricSelection = document.getElementById("fabricsSelection1");
	var FabricSupport = document.getElementById("FabricSupportedVersions");
	
	
	//ServerSelection.innerHTML="HELLO";
	//alert(ServerSelection);
	//ArraySelection.innerHTML="HELLO WORLD";
	
	
	//alert(ArraySelection);
	while(ArraySelection.hasChildNodes() || SupportedVersion.hasChildNodes())
	{
		ArraySelection.removeChild(ArraySelection.lastChild);
		SupportedVersion.removeChild(SupportedVersion.lastChild);
	}
	
	for(var i=0; i<ViPRArrays;i++)
		{
			/*ArraySelection.appendChild(document.createTextNode((i+1)+". Array name - Last 4 digits os S/N  "));
			var input = document.createElement("input");
			input.type = "text";
			input.className="form-control";
			ArraySelection.appendChild(input);
			ArraySelection.appendChild(document.createElement("br"));*/
			
			/*var labeldiv = document.createElement('div');
			labeldiv.className="col-md-5";
			labeldiv.innerHTML = "<label>"+(i+1)+". Array name - Last 4 digits os S/N </label>";
			ArraySelection.appendChild(labeldiv);
			var inputdiv = document.createElement('div');
			inputdiv.className="col-md-7";
			inputdiv.innerHTML="<input class=\"form-control\"><br>";
			ArraySelection.appendChild(inputdiv);*/
			input("labeldiv","inputdiv"," Array name - Last 4 digits os S/N",i, ArraySelection,"ArrayNameID"+(i+1));
			
	//  Select for Array type
			
			/*var selectLabelDiv = document.createElement('div');
			selectLabelDiv.className="col-md-5";
			selectLabelDiv.innerHTML = "<label> Array Type </label>";
			ArraySelection.appendChild(selectLabelDiv);
			var selectDiv = document.createElement('div');
			selectDiv.className = "col-md-7";
			var options = [" ","VMAX", "VMAX3", "VNX block", "VNX unified", "VNXe Block 3200", "NetApp (7-mode)", "Scale IO", "Xtreme IO", "IBM XIV", "Isilon", "VNX FIle", "VNXe File 3200"];
			var ArraySelect = document.createElement('select');
			ArraySelect.id = "ArraySelectId";
			ArraySelect.className="form-control arrayslct";
			selectDiv.appendChild(ArraySelect);
			selectDiv.appendChild(document.createElement('br'));
			//selectDiv.innerHTML= "<br>";
			ArraySelection.appendChild(selectDiv);
			
			
			for(var j = 0; j<options.length;j++)
				{
					var SelectOption = document.createElement('option');
					SelectOption.id = "SelectOptionID";
					SelectOption.value = options[j];
					SelectOption.text = options[j];
					ArraySelect.appendChild(SelectOption);
				
				}*/
			var options = [" ","VMAX", "VMAX3", "VNX Block", "VNX Unified", "VNXe Block 3200", "NetApp (7-mode)", "Scale IO", "Xtreme IO", "IBM XIV", "Isilon", "VNX File", "VNXe File 3200"];
			select("selectLabelDiv","Array Type","selectDiv",ArraySelection,options, "SelectElement", "optionElement", "ArrayID"+(i+1),i,"Array");
		// Model details
			
			
			var modellabeldiv = document.createElement('div');
			modellabeldiv.className="col-md-5";
			modellabeldiv.innerHTML = "<label> Model </label>";
			ArraySelection.appendChild(modellabeldiv);
			var Modelinputdiv = document.createElement('div');
			Modelinputdiv.className="col-md-7"; 
			Modelinputdiv.innerHTML="<input class=\"form-control\" id=\"ModelInputID"+(i+1) +"\"><br>";
			ArraySelection.appendChild(Modelinputdiv);
			
			
	// Protocols
			
			
			
			var Protocoloptions = [" ","iSCSI", "FC", "NFS", "CIFS", "SCALE IO"];
			select("ProtocolselectLabelDiv","Protocol","ProtocolselectDiv",ArraySelection,Protocoloptions, "ProtocolSelect", "ProtocolSelectOption" ,"ProtocolId"+(i+1));
			
		//Array VDC
			
			
			var VDCoptions = [" ","Greenfield", "coexist", "Ingestion"];
			var VDCselectDiv1 =	select("VDCselectLabelDiv","Arrays VDC state","VDCselectDiv",ArraySelection,VDCoptions, "VDCSelect", "VDCSelectOption","VDCID"+(i+1));
			var rows = 10;
			var SupportedTextdiv1 = suggestionInformation("ArraySuggestionlabeldiv",SupportedVersion,"ArraySupportedTextdiv", rows, "sugestionID"+(i+1));
			
			VDCselectDiv1.appendChild(document.createElement('br'));
			VDCselectDiv1.appendChild(document.createElement('br'));
			SupportedTextdiv1.appendChild(document.createElement('br'));
			SupportedTextdiv1.appendChild(document.createElement('br'));
	
			
			
		}
	
///// ************ server selection tab*********************/////
	
	while(ServerSelection.hasChildNodes())
		{			
			ServerSelection.removeChild(ServerSelection.lastChild);
		}
		
	for(var a=0 ; a<NumberofServers; a++ )
		{
			input("ServerLabeldiv","ServerInputdiv","ServerName",a, ServerSelection, "ServerSelectionID"+(a+1));
				/*var labeldiv = document.createElement('div');
				labeldiv.className="col-md-5";
				labeldiv.innerHTML = "<label>"+(i+1)+". Array name - Last 4 digits os S/N </label>";
				ArraySelection.appendChild(labeldiv);
				var inputdiv = document.createElement('div');
				inputdiv.className="col-md-7";
				inputdiv.innerHTML="<input class=\"form-control\"><br>";
				ArraySelection.appendChild(inputdiv);*/
			var OperatingSystemOptions = [" ","Red Hat Enterprise Linux", "Oracle Enterprise Linux", "Suse Linux Enterprise Server", "VMware ESXi/vCenter","AIX", "AIX VIO", "Win Server"];
			var OperatingSystemSelectDiv1 = select("OperatingSystemsLabelDiv ", "Operating Systems", "OperatingSystemSelectDiv", ServerSelection, OperatingSystemOptions, "OperatingSystemSelectElement", "OperatingSystemOptionElement","ServerOSID"+(a+1), a, "ServerSlct");
			
			var serverRows = 4;
			var ServerSupportedTextdiv1 = suggestionInformation("ServerSuggestionlabeldiv",ServerSupport,"ServerSupportedTextdiv", serverRows, "serverSuggestion"+(a+1));
			
			OperatingSystemSelectDiv1.appendChild(document.createElement('br'));
			OperatingSystemSelectDiv1.appendChild(document.createElement('br'));
			ServerSupportedTextdiv1.appendChild(document.createElement('br'));
			//ServerSupportedTextdiv1.appendChild(document.createElement('br'));
			
		}
		
////************* Fabric Selection ****************///
	
	while(FabricSelection.hasChildNodes())
		{
			FabricSelection.removeChild(FabricSelection.lastChild);
		}
	
	for(var b=0; b<Fabrics; b++)
		{
			input("FabricLabeldiv","FabricInputdiv","Fabric Name(FC)",b, FabricSelection, "FabricSelectionID"+(b+1));
			
			var SwitchOptions = [" ","Brocade 6510", "Brocade DX8510", "Cisco MDS 9148", "CISCO Nexus"];
			var SwitchSelectDiv1 = select("SwitchLabelDiv ", "Switch Model", "SwitchSelectDiv", FabricSelection, SwitchOptions, "SwitchSelectElement", "SwitchOptionElement", "FabricID"+(b+1), b, "Fabric" );
			
			var FabricRows = 5;
			var FabricSupportedTextdiv1 = suggestionInformation("FabricSuggestionlabeldiv",FabricSupport,"FabricSupportedTextdiv", FabricRows, "FabricSuggestion"+(b+1));
			
			SwitchSelectDiv1.appendChild(document.createElement('br'));
			SwitchSelectDiv1.appendChild(document.createElement('br'));
			SwitchSelectDiv1.appendChild(document.createElement('br'));
			FabricSupportedTextdiv1.appendChild(document.createElement('br'));
			
			
		}
	
	//document.getElementById("AllSave").setAttribute("onclick", "saveInformation("+ViPRArrays,NumberofServers,Fabrics+")");
	document.getElementById("AllSave").onclick = function(){saveInformation(ViPRArrays,NumberofServers,Fabrics);};
/*	var button = document.createElement("button");
	button.innerHTML= 'Save';
	button.setAttribute("type", "button");
	button.setAttribute("class", "btn btn-primary btn-lg");
	//buttonDiv.className = "AllSaveButton col-md-12";
	//buttonDiv.innerHTML="<button type=\"button\"  class = \"btn btn-primary btn-lg \" >SAVE</button> ";
	buttonDiv.appendChild(button);*/
	
	

	
}

function input(labeldiv,inputdiv,labelName,i, ArraySelection, inputID)
{
	 
		
	labeldiv = document.createElement('div');
	labeldiv.className="col-md-5";
	labeldiv.innerHTML = "<label>"+(i+1)+". "+ labelName+"</label>";
	ArraySelection.appendChild(labeldiv);
	inputdiv = document.createElement('div');
	inputdiv.className="col-md-7";
	inputdiv.innerHTML="<input class=\"form-control\" id=\""+ inputID +"\"><br>";
	ArraySelection.appendChild(inputdiv);
}

function select(selectLabelDiv,selectLabel,selectDiv,ArraySelection,options, SelectElement, optionElement, selectID,i, component)
{
	
	selectLabelDiv = document.createElement('div');
	selectLabelDiv.className="col-md-5";
	selectLabelDiv.innerHTML = "<label> "+ selectLabel +"</label>";
	ArraySelection.appendChild(selectLabelDiv);
	selectDiv = document.createElement('div');
	selectDiv.className = "col-md-7";
	//var options = [" ","VMAX", "VMAX3", "VNX block", "VNX unified", "VNXe Block 3200", "NetApp (7-mode)", "Scale IO", "Xtreme IO", "IBM XIV", "Isilon", "VNX FIle", "VNXe File 3200"];
	SelectElement = document.createElement('select');
	SelectElement.id = selectID;
	SelectElement.onchange = function(){Change(this,i,component);};
	SelectElement.className="form-control arrayslct";
	selectDiv.appendChild(SelectElement);
	selectDiv.appendChild(document.createElement('br'));
	//selectDiv.innerHTML= "<br>";
	ArraySelection.appendChild(selectDiv);
	
	
	for(var j = 0; j<options.length;j++)
		{
			optionElement = document.createElement('option');
			optionElement.id = "SelectOptionID";
			optionElement.value = options[j];
			optionElement.text = options[j];
			SelectElement.appendChild(optionElement);
		
		}
	return selectDiv;
}

function suggestionInformation(Suggestionlabeldiv,SupportedVersion,SupportedTextdiv,rows,id)
{
	Suggestionlabeldiv = document.createElement('div');
	Suggestionlabeldiv.className="col-md-12";
	Suggestionlabeldiv.innerHTML = "<label>Supported Versions And Their Information</label>";
	SupportedVersion.appendChild(Suggestionlabeldiv);
	SupportedTextdiv = document.createElement('div');
	SupportedTextdiv.className="col-md-12 suggestion";
	SupportedTextdiv.innerHTML="<textarea class=\"form-control\" id=\""+id+"\" rows=\""+rows+"\"></textarea><br>";
	SupportedVersion.appendChild(SupportedTextdiv);
	return SupportedTextdiv;

}

function Change(selct,f,inventoryItem)
{
	document.getElementById("sugestionID"+(f+1));
	document.getElementById("serverSuggestion"+(f+1));
	var ArrayName, Display, ServerOSName;
	//for(var k, h = 0; k = selct.options[h]; h++ )
		//{	
		if(inventoryItem == "Array")
			{
				ArrayName = document.getElementById('ArrayID'+(f+1)).value;
				//alert(ArrayName);
				Display = ArrayName;
			}
		else if(inventoryItem == "ServerSlct")
			{
				ServerOSName = document.getElementById('ServerOSID'+(f+1)).value;
				//alert(ServerOSName);
				Display = ServerOSName;
			}
		else if(inventoryItem == "Fabric")
			{
				FabricName = document.getElementById('FabricID'+(f+1)).value;
				//alert(FabricName);
				Display = FabricName;
			}
		else
			{
				Display = null;
			}
			
			switch(Display)
			{
				case "VMAX": 	
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 5876.229 \n" +
							"2. 5876.272 \n" +
							"3. SMI-S 4.6.2.20 \n" +
							"4. SE 7.6.2.26 \n" 
							);
					break;
					
				case "VMAX3":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 5977.477 (min) \n" +
							"2. 5977.498(max) \n" +
							"3. SMI-S 8.0.1 \n" );						
					break;
					
				case "VNX Block":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. Ver 5.32 (Gen1) block \n" +
							"2. Ver 5.33 (Gen2) block \n" +
							"3. SMI-S 4.6..2.20 \n" +
							"4. SE 7.6.2.26 \n" );						
					break;
				
				case "VNX Unified":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. Ver 5.32 (Gen1) block \n" +
							"2. Ver 5.33 (Gen2) block \n" +
							"3. 7.1.71.1, 7.1.74.5, 7.1.76.4 (Gen 1 File) \n" +
							"4. 8.1.1.33, 8.1.2.51, 8.1.3.72 (Gen 2 File) \n" +
							"5. SMI-S 4.6.2.20 \n" +
							"6. SE 7.6.2.26 \n" );						
					break;
					
				case "VNXe Block 3200":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 3.0.1.3513260 \n" +
							"2. 3.0.1.4029529 \n" );
					break;
					
				case "NetApp (7-mode)":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. ONTAP versions 8.1.x \n" +
							"2. ONTAP versions 8.2.x \n" );
					break;
				
				case "Scale IO":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 1.3 \n" +
							"2. 1.31 \n" );
					break;
					
				case "Xtreme IO":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 3.0.3.0 \n" +
							"2. 3.0.1 \n" );
					break;
					
				case "IBM XIV":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. Firmware 11.4.1.a \n" +
							"2. SMIS 11.4.0.10 \n" );
					break;
					
				case "Isilon":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 7.0.2.1, 7.0.2.4,7.0.2.7, 7.0.2.10 \n" +
							"2. 7.1.0.1, 7.1.0.3, 7.1.0.5 \n" +
							"3. 7.1.1.0, 7.1.1.1 \n" +
							"4. 7.2.0.0 \n" +
							"5. NFS and CIFS protocols supported" );
					break;
				
				case "VNX File":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 7.1.71.1, 7.1.74.5, 7.1.76.4 (GEN 1 File) \n" +
							"2. 8.1.1.33, 8.1.2.51, 8.1.3.72 (GEN 2 File) \n" +
							"3. NFS and CIFS protocol supported" );
					break;
					
				case "VNXe File 3200":
					$("#sugestionID"+(f+1)).val("Supported Versions: \n" +
							"1. 3.0.1.3513260 \n" +
							"2. 3.0.1.4029529 \n" );
					
					break;
					
				case "Oracle Enterprise Linux":
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 6.5 \n" +
							"2. 7.0 \n");
					break;
					
				case "Red Hat Enterprise Linux":
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 6.1, 6.2, 6.3 \n" +
							"2. 6.4, 6.5, 6.6 \n \n" +
							"LVM(Linux Logical Volume Manager): Linux Native Multipathing or EMC PowerPath");
					break;
					
				case "Suse Linux Enterprise Server":
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 11SP1, 2, 3 \n" +
							"2. 12 \n \n" +
							"LVM(Linux Logical Volume Manager): Linux Native Multipathing or EMC PowerPath");
					break;
					
				case "VMware ESXi/vCenter":
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 5.1.x \n" +
							"2. 5.5.x \n \n" +
							"3. EMC PowerPath/VE or NMP");
					break;
					
				case "AIX":
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. AIX 6.1, 7.1.0.0 \n" +
							"2. /VIO 2.1.1.0/ \n \n" +
							"3. JFS2 only supported \n \n" +
							"AIX Native MPIO or EMC PowerPath");
					break;
					
				case "AIX VIO":
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. AIX 6.1, 7.1.0.0 \n" +
							"2. /VIO 2.1.1.0/ \n" +
							"3. HMC V7R3.5.0" +
							"4. JFS2 only supported \n \n" +
							"AIX Native MPIO or EMC PowerPath");
					break;
					
				case "Win Server" :
					$("#serverSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 2008 R2 \n" +
							"2. 2012 \n \n" +
							"3. 2012 R2 \n" +
							"Microsoft MPIO or EMC PowerPath");
					break;
					
				case "Brocade 6510":
					$("#FabricSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 11.2.1 \n" +
							"2. 12.1.5, 12.1.6 \n \n");
					break;					
					
				case "Brocade DX8510":
					$("#FabricSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 11.2.1 \n" +
							"2. 12.1.5, 12.1.6 \n \n");
					break;
					
				case "Cisco MDS 9148":
					$("#FabricSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 5.2(8e) \n" +
							"2. 6.2(9a) \n \n" +
							"Enhanced zoning mode required");
					break;
					
				case "CISCO Nexus":
					$("#FabricSuggestion"+(f+1)).val("Supported Versions: \n" +
							"1. 5.2(1)N1(8b) \n"+
							"2. 6.0(2)N1(2) \n" +
							"3. 7.0(1)N1(1) \n");
					break;
					
				default : 
					break;
			}
			
		
			
			
		//}
			
			
			
}

function saveInformation(ViPRArrays, NumberofServers, Fabrics)
{
	var ViPRArrayName;
	var Paragraphdiv = document.createElement('div');
	Paragraphdiv.className = "paragraphContent col-md-12 jumbotron";
	
	var paragraphElement = document.createElement("pre");
	Paragraphdiv.appendChild(paragraphElement);
	
	
	//Paragraphdiv.innerHTML = "<pre id=\"paragraphId1\"></p>";
	var i=1;
	if(ViPRArrays>=0)
		{	
			//paragraphElement.appendChild("Array Details");
			for(var selectArray = 0; selectArray<ViPRArrays; selectArray++ )
				{
					
					ViPRArrayName = document.getElementById("ArrayNameID"+(selectArray+1)).value;
					var ArrayType1 = document.getElementById("ArrayID"+(selectArray+1)).value;
					var ModelValue = document.getElementById("ModelInputID"+(selectArray+1)).value;
					var ProtocolValue = document.getElementById("ProtocolId"+(selectArray+1)).value;
					var VDCValue = document.getElementById("VDCID"+(selectArray+1)).value;
					
					var consolidatedArray = i+". Array Name - last 4 digits of S/N  \t= " + ViPRArrayName +"\n"+
										      "   Array Type                        \t= " +ArrayType1 +"\n"+
										      "   Model                             \t= " +ModelValue+"\n"+
										      "   Protocol                          \t= " +ProtocolValue+"\n"+
										      "   Array VDC state                   \t= "+VDCValue+"\n\n\n";
					//Paragraphdiv.innerHTML = "<pre>"+consolidatedArray+"</pre>";
					var contentNode = document.createTextNode(consolidatedArray);
					paragraphElement.appendChild(contentNode);
					document.getElementById("paragraphId").appendChild(Paragraphdiv);
					i++;
					alert(ViPRArrayName);
					alert(ArrayType1);
					alert(ModelValue);
					alert(ProtocolValue);
					alert(VDCValue);
					alert(consolidatedArray);
				}
		}
	else 
		{
			console.log("no arrays");
		}
	
	if(NumberofServers>=0)
		{
			for(var selctServer=0; selctServer<NumberofServers; selctServer++)
				{
					alert(NumberofServers);
					var ServerName = document.getElementById("ServerSelectionID"+(selctServer+1)).value;
					var serverOpertngSystm = document.getElementById("ServerOSID"+(selctServer+1)).value;
					alert(ServerName);
					alert(serverOpertngSystm);
					
					var consolidatedServer = (selctServer+1)+". Server Name                          = " +ServerName+"\n"+
														     "   Server operating System              = " +serverOpertngSystm+"\n \n \n";
					
					var ServerContent = document.createTextNode(consolidatedServer);
					paragraphElement.appendChild(ServerContent);
					document.getElementById("paragraphId").appendChild(Paragraphdiv);
				}
		}
	else
		{
			console.log("no servers");
		}
	
	if(Fabrics>=0)
		{
			for(var slctFabrcs =0; slctFabrcs<Fabrics; slctFabrcs++)
				{
					var fabricName = document.getElementById("FabricSelectionID"+(slctFabrcs+1)).value;
					var switchModel = document.getElementById("FabricID"+(slctFabrcs+1)).value;
					var consolidatedFabric = (slctFabrcs+1)+". Fabric Name                          = "+fabricName+"\n"+
															"   Switch Model                          = "+switchModel+"\n\n\n";
					var FabricContent = document.createTextNode(consolidatedFabric);
					paragraphElement.appendChild(FabricContent);
					document.getElementById("paragraphId").appendChild(Paragraphdiv);
				}
		}
	else
		{
			console.log("no Fabrics");
		}
		//document.getElementById("paragraphId").appendChild(Paragraphdiv);	
	//document.getElementById("paragraphId").appendChild(Paragraphdiv);
}

function getInformation()
{
	//alert("its coming here");
	var FirstName = document.getElementById("first_name").value;
	var Title = document.getElementById("title").value;
	var MailId = document.getElementById("Emailid").value;
	var telephone = document.getElementById("Telephone").value;
	var mobile = document.getElementById("Mobile").value;
	
	var tableRow = "<tr><td>"+FirstName+"</td>" +
					"<td>"+Title+"</td>" +
					"<td>"+MailId+"</td>" +
					"<td>"+telephone+"</td>" +
					"<td>"+mobile+"</td></tr>";
	
	$("#contactTable > tbody").append(tableRow);
	//alert("tableupdated");
}


function printContent()
{
	var win = window.open();
	win.document.write($('.Physical1').html());
	win.print();
	win.close();

}


/*function add()
{
	alert(CIFS);
	var ArraySelection = document.getElementById("servers1");
	ArraySelection.innerHTML("HELLO WORLD");
	
	while(ArraySelection.hasChildNodes())
	{
		ArraySelection.removeChild(ArraySelection.lastChild);
	}

for(var i=0; i<ViPRArrays;i++)
	{
		ArraySelection.appendChild(document.createTextNode((i+1)+". Array name - Last 4 digits os S/N"));
		var input = document.createElement("input");
		input.type = "text";
		ArraySelection.appendChild(input);
		ArraySelection.appendChild(document.createElement("br"));
	}

}*/

/*var ArraySelection = document.getElementsBy
alert(ArraySelection);*/