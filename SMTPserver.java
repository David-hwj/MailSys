package ttt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
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
import java.text.SimpleDateFormat;
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
	
	//SMTP�����������Ķ˿�
	private static final int PORT=25;
	private static ServerSocket ss=null;
	
	//�ڷ����еĿͻ����� ʹ���߳�ͬ���ķ�������
	private static int clientCount=0;
	//�����״̬
	private boolean ISRUNNING=false;

	//����ģʽ��ֻ֤��һ��SMTP������
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
	
	public SmtpService(Socket client) {
		this.client=client;
	}
	
	/**
	 * provide Demo to Client
	 */
	private void pop3Service() {
		writeLog("provide Service for client : "+client.getInetAddress().getHostAddress()+":"+client.getPort());
		
		//��ȡ���������
		try {
			in=client.getInputStream();
			out=client.getOutputStream();
		} catch (IOException e) {
			writeLog(client.getInetAddress().getHostAddress()+":"+client.getPort()+" disconnected");
			e.printStackTrace();
		}
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
		BufferedReader br=new BufferedReader(new InputStreamReader(in));
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
			
				if(jdbc.haveUser(command)) {//�д��û�
					userMailAddr=command;
					userName=jdbc.getNickByMailAddr(userMailAddr);
					sendAnswer("250 OK");break;
				}
				else {//�޴��û�
					sendAnswer("251 ERR not exist user");
				}
				
			
		}
		//�û�����
		while(true) {
			String command=readLine();
					if(jdbc.verifyPass(userMailAddr, command)) {//������ȷ
						break;
					}
					else {//�������
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
		//mail from����
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
		//rcptto����
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
		//data����
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
				}
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
	 * ���½׶�
	 * @return
	 */
	private boolean update(SmtpMail mail) {
		SmtpJDBC jdbc=new SmtpJDBC();
		long time = System.currentTimeMillis();
		java.sql.Date date = new java.sql.Date(time);
		mail.setDate(date);
		java.sql.Timestamp theTime = new java.sql.Timestamp(System.currentTimeMillis());
		if(!mail.getTo().substring(mail.getTo().indexOf("@")+1).equals("davidhwj.cn")) {
			SendTMail ss = new SendTMail(mail);
			ss.send();
		}
		else {
			try {
	            File writeName = new File("C:\\ProgramData\\\\Mail\\"+mail.getSubject()+theTime.toString()+".txt"); // ���·�������û����Ҫ����һ���µ�output.txt�ļ�
	            writeName.createNewFile(); // �������ļ�,��ͬ�����ļ��Ļ�ֱ�Ӹ���
	            try (FileWriter writer = new FileWriter(writeName);
	                 BufferedWriter out = new BufferedWriter(writer)
	            ) {
	                out.write(mail.getMailBody());
	                out.flush();
	            }
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
			mail.setDatapath("C:\\ProgramData\\Mail\\"+mail.getSubject()+theTime.toString()+".txt");
			if(jdbc.Connect()) {
				jdbc.setMail(mail, 0);
			}
			else {
				writeLog("���½׶θ������ݿ�ʧ��");
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
	 * д����־
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


