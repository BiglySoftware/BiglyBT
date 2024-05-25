/*
 * Created on Jan 10, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.ui.swt.search;

import java.util.Locale;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.UIFunctionsManager;
import org.eclipse.swt.SWT;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateTab;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.webplugin.WebPlugin;

import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.views.skin.SkinViewManager;

public class
SearchHandler
{
	/**
	 * @author TuxPaper
	 * @created Nov 4, 2015
	 *
	 */
	private static final class ViewTitleInfoImplementation
		implements ViewTitleInfo, ObfuscateTab
	{
		@Override
		public Object getTitleInfoProperty(int propertyID) {
			if (propertyID == TITLE_TEXT) {
				SearchResultsTabArea searchClass = (SearchResultsTabArea) SkinViewManager.getByClass(SearchResultsTabArea.class);
				if (searchClass != null ){
					SearchResultsTabArea.SearchQuery sq = searchClass.getCurrentSearch();

					if ( sq != null ){

						return sq.term;
					}
				}
			}
			return null;
		}
		// @see com.biglybt.ui.swt.debug.ObfuscateTab#getObfuscatedHeader()
		@Override
		public String getObfuscatedHeader() {
			return "";
		}
	}

	public static void
	handleSearch(
		String		sSearchText,
		boolean		toSubscribe )
	{
		if ( !toSubscribe ){

			try{

				if ( 	COConfigurationManager.getBooleanParameter("rcm.overall.enabled",true) &&
						COConfigurationManager.getBooleanParameter( "Plugin.aercm.rcm.search.enable", false ) &&
						COConfigurationManager.getBooleanParameter("search.showRCMView") &&	
						CoreFactory.isCoreRunning()){

					final PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "aercm");

					if (	pi != null &&
							pi.getPluginState().isOperational() &&
							pi.getIPC().canInvoke("lookupByExpression", new Object[]{ "" })){

						Utils.getOffOfSWTThread(()->{
								try{pi.getIPC().invoke("lookupByExpression", new Object[]{ sSearchText });
								
								}catch( Throwable e ){
							
									Debug.out( e );
								}
						});
					}
				}
			}catch (Throwable e ){

				Debug.out(e);
			}
		}

		SearchResultsTabArea.SearchQuery sq = new SearchResultsTabArea.SearchQuery(
				sSearchText, toSubscribe);

		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		String id = MultipleDocumentInterface.SIDEBAR_SECTION_SEARCH + "." + Integer.toHexString(sSearchText.hashCode()) + (toSubscribe ? ".s" : "");
		MdiEntry existingEntry = mdi.getEntry(id);
		if (existingEntry != null) {
			SearchResultsTabArea searchClass = (SearchResultsTabArea) SkinViewManager.getByClass(SearchResultsTabArea.class);
			if (searchClass != null) {
				searchClass.anotherSearch(sSearchText, toSubscribe);
			}
			existingEntry.setDatasource(sq);
			mdi.showEntry(existingEntry);
			return;
		}

		final MdiEntry entry = mdi.createEntryFromSkinRef(
				MultipleDocumentInterface.SIDEBAR_HEADER_DISCOVERY, id,
				"main.area.searchresultstab", sSearchText, null, sq, true, MultipleDocumentInterface.SIDEBAR_POS_FIRST );
		if (entry != null) {
			entry.setImageLeftID("image.sidebar.search");
			entry.setDatasource(sq);
			entry.setViewTitleInfo(new ViewTitleInfoImplementation());
		}

		mdi.showEntryByID(id);

	}
}
