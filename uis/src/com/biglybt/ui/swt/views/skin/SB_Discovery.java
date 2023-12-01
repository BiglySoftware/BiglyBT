/*
 * Created on May 10, 2013
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

package com.biglybt.ui.swt.views.skin;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;

/**
 * @author TuxPaper
 * @created May 10, 2013
 *
 */
public class SB_Discovery
{
	public SB_Discovery(MultipleDocumentInterface mdi) {
		setup(mdi);
	}

	private void setup(final MultipleDocumentInterface mdi) {

		mdi.addListener(entry -> {
			if (entry.getViewID().equals(
					MultipleDocumentInterface.SIDEBAR_HEADER_DISCOVERY)) {
				setupHeader(entry);
			}
		});
	}

	private void setupHeader(final MdiEntry entry) {

		ViewTitleInfo titleInfo = new ViewTitleInfo() {
			@Override
			public Object getTitleInfoProperty(int propertyID) {
				
				if (propertyID == TITLE_TEXT ){
					return( MessageText.getString( "sidebar." + entry.getViewID()));
					
				}else if (propertyID == TITLE_INDICATOR_TEXT) {
					if (entry.isExpanded()) {
						return null;
					}
					StringBuilder sb = new StringBuilder();
					MdiEntry[] entries = entry.getMDI().getEntries();
					for (MdiEntry subEntry : entries) {
						if (subEntry.getViewID().startsWith("Subscription_")) {
							continue;
						}
						if (entry.getViewID().equals(subEntry.getParentID())) {
							ViewTitleInfo titleInfo = subEntry.getViewTitleInfo();
							if (titleInfo != null) {
								Object text = titleInfo.getTitleInfoProperty(TITLE_INDICATOR_TEXT);
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
								Object text = titleInfo.getTitleInfoProperty(TITLE_INDICATOR_TEXT);
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
				}
				return null;
			}
		};
		entry.setViewTitleInfo(titleInfo);
	}
}
