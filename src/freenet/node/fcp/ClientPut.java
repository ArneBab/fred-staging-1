/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.fcp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.util.Arrays;

import freenet.client.ClientMetadata;
import freenet.client.DefaultMIMETypes;
import freenet.client.InsertException;
import freenet.client.Metadata;
import freenet.client.MetadataUnresolvedException;
import freenet.client.async.BinaryBlob;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientPutter;
import freenet.crypt.SHA256;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.SimpleReadOnlyArrayBucket;
import freenet.support.api.Bucket;
import freenet.support.io.CannotCreateFromFieldSetException;
import freenet.support.io.FileBucket;
import freenet.support.io.SerializableToFieldSetBucketUtil;

import com.db4o.ObjectContainer;

public class ClientPut extends ClientPutBase {

	ClientPutter putter;
	private final short uploadFrom;
	/** Original filename if from disk, otherwise null. Purely for PersistentPut. */
	private final File origFilename;
	/** If uploadFrom==UPLOAD_FROM_REDIRECT, this is the target of the redirect */
	private final FreenetURI targetURI;
	private Bucket data;
	private final ClientMetadata clientMetadata;
	/** We store the size of inserted data before freeing it */
	private long finishedSize;
	/** Filename if the file has one */
	private final String targetFilename;
	private boolean logMINOR;
	/** If true, we are inserting a binary blob: No metadata, no URI is generated. */
	private final boolean binaryBlob;
	private transient boolean compressing;
	
	/**
	 * Fproxy
	 * Creates a new persistent insert.
	 * @param uri
	 *            The URI to insert data to
	 * @param identifier
	 *            The identifier of the insert
	 * @param verbosity
	 *            The verbosity bitmask
	 * @param charset TODO
	 * @param priorityClass
	 *            The priority for this insert
	 * @param persistenceType
	 *            The persistence type of this insert
	 * @param clientToken
	 *            The client token of this insert
	 * @param getCHKOnly
	 *            Whether only the resulting CHK is requested
	 * @param dontCompress
	 *            Whether the file should not be compressed
	 * @param maxRetries
	 *            The maximum number of retries
	 * @param uploadFromType
	 *            Where the file is uploaded from
	 * @param origFilename
	 *            The original filename
	 * @param contentType
	 *            The content type of the data
	 * @param data
	 *            The data (may be <code>null</code> if
	 *            <code>uploadFromType</code> is UPLOAD_FROM_REDIRECT)
	 * @param redirectTarget
	 *            The URI to redirect to (if <code>uploadFromType</code> is
	 *            UPLOAD_FROM_REDIRECT)
	 * @param handler
	 *            The FCP connection handler
	 * @param global
	 *            Whether this insert appears on the global queue
	 * 
	 * @throws IdentifierCollisionException
	 * @throws NotAllowedException 
	 * @throws FileNotFoundException 
	 * @throws MalformedURLException 
	 * @throws MetadataUnresolvedException 
	 * @throws InsertException 
	 */
	public ClientPut(FCPClient globalClient, FreenetURI uri, String identifier, int verbosity, 
			String charset, short priorityClass, short persistenceType, String clientToken,
			boolean getCHKOnly, boolean dontCompress, int maxRetries, short uploadFromType, File origFilename,
			String contentType, Bucket data, FreenetURI redirectTarget, String targetFilename, boolean earlyEncode, boolean canWriteClientCache, boolean forkOnCacheable, int extraInsertsSingleBlock, int extraInsertsSplitfileHeaderBlock, FCPServer server, ObjectContainer container) throws IdentifierCollisionException, NotAllowedException, FileNotFoundException, MalformedURLException, MetadataUnresolvedException {
		super(uri, identifier, verbosity, charset, null, globalClient, priorityClass, persistenceType, null, true, getCHKOnly, dontCompress, maxRetries, earlyEncode, canWriteClientCache, forkOnCacheable, extraInsertsSingleBlock, extraInsertsSplitfileHeaderBlock, null, server, container);
		if(uploadFromType == ClientPutMessage.UPLOAD_FROM_DISK) {
			if(!server.core.allowUploadFrom(origFilename))
				throw new NotAllowedException();
			if(!(origFilename.exists() && origFilename.canRead()))
				throw new FileNotFoundException();
		}

		logMINOR = Logger.shouldLog(Logger.MINOR, this);
		binaryBlob = false;
		this.targetFilename = targetFilename;
		this.uploadFrom = uploadFromType;
		this.origFilename = origFilename;
		// Now go through the fields one at a time
		String mimeType = contentType;
		this.clientToken = clientToken;
		Bucket tempData = data;
		ClientMetadata cm = new ClientMetadata(mimeType);
		boolean isMetadata = false;
		logMINOR = Logger.shouldLog(Logger.MINOR, this);
		if(logMINOR) Logger.minor(this, "data = "+tempData+", uploadFrom = "+ClientPutMessage.uploadFromString(uploadFrom));
		if(uploadFrom == ClientPutMessage.UPLOAD_FROM_REDIRECT) {
			this.targetURI = redirectTarget;
			Metadata m = new Metadata(Metadata.SIMPLE_REDIRECT, null, null, targetURI, cm);
			byte[] d;
			d = m.writeToByteArray();
			tempData = new SimpleReadOnlyArrayBucket(d);
			isMetadata = true;
		} else
			targetURI = null;

		this.data = tempData;
		this.clientMetadata = cm;

		putter = new ClientPutter(this, data, uri, cm, 
				ctx, priorityClass, 
				getCHKOnly, isMetadata, 
				lowLevelClient,
				null, targetFilename, binaryBlob);
	}
	
	public ClientPut(FCPConnectionHandler handler, ClientPutMessage message, FCPServer server, ObjectContainer container) throws IdentifierCollisionException, MessageInvalidException, MalformedURLException {
		super(message.uri, message.identifier, message.verbosity, null, 
				handler, message.priorityClass, message.persistenceType, message.clientToken,
				message.global, message.getCHKOnly, message.dontCompress, message.maxRetries, message.earlyEncode, message.canWriteClientCache, message.forkOnCacheable, message.compressorDescriptor, message.extraInsertsSingleBlock, message.extraInsertsSplitfileHeaderBlock, message.compatibilityMode, server, container);
		String salt = null;
		byte[] saltedHash = null;
		binaryBlob = message.binaryBlob;
		
		if(message.uploadFromType == ClientPutMessage.UPLOAD_FROM_DISK) {
			if(!handler.server.core.allowUploadFrom(message.origFilename))
				throw new MessageInvalidException(ProtocolErrorMessage.ACCESS_DENIED, "Not allowed to upload from "+message.origFilename, identifier, global);

			if(message.fileHash != null) {
				try {
					salt = handler.connectionIdentifier + '-' + message.identifier + '-';
					saltedHash = Base64.decode(message.fileHash);
				} catch (IllegalBase64Exception e) {
					throw new MessageInvalidException(ProtocolErrorMessage.INVALID_FIELD, "Can't base64 decode " + ClientPutBase.FILE_HASH, identifier, global);
				}
			} else if(!handler.allowDDAFrom(message.origFilename, false))
				throw new MessageInvalidException(ProtocolErrorMessage.DIRECT_DISK_ACCESS_DENIED, "Not allowed to upload from "+message.origFilename+". Have you done a testDDA previously ?", identifier, global);		
		}
			
		this.targetFilename = message.targetFilename;
		logMINOR = Logger.shouldLog(Logger.MINOR, this);
		this.uploadFrom = message.uploadFromType;
		this.origFilename = message.origFilename;
		// Now go through the fields one at a time
		String mimeType = message.contentType;
		if(binaryBlob) {
			if(mimeType != null && !mimeType.equals(BinaryBlob.MIME_TYPE)) {
				throw new MessageInvalidException(ProtocolErrorMessage.INVALID_FIELD, "No MIME type allowed when inserting a binary blob", identifier, global);
			}
		}
		if(mimeType == null && origFilename != null) {
			mimeType = DefaultMIMETypes.guessMIMEType(origFilename.getName(), true);
		}
		if ((mimeType == null) && (targetFilename != null)) {
			mimeType = DefaultMIMETypes.guessMIMEType(targetFilename, true);
		}
		if(mimeType == null) {
			mimeType = DefaultMIMETypes.guessMIMEType(identifier, true);
		}
		clientToken = message.clientToken;
		Bucket tempData = message.bucket;
		ClientMetadata cm = new ClientMetadata(mimeType);
		boolean isMetadata = false;
		if(logMINOR) Logger.minor(this, "data = "+tempData+", uploadFrom = "+ClientPutMessage.uploadFromString(uploadFrom));
		if(uploadFrom == ClientPutMessage.UPLOAD_FROM_REDIRECT) {
			this.targetURI = message.redirectTarget;
			Metadata m = new Metadata(Metadata.SIMPLE_REDIRECT, null, null, targetURI, cm);
			byte[] d;
			try {
				d = m.writeToByteArray();
			} catch (MetadataUnresolvedException e) {
				// Impossible
				Logger.error(this, "Impossible: "+e, e);
				this.data = null;
				clientMetadata = cm;
				putter = null;
				// This is *not* an InsertException since we don't register it: it's a protocol error.
				throw new MessageInvalidException(ProtocolErrorMessage.INTERNAL_ERROR, "Impossible: metadata unresolved: "+e, identifier, global);
			}
			tempData = new SimpleReadOnlyArrayBucket(d);
			isMetadata = true;
		} else
			targetURI = null;
		this.data = tempData;
		this.clientMetadata = cm;
		
		// Check the hash : allow it to be null for backward compatibility and if testDDA is allowed
		if(salt != null) {
			MessageDigest md = SHA256.getMessageDigest();
			byte[] foundHash;
			try {
				try {
					md.update(salt.getBytes("UTF-8"));
				} catch (UnsupportedEncodingException e) {
					throw new Error("Impossible: JVM doesn't support UTF-8: " + e, e);
				}
				try {
					InputStream is = data.getInputStream();
					SHA256.hash(is, md);
					is.close();
				} catch (IOException e) {
					SHA256.returnMessageDigest(md);
					Logger.error(this, "Got IOE: " + e.getMessage(), e);
					throw new MessageInvalidException(ProtocolErrorMessage.COULD_NOT_READ_FILE,
					        "Unable to access file: " + e, identifier, global);
				}
				foundHash = md.digest();
			} finally {
				SHA256.returnMessageDigest(md);
			}

			if(logMINOR) Logger.minor(this, "FileHash result : we found " + Base64.encode(foundHash) + " and were given " + Base64.encode(saltedHash) + '.');

			if(!Arrays.equals(saltedHash, foundHash))
				throw new MessageInvalidException(ProtocolErrorMessage.DIRECT_DISK_ACCESS_DENIED, "The hash doesn't match! (salt used : \""+salt+"\")", identifier, global);
		}
		
		if(logMINOR) Logger.minor(this, "data = "+data+", uploadFrom = "+ClientPutMessage.uploadFromString(uploadFrom));
		putter = new ClientPutter(this, data, uri, cm, 
				ctx, priorityClass, 
				getCHKOnly, isMetadata,
				lowLevelClient,
				null, targetFilename, binaryBlob);
	}
	
	/**
	 * Create from a persisted SimpleFieldSet.
	 * Not very tolerant of errors, as the input was generated
	 * by the node.
	 * @throws PersistenceParseException 
	 * @throws IOException 
	 * @throws InsertException 
	 */
	public ClientPut(SimpleFieldSet fs, FCPClient client2, FCPServer server, ObjectContainer container) throws PersistenceParseException, IOException, InsertException {
		super(fs, client2, server);
		logMINOR = Logger.shouldLog(Logger.MINOR, this);
		String mimeType = fs.get("Metadata.ContentType");

		String from = fs.get("UploadFrom");
		
		if(from.equals("direct")) {
			uploadFrom = ClientPutMessage.UPLOAD_FROM_DIRECT;
		} else if(from.equals("disk")) {
			uploadFrom = ClientPutMessage.UPLOAD_FROM_DISK;
		} else if(from.equals("redirect")) {
			uploadFrom = ClientPutMessage.UPLOAD_FROM_REDIRECT;
		} else {
				throw new PersistenceParseException("Unknown UploadFrom: "+from);
		}
		
		ClientMetadata cm = new ClientMetadata(mimeType);
		
		boolean isMetadata = false;
		
		binaryBlob = fs.getBoolean("BinaryBlob", false);
		targetFilename = fs.get("TargetFilename");
		
		if(uploadFrom == ClientPutMessage.UPLOAD_FROM_DISK) {
			origFilename = new File(fs.get("Filename"));
			if(logMINOR)
				Logger.minor(this, "Uploading from disk: "+origFilename+" for "+this);
			data = new FileBucket(origFilename, true, false, false, false, false);
			targetURI = null;
		} else if(uploadFrom == ClientPutMessage.UPLOAD_FROM_DIRECT) {
			origFilename = null;
			if(logMINOR)
				Logger.minor(this, "Uploading from direct for "+this);
			if(!finished) {
				try {
					data = SerializableToFieldSetBucketUtil.create(fs.subset("TempBucket"), server.core.random, server.core.persistentTempBucketFactory);
				} catch (CannotCreateFromFieldSetException e) {
					throw new PersistenceParseException("Could not read old bucket for "+identifier+" : "+e, e);
				}
			} else {
				if(Logger.shouldLog(Logger.MINOR, this)) 
					Logger.minor(this, "Finished already so not reading bucket for "+this);
				data = null;
			}
			targetURI = null;
		} else if(uploadFrom == ClientPutMessage.UPLOAD_FROM_REDIRECT) {
			String target = fs.get("TargetURI");
			targetURI = new FreenetURI(target);
			if(logMINOR)
				Logger.minor(this, "Uploading from redirect for "+this+" : "+targetURI);
			Metadata m = new Metadata(Metadata.SIMPLE_REDIRECT, null, null, targetURI, cm);
			byte[] d;
			try {
				d = m.writeToByteArray();
			} catch (MetadataUnresolvedException e) {
				// Impossible
				Logger.error(this, "Impossible: "+e, e);
				this.data = null;
				clientMetadata = cm;
				origFilename = null;
				putter = null;
				throw new InsertException(InsertException.INTERNAL_ERROR, "Impossible: "+e+" in ClientPut", null);
			}
			data = new SimpleReadOnlyArrayBucket(d);
			origFilename = null;
			isMetadata = true;
		} else {
			throw new PersistenceParseException("shouldn't happen");
		}
		if(logMINOR) Logger.minor(this, "data = "+data);
		this.clientMetadata = cm;
		SimpleFieldSet oldProgress = fs.subset("progress");
		if(finished) oldProgress = null; // Not useful any more
		putter = new ClientPutter(this, data, uri, cm, ctx, 
				priorityClass, getCHKOnly, isMetadata,
				lowLevelClient,
				oldProgress, targetFilename, binaryBlob);
		if(persistenceType != PERSIST_CONNECTION) {
			FCPMessage msg = persistentTagMessage(container);
			client.queueClientRequestMessage(msg, 0, container);
		}
		
	}

	@Override
	void register(ObjectContainer container, boolean noTags) throws IdentifierCollisionException {
		if(persistenceType != PERSIST_CONNECTION)
			client.register(this, container);
		if(persistenceType != PERSIST_CONNECTION && !noTags) {
			FCPMessage msg = persistentTagMessage(container);
			client.queueClientRequestMessage(msg, 0, container);
		}
	}
	
	@Override
	public void start(ObjectContainer container, ClientContext context) {
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "Starting "+this+" : "+identifier);
		synchronized(this) {
			if(finished) return;
		}
		try {
			putter.start(earlyEncode, false, container, context);
			if(persistenceType != PERSIST_CONNECTION && !finished) {
				FCPMessage msg = persistentTagMessage(container);
				client.queueClientRequestMessage(msg, 0, container);
			}
			synchronized(this) {
				started = true;
			}
			if(persistenceType == PERSIST_FOREVER)
				container.store(this); // Update
		} catch (InsertException e) {
			synchronized(this) {
				started = true;
			}
			onFailure(e, null, container);
		} catch (Throwable t) {
			synchronized(this) {
				started = true;
			}
			onFailure(new InsertException(InsertException.INTERNAL_ERROR, t, null), null, container);
		}
	}

	@Override
	protected void freeData(ObjectContainer container) {
		Bucket d;
		synchronized(this) {
			d = data;
			data = null;
			if(d == null) return;
			if(persistenceType == PERSIST_FOREVER)
				container.activate(d, 5);
			finishedSize = d.size();
		}
		d.free();
		if(persistenceType == PERSIST_FOREVER)
			d.removeFrom(container);
	}
	
	@Override
	protected freenet.client.async.ClientRequester getClientRequest() {
		return putter;
	}

	@Override
	protected FCPMessage persistentTagMessage(ObjectContainer container) {
		if(persistenceType == PERSIST_FOREVER) {
			container.activate(publicURI, 5);
			container.activate(clientMetadata, 5);
			container.activate(origFilename, 5);
		}
		return new PersistentPut(identifier, publicURI, verbosity, priorityClass, uploadFrom, targetURI, 
				persistenceType, origFilename, clientMetadata.getMIMEType(), client.isGlobalQueue,
				getDataSize(container), clientToken, started, ctx.maxInsertRetries, targetFilename, binaryBlob);
	}

	@Override
	protected String getTypeName() {
		return "PUT";
	}

	@Override
	public boolean hasSucceeded() {
		return succeeded;
	}

	public FreenetURI getFinalURI(ObjectContainer container) {
		if(persistenceType == PERSIST_FOREVER)
			container.activate(generatedURI, 5);
		return generatedURI;
	}

	public boolean isDirect() {
		return uploadFrom == ClientPutMessage.UPLOAD_FROM_DIRECT;
	}

	public File getOrigFilename(ObjectContainer container) {
		if(uploadFrom != ClientPutMessage.UPLOAD_FROM_DISK)
			return null;
		if(persistenceType == PERSIST_FOREVER)
			container.activate(origFilename, 5);
		return origFilename;
	}

	public long getDataSize(ObjectContainer container) {
		if(data == null)
			return finishedSize;
		else {
			if(persistenceType == PERSIST_FOREVER) container.activate(data, 1);
			return data.size();
		}
	}

	public String getMIMEType() {
		return clientMetadata.getMIMEType();
	}

	@Override
	public boolean canRestart() {
		if(!finished) {
			Logger.minor(this, "Cannot restart because not finished for "+identifier);
			return false;
		}
		if(succeeded) {
			Logger.minor(this, "Cannot restart because succeeded for "+identifier);
			return false;
		}
		return putter.canRestart();
	}

	@Override
	public boolean restart(boolean filterData, ObjectContainer container, ClientContext context) {
		if(!canRestart()) return false;
		setVarsRestart(container);
		try {
			if(putter.restart(earlyEncode, container, context)) {
				synchronized(this) {
					generatedURI = null;
					started = true;
				}
			}
			if(persistenceType == PERSIST_FOREVER)
				container.store(this);
			return true;
		} catch (InsertException e) {
			onFailure(e, null, container);
			return false;
		}
	}
	
	public void onRemoveEventProducer(ObjectContainer container) {
		// Do nothing, we called the removeFrom().
	}
	
	@Override
	public void requestWasRemoved(ObjectContainer container, ClientContext context) {
		if(persistenceType == PERSIST_FOREVER) {
			container.activate(putter, 1);
			putter.removeFrom(container, context);
			putter = null;
			if(origFilename != null) {
				container.activate(origFilename, 5);
				container.delete(origFilename);
			}
			// clientMetadata will be deleted by putter
			if(targetURI != null) {
				container.activate(targetURI, 5);
				targetURI.removeFrom(container);
			}
		}
		super.requestWasRemoved(container, context);
	}
	
	public enum COMPRESS_STATE {
		WAITING,
		COMPRESSING,
		WORKING
	}
	
	/** Probably not meaningful for ClientPutDir's */
	public COMPRESS_STATE isCompressing(ObjectContainer container) {
		if(persistenceType == PERSIST_FOREVER) container.activate(ctx, 1);
		if(ctx.dontCompress) return COMPRESS_STATE.WORKING;
		synchronized(this) {
			if(progressMessage == null) return COMPRESS_STATE.WAITING; // An insert starts at compressing
			// The progress message persists... so we need to know whether we have
			// started compressing *SINCE RESTART*.
			if(compressing) return COMPRESS_STATE.COMPRESSING;
			return COMPRESS_STATE.WORKING;
		}
	}

	@Override
	protected synchronized void onStartCompressing() {
		compressing = true;
	}

	@Override
	protected synchronized void onStopCompressing() {
		compressing = false;
	}
	

}
