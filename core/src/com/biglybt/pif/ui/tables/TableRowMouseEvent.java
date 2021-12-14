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

package com.biglybt.pif.ui.tables;

/**
 * @author TuxPaper
 * @created May 13, 2007
 *
 * @note originally TableCellMouseEvent
 */
public class TableRowMouseEvent
{
	public final Object		baseEvent;
	
	public
	TableRowMouseEvent(
		Object		_baseEvent )
	{
		baseEvent	= _baseEvent;
	}
	
	/**
	 * eventType is triggered when mouse is pressed down
	 *
	 * @since 2.3.0.7
	 */
	public final static int EVENT_MOUSEDOWN = 0;

	/**
	 * eventType is triggered when mouse is let go
	 *
	 * @since 2.3.0.7
	 */
	public final static int EVENT_MOUSEUP = 1;

	/**
	 * eventType is trigggered when mouse is double clicked
	 *
	 * @since 2.3.0.7
	 */
	public final static int EVENT_MOUSEDOUBLECLICK = 2;

	/**
	 * eventType is triggered when the mouse is moved.  ONLY fires for
	 * listeners with subclass of TableMouseMoveListener.
	 * {@link TableCellMouseListener} will not recieve mouse move events.
	 */
	public final static int EVENT_MOUSEMOVE = 3;

	public final static int EVENT_MOUSEENTER = 4;

	public final static int EVENT_MOUSEEXIT = 5;

	/**
	 * EVENT_* constant specifying the type of event that has been triggered
	 *
	 * @since 2.3.0.7
	 */
	public int eventType;

	/**
	 * x position of mouse relative to table cell
	 *
	 * @since 2.3.0.7
	 */
	public int x;

	/**
	 * y position of mouse relative to table cell
	 */
	public int y;

	/**
	 * Which button was pressed.  1 = first button (left),
	 * 2 = second button (middle), 3 = third button (right)
	 * <p>
	 * More buttons may already be pressed down.
	 * <p>
	 * For events of type EVENT_MOUSEMOVE, button will be 0
	 *
	 * @since 2.3.0.7
	 */
	public int button;

	/**
	 * Keyboard state when the mouse event was triggered.
	 *
	 * @TODO Define state constants
	 *
	 * @since 2.3.0.7
	 */
	public int keyboardState;

	/**
	 * Setting this value to true will prevent Azureus from running its
	 * core functionality (if any) for the mouse event.
	 *
	 * For example, by default the double click in My Torrents goes to
	 * the details view.  If your plugin remaps double click to do a different
	 * function, set skipCoreFunctionality = true.
	 */
	public boolean skipCoreFunctionality;

	/**
	 * Misc data
	 *
	 * @since 3.0.1.7
	 */
	public Object data;

	/**
	 * TableRow that the mouse trigger applies to
	 *
	 * @since 3.0.1.6
	 */
	public TableRow row;
}
