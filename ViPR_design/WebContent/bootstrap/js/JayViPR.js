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
	var Fabricvendor = document.getElementById('vendor').value;
	var NumberOfFabrics = document.getElementById('NumberFabrics').value;
/*	var ViPRSolPak = document.getElementById('ViPRSolPak1').value;
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
	var CIFS = document.getElementById('CIFS1').value;*/

	
	//var ArraySelection = document.getElementById("servers1");
	//var SupportedVersion = document.getElementById("supportedVersions");
	var FabricTabs = document.getElementById("fabrics1");
	var tabcontent = document.getElementById("tabcontentFull");
	
/*	var ServerSelection = document.getElementById("serversSelection1");
	var ServerSupport = document.getElementById("serverSupportedVersions");
	var FabricSelection = document.getElementById("fabricsSelection1");
	var FabricSupport = document.getElementById("FabricSupportedVersions");*/
	
	
	//ServerSelection.innerHTML="HELLO";
	//alert(ServerSelection);
	//ArraySelection.innerHTML="HELLO WORLD";
	
	
	//alert(ArraySelection);
	while(FabricTabs.hasChildNodes())
		{
			FabricTabs.removeChild(FabricTabs.lastChild);
		}
	/*while(ArraySelection.hasChildNodes() || SupportedVersion.hasChildNodes())
	{
		ArraySelection.removeChild(ArraySelection.lastChild);
		SupportedVersion.removeChild(SupportedVersion.lastChild);
	}*/
	
	for(var m=0; m<NumberOfFabrics;m++)
		{
			
			
			
			if(m==0)
				{
					$('#fabrics1').append('<li class=\"active\"><a href=\"#Fabric'+(m+1)+'\" data-toggle=\"tab\" >Fabric'+(m+1)+'</a></li>');
					
				}
			else
				{
					$('#fabrics1').append('<li><a href=\"#Fabric'+(m+1)+'\" data-toggle=\"tab\" >Fabric'+(m+1)+'</a></li>');
					
					
				}
			
		}
	
	/*for(var z=0; z<NumberOfFabrics; z++)
		{
			var tabcontent1 = document.createElement('div');
			
			if(z=0)
				{
				tabcontent1.className = "tab-pane fade in active container-fluid form-horizontal col-md-12";
				}
			else
				tabcontent1.className = "tab-pane fade active container-fluid form-horizontal col-md-12";
			tabcontent1.id = "Fabric"+(z+1);
			tabcontent.appendChild(tabcontent1);
			
		}*/
	
	 for(var i=0; i<NumberOfFabrics;i++)
		{
			var contentPage1 = document.getElementById("tabcontentFull");
			var contentPage = document.createElement('div');
			contentPage.id = "Fabric"+(i+1);
			if(i==0)
				{
					contentPage.className = "tab-pane fade in active container-fluid form-horizontal col-md-12 Fabric1";
				}
			else 
			{
				contentPage.className = "tab-pane fade container-fluid form-horizontal col-md-12 serverSelectionBody"+(i+1); 
			}
		
			
			contentPage1.appendChild(contentPage);
			
			input("labeldiv","inputdiv"," Enter the Host 1 WWPN -"+(i+1),i, contentPage,"ArrayNameID"+(i+1));
			
	//  Select for Array type
			
	
			
			input1("2ndwwpn","2ndwwpninput","Enter the Host 2 WWPN -"+(i+1),contentPage, "2ndWWPNID"+(i+1));
			
			var ArrayFEPorts = document.createElement('div');
			ArrayFEPorts.className="col-md-5";
			ArrayFEPorts.innerHTML = "<label> Enter the Array FE port - 1 </label>";
			contentPage.appendChild(ArrayFEPorts);
			var ModelArraydiv = document.createElement('div');
			ModelArraydiv.className="col-md-7"; 
			ModelArraydiv.innerHTML="<input class=\"form-control\" id=\"ModelInputID"+(i+1) +"\"><br>";
			contentPage.appendChild(ModelArraydiv);
			
			
			var ActiveZoneSet = document.createElement('div');
			ActiveZoneSet.className="col-md-5";
			ActiveZoneSet.innerHTML = "<label> Enter the Active ZoneSet </label>";
			contentPage.appendChild(ActiveZoneSet);
			var ActiveZoneSet1 = document.createElement('div');
			ActiveZoneSet1.className="col-md-7"; 
			ActiveZoneSet1.innerHTML="<input class=\"form-control\" id=\"ActiveZoneSet"+(i+1) +"\"><br>";
			contentPage.appendChild(ActiveZoneSet1);
			
			
			//modellabeldiv.appendChild(document.createElement('br'));
			//modellabeldiv.appendChild(document.createElement('br'));
			
			var modellabeldiv1 = document.createElement('div');
			modellabeldiv1.className="col-md-5";
			modellabeldiv1.innerHTML = "<label> Enter the 1st FC Alias Name </label>";
			contentPage.appendChild(modellabeldiv1);
			var Modelinputdiv1 = document.createElement('div');
			Modelinputdiv1.className="col-md-7"; 
			Modelinputdiv1.innerHTML="<input class=\"form-control\" id=\"1stFCAlias"+(i+1) +"\"><br>";
			contentPage.appendChild(Modelinputdiv1);
			
			
			var modellabeldiv2 = document.createElement('div');
			modellabeldiv2.className="col-md-5";
			modellabeldiv2.innerHTML = "<label> Enter the 2nd FC Alias Name </label>";
			contentPage.appendChild(modellabeldiv2);
			var Modelinputdiv2 = document.createElement('div');
			Modelinputdiv2.className="col-md-7"; 
			Modelinputdiv2.innerHTML="<input class=\"form-control\" id=\"2ndFCAlias"+(i+1) +"\"><br>";
			contentPage.appendChild(Modelinputdiv2);
			
			var ArrayFEPorts1 = document.createElement('div');
			ArrayFEPorts1.className="col-md-5";
			ArrayFEPorts1.innerHTML = "<label> Enter the Array FE port- 2</label>";
			contentPage.appendChild(ArrayFEPorts1);
			var ModelArraydiv1 = document.createElement('div');
			ModelArraydiv1.className="col-md-7"; 
			ModelArraydiv1.innerHTML="<input class=\"form-control\" id=\"2ndArrayFE"+(i+1) +"\"><br>";
			contentPage.appendChild(ModelArraydiv1);
			
			input1("vsanNum","vsanNuminput","Enter the vSAN Number ",contentPage, "vSANNum"+(i+1));
			
			
			if((i+1)==NumberOfFabrics)
				{
					var saveDiv = document.createElement('div');
					saveDiv.className = "SaveButton col-md-12";
					saveDiv.id = "AllSaveButton";
					saveDiv.innerHTML="<button type=\"button\" class=\"btn btn-primary btn-lg\" id=\"AllSave\" data-toggle=\"modal\" data-target=\"#ConsolidatedData\" >SAVE</button>";
					contentPage.appendChild(saveDiv);
				}
			
	// Protocols
			
			
			
			/*var Protocoloptions = [" ","iSCSI", "FC", "NFS", "CIFS", "SCALE IO"];
			select("ProtocolselectLabelDiv","Protocol","ProtocolselectDiv",ArraySelection,Protocoloptions, "ProtocolSelect", "ProtocolSelectOption" ,"ProtocolId"+(i+1));*/
			
		//Array VDC
			
			
			/*var VDCoptions = [" ","Greenfield", "coexist", "Ingestion"];
			var VDCselectDiv1 =	select("VDCselectLabelDiv","Arrays VDC state","VDCselectDiv",ArraySelection,VDCoptions, "VDCSelect", "VDCSelectOption","VDCID"+(i+1));
			var rows = 10;
			var SupportedTextdiv1 = suggestionInformation("ArraySuggestionlabeldiv",SupportedVersion,"ArraySupportedTextdiv", rows, "sugestionID"+(i+1));*/
			
			/*VDCselectDiv1.appendChild(document.createElement('br'));
			VDCselectDiv1.appendChild(document.createElement('br'));
			SupportedTextdiv1.appendChild(document.createElement('br'));
			SupportedTextdiv1.appendChild(document.createElement('br'));*/
	
			
			
		}
	

	
	//document.getElementById("AllSave").setAttribute("onclick", "saveInformation("+ViPRArrays,NumberofServers,Fabrics+")");
	document.getElementById("AllSave").onclick = function(){saveInformation(NumberOfFabrics);};
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

function input1(labeldiv1,inputdiv1,labelName1, ArraySelection1, inputID1)
{
	labeldiv1 = document.createElement('div');
	labeldiv1.className="col-md-5";
	labeldiv1.innerHTML = "<label>"+ labelName1+"</label>";
	ArraySelection1.appendChild(labeldiv1);
	inputdiv1 = document.createElement('div');
	inputdiv1.className="col-md-7";
	inputdiv1.innerHTML="<input class=\"form-control\" id=\""+ inputID1 +"\"><br>";
	ArraySelection1.appendChild(inputdiv1);
	
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


function saveInformation(NumberOfFabrics)
{
	
	var Paragraphdiv = document.createElement('div');
	Paragraphdiv.className = "paragraphContent col-md-12 jumbotron";
	
	var paragraphElement = document.createElement("pre");
	Paragraphdiv.appendChild(paragraphElement);
	
	for(var k=0;k<NumberOfFabrics;k++)
		{
			var Host1WWPN_1 = document.getElementById("ArrayNameID"+(k+1)).value;
			var Host2wwpn_1 = document.getElementById("2ndWWPNID"+(k+1)).value;
			var ArrayFEPort1 = document.getElementById("ModelInputID"+(k+1)).value;
			var ArrayFEPort2 = document.getElementById("2ndArrayFE"+(k+1)).value;
			var ActiveZoneset = document.getElementById("ActiveZoneSet"+(k+1)).value;
			var FCAlias1 = document.getElementById("1stFCAlias"+(k+1)).value;
			var FCAlias2 = document.getElementById("2ndFCAlias"+(k+1)).value;
			var vsanNum1 = document.getElementById("vSANNum"+(k+1)).value;
			
			
			var ConsolidatedFabric = (k+1)+". Fabric "+(k+1) +"\n"+
									   "Host1 \n\n\n"+
									   "copy running-config startup-config \n \n"+
									   "fcalias name "+FCAlias1+" vsan "+vsanNum1+" \n"+
									   "member pwwn "+Host1WWPN_1+" \n\n"+
									   "Sh flogi database | grep "+Host1WWPN_1+" \n"+

									   "sh zone member pwwn "+Host1WWPN_1+" \n"+
									   "sh fcalias name | grep "+Host1WWPN_1+" \n \n"+
									  
									   "zone name Z_"+FCAlias1+"-"+ArrayFEPort1+" vsan "+vsanNum1+" \n"+
									   "member fcalias "+FCAlias1+" \n"+
									   "member fcalias "+ArrayFEPort1+" \n\n"+
									   
									   "zone name Z_"+FCAlias1+"-"+ArrayFEPort2+" vsan "+vsanNum1+" \n"+
									   "member fcalias "+FCAlias1+" \n"+
									   "member fcalias "+ArrayFEPort2+" \n\n"+
									   
									   "zoneset name "+ActiveZoneset+" vsan "+vsanNum1+" \n"+
									   "member Z_"+FCAlias1+"-"+ArrayFEPort1+" \n"+
									   "member Z_"+FCAlias1+"-"+ArrayFEPort2+" \n \n"+
									   
									   "zoneset activate name "+ActiveZoneset+" vsan "+vsanNum1+" \n \n \n \n"+
									   
									   
									   "Host2 \n \n \n"+
									   
									   "copy running-config startup-config \n \n"+
									   "fcalias name "+FCAlias2+" vsan "+vsanNum1+" \n"+
									   "member pwwn "+Host2wwpn_1+" \n\n"+
									   "Sh flogi database | grep "+Host2wwpn_1+" \n"+

									   "sh zone member pwwn "+Host2wwpn_1+" \n"+
									   "sh fcalias name | grep "+Host2wwpn_1+" \n \n"+
									  
									   "zone name Z_"+FCAlias2+"-"+ArrayFEPort1+" vsan "+vsanNum1+" \n"+
									   "member fcalias "+FCAlias2+" \n"+
									   "member fcalias "+ArrayFEPort1+" \n\n"+
									   
									   "zone name Z_"+FCAlias2+"-"+ArrayFEPort2+" vsan "+vsanNum1+" \n"+
									   "member fcalias "+FCAlias2+" \n"+
									   "member fcalias "+ArrayFEPort2+" \n\n"+
									   
									   "zoneset name "+ActiveZoneset+" vsan "+vsanNum1+" \n"+
									   "member Z_"+FCAlias2+"-"+ArrayFEPort1+" \n"+
									   "member Z_"+FCAlias2+"-"+ArrayFEPort2+" \n \n"+
									   
									   "zoneset activate name "+ActiveZoneset+" vsan "+vsanNum1+" \n \n \n \n";
			
			
			var contentNode = document.createTextNode(ConsolidatedFabric);
			paragraphElement.appendChild(contentNode);
			document.getElementById("paragraphId").appendChild(Paragraphdiv);
							   

			
									   
			/*alert(Host1WWPN_1);
			alert(Host2wwpn_1);
			alert(ArrayFEPort1);
			alert(ArrayFEPort2);
			alert(ActiveZoneset);
			alert(FCAlias1);
			alert(FCAlias2);
			alert(vsanNum1);*/
			
			
			
		}
	
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