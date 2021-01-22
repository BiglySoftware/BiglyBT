/*
 * File    : UpItem.java
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

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/** bytes uploaded column
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class UpItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "up";

	/** Default Constructor */
  public UpItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
    setRefreshInterval(INTERVAL_LIVE);

    setPosition(POSITION_LAST);
    
    TableContextMenuItem menuItem = addContextMenuItem("label.set.uploaded");

 	menuItem.setStyle(MenuItem.STYLE_PUSH);

 	menuItem.addMultiListener(new MenuItemListener() {
 		@Override
 		public void selected(MenuItem menu, Object target) {
 			final Object[] dms = (Object[])target;

 			SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
 					"set.uploaded.win.title", "set.uploaded.win.msg");

 			entryWindow.setPreenteredText( "-1", false );
 			entryWindow.selectPreenteredText( true );

 			entryWindow.prompt(new UIInputReceiverListener() {
 				@Override
 				public void UIInputReceiverClosed(UIInputReceiver receiver) {
 					if (!receiver.hasSubmittedInput()) {
 						return;
 					}

 					try{
 						String str = receiver.getSubmittedInput().trim();

 						if ( str.startsWith( "-" )){
 						
 							double copies = Double.parseDouble( str.substring(1));

 							for ( Object object: dms ){
 								if (object instanceof TableRowCore) {
 									object = ((TableRowCore) object).getDataSource(true);
 								}
 	
 								DownloadManager dm = (DownloadManager)object;
 	
 								DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();

 								long total_size = 0;

 								for ( DiskManagerFileInfo file: files ){

 									if ( !file.isSkipped()){

 										total_size += file.getLength();
 									}
 								}
 								
 								dm.getStats().resetTotalBytesSentReceived((long)( total_size*copies), -1);
 							}
 							
 						}else{
 							long bytes = Long.parseLong( str);

 							for ( Object object: dms ){
 								if (object instanceof TableRowCore) {
 									object = ((TableRowCore) object).getDataSource(true);
 								}
 	
 								DownloadManager dm = (DownloadManager)object;
 								
 								dm.getStats().resetTotalBytesSentReceived( bytes, -1 );
 							}
 						}

 					}catch( Throwable e ){

 						Debug.out( e );
 					}

 				}
 			});

 		}});
  }

  @Override
  public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SHARING,
			CAT_BYTES
		});
	}

  @Override
  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();
    long value = (dm == null) ? 0 : dm.getStats().getTotalDataBytesSent();

    if (!cell.setSortValue(value) && cell.isValid())
      return;

    cell.setText(DisplayFormatters.formatByteCountToKiBEtc(value));
    cell.setToolTip( String.valueOf( value ));
  }
}
