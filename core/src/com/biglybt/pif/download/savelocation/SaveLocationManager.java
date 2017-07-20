/*
 * Created on 12 May 2008
 * Created by Allan Crooks
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
 */
package com.biglybt.pif.download.savelocation;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManager;

/**
 * Plugins which want to control the logic of where the default save location
 * for downloads (including for <tt>on-completion</tt> and <tt>on-removal</tt>
 * behaviour) can implement this class and register it through the
 * {@link DownloadManager#setSaveLocationManager(SaveLocationManager)}.
 *
 * <p>
 *
 * Each method here returns a {@link SaveLocationChange} object, which contains
 * instructions which allows both the download and the torrent to be moved and
 * renamed.
 *
 * <p>
 *
 * The methods here take two arguments - <tt>for_move</tt> and <tt>on_event</tt>.
 *
 * <ul>
 * <li>When events happen to downloads (like the download being completed or removed),
 *   both of these values will be <tt>true</tt>.
 * <li>When something is trying to update the logical location for the download,
 *   <tt>for_move</tt> will be <tt>true</tt>, while <tt>on_event</tt> will be false.
 * <li>When something is trying to determine all the places where existing data
 *   files might exist, both values will be <tt>false</tt>.
 * </ul>
 *
 * If <tt>for_move</tt> is <tt>false</tt>, any checks normally performed on a download
 * to see if it is applicable or not (to be managed by this object) should not be applied
 * here.
 *
 * <p><b>Note:</b> This interface is intended to be implemented by plugins.</p>
 *
 * @since 3.1.0.1
 */
public interface SaveLocationManager {

	/**
	 * Return the location to move the download to when it first started (or
	 * return <tt>null</tt> to keep the download and torrent in the same
	 * location).
	 *
	 * @param download Download to handle.
	 * @param for_move <tt>true</tt> if the download is going to be moved, or <tt>false</tt>
	 *     if the logical path is just being calculated for other reasons.
	 * @param on_event <tt>true</tt> if the download really is being initialised, or
	 *   <tt>false</tt> if we are just determining the appropriate location for an incomplete
	 *   download.
	 * @return The new save location instructions.
	 */
	public SaveLocationChange onInitialization(Download download, boolean for_move, boolean on_event);

	/**
	 * Return the location to move the download to when it is completed (or
	 * return <tt>null</tt> to keep the download and torrent in the same
	 * location).
	 *
	 * @param download Download to handle.
	 * @param for_move <tt>true</tt> if the download is going to be moved, or <tt>false</tt>
	 *     if the logical path is just being calculated for other reasons.
	 * @param on_event <tt>true</tt> if the download really is being moved for completion, or
	 *   <tt>false</tt> if we are just determining the appropriate location for an complete
	 *   download.
	 * @return The new save location instructions.
	 */
	public SaveLocationChange onCompletion(Download download, boolean for_move, boolean on_event);

	/**
	 * Return the location to move the download to when it is removed (or
	 * return <tt>null</tt> to keep the download and torrent in the same
	 * location).
	 *
	 * @param download Download to handle.
	 * @param for_move <tt>true</tt> if the download is going to be moved, or <tt>false</tt>
	 *     if the logical path is just being calculated for other reasons.
	 * @param on_event <tt>true</tt> if the download really is being removed, or
	 *   <tt>false</tt> if we are just determining the appropriate location for the download
	 *   when it is removed.
	 * @return The new save location instructions.
	 */
	public SaveLocationChange onRemoval(Download download, boolean for_move, boolean on_event);

}
