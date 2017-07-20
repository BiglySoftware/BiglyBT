/*
 * Created on Apr 18, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.ui.swt.views.utils;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.Core;
import org.eclipse.swt.graphics.Image;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginEventListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableCellToolTipListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.utils.LocationProvider;
import com.biglybt.pif.utils.LocationProviderListener;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;

public class
LocProvUtils
	implements LocationProviderListener
{
	private static LocProvUtils	singleton;

	public static void
	initialise(
		Core core )
	{
		synchronized( LocProvUtils.class ){

			if ( singleton == null ){

				singleton = new LocProvUtils( core );
			}
		}
	}

	private Core core;
	private LocationProvider 	active_provider;

	private boolean				cl_installed;

	private List<TableColumn>	columns = new ArrayList<>();


	private
	LocProvUtils(
		Core _core )
	{
		core	= _core;

		PluginManager pm = core.getPluginManager();

		PluginInterface pi = pm.getDefaultPluginInterface();

		pi.getUtilities().addLocationProviderListener( this );

		PluginInterface cl_pi = pm.getPluginInterfaceByID( "CountryLocator" );

		if ( cl_pi != null && cl_pi.getPluginState().isOperational()){

			cl_installed = true;
		}

		pi.addEventListener(
			new PluginEventListener()
			{
				@Override
				public void
				handleEvent(
					PluginEvent	ev )
				{
					if ( ev.getType() == PluginEvent.PEV_PLUGIN_INSTALLED ){

						String id = (String)ev.getValue();

						if ( id.equals( "CountryLocator" )){

							cl_installed = true;

							removeColumns();
						}
					}else if ( ev.getType() == PluginEvent.PEV_PLUGIN_UNINSTALLED ){

						String id = (String)ev.getValue();

							// actually requires a restart but whatever, let's play nice

						if ( id.equals( "CountryLocator" )){

							cl_installed = false;

							addColumns();
						}
					}
				}
			});
	}

	@Override
	public void
	locationProviderAdded(
		LocationProvider	lp )
	{
		synchronized( this ){

			if ( active_provider == null ){

				if ( lp.hasCapabilities( LocationProvider.CAP_ISO3166_BY_IP |  LocationProvider.CAP_FLAG_BY_IP |  LocationProvider.CAP_COUNTY_BY_IP )){

					active_provider = lp;

					addColumns();
				}
			}
		}
	}

	@Override
	public void
	locationProviderRemoved(
		LocationProvider	lp )
	{
		synchronized( this ){

			if ( lp == active_provider ){

				active_provider = null;

				removeColumns();
			}
		}
	}

	private String
	getCountryCode(
		Peer		peer )
	{
		String[] details = PeerUtils.getCountryDetails( peer );

		return( details == null || details.length < 1?"":details[0] );
	}

	private String
	getCountryName(
		Peer		peer )
	{
		String[] details = PeerUtils.getCountryDetails( peer );

		return( details == null || details.length < 1?"":details[1] );
	}

	private void
	addColumns()
	{
		synchronized( this ){

			if ( cl_installed || active_provider == null ){

				return;
			}

			TableManager tm = core.getPluginManager().getDefaultPluginInterface().getUIManager().getTableManager();


			String [] peer_tables = new String[] {
					TableManager.TABLE_TORRENT_PEERS,
					TableManager.TABLE_ALL_PEERS,
			};

				// ISO3166

			for (int i=0; i<peer_tables.length; i++) {

				TableColumn iso3166Column =	tm.createColumn( peer_tables[i], "CountryCode");

				iso3166Column.initialize( TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST,	30, TableColumn.INTERVAL_INVALID_ONLY);

				iso3166Column.addCellRefreshListener(new TableCellRefreshListener() {
					@Override
					public void refresh(TableCell cell) {
						Peer peer = (Peer) cell.getDataSource();

						String s = getCountryCode( peer );

						if (!cell.setSortValue(s) && cell.isValid()){

							return;
						}

						cell.setText(s);
					}
				});

				tm.addColumn(iso3166Column);

				columns.add( iso3166Column );

					// Country Name
				TableColumn countryColumn = tm.createColumn( peer_tables[i], "Country");

				countryColumn.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_INVISIBLE, 80, TableColumn.INTERVAL_INVALID_ONLY);

				countryColumn.addCellRefreshListener(new TableCellRefreshListener() {
					@Override
					public void refresh(TableCell cell) {
						Peer peer = (Peer) cell.getDataSource();

						String s = getCountryName( peer );

						if (!cell.setSortValue(s) && cell.isValid()){
							return;
						}

						cell.setText(s);
					}
				});

				tm.addColumn(countryColumn);

				columns.add( countryColumn );

					// Small Flags

				TableColumn flagsColumn = tm.createColumn(peer_tables[i], "CountryFlagSmall");

				flagsColumn.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_INVISIBLE, 25, TableColumn.INTERVAL_INVALID_ONLY);

				flagsColumn.setType(TableColumn.TYPE_GRAPHIC);

				FlagListener flagListener = new FlagListener( true );

				flagsColumn.addCellRefreshListener(flagListener);

				flagsColumn.addCellToolTipListener(flagListener);

				tm.addColumn(flagsColumn);

				columns.add( flagsColumn );

					// Normal Flags

				flagsColumn = tm.createColumn(peer_tables[i], "CountryFlag");

				flagsColumn.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 25, TableColumn.INTERVAL_INVALID_ONLY);

				flagsColumn.setType(TableColumn.TYPE_GRAPHIC);

				flagListener = new FlagListener( false );

				flagsColumn.addCellRefreshListener(flagListener);

				flagsColumn.addCellToolTipListener(flagListener);

				tm.addColumn(flagsColumn);

				columns.add( flagsColumn );
			}
		}
	}

	private void
	removeColumns()
	{
		synchronized( this ){

			for ( TableColumn c: columns ){

				c.remove();
			}
		}
	}

	private class
	FlagListener
		implements TableCellRefreshListener, TableCellToolTipListener
	{
		private final boolean small;

		public
		FlagListener(
			boolean _small)
		{
			small = _small;
		}

		@Override
		public void
		refresh(
			TableCell cell)
		{
			Peer peer = (Peer) cell.getDataSource();

			Image image = ImageRepository.getCountryFlag(peer, small);

			String cc = getCountryCode(peer);

			if (!cell.setSortValue(cc) && cell.isValid()){

				return;
			}

			cell.setGraphic( new UISWTGraphicImpl( image ));
		}

		@Override
		public void
		cellHover(
			TableCell cell)
		{
			Peer peer = (Peer) cell.getDataSource();

			String[] details = PeerUtils.getCountryDetails( peer );

			if ( details == null || details.length < 2 ){

				cell.setToolTip( "" );

			}else{

				cell.setToolTip( details[0] + " - " + details[1] );
			}
		}

		@Override
		public void cellHoverComplete(TableCell cell) {
			cell.setToolTip(null);
		}
	}
}
