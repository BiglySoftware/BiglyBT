/*
 * BEncoder.java
 *
 * Created on June 4, 2003, 10:17 PM
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.util.JSONUtils;

/**
 * A set of utility methods to encode a Map into a bencoded array of byte.
 * integer are represented as Long, String as byte[], dictionaries as Map, and list as List.
 *
 * @author  TdC_VgA
 */
public class
BEncoder
{
	private static final int BUFFER_DOUBLE_LIMIT	= 256*1024;

	private static final byte[] MINUS_1_BYTES = "-1".getBytes();

	private static volatile int non_ascii_logs;

    public static byte[]
    encode(
    	Map object )

    	throws IOException
    {
    	BEncoder encoder = new BEncoder();

    	encoder.encodeObject( object, false );

    	return( encoder.toByteArray());
    }

    public static void
    encodeToStream(
    	Map					object,
    	OutputStream		os )
    
    	throws IOException
    {
    	BEncoder encoder = new BEncoder();
    	
    	encoder.encodeObject( object, os, false );
    }
    
    private byte[]		current_buffer		= new byte[16*1024];
    private int			current_buffer_pos	= 0;
    private byte[][]	old_buffers;
    
    private OutputStream	current_os;
    

    private final byte[]		int_buffer			= new byte[12];
    
    private void
   	encodeObject(
   		Object 					object,
   		OutputStream			os,
   		boolean					utf_key_expected )

       	throws IOException
   	{
    	current_os	= os;
    	
    	encodeObject( object, utf_key_expected );
    	
    	if ( current_buffer_pos > 0 ){
    		
    		current_os.write( current_buffer, 0, current_buffer_pos );
    	}
   	}
    
    private boolean
   	encodeObject(
   		Object 					object,
   		boolean					utf_key_expected )

       	throws IOException
   	{
        if (object instanceof BEncodableObject) {
            object = ((BEncodableObject)object).toBencodeObject();
        }

        if ( object instanceof String || object instanceof Float || object instanceof Double ){

            String tempString = (object instanceof String) ? (String)object : String.valueOf(object);

            	// usually this is simpler to encode by hand as chars < 0x80 map directly in UTF-8

            boolean	simple = true;

            int	char_count = tempString.length();

            byte[]	encoded = new byte[char_count];

            for (int i=0;i<char_count;i++){

            	char c = tempString.charAt(i);

            	if ( c < 0x80 ){

            		encoded[i] = (byte)c;

            	}else{

            		simple = false;

            		break;
            	}
            }

            if ( simple ){

             	writeInt( char_count );

	            writeChar( ':' );

            	writeBytes( encoded );

            }else{

				ByteBuffer bb = Constants.DEFAULT_ENCODING_CHARSET.encode(tempString);

	            writeInt( bb.limit() );

	            writeChar(':');

	            writeByteBuffer(bb );
            }

        }else if(object instanceof Map){

            Map tempMap = (Map)object;

            SortedMap tempTree = null;

            	// unfortunately there are some occasions where we want to ensure that
            	// the 'key' of the map is not mangled by assuming its UTF-8 encodable.
            	// In particular the response from a tracker scrape request uses the
            	// torrent hash as the KEY. Hence the introduction of the type below
            	// to allow the constructor of the Map to indicate that the keys should
            	// be extracted using a BYTE_ENCODING

            boolean	byte_keys = object instanceof ByteEncodedKeyHashMap;

            //write the d
            writeChar('d');

            //are we sorted?
            if ( tempMap instanceof TreeMap ){

                tempTree = (TreeMap)tempMap;

            }else{
            		tempTree = new TreeMap(tempMap);
            }

            Iterator	it = tempTree.entrySet().iterator();

            while( it.hasNext()){

            	Map.Entry	entry = (Map.Entry)it.next();

            	Object o_key = entry.getKey();

   			   	Object value = entry.getValue();

   			   	if (value != null)
				{
					if (o_key instanceof byte[])
					{
						encodeObject(o_key,false);
						if (!encodeObject(value,utf_key_expected))
							encodeObject("",false);
					} else if(o_key instanceof String)
					{
						String key = (String) o_key;
						if (byte_keys)
						{
							encodeObject(Constants.BYTE_ENCODING_CHARSET.encode(key),false);
							if (!encodeObject(value,utf_key_expected))
								encodeObject("",false);
						} else
						{
								// if we put non-ascii chars in as keys we can get horrible expanding
								// config issues as we cycle through decode/encode cycles with certain
								// characters

							if ( !utf_key_expected ){
								
								if ( key.equals( "file tree" )){	// GAH, yes, v2 torrents have utf-8 keys for the file tree
									
									utf_key_expected = true;
								}
							}
							
							if ( Constants.IS_CVS_VERSION && !utf_key_expected ){
								char[]	chars = key.toCharArray();

								for ( char c: chars ){

									if (c >= '\u0080'){

										if ( non_ascii_logs < 50 ){

											non_ascii_logs++;

											Debug.out( "Non-ASCII key: " + key );
										}

										break;
									}
								}
							}
							encodeObject(key,utf_key_expected); // Key goes in as UTF-8
							if (!encodeObject(value, utf_key_expected))
								encodeObject("", false);
						}
					} else
						Debug.out( "Attempt to encode an unsupported map key type: " + object.getClass() + ";value=" + object);
				}
            }

            writeChar('e');


        }else if(object instanceof List){

            List tempList = (List)object;

            	//write out the l

            writeChar('l');

            for(int i = 0; i<tempList.size(); i++){

            	encodeObject( tempList.get(i), utf_key_expected);
            }

            writeChar('e');

        }else if(object instanceof Long){

            Long tempLong = (Long)object;
            //write out the l
            writeChar('i');
            writeLong(tempLong.longValue());
            writeChar('e');

       }else if(object instanceof byte[]){

            byte[] tempByteArray = (byte[])object;
            writeInt(tempByteArray.length);
            writeChar(':');
            writeBytes(tempByteArray);

       }else if ( object instanceof Integer ){

			Integer tempInteger = (Integer)object;
			//write out the l
			writeChar('i');
			writeInt(tempInteger.intValue());
			writeChar('e');

       }else if (object instanceof Byte ){

			byte temp = (Byte)object;

			writeChar('i');
			writeInt( temp & 0x000000ff );
			writeChar('e');

       }else if( object instanceof ByteBuffer ){

       		ByteBuffer  bb = (ByteBuffer)object;
       		writeInt(bb.limit());
       		writeChar(':');
            writeByteBuffer(bb);

       }else if( object instanceof Boolean ){

    	   Boolean b = (Boolean)object;
           //write out the l
           writeChar('i');
           writeLong(b?1:0);
           writeChar('e');
       }else if ( object == null ){

    	   	// ideally we'd bork here but I don't want to run the risk of breaking existing stuff so just log


    	   Debug.out( "Attempt to encode a null value: sofar=" + getEncodedSoFar());
    	   return false;

       }else{


    	   Debug.out( "Attempt to encode an unsupported entry type: " + object.getClass() + ";value=" + object);
    	   return false;
       }

        return true;
    }

    private void
    writeChar(
    	char		c )
    
    	throws IOException
   	{
    	int rem = current_buffer.length - current_buffer_pos;

    	if ( rem > 0 ){

    		current_buffer[current_buffer_pos++] = (byte)c;

    	}else{

    		int current_buffer_size = current_buffer.length;
    		
       		int	next_buffer_size = current_buffer_size < BUFFER_DOUBLE_LIMIT?(current_buffer_size << 1):current_buffer_size;

    		byte[]	new_buffer = new byte[ next_buffer_size ];

    		new_buffer[ 0 ] = (byte)c;

    		if ( current_os != null ){
    			
    			current_os.write( current_buffer );
    			
    		}else{
    			
	    		if ( old_buffers == null ){
	
	    			old_buffers = new byte[][]{ current_buffer };
	
	    		}else{
	
	    			byte[][] new_old_buffers = new byte[old_buffers.length+1][];
	
	    			System.arraycopy( old_buffers, 0, new_old_buffers, 0, old_buffers.length );
	
	    			new_old_buffers[ old_buffers.length ] = current_buffer;
	
	    			old_buffers = new_old_buffers;
	    		}
    		}

    		current_buffer		= new_buffer;
    		current_buffer_pos 	= 1;
     	}
   	}

    private void
    writeInt(
    	int		i )
    
    	throws IOException
    {
    		// we get a bunch of -1 values, optimise

    	if ( i == -1 ){

    		writeBytes( MINUS_1_BYTES );

    		return;
    	}

    	int start = intToBytes( i );

    	writeBytes( int_buffer, start, 12 - start );
    }

    private void
    writeLong(
    	long	l )
    
    	throws IOException
    {
     	if ( l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE ){

    		writeInt((int)l);

    	}else{

    		writeBytes(Long.toString( l ).getBytes());
    	}
    }

    private void
    writeBytes(
    	byte[]			bytes )
    
    	throws IOException
    {
    	writeBytes( bytes, 0, bytes.length );
    }

    private void
    writeBytes(
    	byte[]			bytes,
    	int				offset,
    	int				length )
    
    	throws IOException
    {
    	int rem = current_buffer.length - current_buffer_pos;

    	if ( rem >= length ){

    		System.arraycopy( bytes, offset, current_buffer, current_buffer_pos, length );

    		current_buffer_pos += length;

    	}else{

    		if ( rem > 0 ){

	    		System.arraycopy( bytes, offset, current_buffer, current_buffer_pos, rem );

	    		length -= rem;
    		}

    		int current_buffer_size = current_buffer.length;
    		
       		int	next_buffer_size = current_buffer_size < BUFFER_DOUBLE_LIMIT?(current_buffer_size << 1):current_buffer_size;

    		byte[]	new_buffer = new byte[ Math.max( next_buffer_size, length + 512 ) ];

    		System.arraycopy( bytes, offset + rem, new_buffer, 0, length );

    		if ( current_os != null ){
    			
    			current_os.write( current_buffer );
    			
    		}else{
    			
	    		if ( old_buffers == null ){
	
	    			old_buffers = new byte[][]{ current_buffer };
	
	    		}else{
	
	    			byte[][] new_old_buffers = new byte[old_buffers.length+1][];
	
	    			System.arraycopy( old_buffers, 0, new_old_buffers, 0, old_buffers.length );
	
	    			new_old_buffers[ old_buffers.length ] = current_buffer;
	
	    			old_buffers = new_old_buffers;
	    		}
    		}
    		
    		current_buffer		= new_buffer;
    		current_buffer_pos 	= length;
     	}
    }

    private void
	writeByteBuffer(
		ByteBuffer		bb )
	
		throws IOException
    {
    	writeBytes( bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
    }

    private String
    getEncodedSoFar()
    {
    	return( new String( toByteArray()));
    }

    private byte[]
    toByteArray()
    {
    	if ( old_buffers == null ){

    		byte[]	res = new byte[current_buffer_pos];

    		System.arraycopy( current_buffer, 0, res, 0, current_buffer_pos );

    		// System.out.println( "-> " + current_buffer_pos );

    		return( res );

    	}else{

    		int	total = current_buffer_pos;

    		for (int i=0;i<old_buffers.length;i++){

    			total += old_buffers[i].length;
    		}

    		byte[] res = new byte[total];

    		int	pos = 0;

    		//String str = "";

    		for (int i=0;i<old_buffers.length;i++){

    			byte[] buffer = old_buffers[i];

    			int	len = buffer.length;

    			System.arraycopy( buffer, 0, res, pos, len );

    			pos += len;

    			//str += (str.length()==0?"":",") + len;
    		}

      		System.arraycopy( current_buffer, 0, res, pos, current_buffer_pos );

    		//System.out.println( "-> " + str + "," + current_buffer_pos );

      		return( res );
    	}
    }


    private static Object
    normaliseObject(
    	Object		o )
    {
    	if ( o instanceof Integer ){
       		o = new Long(((Integer)o).longValue());
    	}else if ( o instanceof Byte ){
           		o = new Long((((Byte)o)&0x000000ff));
      	}else if ( o instanceof Boolean ){
       		o = new Long(((Boolean)o).booleanValue()?1:0);
     	}else if ( o instanceof Float ){
       		o = String.valueOf((Float)o);
     	}else if ( o instanceof Double ){
       		o = String.valueOf((Double)o);
	    } else if ( o instanceof BEncodableObject ) {
    		o = ((BEncodableObject) o).toBencodeObject();
       	}else if ( o instanceof byte[] ){
       		try{
       			byte[] b = (byte[])o;

       			String s = new String(b,"UTF-8");

       			byte[] temp = s.getBytes( "UTF-8" );

       				// if converting the raw byte array into a UTF string and back again doesn't result in
       				// the same bytes then things ain't gonna work dump as a demarked hex string that
       				// can be recognized and handled in BDecoder appropriately

       			if ( !Arrays.equals( b, temp )){

       				StringBuilder sb = new StringBuilder( b.length * 2 + 4 );

					sb.append("\\x");

       				for ( byte x: b ){
       					String ss=Integer.toHexString(x&0xff);
    					for(int k=0;k<2-ss.length();k++){
    						sb.append('0');
    					}
    					sb.append(ss);
       				}

					sb.append("\\x");

       				o = sb.toString();

       			}else{

       				o = s;
       			}
       		}catch( Throwable e ){
       		}
       	}

    	return( o );
    }

    public static boolean isEncodable(Object toCheck) {
		if (toCheck instanceof Integer || toCheck instanceof Long || toCheck instanceof Boolean || toCheck instanceof Float || toCheck instanceof byte[] || toCheck instanceof String || toCheck instanceof BEncodableObject)
			return true;
		if (toCheck instanceof Map)
		{
			for (Iterator it = ((Map) toCheck).keySet().iterator(); it.hasNext();)
			{
				Map.Entry entry = (Map.Entry) it.next();
				Object key = entry.getKey();
				if (!(key instanceof String || key instanceof byte[]) || !isEncodable(entry.getValue()))
					return false;
			}
			return true;
		}
		if (toCheck instanceof List)
		{
			for (Iterator it = ((List) toCheck).iterator(); it.hasNext();)
				if (!isEncodable(it.next()))
					return false;
			return true;
		}
		return false;
	}


    public static boolean
    objectsAreIdentical(
    	Object		o1,
    	Object		o2 )
    {
    	if ( o1 == null && o2 == null ){

    		return( true );

    	}else if ( o1 == null || o2 == null ){

    		return( false );
    	}

      	if ( o1.getClass() != o2.getClass()){

    		if ( 	( o1 instanceof Map && o2 instanceof Map ) ||
    				( o1 instanceof List && o2 instanceof List )){

    			// things actually OK

    		}else{

		    	o1 = normaliseObject( o1 );
		    	o2 = normaliseObject( o2 );

		    	boolean isOk = ( o1 instanceof Map && o2 instanceof Map ) ||
					    ( o1 instanceof List && o2 instanceof List );

		    	if ( !isOk && o1.getClass() != o2.getClass()){

			    	Debug.out( "Failed to normalise classes " + o1.getClass() + "/" + o2.getClass());

			    	return( false );
		    	}
      		}
    	}

    	if ( 	o1 instanceof Long ||
    			o1 instanceof String ){

    		return( o1.equals( o2 ));

     	}else if ( o1 instanceof byte[] ){

     		return( Arrays.equals((byte[])o1,(byte[])o2 ));

    	}else if ( o1 instanceof List ){

    		return( listsAreIdentical((List)o1,(List)o2));

       	}else if ( o1 instanceof Map ){

    		return( mapsAreIdentical((Map)o1,(Map)o2));

       	}else if ( 	o1 instanceof Integer ||
	    			o1 instanceof Boolean ||
	    			o1 instanceof Float ||
	    			o1 instanceof ByteBuffer ){

    		return( o1.equals( o2 ));

    	}else{

    		Debug.out( "Invalid type: " + o1 );

    		return( false );
    	}
    }

    public static boolean
	listsAreIdentical(
		List	list1,
		List	list2 )
    {
    	if ( list1 == null && list2 == null ){

    		return( true );

    	}else if ( list1 == null || list2 == null ){

    		return( false );
    	}

    	if ( list1.size() != list2.size()){

    		return( false );
    	}

    	for ( int i=0;i<list1.size();i++){

    		if ( !objectsAreIdentical( list1.get(i), list2.get(i))){

    			return( false );
    		}
    	}

    	return( true );
    }

    public static boolean
	mapsAreIdentical(
		Map	map1,
		Map	map2 )
	{
    	if ( map1 == null && map2 == null ){

    		return( true );

    	}else if ( map1 == null || map2 == null ){

    		return( false );
    	}

    	if ( map1.size() != map2.size()){

    		return( false );
    	}

    	for (Map.Entry<Object,Object> entry: ((Map<Object,Object>)map1).entrySet()){

    		Object	key = entry.getKey();

    		Object	v1 = entry.getValue();
    		Object	v2 = map2.get(key);

    		if ( !objectsAreIdentical( v1, v2 )){

    			return( false );
    		}
    	}

    	return( true );
    }

    public static Map
    cloneMap(
    	Map		map )
    {
    	if ( map == null ){

    		return( null );
    	}

    	Map res = new TreeMap();

    	Iterator	it = map.entrySet().iterator();

    	while( it.hasNext()){

    		Map.Entry	entry = (Map.Entry)it.next();

    		Object	key 	= entry.getKey();
    		Object	value	= entry.getValue();

    			// keys must be String (or very rarely byte[])

    		if ( key instanceof byte[] ){

    			key = ((byte[])key).clone();
    		}

    		res.put( key, clone( value ));
    	}

    	return( res );
    }

    public static List
    cloneList(
    	List		list )
    {
    	if ( list == null ){

    		return( null );
    	}

    	List	res = new ArrayList(list.size());

    	Iterator	it = list.iterator();

    	while( it.hasNext()){

    		res.add( clone( it.next()));
    	}

    	return( res );
    }

    public static Object
    clone(
    	Object	obj )
    {
    	if ( obj instanceof List ){

    		return( cloneList((List)obj));

    	}else if ( obj instanceof Map ){

    		return( cloneMap((Map)obj));

    	}else if ( obj instanceof byte[]){

    		return(((byte[])obj).clone());

    	}else{
    			// assume immutable - String,Long etc

    		return( obj );
    	}
    }

    public static StringBuffer
    encodeToXML(
    	Map			map,
    	boolean		simple )
    {
     	XMLEncoder writer = new XMLEncoder();

     	return( writer.encode( map, simple ));
    }

    	// JSON

    private static Object
    encodeToJSONGeneric(
    	Object		obj )
    {
    	if ( obj instanceof Map ){

    		return( encodeToJSONObject((Map)obj));

    	}else if ( obj instanceof List ){

    		return( encodeToJSONArray((List)obj));

    	}else{

    		return( normaliseObject( obj ));
    	}
    }

    public static JSONArray
    encodeToJSONArray(
    	List		b_list )
    {
    	if ( b_list == null ){

    		return( null );
    	}

    	JSONArray	j_list = new JSONArray();

    	for ( Object o: b_list ){

    		j_list.add( encodeToJSONGeneric( o ));
    	}

    	return( j_list );
    }


    public static JSONObject
    encodeToJSONObject(
    	Map<Object,Object>		b_map )
    {
    	if ( b_map == null ){

    		return( null );
    	}

    	JSONObject	j_map = new JSONObject();

    	for ( Map.Entry<Object,Object> entry: b_map.entrySet()){

    		Object	key = entry.getKey();
    		Object	val	= entry.getValue();

    		j_map.put((String)key, encodeToJSONGeneric( val ));
    	}

    	return( j_map );
    }

    public static String
    encodeToJSON(
    	Map	b_map )
    {
    	if ( b_map == null ){

    		return( null );
    	}

    	JSONObject j_map = encodeToJSONObject( b_map );

    	return( JSONUtils.encodeToJSON( j_map ));
    }

    	/*
    	 * The following code is from Integer.java as we don't want to
    	 */
    final static byte[] digits = {
    	'0' , '1' , '2' , '3' , '4' , '5' ,
    	'6' , '7' , '8' , '9' , 'a' , 'b' ,
    	'c' , 'd' , 'e' , 'f' , 'g' , 'h' ,
    	'i' , 'j' , 'k' , 'l' , 'm' , 'n' ,
    	'o' , 'p' , 'q' , 'r' , 's' , 't' ,
    	'u' , 'v' , 'w' , 'x' , 'y' , 'z'
        };

    final static byte [] DigitTens = {
    	'0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
    	'1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
    	'2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
    	'3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
    	'4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
    	'5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
    	'6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
    	'7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
    	'8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
    	'9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
    	} ;

    final static byte [] DigitOnes = {
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    	} ;

    	/**
    	 * writes to int_buffer and returns start position in buffer (always runs to end of buffer)
    	 * @param i
    	 * @return
    	 */

    private int
    intToBytes(
    	int 	i )
    {
        int q, r;
        int charPos = 12;
        byte sign = 0;

        if (i < 0) {
            sign = '-';
            i = -i;
        }

        // Generate two digits per iteration
        while (i >= 65536) {
            q = i / 100;
        // really: r = i - (q * 100);
            r = i - ((q << 6) + (q << 5) + (q << 2));
            i = q;
            int_buffer [--charPos] = DigitOnes[r];
            int_buffer [--charPos] = DigitTens[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        for (;;) {
            q = (i * 52429) >>> (16+3);
            r = i - ((q << 3) + (q << 1));  // r = i-(q*10) ...
            int_buffer [--charPos] = digits [r];
            i = q;
            if (i == 0) break;
        }
        if (sign != 0) {
        	int_buffer [--charPos] = sign;
        }
        return charPos;
    }

    protected static class
    XMLEncoder
    	extends XUXmlWriter
    {
    	protected
    	XMLEncoder()
    	{
    	}

    	protected StringBuffer
    	encode(
    		Map		map,
    		boolean	simple )
    	{
    		StringWriter	writer = new StringWriter(1024);

    		setOutputWriter( writer );

    		setGenericSimple( simple );

    		writeGeneric( map );

    		flushOutputStream();

    		return( writer.getBuffer());
    	}
    }

    public static void
    main(
    	String[]	args )
    {
    	Map map = new HashMap();

    	map.put( "a", new Float(1.2));
    	map.put( "b", true );
    	map.put( "c", "fred".getBytes());

    	Map m2 = new HashMap();

    	m2.put( "boo", "meep" );
    	map.put( "m", m2 );

    	List l = new ArrayList();

    	l.add( "foo" );

    	map.put( "l", l );

    	System.out.println( encodeToJSON( map ));
    }
}
