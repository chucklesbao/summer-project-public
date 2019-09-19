import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.Date;
import java.util.ArrayList;

class receiver //takes in all the messages
{
	private DatagramSocket socket;
	private byte b[] = new byte[1024];
	private DatagramPacket packet;
	
	public receiver(DatagramSocket socket) //sets up the socket to receive data
	{
		this.socket = socket;
	}
	
	public String readMessage()throws IOException // gets the packets sent to the socket and then returns the string
	{
		packet = new DatagramPacket(b, b.length);
		socket.receive(packet);
		String received = new String(packet.getData(), 0, packet.getLength());
		return received;
	}
	
	public String getIP() //returns the string version of the IP address of the received packet
	{
		return packet.getAddress().toString().substring(1);
	}
	
	public int getPort() //returns the port of the sender
	{
		return packet.getPort();
	}
	
	public void close() throws IOException //closes the port
	{
		socket.close();
	}
}

class sendMessage extends Thread //sends messages
{
	private byte b[];
	private String message;
	private String IP;
	private int port;
	private int ack;
	private int iterations;
	private boolean received;
	private int index;
	
	public sendMessage(String message, String IP, int port, int ack) //constructor
	{
		this.message = message;
		this.IP = IP;
		this.port = port;
		this.ack = ack;
	}
	
	@Override
	public void run()
	{
		String[] mTemp = {message,Integer.toString(ack)};
		UDPserver.messages.add(mTemp);
		iterations = 0;
		received = false;
		ack();
		if(!received)
		{
			for (int i = 0; i < UDPserver.messages.size();i++)
			{
				if(UDPserver.messages.get(i)[0].equals(message) && UDPserver.messages.get(i)[1].equals(Integer.toString(ack)))
				{
					index = i;
				}	
			}
			UDPserver.messages.remove(index);
		}
	}
	
	public void ack() //sends the message and waits for an ack
	{
		try	
		{
			b = message.getBytes();
			DatagramPacket packet = new DatagramPacket(b,b.length,InetAddress.getByName(IP),port);
			UDPserver.socket.send(packet);
			Thread.sleep(700);
			while(iterations < 7 && !received) //message sends up to 7 times
			{
				for (int i = 0; i < UDPserver.messages.size();i++)
				{
					if(UDPserver.messages.get(i)[1].equals(Integer.toString(ack)))
					{
						iterations++;
						ack();
					}
					else if(i == UDPserver.messages.size()-1)
					{
						received = true;
					}
				}
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
	}
	
	public static void noAck(String message, String IP, int Port)throws IOException //sends the message without an ack
	{
		byte b[];
		b = message.getBytes();
		DatagramPacket packet = new DatagramPacket(b,b.length,InetAddress.getByName(IP),Port);
		UDPserver.socket.send(packet);
	}
}

class messageRouter extends Thread //takes in all the messages and decides what to do
{
	private receiver r;
	private dbCommands db;
	private String message;
	
	public messageRouter(receiver r,dbCommands db) //constructor
	{
		this.r=r;
		this.db = db;
	}
	
	@Override
	public void run()
	{
		while(true)
		{
			try {message = r.readMessage();}catch(IOException e){e.printStackTrace();}
			String sendIP = r.getIP();
			int sendPort = r.getPort();
			boolean newMessage = true;
			
			if(!(message.indexOf("/ackno")>=0 && message.substring(0, 6).equals("/ackno"))) //checks to see if the message is an ack message
			{
				String returnAck = getAck(message);
				try{sendMessage.noAck("/ackno"+returnAck, sendIP, sendPort);}catch(IOException e) {e.printStackTrace();}
			}
			else if(message.indexOf("/ackno")>=0 && message.substring(0, 6).equals("/ackno")) //deletes the message from the messages arrayList which tells the sendMessage class that the message was delivered
			{
				String ackNum = message.substring(6);
				for (int i = 0; i < UDPserver.messages.size();i++)
				{
					if(UDPserver.messages.get(i)[1].equals(ackNum))
					{
						UDPserver.messages.remove(i);
					}
				}
			}
			
			for(String s[] : UDPserver.rMessages ) //checks to see if the message is a new message
			{
				if(s[0].equals(message))
				{
					newMessage = false;	
				}
			}
			
			if(newMessage) //only runs if the message is a new one
			{
				String[] readMessage = {message,Long.toString(new Date().getTime())};
				UDPserver.rMessages.add(readMessage);
				int sendID = db.getID(sendIP, sendPort);
				String sendU = db.getUsername(sendID);
				Date d = new Date();
				int ack = ackNum();
				
				System.out.println(db.getUsername(db.getID(r.getIP(), r.getPort()))+": "+message);
				if(message.indexOf("/login")>=0 && message.substring(0,6).equals("/login")) //checks to see if the message is a login message
				{
					
					int userLength = getInfo(message);
					String username = message.substring(25,25+userLength);
					String password = message.substring(25+userLength);
					sendID = db.getID(username);
					if(sendID != -1 && db.getPW(sendID).equals(db.convertPW(password)) && !db.getOnline(sendID)) //checks to see if the username and password are correct
					{
						System.out.println("New login from "+username+" at "+sendIP+" on port "+sendPort);
						db.updateUser(sendID, sendIP, sendPort);
						db.setOnline(sendID);
						try{onLogin(sendID, ack);}catch(IOException e) {e.printStackTrace();}
					}
					else if(sendID != -1 && db.getPW(sendID).equals(db.convertPW(password)) && db.getOnline(db.getID(username))) //checks to see if the user is already online
					{
						String sendMessage = header("/loger",ack)+"User is already online";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
					else if(sendID !=- 1 && !db.getPW(sendID).equals(db.convertPW(password))) //checks to see if password is incorrect
					{
						String sendMessage = header("/loger",ack)+"Password is incorrect";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
					else if(sendID == -1)  //checks to see if the user exists
					{
						String sendMessage = header("/loger",ack)+"User does not exist";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
				}
				else if(message.indexOf("/regis")>=0 && message.substring(0,6).equals("/regis")) //checks to see if the message is a register new user message
				{
					int userLength = getInfo(message);
					String username = message.substring(25,25+userLength);
					String password = message.substring(25+userLength);
					int ID = db.getID(username);
					if(ID != -1) //checks to see if the username is already used
					{
						String sendMessage = header("/loger",ack)+"username already taken";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
					else //registers and logs the new user in
					{
						System.out.println("New user "+username+" at "+sendIP+" on port "+sendPort);
						db.addUser(username, password, sendIP, sendPort);
						sendID = db.getID(username);
						db.setOnline(sendID);
						String sendMessage = header("/login",ack)+"Connection to server successful. Please use \"/message\" to start a chat session with someone";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
				}
				else if(message.indexOf("/nsess")>=0 && message.substring(0,6).equals("/nsess") && message.substring(25,34).equals("/message ")) // checks to see if the message is a start session command
				{
					String receiveU = message.substring(34);
					int receiveID = db.getID(receiveU);
					
					if(sendU.equals(receiveU)) //checks to see if the user tried to start a session with themselves
					{
						String sendMessage = header("/error",ack)+"Can't start a session with yourself!";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
					else if(db.getID(receiveU) == -1) //checks to see if the receiving user exists
					{
						String sendMessage = header("/error",ack)+"Invalid username error!";
						sendMessage m = new sendMessage(sendMessage,sendIP,sendPort,ack);
						m.start();
					}
					else //sends session start messages
					{
						String sendMessage1 = header("/sessi"+receiveID,ack)+"Starting session with "+receiveU;
						sendMessage m1 = new sendMessage(sendMessage1,sendIP,sendPort,ack);
						m1.start();
						
						int ack2 = ackNum();
						String sendMessage2 = header("/sessi"+sendID,ack2)+"Starting session with "+sendU;
						sendMessage m2 = new sendMessage(sendMessage2,db.getIP(receiveID),db.getPort(receiveID),ack2);
						m2.start();

						System.out.println("starting session between "+sendU+" and "+ receiveU);
						// need to add ability for users to login and already be in a session
					}
				}
				else if(message.indexOf("/messg")>=0 && message.substring(0,6).equals("/messg")) //checks to see if the message is from a user currently in a session
				{
					int receiveID = getInfo(message);
					message = message.substring(25);
					try
					{
						if(!db.getOnline(receiveID)) //checks to see if the receiver is online. if the receiver is offline, the message will be sent to them later
						{
							db.addOfflineMessage(sendU, receiveID, message, d.toString());
							db.addMessage(sendU, db.getUsername(receiveID), message, d.toString());
						}
						else //if the receiver is online, it will send
						{
							String sendMessage = header("/messg",ack)+sendU+": "+message;
							sendMessage m = new sendMessage(sendMessage,db.getIP(receiveID),db.getPort(receiveID),ack);
							m.start();
							db.addMessage(sendU, db.getUsername(receiveID), message, d.toString());
						}
					}catch(Exception e) {e.printStackTrace();}
					
				}
				else if(message.indexOf("/esess")>=0 && message.substring(0,6).equals("/esess")) //checks to see of the message is a bounce error message telling a user that the person they want to start a session with is already in a session
				{
					int receiveID = getInfo(message);
					String sendMessage = header("/esess",ack);
					sendMessage m = new sendMessage(sendMessage, db.getIP(receiveID),db.getPort(receiveID),ack);
					m.start();
				}
				else if(message.indexOf("/endse")>=0 && message.substring(0,6).equals("/endse")) //checks to see if the message is an end session command
				{
					int receiveID = getInfo(message);
					String sendMessage = header("/endse",ack);
					sendMessage m = new sendMessage(sendMessage, db.getIP(receiveID),db.getPort(receiveID),ack);
					m.start();
				}
				else if(message.indexOf("/nsess")>=0 && message.substring(0,6).equals("/nsess")) //sends an invalid message error
				{
					String sendMessage = header("/error",ack)+"Invalid message command";
					sendMessage m = new sendMessage(sendMessage, sendIP,sendPort,ack);
					m.start();
				}
			}	
		}
	}
	
	public void onLogin(int ID, int ack)throws IOException //runs when a user logs on
	{
		ArrayList<String[]> messages= db.getOfflineMessages(ID);
		if(messages.size()>0) //checks to see if the user has any offline messages
		{
			String sendMessage = header("/login",ack)+"Connection to server successful. You have offline messages";
			sendMessage m = new sendMessage(sendMessage,db.getIP(ID),db.getPort(ID),ack);
			m.start();
			for(String[] o : messages)
			{
				sendMessage = header("/omess",ack)+o[0]+": "+o[1]+" at "+o[2];
				m = new sendMessage(sendMessage,db.getIP(ID),db.getPort(ID),ack);
				m.start();
			}
			sendMessage = header("/login",ack)+"Please use \"/message\" to start a chat session with someone";
			m = new sendMessage(sendMessage,db.getIP(ID),db.getPort(ID),ack);
			m.start();
			db.deleteOfflineMessage(ID);
		}
		else
		{
			String sendMessage = header("/login",ack)+"Connection to server successful. Please use \"/message\" to start a chat session with someone";
			sendMessage m = new sendMessage(sendMessage,db.getIP(ID),db.getPort(ID),ack);
			m.start();
		}
	}
	
	public String header(String command,int num) //makes a message header given a message and an ack number
	{
		String header = command;
		String ack = "/ack"+num;
		
		while(header.length()<15)
		{
			header += ".";
		}
		while(ack.length()<10)
		{
			ack = ack+"-";
		}
		return header +ack;
	}
	
	public int ackNum() //creates a unique random 6 digit number for the ack
	{
		int ack = (int)(Math.random()*999999)+1;
		for(String[] s : UDPserver.messages )
		{
			if(s[1].equals(Integer.toString(ack)))
			{
				ack = ackNum();
			}
		}
		return ack;
	}
	
	public String getAck(String message) //returns the ack of a message
	{
		String returnAck;
		if (message.indexOf("-")>=0)
		{
			returnAck = message.substring(message.indexOf("/ack")+4,message.indexOf("-"));
		}
		else
		{
			returnAck = message.substring(message.indexOf("/ack")+4,25);
		}
		return returnAck;
	}
	
	public int getInfo(String message) //returns any info being sent in a message header
	{
		int info;
		if (message.indexOf(".")>=0)
		{
			info = Integer.parseInt(message.substring(6,message.indexOf(".")));	
		}
		else
		{
			info = Integer.parseInt(message.substring(6,15));
		}
		return info;
	}
}

class checkOnline extends Thread //checks to see which users are online
{
	private dbCommands db;
	
	public checkOnline(dbCommands db) //constructor
	{
		this.db = db;
	}
	
	@Override
	public void run()
	{
		while(true)
		{
			ArrayList<Integer> users = db.getOnlineUsers();
			for(int u : users)
			{
				long diff = new Date().getTime() - db.getLastOnlineMilli(u);
				if (Math.abs(diff) > 10000)
				{
					db.setOffline(u);
				}
			}
			try{Thread.sleep(500);}catch(Exception e) {e.printStackTrace();}
		}
	}
}

class dbCommands //holds all of the commands for the database
{
	private Connection myConn;
	
	public dbCommands() //sets up the connection
	{
		try
		{
			myConn = DriverManager.getConnection("jdbc:mysql://localhost:3306/table_name", "root", "password");
			System.out.println("Connection to database successful");
		}
		catch(SQLException e) 
		{
			e.printStackTrace();
		}
	}
	
	public String convertPW(String pw) //converts the password to the encrypted version
	{
		String cPW = "";
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT SHA2('"+pw+"',512)");
			if(rs.next())
			{
				cPW = rs.getString(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return cPW;
	}
	
	public String getUsername(int ID) //gets username based on ID
	{
		String un = "";
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT username FROM userinfo WHERE ID = "+ID);
			if(rs.next())
			{
				un = rs.getString(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return un;
	}
	
	public String getPW(int ID) //gets password based on ID
	{
		String un = "";
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT password FROM userinfo WHERE ID = "+ID);
			if(rs.next())
			{
				un = rs.getString(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return un;
	}
	
	public int getID (String username) //gets ID based on username
	{
		int ID = -1;
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT ID FROM userinfo WHERE username = '"+username+"'");
			if(rs.next())
			{
				ID = rs.getInt(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return ID;
	}
	
	public String getIP(int ID) //gets IP based on ID
	{
		String IP = "";
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT IP FROM userinfo WHERE ID = "+ID);
			if(rs.next())
			{
				IP = rs.getString(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return IP;
	}
	
	public int getPort (int ID) //gets port based on ID
	{
		int port = -1;
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT Port FROM userinfo WHERE ID = "+ID);
			if(rs.next())
			{
				port = rs.getInt(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return port;
	}
	
	public int getID(String IP, int port) //gets ID based on Ip and port
	{
		int ID = -1;
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT ID FROM userinfo WHERE IP = '"+IP+"' AND Port = "+port);
			if(rs.next())
			{
				ID = rs.getInt(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return ID;
	}
	
	public boolean getOnline(int ID) //checks to see if user is online
	{
		boolean online = false;
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT Online FROM userinfo WHERE ID = "+ID);
			if(rs.next())
			{
				online = rs.getBoolean(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return online;
	}
	
	public long getLastOnlineMilli(int ID) //checks to see when the user was last online
	{
		long online = -1;
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT LastOnlineMilli FROM userinfo WHERE ID = "+ID);
			if(rs.next())
			{
				online = rs.getLong(1);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return online;
	}
	
	public ArrayList<Integer> getOnlineUsers() //gets a list of all online users
	{
		ArrayList<Integer> users = new ArrayList<Integer>();
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT ID FROM userinfo WHERE Online = TRUE");
			while(rs.next())
			{
				users.add(rs.getInt("ID"));
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return users;
	}
	
	public ArrayList<String[]> getOfflineMessages(int ID) //gets a list of offline messages meant for a user
	{
		ArrayList<String[]> messages = new ArrayList<String[]>();
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("SELECT sender, message, time FROM offlinemessages WHERE receiver= "+ID);
			while(rs.next())
			{
				String[] temp = new String[3];
				temp[0] = rs.getString("sender");
				temp[1] = rs.getString("message");
				temp[2] = rs.getString("time");
				messages.add(temp);
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return messages;
	}
	
	public void updateUser(int ID, String IP, int port) //updates user data
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("UPDATE userinfo SET IP = '"+IP+"' WHERE ID = '"+ID+"'");
			stmnt.executeUpdate("UPDATE userinfo SET Port = '"+port+"' WHERE ID = '"+ID+"'");
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void addUser(String username, String password, String IP, int port) //adds or updates new logins to userinfo table. update is not currently in use
	{
		try
		{
			int id;
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("CALL getLastID()");
			if(rs.next())
			{
				id = rs.getInt(1)+1;
			}
			else
			{
				id = 1;
			}
			stmnt.executeUpdate("INSERT INTO userinfo (username, password, ID, IP, Port) VALUES('"+username+"',SHA2('"+password+"',512),"+id+",'"+IP+"',"+port+")");
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void addMessage(String sender, String receiver, String message, String time) //adds a message to messages table
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("INSERT INTO messages (sender, receiver, message, time) VALUES('"+sender+"','"+receiver+"','"+message+"','"+time+"')");
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void addOfflineMessage(String sender, int receiver, String message, String time) //adds a message to messages table
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("INSERT INTO offlinemessages (sender, receiver, message, time) VALUES('"+sender+"',"+receiver+",'"+message+"','"+time+"')");
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void deleteOfflineMessage(int ID) //deletes all offlines messages for a certain user
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("DELETE FROM offlinemessages WHERE receiver = "+ID);
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void printUsers() //not used but useful for debugging
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			ResultSet rs = stmnt.executeQuery("CALL showAllUsers()");
			while(rs.next())
			{
				System.out.println(rs.getString("username")+" / "+rs.getInt("ID")+" / "+rs.getString("IP")+":"+rs.getInt("Port"));
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void updateOnline(int ID, String time, long milli) //updates the LastOnline and LastOnlineMilli
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("UPDATE userinfo SET LastOnline = '"+time+"' WHERE ID = "+ID);
			stmnt.executeUpdate("UPDATE userinfo SET LastOnlineMilli = "+milli+" WHERE ID = "+ID);
			
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void setOnline(int ID) //sets the user to online
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("UPDATE userinfo SET Online = TRUE WHERE ID = "+ID);
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void setOffline(int ID) //sets the user to offline
	{
		try
		{
			Statement stmnt = myConn.createStatement();
			stmnt.executeUpdate("UPDATE userinfo SET Online = FALSE WHERE ID = "+ID);
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}	
}

class pingReceiver extends Thread
{
	private dbCommands db;
	private DatagramSocket socket;
	private byte b[] = new byte[1025];
	private DatagramPacket packet;
	private String message;
	
	public pingReceiver(dbCommands db, int rSocket) //constructor
	{
		this.db = db;
		try{socket = new DatagramSocket(rSocket);}catch(IOException e) {e.printStackTrace();}
	}
	
	@Override
	public void run()
	{
		while(true)
		{
			try{message = readMessage();}catch (IOException e) {e.printStackTrace();}
			if(message.equals("keepAlive"))
			{
				Date d = new Date();
				String sendIP = getIP();
				int sendPort = packet.getPort();
				int sendID = db.getID(sendIP, sendPort);
				db.setOnline(sendID);
				db.updateOnline(sendID, d.toString(), d.getTime());
			}
		}
	}
	
	public String readMessage()throws IOException // gets the packets sent to the socket and then returns the string
	{
		packet = new DatagramPacket(b, b.length);
		socket.receive(packet);
		String received = new String(packet.getData(), 0, packet.getLength());
		return received;
	}
	
	public String getIP() //returns the string version of the IP address of the received packet
	{
		return packet.getAddress().toString().substring(1);
	}
}

class messageManager extends Thread
{
	private boolean running;
	
	public messageManager() //constructor
	{
		running = true;
	}
	
	@Override
	public void run()
	{
		do
		{
			ArrayList<String[]> toRemove = new ArrayList<String[]>();
			for (String[] s: UDPserver.rMessages)
			{
				long diff = new Date().getTime()-Long.parseLong(s[1]);
				if(Math.abs(diff)>5600)
				{
					toRemove.add(s);
				}
			}
			UDPserver.rMessages.removeAll(toRemove);
			try{Thread.sleep(1000);}catch(Exception e) {e.printStackTrace();}
		}while(running);
	}
	
	public void stopThread() //stops the thread
	{
		running = false;
	}
}


public class UDPserver
{	
	public static ArrayList<String[]> messages = new ArrayList<String[]>();
	public static ArrayList<String[]> rMessages = new ArrayList<String[]>();
	public static final int PORT = 1012; //can be any number as long as the client has the same number
	public static final int PINGPORT = 1013;
	public static DatagramSocket socket;
	
	public static void main(String[] args)throws IOException, Exception
	{
		socket = new DatagramSocket(PORT);
		dbCommands db = new dbCommands();
		receiver serverReader = new receiver(socket);
		messageRouter serverRouter = new messageRouter(serverReader,db);
		messageManager serverMessages = new messageManager();
		checkOnline check = new checkOnline(db);
		pingReceiver pr = new pingReceiver(db,PINGPORT);
		
	 	
		serverMessages.start();
		serverRouter.start();
		check.start();
		pr.start();
		System.out.println("Running");
	}
}
