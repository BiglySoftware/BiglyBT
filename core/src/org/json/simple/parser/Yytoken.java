/*
 * $Id: Yytoken.java,v 1.1 2007-06-05 00:43:56 tuxpaper Exp $
 * Created on 2006-4-15
 */
package org.json.simple.parser;

/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class Yytoken {
	public static final int TYPE_VALUE=0;//JSON primitive value: string,number,boolean,null
	public static final int TYPE_LEFT_BRACE=1;
	public static final int TYPE_RIGHT_BRACE=2;
	public static final int TYPE_LEFT_SQUARE=3;
	public static final int TYPE_RIGHT_SQUARE=4;
	public static final int TYPE_COMMA=5;
	public static final int TYPE_COLON=6;
	public static final int TYPE_EOF=-1;//end of file

	public int type=0;
	public Object value=null;

	public Yytoken(int type,Object value){
		this.type=type;
		this.value=value;
	}

	public String toString(){
		return String.valueOf(type+"=>|"+value+"|");
	}
}
