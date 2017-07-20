/*
 * File    : DownloadListener.java
 * Created : 11-Jan-2004
 * By      : parg
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

package com.biglybt.pif.download;

/** A listener informed of changes to a Download's state and position
 *
 * @author parg
 * @author TuxPaper (positionChanged)
 */

public interface
DownloadListener
{
  /** The Download's state has changed.  This is also triggered if the user
   * toggles the Force Start on/off.
   */
	public void
	stateChanged(
		Download		download,
		int				old_state,
		int				new_state );

  /** Position of download has changed.
   *
   * @param download object in which the position has changed
   * @param oldPosition position that the download used to be at
   * @param newPosition position that the download is now at
   */
	public void
	positionChanged(
		Download	download,
		int oldPosition,
		int newPosition );
}
