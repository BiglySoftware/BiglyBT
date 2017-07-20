/*
 * $Id: JSONParser.java,v 1.2 2008-08-07 01:18:55 parg Exp $
 * Created on 2006-4-15
 */
package org.json.simple.parser;

import java.io.Reader;
import java.util.Stack;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONParser {
	public static final int S_INIT=0;
	public static final int S_IN_FINISHED_VALUE=1;//string,number,boolean,null,object,array
	public static final int S_IN_OBJECT=2;
	public static final int S_IN_ARRAY=3;
	public static final int S_PASSED_PAIR_KEY=4;
	public static final int S_IN_ERROR=-1;

	private int peekStatus(Stack statusStack){
		if(statusStack.size()==0)
			return -1;
		Integer status=(Integer)statusStack.peek();
		return status.intValue();
	}

	public Object parse(Reader in) throws Exception{
		Stack statusStack=new Stack();
		Stack valueStack=new Stack();
		Yylex lexer=new Yylex(in);
		Yytoken token=null;
		int status=S_INIT;

		try{
			do{
				token=lexer.yylex();
				if(token==null)
					token=new Yytoken(Yytoken.TYPE_EOF,null);
				switch(status){
				case S_INIT:
					switch(token.type){
					case Yytoken.TYPE_VALUE:
						status=S_IN_FINISHED_VALUE;
						statusStack.push(new Integer(status));
						valueStack.push(token.value);
						break;
					case Yytoken.TYPE_LEFT_BRACE:
						status=S_IN_OBJECT;
						statusStack.push(new Integer(status));
						valueStack.push(new JSONObject());
						break;
					case Yytoken.TYPE_LEFT_SQUARE:
						status=S_IN_ARRAY;
						statusStack.push(new Integer(status));
						valueStack.push(new JSONArray());
						break;
					default:
						status=S_IN_ERROR;
					}//inner switch
					break;

				case S_IN_FINISHED_VALUE:
					if(token.type==Yytoken.TYPE_EOF)
						return valueStack.pop();
					else
						return null;

				case S_IN_OBJECT:
					switch(token.type){
					case Yytoken.TYPE_COMMA:
						break;
					case Yytoken.TYPE_VALUE:
						if(token.value instanceof String){
							String key=(String)token.value;
							valueStack.push(key);
							status=S_PASSED_PAIR_KEY;
							statusStack.push(new Integer(status));
						}
						else{
							status=S_IN_ERROR;
						}
						break;
					case Yytoken.TYPE_RIGHT_BRACE:
						if(valueStack.size()>1){
							statusStack.pop();
							JSONObject map = (JSONObject)valueStack.pop();
							map.compactify(-0.9f);
							status=peekStatus(statusStack);
						}
						else{
							status=S_IN_FINISHED_VALUE;
						}
						break;
					default:
						status=S_IN_ERROR;
						break;
					}//inner switch
					break;

				case S_PASSED_PAIR_KEY:
					switch(token.type){
					case Yytoken.TYPE_COLON:
						break;
					case Yytoken.TYPE_VALUE:
						statusStack.pop();
						String key=(String)valueStack.pop();
						JSONObject parent=(JSONObject)valueStack.peek();
						parent.put(key,token.value);
						status=peekStatus(statusStack);
						break;
					case Yytoken.TYPE_LEFT_SQUARE:
						statusStack.pop();
						key=(String)valueStack.pop();
						parent=(JSONObject)valueStack.peek();
						JSONArray newArray=new JSONArray();
						parent.put(key,newArray);
						status=S_IN_ARRAY;
						statusStack.push(new Integer(status));
						valueStack.push(newArray);
						break;
					case Yytoken.TYPE_LEFT_BRACE:
						statusStack.pop();
						key=(String)valueStack.pop();
						parent=(JSONObject)valueStack.peek();
						JSONObject newObject=new JSONObject();
						parent.put(key,newObject);
						status=S_IN_OBJECT;
						statusStack.push(new Integer(status));
						valueStack.push(newObject);
						break;
					default:
						status=S_IN_ERROR;
					}
					break;

				case S_IN_ARRAY:
					switch(token.type){
					case Yytoken.TYPE_COMMA:
						break;
					case Yytoken.TYPE_VALUE:
						JSONArray val=(JSONArray)valueStack.peek();
						val.add(token.value);
						break;
					case Yytoken.TYPE_RIGHT_SQUARE:
						if(valueStack.size()>1){
							statusStack.pop();
							valueStack.pop();
							status=peekStatus(statusStack);
						}
						else{
							status=S_IN_FINISHED_VALUE;
						}
						break;
					case Yytoken.TYPE_LEFT_BRACE:
						val=(JSONArray)valueStack.peek();
						JSONObject newObject=new JSONObject();
						val.add(newObject);
						status=S_IN_OBJECT;
						statusStack.push(new Integer(status));
						valueStack.push(newObject);
						break;
					case Yytoken.TYPE_LEFT_SQUARE:
						val=(JSONArray)valueStack.peek();
						JSONArray newArray=new JSONArray();
						val.add(newArray);
						status=S_IN_ARRAY;
						statusStack.push(new Integer(status));
						valueStack.push(newArray);
						break;
					default:
						status=S_IN_ERROR;
					}//inner switch
					break;
				case S_IN_ERROR:
					return null;
				}//switch
				if(status==S_IN_ERROR)
					return null;
			}while(token.type!=Yytoken.TYPE_EOF);
		}
		catch(Exception e){
			throw e;
		}
		return null;
	}
}
