/*
 * Created on 02-Jan-2005
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

package com.biglybt.pifimpl.local.utils.xml.rss;

import java.net.URL;
import java.util.Date;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.utils.xml.rss.RSSItem;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentAttribute;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

/**
 * @author parg
 *
 */

public class
RSSItemImpl
	implements RSSItem
{
	final private RSSChannelImpl					channel;
	final private boolean							is_atom;
	final private SimpleXMLParserDocumentNode		node;

	protected
	RSSItemImpl(
		RSSChannelImpl					_channel,
		SimpleXMLParserDocumentNode		_node,
		boolean							_is_atom )
	{
		channel	= _channel;
		is_atom	= _is_atom;
		node	= _node;
	}

	@Override
	public String
	getTitle()
	{
		if ( node.getChild( "title" ) != null ){

			return( node.getChild( "title" ).getValue());
		}

		return( null );
	}

	@Override
	public String
	getDescription()
	{
		if ( node.getChild( "description" ) != null ){

			return( node.getChild( "description" ).getValue());
		}

		return( null );
	}

	@Override
	public URL
	getLink()
	{
		SimpleXMLParserDocumentNode link_node = node.getChild( "link" );

		if ( link_node != null ){

			try{
				String value = "";

				if ( is_atom ){

					SimpleXMLParserDocumentAttribute attr = link_node.getAttribute( "href" );

					if ( attr == null ){

						return( null );
					}

					value = attr.getValue().trim();

				}else{

					value = link_node.getValue().trim();
				}

				if ( value.length() == 0 ){

					return( null );
				}

				if ( value.startsWith( "//" )){
					
					value = (channel.isHTTPS()?"https:":"http:" ) + value;
					
				}else if ( value.startsWith( "/" )){
					
					// relative URL
					
					String base = channel.getLinkRaw();
					
					if ( base.endsWith( "/" )){
						
						value = base + value.substring( 1 );
						
					}else{
						
						value = base + value;
					}
				}

				return( new URL( value ));

			}catch( Throwable e ){

				Debug.printStackTrace(e);

				return( null );
			}
		}

		return( null );
	}

	@Override
	public Date
	getPublicationDate()
	{
		SimpleXMLParserDocumentNode pd = node.getChild( is_atom?"published":"pubdate" );

		if ( pd != null ){

			if ( is_atom ){

				return( RSSUtils.parseAtomDate( pd.getValue()));

			}else{

				return( RSSUtils.parseRSSDate( pd.getValue()));
			}
		}

		return( null );
	}

	@Override
	public String
	getUID()
	{
		SimpleXMLParserDocumentNode uid = node.getChild( is_atom?"id":"guid" );

		if ( uid != null ){

			String value = uid.getValue().trim();

			if ( value.length() > 0 ){

				return( value );
			}
		}

		return( null );
	}

	@Override
	public SimpleXMLParserDocumentNode
	getNode()
	{
		return( node );
	}
}
