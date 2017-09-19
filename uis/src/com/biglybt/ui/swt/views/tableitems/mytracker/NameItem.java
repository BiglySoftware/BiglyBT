/*
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

package com.biglybt.ui.swt.views.tableitems.mytracker;

import org.eclipse.swt.graphics.Image;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

/**
 *
 * @author TuxPaper
 * @since 1.0.0.0
 */
public class NameItem extends CoreTableColumnSWT implements
		TableCellRefreshListener, ObfuscateCellText
{
	private static boolean bShowIcon;

	private ParameterListener configShowProgramIconListener = new ParameterListenerConfigShowProgramIcon();

	/** Default Constructor */
	public NameItem() {
		super("name", POSITION_LAST, 250, TableManager.TABLE_MYTRACKER);
		setType(TableColumn.TYPE_TEXT);

		COConfigurationManager.addWeakParameterListener(
				configShowProgramIconListener, true, "NameColumn.showProgramIcon");
	}

	@Override
	public void remove() {
		super.remove();
		COConfigurationManager.removeWeakParameterListener(
				configShowProgramIconListener, "NameColumn.showProgramIcon");
	}

	@Override
	public void refresh(TableCell cell) {
		TRHostTorrent item = (TRHostTorrent) cell.getDataSource();
		String name = (item == null) ? ""
				: TorrentUtils.getLocalisedName(item.getTorrent());
		//setText returns true only if the text is updated

		if (cell.setText(name) || !cell.isValid()) {
			if (item != null && item.getTorrent() != null && bShowIcon
					&& (cell instanceof TableCellSWT)) {
				try {
	  				final TOTorrent torrent = item.getTorrent();
	  				final String path = torrent.getFiles()[0].getRelativePath();

	  				if ( path != null ){

	  					Image icon = null;

	  					final TableCellSWT _cell = (TableCellSWT)cell;

						// Don't ever dispose of PathIcon, it's cached and may be used elsewhere

						if ( Utils.isSWTThread()){

							icon = ImageRepository.getPathIcon(path, false, !torrent.isSimpleTorrent());
						}else{
								// happens rarely (seen of filtering of file-view rows
								// when a new row is added )

							Utils.execSWTThread(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										Image icon = ImageRepository.getPathIcon(path, false, !torrent.isSimpleTorrent());

										_cell.setIcon(icon);

										_cell.redraw();
									}
								});
						}


						if ( icon != null ){

							_cell.setIcon(icon);
						}
	  				}
				} catch (Exception e) {
				}
			}
		}
	}

	@Override
	public String getObfuscatedText(TableCell cell) {
		TRHostTorrent item = (TRHostTorrent) cell.getDataSource();
		String name = null;

		try {
			name = ByteFormatter.nicePrint(item.getTorrent().getHash(), true);
		} catch (Throwable e) {
		}

		if (name == null)
			name = "";
		return name;
	}

	private static class ParameterListenerConfigShowProgramIcon implements ParameterListener {
		@Override
		public void parameterChanged(String parameterName) {
			bShowIcon = COConfigurationManager.getBooleanParameter("NameColumn.showProgramIcon");
		}
	}
}
