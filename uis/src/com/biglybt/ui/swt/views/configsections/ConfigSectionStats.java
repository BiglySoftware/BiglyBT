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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTConfigSection;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.Messages;
import com.biglybt.core.stats.StatsWriterPeriodic;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.swt.imageloader.ImageLoader;

public class ConfigSectionStats implements UISWTConfigSection {

  private static final int defaultStatsPeriod = 30;

  private static final int statsPeriods[] =
    {
      1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40, 50,
      60, 120, 180, 240, 300, 360, 420, 480, 540, 600,
      900, 1200, 1800, 2400, 3000, 3600,
      7200, 10800, 14400, 21600, 43200, 86400,
    };

  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_ROOT;
  }

	@Override
	public String configSectionGetName() {
		return "stats";
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
		return 0;
	}



  @Override
  public Composite configSectionCreate(final Composite parent) {
		ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgOpenFolder = imageLoader.getImage("openFolderButton");

		GridData gridData;
    GridLayout layout;

    Composite gOutter = new Composite(parent, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gOutter.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 1;
    gOutter.setLayout(layout);

 		// general

	Group gGeneral = new Group(gOutter, SWT.NULL);
	Messages.setLanguageText(gGeneral, "ConfigView.section.general");
	layout = new GridLayout(2, false);
	gGeneral.setLayout(layout);
	gGeneral.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

	Label lSmooth = new Label(gGeneral, SWT.NULL);
	Messages.setLanguageText(lSmooth, "stats.general.smooth_secs");

	IntParameter smooth_secs = new IntParameter( gGeneral, "Stats Smoothing Secs", 30, 30*60 );

    	// display

	Group gDisplay = new Group(gOutter, SWT.NULL);
	Messages.setLanguageText(gDisplay, "stats.display.group");
	layout = new GridLayout(1, false);
	gDisplay.setLayout(layout);
	gDisplay.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

    gridData = new GridData();

    BooleanParameter graph_dividers = new BooleanParameter(gDisplay, "Stats Graph Dividers", "ConfigView.section.stats.graph_update_dividers");
    graph_dividers.setLayoutData(gridData);



    	// snapshots

	Group gSnap = new Group(gOutter, SWT.NULL);
	Messages.setLanguageText(gSnap, "stats.snapshot.group");
	layout = new GridLayout(3, false);
	gSnap.setLayout(layout);
	gSnap.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

    // row

    gridData = new GridData();
    gridData.horizontalSpan = 3;
    BooleanParameter enableStats =
        new BooleanParameter(gSnap, "Stats Enable",
                             "ConfigView.section.stats.enable");
    enableStats.setLayoutData(gridData);

    Control[] controls = new Control[13];

    // row

    Label lStatsPath = new Label(gSnap, SWT.NULL);
    Messages.setLanguageText(lStatsPath, "ConfigView.section.stats.defaultsavepath"); //$NON-NLS-1$

    gridData = new GridData();
    gridData.widthHint = 150;
    final StringParameter pathParameter = new StringParameter(gSnap, "Stats Dir", ""); //$NON-NLS-1$ //$NON-NLS-2$
    pathParameter.setLayoutData(gridData);
    controls[0] = lStatsPath;
    controls[1] = pathParameter.getControl();
    Button browse = new Button(gSnap, SWT.PUSH);
    browse.setImage(imgOpenFolder);
    imgOpenFolder.setBackground(browse.getBackground());
    browse.setToolTipText(MessageText.getString("ConfigView.button.browse"));
    controls[2] = browse;
    browse.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event event) {
        DirectoryDialog dialog = new DirectoryDialog(parent.getShell(), SWT.APPLICATION_MODAL);
      dialog.setFilterPath(pathParameter.getValue());
      dialog.setText(MessageText.getString("ConfigView.section.stats.choosedefaultsavepath")); //$NON-NLS-1$
      String path = dialog.open();
      if (path != null) {
        pathParameter.setValue(path);
      }
      }
    });

    // row

    Label lSaveFile = new Label(gSnap, SWT.NULL);
    Messages.setLanguageText(lSaveFile, "ConfigView.section.stats.savefile"); //$NON-NLS-1$
    controls[3] = lSaveFile;

    gridData = new GridData();
    gridData.widthHint = 150;
    final StringParameter fileParameter = new StringParameter(gSnap, "Stats File", StatsWriterPeriodic.DEFAULT_STATS_FILE_NAME );
    fileParameter.setLayoutData(gridData);
    controls[4] = fileParameter.getControl();
    new Label(gSnap, SWT.NULL);

    // row

    Label lxslFile = new Label(gSnap, SWT.NULL);
    Messages.setLanguageText(lxslFile, "ConfigView.section.stats.xslfile"); //$NON-NLS-1$
    controls[5] = lxslFile;

    gridData = new GridData();
    gridData.widthHint = 150;
    final StringParameter xslParameter = new StringParameter(gSnap, "Stats XSL File", "" );
    xslParameter.setLayoutData(gridData);
    controls[6] = xslParameter.getControl();
    Label lxslDetails = new Label(gSnap, SWT.NULL);
    Messages.setLanguageText(lxslDetails, "ConfigView.section.stats.xslfiledetails"); //$NON-NLS-1$
    final String linkFAQ = Constants.PLUGINS_WEB_SITE + "faq.php#20";
    lxslDetails.setCursor(lxslDetails.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
    lxslDetails.setForeground(Colors.blue);
    lxslDetails.setData( linkFAQ );
    lxslDetails.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent arg0) {
      	Utils.launch(linkFAQ);
      }
      @Override
      public void mouseDown(MouseEvent arg0) {
      	Utils.launch(linkFAQ);
      }
    });
    ClipboardCopy.addCopyToClipMenu( lxslDetails );
    controls[7] = lxslDetails;

    // row

    Label lSaveFreq = new Label(gSnap, SWT.NULL);

    Messages.setLanguageText(lSaveFreq, "ConfigView.section.stats.savefreq");
    controls[8] = lSaveFreq;

    final String spLabels[] = new String[statsPeriods.length];
    final int spValues[] = new int[statsPeriods.length];
    for (int i = 0; i < statsPeriods.length; i++) {
      int num = statsPeriods[i];

      if ( num%3600 == 0 )
        spLabels[i] = " " + (statsPeriods[i]/3600) + " " +
                             MessageText.getString("ConfigView.section.stats.hours");

      else if ( num%60 == 0 )
        spLabels[i] = " " + (statsPeriods[i]/60) + " " +
                             MessageText.getString("ConfigView.section.stats.minutes");

      else
        spLabels[i] = " " + statsPeriods[i] + " " +
                            MessageText.getString("ConfigView.section.stats.seconds");

      spValues[i] = statsPeriods[i];
    }

    controls[9] = lSaveFreq;
    controls[10] = new IntListParameter(gSnap, "Stats Period", defaultStatsPeriod, spLabels, spValues).getControl();
    new Label(gSnap, SWT.NULL);

    	// ROW

    gridData = new GridData();
    gridData.horizontalSpan = 3;
    BooleanParameter exportPeers =
        new BooleanParameter(gSnap, "Stats Export Peer Details",
                             "ConfigView.section.stats.exportpeers");
    exportPeers.setLayoutData(gridData);

    controls[11] = exportPeers.getControl();

 	// ROW

    gridData = new GridData();
    gridData.horizontalSpan = 3;
    BooleanParameter exportFiles =
        new BooleanParameter(gSnap, "Stats Export File Details",
                             "ConfigView.section.stats.exportfiles");
    exportFiles.setLayoutData(gridData);

    controls[12] = exportFiles.getControl();

      	// control stuff

    enableStats.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controls));

  		// xfer

	Group gXfer = new Group(gOutter, SWT.NULL);
	Messages.setLanguageText(gXfer, "ConfigView.section.transfer");
	layout = new GridLayout(3, false);
	gXfer.setLayout(layout);
	gXfer.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

	List<Button> buttons = new ArrayList<>();

		// set mark

    Label set_mark_label = new Label(gXfer, SWT.NULL );
    Messages.setLanguageText(set_mark_label, "ConfigView.section.transfer.setmark" );

    Button set_mark_button = new Button(gXfer, SWT.PUSH);

    buttons.add( set_mark_button );

    Messages.setLanguageText(set_mark_button, "Button.set" );

    set_mark_button.addListener(SWT.Selection,
		new Listener()
		{
	        @Override
	        public void
			handleEvent(Event event)
	        {
	        	OverallStats stats = StatsFactory.getStats();

	        	stats.setMark();
	        }
		});

    Button clear_mark_button = new Button(gXfer, SWT.PUSH);

    buttons.add( clear_mark_button );

    Messages.setLanguageText(clear_mark_button, "Button.clear" );

    clear_mark_button.addListener(SWT.Selection,
		new Listener()
		{
	        @Override
	        public void
			handleEvent(Event event)
	        {
	        	OverallStats stats = StatsFactory.getStats();

	        	stats.clearMark();
	        }
		});
    	// long term

	Group gLong = new Group(gOutter, SWT.NULL);
	Messages.setLanguageText(gLong, "stats.longterm.group");
	layout = new GridLayout(2, false);
	gLong.setLayout(layout);
	gLong.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

	  // row

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    BooleanParameter enableLongStats =
        new BooleanParameter(gLong, "long.term.stats.enable",
                             "ConfigView.section.stats.enable");
    enableLongStats.setLayoutData(gridData);

    	// week start

    Label lWeekStart = new Label(gLong, SWT.NULL);
    Messages.setLanguageText(lWeekStart, "stats.long.weekstart");

    final String wsLabels[] = new String[7];
    final int wsValues[] = new int[7];

    Calendar cal = new GregorianCalendar();
    SimpleDateFormat format = new SimpleDateFormat( "E" );

    for ( int i=0;i<7;i++){
    	int dow = i+1;	// sun = 1 etc
    	cal.set( Calendar.DAY_OF_WEEK, dow );
    	wsLabels[i] = format.format( cal.getTime());
    	wsValues[i] = i+1;
    }

    IntListParameter week_start = new IntListParameter(gLong, "long.term.stats.weekstart", Calendar.SUNDAY, wsLabels, wsValues);

    	// month start

    Label lMonthStart = new Label(gLong, SWT.NULL);
    Messages.setLanguageText(lMonthStart, "stats.long.monthstart");

    IntParameter month_start = new IntParameter( gLong, "long.term.stats.monthstart", 1, 28 );

    enableLongStats.setAdditionalActionPerformer( new ChangeSelectionActionPerformer( new Control[]{ lWeekStart, lMonthStart }));
    enableLongStats.setAdditionalActionPerformer( new ChangeSelectionActionPerformer( week_start, month_start ));


    	// reset

    Label lt_reset_label = new Label(gLong, SWT.NULL );
    Messages.setLanguageText(lt_reset_label, "ConfigView.section.transfer.lts.reset" );

    Button lt_reset_button = new Button(gLong, SWT.PUSH);

    buttons.add( lt_reset_button );

    Messages.setLanguageText(lt_reset_button, "Button.clear" );

    lt_reset_button.addListener(SWT.Selection,
		new Listener()
		{
	        @Override
	        public void
			handleEvent(Event event)
	        {
	        	MessageBoxShell mb = new MessageBoxShell(
	        			SWT.ICON_WARNING | SWT.OK | SWT.CANCEL,
	        			MessageText.getString("ConfigView.section.security.resetcerts.warning.title"),
	        			MessageText.getString("ConfigView.section.transfer.ltsreset.warning.msg"));
	        	mb.setDefaultButtonUsingStyle(SWT.CANCEL);
	        	mb.setParent(parent.getShell());

	        	mb.open(new UserPrompterResultListener() {
	        		@Override
			        public void prompterClosed(int returnVal) {
	        			if (returnVal != SWT.OK) {
	        				return;
	        			}

	        			Utils.getOffOfSWTThread(
	        				new AERunnable() {

								@Override
								public void runSupport() {
									StatsFactory.clearLongTermStats();
								}
							});

	        		}
	        	});
	        }
		});

    Utils.makeButtonsEqualWidth(buttons);

    return gOutter;
  }
}
