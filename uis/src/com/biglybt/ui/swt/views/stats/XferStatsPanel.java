/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
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
package com.biglybt.ui.swt.views.stats;

import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.mainwindow.Colors;

import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.global.GlobalManagerStats.AggregateStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.FrequencyLimitedDispatcher;


import com.biglybt.ui.swt.utils.ColorCache;

public class
XferStatsPanel
{
	private static final int ALPHA_FOCUS = 255;
	private static final int ALPHA_NOFOCUS = 150;

	private Display display;

	private BufferedLabel	header_label;
	private Canvas 			canvas;
	
	private Scale scale	 = new Scale();

	private boolean	show_samples = COConfigurationManager.getBooleanParameter( "XferStats.show.samples" );
	
	
	private boolean mouseLeftDown = false;
	private boolean mouseRightDown = false;
	private int xDown;
	private int yDown;

	private Image img;

	private int alpha = 255;

	private boolean autoAlpha = false;

	private GlobalManagerStats		gm_stats;

	private long	latest_sequence	= Long.MAX_VALUE;
	
	private FrequencyLimitedDispatcher	refresh_dispatcher = 
		new FrequencyLimitedDispatcher(
			new AERunnable(){
				
				@Override
				public void runSupport(){
					Utils.execSWTThread(
						new Runnable(){
							
							@Override
							public void run(){
								refresh();
							}
						});
				}
			},
			250 );

	int flag_width;
	int flag_height;
	int	text_height;
	
	private List<Object[]>	currentPositions = new ArrayList<>();

	private Node			hover_node;
	private float			tp_ratio;
	
	static float def_minX = -1000;
	static float def_maxX = 1000;
	static float def_minY = -1000;
	static float def_maxY = 1000;
	static double def_rotation = 0;
	
	private static class Scale {
		int width;
		int height;
		float minX;
		float maxX;
		float minY;
		float maxY;
		double rotation;
		
		float saveMinX;
		float saveMaxX;
		float saveMinY;
		float saveMaxY;
		double saveRotation;

		{
			init();
		}
		
		private void
		init()
		{
			minX = def_minX;
			maxX = def_maxX;
			minY = def_minY;
			maxY = def_maxY;
			rotation = def_rotation;
			
			saveMinX	= 0;
			saveMaxX	= 0;
			saveMinY	= 0;
			saveMaxY	= 0;
			saveRotation	= 0;
		}
		
		public int getX(float x,float y) {
			return (int) (((x * Math.cos(rotation) + y * Math.sin(rotation))-minX)/(maxX - minX) * width);
		}

		public int getY(float x,float y) {
			return (int) (((y * Math.cos(rotation) - x * Math.sin(rotation))-minY)/(maxY-minY) * height);
		}
		
		public int[] getXY( float x, float y ){
			return( new int[]{getX(x,y), getY( x,y)});
		}
			
		public int getWidth( float w ){
			return( (int)(w/(maxX-minX)* width));
		}		
		
		public int getHeight( float w ){	
			return( (int)(w/(maxY-minY)* height));
		}
		
		public int getReverseWidth( float w ){
			return( (int)((w/width)* (maxX-minX)));
		}	
		
		public int getReverseHeight( float h ){
			return( (int)((h/height)* (maxY-minY)));
		}	
	}

	public 
	XferStatsPanel(
		Composite composite) 
	{
		display = composite.getDisplay();

		Color white = ColorCache.getColor(display,255,255,255);

		Composite panel = new Composite(composite,SWT.NULL);
	    GridLayout layout = new GridLayout();
	    layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 
	    		layout.marginHeight = layout.marginWidth = 0;
	    
	    layout.numColumns = 2;
	    panel.setLayout(layout);
	    panel.setBackground( white );
	    
	    	// header
	    
	    header_label = new BufferedLabel( panel, SWT.DOUBLE_BUFFERED );
	    GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );
	    grid_data.horizontalIndent = 5;
	    Utils.setLayoutData( header_label, grid_data);
	    header_label.getControl().setBackground( white );
	    
	    	// controls
	    
	    Composite controls = new Composite(panel,SWT.NULL);
	    layout = new GridLayout();
	    layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 
	    		layout.marginHeight = layout.marginWidth = 0;
	    layout.numColumns = 3;
	    
	    controls.setLayout(layout);
	    
	    Label label = new Label( controls, SWT.NULL );
	    label.setBackground( white );
	    Messages.setLanguageText( label, "ConfigView.section.display" );
	    
	    Button sample_button	= new Button( controls, SWT.RADIO );
	    sample_button.setBackground( white );
	    Messages.setLanguageText( sample_button, "label.samples" );
	    sample_button.setSelection( show_samples );
	    
	    
	    Button tp_button	= new Button( controls, SWT.RADIO );
	    tp_button.setBackground( white );
	    Messages.setLanguageText( tp_button, "label.throughput" );
	    tp_button.setSelection( !show_samples );

	    SelectionAdapter sa = 
	    	new SelectionAdapter(){
	    		@Override
	    		public void widgetSelected(SelectionEvent e){
	    			show_samples = sample_button.getSelection();
	    			COConfigurationManager.setParameter( "XferStats.show.samples", show_samples );
	    			requestRefresh();
	    		}
			};
	    
	    sample_button.addSelectionListener( sa );
	    tp_button.addSelectionListener( sa );
	    
	    	// canvas
	    
		canvas = new Canvas(panel,SWT.NO_BACKGROUND);
		grid_data = new GridData( GridData.FILL_BOTH );
		grid_data.horizontalSpan = 2;
		
		canvas.setLayoutData( grid_data );
		
		canvas.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				if (img != null && !img.isDisposed()) {
					Rectangle bounds = img.getBounds();
					if (bounds.width >= ( e.width + e.x ) && bounds.height >= ( e.height + e.y )) {
						if (alpha != 255) {
							try {
								e.gc.setAlpha(alpha);
							} catch (Exception ex) {
								// Ignore ERROR_NO_GRAPHICS_LIBRARY error or any others
							}
						}
						
						e.gc.drawImage(img, e.x, e.y, e.width, e.height, e.x, e.y,
								e.width, e.height);
					}
				} else {
					e.gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_WIDGET_BACKGROUND));
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);

					e.gc.drawText(
							MessageText.getString( "v3.MainWindow.view.wait"), 10,
							10, true);
				}
			}
		});

		canvas.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseDown(MouseEvent event) {
				if(event.button == 1) mouseLeftDown = true;
				if(event.button == 3) mouseRightDown = true;
				xDown = event.x;
				yDown = event.y;
				scale.saveMinX = scale.minX;
				scale.saveMaxX = scale.maxX;
				scale.saveMinY = scale.minY;
				scale.saveMaxY = scale.maxY;
				scale.saveRotation = scale.rotation;
			}

			@Override
			public void mouseUp(MouseEvent event) {
				if(event.button == 1) mouseLeftDown = false;
				if(event.button == 3) mouseRightDown = false;
				refresh();
			}
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				scale.init();
				refresh();
			}
		});

		canvas.addListener(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
			}
		});

		canvas.addListener(SWT.MouseWheel, new Listener() {
			@Override
			public void handleEvent(Event event) {
				// System.out.println(event.count);
				scale.saveMinX = scale.minX;
				scale.saveMaxX = scale.maxX;
				scale.saveMinY = scale.minY;
				scale.saveMaxY = scale.maxY;

				int deltaY = event.count * 5;
				// scaleFactor>1 means zoom in, this happens when
				// deltaY<0 which happens when the mouse is moved up.
				float scaleFactor = 1 - (float) deltaY / 300;
				if(scaleFactor <= 0) scaleFactor = 0.01f;

				// Scalefactor of e.g. 3 makes elements 3 times larger
				float moveFactor = 1 - 1/scaleFactor;

				float centerX = (scale.saveMinX + scale.saveMaxX)/2;
				scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
				scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

				float centerY = (scale.saveMinY + scale.saveMaxY)/2;
				scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
				scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
				refresh();
			}
		});

		canvas.addMouseMoveListener(new MouseMoveListener() {
			@Override
			public void mouseMove(MouseEvent event) {
				
				if(mouseLeftDown && (event.stateMask & SWT.MOD4) == 0) {
					int deltaX = event.x - xDown;
					int deltaY = event.y - yDown;
					float width = scale.width;
					float height = scale.height;
					float ratioX = (scale.saveMaxX - scale.saveMinX) / width;
					float ratioY = (scale.saveMaxY - scale.saveMinY) / height;
					float realDeltaX = deltaX * ratioX;
					float realDeltaY  = deltaY * ratioY;
					scale.minX = scale.saveMinX - realDeltaX;
					scale.maxX = scale.saveMaxX - realDeltaX;
					scale.minY = scale.saveMinY - realDeltaY;
					scale.maxY = scale.saveMaxY - realDeltaY;
					requestRefresh();
				}
				if(mouseRightDown || (mouseLeftDown && (event.stateMask & SWT.MOD4) > 0)) {
					int deltaX = event.x - xDown;
					scale.rotation = scale.saveRotation - (float) deltaX / 100;

					int deltaY = event.y - yDown;
					// scaleFactor>1 means zoom in, this happens when
					// deltaY<0 which happens when the mouse is moved up.
					float scaleFactor = 1 - (float) deltaY / 300;
					if(scaleFactor <= 0) scaleFactor = 0.01f;

					// Scalefactor of e.g. 3 makes elements 3 times larger
					float moveFactor = 1 - 1/scaleFactor;

					float centerX = (scale.saveMinX + scale.saveMaxX)/2;
					scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
					scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

					float centerY = (scale.saveMinY + scale.saveMaxY)/2;
					scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
					scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
					requestRefresh();
				}
			}
		});

		canvas.addMouseTrackListener(new MouseTrackListener() {
			@Override
			public void mouseHover(MouseEvent e) {
	    		int x = e.x;
	    		int	y = e.y;

	    		Node closest = null;

	    		int		closest_distance = Integer.MAX_VALUE;

	    		for ( Object[] entry: currentPositions ){

	       			int		e_x = (Integer)entry[0];
	     			int		e_y = (Integer)entry[1];

	       			long	x_diff = x - e_x;
	       			long	y_diff = y - e_y;

	       			int distance = (int)Math.sqrt( x_diff*x_diff + y_diff*y_diff );

	       			if ( distance < closest_distance ){

	       				closest_distance 	= distance;
	       				closest				= (Node)entry[2];
	       			}
	    		}

	    		if ( closest_distance <= 30 ){
	    			
	    			canvas.setToolTipText( closest.getToolTip());

	    			hover_node	= closest;
	    			
	    		}else{

	    			hover_node = null;
	    			
	    			canvas.setToolTipText( "" );
	    		}
	    		
	    		requestRefresh();
			}

			@Override
			public void mouseExit(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_NOFOCUS);
				}
			}

			@Override
			public void mouseEnter(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_FOCUS);
				}
			}
		});
		
		canvas.addListener(
			SWT.Resize,
			new Listener(){
				
				@Override
				public void handleEvent(Event event){
					requestRefresh();
				}
			});
	}

	public void setLayoutData(Object data) {
		canvas.setLayoutData(data);
	}

	protected void
	init(
		GlobalManagerStats		_stats )
	{
		gm_stats	= _stats;
	}
	
	private String
	getBPSForDisplay(
		long	bytes )
	{
		if ( show_samples ){
			
			bytes = bytes/60;
			
		}else{
			
			if ( tp_ratio == 0 ){
				
				return( "" );
			}
		
			bytes = (long)(( tp_ratio * bytes)/60);
		}
		
		return( DisplayFormatters.formatByteCountToKiBEtcPerSec( bytes ));
	}
	
	public void
	refreshView()
	{
		if ( gm_stats == null ){
			
			return;
		}
		
		AggregateStats		a_stats = gm_stats.getAggregateRemoteStats();
			
		if ( latest_sequence == Long.MAX_VALUE || latest_sequence != a_stats.getSequence()){
		
			refresh();
		}
	}

	public void
	requestRefresh()
	{
		refresh_dispatcher.dispatch();
	}
	
	public void
	refresh()
	{
		if ( canvas.isDisposed()){

			return;
		}

		Rectangle size = canvas.getBounds();

		if ( size.width <= 0 || size.height <= 0 ){

			return;
		}

		scale.width 	= size.width;
		scale.height 	= size.height;

		if (img != null && !img.isDisposed()){

			img.dispose();
		}

		img = new Image(display,size);

		GC gc = new GC(img);

		gc.setAdvanced( true );

		gc.setAntialias( SWT.ON );
		gc.setTextAntialias( SWT.ON );

		Color white = ColorCache.getColor(display,255,255,255);
		gc.setForeground(white);
		gc.setBackground(white);
		gc.fillRectangle(size);

		if ( gm_stats == null ){
			
			return;
		}
		
		gc.setForeground(Colors.black);

		flag_width	= scale.getReverseWidth( 25 );
		flag_height	= scale.getReverseHeight( 15 );
			
		Point text_extent = gc.textExtent( "1234567890" + DisplayFormatters.formatByteCountToKiBEtcPerSec( 1024));
		
		text_height = scale.getReverseHeight( text_extent.y );

		currentPositions.clear();

		AggregateStats		a_stats = gm_stats.getAggregateRemoteStats();
		
		latest_sequence = a_stats.getSequence();
		
		float samples 		= a_stats.getSamples();
		float population	= a_stats.getEstimatedPopulation();
		
		long received 	= a_stats.getLatestReceived();
		long sent		= a_stats.getLatestSent();
		
		String throughput;
		
		if ( samples > 0 && population > 0 ){
			
			tp_ratio = population/samples;
			
			throughput =  DisplayFormatters.formatByteCountToKiBEtcPerSec((long)(tp_ratio*(received+sent)/60));
			
		}else{
			
			tp_ratio = 0;
			
			throughput = "";
		}
		
		String header = MessageText.getString(
							"XferStatsView.header",
							new String[]{
								String.valueOf( (int)samples ),
								String.valueOf( (int)population ),
								throughput
							});
		
		header_label.setText( header );
						
		Map<String,Map<String,long[]>> stats = a_stats.getStats();
						
		List<Node> 			origins 	= new ArrayList<>();
		
		Map<String,Node>	dest_recv_map 	= new HashMap<>();
		Map<String,Node>	dest_sent_map 	= new HashMap<>();
		
		
		for ( Map.Entry<String,Map<String,long[]>> entry: stats.entrySet()){
					
			String from_cc = entry.getKey();
				
			if ( from_cc.isEmpty()){
				
				continue;
			}
			
			Node from_node 	= new Node();
			
			origins.add( from_node );
			
			Image from_image = ImageRepository.getCountryFlag( from_cc, false );

			from_node.cc		= from_cc;
			from_node.image		= from_image;
			from_node.links		= new ArrayList<>();
						
			for ( Map.Entry<String,long[]>	entry2: entry.getValue().entrySet()){
				
				String 	to_cc 		= entry2.getKey();
				
				if ( to_cc.isEmpty()){
					
					continue;
				}
				
				long[]	to_counts	= entry2.getValue();
				
				long	to_recv = to_counts[0];
				long	to_sent = to_counts[1];
				
				Image to_image = ImageRepository.getCountryFlag( to_cc, false );

				if ( to_recv > 0 ){
					
					Node	to_node = dest_recv_map.get( to_cc );
					
					if ( to_node == null ){
						
						to_node = new Node();
						
						dest_recv_map.put( to_cc,  to_node );
							
						to_node.cc		= to_cc;
						to_node.image	= to_image;
					}
					
					from_node.count_recv += to_recv;
					to_node.count_recv += to_recv;
					
					Link link = new Link();
					
					link.count += to_recv;
					link.source	= from_node;
					link.target	= to_node;
					
					from_node.links.add( link );
				}
				
				if ( to_sent > 0 ){
					
					Node	to_node = dest_sent_map.get( to_cc );
					
					if ( to_node == null ){
						
						to_node = new Node();
						
						dest_sent_map.put( to_cc,  to_node );
							
						to_node.cc		= to_cc;
						to_node.image	= to_image;
					}
					
					from_node.count_sent += to_sent;
					to_node.count_sent += to_sent;
					
					Link link = new Link();
					
					link.count += to_sent;
					link.source	= from_node;
					link.target	= to_node;
					
					from_node.links.add( link );
				}
			}
		}
				
		List<Node>	dests_recv = new ArrayList<>( dest_recv_map.values());
		List<Node>	dests_sent = new ArrayList<>( dest_sent_map.values());
		
		float	lhs = scale.minX;
		float	rhs = scale.maxX - flag_width -10;
			
		for ( int i=0;i<3;i++){
			
			int flag_x 	= (int)( -1000 + scale.getReverseHeight( 5 ));
			
			int 		flag_y;
			List<Node>	nodes;
			boolean		odd;
			
			if ( i == 0 ){
				
				nodes 	= dests_recv;
				flag_y	= (int)( -1000 + text_height );
				odd		= false;
				
			}else if ( i == 1 ){
								
				nodes 	= dests_sent;
				flag_y	= (int)( 1000 - flag_height - text_height - scale.getReverseHeight( 5 ));
				odd		= true;
				
			}else{
				
				nodes 	= origins;
				flag_y	= -flag_height/2;
				odd		= true;
			}
	
			Collections.sort(
				nodes,
				new Comparator<Node>()
				{
					@Override
					public int compare(Node o1, Node o2){
						return( Long.compare(o2.count_recv+o2.count_sent, o1.count_recv+o1.count_sent ));
					}
				});

			int		max_flags = (int)(( rhs - lhs ) / ( 2 * flag_width ));
			
			int	pad;
			
			if ( nodes.size() >= max_flags ){
				
				pad = 0;
				
			}else{
			
				pad = (int)((( rhs - lhs) - nodes.size() * 2 * flag_width ) / 2 );
			}
			
			for ( Node node: nodes ){
				
				node.x_pos	= flag_x + pad;
				node.y_pos	= flag_y;
							
				if ( 	node.x_pos > rhs || 
						node.x_pos < lhs ){
					
					node.hidden = true;
					
					flag_x += flag_width;
									
				}else{
													
					flag_x += flag_width * 2;
				}
			}
	
			boolean draw_links = nodes == origins;
			
			for ( Node node: nodes ){
	
				if ( !node.hidden ){
								
					node.draw( gc, odd  );
					
					odd = !odd;
					
					if ( draw_links ){
												
						for ( Link link: node.links ){
							
							link.draw(gc);
						}
					}
				}
			}
		}
		
		gc.dispose();

		canvas.redraw();
	}



	public int getAlpha() {
		return alpha;
	}

	public void setAlpha(int alpha) {
		this.alpha = alpha;
		if (canvas != null && !canvas.isDisposed()) {
			canvas.redraw();
		}
	}

	public void setAutoAlpha(boolean autoAlpha) {
		this.autoAlpha = autoAlpha;
		if (autoAlpha) {
			setAlpha(canvas.getDisplay().getCursorControl() == canvas ? ALPHA_FOCUS : ALPHA_NOFOCUS);
		}
	}

	public void delete()
	{
		if(img != null && !img.isDisposed())
		{
			img.dispose();
		}
	}
	
	private class
	Link
	{
		Node	source;
		Node	target;
		long	count;
		
		private void
		draw(
			GC		gc )
		{
			if ( source.hidden || target.hidden ){
				
				return;
			}
			
			boolean above = source.y_pos > target.y_pos;
			
			int x1 = source.x_pos + flag_width/2;
			int x2 = target.x_pos + flag_width/2;

			int y1;
			int y2;
			
			if ( above ){
				y1 = source.y_pos - text_height;		
				y2 = target.y_pos + flag_height + text_height;
			}else{
				y1 = source.y_pos + flag_height + text_height;			
				y2 = target.y_pos - text_height;	
			}
			
			int[] xy1 = scale.getXY( x1, y1 );
			int[] xy2 = scale.getXY( x2, y2 );
			
			Color old = gc.getForeground();
			
			if ( hover_node != null && (source.cc.equals( hover_node.cc ) || target.cc.equals( hover_node.cc ))){
				
				gc.setForeground( Colors.fadedGreen );
				
				gc.setLineWidth( 2 );
				
			}else{
			
				long	per_sec = count/60;
				
				if ( per_sec > 10*1024*1024 ){
					
					gc.setForeground( Colors.blue );
					
				}else{
					int	blues_index ;
					
					if ( per_sec > 1024*1024 ){
						blues_index = Colors.BLUES_DARKEST;
					}else if ( per_sec > 100*1024 ){
						blues_index = Colors.BLUES_MIDDARK;
					}else if ( per_sec > 10*1024 ){
						blues_index = 5;
					}else{
						blues_index = 3;
					}				
				
					gc.setForeground( Colors.blues[ blues_index ]);
				}
				
				gc.setLineWidth( 1 );
			}
			
			gc.drawLine(xy1[0],xy1[1],xy2[0],xy2[1] );
			
			gc.setForeground( old );
		}
	}
	
	private class
	Node
	{
		String			cc;
		Image			image;
		long			count_sent;
		long			count_recv;
		
		List<Link>		links;
		
		int			x_pos;
		int			y_pos;
		boolean		hidden;
		
		private void
		draw(
			GC			gc,
			boolean		odd )
		{
			String speed = getBPSForDisplay( count_recv+count_sent );
			
			String nums = speed;
			
			int		pos = nums.indexOf( " " );
			
			if ( pos != -1 ){
				
				nums = nums.substring( 0, pos );
			}
			
			int speed_width = gc.textExtent( nums ).x;
			
			int speed_pad = ( flag_width - scale.getReverseWidth( speed_width ))/2;
			
			int[] xy = scale.getXY( speed_pad + x_pos, odd?(y_pos+flag_height):(y_pos-text_height));
			
			// remember stats are in bytes per min
		
			gc.drawText( speed, xy[0], xy[1] );
		
			int	width;
			
			//image = null;
			
			if ( image == null ){
				
				width = gc.textExtent( cc ).x;
				
			}else{
			
				width = image.getBounds().width;
			}
			
			int flag_pad = ( flag_width - scale.getReverseWidth( width ))/2;
			
			xy = scale.getXY( flag_pad + x_pos, y_pos );
			
			if ( image == null ){
				
				gc.drawText( cc, xy[0], xy[1] );

			}else{
				
				gc.drawImage( image, xy[0], xy[1] );
			}

			currentPositions.add( new Object[]{ xy[0], xy[1], this });
			

				
		}
		
		private String
		getToolTip()
		{
			String tt = cc;
			
			if ( count_recv > 0 ){
				
				tt += "; " + MessageText.getString( "label.download") + "=" + getBPSForDisplay( count_recv );
			}
			
			if ( count_sent > 0 ){
				
				tt += "; " + MessageText.getString( "label.upload") + "=" + getBPSForDisplay( count_sent );
			}

			
			return( tt );
		}
	}
}
