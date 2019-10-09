/*
 * Created on Aug 14, 2012
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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


package com.biglybt.ui.swt.core.nwmadmin;


import org.eclipse.swt.graphics.Image;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.impl.NetworkAdminImpl;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdater.UIUpdaterListener;
import com.biglybt.ui.config.ConfigSectionConnectionAdvanced;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTStatusEntry;
import com.biglybt.ui.swt.pif.UISWTStatusEntryListener;
import com.biglybt.ui.swt.views.stats.StatsView;

public class
NetworkAdminSWTImpl
{
	private final NetworkAdminImpl		network_admin;

	private UISWTStatusEntry 	status;

	private Image	icon_grey;
	private Image	icon_green;
	private Image	icon_yellow;
	private Image	icon_red;

	private Image	last_icon;
	private String	last_tip;

	private volatile boolean	is_visible;
	private UIUpdaterListener uiUpdaterListener;
	private ParameterListener configBindIPListener;

	public
	NetworkAdminSWTImpl(
		Core _core,
		NetworkAdminImpl		_network_admin )
	{
		network_admin	= _network_admin;

		final PluginInterface default_pi = PluginInitializer.getDefaultInterface();

		default_pi.getUIManager().addUIListener(
				new UIManagerListener()
				{
					@Override
					public void
					UIAttached(
						final UIInstance		instance )
					{
						if ( instance instanceof UISWTInstance ){

							UIFunctions uif = UIFunctionsManager.getUIFunctions();

							if ( uif != null ){

								uiUpdaterListener = new UIUpdaterListener() {
									@Override
									public void
									updateComplete(
											int count )
									{
										updateStatus();
									}
								};
								uif.getUIUpdater().addListener(uiUpdaterListener);
							}

							Utils.execSWTThread(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										ImageLoader imageLoader = ImageLoader.getInstance();

										icon_grey	 	= imageLoader.getImage( "st_net_grey" );
										icon_yellow 	= imageLoader.getImage( "st_net_yellow" );
										icon_green 		= imageLoader.getImage( "st_net_green" );
										icon_red		= imageLoader.getImage( "st_net_red" );

										final UISWTInstance	ui_instance = (UISWTInstance)instance;

										status	= ui_instance.createStatusEntry();

										status.setText( MessageText.getString( "label.routing" ));

										status.setImageEnabled( true );

										status.setImage( icon_grey );

										final String icon_param = "Show IP Bindings Icon";

										final MenuItem mi_show =
											default_pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"pairing.ui.icon.show" );

										mi_show.setStyle( MenuItem.STYLE_CHECK );
										mi_show.setData( false );

										mi_show.addListener(
												new MenuItemListener()
												{
													@Override
													public void
													selected(
														MenuItem			menu,
														Object 				target )
													{
														COConfigurationManager.setParameter( icon_param, false );
													}
												});

										configBindIPListener = new ParameterListener() {
											@Override
											public void
											parameterChanged(
													String parameterName)
											{
												String 	bind_ip 	= COConfigurationManager.getStringParameter("Bind IP", "").trim();

												is_visible	 =
														bind_ip.trim().length() > 0 &&
																COConfigurationManager.getBooleanParameter( icon_param );

												status.setVisible( is_visible );

												mi_show.setData(Boolean.valueOf(is_visible));

												if ( is_visible ){

													updateStatus();
												}
											}
										};
										COConfigurationManager.addAndFireParameterListeners(
											new String[]{ "Bind IP", icon_param },
												configBindIPListener);

										MenuItem mi_sep1 =
												default_pi.getUIManager().getMenuManager().addMenuItem(
																	status.getMenuContext(),
																	"sep1" );

										mi_sep1.setStyle( MenuItem.STYLE_SEPARATOR );

										MenuItem mi_reset =
											default_pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"menu.remove.net.binding" );

										mi_reset.addFillListener(
											new MenuItemFillListener() {

												@Override
												public void
												menuWillBeShown(
													MenuItem 	mi,
													Object 		data)
												{
													mi.setText(
														MessageText.getString(
															"menu.remove.net.binding",
															new String[]{
																	COConfigurationManager.getStringParameter( "Bind IP", "" )
															}));
												}
											});

										mi_reset.addListener(
											new MenuItemListener()
											{
												@Override
												public void
												selected(
													MenuItem			menu,
													Object 				target )
												{
													COConfigurationManager.setParameter( "Enforce Bind IP", false );
													COConfigurationManager.setParameter( "Bind IP", "" );
													COConfigurationManager.save();
												}
											});

										MenuItem mi_sep2 =
											default_pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"sep2" );

										mi_sep2.setStyle( MenuItem.STYLE_SEPARATOR );

										MenuItem mi_options =
											default_pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"MainWindow.menu.view.configuration" );

										mi_options.addListener(
											new MenuItemListener()
											{
												@Override
												public void
												selected(
													MenuItem			menu,
													Object 				target )
												{
													UIFunctions uif = UIFunctionsManager.getUIFunctions();

													if ( uif != null ){

														uif.getMDI().showEntryByID(
																MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
																ConfigSectionConnectionAdvanced.SECTION_ID);
													}
												}
											});


										UISWTStatusEntryListener click_listener =
											new UISWTStatusEntryListener()
										{
												@Override
												public void
												entryClicked(
													UISWTStatusEntry entry )
												{
													UIFunctions uif = UIFunctionsManager.getUIFunctions();

													if ( uif != null ){

														uif.getMDI().loadEntryByID( StatsView.VIEW_ID, true, false, "TransferStatsView" );
													}
												}
											};

										status.setListener( click_listener );
									}
								});
						}

					}

					@Override
					public void
					UIDetached(
						UIInstance		instance )
					{

						if ( instance instanceof UISWTInstance ){

							UIFunctions uif = UIFunctionsManager.getUIFunctions();

							if ( uif != null && uiUpdaterListener != null) {

								uif.getUIUpdater().removeListener(uiUpdaterListener);
								uiUpdaterListener = null;
							}

							if (status != null) {
								// menu items get destroyed with this call
								status.destroy();
								status = null;
							}

							if (configBindIPListener != null) {
								COConfigurationManager.removeParameterListener(
										"Show IP Bindings Icon", configBindIPListener);
								COConfigurationManager.removeParameterListener("Bind IP",
										configBindIPListener);
								configBindIPListener = null;
							}
						}

					}
				});
	}

	private void
	updateStatus()
	{
		if ( !is_visible ){

			return;
		}

		Image	icon;
		String	tip;

	    Object[] bs_status = network_admin.getBindingStatus();

	    int		bs_state 	= (Integer)bs_status[0];
	    tip					= (String)bs_status[1];

		if ( bs_state == NetworkAdminImpl.BS_INACTIVE ){

			icon 	= icon_grey;

		}else if ( bs_state == NetworkAdminImpl.BS_OK){

			icon 	= icon_green;

		}else if ( bs_state == NetworkAdminImpl.BS_WARNING ){

			icon 	= icon_yellow;

		}else{

			icon 	= icon_red;
		}

		if ( last_icon != icon || !tip.equals( last_tip )){

			final Image 	f_icon 	= icon;
			final String	f_tip	= tip;

			Utils.execSWTThread(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						last_icon 	= f_icon;
						last_tip	= f_tip;

						status.setImage( f_icon );

						Utils.setTT(status, f_tip );
					}
				});
		}
	}
}
