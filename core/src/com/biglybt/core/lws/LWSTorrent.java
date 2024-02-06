/*
 * Created on Jul 16, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.lws;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.torrent.*;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.TorrentUtils;


public class
LWSTorrent
	extends LogRelation
	implements TOTorrent
{
	private static final TOTorrentAnnounceURLGroup announce_group =
		new TOTorrentAnnounceURLGroup()
		{
			private final long uid = TorrentUtils.getAnnounceGroupUID();
			
			private TOTorrentAnnounceURLSet[]	sets = new TOTorrentAnnounceURLSet[0];

			@Override
			public long 
			getUID()
			{
				return( uid );
			}
			
			@Override
			public TOTorrentAnnounceURLSet[]
           	getAnnounceURLSets()
			{
				return( sets );
			}

           	@Override
            public void
           	setAnnounceURLSets(
           		TOTorrentAnnounceURLSet[]	_sets )
           	{
           		sets	= _sets;
           	}

           	@Override
            public TOTorrentAnnounceURLSet
           	createAnnounceURLSet(
           		final URL[]	_urls )
           	{
           		return(
           			new TOTorrentAnnounceURLSet()
           			{
           				private URL[] urls = _urls;

           				@Override
			            public URL[]
           				getAnnounceURLs()
           				{
           					return( urls );
           				}

           				@Override
			            public void
           				setAnnounceURLs(
           					URL[]		_urls )
           				{
           					urls = _urls;
           				}
           			});
           	}
		};

	private static void
	notSupported()
	{
		Debug.out( "Not Supported" );
	}

	private final LightWeightSeed		lws;



	protected
	LWSTorrent(
		LightWeightSeed		_lws )
	{
		lws				= _lws;
	}

	@Override
	public int 
	getTorrentType()
	{
		return( TT_V1 );
	}
	
	@Override
	public boolean 
	isExportable()
	{
		return( true );
	}
	
	@Override
	public boolean
	updateExportability(
		TOTorrent		from )
	{
		return( true );
	}
	
	protected TOTorrent
	getDelegate()
	{
		return( lws.getTOTorrent( true ));
	}

	@Override
	public byte[]
	getName()
	{
		return( lws.getName().getBytes());
	}

	@Override
	public String getUTF8Name() {
		return lws.getName();
	}

	@Override
	public boolean
	isSimpleTorrent()
	{
		return( getDelegate().isSimpleTorrent());
	}

	@Override
	public byte[]
	getComment()
	{
	 	return( getDelegate().getComment());
	}

	@Override
	public void
	setComment(
		String		comment )
	{
		getDelegate().setComment(comment);
	}

	@Override
	public long
	getCreationDate()
	{
		return( getDelegate().getCreationDate());
	}

	@Override
	public boolean
	isDecentralised()
	{
		return( getDelegate().isDecentralised());
	}

	@Override
	public void
	setCreationDate(
		long		date )
	{
		getDelegate().setCreationDate(date);
	}

	@Override
	public byte[]
	getCreatedBy()
	{
		return( getDelegate().getCreatedBy());
	}

  	@Override
	  public void
	setCreatedBy(
		byte[]		cb )
   	{
  		getDelegate().setCreatedBy( cb );
   	}

	@Override
	public boolean
	isCreated()
	{
		return( true );
	}

	@Override
	public URL
	getAnnounceURL()
	{
		return( lws.getAnnounceURL());
	}

	@Override
	public boolean
	setAnnounceURL(
		URL		url )
	{
		notSupported();

		return( false );
	}

	@Override
	public TOTorrentAnnounceURLGroup
	getAnnounceURLGroup()
	{
		return( announce_group );
	}

	@Override
	public byte[][]
	getPieces()

		throws TOTorrentException
	{
		return( getDelegate().getPieces());
	}


	@Override
	public void
	setPieces(
		byte[][]	pieces )

		throws TOTorrentException
	{
		getDelegate().setPieces(pieces);
	}

	@Override
	public long
	getPieceLength()
	{
		return( getDelegate().getPieceLength());
	}

	@Override
	public int
	getNumberOfPieces()
	{
		return( getDelegate().getNumberOfPieces());
	}

	@Override
	public long
	getSize()
	{
		return( lws.getSize());
	}

	@Override
	public int
	getFileCount()
	{
		return( getDelegate().getFileCount());
	}

	@Override
	public TOTorrentFile[]
	getFiles()
	{
		return( getDelegate().getFiles());
	}

	@Override
	public byte[]
	getHash()

		throws TOTorrentException
	{
		return( lws.getHash().getBytes());
	}

	@Override
	public byte[]
	getFullHash(
		int	type )
	
		throws TOTorrentException
	{
		if ( type == TT_V1 ){
			return( getHash());
		}else{
			return( null );
		}
	}
	
	@Override
	public HashWrapper
	getHashWrapper()

		throws TOTorrentException
	{
		return( lws.getHash());
	}
	
	@Override
	public TOTorrent 
	selectHybridHashType(
		int type ) 
		
		throws TOTorrentException
	{
		throw( new TOTorrentException( "Not supported", TOTorrentException.RT_CREATE_FAILED ));
	}
	
   	@Override
	public TOTorrent
	setSimpleTorrentDisabled(
		boolean	disabled )
	
		throws TOTorrentException
	{
   		throw( new TOTorrentException( "Not supported", TOTorrentException.RT_CREATE_FAILED ));
	}
	
	@Override
	public boolean
	isSimpleTorrentDisabled()
	
		throws TOTorrentException
	{
		throw( new TOTorrentException( "Not supported", TOTorrentException.RT_CREATE_FAILED ));
	}
	
   	@Override
    public void
	setHashOverride(
		byte[] hash )

		throws TOTorrentException
	{
		throw( new TOTorrentException( "Not supported", TOTorrentException.RT_HASH_FAILS ));
	}

	@Override
	public boolean
	hasSameHashAs(
		TOTorrent		other )
	{
		try{
			byte[]	other_hash = other.getHash();

			return( Arrays.equals( getHash(), other_hash ));

		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );

			return( false );
		}
		}

	@Override
	public boolean
	getPrivate()
	{
		return( false );
	}

	@Override
	public void
	setPrivate(
		boolean	_private )

		throws TOTorrentException
	{
		notSupported();
	}

   	@Override
    public String
	getSource()
   	{
 		return( getDelegate().getSource());
   	}

	@Override
    public void
	setSource(
		String	str )
	
		throws TOTorrentException
   	{
		getDelegate().setSource( str );
	}
	
	@Override
	public void
	setAdditionalStringProperty(
		String		name,
		String		value )
	{
		getDelegate().setAdditionalStringProperty(name, value);
	}

	@Override
	public String
	getAdditionalStringProperty(
		String		name )
	{
		return( getDelegate().getAdditionalStringProperty( name ));
	}

	@Override
	public void
	setAdditionalByteArrayProperty(
		String		name,
		byte[]		value )
	{
		getDelegate().setAdditionalByteArrayProperty(name, value);
	}

	@Override
	public byte[]
	getAdditionalByteArrayProperty(
		String		name )
	{
		return( getDelegate().getAdditionalByteArrayProperty( name ));
	}

	@Override
	public void
	setAdditionalLongProperty(
		String		name,
		Long		value )
	{
		getDelegate().setAdditionalLongProperty(name, value);
	}

	@Override
	public Long
	getAdditionalLongProperty(
		String		name )
	{
		return( getDelegate().getAdditionalLongProperty( name ));
	}


	@Override
	public void
	setAdditionalListProperty(
		String		name,
		List		value )
	{
		getDelegate().setAdditionalListProperty(name, value);
	}

	@Override
	public List
	getAdditionalListProperty(
		String		name )
	{
		return( getDelegate().getAdditionalListProperty( name ));
	}

	@Override
	public void
	setAdditionalMapProperty(
		String		name,
		Map			value )
	{
		getDelegate().setAdditionalMapProperty(name, value);
	}

	@Override
	public Map
	getAdditionalMapProperty(
			String		name )
	{
		return( getDelegate().getAdditionalMapProperty( name ));
	}

	@Override
	public Object
	getAdditionalProperty(
		String		name )
	{
		if ( name.equals( "url-list" ) || name.equals( "httpseeds" )){

			return( null );
		}

		return( getDelegate().getAdditionalProperty( name ));
	}

	@Override
	public void
	setAdditionalProperty(
		String		name,
		Object		value )
	{
		getDelegate().setAdditionalProperty(name, value);
	}

	@Override
	public void
	removeAdditionalProperty(
		String name )
	{
		getDelegate().removeAdditionalProperty(name);
	}

	@Override
	public void
	removeAdditionalProperties()
	{
		getDelegate().removeAdditionalProperties();
	}

	@Override
	public void
	serialiseToBEncodedFile(
		File		file )

		throws TOTorrentException
	{
		getDelegate().serialiseToBEncodedFile( file );
	}

	@Override
	public void
	addListener(
		TOTorrentListener		l )
	{
		getDelegate().addListener( l );
	}

	@Override
	public void
	removeListener(
		TOTorrentListener		l )
	{
		getDelegate().removeListener( l );
	}

	@Override
	public Map
	serialiseToMap()

		throws TOTorrentException
	{
		return( getDelegate().serialiseToMap());
	}

	@Override
	public void
	serialiseToXMLFile(
		File		file )

		throws TOTorrentException
	{
		getDelegate().serialiseToXMLFile( file );
	}

	@Override
	public AEMonitor
	getMonitor()
	{
		return( getDelegate().getMonitor());
	}

	@Override
	public void
	print()
	{
		getDelegate().print();
	}

	@Override
	public String
	getRelationText()
	{
		return "Internal: '" + new String(getName()) + "'";
	}

	@Override
	public Object[]
	getQueryableInterfaces()
	{
		return new Object[] { lws };
	}
}
