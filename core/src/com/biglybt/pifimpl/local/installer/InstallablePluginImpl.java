/*
 * Created on 01-Dec-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pifimpl.local.installer;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.InstallablePlugin;
import com.biglybt.pif.installer.PluginInstallationListener;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pifimpl.update.PluginUpdatePlugin;

import java.util.Map;

/**
 * @author parg
 *
 */

public abstract class
InstallablePluginImpl
	implements InstallablePlugin
{
	private PluginInstallerImpl		installer;


	protected
	InstallablePluginImpl(
		PluginInstallerImpl		_installer )
	{
		installer = _installer;
	}

	/**
	 * Returns the plugin's interface if already installed, null if it isn't
	 * @return
	 */

	@Override
	public boolean
	isAlreadyInstalled()
	{
		PluginInterface pi = getAlreadyInstalledPlugin();

		if ( pi == null ){

			return( false );
		}

		String version = getVersion();

		if ( version == null || version.length() == 0 ){

			return( false );
		}

		String existing_version = pi.getPluginVersion();

			// this is the case when running with plugin in eclipse

		if ( existing_version == null ){

			return( true );
		}

		return( Constants.compareVersions( existing_version, version ) >= 0);
	}

	@Override
	public PluginInterface
	getAlreadyInstalledPlugin()
	{
		return( installer.getAlreadyInstalledPlugin( getId()));
	}

	@Override
	public void
	install(
		boolean		shared )

		throws PluginException
	{
		installer.install( this, shared );
	}

	@Override
	public void install(
		boolean shared,
		boolean low_noise,
		boolean wait_until_done)
		throws PluginException
	{
		install(shared, low_noise, wait_until_done, null);
	}

	@Override
	public void
	install(
		boolean				shared,
		boolean				low_noise,
		final boolean		wait_until_done,
		Map<Integer, Object> properties )

		throws PluginException
	{
		final AESemaphore sem = new AESemaphore( "FPI" );

		final PluginException[]	error = { null };

		installer.install(
			new InstallablePlugin[]{ this },
			shared,
			low_noise,
				properties,
			new PluginInstallationListener()
			{
				public boolean cancelled;

				@Override
				public void
				completed()
				{
					sem.release();
				}

				@Override
				public void
				cancelled()
				{
					cancelled = true;
					error[0] = new PluginException( "Install cancelled" );

					sem.release();
				}

				@Override
				public void
				failed(
					PluginException e )
				{
					error[0] = e;

					sem.release();

					if ( !wait_until_done && !cancelled ){

						Debug.out( "Install failed", e );
					}
				}
			});

		if ( wait_until_done ){

			sem.reserve();

			if ( error[0] != null ){

				throw( error[0] );
			}
		}
	}

	@Override
	public void
	uninstall()

		throws PluginException
	{
		installer.uninstall( this );
	}

	@Override
	public PluginInstaller
	getInstaller()
	{
		return( installer );
	}

	public abstract void
	addUpdate(
			UpdateCheckInstance	inst,
			PluginUpdatePlugin	plugin_update_plugin,
			Plugin plugin,
			PluginInterface plugin_interface );
}
