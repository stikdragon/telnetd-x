package uk.co.stikman.wimpi.telnetd.util;

/**
 * Buffer of <code>char</code>s, {@link #write(char)} adds a char,
 * {@link #read()} gets one. Reading also triggers the compact method, which
 * shrinks the buffer
 * 
 * @author Stik
 *
 */
public class FifoCharBuffer {

	private char[]	data;
	private int		readPtr		= 0;
	private int		writePtr	= 0;

	public FifoCharBuffer(int capacity) {
		data = new char[capacity];
	}

	public void write(char b) {
		data[writePtr++] = b;
	}

	public int available() {
		return writePtr - readPtr;
	}

	private void compact() {
		if (readPtr > data.length / 2) {
			char[] nu = new char[data.length];
			System.arraycopy(data, readPtr, nu, 0, available());
			data = nu;
			writePtr -= readPtr;
			readPtr = 0;
		}
	}

	public char read() {
		compact();
		return data[readPtr++];
	}

}
