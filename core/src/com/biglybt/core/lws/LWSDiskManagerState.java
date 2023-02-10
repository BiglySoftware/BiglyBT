/*
 * Created on 11-Dec-2005
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

package com.biglybt.core.lws;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.category.Category;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.download.DownloadManagerState.ResumeHistory;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.core.util.LinkFileMap;

public class
LWSDiskManagerState
	implements DownloadManagerState
{
	private long flags = FLAG_LOW_NOISE | FLAG_DISABLE_AUTO_FILE_MOVE;

	protected
	LWSDiskManagerState()
	{
	}

	@Override
	public TOTorrent
	getTorrent()
	{
		return( null );
	}
	
	public boolean
	getAndClearRecoveredStatus()
	{
		return( false );
	}

	public File
	getStateFile(
		String	name )
	{
		return( null );
	}

	@Override
	public File
	getStateFile()
	{
		return null;
	}

	@Override
	public DownloadManager
	getDownloadManager()
	{
		return( null );
	}

	@Override
	public void
	clearResumeData()
	{
	}

	@Override
	public Map
	getResumeData()
	{
		return( new HashMap());
	}

	@Override
	public void
	setResumeData(
		Map	data )
	{
	}

	@Override
	public boolean
	isResumeDataComplete()
	{
		return( true );
	}

	@Override
	public List<ResumeHistory> 
	getResumeDataHistory()
	{
		return( Collections.emptyList());
	}
	
	@Override
	public void 
	restoreResumeData(
		ResumeHistory history)
	{
	}
	
	@Override
	public void
	clearTrackerResponseCache()
	{
	}

	@Override
	public Map
	getTrackerResponseCache()
	{
		return( new HashMap());
	}

	@Override
	public void
	setTrackerResponseCache(
		Map		value )
	{
	}

	@Override
	public void
	setFlag(
		long		flag,
		boolean		set )
	{
		if ( set ){
			flags |= flag;
		}else{
			flags &= ~flag;
		}
	}

	@Override
	public boolean
	getFlag(
		long		flag )
	{
		return(( flags & flag ) != 0 );
	}

	@Override
	public long
	getFlags()
	{
		return( flags );
	}

	public void
	setTransientFlag(
		long		flag,
		boolean		set )
	{
	}

	public boolean
	getTransientFlag(
		long		flag )
	{
		return( false );
	}

	public long
	getTransientFlags()
	{
		return( 0 );
	}
	
	public Object 
	getTransientAttribute(
		String name )
	{
		return( null );
	}
	
	@Override
	public void 
	setTransientAttribute(
		String 		name, 
		Object		value)
	{
	}
	
	@Override
	public boolean
	isOurContent()
	{
		return false;
	}

	@Override
	public int
	getIntParameter(
		String	name )
	{
		return( 0 );
	}

	@Override
	public void
	setIntParameter(
		String	name,
		int	value )
	{
	}

	@Override
	public long
	getLongParameter(
		String	name )
	{
		return( 0 );
	}

	@Override
	public void
	setParameterDefault(
		String name)
	{
	}

	@Override
	public void
	setLongParameter(
		String	name,
		long	value )
	{
	}

	@Override
	public boolean
	getBooleanParameter(
		String	name )
	{
		return( false );
	}

	@Override
	public void
	setBooleanParameter(
		String		name,
		boolean		value )
	{
	}

	@Override
	public void
	setAttribute(
		String		name,
		String		value )
	{
	}

	@Override
	public void 
	setAttribute(
		String name, 
		String value, 
		boolean setDirty)
	{
	}
	
	@Override
	public String
	getAttribute(
		String		name )
	{
		return( null );
	}


	@Override
	public void setIntAttribute(String name, int value){}
	@Override
	public int getIntAttribute(String name){ return( 0 ); }
	@Override
	public void setLongAttribute(String name, long value){}
	@Override
	public long getLongAttribute(String name){ return( 0 ); }
	@Override
	public void setBooleanAttribute(String name, boolean value){}
	@Override
	public boolean getBooleanAttribute(String name){ return( false ); }
	@Override
	public boolean hasAttribute(String name){ return( false );}
	@Override
	public void removeAttribute(String name){}
	
	@Override
	public String
	getTrackerClientExtensions()
	{
		return( null );
	}

	@Override
	public void
	setTrackerClientExtensions(
		String		value )
	{
	}

	@Override
	public void
	setListAttribute(
		String		name,
		String[]	values )
	{
	}

	@Override
	public String[]
	getListAttribute(
		String	name )
	{
		return( null );
	}

	@Override
	public String
	getListAttribute(
		String 		name,
		int 		idx)
	{
		return null;
	}

	@Override
	public void
	setMapAttribute(
		String		name,
		Map			value )
	{
	}

	@Override
	public Map
	getMapAttribute(
		String		name )
	{
		return( null );
	}

	@Override
	public Category
	getCategory()
	{
		return( null );
	}

	@Override
	public void
	setCategory(
		Category cat )
	{
	}

	@Override
	public void
	setPrimaryFile(
		DiskManagerFileInfo dmfi)
	{
	}

	@Override
	public DiskManagerFileInfo
	getPrimaryFile()
	{
		return null;
	}

	@Override
	public String[]
	getNetworks()
	{
		return( new String[0] );
	}


    @Override
    public boolean
    isNetworkEnabled(
    	String network)
    {
    	return false;
    }

	@Override
	public void
	setNetworks(
		String[]		networks )
	{
	}


    @Override
    public void
    setNetworkEnabled(
        String network,
        boolean enabled)
    {
    }

	@Override
	public String[]
	getPeerSources()
	{
		return( new String[0] );
	}

	@Override
	public boolean
	isPeerSourcePermitted(
		String	peerSource )
	{
		return( false );
	}

	@Override
	public void
	setPeerSourcePermitted(
		String peerSource,
		boolean permitted )
	{
	}

    @Override
    public boolean
    isPeerSourceEnabled(
        String peerSource)
    {
    	return false;
    }

	@Override
	public void
	setPeerSources(
		String[]		networks )
	{
	}


    @Override
    public void
    setPeerSourceEnabled(
        String source,
        boolean enabled )
    {
    }

    @Override
    public void
	setFileLink(
		int		source_index,
		File	link_source,
		File	link_destination )
    {
    }

    @Override
    public void
	setFileLinks(
		List<Integer>	source_indexes,
		List<File>		link_sources,
		List<File>		link_destinations )
    {
    }

    @Override
    public int getFileFlags(int file_index){
    	return 0;
    }
    
    @Override
    public void setFileFlags(int file_index, int flags){
    	
    }
    
    @Override
    public void
    discardFluff()
    {
    }

	@Override
	public void
	clearFileLinks()
	{
	}

	@Override
	public File
	getFileLink(
		int		source_index,
		File	link_source )
	{
		return( null );
	}

	@Override
	public LinkFileMap
	getFileLinks()
	{
		return( new LinkFileMap());
	}

	@Override
	public String
	getUserComment()
	{
		return( "" );
	}

	@Override
	public void
	setUserComment(
		String name )
	{
	}

	@Override
	public String
	getRelativeSavePath()
	{
		return null;
	}

	public void
	setRelativeSavePath(
		String path )
	{
	}

	@Override
	public void
	setActive(
		boolean	a )
	{
	}

	@Override
	public boolean
	exportState(
		File	target_dir )
	{
		return( false );
	}

	@Override
	public void
	save(
		boolean	interim )
	{
	}

	@Override
	public void
	delete()
	{
	}

	@Override
	public void
	suppressStateSave(
		boolean suppress)
	{
	}

	@Override
	public void
	addListener(
		DownloadManagerStateAttributeListener 	l,
		String 									attribute,
		int 									event_type)
	{
	}

	@Override
	public void
	removeListener(
		DownloadManagerStateAttributeListener 	l,
		String 									attribute,
		int 									event_type)
	{
	}

	@Override
	public void
	generateEvidence(
		IndentWriter writer)
	{
	}

	@Override
	public void
	dump(
		IndentWriter writer)
	{
	}

	@Override
	public String
	getDisplayName()
	{
		return null;
	}

	@Override
	public void
	setDisplayName(
		String name)
	{
	}

	@Override
	public boolean
	parameterExists(
		String name)
	{
		return false;
	}

	public void
	supressStateSave(
		boolean supress )
	{
	}
}
