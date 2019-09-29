/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.skin;

import java.util.ArrayList;

import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoListener;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.swt.views.ViewTitleInfoBetaP;

public class SB_Vuze
{
	private ArrayList<MdiEntry> children = new ArrayList<>(4);

	private ViewTitleInfo titleInfo;
	private ViewTitleInfoListener viewTitleInfoListener;

	public SB_Vuze(MultipleDocumentInterface mdi) {
		setup(mdi);
	}

	private void setup(final MultipleDocumentInterface mdi) {

		ViewTitleInfoBetaP.setupSidebarEntry(mdi);

		WelcomeView.setupSidebarEntry(mdi);

		SBC_ActivityTableView.setupSidebarEntry(mdi);

		// Refresh the Vuze header when one of the children's title properties change
		viewTitleInfoListener = new ViewTitleInfoListener() {
			@Override
			public void viewTitleInfoRefresh(ViewTitleInfo titleInfo) {
				if (SB_Vuze.this.titleInfo == null) {
					return;
				}
				MdiEntry childrenArray[] = children.toArray(new MdiEntry[0]);
				for (MdiEntry entry : childrenArray) {
					if (entry.getViewTitleInfo() == titleInfo) {
						ViewTitleInfoManager.refreshTitleInfo(SB_Vuze.this.titleInfo);
						break;
					}
				}
			}
		};
		ViewTitleInfoManager.addListener(viewTitleInfoListener);

		// Maintain a list of children entries; Open header on load
		mdi.addListener(new MdiEntryLoadedListener() {
			@Override
			public void mdiEntryLoaded(MdiEntry entry) {
				if (MultipleDocumentInterface.SIDEBAR_HEADER_VUZE.equals(
						entry.getParentID())) {
					children.add(entry);
					entry.addListener(new MdiChildCloseListener() {
						@Override
						public void mdiChildEntryClosed(MdiEntry parent, MdiEntry child,
						                                boolean user) {
							children.remove(child);
						}
					});
				}
				if (!entry.getViewID().equals(
						MultipleDocumentInterface.SIDEBAR_HEADER_VUZE)) {
					return;
				}
				titleInfo = new ViewTitleInfo_Vuze(entry);
				entry.setViewTitleInfo(titleInfo);
			}
		});
	}

	public void dispose() {
		if (viewTitleInfoListener != null) {
			ViewTitleInfoManager.removeListener(viewTitleInfoListener);
			viewTitleInfoListener = null;
		}
	}

	private static class ViewTitleInfo_Vuze
		implements ViewTitleInfo
	{
		private MdiEntry entry;

		public ViewTitleInfo_Vuze(MdiEntry entry) {
			this.entry = entry;
		}

		@Override
		public Object getTitleInfoProperty(int propertyID) {
			if (propertyID == TITLE_INDICATOR_TEXT) {
				if (entry.isExpanded()) {
					return null;
				}
				StringBuilder sb = new StringBuilder();
				MdiEntry[] entries = entry.getMDI().getEntries();
				for (MdiEntry subEntry : entries) {
					if (entry.getViewID().equals(subEntry.getParentID())) {
						ViewTitleInfo titleInfo = subEntry.getViewTitleInfo();
						if (titleInfo != null) {
							Object text = titleInfo.getTitleInfoProperty(
									TITLE_INDICATOR_TEXT);
							if (text instanceof String) {
								if (sb.length() > 0) {
									sb.append(" | ");
								}
								sb.append(text);
							}
						}
					}
				}
				if (sb.length() > 0) {
					return sb.toString();
				}
			} else if (propertyID == TITLE_INDICATOR_TEXT_TOOLTIP) {
				if (entry.isExpanded()) {
					return null;
				}
				StringBuilder sb = new StringBuilder();
				MdiEntry[] entries = entry.getMDI().getEntries();
				for (MdiEntry subEntry : entries) {
					if (entry.getViewID().equals(subEntry.getParentID())) {
						ViewTitleInfo titleInfo = subEntry.getViewTitleInfo();
						if (titleInfo != null) {
							Object text = titleInfo.getTitleInfoProperty(
									TITLE_INDICATOR_TEXT);
							if (text instanceof String) {
								if (sb.length() > 0) {
									sb.append("\n");
								}
								sb.append(subEntry.getTitle()).append(": ").append(text);
							}
						}
					}
				}
				if (sb.length() > 0) {
					return sb.toString();
				}
			}else if (propertyID == TITLE_INDICATOR_COLOR) {
				if (entry.isExpanded()) {
					return null;
				}
				MdiEntry[] entries = entry.getMDI().getEntries();
				for (MdiEntry subEntry : entries) {
					if (entry.getViewID().equals(subEntry.getParentID())) {
						ViewTitleInfo titleInfo = subEntry.getViewTitleInfo();
						if (titleInfo != null) {
							Object color = titleInfo.getTitleInfoProperty(
									TITLE_INDICATOR_COLOR);
							if ( color instanceof int[]) {
								return( color );
							}
						}
					}
				}
				return( null );
			}
			return null;
		}
	}

}
