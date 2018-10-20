package uk.co.stikman.telnetd.standalone;

import java.io.IOException;

import uk.co.stikman.wimpi.telnetd.io.BasicTerminalIO;
import uk.co.stikman.wimpi.telnetd.net.Connection;
import uk.co.stikman.wimpi.telnetd.net.ConnectionData;
import uk.co.stikman.wimpi.telnetd.net.ConnectionEvent;
import uk.co.stikman.wimpi.telnetd.shell.Shell;

public class MyShell implements Shell {

	private BasicTerminalIO	io;
	private Connection		conn;

	@Override
	public void run(Connection conn) {
		io = conn.getTerminalIO();
		this.conn = conn;
		conn.addConnectionListener(this);

		try {

			ConnectionData cd = conn.getConnectionData();
			io.write("               getHostName = " + cd.getHostName() + "\n");
			io.write("            getHostAddress = " + cd.getHostAddress() + "\n");
			io.write("                   getPort = " + cd.getPort() + "\n");
			io.write("                 getLocale = " + cd.getLocale() + "\n");
			io.write("           getLastActivity = " + cd.getLastActivity() + "\n");
			io.write("                 getWarned = " + cd.isWarned() + "\n");
			io.write(" getNegotiatedTerminalType = " + cd.getNegotiatedTerminalType() + "\n");
			io.write("       getTerminalGeometry = " + cd.getTerminalGeometry() + "\n");
			io.write("getTerminalGeometryChanged = " + cd.isTerminalGeometryChanged() + "\n");
			io.write("             getLoginShell = " + cd.getLoginShell() + "\n");
			io.write("               getLineMode = " + cd.isLineMode() + "\n");
			io.write("               getEchoMode = " + cd.getEchoMode() + "\n");
			io.write("  UTF8 symbol: ♥\n");
			io.write("\n");
			for (;;) {
				int i = io.read();
				io.write(safeChar((char) i) + " (0x" + Integer.toHexString(i) + ")\n");
				if (i == 'q')
					break;
			}
		} catch (Exception e) {
			conn.close();
			throw new RuntimeException(e);
		}
	}

	private char safeChar(char ch) {
		if (ch < 32)
			return '.';
		return ch;
	}

	@Override
	public void connectionTimedOut(ConnectionEvent ce) {
		try {
			io.write("CONNECTION_TIMEDOUT");
			io.flush();
			//close connection
			conn.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void connectionIdle(ConnectionEvent ce) {
		try {
			io.write("CONNECTION_IDLE");
			io.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void connectionLogoutRequest(ConnectionEvent ce) {
		try {
			io.write("CONNECTION_LOGOUTREQUEST");
			io.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void connectionSentBreak(ConnectionEvent ce) {
		try {
			io.write("CONNECTION_BREAK");
			io.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static MyShell createShell() {
		return new MyShell();
	}

}
