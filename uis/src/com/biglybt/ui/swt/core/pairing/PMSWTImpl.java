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


package com.biglybt.ui.swt.core.pairing;

import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.*;
import com.biglybt.ui.common.updater.UIUpdater.UIUpdaterListener;
import com.biglybt.ui.swt.Utils;
import org.eclipse.swt.graphics.Image;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTStatusEntry;
import com.biglybt.ui.swt.pif.UISWTStatusEntryListener;
import com.biglybt.ui.swt.auth.CryptoWindow;

import com.biglybt.core.pairing.PairingManager;
import com.biglybt.core.pairing.impl.PairingManagerImpl;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterface;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.security.CryptoManagerPasswordHandler;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.imageloader.ImageLoader;

public class
PMSWTImpl
	implements PairingManagerImpl.UIAdapter
{
	private UISWTStatusEntry 	status;

	private volatile Set<String>	local_addresses = new HashSet<>();

	private Image	icon_idle;
	private Image	icon_green;
	private Image	icon_red;

	private int		last_update_count;
	private Image	last_image;
	private String	last_tooltip_text	= "";

	private long	last_image_expiry_mono;
	private long	last_image_expiry_uc_min;
	private UIUpdater.UIUpdaterListener uiUpdaterListener;
	private ParameterListener iconEnableListener;

	@Override
	public void
	initialise(
		final PluginInterface			pi,
		final BooleanParameter			icon_enable )
	{
		final NetworkAdmin na = NetworkAdmin.getSingleton();

		na.addPropertyChangeListener(
			new NetworkAdminPropertyChangeListener()
			{
				@Override
				public void
				propertyChanged(
					String property)
				{
					if ( property == NetworkAdmin.PR_NETWORK_INTERFACES ){

						updateLocalAddresses( na );
					}
				}
			});

		updateLocalAddresses( na );

		pi.getUIManager().addUIListener(
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
											int count) {
										last_update_count = count;

										updateStatus(true);
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

										icon_idle 	= imageLoader.getImage( "pair_sb_idle" );
										icon_green 	= imageLoader.getImage( "pair_sb_green" );
										icon_red	= imageLoader.getImage( "pair_sb_red" );

										UISWTInstance	ui_instance = (UISWTInstance)instance;

										status	= ui_instance.createStatusEntry();

										last_tooltip_text = MessageText.getString( "pairing.ui.icon.tip" );

										status.setTooltipText( last_tooltip_text );

										status.setImageEnabled( true );

										status.setImage( icon_idle );

										last_image	= icon_idle;

										boolean	is_visible = icon_enable.getValue();

										status.setVisible( is_visible );

										if ( is_visible ){

											updateStatus( false );
										}

										final MenuItem mi_show =
											pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"pairing.ui.icon.show" );

										mi_show.setStyle( MenuItem.STYLE_CHECK );
										mi_show.setData(Boolean.valueOf(is_visible));

										mi_show.addListener(
												new MenuItemListener()
												{
													@Override
													public void
													selected(
														MenuItem			menu,
														Object 				target )
													{
														icon_enable.setValue( false );
													}
												});

										iconEnableListener = new ParameterListener() {
											@Override
											public void
											parameterChanged(
													Parameter param )
											{
												boolean is_visible = icon_enable.getValue();

												status.setVisible( is_visible );

												mi_show.setData(Boolean.valueOf(is_visible));

												if ( is_visible ){

													updateStatus( false );
												}
											}
										};
										icon_enable.addListener(iconEnableListener);


										MenuItem mi_pairing =
											pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"MainWindow.menu.pairing" );

										mi_pairing.addListener(
											new MenuItemListener()
											{
												@Override
												public void
												selected(
													MenuItem			menu,
													Object 				target )
												{
													UIFunctions uif = UIFunctionsManager.getUIFunctions();

													if ( uif == null ){

														Debug.out( "UIFunctions not available, can't open remote pairing window" );

													}else{

														uif.openRemotePairingWindow();
													}
												}
											});

										MenuItem mi_sep =
											pi.getUIManager().getMenuManager().addMenuItem(
																status.getMenuContext(),
																"" );

										mi_sep.setStyle( MenuItem.STYLE_SEPARATOR );

										MenuItem mi_options =
											pi.getUIManager().getMenuManager().addMenuItem(
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
																PairingManager.CONFIG_SECTION_ID );
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

														uif.getMDI().showEntryByID(
																MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
																PairingManager.CONFIG_SECTION_ID );
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

							if (icon_enable != null && iconEnableListener != null) {
								icon_enable.removeListener(iconEnableListener);
								iconEnableListener = null;
							}
						}

					}
				});
	}

	private void
	updateLocalAddresses(
		NetworkAdmin		network_admin )
	{
		NetworkAdminNetworkInterface[] interfaces = network_admin.getInterfaces();

		Set<String>	ias = new HashSet<>();

		for ( NetworkAdminNetworkInterface intf: interfaces ){

			NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

			for ( NetworkAdminNetworkInterfaceAddress address: addresses ){

				InetAddress ia = address.getAddress();

				ias.add( ia.getHostAddress());
			}
		}

		local_addresses = ias;
	}

	private final Map<String,RemoteHistory>	history_map	= new HashMap<>();

	@Override
	public void
	recordRequest(
		final String		name,
		final String		ip,
		final boolean		good )
	{
		Utils.execSWTThread(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					RemoteHistory entry = history_map.get( name );

					if ( entry == null ){

						entry = new RemoteHistory();

						history_map.put( name, entry );
					}

					entry.addRequest( ip, good );

					updateStatus( false );
				}
			});
	}

	private void
	updateStatus(
		boolean		update_completed )
	{
		final int RECORD_EXPIRY		= 60*60*1000;
		final int GOOD_EXPIRY		= 1*1000;
		final int BAD_EXPIRY		= 5*60*1000;
		final int MAX_IPS_PER_TYPE	= 10;
		final int MAX_TYPES			= 10;

		if ( status == null ){

			return;
		}

		long	now_mono = SystemTime.getMonotonousTime();

		if ( update_completed ){

			if ( last_image != icon_idle && last_update_count >= last_image_expiry_uc_min ){

				if ( now_mono >= last_image_expiry_mono ){

					last_image = icon_idle;

					status.setImage( icon_idle );
				}
			}
		}

		StringBuilder tooltip_text = new StringBuilder( 256 );

		tooltip_text.append( MessageText.getString( "pairing.ui.icon.tip" ));

		long newest_bad_mono	= -1;
		long newest_good_mono	= -1;

		Iterator<Map.Entry<String,RemoteHistory>>	it = history_map.entrySet().iterator();

		String	oldest_type			= null;
		long	oldest_type_mono	= Long.MAX_VALUE;

		int	records_added = 0;

		while( it.hasNext()){

			Map.Entry<String,RemoteHistory> entry = it.next();

			String			name 	= entry.getKey();
			RemoteHistory	history = entry.getValue();

			String	oldest_ip		= null;
			long	oldest_ip_mono	= Long.MAX_VALUE;

			Map<String,RemoteHistoryEntry> records = history.getEntries();

			Iterator<Map.Entry<String,RemoteHistoryEntry>>	record_it = records.entrySet().iterator();

			StringBuilder tt_ip_details = new StringBuilder( 256 );

			while( record_it.hasNext()){

				Map.Entry<String,RemoteHistoryEntry>	record = record_it.next();

				String				ip 	= record.getKey();
				RemoteHistoryEntry 	e 	= record.getValue();

				long e_mono = e.getLastReceivedMono();

				if ( e_mono < oldest_ip_mono ){

					oldest_ip_mono 	= e_mono;
					oldest_ip		= ip;
				}

				long age = now_mono - e_mono;

				if ( age > RECORD_EXPIRY ){

					record_it.remove();

				}else{

					String age_str = TimeFormatter.format( age/1000 );

					tt_ip_details.append( "\n        " );

					if ( local_addresses.contains( ip )){

						tt_ip_details.append(MessageText.getString("DHTView.db.local")).append(" (").append(ip).append(")");

					}else{

						tt_ip_details.append( ip );
					}

					if ( e.wasLastGood()){

						tt_ip_details.append( " OK" );

						newest_good_mono 	= Math.max( newest_good_mono, e_mono );

					}else{

						tt_ip_details.append(" ").append(MessageText.getString("label.access.denied"));

						newest_bad_mono 	= Math.max( newest_bad_mono, e_mono );
					}

					tt_ip_details.append(" - ").append(age_str).append(" ago");
				}
			}

			if ( records.size() == 0 ){

				it.remove();

			}else{

				if ( oldest_ip_mono < oldest_type_mono ){

					oldest_type_mono 	= oldest_ip_mono;
					oldest_type			= name;
				}

				if ( records.size() >= MAX_IPS_PER_TYPE ){

					records.remove( oldest_ip );

				}else{

					tooltip_text.append("\n    ").append(name);
					tooltip_text.append( tt_ip_details );

					records_added++;
				}
			}
		}

		if ( history_map.size() > MAX_TYPES ){

			history_map.remove( oldest_type );
		}

		if ( records_added == 0 ){

			tooltip_text.append("\n    ").append(MessageText.getString("pairing.ui.icon.tip.no.recent"));
		}

		String tooltip_text_str = tooltip_text.toString();

		if ( !tooltip_text_str.equals( last_tooltip_text )){

			last_tooltip_text = tooltip_text_str;

			status.setTooltipText( last_tooltip_text );
		}

		Image	target_image = null;

		long	age_newest_bad = now_mono - newest_bad_mono;

		if ( newest_bad_mono >= 0 && age_newest_bad <= BAD_EXPIRY ){

			target_image = icon_red;

			last_image_expiry_mono 		= newest_bad_mono + BAD_EXPIRY;
		}else{

			long	age_newest_good = now_mono - newest_good_mono;

			if ( newest_good_mono >= 0 && age_newest_good <= GOOD_EXPIRY ){

				target_image = icon_green;

				last_image_expiry_mono 		= age_newest_good + GOOD_EXPIRY;
			}
		}

		if ( target_image != null && target_image != last_image ){

			last_image = target_image;

			last_image_expiry_uc_min	= last_update_count + 2;

			status.setImage( target_image );
		}
	}

	@Override
	public char[]
	getSRPPassword()
	{
		CryptoWindow pw_win = new CryptoWindow( true );

		CryptoWindow.passwordDetails result =
			pw_win.getPassword(
				-1,
				CryptoManagerPasswordHandler.ACTION_PASSWORD_SET,
				true, "Change SRP Password");

		if ( result != null ){

			return( result.getPassword());
		}

		return( null );
	}

	private static class
	RemoteHistory
	{
		private final Map<String,RemoteHistoryEntry>	map = new HashMap<>();

		private void
		addRequest(
			String		ip,
			boolean		good )
		{
			RemoteHistoryEntry entry = map.get( ip );

			if ( entry == null ){

				entry = new RemoteHistoryEntry();

				map.put( ip, entry );
			}

			entry.update( good );
		}

		private Map<String,RemoteHistoryEntry>
		getEntries()
		{
			return( map );
		}
	}

	private static class
	RemoteHistoryEntry
	{
		private long		last_received_mono;
		private long		last_received_rtc;

		private int			request_count;
		private boolean		last_was_good;

		private long
		getLastReceivedMono()
		{
			return( last_received_mono );
		}

		private long
		getLastReceivedRTC()
		{
			return( last_received_rtc );
		}

		private int
		getRequestCount()
		{
			return( request_count );
		}

		private boolean
		wasLastGood()
		{
			return( last_was_good );
		}

		private void
		update(
			boolean	good )
		{
			last_received_mono	= SystemTime.getMonotonousTime();
			last_received_rtc	= SystemTime.getCurrentTime();

			request_count++;

			last_was_good	= good;
		}
	}
}
