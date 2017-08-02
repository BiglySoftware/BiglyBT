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

package com.biglybt.ui.swt.views;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import com.biglybt.ui.mdi.MdiCloseListener;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryCreationListener;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pifimpl.local.PluginInitializer;

import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.util.JSONUtils;
import com.biglybt.util.MapUtils;

public class ViewTitleInfoBetaP
	implements ViewTitleInfo
{
	private static final String PARAM_LASTPOSTCOUNT = "betablog.numPosts";
	private static final String TUMBLR_DEVBLOG = "biglybt.tumblr.com";
	private static final String TUMBLR_APIKEY = "LTS3TRTjTmkt980WqLzZwU6JnJzHbAF0PtBApjiZZQFmN07tRA";

	long numNew = 0;

	private long postCount = 0;

	@SuppressWarnings("rawtypes")
	public ViewTitleInfoBetaP() {
		if (TUMBLR_DEVBLOG == null) {
			return; // :(
		}
		SimpleTimer.addEvent("devblog", SystemTime.getCurrentTime(),
				new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						long lastPostCount = COConfigurationManager.getLongParameter(
								PARAM_LASTPOSTCOUNT, 0);
						PluginInterface pi = PluginInitializer.getDefaultInterface();
						try {
							ResourceDownloader rd = pi.getUtilities().getResourceDownloaderFactory().create(
									new URL(
											"http://api.tumblr.com/v2/blog/" + TUMBLR_DEVBLOG +"/info?api_key=" + TUMBLR_APIKEY));
							InputStream download = rd.download();
							Map json = JSONUtils.decodeJSON(FileUtil.readInputStreamAsString(
									download, 65535));
							Map mapResponse = MapUtils.getMapMap(json, "response", null);
							if (mapResponse != null) {
								Map mapBlog = MapUtils.getMapMap(mapResponse, "blog", null);
								if (mapBlog != null) {
									postCount = MapUtils.getMapLong(mapBlog, "posts", 0);
									numNew = postCount - lastPostCount;
									ViewTitleInfoManager.refreshTitleInfo(ViewTitleInfoBetaP.this);
								}
							}

						} catch (Exception e) {
						}
					}
				});
	}

	@Override
	public Object getTitleInfoProperty(int propertyID) {
		if (propertyID == TITLE_INDICATOR_TEXT && numNew > 0) {
			return "" + numNew;
		}
		return null;
	}

	public void clearIndicator() {
		COConfigurationManager.setParameter(PARAM_LASTPOSTCOUNT, postCount);
		numNew = 0;
	}

	public static void setupSidebarEntry(final MultipleDocumentInterface mdi) {
		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {

						final ViewTitleInfoBetaP viewTitleInfo = new ViewTitleInfoBetaP();

						MdiEntry entry = mdi.createEntryFromSkinRef(
								MultipleDocumentInterface.SIDEBAR_HEADER_VUZE,
								MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM,
								"main.area.beta", "{Sidebar.beta.title}", viewTitleInfo, null,
								true, MultipleDocumentInterface.SIDEBAR_POS_FIRST);

						entry.setImageLeftID("image.sidebar.beta");

						entry.addListener(new MdiCloseListener() {
							@Override
							public void mdiEntryClosed(MdiEntry entry, boolean userClosed) {
								viewTitleInfo.clearIndicator();
							}
						});

						return entry;
					}
				});
	}
}
