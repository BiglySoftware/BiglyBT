/*
 * BeDecoder.java
 *
 * Created on May 30, 2003, 2:44 PM
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.*;

import com.biglybt.util.JSONUtils;

/**
 * A set of utility methods to decode a bencoded array of byte into a Map.
 * integer are represented as Long, String as byte[], dictionaries as Map, and list as List.
 *
 * @author TdC_VgA
 *
 */
public class BDecoder
{
	public static final int MAX_BYTE_ARRAY_SIZE		= 192*1024*1024;

	private static final int MAX_MAP_KEY_SIZE		= 64*1024;

	private static final boolean NEWDECODER_FOR_DEF_CHARSET = System.getProperty("bdecoder.new", "0").equals("1");

	private static final boolean TRACE	= false;

	private static final byte[]	PORTABLE_ROOT;

	private boolean recovery_mode;

	private boolean	verify_map_order;

	private boolean useNewDecoder;

	private final CharsetDecoder keyDecoder; // old decoder only

	private final Charset keyCharset; // new decoder only

	private CharsetDecoder keyDecoderUTF8; // old decoder only

	private Charset keyCharsetUTF8; // new decoder only

	
	private int keyBytesLen = 0;

	private ByteBuffer keyBytesBuffer; // old decoder only

	private byte[] keyBytes; // new decoder only

	private CharBuffer keyCharsBuffer; // old decoder only

	private MapDecodeListener mapDecodeListener;

	private boolean force_utf8_keys;
	
	static{
		byte[]	portable = null;

		try{
			String root = System.getProperty(SystemProperties.SYSPROP_PORTABLE_ROOT, "" );

			if ( root.length() > 0 ){

				portable = root.getBytes( "UTF-8" );
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}

		PORTABLE_ROOT = portable;
	}

	/**
	 * Create a BDecoder using BYTE_ENCODING_CHARSET (ISO_8859_1)
	 */
	public
	BDecoder()
	{
		this(Constants.BYTE_ENCODING_CHARSET, NEWDECODER_FOR_DEF_CHARSET);
	}

	/**
	 * Create a BDecoder using specified charset.
	 * <p/>
	 * New decoder will be used, which can handle UTF-8 properly
	 */
	public
	BDecoder(
		Charset	keyCharset )
	{
		this(keyCharset, true);
	}

	private
	BDecoder(
		Charset	keyCharset,
		boolean	useNewDecoder )
	{
		this.keyCharset = keyCharset;
		this.useNewDecoder = useNewDecoder;
		if (useNewDecoder) {
			keyDecoder = null;
			keyBytes = new byte[32];
		} else {
			keyDecoder = keyCharset.newDecoder();
			keyBytesBuffer = ByteBuffer.allocate(32);
			keyCharsBuffer = CharBuffer.allocate(32);
		}
	}


	public static Map<String,Object>
	decode(
		byte[]	data )

		throws IOException
	{
		return( new BDecoder().decodeByteArray( data ));
	}

	public static Map<String,Object>
	decode(
		byte[]	data,
		int		offset,
		int		length )

		throws IOException
	{
		return( new BDecoder().decodeByteArray( data, offset, length ));
	}

	public static Map<String,Object>
	decode(
		BufferedInputStream	is  )

		throws IOException
	{
		return( new BDecoder().decodeStream( is ));
	}


	public Map<String,Object>
	decodeByteArray(
		byte[] data)

		throws IOException
	{
		return( decode(new BDecoderInputStreamArray(data),true));
	}

	public Map<String, Object>
	decodeByteArray(
		byte[] 	data,
		int		offset,
		int		length )

		throws IOException
	{
		return( decode(new BDecoderInputStreamArray(data, offset, length ),true));
	}

	public Map<String, Object>
	decodeByteArray(
		byte[] 	data,
		int		offset,
		int		length,
		boolean internKeys)

		throws IOException
	{
		return( decode(new BDecoderInputStreamArray(data, offset, length ),internKeys));
	}

	// used externally
	public Map<String, Object> decodeByteBuffer(ByteBuffer buffer, boolean internKeys) throws IOException {
		InputStream is = new BDecoderInputStreamArray(buffer);
		Map<String,Object> result = decode(is,internKeys);
		buffer.position(buffer.limit()-is.available());
		return result;
	}

	public Map<String, Object>
	decodeStream(
		BufferedInputStream data )

		throws IOException
	{
		return decodeStream(data, true);
	}

	public Map<String, Object>
	decodeStream(
		BufferedInputStream data,
		boolean internKeys)

		throws IOException
	{
		Object	res = useNewDecoder
				? decodeInputStream2(data, "", 0, internKeys)
				: decodeInputStream(data, "", 0, internKeys);

		if ( res == null ){

			throw( new BEncodingException( "BDecoder: zero length file" ));

		}else if ( !(res instanceof Map )){

			throw( new BEncodingException( "BDecoder: top level isn't a Map" ));
		}

		return((Map<String,Object>)res );
	}

	private Map<String, Object>
	decode(
		InputStream data, boolean internKeys )

		throws IOException
	{
		Object res = useNewDecoder
				? decodeInputStream2(data, "", 0, internKeys)
				: decodeInputStream(data, "", 0, internKeys);

		if ( res == null ){

			throw( new BEncodingException( "BDecoder: zero length file" ));

		}else if ( !(res instanceof Map )){

			throw( new BEncodingException( "BDecoder: top level isn't a Map" ));
		}

		return((Map<String, Object>)res );
	}

	private Object
	decodeInputStream(
		InputStream dbis,
		String		context,
		int			nesting,
		boolean internKeys)

		throws IOException
	{
		if (nesting == 0 && !dbis.markSupported()) {

			throw new IOException("InputStream must support the mark() method");
		}

			//set a mark

		dbis.mark(1);

			//read a byte

		int tempByte = dbis.read();

			//decide what to do

		switch (tempByte) {
		case 'd' :
				//create a new dictionary object

				// in almost all cases the dictionary keys are ascii or utf-8, as advised in the 'standards'. unfortunately
				// bt v2 has a raw byte key dictionary for 'piece layers'. jeez
			
			if ( context.length() == 12 && context.equals( "piece layers" )){

				ByteEncodedKeyHashMap<String,Object> dict = new ByteEncodedKeyHashMap<>();
				
				try{

						//get the key

					while (true) {

						dbis.mark(1);

						tempByte = dbis.read();
						
						if (tempByte == 'e' || tempByte == -1 ){
							
							break; // end of map
						}
						
						dbis.reset();

						int keyLength = (int)getPositiveNumberFromStream(dbis, ':');

						int skipBytes = 0;

						if ( keyLength > MAX_MAP_KEY_SIZE ){
							skipBytes = keyLength - MAX_MAP_KEY_SIZE;
							keyLength = MAX_MAP_KEY_SIZE;
						}

						byte[] keyBytes = new byte[keyLength];

						getByteArrayFromStream(dbis, keyLength, keyBytes );

						if (skipBytes > 0) {
							
							dbis.skip(skipBytes);
						}


						//decode value

						Object value = decodeInputStream(dbis,"<binary key>",nesting+1,internKeys);

						if ( value == null ){

							System.err.println( "Invalid encoding - value not serialised for binary key " +  Base32.encode( keyBytes ) + " - ignoring: map so far=" + dict + ",loc=" + Debug.getCompressedStackTrace());

							break;
						}

						if (skipBytes > 0) {

							String msg = "dictionary key is too large - "
									+ (keyLength + skipBytes) + ":, max=" + MAX_MAP_KEY_SIZE
									+ ": skipping binary key " +  Base32.encode( keyBytes );
							
							System.err.println( msg );

						} else {

		  					if ( dict.put( new String( keyBytes, Constants.BYTE_ENCODING_CHARSET), value) != null ){

		  						Debug.out( "BDecoder: binary key '" + Base32.encode( keyBytes ) + "' already exists!" );
		  					}
						}
					}

					dbis.mark(1);
					
					tempByte = dbis.read();
					
					dbis.reset();
					
					if ( nesting > 0 && tempByte == -1 ){

						throw( new BEncodingException( "BDecoder: invalid input data, 'e' missing from end of dictionary"));
					}
				}catch( Throwable e ){

					if ( !recovery_mode ){

						if ( e instanceof IOException ){

							throw((IOException)e);
						}

						throw( new IOException( Debug.getNestedExceptionMessage(e)));
					}
				}

					//return the map

				return dict;
				
			}else{
				
				LightHashMap tempMap = new LightHashMap();
				
				try{
					byte[]	prev_key = null;
	
						//get the key
	
					while (true) {
	
						dbis.mark(1);
	
						tempByte = dbis.read();
						if(tempByte == 'e' || tempByte == -1)
							break; // end of map
	
						dbis.reset();
	
						// decode key strings manually so we can reuse the bytebuffer
	
						int keyLength = (int)getPositiveNumberFromStream(dbis, ':');
	
						int skipBytes = 0;
	
						if ( keyLength > MAX_MAP_KEY_SIZE ){
							skipBytes = keyLength - MAX_MAP_KEY_SIZE;
							keyLength = MAX_MAP_KEY_SIZE;
							//new Exception().printStackTrace();
							//throw( new IOException( msg ));
						}
	
						if(keyLength < keyBytesBuffer.capacity())
						{
							keyBytesBuffer.position(0).limit(keyLength);
							keyCharsBuffer.position(0).limit(keyLength);
						} else {
							keyBytesBuffer = ByteBuffer.allocate(keyLength);
							keyCharsBuffer = CharBuffer.allocate(keyLength);
						}
	
						getByteArrayFromStream(dbis, keyLength, keyBytesBuffer.array());
	
						if (skipBytes > 0) {
							dbis.skip(skipBytes);
						}
	
						if ( verify_map_order ){
	
							byte[] current_key = new byte[keyLength];
	
							System.arraycopy( keyBytesBuffer.array(), 0, current_key, 0, keyLength );
	
							if ( prev_key != null ){
	
								int	len = Math.min( prev_key.length, keyLength );
	
								int	state = 0;
	
								for ( int i=0;i<len;i++){
	
									int	cb = current_key[i]&0x00ff;
									int	pb = prev_key[i]&0x00ff;
	
									if ( cb > pb ){
										state = 1;
										break;
									}else if ( cb < pb ){
										state = 2;
										break;
									}
								}
	
								if ( state == 0){
									if ( prev_key.length > keyLength ){
	
										state = 2;
									}
								}
	
								if ( state == 2 ){
	
									// Debug.out( "Dictionary order incorrect: prev=" + new String( prev_key ) + ", current=" + new String( current_key ));
	
									if (!( tempMap instanceof LightHashMapEx )){
	
										LightHashMapEx x = new LightHashMapEx( tempMap );
	
										x.setFlag( LightHashMapEx.FL_MAP_ORDER_INCORRECT, true );
	
										tempMap = x;
									}
								}
							}
	
							prev_key = current_key;
						}
						
						String key;
	
						if ( force_utf8_keys ){
							
							keyDecoderUTF8.reset();
							keyDecoderUTF8.decode(keyBytesBuffer,keyCharsBuffer,true);
							keyDecoderUTF8.flush(keyCharsBuffer);
	
							key = new String(keyCharsBuffer.array(),0,keyCharsBuffer.position());

						}else{
							
							keyDecoder.reset();
							keyDecoder.decode(keyBytesBuffer,keyCharsBuffer,true);
							keyDecoder.flush(keyCharsBuffer);
						
						
							/* XXX Should be keyCharsBuffer.position() and not limit()
							 * .position() is where the decode ended,
							 * .limit() is keyLength in bytes.
							 * Limit may be larger than needed since some chars are built from
							 * multiple bytes. Not changing code because limit and position are
							 * always (?) the same for ISO-8859-1, and for other encodings we
							 * use the new decoder, which handles size correctly
							 */
							key = new String(keyCharsBuffer.array(),0,keyCharsBuffer.limit());
						}
						
						// keys often repeat a lot - intern to save space. utf8 keys imply non-fixed keys (e.g. file names...)
						
						if (internKeys && !force_utf8_keys )
							key = StringInterner.intern( key );
	
							//decode value

						Object value;
						
						if ( !force_utf8_keys && key.equals( "file tree" )){
							
							try{
								force_utf8_keys = true;
								
								keyDecoderUTF8 = Constants.DEFAULT_ENCODING_CHARSET.newDecoder();
								
								value = decodeInputStream(dbis,key,nesting+1,internKeys);
								
							}finally{
								
								force_utf8_keys = false;
							}
						}else{
						
							value = decodeInputStream(dbis,key,nesting+1,internKeys);
						}
						
						// value interning is too CPU-intensive, let's skip that for now
						/*if(value instanceof byte[] && ((byte[])value).length < 17)
						value = StringInterner.internBytes((byte[])value);*/
	
						if ( TRACE ){
							System.out.println( key + "->" + value + ";" );
						}
	
							// recover from some borked encodings that I have seen whereby the value has
							// not been encoded. This results in, for example,
							// 18:azureus_propertiesd0:e
							// we only get null back here if decoding has hit an 'e' or end-of-file
							// that is, there is no valid way for us to get a null 'value' here
	
						if ( value == null ){
	
							System.err.println( "Invalid encoding - value not serialised for '" + key + "' - ignoring: map so far=" + tempMap + ",loc=" + Debug.getCompressedStackTrace());
	
							break;
						}
	
						if (skipBytes > 0) {
	
							String msg = "dictionary key is too large - "
									+ (keyLength + skipBytes) + ":, max=" + MAX_MAP_KEY_SIZE
									+ ": skipping key starting with " + new String(key.substring(0, 128));
							System.err.println( msg );
	
						} else {
	
	  					if ( tempMap.put( key, value) != null ){
	
	  						Debug.out( "BDecoder: key '" + key + "' already exists!" );
	  					}
						}
					}
	
					/*
		        if ( tempMap.size() < 8 ){
	
		        	tempMap = new CompactMap( tempMap );
		        }*/
	
					dbis.mark(1);
					tempByte = dbis.read();
					dbis.reset();
					if ( nesting > 0 && tempByte == -1 ){
	
						throw( new BEncodingException( "BDecoder: invalid input data, 'e' missing from end of dictionary"));
					}
				}catch( Throwable e ){
	
					if ( !recovery_mode ){
	
						if ( e instanceof IOException ){
	
							throw((IOException)e);
						}
	
						throw( new IOException( Debug.getNestedExceptionMessage(e)));
					}
				}
	
				tempMap.compactify(-0.9f);
	
					//return the map
	
				return tempMap;
			}
		case 'l' :
				//create the list

			ArrayList tempList = new ArrayList();

			try{
					//create the key

				String context2 = PORTABLE_ROOT==null?context:(context+"[]");

				Object tempElement = null;
				while ((tempElement = decodeInputStream(dbis, context2, nesting+1, internKeys)) != null) {
						//add the element
					tempList.add(tempElement);
				}

				tempList.trimToSize();
				dbis.mark(1);
				tempByte = dbis.read();
				dbis.reset();
				if ( nesting > 0 && tempByte == -1 ){

					throw( new BEncodingException( "BDecoder: invalid input data, 'e' missing from end of list"));
				}
			}catch( Throwable e ){

				if ( !recovery_mode ){

					if ( e instanceof IOException ){

						throw((IOException)e);
					}

					throw( new IOException( Debug.getNestedExceptionMessage(e)));
				}
			}
				//return the list
			return tempList;

		case 'e' :
		case -1 :
			return null;

		case 'i' :
			return Long.valueOf(getNumberFromStream(dbis, 'e'));

		case '0' :
		case '1' :
		case '2' :
		case '3' :
		case '4' :
		case '5' :
		case '6' :
		case '7' :
		case '8' :
		case '9' :
				//move back one
			dbis.reset();
				//get the string
			return getByteArrayFromStream(dbis, context );

		default :{

			int	rem_len = dbis.available();

			if ( rem_len > 256 ){

				rem_len	= 256;
			}

			byte[] rem_data = new byte[rem_len];

			dbis.read( rem_data );

			throw( new BEncodingException(
					"BDecoder: unknown command '" + tempByte + ", remainder = " + new String( rem_data )));
		}
		}
	}

	/*
  private long getNumberFromStream(InputStream dbis, char parseChar) throws IOException {
    StringBuffer sb = new StringBuffer(3);

    int tempByte = dbis.read();
    while ((tempByte != parseChar) && (tempByte >= 0)) {
    	sb.append((char)tempByte);
      tempByte = dbis.read();
    }

    //are we at the end of the stream?
    if (tempByte < 0) {
      return -1;
    }

    String str = sb.toString();

    	// support some borked impls that sometimes don't bother encoding anything

    if ( str.length() == 0 ){

    	return( 0 );
    }

    return Long.parseLong(str);
  }
	 */

	/**
	 * Differences between decodeInputStream<br/>
	 * This: Uses byte[] for keys<br/>
	 * Other: Uses ByteBuffer<br/>
	 * <br/>
	 * This: Uses new String(byte[], pos, len, charset)<br/>
	 * Other: Uses CharsetDecoder.decode(ByteBuffer, CharBuffer) and new String(charBuffer.array(), 0, len)<br/>
	 * <br/>
	 * This: Verifies map order using key in String form<br/>
	 * Other: Verifies map order by copying bytes and comparing<br/>
	 * <p/>
	 * On a torrent with 100k files and set to verify map order, this method is
	 * 2.5x faster.
	 */
	private Object
	decodeInputStream2(
			InputStream dbis,
			String		context,
			int			nesting,
			boolean internKeys)

			throws IOException
	{
		if (nesting == 0 && !dbis.markSupported()) {

			throw new IOException("InputStream must support the mark() method");
		}

		//set a mark

		dbis.mark(1);

		//read a byte

		int tempByte = dbis.read();

		//decide what to do

		switch (tempByte) {
			case 'd' :
				//create a new dictionary object

				// in almost all cases the dictionary keys are ascii or utf-8, as advised in the 'standards'. unfortunately
				// bt v2 has a raw byte key dictionary for 'piece layers'. jeez
			
			if ( context.length() == 12 && context.equals( "piece layers" )){

				ByteEncodedKeyHashMap<String,Object> dict = new ByteEncodedKeyHashMap<>();
				
				try{

						//get the key

					while (true) {

						dbis.mark(1);

						tempByte = dbis.read();
						
						if (tempByte == 'e' || tempByte == -1 ){
							
							break; // end of map
						}
						
						dbis.reset();

						int keyLength = (int)getPositiveNumberFromStream(dbis, ':');

						int skipBytes = 0;

						if ( keyLength > MAX_MAP_KEY_SIZE ){
							skipBytes = keyLength - MAX_MAP_KEY_SIZE;
							keyLength = MAX_MAP_KEY_SIZE;
						}

						byte[] keyBytes = new byte[keyLength];

						getByteArrayFromStream(dbis, keyLength, keyBytes );

						if (skipBytes > 0) {
							
							dbis.skip(skipBytes);
						}


						//decode value

						Object value = decodeInputStream(dbis,"<binary key>",nesting+1,internKeys);

						if ( value == null ){

							System.err.println( "Invalid encoding - value not serialised for binary key " +  Base32.encode( keyBytes ) + " - ignoring: map so far=" + dict + ",loc=" + Debug.getCompressedStackTrace());

							break;
						}

						if ( skipBytes > 0 ){

							String msg = "dictionary key is too large - "
									+ (keyLength + skipBytes) + ":, max=" + MAX_MAP_KEY_SIZE
									+ ": skipping binary key " +  Base32.encode( keyBytes );
							
							System.err.println( msg );

						}else{

		  					if ( dict.put( new String( keyBytes, Constants.BYTE_ENCODING_CHARSET), value) != null ){
	
		  						Debug.out( "BDecoder: binary key '" + Base32.encode( keyBytes ) + "' already exists!" );
		  					}
						}
					}

					dbis.mark(1);
					
					tempByte = dbis.read();
					
					dbis.reset();
					
					if ( nesting > 0 && tempByte == -1 ){

						throw( new BEncodingException( "BDecoder: invalid input data, 'e' missing from end of dictionary"));
					}
				}catch( Throwable e ){

					if ( !recovery_mode ){

						if ( e instanceof IOException ){

							throw((IOException)e);
						}

						throw( new IOException( Debug.getNestedExceptionMessage(e)));
					}
				}

					//return the map

				return dict;
				
			}else{
				
				LightHashMap tempMap = new LightHashMap();

				try{
					String prev_key = null;

					//get the key

					while (true) {

						dbis.mark(1);

						tempByte = dbis.read();
						if(tempByte == 'e' || tempByte == -1)
							break; // end of map

						dbis.reset();

						// decode key strings manually so we can reuse the bytebuffer

						int keyLength = (int)getPositiveNumberFromStream(dbis, ':');

						int skipBytes = 0;

						if ( keyLength > MAX_MAP_KEY_SIZE ){
							skipBytes = keyLength - MAX_MAP_KEY_SIZE;
							keyLength = MAX_MAP_KEY_SIZE;
							//new Exception().printStackTrace();
							//throw( new IOException( msg ));
						}

						if(keyLength < keyBytesLen)
						{
							keyBytesLen = keyLength;
						} else {
							keyBytes = new byte[keyLength];
							keyBytesLen = keyLength;
						}

						getByteArrayFromStream(dbis, keyLength, keyBytes);

						if (skipBytes > 0) {
							dbis.skip(skipBytes);
						}

						String key;
						
						if ( force_utf8_keys ){
							
							key = new String(keyBytes, 0, keyLength, keyCharsetUTF8 );
							
						}else{
							
							key = new String(keyBytes, 0, keyLength, keyCharset);
						}
						
						// keys often repeat a lot - intern to save space
						if (internKeys && !force_utf8_keys )
							key = StringInterner.intern( key );

						if ( verify_map_order ){
							if (prev_key != null) {
								if (prev_key.compareTo(key) > 0) {
									Debug.out( "Dictionary order incorrect: prev=" + prev_key + ", current=" + key);

									if (!( tempMap instanceof LightHashMapEx )){

										LightHashMapEx x = new LightHashMapEx( tempMap );

										x.setFlag( LightHashMapEx.FL_MAP_ORDER_INCORRECT, true );

										tempMap = x;
									}

								}
							}
							prev_key = key;
						}

						//decode value

						Object value;
						
						if ( !force_utf8_keys && key.equals( "file tree" )){
							
							try{
								force_utf8_keys = true;

								keyCharsetUTF8 = Constants.DEFAULT_ENCODING_CHARSET;
								
								value = decodeInputStream2(dbis,key,nesting+1,internKeys);
								
							}finally{
								
								force_utf8_keys = false;
							}
						}else{
							
							value = decodeInputStream2(dbis,key,nesting+1,internKeys);
						}
						
						// value interning is too CPU-intensive, let's skip that for now
					/*if(value instanceof byte[] && ((byte[])value).length < 17)
					value = StringInterner.internBytes((byte[])value);*/

						if ( TRACE ){
							System.out.println( key + "->" + value + ";" );
						}

						// recover from some borked encodings that I have seen whereby the value has
						// not been encoded. This results in, for example,
						// 18:azureus_propertiesd0:e
						// we only get null back here if decoding has hit an 'e' or end-of-file
						// that is, there is no valid way for us to get a null 'value' here

						if ( value == null ){

							System.err.println( "Invalid encoding - value not serialised for '" + key + "' - ignoring: map so far=" + tempMap + ",loc=" + Debug.getCompressedStackTrace());

							break;
						}

						if (skipBytes > 0) {

							String msg = "dictionary key is too large - "
									+ (keyLength + skipBytes) + ":, max=" + MAX_MAP_KEY_SIZE
									+ ": skipping key starting with " + new String(key.substring(0, 128));
							System.err.println( msg );

						} else {

							if ( tempMap.put( key, value) != null ){

								Debug.out( "BDecoder: key '" + key + "' already exists!" );
							}
						}
					}

				/*
	        if ( tempMap.size() < 8 ){

	        	tempMap = new CompactMap( tempMap );
	        }*/

					dbis.mark(1);
					tempByte = dbis.read();
					dbis.reset();
					if ( nesting > 0 && tempByte == -1 ){

						throw( new BEncodingException( "BDecoder: invalid input data, 'e' missing from end of dictionary"));
					}
				}catch( Throwable e ){

					if ( !recovery_mode ){

						if ( e instanceof IOException ){

							throw((IOException)e);
						}

						throw( new IOException( Debug.getNestedExceptionMessage(e), e));
					}
				}

				if (mapDecodeListener != null) {
					mapDecodeListener.mapDecoded(context, tempMap, nesting);
				}

				tempMap.compactify(-0.9f);

				//return the map

				return tempMap;
			}

			case 'l' :
				//create the list

				ArrayList tempList = new ArrayList();

				try{
					//create the key

					String context2 = PORTABLE_ROOT==null?context:(context+"[]");

					Object tempElement = null;
					while ((tempElement = decodeInputStream2(dbis, context2, nesting+1, internKeys)) != null) {
						//add the element
						tempList.add(tempElement);
					}

					tempList.trimToSize();
					dbis.mark(1);
					tempByte = dbis.read();
					dbis.reset();
					if ( nesting > 0 && tempByte == -1 ){

						throw( new BEncodingException( "BDecoder: invalid input data, 'e' missing from end of list"));
					}
				}catch( Throwable e ){

					if ( !recovery_mode ){

						if ( e instanceof IOException ){

							throw((IOException)e);
						}

						throw( new IOException( Debug.getNestedExceptionMessage(e)));
					}
				}
				//return the list
				return tempList;

			case 'e' :
			case -1 :
				return null;

			case 'i' :
				return Long.valueOf(getNumberFromStream(dbis, 'e'));

			case '0' :
			case '1' :
			case '2' :
			case '3' :
			case '4' :
			case '5' :
			case '6' :
			case '7' :
			case '8' :
			case '9' :
				//move back one
				dbis.reset();
				//get the string
				return getByteArrayFromStream(dbis, context );

			default :{

				int	rem_len = dbis.available();

				if ( rem_len > 256 ){

					rem_len	= 256;
				}

				byte[] rem_data = new byte[rem_len];

				dbis.read( rem_data );

				throw( new BEncodingException(
						"BDecoder: unknown command '" + tempByte + ", remainder = " + new String( rem_data )));
			}
		}
	}

	/** only create the array once per decoder instance (no issues with recursion as it's only used in a leaf method)
	 */
	private final char[] numberChars = new char[32];

	/**
	 * @note will break (likely return a negative) if number >
	 * {@link Integer#MAX_VALUE}.  This check is intentionally skipped to
	 * increase performance
	 */
	private int
	getPositiveNumberFromStream(
			InputStream	dbis,
			char	parseChar)

	throws IOException
	{
		int tempByte = dbis.read();
		if (tempByte < 0) {
			return -1;
		}
		if (tempByte != parseChar) {

			int value = tempByte - '0';

			tempByte = dbis.read();
			// optimized for single digit cases
			if (tempByte == parseChar) {
				return value;
			}
			if (tempByte < 0) {
				return -1;
			}

			while (true) {
				// Base10 shift left --> v*8 + v*2 = v*10
				value = (value << 3) + (value << 1) + (tempByte - '0');
				// For bounds check:
				// if (value < 0) return something;
				tempByte = dbis.read();
				if (tempByte == parseChar) {
					return value;
				}
				if (tempByte < 0) {
					return -1;
				}
			}
		} else {
			return 0;
		}
	}

	private long
	getNumberFromStream(
		InputStream 	dbis,
		char 					parseChar)

		throws IOException
	{


		int tempByte = dbis.read();

		int pos = 0;

		while ((tempByte != parseChar) && (tempByte >= 0)) {
			numberChars[pos++] = (char)tempByte;
			if ( pos == numberChars.length ){
				throw( new NumberFormatException( "Number too large: " + new String(numberChars,0,pos) + "..." ));
			}
			tempByte = dbis.read();
		}

		//are we at the end of the stream?

		if (tempByte < 0) {

			return -1;

		}else if ( pos == 0 ){
			// support some borked impls that sometimes don't bother encoding anything

			return(0);
		}

		try{
			return( parseLong( numberChars, 0, pos ));

		}catch( NumberFormatException e ){

			String temp = new String( numberChars, 0, pos );

			try{
				double d = Double.parseDouble( temp );

				long l = (long)d;

				Debug.out( "Invalid number '" + temp + "' - decoding as " + l + " and attempting recovery" );

				return( l );

			}catch( Throwable f ){
			}

			throw( e );
		}
	}

	// This is similar to Long.parseLong(String) source
	// It is also used in projects external to azureus2/azureus3 hence it is public
	public static long
	parseLong(
		char[]	chars,
		int		start,
		int		length )
	{
		if ( length > 0 ){
			// Short Circuit: We don't support octal parsing, so if it
			// starts with 0, it's 0
			if (chars[start] == '0') {

				return 0;
			}

			long result = 0;

			boolean negative = false;

			int 	i 	= start;

			long limit;

			if ( chars[i] == '-' ){

				negative = true;

				limit = Long.MIN_VALUE;

				i++;

			}else{
				// Short Circuit: If we are only processing one char,
				// and it wasn't a '-', just return that digit instead
				// of doing the negative junk
				if (length == 1) {
					int digit = chars[i] - '0';

					if ( digit < 0 || digit > 9 ){

						throw new NumberFormatException(new String(chars,start,length));

					}else{

						return digit;
					}
				}

				limit = -Long.MAX_VALUE;
			}

			int	max = start + length;

			if ( i < max ){

				int digit = chars[i++] - '0';

				if ( digit < 0 || digit > 9 ){

					throw new NumberFormatException(new String(chars,start,length));

				}else{

					result = -digit;
				}
			}

			long multmin = limit / 10;

			while ( i < max ){

				// Accumulating negatively avoids surprises near MAX_VALUE

				int digit = chars[i++] - '0';

				if ( digit < 0 || digit > 9 ){

					throw new NumberFormatException(new String(chars,start,length));
				}

				if ( result < multmin ){

					throw new NumberFormatException(new String(chars,start,length));
				}

				result *= 10;

				if ( result < limit + digit ){

					throw new NumberFormatException(new String(chars,start,length));
				}

				result -= digit;
			}

			if ( negative ){

				if ( i > start+1 ){

					return result;

				}else{	/* Only got "-" */

					throw new NumberFormatException(new String(chars,start,length));
				}
			}else{

				return -result;
			}
		}else{

			throw new NumberFormatException(new String(chars,start,length));
		}

	}



	// This one causes lots of "Query Information" calls to the filesystem
	/*
  private long getNumberFromStreamOld(InputStream dbis, char parseChar) throws IOException {
    int length = 0;

    //place a mark
    dbis.mark(???); 1 wouldn't work here ;)

    int tempByte = dbis.read();
    while ((tempByte != parseChar) && (tempByte >= 0)) {
      tempByte = dbis.read();
      length++;
    }

    //are we at the end of the stream?
    if (tempByte < 0) {
      return -1;
    }

    //reset the mark
    dbis.reset();

    //get the length
    byte[] tempArray = new byte[length];
    int count = 0;
    int len = 0;

    //get the string
    while (count != length && (len = dbis.read(tempArray, count, length - count)) > 0) {
      count += len;
    }

    //jump ahead in the stream to compensate for the :
    dbis.skip(1);

    //return the value

    CharBuffer	cb = Constants.DEFAULT_CHARSET.decode(ByteBuffer.wrap(tempArray));

    String	str_value = new String(cb.array(),0,cb.limit());

    return Long.parseLong(str_value);
  }
	 */

	private byte[]
	getByteArrayFromStream(
		InputStream dbis,
		String		context )

		throws IOException
	{
		int length = (int) getPositiveNumberFromStream(dbis, ':');

		if (length < 0) {
			return null;
		}

		// note that torrent hashes can be big (consider a 55GB file with 2MB pieces
		// this generates a pieces hash of 1/2 meg

		if ( length > MAX_BYTE_ARRAY_SIZE ){

			throw( new IOException( "Byte array length too large (" + length + ")"));
		}

		byte[] tempArray = new byte[length];

		getByteArrayFromStream(dbis, length, tempArray);

		if ( PORTABLE_ROOT != null && length >= PORTABLE_ROOT.length && tempArray[1] == ':' && tempArray[2] == '\\' && context != null ){

			boolean	mismatch = false;

			for ( int i=2;i<PORTABLE_ROOT.length;i++){

				if ( tempArray[i] != PORTABLE_ROOT[i] ){

					mismatch = true;

					break;
				}
			}

			if ( !mismatch ){

				context = context.toLowerCase( Locale.US );

					// always a chance a hash will match the root so we just pick on relevant looking
					// entries...

				if ( 	context.contains( "file" ) ||
						context.contains( "link" ) ||
						context.contains( "dir" ) ||
						context.contains( "folder" ) ||
						context.contains( "path" ) ||
						context.contains( "save" ) ||
						context.contains( "torrent" )){

					tempArray[0] = PORTABLE_ROOT[0];

					/*
					String	test = new String( tempArray, 0, tempArray.length > 80?80:tempArray.length );

					System.out.println( "mapped " + context + "->" + tempArray.length + ": " + test );
					*/

				}else{

					String	test = new String( tempArray, 0, tempArray.length > 80?80:tempArray.length );

					System.out.println( "Portable: not mapping " + context + "->" + tempArray.length + ": " + test );
				}
			}
		}

		return tempArray;
	}

	private void getByteArrayFromStream(InputStream dbis, int length, byte[] targetArray) throws IOException {

		int count = 0;
		int len = 0;
		//get the string
		while (count != length && (len = dbis.read(targetArray, count, length - count)) > 0)
			count += len;

		if (count != length)
			throw (new IOException("BDecoder::getByteArrayFromStream: truncated"));
	}

	public void
	setVerifyMapOrder(
		boolean	b )
	{
		verify_map_order = b;
	}

	public void
	setRecoveryMode(
		boolean	r )
	{
		recovery_mode	= r;
	}

	public static void
	print(
		Object		obj )
	{
		StringWriter 	sw = new StringWriter();

		PrintWriter		pw = new PrintWriter( sw );

		print( pw, obj );

		pw.flush();

		System.out.println( sw.toString());
	}

	public static void
	print(
		PrintWriter	writer,
		Object		obj )
	{
		print( writer, obj, "", false );
	}

	private static void
	print(
		PrintWriter	writer,
		Object		obj,
		String		indent,
		boolean		skip_indent )
	{
		String	use_indent = skip_indent?"":indent;

		if ( obj instanceof Long ){

			writer.println( use_indent + obj );

		}else if ( obj instanceof byte[]){

			byte[]	b = (byte[])obj;

			if ( b.length==20 ){
				writer.println( use_indent + " { "+ ByteFormatter.nicePrint( b )+ " }" );
			}else if ( b.length < 64 ){
				writer.println( new String(b) + " [" + ByteFormatter.encodeString( b ) + "]" );
			}else{
				writer.println( "[byte array length " + b.length );
			}

		}else if ( obj instanceof String ){

			writer.println( use_indent + obj );

		}else if ( obj instanceof List ){

			List	l = (List)obj;

			writer.println( use_indent + "[" );

			for (int i=0;i<l.size();i++){

				writer.print( indent + "  (" + i + ") " );

				print( writer, l.get(i), indent + "    ", true );
			}

			writer.println( indent + "]" );

		}else{

			Map	m = (Map)obj;

			Iterator	it = m.keySet().iterator();

			while( it.hasNext()){

				String	key = (String)it.next();

				if ( key.length() > 256 ){
					writer.print( indent + key.substring(0,256) + "... = " );
				}else{
					writer.print( indent + key + " = " );
				}

				print( writer, m.get(key), indent + "  ", true );
			}
		}
	}

	/**
	 * Converts any byte[] entries into UTF-8 strings.
	 * REPLACES EXISTING MAP VALUES
	 *
	 * @param map
	 * @return
	 */

	public static Map
	decodeStrings(
		Map	map )
	{
		if (map == null ){

			return( null );
		}

		Iterator it = map.entrySet().iterator();

		while( it.hasNext()){

			Map.Entry	entry = (Map.Entry)it.next();

			Object	value = entry.getValue();

			if ( value instanceof byte[]){

				try{
					entry.setValue( new String((byte[])value,"UTF-8" ));

				}catch( Throwable e ){

					System.err.println(e);
				}
			}else if ( value instanceof Map ){

				decodeStrings((Map)value );
			}else if ( value instanceof List ){

				decodeStrings((List)value );
			}
		}

		return( map );
	}

	/**
	 * Decodes byte arrays into strings.
	 * REPLACES EXISTING LIST VALUES
	 *
	 * @param list
	 * @return the same list passed in
	 */
	public static List
	decodeStrings(
		List	list )
	{
		if ( list == null ){

			return( null );
		}

		for (int i=0;i<list.size();i++){

			Object value = list.get(i);

			if ( value instanceof byte[]){

				try{
					String str = new String((byte[])value, "UTF-8" );

					list.set( i, str );

				}catch( Throwable e ){

					System.err.println(e);
				}
			}else if ( value instanceof Map ){

				decodeStrings((Map)value );

			}else if ( value instanceof List ){

				decodeStrings((List)value );
			}
		}

		return( list );
	}

	private static void
	print(
		File		f,
		File		output )
	{
		try{
			BDecoder	decoder = new BDecoder();

			decoder.setRecoveryMode( false );

			PrintWriter	pw = new PrintWriter( new OutputStreamWriter( FileUtil.newFileOutputStream( output )));

			print( pw, decoder.decodeStream( new BufferedInputStream( FileUtil.newFileInputStream( f ))));

			pw.flush();

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

   	// JSON

    private static Object
    decodeFromJSONGeneric(
    	Object		obj )
    {
    	if ( obj == null ){

    		return( null );

    	}else if ( obj instanceof Map ){

    		return( decodeFromJSONObject((Map)obj));

    	}else if ( obj instanceof List ){

    		return( decodeFromJSONArray((List)obj));

     	}else if ( obj instanceof String ){

 			String s = (String)obj;

     		try{

     			int	len = s.length();

     			if ( len >= 6 && s.startsWith( "\\x" ) && s.endsWith( "\\x" )){

     				byte[]	result = new byte[(len-4)/2];

     				int	pos = 2;

     				for ( int i=0;i<result.length;i++){

     					result[i] = (byte)Integer.parseInt( s.substring( pos, pos+2 ), 16 );

     					pos += 2;
     				}

     				return( result );
     			}

     			return(s.getBytes( "UTF-8" ));

     		}catch( Throwable e ){

     			return(s.getBytes());
     		}

     	}else if ( obj instanceof Long ){

      		return( obj );

      	}else if ( obj instanceof Boolean ){

    		return( new Long(((Boolean)obj)?1:0 ));

    	}else if ( obj instanceof Double ){

    		return( String.valueOf((Double)obj));

    	}else{

    		System.err.println( "Unexpected JSON value type: " + obj.getClass());

    		return( obj );
    	}
    }

    public static List
    decodeFromJSONArray(
    	List		j_list )
    {
    	List	b_list = new ArrayList();

    	for ( Object o: j_list ){

    		b_list.add( decodeFromJSONGeneric( o ));
    	}

    	return( b_list );
    }


    public static Map
    decodeFromJSONObject(
    	Map<Object,Object>		j_map )
    {
    	Map	b_map = new HashMap();

    	for ( Map.Entry<Object,Object> entry: j_map.entrySet()){

    		Object	key = entry.getKey();
    		Object	val	= entry.getValue();

    		b_map.put((String)key, decodeFromJSONGeneric( val ));
    	}

    	return( b_map );
    }

    public static Map
    decodeFromJSON(
    	String	json )
    {
    	Map j_map = JSONUtils.decodeJSON(json);

    	return( decodeFromJSONObject( j_map ));
    }

	public MapDecodeListener getMapDecodeListener() {
		return mapDecodeListener;
	}

	public void setMapDecodeListener(MapDecodeListener mapDecodeListener) {
		this.mapDecodeListener = mapDecodeListener;
	}


/*
	private interface
	BDecoderInputStream
	{
		public int
		read()

			throws IOException;

		public int
		read(
			byte[] buffer )

			throws IOException;

		public int
		read(
			byte[] 	buffer,
			int		offset,
			int		length )

			throws IOException;

		public int
		available()

			throws IOException;

		public boolean
		markSupported();

		public void
		mark(
				int	limit );

		public void
		reset()

			throws IOException;
	}

	private class
	BDecoderInputStreamStream

		implements BDecoderInputStream
	{
		final private BufferedInputStream		is;

		private
		BDecoderInputStreamStream(
			BufferedInputStream	_is )
		{
			is	= _is;
		}

		public int
		read()

		throws IOException
		{
			return( is.read());
		}

		public int
		read(
			byte[] buffer )

		throws IOException
		{
			return( is.read( buffer ));
		}

		public int
		read(
			byte[] 	buffer,
			int		offset,
			int		length )

			throws IOException
		{
			return( is.read( buffer, offset, length ));
		}

		public int
		available()

			throws IOException
		{
			return( is.available());
		}

		public boolean
		markSupported()
		{
			return( is.markSupported());
		}

		public void
		mark(
			int	limit )
		{
			is.mark( limit );
		}

		public void
		reset()

			throws IOException
		{
			is.reset();
		}
	}
*/
	private static class
	BDecoderInputStreamArray

		extends InputStream
	{
		final private byte[] bytes;
		private int pos = 0;
		private int markPos;
		private final int overPos;


		public BDecoderInputStreamArray(ByteBuffer buffer) {
			bytes = buffer.array();
			pos = buffer.arrayOffset() + buffer.position();
			overPos = pos + buffer.remaining();
		}


		private
		BDecoderInputStreamArray(
			byte[]		_buffer )
		{
			bytes = _buffer;
			overPos = bytes.length;
		}

		private
		BDecoderInputStreamArray(
			byte[]		_buffer,
			int			_offset,
			int			_length )
		{
			if (_offset == 0) {
				bytes = _buffer;
				overPos = _length;
			} else {
				bytes = _buffer;
				pos = _offset;
				overPos = Math.min(_offset + _length, bytes.length);
			}
		}

		@Override
		public int
		read()

			throws IOException
		{
			if (pos < overPos) {
				return bytes[pos++] & 0xFF;
			}
			return -1;
		}

		@Override
		public int
		read(
			byte[] buffer )

			throws IOException
		{
			return( read( buffer, 0, buffer.length ));
		}

		@Override
		public int
		read(
			byte[] 	b,
			int		offset,
			int		length )

			throws IOException
		{

			if (pos < overPos) {
				int toRead = Math.min(length, overPos - pos);
				System.arraycopy(bytes, pos, b, offset, toRead);
				pos += toRead;
				return toRead;
			}
			return -1;

		}

		@Override
		public int
		available()

			throws IOException
		{
			return overPos - pos;
		}

		@Override
		public boolean
		markSupported()
		{
			return( true );
		}

		@Override
		public void
		mark(
			int	limit )
		{
			markPos = pos;
		}

		@Override
		public void
		reset()

			throws IOException
		{
			pos = markPos;
		}
	}

	public interface MapDecodeListener {
		/**
		 * Triggered when a map is finished decoding.  Useful if you want to
		 * modify the map before it's returned.  For example, removing unused
		 * key/values that would otherwise take up memory.
		 *
		 * @param context root context is ""
		 * @param map Modifying this map will affect the return value from BDecoder.decode(..)
		 * @param nestingLevel root level is 0
		 */
		void mapDecoded(String context, Map<String, Object> map, int nestingLevel);
	}

	public static void
	main(
			String[]	args )
	{
		print( 	new File( "C:\\Temp\\tables.config" ),
				new File( "C:\\Temp\\tables.txt" ));
	}
}
