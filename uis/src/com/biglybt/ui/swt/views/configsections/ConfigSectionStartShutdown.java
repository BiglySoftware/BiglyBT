/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
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

package com.biglybt.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEJavaManagement;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigSectionStartShutdown implements UISWTConfigSection {
	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_ROOT;
	}

	@Override
	public String configSectionGetName() {
		return "startstop";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}

	@Override
	public int maxUserMode() {
		return 1;
	}


	@Override
	public Composite configSectionCreate(final Composite parent) {

		GridData gridData;
		GridLayout layout;
		Label label;

		final Composite cDisplay = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(cDisplay, gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cDisplay.setLayout(layout);

		final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		int userMode = COConfigurationManager.getIntParameter("User Mode");

			// ***** start group

		boolean can_ral = platform.hasCapability(PlatformManagerCapabilities.RunAtLogin );

		if ( can_ral || userMode > 0 ){

			Group gStartStop = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gStartStop, "ConfigView.label.start");
			layout = new GridLayout(2, false);
			gStartStop.setLayout(layout);
			Utils.setLayoutData(gStartStop, new GridData( GridData.FILL_HORIZONTAL ));

			if ( can_ral ){

				gridData = new GridData();
				gridData.horizontalSpan = 2;
				BooleanParameter start_on_login = new BooleanParameter(gStartStop, "Start On Login", "ConfigView.label.start.onlogin");

				try{
					start_on_login.setSelected( platform.getRunAtLogin());

					start_on_login.addChangeListener(
						new ParameterChangeAdapter()
						{
							@Override
							public void
							booleanParameterChanging(
								Parameter p,
								boolean toValue)
							{
								try{
									platform.setRunAtLogin( toValue );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						});

				}catch( Throwable e ){

					start_on_login.setEnabled( false );

					Debug.out( e );
				}

				start_on_login.setLayoutData(gridData);
			}

			if ( userMode > 0 ){

				gridData = new GridData();
				gridData.horizontalSpan = 2;

				BooleanParameter start_in_lr_mode = new BooleanParameter(gStartStop, "Start In Low Resource Mode", "ConfigView.label.start.inlrm");

				start_in_lr_mode.setLayoutData(gridData);
			}
		}

		if ( platform.hasCapability( PlatformManagerCapabilities.PreventComputerSleep )){

			Group gSleep = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gSleep, "ConfigView.label.sleep");
			layout = new GridLayout(2, false);
			gSleep.setLayout(layout);
			Utils.setLayoutData(gSleep, new GridData( GridData.FILL_HORIZONTAL ));

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			label = new Label(gSleep, SWT.NULL);
		    Messages.setLanguageText(label, "ConfigView.label.sleep.info");
		    Utils.setLayoutData(label,  gridData );

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			BooleanParameter no_sleep_dl = new BooleanParameter(gSleep, "Prevent Sleep Downloading", "ConfigView.label.sleep.download");
			no_sleep_dl.setLayoutData(gridData);

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			BooleanParameter no_sleep_se = new BooleanParameter(gSleep, "Prevent Sleep FP Seeding", "ConfigView.label.sleep.fpseed");
			no_sleep_se.setLayoutData(gridData);
		}

		if ( userMode > 0 ){

			Group gPR = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gPR, "ConfigView.label.pauseresume");
			layout = new GridLayout(2, false);
			gPR.setLayout(layout);
			Utils.setLayoutData(gPR, new GridData( GridData.FILL_HORIZONTAL ));

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			BooleanParameter pauseOnExit = new BooleanParameter(gPR,
					"Pause Downloads On Exit", "ConfigView.label.pause.downloads.on.exit");
			pauseOnExit.setLayoutData(gridData);

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			BooleanParameter resumeOnStart = new BooleanParameter(gPR,
					"Resume Downloads On Start", "ConfigView.label.resume.downloads.on.start");
			resumeOnStart.setLayoutData(gridData);
		}

		if ( userMode >= 0 ){

			Group gStop = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gStop, "ConfigView.label.stop");
			layout = new GridLayout(5, false);
			gStop.setLayout(layout);
			Utils.setLayoutData(gStop, new GridData( GridData.FILL_HORIZONTAL ));

				// done downloading

			addDoneDownloadingOption( gStop, true );

				// done seeding

			addDoneSeedingOption( gStop, true );

		    	// reset on trigger

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			BooleanParameter resetOnTrigger =
				new BooleanParameter(
					gStop, "Stop Triggers Auto Reset",
					"!" + MessageText.getString(
						"ConfigView.label.stop.autoreset",
						new String[]{ MessageText.getString( "ConfigView.label.stop.Nothing" )})+ "!");

			resetOnTrigger.setLayoutData(gridData);

		    	// prompt to allow abort

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			BooleanParameter enablePrompt =	new BooleanParameter( gStop, "Prompt To Abort Shutdown", "ConfigView.label.prompt.abort" );
			enablePrompt.setLayoutData(gridData);
		}

		if ( userMode > 0 ){

			Group gRestart = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gRestart, "label.restart");
			layout = new GridLayout(2, false);
			gRestart.setLayout(layout);
			Utils.setLayoutData(gRestart, new GridData( GridData.FILL_HORIZONTAL ));

			label = new Label(gRestart, SWT.NULL);
			Messages.setLanguageText(label, "ConfigView.label.restart.auto");

			new IntParameter(gRestart, "Auto Restart When Idle", 0, 100000 );
		}

		if ( userMode > 0 && platform.hasCapability( PlatformManagerCapabilities.AccessExplicitVMOptions )){

			Group gJVM = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gJVM, "ConfigView.label.jvm");
			layout = new GridLayout(2, false);
			gJVM.setLayout(layout);
			Utils.setLayoutData(gJVM, new GridData( GridData.FILL_HORIZONTAL ));

				// wiki link

			gridData = new GridData();
			gridData.horizontalSpan = 2;

			LinkLabel link = new LinkLabel(	gJVM, gridData, "ConfigView.label.please.visit.here",
					Constants.URL_WIKI + "w/Java_VM_memory_usage");

				// info

			label = new Label(gJVM, SWT.NULL);
			Messages.setLanguageText(label, "jvm.info");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(label,  gridData );

			try{
				final File option_file = platform.getVMOptionFile();

				final Group gJVMOptions = new Group(gJVM, SWT.NULL);
				layout = new GridLayout(3, false);
				gJVMOptions.setLayout(layout);
				gridData = new GridData( GridData.FILL_HORIZONTAL );
				gridData.horizontalSpan = 2;
				Utils.setLayoutData(gJVMOptions,  gridData );

				buildOptions( cDisplay, platform, gJVMOptions, false );

					// show option file

				label = new Label(gJVM, SWT.NULL);
				Messages.setLanguageText(label, "jvm.show.file", new String[]{ option_file.getAbsolutePath() });

				Button show_folder_button = new Button( gJVM, SWT.PUSH );

			 	Messages.setLanguageText( show_folder_button, "MyTorrentsView.menu.explore");

			 	show_folder_button.addSelectionListener(
			 		new SelectionAdapter()
			 		{
			 			@Override
					  public void
			 			widgetSelected(
			 				SelectionEvent e )
			 			{
			 				ManagerUtils.open( option_file );
			 			}
			 		});

			 	label = new Label(gJVM, SWT.NULL);
				Messages.setLanguageText(label, "jvm.reset");

				Button reset_button = new Button( gJVM, SWT.PUSH );

			 	Messages.setLanguageText( reset_button, "Button.reset");

			 	reset_button.addSelectionListener(
			 		new SelectionAdapter()
			 		{
			 			@Override
					  public void
			 			widgetSelected(
			 				SelectionEvent event )
			 			{
			 				try{
			 					platform.setExplicitVMOptions( new String[0] );

			 					buildOptions( cDisplay, platform, gJVMOptions, true );

			 				}catch( Throwable e ){

			 					Debug.out( e );
			 				}
			 			}
			 		});

			}catch( Throwable e ){

				Debug.out( e );

				label = new Label(gJVM, SWT.NULL);
				Messages.setLanguageText(label, "jvm.error", new String[]{ Debug.getNestedExceptionMessage(e) });
				gridData = new GridData();
				gridData.horizontalSpan = 2;
				Utils.setLayoutData(label,  gridData );
			}
		}

		return cDisplay;
	}

	private void
	buildOptions(
		final Composite			parent,
		final PlatformManager	platform,
		final Composite			area,
		boolean					rebuild )

		throws PlatformManagerException
	{
		if ( rebuild ){

			Control[] kids = area.getChildren();

			for ( Control k: kids ){
				k.dispose();
			}
		}

		String[] options = platform.getExplicitVMOptions();

		{
				// max mem

			long	max_mem = AEJavaManagement.getJVMLongOption( options, "-Xmx" );

			final int MIN_MAX_JVM = 32*1024*1024;

			GridData gridData = new GridData();
			Label label = new Label(area, SWT.NULL);
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label,	"jvm.max.mem", new String[]{encodeDisplayLong(MIN_MAX_JVM)});

			gridData = new GridData();
			gridData.widthHint = 125;
			final StringParameter max_vm = new StringParameter(area, "jvm.max.mem", "", false );
			max_vm.setLayoutData(gridData);

			max_vm.setValue( max_mem == -1?"":encodeDisplayLong( max_mem ));

			max_vm.addChangeListener(
				new ParameterChangeAdapter()
				{
					private String	last_value;

					@Override
					public void
					parameterChanged(
						Parameter	p,
						boolean		caused_internally )
					{
						if ( max_vm.isDisposed()){

							max_vm.removeChangeListener( this );

							return;
						}

						String val = max_vm.getValue();

						if ( last_value != null && last_value.equals( val )){

							return;
						}

						last_value = val;

						try{
							long max_mem = decodeDisplayLong( val );

							if ( max_mem < MIN_MAX_JVM ){

								throw( new Exception( "Min=" + encodeDisplayLong( MIN_MAX_JVM )));
							}

							String[] options = platform.getExplicitVMOptions();

							options = AEJavaManagement.setJVMLongOption( options, "-Xmx", max_mem );

							long	min_mem = AEJavaManagement.getJVMLongOption( options, "-Xms" );

							if ( min_mem == -1 || min_mem > max_mem ){

								options = AEJavaManagement.setJVMLongOption( options, "-Xms", max_mem );
							}

							platform.setExplicitVMOptions( options );

							buildOptions( parent, platform, area, true );

						}catch( Throwable e ){

							String param_name = MessageText.getString( "jvm.max.mem" );

							int	pos = param_name.indexOf( '[' );

							if ( pos != -1 ){

								param_name = param_name.substring( 0, pos ).trim();
							}

							MessageBoxShell mb =
								new MessageBoxShell(
									SWT.ICON_ERROR | SWT.OK,
									MessageText.getString( "ConfigView.section.invalid.value.title"),
									MessageText.getString(
										"ConfigView.section.invalid.value",
										new String[]{ val, param_name, Debug.getNestedExceptionMessage(e)}));

									mb.setParent( parent.getShell());
									mb.open(null);
						}
					}
				});

			label = new Label(area, SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(label, gridData);

			Long max_heap_mb = AEJavaManagement.getMaxHeapMB();

			if ( max_heap_mb > 0 ){

				Messages.setLanguageText(label,	"jvm.max.mem.current", new String[]{ DisplayFormatters.formatByteCountToKiBEtc( max_heap_mb*1024*1024, true )});
			}
		}

		{
				// min mem

			final int MIN_MIN_JVM = 8*1024*1024;

			long	min_mem = AEJavaManagement.getJVMLongOption( options, "-Xms" );

			GridData gridData = new GridData();
			Label label = new Label(area, SWT.NULL);
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label,	"jvm.min.mem", new String[]{encodeDisplayLong(MIN_MIN_JVM)});

			gridData = new GridData();
			gridData.widthHint = 125;
			final StringParameter min_vm = new StringParameter(area, "jvm.min.mem", "", false );
			min_vm.setLayoutData(gridData);

			min_vm.setValue( min_mem == -1?"":encodeDisplayLong( min_mem ));

			min_vm.addChangeListener(
				new ParameterChangeAdapter()
				{
					private String	last_value;

					@Override
					public void
					parameterChanged(
						Parameter	p,
						boolean		caused_internally )
					{
						if ( min_vm.isDisposed()){

							min_vm.removeChangeListener( this );

							return;
						}

						String val = min_vm.getValue();

						if ( last_value != null && last_value.equals( val )){

							return;
						}

						last_value = val;

						try{
							long min_mem = decodeDisplayLong( val );

							if ( min_mem < MIN_MIN_JVM ){

								throw( new Exception( "Min=" + encodeDisplayLong( MIN_MIN_JVM )));
							}

							String[] options = platform.getExplicitVMOptions();

							options = AEJavaManagement.setJVMLongOption( options, "-Xms", min_mem );

							long	max_mem = AEJavaManagement.getJVMLongOption( options, "-Xmx" );

							if ( max_mem == -1 || max_mem < min_mem ){

								options = AEJavaManagement.setJVMLongOption( options, "-Xmx", min_mem );
							}

							platform.setExplicitVMOptions( options );

							buildOptions( parent, platform, area, true );

						}catch( Throwable e ){

							String param_name = MessageText.getString( "jvm.min.mem" );

							int	pos = param_name.indexOf( '[' );

							if ( pos != -1 ){

								param_name = param_name.substring( 0, pos ).trim();
							}

							MessageBoxShell mb =
								new MessageBoxShell(
									SWT.ICON_ERROR | SWT.OK,
									MessageText.getString( "ConfigView.section.invalid.value.title"),
									MessageText.getString(
										"ConfigView.section.invalid.value",
										new String[]{ val, param_name, Debug.getNestedExceptionMessage(e)}));

									mb.setParent( parent.getShell());
									mb.open(null);
						}
					}
				});

			label = new Label(area, SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(label, gridData);
		}

		{
				// max DIRECT mem

			final int MIN_DIRECT_JVM = 32*1024*1024;

			final String OPTION_KEY = "-XX:MaxDirectMemorySize=";

			long	max_direct = AEJavaManagement.getJVMLongOption( options, OPTION_KEY );

			GridData gridData = new GridData();
			Label label = new Label(area, SWT.NULL);
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label,	"jvm.max.direct.mem", new String[]{encodeDisplayLong(MIN_DIRECT_JVM)});

			gridData = new GridData();
			gridData.widthHint = 125;
			final StringParameter max_direct_vm = new StringParameter(area, "jvm.max.direct.mem", "", false );
			max_direct_vm.setLayoutData(gridData);

			max_direct_vm.setValue( max_direct == -1?"":encodeDisplayLong( max_direct ));

			max_direct_vm.addChangeListener(
				new ParameterChangeAdapter()
				{
					private String	last_value;

					@Override
					public void
					parameterChanged(
						Parameter	p,
						boolean		caused_internally )
					{
						if ( max_direct_vm.isDisposed()){

							max_direct_vm.removeChangeListener( this );

							return;
						}

						String val = max_direct_vm.getValue();

						if ( last_value != null && last_value.equals( val )){

							return;
						}

						last_value = val;

						try{
							long max_direct = decodeDisplayLong( val );

							if ( max_direct < MIN_DIRECT_JVM ){

								throw( new Exception( "Min=" + encodeDisplayLong( MIN_DIRECT_JVM )));
							}

							String[] options = platform.getExplicitVMOptions();

							options = AEJavaManagement.setJVMLongOption( options, OPTION_KEY, max_direct );

							platform.setExplicitVMOptions( options );

							buildOptions( parent, platform, area, true );

						}catch( Throwable e ){

							String param_name = MessageText.getString( "jvm.max.direct.mem" );

							int	pos = param_name.indexOf( '[' );

							if ( pos != -1 ){

								param_name = param_name.substring( 0, pos ).trim();
							}

							MessageBoxShell mb =
								new MessageBoxShell(
									SWT.ICON_ERROR | SWT.OK,
									MessageText.getString( "ConfigView.section.invalid.value.title"),
									MessageText.getString(
										"ConfigView.section.invalid.value",
										new String[]{ val, param_name, Debug.getNestedExceptionMessage(e)}));

									mb.setParent( parent.getShell());
									mb.open(null);
						}
					}
				});

			label = new Label(area, SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label,	"jvm.max.direct.mem.info" );
		}

			// all options

		Label label = new Label(area, SWT.NULL);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(label, gridData);
		Messages.setLanguageText(label,	"jvm.options.summary" );

		for ( String option: options ){

			label = new Label(area, SWT.NULL);
			label.setText( option );
			gridData = new GridData( );
			gridData.horizontalSpan = 3;
			gridData.horizontalIndent = 20;
			Utils.setLayoutData(label,  gridData );
		}

		if ( rebuild ){

			parent.layout( true, true );
		}
	}

	private static  String
	encodeDisplayLong(
		long		val )
	{
		if ( val < 1024 ){

			return( String.valueOf( val ));
		}

		val = val/1024;

		if ( val < 1024 ){

			return( String.valueOf( val ) + " KB" );
		}

		val = val/1024;

		if ( val < 1024 ){

			return( String.valueOf( val ) + " MB" );
		}

		val = val/1024;

		return( String.valueOf( val ) + " GB" );
	}

	private static  long
	decodeDisplayLong(
		String		val )

		throws Exception
	{
		char[] chars = val.trim().toCharArray();

		String	digits = "";
		String	units = "";

		for ( char c: chars ){

			if ( Character.isDigit( c )){

				if ( units.length() > 0 ){

					throw( new Exception( "Invalid unit" ));
				}

				digits += c;

			}else{

				if ( digits.length() == 0 ){

					throw( new Exception( "Missing digits" ));

				}else if ( units.length() == 0 && Character.isWhitespace( c )){

				}else{

					units += c;
				}
			}
		}

		long value = Long.parseLong( digits );

		if( units.length() == 0 ){

			units = "m";
		}

		if ( units.length() > 0 ){

			char c = Character.toLowerCase( units.charAt(0));

			if ( c == 'k' ){

				value = value * 1024;

			}else if ( c == 'm' ){

				value = value * 1024 * 1024;

			}else if ( c == 'g' ){

				value = value * 1024 * 1024 * 1024;

			}else{

				throw( new Exception( "Invalid size unit '" + units + "'" ));
			}
		}

		return( value );
	}

	private static String[][]
	getActionDetails()
	{
		final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

	    int	shutdown_types = platform.getShutdownTypes();

		List<String>	l_action_values = new ArrayList<>();
		List<String>	l_action_descs 	= new ArrayList<>();

		l_action_values.add( "Nothing" );
		l_action_values.add( "QuitVuze" );

		if (( shutdown_types & PlatformManager.SD_SLEEP ) != 0 ){

			l_action_values.add( "Sleep" );
		}
		if (( shutdown_types & PlatformManager.SD_HIBERNATE ) != 0 ){

			l_action_values.add( "Hibernate" );
		}
		if (( shutdown_types & PlatformManager.SD_SHUTDOWN ) != 0 ){

			l_action_values.add( "Shutdown" );
		}

		l_action_values.add( "RunScript" );
		l_action_values.add( "RunScriptAndClose" );

		String[] action_values = l_action_values.toArray( new String[ l_action_values.size()]);

		for ( String s: action_values ){

			l_action_descs.add( MessageText.getString( "ConfigView.label.stop." + s ));
		}

		String[] action_descs = l_action_descs.toArray( new String[ l_action_descs.size()]);

		return( new String[][]{ action_descs, action_values });
	}

	public static void
	addDoneDownloadingOption(
		Composite		comp,
		boolean			include_script_setting )
	{
		GridData gridData = new GridData();
		Label label = new Label(comp, SWT.NULL);
	    Messages.setLanguageText(label, "ConfigView.label.stop.downcomp");
	    Utils.setLayoutData(label,  gridData );

	    String[][]	action_details = getActionDetails();

		final StringListParameter dc = new StringListParameter(comp, "On Downloading Complete Do", "Nothing", action_details[0], action_details[1] );

		if ( include_script_setting ){

			final Label dc_label = new Label(comp, SWT.NONE);
			Messages.setLanguageText(dc_label, "label.script.to.run");
			Utils.setLayoutData(dc_label, new GridData());

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			final FileParameter dc_script = new FileParameter(comp, "On Downloading Complete Script", "", new String[0]);
			dc_script.setLayoutData(gridData);

			boolean	is_script = dc.getValue().startsWith( "RunScript" );

			dc_label.setEnabled( is_script );
			dc_script.setEnabled( is_script );

			dc.addChangeListener(
				new ParameterChangeAdapter()
				{
					@Override
					public void
					parameterChanged(
						Parameter	p,
						boolean		caused_internally )
					{
						boolean	is_script = dc.getValue().startsWith( "RunScript" );

						dc_label.setEnabled( is_script );
						dc_script.setEnabled( is_script );
					}
				});
		}
	}

	private static void
	addDoneSeedingOption(
		Composite		comp,
		boolean			include_script_setting )
	{
		GridData gridData = new GridData();
		Label label = new Label(comp, SWT.NULL);
	    Messages.setLanguageText(label, "ConfigView.label.stop.seedcomp");
	    Utils.setLayoutData(label,  gridData );

	    String[][]	action_details = getActionDetails();

	    final StringListParameter sc = new StringListParameter(comp, "On Seeding Complete Do", "Nothing", action_details[0], action_details[1] );

	    if ( include_script_setting ){

			final Label sc_label = new Label(comp, SWT.NONE);
			Messages.setLanguageText(sc_label, "label.script.to.run");
			Utils.setLayoutData(sc_label, new GridData());
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			final FileParameter sc_script = new FileParameter(comp, "On Seeding Complete Script", "", new String[0]);
			sc_script.setLayoutData(gridData);

			boolean is_script = sc.getValue().startsWith( "RunScript" );

			sc_label.setEnabled( is_script );
			sc_script.setEnabled( is_script );

			sc.addChangeListener(
				new ParameterChangeAdapter()
				{
					@Override
					public void
					parameterChanged(
						Parameter	p,
						boolean		caused_internally )
					{
						boolean	is_script = sc.getValue().startsWith( "RunScript" );

						sc_label.setEnabled( is_script );
						sc_script.setEnabled( is_script );
					}
				});
	    }
	}
}
