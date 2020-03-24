package mailServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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

public class SMTPserver {
	public static void main(String[] args) {
		
		/*******����������***************************/
		SMTPserver server=SMTPserver.createDemo();
		server.start();
//		server.stop();
		
		/********���ݿ����Ӳ���************************/
		
//		JDBC jdbc=new SmtpJDBC();
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
	
	//POP3�����������Ķ˿�
	private static final int PORT=25;
	private static ServerSocket ss=null;
	
	//�ڷ����еĿͻ����� ʹ���߳�ͬ���ķ�������
	private static int clientCount=0;
	//�����״̬
	private boolean ISRUNNING=false;

	//����ģʽ��ֻ֤��һ��POP3������
	private static SMTPserver server=null;
	private SMTPserver() {}
	public static SMTPserver createDemo() {
		if(server==null) {
			server=new SMTPserver();
			return server;
		}
		else {
			return server;
		}
	}
	/**
	 * ����������
	 * @return
	 */
	public boolean start() {
		
		if(ss==null) {
			try {
				ss=new ServerSocket(PORT);
			} catch (IOException e) {
				writeLog("Demo fail to listen Port "+PORT);
				e.printStackTrace();
				return false;
			}
			Thread task=new Thread() {
				//�߳����ѭ���ȴ�����
				public void run() {
					while(true) {
						try {
							Socket client=ss.accept();	
							writeLog("A client connect:"+client.getInetAddress().getHostAddress()+":"+client.getPort());
							
							SmtpService service=new SmtpService(client);
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
			writeLog("Demo is running...");
		}	
		ISRUNNING=true;
		return true;
	}
	/**
	 * ֹͣ������
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
				writeLog("fail to stop Demo "+clientCount+" are connecting");
				return false;
			}
			else {
				if(ss!=null&&!ss.isClosed()) {
					ss.close();
				}
				writeLog("Success to close Demo");
				ss=null;
				ISRUNNING=false;//�رճɹ�
				return true;
			}
			
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	//�ͻ������ķ��ʷ���
	synchronized public static void addClientCount() {
		clientCount++;
	}
	synchronized public static void decClientCount() {
		clientCount--;
	}
	//������״̬�����÷���
	public boolean getISRUNNING() {return ISRUNNING;}
	/**
	 * д����־
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
	

}
/**
 * Ϊ�ͻ����ṩ����
 * @author HWJ
 *
 */
class SmtpService extends Thread{
	private Socket client;
	private InputStream in;
	private OutputStream out;
	private String userName;
	private String userMailAddr;
	private ArrayList<SmtpMail> mails=new ArrayList();
	BufferedReader br;
	
	public SmtpService(Socket client) {
		this.client=client;
		try {
			br=new BufferedReader(new InputStreamReader(client.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * provide Demo to Client
	 */
	private void pop3Service() {
		writeLog("provide pop3Service for client : "+client.getInetAddress().getHostAddress()+":"+client.getPort());
		
		//��ȡ���������
		try {
			in=client.getInputStream();
			out=client.getOutputStream();
		} catch (IOException e) {
			writeLog(client.getInetAddress().getHostAddress()+":"+client.getPort()+" disconnected");
			e.printStackTrace();
		}
		/*******pop3Э������*************/
		sendAnswer("220 +OK SMTP server ready");
		//����׶�
		authorization();
		//���ﴦ��׶�
		resolve();
		try {
			client.close();
		} catch (IOException e) {
			e.printStackTrace();
			writeLog("fail to diconnect client:"+client.getInetAddress()+":"+client.getPort());
		}
	}
	/**
	 * ��ȡһ��
	 * @return
	 */
	private String readLine() {
		String res="";
		
		try {
			res=br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		return res;
	}
	/**
	 * ���ͻ��˻���Ϣ
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
	 * ����׶�
	 * @return
	 */
	private boolean authorization() {
		SmtpJDBC jdbc=new SmtpJDBC();
		jdbc.Connect();
		boolean flag = false;
		String[] avgs;
		while(true) {
			String command=readLine();
			avgs=command.split(" ");
			if(avgs[0].toLowerCase().equals("helo")) {
				sendAnswer("220 wait");
				return true;
			}
			if(!command.toLowerCase().equals("auth login") && !flag) {
				sendAnswer("502");
				continue;
			}
			else if(command.toLowerCase().equals("auth login")) {
				flag = true;
				continue;
			}
			
				if(jdbc.haveUser(command)) {//有此用户
					userMailAddr=command;
					userName=jdbc.getNickByMailAddr(userMailAddr);
					sendAnswer("250 OK");break;
				}
				else {//无此用户
					sendAnswer("251 ERR not exist user");
				}
				
			
		}
		//用户密码
		while(true) {
			String command=readLine();
					if(jdbc.verifyPass(userMailAddr, command)) {//密码正确
						break;
					}
					else {//密码错误
						sendAnswer("504 ERR worry password");
					}
				
			
		}
		sendAnswer("235 go ahead");
		jdbc.Close();
		return true;
	}
	/**
	 * ���ﴦ��׶�
	 * @return
	 */
private boolean resolve() {
		
		String command;
		String[] avgs;
		boolean dataFlag = false;
		SmtpMail mail = new SmtpMail();
		//mail from命令
		while(true) {
			command=readLine();
			avgs=command.split(":");
			if(avgs[0].toLowerCase().equals("mail from")) {
				mail.setFrom(avgs[1].substring(avgs[1].indexOf("<")+1, avgs[1].indexOf(">")));
				sendAnswer("250 sender <"+avgs[1].substring(avgs[1].indexOf("<")+1, avgs[1].indexOf(">"))+"> ok");
				break;
			}
			else {
				sendAnswer("503 invalid command");
			}
		}
		//rcptto命令
		while(true) {
			command=readLine();
			avgs=command.split(":");
			if(avgs[0].toLowerCase().equals("rcpt to")) {
				mail.setTo(avgs[1].substring(avgs[1].indexOf("<")+1, avgs[1].indexOf(">")));
				sendAnswer("250 recipien <"+avgs[1].substring(avgs[1].indexOf("<")+1, avgs[1].indexOf(">"))+"> ok");
				break;
			}
			else {
				sendAnswer("503 invalid command");
			}
		}
		//data命令
		while(true) {
			command=readLine();
			if(command.toLowerCase().equals("data") && !dataFlag) {
				dataFlag = true;
				sendAnswer("250 ok");
			}
			else if(!command.toLowerCase().equals("data") && !dataFlag) {
				sendAnswer("503 invalid command");
			}
			else if(command.equals(".")){
				sendAnswer("250 ok:  Message "+mail.getMailBody().length()+ "accepted");
				break;
			}
			else {
				avgs=command.split(":");
				if(avgs.length>1 && avgs[0].toLowerCase().equals("subject")) {
					mail.setSubject(avgs[1]);
					mail.appendMain(command+"\r\n");
				}
				else
					mail.appendMain(command);
				sendAnswer("250 succcuss");
			}
		}
		while(true) {
			command=readLine();
			if(command.toLowerCase().equals("quit")) {
				sendAnswer("220 ok");
				break;
			}
		}
		update(mail);
		
		return true;
	}
	/**
	 * 更新阶段
	 * @return
	 */
	private boolean update(SmtpMail mail) {
		SmtpJDBC jdbc=new SmtpJDBC();
		long time = System.currentTimeMillis();
		java.sql.Date date = new java.sql.Date(time);
		mail.setDate(date);
		java.sql.Timestamp theTime = new java.sql.Timestamp(System.currentTimeMillis());
		String timeTemp = theTime.toString().replaceAll(" ", "");
		timeTemp = timeTemp.replaceAll("\\.", "_");
		timeTemp = timeTemp.replaceAll("\\:", "_");
		timeTemp = timeTemp.replaceAll("-", "_");
		if(false) {
			
		}
		else {
			try {
	            File writeName = new File("C:\\ProgramData\\Mail\\"+mail.getSubject()+timeTemp+".txt"); // 相对路径，如果没有则要建立一个新的output.txt文件
	            writeName.createNewFile(); // 创建新文件,有同名的文件的话直接覆盖
	            try (FileWriter writer = new FileWriter(writeName);
	                 BufferedWriter out = new BufferedWriter(writer)
	            ) {
	                out.write(mail.getMailBody());
	                out.flush();
	            }
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
			mail.setDatapath("C:\\ProgramData\\Mail\\"+mail.getSubject()+timeTemp+".txt");
			if(jdbc.Connect()) {
				jdbc.setMail(mail, 0);
			}
			else {
				writeLog("更新阶段更新数据库失败");
				return false;
			}
		}
		
		jdbc.Close();
		return true;
	}
	@Override
	public void run() {
		SMTPserver.addClientCount();
		pop3Service();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		SMTPserver.decClientCount();
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
 * ���ݿ����
 * @author HWJ
 *
 */
class SmtpJDBC{
	// ���ݿ� URL
    static final String DB_URL = "jdbc:mysql://47.106.20.56:3306/mailserver?useSSL=false&serverTimezone=UTC";
 
    // ���ݿ���û���������
    static final String USERNAME = "root";
    static final String PASSWORD = "@SkyrimOf3";
    		
    //���ݿ�����
    private static Connection conn=null;
    
	public SmtpJDBC() {
		// register SmtpJDBC Driver
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
	 * �������ݿ����ʼ���״̬
	 * @param mail Ҫ�޸ĵ��ʼ�
	 * @param status Ŀ��״̬
	 * @return
	 */
	public boolean setMail(SmtpMail mail,int status) {
		PreparedStatement stmt=null;
		String sql="Insert Into mail(sendDate,fromMailAddr,toMailAddr,dataPath,status) Values (?,?,?,?,?)";
		try {
			if(!Connect()) {
				System.out.println("fail to connect");
			}
			else {
				stmt=conn.prepareStatement(sql);
				stmt.setDate(1, mail.getSendDate());
				stmt.setString(2, mail.getFrom());
				stmt.setString(3, mail.getTo());
				stmt.setString(4, mail.getDatapath());
				stmt.setInt(5, status);
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
	 * �����û������ȡ�û��ǳ�
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
	 * ��ѯ���û��Ƿ����
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
	                // ͨ���ֶμ���
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
	 * ��֤����
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
	                // ͨ���ֶμ���
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
	 * д����־
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
}
/**
 * �ʼ���
 * @author HWJ
 *
 */
class SmtpMail{
	//�ʼ���ţ�������
	private int NUM;
	//�ʼ���������
	private Date SENDDATE;
	//�ʼ��ķ��ͺͽ�������
	private String FROM,TO;
	//�洢�ʼ���������ĵ��ļ�·��
	private String DATAPATH;
	//�ʼ�״̬0δ��1�Ѷ�-1��ɾ��
	private int STATUS;
	private String SUBJECT;
	//�ʼ�����
	private String MAILBODY;
	
	public SmtpMail() {
		MAILBODY = "";
	}
	
	//�ⲿ��ȡ�ʼ���Ϣ
	public int getNum() {return NUM;}
	public Date getSendDate() {return SENDDATE;}
	public String getFrom() {return FROM;}
	public String getTo() {return TO;}
	public String getDatapath() {return DATAPATH;}
	public String getMailBody() {return MAILBODY;}
	public String getSubject() {return SUBJECT;}
	public int getStatus() {return STATUS;}
	//����
	public void setDate(Date date) {this.SENDDATE = date;}
	public void setFrom(String from) {this.FROM = from;}
	public void setTo(String to) {this.TO = to;}
	public void setDatapath(String path) {this.DATAPATH = path;}
	public void setSubject(String subject) {this.SUBJECT = subject;}
	public void appendMain(String temp) {MAILBODY = MAILBODY.concat(temp);}
	//�ʼ���С���������������
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
	 * д����־
	 * @param log
	 */
	private void writeLog(String log) {
		System.out.println(log);
	}
}

