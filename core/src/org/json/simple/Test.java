/*
 * $Id: Test.java,v 1.1 2007-06-05 00:43:56 tuxpaper Exp $
 * Created on 2006-4-15
 */
package org.json.simple;


/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class Test {

	public static void main(String[] args) throws Exception{
		JSONArray array1=new JSONArray();
		array1.add("abc\u0010a/");
		array1.add(new Integer(123));
		array1.add(new Double(122.22));
		array1.add(Boolean.TRUE);
		System.out.println("======array1==========");
		System.out.println(array1);
		System.out.println();

		JSONObject obj1=new JSONObject();
		obj1.put("name","fang");
		obj1.put("age",new Integer(27));
		obj1.put("is_developer", Boolean.TRUE);
		obj1.put("weight",new Double(60.21));
		obj1.put("array1",array1);
		System.out.println();

		System.out.println("======obj1 with array1===========");
		System.out.println(obj1);
		System.out.println();

		obj1.remove("array1");
		array1.add(obj1);
		System.out.println("======array1 with obj1========");
		System.out.println(array1);
		System.out.println();

		System.out.println("======parse to java========");

		String s="[0,{\"1\":{\"2\":{\"3\":{\"4\":[5,{\"6\":7}]}}}}]";
		Object obj=JSONValue.parse(s);
		JSONArray array=(JSONArray)obj;
		System.out.println("======the 2nd element of array======");
		System.out.println(array.get(1));
		System.out.println();

		JSONObject obj2=(JSONObject)array.get(1);
		System.out.println("======field \"1\"==========");
		System.out.println(obj2.get("1"));
	}
}
