/*
 * Created on 27-Apr-2004
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

package com.biglybt.pifimpl.local.ui.model;

/**
 * @author parg
 *
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DataSourceResolver.ExportableDataSource;
import com.biglybt.core.util.DataSourceResolver.ExportedDataSource;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.ui.components.UIButton;
import com.biglybt.pif.ui.components.UIProgressBar;
import com.biglybt.pif.ui.components.UITextArea;
import com.biglybt.pif.ui.components.UITextField;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.pifimpl.local.ui.components.UIButtonImpl;
import com.biglybt.pifimpl.local.ui.components.UIProgressBarImpl;
import com.biglybt.pifimpl.local.ui.components.UITextAreaImpl;
import com.biglybt.pifimpl.local.ui.components.UITextFieldImpl;

public class
BasicPluginViewModelImpl
	implements BasicPluginViewModel, ExportableDataSource
{
	private UIManagerImpl		ui_manager;

	private String		name;

	private UITextField	status;
	private UITextField	activity;
	private UITextArea	log;
	private UIProgressBar	progress;
	private String sConfigSectionID;

	private List<UIButton>	buttons = new ArrayList<>();
	
	private Map<Integer,Object>	properties;
	
	public
	BasicPluginViewModelImpl(
		UIManagerImpl	_ui_manager,
		String			_name )
	{
		ui_manager	= _ui_manager;
		name		= _name;

		status 		= new UITextFieldImpl();
		activity	= new UITextFieldImpl();
		log			= new UITextAreaImpl();
		progress	= new UIProgressBarImpl();
	}
	
	@Override
	public ExportedDataSource 
	exportDataSource()
	{
		return( UIManagerImpl.exportDataSource( this ));
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public UITextField
	getStatus()
	{
		return( status );
	}

	@Override
	public UITextField
	getActivity()
	{
		return( activity );
	}

	@Override
	public UIButton 
	addButton()
	{
		UIButton res = new UIButtonImpl();
		
		buttons.add( res );
		
		return( res );
	}
	
	@Override
	public List<UIButton> 
	getButtons()
	{
		return( buttons );
	}
	
	@Override
	public PluginInterface
	getPluginInterface()
	{
		return( ui_manager.getPluginInterface());
	}

	@Override
	public UITextArea
	getLogArea()
	{
		return( log );
	}

	@Override
	public UIProgressBar
	getProgress()
	{
		return( progress );
	}

	@Override
	public void
	setConfigSectionID(String id)
	{
		sConfigSectionID = id;
	}

	@Override
	public String
	getConfigSectionID()
	{
		return sConfigSectionID;
	}

	public void
	setProperty(
		int		property,
		Object	value )
	{
		synchronized( this ){
			
			if ( properties == null ){
				
				properties = new HashMap<>();
			}
			
			properties.put( property, value);
		}
	}
	
	public Object
	getProperty(
		int		property )
	{
		synchronized( this ){
			
			if ( properties == null ){
				
				return( null );
			}
			
			return( properties.get( property ));
		}
	}
	
	@Override
	public void
	destroy()
	{
		ui_manager.destroy( this );
	}

	@Override
	public void attachLoggerChannel(LoggerChannel channel) {
		channel.addListener(new LoggerChannelListener() {
			@Override
			public void messageLogged(String message, Throwable t) {
				messageLogged(LoggerChannel.LT_ERROR, message, t);
			}
			@Override
			public void messageLogged(int logtype, String message) {
				messageLogged(logtype, message, null);
			}
			public void messageLogged(int logtype, String message, Throwable t) {
				String log_type_s = null;
				switch(logtype) {
					case LoggerChannel.LT_WARNING:
						log_type_s = "warning";
						break;
					case LoggerChannel.LT_ERROR:
						log_type_s = "error";
						break;
				}
				if (log_type_s != null) {
					String prefix = MessageText.getString("AlertMessageBox." + log_type_s);
					log.appendText("[" + prefix.toUpperCase() + "] ");
				}
				log.appendText(message + "\n");
				if (t != null) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					t.printStackTrace(pw);
					log.appendText(sw.toString() + "\n");
				}
			}
		});
	}
}
