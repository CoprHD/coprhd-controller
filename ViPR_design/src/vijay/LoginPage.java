package vijay;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class LoginPage
 */
public class LoginPage extends HttpServlet
{
	private static final long serialVersionUID = 1L;

       
    /**
     * @throws IOException 
     * @see HttpServlet#HttpServlet()
     */
    public void LoginServlet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
    	
    	PrintWriter out = response.getWriter();
    	
    	String userName = request.getParameter("username");
    	String password = request.getParameter("password");
    	
    	
    	System.out.println("UserName is " +userName);
    	System.out.println("Password is ." +password);
    	
    
    }
    
	public void doGet()
	{
		
	}
	public void doPost()
	{
		
	}
   
 }