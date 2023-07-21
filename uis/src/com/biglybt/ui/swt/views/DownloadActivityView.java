/*
 * Created on 2 juil. 2003
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
package com.biglybt.ui.swt.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.GeneralUtils.SmoothAverage;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.components.graphics.MultiPlotGraphic;
import com.biglybt.ui.swt.components.graphics.ValueFormater;
import com.biglybt.ui.swt.components.graphics.ValueSource;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mdi.MdiSWTMenuHackListener;
import com.biglybt.ui.swt.mdi.TabbedEntry;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.pif.ui.UIPluginViewToolBarListener;


/**
 * aka "Speed" sub view
 */
public class
DownloadActivityView
	implements UISWTViewCoreEventListener, UIPluginViewToolBarListener, MdiSWTMenuHackListener, ParameterListener
{
	public static final String MSGID_PREFIX = "DownloadActivityView";

	private static Color[]	mpg_colors = {
		Colors.fadedGreen, Colors.fadedGreen,
		Colors.blues[Colors.BLUES_DARKEST], Colors.blues[Colors.BLUES_DARKEST],
		Colors.light_grey };

	private static final int ETA_AVERAGE_TICKS = 30;
	
	private static Color[]	eta_colors = { Colors.fadedGreen, Colors.light_grey };

	private UISWTView 				swtView;
	private boolean					legend_at_bottom = true;
	private Composite				panel;
	private boolean					viewBuilt;
	
	private MultiPlotGraphic 		mpg;
	private MultiPlotGraphic		eta;
	
	private List<DownloadManager> 		managers = new ArrayList<>();

	private Composite parent;

	private boolean show_time = true;
	
	public
	DownloadActivityView()
	{
	}

	private String
	getFullTitle()
	{
		return( MessageText.getString(MSGID_PREFIX + ".title.full" ));
	}

	public void
	initialize(
		Composite parent )
	{
	    this.parent = parent;
	    
	    if ( panel == null || panel.isDisposed()){
	    	
	    	viewBuilt = false;
	    }
	    
		panel = new Composite(parent,SWT.NULL);
			   
		panel.setLayout( new FormLayout());
	}

	private void 
	fillPanel() 
	{
		Utils.disposeComposite(panel, false);

	    GridData gridData;
	    
	    // download graphic
	    
	    Composite mpg_panel 	= new Composite( panel, SWT.NULL );
	    
	    {
	    	FormData	formData = new FormData();
	    	formData.left = new FormAttachment( 0, 0 );
	    	formData.right = new FormAttachment( show_time?75:100, 0 );
	    	formData.top = new FormAttachment( 0, 0 );
	    	formData.bottom = new FormAttachment( 100, 0 );
	    	
	    	mpg_panel.setLayoutData( formData );
	    	
	    	mpg_panel.setLayout(new GridLayout(legend_at_bottom?1:2, false));

		    ValueFormater formatter =
		    	new ValueFormater()
		    	{
		        	@Override
			        public String
		        	format(
		        		int value)
		        	{
		        		return DisplayFormatters.formatByteCountToKiBEtcPerSec( value );
		        	}
		    	};
	
	
		    final ValueSourceImpl[] sources = {
		    	new ValueSourceImpl( "Up", 0, mpg_colors, true, false, false )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			List<DownloadManager> dms = managers;
	
		    			int	res = 0;
		    			
		    			for ( DownloadManager dm: dms ){
		    						    			
		    				DownloadManagerStats stats = dm.getStats();
	
		    				res += (int)(stats.getDataSendRate());
		    			}
		    			
		    			return( res );
		    		}
		    	},
		    	new ValueSourceImpl( "Up Smooth", 1, mpg_colors, true, false, true )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			List<DownloadManager> dms = managers;
		    			
		    			int	res = 0;
		    			
		    			for ( DownloadManager dm: dms ){
		    						    			
		    				DownloadManagerStats stats = dm.getStats();
	
		    				res += (int)(stats.getSmoothedDataSendRate());
		    			}
		    			
		    			return( res );
		    		}
		    	},
		    	new ValueSourceImpl( "Down", 2, mpg_colors, false, false, false )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			List<DownloadManager> dms = managers;
		    			
		    			int	res = 0;
		    			
		    			for ( DownloadManager dm: dms ){
		    						    			
		    				DownloadManagerStats stats = dm.getStats();
	
		    				res += (int)(stats.getDataReceiveRate());
		    			}
		    			
		    			return( res );
		    		}
		    	},
		    	new ValueSourceImpl( "Down Smooth", 3, mpg_colors, false, false, true )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			List<DownloadManager> dms = managers;
		    			
		    			int	res = 0;
		    			
		    			for ( DownloadManager dm: dms ){
		    						    			
		    				DownloadManagerStats stats = dm.getStats();
	
		    				res += (int)(stats.getSmoothedDataReceiveRate());
		    			}
		    			
		    			return( res );
		    		}
		    	},
		    	new ValueSourceImpl( "Swarm Peer Average", 4, mpg_colors, false, true, false )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			List<DownloadManager> dms = managers;
		    			
		    			int	res = 0;
		    			
		    			for ( DownloadManager dm: dms ){
		    						    				
		    				res += (int)(dm.getStats().getTotalAveragePerPeer());
		    			}
		    			
		    			return( res );
		    		}
		    	}
		    };
	
		    if ( mpg != null ){
		    	
		    	mpg.dispose();
		    }
		    
			final MultiPlotGraphic f_mpg = mpg = MultiPlotGraphic.getInstance( sources, formatter );
	
	
			String[] color_configs = new String[] {
					"DownloadActivityView.legend.up",
					"DownloadActivityView.legend.up_smooth",
					"DownloadActivityView.legend.down",
					"DownloadActivityView.legend.down_smooth",
					"DownloadActivityView.legend.peeraverage",
				};
	
			Legend.LegendListener legend_listener =
				new Legend.LegendListener()
				{
					private int	hover_index = -1;
	
					@Override
					public void
					hoverChange(
						boolean 	entry,
						int 		index )
					{
						if ( hover_index != -1 ){
	
							sources[hover_index].setHover( false );
						}
	
						if ( entry ){
	
							hover_index = index;
	
							sources[index].setHover( true );
						}
	
						f_mpg.refresh( true );
					}
	
					@Override
					public void
					visibilityChange(
						boolean	visible,
						int		index )
					{
						sources[index].setVisible( visible );
	
						f_mpg.refresh( true );
					}
				};
	
	
			if ( !legend_at_bottom ){
	
				gridData = new GridData( GridData.FILL_VERTICAL );
				gridData.verticalAlignment = SWT.CENTER;
	
				Legend.createLegendComposite(mpg_panel, mpg_colors, color_configs, null, gridData, false, legend_listener );
			}
	
		    Composite gSpeed = new Composite(mpg_panel,SWT.NULL);
		    gridData = new GridData(GridData.FILL_BOTH);
		    gSpeed.setLayoutData(gridData);
		    gSpeed.setLayout(new GridLayout());
	
		    if ( legend_at_bottom ){
	
				gridData = new GridData(GridData.FILL_HORIZONTAL);
	
				Legend.createLegendComposite(mpg_panel, mpg_colors, color_configs, null, gridData, true, legend_listener );
	
		    }
	
		    Canvas speedCanvas = new Canvas(gSpeed,SWT.NO_BACKGROUND);
		    gridData = new GridData(GridData.FILL_BOTH);
		    speedCanvas.setLayoutData(gridData);
	
			mpg.initialize( speedCanvas, false );
	    }
	    
	    	// time panel
	    
	    if ( show_time ){
	    	
		    Composite time_panel 	= new Composite( panel, SWT.NULL );

	    	FormData	formData = new FormData();
	    	formData.left = new FormAttachment( mpg_panel, 0 );
	    	formData.right = new FormAttachment( 100, 0 );
	    	formData.top = new FormAttachment( 0, 0 );
	    	formData.bottom = new FormAttachment( 100, 0 );
	    	
	    	time_panel.setLayoutData( formData );
	    	
	    	time_panel.setLayout(new GridLayout(legend_at_bottom?1:2, false));
	    	
		    ValueFormater formatter =
		    	new ValueFormater()
		    	{
		        	@Override
			        public String
		        	format(
		        		int value)
		        	{
		        		return TimeFormatter.format( value );
		        	}
		    	};
	
	
		    final ValueSourceImpl[] sources = {
		    	new ValueSourceImpl( "ETA", 0, eta_colors, ValueSource.STYLE_BLOB, false, false )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			List<DownloadManager> dms = managers;
		    			
		    			int	res = 0;
	    			
			    		for ( DownloadManager dm: dms ){
		    						    			
		    				DownloadManagerStats stats = dm.getStats();
	
		    				res = Math.max( res, Math.max((int)(stats.getETA()), 0 ));
		    			}
		    			
		    			return( res );
		    		}
		    	},
		    	new ValueSourceImpl( "ETA Average", 1, eta_colors, ValueSource.STYLE_BLOB, true, false )
		    	{
		    		@Override
				    public int
		    		getValue()
		    		{
		    			return( eta.getAverage( ETA_AVERAGE_TICKS )[0]);
		    		}
		    	}
		    };
	
		    if ( eta != null ){
		    	
		    	eta.dispose();
		    }
		    
			final MultiPlotGraphic f_eta = eta = MultiPlotGraphic.getInstance( 500, sources, formatter );
	
	
			String[] color_configs = new String[] {
					"DownloadActivityView.legend.eta",
					"DownloadActivityView.legend.etaAverage",
				};
	
			Legend.LegendListener legend_listener =
				new Legend.LegendListener()
				{
					private int	hover_index = -1;
	
					@Override
					public void
					hoverChange(
						boolean 	entry,
						int 		index )
					{
						if ( hover_index != -1 ){
	
							sources[hover_index].setHover( false );
						}
	
						if ( entry ){
	
							hover_index = index;
	
							sources[index].setHover( true );
						}
	
						f_eta.refresh( true );
					}
	
					@Override
					public void
					visibilityChange(
						boolean	visible,
						int		index )
					{
						sources[index].setVisible( visible );
	
						f_eta.refresh( true );
					}
				};
	
	
			if ( !legend_at_bottom ){
	
				gridData = new GridData( GridData.FILL_VERTICAL );
				gridData.verticalAlignment = SWT.CENTER;
	
				Legend.createLegendComposite(time_panel, eta_colors, color_configs, null, gridData, false, legend_listener );
			}
	
		    Composite gSpeed = new Composite(time_panel,SWT.NULL);
		    gridData = new GridData(GridData.FILL_BOTH);
		    gSpeed.setLayoutData(gridData);
		    gSpeed.setLayout(new GridLayout());
	
		    if ( legend_at_bottom ){
	
				gridData = new GridData(GridData.FILL_HORIZONTAL);
	
				Legend.createLegendComposite(time_panel, eta_colors, color_configs, null, gridData, true, legend_listener );
	
		    }
	
		    Canvas speedCanvas = new Canvas(gSpeed,SWT.NO_BACKGROUND);
		    gridData = new GridData(GridData.FILL_BOTH);
		    speedCanvas.setLayoutData(gridData);
	
		    eta.initialize( speedCanvas, false );
	    }
	}

	private void
	refresh(
		boolean	force )
	{
		if ( mpg != null ){
	
			mpg.refresh( force );
		}
		
		if ( eta != null ){
			
			eta.refresh( force );
		}
	}

	public Composite
	getComposite()
	{
		return( panel );
	}

	private boolean comp_visible;
	private Object visible_pending_ds;

	private void
	setVisible( boolean vis )
	{
		if ( vis ){

			comp_visible = true;

			dataSourceChanged( visible_pending_ds );

		}else{

			visible_pending_ds = managers;

			dataSourceChanged( null );

			comp_visible = false;
		}
	}

	public void
	dataSourceChanged(
		Object newDataSource )
	{
		if ( !comp_visible ){
			
			visible_pending_ds = newDataSource;
			
			return;
		}

		List<DownloadManager> newManagers = ViewUtils.getDownloadManagersFromDataSource( newDataSource, managers );

		if ( viewBuilt ){
			
			if ( newManagers.size() == managers.size()){
				
				boolean same = true;
				
				for ( int i=0;i<managers.size();i++){
					
					if ( newManagers.get(i) != managers.get(i)){
				
						same = false;
						
						break;
					}
				}
				
				if ( same ){
					
					return;
				}
			}
		}
		
		managers = newManagers;

		viewBuilt = true;
		
		rebuild();
	}
	
	private void
	rebuild()
	{
		Utils.execSWTThread(()->{
				
			if ( panel == null || panel.isDisposed()){
				return;
			}
			
			Utils.disposeComposite(panel, false);
			
			List<DownloadManager>	dms = managers;
			
			if ( !dms.isEmpty()){
				
				fillPanel();
				
				parent.layout(true, true);
				
				int	min_history_secs = Integer.MAX_VALUE;
				
				for ( DownloadManager dm: managers ){
				
					DownloadManagerStats stats = dm.getStats();

					stats.setRecentHistoryRetention( true );
					
					int[][] _history = stats.getRecentHistory();

					int[] send_history = _history[0];

					min_history_secs = Math.min( min_history_secs, send_history.length );
				}
				
				int[] t_recv 			= new int[min_history_secs];
				int[] t_send			= new int[min_history_secs];
				int[] t_swarm_peer_av	= new int[min_history_secs];
				int[] t_eta				= new int[min_history_secs];

				for ( DownloadManager dm: managers ){
					
					DownloadManagerStats stats = dm.getStats();
					
					int[][] _history = stats.getRecentHistory();

						// reconstitute the smoothed values to the best of our ability (good enough unless we decide we want
						// to throw more memory at remembering this more accurately...)

					int[] send_history 	= _history[0];
					int[] recv_history 	= _history[1];
					int[] sp_history 	= _history[2];
					int[] eta_history 	= _history[3];

					for ( int i=0;i<min_history_secs;i++){
						t_send[i] 			+= send_history[i];
						t_recv[i] 			+= recv_history[i];
						t_swarm_peer_av[i]	+= sp_history[i];
						
						t_eta[i]	= Math.max( t_eta[i], eta_history[i]);
					}
				}
				
				int[] t_smoothed_recv	= new int[min_history_secs];
				int[] t_smoothed_send	= new int[min_history_secs];
					
				SmoothAverage	send_average = GeneralUtils.getSmoothAverageForReplay();
				SmoothAverage	recv_average = GeneralUtils.getSmoothAverageForReplay();

				int smooth_interval = GeneralUtils.getSmoothUpdateInterval();

				int	current_smooth_send = 0;
				int	current_smooth_recv = 0;
				int	pending_smooth_send = 0;
				int	pending_smooth_recv = 0;

				for ( int i=0;i<min_history_secs;i++){
					pending_smooth_send += t_send[i];
					pending_smooth_recv += t_recv[i];

					if ( i % smooth_interval == 0 ){
						send_average.addValue( pending_smooth_send );
						current_smooth_send = (int)send_average.getAverage();
						recv_average.addValue( pending_smooth_recv );
						current_smooth_recv = (int)recv_average.getAverage();

						pending_smooth_send = 0;
						pending_smooth_recv = 0;
					}
					t_smoothed_send[i] = current_smooth_send;
					t_smoothed_recv[i] = current_smooth_recv;
				}
				
				int[][] mpg_history = { t_send, t_smoothed_send, t_recv, t_smoothed_recv, t_swarm_peer_av };

				
				
				mpg.reset( mpg_history );

				mpg.setActive( true );
					
				// ETA Stuff
				
				if ( eta != null ){
				
					int[] t_eta_average	= new int[min_history_secs];
				
					MovingImmediateAverage eta_average = AverageFactory.MovingImmediateAverage( ETA_AVERAGE_TICKS );
											
					for ( int i=0;i<min_history_secs;i++ ){
						
						eta_average.update( t_eta[i] );
						
						t_eta_average[i] = (int)eta_average.getAverage();
					}
					
					int[][] eta_history = { t_eta, t_eta_average };
					
					eta.reset( eta_history );
					
					eta.setActive( true );
				}
			}else{
				
				ViewUtils.setViewRequiresOneOrMoreDownloads( panel );
				
				if ( mpg != null ){
					
					mpg.setActive( false );

					mpg.reset( new int[5][0] );
				}
				
				if ( eta != null ){
				
					eta.setActive( false );
				
					eta.reset( new int[2][0] );
				}
			}
		});
	}

	@Override
	public void
	menuWillBeShown(
		MdiEntry 	entry, 
		Menu 		menu)
	{
		MenuItem mi = new MenuItem( menu, SWT.CHECK );
		
		mi.setSelection( show_time );
		
		mi.setText( MessageText.getString( "ColumnProgressETA.showETA" ));
		
		mi.addListener( SWT.Selection, (ev)->{
		
			COConfigurationManager.setParameter( "DownloadActivity.show.eta", !show_time );
		});
		
		new MenuItem( menu, SWT.SEPARATOR );
	}
	
	@Override
	public void 
	parameterChanged(
		String parameterName )
	{
	   	show_time = COConfigurationManager.getBooleanParameter( "DownloadActivity.show.eta" );
	
	   	rebuild();
	}

	private void
	create()
	{
    	swtView.setTitle(getFullTitle());

    	swtView.setToolBarListener(this);

    	COConfigurationManager.addParameterListener( "DownloadActivity.show.eta", this );
    	
    	show_time = COConfigurationManager.getBooleanParameter( "DownloadActivity.show.eta" );
    	
		if (swtView instanceof TabbedEntry) {
			
			TabbedEntry tabView = (TabbedEntry)swtView;
			
			tabView.addListener( this );
			
			legend_at_bottom = tabView.getMDI().getAllowSubViews();
		}
	}
	
	private void
	delete()
	{
		 Utils.disposeComposite( panel );

		 COConfigurationManager.removeParameterListener( "DownloadActivity.show.eta", this );
		 
		 if ( mpg != null ){

			 mpg.dispose();
			 
			 mpg = null;
		 }
		 
		 if ( eta != null ){
			 
			 eta.dispose();
			 
			 eta = null;
		 }
		 
		 if ( swtView instanceof TabbedEntry ){
				
			TabbedEntry tabView = (TabbedEntry)swtView;
			
			tabView.removeListener( this );
		 }
	}

	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
	    switch( event.getType()){
		    case UISWTViewEvent.TYPE_CREATE:{
		    	swtView = event.getView();

		    	create();

		    	break;
		    }
		    case UISWTViewEvent.TYPE_DESTROY:{

		    	delete();

		    	break;
		    }
		    case UISWTViewEvent.TYPE_INITIALIZE:{

		    	initialize((Composite)event.getData());

		    	break;
		    }
		    case UISWTViewEvent.TYPE_REFRESH:{

		    	refresh( false );

		        break;
		    }
		    case UISWTViewEvent.TYPE_LANGUAGEUPDATE:{
		    	Messages.updateLanguageForControl(getComposite());

		    	swtView.setTitle(getFullTitle());

		    	break;
		    }
		    case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:{

		    	dataSourceChanged(event.getData());

		    	break;
		    }
	    	case UISWTViewEvent.TYPE_SHOWN:{

	    		String id = "DMDetails_DownloadGraph";

	    		setVisible( true );	// do this here to pick up current manager before rest of code

	    		if ( managers != null ){

	    			List<SelectedContent>	sc = new ArrayList<>();
	    			
	    			for ( DownloadManager dm: managers ){
	    				
	    				sc.add( new SelectedContent( dm ));
	    				
	    				if ( dm.getTorrent() != null ){

	    					id += "." + dm.getInternalName();

	    				}else{

	    					id += ":" + dm.getSize();
	    				}
	    			}

	    			SelectedContentManager.changeCurrentlySelectedContent(id, sc.toArray( new SelectedContent[0] ));
	    					
	    		}else{
	    			
	    			SelectedContentManager.changeCurrentlySelectedContent(id, null);
	    		}

	    		refresh( true );

			    break;
	    	}
		    case UISWTViewEvent.TYPE_HIDDEN:{

		    	setVisible( false );

		    	SelectedContentManager.clearCurrentlySelectedContent();

		    	break;
		    }
	    }

	    return( true );
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		return false; // default handler will handle it
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		Map<String, Long> states = TorrentUtil.calculateToolbarStates(
				SelectedContentManager.getCurrentlySelectedContent(), null);
		list.putAll(states);
	}

	private abstract static class
	ValueSourceImpl
		implements ValueSource
	{
		private String			name;
		private int				index;
		private Color[]			colours;
		private int				base_style;
		private boolean			trimmable;

		private boolean			is_hover;
		private boolean			is_invisible;
		private boolean			is_dotted;

		private
		ValueSourceImpl(
			String					_name,
			int						_index,
			Color[]					_colours,
			boolean					_is_up,
			boolean					_trimmable,
			boolean					_is_dotted )
		{
			name			= _name;
			index			= _index;
			colours			= _colours;
			trimmable		= _trimmable;
			is_dotted		= _is_dotted;
			
			base_style = _is_up?STYLE_UP:STYLE_DOWN;
		}

		private
		ValueSourceImpl(
			String					_name,
			int						_index,
			Color[]					_colours,
			int						_base_style,
			boolean					_trimmable,
			boolean					_is_dotted )
		{
			name			= _name;
			index			= _index;
			colours			= _colours;
			trimmable		= _trimmable;
			is_dotted		= _is_dotted;
			
			base_style = _base_style;
		}
		
		@Override
		public String
		getName()
		{
			return( name );
		}

		@Override
		public Color
		getLineColor()
		{
			return( colours[index] );
		}

		@Override
		public boolean
		isTrimmable()
		{
			return( trimmable );
		}

		private void
		setHover(
			boolean	h )
		{
			is_hover = h;
		}

		private void
		setVisible(
			boolean	visible )
		{
			is_invisible = !visible;
		}

		@Override
		public int
		getStyle()
		{
			if ( is_invisible ){

				return( STYLE_INVISIBLE );
			}

			int style = base_style;

			if ( is_hover ){

				style |= STYLE_BOLD;
			}

			if ( is_dotted ){

				style |= STYLE_HIDE_LABEL;
			}

			return( style );
		}

		@Override
		public int
		getAlpha()
		{
			return( is_dotted?128:255 );
		}
	}
}
