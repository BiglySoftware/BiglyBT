/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.UrlUtils;
import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import org.json.simple.JSONArray;

/**
 * Various Map parsing utils.
 * <p/>
 * ImportExportUtils has been merged into this class (conflicting logic in
 * methods have been kept separate as importXxx and exportXxx)
 *
 * @author TuxPaper
 * @created Jun 1, 2007
 *
 */
public class MapUtils
{
	public static void setMapInt(Map map, String key, int val) {
		map.put( key, new Long( val ));
	}

	public static int getMapInt(Map map, String key, int def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if ( n == null ){

				return( def );
			}

			return n.intValue();
		} catch (Throwable e) {
			Debug.out(e);
			return def;
		}
	}

	public static long getMapLong(Map map, String key, long def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if ( n == null ){

				return( def );
			}

			return n.longValue();
		} catch (Throwable e) {
			Debug.out(e);
			return def;
		}
	}

	/**
	 * Retrieves a string value from the map, parsing byte arrays if needed.
	 * If the map is invalid, the key does not exist, or if the value is null,
	 * def is returned.
	 */
	public static String getMapString(Map map, String key, String def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o == null && !map.containsKey(key)) {
				return def;
			}
			// NOTE: The above returns def when map doesn't contain the key,
			//       which suggests below we would return the null when o is null.
			//       But we don't! And now, some callers rely on this :(

			if (o instanceof String) {
				return (String) o;
			}
			if (o instanceof byte[]) {
				return new String((byte[]) o, Constants.UTF_8);
			}
			return def;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static String[]
	getMapStringArray(
		Map			map,
		String		key,
		String[]	def )
	{
		Object o = map.get( key );
		if (!(o instanceof List)) {
			return def;
		}
		List list = (List) o;
		String[] result = new String[list.size()];
		for (int i=0;i<result.length;i++){
			result[i] = getString( list.get(i));
		}
		return( result );
	}

	public static String
	getString(
		Object	obj )
	{
		if ( obj instanceof String ){
			return((String)obj);
		}else if ( obj instanceof byte[]){

			try{
				return new String((byte[])obj, Constants.UTF_8);
			}catch( Throwable e ){

			}
		}
		return( null );
	}

	/**
	 * Puts a String value into a map as a UTF-8 byte array.  If value is null,
	 * removed key from map
	 */
	public static void setMapString(Map map, String key, String val ){
		if ( map == null ){
			Debug.out( "Map is null!" );
			return;
		}
		try{
			if ( val == null ){
				map.remove( key );
			}else{
				map.put( key, val.getBytes( Constants.UTF_8 ));
			}
		}catch( Throwable e ){
			Debug.out(e);
		}
	}

	public static byte[] getMapByteArray(Map map, String key, byte[] def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o instanceof byte[]) {
				return (byte[]) o;
			}

			String b64Key = key + ".B64";
			if (map.containsKey(b64Key)) {
				o = map.get(b64Key);
				if (o instanceof String) {
					return Base64.decode((String) o);
				}
			}

			String b32Key = key + ".B32";
			if (map.containsKey(b32Key)) {
				o = map.get(b32Key);
				if (o instanceof String) {
					return Base32.decode((String) o);
				}
			}

			return def;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static Object getMapObject(Map map, String key, Object def, Class cla) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (cla.isInstance(o)) {
				return o;
			} else {
				return def;
			}
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static void setMapBoolean(Map map, String key, boolean val) {
		map.put( key, new Long( val?1:0 ));
	}
		
	public static boolean getMapBoolean(Map map, String key, boolean def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o instanceof Boolean) {
				return ((Boolean) o).booleanValue();
			}

			if (o instanceof Number) {
				return ((Number) o).longValue() == 1;
			}

			return def;
		} catch (Throwable e) {
			Debug.out(e);
			return def;
		}
	}

	public static List getMapList(Map map, String key, List def) {
		if (map == null) {
			return def;
		}
		try {
			List list = (List) map.get(key);
			if (list == null && !map.containsKey(key)) {
				return def;
			}
			return list;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static Map getMapMap(Map map, String key, Map def) {
		if (map == null) {
			return def;
		}
		try {
			Map valMap = (Map) map.get(key);
			if (valMap == null && !map.containsKey(key)) {
				return def;
			}
			return valMap;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	/**
	 * Retrieve a long from a map, parsing string values if needed
	 */
	public static long
	importLong(
		Map		map,
		String	key,
		long	def )
	{
		if ( map == null ){

			return( def );
		}

		Object	obj = map.get( key );

		if ( obj instanceof Long){

			return(((Long)obj).longValue());

		}else if ( obj instanceof String ){

			return( Long.parseLong((String)obj));
		}

		return( def );
	}

	public static void
	exportLong(
		Map		map,
		String	key,
		long	value )
	{
		map.put( key, value );
	}

	public static void
	exportInt(
		Map		map,
		String	key,
		int		value )
	{
		map.put( key, new Long( value ));
	}

	public static int
	importInt(
		Map		map,
		String	key,
		int		def )

	{
		return((int)importLong( map, key, def ));
	}

	public static void
	exportFloatAsString(
		Map		map,
		String	key,
		float	value )
	{
		setMapString(map, key, String.valueOf(value));
	}

	public static float
	importFloatFromString(
		Map		map,
		String	key,
		float	def )
	{
		String	str = getMapString( map, key, null );

		if ( str == null ){

			return( def );
		}

		return( Float.parseFloat( str ));
	}

	public static void
	exportBooleanAsLong(
		Map		map,
		String	key,
		boolean	value )
	{
		map.put( key, new Long( value?1:0 ));
	}

	public static String
	importURL(
		Map		map,
		String	key )
	{
		String url = getMapString( map, key, null );

		if ( url != null ){

			url = url.trim();

			if ( url.length() == 0 ){

				url = null;

			}else{

				try{
					url = URLDecoder.decode( url, "UTF-8" );

				}catch( UnsupportedEncodingException e ){

					e.printStackTrace();
				}
			}
		}

		return( url );
	}

	public static void
	exportJSONURL(
		Map		map,
		String	key,
		String	value )
	{
		map.put(key, UrlUtils.encode( value ));
	}

	public static String[]
	importStringArray(
		Map		map,
		String	key )
	{
		List	list = (List)map.get( key );

		if ( list == null ){

			return( new String[0] );
		}

		String[]	res = new String[list.size()];

		for (int i=0;i<res.length;i++){

			Object obj = list.get(i);

			if ( obj instanceof String ){

				res[i] = (String)obj;

			}else if ( obj instanceof byte[] ){

				res[i] = new String((byte[])obj, Constants.UTF_8 );
			}
		}

		return( res );
	}

	public static void
	exportStringArray(
		Map			map,
		String		key,
		String[]	data )
	{
		List	l = new ArrayList(data.length);

		map.put( key, l );

		for (int i=0;i<data.length;i++){

			l.add( data[i].getBytes( Constants.UTF_8 ));
		}
	}

	public static void
	exportJSONStringArray(
		Map			map,
		String		key,
		String[]	data )
	{
		List	l = new JSONArray(data.length);

		map.put( key, l );

		Collections.addAll(l, data);
	}

	public static void
	exportIntArrayAsByteArray(
		Map			map,
		String		key,
		int[]		values )
	{
		if ( values == null ){

			return;
		}

		int	num = values.length;

		byte[]	bytes 	= new byte[num*4];
		int		pos		= 0;

		for (int i=0;i<num;i++){

			int	v = values[i];

		    bytes[pos++] = (byte)(v >>> 24);
		    bytes[pos++] = (byte)(v >>> 16);
		    bytes[pos++] = (byte)(v >>> 8);
		    bytes[pos++] = (byte)(v);
		}

		map.put( key, bytes );
	}

	public static int[]
	importIntArray(
		Map			map,
		String		key )
	{
		byte[]	bytes = (byte[])map.get( key );

		if ( bytes == null ){

			return( null );
		}

		int[]	values = new int[bytes.length/4];

		int	pos = 0;

		for (int i=0;i<values.length;i++){

			values[i] =
				((bytes[pos++]&0xff) << 24) +
				((bytes[pos++]&0xff) << 16) +
				((bytes[pos++]&0xff) << 8) +
				((bytes[pos++]&0xff));
		}

		return( values );
	}
	
	public static String
	importString(
		Object		o )
	{
		if ( o == null ){
			
			return( null );
		}
		
		if (o instanceof String){
			return (String) o;
		}else  if (o instanceof byte[]){
			return new String((byte[]) o, Constants.UTF_8);
		}else{
			Debug.out( "unsupported: " + o );
			return( null );
		}
	}
}
