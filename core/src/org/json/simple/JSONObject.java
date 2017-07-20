/*
 * $Id: JSONObject.java,v 1.2 2008-08-07 01:18:54 parg Exp $
 * Created on 2006-4-10
 */
package org.json.simple;

import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.util.LightHashMap;

/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONObject extends LightHashMap<String,Object>{

	public JSONObject() {
		super();
	}

	public JSONObject(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	public JSONObject(int initialCapacity) {
		super(initialCapacity);
	}

	public JSONObject(Map<String,Object> arg0) {
		super(arg0);
	}

	public String toString(){
		ItemList list=new ItemList();
		Iterator<Map.Entry<String, Object>> iter=entrySet().iterator();

		while(iter.hasNext()){
			Map.Entry<String, Object> entry=iter.next();
			list.add(toString(entry.getKey().toString(),entry.getValue()));
		}
		return "{"+list.toString()+"}";
	}

	public void toString( StringBuilder sb ){

		sb.append( "{" );

		Iterator iter=entrySet().iterator();

		boolean first = true;

		while(iter.hasNext()){
			if ( first ){
				first = false;
			}else{
				sb.append( "," );
			}
			Map.Entry entry=(Map.Entry)iter.next();
			toString(sb, entry.getKey().toString(),entry.getValue());
		}

		sb.append( "}" );
	}

	public static String toString(String key,Object value){
		StringBuilder sb=new StringBuilder();

		sb.append("\"");
		sb.append(escape(key));
		sb.append("\":");
		if(value==null){
			sb.append("null");
			return sb.toString();
		}

		if(value instanceof String){
			sb.append("\"");
			sb.append(escape((String)value));
			sb.append("\"");
		}
		else
			sb.append(value);
		return sb.toString();
	}

	public static void toString(StringBuilder sb, String key,Object value){
		sb.append("\"");
		escape(sb,key);
		sb.append("\":");
		if(value==null){
			sb.append("null");
			return;
		}

		if(value instanceof String){
			sb.append("\"");
			escape(sb,(String)value);
			sb.append("\"");
		}else if ( value instanceof JSONObject ){
			((JSONObject)value).toString( sb );
		}else if ( value instanceof JSONArray ){
			((JSONArray)value).toString( sb );
		}else{
			sb.append(String.valueOf( value ));
		}
	}

	/**
	 * " => \" , \ => \\
	 * @param s
	 * @return
	 */
	public static String escape(String s){
		if(s==null)
			return null;
		StringBuilder sb=new StringBuilder();
		for(int i=0;i<s.length();i++){
			char ch=s.charAt(i);
			switch(ch){
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '/':
				sb.append("\\/");
				break;
			default:
				if(ch>='\u0000' && ch<='\u001F'){
					String ss=Integer.toHexString(ch);
					sb.append("\\u");
					for(int k=0;k<4-ss.length();k++){
						sb.append('0');
					}
					sb.append(ss.toUpperCase());
				}
				else{
					sb.append(ch);
				}
			}
		}//for
		return sb.toString();
	}

	public static void escape(StringBuilder sb, String s){
		if(s==null){
			sb.append((String)null);
		}else{
			for(int i=0;i<s.length();i++){
				char ch=s.charAt(i);
				switch(ch){
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\f':
					sb.append("\\f");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				case '/':
					sb.append("\\/");
					break;
				default:
					if(ch>='\u0000' && ch<='\u001F'){
						String ss=Integer.toHexString(ch);
						sb.append("\\u");
						for(int k=0;k<4-ss.length();k++){
							sb.append('0');
						}
						sb.append(ss.toUpperCase());
					}
					else{
						sb.append(ch);
					}
				}
			}//for
		}
	}
}
