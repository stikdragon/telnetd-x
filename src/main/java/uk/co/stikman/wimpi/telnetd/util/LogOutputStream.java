package uk.co.stikman.wimpi.telnetd.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes everything that goes through it to stdout. Used for debugging
 * 
 * @author Stik
 *
 */
public class LogOutputStream extends FilterOutputStream {

	public LogOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void write(int b) throws IOException {
		super.write(b);
		log(new byte[] { (byte) b }, 0, 1);
	}

	@Override
	public void write(byte[] b) throws IOException {
		super.write(b);
		log(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		super.write(b, off, len);
		log(b, off, len);
	}

	private void log(byte[] b, int off, int len) {
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		sb.append("[");
		String sep = "";
		while (len-- > 0) {
			sb.append(sep);
			sep = ", ";
			int n = b[off++] & 0xff;
			if (n <= 0xf)
				sb.append("0");
			sb.append(Integer.toHexString(n));
			if (n > 31)
				sb2.append((char) n);
			else
				sb2.append("·");
		}
		sb.append("]");
		System.out.println(sb.toString() + "  " + sb2.toString());
	}

}
