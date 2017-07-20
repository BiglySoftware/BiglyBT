**** LICENSING NOTE FROM PARG ****

The version included within Vuze in LGPL version 2.1 and is an old version of the software available here:

https://code.google.com/p/json-simple/downloads/detail?name=json_simple.zip&can=2&q=

(archived here: https://web.archive.org/web/20140328054522/https://json-simple.googlecode.com/files/json_simple.zip )

The license was subsequently updated to Apache but the Vuze copy remains LGPL.

***********************************


Simple Java toolkit for JSON (JSON.simple)
==========================================

1.Why the Simple Java toolkit (also named as JSON.simple) for JSON?
  
  When I use JSON as the data exchange format between the AJAX client and JSP 
  for the first time, what worry me mostly is how to encode Java strings and 
  numbers correctly in the server side so the AJAX client will receive a well
  formed JSON data. When I looked into the 'JSON in Java' directory in JSON
  website,I found that wrappers to JSONObject and JSONArray can be simpler, 
  due to the simplicity of JSON itself. So I wrote the JSON.simple package.

2.Is it simple,really?
  
  I think so. Take an example:

  import org.json.simple.JSONObject;

  JSONObject obj=new JSONObject();
  obj.put("name","foo");
  obj.put("num",new Integer(100));
  obj.put("balance",new Double(1000.21));
  obj.put("is_vip",new Boolean(true));
  obj.put("nickname",null);
  System.out.print(obj);

  Result:
  {"nickname":null,"num":100,"balance":1000.21,"is_vip":true,"name":"foo"}

  The JSONObject.toString() will escape controls and specials correctly.

3.How to use JSON.simple in JSP?

  Take an example in JSP:
  
  <%@page contentType="text/html; charset=UTF-8"%>
  <%@page import="org.json.simple.JSONObject"%>
  <%
	JSONObject obj=new JSONObject();
  	obj.put("name","foo");
  	obj.put("num",new Integer(100));
  	obj.put("balance",new Double(1000.21));
  	obj.put("is_vip",new Boolean(true));
  	obj.put("nickname",null);
	out.print(obj);
	out.flush();
  %>
  
  So the AJAX client will get the responseText.

4.Some details about JSONObject?
  
  JSONObject inherits java.util.HashMap,so it don't have to worry about the 
  mapping things between keys and values. Feel free to use the Map methods 
  like get(), put(), and remove() and others. JSONObject.toString() will 
  combine key value pairs to get the JSON data string. Values will be escaped
  into JSON quote string format if it's an instance of java.lang.String. Other
  type of instance like java.lang.Number,java.lang.Boolean,null,JSONObject and
  JSONArray will NOT escape, just take their java.lang.String.valueOf() result.
  null value will be the JSON 'null' in the result.

  It's still correct if you put an instance of JSONObject or JSONArray into an 
  instance of JSONObject or JSONArray. Take the example about:

  JSONObject obj2=new JSONObject();
  obj2.put("phone","123456");
  obj2.put("zip","7890");
  obj.put("contact",obj2);
  System.out.print(obj);

  Result:
  {"nickname":null,"num":100,"contact":{"phone":"123456","zip":"7890"},"balance":1000.21,"is_vip":true,"name":"foo"}

  The method JSONObject.escape() is used to escape Java string into JSON quote 
  string. Controls and specials will be escaped correctly into \b,\f,\r,\n,\t,
  \",\\,\/,\uhhhh.

5.Some detail about JSONArray?
  
  org.json.simple.JSONArray inherits java.util.ArrayList. Feel free to use the
  List methods like get(),add(),remove(),iterator() and so on. The rules of 
  JSONArray.toString() is similar to JSONObject.toString(). Here's the example:

  import org.json.simple.JSONArray;

  JSONArray array=new JSONArray();
  array.add("hello");
  array.add(new Integer(123));
  array.add(new Boolean(false));
  array.add(null);
  array.add(new Double(123.45));
  array.add(obj2);//see above
  System.out.print(array);

  Result:
  ["hello",123,false,null,123.45,{"phone":"123456","zip":"7890"}]

6.What is JSONValue for?  

  org.json.simple.JSONValue is use to parse JSON data into Java Object. 
  In JSON, the topmost entity is JSON value, not the JSON object. But
  it's not necessary to wrap JSON string,boolean,number and null again,
  for the Java has already had the according classes: java.lang.String,
  java.lang.Boolean,java.lang.Number and null. The mapping is:

  JSON			Java
  ------------------------------------------------
  string      <=>  	java.lang.String 
  number      <=>	java.lang.Number
  true|false  <=>	java.lang.Boolean
  null        <=>	null
  array	      <=>	org.json.simple.JSONArray
  object      <=>       org.json.simple.JSONObject
  ------------------------------------------------

  JSONValue has only one kind of method, JSONValue.parse(), which receives
  a java.io.Reader or java.lang.String. Return type of JSONValue.parse() 
  is according to the mapping above. If the input is incorrect in syntax or
  there's exceptions during the parsing, I choose to return null, ignoring 
  the exception: I have no idea if it's a serious implementaion, but I think
  it's convenient to the user.
  
  Here's the example:
  
  String s="[0,{\"1\":{\"2\":{\"3\":{\"4\":[5,{\"6\":7}]}}}}]";
  Object obj=JSONValue.parse(s);
  JSONArray array=(JSONArray)obj;
  System.out.println(array.get(1));
  JSONObject obj2=(JSONObject)array.get(1);
  System.out.println(obj2.get("1"));

  Result:
  {"1":{"2":{"3":{"4":[5,{"6":7}]}}}}
  {"2":{"3":{"4":[5,{"6":7}]}}}

7.About the author.

  I'm a Java EE developer on Linux. 
  I'm working on web systems and information retrieval systems.
  I also develop 3D games and Flash games. 

  You can contact me through: 
  Fang Yidong<fangyidong@yahoo.com.cn>
  Fang Yidong<fangyidng@gmail.com> 
