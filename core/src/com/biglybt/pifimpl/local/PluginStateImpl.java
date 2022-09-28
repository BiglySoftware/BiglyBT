/**
 * File: PluginStateImpl.java
 * Date: 19 Aug 2008
 * Author: Allan Crooks
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the COPYING file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.pifimpl.local;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginState;
import com.biglybt.pif.UnloadablePlugin;
import com.biglybt.pifimpl.local.installer.PluginInstallerImpl;
import com.biglybt.update.UpdaterUtils;

public class PluginStateImpl implements PluginState {

	private PluginInterfaceImpl pi;
	private PluginInitializer initialiser;
	private boolean	disabled;
	boolean operational;
	boolean failed;
	boolean restart_pending;
	
	public PluginStateImpl(PluginInterfaceImpl pi, PluginInitializer initialiser) {
		this.pi = pi;
		this.initialiser = initialiser;
	}

	@Override
	public void setLoadedAtStartup(boolean load_at_startup) {
		String param_name = "PluginInfo." + pi.getPluginID() + ".enabled";
		COConfigurationManager.setParameter(param_name, load_at_startup);
	}

	@Override
	public boolean isLoadedAtStartup() {
		String param_name = "PluginInfo." + pi.getPluginID() + ".enabled";
		if (!COConfigurationManager.hasParameter(param_name, false)) {
			return true; // Load at startup by default.
		}
		return COConfigurationManager.getBooleanParameter(param_name);
	}

	@Override
	public boolean hasFailed() {
		return failed;
	}

	@Override
	public void	setDisabled(boolean	_disabled) {
		disabled = _disabled;
	}

	@Override
	public boolean isDisabled()	{
		return disabled;
	}

	@Override
	public boolean isBuiltIn() {
		String dir = pi.getPluginDirectoryName();
		if (dir == null) {
			return PluginInitializer.isLoadingBuiltin();
		}
		return(
			dir.length() == 0 ||
			pi.getPluginID().equals( UpdaterUtils.AZUPDATER_PLUGIN_ID ) ||
			pi.getPluginID().equals( UpdaterUtils.AZUPDATERPATCHER_PLUGIN_ID ));
	}

	@Override
	public boolean isMandatory() {
		if ( 	pi.getPluginID().equals( UpdaterUtils.AZUPDATER_PLUGIN_ID ) ||
				pi.getPluginID().equals( UpdaterUtils.AZUPDATERPATCHER_PLUGIN_ID )){

			return( true );
		}

		String mand = pi.getPluginProperties().getProperty("plugin.mandatory");
		return (mand != null && mand.trim().toLowerCase().equals("true"));
	}

  	void setOperational(boolean b, boolean reloading ) {
  		operational	= b;

  		if ( !reloading ){

  			initialiser.fireOperational( pi, operational );
  		}
  	}

    @Override
    public boolean isOperational() {
    	return operational;
    }

	@Override
	public boolean isShared() {
		String shared_dir = FileUtil.getApplicationFile("plugins").toString();
		String plugin_dir = pi.getPluginDirectoryName();
		return plugin_dir.startsWith(shared_dir);
	}

	@Override
	public boolean
	isInitialisationComplete()
	{
		return( initialiser.isInitialisationComplete());
	}

	public boolean 
	isRestartPending()
	{
		return( restart_pending );
	}
	
	public void 
	setRestartPending( boolean b )
	{
		restart_pending = b;
	}
	  
	@Override
	public void reload() throws PluginException {
		// we use the "reload" method to load disabled plugins regardless of whether they are
		// unloadable. If currently disabled then no unloading to do anyway
		if (isUnloadable() || isOperational()) {unload( true );}
	  	initialiser.reloadPlugin(this.pi);
	}

	@Override
	public void	uninstall() throws PluginException {
		PluginInstallerImpl.getSingleton(pi.getPluginManager()).uninstall(this.pi);
	}

	@Override
	public boolean
	isUnloaded()
	{
		return( pi.class_loader == null );
	}

	@Override
	public void unload() throws PluginException {
		unload( false );
	}

	protected void unload( boolean for_reload ) throws PluginException {
		if (!isUnloadable()) {
			throw new PluginException("Plugin isn't unloadable");
		}

		String dir = pi.getPluginDirectoryName();

		// if not dir based then just test this one
		if (dir == null || dir.length() == 0) {
			try{
				((UnloadablePlugin)pi.getPlugin()).unload();
			}catch( Throwable e ){
				Debug.out( "Plugin unload operation failed", e );
			}
			initialiser.unloadPlugin(this.pi);
		} else {

			// we must copy the list here as when we unload interfaces they will be
			// removed from the original list
			List pis = new ArrayList(PluginInitializer.getPluginInterfaces());
			for (int i=0;i<pis.size();i++){
				PluginInterfaceImpl	pi = (PluginInterfaceImpl)pis.get(i);
				String other_dir = pi.getPluginDirectoryName();
		  		if (other_dir == null || other_dir.length() == 0) {continue;}
		  		if (dir.equals(other_dir)) {
		  			try{
		  				((UnloadablePlugin)pi.getPlugin()).unload();
		  			}catch( Throwable e ){
						Debug.out( "Plugin unload operation failed", e );
					}
		  			initialiser.unloadPlugin( pi );
		  		}
			}
		}

		for (int i=0;i<pi.children.size();i++){
			((PluginStateImpl)((PluginInterface)pi.children.get(i)).getPluginState()).unload( for_reload );
		}

		setOperational(false, for_reload );
		pi.destroy();
	}

	@Override
	public boolean isUnloadable() {
		String dir = pi.getPluginDirectoryName();

  		// mechanism to override unloadability
	   	boolean	disable_unload = pi.getPluginProperties().getProperty("plugin.unload.disabled", "").equalsIgnoreCase("true");
	  	if (disable_unload) {return false;}

	  	// if not dir based then just test this one
	  	if (dir == null || dir.length() == 0) {
		  	return pi.getPlugin() instanceof UnloadablePlugin;
		}

	  	List pis = PluginInitializer.getPluginInterfaces();
		for (int i=0;i<pis.size();i++) {
		  	PluginInterface	pi = (PluginInterface)pis.get(i);
	  		String other_dir = pi.getPluginDirectoryName();
	  		if (other_dir == null || other_dir.length() == 0) {continue;}
	  		if (dir.equals(other_dir)) {
		  		if (!(pi.getPlugin() instanceof UnloadablePlugin)) {
		  			return false;
		  		}
	  		}
		}

		for (int i=0;i<pi.children.size();i++){
			if (!((PluginInterface)pi.children.get(i)).getPluginState().isUnloadable()){
				return false;
		  	}
		}

	  	return true;
	}

}
