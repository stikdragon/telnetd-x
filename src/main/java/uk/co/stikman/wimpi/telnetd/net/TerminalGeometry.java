package uk.co.stikman.wimpi.telnetd.net;

public class TerminalGeometry {
	private final int	width;
	private final int	height;

	/**
	 * @param width
	 * @param height
	 */
	public TerminalGeometry(int width, int height) {
		super();
		this.width = width;
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	@Override
	public String toString() {
		return "[" + width + " cols, " + height + " rows]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + height;
		result = prime * result + width;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TerminalGeometry other = (TerminalGeometry) obj;
		if (height != other.height)
			return false;
		if (width != other.width)
			return false;
		return true;
	}

}
