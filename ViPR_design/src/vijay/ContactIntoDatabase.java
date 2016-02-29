package vijay;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.JSONArray;

import com.mysql.jdbc.PreparedStatement;
import com.sun.corba.se.spi.orbutil.fsm.State;

public class ContactIntoDatabase 
{
	static Statement st;
	public static boolean validateContactAndCheckIn(String username, String title, String Email, String telephone, String mobile, Connection con) throws ClassNotFoundException, SQLException
	{
		/*Class.forName("com.mysql.jdbc.Driver");
		
		
		
		Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/ViPR","root","SSGiis123");*/
	//	String str = "UPDATE contact SET username = '" + username + "', title ='" + title + "', email ='" + Email + "', telephone='" + telephone + "', mobile='"+ mobile + "';";
		
		String str = "INSERT INTO contact(username,title,email,telephone,mobile) VALUES (?, ?, ?, ?, ?)";
		PreparedStatement statement = (PreparedStatement) con.prepareStatement(str);
		statement.setString(1, username);
		statement.setString(2, title);
		statement.setString(3, Email);
		statement.setString(4, telephone);
		statement.setString(5, mobile);
		
		System.out.println("query : " +str); 
		
		
		
		int rows = statement.executeUpdate();
		
		if(rows>0)
		{
			System.out.println("table updated");
			return true;
		}else{
			System.out.println("Table not updated");
			return false;
		}
		
		
		
		
	}
	
	// get data from database
	public static String[][] getDataFromDatabase(Connection con) throws SQLException
	{
		int i = 1,j = 0;
		String fetch = "SELECT * FROM contact;";
		
		st = con.createStatement();
		System.out.println(st);
		ResultSet rs = st.executeQuery(fetch);
		
		String[][] Contacts = new String[15][5];
		
		while(rs.next())
		{
			if (i < 15)
			{
				Contacts[i][0] = rs.getString("username");
				Contacts[i][1] = rs.getString("title");
				Contacts[i][2] = rs.getString("email");
				Contacts[i][3] = rs.getString("telephone");
				Contacts[i][4] = rs.getString("mobile");	
				i++;
			}
		}
		
		for(int k=1; k<i;k++)
		{
			for(j=0;j<5;j++)
			{
				System.out.println("Contact = " + Contacts[k][j]);
			}
		}
		return Contacts;
		
		
	}
	
	// converting the recevied array from database to JSON object
	
	public static JSONArray getContactsAsJSONArray(Connection con) throws Exception 
	{
		
		String fetch = "SELECT * FROM contact;";


		st = con.createStatement();
		ResultSet rs = st.executeQuery(fetch);
		
		
		if(rs.next())
		{
			return Convertor.convertResultSetIntoJSON(rs);
		} else
			
		return null;
		
				
	}
	
	public static boolean validateUser( Connection con, String userEnteredname, String userEnteredpassword ) throws SQLException
	{
		String [] userValidate = new String[10];
		int i=0;
		String queryUsername = "SELECT * FROM login;" ;
		st = con.createStatement();
		System.out.println(st);
		ResultSet rs1 = st.executeQuery(queryUsername);
		while(rs1.next() && i<10)
		{
			userValidate[i] = rs1.getString("username");
			if(userValidate[i].equalsIgnoreCase(userEnteredname))
			{
				String actualPassword = rs1.getString("password");
				if(actualPassword.equals(userEnteredpassword))
						{
							System.out.println("username and Password match");
							return true;
							
						}
				else{
					System.out.println("password is wrong");
				}
			}
			else
			{
				i++;
			}
		}
		
		return false;
	}
	
	public static void main(String [] args) throws ClassNotFoundException, SQLException
	{
		//String name = "kishore";
	//	ContactIntoDatabase.validateContactAndCheckIn("kishore2", "Soft", "Email", "654678745", "234556577");
		
		Class.forName("com.mysql.jdbc.Driver");
		
		
		
		Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/ViPR","root","SSGiis123");
		
		//ContactIntoDatabase.getDataFromDatabase(con);
		
		boolean flag = ContactIntoDatabase.validateUser(con, "admi", "passwor");
		
		System.out.println(flag);
	}

}
