/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.peermanager.piecepicker;

public interface
PiecePickerListener
{
	public default void
	providerAdded(
		PieceRTAProvider	provider )
	{}

	public default void
	providerRemoved(
		PieceRTAProvider	provider )
	{}

	public default void
	providerAdded(
		PiecePriorityProvider	provider )
	{}

	public default void
	providerRemoved(
		PiecePriorityProvider	provider )
	{}
	
	public static final int THING_FORCE_PIECE	= 1;
		
	public default void
	somethingChanged(
		PiecePicker	pp,
		int			thing,
		Object		data )
	{	
	}
}
