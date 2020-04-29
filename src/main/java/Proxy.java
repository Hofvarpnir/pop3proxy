import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class Proxy implements Runnable{


	// Main method for the program
	public static void main(String[] args) {
		// Create an instance of Proxy and begin listening for connections
		Proxy myProxy = new Proxy(8085);
		myProxy.listen();	
	}

	private ServerSocket serverSocket;

	private volatile boolean running = true;

	static ArrayList<Thread> servicingThreads;

	/**
	 * Create the Proxy Server
	 * @param port Port number to run proxy server from.
	 */
	public Proxy(int port) {

		// Create array list to hold servicing threads
		servicingThreads = new ArrayList<>();

		// Start dynamic manager on a separate thread.
		new Thread(this).start();	// Starts overriden run() method at bottom

		try {
			// Create the Server Socket for the Proxy 
			serverSocket = new ServerSocket(port);
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
			running = true;
		} 

		// Catch exceptions associated with opening socket
		catch (SocketException se) {
			System.out.println("Socket Exception when connecting to client");
			se.printStackTrace();
		}
		catch (SocketTimeoutException ste) {
			System.out.println("Timeout occurred while connecting to client");
		} 
		catch (IOException io) {
			System.out.println("IO exception when connecting to client");
		}
	}

	public void listen(){

		while(running){
			try {
				// serverSocket.accept() Blocks until a connection is made
				Socket socket = serverSocket.accept();
				
				// Create new Thread and pass it Runnable RequestHandler
				Thread thread = new Thread(new RequestHandler(socket));

				System.out.println("Connection from mail program on thread " +  thread.getId());
				
				// Key a reference to each thread so they can be joined later if necessary
				servicingThreads.add(thread);
				
				thread.start();

			} catch (SocketException e) {
				// Socket exception is triggered by management system to shut down the proxy 
				System.out.println("Server closed");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void closeServer(){
		System.out.println("\nClosing Server..");
		running = false;
		try{
			// Close all servicing threads
			for(Thread thread : servicingThreads){
				if(thread.isAlive()){
					System.out.print("Waiting on "+  thread.getId()+" to close..");
					thread.join();
					System.out.println(" closed");
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Close Server Socket
		try{
			System.out.println("Terminating Connection");
			serverSocket.close();
		} catch (Exception e) {
			System.out.println("Exception closing proxy's server socket");
			e.printStackTrace();
		}

	}

		@Override
		public void run() {
			Scanner scanner = new Scanner(System.in);

			String command;
			while(running){
				System.out.println("Type \"close\" to close server.");
				command = scanner.nextLine();
				if(command.equals("close")){
					running = false;
					closeServer();
				}
			}
			scanner.close();
		}
	}
