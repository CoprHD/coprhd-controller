package vijay;

import java.io.Console;
import java.io.IOException;
import java.sql.Connection;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet implementation class ContactFetch5
 */
public class ContactFetch5 extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{
		
		// TODO Auto-generated method stub
	/*	Boolean flag, flag1;
		String Name = request.getParameter("username");
		String Title = request.getParameter("title");
		String mailId = request.getParameter("email");
		String telephone = request.getParameter("telephone");
		String mobile = request.getParameter("mobile");
		JSONObject jsonObj = new JSONObject();*/
	//	request.setAttribute(arg0, arg1);
		System.out.println("received");
		//String values[] = request.getParameterValues("toServer");
		
		String jsonobj = request.getParameter("myJsonString"); 
		
		System.out.println("received2");
		
		try {
			JSONObject object = new JSONObject(jsonobj);
			System.out.println(object.toString());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
/*		System.out.println(Name);
		System.out.println(Title);
		System.out.println(mailId);
		System.out.println(telephone);
		System.out.println(mobile);*/
		
	/*	try {
				Connection con = ConnectToDatabase.connect();
				flag = ContactIntoDatabase.validateContactAndCheckIn(Name, Title, mailId, telephone, mobile, con);
			
				if(flag=true)
				{
					System.out.println("table updated");
				}else{
					System.out.println("table not updated");
				}
			*/
				

			/*String[][] contacts = ContactIntoDatabase.getDataFromDatabase(con);
			request.setAttribute("contacts", contacts);
			
			RequestDispatcher rd = request.getRequestDispatcher("./bootstrap/js/getInformation.js");
			rd.forward(request, response);
			*/
			
			/*JSONArray message = ContactIntoDatabase.getContactsAsJSONArray(con);
			
			
			System.out.println(message.toString());
			
			
			jsonObj.put("array", message);*/
			
			//JSONObject jsobj = message.toJSONObject(message);
			
			
			//System.out.println(jsobj.length());
			
			
	
			
			
		/*} 
		catch (Exception e)
			{
			// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
		// TODO Auto-generated method stub
		/*RequestDispatcher rd = request.getRequestDispatcher("./bootstrap/js/getInformation.js");
		rd.forward(request, response);*/
		//response.getOutputStream().println(./bootstrap/js/getInformation.js);
	/*	response.setContentType("text/json");
		response.getWriter().write(jsonObj.toString());*/
		
		
		//RequestDispatcher rd1 = request.getRequestDispatcher("./bootstrap/js/getInformation.js");
		
		/*PrintWriter out1 = response.getWriter();
		out1.println("<script type=\"text/javascript\">getInformation();</script>");*/
		/*RequestDispatcher rd1 = request.getRequestDispatcher("./bootstrap/js/getInformation.js");
		out1.println("</script>");*/
		//rd.forward(request, response);
		//System.out.println("here");
		
		
		
		//rd1.include(request,response);
		//rd.forward(request, response);
		
	}
	

}
