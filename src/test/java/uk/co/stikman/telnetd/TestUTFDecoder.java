package uk.co.stikman.telnetd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import uk.co.stikman.wimpi.telnetd.util.PushDecoder;

public class TestUTFDecoder {
	@Test
	public void testBasicFunctionality() {
		PushDecoder decoder = new PushDecoder(StandardCharsets.UTF_8);

		Assert.assertFalse(decoder.hasOutput());
		decoder.write((byte) 'A');
		Assert.assertTrue(decoder.hasOutput());
		Assert.assertEquals('A', decoder.read());
		Assert.assertFalse(decoder.hasOutput());

		decoder.write((byte) 0xE2);
		decoder.write((byte) 0x99);
		decoder.write((byte) 0xA5);
		Assert.assertEquals('♥', decoder.read());
	}

	@Test
	public void testAllCodePoints() throws IOException {
		PushDecoder decoder = new PushDecoder(StandardCharsets.UTF_8);
		
		//
		// try everything in the BMP (i think)
		//
		try (InputStream is = TestUTFDecoder.class.getResourceAsStream("codepoints.txt")) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
				while (reader.ready()) {
					String line = reader.readLine();
					int cp = Integer.parseInt(line.split("\t")[0], 16);
					String s = String.valueOf((char)cp);
					byte[] buf = s.getBytes(StandardCharsets.UTF_8);
					for (byte b : buf)
						decoder.write(b);
					Assert.assertTrue("Codepoint: 0x" + Integer.toHexString(cp), decoder.hasOutput());
					Assert.assertEquals("Codepoint: 0x" + Integer.toHexString(cp), s.charAt(0), decoder.read());
				}
			}
		}

	}
}
