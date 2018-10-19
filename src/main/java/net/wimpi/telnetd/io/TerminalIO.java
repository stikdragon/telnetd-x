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

package net.wimpi.telnetd.io;

import net.wimpi.telnetd.io.terminal.Terminal;
import net.wimpi.telnetd.io.terminal.TerminalManager;
import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionData;
import net.wimpi.telnetd.net.ConnectionEvent;
import net.wimpi.telnetd.util.Mutex;
import net.wimpi.telnetd.util.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Class for Terminal specific I/O. It represents the layer between the
 * application layer and the generic telnet I/O. Terminal specific I/O is
 * achieved via pluggable terminal classes
 *
 * @author Dieter Wimberger
 * @version 2.0 (16/07/2006)
 * @see net.wimpi.telnetd.io.TelnetIO
 * @see net.wimpi.telnetd.io.terminal.Terminal
 */
public class TerminalIO implements BasicTerminalIO {

	private static Log			log	= LogFactory.getLog(TerminalIO.class);
	private TelnetIO			telnetIO;									//low level I/O
	private TelnetInputStream	telnetInputStream;

	private Connection			connection;									//the connection this instance is working for
	private ConnectionData		connectionData;								//holds data of the connection
	private Terminal			terminal;									//active terminal object
	private ReentrantLock		writeLock;
	private Mutex				readLock;
	//Members
	private boolean				acousticSignalling;							//flag for accoustic signalling
	private boolean				autoflush;									//flag for autoflushing mode
	private boolean				forceBold;									//flag for forcing bold output
	private boolean				lineWrapping;

	/**
	 * Constructor of the TerminalIO class.
	 *
	 * @param con
	 *            Connection the instance will be working for
	 */
	public TerminalIO(Connection con) {
		connection = con;
		acousticSignalling = true;
		autoflush = true;
		writeLock = new ReentrantLock();
		readLock = new Mutex();
		//store the associated  ConnectionData instance
		connectionData = connection.getConnectionData();
		try {
			//create a new telnet io
			telnetIO = new TelnetIO();
			telnetInputStream = new TelnetInputStream(telnetIO);
			telnetIO.setConnection(con);
			telnetIO.initIO();

		} catch (Exception ex) {
			//handle, at least log
		}

		//set default terminal
		try {
			setDefaultTerminal();
		} catch (Exception ex) {
			log.error("TerminalIO()", ex);
			throw new RuntimeException();
		}
	}//constructor

	/************************************************************************
	 * Visible character I/O methods *
	 ************************************************************************/

	/**
	 * Read a single character and take care for terminal function calls.
	 *
	 * @return
	 *         <ul>
	 *         <li>character read
	 *         <li>IOERROR in case of an error
	 *         <li>DELETE,BACKSPACE,TABULATOR,ESCAPE,COLORINIT,LOGOUTREQUEST
	 *         <li>UP,DOWN,LEFT,RIGHT
	 *         </ul>
	 */
	public int read() throws IOException {
		try {
			readLock.acquire();

			int i = telnetInputStream.readChar();

			//translate possible control sequences
			int cc = terminal.translateControlCharacter(i);

			//catch & fire a logoutrequest event
			if (cc == LOGOUTREQUEST) {
				connection.processConnectionEvent(new ConnectionEvent(connection, ConnectionEvent.CONNECTION_LOGOUTREQUEST));
				cc = HANDLED;
			} else if (cc == ESCAPE) {
				//translate an incoming escape sequence
				i = handleEscapeSequence(cc);
			}

			//return i holding a char or a defined special key
			return i;
		} catch (InterruptedException ex) {
			return -1;
		} finally {
			readLock.release();
		}

	}//read

	public void write(byte b) throws IOException {
		telnetIO.write(b);
		if (autoflush) {
			flush();
		}
	}//write

	public void write(char ch) throws IOException {
		try {
			writeLock.acquire();
			telnetIO.write(ch);
			if (autoflush) {
				flush();
			}
		} catch (InterruptedException ex) {
			log.error("write(byte)", ex);
		} finally {
			writeLock.release();
		}
	}//write(char)

	public void write(String str) throws IOException {
		try {
			writeLock.acquire();
			if (forceBold) {
				telnetIO.write(terminal.formatBold(str));
			} else {
				telnetIO.write(terminal.format(str));
			}
			if (autoflush) {
				flush();
			}
		} catch (InterruptedException ex) {
			log.error("write(byte)", ex);
		} finally {
			writeLock.release();
		}
	}//write(String)

	/*** End of Visible character I/O methods ******************************/

	/**
	 * *********************************************************************
	 * Erase methods *
	 * **********************************************************************
	 */

	public synchronized void eraseToEndOfLine() throws IOException {
		doErase(EEOL);
	}//eraseToEndOfLine

	public synchronized void eraseToBeginOfLine() throws IOException {
		doErase(EBOL);
	}//eraseToBeginOfLine

	public synchronized void eraseLine() throws IOException {
		doErase(EEL);
	}//eraseLine

	public synchronized void eraseToEndOfScreen() throws IOException {
		doErase(EEOS);
	}//eraseToEndOfScreen

	public synchronized void eraseToBeginOfScreen() throws IOException {
		doErase(EBOS);
	}//eraseToBeginOfScreen

	public synchronized void eraseScreen() throws IOException {
		doErase(EES);
	}//eraseScreen

	private void doErase(int funcConst) throws IOException {
		try {
			writeLock.acquire();
			telnetIO.write(terminal.getEraseSequence(funcConst));
			if (autoflush) {
				flush();
			}
		} catch (InterruptedException ex) {
			log.error("doErase(int)", ex);
		} finally {
			writeLock.release();
		}
	}//erase

	/*** End of Erase methods **********************************************/

	/**
	 * *********************************************************************
	 * Cursor related methods *
	 * **********************************************************************
	 */

	public void moveCursor(int direction, int times) throws IOException {
		try {
			writeLock.acquire();
			telnetIO.write(terminal.getCursorMoveSequence(direction, times));
			if (autoflush) {
				flush();
			}
		} catch (InterruptedException ex) {
			log.error("moveCursor(int,int)", ex);
		} finally {
			writeLock.release();
		}
	}//moveCursor

	public void moveLeft(int times) throws IOException {
		moveCursor(LEFT, times);
	}//moveLeft

	public void moveRight(int times) throws IOException {
		moveCursor(RIGHT, times);
	}//moveRight

	public void moveUp(int times) throws IOException {
		moveCursor(UP, times);
	}//moveUp

	public void moveDown(int times) throws IOException {
		moveCursor(DOWN, times);
	}//moveDown

	public void setCursor(int row, int col) throws IOException {
		int[] pos = new int[2];
		pos[0] = row;
		pos[1] = col;
		try {
			writeLock.acquire();
			telnetIO.write(terminal.getCursorPositioningSequence(pos));
			if (autoflush) {
				flush();
			}
		} catch (InterruptedException ex) {
			log.error("setCursor(int,int)", ex);
		} finally {
			writeLock.release();
		}
	}//setCursor

	public void homeCursor() throws IOException {
		try {
			writeLock.acquire();
			telnetIO.write(terminal.getCursorPositioningSequence(HOME));
			if (autoflush) {
				flush();
			}
		} catch (InterruptedException ex) {
			log.error("homeCursor()", ex);
		} finally {
			writeLock.release();
		}
	}//homeCursor

	public void storeCursor() throws IOException {
		try {
			writeLock.acquire();
			telnetIO.write(terminal.getSpecialSequence(STORECURSOR));
		} catch (InterruptedException ex) {
			log.error("storeCursor()", ex);
		} finally {
			writeLock.release();
		}
	}//store Cursor

	public synchronized void restoreCursor() throws IOException {
		try {
			writeLock.acquire();
			telnetIO.write(terminal.getSpecialSequence(RESTORECURSOR));
		} catch (InterruptedException ex) {
			log.error("write(byte)", ex);
		} finally {
			writeLock.release();
		}
	}//restore Cursor

	/*** End of cursor related methods **************************************/

	/**
	 * *********************************************************************
	 * Special terminal function methods *
	 * **********************************************************************
	 */

	public synchronized void setSignalling(boolean bool) {
		acousticSignalling = bool;
	}//setAcousticSignalling

	public synchronized boolean isSignalling() {
		return acousticSignalling;
	}//isAcousticSignalling

	/**
	 * Method to write the NVT defined BEL onto the stream. If signalling is
	 * off, the method simply returns, without any action.
	 */
	public synchronized void bell() throws IOException {
		if (acousticSignalling) {
			telnetIO.write(BEL);
		}
		if (autoflush) {
			flush();
		}
	}//bell

	/**
	 * EXPERIMENTAL, not defined in the interface.
	 */
	public synchronized boolean defineScrollRegion(int topmargin, int bottommargin) throws IOException {
		if (terminal.supportsScrolling()) {
			telnetIO.write(terminal.getScrollMarginsSequence(topmargin, bottommargin));
			flush();
			return true;
		} else {
			return false;
		}
	}//defineScrollRegion

	public synchronized void setForegroundColor(int color) throws IOException {
		if (terminal.supportsSGR()) {
			telnetIO.write(terminal.getGRSequence(FCOLOR, color));
			if (autoflush) {
				flush();
			}
		}
	}//setForegroundColor

	public synchronized void setBackgroundColor(int color) throws IOException {
		if (terminal.supportsSGR()) {
			//this method adds the offset to the fg color by itself
			telnetIO.write(terminal.getGRSequence(BCOLOR, color + 10));
			if (autoflush) {
				flush();
			}
		}
	}//setBackgroundColor

	public synchronized void setBold(boolean b) throws IOException {
		if (terminal.supportsSGR()) {
			if (b) {
				telnetIO.write(terminal.getGRSequence(STYLE, BOLD));
			} else {
				telnetIO.write(terminal.getGRSequence(STYLE, BOLD_OFF));
			}
			if (autoflush) {
				flush();
			}
		}
	}//setBold

	public synchronized void forceBold(boolean b) {
		forceBold = b;
	}//forceBold

	public synchronized void setUnderlined(boolean b) throws IOException {
		if (terminal.supportsSGR()) {
			if (b) {
				telnetIO.write(terminal.getGRSequence(STYLE, UNDERLINED));
			} else {
				telnetIO.write(terminal.getGRSequence(STYLE, UNDERLINED_OFF));
			}
			if (autoflush) {
				flush();
			}

		}
	}//setUnderlined

	public synchronized void setItalic(boolean b) throws IOException {
		if (terminal.supportsSGR()) {
			if (b) {
				telnetIO.write(terminal.getGRSequence(STYLE, ITALIC));
			} else {
				telnetIO.write(terminal.getGRSequence(STYLE, ITALIC_OFF));
			}
			if (autoflush) {
				flush();
			}
		}
	}//setItalic

	public synchronized void setBlink(boolean b) throws IOException {
		if (terminal.supportsSGR()) {
			if (b) {
				telnetIO.write(terminal.getGRSequence(STYLE, BLINK));
			} else {
				telnetIO.write(terminal.getGRSequence(STYLE, BLINK_OFF));
			}
			if (autoflush) {
				flush();
			}
		}
	}//setItalic

	public synchronized void resetAttributes() throws IOException {
		if (terminal.supportsSGR()) {
			telnetIO.write(terminal.getGRSequence(RESET, 0));
		}
	}//resetGR

	/*** End of special terminal function methods ***************************/

	/************************************************************************
	 * Auxiliary I/O methods *
	 ************************************************************************/

	/**
	 * Method that parses forward for escape sequences
	 */
	private int handleEscapeSequence(int i) throws IOException {
		if (i == ESCAPE) {
			int[] bytebuf = new int[terminal.getAtomicSequenceLength()];
			//fill atomic length
			//FIXME: ensure CAN, broken Escapes etc.
			for (int m = 0; m < bytebuf.length; m++) {
				bytebuf[m] = telnetInputStream.read();
			}
			return terminal.translateEscapeSequence(bytebuf);
		}
		if (i == BYTEMISSING) {
			//FIXME:longer escapes etc...
		}

		return HANDLED;
	}//handleEscapeSequence

	/**
	 * Accessor method for the autoflushing mechanism.
	 */
	public boolean isAutoflushing() {
		return autoflush;
	}//isAutoflushing

	public synchronized void resetTerminal() throws IOException {
		telnetIO.write(terminal.getSpecialSequence(DEVICERESET));
	}

	public synchronized void setLinewrapping(boolean b) throws IOException {
		if (b && !lineWrapping) {
			telnetIO.write(terminal.getSpecialSequence(LINEWRAP));
			lineWrapping = true;
			return;
		}
		if (!b && lineWrapping) {
			telnetIO.write(terminal.getSpecialSequence(NOLINEWRAP));
			lineWrapping = false;
			return;
		}
	}//setLineWrapping

	public boolean isLineWrapping() {
		return lineWrapping;
	}//

	/**
	 * Mutator method for the autoflushing mechanism.
	 */
	public synchronized void setAutoflushing(boolean b) {
		autoflush = b;
	}//setAutoflushing

	/**
	 * Method to flush the Low-Level Buffer
	 */
	public synchronized void flush() throws IOException {
		telnetIO.flush();
	}//flush (implements the famous iToilet)

	public synchronized void close() {
		telnetIO.closeOutput();
		telnetIO.closeInput();
	}//close

	/*** End of Auxiliary I/O methods **************************************/

	/************************************************************************
	 * Terminal management specific methods *
	 ************************************************************************/

	/**
	 * Accessor method to get the active terminal object
	 *
	 * @return Object that implements Terminal
	 */
	public Terminal getTerminal() {
		return terminal;
	}//getTerminal

	/**
	 * Sets the default terminal ,which will either be the negotiated one for
	 * the connection, or the systems default.
	 */
	public void setDefaultTerminal() throws IOException {
		//set the terminal passing the negotiated string
		setTerminal(connectionData.getNegotiatedTerminalType());
	}//setDefaultTerminal

	/**
	 * Mutator method to set the active terminal object If the String does not
	 * name a terminal we support then the vt100 is the terminal of selection
	 * automatically.
	 *
	 * @param terminalName
	 *            String that represents common terminal name
	 */
	public void setTerminal(String terminalName) throws IOException {

		terminal = TerminalManager.getReference().getTerminal(terminalName);
		//Terminal is set we init it....
		initTerminal();
		//debug message
		log.debug("Set terminal to " + terminal.toString());
	}//setTerminal

	/**
	 * Terminal initialization
	 */
	private synchronized void initTerminal() throws IOException {
		telnetIO.write(terminal.getInitSequence());
		flush();
	}//initTerminal

	/**
	 *
	 */
	public int getRows() {
		return connectionData.getTerminalRows();
	}//getRows

	/**
	 *
	 */
	public int getColumns() {
		return connectionData.getTerminalColumns();
	}//getColumns

	/**
	 * Accessor Method for the terminal geometry changed flag
	 */
	public boolean isTerminalGeometryChanged() {
		return connectionData.isTerminalGeometryChanged();
	}//isTerminalGeometryChanged

	/*** End of terminal management specific methods ***********************/

	/** Constants Declaration **********************************************/

	/**
	 * Terminal independent representation constants for terminal functions.
	 * Have remapped these so that they fit into the Unicode BMP private use
	 * area range (U+E000 to U+F8FF)
	 */
	public static final int[]	HOME			= { 0, 0 };

	public static final int		IOERROR			= -1;													//IO error
	public static final int																				// Positioning 10xx
								UP				= 0xe001;												//one up
	public static final int		DOWN			= 0xe002;												//one down
	public static final int		RIGHT			= 0xe003;												//one left
	public static final int		LEFT			= 0xe004;												//one right
	//HOME=1005,      //Home cursor pos(0,0)

	public static final int																				// Functions 105x
								STORECURSOR		= 0xe051;												//store cursor position + attributes
	public static final int		RESTORECURSOR	= 0xe052;												//restore cursor + attributes

	public static final int																				// Erasing 11xx
								EEOL			= 0xe100;												//erase to end of line
	public static final int		EBOL			= 0xe101;												//erase to beginning of line
	public static final int		EEL				= 0xe103;												//erase entire line
	public static final int		EEOS			= 0xe104;												//erase to end of screen
	public static final int		EBOS			= 0xe105;												//erase to beginning of screen
	public static final int		EES				= 0xe106;												//erase entire screen

	public static final int																				// Escape Sequence-ing 12xx
								ESCAPE			= 0xe200;												//Escape
	public static final int		BYTEMISSING		= 0xe201;												//another byte needed
	public static final int		UNRECOGNIZED	= 0xe202;												//escape match missed

	public static final int																				// Control Characters 13xx
								ENTER			= 0xe300;												//LF is ENTER at the moment
	public static final int		TABULATOR		= 0xe301;												//Tabulator
	public static final int		DELETE			= 0xe302;												//Delete
	public static final int		BACKSPACE		= 0xe303;												//BACKSPACE
	public static final int		COLORINIT		= 0xe304;												//Color inited
	public static final int		HANDLED			= 0xe305;
	public static final int		LOGOUTREQUEST	= 0xe306;												//CTRL-D beim login

	/**
	 * Internal UpdateType Constants
	 */
	public static final int		LineUpdate		= 475, CharacterUpdate = 476, ScreenpartUpdate = 477;

	/**
	 * Internal BufferType Constants
	 */
	public static final int		EditBuffer		= 575, LineEditBuffer = 576;

	/**
	 * Network Virtual Terminal Specific Keys Thats what we have to offer at
	 * least.
	 */
	public static final int		BEL				= 7;
	public static final int		BS				= 8;
	public static final int		DEL				= 127;
	public static final int		CR				= 13;
	public static final int		LF				= 10;

	public static final int		FCOLOR			= 10001;
	public static final int		BCOLOR			= 10002;
	public static final int		STYLE			= 10003;
	public static final int		RESET			= 10004;
	public static final int		BOLD			= 1;
	public static final int		BOLD_OFF		= 22;
	public static final int		ITALIC			= 3;
	public static final int		ITALIC_OFF		= 23;
	public static final int		BLINK			= 5;
	public static final int		BLINK_OFF		= 25;
	public static final int		UNDERLINED		= 4;
	public static final int		UNDERLINED_OFF	= 24;
	public static final int		DEVICERESET		= 10005;
	public static final int		LINEWRAP		= 10006;
	public static final int		NOLINEWRAP		= 10007;

	/** end Constants Declaration ******************************************/

}//class TerminalIO
