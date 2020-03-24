package mailServer;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.mysql.cj.jdbc.Driver;

public class POP3Server {
	public static void main(String[] args) {
		
		/*******服务器测试***************************/
		POP3Server server=POP3Server.createPOP3Server();
		server.start();
//		server.stop();
		
		/********数据库连接测试************************/
		
//		JDBC jdbc=new JDBC();
//		if(jdbc.Connect()) {
//			System.out.println("Success to Connect");
//		}
//		ArrayList<Mail> res=jdbc.allMailOfSomeOne("test@mail.davidhwj.cn",-1);
//		for(int i=0;i<res.size();i++) {
//			res.get(i).display();
//		}
//		jdbc.setMailStatus( new Mail(1,new Date(1,2,3),"","","",1) , 0);
		
//		System.out.println(jdbc.haveUser("NICK"));
//		System.out.println(jdbc.verifyPass("nick", "test"));
//		if(jdbc.Close()) {
//			System.out.println("Success to Close");
//		}
		
		/************************************************/
		
	}
	
	//POP3服务器监听的端口
	private static final int PORT=110;
	private static ServerSocket ss=null;
	
	//在服务中的客户端数 使用线程同步的方法访问
	private static int clientCount=0;
	//服务的状态
	private boolean ISRUNNING=false;

	//单例模式保证只有一个POP3服务器
	private static POP3Server server=null;
	private POP3Server() {}
	public static POP3Server createPOP3Server() {
		if(server==null) {
			server=new POP3Server();
			return server;
		}
		else {
			return server;
		}
	}
	/**
	 * 启动服务器
	 * @return
	 */
	public boolean start() {
		
		if(ss==null) {
			try {
				ss=new ServerSocket(PORT);
			} catch (IOException e) {
				writeLog("POP3Server fail to listen Port "+PORT);
				e.printStackTrace();
				return false;
			}
			Thread task=new Thread() {
				//线程入口循环等待连接
				public void run() {
					while(true) {
						try {
							Socket client=ss.accept();	
							writeLog("A client connect:"+client.getInetAddress().getHostAddress()+":"+client.getPort());
							
							Service service=new Service(client);
							service.start();
							Thread.sleep(10);
							writeLog(clientCount+" clients is connecting currently");
						} catch (IOException | InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};
			task.start();
			writeLog("POP3Server is running...");
		}	
		ISRUNNING=true;
		return true;
	}
	/**
	 * 停止服务器
	 * @return
	 */
	public boolean stop() {
		try {
			int waitCount=10;
			while(clientCount>0) {
				Thread.sleep(100);
				waitCount--;
				if(waitCount<0)break;
			}
			if(clientCount>0) {
				writeLog("fail to stop POP3Server "+clientCount+" are connecting");
				return false;
			}
			else {
				if(ss!=null&&!ss.isClosed()) {
					ss.close();
				}
				writeLog("Success to close POP3Server");
				ss=null;
				ISRUNNING=false;//关闭成功
				return true;
			}
			
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	//客户端数的访问方法
	synchronized public static void addClientCount() {
		clientCount++;
	}
	synchronized public static void decClientCount() {
		clientCount--;
	}
	//服务器状态的设置方法
	public boolean getISRUNNING() {return ISRUNNING;}
	/**
	 * 写入日志
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
	

}
/**
 * 为客户端提供服务
 * @author HWJ
 *
 */
class Service extends Thread{
	private Socket client;
	private InputStream in;
	private OutputStream out;
	private String userName;
	private String userMailAddr;
	private ArrayList<Mail> mails=new ArrayList();
	
	public Service(Socket client) {
		this.client=client;
	}
	
	/**
	 * provide POP3Server to Client
	 */
	private void pop3Service() {
		writeLog("provide pop3Service for client : "+client.getInetAddress().getHostAddress()+":"+client.getPort());
		
		//获取输入输出流
		try {
			in=client.getInputStream();
			out=client.getOutputStream();
		} catch (IOException e) {
			writeLog(client.getInetAddress().getHostAddress()+":"+client.getPort()+" disconnected");
			e.printStackTrace();
		}
		/*******pop3协议流程*************/
		sendAnswer("+OK POP3 server ready");
		//特许阶段
		authorization();
		//事物处理阶段
		resolve();
		//更新阶段
		update();
		try {
			client.close();
		} catch (IOException e) {
			e.printStackTrace();
			writeLog("fail to diconnect client:"+client.getInetAddress()+":"+client.getPort());
		}
	}
	/**
	 * 读取一行
	 * @return
	 */
	private String readLine() {
		String res="";
		BufferedReader br=new BufferedReader(new InputStreamReader(in));
		try {
			res=br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		return res;
	}
	/**
	 * 给客户端回消息
	 * @param msg
	 */
	private void sendAnswer(String msg) {
		try {
			String message=msg+"\r\n";
			out.write(message.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 给客户端发邮件
	 * @param mail
	 */
	private void sendMail(Mail mail){
		sendAnswer("+OK mailSize: "+mail.mesLength()+" Bytes");
		sendAnswer("Date: "+mail.getSendDate());
		sendAnswer("From: <"+mail.getFrom()+">");
		sendAnswer("To: <"+mail.getTo()+">");
		sendAnswer("Subject: "+mail.getSubject());
		sendAnswer("Data:");
		try {
			out.write(mail.getMailBody().getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	/**
	 * 特许阶段
	 * @return
	 */
	private boolean authorization() {
		JDBC jdbc=new JDBC();
		jdbc.Connect();
		while(true) {
			String command=readLine();
			String[] avgs=command.split(" ");
			if(avgs.length>1) {
				if(avgs[0].toLowerCase().equals("user")) {//是user命令
					if(jdbc.haveUser(avgs[1])) {//有此用户
						userMailAddr=avgs[1];
						userName=jdbc.getNickByMailAddr(userMailAddr);
						sendAnswer("+OK");break;
					}
					else {//无此用户
						sendAnswer("-ERR not exist user");
					}
				}
				else {
					sendAnswer("-ERR invalid command in this status");
				}
			}
			else {//命令不合法
				sendAnswer("-ERR invalid command in this status");
			}
		}
		//用户密码
		while(true) {
			String command=readLine();
			String[] avgs=command.split(" ");
			if(avgs.length>1) {
				if(avgs[0].toLowerCase().equals("pass")) {//是pass命令
					if(jdbc.verifyPass(userMailAddr, avgs[1])) {//密码正确
						sendAnswer("+OK user successfully logged on");break;
					}
					else {//密码错误
						sendAnswer("-ERR worry password");
					}
				}
				else {
					sendAnswer("-ERR invalid command in this status");
				}
			}
			else {//命令不合法
				sendAnswer("-ERR invalid command in this status");
			}
		}
		jdbc.Close();
		return true;
	}
	/**
	 * 事物处理阶段
	 * @return
	 */
	private boolean resolve() {
		JDBC jdbc=new JDBC();
		jdbc.Connect();
		
		String command;
		String[] avgs;
		//list命令
		while(true) {
			command=readLine();
			if(command.toLowerCase().equals("list")) {
				mails=jdbc.allMailOfSomeOne(userMailAddr,0);//所有属于用户的未邮件
				for(int i=0;i<mails.size();i++) {
					int index=i+1;
					sendAnswer(index+" "+mails.get(i).mesLength());
				}
				sendAnswer(".");break;
			}
			else {
				sendAnswer("-ERR invalid command in this status");
			}
		}
		//retr和dele命令
		while(true) {
			command=readLine();
			avgs=command.split(" ");
			if(avgs.length==1) {
				if(avgs[0].toLowerCase().equals("quit")) {
					break;
				}
				else {
					sendAnswer("-ERR invalid command in this status");
				}
			}
			else if(avgs.length==2) {
				if(avgs[0].toLowerCase().equals("retr")) {//retr命令
					boolean flag=false;
					String s=avgs[1];
					for(int i=0;i<s.length();i++) {
						if('0'<=s.charAt(i)&&s.charAt(i)<='9') {
							flag=true;
						}
						else {
							flag=false;break;
						}
					}
					if(flag) {//合法参数
						int index=Integer.parseInt(s);
						if(index>0&&index<=mails.size()) {
							sendMail(mails.get(index-1));
							sendAnswer(".");
						}
						else {
							sendAnswer("-ERR invlid parament of command retr");
						}
					}
					else {//不合法参数
						sendAnswer("-ERR invlid parament of command retr");
					}
					
				}
				else if(avgs[0].toLowerCase().equals("dele")) {//dele命令
					boolean flag=false;
					String s=avgs[1];
					for(int i=0;i<s.length();i++) {
						if('0'<=s.charAt(i)&&s.charAt(i)<='9') {
							flag=true;
						}
						else {
							flag=false;break;
						}
					}
					if(flag) {//合法参数
						int index=Integer.parseInt(s);
						if(index>0&&index<=mails.size()) {
							mails.get(index-1).setStatus(-1);
							sendAnswer("+OK mail "+index+" will be deleted");
						}
						else {
							sendAnswer("-ERR invlid parament of command dele");
						}
					}
					else {//不合法参数
						sendAnswer("-ERR invlid parament of command dele");
					}
				}
				else {
					sendAnswer("-ERR invalid command in this status");
				}
			}
			else {
				sendAnswer("-ERR invalid command in this status");
			}
		}
		
		
		jdbc.Close();
		return true;
	}
	/**
	 * 更新阶段
	 * @return
	 */
	private boolean update() {
		JDBC jdbc=new JDBC();
		if(jdbc.Connect()) {
			for(int i=0;i<mails.size();i++) {
				jdbc.setMailStatus(mails.get(i), mails.get(i).getStatus());
			}
		}
		else {
			writeLog("更新阶段更新数据库失败");
			return false;
		}
		jdbc.Close();
		return true;
	}
	@Override
	public void run() {
		POP3Server.addClientCount();
		pop3Service();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		POP3Server.decClientCount();
	}
	/**
	 * 写入日志
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
}
/**
 * 数据库操作
 * @author HWJ
 *
 */
class JDBC{
	// 数据库 URL
    static final String DB_URL = "jdbc:mysql://47.106.20.56:3306/mailserver?useSSL=false&serverTimezone=UTC";
 
    // 数据库的用户名与密码
    static final String USERNAME = "root";
    static final String PASSWORD = "@SkyrimOf3";
    		
    //数据库连接
    private static Connection conn=null;
    
	public JDBC() {
		// register JDBC Driver
		try {
			DriverManager.registerDriver(new Driver());
		} catch (SQLException e) {
			e.printStackTrace();
		}      
	}
	/**
	 * Connect to mysql Server
	 * @return
	 */
	public boolean Connect() {
		try {
			if(conn==null||conn.isClosed()) {
	            // get Connect
	            conn = DriverManager.getConnection(DB_URL,USERNAME,PASSWORD);
	            return true;
			}
			else {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			writeLog("Fail to connect Mysql Server");
			return false;
		}
	}
	/**
	 * Close connect to Mysql Server
	 * @return
	 */
	public boolean Close() {
		try {
			if(conn==null||conn.isClosed()) {
				conn=null;
	            return true;
			}
			else {
				conn.close();
				conn=null;
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			writeLog("Fail to Close Connect to Mysql Server");
			return false;
		}
	}
	/**
	 * 设置数据库中邮件的状态
	 * @param mail 要修改的邮件
	 * @param status 目标状态
	 * @return
	 */
	public boolean setMailStatus(Mail mail,int status) {
		PreparedStatement stmt=null;
		String sql="UPDATE mail set status=? WHERE Num=?";
		try {
			if(!Connect()) {
				System.out.println("fail to connect");
			}
			else {
				stmt=conn.prepareStatement(sql);
				stmt.setInt(1, status);
				stmt.setInt(2, mail.getNum());
				stmt.executeUpdate();
				
				stmt.close();
				Close();
			}
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			Close();
			return false;
		}		
	}
	/**
	 * 根据用户邮箱获取用户昵称
	 * @param mailAddr
	 * @return
	 */
	public String getNickByMailAddr(String mailAddr){
		String res="";
		PreparedStatement stmt=null;
		ResultSet rs=null;
		String sql="SELECT nick FROM user WHERE account=?";
		try {
			if(!Connect()) {
				System.out.println("fail to connect");
			}
			else {
				stmt=conn.prepareStatement(sql);
				stmt.setString(1, mailAddr);
				rs=stmt.executeQuery();
				if(rs.next()){
	                res  = rs.getString("nick");
				}
			}
			stmt.close();
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();

		}finally {		
			Close();
			return res;
		}
	}
	/**
	 * 查找发到某邮箱的所有邮件
	 * @param toMailAddr
	 * @return
	 */
	public ArrayList<Mail> allMailOfSomeOne(String toMailAddr,int status){
		ArrayList<Mail> res=new ArrayList();
		PreparedStatement stmt=null;
		ResultSet rs=null;
		String sql="SELECT * FROM mail WHERE toMailAddr=? AND status=?";
		try {
			if(!Connect()) {
				System.out.println("fail to connect");
			}
			else {
				stmt=conn.prepareStatement(sql);
				stmt.setString(1, toMailAddr);
				stmt.setInt(2, status);
				rs=stmt.executeQuery();
				while(rs.next()){
	                // 通过字段检索
					int num = rs.getInt("Num");
					Date date = rs.getDate("sendDate");
	                String fromAddr  = rs.getString("fromMailAddr");
	                String toAddr = rs.getString("toMailAddr");
	                String path = rs.getString("dataPath");
	                int statuss = rs.getInt("status");
	                res.add(new Mail(num,date,fromAddr,toAddr,path,statuss));
	            }
				stmt.close();
				rs.close();
				Close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}		
		return res;
	}
	
	/**
	 * 查询此用户是否存在
	 * @param AccountOrNick
	 * @return
	 */
	public boolean haveUser(String AccountOrNick) {
		boolean res=false;
		PreparedStatement stmt=null;
		ResultSet rs=null;
		String sql="SELECT account FROM user WHERE account=?";
		try {
			if(!Connect()) {
				System.out.println("fail to connect");
			}
			else {
				stmt=conn.prepareStatement(sql);
				stmt.setString(1, AccountOrNick);
				rs=stmt.executeQuery();
				while(rs.next()){
	                // 通过字段检索
	                String account  = rs.getString("account");
//	                writeLog(account);
	                res= true;break;
	            }
				stmt.close();
				rs.close();
				Close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			res= false;
		}finally {
			return res;
		}

	}
	/**
	 * 验证密码
	 * @param AccountOrNick
	 * @param Pass
	 * @return
	 */
	public boolean verifyPass(String Account,String Pass) {
		boolean res=false;
		PreparedStatement stmt=null;
		ResultSet rs=null;
		String sql="SELECT password FROM user WHERE account=?";
		try {
			if(!Connect()) {
				System.out.println("fail to connect");
			}
			else {
				stmt=conn.prepareStatement(sql);
				stmt.setString(1, Account);
				rs=stmt.executeQuery();
				while(rs.next()){
	                // 通过字段检索
	                String pass  = rs.getString("password");
//	                writeLog(pass);
	                if(pass.equals(Pass)) {
	                	res= true;break;
	                }
	                else {
	                	res=false;
	                }            
	            }
				stmt.close();
				rs.close();
				Close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			res= false;
		}finally {
			return res;
		}	
	}
	/**
	 * 写入日志
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
}
/**
 * 邮件类
 * @author HWJ
 *
 */
class Mail{
	//邮寄序号（主键）
	private int NUM;
	//邮件发送日期
	private Date SENDDATE;
	//邮件的发送和接收邮箱
	private String FROM,TO;
	//存储邮件主题和正文的文件路径
	private String DATAPATH;
	//邮件状态0未读1已读-1已删除
	private int STATUS;
	//邮件主题
	private String SUBJECT;
	//邮件正文
	private String MAILBODY;
	
	public Mail(int num,Date date,String from,String to,String datapath,int status) {
		this.NUM=num;this.SENDDATE=date;this.FROM=from;this.TO=to;this.DATAPATH=datapath;this.STATUS=status;
		//初始化邮件主题和正文
		File f=new File(datapath);
		try {
			BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f)));
			String[] line=br.readLine().split(":");
			StringBuilder sb=new StringBuilder();
			if(line.length>1) {
				sb.append(line[1]);
				for(int i=2;i<line.length;i++)
					sb.append(":"+line[i]);
			}
			else {
				sb.append("");//主题为空
			}
			SUBJECT=sb.toString();//获取邮件主题
			
			sb=new StringBuilder();
			String str=null;
			while((str=br.readLine())!=null) {
				sb.append(str+"\r\n");
			}
			MAILBODY=sb.toString();		
		} catch (IOException e) {
			System.out.println("fail to read mailFile:"+datapath);
			e.printStackTrace();
		}
	}
	//外部获取邮件信息
	public int getNum() {return NUM;}
	public Date getSendDate() {return SENDDATE;}
	public String getFrom() {return FROM;}
	public String getTo() {return TO;}
	public String getDatapath() {return DATAPATH;}
	public String getSubject() {return SUBJECT;}
	public String getMailBody() {return MAILBODY;}
	public int getStatus() {return STATUS;}
	//邮件大小：包括主题和正文
	public int mesLength(){
		File f=new File(DATAPATH);		
		return (int) f.length();
	}
	
	public boolean setStatus(int status) {
		this.STATUS=status;
		return true;
	}
	public void display() {
	    System.out.print("FROM: " + FROM);
	    System.out.print("	TO: " + TO);
	    System.out.print("	DATAPATH: " + DATAPATH);
	    System.out.print("	STATUS: " + STATUS);
	    System.out.print("\n");
	}
	/**
	 * 写入日志
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
}

