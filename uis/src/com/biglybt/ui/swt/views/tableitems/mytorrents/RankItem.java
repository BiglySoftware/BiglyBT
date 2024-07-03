/*
 * File    : RankItem.java
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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.Core;

import java.util.*;

import org.eclipse.swt.graphics.Image;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.CoreFactory;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

/**
 * Torrent Position column.
 *
 * One object for all rows to save memory
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class RankItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class<Download> DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "#";
	private final ParameterListener paramShowIconKeyListener;

	private String	showIconKey;
	private boolean	showIcon;
	private Image 	imgUp;
	private Image 	imgDown;
	private GMListener gmListener;

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

	private boolean bInvalidByTrigger = false;

  /** Default Constructor */
  public RankItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 50, sTableID);
    setRefreshInterval(INTERVAL_INVALID_ONLY);
    CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				gmListener = new GMListener();
				core.getGlobalManager().addListener(gmListener);
			}
		});
 
    showIconKey = "RankColumn.showUpDownIcon." + (sTableID.endsWith( ".big" )?"big":"small" );

	TableContextMenuItem menuShowIcon = addContextMenuItem(
			"ConfigView.section.style.showRankIcon", MENU_STYLE_HEADER);
	menuShowIcon.setStyle(TableContextMenuItem.STYLE_CHECK);
	menuShowIcon.addFillListener(new MenuItemFillListener() {
		@Override
		public void menuWillBeShown(MenuItem menu, Object data) {
			menu.setData(Boolean.valueOf(showIcon));
		}
	});

	menuShowIcon.addMultiListener(new MenuItemListener() {
		@Override
		public void selected(MenuItem menu, Object target) {
			COConfigurationManager.setParameter(showIconKey,
					((Boolean) menu.getData()).booleanValue());
		}
	});

	  paramShowIconKeyListener = new ParameterListener() {
		  @Override
		  public void parameterChanged(String parameterName) {
			  showIcon = (COConfigurationManager.getBooleanParameter(showIconKey));
			  RankItem.this.invalidateCells();
		  }
	  };
	  COConfigurationManager.addWeakParameterListener(paramShowIconKeyListener,
			true, showIconKey);

    ImageLoader imageLoader = ImageLoader.getInstance();
    imgUp = imageLoader.getImage("image.torrentspeed.up");
    imgDown = imageLoader.getImage("image.torrentspeed.down");
    
    TableContextMenuItem sep = addContextMenuItem( "menu.set.order.sep", MENU_STYLE_HEADER);
    sep.setStyle(TableContextMenuItem.STYLE_SEPARATOR);
    
	TableContextMenuItem menuSetFromSort = addContextMenuItem(
			"menu.set.order.from.sort", MENU_STYLE_HEADER);
	menuSetFromSort.setStyle(TableContextMenuItem.STYLE_PUSH);
	
	menuSetFromSort.addFillListener(new MenuItemFillListener() {
		@Override
		public void menuWillBeShown(MenuItem menu, Object data) {
			TableView<?> tv = menuSetFromSort.getTable();
			
			if ( tv != null ){
				
				TableRowCore[] selRows = tv.getSelectedRows();
				
				if ( selRows.length > 1 ){
			
					menuSetFromSort.setText( MessageText.getString( "menu.set.order.for.selection"));
					
					return;
				}
			}
			
			menuSetFromSort.setText( MessageText.getString( "menu.set.order.from.sort"));
		}
	});
	
	menuSetFromSort.addListener((menu, target)->{
		
		TableView<?> tv = menuSetFromSort.getTable();
		
		if ( tv != null ){
			
			TableRowCore[] rows;
			
			TableRowCore[] selRows = tv.getSelectedRows();
			
			if ( selRows.length > 1 ){
				
				rows = selRows;
				
			}else{
				
				rows = tv.getRows();
			}
			
			int incomp_pos	= 1;
			int comp_pos	= 1;
			
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
						
			List<DownloadManager>	managers	= new ArrayList<>( rows.length );
			List<Integer>			positions	= new ArrayList<>( rows.length );
			
			for ( TableRowCore row: rows ){
				
				DownloadManager dm = (DownloadManager)row.getDataSource(true);
				
				managers.add( dm );
				
				positions.add( dm.isDownloadComplete(false)?comp_pos++:incomp_pos++ );
				
				// gm.moveTo(o, pos++);
			}
			
			gm.moveTo( managers, positions );

			RankItem.this.invalidateCells();
		}
	});
	
	
	TableContextMenuItem menuSortToClipboard = addContextMenuItem(
			"menu.copy.order.to.clip", MENU_STYLE_HEADER);
	
	menuSortToClipboard.setStyle(TableContextMenuItem.STYLE_PUSH);

	menuSortToClipboard.addListener((menu, target)->{
		
		TableView<?> tv = menuSortToClipboard.getTable();
		
		if ( tv != null ){
			
			TableRowCore[] rows = tv.getRows();
													
			StringBuffer str = new StringBuffer( 2048 );
			
			for ( TableRowCore row: rows ){
				
				DownloadManager dm = (DownloadManager)row.getDataSource(true);
				
				TOTorrent torrent = dm.getTorrent();
				
				if ( torrent != null ){

					try{
						byte[] hash = torrent.getHash();
					
						str.append( dm.getPosition());
						str.append( "," );
						str.append( ByteFormatter.encodeString( hash ));
						str.append( "," );
						str.append( dm.isDownloadComplete(false)?1:0);
						str.append( ",\"" );
						str.append( dm.getDisplayName());
						str.append( "\"\n");
						
					}catch( Throwable e ){
						
					}
				}
			}
			
			ClipboardCopy.copyToClipBoard( str.toString());
		}
	});
	
	TableContextMenuItem menuClipboardToSort = addContextMenuItem(
			"menu.set.sort.from.clip", MENU_STYLE_HEADER);
	menuClipboardToSort.setStyle(TableContextMenuItem.STYLE_PUSH);
	
	menuClipboardToSort.addFillListener(new MenuItemFillListener() {
		@Override
		public void menuWillBeShown(MenuItem menu, Object data) {
			TableView<?> tv = menuClipboardToSort.getTable();
			
			boolean ok = false;
			
			if ( tv != null ){
			
				ok = clipboardToSort( tv, true );
			}
			
			menuClipboardToSort.setEnabled( ok );
		}
	});
	
	menuClipboardToSort.addListener((menu, target)->{
		
		TableView<?> tv = menuClipboardToSort.getTable();
		
		if ( tv != null ){
			clipboardToSort( tv, false );
		}
	});
  }

  
  private boolean
  clipboardToSort(
	TableView<?> 	tv,
	boolean			test )
  {
	  String lines[] = ClipboardCopy.copyFromClipboard().split( "\n" );
	  
	  if ( lines.length == 0 ){
		  
		  return( false );
	  }
	  
	  GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
	  
	  int max_incomp	= 0;
	  int max_comp		= 0;
	  
	  for ( DownloadManager dm: gm.getDownloadManagers()){
		  
		  if ( dm.isDownloadComplete( false )){
			  
			  max_comp = Math.max( dm.getPosition(), max_comp );
			  
		  }else{
			 
			  max_incomp = Math.max( dm.getPosition(), max_incomp );
		  }
	  }
	  
	  List<DownloadManager>	managers	= new ArrayList<>( lines.length );
	  List<Integer>			positions	= new ArrayList<>( lines.length );
		
	  Set<Integer>			dup_pos_check = new HashSet<>();
			  
	  for ( String line: lines ){
		  
		  line = line.trim();
		  
		  if ( line.isEmpty()){
			  
			  continue;
		  }
		  
		  String[] bits = line.split(",");
		  
		  if ( bits.length < 3 ){
			  
			  return( false );
		  }
		  
		  try{
			  
			int pos = Integer.parseInt(bits[0].trim());
			
			if ( pos <= 0 ){
				
				return( false );
			}
			
			byte[] hash = ByteFormatter.decodeString( bits[1].trim());
			
			DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));
			
			if ( dm == null ){
				
				return( false );
			}
			
			boolean comp_expected	= bits[2].trim().equals("1");
			boolean comp			= dm.isDownloadComplete( false );
			
			if ( comp_expected != comp ){
				
				return( false );
			}
			
			if ( comp ){
				
				if ( pos > max_comp || dup_pos_check.contains( pos )){
					
					return( false );
				}
				
				dup_pos_check.add( pos );
				
			}else{
				
				if ( pos > max_incomp || dup_pos_check.contains( -pos )){
					
					return( false );
				}
				
				dup_pos_check.add( -pos );
			}
			
			managers.add( dm );
			
			positions.add( pos );
			
		  }catch( Throwable e ){
			  
			  return( false );
		  }
	  }
		
	  if ( !test ){
		 		
		gm.moveTo( managers, positions );

		RankItem.this.invalidateCells();
	  }
	  
	  return( true );
  }
  
  @Override
  public void reset() {
	  super.reset();

	  COConfigurationManager.removeParameter( showIconKey );
  }

  @Override
  public void
  remove()
  {
	  super.remove();

	  if (gmListener != null) {
	  	try {
	  	  CoreFactory.getSingleton().getGlobalManager().removeListener(gmListener);
		  } catch (Throwable ignore) {
		  }
	  	gmListener = null;
	  }
	  COConfigurationManager.removeWeakParameterListener(paramShowIconKeyListener,
			  showIconKey);
  }

  @Override
  public void refresh(TableCell cell) {
  	bInvalidByTrigger = false;

    DownloadManager dm = (DownloadManager)cell.getDataSource();
    long value = (dm == null) ? 0 : dm.getPosition();
    String text = "" + value;

  	boolean complete = dm == null ? false : dm.getAssumedComplete();
  	if (complete) {
  		value += 0x1000000;
  	}

    cell.setSortValue(value);
    cell.setText(text);

	TableView<?> table = cell.getTableRow().getView();
	if ( table != null ){
		Object dst = table.getDataSourceType();
		if ( dst == Download.class ){
			cell.setToolTip( text + ", " + (MessageText.getString( complete?"label.complete":"label.incomplete")));
		}
	}
	
    if (cell instanceof TableCellSWT) {
    	if ( showIcon && dm != null ){
    		Image img = dm.getAssumedComplete() ? imgUp : imgDown;
    		((TableCellSWT)cell).setIcon(img);
    	}else{
    		((TableCellSWT)cell).setIcon(null);
    	}
    }
  }

  private class GMListener implements GlobalManagerListener {
    	DownloadManagerListener listener;

    	public GMListener() {
    		 listener = new DownloadManagerListener() {
					@Override
					public void completionChanged(DownloadManager manager, boolean bCompleted) {
					}

					@Override
					public void downloadComplete(DownloadManager manager) {
					}

					@Override
					public void positionChanged(DownloadManager download, int oldPosition, int newPosition) {
						/** We will be getting multiple position changes, but we only need
						 * to invalidate cells once.
						 */
						if (bInvalidByTrigger)
							return;
						RankItem.this.invalidateCells();
						bInvalidByTrigger = true;
					}

					@Override
					public void stateChanged(DownloadManager manager, int state) {
					}
					@Override
					public void
					filePriorityChanged( DownloadManager download, com.biglybt.core.disk.DiskManagerFileInfo file )
					{
					}
    		 };
    	}

			@Override
			public void destroyed() {
			}

			@Override
			public void destroyInitiated() {
				try {
					GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
					gm.removeListener(this);
				} catch (Exception e) {
					Debug.out(e);
				}
			}

			@Override
			public void downloadManagerAdded(DownloadManager dm) {
				dm.addListener(listener);
			}

			@Override
			public void downloadManagerRemoved(DownloadManager dm) {
				dm.removeListener(listener);
			}

			@Override
			public void seedingStatusChanged(boolean seeding_only_mode, boolean b) {
			}
  }

}
