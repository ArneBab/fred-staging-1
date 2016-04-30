/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client.filter;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.HashMap;

import freenet.l10n.NodeL10n;
import freenet.support.io.FileUtil;

/**
 * Content filter for M3Us
 * 
 * This one kills every comment and ensures that every file is a safe
 * URL. Currently far too strict: allows only relative paths with as
 * letters alphanumeric or - and exactly one dot.
 * 
 * The structure of a simple M3U is just a list of valid relative
 * URLs.  The structure of an extended M3U is as follows (taken from
 * http://schworak.com/blog/e39/m3u-play-list-specification/ ):
 * 
 * #EXTM3U
 * #EXTINF:233,Title 1
 * Somewhere\title1.mp3
 * #EXTINF:129,Title 2
 * http://www.site.com/~user/title2.mp3
 * #EXTINF:-1,Stream
 * stream-2016-01-03.m3u
 * 
 * #EXTM3U starts the File
 * #EXTINF:<length in seconds>,<title>
 * <path>
 * 
 * Might be useful to extend to m3u8:
 * https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/StreamingMediaGuide/HTTPStreamingArchitecture/HTTPStreamingArchitecture.html#//apple_ref/doc/uid/TP40008332-CH101-SW10
 */
public class M3UFilter implements ContentDataFilter {

    static final byte[] CHAR_COMMENT_START =
        { (byte)'#' };
    static final byte[] CHAR_NEWLINE =
        { (byte)'\n' };
    static final byte[] CHAR_CARRIAGE_RETURN =
        { (byte)'\r' };
    static final int MAX_URI_LENGTH = 16384;
    static final String badUriReplacement = "#bad-uri-removed-filter-allows-only-alphanumeric-or-minus-with-exactly-one-dot";
    // TODO: Add parsing of ext-comments to allow for gapless playback.
    // static final int COMMENT_EXT_SIZE = 4;
    // static final byte[] COMMENT_EXT_START =
    //  { (byte)'#', (byte)'E', (byte)'X', (byte)'T' };
    // static final int EXT_HEADER_SIZE = 7;
    // static final byte[] EXT_HEADER =
    // { (byte)'#', (byte)'E', (byte)'X', (byte)'T', (byte)'M', (byte)'3', (byte)'U' };
        
    static public boolean isAllowedInUri(byte b) {
        // overly strict filtering to keep it simple for starters.
        // allow only alphanumeric values in UTF-8 encoding and exactly one period in the filename.
        final byte utf80 = (byte)'0';
        final byte utf89 = (byte)'9';
        final byte utf8A = (byte)'A';
        final byte utf8Z = (byte)'Z';
        final byte utf8a = (byte)'a';
        final byte utf8z = (byte)'z';
        final byte utf8dot = (byte)'.';
        final byte utf8dash = (byte)'-';
        return ((utf80 <= b && b <= utf89) ||
                (utf8A <= b && b <= utf8Z) ||
                (utf8a <= b && b <= utf8z) ||
                utf8dot == b ||
                utf8dash == b);
    }
    
    @Override
    public void readFilter(InputStream input, OutputStream output, String charset, HashMap<String, String> otherParams,
            FilterCallback cb) throws DataFilterException, IOException {
        // TODO: Check the header whether this is an ext m3u.
        // TODO: Check the EXTINF headers instead of killing comments.
        // Check whether the line is a comment
        boolean isComment = false;
        boolean isBadUri = false;
        int numberOfDotsInUri = 0;
        int readcount;
        byte[] nextbyte = new byte[1];
        byte[] fileUri;
        int fileIndex;
        /* TODO: select the type per suffix. Right now we can get away
         * with just forcing mp3 since we only have a filter for
         * that.*/
        /* We need to force the type, because browsers might use
         * content type sniffing in the media tag and audio-players,
         * so people could insert as text/plain to circumvent the
         * filter.*/
        String uriForcetypeMp3Suffix = "?type=audio/mpeg";
        try (DataInputStream dis = new DataInputStream(input)) {
            readcount = dis.read(nextbyte);
            // read each line manually
            while (readcount != -1) {
                if (Arrays.equals(nextbyte, CHAR_COMMENT_START)) {
                    isComment = true;
                } else {
                    isComment = false;
                }
                // read one line as a fileUri
                numberOfDotsInUri = 0;
                isBadUri = false;
                fileIndex = 0;
                fileUri = new byte[MAX_URI_LENGTH];
                while (readcount != -1) {
                    if (!isComment && 
                        // do not include carriage return in filenames
                        !Arrays.equals(nextbyte, CHAR_CARRIAGE_RETURN) &&
                        // enforce maximum path length to avoid OOM attacks
                        fileIndex <= MAX_URI_LENGTH) {
                        fileUri[fileIndex] = nextbyte[0];
                        fileIndex += readcount;
                    }
                    readcount = dis.read(nextbyte);
                    if (Arrays.equals(nextbyte, CHAR_NEWLINE)) {
                        if (!isComment) {
                            // remove too long paths
                            if (fileIndex <= MAX_URI_LENGTH) {
                                for (int i = 0; i < fileIndex; i++) {
                                    byte b = fileUri[i];
                                    if (!isAllowedInUri(b)) {
                                        isBadUri = true;
                                        break;
                                    } 
                                    if ((byte)'.' == b) {
                                        numberOfDotsInUri += 1;
                                    }
                                }
                                if (numberOfDotsInUri != 1) {
                                    isBadUri = true;
                                }
                                // use only the first fileIndex bytes
                                String uri;
                                // TODO: cleanly filter the URI, i.e. with processURI from GenericReadFilterCallback.
                                if (isBadUri) {
                                    uri = badUriReplacement;
                                } else {
                                    uri = new String(fileUri, 0, fileIndex, "UTF-8");
                                    uri += uriForcetypeMp3Suffix;
                                }
                                output.write(uri.getBytes("UTF-8"));
                                output.write(nextbyte);
                            }
                        }
                        // skip the newline
                        readcount = dis.read(nextbyte);
                        break;
                    }
                }
            }
        }
        output.flush();
    }
    
    private static String l10n(String key) {
        return NodeL10n.getBase().getString("M3UFilter."+key);
    }
    
    private void throwHeaderError(String shortReason, String reason) throws DataFilterException {
        // Throw an exception
        String message = l10n("notM3u");
        if (reason != null) {
            message += ' ' + reason;
        }
        if (shortReason != null) {
            message += " - (" + shortReason + ')';
        }
        throw new DataFilterException(shortReason, shortReason, message);
    }
    
    @Override
    public void writeFilter(InputStream input, OutputStream output, String charset, HashMap<String, String> otherParams,
                            FilterCallback cb) throws DataFilterException, IOException {
        output.write(input.read());
    }
    
}
