/*
 * File    : StatusItem.java
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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class StatusItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellMouseListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "status";

	private final static Object CLICK_KEY = new Object();
	
	private static Map<String,Integer>	sort_orders = new HashMap<>();
	
	static{
		for ( String tid: TableManager.TABLE_MYTORRENTS_ALL ){
			COConfigurationManager.addAndFireParameterListener(
					"MyTorrents.status.sortorder." + tid,
					new ParameterListener(){
						
						@Override
						public void parameterChanged(String parameterName){
							sort_orders.put(tid, COConfigurationManager.getIntParameter( parameterName ));
						}
					});
		}
	}
	
	private static final int[] BLUE = Utils.colorToIntArray( Colors.blue );

	private boolean changeRowFG;
	private boolean changeCellFG = true;

	private boolean	showTrackerErrors;

	public StatusItem(String sTableID, boolean changeRowFG) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 80, sTableID);
		this.changeRowFG = changeRowFG;
		setRefreshInterval(INTERVAL_LIVE);
	}

	public StatusItem(String sTableID) {
		this(sTableID, true);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadManager dm = (DownloadManager) cell.getDataSource();

		if ( dm == null ){

			return;
		}

		int sort_order = sort_orders.get( getTableID());
		
		int state = dm.getState();

		long	sort_value;
		
		String	text;

		String tooltip = null;
		
		if ( showTrackerErrors && dm.isUnauthorisedOnTracker() && state != DownloadManager.STATE_ERROR ){

			text = dm.getTrackerStatus();

			sort_value = 1100;
			
		}else{

			text = DisplayFormatters.formatDownloadStatus(dm);
			
			boolean forced = dm.isForceStart() && ( state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_SEEDING );
			
			if ( sort_order == 1 ){
				
				if ( forced ){
					
					sort_value = 1000;
					
				}else{
					
					switch( state ){
					
						case DownloadManager.STATE_SEEDING:{
							sort_value		= 900;
							break;
						}
						case DownloadManager.STATE_DOWNLOADING:{
							sort_value		= 800;
							break;
						}
						case DownloadManager.STATE_QUEUED:{
							sort_value		= 700;
							tooltip = MessageText.getString( "ManagerItem.queued.tooltip" );
							break;
						}
						case DownloadManager.STATE_STOPPED:{
							if ( dm.isPaused()){
								sort_value		= 600;
							}else{
								sort_value		= 500;
							}
							break;
						}
						default:{
							sort_value	= 0;
						}
					}
				}
			}else if ( sort_order == 2 ){
					
				PEPeerManager pm = dm.getPeerManager();
				
				boolean super_seeding = pm != null && pm.isSuperSeedMode();
				
				if ( forced ){
					
					sort_value = 800;
					
				}else if ( dm.isPaused()){
					
					sort_value = 600;
					
				}else if ( super_seeding ){
					
					sort_value = 825;
					
				}else{
					switch( state ){
						case DownloadManager.STATE_WAITING:{
							sort_value = 100;
							break;
						}
						case DownloadManager.STATE_INITIALIZING:{
							sort_value = 100;
							break;
						}
						case DownloadManager.STATE_INITIALIZED:{
							sort_value = 100;
							break;
						}
						case DownloadManager.STATE_ALLOCATING:{
							sort_value = 200;
							break;
						}
						case DownloadManager.STATE_CHECKING:{
							sort_value = 900;
							break;
						}
						case DownloadManager.STATE_READY:{
							sort_value = 300;
							break;
						}
						case DownloadManager.STATE_DOWNLOADING:{
							sort_value = 400;
							break;
						}
						case DownloadManager.STATE_FINISHING:{
							sort_value = 400;
							break;
						}
						case DownloadManager.STATE_SEEDING:{
							DiskManager diskManager = dm.getDiskManager();
							if ( diskManager != null && diskManager.getCompleteRecheckStatus() != -1 ){
								sort_value = 900;
							}else{
								sort_value = 850;
							}
							
							break;
						}
						case DownloadManager.STATE_STOPPING:{
							sort_value = 500;
							break;
						}
						case DownloadManager.STATE_STOPPED:{
							sort_value = 500;
							break;
						}
						case DownloadManager.STATE_QUEUED:{
							sort_value = 700;
							break;
						}
						case DownloadManager.STATE_ERROR:{
							sort_value = 1000;
							break;
						}
						default:{
							sort_value = 999;
							break;
						}
					}
				}
			}else{
				
				sort_value = 0;	// not used
			}
		}

		if ( sort_order == 1 ){
			
				// priority based - mix in actual state and priority
			
			sort_value += state;
			sort_value	<<= 32;
			
			if ( dm.isDownloadComplete( false )){
				
				Download dl = PluginCoreUtils.wrap( dm );
				
				if ( dl != null ){
					
					sort_value += dl.getSeedingRank();
				}
			}else{
				
				sort_value -= dm.getPosition();
			}
		}
		
		boolean update;
		
		if ( sort_order == 0 ){
			
			update = cell.setSortValue( text );
			
		}else{
		
			update = cell.setSortValue( sort_value );
			
			if ( !update ){
				
				if ( !cell.getText().equals( text )){
					
					update = true;
				}
			}
		}
		
		if ( update || !cell.isValid()){
			
			cell.setText( text );
			
			cell.setToolTip( tooltip );
			
			boolean clickable = false;

			if (cell instanceof TableCellSWT){

				int cursor_id;

				if (!text.contains("http://")){

					dm.setUserData( CLICK_KEY, null );

					cursor_id = SWT.CURSOR_ARROW;

				}else{

					dm.setUserData( CLICK_KEY, text );

					cursor_id = SWT.CURSOR_HAND;

					clickable = true;
				}

				((TableCellSWT)cell).setCursorID( cursor_id );
			}

			if (!changeCellFG && !changeRowFG){

					// clickable, make it blue whatever

				cell.setForeground( clickable?BLUE:null);

				return;
			}

			TableRow row = cell.getTableRow();

			if (row != null ) {

				Color color = null;
				if (state == DownloadManager.STATE_SEEDING) {
					color = Colors.blues[Colors.BLUES_MIDDARK];
				} else if (state == DownloadManager.STATE_ERROR) {
					color = Colors.colorError;
				} else {
					color = null;
				}
				if (changeRowFG) {
					row.setForeground(Utils.colorToIntArray(color));
				} else if (changeCellFG) {
					cell.setForeground(Utils.colorToIntArray(color));
				}
				if ( clickable ){
					cell.setForeground( Utils.colorToIntArray( Colors.blue ));
				}

			}
		}
	}

	public boolean isChangeRowFG() {
		return changeRowFG;
	}

	public void setChangeRowFG(boolean changeRowFG) {
		this.changeRowFG = changeRowFG;
	}

	public boolean isChangeCellFG() {
		return changeCellFG;
	}

	public void setChangeCellFG(boolean changeCellFG) {
		this.changeCellFG = changeCellFG;
	}

	public void
	setShowTrackerErrors(
		boolean	s )
	{
		showTrackerErrors = s;
	}

	@Override
	public void
	cellMouseTrigger(
		TableCellMouseEvent event )
	{

		DownloadManager dm = (DownloadManager) event.cell.getDataSource();
		if (dm == null) {return;}

		String clickable = (String)dm.getUserData( CLICK_KEY );

		if ( clickable == null ){

			return;
		}

		event.skipCoreFunctionality = true;

		if ( event.eventType == TableCellMouseEvent.EVENT_MOUSEUP ){

			String url = UrlUtils.getURL( clickable );

			if ( url != null ){

				Utils.launch( url );
			}
		}
	}
}
