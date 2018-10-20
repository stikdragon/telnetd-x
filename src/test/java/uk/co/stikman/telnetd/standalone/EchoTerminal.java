package uk.co.stikman.telnetd.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import uk.co.stikman.wimpi.telnetd.BootException;
import uk.co.stikman.wimpi.telnetd.TelnetD;

//
// Runs a basic echo terminal that replays each key press back to you
//
public class EchoTerminal {

	public static void main(String[] args) throws IOException, BootException {
		Properties props = new Properties();
		try (InputStream is = EchoTerminal.class.getResourceAsStream("echoterm.properties")) {
			props.load(is);
		}
		TelnetD td = TelnetD.createTelnetD(props);
		td.start();
	}
}
