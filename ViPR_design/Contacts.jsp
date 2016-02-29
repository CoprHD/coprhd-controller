<!DOCTYPE html>
<html>
<head>
	<meta charset="ISO-8859-1">
	<title>Contacts Table</title>

	<link rel="shortcut icon" href="../../assets/ico/favicon.ico">
	 
	
	
	<!-- Twitter Bootstrap core CSS -->
    <link href="./bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <!-- Custom styles for this template -->
    <link href="./bootstrap/css/fun.css" rel="stylesheet">
</head>
<body>
	
	
		
	<div class="navbar navbar-inverse navbar-fixed-top" role="navigation">
		<div class="container-fluid">
			<div class="navbar-header">
				<button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
					<span class="sr-only">Toggle navigation</span>
					<span class="icon-bar"></span>
					<span class="icon-bar"></span>
					<span class="icon-bar"></span>
				</button>
				<a class="navbar-brand" href="#">Contacts table </a>
			</div>
		</div>
	</div>

	<div class="container-fluid">
		<table class="table table-hover">
			
			<thead>
				<tr>
					<th> Name </th>
					<th> Role/Title </th>
					<th> Email address </th>
					<th> Telephone No </th>
					<th> Mobile No </th> 
				</tr>
			</thead>
			<tbody>
				<tr>
					<td> Don Hellums </td>
					<td> SA </td>
					<td> don.hellums@emc.com </td>
					<td> 672-4377 </td>
					<td> 850-212-0195 </td>
				</tr>
				
			</tbody>
			<tbody>
			</tbody>
			
		</table>
		
		
	</div>
	
	<form class="details form-horizontal" action="ContactFetch">
		<div class=" heading container-fluid ">
			<h1 > Contact Details</h1>
		</div>
		<div class="form-group">
			<div class="col-md-7 container-fluid">
				<label for="name" class="col-md-2 Contact-name"> Enter name</label>
				<div class="col-md-4"> 
					<input type="text" class="form-control" id="first name" name="username">
				</div>			
			</div>
		</div>	
		
		<div class="form-group">
			<div class="col-md-7 container-fluid">
				<label for="Role" class="col-md-2 Role/Title">Enter Role/Title</label>
				<div class="col-md-4"> 
					<input type="text" class="form-control" id="title" name="title">
				</div>			
			</div>
		</div>
		
		<div class="form-group">
			<div class="col-md-7 container-fluid">
				<label for="Email" class="col-md-2 Email"> Enter mail Id</label>
				<div class="col-md-4"> 
					<input type="text" class="form-control" id="Emailid" name="email">
				</div>			
			</div>
		</div>
		<div class="form-group">
			<div class="col-md-7 container-fluid">
				<label for="telephone" class="col-md-2 telephone"> Enter Telephone no</label>
				<div class="col-md-4"> 
					<input type="text" class="form-control" id="Telephone" name="telephone">
				</div>			
			</div>
		</div>
		<div class="form-group">
			<div class="col-md-7 container-fluid">
				<label for="Mobile" class="col-md-2 Mobile"> Enter Mobile No</label>
				<div class="col-md-4"> 
					<input type="text" class="form-control" id="Mobile" name="mobile">
				</div>			
			</div>
		</div>
		<div class="col-md-5 button1">
			<button type="submit" class="btn btn-primary btn-lg"> Save </button>
			<button type="button" class="btn btn-primary btn-lg button2"> Next </button>
		</div>
		
		
	</form>
	
	
	<script src="./bootstrap/js/jquery.js"></script>

	<!-- Twitter Bootstrap -->
	<script src="./bootstrap/js/bootstrap.min.js"></script>
	
</body>
</html>