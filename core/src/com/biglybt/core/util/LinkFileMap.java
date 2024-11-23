/*
 * Created on 21-Mar-2006
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

package com.biglybt.core.util;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.core.util.StringInterner.FileKey;

public class
LinkFileMap
	implements BEncodableObject
{
		/*
		 * History here: Before 5001_B22 file linkage was performed by linking source files and target files - source file being the
		 * original, unmodified location of the file as if no linking had been performed, target being wherever. This was designed way
		 * back when the FileManager didn't have any knowledge of torrents and therefore didn't understand what a file index within
		 * a torrent was and it just applied a source->target renaming operation transparently. However, things changed and torrent
		 * knowledge crept into the FileManager (for piece-reordering storage for example). Linking was still based on file though.
		 * Then a bug appeared caused by the removal of OS-specific illegal file system chars (e.g. : on windows) resulting in
		 * two files in the torrent resolving to the same physical file on disk. The 'obvious' solution, to rename one of them to
		 * avoid the conflict, didn't work as renaming is based on linking and linking was based on physical file names, and both
		 * files had the same physical file so the rename affected both files and left the conflict in existence.
		 * So I decided to rework the code to use file indexes instead of source file names. Well, I currently have both in place
		 * to support migration of config, but at some point we should be able to remove the source-file component of linking
		 * and just use index->target. Maybe
		 *
		 * Note that the FileManagerImpl's getLink requires access to the from-name to verify that the link it looks up
		 * is valid (principally caused by the 'move' method running BEFORE links are updated - really this should be
		 * reworked
		 * 
		 * From 3701 the FileKey has been introduced to reduce memory usage of full file name storage
		 * Also we never write entries for deleted links (target=null) and treat source=dest as deleted link
		 */

	// private final Map<FileKey,Entry>	name_map 	= new HashMap<>();	removed 3700
	
	private static final boolean	STORE_FROM_KEY = true;	// can set false from 3801+ and users can still revert to 3800 if wanted (but not earlier...)
	
	private final Map<Integer,Entry>	index_map 	= new ConcurrentHashMap<>();

	public File
	get(
		int			index )
	{
		if ( index >= 0 ){

			Entry entry = index_map.get( index );

			if ( entry != null ){

				FileKey to_fk = entry.getToFile();
				
				return( to_fk==null?null:to_fk.getFile());
			}
		}else{

			Debug.out( "unexpected index: " + index );
		}

		return( null );
	}

	public Entry
	getEntry(
		int			index )
	{
		if ( index >= 0 ){

			Entry entry = index_map.get( index );

			if ( entry != null ){

				return( entry );
			}
		}else{

			Debug.out( "unexpected index" );
		}

		return( null );
	}

	public void
	put(
		int			index,
		FileKey		from_fk_maybe_null,
		FileKey		to_fk )
	{			
		if ( to_fk == null || ( from_fk_maybe_null != null && from_fk_maybe_null.equals(to_fk))){
			
			index_map.remove( index );
			
		}else{
			
			Entry entry = new Entry( index, from_fk_maybe_null, to_fk );
	
			if ( index >= 0 ){
	
				index_map.put( index, entry );
	
			}else{
	
				Debug.out( "unexpected index" );
			}
		}
	}

	public void
	clear()
	{
		index_map.clear();
	}
	
	public Iterator<LinkFileMap.Entry>
	entryIterator()
	{
		return( index_map.values().iterator());
	}
	
	public int
	size()
	{
		return( index_map.size());
	}

	@Override
	public Object 
	toBencodeObject()
	{
			// I'd like to add some common-prefix optimisations to this encoding but this
			// would break backwards compatibility so it would need to be added in parallel 
			// to the existing crap method for a few releases before actually switching
			// which would make things worse for a while and I can't summon the strength
		
		List<String>	list = new ArrayList<>();

		Iterator<LinkFileMap.Entry>	it = entryIterator();

		while( it.hasNext()){

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			
			StringInterner.FileKey	source_maybe_null 	= entry.getFromFileMaybeNull();
			
			StringInterner.FileKey	target 	= entry.getToFile();

			StringBuilder	str = new StringBuilder( 512 );
			
			str.append( index );
			str.append( "\n" );
			if ( source_maybe_null != null ){
				str.append( source_maybe_null.toString());
			}
			str.append( "\n" );
			str.append( target.toString());
						
			list.add( str.toString());
		}
		
		return( list );
	}
	
	public void
	fromBencodeObject(
		List<String>		list )
	{
		for (int i=0;i<list.size();i++){

			String	entry = (String)list.get(i);

			String[] bits = entry.split( "\n" );

			if ( bits.length >= 2 ){

				try{
					int		index 	= Integer.parseInt( bits[0].trim());
					
					String from_str = bits[1];
					
					StringInterner.FileKey	source_maybe_null	= from_str.isEmpty()?null: new StringInterner.FileKey( from_str );
					
					StringInterner.FileKey	target;
					
					if ( bits.length < 3 ){
						
						target = null;
						
					}else{
						
						String to_str = bits[2];
							
						target	= new StringInterner.FileKey( to_str );
					}

					if( index >= 0 ){

						put( index, source_maybe_null, target );

					}else{
						
						Debug.out( "unexpected index" );
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}
	
	public String
	getString()
	{
		String str = "";

		if ( index_map.size() > 0 ){

			String i_str = "";

			for ( Entry e: index_map.values()){

				i_str += (i_str.length()==0?"":", ") + e.getString();
			}

			str += "i_map={ " + i_str + " }";
		}

		return( str );
	}

	public static class
	Entry
	{
		private final int		index;
		private final FileKey	from_file_maybe_null;
		private final FileKey	to_file;

		private
		Entry(
			int			_index,
			FileKey		_from_file_maybe_null,
			FileKey		_to_file )
		{
			index					= _index;
			from_file_maybe_null	= STORE_FROM_KEY?_from_file_maybe_null:null;
			to_file					= _to_file;
		}

		public int
		getIndex()
		{
			return( index );
		}

		public FileKey
		getFromFileMaybeNull()
		{
			return( from_file_maybe_null );
		}

		public FileKey
		getToFile()
		{
			return( to_file );
		}

		public String
		getString()
		{
			return( index + ": " + from_file_maybe_null + " -> " + to_file );
		}
	}
}
