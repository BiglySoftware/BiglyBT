/*
 * Created on Jan 30, 2008
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


package com.biglybt.plugin.net.netstatus.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.plugin.net.netstatus.NetStatusPlugin;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;

import com.biglybt.pif.ui.UIInstance;

public class
NetStatusPluginView
	implements UISWTViewEventListener
{
	private NetStatusPlugin	plugin;

	private boolean		created = false;

	private Composite	composite;
	private Button		start_button;
	private Button		cancel_button;
	private StyledText 	log;

	private int			selected_tests;

	private NetStatusPluginTester		current_test;

	private static final int LOG_NORMAL 	= 1;
	private static final int LOG_SUCCESS 	= 2;
	private static final int LOG_ERROR 		= 3;
	private static final int LOG_INFO 		= 4;

	private int	log_type	= LOG_NORMAL;

	/** Called via reflection from {@link NetStatusPlugin} */
	@SuppressWarnings("unused")
	public static void initSWTUI(UIInstance _ui) {
		UISWTInstance ui = (UISWTInstance) _ui;
		ui.registerView(UISWTInstance.VIEW_MAIN, ui.createViewBuilder(
				NetStatusPlugin.VIEW_ID, NetStatusPluginView.class));
	}

	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
		switch( event.getType() ){

			case UISWTViewEvent.TYPE_CREATE:{
				plugin = (NetStatusPlugin) event.getView().getPluginInterface().getPlugin();

				if ( created ){

					return( false );
				}

				created = true;

				break;
			}
			case UISWTViewEvent.TYPE_INITIALIZE:{

				initialise((Composite)event.getData());

				break;
			}
			case UISWTViewEvent.TYPE_CLOSE:
			case UISWTViewEvent.TYPE_DESTROY:{

				try{
					destroy();

				}finally{

					created = false;
				}

				break;
			}
		}

		return true;
	}

	protected void
	initialise(
		Composite	_composite )
	{
		composite	= _composite;

		Composite main = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		main.setLayout(layout);
		GridData grid_data = new GridData(GridData.FILL_BOTH );
		main.setLayoutData(grid_data);

			// control

		Composite control = new Composite(main, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = 4;
		layout.marginWidth = 4;
		control.setLayout(layout);

		Label info = new Label( control, SWT.NULL );
		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalSpan = 3;
		info.setLayoutData(grid_data);
		Messages.setLanguageText( info,  "label.test.internet" );

		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalSpan = 1;
		control.setLayoutData(grid_data);

		List<Button> buttons = new ArrayList<>();

				// start

			start_button = new Button( control, SWT.PUSH );

			buttons.add( start_button );

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

		 	buttons.add( cancel_button );

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

		 	Utils.makeButtonsEqualWidth( buttons );

			Group options = new Group(control, SWT.NONE);
			layout = new GridLayout();
			layout.numColumns 	 	= 4;
			layout.marginHeight 	= 4;
			layout.marginWidth 		= 4;
			options.setLayout(layout);
		 	Messages.setLanguageText( options, "label.test.types");


			grid_data = new GridData(GridData.FILL_HORIZONTAL);
		options.setLayoutData(grid_data);

			/*
				Button opt1 = new Button( options, SWT.CHECK );

				opt1.setText( "ping/route" );

				addOption( opt1, NetStatusPluginTester.TEST_PING_ROUTE );
			*/
				Button opt = new Button( options, SWT.CHECK );

				Messages.setLanguageText( opt, "label.outbound" );

				addOption( opt, NetStatusPluginTester.TEST_OUTBOUND, true );

				opt = new Button( options, SWT.CHECK );

				Messages.setLanguageText( opt, "label.inbound" );

				addOption( opt, NetStatusPluginTester.TEST_INBOUND, true );

				opt = new Button( options, SWT.CHECK );

				Messages.setLanguageText( opt, "label.nat.proxies" );

				addOption( opt, NetStatusPluginTester.TEST_NAT_PROXIES, true );

				opt = new Button( options, SWT.CHECK );

				Messages.setLanguageText( opt, "label.bt.connect" );

				addOption( opt, NetStatusPluginTester.TEST_BT_CONNECT, true );

				opt = new Button( options, SWT.CHECK );

				opt.setText( "IPv6" );

				boolean ipv6_enabled = COConfigurationManager.getBooleanParameter( "IPV6 Enable Support" );

				addOption( opt, NetStatusPluginTester.TEST_IPV6, ipv6_enabled );

				opt = new Button( options, SWT.CHECK );

				Messages.setLanguageText( opt, "label.vuze.services" );

				addOption( opt, NetStatusPluginTester.TEST_BIGLYBT_SERVICES, true );

				if ( Constants.isWindows || Constants.isOSX ){

					opt = new Button( options, SWT.CHECK );

					Messages.setLanguageText( opt, "label.indirect.connect" );

					boolean ic_enabled = AEProxyFactory.hasPluginProxy();

					addOption( opt, NetStatusPluginTester.TEST_PROXY_CONNECT, ic_enabled );
				}

			// log area

		log = new StyledText(main,SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		grid_data = new GridData(GridData.FILL_BOTH);
		grid_data.horizontalSpan = 1;
		grid_data.horizontalIndent = 4;
		log.setLayoutData(grid_data);
		log.setIndent( 4 );

		ClipboardCopy.addCopyToClipMenu(
				log,
				new ClipboardCopy.copyToClipProvider()
				{
					@Override
					public String
					getText()
					{
						return( log.getText().trim());
					}
				});
	}

	protected void
	addOption(
		final Button		button,
		final int			type,
		boolean				enable )
	{
		final String	config = "test.option." + type;

		boolean	selected = plugin.getBooleanParameter( config, enable );

		if ( selected && enable ){

			selected_tests |= type;

		}else{

			selected_tests &= ~type;
		}

		if ( !enable ){

			button.setEnabled( false );
		}

		button.setSelection( selected );

	 	button.addSelectionListener(
		 		new SelectionAdapter()
		 		{
		 			@Override
				  public void
		 			widgetSelected(
		 				SelectionEvent e )
		 			{
		 				boolean selected = button.getSelection();

		 				if ( selected ){

		 					selected_tests |= type;

		 				}else{

		 					selected_tests &= ~type;
		 				}

		 				plugin.setBooleanParameter( config, selected );
		 			}
		 		});
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
	startTestSupport(Core core)
	{
		try{
			synchronized( this ){

				if ( current_test != null ){

					Debug.out( "Test already running!!!!" );

					return;
				}

				current_test =
					new NetStatusPluginTester(
						plugin,
						selected_tests,
						new NetStatusPluginTester.loggerProvider()
						{
							@Override
							public void
							log(
								String 		str,
								boolean		detailed )
							{
								if ( detailed && !plugin.isDetailedLogging()){

									return;
								}

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
				Composite c = composite;

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
		plugin.log( str );

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

	protected void
	destroy()
	{
		cancelTest();

		composite = null;
	}
}
