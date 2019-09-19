import java.net.*;
import java.io.*;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Date;

class sendMessage extends Thread //sends messages with ack
{
	private byte b[];
	private String message;
	private int ack;
	private int iterations;
	private boolean received;
	private int index;
	
	public sendMessage(String message, int ack) //constructor
	{
		this.message = message;
		this.ack = ack;
	}
	
	@Override
	public void run()
	{
		String[] mTemp = {message,Integer.toString(ack)};
		client.messages.add(mTemp);
		iterations = 0;
		received = false;
		ack();
		if(!received)
		{
			for (int i = 0; i < client.messages.size();i++)
			{
				if(client.messages.get(i)[0].equals(message) && client.messages.get(i)[1].equals(Integer.toString(ack)))
				{
					index = i;
				}	
			}
			client.messages.remove(index);
		}
	}
	
	public void ack() //sends the message and waits for an ack
	{
		try	
		{
			b = message.getBytes();
			DatagramPacket packet = new DatagramPacket(b,b.length,InetAddress.getByName(client.serIP),client.SERPORT);
			client.socket.send(packet);
			Thread.sleep(700);
			while(iterations < 7 && !received)
			{
				for (int i = 0; i < client.messages.size();i++)
				{
					if(client.messages.get(i)[1].equals(Integer.toString(ack)))
					{
						iterations++;
						ack();
					}
					else if(i == client.messages.size()-1)
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
}

class sender extends Thread //sends the messages that the scanner reads
{
	private DatagramSocket socket;
	private String serverIP;
	private int serverPort;
	private boolean running;
	private int pingPort;
	private byte b[];
	
	public sender(String serverIP, int serverPort,int pingPort,DatagramSocket socket)throws IOException //constructor
	{
		this.pingPort = pingPort;
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.socket = socket;
	}
	
	@Override
	public void run()
	{
		Scanner in = new Scanner(System.in);
		running = true;
		while(running)
		{
			String message = in.nextLine();
			int ack = ackNum();
			if(client.session) //checks to see if the user is in a session
			{
				if(message.equals("/end")) //checks to see if the user is trying to end the session
				{
					System.out.println("Session ended");
					client.session = false;
					message = header("/endse"+client.rUser,ack)+message;
					sendMessage m = new sendMessage(message,ack);
					m.start();
				}
				else //sends the message with the session marker
				{
					message = header("/messg"+client.rUser,ack)+message;
					sendMessage m = new sendMessage(message,ack);
					m.start();
				}
			}
			else //sends a command to the server with the "not in a session" marker
			{
				message = header("/nsess",ack)+message;
				sendMessage m = new sendMessage(message,ack);
				m.start();
			}
		}
		in.close();
	}
	public void sendMessage(String message)throws IOException //sends a message without ack
	{
		b = message.getBytes();
		DatagramPacket packet = new DatagramPacket(b,b.length,InetAddress.getByName(serverIP),serverPort);
		socket.send(packet);
	}
	
	public void sendPing()throws IOException //sends a ping to server
	{
		String ping = "keepAlive";
		b = ping.getBytes();
		DatagramPacket packet = new DatagramPacket(b,b.length,InetAddress.getByName(serverIP),pingPort);
		socket.send(packet);
	}
	
	public void stopThread() // not used but useful for debugging
	{
		running = false;
	}

	public boolean getSession() //not used but useful for debugging
	{
		return client.session;
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
		for(String[] s : client.messages )
		{
			if(s[1].equals(Integer.toString(ack)))
			{
				ack = ackNum();
			}
		}
		return ack;
	}
}



class receiver extends Thread //takes in messages and prints out the corresponding dta
{
	private DatagramSocket socket;
	private byte b[] = new byte[1025];
	private DatagramPacket packet;
	private sender s;
	private boolean running;
	
	public receiver(DatagramSocket socket, sender s) //constructor
	{
		this.s = s;
		this.socket = socket;
	}
	
	@Override
	public void run()
	{
		client.session = false;
		running = true;
		while(running)
		{
			String message = readMessage();
			boolean newMessage = true;
			if(!(message.indexOf("/ackno")>=0 && message.substring(0, 6).equals("/ackno"))) //checks to see if the message is an ack message
			{
				try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
			}
			else if(message.indexOf("/ackno")>=0 && message.substring(0, 6).equals("/ackno")) //deletes the message from the messages arrayList which tells the sendMessage class that the message was delivered
			{
				String ack = message.substring(6);
				for (int i = 0; i < client.messages.size();i++)
				{
					if(client.messages.get(i)[1].equals(ack))
					{
						client.messages.remove(i);
					}
				}
			}
			for(String s[] : client.rMessages )
			{
				if(s[0].equals(message))
				{
					newMessage = false;
				}
			}
			if(newMessage)
			{
				String[] readMessage = {message,Long.toString(new Date().getTime())};
				client.rMessages.add(readMessage);
				if(!client.session && message.indexOf("/login")>=0 && message.substring(0,6).equals("/login")) //checks to see if a session is being started with this client
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					message = message.substring(25)+"\n";
				}
				else if(!client.session && message.indexOf("/omess")>=0 && message.substring(0,6).equals("/omess")) //checks to see if a session is being started with this client
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					message = message.substring(25)+"\n";
				}
				else if(client.session && message.indexOf("/messg")>=0 && message.substring(0,6).equals("/messg")) //checks to see if a session is being started with this client
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					message = message.substring(25)+"\n";
				}
				else if(!client.session && message.indexOf("/error")>=0 && message.substring(0,6).equals("/error")) //checks to see if a session is being started with this client
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					message = message.substring(25)+"\n";
				}
				else if(!client.session && message.indexOf("/sessi")>=0 && message.substring(0,6).equals("/sessi")) //checks to see if a session is being started with this client
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					client.session = true;
					client.rUser = Integer.parseInt(message.substring(6,message.indexOf(".")));
					message = message.substring(25)+"\n";
				}
				else if(client.session && message.indexOf("/sessi")>=0 && message.substring(0,6).equals("/sessi")) // checks to see if the someone is trying to start a session with the client and lets them know the client is already in a session
				{
					int temp = client.rUser;
					client.rUser = Integer.parseInt(message.substring(6,message.indexOf(".")));
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					
					int ack = s.ackNum();
					message = s.header("/esess"+client.rUser,ack);
					sendMessage m = new sendMessage(message,ack);
					m.start();
					
					message ="Someone wants to start a session with you but you are already in one\n";
					client.rUser = temp;
				}
				else if(client.session && message.indexOf("/endse")>=0 && message.substring(0,6).equals("/endse")) //checks to see if the other user ended the session
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					message = "Other user has ended the session\n";
					client.session= false;
				}
				else if(message.indexOf("/esess")>=0 && message.substring(0,6).equals("/esess")) //checks to see if the use the client wants to start a session with is already in a session
				{
					try{s.sendMessage("/ackno"+getAck(message));}catch(IOException e){e.printStackTrace();}
					client.session = false;
					message = "User is already in a session\n";
				}
				else if(message.indexOf("/ackno")>=0 && message.substring(0, 6).equals("/ackno")) //deletes the message from the messages arrayList which tells the sendMessage class that the message was delivered
				{
					message = "";
				}
				System.out.print(message);
			}
			
		}
	}
	
	public String readMessage() //reads a message
	{
		packet = new DatagramPacket(b, b.length);
		try{socket.receive(packet);}catch(IOException e) {e.printStackTrace();}
		String received = new String(packet.getData(), 0, packet.getLength());
		return received;
	}
	
	public String getAck(String message) //gets the ack of a message
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
	
	public void stopThread() //not used but useful for debugging
	{
		running = false;
	}
}

class pingServer extends Thread //pings the server to tell the server its online
{
	private sender s;
	private boolean running;
	
	public pingServer(sender s) //constructor
	{
		this.s = s;
		running = true;
	}
	
	@Override
	public void run()
	{
		do
		{
			try 
			{
				s.sendPing();
				Thread.sleep(2000);
			}
			catch(IOException e) 
			{
				e.printStackTrace();
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}while(running);
	}
	
	public void stopThread() //stops the thread
	{
		running = false;
	}
}

class messageManager extends Thread //removes received messages after 5600 milliseconds
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
			for (String[] s: client.rMessages)
			{
				long diff = new Date().getTime()-Long.parseLong(s[1]);
				if(Math.abs(diff)>5600)
				{
					toRemove.add(s);
				}
			}
			client.rMessages.removeAll(toRemove);
			try{Thread.sleep(1000);}catch(Exception e) {e.printStackTrace();}
		}while(running);
	}
	
	public void stopThread() //stops the thread
	{
		running = false;
	}
}

public class client 
{
	public static boolean session;
	public static int rUser;
	public static ArrayList<String[]> messages = new ArrayList<String[]>();
	public static ArrayList<String[]> rMessages = new ArrayList<String[]>();
	private static sender clientSender;
	private static receiver clientReader;
	private static messageManager clientMessages;
	private static Scanner in;
	public static String serIP;
	public static final int SERPORT = 1012;
	public static final int PINGPORT = 1013;
	public static DatagramSocket socket;
	
	
	public static void main(String[] args)throws UnknownHostException, IOException
	{
		 try
		 {
			socket = new DatagramSocket();
		 	socket.setReuseAddress(true);
		 	in = new Scanner(System.in);
		 	
		 	System.out.println("Enter the IP of the server you would like to connect to: ");
		 	serIP = in.nextLine();
		 	System.out.println("Would you like to login or register");
		 	clientSender = new sender(serIP,SERPORT,PINGPORT,socket);
		 	clientReader = new receiver(socket,clientSender);
		 	clientMessages = new messageManager(); //manages rMessages
		 	clientMessages.start();
		 	
		 	logOrReg(in); //logs the user in
		 	clientReader.start();
		 	clientSender.start();
		 	
		 	pingServer ping = new pingServer(clientSender);
		 	ping.start();
		 }
		 catch(IOException e)
		 {
		 	e.printStackTrace();
		 }

	}

	public static void logOrReg(Scanner s)throws IOException //directs the user to either login or register
	{
		String response = s.nextLine();
		if(response.equals("login"))
		{
			login(s);
		}
		else if(response.equals("register"))
		{
			register(s);
		}
		else
		{
			System.out.println("please enter \"register\" or \"login\"");
			logOrReg(s);
		}
		
	}
	
	public static void login(Scanner s)throws IOException //logs the user in
	{
		System.out.println("Enter your username:");
		String username = s.nextLine();
		System.out.println("Enter your password");
		String pw = s.nextLine();
		
		int ack = clientSender.ackNum();
		String sendMessage = clientSender.header("/login"+username.length(),ack)+username+pw;
		sendMessage m = new sendMessage(sendMessage,ack);
		m.start();
		String message = ackIgnore();
		try{clientSender.sendMessage("/ackno"+clientReader.getAck(message));}catch(IOException e){e.printStackTrace();}
		if(message.indexOf("/loger")>=0 && message.substring(0,6).equals("/loger")) // checks to see if the username was already taken
		{
			System.out.println(message.substring(25)+". Please try again");
			login(in); //iterates
		}
		else if(!client.session && message.indexOf("/login")>=0 && message.substring(0,6).equals("/login")) //checks to see if a session is being started with this client
		{
			System.out.println(message.substring(25));
		}
		else if(!client.session && message.indexOf("/omess")>=0 && message.substring(0,6).equals("/omess")) //checks to see if a session is being started with this client
		{
			System.out.println(message.substring(25));
		}
	}
	
	public static void register(Scanner s)throws IOException //registers the user
	{
		System.out.println("Enter your username:");
		String username = s.nextLine();
		System.out.println("Enter your password");
		String pw = s.nextLine();
		
		int ack = clientSender.ackNum();
		String sendMessage = clientSender.header("/regis"+username.length(),ack)+username+pw;
		sendMessage m = new sendMessage(sendMessage,ack);
		m.start();
		String message = ackIgnore();
		try{clientSender.sendMessage("/ackno"+clientReader.getAck(message));}catch(IOException e){e.printStackTrace();}
		if(message.indexOf("/loger")>=0 && message.substring(0,6).equals("/loger")) // checks to see if the username was already taken
		{
			System.out.println(message.substring(25)+". Please try again");
			register(in); //iterates
		}
		else if(!client.session && message.indexOf("/login")>=0 && message.substring(0,6).equals("/login")) //checks to see if a session is being started with this client
		{
			System.out.println(message.substring(25));
		}
	}
	
	
	public static String ackIgnore() //ignores ack messages
	{
		String message = clientReader.readMessage();
		boolean newMessage = true;
		for(String s[] : rMessages )
		{
			if(s[0].equals(message))
			{
				newMessage = false;
			}
		}
		if(newMessage)
		{
			String[] readMessage = {message,Long.toString(new Date().getTime())};
			client.rMessages.add(readMessage);
			if(message.indexOf("/ackno")>=0 && message.substring(0,6).equals("/ackno"))
			{
				String ackNum = message.substring(6);
				for (int i = 0; i < client.messages.size();i++)
				{
					if(client.messages.get(i)[1].equals(ackNum))
					{
						client.messages.remove(i);
					}
				}
				message = ackIgnore();
			}
			return message;
		}
		return "";
	}
}
