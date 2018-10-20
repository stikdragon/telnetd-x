package uk.co.stikman.wimpi.telnetd.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * {@link #write(byte)} adds a byte, {@link #createByteBuffer()} returns a
 * {@link ByteBuffer} from the current read position until the end.
 * {@link #discardFirst(int)} discards some number of bytes from the beginning
 * of the buffer, you would do this after having used the returned buffer to
 * have successfully read something
 * 
 * @author Stik
 *
 */
public class FifoByteBuffer {

	private byte[]	data;
	private int		readPtr		= 0;
	private int		writePtr	= 0;

	public FifoByteBuffer(int capacity) {
		data = new byte[capacity];
	}

	public void write(byte b) {
		data[writePtr++] = b;
	}

	public int available() {
		return writePtr - readPtr;
	}

	public ByteBuffer createByteBuffer() {
		ByteBuffer bb = ByteBuffer.wrap(data, readPtr, available());
		return bb;
	}

	public void discardFirst(int length) {
		readPtr += length;
		compact();
	}

	private void compact() {
		if (readPtr > data.length / 2) {
			byte[] nu = new byte[data.length];
			System.arraycopy(data, readPtr, nu, 0, available());
			data = nu;
			writePtr -= readPtr;
			readPtr = 0;
		}
	}

	public void write(int i) {
		write((byte) i);
	}

	@Override
	public String toString() {
		return "SomeBuffer [r=" + readPtr + ", w=" + writePtr + ", data=" + Arrays.toString(data) + "]";
	}

}
