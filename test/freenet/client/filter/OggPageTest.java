package freenet.client.filter;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import junit.framework.Assert;
import junit.framework.TestCase;

public class OggPageTest extends TestCase {
	public void testStripNonsenseInterruption() throws IOException {
		InputStream badData = getClass().getResourceAsStream("./ogg/nonsensical_interruption.ogg");
		ByteArrayOutputStream filteredDataStream = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(filteredDataStream);
		DataInputStream input = new DataInputStream(badData);
		OggPage page = OggPage.readPage(input);
		if(page.headerValid()) output.write(page.array());
		page = OggPage.readPage(input);
		if(page.headerValid()) output.write(page.array());
		byte[] filteredData = filteredDataStream.toByteArray();
		output.close();
		input.close();

		InputStream goodData = getClass().getResourceAsStream("./ogg/nonsensical_interruption_filtered.ogg");
		input = new DataInputStream(goodData);
		ByteArrayOutputStream expectedDataStream = new ByteArrayOutputStream();
		output = new DataOutputStream(expectedDataStream);
		page = OggPage.readPage(input);
		if(page.headerValid()) output.write(page.array());
		page = OggPage.readPage(input);
		if(page.headerValid()) output.write(page.array());
		byte[] expectedData = expectedDataStream.toByteArray();
		output.close();
		input.close();

		Assert.assertTrue(Arrays.equals(filteredData, expectedData));
	}

	public void testChecksum() throws IOException {
		DataInputStream input = new DataInputStream(getClass().getResourceAsStream("./ogg/valid_checksum.ogg"));
		OggPage page = OggPage.readPage(input);
		Assert.assertTrue(page.headerValid());
		input.close();
	}

	public void testInvalidChecksumInvalidates() throws IOException {
		DataInputStream input = new DataInputStream(getClass().getResourceAsStream("./ogg/invalid_checksum.ogg"));
		OggPage page = OggPage.readPage(input);
		Assert.assertFalse(page.headerValid());
		input.close();
	}

	public void testValidSubPageCausesFailure() throws IOException {
		DataInputStream input = new DataInputStream(getClass().getResourceAsStream("./ogg/contains_subpages.ogg"));
		try {
			@SuppressWarnings("unused")
			OggPage page = OggPage.readPage(input);
			Assert.fail("Expected exception not caught");
		} catch(DataFilterException e) {}
		input.close();
	}
}
