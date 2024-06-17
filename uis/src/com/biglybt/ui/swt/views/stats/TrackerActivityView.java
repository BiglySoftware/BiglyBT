/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.stats;


import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AnnounceStats;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.components.graphics.MultiPlotGraphic;
import com.biglybt.ui.swt.components.graphics.ValueFormater;
import com.biglybt.ui.swt.components.graphics.ValueSource;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;



public class TrackerActivityView
	implements UISWTViewCoreEventListener
{

	public static final String MSGID_PREFIX = "TrackerActivityView";

	private static MultiPlotGraphic[] mpg_histories = new MultiPlotGraphic[3];
	
	private static Color[]	mpg_rates_colors = { 
			Colors.blues[Colors.BLUES_DARKEST],
			Colors.fadedGreen };
	
	private static Color[]	mpg_lags_colors =  { 
			Colors.blues[Colors.BLUES_DARKEST],
			Colors.maroon,
			Colors.fadedGreen };
	
	private static Color[]	mpg_sched_colors = { 
			Colors.blues[Colors.BLUES_DARKEST],
			Colors.maroon,
			Colors.fadedGreen,
			Colors.dark_grey };
	
	private Composite panel;
		
	private MultiPlotGraphic[] mpgs = new MultiPlotGraphic[3];

	private AllTrackers	all_trackers = AllTrackersManager.getAllTrackers();

	public 
	TrackerActivityView() 
	{
	}


	private void 
	initialize(
		Composite composite ) 
	{
		panel = new Composite(composite,SWT.NULL);
		panel.setLayout(new GridLayout(2, true));
		GridData gridData;

		{
			Group gRates = Utils.createSkinnedGroup(panel,SWT.NULL);
			Messages.setLanguageText(gRates,"TrackerActivityView.rates");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			gRates.setLayoutData(gridData);
			gRates.setLayout(new GridLayout());
	
			ValueFormater formatter =
					new ValueFormater()
			{
				@Override
				public String
				format(
						int value)
				{
					return( (float)value/1000 + "/"+ TimeFormatter.getShortSuffix( TimeFormatter.TS_SECOND ));
				}
			};
	
	
			final ValueSourceImpl[] sources = {
					new ValueSourceImpl( "Announce", 0, mpg_rates_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							return((int)( all_trackers.getAnnouncesPerSecond()*1000));
						}
					},
					new ValueSourceImpl( "Scrape", 1, mpg_rates_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							return((int)( all_trackers.getScrapesPerSecond()*1000));
						}
					}
			};
	
			MultiPlotGraphic mpg_rates = mpgs[0] = MultiPlotGraphic.getInstance( sources, formatter );
	
	
			String[] color_configs = new String[] {
					"TrackerActivityView.legend.rate.announce",
					"TrackerActivityView.legend.rate.scrape",
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
	
					mpg_rates.refresh( true );
				}
	
				@Override
				public void
				visibilityChange(
						boolean	visible,
						int		index )
				{
					sources[index].setVisible( visible );
	
					mpg_rates.refresh( true );
				}
			};	
	
			Canvas ratesCanvas = new Canvas(gRates,SWT.NO_BACKGROUND);
			gridData = new GridData(GridData.FILL_BOTH);
			ratesCanvas.setLayoutData(gridData);
	
			gridData = new GridData(GridData.FILL_HORIZONTAL);
	
			Legend.createLegendComposite(gRates, mpg_rates_colors, color_configs, null, gridData, true, legend_listener );
	
			mpg_rates.initialize( ratesCanvas, false );
	
			MultiPlotGraphic history = mpg_histories[0];
			
			if ( history != null ){
				
				mpg_rates.reset( history.getHistory());
			}
			
			mpg_rates.setActive( true );
		}
		
		{
			Group gLag = Utils.createSkinnedGroup(panel,SWT.NULL);
			Messages.setLanguageText(gLag,"TrackerActivityView.lags");
			gridData = new GridData(GridData.FILL_BOTH);
			gLag.setLayoutData(gridData);
			gLag.setLayout(new GridLayout());
	
			Canvas lagsCanvas = new Canvas(gLag,SWT.NO_BACKGROUND);
			gridData = new GridData(GridData.FILL_BOTH);
			lagsCanvas.setLayoutData(gridData);

			ValueFormater formatter =
					new ValueFormater()
			{
				@Override
				public String
				format(
					int value)
				{
					String str;
					
					if ( value < 10000 ){
						
						str = DisplayFormatters.formatDecimal((double)value/1000, 3, true, true );
						
					}else{
						
						str = String.valueOf( value/1000 );
					}
					
					return( str + " " + TimeFormatter.getShortSuffix( TimeFormatter.TS_SECOND) );
				}
			};
	
	
			final ValueSourceImpl[] sources = {
					new ValueSourceImpl( "Announce Public", 0, mpg_lags_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							AnnounceStats stats = all_trackers.getAnnounceStats();
							
							int result = (int)stats.getPublicLagMillis();
							
							Utils.execSWTThread(()->{
								
								if ( lagsCanvas.isDisposed()){
									
									return;
								}
							
								List<String> active = stats.getPublicActive();
								
								String tt = "";
								
								if ( !active.isEmpty()){
									
									tt += MessageText.getString( "subs.prop.is_public" ) + " (" + active.size() + ")\n";
								
									int lines = 0;
									
									for ( String s: active ){
									
										if ( lines == 5 ){
											
											tt += "    ...\n";
											
											break;
										}
										
										tt += "    " + s + "\n";
										
										lines++;
									}
								}
								active = stats.getPrivateActive();
								
								if ( !active.isEmpty()){
									
									if ( !tt.isEmpty()){
										
										tt += "\n";
									}
									
									tt += MessageText.getString( "label.private" ) + " (" + active.size() + ")\n";
								
									int lines = 0;
									
									for ( String s: active ){
									
										if ( lines == 5 ){
											
											tt += "    ...\n";
											
											break;
										}
										
										tt += "    " + s + "\n";
										
										lines++;
									}
								}
								
								Utils.setTT( lagsCanvas, tt );
							});
							
							if ( result < 500 ){
				
								result = 0;
							}
							
							return( result );
						}
					},
					new ValueSourceImpl( "Announce Private", 1, mpg_lags_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							int result = (int)all_trackers.getAnnounceStats().getPrivateLagMillis();
							
							if ( result < 500 ){
				
								result = 0;
							}
							
							return( result );

						}
					},
					new ValueSourceImpl( "Scrape", 2, mpg_lags_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							int result = (int)all_trackers.getScrapeStats().getLagMillis();
							
							if ( result < 500 ){
				
								result = 0;
							}
							
							return( result );

						}
					}
			};
	
			MultiPlotGraphic mpg_lags = mpgs[1] = MultiPlotGraphic.getInstance( sources, formatter );
	
	
			String[] color_configs = new String[] {
					"TrackerActivityView.legend.lag.announce.pub",
					"TrackerActivityView.legend.lag.announce.priv",
					"TrackerActivityView.legend.lag.scrape",
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
	
					mpg_lags.refresh( true );
				}
	
				@Override
				public void
				visibilityChange(
						boolean	visible,
						int		index )
				{
					sources[index].setVisible( visible );
	
					mpg_lags.refresh( true );
				}
			};	
		
			gridData = new GridData(GridData.FILL_HORIZONTAL);
	
			Legend.createLegendComposite(gLag, mpg_lags_colors, color_configs, null, gridData, true, legend_listener );
	
			mpg_lags.initialize( lagsCanvas, false );
	
			MultiPlotGraphic history = mpg_histories[1];
			
			if ( history != null ){
				
				mpg_lags.reset( history.getHistory());
			}
			
			mpg_lags.setActive( true );
		}
		
		{
			Group gSched = Utils.createSkinnedGroup(panel,SWT.NULL);
			Messages.setLanguageText(gSched,"TrackerActivityView.scheduled");
			gridData = new GridData(GridData.FILL_BOTH);
			gSched.setLayoutData(gridData);
			gSched.setLayout(new GridLayout());
	
			ValueFormater formatter =
					new ValueFormater()
			{
				@Override
				public String
				format(
					int value)
				{
					return( String.valueOf( value ));
				}
			};
	
	
			final ValueSourceImpl[] sources = {
					new ValueSourceImpl( "Sched Public", 0, mpg_sched_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							return((int)all_trackers.getAnnounceStats().getPublicScheduledCount());
						}
					},
					new ValueSourceImpl( "Sched Private", 1, mpg_sched_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							return((int)all_trackers.getAnnounceStats().getPrivateScheduledCount());
						}
					},
					new ValueSourceImpl( "Sched Active", 2, mpg_sched_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							return((int)all_trackers.getActiveRequestCount());
						}
					},
					new ValueSourceImpl( "Sched Pending", 3, mpg_sched_colors, ValueSource.STYLE_NONE, false, false )
					{
						@Override
						public int
						getValue()
						{
							AnnounceStats stats = all_trackers.getAnnounceStats();
							
							return( stats.getPrivatePendingCount() + stats.getPublicPendingCount());
						}
					},
			};
	
			MultiPlotGraphic mpg_sched = mpgs[2] = MultiPlotGraphic.getInstance( sources, formatter );
	
	
			String[] color_configs = new String[] {
					"TrackerActivityView.legend.sched.announce.pub",
					"TrackerActivityView.legend.sched.announce.priv",
					"TrackerActivityView.legend.sched.announce.active",
					"TrackerActivityView.legend.sched.announce.pending",
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
	
					mpg_sched.refresh( true );
				}
	
				@Override
				public void
				visibilityChange(
						boolean	visible,
						int		index )
				{
					sources[index].setVisible( visible );
	
					mpg_sched.refresh( true );
				}
			};	
	
			Canvas lagsCanvas = new Canvas(gSched,SWT.NO_BACKGROUND);
			gridData = new GridData(GridData.FILL_BOTH);
			lagsCanvas.setLayoutData(gridData);
	
			gridData = new GridData(GridData.FILL_HORIZONTAL);
	
			Legend.createLegendComposite(gSched, mpg_sched_colors, color_configs, null, gridData, true, legend_listener );
	
			mpg_sched.initialize( lagsCanvas, false );
	
			MultiPlotGraphic history = mpg_histories[2];
			
			if ( history != null ){
				
				mpg_sched.reset( history.getHistory());
			}
			
			mpg_sched.setActive( true );
		}
	}

	private void delete()
	{	
		synchronized( TrackerActivityView.class ){
			
			for ( int i=0; i<mpg_histories.length; i++ ){
						
				if ( mpg_histories[i] == null ){
				
					MultiPlotGraphic mpg = mpgs[i];
					
					if ( mpg != null ){
					
						mpg_histories[i] = mpg;
						
						mpg.dispose( true );
						
						mpgs[i] = null;
						
						final int f_i = i;
						
						Utils.execSWTThread(()->{
							Shell shell = Utils.findAnyShell( true );
							
							if ( shell != null && !shell.isDisposed()){
								
								shell.addListener( SWT.Dispose, (ev)->{
									
									synchronized( TrackerActivityView.class ){
										
										if ( mpg_histories[f_i] != null ){
											
											mpg_histories[f_i].dispose();
										
											mpg_histories[f_i] = null;
										}
									}
								});
							}
						});
					}
				}
			}
		}

		for ( int i=0; i<mpgs.length; i++ ){
			
			if ( mpgs[i] != null ){
			
				mpgs[i].dispose();
				
				mpgs[i] = null;
			}
		}
	}

	private Composite 
	getComposite() 
	{
		return panel;
	}

	private void 
	refresh( boolean force) 
	{
		for ( int i=0; i<mpgs.length; i++ ){
			
			if ( mpgs[i] != null ){
				
				mpgs[i].refresh(force);
			}
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
		case UISWTViewEvent.TYPE_CREATE:
			UISWTView swtView = event.getView();
			swtView.setTitle(MessageText.getString(MSGID_PREFIX + ".title.full"));
			break;

		case UISWTViewEvent.TYPE_DESTROY:
			delete();
			break;

		case UISWTViewEvent.TYPE_INITIALIZE:
			initialize((Composite)event.getData());
			break;

		case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
			Messages.updateLanguageForControl(getComposite());
			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			break;

		case UISWTViewEvent.TYPE_SHOWN:
			refresh(true);
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			refresh(false);
			break;
		}

		return true;
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
