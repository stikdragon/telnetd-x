package uk.co.stikman.wimpi.telnetd.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * A charset decoder that accumulates bytes. When it's got enough to return at
 * least one <code>char</code> then {@link #hasOutput()} returns
 * <code>true</code>, and you can call {@link #read()} once.
 * 
 * @author Stik
 *
 */
public class PushDecoder {

	private FifoByteBuffer	buf;
	private FifoCharBuffer	output;
	private CharBuffer		out;
	private CharsetDecoder	cs;

	public PushDecoder(Charset cs) {
		this(cs.newDecoder());
	}

	public PushDecoder(CharsetDecoder csd) {
		this(csd, 1024);
	}

	/**
	 * bufferSize is the size of internal fifo buffers, if you write more bytes
	 * than this to the decoder without reading them back you'll get problems
	 * with buffers overflowing
	 * 
	 * @param cs
	 * @param bufferSize
	 */
	public PushDecoder(CharsetDecoder csd, int bufferSize) {
		this.cs = csd;
		buf = new FifoByteBuffer(bufferSize);
		output = new FifoCharBuffer(bufferSize);
		out = CharBuffer.allocate(bufferSize);
	}

	public void write(byte b) {
		buf.write(b);
	}

	public boolean hasOutput() {
		if (output.available() > 0)
			return true;
		decodeMore();
		return output.available() > 0;
	}

	private void decodeMore() {
		//
		// Try to decode some more
		//
		ByteBuffer in = buf.createByteBuffer();
		int offset = in.position();
		cs.decode(in, out, false);
		out.flip();
		buf.discardFirst(in.position() - offset);
		for (int i = 0; i < out.limit(); ++i)
			output.write(out.get());
		out.clear();
	}

	/**
	 * Will throw an {@link IllegalStateException} if you call it when
	 * {@link #hasOutput()} would return <code>true</code>
	 * 
	 * @return
	 */
	public char read() {
		while (output.available() > 0)
			return output.read();
		decodeMore();
		if (output.available() > 0)
			return output.read();
		throw new IllegalStateException();
	}

}
