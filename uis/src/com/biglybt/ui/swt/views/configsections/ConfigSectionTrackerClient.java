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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class
ConfigSectionTrackerClient
	implements UISWTConfigSection
{
  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_TRACKER;
  }

	@Override
	public String configSectionGetName() {
		return "tracker.client";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
  }

	@Override
	public int maxUserMode() {
		return 2;
	}


  @Override
  public Composite configSectionCreate(final Composite parent) {
    GridData gridData;
    GridLayout layout;
    Label  label;
    int userMode = COConfigurationManager.getIntParameter("User Mode");

    // extensions tab set up
    Composite gMainTab = new Composite(parent, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gMainTab.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    gMainTab.setLayout(layout);

    	//////////////////////SCRAPE GROUP ///////////////////

    Group scrapeGroup = new Group(gMainTab,SWT.NULL);
    Messages.setLanguageText(scrapeGroup,"ConfigView.group.scrape");
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 1;
    scrapeGroup.setLayout(gridLayout);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    scrapeGroup.setLayoutData( gridData );

    label = new Label(scrapeGroup, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label, "ConfigView.section.tracker.client.scrapeinfo");

    BooleanParameter	scrape =
    	new BooleanParameter(scrapeGroup, "Tracker Client Scrape Enable",
    							"ConfigView.section.tracker.client.scrapeenable");

    BooleanParameter	scrape_stopped =
    	new BooleanParameter(scrapeGroup, "Tracker Client Scrape Stopped Enable",
    							"ConfigView.section.tracker.client.scrapestoppedenable");

    scrape.setAdditionalActionPerformer(new ChangeSelectionActionPerformer( scrape_stopped.getControls()));

    new BooleanParameter(scrapeGroup, "Tracker Client Scrape Single Only",
    							"ConfigView.section.tracker.client.scrapesingleonly");

    	/////////////// INFO GROUP

    Group infoGroup = new Group(gMainTab,SWT.NULL);
    Messages.setLanguageText(infoGroup,"label.information");
    gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    infoGroup.setLayout(gridLayout);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    infoGroup.setLayoutData( gridData );

    	// send info

    gridData = new GridData();
    gridData.horizontalSpan = 2;

    new BooleanParameter(infoGroup, "Tracker Client Send OS and Java Version",
                         "ConfigView.section.tracker.sendjavaversionandos").setLayoutData(gridData);

    	// show warnings

    BooleanParameter showWarnings = new BooleanParameter(infoGroup, "Tracker Client Show Warnings", "ConfigView.section.tracker.client.showwarnings" );
    gridData = new GridData();
    gridData.horizontalSpan = 2;
	showWarnings.setLayoutData(gridData);

		// exclude LAN

	BooleanParameter excludeLAN = new BooleanParameter(infoGroup, "Tracker Client Exclude LAN", "ConfigView.section.tracker.client.exclude_lan" );
	gridData = new GridData();
	gridData.horizontalSpan = 2;
	excludeLAN.setLayoutData(gridData);

   		/////////////// PROTOCOL GROUP

    Group protocolGroup = new Group(gMainTab,SWT.NULL);
    Messages.setLanguageText(protocolGroup,"label.protocol");
    gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    protocolGroup.setLayout(gridLayout);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    protocolGroup.setLayoutData( gridData );

    	// tcp enable

    BooleanParameter enableTCP = new BooleanParameter(protocolGroup, "Tracker Client Enable TCP", "ConfigView.section.tracker.client.enabletcp");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    enableTCP.setLayoutData(gridData);

    	// udp enable

    BooleanParameter enableUDP = new BooleanParameter(protocolGroup, "Server Enable UDP", "ConfigView.section.server.enableudp");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    enableUDP.setLayoutData(gridData);

    	// udp probe enable

    BooleanParameter enableUDPProbe = new BooleanParameter(protocolGroup, "Tracker UDP Probe Enable", "ConfigView.section.server.enableudpprobe");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    enableUDPProbe.setLayoutData(gridData);

    enableUDP.setAdditionalActionPerformer(new ChangeSelectionActionPerformer( enableUDPProbe.getControls()));

    if (userMode > 1 ){

    	BooleanParameter enableDNS = new BooleanParameter(protocolGroup, "Tracker DNS Records Enable", "ConfigView.section.server.enablednsrecords");
    }


    if (userMode > 0) {

//////////////////////OVERRIDE GROUP ///////////////////

    Group overrideGroup = new Group(gMainTab,SWT.NULL);
    Messages.setLanguageText(overrideGroup,"ConfigView.group.override");
    gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    overrideGroup.setLayout(gridLayout);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    overrideGroup.setLayoutData( gridData );


    label = new Label(overrideGroup, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label, "ConfigView.label.overrideip");

    StringParameter overrideip = new StringParameter(overrideGroup, "Override Ip", "");
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    data.widthHint = 100;
    overrideip.setLayoutData(data);

    label = new Label(overrideGroup, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label, "ConfigView.label.announceport");

    StringParameter tcpOverride = new StringParameter(overrideGroup, "TCP.Listen.Port.Override");
    data = new GridData();
    data.widthHint = 50;
    tcpOverride.setLayoutData(data);

    tcpOverride.addChangeListener(new ParameterChangeAdapter() {
    	@Override
	    public void stringParameterChanging(Parameter p, String toValue)
    	{
    		if(toValue.length() == 0 ){
    			return;
    		}

    		try
			{
    			int portVal = Integer.parseInt(toValue);
				if(portVal >= 0 && portVal <= 65535)
					return;
			} catch (NumberFormatException e) {}
			p.setValue("");
    	}
    });

    label = new Label(overrideGroup, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label, "ConfigView.label.noportannounce");

    BooleanParameter noPortAnnounce = new BooleanParameter(overrideGroup,"Tracker Client No Port Announce");
    data = new GridData();
    noPortAnnounce.setLayoutData(data);

    label = new Label(overrideGroup, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label, "ConfigView.label.maxnumwant");

    IntParameter numwant = new IntParameter(overrideGroup, "Tracker Client Numwant Limit",0,100);
    data = new GridData();
    numwant.setLayoutData(data);

    label = new Label(overrideGroup, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label, "ConfigView.label.minannounce");

    IntParameter minmininterval = new IntParameter(overrideGroup, "Tracker Client Min Announce Interval");
    data = new GridData();
    minmininterval.setLayoutData(data);


    //////////////////////////

    if(userMode>1) {

    // row

    label = new Label(gMainTab, SWT.NULL);
    Messages.setLanguageText(label,  "ConfigView.section.tracker.client.connecttimeout");
    gridData = new GridData();
    IntParameter	connect_timeout = new IntParameter(gMainTab, "Tracker Client Connect Timeout" );
    connect_timeout.setLayoutData(gridData);
    label = new Label(gMainTab, SWT.NULL);

    // row

    label = new Label(gMainTab, SWT.NULL);
    Messages.setLanguageText(label,  "ConfigView.section.tracker.client.readtimeout");
    gridData = new GridData();
    IntParameter	read_timeout = new IntParameter(gMainTab, "Tracker Client Read Timeout" );
    read_timeout.setLayoutData(gridData);
    label = new Label(gMainTab, SWT.NULL);

    ////// main tab

    // row

    gridData = new GridData();
    gridData.horizontalSpan = 2;

    new BooleanParameter(gMainTab, "Tracker Key Enable Client",
                         "ConfigView.section.tracker.enablekey").setLayoutData(gridData);

    label = new Label(gMainTab, SWT.NULL);


    // row

    gridData = new GridData();
    gridData.horizontalSpan = 2;

    new BooleanParameter(gMainTab, "Tracker Separate Peer IDs",
                         "ConfigView.section.tracker.separatepeerids").setLayoutData(gridData);

    label = new Label(gMainTab, SWT.WRAP);
    label.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
    Messages.setLanguageText(label,  "ConfigView.section.tracker.separatepeerids.info");

    }
    }


    return gMainTab;
  }
}
