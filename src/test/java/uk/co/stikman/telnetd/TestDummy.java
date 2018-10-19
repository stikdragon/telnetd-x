package uk.co.stikman.telnetd;

import java.io.InputStream;
import java.util.Properties;

import net.wimpi.telnetd.TelnetD;

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
