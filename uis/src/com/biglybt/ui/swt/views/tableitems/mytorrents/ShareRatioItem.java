/*
 * File    : ShareRatioItem.java
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

import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import org.eclipse.swt.graphics.Color;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.ui.common.table.TableRowCore;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class ShareRatioItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, ParameterListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  private final static String CONFIG_ID = "StartStopManager_iFirstPriority_ShareRatio";
	public static final String COLUMN_ID = "shareRatio";
  private int iMinShareRatio;
  private boolean changeFG = true;

  @Override
  public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_SHARING, CAT_SWARM });
	}

	/** Default Constructor */
  public ShareRatioItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 73, sTableID);
		setType(TableColumn.TYPE_TEXT);
    setRefreshInterval(INTERVAL_LIVE);
    setMinWidthAuto(true);

    setPosition(POSITION_LAST);

    iMinShareRatio = COConfigurationManager.getIntParameter(CONFIG_ID);
    COConfigurationManager.addWeakParameterListener(this, false, CONFIG_ID);

    TableContextMenuItem menuItem = addContextMenuItem("label.set.share.ratio");

	menuItem.setStyle(MenuItem.STYLE_PUSH);

	menuItem.addMultiListener(new MenuItemListener() {
		@Override
		public void selected(MenuItem menu, Object target) {
			final Object[] dms = (Object[])target;

			SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
					"set.share.ratio.win.title", "set.share.ratio.win.msg");

			entryWindow.setPreenteredText( "1.000", false );
			entryWindow.selectPreenteredText( true );

			entryWindow.prompt(new UIInputReceiverListener() {
				@Override
				public void UIInputReceiverClosed(UIInputReceiver receiver) {
					if (!receiver.hasSubmittedInput()) {
						return;
					}

					try{
						String str = receiver.getSubmittedInput().trim();

						int share_ratio = (int)( Float.parseFloat( str ) * 1000 );

						for ( Object object: dms ){
							if (object instanceof TableRowCore) {
								object = ((TableRowCore) object).getDataSource(true);
							}

							DownloadManager dm = (DownloadManager)object;

							dm.getStats().setShareRatio( share_ratio );
						}

					}catch( Throwable e ){

						Debug.out( e );
					}

				}
			});

		}
	});
  }

  @Override
  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();

    int sr = (dm == null) ? 0 : dm.getStats().getShareRatio();

    if ( sr == Integer.MAX_VALUE ){
    	sr = Integer.MAX_VALUE-1;
    }
    if ( sr == -1 ){
      sr = Integer.MAX_VALUE;
    }

    if (!cell.setSortValue(sr) && cell.isValid())
      return;

    String shareRatio = "";

    if (sr == Integer.MAX_VALUE ) {
      shareRatio = Constants.INFINITY_STRING;
    } else {
      shareRatio = DisplayFormatters.formatDecimal((double) sr / 1000, 3);
    }

    if( cell.setText(shareRatio) && changeFG ) {
    	Color color = sr < iMinShareRatio ? Colors.colorWarning : null;
    	cell.setForeground(Utils.colorToIntArray(color));
    }
  }

  @Override
  public void parameterChanged(String parameterName) {
    iMinShareRatio = COConfigurationManager.getIntParameter(CONFIG_ID);
    invalidateCells();
  }

  public boolean isChangeFG() {
		return changeFG;
	}

	public void setChangeFG(boolean changeFG) {
		this.changeFG = changeFG;
	}
}
