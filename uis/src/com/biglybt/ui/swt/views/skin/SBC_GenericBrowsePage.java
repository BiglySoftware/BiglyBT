/*
 * Created on Sep 13, 2010
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

import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.browser.BrowserContext;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectBrowser;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.ui.swt.skin.SWTSkinUtils;
import com.biglybt.core.util.SystemTime;

import com.biglybt.ui.mdi.MdiListener;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;

/**
 * @author TuxPaper
 * @created Sep 13, 2010
 *
 */
public class SBC_GenericBrowsePage
extends SkinView
{
	private SWTSkinObjectBrowser browserSkinObject;
	private MdiEntryVitalityImage vitalityImage;
	private MdiEntry entry;

	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		Object creationParams = skinObject.getData("CreationParams");

		browserSkinObject = SWTSkinUtils.findBrowserSO(soMain);

		final MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();
		if (mdi != null) {
			entry = mdi.getEntryBySkinView(this);
			if (entry != null) {
				vitalityImage = entry.addVitalityImage("image.sidebar.vitality.dots");
				vitalityImage.setVisible(false);

				mdi.addListener(new MdiListener() {
					long lastSelect = 0;

					@Override
					public void mdiEntrySelected(MdiEntry newEntry,
					                             MdiEntry oldEntry) {
						if (entry == newEntry) {
							if (entry == oldEntry) {
								if (lastSelect < SystemTime.getOffsetTime(-1000)) {
									if (browserSkinObject != null) {
										browserSkinObject.restart();
									}
								}
							} else {
								lastSelect = SystemTime.getCurrentTime();
							}
						}
					}

					@Override
					public void mdiDisposed(MultipleDocumentInterface mdi) {

					}
				});
			}
		}

		browserSkinObject.addListener(new SWTSkinObjectListener() {

			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == EVENT_SHOW) {
					browserSkinObject.removeListener(this);

					browserSkinObject.addListener(new BrowserContext.loadingListener() {
						@Override
						public void browserLoadingChanged(boolean loading, String url) {
							if (vitalityImage != null) {
								vitalityImage.setVisible(loading);
							}
						}
					});
					
				}else if (eventType == EVENT_DATASOURCE_CHANGED){
					
					if ( params instanceof String ) {
						browserSkinObject.setURL((String) params);
					}
				}
				return null;
			}
		});

		openURL();

		return null;
	}

	private void openURL() {

		if ( entry != null ){
			Object o = entry.getDataSource();
			if (o instanceof String) {
				browserSkinObject.setURL((String) o);
			}
		}
	}
}
