//License
/***
 * Java TelnetD library (embeddable telnet daemon)
 * Copyright (c) 2000-2005 Dieter Wimberger 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the author nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS ``AS
 * IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 ***/

package uk.co.stikman.wimpi.telnetd.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.co.stikman.wimpi.telnetd.BootException;

/**
 * Class that takes care for active and queued connection. Housekeeping is done
 * also for connections that were just broken off, or exceeded their timeout.
 * 
 * @author Dieter Wimberger
 * @version 2.0 (16/07/2006)
 */
public class ConnectionManager implements Runnable {

	private static Log			log			= LogFactory.getLog(ConnectionManager.class);

	private Thread				thread;
	private ThreadGroup			threadGroup;												//ThreadGroup all connections run in
	private List<Connection>	openConnections;
	private Deque<Connection>	closedConnections;
	private ConnectionFilter	connectionFilter;											//reference to the connection filter
	private int					maxConnections;												//maximum allowed connections stored from the properties
	private int					warningTimeout;												//time to idle warning
	private int					disconnectTimeout;											//time to idle diconnection
	private int					housekeepingInterval;										//interval for managing cleanups
	private String				loginShell;
	private boolean				lineMode	= false;
	private boolean				stopping	= false;

	public ConnectionManager() {
		threadGroup = new ThreadGroup(new StringBuffer().append(this.toString()).append("Connections").toString());
		closedConnections = new LinkedList<>();
		openConnections = Collections.synchronizedList(new ArrayList<>(100));
	}

	public ConnectionManager(int con, int timew, int timedis, int hoke, ConnectionFilter filter, String lsh, boolean lm) {
		this();
		connectionFilter = filter;
		loginShell = lsh;
		lineMode = lm;
		maxConnections = con;
		warningTimeout = timew;
		disconnectTimeout = timedis;
		housekeepingInterval = hoke;
	}//constructor

	/**
	 * Set a connection filter for this ConnectionManager instance. The filter
	 * is used to handle IP level allow/deny of incoming connections.
	 *
	 * @param filter
	 *            ConnectionFilter instance.
	 */
	public void setConnectionFilter(ConnectionFilter filter) {
		connectionFilter = filter;
	}//setConnectionFilter

	/**
	 * Gets the active ConnectionFilter instance or returns null if no filter is
	 * set.
	 *
	 * @return the managers ConnectionFilter.
	 */
	public ConnectionFilter getConnectionFilter() {
		return connectionFilter;
	}//getConnectionFilter

	/**
	 * Returns the number of open connections.
	 * 
	 * @return the number of open connections as <tt>int</tt>.
	 */
	public int openConnectionCount() {
		return openConnections.size();
	}//openConnectionCount

	/**
	 * Returns the {@link Connection} at the given index.
	 * 
	 * @param idx
	 * @return
	 */
	public Connection getConnection(int idx) {
		synchronized (openConnections) {
			return (Connection) openConnections.get(idx);
		}
	}//getConnection

	/**
	 * Get all {@link Connection} instances with the given <tt>InetAddress</tt>.
	 *
	 * @return all {@link Connection} instances with the given
	 *         <tt>InetAddress</tt>.
	 */
	public Connection[] getConnectionsByAdddress(InetAddress addr) {
		List<Connection> l = new ArrayList<>();
		synchronized (openConnections) {
			for (Connection connection : openConnections)
				if (connection.getConnectionData().getInetAddress().equals(addr))
					l.add(connection);
		}
		Connection[] conns = new Connection[l.size()];
		return (Connection[]) l.toArray(conns);
	}//getConnectionsByAddress

	/**
	 * Starts this <tt>ConnectionManager</tt>.
	 */
	public void start() {
		thread = new Thread(this);
		thread.start();
	}//start

	/**
	 * Stops this <tt>ConnectionManager</tt>.
	 */
	public void stop() {
		log.debug("stop()::" + this.toString());
		stopping = true;
		//wait for thread to die
		try {
			if (thread != null) {
				thread.join();
			}
		} catch (InterruptedException iex) {
			log.error("stop()", iex);
		}
		synchronized (openConnections) {
			for (Iterator iter = openConnections.iterator(); iter.hasNext();) {
				try {
					Connection tc = (Connection) iter.next();
					//maybe write a disgrace to the socket?
					tc.close();
				} catch (Exception exc) {
					log.error("stop()", exc);
				}
			}
			openConnections.clear();
		}
		log.debug("stop():: Stopped " + this.toString());
	}//stop

	/**
	 * Method that that tries to connect an incoming request. Properly queueing.
	 *
	 * @param insock
	 *            Socket thats representing the incoming connection.
	 */
	public void makeConnection(Socket insock) {
		log.debug("makeConnection()::" + insock.toString());
		if (connectionFilter == null || (connectionFilter != null && connectionFilter.isAllowed(insock.getInetAddress()))) {
			//we create the connection data object at this point to
			//store certain information there.
			ConnectionData newCD = new ConnectionData(insock, this);
			newCD.setLoginShell(loginShell);
			newCD.setLineMode(lineMode);
			if (openConnections.size() < maxConnections) {
				//create a new Connection instance
				Connection con = new Connection(threadGroup, newCD);
				//log the newly created connection
				Object[] args = { new Integer(openConnections.size() + 1) };
				log.info(MessageFormat.format("connection #{0,number,integer} made.", args));
				//register it for being managed
				synchronized (openConnections) {
					openConnections.add(con);
				}
				//start it
				con.start();
			}
		} else {
			log.info("makeConnection():: Active Filter blocked incoming connection.");
			try {
				insock.close();
			} catch (IOException ex) {
				//do nothing or log.
			}
		}
	}//makeConnection

	/**
	 * Periodically does following work:
	 * <ul>
	 * <li>cleaning up died connections.
	 * <li>checking managed connections if they are working properly.
	 * <li>checking the open connections.
	 * </ul>
	 */
	public void run() {
		//housekeep connections
		try {
			do {
				//clean up and close all broken connections
				//cleanupBroken();
				//clean up closed connections
				cleanupClosed();
				//check all active connections
				checkOpenConnections();
				//sleep interval
				Thread.sleep(housekeepingInterval);
			} while (!stopping);

		} catch (Exception e) {
			log.error("run()", e);
		}
		log.debug("run():: Ran out " + this.toString());
	}//run

	/*
	private void cleanupBroken() {
	  //cleanup loop
	  while (!m_BrokenConnections.isEmpty()) {
	    Connection nextOne = (Connection) m_BrokenConnections.pop();
	    log.info("cleanupBroken():: Closing broken connection " + nextOne.toString());
	    //fire logoff event for shell site cleanup , beware could hog the daemon thread
	    nextOne.processConnectionEvent(new ConnectionEvent(nextOne, ConnectionEvent.CONNECTION_BROKEN));
	    //close the connection, will be automatically registered as closed
	    nextOne.close();
	  }
	}//cleanupBroken
	*/
	private void cleanupClosed() {
		if (stopping) {
			return;
		}
		//cleanup loop
		synchronized (closedConnections) {
			while (!closedConnections.isEmpty()) {
				Connection nextOne = (Connection) closedConnections.pop();
				log.info("cleanupClosed():: Removing closed connection " + nextOne.toString());
				openConnections.remove(nextOne);
			}
		}
	}//cleanupBroken

	private void checkOpenConnections() {
		if (stopping) {
			return;
		}
		//do routine checks on active connections
		synchronized (openConnections) {
			for (Connection conn : openConnections) {
				ConnectionData cd = conn.getConnectionData();
				//check if it is dead and remove it.
				if (!conn.isActive()) {
					registerClosedConnection(conn);
					continue;
				}
				/* Timeouts check */
				//first we caculate the inactivity time
				long inactivity = System.currentTimeMillis() - cd.getLastActivity();
				//now we check for warning and disconnection
				if (inactivity > warningTimeout) {
					//..and for disconnect
					if (inactivity > (disconnectTimeout + warningTimeout)) {
						//this connection needs to be disconnected :)
						log.debug("checkOpenConnections():" + conn.toString() + " exceeded total timeout.");
						//fire logoff event for shell site cleanup , beware could hog the daemon thread
						conn.processConnectionEvent(new ConnectionEvent(conn, ConnectionEvent.CONNECTION_TIMEDOUT));
						//conn.close();
					} else {
						//this connection needs to be warned :)
						if (!cd.isWarned()) {
							log.debug("checkOpenConnections():" + conn.toString() + " exceeded warning timeout.");
							cd.setWarned(true);
							//warning event is fired but beware this could hog the daemon thread!!
							conn.processConnectionEvent(new ConnectionEvent(conn, ConnectionEvent.CONNECTION_IDLE));
						}
					}
				}
			}
			/* end Timeouts check */
		}
	}//checkConnections

	/**
	 * Called by connections that got broken (i.e. I/O errors). The housekeeper
	 * will properly close the connection, and take care for misc necessary
	 * cleanup.
	 *
	 * @param con
	 *            the connection that is broken.
	 *
	 *            public void registerBrokenConnection(Connection con) { if
	 *            (!m_BrokenConnections.contains(con) &&
	 *            !m_ClosedConnections.contains(con)) {
	 *            log.debug("registerBrokenConnection()::" + con.toString());
	 *            m_BrokenConnections.push(con); } }//registerBrokenConnection
	 */
	public void registerClosedConnection(Connection con) {
		if (stopping) {
			return;
		}
		synchronized (closedConnections) {
			if (!closedConnections.contains(con)) {
				log.debug("registerClosedConnection()::" + con.toString());
				closedConnections.push(con);
			}
		}
	}//unregister

	/**
	 * Factory method for the ConnectionManager.<br>
	 * A class operation that will return a new ConnectionManager instance.
	 *
	 * @param settings
	 *            Properties containing the settings for this instance.
	 */
	public static ConnectionManager createConnectionManager(String name, Properties settings) throws BootException {

		try {
			int maxc = Integer.parseInt(settings.getProperty(name + ".maxcon"));
			int timow = Integer.parseInt(settings.getProperty(name + ".time_to_warning"));
			int timodis = Integer.parseInt(settings.getProperty(name + ".time_to_timedout"));
			int hoke = Integer.parseInt(settings.getProperty(name + ".housekeepinginterval"));
			String filterclass = settings.getProperty(name + ".connectionfilter");
			ConnectionFilter filter = null;
			String loginshell = "";
			boolean linemode = false;
			if (filterclass != null && filterclass.length() != 0 && !filterclass.toLowerCase().equals("none")) {
				//load filter
				filter = (ConnectionFilter) Class.forName(filterclass).newInstance();
				filter.initialize(settings);
			}
			loginshell = settings.getProperty(name + ".loginshell");
			if (loginshell == null || loginshell.length() == 0) {
				log.error("Login shell not specified.");
				throw new BootException("Login shell must be specified.");
			}
			String inputmode = settings.getProperty(name + ".inputmode");
			if (inputmode == null || inputmode.length() == 0) {
				log.info("Input mode not specified using character input as default.");
				linemode = false;
			} else if (inputmode.toLowerCase().equals("line")) {
				linemode = true;
			}
			//return fabricated manager
			ConnectionManager cm = new ConnectionManager(maxc, timow, timodis, hoke, filter, loginshell, linemode);
			//set higher priority!
			//cm.setPriority(Thread.NORM_PRIORITY + 2);
			return cm;
		} catch (Exception ex) {
			log.error("createConnectionManager():", ex);
			throw new BootException("Failure while creating ConnectionManger instance:\n" + ex.getMessage());
		}
	}//createManager

	public int getDisconnectTimeout() {
		return disconnectTimeout;
	}

	public void setDisconnectTimeout(int disconnectTimeout) {
		this.disconnectTimeout = disconnectTimeout;
	}

	public int getHousekeepingInterval() {
		return housekeepingInterval;
	}

	public void setHousekeepingInterval(int housekeepingInterval) {
		this.housekeepingInterval = housekeepingInterval;
	}

	public boolean isLineMode() {
		return lineMode;
	}

	public void setLineMode(boolean lineMode) {
		this.lineMode = lineMode;
	}

	public String getLoginShell() {
		return loginShell;
	}

	public void setLoginShell(String loginShell) {
		this.loginShell = loginShell;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public int getWarningTimeout() {
		return warningTimeout;
	}

	public void setWarningTimeout(int warningTimeout) {
		this.warningTimeout = warningTimeout;
	}

}//class ConnectionManager
