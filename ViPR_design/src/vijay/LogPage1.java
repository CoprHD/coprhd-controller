package vijay;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class LogPage1
 */
public class LogPage1 extends HttpServlet 
{
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#service(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
		{
			// TODO Auto-generated method stub
			boolean flag;
			
			String userName = request.getParameter("username");
			String password = request.getParameter("password");
			
			
			System.out.println("UserName is " +userName);
			System.out.println("Password is " +password);
			
			PrintWriter out = response.getWriter();
			
			
				Connection con;
				try {
					con = ConnectToDatabase.connect();
					flag = ContactIntoDatabase.validateUser(con, userName, password);
					
					if(flag)
					{
						response.sendRedirect("/ViPR_design/Contacts.html");
					}else{
						System.out.println("username or password is wrong");
						out.println("Invalid username and password");
						
						
					}
				} catch (ClassNotFoundException | SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
								
		}

}
