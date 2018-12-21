/*
 * Created on 29 nov. 2004
 * Created by Olivier Chalouhi
 * 
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
package com.biglybt.ui.swt.plugininstaller;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.installer.*;
import com.biglybt.ui.swt.wizard.*;


/**
 * @author Olivier Chalouhi
 *
 */
public class InstallPluginWizard extends Wizard {
  
  List<InstallablePlugin> plugins = new ArrayList<InstallablePlugin>();
  
  boolean shared = false;
    
  
  public InstallPluginWizard()
	{
		super("installPluginsWizard.title");			
				
		IPWFilePanel file_panel = new IPWFilePanel(this,null);
	
		setFirstPanel(file_panel);
	}
  	
  	public void 
	onClose() 
	{
		// Call the parent class to clean up resources
		super.onClose();	
	}
  	
  	public void 
	setPluginList(List<InstallablePlugin> _plugins) {
  	  plugins = _plugins;
  	}
  	
  	public List<InstallablePlugin>
  	getPluginList()
  	{
  		return( plugins );
  	}
  	
  	public void performInstall() 
  	{
   	  InstallablePlugin[]	ps = new InstallablePlugin[ plugins.size()];
  	  
  	  plugins.toArray( ps );
  	  
  	  if ( ps.length > 0 ){
  	  	
  	    try{
  	    	
  	      ps[0].getInstaller().install(ps,shared);
  	      
  	    }catch(Exception e){
  	    	
  	      Debug.printStackTrace(e);
  	      
  	      Logger.log(new LogAlert(LogAlert.REPEATABLE,
						"Failed to initialise installer", e));
  	    }
  	  }
  	}
}
