/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.table;

import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.views.table.TableViewSWT.ColorRequester;

import org.eclipse.swt.graphics.*;

/**
 * SWT specifics interfaces for TableRow
 *
 * @author TuxPaper
 * @created Jan 22, 2007
 *
 */
public interface TableRowSWT extends TableRowCore
{

	public boolean setIconSize(Point pt);

	public Color getForeground();

	public Color getBackground();

	
	public void
	requestForegroundColor(
		ColorRequester		requester,
		Color				color );
	
	public void
	requestBackgroundColor(
		ColorRequester		requester,
		Color				color );
	  
	/**
	 * @param cellName
	 * @return
	 */
	public TableCellSWT getTableCellSWT(String cellName);

	public Rectangle getBounds();

	public void setBackgroundImage(Image image);


	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public int getFontStyle();

	/**
	 * @param bold
	 *
	 * @since 3.1.1.1
	 */
	public boolean setFontStyle(int style);

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public int getAlpha();

	/**
	 * @param i
	 *
	 * @since 3.1.1.1
	 */
	public boolean setAlpha(int alpha);

	/**
	 * @param selected
	 *
	 * @since 4.4.0.5
	 */
	void setWidgetSelected(boolean selected);

	/**
	 *
	 * @param shown
	 * @param force
	 *
	 * @return true: changed
	 */
	public boolean setShown(boolean shown, boolean force);

	public int getFullHeight();

	boolean isShown();
	
	TableViewSWT<?> getView();
}
