/*
 * Created on 16-Jan-2006
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

package com.biglybt.ui.swt.views;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.config.BooleanSwtParameter;
import com.biglybt.ui.swt.config.FloatSwtParameter;
import com.biglybt.ui.swt.config.IntSwtParameter;
import com.biglybt.ui.swt.config.actionperformer.ChangeSelectionActionPerformer;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mdi.TabbedEntry;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.util.DataSourceUtils;

/**
 * aka "Options" Tab in Torrent Details, 
 * and torrent view right click -> "Options/Info" when multiple selected
 */
public class
TorrentOptionsView
	implements UISWTViewCoreEventListener, DownloadManagerOptionsHandler.ParameterChangeListener
{
		// adhoc parameters need explicit code to reset default values below

	private static final String	MAX_UPLOAD		= "max.upload";
	private static final String	MAX_DOWNLOAD	= "max.download";

	public static final String MSGID_PREFIX = "TorrentOptionsView";

	private boolean								multi_view;
	private DownloadManagerOptionsHandler[]		managers;

	private downloadStateBooleanParameterAdapter ds_boolparam_adapter	= new downloadStateBooleanParameterAdapter();
	private downloadStateIntParameterAdapter ds_intparam_adapter	= new downloadStateIntParameterAdapter();
	private downloadStateFloatParameterAdapter ds_floatparam_adapter	= new downloadStateFloatParameterAdapter();
	private adhocIntParameterAdapter adhoc_param_adapter	= new adhocIntParameterAdapter();

	private Map<String, BaseSwtParameter<?,?>> 	adhoc_parameters	= new HashMap<>();
	private Map<String, BaseSwtParameter<?,?>>	ds_parameters 		= new HashMap<>();

	private ScrolledComposite sc;
	private Font 				headerFont;

	// info panel

	private BufferedLabel	agg_size;
	private BufferedLabel	agg_remaining;
	private BufferedLabel	agg_uploaded;
	private BufferedLabel	agg_downloaded;
	private BufferedLabel	agg_share_ratio;
	private BufferedLabel	agg_upload_speed;
	private BufferedLabel	agg_download_speed;


	private Composite parent;

	private UISWTView swtView;

	public
	TorrentOptionsView()
	{
	}

	private void
	initialize(
		Composite composite)
	{
		this.parent = composite;

		GridLayout layout;

		// cheap trick to allow datasource changes.  Normally we'd just
		// refill the components with new info, but I didn't write this and
		// I don't want to waste my time :) [tux]
		if (sc != null && !sc.isDisposed()) {
			sc.dispose();
		}

		sc = new ScrolledComposite(composite, SWT.V_SCROLL | SWT.H_SCROLL );
		sc.getVerticalBar().setIncrement(16);
		sc.setExpandHorizontal(true);
		sc.setExpandVertical(true);
		GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 1, 1);
		sc.setLayoutData(gridData);

		Composite panel = new Composite(sc, SWT.NULL);

		sc.setContent( panel );

		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 1;
		panel.setLayout(layout);

		Layout parentLayout = parent.getLayout();
		if (parentLayout instanceof FormLayout) {
			panel.setLayoutData(Utils.getFilledFormData());
		} else {
			panel.setLayoutData(new GridData(GridData.FILL_BOTH));
		}


		if (managers == null) {
			return;
		}

		int userMode = COConfigurationManager.getIntParameter("User Mode");

			// header

		boolean showHeader = true;
		if (swtView instanceof TabbedEntry) {
			showHeader = ((TabbedEntry) swtView).getMDI().getAllowSubViews();
		}
		
		if (showHeader) {
			Composite cHeader = new Composite(panel, Utils.isDarkAppearanceNativeWindows()?SWT.NONE:SWT.BORDER);
			GridLayout configLayout = new GridLayout();
			configLayout.marginHeight = 3;
			configLayout.marginWidth = 0;
			cHeader.setLayout(configLayout);
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER);
			cHeader.setLayoutData(gridData);
	
			Display d = panel.getDisplay();
			cHeader.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION));
			cHeader.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION_TEXT));
	
			Label lHeader = new Label(cHeader, SWT.NULL);
			lHeader.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION));
			lHeader.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION_TEXT));
			FontData[] fontData = lHeader.getFont().getFontData();
			fontData[0].setStyle(SWT.BOLD);
			int fontHeight = (int)(fontData[0].getHeight() * 1.2);
			fontData[0].setHeight(fontHeight);
			headerFont = new Font(d, fontData);
			lHeader.setFont(headerFont);

			if ( managers.length == 1 ){
				if ( managers[0].getDownloadManager() == null ){
					lHeader.setText( " " + managers[0].getName().replaceAll("&", "&&"));
				}else{
					lHeader.setText( " " + MessageText.getString( "authenticator.torrent" ) + " : " + managers[0].getName().replaceAll("&", "&&"));
				}
			}else{
				String	str = "";
	
				for (int i=0;i<Math.min( 3, managers.length ); i ++ ){
	
					str += (i==0?"":", ") + managers[i].getName().replaceAll("&", "&&");
				}
	
				if ( managers.length > 3 ){
	
					str += "...";
				}
	
				lHeader.setText( " " + managers.length + " " + MessageText.getString( "ConfigView.section.torrents" ) + " : " + str );
			}

			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER);
			lHeader.setLayoutData(gridData);
		}

		Group gTorrentOptions = Utils.createSkinnedGroup(panel, SWT.NULL);
		Messages.setLanguageText(gTorrentOptions, "ConfigView.section.transfer");
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		gTorrentOptions.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		gTorrentOptions.setLayout(layout);

		//Disabled for release. Need to convert from user-specified units to
	    //KB/s before restoring the following line
	    //String k_unit = DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB).trim()
	    String k_unit = DisplayFormatters.getRateUnitBase10(DisplayFormatters.UNIT_KB).trim();

			// max upload speed

		Label label;

		IntSwtParameter max_upload = new IntSwtParameter(gTorrentOptions,
				MAX_UPLOAD, "", null, adhoc_param_adapter);
		max_upload.setLabelText(k_unit + " "
				+ MessageText.getString("GeneralView.label.maxuploadspeed.tooltip"));
		adhoc_parameters.put( MAX_UPLOAD, max_upload );

		if ( userMode > 0) {

				// max upload when busy

			IntSwtParameter max_upload_when_busy = new IntSwtParameter(
					gTorrentOptions, DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY,
					"TorrentOptionsView.param.max.uploads.when.busy", null,
					ds_intparam_adapter);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY, max_upload_when_busy );
		}

			// max download speed

		IntSwtParameter max_download = new IntSwtParameter(gTorrentOptions,
				MAX_DOWNLOAD, "", null, adhoc_param_adapter);
		max_download.setLabelText(k_unit + " "
				+ MessageText.getString("GeneralView.label.maxdownloadspeed.tooltip"));
		adhoc_parameters.put( MAX_DOWNLOAD, max_download );

			// max uploads

		if (userMode > 0) {
			IntSwtParameter max_uploads = new IntSwtParameter(gTorrentOptions,
					DownloadManagerState.PARAM_MAX_UPLOADS,
					"TorrentOptionsView.param.max.uploads", null, ds_intparam_adapter);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOADS, max_uploads );
			max_uploads.setMinimumValue(2);

				//	max uploads when seeding enabled

			final Composite cMaxUploadsOptionsArea = new Composite(gTorrentOptions, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			cMaxUploadsOptionsArea.setLayout(layout);
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			cMaxUploadsOptionsArea.setLayoutData(gridData);

			BooleanSwtParameter	max_uploads_when_seeding_enabled =
					new BooleanSwtParameter(cMaxUploadsOptionsArea,
							DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED,
							"TorrentOptionsView.param.alternative.value.enable", null,
							ds_boolparam_adapter);
			max_uploads_when_seeding_enabled.setIndent(1, true);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED, max_uploads_when_seeding_enabled );


			IntSwtParameter max_uploads_when_seeding = new IntSwtParameter(
					cMaxUploadsOptionsArea,
					DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING,
					null, null,
					ds_intparam_adapter);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING, max_uploads_when_seeding );
			max_uploads_when_seeding.setMinimumValue(2);

			max_uploads_when_seeding_enabled.setAdditionalActionPerformer(
					new ChangeSelectionActionPerformer( max_uploads_when_seeding));

				// max peers

			IntSwtParameter max_peers = new IntSwtParameter(gTorrentOptions,
					DownloadManagerState.PARAM_MAX_PEERS,
					"TorrentOptionsView.param.max.peers", null, ds_intparam_adapter);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_PEERS, max_peers );

				// max peers when seeding

			final Composite cMaxPeersOptionsArea = new Composite(gTorrentOptions, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			cMaxPeersOptionsArea.setLayout(layout);
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			cMaxPeersOptionsArea.setLayoutData(gridData);

			BooleanSwtParameter	max_peers_when_seeding_enabled =
					new BooleanSwtParameter(cMaxPeersOptionsArea,
							DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED,
							"TorrentOptionsView.param.alternative.value.enable",null,
							ds_boolparam_adapter);
			max_peers_when_seeding_enabled.setIndent(1, true);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED, max_peers_when_seeding_enabled );


			IntSwtParameter max_peers_when_seeding = new IntSwtParameter(
					cMaxPeersOptionsArea,
					DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING, null, null,
					ds_intparam_adapter);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING, max_peers_when_seeding );

			max_peers_when_seeding_enabled.setAdditionalActionPerformer(
					new ChangeSelectionActionPerformer( max_peers_when_seeding));


				// max seeds

			IntSwtParameter max_seeds = new IntSwtParameter(gTorrentOptions,
					DownloadManagerState.PARAM_MAX_SEEDS,
					"TorrentOptionsView.param.max.seeds", null, ds_intparam_adapter);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_SEEDS, max_seeds );
		}

			// upload priority

		if ( userMode > 0){

			IntSwtParameter upload_priority_enabled = new IntSwtParameter(
					gTorrentOptions, DownloadManagerState.PARAM_UPLOAD_PRIORITY,
					"TorrentOptionsView.param.upload.priority", null, 0, 1,
					ds_intparam_adapter);

			ds_parameters.put( DownloadManagerState.PARAM_UPLOAD_PRIORITY, upload_priority_enabled );

				// min sr


			FloatSwtParameter min_sr = new FloatSwtParameter(gTorrentOptions,
					DownloadManagerState.PARAM_MIN_SHARE_RATIO,
					"TableColumn.header.min_sr", null, 0, Float.MAX_VALUE, true, 3,
					ds_floatparam_adapter);

			ds_parameters.put( DownloadManagerState.PARAM_MIN_SHARE_RATIO, min_sr );

				// max sr


			FloatSwtParameter max_sr = new FloatSwtParameter(gTorrentOptions,
					DownloadManagerState.PARAM_MAX_SHARE_RATIO,
					"TableColumn.header.max_sr", null, 0, Float.MAX_VALUE, true, 3,
					ds_floatparam_adapter);

			ds_parameters.put( DownloadManagerState.PARAM_MAX_SHARE_RATIO, max_sr );
		}

			// reset

	    Label reset_label = new Label(gTorrentOptions, SWT.NULL );
	    Messages.setLanguageText(reset_label, "TorrentOptionsView.param.reset.to.default");

	    Button reset_button = new Button(gTorrentOptions, SWT.PUSH);

	    Messages.setLanguageText(reset_button, "TorrentOptionsView.param.reset.button" );

	    reset_button.addListener(SWT.Selection,
	    		new Listener()
				{
			        @Override
			        public void
					handleEvent(Event event)
			        {
			        	setDefaults();
			        }
			    });

	    for (int i=0;i<managers.length;i++){
	    	managers[i].addListener(this);
	    }


		Group gTorrentInfo = Utils.createSkinnedGroup(panel, SWT.NULL);
		Messages.setLanguageText(gTorrentInfo, "label.aggregate.info");
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		gTorrentInfo.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		gTorrentInfo.setLayout(layout);

			// total size

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString( "TableColumn.header.size" ) + ": " );

		agg_size = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_size.setLayoutData( gridData );

			// remaining

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString( "TableColumn.header.remaining" ) + ": " );

		agg_remaining = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_remaining.setLayoutData( gridData );

			// uploaded

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString("MyTracker.column.uploaded") + ": " );

		agg_uploaded = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_uploaded.setLayoutData( gridData );

			// downloaded

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString("MyTracker.column.downloaded") + ": " );

		agg_downloaded = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_downloaded.setLayoutData( gridData );

			// upload speed

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString( "SpeedView.uploadSpeed.title" ) + ": " );

		agg_upload_speed = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_upload_speed.setLayoutData( gridData );

			// download speed

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString( "SpeedView.downloadSpeed.title" ) + ": " );

		agg_download_speed = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_download_speed.setLayoutData( gridData );

			// share ratio

		label = new Label(gTorrentInfo, SWT.NULL );
	    label.setText( MessageText.getString( "TableColumn.header.shareRatio" ) + ": " );

		agg_share_ratio = new BufferedLabel( gTorrentInfo, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		agg_share_ratio.setLayoutData( gridData );


		// reset

	    Label stats_reset_label = new Label(gTorrentInfo, SWT.NULL );
	    Messages.setLanguageText(stats_reset_label, "TorrentOptionsView.param.reset.stats");

	    Button stats_reset_button = new Button(gTorrentInfo, SWT.PUSH);

	    Messages.setLanguageText(stats_reset_button, "TorrentOptionsView.param.reset.button" );

	    stats_reset_button.addListener(SWT.Selection,
	    		new Listener()
				{
			        @Override
			        public void
					handleEvent(Event event)
			        {
			        	for ( DownloadManagerOptionsHandler dm: managers ){

			        		if ( dm.getDownloadManager() != null ){
			        			dm.getDownloadManager().getStats().resetTotalBytesSentReceived( 0, 0 );
			        		}
			        	}
			        }
			    });

		sc.setMinSize( panel.computeSize( SWT.DEFAULT, SWT.DEFAULT ));
		composite.layout();
	}

	private void
	refresh()
	{
		if ( agg_size == null || managers.length == 0 || managers[0].getDownloadManager() == null ){

				// not yet init

			return;
		}

		long	total_size 				= 0;
		long	total_remaining			= 0;
		long	total_good_downloaded	= 0;
		long	total_downloaded		= 0;
		long	total_uploaded			= 0;

		long	total_data_up_speed		= 0;
		long	total_prot_up_speed		= 0;

		long	total_data_down_speed		= 0;
		long	total_prot_down_speed		= 0;

		for (int i=0;i<managers.length;i++){

			DownloadManagerOptionsHandler	dm = managers[i];

			DownloadManagerStats stats = dm.getDownloadManager().getStats();

			total_size += stats.getSizeExcludingDND();

			total_remaining += stats.getRemainingExcludingDND();

			long	good_received 	= stats.getTotalGoodDataBytesReceived();
			long	received 		= stats.getTotalDataBytesReceived();
			long	sent			= stats.getTotalDataBytesSent();

			total_good_downloaded 	+= good_received;
			total_downloaded 		+= received;
			total_uploaded			+= sent;

			total_data_up_speed 		+= stats.getDataSendRate();
			total_prot_up_speed 		+= stats.getProtocolSendRate();

			total_data_down_speed 	+= stats.getDataReceiveRate();
			total_prot_down_speed 	+= stats.getProtocolReceiveRate();
		}

		agg_size.setText( DisplayFormatters.formatByteCountToKiBEtc( total_size ));
		agg_remaining.setText( DisplayFormatters.formatByteCountToKiBEtc( total_remaining ));
		agg_uploaded.setText( DisplayFormatters.formatByteCountToKiBEtc( total_uploaded ));
		agg_downloaded.setText( DisplayFormatters.formatByteCountToKiBEtc( total_downloaded ));

		agg_upload_speed.setText( DisplayFormatters.formatDataProtByteCountToKiBEtc( total_data_up_speed, total_prot_up_speed ));
		agg_download_speed.setText( DisplayFormatters.formatDataProtByteCountToKiBEtc( total_data_down_speed, total_prot_down_speed));

		long	sr;

		if ( total_good_downloaded == 0 ){

			if ( total_uploaded == 0 ){

				sr = 1000;
			}else{

				sr = -1;
			}
		}else{

			sr = 1000*total_uploaded/total_good_downloaded;
		}

		String	share_ratio_str;

		if ( sr == -1 ){

			share_ratio_str = Constants.INFINITY_STRING;

		}else{

			String partial = "" + sr%1000;

			while( partial.length() < 3 ){

				partial = "0" + partial;
			}

			share_ratio_str = (sr/1000) + "." + partial;
		}

		agg_share_ratio.setText( share_ratio_str );
	}

	protected void
	setDefaults()
	{
		Iterator<Entry<String, BaseSwtParameter<?,?>>> it = ds_parameters.entrySet().iterator();

		while( it.hasNext()){

			Map.Entry<String, BaseSwtParameter<?,?>>	entry = it.next();
			
			String	key 	= entry.getKey();
			
		    for (int i=0;i<managers.length;i++){

		    	managers[i].setParameterDefault( key );
		    }
		    
		    	// we need an explicit refresh here because of the way the reset works - by the time to code
		    	// runs to see if the displayed value has changed from the parameter value the value has been
		    	// reset and appears not to have changed (even though the parameter has...)
		    
		    entry.getValue().refreshControl();
		}

		it = adhoc_parameters.entrySet().iterator();

		while ( it.hasNext()){

			Map.Entry<String, BaseSwtParameter<?,?>>	entry = it.next();
			
			BaseSwtParameter<?,?> param = entry.getValue();
			
			if ( param instanceof IntSwtParameter ){

				IntSwtParameter	int_param = (IntSwtParameter)param;

				int_param.setValue( 0 );
				
			}else{
				Debug.out( "Unknown parameter type: " + param.getClass());
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.download.DownloadManagerStateAttributeListener#attributeEventOccurred(com.biglybt.core.download.DownloadManager, java.lang.String, int)
	 */
	@Override
	public void
	parameterChanged(
		DownloadManagerOptionsHandler manager )
	{
		Utils.execSWTThread(new Runnable() {
			@Override
			public void	run() {
				Iterator<Entry<String, BaseSwtParameter<?,?>>> it = ds_parameters.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, BaseSwtParameter<?,?>>	entry = it.next();
					String	key 	= entry.getKey();
					Object	param 	= entry.getValue();

					if (param instanceof IntSwtParameter) {
						IntSwtParameter	int_param = (IntSwtParameter)param;
						int	value = manager.getIntParameter( key );
						int_param.setValue( value );
					} else if (param instanceof BooleanSwtParameter) {
						BooleanSwtParameter	bool_param = (BooleanSwtParameter)param;
						boolean	value = manager.getBooleanParameter( key );
						bool_param.setSelected( value );
					} else if (param instanceof FloatSwtParameter) {
						FloatSwtParameter	float_param = (FloatSwtParameter)param;
						float	value = manager.getIntParameter( key )/1000f;
						float_param.setValue( value );
					} else {
						Debug.out( "Unknown parameter type: " + param.getClass());
					}
				}
			}
		}, true);
	}

	private Composite
	getComposite()
	{
		return sc;
	}

	private String
	getFullTitle()
	{
		return MessageText.getString( multi_view?"TorrentOptionsView.multi.title.full":"TorrentOptionsView.title.full");
	}

	private void
	delete()
	{
		if ( headerFont != null ){

			headerFont.dispose();
		}

		if (managers != null) {
			for (int i = 0; i < managers.length; i++) {
				managers[i].removeListener( this );
			}
		}
	}



	protected class
	adhocIntParameterAdapter
		implements IntSwtParameter.ValueProcessor
	{
		@Override
		public Integer getValue(IntSwtParameter p) {
			String key = p.getParamID();
			if (MAX_UPLOAD.equals(key)){
				int	result = 0;

				for (int i=0;i<managers.length;i++){
					int	val = managers[i].getUploadRateLimitBytesPerSecond()/DisplayFormatters.getKinB();

					if ( i==0 ){
						result = val;
					}else if ( result != val ){
						return( 0 );
					}
				}

				return( result );

			}else if (MAX_DOWNLOAD.equals(key)){
				int	result = 0;

				for (int i=0;i<managers.length;i++){
					int	val = managers[i].getDownloadRateLimitBytesPerSecond()/DisplayFormatters.getKinB();

					if ( i==0 ){
						result = val;
					}else if ( result != val ){
						return( 0 );
					}
				}

				return( result );
			}else{
				Debug.out( "Unknown key '" + key + "'" );
				return(0);
			}
		}

		@Override
		public boolean setValue(IntSwtParameter p, Integer value) {
			boolean changed = false;
			String key = p.getParamID();
			if (key.equals(MAX_UPLOAD)){
				for (int i=0;i<managers.length;i++){

					DownloadManagerOptionsHandler	manager = managers[i];

					if ( value != manager.getUploadRateLimitBytesPerSecond()/DisplayFormatters.getKinB()){

						manager.setUploadRateLimitBytesPerSecond(value*DisplayFormatters.getKinB());
						changed = true;
					}
				}
			}else if (key.equals(MAX_DOWNLOAD)){
				for (int i=0;i<managers.length;i++){

					DownloadManagerOptionsHandler	manager = managers[i];

					if ( value != manager.getDownloadRateLimitBytesPerSecond()/DisplayFormatters.getKinB()){

						manager.setDownloadRateLimitBytesPerSecond(value*DisplayFormatters.getKinB());
						changed = true;
					}
				}
			}else{
				Debug.out( "Unknown key '" + key + "'" );
			}
			return changed;
		}
	}

	protected class
	downloadStateIntParameterAdapter
			implements IntSwtParameter.ValueProcessor
	{
		@Override
		public Integer getValue(IntSwtParameter p) {
			int	result = 0;
			String key = p.getParamID();

			for (int i=0;i<managers.length;i++){
				int	val = managers[i].getIntParameter( key );

				if ( i==0 ){
					result = val;
				}else if ( result != val ){
					return( 0 );
				}
			}

			return( result );
		}

		public boolean setValue(IntSwtParameter p, Integer value) {
			boolean changed = false;
			String key = p.getParamID();

			for (int i=0;i<managers.length;i++){

				DownloadManagerOptionsHandler	manager = managers[i];

				if ( value != manager.getIntParameter( key )){

					manager.setIntParameter( key, value );
					changed = true;
				}
			}
			return changed;
		}
	}

	protected class
	downloadStateBooleanParameterAdapter
			implements BooleanSwtParameter.ValueProcessor
	{

		@Override
		public Boolean
		getValue(
				BooleanSwtParameter	p )
		{
			boolean	result = false;
			String key = p.getParamID();

			for (int i=0;i<managers.length;i++){
				boolean	val = managers[i].getBooleanParameter( key );

				if ( i==0 ){
					result = val;
				}else if ( result != val ){
					return( false );
				}
			}

			return( result );
		}

		@Override
		public boolean
		setValue(
				BooleanSwtParameter		p,
				Boolean		value )
		{
			boolean changed = managers.length == 0;
			String key = p.getParamID();

			for (int i=0;i<managers.length;i++){

				DownloadManagerOptionsHandler	manager = managers[i];

				if ( value != manager.getBooleanParameter( key )){

					manager.setBooleanParameter( key, value );
					changed = true;
				}
			}
			return changed;
		}
	}

	protected class
	downloadStateFloatParameterAdapter
			implements FloatSwtParameter.ValueProcessor
	{
		@Override
		public Float
		getValue(
				FloatSwtParameter		p )
		{
			int	result = 0;
			String key = p.getParamID();

			for (int i=0;i<managers.length;i++){
				int	val = managers[i].getIntParameter( key );

				if ( i==0 ){
					result = val;
				}else if ( result != val ){
					return( 0f );
				}
			}

			return( result/1000.0f );
		}

		@Override
		public boolean
		setValue(
				FloatSwtParameter		p,
				Float		_value )
		{
			boolean changed = managers.length == 0;
			int	value = (int)(_value*1000);
			String key = p.getParamID();

			for (int i=0;i<managers.length;i++){

				DownloadManagerOptionsHandler	manager = managers[i];

				if ( value != manager.getIntParameter( key )){

					manager.setIntParameter( key, value );
					changed = true;
				}
			}
			return changed;
		}
	}

	private void dataSourceChanged(Object newDataSource) {
		DownloadManagerOptionsHandler[] old_managers = managers;
		if (old_managers != null) {
			for (int i = 0; i < old_managers.length; i++) {
				old_managers[i].removeListener( this );
			}
		}
		if ( newDataSource instanceof DownloadManagerOptionsHandler ){
			multi_view = false;
			managers = new DownloadManagerOptionsHandler[] {(DownloadManagerOptionsHandler)newDataSource};
		} else {
			DownloadManager[] dms = DataSourceUtils.getDMs(newDataSource);
			managers = new DownloadManagerOptionsHandler[dms.length];
			for (int i = 0, dmsLength = dms.length; i < dmsLength; i++) {
				DownloadManager dm = dms[i];
				managers[i] = new DMWrapper(dm);
			}
			multi_view = managers.length > 1;
		}

		
		if (parent != null && !parent.isDisposed()) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if ( !parent.isDisposed()){

						initialize(parent);
					}
				}
			});
		}
		swtView.setTitle(getFullTitle());
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	dataSourceChanged(event.getData());
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
    	refresh();
        break;
    }

    return true;
  }
	
	private static class
	DMWrapper
		implements DownloadManagerOptionsHandler, DownloadManagerStateAttributeListener
	{
		private DownloadManager		dm;
		
		private CopyOnWriteList<ParameterChangeListener>	listeners = new CopyOnWriteList<>();
		
		private
		DMWrapper(
			DownloadManager		_dm )
		{
			dm	= _dm;
		}
		
		@Override 
		public String getName(){
			return( dm.getDisplayName());
		}
		@Override
		public void setIntParameter(String name, int value){
			dm.getDownloadState().setIntParameter(name, value);
		}
		
		@Override
		public int getIntParameter(String name){
			return( dm.getDownloadState().getIntParameter(name));
		}
		
		@Override
		public void setBooleanParameter(String name, boolean value){
			dm.getDownloadState().setBooleanParameter(name, value);
		}
		
		@Override
		public boolean getBooleanParameter(String name){
			return( dm.getDownloadState().getBooleanParameter(name));
		}
		
		@Override
		public void setParameterDefault(String key){
			dm.getDownloadState().setParameterDefault( key );
		}
		
		@Override
		public int
		getUploadRateLimitBytesPerSecond()
		{
			return( dm.getStats().getUploadRateLimitBytesPerSecond());
		}
		
		@Override
		public void
		setUploadRateLimitBytesPerSecond(
			int		limit )
		{
			dm.getStats().setUploadRateLimitBytesPerSecond( limit );
		}
		
		@Override
		public int
		getDownloadRateLimitBytesPerSecond()
		{
			return( dm.getStats().getDownloadRateLimitBytesPerSecond());
		}
		
		@Override
		public void
		setDownloadRateLimitBytesPerSecond(
			int		limit )
		{
			dm.getStats().setDownloadRateLimitBytesPerSecond( limit );
		}
		
		@Override
		public DownloadManager getDownloadManager(){
			return( dm );
		}
		
		@Override
		public void attributeEventOccurred(DownloadManager dm, String attribute_name, int event_type) {
			for (ParameterChangeListener l: listeners ){
				try{
					l.parameterChanged( this );
				}catch( Throwable e ){
					Debug.out( e );
				}
			}
		}
		
		@Override
		public void
		addListener(
			ParameterChangeListener	listener )
		{
			listeners.add( listener );
			
			dm.getDownloadState().addListener(this, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN);
		}
		@Override
		public void
		removeListener(
			ParameterChangeListener	listener )
		{
			listeners.remove( listener );
			
			dm.getDownloadState().removeListener(this, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN);
		}
	}
}
