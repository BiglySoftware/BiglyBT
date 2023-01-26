/*
 * Created on 13-Sep-2005
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

package com.biglybt.ui.swt.components;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.tableitems.pieces.BlocksItem;

/**
 *
 * TODO: have a callback when color changes
 */
public class Legend {
	/**
	 * Create a legend containing a modifyable color box and description
	 *
	 * @param panel Where to add legend to
	 * @param blockColors array of colors for each legend entry.  This
	 *                     array WILL BE modified if the user changes the color
	 * @param keys array of keys for each legend entry
	 * @return The composite containing the legend
	 */
	public static Composite
	createLegendComposite(
		Composite panel,
		Color[] blockColors,
		String[] keys )
	{
		return( createLegendComposite( panel, blockColors, keys, true ));
	}

	public static Composite
	createLegendComposite(
		Composite panel,
		Color[] blockColors,
		String[] keys,
		boolean horizontal )
	{
		Object layout = panel.getLayout();
		Object layoutData = null;
		if (layout instanceof GridLayout)
			layoutData = new GridData(GridData.FILL_HORIZONTAL);

		return createLegendComposite(panel, blockColors, keys, null, layoutData, horizontal );
	}


	public static Composite
	createLegendComposite(
		final Composite panel,
		final Color[] blockColors,
		final String[] keys,
		Object layoutData)
	{
		return( createLegendComposite( panel, blockColors, keys, null, layoutData, true ));
	}
	/**
	 * Create a legend containing a modifyable color box and description
	 *
	 * @param panel Where to add legend to
	 * @param blockColors array of colors for each legend entry.  This
	 *                     array WILL BE modified if the user changes the color
	 * @param keys array of keys for each legend entry
	 * @param layoutData How to layout the legend (ie. GridData, LayoutData, etc)
	 * @return The composite containing the legend
	 */
	public static Composite
	createLegendComposite(
		final Composite panel,
		final Color[] blockColors,
		final String[] keys,
		final String[] key_texts,
		Object layoutData,
		boolean horizontal)
	{
		return( createLegendComposite( panel, blockColors, keys, key_texts, layoutData, horizontal, null ));
	}

	public static Color
	getLegendColor(
		String		key,
		String[]	keys,
		Color[]		defs )
	{
		Color	def = null;
		
		for ( int i=0;i<keys.length;i++){
			if ( keys[i].equals( key )){
				def = BlocksItem.colors[i];
			}
		}
		
		return( getLegendColor( key, def ));
	}
	
	private static Color
	getLegendColor(
		String		key,
		Color		def )
	{
		ConfigurationManager config = ConfigurationManager.getInstance();
		
		int r = config.getIntParameter(key + ".red", -1);
		if (r >= 0) {
			int g = config.getIntParameter(key + ".green");
			int b = config.getIntParameter(key + ".blue");

			return( ColorCache.getColor( Utils.getDisplay(), r, g, b));
		}
		return( def );
	}
	
	public static Composite
	createLegendComposite(
		final Composite panel,
		final Color[] blockColors,
		final String[] keys,
		final String[] key_texts,
		Object layoutData,
		boolean horizontal,
		final LegendListener	listener )
	{
		final ConfigurationManager config = ConfigurationManager.getInstance();

		if (blockColors.length != keys.length)
			return null;

		final Color[] defaultColors = new Color[blockColors.length];
		final ParameterListener[] paramListeners = new ParameterListener[keys.length];
		System.arraycopy(blockColors, 0, defaultColors, 0, blockColors.length);

		Composite legend = new Composite(panel, SWT.NONE);
		if (layoutData != null)
			legend.setLayoutData(layoutData);

		RowLayout layout = new RowLayout(horizontal?SWT.HORIZONTAL:SWT.VERTICAL);
		layout.wrap = true;
		layout.marginBottom = 0;
		layout.marginTop = 0;
		layout.marginLeft = 0;
		layout.marginRight = 0;
		layout.marginHeight = 0;
		layout.spacing = 0;
		legend.setLayout(layout);

		RowData data;

		final int[] hover_state = { -1, 0 };

		for (int i = 0; i < blockColors.length; i++) {
			int r = config.getIntParameter(keys[i] + ".red", -1);
			if (r >= 0) {
				int g = config.getIntParameter(keys[i] + ".green");
				int b = config.getIntParameter(keys[i] + ".blue");

				Color color = ColorCache.getColor(panel.getDisplay(), r, g, b);
				blockColors[i] = color;
			}

			Composite colorSet = new Composite(legend, SWT.NONE);

			colorSet.setLayout(new RowLayout(SWT.HORIZONTAL));

			final Canvas cColor = new Canvas(colorSet, SWT.BORDER);
			cColor.setData("Index", new Integer(i));

			Messages.setLanguageTooltip( cColor, "label.click.to.change.tooltip" );

			// XXX Use paint instead of setBackgrond, because OSX does translucent
			// crap
			cColor.addPaintListener(new PaintListener() {
				@Override
				public void paintControl(PaintEvent e) {
					int i = ((Integer)cColor.getData("Index")).intValue();
					e.gc.setBackground(blockColors[i]);
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);
				}
			});

			cColor.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					Integer iIndex = (Integer)cColor.getData("Index");
					if (iIndex == null)
						return;
					int index = iIndex.intValue();

					if (e.button == 1) {

						RGB rgb = Utils.showColorDialog( panel, blockColors[index].getRGB());

						if ( rgb != null ){

							config.setRGBParameter(keys[index], rgb.red, rgb.green, rgb.blue, true);
						}
					}else{

						config.removeRGBParameter(keys[index]);
					}
				}
			});

			final Label lblDesc = new Label(colorSet, SWT.NULL);

			if ( key_texts == null ){
				Messages.setLanguageText(lblDesc, keys[i]);
			}else{
				lblDesc.setText( key_texts[i] );
			}

			if ( listener != null ){

				Messages.setLanguageTooltip( lblDesc, "label.click.to.showhide.tooltip" );

				final int f_i = i;

				lblDesc.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseUp(MouseEvent e) {
						boolean vis = !config.getBooleanParameter(keys[f_i] + ".vis", true );
						config.setParameter(keys[f_i] + ".vis", vis );
						listener.visibilityChange( vis, f_i );
						Utils.setSkinnedForeground(lblDesc,(vis?null:(Utils.isDarkAppearanceNative()?Colors.dark_grey:Colors.grey )));
					}
				});

				boolean vis = config.getBooleanParameter(keys[f_i] + ".vis", true );
				if ( !vis ){
					listener.visibilityChange( vis, i );
					lblDesc.setForeground( Utils.isDarkAppearanceNative()?Colors.dark_grey:Colors.grey );
				}
			}

			data = new RowData();
			data.width = 12;
			data.height = lblDesc.computeSize(SWT.DEFAULT, SWT.DEFAULT).y - 3;
			cColor.setLayoutData(data);

			// If color changes, update our legend
			config.addParameterListener(keys[i],paramListeners[i] = new ParameterListener() {
				@Override
				public void parameterChanged(String parameterName) {
					for (int j = 0; j < keys.length; j++) {
						if (keys[j].equals(parameterName)) {
							final int index = j;

							final int r = config.getIntParameter(keys[j] + ".red", -1);
							if (r >= 0) {
								final int g = config.getIntParameter(keys[j] + ".green");
								final int b = config.getIntParameter(keys[j] + ".blue");

								Utils.execSWTThread(new AERunnable() {
									@Override
									public void runSupport() {
										if (panel == null || panel.isDisposed())
											return;
										Color color = ColorCache.getColor(panel.getDisplay(), r, g, b);
										blockColors[index] = color;
										cColor.redraw();
									}
								});

							} else {

								Utils.execSWTThread(new AERunnable() {
									@Override
									public void runSupport() {
										if (panel == null || panel.isDisposed())
											return;
										blockColors[index] = defaultColors[index];
										cColor.redraw();
									}
								});
							}
						}
					}
				}
			});

			if ( listener != null ){

				final int f_i = i;

				Control[] controls = { colorSet, cColor, lblDesc };

				MouseTrackListener ml =
					new MouseTrackListener()
					{
						@Override
						public void
						mouseEnter(
							MouseEvent e )
						{
							handleHover( listener, true, f_i, hover_state );
						}

						@Override
						public void
						mouseExit(
							MouseEvent e )
						{
							handleHover( listener, false, f_i, hover_state );
						}

						@Override
						public void
						mouseHover(
							MouseEvent e )
						{
						}
					};

				for ( Control c: controls ){

					c.addMouseTrackListener( ml );
				}
			}
		}

		legend.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				// We don't want to give them disposed colors
				// Restore defaults in case blockColors is a static or is used
				// afterwards, or if the view wants to dispose of the old colors.
				// PARG update: colors are used by the speed graphic whether legend is visible or not
				// so we don't want to revert to defaults. Also colours come from colorcache so aren't
				// disposed AFAIK
				//System.arraycopy(defaultColors, 0, blockColors, 0, blockColors.length);
				for (int i = 0; i < keys.length;i++){
					config.removeParameterListener(keys[i], paramListeners[i]);
				}
			}
		});

		List<Control> legControls = Utils.getAllChildren( legend );
		
		legControls.add( legend );
				
		String keyVis = keys == null || keys.length == 0?null:keys[0] + ".legend.hidden";
		
		Listener visListener = (Listener)(event)->{
			
			if ( keyVis != null ){			
				COConfigurationManager.setParameter( keyVis, false );
			}
			
			legend.dispose();
			panel.layout( true, true );
					
			Composite kidsPanel;;
			
			List<Control> kids = Utils.getAllChildren( panel );
			
			if ( kids.size() == 0 ){
				
				kidsPanel = panel.getParent();
				
				kids = Utils.getAllChildren( kidsPanel );
				
			}else{
				
				kidsPanel = panel;
			}
			
			List<Menu> 		showListenersAdded 	= new ArrayList<>();
			List<MenuItem>	menuItemsAdded		= new ArrayList<>();
			
			Listener showListener = new Listener(){
				public void
				handleEvent( Event ev )
				{
					Menu menu = (Menu)ev.widget;
										
					MenuItem[] items = menu.getItems();
					
					for ( MenuItem x: items ){
						if ( x.getData("Legend.hacked") != null ){
							return;
						}
					}
					if ( menu.getItemCount() > 0 ){
						menuItemsAdded.add( new MenuItem( menu, SWT.SEPARATOR ));
					}
					
					MenuItem kmi = new MenuItem( menu, SWT.PUSH );
					kmi.setData( "Legend.hacked", "" );
					menuItemsAdded.add( kmi );
					Messages.setLanguageText(kmi,"menu.show.legend");
					kmi.addListener(SWT.Selection, (event)->{
						
						for ( Menu x: showListenersAdded ){
							x.removeListener( SWT.Show, this );
						}
						for ( MenuItem x: menuItemsAdded ){
							x.dispose();
						}
						
						if ( keyVis != null ){			
							COConfigurationManager.setParameter( keyVis, true );
						}
						
						createLegendComposite( panel, blockColors, keys, key_texts, layoutData, horizontal, listener );
	
						kidsPanel.layout( true, true );
					});
				}
			};
				
			for ( Control kid: kids ){
				
				Menu km = kid.getMenu();
				
				if ( km == null ){
					
					km = new Menu( kid );
					
					kid.setMenu( km );
				}
								
				km.addListener( SWT.Show, showListener );
					
				showListenersAdded.add( km );
			}
		};

		for ( Control c: legControls ){
			Menu menu = new Menu( c );
			c.setMenu( menu );
			MenuItem mi = new MenuItem( menu, SWT.PUSH );
			Messages.setLanguageText(mi,"menu.hide.legend");
	
			mi.addListener( SWT.Selection, visListener );
		}
		
		if ( keyVis != null && !COConfigurationManager.getBooleanParameter( keyVis, true )){	
			visListener.handleEvent( null );
		}
		
		return legend;
	}

	private static void
	handleHover(
		final LegendListener	listener,
		boolean					entry,
		int						index,
		final int[]				state )
	{
		if ( entry ){

			state[1]++;

			if ( state[0] != index ){

				state[0] = index;

				listener.hoverChange( true, index );
			}
		}else{

			if ( state[0] == -1 ){

				return;
			}

			final int timer_index = ++state[1];

			Utils.execSWTThreadLater(
				100,
				new Runnable()
				{
					@Override
					public void
					run()
					{
						int	leaving = state[0];

						if ( timer_index != state[1] || leaving == -1 ){

							return;
						}

						state[0] = -1;

						listener.hoverChange( false, leaving );
					}
				});
		}
	}
	public interface
	LegendListener
	{
		public void
		hoverChange(
			boolean	entry,
			int		index );

		public void
		visibilityChange(
			boolean	visible,
			int		index );
	}
}
