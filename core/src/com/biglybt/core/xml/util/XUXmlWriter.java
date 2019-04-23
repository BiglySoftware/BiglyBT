/*
 * File    : XUXmlWriter.java
 * Created : 23-Oct-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.xml.util;

import java.io.*;
import java.util.*;

import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Constants;

public class
XUXmlWriter
{
	private static final int			INDENT_AMOUNT	= 4;

	private String						current_indent_string;

	private PrintWriter					writer;

	private boolean						generic_simple;

	protected
	XUXmlWriter()
	{
		resetIndent();
	}

	protected
	XUXmlWriter(
		OutputStream	_output_stream )
	{
		setOutputStream( _output_stream );

		resetIndent();
	}

	protected void
	setOutputStream(
		OutputStream	_output_stream )
	{
		writer = new PrintWriter(new OutputStreamWriter(_output_stream, Constants.DEFAULT_ENCODING_CHARSET));
	}

	protected void
	setOutputWriter(
		Writer		_writer )
	{
		if ( _writer instanceof PrintWriter ){

			writer = (PrintWriter)_writer;

		}else{

			writer = new PrintWriter( _writer );
		}
	}

	protected void
	setGenericSimple(
		boolean		simple )
	{
		generic_simple	= simple;
	}

	protected void
	writeTag(
		String		tag,
		String		content )
	{
		writeLineRaw( "<" + tag + ">" + escapeXML( content ) + "</" + tag + ">" );
	}

	protected void
	writeTag(
		String		tag,
		long		content )
	{
		writeLineRaw( "<" + tag + ">" + content + "</" + tag + ">" );
	}

	protected void
	writeTag(
		String		tag,
		boolean		content )
	{
		writeLineRaw( "<" + tag + ">" + (content?"YES":"NO") + "</" + tag + ">" );
	}

	protected void
	writeLineRaw(
		String	str )
	{
		writer.println( current_indent_string + str );
	}

	protected void
	writeLineEscaped(
		String	str )
	{
		writer.println( current_indent_string + escapeXML(str));
	}

	protected void
	resetIndent()
	{
		current_indent_string	= "";
	}

	protected void
	indent()
	{
		for (int i=0;i<INDENT_AMOUNT;i++){

			current_indent_string += " ";
		}
	}

	protected void
	exdent()
	{
		if ( current_indent_string.length() >= INDENT_AMOUNT ){

			current_indent_string = current_indent_string.substring(0,current_indent_string.length()-INDENT_AMOUNT);
		}else{

			current_indent_string	= "";
		}
	}

	public static String
	escapeXML(
		String	str )
	{
		if ( str == null ){

			return( "" );

		}
		str = str.replaceAll( "&", "&amp;" );
		str = str.replaceAll( ">", "&gt;" );
		str = str.replaceAll( "<", "&lt;" );
		str = str.replaceAll( "\"", "&quot;" );
		str = str.replaceAll( "'", "&apos;" );
		str = str.replaceAll( "--", "&#45;&#45;" );

		char[]	chars = str.toCharArray();

			// eliminate chars not supported by XML

		for ( int i=0;i<chars.length;i++){

			int	c = (int)chars[i];

			if (	( c <= 31 ) ||
					( c >= 127 && c <= 159 ) ||
					!Character.isDefined( c )){

				chars[i] = '?';
			}
		}

		return( new String( chars ));
	}

	public static String
	unescapeXML(
		String	str )
	{
		if ( str == null ){

			return( "" );

		}
		str = str.replaceAll( "&gt;", ">" );
		str = str.replaceAll( "&lt;", "<" );
		str = str.replaceAll( "&quot;", "\"" );
		str = str.replaceAll( "&apos;", "'" );
		str = str.replaceAll( "&#45;&#45;", "--" );
		str = str.replaceAll( "&amp;", "&" );

		return( str );
	}

	public static String[]
	splitWithEscape(
		String		str,
		char		delim )
	{
		List<String> res = new ArrayList<>();

		String	current = "";

		char[]	chars = str.toCharArray();

		for (int i=0;i<chars.length;i++){

			char c = chars[i];

			if ( c == '\\' && i+1<chars.length && chars[i+1] == delim ){

				current += delim;

				i++;

			}else if ( c == delim ){

				if ( current.length() > 0 ){

					res.add( current );

					current = "";
				}
			}else{

				current += c;
			}
		}

		if ( current.length() > 0 ){

			res.add( current );
		}

		return( res.toArray( new String[ res.size() ]));
	}

	protected void
	flushOutputStream()
	{
		if ( writer != null ){

			writer.flush();
		}
	}

	protected void
	closeOutputStream()
	{
		if ( writer != null ){

			writer.flush();

			writer.close();

			writer	= null;
		}
	}

		// generic Map encoder

	protected void
	writeGenericMapEntry(
		String	name,
		Object	value )
	{
		if ( generic_simple ){

			name = name.replace(' ', '_' ).toUpperCase();

			writeLineRaw( "<" + name + ">" );

			try{
				indent();

				writeGeneric( value );
			}finally{

				exdent();
			}

			writeLineRaw( "</" + name + ">" );

		}else{
			writeLineRaw( "<KEY name=\"" + escapeXML( name ) + "\">");

			try{
				indent();

				writeGeneric( value );
			}finally{

				exdent();
			}

			writeLineRaw( "</KEY>");
		}
	}

	protected void
	writeGeneric(
		Object	obj )
	{
		if ( obj instanceof Map ){

			writeGeneric((Map)obj);

		}else if( obj instanceof List ){

			writeGeneric((List)obj);

		}else if ( obj instanceof String ){

			writeGeneric((String)obj );

		}else if ( obj instanceof byte[] ){

			writeGeneric((byte[])obj);

		}else{

			writeGeneric((Long)obj);
		}
	}

	protected void
	writeGeneric(
		Map		map )
	{
		writeLineRaw( "<MAP>" );

		try{
			indent();

			Iterator it = map.keySet().iterator();

			while(it.hasNext()){

				String	key = (String)it.next();

				writeGenericMapEntry( key, map.get( key ));
			}
		}finally{

			exdent();
		}

		writeLineRaw( "</MAP>" );
	}

	protected void
	writeGeneric(
		List	list )
	{
		writeLineRaw( "<LIST>" );

		try{
			indent();

			for (int i=0;i<list.size();i++){

				writeGeneric( list.get(i));
			}
		}finally{

			exdent();
		}

		writeLineRaw( "</LIST>" );
	}

	protected void
	writeGeneric(
		byte[]		bytes )
	{
		if ( generic_simple ){

			writeLineRaw(escapeXML(new String(bytes, Constants.UTF_8)));
		}else{

			writeTag( "BYTES", encodeBytes( bytes ));
		}
	}

	protected void
	writeGeneric(
		String	str  )
	{
		if ( generic_simple ){

			try{
				writeLineRaw( escapeXML( str ));

			}catch( Throwable e ){

				e.printStackTrace();
			}
		}else{

			writeTag( "STRING", str );
		}
	}

	protected void
	writeGeneric(
		Long		l )
	{
		if ( generic_simple ){

			writeLineRaw( l.toString());

		}else{
			writeTag( "LONG", ""+l );
		}
	}

	protected void
	writeTag(
		String		tag,
		byte[]		content )
	{
		writeLineRaw( "<" + tag + ">" + encodeBytes( content ) + "</" + tag + ">" );
	}

	protected void
	writeLocalisableTag(
		String		tag,
		byte[]		content )
	{
		boolean	use_bytes = true;

		String utf_string = new String(content, Constants.DEFAULT_ENCODING_CHARSET);

		if (Arrays.equals(content, utf_string.getBytes(Constants.DEFAULT_ENCODING_CHARSET))) {
			use_bytes = false;
		}

		writeLineRaw( "<" + tag + " encoding=\""+(use_bytes?"bytes":"utf8") + "\">" +
					(use_bytes?encodeBytes( content ):escapeXML(utf_string)) + "</" + tag + ">" );
	}

	protected String
	encodeBytes(
		byte[]	bytes )
	{
		String data = ByteFormatter.nicePrint( bytes, true );

		return( data );

		/*
		try{

			return( URLEncoder.encode(new String( bytes, Constants.DEFAULT_ENCODING ), Constants.DEFAULT_ENCODING));

		}catch( UnsupportedEncodingException e ){

			throw( new TOTorrentException( 	"TOTorrentXMLSerialiser: unsupported encoding for '" + new String(bytes) + "'",
										TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
		*/
	}


}
