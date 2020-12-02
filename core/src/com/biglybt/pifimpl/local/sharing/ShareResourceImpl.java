/*
 * File    : ShareResourceImpl.java
 * Created : 31-Dec-2003
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

package com.biglybt.pifimpl.local.sharing;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.BrokenMd5Hasher;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;

public abstract class
ShareResourceImpl
	implements ShareResource
{
	protected static BrokenMd5Hasher	hasher = new BrokenMd5Hasher();

	protected ShareManagerImpl				manager;
	protected int							type;
	protected ShareResourceDirContents		parent;

	protected Map	attributes			= new HashMap();

	protected List	change_listeners 	= new ArrayList();
	protected List	deletion_listeners 	= new ArrayList();

	private volatile boolean deleted;
	
		// new constructor

	protected
	ShareResourceImpl(
		ShareManagerImpl	_manager,
		int					_type )
	{
		manager	= _manager;
		type 	= _type;
	}

		// deserialised constructor

	protected
	ShareResourceImpl(
		ShareManagerImpl	_manager,
		int					_type,
		Map					_map )
	{
		manager	= _manager;
		type 	= _type;

		Map	attrs = (Map)_map.get( "attributes" );

		if ( attrs != null ){

			Iterator	keys = attrs.keySet().iterator();

			while( keys.hasNext()){

				String	key = (String)keys.next();

				try{
					String	value = new String((byte[])attrs.get(key), Constants.DEFAULT_ENCODING_CHARSET );

					TorrentAttribute ta = TorrentManagerImpl.getSingleton().getAttribute( key );

					if ( ta == null ){

						Debug.out( "Invalid attribute '" + key );
					}else{

						attributes.put( ta, value );
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
	}

	protected void
	serialiseResource(
		Map		map )
	{
		Iterator	it = attributes.keySet().iterator();

		Map	attrs = new HashMap();

		map.put( "attributes", attrs );

		while( it.hasNext()){

			TorrentAttribute	ta = (TorrentAttribute)it.next();

			String	value = (String)attributes.get(ta);

			try{
				if ( value != null ){

					attrs.put( ta.getName(), value.getBytes( Constants.DEFAULT_ENCODING_CHARSET ));

				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public ShareResourceDirContents
	getParent()
	{
		return( parent );
	}

	protected void
	setParent(
		ShareResourceDirContents	_parent )
	{
		parent	= _parent;
	}

	public ShareResource[]
	getChildren()
	{
		return( new ShareResource[0] );
	}

	@Override
	public int
	getType()
	{
		return( type );
	}

	@Override
	public void
	setAttribute(
		final TorrentAttribute		attribute,
		String						value )
	{
		ShareConfigImpl	config = manager.getShareConfig();

		try{
			config.suspendSaving();

			ShareResource[]	kids = getChildren();

			for (int i=0;i<kids.length;i++){

				kids[i].setAttribute( attribute, value );
			}

			String	old_value = (String)attributes.get( attribute );

			if( old_value == null && value == null ){

				return;
			}

			if ( old_value != null && value != null && old_value.equals( value )){

				return;
			}

			attributes.put( attribute, value );

			try{
				config.saveConfig();

			}catch( ShareException e ){

				Debug.printStackTrace( e );
			}

		}finally{

			try{
				config.resumeSaving();

			}catch( ShareException e ){

				Debug.printStackTrace( e );
			}
		}

		fireChangeEvent( ShareResourceEvent.ET_ATTRIBUTE_CHANGED, false, attribute );
	}

	protected void
	fireChangeEvent(
		int			type,
		boolean		internal,
		Object		data )
	{
		for (int i=0;i<change_listeners.size();i++){

			try{
				((ShareResourceListener)change_listeners.get(i)).shareResourceChanged(
						this,
						new ShareResourceEvent()
						{
							@Override
							public int
							getType()
							{
								return( type );
							}

							@Override
							public boolean 
							isInternal()
							{
								return( internal );
							}
							
							@Override
							public Object
							getData()
							{
								return( data );
							}
						});

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}
	@Override
	public String
	getAttribute(
		TorrentAttribute		attribute )
	{
		return((String)attributes.get( attribute ));
	}

	@Override
	public TorrentAttribute[]
	getAttributes()
	{
		TorrentAttribute[]	res = new TorrentAttribute[attributes.size()];

		attributes.keySet().toArray( res );

		return( res );
	}

	protected void
	inheritAttributes(
		ShareResourceImpl	source )
	{
		TorrentAttribute[]	attrs = source.getAttributes();

		for ( int i=0;i<attrs.length;i++ ){

			setAttribute( attrs[i], source.getAttribute( attrs[i] ));
		}
	}

	@Override
	public void
	delete()

		throws ShareException, ShareResourceDeletionVetoException
	{
		if ( getParent() != null ){


			throw( new ShareResourceDeletionVetoException( MessageText.getString("plugin.sharing.remove.veto")));
		}

		delete( false );
	}

	@Override
	public void
	delete(
		boolean	force )

		throws ShareException, ShareResourceDeletionVetoException
	{
		delete( force, true );
	}

	public void
	delete(
		boolean	force,
		boolean	fire_listeners )

		throws ShareException, ShareResourceDeletionVetoException
	{
		if ( !force ){

			canBeDeleted();
		}

		manager.delete( this, fire_listeners );
		
		deleted = true;
	}

	@Override
	public abstract boolean
	canBeDeleted()

		throws ShareResourceDeletionVetoException;

	@Override
	public boolean 
	isDeleted()
	{
		return( deleted );
	}
	
	@Override
	public boolean
	isPersistent()
	{
		Map<String,String>	properties = getProperties();

		if ( properties == null ){

			return( false );
		}

		String persistent_str = properties.get( ShareManager.PR_PERSISTENT );

		boolean	persistent = persistent_str!=null && persistent_str.equalsIgnoreCase( "true" );

		return( persistent );
	}

	protected void
	deleteInternal()
	{
		deleted = true;
	}

	protected byte[]
	getFingerPrint(
		File		file )

		throws ShareException
	{
		try{
			StringBuffer	buffer = new StringBuffer();

			getFingerPrintSupport( buffer, file, TorrentUtils.getIgnoreSet());

			return( hasher.calculateHash(buffer.toString().getBytes()));

		}catch( ShareException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new ShareException( "ShareResource::getFingerPrint: fails", e ));
		}
	}

	protected void
	getFingerPrintSupport(
		StringBuffer	buffer,
		File			file,
		Set				ignore_set )

		throws ShareException
	{
		try{
			if ( file.isFile()){

				long	mod 	= file.lastModified();
				long	size	= file.length();

				String	file_name = file.getName();

				if  ( ignore_set.contains( file_name.toLowerCase())){

				}else{

					buffer.append( file_name ).append( ":" ).append( mod ).append( ":" ).append( size );
				}
			}else if ( file.isDirectory()){

				File[]	dir_file_list = file.listFiles();

				List file_list = new ArrayList(Arrays.asList(dir_file_list));

				Collections.sort(file_list);

				for (int i=0;i<file_list.size();i++){

					File	f = (File)file_list.get(i);

					String	file_name = f.getName();

					if ( !(file_name.equals( "." ) || file_name.equals( ".." ))){

						StringBuffer	sub_print	= new StringBuffer();

						getFingerPrintSupport( sub_print, f, ignore_set );

						if  ( sub_print.length() > 0 ){

							buffer.append( ":" ).append( sub_print );
						}
					}
				}
			}else{

				throw( new ShareException( "ShareResource::getFingerPrint: '" + file.toString() + "' doesn't exist" ));
			}

		}catch( Throwable e ){

			if ( e instanceof ShareException ){

				throw((ShareException)e);
			}

			Debug.printStackTrace( e );

			throw( new ShareException( "ShareResource::getFingerPrint: fails", e ));
		}
	}
	
	protected String
	getNewTorrentLocation()

		throws ShareException
	{
		return( manager.getNewTorrentLocation());
	}

	protected void
	writeTorrent(
		ShareItemImpl		item )

		throws ShareException
	{
		manager.writeTorrent( item );
	}

	protected void
	readTorrent(
		ShareItemImpl		item )

		throws ShareException
	{
		manager.readTorrent( item );
	}

	protected void
	deleteTorrent(
		ShareItemImpl		item )
	{
		manager.deleteTorrent( item );
	}

	public File
	getTorrentFile(
		ShareItemImpl		item )
	{
		return( manager.getTorrentFile(item));
	}
	
	protected abstract void
	checkConsistency()

		throws ShareException;

	@Override
	public void
	addChangeListener(
		ShareResourceListener	l )
	{
		change_listeners.add( l );
	}

	@Override
	public void
	removeChangeListener(
		ShareResourceListener	l )
	{
		change_listeners.remove( l );
	}

	@Override
	public void
	addDeletionListener(
		ShareResourceWillBeDeletedListener	l )
	{
		deletion_listeners.add( l );
	}

	@Override
	public void
	removeDeletionListener(
		ShareResourceWillBeDeletedListener	l )
	{
		deletion_listeners.remove( l );
	}
}
