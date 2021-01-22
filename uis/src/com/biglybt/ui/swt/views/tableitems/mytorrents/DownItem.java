/*
 * File    : DownItem.java
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



/** bytes downloaded column
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class DownItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "down";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
			CAT_PROGRESS,
			CAT_BYTES
		});
	}

	/** Default Constructor */
  public DownItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
    setRefreshInterval(INTERVAL_LIVE);
     
    TableContextMenuItem menuItem = addContextMenuItem("label.set.downloaded");

	menuItem.setStyle(MenuItem.STYLE_PUSH);

	menuItem.addMultiListener(new MenuItemListener() {
		@Override
		public void selected(MenuItem menu, Object target) {
			final Object[] dms = (Object[])target;

			SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
					"set.downloaded.win.title", "set.downloaded.win.msg");

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
								
								dm.getStats().resetTotalBytesSentReceived(-1, (long)( total_size*copies));
							}
							
						}else{
							long bytes = Long.parseLong( str);

							for ( Object object: dms ){
								if (object instanceof TableRowCore) {
									object = ((TableRowCore) object).getDataSource(true);
								}
	
								DownloadManager dm = (DownloadManager)object;
								
								dm.getStats().resetTotalBytesSentReceived(-1,  bytes );
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
  public void refresh(TableCell cell) {
  	Object ds = cell.getDataSource();
  	long value = 0;
  	if (ds instanceof DownloadManager) {
      DownloadManager dm = (DownloadManager)cell.getDataSource();
      value = dm.getStats().getTotalGoodDataBytesReceived();
  	} else if (ds instanceof DiskManagerFileInfo) {
  		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
  		value = fileInfo.getDownloaded();
  	}
    if (!cell.setSortValue(value) && cell.isValid())
      return;
    cell.setText(DisplayFormatters.formatByteCountToKiBEtc(value));
    cell.setToolTip( String.valueOf( value ));
  }
}
