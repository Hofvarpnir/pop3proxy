import com.sun.mail.pop3.POP3Folder;
import com.sun.mail.pop3.POP3Store;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.Properties;
import javax.mail.*;

public class RequestHandler implements Runnable {

	/**
	 * Socket connected to client passed by Proxy server
	 */
	Socket clientSocket;
	String username = "null";
	String password = "null";
	String email = "null";
	String host = "null";
	POP3Store store;
	POP3Folder inbox;
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
			case "STAT": {
				statRequest();
				break;
			}
			case "LIST": {
				listRequest();
				break;
			}
			case "UIDL": {
				uidlRequest();
				break;
			}
			case "QUIT": {
				quitRequest();
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
			case "RSET": {
				rsetRequest();
				break;
			}
			default:
				System.out.println("Need to implement simple");
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
				connectToEmail();
				inbox.open(Folder.READ_WRITE);
				break;
			}
			case "LIST":{
				listComplexRequest(Integer.parseInt(requestLine.substring(requestLine.indexOf(' ') + 1)));
				break;
			}
			case "UIDL":{
				uidlComplexRequest(Integer.parseInt(requestLine.substring(requestLine.indexOf(' ') + 1)));
				break;
			}
			case "RETR":{
				retrComplexRequest(Integer.parseInt(requestLine.substring(requestLine.indexOf(' ') + 1)));
				break;
			}
			case "TOP":{
				topComplexRequest(Integer.parseInt(requestLine.substring(requestLine.indexOf(' ') + 1, requestLine.indexOf(' ', requestLine.indexOf(' ') + 1))), Integer.parseInt(requestLine.substring(requestLine.indexOf(' ', requestLine.indexOf(' ') + 1) + 1)));
				break;
			}
			case "DELE":{
				deleComplexRequest(Integer.parseInt(requestLine.substring(requestLine.indexOf(' ') + 1)));
				break;
			}
			default:{
				System.out.println("Need to implement complex");
				break;
			}
		}
	}

	public void rsetRequest() throws MessagingException, IOException {
		int count = inbox.getMessageCount();
		Message mail;
		for (int i = 0; i < count; i++) {
			mail = inbox.getMessage(i + 1);
			mail.setFlag(Flags.Flag.DELETED, false);
		}
		String answer = "+OK" + eol;
		sentStringToClient(answer);
	}

	public void deleComplexRequest(int number) throws MessagingException, IOException {
		int count = inbox.getMessageCount();
		String answer = "";
		if (number > count) {
			answer = "-ERR no such message" + eol;
		}
		else {
			Message mail = inbox.getMessage(number);
			mail.setFlag(Flags.Flag.DELETED, true);
			answer = "+OK message " + String.valueOf(number) + " deleted" + eol;
			sentStringToClient(answer);
		}
	}

	public void topComplexRequest(int number, int lines) throws MessagingException {
		int count = inbox.getMessageCount();
		String answer = "";
		if (number > count) {
			answer = "-ERR no such message" + eol;
		}
		else {
			Message email = inbox.getMessage(number);
			Enumeration headers = email.getAllHeaders();
			while (headers.hasMoreElements()) {
				Header h = (Header) headers.nextElement();
				answer = answer + h.getName() + ":" + h.getValue() + eol;
			}
			answer = "+OK" + eol + answer + "." + eol;
		}
		sentStringToClient(answer);
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

	public void connectToEmail() throws MessagingException {
		Properties props = new Properties();
		props.setProperty("mail.pop3.ssl.enable", "true");
		Session session = javax.mail.Session.getInstance(props);
		store = (POP3Store) session.getStore("pop3");
		System.out.println("Credentials: " + host + " " + port + " " + username + " " + password);
		store.connect(host, username, password);
		System.out.println("Connected " + store.isConnected());
		inbox = (POP3Folder) store.getFolder("Inbox");
	}

	public void quitRequest() throws MessagingException, IOException {
		inbox.close(true);
		String answer = "+OK" + eol;
		sentStringToClient(answer);
		clientSocket.close();
		running = false;
	}

	public void statRequest() throws MessagingException {
		int count = inbox.getMessageCount();
		int size = inbox.getSize();
		String answer = "+OK " + String.valueOf(count) + " " + String.valueOf(size) + eol;
		sentStringToClient(answer);
	}

	public void retrComplexRequest(int number) throws MessagingException, IOException {
		int count = inbox.getMessageCount();
		String answer = "";
		if (number > count) {
			answer = "-ERR no such message" + eol;
		}
		else {
			int sizes[] = inbox.getSizes();
			Message mail = inbox.getMessage(number);
			answer = "+OK " + String.valueOf(sizes[number - 1]) + " octets" + eol;
			sentStringToClient(answer);
			answer = "";
			mail.writeTo(clientSocket.getOutputStream());
			sentStringToClient("." + eol);
		}
	}

		public void uidlRequest() throws MessagingException {
		int count = inbox.getMessageCount();
		String answer = "";
		if (count == 0) {
			answer = "-ERR no messages" + eol;
		}
		else {
			for (int i = 0; i < count; i++) {
				answer = answer + String.valueOf(i + 1) + " " + inbox.getUID(inbox.getMessage(i + 1)) + eol;
			}
			answer = "+OK" + eol + answer + "." + eol;
		}
		sentStringToClient(answer);
	}

	public void uidlComplexRequest(int number) throws MessagingException {
		int count = inbox.getMessageCount();
		String answer = "";
		if (number > count) {
			answer = "-ERR no such message" + eol;
		}
		else {
			answer = "+OK " + String.valueOf(number) + " " + inbox.getUID(inbox.getMessage(number)) + eol;
		}
		sentStringToClient(answer);
	}

	public void listRequest() throws MessagingException {
		int count = inbox.getMessageCount();
		int size = inbox.getSize();
		String answer = "";
		if (count != 0) {
			int sizes[] = inbox.getSizes();
			for (int i = 0; i < count; i++) {
				answer = answer + String.valueOf(i + 1) + " " + String.valueOf(sizes[i]) + eol;
			}
			answer = "+OK " + String.valueOf(count) + " messages (" + String.valueOf(size) + " octets)" + eol + answer + "." + eol;
		}
		else {
			answer = "-ERR no messages" + eol;
		}
		sentStringToClient(answer);
	}

	public void listComplexRequest(int number) throws MessagingException {
		int count = inbox.getMessageCount();
		String answer = "";
		if (number <= count) {
			int sizes[] = inbox.getSizes();
			answer = "+OK " + String.valueOf(number) + String.valueOf(sizes[number - 1]) + eol;
		}
		else {
			answer = "-ERR no such message" + eol;
		}
		sentStringToClient(answer);
	}

	public void setUserCredentials(String requestLine){
		username = requestLine.substring(requestLine.indexOf(' ') + 1);
		email = username.substring(username.indexOf("@"));
		getHostFromEmail();
		System.out.println("Username is: " + username + "\nEmail server is: " + email);
		sentStringToClient("+OK password please" + eol);
	}

	public void setPasswordCredentials(String requestLine){
		password = requestLine.substring(requestLine.indexOf(' ') + 1);
		System.out.println("Password is: " + password);
		sentStringToClient("+OK welcome back" + eol);
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




