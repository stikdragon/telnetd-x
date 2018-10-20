package uk.co.stikman.wimpi.telnetd.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import uk.co.stikman.wimpi.telnetd.util.PushDecoder;

public class TelnetInputStream extends InputStream {

	private TelnetIO	io;
	private PushDecoder	dec	= new PushDecoder(StandardCharsets.UTF_8.newDecoder());

	public TelnetInputStream(TelnetIO telnetIO) {
		this.io = telnetIO;
	}

	@Override
	public int read() throws IOException {
		return io.read();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		//
		// We have no way of testing for available(), so we can only
		// return a single byte at a time here
		//
		if (len == 0)
			return 0;
		int n = io.read();
		if (n == -1)
			throw new IOException("End of Stream");
		b[0] = (byte) n;
		System.out.println("0x" + Integer.toHexString(n) + "  (" + n + ") " + Character.valueOf((char) n));
		return 1;
	}

	public char readChar() throws IOException {
		while (!dec.hasOutput()) {
			int n = read();
			if (n == -1)
				throw new IOException("End of Stream");
			dec.write((byte) n);
		}
		return dec.read();
	}

}
