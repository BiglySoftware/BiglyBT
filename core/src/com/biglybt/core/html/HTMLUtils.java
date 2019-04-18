/*
 * Created on 30-Nov-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.core.html;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.xml.util.XUXmlWriter;

/**
 * @author parg
 *
 */
public class
HTMLUtils
{

		/**
		 * returns a list of strings for each line in a basic text representation
		 * @param indent
		 * @param text
		 * @return
		 */

	public static List
	convertHTMLToText(
		String		indent,
		String		text )
	{
		int		pos = 0;

		text = text.replaceAll("<ol>","");
		text = text.replaceAll("</ol>","");
		text = text.replaceAll("<ul>","");
		text = text.replaceAll("</ul>","");
		text = text.replaceAll("</li>","");
		text = text.replaceAll("<li>","\n\t*");

		String lc_text = text.toLowerCase( MessageText.LOCALE_ENGLISH );

		List	lines = new ArrayList();

		while( true ){

			String	line;

			String[]	tokens = new String[]{ "<br>", "<p>" };

			String	token 	= null;
			int		p1		= -1;

			for (int i=0;i<tokens.length;i++){

				int	x = lc_text.indexOf( tokens[i], pos );

				if ( x != -1 ){
					if ( p1 == -1 || x < p1 ){
						token	= tokens[i];
						p1		= x;
					}
				}
			}

			if ( p1 == -1 ){

				line = text.substring(pos);

			}else{

				line = text.substring(pos,p1);

				pos = p1+token.length();
			}

			lines.add( indent + line );

			if ( p1 == -1 ){

				break;
			}
		}

		return( lines );
	}

	public static String convertListToString(List list) {

	  StringBuilder result = new StringBuilder();
	  String separator = "";
	  Iterator iter = list.iterator();
	  while(iter.hasNext()) {
	    String line = iter.next().toString();
	    result.append(separator);
	    result.append(line);
	    separator = "\n";
	  }

	  return result.toString();
	}

	public static String
	convertHTMLToText2(
		String		content )
	{
		int	pos	= 0;

		String	res = "";

		content = removeTagPairs( content, "script" );

		content = content.replaceAll( "&nbsp;", " " );

		content = content.replaceAll( "[\\s]+", " " );

		while(true){

			int	p1 = content.indexOf( "<",  pos );

			if ( p1 == -1 ){

				res += content.substring(pos);

				break;
			}

			int	p2 = content.indexOf( ">", p1 );

			if ( p2 == -1 ){

				res += content.substring(pos);

				break;
			}

			String	tag = content.substring(p1+1,p2).toLowerCase( MessageText.LOCALE_ENGLISH );

			res += content.substring(pos,p1);

			if ( tag.equals("p") || tag.equals("br")){

				if ( res.length() > 0 && res.charAt(res.length()-1) != '\n' ){

					res += "\n";
				}
			}

			pos	= p2+1;
		}

		res = res.replaceAll( "[ \\t\\x0B\\f\\r]+", " " );
		res = res.replaceAll( "[ \\t\\x0B\\f\\r]+\\n", "\n" );
		res = res.replaceAll( "\\n[ \\t\\x0B\\f\\r]+", "\n" );

		if ( res.length() > 0 && Character.isWhitespace(res.charAt(0))){

			res = res.substring(1);
		}

		return( res );
	}

	public static String
	splitWithLineLength(
		String		str,
		int			length )
	{
		String	res = "";

		StringTokenizer tok = new StringTokenizer(str, "\n");

		while( tok.hasMoreTokens()){

			String	line = tok.nextToken();

			while( line.length() > length ){

				if ( res.length() > 0 ){

					res += "\n";
				}

				boolean	done = false;

				for (int i=length-1;i>=0;i--){

					if ( Character.isWhitespace( line.charAt(i))){

						done	= true;

						res += line.substring(0,i);

						line = line.substring(i+1);

						break;
					}
				}

				if ( !done ){

					res += line.substring(0,length);

					line = line.substring( length );
				}
			}

			if ( res.length() > 0 && line.length() > 0 ){

				res += "\n";

				res += line;
			}
		}

		return( res );
	}

	public static String
	removeTagPairs(
		String	content,
		String	tag_name )
	{
		tag_name = tag_name.toLowerCase( MessageText.LOCALE_ENGLISH );

		String	lc_content = content.toLowerCase( MessageText.LOCALE_ENGLISH );

		int	pos	= 0;

		String	res = "";

		int	level 		= 0;
		int	start_pos	= -1;

		while(true){

			int	start_tag_start = lc_content.indexOf( "<" + tag_name,  pos );
			int end_tag_start	= lc_content.indexOf( "</" + tag_name, pos );

			if ( level == 0 ){

				if ( start_tag_start == -1 ){

					res += content.substring(pos);

					break;
				}

				res += content.substring(pos,start_tag_start);

				start_pos = start_tag_start;

				level	= 1;

				pos		= start_pos+1;

			}else{

				if ( end_tag_start == -1 ){

					res += content.substring(pos);

					break;
				}

				if ( start_tag_start == -1 || end_tag_start < start_tag_start ){

					level--;

					int	end_end = lc_content.indexOf( '>', end_tag_start );

					if( end_end == -1 ){

						break;
					}

					pos	= end_end + 1;

				}else{

					level++;

					pos = start_tag_start+1;
				}
			}
		}

		return( res );
	}

	public static Object[]
	getLinks(
		String	content_in )
	{
		int	pos	= 0;

		List	urls = new ArrayList();

		String	content_out = "";

		String	current_url				= null;
		int		current_url_start		= -1;

		while(true){

			int	p1 = content_in.indexOf( "<", pos );

			if ( p1 == -1 ){

				break;
			}

			p1++;

			int	p2 = content_in.indexOf( ">", p1 );

			if ( p2 == -1 ){

				break;
			}

			if ( p1 > pos ){

				content_out += content_in.substring( pos, p1-1 );
			}

			pos	= p2+1;

			String	tag 	= content_in.substring( p1, p2 ).trim();

			String	lc_tag 	= tag.toLowerCase( MessageText.LOCALE_ENGLISH );

			if ( lc_tag.startsWith("a " )){

				int	hr_start = lc_tag.indexOf( "href");

				if ( hr_start == -1 ){

					continue;
				}

				hr_start = lc_tag.indexOf("=", hr_start);

				if ( hr_start == -1 ){

					continue;
				}

				hr_start += 1;

				while( 	hr_start < lc_tag.length() &&
						Character.isWhitespace(lc_tag.charAt(hr_start))){

					hr_start++;
				}

				int hr_end = lc_tag.length()-1;

				while(	hr_end >= lc_tag.length() &&
						Character.isWhitespace(lc_tag.charAt(hr_end))){

					hr_end--;
				}

				String	href = tag.substring(hr_start, hr_end+1 ).trim();

				if ( href.startsWith("\"")){

					int endQuotePos = href.indexOf('\"', 1);
					if (endQuotePos == -1) {
						href = href.substring(1,href.length()-1);
					} else {
						href = href.substring(1,endQuotePos);
					}
				}

				current_url = href;

				current_url_start = content_out.length();

			}else if ( lc_tag.startsWith( "/" ) && lc_tag.substring(1).trim().equals( "a" )){

				if ( current_url != null ){

					int	len = content_out.length() - current_url_start;

					urls.add( new Object[]{ current_url, new int[]{ current_url_start, len }});
				}

				current_url = null;
			}
		}

		if ( pos < content_in.length()){

			content_out += content_in.substring( pos );
		}

		return( new Object[]{ content_out, urls });
	}

	public static String
	expand(
		String		str )
	{
		str = XUXmlWriter.unescapeXML( str );

		str = str.replaceAll( "&nbsp;", " " );

		return( str );
	}

	public static void
	main(
		String[]	args )
	{
		Object[] obj = getLinks( "aaaaaaa <a href=\"http://here/parp  \">link< / a > prute <a href=\"http://here/pa\">klink</a>" );

		System.out.println( obj[0] );

		List	urls = (List)obj[1];

		for (int i=0;i<urls.size();i++){

			Object[]	entry = (Object[])urls.get(i);

			System.out.println( "    " + entry[0] + ((int[])entry[1])[0] + "," + ((int[])entry[1])[1] );
		}
	}

	public static String toColorHexString(int r, int g, int b, int a) {
		// left 0 padding is done by adding a bit beyond the MSB, and then
		// chopping off the extra first char
		long l = r << 16 | g << 8 | b;
		if (a != 255) {
			l |= a << 24;
			l |= 1L << 32;
		} else {
			l |= 1L << 24;
		}
		String s = Long.toHexString(l).toUpperCase();
		return s.substring(1);
	}
}
