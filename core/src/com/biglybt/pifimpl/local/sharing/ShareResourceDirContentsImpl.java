/*
 * File    : ShareResourceDirContentsImpl.java
 * Created : 02-Jan-2004
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.torrent.TorrentAttribute;

public class
ShareResourceDirContentsImpl
	extends		ShareResourceImpl
	implements 	ShareResourceDirContents
{
	private final File					root;
	private final boolean				recursive;
	private final Map<String,String>	properties;
	private final byte[]				personal_key;

	protected ShareResource[]		children	= new ShareResource[0];

	protected
	ShareResourceDirContentsImpl(
		ShareManagerImpl	_manager,
		File				_dir,
		boolean				_recursive,
		boolean				_personal,
		Map<String,String>	_properties,
		boolean				_async_check )

		throws ShareException
	{
		super( _manager, ST_DIR_CONTENTS );

		root 		= _dir;
		recursive	= _recursive;
		properties	= _properties==null?new HashMap<>():_properties;

		if ( !root.exists()){

			throw( new ShareException( "Dir '" + root.getName() + "' not found"));
		}

		if ( root.isFile()){

			throw( new ShareException( "Not a directory"));
		}

		personal_key = _personal?RandomUtils.nextSecureHash():null;

			// new resource, trigger processing

		if ( _async_check ){

			new AEThread2( "SM:asyncCheck", true )
			{
				@Override
				public void
				run()
				{
					try{
						checkConsistency();

					}catch( Throwable e ){

						Debug.out( "Failed to update consistency", e );
					}
				}
			}.start();

		}else{

		      checkConsistency();
		}
	}

	protected
	ShareResourceDirContentsImpl(
		ShareManagerImpl	_manager,
		File				_dir,
		boolean				_recursive,
		Map					_map )

		throws ShareException
	{
		super( _manager, ST_DIR_CONTENTS, _map );

		root 		= _dir;
		recursive	= _recursive;

			// recovery - see comment below about not failing if dir doesn't exist...

		if ( !root.exists()){

			Debug.out( "Dir '" + root.getName() + "' not found");

			// throw( new ShareException( "Dir '".concat(root.getName()).concat("' not found")));

		}else{

			if ( root.isFile()){

				throw( new ShareException( "Not a directory"));
			}
		}

		personal_key = (byte[])_map.get( "per_key" );

		properties = BDecoder.decodeStrings((Map)_map.get( "props" ));

			// deserialised resource, checkConsistency will be called later to trigger sub-share adding
	}

	@Override
	public boolean
	canBeDeleted()

		throws ShareResourceDeletionVetoException
	{
		for (int i=0;i<children.length;i++){

			if ( !children[i].canBeDeleted()){

				return( false );
			}
		}

		return( true );
	}

	@Override
	protected void
	checkConsistency()

		throws ShareException
	{
		// ensure all shares are defined as per dir contents and recursion flag

		List	kids = checkConsistency(root);

		if ( kids != null ){

			children = new ShareResource[kids.size()];

			kids.toArray( children );

		}else{

			children = new ShareResource[0];
		}
	}

	protected List
	checkConsistency(
		File		dir )

		throws ShareException
	{
		List	kids = new ArrayList();

		File[]	files = dir.listFiles();

		if ( files == null || !dir.exists() ){

				// dir has been deleted

			if ( !isPersistent()){

					// actually, this can be bad as some os errors (e.g. "too many open files") can cause the dir
					// to appear to have been deleted. However, we don't want to delete the share. So let's just
					// leave it around, manual delete required if deletion required.

				if ( dir == root ){

					return( null );

				}else{

					manager.delete( this, true );
				}
			}
		}else{

			for (int i=0;i<files.length;i++){

				File	file = files[i];

				String	file_name = file.getName();

				if (!(file_name.equals(".") || file_name.equals(".." ))){

					if ( file.isDirectory()){

						if ( recursive ){

							List	child = checkConsistency( file );

							kids.add( new shareNode( this, file, child ));

						}else{

							try{
								ShareResource res = manager.getDir( file );

								if ( res == null ){

									res = manager.addDir( this, file, personal_key != null, properties );
								}

								kids.add( res );

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}
					}else{

						try{
							ShareResource res = manager.getFile( file );

							if ( res == null ){

								res = manager.addFile( this, file, personal_key != null, properties );
							}

							kids.add( res );

						}catch( Throwable e ){

							Debug.printStackTrace( e );
						}
					}
				}
			}

			for (int i=0;i<kids.size();i++){

				Object	o = kids.get(i);

				if ( o instanceof ShareResourceImpl ){

					((ShareResourceImpl)o).setParent(this);
				}else{

					((shareNode)o).setParent(this);
				}
			}
		}

		return( kids );
	}

	@Override
	protected void
	deleteInternal()
	{
		super.deleteInternal();
		
		for (int i=0;i<children.length;i++){

			try{
				if ( children[i] instanceof ShareResourceImpl ){

					((ShareResourceImpl)children[i]).delete(true);
				}else{

					((shareNode)children[i]).delete(true);

				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	@Override
	protected void
	serialiseResource(
		Map		map )
	{
		super.serialiseResource( map );

		map.put( "type", new Long(getType()));
		map.put( "recursive", new Long(recursive?1:0));
		map.put("file", root.toString().getBytes(Constants.DEFAULT_ENCODING_CHARSET));

		if ( personal_key != null ){

			map.put( "per_key", personal_key );
		}

		if ( properties != null ){

			map.put( "props", properties );
		}
	}

	protected static ShareResourceImpl
	deserialiseResource(
		ShareManagerImpl	manager,
		Map					map )

		throws ShareException
	{
		File root = FileUtil.newFile(new String((byte[]) map.get("file"), Constants.DEFAULT_ENCODING_CHARSET));

		boolean recursive = ((Long) map.get("recursive")).longValue() == 1;

		ShareResourceImpl res = new ShareResourceDirContentsImpl(manager, root, recursive, map);

		return res;
	}

	@Override
	public String
	getName()
	{
		return( root.toString());
	}

	@Override
	public File
	getRoot()
	{
		return( root );
	}

	@Override
	public boolean
	isRecursive()
	{
		return( recursive );
	}

	@Override
	public ShareResource[]
	getChildren()
	{
		return( children );
	}

	@Override
	public Map<String, String>
	getProperties()
	{
		return( properties );
	}
	
	@Override
	public void 
	setProperties(
		Map<String, String> 	props,
		boolean					internal )
	{
		properties.putAll( props );
		
		for ( ShareResource sr: children ){
			
			sr.setProperties(props,internal);
		}
		
		manager.configDirty();
	}
	

	protected class
	shareNode
		implements ShareResourceDirContents
	{
		protected ShareResourceDirContents	node_parent;
		protected File						node;
		protected ShareResource[]			node_children;

		protected
		shareNode(
			ShareResourceDirContents	_parent,
			File						_node,
			List						kids )
		{
			node_parent	= _parent;
			node		=_node;

			node_children = new ShareResource[kids.size()];

			kids.toArray( node_children );

			for (int i=0;i<node_children.length;i++){

				Object	o = node_children[i];

				if ( o instanceof ShareResourceImpl ){

					((ShareResourceImpl)o).setParent( this );
				}else{

					((shareNode)o).setParent( this );

				}
			}
		}

		@Override
		public ShareResourceDirContents
		getParent()
		{
			return( node_parent );
		}

		protected void
		setParent(
			ShareResourceDirContents	_parent )
		{
			node_parent	= _parent;
		}

		@Override
		public int
		getType()
		{
			return( ShareResource.ST_DIR_CONTENTS );
		}

		@Override
		public String
		getName()
		{
			return( node.toString());
		}

		@Override
		public void
		setAttribute(
			TorrentAttribute		attribute,
			String					value )
		{
			for (int i=0;i<node_children.length;i++){

				node_children[i].setAttribute( attribute, value );
			}
		}

		@Override
		public String
		getAttribute(
			TorrentAttribute		attribute )
		{
			return( null );
		}

		@Override
		public TorrentAttribute[]
		getAttributes()
		{
			return( new TorrentAttribute[0]);
		}

		@Override
		public void
		delete()

			throws ShareResourceDeletionVetoException
		{
			throw( new ShareResourceDeletionVetoException( MessageText.getString("plugin.sharing.remove.veto")));
		}

		@Override
		public void
		delete(
			boolean	force )

			throws ShareException, ShareResourceDeletionVetoException
		{
			for (int i=0;i<node_children.length;i++){

				Object	o = node_children[i];

				if ( o instanceof ShareResourceImpl ){

					((ShareResourceImpl)o).delete(force);
				}else{

					((shareNode)o).delete(force);
				}
			}
		}


		@Override
		public boolean
		canBeDeleted()

			throws ShareResourceDeletionVetoException
		{
			for (int i=0;i<node_children.length;i++){

				node_children[i].canBeDeleted();
			}

			return( true );
		}
		
		@Override
		public boolean 
		isDeleted()
		{
			return( false );
		}

		@Override
		public File
		getRoot()
		{
			return( node );
		}

		@Override
		public boolean
		isRecursive()
		{
			return( recursive );
		}

		@Override
		public ShareResource[]
		getChildren()
		{
			return( node_children );
		}

		@Override
		public Map<String, String>
		getProperties()
		{
			return( null );
		}

		@Override
		public void 
		setProperties(
			Map<String, String> props,
			boolean				internal )
		{
			for (int i=0;i<node_children.length;i++){

				node_children[i].setProperties( props, internal );
			}
		}
		
		@Override
		public boolean
		isPersistent()
		{
			return( false );
		}

		@Override
		public void
		addChangeListener(
			ShareResourceListener	l )
		{
		}

		@Override
		public void
		removeChangeListener(
			ShareResourceListener	l )
		{
		}

		@Override
		public void
		addDeletionListener(
			ShareResourceWillBeDeletedListener	l )
		{
		}

		@Override
		public void
		removeDeletionListener(
			ShareResourceWillBeDeletedListener	l )
		{
		}
	}
}
