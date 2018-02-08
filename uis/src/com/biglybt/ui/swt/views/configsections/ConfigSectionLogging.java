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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import com.biglybt.core.stats.CoreStats;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.logging.impl.FileLogging;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.update.UpdateException;
import com.biglybt.pif.update.UpdateInstaller;
import com.biglybt.pif.update.UpdateInstallerListener;

public class ConfigSectionLogging implements UISWTConfigSection {
  private static final LogIDs LOGID = LogIDs.GUI;
  private static final int logFileSizes[] =
     {
       1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 40, 50, 75, 100, 200, 300, 500
     };

  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_ROOT;
  }

	@Override
	public String configSectionGetName() {
		return "logging";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("openFolderButton");
  }

	@Override
	public int maxUserMode() {
		return 2;
	}



  @Override
  public Composite configSectionCreate(final Composite parent) {
		ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgOpenFolder = imageLoader.getImage("openFolderButton");

		GridData gridData;
    GridLayout layout;

    Composite gLogging = new Composite(parent, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(gLogging, gridData);
    layout = new GridLayout();
    layout.numColumns = 2;
    gLogging.setLayout(layout);

    int userMode = COConfigurationManager.getIntParameter("User Mode");


    BooleanParameter enable_logger = new BooleanParameter(gLogging, "Logger.Enabled", "ConfigView.section.logging.loggerenable");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    enable_logger.setLayoutData(gridData);

    // row

    final BooleanParameter enableLogging =
      new BooleanParameter(gLogging,
                           "Logging Enable",
                           "ConfigView.section.logging.enable");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    enableLogging.setLayoutData(gridData);

    Composite cArea = new Composite(gLogging, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 3;
    cArea.setLayout(layout);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    cArea.setLayoutData(gridData);


    // row

    Label lStatsPath = new Label(cArea, SWT.NULL);
    Messages.setLanguageText(lStatsPath, "ConfigView.section.logging.logdir"); //$NON-NLS-1$

    gridData = new GridData();
    gridData.widthHint = 150;
    final StringParameter pathParameter = new StringParameter(cArea, "Logging Dir"); //$NON-NLS-1$ //$NON-NLS-2$
    pathParameter.setLayoutData(gridData);
    Button browse = new Button(cArea, SWT.PUSH);
    browse.setImage(imgOpenFolder);
    imgOpenFolder.setBackground(browse.getBackground());
    browse.setToolTipText(MessageText.getString("ConfigView.button.browse"));
    browse.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event event) {
      DirectoryDialog dialog = new DirectoryDialog(parent.getShell(), SWT.APPLICATION_MODAL);
        dialog.setFilterPath(pathParameter.getValue());
        dialog.setText(MessageText.getString("ConfigView.section.logging.choosedefaultsavepath")); //$NON-NLS-1$
        String path = dialog.open();
        if (path != null) {
        pathParameter.setValue(path);
        }
      }
    });

    Label lMaxLog = new Label(cArea, SWT.NULL);

    Messages.setLanguageText(lMaxLog, "ConfigView.section.logging.maxsize");
    final String lmLabels[] = new String[logFileSizes.length];
    final int lmValues[] = new int[logFileSizes.length];
    for (int i = 0; i < logFileSizes.length; i++) {
      int  num = logFileSizes[i];
      lmLabels[i] = " " + num + " MB";
      lmValues[i] = num;
    }

    IntListParameter paramMaxSize = new IntListParameter(cArea, "Logging Max Size", lmLabels, lmValues);
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    paramMaxSize.setLayoutData(gridData);

    if(userMode > 1)
    {
    	Label timeStampLbl = new Label(cArea, SWT.NULL);
    	Messages.setLanguageText(timeStampLbl, "ConfigView.section.logging.timestamp");
    	Utils.setLayoutData(timeStampLbl, new GridData());
    	StringParameter timeStamp = new StringParameter(cArea,"Logging Timestamp");
    	gridData = new GridData();
    	gridData.horizontalSpan = 2;
    	gridData.widthHint = 150;
    	timeStamp.setLayoutData(gridData);
    }



    /** FileLogging filter, consisting of a List of types (info, warning, error)
     * and a checkbox Table of component IDs.
     */
    final String sFilterPrefix = "ConfigView.section.logging.filter";
    Group gLogIDs = new Group(gLogging, SWT.NULL);
    Messages.setLanguageText(gLogIDs, sFilterPrefix);
    layout = new GridLayout();
    layout.numColumns = 2;
    gLogIDs.setLayout(layout);
    gridData = new GridData(SWT.BEGINNING, SWT.BEGINNING, true, true);
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(gLogIDs, gridData);

    final List listLogTypes = new List(gLogIDs, SWT.BORDER | SWT.SINGLE
				| SWT.V_SCROLL);
    gridData = new GridData(SWT.NULL, SWT.BEGINNING, false, false);
    listLogTypes.setLayoutData(gridData);

    final int[] logTypes = { LogEvent.LT_INFORMATION, LogEvent.LT_WARNING,
				LogEvent.LT_ERROR };
		for (int i = 0; i < logTypes.length; i++)
			listLogTypes.add(MessageText.getString("ConfigView.section.logging.log" + i + "type"));
		listLogTypes.select(0);

		final LogIDs[] logIDs = FileLogging.configurableLOGIDs;
		//Arrays.sort(logIDs);
		final Table tableLogIDs = new Table(gLogIDs, SWT.CHECK | SWT.BORDER
				| SWT.SINGLE | SWT.FULL_SELECTION);
    gridData = new GridData(GridData.FILL_BOTH);
    tableLogIDs.setLayoutData(gridData);
    tableLogIDs.setLinesVisible (false);
    tableLogIDs.setHeaderVisible(false);
    TableColumn column = new TableColumn(tableLogIDs, SWT.NONE);

    for (int i = 0; i < logIDs.length; i++) {
    	TableItem item = new TableItem(tableLogIDs, SWT.NULL);
			item.setText(0, MessageText.getString(sFilterPrefix + "." + logIDs[i],
					logIDs[i].toString()));
			item.setData(logIDs[i]);
			boolean checked = COConfigurationManager.getBooleanParameter("bLog."
					+ logTypes[0] + "." + logIDs[i], true);
			item.setChecked(checked);
    }
    column.pack();

    // Update table when list selection changes
		listLogTypes.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = listLogTypes.getSelectionIndex();
				if (index < 0 || index >= logTypes.length)
					return;
				TableItem[] items = tableLogIDs.getItems();
				for (int i = 0; i < items.length; i++) {
					boolean checked = COConfigurationManager.getBooleanParameter(
							"bLog." + logTypes[index] + "." + items[i].getData(),
							true);
					items[i].setChecked(checked);

				}
			}
		});

    // Save config when checkbox is clicked
    tableLogIDs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.detail != SWT.CHECK)
					return;
				int index = listLogTypes.getSelectionIndex();
				if (index < 0 || index >= logTypes.length)
					return;
				TableItem item = (TableItem) e.item;
				COConfigurationManager.setParameter("bLog." + logTypes[index] + "."
						+ item.getData(), item.getChecked());
			}
		});


    final Control[] controls_main = { cArea, gLogIDs };
    final ChangeSelectionActionPerformer perf2 = new ChangeSelectionActionPerformer( controls_main );

    enableLogging.setAdditionalActionPerformer( perf2 );

    enable_logger.setAdditionalActionPerformer(
        new IAdditionalActionPerformer() {
          ChangeSelectionActionPerformer p1 = new ChangeSelectionActionPerformer(new Control[] {enableLogging.getControl() } );

          @Override
          public void performAction() {
            p1.performAction();
          }
          @Override
          public void setSelected(boolean selected) {
            p1.setSelected( selected );
            if( !selected && enableLogging.isSelected() )  enableLogging.setSelected( false );
          }
          @Override
          public void setIntValue(int value) { /*nothing*/ }
          @Override
          public void setStringValue(String value) { /*nothing*/ }
        }
    );

    /*
    BooleanParameter udp_transport = new BooleanParameter(gLogging, "Logging Enable UDP Transport", "ConfigView.section.logging.udptransport");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    udp_transport.setLayoutData(gridData);
    */

    if (userMode > 0 ){
    	
	    Composite cDebugFiles = new Composite(gLogging, SWT.NULL);
	    layout = new GridLayout();
	    layout.marginHeight = 0;
	    layout.marginWidth = 0;
	    layout.numColumns = 3;
	    cDebugFiles.setLayout(layout);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.horizontalSpan = 2;
	    cDebugFiles.setLayoutData(gridData);
	    
	    Label l_debug_file_size = new Label(cDebugFiles, SWT.NULL);
		Messages.setLanguageText(l_debug_file_size, "ConfigView.section.logging.debugfilesize");
		l_debug_file_size.setLayoutData(new GridData());
		
	    new IntParameter(cDebugFiles, "Logger.DebugFiles.SizeKB", 10, Integer.MAX_VALUE);
	    new Label(cDebugFiles, SWT.NULL).setLayoutData( new GridData( GridData.FILL_HORIZONTAL));
    }
    
    if (userMode > 1){

	    	// advanced option

	    Group cAO = new Group(gLogging, SWT.NULL);
	    cAO.setText( MessageText.getString("dht.advanced.group"));
	    layout = new GridLayout();
	    layout.marginHeight = 0;
	    layout.marginWidth = 0;
	    layout.numColumns = 5;
	    cAO.setLayout(layout);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.horizontalSpan = 2;
	    cAO.setLayoutData(gridData);

	    	// name

    	Label aoName = new Label(cAO, SWT.NULL);
    	Messages.setLanguageText(aoName, "label.name");
    	aoName.setLayoutData(new GridData());
    	final StringParameter name = new StringParameter(cAO,"Advanced Option Name");
    	gridData = new GridData();
    	gridData.widthHint = 150;
    	name.setLayoutData(gridData);

    	// value

		Label aoValue = new Label(cAO, SWT.NULL);
		Messages.setLanguageText(aoValue, "label.value");
		aoName.setLayoutData(new GridData());
		final StringParameter value = new StringParameter(cAO,"Advanced Option Value");
		gridData = new GridData();
		gridData.widthHint = 150;
		value.setLayoutData(gridData);

	    	// set

		Button set_option = new Button(cAO, SWT.PUSH);
		Messages.setLanguageText(set_option, "Button.set");

		set_option.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event event)
				{
					String key = name.getValue().trim();

					if (	( key.startsWith( "'") && key.endsWith( "'" )) ||
							( key.startsWith( "\"") && key.endsWith( "\"" ))){

						key = key.substring( 1, key.length() - 1 );
					}

					if ( key.length() > 0 ){

						if ( key.startsWith( "!" )){
							key = key.substring( 1 );
						}else{
							key = "adv.setting." + key;
						}

						String val = value.getValue().trim();

						boolean	is_string = false;

						if (	( val.startsWith( "'") && val.endsWith( "'" )) ||
								( val.startsWith( "\"") && val.endsWith( "\"" ))){

							val = val.substring( 1, val.length() - 1 );

							is_string = true;
						}

						if ( val.length() == 0 ){

							COConfigurationManager.removeParameter( key );

						}else{

							if ( is_string ){
								COConfigurationManager.setParameter( key, val );
							}else{
								String lc_val = val.toLowerCase( Locale.US );

								if ( lc_val.equals( "false" ) || lc_val.equals( "true" )){
									COConfigurationManager.setParameter( key, lc_val.startsWith( "t" ));
								}else{
									try{
										long l = Long.parseLong(val);
										COConfigurationManager.setParameter( key, l );
									}catch( Throwable e ){
										COConfigurationManager.setParameter( key, val );
									}
								}
							}
						}

						COConfigurationManager.save();
					}

				}
			});
    }
		// network diagnostics

	Label generate_net_info = new Label(gLogging, SWT.NULL);
	Messages.setLanguageText(generate_net_info, "ConfigView.section.logging.netinfo");

	Button generate_net_button = new Button(gLogging, SWT.PUSH);
	Messages.setLanguageText(generate_net_button, "ConfigView.section.logging.generatediagnostics");

	generate_net_button.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event event)
				{
					new AEThread2("GenerateNetDiag", true)
					{
						@Override
						public void run() {
							StringWriter sw = new StringWriter();

							PrintWriter	pw = new PrintWriter( sw );

							IndentWriter iw = new IndentWriter( pw );

							NetworkAdmin admin = NetworkAdmin.getSingleton();

							admin.generateDiagnostics( iw );

							pw.close();

							final String	info = sw.toString();

							Logger.log( new LogEvent(LOGID, "Network Info:\n" + info));

							Utils.execSWTThread(
								new Runnable()
								{
									@Override
									public void
									run()
									{
										ClipboardCopy.copyToClipBoard( info );
									}
								});
						}
					}.start();
				}
			});

	// stats

	Label generate_stats_info = new Label(gLogging, SWT.NULL);
	Messages.setLanguageText(generate_stats_info, "ConfigView.section.logging.statsinfo");

	Button generate_stats_button = new Button(gLogging, SWT.PUSH);
	Messages.setLanguageText(generate_stats_button, "ConfigView.section.logging.generatediagnostics");


	generate_stats_button.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event event)
				{
					java.util.Set	types = new HashSet();

					types.add( CoreStats.ST_ALL );

					Map	reply = CoreStats.getStats( types );

					Iterator	it = reply.entrySet().iterator();

					StringBuilder buffer = new StringBuilder(16000);

					while( it.hasNext()){

						Map.Entry	entry = (Map.Entry)it.next();

						buffer.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\r\n");
					}

					String	str = buffer.toString();

					ClipboardCopy.copyToClipBoard( str );

					Logger.log( new LogEvent(LOGID, "Stats Info:\n" + str));
				}
			});

        // diagnostics


	Label generate_info = new Label(gLogging, SWT.NULL);

	Messages.setLanguageText(generate_info, "ConfigView.section.logging.generatediagnostics.info");

	Button generate_button = new Button(gLogging, SWT.PUSH);

	Messages.setLanguageText(generate_button, "ConfigView.section.logging.generatediagnostics");

	generate_button.addListener(
			SWT.Selection,
			new Listener()
			{
				@Override
				public void
				handleEvent(Event event)
				{
					StringWriter sw = new StringWriter();

					PrintWriter	pw = new PrintWriter( sw );

					AEDiagnostics.generateEvidence( pw );

					pw.close();

					String	evidence = sw.toString();

					ClipboardCopy.copyToClipBoard( evidence );

					Logger.log( new LogEvent(LOGID, "Evidence Generation:\n" + evidence));
				}
			});

	if ( false ){
		Button test_button = new Button(gLogging, SWT.PUSH);

		test_button.setText( "Test" );

		test_button.addListener(
				SWT.Selection,
				new Listener()
				{
					@Override
					public void
					handleEvent(Event event)
					{
						try{
							PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();

							UpdateInstaller installer = pi.getUpdateManager().createInstaller();

							installer.addMoveAction(
								"C:\\temp\\file1", "C:\\temp\\file2" );

							installer.installNow(
								new UpdateInstallerListener()
								{
									@Override
									public void
									reportProgress(
										String		str )
									{
										System.out.println( str );
									}

									@Override
									public void
									complete()
									{
										System.out.println( "complete" );
									}

									@Override
									public void
									failed(
										UpdateException	e )
									{
										System.out.println( "failed" );

										e.printStackTrace();

									}
								});

						}catch( Throwable e ){

							e.printStackTrace();
						}
					}
				});
	}

    return gLogging;
  }
}
