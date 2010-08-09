package freenet.client.filter;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.Assert;
import junit.framework.TestCase;

public class OggFilterTest extends TestCase {
	private OggFilter filter;

	@Override
	protected void setUp() {
		filter = new OggFilter();
	}

	public void testValidSubPageStripped() throws IOException, DataFilterException {
		DataInputStream input = new DataInputStream(getClass().getResourceAsStream("./ogg/contains_subpages.ogg"));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		filter.readFilter(input, output, null, null, null);
		Assert.assertTrue(Arrays.equals(new byte[]{}, output.toByteArray()));
		input.close();
		output.close();
	}
}
