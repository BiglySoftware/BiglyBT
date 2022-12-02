/*
 * Created on Feb 2, 2009
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


package com.biglybt.ui.swt.devices;

import com.biglybt.core.Core;
import com.biglybt.plugin.net.netstatus.NetStatusPlugin;
import com.biglybt.plugin.net.netstatus.swt.NetStatusPluginTester;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;

import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.devices.Device;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;

public class
DeviceInternetView
	extends DeviceManagerUI.categoryView
{
	private DeviceManagerUI		device_manager_ui;

	private static final int
		selected_tests = 	NetStatusPluginTester.TEST_INBOUND |
							NetStatusPluginTester.TEST_OUTBOUND |
							NetStatusPluginTester.TEST_NAT_PROXIES |
							NetStatusPluginTester.TEST_BIGLYBT_SERVICES |
							NetStatusPluginTester.TEST_PROXY_CONNECT;

	private static final int LOG_NORMAL 	= 1;
	private static final int LOG_SUCCESS 	= 2;
	private static final int LOG_ERROR 		= 3;
	private static final int LOG_INFO 		= 4;

	protected
	DeviceInternetView(
		DeviceManagerUI	dm_ui,
		String			title )
	{
		super( dm_ui, Device.DT_INTERNET, title );

		device_manager_ui	= dm_ui;
	}
	
	@Override
	public ViewInstance 
	createInstance(
		UISWTView view )
	{
		return( new DeviceInternetViewInstance());
	}

	private class
	DeviceInternetViewInstance
		implements ViewInstance
	{
		private Composite		main;

		private Button			start_button;
		private Button			cancel_button;
		private StyledText 		log;

		private NetStatusPluginTester		current_test;

		private int	log_type	= LOG_NORMAL;

		private NetStatusPlugin plugin;

		@Override
		public void
		initialize(
			Composite parent )
		{
			PluginInterface pi = device_manager_ui.getPluginInterface().getPluginManager().getPluginInterfaceByClass( NetStatusPlugin.class  );
	
			plugin = (NetStatusPlugin)pi.getPlugin();
	
			main = new Composite( parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginTop = 4;
			layout.marginBottom = 4;
			layout.marginHeight = 4;
			layout.marginWidth = 4;
			main.setLayout(layout);
			GridData grid_data = new GridData(GridData.FILL_BOTH );
			main.setLayoutData(grid_data);
	
			Label info_lab = new Label( main, SWT.NONE );
	
			Messages.setLanguageText( info_lab, "label.test.internet");
	
				// control
	
			Composite control = new Composite(main, SWT.NONE);
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginHeight = 4;
			layout.marginWidth = 4;
			control.setLayout(layout);
	
			grid_data = new GridData(GridData.FILL_HORIZONTAL);
			grid_data.horizontalSpan = 1;
			control.setLayoutData(grid_data);
	
					// start
	
				start_button = new Button( control, SWT.PUSH );
	
			 	Messages.setLanguageText( start_button, "ConfigView.section.start");
	
			 	start_button.addSelectionListener(
			 		new SelectionAdapter()
			 		{
			 			@Override
					  public void
			 			widgetSelected(
			 				SelectionEvent e )
			 			{
			 				start_button.setEnabled( false );
	
			 				cancel_button.setEnabled( true );
	
			 				startTest();
			 			}
			 		});
	
			 		// cancel
	
			 	cancel_button = new Button( control, SWT.PUSH );
	
			 	Messages.setLanguageText( cancel_button, "UpdateWindow.cancel");
	
			 	cancel_button.addSelectionListener(
			 		new SelectionAdapter()
			 		{
			 			@Override
					  public void
			 			widgetSelected(
			 				SelectionEvent e )
			 			{
			 				cancel_button.setEnabled( false );
	
			 				cancelTest();
			 			}
			 		});
	
			 	cancel_button.setEnabled( false );
	
	
				// log area
	
			log = new StyledText(main,SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
			grid_data = new GridData(GridData.FILL_BOTH);
			grid_data.horizontalSpan = 1;
			grid_data.horizontalIndent = 4;
			log.setLayoutData(grid_data);
			log.setIndent( 4 );
		}
	
		protected void
		startTest()
		{
			CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
					new CoreRunningListener() {
						@Override
						public void coreRunning(Core core) {
							startTestSupport(core);
						}
					});
		}
	
		protected void
		cancelTest()
		{
			new AEThread2( "NetStatus:cancel", true )
				{
					@Override
					public void
					run()
					{
						cancelTestSupport();
					}
				}.start();
		}
	
		protected void
		startTestSupport(
				Core core)
		{
			try{
				synchronized( this ){
	
					if ( current_test != null ){
	
						Debug.out( "Test already running!!!!" );
	
						return;
					}
	
					int tests = selected_tests;
	
					if ( NetworkAdmin.getSingleton().isIPV6Enabled()){
	
						tests |= NetStatusPluginTester.TEST_IPV6;
					}
	
					current_test =
						new NetStatusPluginTester(
							plugin,
							tests,
							new NetStatusPluginTester.loggerProvider()
							{
								@Override
								public void
								log(
									String 		str,
									boolean		detailed )
								{
									println( str );
								}
	
								@Override
								public void
								logSuccess(
									String str)
								{
									try{
										log_type = LOG_SUCCESS;
	
										println( str );
	
									}finally{
	
										log_type = LOG_NORMAL;
									}
								}
	
								@Override
								public void
								logInfo(
									String str)
								{
									try{
										log_type = LOG_INFO;
	
										println( str );
	
									}finally{
	
										log_type = LOG_NORMAL;
									}
								}
	
								@Override
								public void
								logFailure(
									String str)
								{
									try{
										log_type = LOG_ERROR;
	
										println( str );
	
									}finally{
	
										log_type = LOG_NORMAL;
									}
								}
							});
				}
	
				println( "Test starting", true );
	
				current_test.run(core);
	
				println( current_test.isCancelled()?"Test Cancelled":"Test complete" );
	
			}catch( Throwable e ){
	
			}finally{
	
				try{
					Composite c = main;
	
					if ( c != null && !c.isDisposed()){
	
						try{
							c.getDisplay().asyncExec(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										if ( !start_button.isDisposed()){
	
											start_button.setEnabled( true );
										}
	
										if ( !cancel_button.isDisposed()){
	
											cancel_button.setEnabled( false );
										}
									}
								});
	
						}catch( Throwable e ){
						}
					}
				}finally{
	
					synchronized( this ){
	
						current_test.cancel();
	
						current_test = null;
					}
				}
			}
		}
	
		protected void
		println(
			String		str )
		{
			print( str + "\n", false );
		}
	
		protected void
		println(
			String		str,
			boolean		clear_first )
		{
			print( str + "\n", clear_first );
		}
	
		protected void
		print(
			final String		str,
			final boolean		clear_first )
		{
			if ( !( log.isDisposed() || log.getDisplay().isDisposed())){
	
				final int f_log_type = log_type;
	
				log.getDisplay().asyncExec(
						new Runnable()
						{
							@Override
							public void
							run()
							{
								if ( log.isDisposed()){
	
									return;
								}
	
								int	start;
	
								if ( clear_first ){
	
									start	= 0;
	
									log.setText( str );
	
								}else{
	
									start = log.getText().length();
	
									log.append( str );
								}
	
								Color 	color;
	
								if ( f_log_type == LOG_NORMAL ){
	
									color = Utils.isDarkAppearanceNative()?null:Colors.black;
	
								}else if ( f_log_type == LOG_SUCCESS ){
	
									color = Colors.green;
	
								}else if ( f_log_type == LOG_INFO ){
	
									color = Colors.blues[Colors.BLUES_MIDDARK];
	
								}else{
	
									color = Colors.red;
								}
	
								StyleRange styleRange = new StyleRange();
								styleRange.start = start;
								styleRange.length = str.length();
								styleRange.foreground = color;
								log.setStyleRange(styleRange);
	
								log.setSelection( log.getText().length());
							}
						});
			}
		}
	
		protected void
		cancelTestSupport()
		{
			synchronized( this ){
	
				if ( current_test != null ){
	
					println( "Cancelling test..." );
	
					current_test.cancel();
				}
			}
		}
		
		@Override
		public void
		updateLanguage()
		{
			Messages.updateLanguageForControl( main );
		}
	}
}
