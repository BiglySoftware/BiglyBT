/*
 * Created on 15-Jul-2004
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

package com.biglybt.ui.webplugin;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.ConfigParameterListener;
import com.biglybt.pifimpl.remote.RPRequest;
import com.biglybt.pifimpl.remote.RPRequestAccessController;
import com.biglybt.pifimpl.remote.rpexceptions.RPMethodAccessDeniedException;

/**
 * @author parg
 *
 */

public class
WebPluginAccessController
    implements RPRequestAccessController
{
    protected boolean   view_mode;

    public
    WebPluginAccessController(
        final PluginInterface pi )
    {
        ConfigParameter mode_parameter = pi.getPluginconfig().getPluginParameter( WebPlugin.CONFIG_MODE );

        if ( mode_parameter == null ){

        	view_mode = true;

        }else{

        	mode_parameter.addConfigParameterListener(
        		new ConfigParameterListener()
        		{
        			@Override
			        public void
        			configParameterChanged(
        				ConfigParameter param )
        			{
        				setViewMode( pi );
        			}
        		});

        	setViewMode( pi );
        }
    }

    protected void
    setViewMode(
    	PluginInterface		pi )
    {
        String mode_str = pi.getPluginconfig().getPluginStringParameter( WebPlugin.CONFIG_MODE, ((WebPlugin)pi.getPlugin()).CONFIG_MODE_DEFAULT );

        view_mode = !mode_str.equalsIgnoreCase( WebPlugin.CONFIG_MODE_FULL );
    }

    public void
    checkUploadAllowed()
    {
        if ( view_mode ){
            throw new RPMethodAccessDeniedException();
        }
    }

    @Override
    public void
    checkAccess(
        String          name,
        RPRequest       request )
    {
        String  method = request.getMethod();

        // System.out.println( "request: " + name + "/" + method );

        if ( view_mode ){

            /*
            request: PluginInterface/getDownloadManager
            request: PluginInterface/getPluginconfig
            request: PluginConfig/getPluginStringParameter[String,String]
            request: DownloadManager/getDownloads
            request: PluginConfig/getPluginIntParameter[String,int]
            request: PluginInterface/getIPFilter
            request: PluginConfig/setPluginParameter[String,int]
            request: PluginConfig/save
            */

            boolean ok = false;

            if ( name.equals( "PluginInterface" )){

                ok  =   method.equals( "getPluginconfig" ) ||
                        method.equals( "getDownloadManager" ) ||
                        method.equals( "getIPFilter" );

            }else if ( name.equals( "DownloadManager" )){

                ok  =   method.equals( "getDownloads" );

            }else if ( name.equals( "PluginConfig" )){

                if (    method.startsWith( "getPlugin") ||
                        method.equals( "save" )){

                    ok  = true;

                }else if ( method.equals( "setPluginParameter[String,int]" )){

                    String  param = (String)request.getParams()[0];

                    ok = param.equals( "MDConfigModel:refresh_period" );
                }
            }


            if ( !ok ){
                throw new RPMethodAccessDeniedException();
            }
        }
    }
}
