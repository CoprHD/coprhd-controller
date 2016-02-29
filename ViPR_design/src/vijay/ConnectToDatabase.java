package vijay;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;




public class ConnectToDatabase 
{
	
	public static Connection connect() throws ClassNotFoundException, SQLException
	{
		Class.forName("com.mysql.jdbc.Driver");
		
		Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/ViPR","root","SSGiis123");
		
		return con;
		
	}

}
