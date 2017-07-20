/*
 * Created on 10-Dec-2004
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

package com.biglybt.ui.swt.views.tableitems.myshares;

import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * @author parg
 *
 */

public class
CategoryItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
		/** Default Constructor */

	protected static final TorrentAttribute	category_attribute =
		TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_CATEGORY );

	public
	CategoryItem()
	{
		super("category", POSITION_LAST, 400, TableManager.TABLE_MYSHARES);

		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void
	refresh(TableCell cell)
	{
		ShareResource item = (ShareResource)cell.getDataSource();

		if (item == null){

			cell.setText("");

		}else{

			String	value = item.getAttribute(category_attribute);

			cell.setText( value==null?"":value);
		}
	}
}
