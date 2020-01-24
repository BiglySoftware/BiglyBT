/*
 * Created on Jul 8, 2008
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

package com.biglybt.ui.common.viewtitleinfo;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.mdi.MdiEntry;

/**
 * @author TuxPaper
 * @created Jul 8, 2008
 *
 */
public class ViewTitleInfoManager
{
	public static CopyOnWriteList<ViewTitleInfoListener> listeners = new CopyOnWriteList<>();

	public static void addListener(ViewTitleInfoListener l) {
		listeners.addIfNotPresent(l);
	}

	public static void removeListener(ViewTitleInfoListener l) {
		listeners.remove(l);
	}

	public static void refreshTitleInfo(ViewTitleInfo titleinfo) {
		if (titleinfo == null) {
			return;
		}

		for ( ViewTitleInfoListener l: listeners ){

			try{
				l.viewTitleInfoRefresh( titleinfo );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
		
		if ( titleinfo instanceof ViewTitleInfo2 ){
			
			MdiEntry entry = ((ViewTitleInfo2)titleinfo).getLinkedMdiEntry();
			
			if ( entry != null ){
				
				entry.redraw();
			}
		}
	}

}
