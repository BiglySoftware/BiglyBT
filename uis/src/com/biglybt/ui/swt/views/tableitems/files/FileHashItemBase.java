/*
 * File    : NameItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import org.eclipse.swt.SWT;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.ui.common.table.TableCellCore;


public class
FileHashItemBase
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellMouseListener
{
	protected static final String	HT_CRC32	= "crc32";
	protected static final String	HT_MD5		= "md5";
	protected static final String	HT_SHA1		= "sha1";

	final String				hash_type;
	final TableContextMenuItem 	menuItem;

	public
	FileHashItemBase(
		String		_hash_type,
		int			width )
	{
		super( _hash_type, ALIGN_LEAD, POSITION_INVISIBLE, width, TableManager.TABLE_TORRENT_FILES);

		hash_type = _hash_type;

		setType( TableColumn.TYPE_TEXT );

		setRefreshInterval( INTERVAL_LIVE );

		menuItem = addContextMenuItem( "FilesView." + hash_type + ".calculate" );

		menuItem.setStyle( MenuItem.STYLE_PUSH );

		menuItem.addMultiListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem menu, Object target)
				{
					Object[]	files = (Object[])target;

					for ( Object _file: files ){

						if (_file instanceof TableRow) {
							_file = ((TableRow)_file).getDataSource();
						}
						DiskManagerFileInfo file = (DiskManagerFileInfo)_file;

						updateHash( hash_type, file );
					}
				}
			});
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info)
	{
		info.addCategories(new String[] {
				CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED );
	}

	@Override
	public void
	cellMouseTrigger(
		TableCellMouseEvent event)
	{
		DiskManagerFileInfo file = (DiskManagerFileInfo) event.cell.getDataSource();

		if ( file == null ){

			return;
		}

		TableCellCore core_cell = (TableCellCore)event.cell;

		if ( !event.cell.getText().startsWith( "<" )){

			core_cell.setCursorID( SWT.CURSOR_ARROW );
			core_cell.setToolTip( null );

			return;
		}

		if (event.eventType == TableRowMouseEvent.EVENT_MOUSEENTER){

			core_cell.setCursorID( SWT.CURSOR_HAND );
			core_cell.setToolTip( MessageText.getString( "FilesView.click.info" ) );

		}else if (event.eventType == TableRowMouseEvent.EVENT_MOUSEEXIT ){

			core_cell.setCursorID( SWT.CURSOR_ARROW );
			core_cell.setToolTip( null );
		}

		if ( event.eventType != TableCellMouseEvent.EVENT_MOUSEUP ){

			return;
		}

			// Only activate on LMB.

		if ( event.button != 1 ){

			return;
		}

		event.skipCoreFunctionality = true;

		updateHash( hash_type, file );
	}

	@Override
	public void
	refresh(
		TableCell cell)
	{
		DiskManagerFileInfo file = (DiskManagerFileInfo)cell.getDataSource();

		if ( file == null ){

			return;
		}

		cell.setText( getHash( hash_type, file ));
	}


	private static AsyncDispatcher dispatcher = new AsyncDispatcher();

	private static Map<DiskManagerFileInfo,Set<String>>	pending = new HashMap<>();

	private static volatile DiskManagerFileInfo	active;
	private static volatile String				active_hash;
	private static volatile int					active_percent;

	private static boolean
	isFileReady(
		DiskManagerFileInfo		file )
	{
		if ( 	file == null ||
				file.getLength() != file.getDownloaded() ||
				file.getAccessMode() != DiskManagerFileInfo.READ ){

			return( false );
		}

		File f = file.getFile( true );

		if ( FileUtil.lengthWithTimeout(f) != file.getLength() || !FileUtil.canReadWithTimeout( f )){

			return( false );
		}

		return( true );
	}

	private static void
	updateHash(
		final String				hash_type,
		final DiskManagerFileInfo	file )
	{
		if ( !isFileReady( file )){

			return;
		}

		synchronized( pending ){

			Set<String> hashes = pending.get( file );

			if ( hashes != null && hashes.contains( hash_type )){

				return;
			}

			if ( hashes == null ){

				hashes = new HashSet<>();

				pending.put( file, hashes );
			}

			hashes.add( hash_type );
		}

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					try{
						DownloadManager dm = file.getDownloadManager();

						if ( dm == null ){

							return;
						}

						if ( !isFileReady( file )){

							return;
						}

						active_percent	= 0;
						active_hash		= hash_type;
						active 			= file;

						File f = file.getFile( true );

						CRC32 			crc32 	= null;
						MessageDigest	md		= null;

						if ( hash_type == HT_CRC32 ){

							crc32 = new CRC32();

						}else if ( hash_type == HT_MD5 ){

							md = MessageDigest.getInstance( "md5" );

						}else{

							md = MessageDigest.getInstance( "SHA1" );

						}

						FileInputStream fis = new FileInputStream( f );

						long	size 	= f.length();
						long	done	= 0;

						if ( size == 0 ){

							size = 1;
						}

						try{
							byte[]	buffer = new byte[512*1024];

							while( true ){

								int	len = fis.read( buffer );

								if ( len <= 0 ){

									break;
								}

								if ( crc32 != null ){

									crc32.update( buffer, 0, len );
								}

								if ( md != null ){

									md.update( buffer, 0, len );
								}

								done += len;

								active_percent = (int)(( 1000*done) / size );
							}

							byte[]		hash;

							if ( crc32 != null ){

								long	val = crc32.getValue();

								hash = ByteFormatter.intToByteArray( val );

							}else{

								hash = md.digest();
							}

							Map other_hashes = dm.getDownloadState().getMapAttribute( DownloadManagerState.AT_FILE_OTHER_HASHES );

							if ( other_hashes == null ){

								other_hashes = new HashMap();

							}else{

								other_hashes = BEncoder.cloneMap( other_hashes );
							}

							Map file_hashes = (Map)other_hashes.get( String.valueOf( file.getIndex()));

							if ( file_hashes == null ){

								file_hashes = new HashMap();

								other_hashes.put( String.valueOf( file.getIndex()), file_hashes );
							}

							file_hashes.put( hash_type, hash );

							dm.getDownloadState().setMapAttribute( DownloadManagerState.AT_FILE_OTHER_HASHES, other_hashes );

						}finally{

							fis.close();
						}
					}catch( Throwable e ){

						Debug.out( e );

					}finally{

						synchronized( pending ){

							Set<String> hashes = pending.get( file );

							hashes.remove( hash_type );

							if ( hashes.size() == 0 ){

								pending.remove( file );
							}

							active = null;
						}
					}
				}
			});
	}

	private static String
	getHash(
		String					hash_type,
		DiskManagerFileInfo		file )
	{
		if ( file == null ){

			return( "" );
		}

		DownloadManager dm = file.getDownloadManager();

		if ( dm == null ){

			return( "" );
		}

		synchronized( pending ){

			Set<String> hashes = pending.get( file );

			if ( hashes != null && hashes.contains( hash_type )){

				if ( active == file && active_hash == hash_type ){

					return( DisplayFormatters.formatPercentFromThousands( active_percent ));

				}else{

					return( "..." );
				}
			}
		}

		Map other_hashes = dm.getDownloadState().getMapAttribute( DownloadManagerState.AT_FILE_OTHER_HASHES );

		if ( other_hashes != null ){

			Map file_hashes = (Map)other_hashes.get( String.valueOf( file.getIndex()));

			if ( file_hashes != null ){

				byte[] hash = (byte[])file_hashes.get( hash_type );

				if ( hash != null ){

					return( ByteFormatter.encodeString( hash ).toLowerCase());
				}
			}
		}

		if ( !isFileReady( file )){

			return( "" );
		}

		return( "<" + MessageText.getString( "FilesView.click" ) + ">" );
	}

}
