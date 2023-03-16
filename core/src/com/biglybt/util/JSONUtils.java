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
import java.util.*;

import org.gudy.bouncycastle.util.encoders.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.biglybt.core.util.Debug;


/**
 * @author TuxPaper
 * @created Feb 14, 2007
 *
 */
public class JSONUtils
{
	/**
	 * decodes JSON formatted text into a map.
	 *
	 * @return Map parsed from a JSON formatted string
	 * <p>
	 *  If the json text is not a map, a map with the key "value" will be returned.
	 *  the value of "value" will either be an List, String, Number, Boolean, or null
	 *  <p>
	 *  if the String is formatted badly, null is returned
	 */
	public static Map decodeJSON(String json) {
		try {
			Object object = JSONValue.parse(json);
			if (object instanceof Map) {
				return (Map) object;
			}
			// could be : ArrayList, String, Number, Boolean
			Map map = new HashMap();
			map.put("value", object);
			return map;
		} catch (Throwable t) {
			Debug.out("Warning: Bad JSON String: " + json + ": " + Debug.getNestedExceptionMessage(t));
			return null;
		}
	}

	/**
	 * encodes a map into a JSONObject.
	 * <P>
	 * It's recommended that you use {@link #encodeToJSON(Map)} instead
	 *
	 * @param map
	 * @return
	 *
	 * @since 3.0.1.5
	 */
	public static JSONObject encodeToJSONObject(Map map) {
		JSONObject newMap = new JSONObject((int)(map.size()*1.5));

		for (Map.Entry<String, Object> entry: ((Map<String,Object>)map).entrySet()){
			String key 		= entry.getKey();
			Object value	= entry.getValue();

			if (value instanceof byte[]) {
				key += ".B64";
				value = Base64.encode((byte[]) value);
			}

			value = coerce(value);

			newMap.put(key, value);
		}
		return newMap;
	}

	/**
	 * Encodes a map into a JSON formatted string.
	 * <p>
	 * Handles multiple layers of Maps and Lists.  Handls String, Number,
	 * Boolean, and null values.
	 *
	 * @param map Map to change into a JSON formatted string
	 * @return JSON formatted string
	 *
	 * @since 3.0.1.5
	 */
	public static String encodeToJSON(Map map) {
		JSONObject jobj = encodeToJSONObject(map);
		StringBuilder	sb = new StringBuilder(8192);
		jobj.toString( sb );
		return( sb.toString());
	}

	public static String encodeToJSON(Collection list) {
		return encodeToJSONArray(list).toString();
	}

	private static Object coerce(Object value) {
		if (value instanceof Map) {
			value = encodeToJSONObject((Map) value);
		} else if (value instanceof List) {
			value = encodeToJSONArray((List) value);
		} else if (value instanceof Object[]) {
			Object[] array = (Object[]) value;
			value = encodeToJSONArray(Arrays.asList(array));
		} else if (value instanceof byte[]) {
			try {
				value = new String((byte[]) value, "utf-8");
			} catch (UnsupportedEncodingException e) {
			}
		} else if (value instanceof boolean[]) {
			boolean[] array = (boolean[]) value;
			ArrayList<Object> list = new ArrayList<>();
			for (boolean b : array) {
				list.add(b);
			}
			value = encodeToJSONArray(list);
		} else if (value instanceof long[]) {
			long[] array = (long[]) value;
			ArrayList<Object> list = new ArrayList<>();
			for (long b : array) {
				list.add(b);
			}
			value = encodeToJSONArray(list);
		} else if (value instanceof int[]) {
			int[] array = (int[]) value;
			ArrayList<Object> list = new ArrayList<>();
			for (int b : array) {
				list.add(b);
			}
			value = encodeToJSONArray(list);
		}
		return value;
	}

	/**
	 * @param value
	 * @return
	 *
	 * @since 3.0.1.5
	 */
	private static JSONArray encodeToJSONArray(Collection list) {
		JSONArray newList = new JSONArray(list.size());

		for ( Object value: list ){

			newList.add(coerce(value));
		}

		return newList;
	}

	public static void main(String[] args) {

		Map mapBefore = new HashMap();
		byte[] b = {
			0,
			1,
			2
		};
		mapBefore.put("Hi", b);
		String jsonByteArray = JSONUtils.encodeToJSON(mapBefore);
		System.out.println(jsonByteArray);
		Map mapAfter = JSONUtils.decodeJSON(jsonByteArray);
		b = MapUtils.getMapByteArray(mapAfter, "Hi", null);
		System.out.println(b.length);
		for (int i = 0; i < b.length; i++) {
			byte c = b[i];
			System.out.println("--" + c);
		}

		Map map = new HashMap();
		map.put("Test", "TestValue");
		Map map2 = new HashMap();
		map2.put("Test2", "test2value");
		map.put("TestMap", map2);

		List list = new ArrayList();
		list.add(new Long(5));
		list.add("five");
		map2.put("ListTest", list);

		Map map3 = new HashMap();
		map3.put("Test3", "test3value");
		list.add(map3);

		System.out.println(encodeToJSON(map));
		System.out.println(encodeToJSON(list));
	}
}
