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

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class ConfigSectionSharing implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_ROOT;
  }

	@Override
	public String configSectionGetName() {
		return "sharing";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
  }

	@Override
	public int maxUserMode() {
		return 0;
	}



  @Override
  public Composite configSectionCreate(final Composite parent) {
    GridData gridData;
	GridLayout layout;

    Composite gSharing = new Composite(parent, SWT.WRAP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(gSharing, gridData);
    layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginHeight = 0;
    gSharing.setLayout(layout);

    	// row

	gridData = new GridData();
    Label protocol_lab = new Label(gSharing, SWT.NULL);
    Messages.setLanguageText(protocol_lab, "ConfigView.section.sharing.protocol");
	Utils.setLayoutData(protocol_lab,  gridData );

	String[]	protocols = {"HTTP","HTTPS","UDP","DHT" };
    String[]	descs = {"HTTP","HTTPS (SSL)", "UDP", "Decentralised" };

    StringListParameter protocol = new StringListParameter(gSharing, "Sharing Protocol", "DHT", descs, protocols );

	// row

	GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );
	grid_data.horizontalSpan = 2;
	final BooleanParameter private_torrent =
		new BooleanParameter(gSharing, 	"Sharing Torrent Private",
                         			"ConfigView.section.sharing.privatetorrent");
	private_torrent.setLayoutData(grid_data);
	
	// row
    gridData = new GridData( GridData.FILL_HORIZONTAL );
    gridData.horizontalSpan = 2;
	final BooleanParameter permit_dht_backup =
		new BooleanParameter(gSharing, "Sharing Permit DHT",
                         "ConfigView.section.sharing.permitdht");
	permit_dht_backup.setLayoutData( gridData );


	ParameterChangeAdapter protocol_cl = 
			new ParameterChangeAdapter()
			{
				@Override
				public void
				parameterChanged(
					Parameter p,
					boolean caused_internally )
				{
					boolean not_dht = !protocol.getValue().equals( "DHT" );
					
					private_torrent.setEnabled( not_dht );
					
					permit_dht_backup.setEnabled( not_dht && !private_torrent.isSelected());
					
					if ( private_torrent.isSelected() ){

						permit_dht_backup.setSelected( false );
					}
				}
			};
			
	protocol_cl.parameterChanged( protocol, true );
	
	protocol.addChangeListener( protocol_cl );
	private_torrent.addChangeListener( protocol_cl );

    	// row
    gridData = new GridData( GridData.FILL_HORIZONTAL );
    gridData.horizontalSpan = 2;
    new BooleanParameter(gSharing, "Sharing Add Hashes",
                         "wizard.createtorrent.extrahashes").setLayoutData( gridData );

	// row

	grid_data = new GridData( GridData.FILL_HORIZONTAL );
	grid_data.horizontalSpan = 2;
	final BooleanParameter disable_rcm =
		new BooleanParameter(gSharing, 	"Sharing Disable RCM",
                         			"ConfigView.section.sharing.disable_rcm");
	disable_rcm.setLayoutData( gridData );
	

    	// row
    gridData = new GridData( GridData.FILL_HORIZONTAL );
    gridData.horizontalSpan = 2;
    BooleanParameter rescan_enable =
    	new BooleanParameter(gSharing, "Sharing Rescan Enable",
    						"ConfigView.section.sharing.rescanenable");

	rescan_enable.setLayoutData( gridData );

    	//row
    gridData = new GridData();
	gridData.horizontalIndent = 25;
    Label period_label = new Label(gSharing, SWT.NULL );
    Messages.setLanguageText(period_label, "ConfigView.section.sharing.rescanperiod");
	Utils.setLayoutData(period_label,  gridData );

    gridData = new GridData();
	IntParameter rescan_period = new IntParameter(gSharing, "Sharing Rescan Period");
    rescan_period.setMinimumValue(1);
    rescan_period.setLayoutData( gridData );

    rescan_enable.setAdditionalActionPerformer(new ChangeSelectionActionPerformer( rescan_period.getControls() ));
    rescan_enable.setAdditionalActionPerformer(new ChangeSelectionActionPerformer( new Control[]{period_label} ));

    	// comment

    Label comment_label = new Label(gSharing, SWT.NULL );
    Messages.setLanguageText(comment_label, "ConfigView.section.sharing.torrentcomment");

	new Label(gSharing, SWT.NULL);

	gridData = new GridData(GridData.FILL_HORIZONTAL);
	gridData.horizontalIndent = 25;
	gridData.horizontalSpan = 2;
    StringParameter torrent_comment = new StringParameter(gSharing, "Sharing Torrent Comment", "" );
    torrent_comment.setLayoutData(gridData);

   	// row
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    BooleanParameter persistent =
    	new BooleanParameter(gSharing, "Sharing Is Persistent",
    						"ConfigView.section.sharing.persistentshares");

    persistent.setLayoutData( gridData );

	/////////////////////// NETWORKS GROUP ///////////////////

	Group networks_group = new Group(gSharing, SWT.NULL);
	Messages.setLanguageText(networks_group,
			"ConfigView.section.connection.group.networks");
	GridLayout networks_layout = new GridLayout();
	networks_group.setLayout(networks_layout);

	gridData = new GridData(GridData.FILL_HORIZONTAL);
	gridData.horizontalSpan = 2;
	Utils.setLayoutData(networks_group, gridData);

	BooleanParameter network_global = new BooleanParameter( networks_group, "Sharing Network Selection Global", "label.use.global.defaults");

	java.util.List<BooleanParameter> net_params = new ArrayList<>();
	
	for ( String net: AENetworkClassifier.AT_NETWORKS ){
				
		String config_name = "Sharing Network Selection Default." + net;
		String msg_text = "ConfigView.section.connection.networks." + net;
		
		BooleanParameter network = new BooleanParameter( networks_group, config_name, msg_text);

		gridData = new GridData();
		gridData.horizontalIndent = 25;
		Utils.setLayoutData(network.getControl(),  gridData );
		
		net_params.add( network );
	}

	ParameterChangeAdapter net_listener = 
			new ParameterChangeAdapter(){	
			@Override
			public void parameterChanged(Parameter x, boolean caused_internally){
				for ( BooleanParameter p: net_params ){
					
					p.setEnabled( !network_global.isSelected());
				}
			}
		};
	
	net_listener.parameterChanged( null,  false );
		
	network_global.addChangeListener( net_listener );

    
    return gSharing;

  }
}
