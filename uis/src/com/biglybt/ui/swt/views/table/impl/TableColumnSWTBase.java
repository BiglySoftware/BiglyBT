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

package com.biglybt.ui.swt.views.table.impl;

import org.eclipse.swt.graphics.Color;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.table.TableColumnSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT.ColorRequester;

public class 
TableColumnSWTBase
	implements TableColumnSWT
{
	private final TableColumnCore		core;
	
	protected
	TableColumnSWTBase(
		TableColumnCore		_core )
	{
		core	= _core;
	}

	public TableColumnCore
	getColumnCore()
	{
		return( core );
	}
	
	
	public void
	sync()
	{
		
	}
	
	public void
	requestForegroundColor(
		ColorRequester		requester,
		Color				color )
	{
		
	}
	
	public void
	requestBackgroundColor(
		ColorRequester		requester,
		Color				color )
	{
		
	}
	
	@Override
	public Color 
	getForeground()
	{
		return( null );
	}
}
