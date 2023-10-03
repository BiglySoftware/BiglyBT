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

public class
LinkFileMap
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
		 */

	private final Map<wrapper,Entry>	name_map 	= new HashMap<>();
	private final Map<Integer,Entry>	index_map 	= new HashMap<>();

	public File
	get(
		int			index,
		File		from_file )
	{
		if ( index >= 0 ){

			Entry entry = index_map.get( index );

			if ( entry != null ){

				return( entry.getToFile());
			}
		}else{

			// just an old link pre-migration
			// Debug.out( "unexpected index: " + index );
		}

		Entry entry = name_map.get( new wrapper( from_file ));

		if ( entry == null ){

			return( null );

		}else{

				// migration - all existing links to migrate have an index of -1

			int	e_index = entry.getIndex();

			if ( e_index >= 0 && e_index != index ){

				return( null );
			}

			return( entry.getToFile());
		}
	}

	public Entry
	getEntry(
		int			index,
		File		from_file )
	{
		if ( index >= 0 ){

			Entry entry = index_map.get( index );

			if ( entry != null ){

				return( entry );
			}
		}else{

			Debug.out( "unexpected index" );
		}

		Entry entry = name_map.get( new wrapper( from_file ));

		if ( entry == null ){

			return( null );

		}else{

				// migration - all existing links to migrate have an index of -1

			int	e_index = entry.getIndex();

			if ( e_index >= 0 && e_index != index ){

				return( null );
			}

			return( entry );
		}
	}

	public void
	put(
		int			index,
		File		from_file,
		File		to_file )
	{
		Entry entry = new Entry( index, from_file, to_file );

		if ( index >= 0 ){

			index_map.put( index, entry );

				// remove any legacy entry

			if ( name_map.size() > 0 ){

				name_map.remove( new wrapper( from_file ));
			}
		}else{

			wrapper wrap = new wrapper( from_file );

			Entry existing = name_map.get( wrap );

			if ( 	existing == null ||
					!existing.getFromFile().equals( from_file) ||
					!existing.getToFile().equals( to_file )){

				Debug.out( "unexpected index" );
			}

			name_map.put( wrap, entry );
		}
	}

	public void
	putMigration(
		File		from_file,
		File		to_file )
	{
		Entry entry = new Entry( -1, from_file, to_file );

		name_map.put( new wrapper( from_file ), entry );
	}

	public void
	remove(
		int			index,
		File		key )
	{
		if ( index >= 0 ){

			index_map.remove( index );

		}else{
			// this can happen when removing non-resolved entries, not a problem
			//Debug.out( "unexpected index" );
		}

		if ( name_map.size() > 0 ){

			name_map.remove( new wrapper( key ));
		}
	}

	public boolean
	hasLinks()
	{
		for (Entry entry: index_map.values()){

			File to_file = entry.getToFile();

			if ( to_file != null ){

				if ( !entry.getFromFile().equals( to_file )){

					return( true );
				}
			}
		}

		return( false );
	}

	public int
	size()
	{
		int	size = 0;

		for (Entry entry: index_map.values()){

			File to_file = entry.getToFile();

			if ( to_file != null ){

				if ( !entry.getFromFile().equals( to_file )){

					size++;
				}
			}
		}

		return( size );
	}

	public Iterator<Entry>
	entryIterator()
	{
		if ( index_map.size() > 0 ){

			if ( name_map.size() == 0 ){

				return( index_map.values().iterator());
			}

			Set<Entry> entries = new HashSet<>(index_map.values());

			entries.addAll( name_map.values());

			return( entries.iterator());
		}

		return( name_map.values().iterator());
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

		if ( name_map.size() > 0 ){

			String n_str = "";

			for ( Entry e: name_map.values()){

				n_str += (n_str.length()==0?"":", ") + e.getString();
			}

			str += "n_map={ " + n_str + " }";
		}

		return( str );
	}

	public static class
	Entry
	{
		private final int		index;
		private final File	from_file;
		private final File	to_file;

		private
		Entry(
			int		_index,
			File	_from_file,
			File	_to_file )
		{
			index		= _index;
			from_file	= _from_file;
			to_file		= _to_file;
		}

		public int
		getIndex()
		{
			return( index );
		}

		public File
		getFromFile()
		{
			return( from_file );
		}

		public File
		getToFile()
		{
			return( to_file );
		}

		public String
		getString()
		{
			return( index + ": " + from_file + " -> " + to_file );
		}
	}

	private static class
	wrapper
	{
		private final String		file_str;

		protected
		wrapper(
			File	file )
		{
			file_str	= file.toString();
		}


		public boolean
		equals(
			Object	other )
		{
			if ( other instanceof wrapper ){

				return( file_str.equals(((wrapper)other).file_str ));
			}

			return( false );
		}

		public int
		hashCode()
		{
			return( file_str.hashCode());
		}
	}
}
