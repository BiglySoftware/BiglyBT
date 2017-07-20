/*
 * Created : 2004/Apr/30
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

package com.biglybt.pif.ui.tables;

/** A listener that triggers when a cell is being disposed
 *
 * @author TuxPaper
 */
public interface TableCellDisposeListener {
  /** triggered when a cell is being dispose of
   *
   * @param cell TableCell that is being disposed of
   */
  void dispose(TableCell cell);
}
