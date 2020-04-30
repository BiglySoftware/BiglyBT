/*
 * Created on Jul 9, 2009
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


package com.biglybt.core.content;

import com.biglybt.core.util.Base32;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.download.Download;

public abstract class
RelatedContent
{
	public static final int	VERSION_NA					= -1;		// not applicable
	public static final int	VERSION_INITIAL				= 0;
	public static final int	VERSION_BETTER_SCRAPE		= 1;

	public final static String[]	NO_TAGS = {};

	final private String 		title;
	final private byte[]		hash;
	final private String		tracker;
	final private long			size;

	private int					version;
	private int					date;
	private int					seeds_leechers;

	private byte[]				related_to_hash;

	private byte[]				tracker_keys;
	private byte[]				ws_keys;

	private String[]			tags;
	private byte				nets;

	private long changed_locally_on;

	public
	RelatedContent(
		int			_version,
		byte[]		_related_to_hash,
		String		_title,
		byte[]		_hash,
		String		_tracker,
		byte[]		_tracker_keys,
		byte[]		_ws_keys,
		String[]	_tags,
		byte		_nets,
		long		_size,
		int			_date,
		int			_seeds_leechers )
	{
		version				= _version;
		related_to_hash		= _related_to_hash;
		title				= fixTitle( _title );
		hash				= _hash;
		tracker				= _tracker;
		tracker_keys		= _tracker_keys;
		ws_keys				= _ws_keys;
		tags				= _tags;
		nets				= _nets;
		size				= _size;
		date				= _date;
		seeds_leechers		= _seeds_leechers;
		setChangedLocallyOn(0);
	}

	public
	RelatedContent(
		String		_title,
		byte[]		_hash,
		String		_tracker,
		long		_size,
		int			_date,
		int			_seeds_leechers )
	{
			// legacy constructor as referenced from plugin - remove oneday!

		this( VERSION_NA, _title, _hash, _tracker, null, null, null, RelatedContentManager.NET_PUBLIC, _size, _date, _seeds_leechers);
	}

	public
	RelatedContent(
		int			_version,
		String		_title,
		byte[]		_hash,
		String		_tracker,
		byte[]		_tracker_keys,
		byte[]		_ws_keys,
		String[]	_tags,
		byte		_nets,
		long		_size,
		int			_date,
		int			_seeds_leechers )
	{
		version				= _version;
		title				= fixTitle( _title );
		hash				= _hash;
		tracker				= _tracker;
		tracker_keys		= _tracker_keys;
		ws_keys				= _ws_keys;
		tags				= _tags;
		nets				= _nets;
		size				= _size;
		date				= _date;
		seeds_leechers		= _seeds_leechers;
		setChangedLocallyOn(0);
	}
	
	private String
	fixTitle(
		String	str )
	{
		if ( str != null ){
			
			if ( str.startsWith( "urlencode(" )){
				
				str = str.substring( 10 );
				
				if ( str.endsWith( ")" )){
					
					str = str.substring( 0, str.length() - 1 );
				}
				
				str = str.trim();
				
				if ( str.isEmpty()){
					
					str = "download";
				}
			}
		}
		
		return( str );
	}

	public int
	getVersion()
	{
		return( version );
	}

	protected void
	setVersion(
		int		_version )
	{
		version = _version;
	}

	protected void
	setRelatedToHash(
		byte[]		h )
	{
		related_to_hash = h;
		// Intentionally not called, since setRelatedToHash gets called after
		// constructing all the RelatedContent objects
		// setChangedLocallyOn(0);
	}

	public byte[]
	getRelatedToHash()
	{
		return( related_to_hash );
	}

	public abstract Download
	getRelatedToDownload();

	public String
	getTitle()
	{
		return( title );
	}

	public abstract int
	getRank();

	public byte[]
	getHash()
	{
		return( hash );
	}

	public abstract int
	getLevel();

	public abstract boolean
	isUnread();

	public abstract void
	setUnread(
		boolean	unread );

	public abstract int
	getLastSeenSecs();

	public abstract int
	getFirstSeenSecs();
	
	public String
	getTracker()
	{
		return( tracker );
	}

	public byte[]
	getTrackerKeys()
	{
		return( tracker_keys );
	}

	public byte[]
	getWebSeedKeys()
	{
		return( ws_keys );
	}

	public String[]
	getTags()
	{
		return( tags==null?NO_TAGS:tags );
	}

	protected void
	setTags(
		String[]	_tags )
	{
		tags	= _tags;
		setChangedLocallyOn(0);
	}

	public String[]
	getNetworks()
	{
		return( RelatedContentManager.convertNetworks( nets ));
	}

	protected byte
	getNetworksInternal()
	{
		return( nets );
	}

	protected void
	setNetworksInternal(
		byte		n )
	{
		nets = n;
	}

	public long
	getSize()
	{
		return( size );
	}

	public long
	getPublishDate()
	{
		return( date*60*60*1000L );
	}

	protected int
	getDateHours()
	{
		return( date );
	}

	protected void
	setDateHours(
		int		_date )
	{
		date = _date;
		setChangedLocallyOn(0);
	}

	public int
	getLeechers()
	{
		if ( seeds_leechers == -1 ){

			return( -1 );
		}

		return( seeds_leechers&0xffff );
	}

	public int
	getSeeds()
	{
		if ( seeds_leechers == -1 ){

			return( -1 );
		}

		return( (seeds_leechers>>16) & 0xffff );
	}

	protected int
	getSeedsLeechers()
	{
		return( seeds_leechers );
	}

	protected void
	setSeedsLeechers(
		int		_sl )
	{
		seeds_leechers = _sl;
		setChangedLocallyOn(0);
	}

	public long
	getChangedLocallyOn()
	{
		return changed_locally_on;
	}

	/**
	 *
	 * @param _changed_locally_on  0 == current time
	 */
	public void
	setChangedLocallyOn(long _changed_locally_on)
	{
		changed_locally_on = (_changed_locally_on == 0) ? SystemTime.getCurrentTime() : _changed_locally_on;
	}

	public abstract void
	delete();

	public String
	getString()
	{
		return( "title=" + title + ", ver=" + version + ", hash=" + (hash==null?"null":Base32.encode( hash )) + ", tracker=" + tracker +", date=" + date + ", sl=" + seeds_leechers + ", nets=" + nets );
	}
}
