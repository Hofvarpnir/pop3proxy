import com.sun.mail.pop3.POP3Folder;
import com.sun.mail.pop3.POP3Store;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.Properties;
import javax.mail.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class RequestHandler implements Runnable {

	/**
	 * Socket connected to client passed by Proxy server
	 */
	Socket clientSocket;
	SSLSocket toServer;
	String recvreply;


	String username = "null";
	String password = "null";
	String email = "null";
	String host = "null";
	int port = 995;
	boolean running = true;

	private final String eol = "\r\n";

	private final String capabilities = "+OK Capability list follows" + eol +
			"USER" + eol +
			"PASS" + eol +
			"STAT" + eol +
			"LIST" + eol +
			"RETR" + eol +
			"DELE" + eol +
			"TOP" + eol +
			"UIDL" + eol +
			"RSET" + eol +
			"QUIT" + eol +
			"." + eol;

	/**
	 * Read data client sends to proxy
	 */
	BufferedReader proxyToClientBr;

	/**
	 * Send data from proxy to client
	 */
	BufferedWriter proxyToClientBw;

	/**
	 * Read data server sends to proxy
	 */
	BufferedReader proxyToServerBr;

	/**
	 * Send data from proxy to server
	 */
	BufferedWriter proxyToServerBw;


	/**
	 * Creates a ReuqestHandler object capable of servicing requests
	 * @param clientSocket socket connected to the client
	 */
	public RequestHandler(Socket clientSocket){
		this.clientSocket = clientSocket;
		try{
			this.clientSocket.setSoTimeout(2000);
			proxyToClientBr = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sentStringToClient(String response){
		try{
			proxyToClientBw.write(response);
			proxyToClientBw.flush();
			System.out.println("Request Sent: " + response);
		} catch (SocketTimeoutException ste) {
			ste.printStackTrace();
			System.out.println("Error timeout from client");
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error sending string to client");
		}
	}

	public String getStringFromClient(){
		try{
			while (!proxyToClientBr.ready()){}
			String request = proxyToClientBr.readLine();
			System.out.println("Request Received: " + request);
			return  request;
		} catch (SocketTimeoutException ste) {
			ste.printStackTrace();
			System.out.println("Error timeout from client");
			return "error";
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error getting string from client");
			return "error";
		}
	}

	public void simpleRequest(String requestType) throws MessagingException, IOException {
		switch (requestType) {
			case "CAPA": {
				sentStringToClient(capabilities);
				break;
			}
			case "AUTH": {
				sentStringToClient("-ERR" + eol);
				break;
			}
			case "NOOP": {
				sentStringToClient("+OK" + eol);
				break;
			}
			case "LIST":
			case "UIDL": {
				MultiLineResponse(requestType);
				break;
			}
			case "RSET":
			case "STAT": {
				SingleLineResponse(requestType);
				break;
			}
			case "QUIT": {
				proxyToServerBw.write(requestType + eol);
				proxyToServerBw.flush();
				sentStringToClient("+OK" + eol);
				clientSocket.close();
				toServer.close();
				running = false;
				break;
			}
			default:
				System.out.println("NOT IMPLEMENTED");
				break;
		}
	}

	public void complexRequest(String requestLine) throws MessagingException, IOException {
		String requestType = requestLine.substring(0, requestLine.indexOf(' '));
		switch (requestType) {
			case "USER":{
				setUserCredentials(requestLine);
				break;
			}
			case "PASS":{
				setPasswordCredentials(requestLine);
				break;
			}
			case "DELE":
			case "LIST":
			case "UIDL": {
				SingleLineResponse(requestLine);
				break;
			}
			case "TOP":
			case "RETR": {
				MultiLineResponse(requestLine);
				break;
			}
			default:{
				System.out.println("NOT IMPLEMENTED");
				break;
			}
		}
	}

	public void SingleLineResponse(String requestLine) throws IOException {
		proxyToServerBw.write(requestLine + eol);
		proxyToServerBw.flush();
		recvreply = proxyToServerBr.readLine();
		sentStringToClient(recvreply + eol);
	}

	public void MultiLineResponse(String requestLine) throws IOException {
		proxyToServerBw.write(requestLine + eol);
		proxyToServerBw.flush();
		while (!(recvreply = proxyToServerBr.readLine()).equals(".")) {
			sentStringToClient(recvreply + eol);
		}
		sentStringToClient(recvreply + eol);
	}

	public void getHostFromEmail(){
		switch (email){
			case "@gmail.com": {
				host = "pop.gmail.com";
				break;
			}
			case "@inbox.ru":
			case "@bk.ru":
			case "@list.ru":
			case "@mail.ru": {
				host = "pop.mail.ru";
				break;
			}
			default: {
				System.out.println("Need to implement mailbox " + email);
				break;
			}
		}
	}

	public void setUserCredentials(String requestLine) throws IOException {
		username = requestLine.substring(requestLine.indexOf(' ') + 1);
		email = username.substring(username.indexOf("@"));
		getHostFromEmail();
		System.out.println("Username is: " + username + "\nEmail server is: " + email);
		toServer = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(InetAddress.getByName(host), port);
		toServer.startHandshake();

		proxyToServerBr = new BufferedReader(new InputStreamReader(toServer.getInputStream()));
		proxyToServerBw = new BufferedWriter(new OutputStreamWriter(toServer.getOutputStream()));

		recvreply = proxyToServerBr.readLine();
		proxyToServerBw.write(requestLine + eol);
		proxyToServerBw.flush();
		recvreply = proxyToServerBr.readLine();
		sentStringToClient(recvreply + eol);
	}

	public void setPasswordCredentials(String requestLine) throws IOException {
		password = requestLine.substring(requestLine.indexOf(' ') + 1);
		System.out.println("Password is: " + password);
		proxyToServerBw.write(requestLine + eol);
		proxyToServerBw.flush();
		recvreply = proxyToServerBr.readLine();
		sentStringToClient(recvreply + eol);
	}

	@Override
	public void run() {

		// Client
		String requestString;
		sentStringToClient("+OK POP3 server ready" + eol);

		while (running) {
			requestString = getStringFromClient();
			if (requestString.indexOf(' ') == -1) {
				try {
					simpleRequest(requestString);
				} catch (MessagingException | IOException e) {
					e.printStackTrace();
				}
			}
			else {
				try {
					complexRequest(requestString);
				} catch (MessagingException | IOException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println("Thread finished service and ends: " +  Thread.currentThread().getId());
	}
}




