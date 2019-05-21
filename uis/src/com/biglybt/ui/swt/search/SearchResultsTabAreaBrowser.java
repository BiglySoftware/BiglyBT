/*
 * Created on Dec 7, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.ui.swt.search;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.browser.BrowserContext.loadingListener;
import com.biglybt.ui.swt.search.SearchResultsTabArea.SearchQuery;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectBrowser;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.util.UrlFilter;

public class
SearchResultsTabAreaBrowser
	implements SearchResultsTabAreaBase
{
	private static boolean							search_proxy_init_done;
	private static AEProxyFactory.PluginHTTPProxy	search_proxy;
	private static boolean							search_proxy_set;
	private static AESemaphore						search_proxy_sem = new AESemaphore( "sps" );

	private static List<SearchResultsTabAreaBrowser>	pending = new ArrayList<>();

	private static void
	initProxy()
	{
		synchronized( SearchResultsTabArea.class ){

			if ( search_proxy_init_done ){

				return;
			}

			search_proxy_init_done = true;
		}

		new AEThread2( "ST_test" )
		{
			@Override
			public void
			run()
			{
				try{

					String test_url = Constants.URL_WEBSEARCH.replaceAll("%s", "derp");

					try{
						URL url = new URL( test_url );

						url = UrlUtils.setProtocol( url, "https" );

						url = UrlUtils.setPort( url, 443 );

						boolean use_proxy = !COConfigurationManager.getStringParameter( "browser.internal.proxy.id", "none" ).equals( "none" );

						if ( !use_proxy ){

							Boolean looks_ok = AEProxyFactory.testPluginHTTPProxy( url, true, "Search Proxy" );

							use_proxy = looks_ok != null && !looks_ok;
						}

						if ( use_proxy ){

							search_proxy = AEProxyFactory.getPluginHTTPProxy( "search", url, true );

							if ( search_proxy != null ){

								UrlFilter.getInstance().addUrlWhitelist( "https?://" + ((InetSocketAddress)search_proxy.getProxy().address()).getAddress().getHostAddress() + ":?[0-9]*/.*" );
							}
						}
					}catch( Throwable e ){
					}
				}finally{

					List<SearchResultsTabAreaBrowser> to_redo = null;

					synchronized( SearchResultsTabArea.class ){

						search_proxy_set	= true;

						to_redo = new ArrayList<>( pending );

						pending.clear();
					}

					search_proxy_sem.releaseForever();

					for ( SearchResultsTabAreaBrowser area: to_redo ){

						try{
							try{
								area.browserSkinObject.setAutoReloadPending( false, search_proxy == null );

							}catch( Throwable e ){
							}

							if ( search_proxy != null ){

								SearchQuery sq = area.sq;

								if ( sq != null ){

									area.anotherSearch( sq );
								}
							}
						}catch( Throwable e ){
						}
					}
				}
			}
		}.start();
	}

	private static ParameterListener configProxyIDListener;

	static{
		configProxyIDListener = new ParameterListener() {
			@Override
			public void
			parameterChanged(
					String parameterName )
			{
				synchronized( SearchResultsTabArea.class ){

					if ( !search_proxy_init_done ){

						return;
					}

					search_proxy_init_done = false;

					search_proxy_set	= false;

					if ( search_proxy != null ){

						search_proxy.destroy();

						search_proxy = null;
					}
				}
			}
		};
	}

	private static AEProxyFactory.PluginHTTPProxy
	getSearchProxy(
		SearchResultsTabAreaBrowser		area )
	{
		initProxy();

		boolean force_proxy = !COConfigurationManager.getStringParameter( "browser.internal.proxy.id", "none" ).equals( "none" );

		search_proxy_sem.reserve( force_proxy?60*1000:2500 );

		synchronized( SearchResultsTabArea.class ){

			if ( search_proxy_set ){

				return( search_proxy );

			}else{

				pending.add( area );

				try{
					area.browserSkinObject.setAutoReloadPending( true, false );

				}catch( Throwable e ){
				}

				return( null );
			}
		}
	}

	private final SearchResultsTabArea	parent;

	private SWTSkinObjectBrowser	browserSkinObject;

	private SearchQuery		sq;

	protected
	SearchResultsTabAreaBrowser(
		SearchResultsTabArea		_parent )
	{
		parent	= _parent;
		COConfigurationManager.addParameterListener(
				"browser.internal.proxy.id",
				configProxyIDListener);
	}

	protected void
	init(
		SWTSkinObjectBrowser		_browserSkinObject )
	{
		browserSkinObject = _browserSkinObject;

		browserSkinObject.addListener(new SWTSkinObjectListener() {

			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == EVENT_SHOW) {
					browserSkinObject.removeListener(this);

					createBrowseArea(browserSkinObject);
				}
				return null;
			}
		});
	}

	/**
	 *
	 */
	private void createBrowseArea(SWTSkinObjectBrowser browserSkinObject) {
		this.browserSkinObject = browserSkinObject;


		browserSkinObject.addListener(new loadingListener() {
			@Override
			public void browserLoadingChanged(boolean loading, String url) {
				parent.setBusy( loading );
			}
		});
	}


	@Override
	public void
	anotherSearch(
		SearchQuery sq )
	{
		this.sq	= sq;

		String url = Constants.URL_WEBSEARCH.replaceAll("%s",
				Matcher.quoteReplacement(UrlUtils.encode(sq.term)));

		AEProxyFactory.PluginHTTPProxy proxy = getSearchProxy( this );

		if ( proxy != null ){

			url = proxy.proxifyURL( url );
		}

		if (Utils.isThisThreadSWT()) {
			try {
				final AESemaphore sem = new AESemaphore("brwoserWait");

  			final BrowserWrapper browser = browserSkinObject.getBrowser();
  			browser.addLocationListener(new LocationListener() {
  				@Override
				  public void changing(LocationEvent event) {
  				}

  				@Override
				  public void changed(LocationEvent event) {
  					browser.removeLocationListener(this);
					  sem.releaseForever();
  				}
  			});
  			browserSkinObject.getBrowser().setUrl("about:blank");
  			browserSkinObject.getBrowser().refresh();
  			browserSkinObject.getBrowser().update();

  			final String finalURL = url;
  			Utils.getOffOfSWTThread(new AERunnable() {
				  @Override
				  public void runSupport() {
					  sem.reserve(300);
					  browserSkinObject.setURL(finalURL);
				  }
			  });
			} catch (Throwable t) {

			}
		} else {
			browserSkinObject.setURL(url);
		}
	}

	// @see SearchResultsTabAreaBase#getResultCount()
	@Override
	public int getResultCount() {
		return 0;
	}

	// @see SearchResultsTabAreaBase#showView()
	@Override
	public void showView() {
	}

	// @see SearchResultsTabAreaBase#refreshView()
	@Override
	public void refreshView() {
	}

	// @see SearchResultsTabAreaBase#hideView()
	@Override
	public void hideView() {
	}

	public void destroy() {
		COConfigurationManager.removeParameterListener(
				"browser.internal.proxy.id",
				configProxyIDListener);
	}
}
