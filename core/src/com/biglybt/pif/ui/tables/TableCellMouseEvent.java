/*
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

package com.biglybt.pif.ui.tables;

/**
 * Mouse event information for
 * {@link com.biglybt.pif.ui.tables.TableCellMouseListener}
 * <br><br>
 * Note: 3.0.1.7 moved most functions to {@link TableRowMouseEvent}
 *
 * @author TuxPaper
 * @created Jan 10, 2006
 * @since 2.3.0.7
 */
public class TableCellMouseEvent extends TableRowMouseEvent {
	
	public
	TableCellMouseEvent(
		Object	baseEvent )
	{
		super( baseEvent );
	}
	
	/**
	 * TableCell that the mouse trigger applies to
	 *
	 * @since 2.3.0.7
	 */
	public TableCell cell;
}
