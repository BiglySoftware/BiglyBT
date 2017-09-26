/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.search;

import com.biglybt.core.*;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.swt.skin.*;

import java.util.*;

import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.*;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.core.util.DataSourceResolver.ExportedDataSource;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;


import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.views.skin.SkinView;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.*;

/**
 * @author TuxPaper
 * @created Sep 30, 2006
 *
 */
public class SearchResultsTabArea
	extends SkinView
	implements ViewTitleInfo
{
	private boolean					isBrowserView		= COConfigurationManager.getBooleanParameter( "Search View Is Web View", true );
	private boolean					isViewSwitchHidden	= COConfigurationManager.getBooleanParameter( "Search View Switch Hidden", false );


	private SWTSkinObjectBrowser browserSkinObject;
	private SWTSkinObjectContainer nativeSkinObject;

	private SWTSkin skin;
	private MdiEntry mdi_entry;
	private MdiEntryVitalityImage vitalityImage;

	private boolean menu_added;

	private SearchQuery 	current_sq;

	private SearchResultsTabAreaBase	activeImpl;

	private SearchResultsTabAreaBrowser	browserImpl = new SearchResultsTabAreaBrowser( this );
	private SBC_SearchResultsView	nativeImpl 	= new SBC_SearchResultsView( this );
	private SWTSkinObject soButtonWeb;
	private SWTSkinObject soButtonMeta;

	public
	SearchResultsTabArea()
	{
	}

	public static class SearchQuery implements DataSourceResolver.ExportableDataSource {
		public SearchQuery(String term, boolean toSubscribe) {
			this.term = term;
			this.toSubscribe = toSubscribe;
		}

		public String term;
		public boolean toSubscribe;
		
		public ExportedDataSource
		exportDataSource()
		{
			return(
				new ExportedDataSource()
				{
					public Class<? extends DataSourceImporter>
					getExporter()
					{
						return( SearchUI.class );
					}
					
					public Map<String,Object>
					getExport()
					{
						Map	map = new HashMap();
						
						map.put( "term", term );
						map.put( "toSubscribe", new Long( toSubscribe?1:0 ));
						
						return( map );
					}
				});
		}
	}

	@Override
	public Object
	skinObjectInitialShow(
		final SWTSkinObject 	skinObject,
		Object 					params )
	{
		skin = skinObject.getSkin();

		SWTSkinObjectContainer controlArea = (SWTSkinObjectContainer)skin.getSkinObject( "searchresultstop", skinObject);

		if ( controlArea != null ){

			if ( isViewSwitchHidden ){

				controlArea.setVisible( false );

			}else{
				Composite control_area = controlArea.getComposite();

				soButtonWeb = skin.getSkinObject( "searchresults-button-web", controlArea);
				soButtonMeta = skin.getSkinObject( "searchresults-button-meta", controlArea);

				SWTSkinButtonUtility btnWeb = new SWTSkinButtonUtility(soButtonWeb);
				btnWeb.addSelectionListener(new ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						isBrowserView = true;
						COConfigurationManager.setParameter( "Search View Is Web View", isBrowserView );
						selectView( skinObject );
					}
				});

				SWTSkinButtonUtility btnMeta = new SWTSkinButtonUtility(soButtonMeta);
				btnMeta.addSelectionListener(new ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						isBrowserView = false;
						COConfigurationManager.setParameter( "Search View Is Web View", isBrowserView );
						selectView( skinObject );
					}
				});

			}
		}

		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();

		if ( mdi != null ){

			mdi_entry = mdi.getEntryBySkinView(this);

			if ( mdi_entry != null ){

				mdi_entry.setViewTitleInfo( this );

				vitalityImage = mdi_entry.addVitalityImage("image.sidebar.vitality.dots");

				if ( vitalityImage != null ){

					vitalityImage.setVisible(false);
				}
			}
		}

		browserSkinObject 	= (SWTSkinObjectBrowser)skin.getSkinObject("web-search-results", skinObject);

		browserImpl.init( browserSkinObject );

		nativeSkinObject 	= (SWTSkinObjectContainer)skin.getSkinObject( "meta-search-results", skinObject);

		nativeImpl.skinObjectInitialShow( skinObject, params );

		selectView( skinObject );

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(final Core core) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						initCoreStuff(core);
					}

				});
			}
		});

		return null;
	}

	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		if ( activeImpl != null ){

			activeImpl.refreshView();
		}

		return( super.skinObjectShown(skinObject, params));
	}

	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		browserImpl.destroy();

		nativeImpl.skinObjectDestroyed( skinObject, params );

		return super.skinObjectDestroyed(skinObject, params);
	}

	private void
	selectView(
		SWTSkinObject		parent )
	{
		SearchResultsTabAreaBase newImpl = isBrowserView?browserImpl:nativeImpl;

		if (newImpl == activeImpl) {
			return;
		}

		Control[] kids = nativeSkinObject.getControl().getParent().getChildren();

		Control visible_parent = isBrowserView?browserSkinObject.getControl():nativeSkinObject.getControl();

		for ( Control kid: kids ){
			kid.setVisible( kid == visible_parent );
		}

		browserSkinObject.setVisible( isBrowserView );
		nativeSkinObject.setVisible( !isBrowserView );

		if (soButtonWeb != null) {
			soButtonWeb.switchSuffix(isBrowserView ? "-selected" : "");
		}
		if (soButtonMeta != null) {
			soButtonMeta.switchSuffix(isBrowserView ? "" : "-selected");
		}


		parent.relayout();

		if ( activeImpl != null ){

			activeImpl.hideView();
		}

		activeImpl = newImpl;

		activeImpl.showView();

		if ( current_sq != null ){

			anotherSearch( current_sq );
		}
	}

	private void initCoreStuff(Core core) {
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();

		final MenuManager menuManager = uim.getMenuManager();

		if ( !menu_added ){

			menu_added = true;

			SearchUtils.addMenus( menuManager );
		}
	}

	@Override
	public Object
	dataSourceChanged(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( params instanceof SearchQuery ){

			anotherSearch((SearchQuery)params);
		}

		return null;
	}

	public void
	anotherSearch(
		String 		searchText,
		boolean 	toSubscribe )
	{
		anotherSearch(new SearchQuery(searchText, toSubscribe));
	}

	public void
	anotherSearch(
		SearchQuery another_sq )
	{
		current_sq = another_sq;

		if ( activeImpl != null ){

			activeImpl.anotherSearch( current_sq );

			ViewTitleInfoManager.refreshTitleInfo( this );
		}
	}

	public SearchQuery
	getCurrentSearch()
	{
		return( current_sq );
	}

	@Override
	public Object
	getTitleInfoProperty(
		int 	pid )
	{
		SearchQuery	sq 						= current_sq;
		SearchResultsTabAreaBase	impl 	= activeImpl;

		if ( pid == TITLE_TEXT ){

			if ( sq != null ){

				return( sq.term );
			}
		}else if ( pid == TITLE_INDICATOR_TEXT ){

			if ( impl != null ){

				int results = impl.getResultCount();

				if ( results >= 0 ){

					return( String.valueOf( results ));
				}
			}
		}

		return( null );
	}

	protected void
	setBusy(
		boolean	busy )
	{
		if ( vitalityImage != null ){

			vitalityImage.setVisible( busy );
		}
	}

	protected void
	resultsFound()
	{
		ViewTitleInfoManager.refreshTitleInfo( this );
	}
}
