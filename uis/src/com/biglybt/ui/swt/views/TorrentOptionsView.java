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

import java.util.*;
import java.util.Map.Entry;

import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.config.ChangeSelectionActionPerformer;
import com.biglybt.ui.swt.config.generic.GenericBooleanParameter;
import com.biglybt.ui.swt.config.generic.GenericFloatParameter;
import com.biglybt.ui.swt.config.generic.GenericIntParameter;
import com.biglybt.ui.swt.config.generic.GenericParameterAdapter;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.ui.swt.imageloader.ImageLoader;

public class
TorrentOptionsView
	implements DownloadManagerStateAttributeListener, UISWTViewCoreEventListener
{
		// adhoc parameters need explicit code to reset default values below

	private static final String	MAX_UPLOAD		= "max.upload";
	private static final String	MAX_DOWNLOAD	= "max.download";

	public static final String MSGID_PREFIX = "TorrentOptionsView";

	private boolean						multi_view;
	private DownloadManager[]			managers;

	private GenericParameterAdapter	ds_param_adapter	= new downloadStateParameterAdapter();
	private GenericParameterAdapter	adhoc_param_adapter	= new adhocParameterAdapter();

	private Map<String, Object> adhoc_parameters	= new HashMap<>();
	private Map<String, Object>	ds_parameters 		= new HashMap<>();

	private Composite 			panel;
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

	/**
	 * @param managers2
	 */
	public TorrentOptionsView(DownloadManager[] managers2) {
		dataSourceChanged(managers2);
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
		if (panel != null && !panel.isDisposed()) {
			Utils.disposeComposite(panel, false);
		} else {
			panel = new Composite(composite, SWT.NULL);

			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 1;
			panel.setLayout(layout);

			Layout parentLayout = parent.getLayout();
			if (parentLayout instanceof FormLayout) {
				Utils.setLayoutData(panel, Utils.getFilledFormData());
			} else {
				Utils.setLayoutData(panel, new GridData(GridData.FILL_BOTH));
			}
		}


		if (managers == null) {
			return;
		}

		int userMode = COConfigurationManager.getIntParameter("User Mode");

			// header

		Composite cHeader = new Composite(panel, SWT.BORDER);
		GridLayout configLayout = new GridLayout();
		configLayout.marginHeight = 3;
		configLayout.marginWidth = 0;
		cHeader.setLayout(configLayout);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER);
		Utils.setLayoutData(cHeader, gridData);

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
			lHeader.setText( " " + MessageText.getString( "authenticator.torrent" ) + " : " + managers[0].getDisplayName().replaceAll("&", "&&"));
		}else{
			String	str = "";

			for (int i=0;i<Math.min( 3, managers.length ); i ++ ){

				str += (i==0?"":", ") + managers[i].getDisplayName().replaceAll("&", "&&");
			}

			if ( managers.length > 3 ){

				str += "...";
			}

			lHeader.setText( " " + managers.length + " " + MessageText.getString( "ConfigView.section.torrents" ) + " : " + str );
		}

		gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER);
		Utils.setLayoutData(lHeader, gridData);

		Group gTorrentOptions = new Group(panel, SWT.NULL);
		Messages.setLanguageText(gTorrentOptions, "ConfigView.section.transfer");
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(gTorrentOptions, gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		gTorrentOptions.setLayout(layout);

		//Disabled for release. Need to convert from user-specified units to
	    //KB/s before restoring the following line
	    //String k_unit = DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB).trim()
	    String k_unit = DisplayFormatters.getRateUnitBase10(DisplayFormatters.UNIT_KB).trim();

			// max upload speed

		Label label = new Label(gTorrentOptions, SWT.NULL);
		gridData = new GridData();
		Utils.setLayoutData(label,  gridData );
		label.setText(k_unit + " " + MessageText.getString( "GeneralView.label.maxuploadspeed.tooltip" ));

		GenericIntParameter max_upload = new GenericIntParameter(
				adhoc_param_adapter, gTorrentOptions, MAX_UPLOAD);
		adhoc_parameters.put( MAX_UPLOAD, max_upload );
		gridData = new GridData();
		max_upload.setLayoutData(gridData);

		if ( userMode > 0) {

				// max upload when busy

			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TorrentOptionsView.param.max.uploads.when.busy");

			GenericIntParameter max_upload_when_busy = new GenericIntParameter(
					ds_param_adapter, gTorrentOptions,
					DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY, max_upload_when_busy );
			gridData = new GridData();
			max_upload_when_busy.setLayoutData(gridData);
		}

			// max download speed

		label = new Label(gTorrentOptions, SWT.NULL);
		gridData = new GridData();
		Utils.setLayoutData(label,  gridData );
		label.setText(k_unit + " " + MessageText.getString( "GeneralView.label.maxdownloadspeed.tooltip" ));

		GenericIntParameter max_download = new GenericIntParameter(
				adhoc_param_adapter, gTorrentOptions, MAX_DOWNLOAD);
		adhoc_parameters.put( MAX_DOWNLOAD, max_download );
		gridData = new GridData();
		max_download.setLayoutData(gridData);

			// max uploads

		if (userMode > 0) {
			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TorrentOptionsView.param.max.uploads" );

			GenericIntParameter max_uploads = new GenericIntParameter(
					ds_param_adapter, gTorrentOptions,
					DownloadManagerState.PARAM_MAX_UPLOADS);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOADS, max_uploads );
			max_uploads.setMinimumValue(2);
			gridData = new GridData();
			max_uploads.setLayoutData(gridData);

				//	max uploads when seeding enabled

			final Composite cMaxUploadsOptionsArea = new Composite(gTorrentOptions, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			cMaxUploadsOptionsArea.setLayout(layout);
			gridData = new GridData();
			gridData.horizontalIndent = 15;
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(cMaxUploadsOptionsArea, gridData);

			label = new Label(cMaxUploadsOptionsArea, SWT.NULL);
			ImageLoader.getInstance().setLabelImage(label, "subitem");
			gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
			Utils.setLayoutData(label, gridData);

			gridData = new GridData();
			GenericBooleanParameter	max_uploads_when_seeding_enabled =
				new GenericBooleanParameter(
						ds_param_adapter,
						cMaxUploadsOptionsArea,
						DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED,
						false,
						"TorrentOptionsView.param.alternative.value.enable");
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED, max_uploads_when_seeding_enabled );
			max_uploads_when_seeding_enabled.setLayoutData( gridData );


			GenericIntParameter max_uploads_when_seeding = new GenericIntParameter(
					ds_param_adapter, cMaxUploadsOptionsArea,
					DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING, max_uploads_when_seeding );
			gridData = new GridData();
			max_uploads_when_seeding.setMinimumValue(2);
			max_uploads_when_seeding.setLayoutData(gridData);

			max_uploads_when_seeding_enabled.setAdditionalActionPerformer(
					new ChangeSelectionActionPerformer( max_uploads_when_seeding.getControl()));

				// max peers

			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TorrentOptionsView.param.max.peers");

			GenericIntParameter max_peers = new GenericIntParameter(ds_param_adapter,
					gTorrentOptions, DownloadManagerState.PARAM_MAX_PEERS);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_PEERS, max_peers );
			gridData = new GridData();
			max_peers.setLayoutData(gridData);

				// max peers when seeding

			final Composite cMaxPeersOptionsArea = new Composite(gTorrentOptions, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			cMaxPeersOptionsArea.setLayout(layout);
			gridData = new GridData();
			gridData.horizontalIndent = 15;
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(cMaxPeersOptionsArea, gridData);

			label = new Label(cMaxPeersOptionsArea, SWT.NULL);
			ImageLoader.getInstance().setLabelImage(label, "subitem");
			gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
			Utils.setLayoutData(label, gridData);

			gridData = new GridData();
			GenericBooleanParameter	max_peers_when_seeding_enabled =
				new GenericBooleanParameter(
						ds_param_adapter,
						cMaxPeersOptionsArea,
						DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED,
						false,
						"TorrentOptionsView.param.alternative.value.enable");
			ds_parameters.put( DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED, max_peers_when_seeding_enabled );
			max_peers_when_seeding_enabled.setLayoutData( gridData );


			GenericIntParameter max_peers_when_seeding = new GenericIntParameter(
					ds_param_adapter, cMaxPeersOptionsArea,
					DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING, max_peers_when_seeding );
			gridData = new GridData();
			max_peers_when_seeding.setLayoutData(gridData);

			max_peers_when_seeding_enabled.setAdditionalActionPerformer(
					new ChangeSelectionActionPerformer( max_peers_when_seeding.getControl()));


				// max seeds

			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TorrentOptionsView.param.max.seeds" );

			GenericIntParameter max_seeds = new GenericIntParameter(
					ds_param_adapter, gTorrentOptions,
					DownloadManagerState.PARAM_MAX_SEEDS);
			ds_parameters.put( DownloadManagerState.PARAM_MAX_SEEDS, max_seeds );
			gridData = new GridData();
			max_seeds.setLayoutData(gridData);
		}

			// upload priority

		if ( userMode > 0){

			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TorrentOptionsView.param.upload.priority" );

			gridData = new GridData();
			GenericIntParameter	upload_priority_enabled =
				new GenericIntParameter(
						ds_param_adapter,
						gTorrentOptions,
						DownloadManagerState.PARAM_UPLOAD_PRIORITY, 0, 1 );

			ds_parameters.put( DownloadManagerState.PARAM_UPLOAD_PRIORITY, upload_priority_enabled );
			upload_priority_enabled.setLayoutData( gridData );

				// min sr

			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TableColumn.header.min_sr" );

			gridData = new GridData();
			gridData.widthHint=50;
			GenericFloatParameter	min_sr =
				new GenericFloatParameter(
						ds_param_adapter,
						gTorrentOptions,
						DownloadManagerState.PARAM_MIN_SHARE_RATIO, 0, Float.MAX_VALUE, true, 3 );

			ds_parameters.put( DownloadManagerState.PARAM_MIN_SHARE_RATIO, min_sr );
			min_sr.setLayoutData( gridData );

				// max sr

			label = new Label(gTorrentOptions, SWT.NULL);
			gridData = new GridData();
			Utils.setLayoutData(label,  gridData );
			Messages.setLanguageText(label, "TableColumn.header.max_sr" );

			gridData = new GridData();
			gridData.widthHint=50;
			GenericFloatParameter	max_sr =
				new GenericFloatParameter(
						ds_param_adapter,
						gTorrentOptions,
						DownloadManagerState.PARAM_MAX_SHARE_RATIO, 0, Float.MAX_VALUE, true, 3 );

			ds_parameters.put( DownloadManagerState.PARAM_MAX_SHARE_RATIO, max_sr );
			max_sr.setLayoutData( gridData );
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
	    	managers[i].getDownloadState().addListener(this, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN);
	    }


		Group gTorrentInfo = new Group(panel, SWT.NULL);
		Messages.setLanguageText(gTorrentInfo, "label.aggregate.info");
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(gTorrentInfo, gridData);
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
			        	for ( DownloadManager dm: managers ){

			        		dm.getStats().resetTotalBytesSentReceived( 0, 0 );
			        	}
			        }
			    });

	    panel.layout(true, true);
	}

	private void
	refresh()
	{
		if ( agg_size == null ){

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

			DownloadManager	dm = managers[i];

			DownloadManagerStats stats = dm.getStats();

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
		Iterator<?>	it = ds_parameters.keySet().iterator();

		while( it.hasNext()){

			String	key 	= (String)it.next();

		    for (int i=0;i<managers.length;i++){

		    	managers[i].getDownloadState().setParameterDefault( key );
		    }
		}

		it = adhoc_parameters.values().iterator();

		while ( it.hasNext()){

			Object	param 	= it.next();

			if ( param instanceof GenericIntParameter ){

				GenericIntParameter	int_param = (GenericIntParameter)param;

				int_param.setValue( 0, true );

			}else{
				Debug.out( "Unknown parameter type: " + param.getClass());
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.download.DownloadManagerStateAttributeListener#attributeEventOccurred(com.biglybt.core.download.DownloadManager, java.lang.String, int)
	 */
	@Override
	public void attributeEventOccurred(DownloadManager dm, String attribute_name, int event_type) {
		final DownloadManagerState state = dm.getDownloadState();
		Utils.execSWTThread(new Runnable() {
			@Override
			public void	run() {
				Iterator<Entry<String, Object>> it = ds_parameters.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Object>	entry = it.next();
					String	key 	= entry.getKey();
					Object	param 	= entry.getValue();

					if (param instanceof GenericIntParameter) {
						GenericIntParameter	int_param = (GenericIntParameter)param;
						int	value = state.getIntParameter( key );
						int_param.setValue( value );
					} else if (param instanceof GenericBooleanParameter) {
						GenericBooleanParameter	bool_param = (GenericBooleanParameter)param;
						boolean	value = state.getBooleanParameter( key );
						bool_param.setSelected( value );
					} else if (param instanceof GenericFloatParameter) {
						GenericFloatParameter	float_param = (GenericFloatParameter)param;
						float	value = state.getIntParameter( key )/1000f;
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
		return panel;
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
				managers[i].getDownloadState().removeListener(this,
						DownloadManagerState.AT_PARAMETERS,
						DownloadManagerStateAttributeListener.WRITTEN);
			}
		}
	}



	protected class
	adhocParameterAdapter
		extends GenericParameterAdapter
	{
		@Override
		public int
		getIntValue(
			String	key )
		{
			return( getIntValue( key, 0 ));
		}

		@Override
		public int
		getIntValue(
			String	key,
			int		def )
		{
			if ( key == MAX_UPLOAD ){
				int	result = def;

				for (int i=0;i<managers.length;i++){
					int	val = managers[i].getStats().getUploadRateLimitBytesPerSecond()/DisplayFormatters.getKinB();

					if ( i==0 ){
						result = val;
					}else if ( result != val ){
						return( def );
					}
				}

				return( result );

			}else if ( key == MAX_DOWNLOAD ){
				int	result = def;

				for (int i=0;i<managers.length;i++){
					int	val = managers[i].getStats().getDownloadRateLimitBytesPerSecond()/DisplayFormatters.getKinB();

					if ( i==0 ){
						result = val;
					}else if ( result != val ){
						return( def );
					}
				}

				return( result );
			}else{
				Debug.out( "Unknown key '" + key + "'" );
				return(0);
			}
		}

		@Override
		public void
		setIntValue(
			String	key,
			int		value )
		{
			if ( key == MAX_UPLOAD ){
				for (int i=0;i<managers.length;i++){

					DownloadManager	manager = managers[i];

					if ( value != manager.getStats().getUploadRateLimitBytesPerSecond()/DisplayFormatters.getKinB()){

						manager.getStats().setUploadRateLimitBytesPerSecond(value*DisplayFormatters.getKinB());
					}
				}
			}else if ( key == MAX_DOWNLOAD ){
				for (int i=0;i<managers.length;i++){

					DownloadManager	manager = managers[i];

					if ( value != manager.getStats().getDownloadRateLimitBytesPerSecond()/DisplayFormatters.getKinB()){

						manager.getStats().setDownloadRateLimitBytesPerSecond(value*DisplayFormatters.getKinB());
					}
				}
			}else{
				Debug.out( "Unknown key '" + key + "'" );
			}
		}
	}

	protected class
	downloadStateParameterAdapter
		extends GenericParameterAdapter
	{
		@Override
		public int
		getIntValue(
			String	key )
		{
			return( getIntValue( key, 0 ));
		}

		@Override
		public int
		getIntValue(
			String	key,
			int		def )
		{
			int	result = def;

			for (int i=0;i<managers.length;i++){
				int	val = managers[i].getDownloadState().getIntParameter( key );

				if ( i==0 ){
					result = val;
				}else if ( result != val ){
					return( def );
				}
			}

			return( result );
		}

		@Override
		public void
		setIntValue(
			String	key,
			int		value )
		{
			for (int i=0;i<managers.length;i++){

				DownloadManager	manager = managers[i];

				if ( value != manager.getDownloadState().getIntParameter( key )){

					manager.getDownloadState().setIntParameter( key, value );
				}
			}
		}

		@Override
		public Boolean
		getBooleanValue(
			String	key )
		{
			return( getBooleanValue(key,false));
		}

		@Override
		public Boolean
		getBooleanValue(
			String		key,
			Boolean		def )
		{
			boolean	result = def;

			for (int i=0;i<managers.length;i++){
				boolean	val = managers[i].getDownloadState().getBooleanParameter( key );

				if ( i==0 ){
					result = val;
				}else if ( result != val ){
					return( def );
				}
			}

			return( result );
		}

		@Override
		public void
		setBooleanValue(
			String		key,
			boolean		value )
		{
			for (int i=0;i<managers.length;i++){

				DownloadManager	manager = managers[i];

				if ( value != manager.getDownloadState().getBooleanParameter( key )){

					manager.getDownloadState().setBooleanParameter( key, value );
				}
			}
		}

		@Override
		public float
		getFloatValue(
			String		key )
		{
			int	result = 0;

			for (int i=0;i<managers.length;i++){
				int	val = managers[i].getDownloadState().getIntParameter( key );

				if ( i==0 ){
					result = val;
				}else if ( result != val ){
					return( 0 );
				}
			}

			return( result/1000.0f );
		}

		@Override
		public void
		setFloatValue(
			String		key,
			float		_value )
		{
			int	value = (int)(_value*1000);

			for (int i=0;i<managers.length;i++){

				DownloadManager	manager = managers[i];

				if ( value != manager.getDownloadState().getIntParameter( key )){

					manager.getDownloadState().setIntParameter( key, value );
				}
			}
		}
	}

	private void dataSourceChanged(Object newDataSource) {
		DownloadManager[] old_managers = managers;
		if (old_managers != null) {
			for (int i = 0; i < old_managers.length; i++) {
				old_managers[i].getDownloadState().removeListener(this,
						DownloadManagerState.AT_PARAMETERS,
						DownloadManagerStateAttributeListener.WRITTEN);
			}
		}
		if (newDataSource instanceof DownloadManager) {
			multi_view = false;
			managers = new DownloadManager[] { (DownloadManager) newDataSource };
		} else if (newDataSource instanceof DownloadManager[]) {
			multi_view = true;
			managers = (DownloadManager[]) newDataSource;
		}else if ( newDataSource instanceof Object[]){
			Object[] objs = (Object[])newDataSource;
			if ( objs.length > 0 ){
				if ( objs[0] instanceof DownloadManager ){
					managers = new DownloadManager[objs.length];
					for ( int i=0;i<objs.length;i++){
						managers[i] = (DownloadManager)objs[i];
					}
					multi_view = true;
				}
			}
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
}
