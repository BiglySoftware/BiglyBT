/*
 * Created on May 6, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.selectedcontent;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.Download;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.common.table.TableView;

/**
 * Manages the currently selected content in the visible display
 *
 * @author TuxPaper
 * @created May 6, 2008
 *
 */
public class SelectedContentManager
{
	private static CopyOnWriteList<SelectedContentListener> listeners = new CopyOnWriteList<>();

	private static volatile ISelectedContent[] currentlySelectedContent = new ISelectedContent[0];

	private static volatile String viewID = null;

	private static volatile TableView tv = null;

	public static void destroyStatic() {
		listeners.clear();
		currentlySelectedContent = new ISelectedContent[0];
		tv = null;
	}

	public static String getCurrentySelectedViewID() {
		return viewID;
	}

	public static void addCurrentlySelectedContentListener(
			SelectedContentListener l) {
		if (listeners.contains(l)) {
			return;
		}
		listeners.add(l);
		l.currentlySelectedContentChanged(currentlySelectedContent, viewID);
	}

	public static void removeCurrentlySelectedContentListener(
			SelectedContentListener l) {
		listeners.remove(l);
	}
	public static void clearCurrentlySelectedContent() {
		changeCurrentlySelectedContentNoTrigger(null, null, null);
		// Always trigger selected content listeners since toolbar relies it
		// them to reset the toolbaritems if something that didn't use
		// SelectedContentManager modified the toolbaritems states
		triggerSelectedContentListeners();
	}

	public static void changeCurrentlySelectedContent(String viewID,
			ISelectedContent[] currentlySelectedContent) {
		changeCurrentlySelectedContent(viewID, currentlySelectedContent, null);
	}

	public static void changeCurrentlySelectedContent(String viewID,
			ISelectedContent[] currentlySelectedContent, TableView tv) {
		if (changeCurrentlySelectedContentNoTrigger(viewID, currentlySelectedContent, tv)) {
			triggerSelectedContentListeners();
		}
	}

	private static boolean changeCurrentlySelectedContentNoTrigger(String viewID,
			ISelectedContent[] currentlySelectedContent, TableView tv) {
		if (currentlySelectedContent == null) {
			currentlySelectedContent = new ISelectedContent[0];
		}
		/*
		System.out.println("change CURSEL for '"
				+ viewID
				+ "' to "
				+ currentlySelectedContent.length
				+ ";"
				+ (currentlySelectedContent.length > 0 ? currentlySelectedContent[0].getDisplayName()
						: "") + Debug.getCompressedStackTrace());
		*/			
		if (currentlySelectedContent.length == 0
				&& SelectedContentManager.viewID != null && viewID != null
				&& !viewID.equals(SelectedContentManager.viewID)) {
			// don't allow clearing if someone else set the currently selected
			//System.out.println("-->abort because it's not " + SelectedContentManager.viewID);
			return false;
		}

		synchronized( SelectedContentManager.class ){
			boolean	same = SelectedContentManager.tv == tv;

			if ( same ){

				same =
					SelectedContentManager.viewID == viewID ||
					( 	SelectedContentManager.viewID != null &&
						viewID != null &&
						SelectedContentManager.viewID.equals( viewID ));

				if ( same ){

					if ( SelectedContentManager.currentlySelectedContent.length == currentlySelectedContent.length ){

						for ( int i=0;i<currentlySelectedContent.length && same ;i++){

							same = currentlySelectedContent[i].sameAs( SelectedContentManager.currentlySelectedContent[i]);
						}

						if ( same ){

							return false;
						}
					}
				}
			}

			SelectedContentManager.tv = tv;
			SelectedContentManager.currentlySelectedContent = currentlySelectedContent;
			SelectedContentManager.viewID = viewID;
		}
		return true;
	}

	public static void triggerSelectedContentListeners() {
		for( SelectedContentListener l: listeners ){

			try{
				l.currentlySelectedContentChanged( currentlySelectedContent, viewID);

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	public static ISelectedContent[] getCurrentlySelectedContent() {
		return currentlySelectedContent;
	}

	public static DownloadManager[] getDMSFromSelectedContent() {
		ISelectedContent[] sc = SelectedContentManager.getCurrentlySelectedContent();
		if (sc.length > 0) {
			int x = 0;
			DownloadManager[] dms = new DownloadManager[sc.length];
			for (int i = 0; i < sc.length; i++) {
				ISelectedContent selectedContent = sc[i];
				if (selectedContent == null) {
					continue;
				}
				dms[x] = selectedContent.getDownloadManager();
				if (dms[x] != null) {
					x++;
				}
			}
			if (x > 0) {
				System.arraycopy(dms, 0, dms, 0, x);
				return dms;
			}
		}
		return null;
	}

	public static TableView getCurrentlySelectedTableView() {
		return tv;
	}

	public static Object convertSelectedContentToObject(ISelectedContent[] contents) {
		if (contents == null) {
			contents = getCurrentlySelectedContent();
		}
		if (contents.length == 0) {
			TableView tv = SelectedContentManager.getCurrentlySelectedTableView();
			if (tv != null) {
				return tv.getSelectedDataSources(false);
			}
			return null;
		}
		if (contents.length == 1) {
			return selectedContentToObject(contents[0]);
		}
		Object[] objects = new Object[contents.length];
		for (int i = 0; i < contents.length; i++) {
			ISelectedContent content = contents[i];
			objects[i] = selectedContentToObject(content);
		}
		return objects;
	}

	private static Object selectedContentToObject(ISelectedContent content) {
		DownloadManager dm = content.getDownloadManager();
		if ( dm == null ){
			return( null );
		}
		Download dl = PluginCoreUtils.wrap( dm );
		if (dl == null) {
			return null;
		}
		int i = content.getFileIndex();
		if (i < 0) {
			return dl;
		}
		return dl.getDiskManagerFileInfo(i);
	}

	public static String
	getCurrentlySelectedContentDetails()
	{
		ISelectedContent[] contents = getCurrentlySelectedContent();
		
		if ( contents.length == 0){
			
			TableView tv = SelectedContentManager.getCurrentlySelectedTableView();
			
			if ( tv != null ){
				
				Object[] ds = tv.getSelectedDataSources( false );
				
				String str = "";
					
				for ( Object d: ds ){
					
					str += (str.isEmpty()?"":", " ) + d;
				}
					
				return( tv.getTableID() + " -> " + str );
			}
			
			return( "None" );
			
		}else{
			
			String str = "";
			
			for ( ISelectedContent ic: contents ){
				
				str += (str.isEmpty()?"":"\n" ) + ic.getString();
			}
			
			return( str );
		}
	}
}
