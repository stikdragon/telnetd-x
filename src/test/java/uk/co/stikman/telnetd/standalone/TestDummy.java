package uk.co.stikman.telnetd.standalone;

import java.io.InputStream;
import java.util.Properties;

import uk.co.stikman.wimpi.telnetd.TelnetD;

//
// run the DummyShell that comes with telnetd-x.  It demos the handful of widget type things
// it comes with.  It's a useful "let me play with it" sort of thing
//
public class TestDummy {
	public static void main(String[] args) {
		try {
			Properties props = new Properties();
			try (InputStream is = TestDummy.class.getResourceAsStream("config.properties")) {
				props.load(is);
			}
			TelnetD daemon = TelnetD.createTelnetD(props);
			daemon.start();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
