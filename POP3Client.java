package mailServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class POP3Client {
	/***********TestClient*********************************************/
	public static void main(String[] args) {
		Socket socket=null;
		try {
			socket=new Socket("47.106.20.56",110);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("连接成功");
		POP3Client popclient=new POP3Client(socket);
		popclient.setUser("test@mail.davidhwj.cn");
		popclient.setPass("test");
		
		ArrayList<MailFromPOP3> mails=popclient.getAllMail();
		for(int i=0;i<mails.size();i++) {
			mails.get(i).display();
		}

	}
	/*******************************************************************/
	//用户名和密码
	private String USER,PASS;
	//和POP3服务器的连接
	private Socket SOCKET;
	private InputStream in;
	private OutputStream out;
	BufferedReader br;
	//从POP3Server获取到的所有邮件
	ArrayList<MailFromPOP3> mails=new ArrayList();
	
	//构造方法
	public POP3Client(Socket socket) {
		this.USER=null;this.PASS=null;this.SOCKET=socket;
		try {
			in=SOCKET.getInputStream();
			out=SOCKET.getOutputStream();
			br=new BufferedReader(new InputStreamReader(in));
		} catch (IOException e) {
			writeLog("fail to get InputStream from socket with POP3Server");
			e.printStackTrace();
		}
	}
	//设置用户名和密码
	public boolean setUser(String user) {this.USER=user;return true;}
	public boolean setPass(String pass) {this.PASS=pass;return true;}
	public boolean serAccount(String user,String pass) {this.USER=user;this.PASS=pass;return true;}

	//获取所有邮件
	public ArrayList<MailFromPOP3> getAllMail(){
		mails.clear();
		//连接参数检查
		if(SOCKET==null||SOCKET.isClosed()||USER==null||PASS==null) {
			return mails;
		}
		//检查服务器状态
		String[] avgs=readLine().split(" ");
		if(!avgs[0].equals("+OK"))return mails;
		//特许阶段
		if(!authorization()) {
			writeLog("fail to Log in POP3Serve");
			return mails;
		}
		//处理阶段
		resolve();
		
		return mails;
	}

	/**
	 * 给服务器发请求
	 * @param msg
	 */
	private boolean sendRequest(String msg) {
		writeLog(msg);
		try {
			String message=msg+"\r\n";
			out.write(message.getBytes());
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	/**
	 * 读取一行
	 * @return
	 */
	private String readLine() {
//		writeLog("begin readLine()");
		String res="";
		
		try {
//			writeLog("begin br.readLine()");
			res=br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}		
//		writeLog(res);
		return res;
	}
	/**
	 * 客户端的特许阶段
	 * @return
	 */
	private boolean authorization() {
		String command=null;
		String[] avgs=null;
		//验证用户名
		if(!sendRequest("user "+USER)) return false;
		command=readLine();
		avgs=command.split(" ");
		if(!avgs[0].equals("+OK")) {
			writeLog(command);
			return false;
		}
		//验证密码
		if(!sendRequest("pass "+PASS)) return false;
		command=readLine();
		avgs=command.split(" ");
		if(!avgs[0].equals("+OK")) {
			writeLog(command);
			return false;
		}
		return true;
	}
	/**
	 * 客户端事物处理阶段
	 * @return
	 */
	private boolean resolve() {

		//list命令
		if(!sendRequest("list")) {
			return false;
		}
		int count=0;//服务器返回的邮件数
		
		while(true) {
			String str=readLine();
			if(str.equals(".")) {
				break;
			}
			else {
				count++;
			}
			
			writeLog(str+" count:"+count);
			
		}
		writeLog("count:"+count);
		//retur 和dele命令
		String command=null;
		String[] avgs=null;
		for(int index=1;index<=count;index++) {
			//获取邮件

			sendRequest("retr "+index);
			command=readLine();
			avgs=command.split(" ");
			if(!avgs[0].equals("+OK")) {
//				writeLog(command);
				continue;
			}
			else {
				int size=Integer.parseInt(avgs[2]);
				String date=readLine().split(" ")[1];
				String from=readLine().split(" ")[1];
				String to=readLine().split(" ")[1];
				String subject=readLine().split(" ")[1];
				readLine();//Data:
				StringBuilder sb=new StringBuilder();
				String tmp=null;
				if(!(tmp=readLine()).equals(".")){
					sb.append(tmp);
					while(!(tmp=readLine()).equals(".")) sb.append("\r\n"+tmp);
				}
				
				String mailbody=sb.toString();
				MailFromPOP3 mail=new MailFromPOP3(size,date,from,to,subject,mailbody);
				mails.add(mail);
			}
			//删除邮件
			sendRequest("dele "+index);
			command=readLine();
			avgs=command.split(" ");
			if(!avgs[0].equals("+OK")) {
				writeLog(command);
				continue;
			}
			
		}
		//quit命令
		sendRequest("quit");
		
		return true;
	}
	/**
	 * POP3服务接收到的邮件格式
	 * @author HWJ
	 *
	 */
	class MailFromPOP3{
		//邮件大小：字节数
		private int Size;
		//邮件信息
		private String Date;
		private String From,To,Subject,Body;
		public MailFromPOP3(int size,String date,String from,String to,String subject,String body) {
			this.Size=size;this.Date=date;this.From=from;this.To=to;this.Subject=subject;this.Body=body;
		}
		//给调用端返回邮件信息
		public int getSize() {return Size;}
		public String getDate() {return Date;}
		public String getFrom() {return From;}
		public String getTo() {return To;}
		public String getSubject() {return Subject;}
		public String getBody() {return Body;}
		//邮件信息输出
		public void display() {
			System.out.print("SIZE: " + Size);
			System.out.print("DATE: " + Date);
		    System.out.print("FROM: " + From);
		    System.out.print("	TO: " + To);
		    System.out.print("	DATAPATH: " + Subject);
		    System.out.print("	STATUS: " + Body);
		    System.out.print("\n");
		}
		
	}
	/**
	 * 日志信息
	 * @param log
	 */
	public void writeLog(String log) {
		System.out.println(log);
	}
}

