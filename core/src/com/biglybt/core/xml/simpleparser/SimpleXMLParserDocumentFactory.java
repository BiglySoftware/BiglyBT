/*
 * File    : SimpleXMLParserDocumentFactory.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.xml.simpleparser;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentException;
import com.biglybt.pifimpl.local.utils.xml.simpleparser.SimpleXMLParserDocumentImpl;

public class
SimpleXMLParserDocumentFactory
{
	public static SimpleXMLParserDocument
	create(
		File		file )

		throws SimpleXMLParserDocumentException
	{
		return( new SimpleXMLParserDocumentImpl( file ));
	}

	/**
	 * @deprecated
	 * @param is
	 * @return
	 * @throws SimpleXMLParserDocumentException
	 */
	public static SimpleXMLParserDocument
	create(
		InputStream		is )

		throws SimpleXMLParserDocumentException
	{
		return( new SimpleXMLParserDocumentImpl( null, is ));
	}

	public static SimpleXMLParserDocument
	create(
		URL				source_url,
		InputStream		is )

		throws SimpleXMLParserDocumentException
	{
		return( new SimpleXMLParserDocumentImpl( source_url, is ));
	}

	public static SimpleXMLParserDocument
	create(
		String		data )

		throws SimpleXMLParserDocumentException
	{
		return( new SimpleXMLParserDocumentImpl( data ));
	}
}
