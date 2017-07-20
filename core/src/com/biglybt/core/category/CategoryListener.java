/*
 * File    : CategoryListener.java
 * Created : 08-Feb-2004
 * By      : TuxPaper
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

package com.biglybt.core.category;

import com.biglybt.core.download.DownloadManager;

/** A listener informed of changes to Category */
public interface CategoryListener {
  /** A DownloadManager has been added to a Category
   * @param cat Category that the DownloadManager has been added to
   * @param manager DownloadManager that was added
   */
	public void	downloadManagerAdded(Category cat, DownloadManager manager);

  /** A DownloadManager has been removed from a Category
   * @param cat Category that the DownloadManager was removed from
   * @param removed The DownloadManager that was removed
   */
	public void	downloadManagerRemoved(Category cat, DownloadManager removed);
}
