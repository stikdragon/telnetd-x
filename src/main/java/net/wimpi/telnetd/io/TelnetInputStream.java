package net.wimpi.telnetd.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

public class TelnetInputStream extends InputStream {

	private TelnetIO		io;
	private CharsetDecoder	dec	= StandardCharsets.UTF_8.newDecoder();

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
		dec.reset();
		for (;;) {
			ByteBuffer input = ByteBuffer.allocate(1);
			int n = read();
			if (n == -1)
				throw new IOException("End of Stream");
			input.put((byte) n);
			input.flip();
			CharBuffer output = CharBuffer.allocate(1);
			CoderResult cr = dec.decode(input, output, false);
			if (output.position() > 0) {
				output.flip();
				return output.get();
			}
		}

	}

}
