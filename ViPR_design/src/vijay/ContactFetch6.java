package vijay;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Servlet implementation class ContactFetch6
 */
public class ContactFetch6 extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{
		/*JSONObject jsonobj = new JSONObject();
		// TODO Auto-generated method stub
		try {
				Connection con = ConnectToDatabase.connect();
			
				JSONArray array1 = ContactIntoDatabase.getContactsAsJSONArray(con);
			
				jsonobj.put("jsonArray", array1);
			
			} catch (ClassNotFoundException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		System.out.println("POST");
		response.setContentType("text/xml");
		PrintWriter out = response.getWriter();
		String name = "Success";
		out.println(name);
		
	}

}
