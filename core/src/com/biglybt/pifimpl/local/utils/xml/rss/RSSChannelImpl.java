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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.utils.xml.rss.RSSChannel;
import com.biglybt.pif.utils.xml.rss.RSSItem;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

/**
 * @author parg
 *
 */

public class
RSSChannelImpl
	implements RSSChannel
{
	final private SimpleXMLParserDocumentNode	node;

	final private RSSItem[]	items;

	final private boolean		is_atom;

	final private boolean		is_https;
	
	protected
	RSSChannelImpl(
		SimpleXMLParserDocumentNode	_node,
		boolean						_is_atom )
	{
		node	= _node;
		is_atom	= _is_atom;

		SimpleXMLParserDocumentNode[]	xml_items = node.getChildren();

		List	its = new ArrayList();

		for (int i=0;i<xml_items.length;i++){

			SimpleXMLParserDocumentNode	xml_item = xml_items[i];

			if ( xml_item.getName().equalsIgnoreCase(is_atom?"entry":"item")){

				its.add( new RSSItemImpl( this, xml_item, is_atom ));
			}
		}

		items	= new RSSItem[ its.size()];

		its.toArray( items );
		
		boolean	https = false;
		
		try{
			String base = getLinkRaw();
			
			if ( base.toLowerCase( Locale.US ).startsWith( "https" )){
				
				https = true;
			}
		}catch( Throwable e ){
		}
		
		is_https = https;
	}

	protected boolean
	isHTTPS()
	{
		return( is_https );
	}
	
	@Override
	public String
	getTitle()
	{
		return( node.getChild( "title" ).getValue());
	}

	@Override
	public String
	getDescription()
	{
		String[] fields;

		if ( is_atom ){

			fields = new String[]{ "summary", "description" };

		}else{

			fields = new String[]{ "description", "summary" };
		}

		for ( String field: fields ){

			SimpleXMLParserDocumentNode x = node.getChild( field );

			if ( x != null ){

				return( x.getValue());
			}
		}

		return( null );
	}

	@Override
	public URL
	getLink()
	{
		try{
			return( new URL( node.getChild("link").getValue()));

		}catch( MalformedURLException e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}
	
	protected String
	getLinkRaw()
	{
		return( node.getChild("link").getValue());
	}

	@Override
	public Date
	getPublicationDate()
	{
			// optional attribute

		SimpleXMLParserDocumentNode	pd = node.getChild( is_atom?"updated":"pubdate" );

		if ( pd == null ){

			return( null );
		}

		if ( is_atom ){

			return( RSSUtils.parseAtomDate( pd.getValue()));

		}else{

			return( RSSUtils.parseRSSDate( pd.getValue()));
		}
	}

	@Override
	public RSSItem[]
	getItems()
	{
		return( items );
	}

	@Override
	public SimpleXMLParserDocumentNode
	getNode()
	{
		return( node );
	}
}
