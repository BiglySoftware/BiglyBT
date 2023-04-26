/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.columns;

import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellMouseEvent;
import com.biglybt.pif.ui.tables.TableCellMouseListener;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;


public abstract class
ColumnCheckBox2
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableColumnExtraInfoListener, TableCellMouseListener
{
	private final UISWTGraphic tick_icon;
	private final UISWTGraphic tick_ro_icon;
	private final UISWTGraphic cross_icon;

	private boolean	read_only;

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info)
	{
		info.addCategories(new String[] {
				TableColumn.CAT_ESSENTIAL,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public
	ColumnCheckBox2(
		String		sTableID,
		String		sColumnID,
		int 		width,
		boolean		read_only )
	{
		super(sColumnID, ALIGN_CENTER, POSITION_INVISIBLE, 100, sTableID);
		setType(TYPE_GRAPHIC);
		
		this.read_only = read_only;

		if ( read_only ){
			removeCellMouseListener( this );
		}

		tick_icon 		= new UISWTGraphicImpl(ImageLoader.getInstance().getImage("check_yes"));
		tick_ro_icon 	= new UISWTGraphicImpl(ImageLoader.getInstance().getImage("check_ro_yes"));
		cross_icon 		= new UISWTGraphicImpl(ImageLoader.getInstance().getImage("check_no"));
	}

	public
	ColumnCheckBox2(
		String		sTableID,
		String		sColumnID )
	{
		this(sTableID, sColumnID, 40,false);
	}

	public
	ColumnCheckBox2(
		String		sTableID,
		String		sColumnID,
		int 		width )
	{
		this( sTableID, sColumnID, width,false );
	}

	protected abstract Boolean
	getCheckBoxState(
		Object		datasource );

	protected abstract void
	setCheckBoxState(
		Object		datasource,
		boolean		set );

	protected boolean
	isReadOnly(
		Object		datasource )
	{
		return( false );
	}

	@Override
	public void
	cellMouseTrigger(
		TableCellMouseEvent event )
	{
		if ( event.eventType == TableCellMouseEvent.EVENT_MOUSEUP ){

			TableCell cell = event.cell;

			int	event_x 		= event.x;
			int	event_y 		= event.y;
			int	cell_width 		= cell.getWidth();
			int	cell_height 	= cell.getHeight();

			Rectangle icon_bounds = tick_icon.getImage().getBounds();

			int x_pad = ( cell_width - icon_bounds.width ) / 2;
			int y_pad = ( cell_height - icon_bounds.height ) / 2;

			if ( 	event_x >= x_pad && event_x <= cell_width - x_pad &&
					event_y >= y_pad && event_y <= cell_height - y_pad ){

				Object datasource = cell.getDataSource();

				if ( !isReadOnly( datasource )){
					
					Boolean state = getCheckBoxState( datasource );
	
					if ( state != null ){
	
						setCheckBoxState( datasource, !state );
	
						cell.invalidate();
	
						if ( cell instanceof TableCellCore){
	
							((TableCellCore)cell).refresh( true );
						}
					}
				}
			}
		}
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		Object dataSource = cell.getDataSource();
		Boolean state = getCheckBoxState( dataSource);

		long 			sortVal = 0;
		UISWTGraphic	icon 	= null;

		if ( state != null ){

			boolean ds_read_only = read_only || isReadOnly(dataSource);
			
			if ( state ){

				sortVal = 2;
				icon 	= ds_read_only?tick_ro_icon:tick_icon;

			}else{

				sortVal = 1;
				icon 	= ds_read_only?null:cross_icon;
			}
		}

		sortVal = adjustSortVal(dataSource, sortVal);

		if (!cell.setSortValue(sortVal) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		if ( cell.getGraphic() != icon ){

			cell.setGraphic( icon );
		}
	}

	public long adjustSortVal(Object ds, long sortVal) {
		return sortVal;
	}
}
