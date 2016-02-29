package vijay;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class Hackathon_fun_login
 */
public class Hackathon_fun_login extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#service(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		
		PrintWriter out = response.getWriter();
		
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		 
		if(username.equalsIgnoreCase("admin") && password.equalsIgnoreCase("password"))
		{
			response.sendRedirect("/ViPR_design/Hackathon_fun_welcome.html");
		}else
		{
			System.out.println("Wrong username or password");
			out.println("Invalid credentials");
		}
	}

}
