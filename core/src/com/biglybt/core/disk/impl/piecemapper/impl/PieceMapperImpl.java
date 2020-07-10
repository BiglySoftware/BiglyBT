/*
 * Created on 02-Aug-2004
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

package com.biglybt.core.disk.impl.piecemapper.impl;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.impl.piecemapper.*;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.StringInterner;

/**
 * @author parg
 *
 */


public class
PieceMapperImpl
	implements DMPieceMapper
{
	private final TOTorrent			torrent;

	private final int				last_piece_length;

	protected final ArrayList<fileInfo> btFileList = new ArrayList<>();


	public
	PieceMapperImpl(
		TOTorrent		_torrent )
	{
		torrent 		= _torrent;

		int piece_length	= (int)torrent.getPieceLength();

		int piece_count		= torrent.getNumberOfPieces();

		long total_length	= torrent.getSize();

		last_piece_length  	= (int) (total_length - ((long) (piece_count - 1) * (long)piece_length));
	}

	@Override
	public void
	construct(
		LocaleUtilDecoder	_locale_decoder,
		String				_save_name )

		throws UnsupportedEncodingException
	{
			//build something to hold the filenames/sizes

		TOTorrentFile[] torrent_files = torrent.getFiles();

		if ( torrent.isSimpleTorrent()){

			buildFileLookupTables( torrent_files[0], _save_name );

		}else{

			buildFileLookupTables( torrent_files, _locale_decoder );
		}
	}

	// method for simple torrents

	private void
	buildFileLookupTables(
		TOTorrentFile			torrent_file,
		String					fileName )
	{
		// not needed as fileName already normalised
		// fileName = FileUtil.convertOSSpecificChars( fileName,  false );

		btFileList.add(new PieceMapperImpl.fileInfo(torrent_file,"", fileName ));
	}

	private void
	buildFileLookupTables(
		TOTorrentFile[]			torrent_files,
		LocaleUtilDecoder 		locale_decoder )

		throws UnsupportedEncodingException
	{
		char	separator = File.separatorChar;

		 //for each file

		for (int i = 0; i < torrent_files.length; i++) {

			buildFileLookupTable(torrent_files[i], locale_decoder, separator);
		}
	}

	/**
	 * Builds the path stored in fileDictionay, saving it in btFileList
	 * @param fileDictionay
	 * @param btFileList
	 * @param localeUtil
	 * @param separator
	 * @return the length of the file as stored in fileDictionay
	 */
	// refactored out of initialize() - Moti
	// code further refactored for readibility

	private void
	buildFileLookupTable(
		TOTorrentFile		torrent_file,
		LocaleUtilDecoder 	locale_decoder,
		final char 			separator)

		throws UnsupportedEncodingException
	{
		//build the path

		byte[][]	path_components = torrent_file.getPathComponents();

		/* replaced the following two calls:
		StringBuffer pathBuffer = new StringBuffer(256);
		pathBuffer.setLength(0);
		*/
		StringBuilder pathBuffer = new StringBuilder(0);

		int lastIndex = path_components.length - 1;
		for (int j = 0; j < lastIndex; j++) {
			//attach every element

			String	comp = locale_decoder.decodeString( path_components[j]);

			comp = FileUtil.convertOSSpecificChars( comp,  true);

			pathBuffer.append(comp);
			pathBuffer.append(separator);
		}

		//no, then we must be a part of the path
		//add the file entry to the file holder list

		String	last_comp = locale_decoder.decodeString(path_components[lastIndex]);

		last_comp = FileUtil.convertOSSpecificChars( last_comp, false );

		btFileList.add(
			new fileInfo(
				torrent_file,
				pathBuffer.toString(),
				last_comp ));
	}



	@Override
	public DMPieceMap
	getPieceMap()
	{
		if ( btFileList.size() == 1 ){

				// optimise for the single file case

			return( new DMPieceMapSimple( torrent, ((fileInfo)btFileList.get(0)).getFileInfo()));

		}else{
			int piece_length	= (int)torrent.getPieceLength();

			int piece_count		= torrent.getNumberOfPieces();

			long total_length	= torrent.getSize();

			DMPieceList[]	pieceMap = new DMPieceList[piece_count];


			//for every piece, except the last one
			//add files to the piece list until we have built enough space to hold the piece
			//see how much space is available in the file
			//if the space available isnt 0
			//add the file to the piece->file mapping list
			//if there is enough space available, stop

				//fix for 1 piece torrents

			int	modified_piece_length	= piece_length;

			if (total_length < modified_piece_length) {

				modified_piece_length = (int)total_length;
			}

			long fileOffset = 0;
			int currentFile = 0;
			for (int i = 0;(1 == piece_count && i < piece_count) || i < piece_count - 1; i++) {
				ArrayList<PieceMapEntryImpl> pieceToFileList = new ArrayList<>();
				int usedSpace = 0;
				while (modified_piece_length > usedSpace) {
					fileInfo tempFile = (fileInfo)btFileList.get(currentFile);
					long length = tempFile.getLength();

					//get the available space
					long availableSpace = length - fileOffset;

					PieceMapEntryImpl tempPieceEntry = null;

					//how much space do we need to use?
					if (availableSpace <= (modified_piece_length - usedSpace)) {
						//use the rest of the file's space
							tempPieceEntry =
								new PieceMapEntryImpl(tempFile.getFileInfo(), fileOffset, (int)availableSpace //safe to convert here
		);

						//update the used space
						usedSpace += availableSpace;
						//update the file offset
						fileOffset = 0;
						//move the the next file
						currentFile++;
					} else //we don't need to use the whole file
						{
						tempPieceEntry = new PieceMapEntryImpl(tempFile.getFileInfo(), fileOffset, modified_piece_length - usedSpace);

						//update the file offset
						fileOffset += modified_piece_length - usedSpace;
						//udate the used space
						usedSpace += modified_piece_length - usedSpace;
					}

					//add the temp pieceEntry to the piece list
					pieceToFileList.add(tempPieceEntry);
				}

				//add the list to the map
				pieceMap[i] = PieceListImpl.convert(pieceToFileList);
			}

			//take care of final piece if there was more than 1 piece in the torrent
			if (piece_count > 1) {
				pieceMap[piece_count - 1] =
					PieceListImpl.convert(
							buildLastPieceToFileList(
										btFileList,
										currentFile,
										fileOffset ));

			}

			return( new DMPieceMapImpl( pieceMap ));
		}
	}

	private List<PieceMapEntryImpl>
	buildLastPieceToFileList(
		List<fileInfo> 	file_list,
		int 			current_file,
		long 			file_offset )
	{
		ArrayList<PieceMapEntryImpl> piece_to_file_list = new ArrayList<>();

		for ( int i=current_file;i<file_list.size();i++){

			fileInfo file = file_list.get( i );

			long space_in_file = file.getLength() - file_offset;

			PieceMapEntryImpl piece_entry = new PieceMapEntryImpl( file.getFileInfo(), file_offset, (int)space_in_file);

			piece_to_file_list.add( piece_entry );

			file_offset = 0;
		}

		return( piece_to_file_list );
	}

	@Override
	public long
	getTotalLength()
	{
		return( torrent.getSize());
	}

	@Override
	public int
	getPieceLength()
	{
		return((int)torrent.getPieceLength());
	}

	@Override
	public int
	getLastPieceLength()
	{
		return( last_piece_length );
	}

	@Override
	public DMPieceMapperFile[]
	getFiles()
	{
		DMPieceMapperFile[]	res = new DMPieceMapperFile[ btFileList.size()];

		btFileList.toArray( res );

		return( res );
	}

	protected static class
	fileInfo
		implements DMPieceMapperFile
	{
		private DiskManagerFileInfo					file;
		private final TOTorrentFile					torrent_file;
		private final String 						relative_path;
		private final String 						name;

		/**
		 * @param _relative_path  Blank or Relative Path with trailing File.separator
		 */
		public
		fileInfo(
			TOTorrentFile	_torrent_file,
			String 			_relative_path,
			String 			_name )
		{
			torrent_file	= _torrent_file;
			relative_path = StringInterner.intern(_relative_path);
			name 			= _name;
		}

		@Override
		public long getLength() {
			return torrent_file.getLength();
		}
		@Override
		public String getRelativeDataPath()
		{
			return relative_path + name;
		}
		@Override
		public TOTorrentFile
		getTorrentFile()
		{
			return( torrent_file );
		}
		@Override
		public DiskManagerFileInfo getFileInfo() {
			return file;
		}
		@Override
		public void setFileInfo(DiskManagerFileInfo _file) {
			file = _file;
		}
	}
}
