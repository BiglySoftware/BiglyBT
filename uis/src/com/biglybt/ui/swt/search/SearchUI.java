/*
 * Created on Dec 14, 2016
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

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.DataSourceResolver;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTInstance;

import java.util.Map;

import com.biglybt.core.metasearch.MetaSearchManager;
import com.biglybt.core.metasearch.MetaSearchManagerFactory;

public class
SearchUI
	implements DataSourceImporter
{
	private static final String CONFIG_SECTION_ID 	= "Search";

	private UIManager ui_manager;

	public
	SearchUI()
	{
		DataSourceResolver.registerExporter( this );
		
		final PluginInterface	default_pi = PluginInitializer.getDefaultInterface();

		ui_manager = default_pi.getUIManager();

		ui_manager.addUIListener(
			new UIManagerListener()
			{
				@Override
				public void
				UIAttached(
					UIInstance		instance )
				{
					if (!( instance instanceof UISWTInstance )){
						return;

					}

					Utilities utilities = default_pi.getUtilities();

					final DelayedTask dt = utilities.createDelayedTask(new Runnable()
					{
						@Override
						public void
						run()
						{
							Utils.execSWTThread(new AERunnable() {

								@Override
								public void
								runSupport()
								{
									delayedInit();
								}
							});
						}
					});

					dt.queue();
				}

				@Override
				public void UIDetached(UIInstance instance) {
				}
			});
	}
	
	public Object
	importDataSource(
		Map<String,Object>		map )
	{
		String	term = (String)map.get( "term" );
		
		boolean toSubscribe = ((Long)map.get( "toSubscribe" )) != 0;
		
		return( new SearchResultsTabArea.SearchQuery(term, toSubscribe));
	}

	private void
	delayedInit()
	{
		final MetaSearchManager manager = MetaSearchManagerFactory.getSingleton();

		if ( manager == null ){

			return;
		}

		BasicPluginConfigModel configModel = ui_manager.createBasicPluginConfigModel(
				ConfigSection.SECTION_ROOT, CONFIG_SECTION_ID);

			// Tor proxy enable

		BooleanParameter proxy_tor_enable =
			configModel.addBooleanParameter2(
				"search.proxy.enable", "search.proxy.enable",
				manager.getProxyRequestsEnabled() == MetaSearchManager.PROXY_TOR );
	
			// I2P proxy enable
			
		BooleanParameter proxy_i2p_enable =
				configModel.addBooleanParameter2(
					"search.proxy.i2p.enable", "search.proxy.i2p.enable",
					manager.getProxyRequestsEnabled() == MetaSearchManager.PROXY_I2P);

		ParameterListener listener = (n)->{
			
			boolean tor = proxy_tor_enable.getValue();
			
			boolean i2p = proxy_i2p_enable.getValue();
			
			int type;
			
			if ( tor ){
				
				type = MetaSearchManager.PROXY_TOR;
				
				proxy_i2p_enable.setValue( false );	// should be false anyway
				
				proxy_i2p_enable.setEnabled(false);
				
			}else if ( i2p ){
				
				type = MetaSearchManager.PROXY_I2P;
				
				proxy_tor_enable.setEnabled(false);
				
			}else{
				
				type = MetaSearchManager.PROXY_NONE;
				
				proxy_tor_enable.setEnabled(true);
				proxy_i2p_enable.setEnabled(true);
			}
			
			if ( n != null ){
				
				manager.setProxyRequestsEnabled( type );
			}
		};
			
		listener.parameterChanged( null );
		
		proxy_tor_enable.addListener(listener);
		proxy_i2p_enable.addListener(listener);

			// open rcm on search

		BooleanParameter showrcm = configModel.addBooleanParameter2("!search.showRCMView!", "search.showRCMView", false);
		showrcm.setAllowedUiTypes(UIInstance.UIT_SWT);

		configModel.addIntParameter2( "!search.rss.template.timeout!", "search.rss.template.timeout", 20 );
		
	}
}
