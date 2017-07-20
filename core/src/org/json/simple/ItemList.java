/*
 * $Id: ItemList.java,v 1.2 2009-03-15 22:12:18 parg Exp $
 * Created on 2006-3-24
 */
package org.json.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * �����÷ָ����ֿ���һ��item.�ָ�������һ����һ��item.ÿ��item���߲����ǿհ׷�.
 * ���磺
 * |a:b:c| => |a|,|b|,|c|
 * |:| => ||,||
 * |a:| => |a|,||
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class ItemList {
	private final static String sp=",";
	List<String> items= new ArrayList<>();


	public ItemList(){}

	/**
	 *
	 * @param s �ָ���������һ���ַ�������
	 */
	public ItemList(String s){
		this.split(s,sp,items);
	}
	/**
	 *
	 * @param s �ָ���������һ���ַ�������
	 * @param sp �ָ���
	 */
	//public ItemList(String s,String sp){
	//	this.sp=s;
	//	this.split(s,sp,items);
	//}

	/**
	 *
	 * @param s
	 * @param sp
	 * @param isMultiToken sp�Ƿ�Ϊ��ָ���
	 */
	public ItemList(String s,String sp,boolean isMultiToken){
		split(s,sp,items,isMultiToken);
	}

	public List<String> getItems(){
		return this.items;
	}

	public String[] getArray(){
		return (String[])this.items.toArray(new String[items.size()]);
	}

	public void split(String s,String sp,List<String> append,boolean isMultiToken){
		if(s==null || sp==null)
			return;
		if(isMultiToken){
			StringTokenizer tokens=new StringTokenizer(s,sp);
			while(tokens.hasMoreTokens()){
				append.add(tokens.nextToken().trim());
			}
		}
		else{
			this.split(s,sp,append);
		}
	}

	public void split(String s,String sp,List<String> append){
		if(s==null || sp==null)
			return;
		int pos=0;
		int prevPos=0;
		do{
			prevPos=pos;
			pos=s.indexOf(sp,pos);
			if(pos==-1)
				break;
			append.add(s.substring(prevPos,pos).trim());
			pos+=sp.length();
		}while(pos!=-1);
		append.add(s.substring(prevPos).trim());
	}

	/**
	 * ���÷ָ���.
	 * @param sp �ָ���
	 */
	//public void setSP(String sp){
	//	this.sp=sp;
	//}

	/**
	 * ���뵥��item.
	 * @param i �����λ��(֮ǰ)
	 * @param item
	 */
	public void add(int i,String item){
		if(item==null)
			return;
		items.add(i,item.trim());
	}
	/**
	 * ���뵥��item.
	 * @param item
	 */
	public void add(String item){
		if(item==null)
			return;
		items.add(item.trim());
	}

	/**
	 * ��һ��item.
	 * @param list �����list
	 */
	public void addAll(ItemList list){
		items.addAll(list.items);
	}

	/**
	 * ��һ��item.
	 * @param s �ָ���������һ���ַ�������
	 */
	public void addAll(String s){
		this.split(s,sp,items);
	}

	/**
	 * ��һ��item.
	 * @param s �ָ���������һ���ַ�������
	 * @param sp �ָ���
	 */
	public void addAll(String s,String sp){
		this.split(s,sp,items);
	}

	public void addAll(String s,String sp,boolean isMultiToken){
		this.split(s,sp,items,isMultiToken);
	}

	/**
	 * ��õ�i��item. 0-based.
	 * @param i
	 * @return
	 */
	public String get(int i){
		return (String)items.get(i);
	}

	/**
	 * ���item��.
	 * @return
	 */
	public int size(){
		return items.size();
	}
	/**
	 * �÷ָ����ָ��ı�ʾ.
	 */
	public String toString(){
		return toString(sp);
	}

	/**
	 * �÷ָ����ָ��ı�ʾ.
	 * @param sp ����ø÷ָ����ָ�.
	 * @return
	 */
	public String toString(String sp){
		StringBuilder sb=new StringBuilder();

		for(int i=0;i<items.size();i++){
			if(i==0)
				sb.append(items.get(i));
			else{
				sb.append(sp);
				sb.append(items.get(i));
			}
		}
		return sb.toString();

	}

	/**
	 * �������item.
	 */
	public void clear(){
		items.clear();
	}

	/**
	 * ��λ.������ݣ����ָ�����Ĭ��ֵ.
	 */
	public void reset(){
		//sp=",";
		items.clear();
	}
}
