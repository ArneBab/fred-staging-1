/*
 * Copyright (c) 2007 Henri Sivonen
 * Copyright (c) 2008-2009 Mozilla Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.htmlparser.sax;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Stack;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import nu.validator.htmlparser.impl.ElementName;
import nu.validator.htmlparser.impl.AttributeName;
public class HtmlSerializer implements ContentHandler, LexicalHandler {

	private static final String[] VOID_ELEMENTS = { "area", "base", "basefont",
		"bgsound", "br", "col", "command", "embed", "event-source",
		"frame", "hr", "img", "input", "keygen", "link", "meta", "param",
		"source", "spacer", "wbr"};

	private static final String[] NON_ESCAPING = { "iframe", "noembed",
		"noframes", "noscript", "plaintext", "script", "style", "xmp" };


	private static Writer wrap(OutputStream out) {
		try {
			return new OutputStreamWriter(out, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	private int escapeLevel = 0;

	private boolean skipCharacter=false;
	private final Writer writer;
	private boolean isXHTML=false;
	private Stack<String> elementStack;


	public HtmlSerializer(OutputStream out) {
		this(wrap(out));
	}

	public HtmlSerializer(Writer out) {
		this.writer = out;
	}



	public void characters(char[] ch, int start, int length)
	throws SAXException {
		try {
			if(skipCharacter==false)
			{
				if (escapeLevel > 0) {
					writer.write(ch, start, length);
				} else {
					for (int i = start; i < start + length; i++) {
						char c = ch[i];
						switch (c) {
						case '<':
							writer.write("&lt;");
							break;
						case '>':
							writer.write("&gt;");
							break;
						case '&':
							writer.write("&amp;");
							break;
						case '\u00A0':
							writer.write("&nbsp;");
							break;
						default:
							writer.write(c);
						break;
						}
					}
				}
			}
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}

	public void endDocument() throws SAXException {
		if(isXHTML==true)
		{
			while(elementStack.size()>0)
			{
				try {
					writer.write('<');
					writer.write('/');
					writer.write(elementStack.pop().toString());
					writer.write('>');
				} catch (IOException e) {
					throw new SAXException(e);
				}

			}

		}

		try {
			writer.flush();
			writer.close();
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}

	public void endElement(String uri, String localName, String qName)
	throws SAXException {
		
		isXHTML=(uri.toLowerCase()=="http://www.w3.org/1999/xhtml");
		if(isXHTML==false)
		{
			if(ElementName.existElement(localName)==true)
			{
				if (escapeLevel > 0) {
					escapeLevel--;
				}
				try {
					writer.write('<');
					writer.write('/');
					writer.write(localName);
					writer.write('>');
				} catch (IOException e) {
					throw new SAXException(e);

				}
			}
		}
		else if(checkVoidElement(localName.toLowerCase())==false)
		{
			if (escapeLevel > 0) {
				escapeLevel--;
			}
			if(elementStack.size()>0)	
			{
				try {
					writer.write('<');
					writer.write('/');
					writer.write(elementStack.pop().toString());
					writer.write('>');
				} catch (IOException e) {
					throw new SAXException(e);

				}
			}

		}
	}

	public void ignorableWhitespace(char[] ch, int start, int length)
	throws SAXException {
		characters(ch, start, length);
	}

	public void processingInstruction(String target, String data)
	throws SAXException {
	}

	public void setDocumentLocator(Locator locator) {
	}

	public void startDocument() throws SAXException {
			elementStack=new Stack<String>();
	}

	public boolean checkVoidElement(String localName)
	{
		localName=localName.toLowerCase();
		for(int i=0;i<VOID_ELEMENTS.length;i++)
		{
			if(VOID_ELEMENTS[i]==localName)
			{
				return true;
			}
		}
		return false;
	}
	
	//TODO: The search can be made more efficient. (e.g. Binary search)
	public boolean checkValidAttributeName(String attName)
	{
		attName=attName.toLowerCase();
		int mode=AttributeName.HTML;
		if(attName==null) return false;
		else
		{
			for(int i=0;i<AttributeName.ATTRIBUTE_NAMES.length;i++)
			{
				if(attName==AttributeName.ATTRIBUTE_NAMES[i].getLocal(mode))
					return true;
			}
		}
		return false;

	}

	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		
		isXHTML=(uri.toLowerCase()=="http://www.w3.org/1999/xhtml");
		if(ElementName.existElement(localName)==true)
		{
			skipCharacter=false;
			if (escapeLevel > 0) {
				escapeLevel++;
			}

			try {
				boolean isVoidElement=checkVoidElement(localName);
				if(isXHTML==true)
				{
					localName=localName.toLowerCase();
					if(isVoidElement==false)	
						elementStack.push(localName);
				}
				writer.write('<');
				writer.write(localName);
				String attributeName;
				for (int i = 0; i < atts.getLength(); i++) {
					if(checkValidAttributeName(atts.getLocalName(i))==false)
					{
						System.out.println("Removing: "+atts.getLocalName(i));
						continue;
					}

					if(isXHTML==false)
					{
						attributeName=atts.getLocalName(i);
					}
					else
					{
						attributeName=atts.getLocalName(i).toLowerCase();
					}



					writer.write(" "+attributeName);
					writer.write('=');
					writer.write('"');
					String val = atts.getValue(i);
					for (int j = 0; j < val.length(); j++) {
						char c = val.charAt(j);
						switch (c) {
						case '"':
							writer.write("&quot;");
							break;
						case '&':
							writer.write("&amp;");
							break;
						case '\u00A0':
							writer.write("&nbsp;");
							break;
						default:
							writer.write(c);
						break;
						}
					}
					writer.write('"');
				}
				if(isXHTML==true && isVoidElement==true)
					writer.write(" /");
				writer.write('>');
				if ("pre".equals(localName) || "textarea".equals(localName)
						|| "listing".equals(localName)) {
					writer.write('\n');
				}
				if (escapeLevel == 0
						&& Arrays.binarySearch(NON_ESCAPING, localName) > -1) {
					escapeLevel = 1;
				}
			} catch (IOException e) {
				throw new SAXException(e);
			}
		}
		else
			skipCharacter=true;
	}

	public void comment(char[] ch, int start, int length) throws SAXException {
		try {
			writer.write("<!--");
			writer.write(ch, start, length);
			writer.write("-->");
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}

	public void endCDATA() throws SAXException {
	}

	public void endDTD() throws SAXException {
	}

	public void endEntity(String name) throws SAXException {
	}

	public void startCDATA() throws SAXException {
	}

	public void startDTD(String name, String publicId, String systemId)
	throws SAXException {
	}

	public void startEntity(String name) throws SAXException {
	}

	public void startPrefixMapping(String prefix, String uri)
	throws SAXException {
	}

	public void endPrefixMapping(String prefix) throws SAXException {
	}

	public void skippedEntity(String name) throws SAXException {
	}

}
