//License
/***
 * Java TelnetD library (embeddable telnet daemon)
 * Copyright (c) 2000-2005 Dieter Wimberger 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the author nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS ``AS
 * IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 ***/

package uk.co.stikman.wimpi.telnetd.io.terminal;

/**
 * Singleton utility class for translating internal color/style markup into ANSI
 * defined escape sequences. It uses a very simple but effective lookup table,
 * and does the job without sophisticated parsing routines. It should therefore
 * perform quite fast.
 *
 * Stik: I've removed the singleton, there's literally no point to it here,
 * patterns for the sake of patterns!
 *
 * @author Dieter Wimberger
 * @version 2.0 (16/07/2006)
 */
public final class Colorizer {

	private int[]	colorMapping;		//translation table
	private boolean	autoReset	= true;	// when true any call to colourize will automatically reset attributes afterwards

	/**
	 * Constructs a Colorizer with its translation table.
	 */
	public Colorizer() {

		colorMapping = new int[128];

		colorMapping[83] = S;
		colorMapping[82] = R;
		colorMapping[71] = G;
		colorMapping[89] = Y;
		colorMapping[66] = B;
		colorMapping[77] = M;
		colorMapping[67] = C;
		colorMapping[87] = W;

		colorMapping[115] = s;
		colorMapping[114] = r;
		colorMapping[103] = g;
		colorMapping[121] = y;
		colorMapping[98] = b;
		colorMapping[109] = m;
		colorMapping[99] = c;
		colorMapping[119] = w;

		colorMapping[102] = f;
		colorMapping[100] = d;
		colorMapping[105] = i;
		colorMapping[106] = j;
		colorMapping[117] = u;
		colorMapping[118] = v;
		colorMapping[101] = e;
		colorMapping[110] = n;
		colorMapping[104] = h;
		colorMapping[97] = a;

	}//constructor

	/**
	 * Translates all internal markups within the String into ANSI Escape
	 * sequences.<br>
	 * The method is hooked into BasicTerminalIO.write(String str), so it is not
	 * necessary to call it directly.
	 *
	 * @param str
	 *            String with internal color/style markups.
	 * @param support
	 *            boolean that represents Terminals ability to support GR
	 *            sequences. if false, the internal markups are ripped out of
	 *            the string.
	 * @return String with ANSI escape sequences (Graphics Rendition), if
	 *         support is true, String without internal markups or ANSI escape
	 *         sequences if support is false.
	 */
	public String colorize(String str, boolean support) {
		return colorize(str, support, false);
	}//colorize

	/**
	 * Translates all internal markups within the String into ANSI Escape
	 * sequences.<br>
	 * The method is hooked into BasicTerminalIO.write(String str), so it is not
	 * necessary to call it directly.
	 *
	 * @param str
	 *            String with internal color/style markups.
	 * @param support
	 *            boolean that represents Terminals ability to support GR
	 *            sequences. if false, the internal markups are ripped out of
	 *            the string.
	 * @param forcebold
	 *            boolean that forces the output to be bold at any time.
	 * @return String with ANSI escape sequences (Graphics Rendition), if
	 *         support is true, String without internal markups or ANSI escape
	 *         sequences if support is false.
	 */
	public String colorize(String str, boolean support, boolean forcebold) {

		StringBuffer out = new StringBuffer(str.length() + 20);
		int parsecursor = 0;
		int foundcursor = 0;

		boolean done = false;
		while (!done) {
			foundcursor = str.indexOf(ColorHelper.MARKER_CODE, parsecursor);
			if (foundcursor != -1) {
				out.append(str.substring(parsecursor, foundcursor));
				if (support) {
					out.append(addEscapeSequence(str.substring(foundcursor + 1, foundcursor + 2), forcebold));
				}
				parsecursor = foundcursor + 2;
			} else {
				out.append(str.substring(parsecursor, str.length()));
				done = true;
			}
		}

		/*
		 * This will always add a "reset all" escape sequence behind the input
		 * string. Basically this is a good idea, because developers tend to
		 * forget writing colored strings properly.
		 */
		if (support && autoReset)
			out.append(addEscapeSequence("a", false));

		return out.toString();
	}//colorize

	private String addEscapeSequence(String attribute, boolean forcebold) {

		StringBuffer tmpbuf = new StringBuffer(10);

		byte[] tmpbytes = attribute.getBytes();
		int key = (int) tmpbytes[0];

		tmpbuf.append((char) 27);
		tmpbuf.append((char) 91);
		//tmpbuf.append((new Integer(m_ColorMapping[key])).toString());
		int attr = colorMapping[key];
		tmpbuf.append(attr);
		if (forcebold && attr != f) {
			tmpbuf.append((char) 59);
			tmpbuf.append(f);
		}
		tmpbuf.append((char) 109);

		return tmpbuf.toString();
	}//addEscapeSequence

	/**
	 * Test Harness *
	 */

	private static void announceResult(boolean res) {
		if (res) {
			System.out.println("[#" + testcount + "] ok.");
		} else {
			System.out.println("[#" + testcount + "] failed (see possible StackTrace).");
		}
	}//announceResult

	private static int			testcount	= 0;
	private static Colorizer	myColorizer;

	private static void announceTest(String what) {
		testcount++;
		System.out.println("Test #" + testcount + " [" + what + "]:");
	}//announceTest

	private static void bfcolorTest(String color) {
		System.out.println("->" + myColorizer.colorize(ColorHelper.boldcolorizeText("COLOR", color), true) + "<-");
	}//bfcolorTest

	private static void fcolorTest(String color) {
		System.out.println("->" + myColorizer.colorize(ColorHelper.colorizeText("COLOR", color), true) + "<-");
	}//fcolorTest

	private static void bcolorTest(String color) {
		System.out.println("->" + myColorizer.colorize(ColorHelper.colorizeBackground("     ", color), true) + "<-");
	}//bcolorTest

	/**
	 * Invokes the build in test harness, and will produce styled and colored
	 * output directly on the terminal.
	 */
	public static void main(String[] args) {
		try {
			announceTest("Instantiation");
			myColorizer = new Colorizer();
			announceResult(true);

			announceTest("Textcolor Tests");
			fcolorTest(ColorHelper.BLACK);
			fcolorTest(ColorHelper.RED);
			fcolorTest(ColorHelper.GREEN);
			fcolorTest(ColorHelper.YELLOW);
			fcolorTest(ColorHelper.BLUE);
			fcolorTest(ColorHelper.MAGENTA);
			fcolorTest(ColorHelper.CYAN);
			fcolorTest(ColorHelper.WHITE);
			announceResult(true);

			announceTest("Bold textcolor Tests");
			bfcolorTest(ColorHelper.BLACK);
			bfcolorTest(ColorHelper.RED);
			bfcolorTest(ColorHelper.GREEN);
			bfcolorTest(ColorHelper.YELLOW);
			bfcolorTest(ColorHelper.BLUE);
			bfcolorTest(ColorHelper.MAGENTA);
			bfcolorTest(ColorHelper.CYAN);
			bfcolorTest(ColorHelper.WHITE);
			announceResult(true);

			announceTest("Background Tests");
			bcolorTest(ColorHelper.BLACK);
			bcolorTest(ColorHelper.RED);
			bcolorTest(ColorHelper.GREEN);
			bcolorTest(ColorHelper.YELLOW);
			bcolorTest(ColorHelper.BLUE);
			bcolorTest(ColorHelper.MAGENTA);
			bcolorTest(ColorHelper.CYAN);
			bcolorTest(ColorHelper.WHITE);
			announceResult(true);

			announceTest("Mixed Color Tests");
			System.out.println("->" + myColorizer.colorize(ColorHelper.colorizeText("COLOR", ColorHelper.WHITE, ColorHelper.BLUE), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.colorizeText("COLOR", ColorHelper.YELLOW, ColorHelper.GREEN), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.boldcolorizeText("COLOR", ColorHelper.WHITE, ColorHelper.BLUE), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.boldcolorizeText("COLOR", ColorHelper.YELLOW, ColorHelper.GREEN), true) + "<-");

			announceResult(true);

			announceTest("Style Tests");
			System.out.println("->" + myColorizer.colorize(ColorHelper.boldText("Bold"), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.italicText("Italic"), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.underlinedText("Underlined"), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.blinkingText("Blinking"), true) + "<-");

			announceResult(true);

			announceTest("Mixed Color/Style Tests");
			System.out.println("->" + myColorizer.colorize(ColorHelper.boldText(ColorHelper.colorizeText("RED", ColorHelper.RED, false) + ColorHelper.colorizeText("BLUE", ColorHelper.BLUE, false) + ColorHelper.colorizeText("GREEN", ColorHelper.GREEN, false)), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.boldText(ColorHelper.colorizeBackground("RED", ColorHelper.RED, false) + ColorHelper.colorizeBackground("BLUE", ColorHelper.BLUE, false) + ColorHelper.colorizeBackground("GREEN", ColorHelper.GREEN, false)), true) + "<-");
			System.out.println("->" + myColorizer.colorize(ColorHelper.boldText(ColorHelper.colorizeText("RED", ColorHelper.WHITE, ColorHelper.RED, false) + ColorHelper.colorizeText("BLUE", ColorHelper.WHITE, ColorHelper.BLUE, false) + ColorHelper.colorizeText("GREEN", ColorHelper.WHITE, ColorHelper.GREEN, false)), true) + "<-");

			announceResult(true);

			announceTest("Visible length test");
			String colorized = ColorHelper.boldcolorizeText("STRING", ColorHelper.YELLOW);

			System.out.println("->" + myColorizer.colorize(colorized, true) + "<-");
			System.out.println("Visible length=" + ColorHelper.getVisibleLength(colorized));

			colorized = ColorHelper.boldcolorizeText("BANNER", ColorHelper.WHITE, ColorHelper.BLUE) + ColorHelper.colorizeText("COLOR", ColorHelper.WHITE, ColorHelper.BLUE) + ColorHelper.underlinedText("UNDER");
			System.out.println("->" + myColorizer.colorize(colorized, true) + "<-");
			System.out.println("Visible length=" + ColorHelper.getVisibleLength(colorized));

			announceResult(true);

			if (false)
				throw new Exception(); //this will shut up jikes

			System.out.println("Forcing bold");
			System.out.println(myColorizer.colorize(ColorHelper.colorizeText("RED", ColorHelper.RED), true, true));

		} catch (Exception ex) {
			announceResult(false);
			ex.printStackTrace();
		}
	}//main (test routine)

	//Constants
	private static final int	S	= 30;	//black
	private static final int	s	= 40;
	private static final int	R	= 31;	//red
	private static final int	r	= 41;
	private static final int	G	= 32;	//green
	private static final int	g	= 42;
	private static final int	Y	= 33;	//yellow
	private static final int	y	= 43;
	private static final int	B	= 34;	//blue
	private static final int	b	= 44;
	private static final int	M	= 35;	//magenta
	private static final int	m	= 45;
	private static final int	C	= 36;	//cyan
	private static final int	c	= 46;
	private static final int	W	= 37;	//white
	private static final int	w	= 47;

	private static final int	f	= 1;	/* bold */

	private static final int	d	= 22;	/* !bold */ //normal color or normal intensity
	private static final int	i	= 3;	/* italic */
	private static final int	j	= 23;	/* !italic */
	private static final int	u	= 4;	/* underlined */
	private static final int	v	= 24;	/* !underlined */
	private static final int	e	= 5;	/* blink */
	private static final int	n	= 25;	/* steady = !blink */
	private static final int	h	= 8;	/* hide = concealed characters */
	private static final int	a	= 0;	/* all out */

	/**
	 * <p>
	 * When <code>true</code> will automatically reset all attributes after
	 * calling {@link #colorize(String, boolean)}. It's safer, but also kind of
	 * annoying if you're actually trying to achieve something.
	 * <p>
	 * Defaults to <code>true</code> for backward-compat
	 * 
	 * @return
	 */
	public boolean isAutoReset() {
		return autoReset;
	}

	/**
	 * @see #isAutoReset()
	 * @param autoReset
	 */
	public void setAutoReset(boolean autoReset) {
		this.autoReset = autoReset;
	}

}//class Colorizer
