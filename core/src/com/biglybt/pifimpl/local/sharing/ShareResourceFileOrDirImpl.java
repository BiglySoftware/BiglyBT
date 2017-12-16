/*
 * File    : ShareResourceFileOrDirImpl.java
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.StringTokenizer;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateFactory;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentCreator;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareItem;
import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.sharing.ShareResourceDeletionVetoException;
import com.biglybt.pif.sharing.ShareResourceWillBeDeletedListener;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;

public abstract class
ShareResourceFileOrDirImpl
	extends		ShareResourceImpl
{
	private final File					file;
	private	final byte[]				personal_key;
	private final Map<String,String>	properties;

	private ShareItemImpl		item;

	protected static ShareResourceImpl
	getResourceSupport(
		ShareManagerImpl	_manager,
		File				_file )

		throws ShareException
	{
		try{
			return( _manager.getResource( _file.getCanonicalFile() ));

		}catch( IOException e ){

			throw( new ShareException( "getCanonicalFile fails", e ));
		}
	}

	protected
	ShareResourceFileOrDirImpl(
		ShareManagerImpl				_manager,
		ShareResourceDirContentsImpl	_parent,
		int								_type,
		File							_file,
		boolean							_personal,
		Map<String,String>				_properties )

		throws ShareException
	{
		super( _manager, _type );

		properties	= _properties;

		if ( getType() == ST_FILE ){

			if ( !_file.exists()){

				throw( new ShareException( "File '" + _file.getName() + "' not found"));
			}

			if ( !_file.isFile()){

				throw( new ShareException( "Not a file"));
			}
		}else{

			if ( !_file.exists()){

				throw( new ShareException( "Dir '"+ _file.getName() + "' not found"));
			}

			if ( _file.isFile()){

				throw( new ShareException( "Not a directory"));
			}
		}

		try{
			file = _file.getCanonicalFile();

		}catch( IOException e ){

			throw( new ShareException("ShareResourceFile: failed to get canonical name", e));
		}

		personal_key = _personal?RandomUtils.nextSecureHash():null;

		if ( _parent != null ){

			setParent( _parent );

			inheritAttributes( _parent );
		}

		createTorrent();
	}

	protected
	ShareResourceFileOrDirImpl(
		ShareManagerImpl	_manager,
		int					_type,
		File				_file,
		Map					_map )

		throws ShareException
	{
		super( _manager, _type, _map );

		file		= _file;

		personal_key = (byte[])_map.get( "per_key" );

		properties = BDecoder.decodeStrings((Map)_map.get( "props" ));

		item = ShareItemImpl.deserialiseItem( this, _map );
	}

	@Override
	public boolean
	canBeDeleted()

		throws ShareResourceDeletionVetoException
	{
		for (int i=0;i<deletion_listeners.size();i++){

			((ShareResourceWillBeDeletedListener)deletion_listeners.get(i)).resourceWillBeDeleted( this );
		}

		return( true );
	}

	protected abstract byte[]
	getFingerPrint()

		throws ShareException;

	protected void
	createTorrent()

		throws ShareException
	{
		try{
			manager.reportCurrentTask( (item==null?"Creating":"Re-creating").concat(" torrent for '").concat(file.toString()).concat("'" ));

			URL[]	urls = manager.getAnnounceURLs();

			TOTorrentCreator creator = TOTorrentFactory.createFromFileOrDirWithComputedPieceLength(
										file,
										urls[0],
										manager.getAddHashes());

			creator.addListener( manager );

			TOTorrent	to_torrent;

			try{
				manager.setTorrentCreator( creator );

				to_torrent = creator.create();

			}finally{

				manager.setTorrentCreator( null );
			}

			if ( personal_key != null ){

				Map	map = to_torrent.serialiseToMap();

				Map	info = (Map)map.get( "info" );

				info.put( "az_salt", personal_key );

				to_torrent =  TOTorrentFactory.deserialiseFromMap( map );
			}

			LocaleTorrentUtil.setDefaultTorrentEncoding( to_torrent );

			for (int i=1;i<urls.length;i++){

				TorrentUtils.announceGroupsInsertLast( to_torrent, new URL[]{ urls[i]});
			}

			String	comment = COConfigurationManager.getStringParameter( "Sharing Torrent Comment" ).trim();

			boolean	private_torrent = COConfigurationManager.getBooleanParameter( "Sharing Torrent Private" );

			boolean	dht_backup_enabled = COConfigurationManager.getBooleanParameter( "Sharing Permit DHT" );

			TorrentAttribute ta_props = TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_SHARE_PROPERTIES );

			String	props = getAttribute( ta_props );

			if ( props != null ){

				StringTokenizer	tok = new StringTokenizer( props, ";" );

				while( tok.hasMoreTokens()){

					String	token = tok.nextToken();

					int	pos = token.indexOf('=');

					if ( pos == -1 ){

						Debug.out( "ShareProperty invalid: " + props );

					}else{

						String	lhs = token.substring(0,pos).trim().toLowerCase();
						String	rhs = token.substring(pos+1).trim().toLowerCase();

						boolean	set = rhs.equals("true");

						if ( lhs.equals("private")){

							private_torrent	= set;

						}else if ( lhs.equals("dht_backup")){

							dht_backup_enabled	= set;

						}else if ( lhs.equals("comment")){

							comment = rhs;

						}else{

							Debug.out( "ShareProperty invalid: " + props );

							break;
						}
					}
				}
			}

			if ( comment.length() > 0 ){

				to_torrent.setComment( comment );
			}

			TorrentUtils.setDHTBackupEnabled( to_torrent, dht_backup_enabled );

			TorrentUtils.setPrivate( to_torrent, private_torrent );

			if ( TorrentUtils.isDecentralised(to_torrent)){

				TorrentUtils.setDecentralised( to_torrent );
			}

			DownloadManagerState	download_manager_state =
				DownloadManagerStateFactory.getDownloadState( to_torrent );

			TorrentUtils.setResumeDataCompletelyValid( download_manager_state );


			download_manager_state.save();

			if ( item == null ){

				byte[] fingerprint = getFingerPrint();

				item = new ShareItemImpl(this, fingerprint, new TorrentImpl(to_torrent));

			}else{

				item.setTorrent( new TorrentImpl(to_torrent));

				item.writeTorrent();
			}

		}catch( TOTorrentException e ){

			if ( e.getReason() == TOTorrentException.RT_CANCELLED ){

				throw( new ShareException("ShareResource: Operation cancelled", e));

			}else{

				throw( new ShareException("ShareResource: Torrent create failed", e));
			}
		}catch( Throwable e ){

			throw( new ShareException("ShareResource: Torrent create failed", e));
		}
	}

	@Override
	protected void
	checkConsistency()

		throws ShareException
	{
		if ( isPersistent()){

			// we don't re-verify stuff for persistent shares

		}else{

			try{
				if ( Arrays.equals(getFingerPrint(), item.getFingerPrint())){

					// check torrent file still exists

					if ( !manager.torrentExists( item )){

						createTorrent();
					}
				}else{

					manager.addFileOrDir( null, file, getType(), personal_key != null, properties );
				}
			}catch( Throwable e ){

				manager.delete( this, true );
			}
		}
	}

	protected static ShareResourceImpl
	deserialiseResource(
		ShareManagerImpl	manager,
		Map					map,
		int					type )

		throws ShareException
	{
		try{
			File file = new File(new String((byte[])map.get("file"), Constants.DEFAULT_ENCODING ));

			if ( type == ST_FILE ){

				return( new ShareResourceFileImpl( manager, file, map ));

			}else{
				return( new ShareResourceDirImpl( manager, file, map ));

			}
		}catch( UnsupportedEncodingException e ){

			throw( new ShareException( "internal error", e ));
		}
	}

	@Override
	protected void
	serialiseResource(
		Map		map )
	{
		super.serialiseResource( map );

		map.put( "type", new Long(getType()));

		try{
			map.put( "file", file.toString().getBytes( Constants.DEFAULT_ENCODING));

		}catch( UnsupportedEncodingException e ){

			Debug.printStackTrace( e );
		}

		if ( personal_key != null ){

			map.put( "per_key", personal_key );
		}

		if ( properties != null ){

			map.put( "props", properties );
		}

		item.serialiseItem( map );
	}

	@Override
	protected ShareResource 
	lookupShare(byte[] torrent_hash){
		try{
			if ( Arrays.equals( item.getTorrent().getHash(), torrent_hash )){
				
				return( this );
			}
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( null );
	}
	
	@Override
	protected void
	deleteInternal()
	{
		item.delete();
	}

	@Override
	public String
	getName()
	{
		return( file.toString());
	}

	public File
	getFile()
	{
		return( file );
	}

	public ShareItem
	getItem()
	{
		return( item );
	}

	@Override
	public Map<String, String>
	getProperties()
	{
		return( properties );
	}
}
