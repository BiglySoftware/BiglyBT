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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.*;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.utils.ColorCache;

public class
GeneralOpsPanel
	extends BasePanel
{
	private static final int ALPHA_FOCUS = 255;
	private static final int ALPHA_NOFOCUS = 150;

	private static final int	FADE_OUT	= 10*1000;

	private static Map<String,Scale>	scale_map = new HashMap<>();
	
	Display display;
	Composite parent;

	Canvas canvas;
	Scale scale;

	private int		min_slots	= 8;

	private boolean	unavailable;

	private String		id;
	private Image 		img;

	private int alpha = 255;

	private boolean autoAlpha = false;

	private ActivityFilter	filter;

	private Map<Activity,ActivityDetail>	activity_map = new IdentityHashMap<>();

	private TimerEventPeriodic timeout_timer;

	private Map<Node,Rectangle>	node_text_map = new HashMap<>();

	public GeneralOpsPanel(Composite parent) {
		this.parent = parent;
		this.display = parent.getDisplay();
		this.canvas = new Canvas(parent,SWT.NO_BACKGROUND);

		this.scale = new Scale();

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
							MessageText.getString( unavailable?(DHTOpsView.MSGID_PREFIX + ".notAvailable"):"v3.MainWindow.view.wait"), 10,
							10, true);
				}
			}
		});

		canvas.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseDown(MouseEvent event) {
				scale.mouseDown( event );
			}

			@Override
			public void mouseUp(MouseEvent event) {
				
				if ( scale.mouseLeftDown ){
					int	x = event.x;
					int y = event.y;
					
					for ( Map.Entry<Node,Rectangle> entry: node_text_map.entrySet()){
						
						if ( entry.getValue().contains(x, y )){
							
							entry.getKey().eventOccurred(
								new NodeEvent()
								{
									public int
									getType()
									{
										return( NodeEvent.ET_CLICKED );
									}
									
									public Object
									getData()
									{
										return( null );
									}
								});
								
						}
					}
				}
				
				if ( scale.mouseRightDown ){
					
					int	x = event.x;
					int y = event.y;
					
					for ( Map.Entry<Node,Rectangle> entry: node_text_map.entrySet()){
						
						if ( entry.getValue().contains(x, y )){
							
							Menu menu = canvas.getMenu();

							if ( menu != null && !menu.isDisposed()){

								menu.dispose();
							}

							menu = new Menu( canvas );

							final Point cursorLocation = Display.getCurrent().getCursorLocation();

							menu.setLocation( cursorLocation.x, cursorLocation.y );

							menu.setVisible( true );
							
							Menu f_menu = menu;
							
							entry.getKey().eventOccurred(
								new NodeEvent()
								{
									public int
									getType()
									{
										return( NodeEvent.ET_MENU );
									}
									
									public Object
									getData()
									{
										return( f_menu );
									}
								});
								
							break;
						}
					}
				}	
				
				scale.mouseUp( event );
				
				refresh();
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				scale.reset();
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
				scale.mouseWheel(event);
				refresh();
			}
		});

		canvas.addMouseMoveListener(new MouseMoveListener() {
			@Override
			public void mouseMove(MouseEvent event) {
				if ( scale.mouseMove( event )){

					refresh();
				}
			}
		});

		canvas.addMouseTrackListener(new MouseTrackListener() {
			@Override
			public void mouseHover(MouseEvent e) {
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

		timeout_timer =
			SimpleTimer.addPeriodicEvent(
				"DHTOps:timer",
				30*1000,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent event)
					{
						if ( canvas.isDisposed()){

							timeout_timer.cancel();

							return;
						}

						synchronized( activity_map ){

							Iterator<ActivityDetail> it = activity_map.values().iterator();

							while( it.hasNext()){

								ActivityDetail act = it.next();

								if ( act.isComplete()){

									it.remove();
								}
							}
						}
					}
				});
	}

	public void
	setID( String _id )
	{
		id = _id;
		
		Scale existing;
		
		synchronized( scale_map ){
			
			existing = scale_map.get( id );
		}
		
		if ( existing != null ){
			
			Utils.execSWTThread(()->{
				scale	= existing;
				refresh();
			});
		}
	}
	
	public void setLayoutData(Object data) {
		canvas.setLayoutData(data);
	}

	public void
	activityChanged(
		Activity	activity,
		boolean		removed )
	{
		if ( filter != null && !filter.accept( activity )){

			return;
		}

		//System.out.println( activity.getString() + "/" + type + "/" + activity.getCurrentState().getString());

		if ( activity.isQueued()){

				// ignore these until they become active

			return;
		}

		synchronized( activity_map ){

			ActivityDetail details = activity_map.get( activity );

			if ( details == null && !removed ){

				details = new ActivityDetail( activity );

				activity_map.put( activity, details );
				
					// seeing an undiagnosed leak in this area, put a hard limit on how many activities we retain...
				
				if ( activity_map.size() > 250 ){
				
					List<ActivityDetail> entries = new ArrayList<>( activity_map.values());
					
					Collections.sort(
						entries,
						new Comparator<ActivityDetail>()
						{
							public int
							compare(
								ActivityDetail	a1,
								ActivityDetail	a2 )
							{
								long res = a1.getCreateTime() - a2.getCreateTime();
								
								if ( res < 0 ){
									return( -1 );
								}else if ( res > 0 ){
									return( 1 );
								}else{
									return( 0 );
								}
							}
						});
					
					for ( int i=0; i<Math.min(50, entries.size()); i++ ){
						
						activity_map.remove( entries.get( i ).getActivity());
					}
				}
			}

			if ( details != null && removed ){

				details.setComplete();
			}
		}
	}

	protected void
	setUnavailable()
	{
		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					unavailable = true;

					if ( !canvas.isDisposed()){

						canvas.redraw();
					}
				}
			});
	}

	public void
	setFilter(
		ActivityFilter		f )
	{
		filter = f;
	}

		/**
		 * @param min things don't work well for < 4...
		 */

	public void
	setMinimumSlots(
		int		min )
	{
		min_slots = min;
	}

	public void
	setScaleAndRotation(
		float		min_x,
		float		max_x,
		float		min_y,
		float		max_y,
		double		rot )
	{
		scale.setScaleAndRotation( min_x, max_x, min_y, max_y, rot );
	}

	private boolean isRefreshQueued = false;
	public void refresh() {
		if (isRefreshQueued) {
			return;
		}
		isRefreshQueued = true;
		Utils.execSWTThreadLater(20, () -> {
			isRefreshQueued = false;
			_refresh();
		});
	}

	private void
	_refresh()
	{
		if ( canvas.isDisposed()){

			return;
		}

		Point canvasSize = canvas.getSize();
		Rectangle size = new Rectangle(0, 0, canvasSize.x, canvasSize.y);

		if ( size.width <= 0 || size.height <= 0 ){

			return;
		}

		scale.setSize( size );

		boolean needNewImage = img == null || img.isDisposed();
		if (!needNewImage) {
			Rectangle bounds = img.getBounds();
			needNewImage = bounds.width != size.width || bounds.height != size.height;
		}
		if (needNewImage) {
			img = new Image(display,size);
		}

		boolean dark = Utils.isDarkAppearanceNative();
		
		GC gc = new GC(img);

		gc.setAdvanced( true );

		gc.setAntialias( SWT.ON );
		gc.setTextAntialias( SWT.ON );

		Color white = Colors.white;
		
		gc.setBackground(dark?canvas.getBackground():white);
		gc.fillRectangle(size);

		List<ActivityDetail>	activities;

		List<ActivityDetail>	to_remove = new ArrayList<>();

		synchronized( activity_map ){

			activities = new ArrayList<>( activity_map.values());
		}

		long	now = SystemTime.getMonotonousTime();

		int	max_slot = Math.max( activities.size(), min_slots );

		for ( ActivityDetail details: activities ){

			max_slot = Math.max( max_slot, details.getSlot()+1);

			long comp_at = details.getCompleteTime();

			if ( comp_at >= 0 && now - comp_at > FADE_OUT ){

				to_remove.add( details );
			}
		}

		boolean[]	slots_in_use = new boolean[max_slot];

		for ( ActivityDetail details: activities ){

			int	slot = details.getSlot();

			if ( slot != -1 ){

				slots_in_use[slot] = true;
			}
		}

		int pos = 0;

		for ( ActivityDetail details: activities ){

			int	slot = details.getSlot();

			if ( slot == -1 ){

				while( slots_in_use[pos] ){

					pos++;
				}

				details.setSlot( pos++ );
			}
		}

	 	int x_origin = scale.getX(0, 0);
		int y_origin = scale.getY(0, 0);

		double slice_angle = 2*Math.PI/max_slot;

		node_text_map.clear();
		
		for ( ActivityDetail details: activities ){

			details.draw( gc, x_origin, y_origin, slice_angle );
		}

		gc.setForeground( dark?Colors.light_grey:Colors.black );

		if ( activities.size() == 0 ){

			gc.drawText( MessageText.getString( DHTOpsView.MSGID_PREFIX + ".idle" ), x_origin, y_origin );

		}else{

			gc.drawLine(x_origin-5, y_origin, x_origin+5, y_origin);
			gc.drawLine(x_origin, y_origin-5, x_origin, y_origin+5);

		}

		gc.dispose();

		canvas.redraw();

		if ( to_remove.size() > 0 ){

			synchronized( activity_map ){

				for ( ActivityDetail detail: to_remove ){

					activity_map.remove( detail.getActivity());
				}
			}
		}
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

	public void
	reset()
	{
		synchronized( activity_map ){

			activity_map.clear();
		}
	}
	
	public void delete()
	{
		if (img != null && !img.isDisposed()){
		
			img.dispose();
		}

		synchronized( activity_map ){

			activity_map.clear();
		}
		
		if ( id != null ){
			
			synchronized( scale_map ){
				
				scale_map.put( id,  scale.clone());
			}
		}
	}

	public interface
	State
	{
		public Node
		getRootNode();
		
		public int
		getDepth();
		
		public String
		getResult();
	}
	
	public interface
	Node
	{
		public static final int	TYPE_1	= 1;
		public static final int	TYPE_2	= 2;
		public static final int	TYPE_3	= 3;
		
		public default String
		getName()
		{
			return( null );
		}
		
		public default int
		getType()
		{
			return( TYPE_1 );
		}
		
		public default Object
		eventOccurred(
			NodeEvent	ev )
		{
			return( null );
		}
		
		public List<Node>
		getChildren();
	}
	
	public interface
	NodeEvent
	{
		public final int ET_CLICKED		= 1;
		public final int ET_MENU		= 2;
		
		public int
		getType();
		
		public Object
		getData();
	}
	
	public interface
	Activity
	{
		public static final int	TYPE_1			= 1;
		public static final int	TYPE_2			= 2;
		public static final int	TYPE_3			= 3;
		public static final int	TYPE_DEFAULT	= 4;
		
		public String
		getDescription();
		
		public int
		getType();
		
		public boolean
		isQueued();
		
		public State
		getCurrentState();
	}
	
	private class
	ActivityDetail
	{
		private final long		create_time	= SystemTime.getMonotonousTime();
		
		private Activity		activity;
		private long			complete_time = -1;

		private int		slot	= -1;

		private int		draw_count	= 0;
		private String	result_str	= "";

		private
		ActivityDetail(
			Activity		_act )
		{
			activity	= _act;
		}

		private long
		getCreateTime()
		{
			return( create_time );
		}
		
		private Activity
		getActivity()
		{
			return( activity );
		}

		private void
		setComplete()
		{
			complete_time = SystemTime.getMonotonousTime();
		}

		private long
		getCompleteTime()
		{
			return( complete_time );
		}

		private boolean
		isComplete()
		{
			return( complete_time != -1 &&
					SystemTime.getMonotonousTime() - complete_time > FADE_OUT );
		}

		private int
		getSlot()
		{
			return( slot );
		}

		private void
		setSlot(
			int	_s )
		{
			slot	= _s;
		}

		private void
		draw(
			GC		gc,
			int		x_origin,
			int		y_origin,
			double	slice_angle )
		{
			boolean dark = Utils.isDarkAppearanceNative();
			
			draw_count++;

			setColour( gc );

			double angle = slice_angle*slot;

			State state_maybe_null = activity.getCurrentState();

			if ( state_maybe_null != null ){

				int	depth = state_maybe_null.getDepth();

				int	level_depth = 750/depth;

				Node root = state_maybe_null.getRootNode();

				List<Object[]> level_nodes = new ArrayList<>();

				float x_start = (float)( 50*Math.sin( angle ));
				float y_start = (float)( 50*Math.cos( angle ));

				level_nodes.add( new Object[]{ root, x_start, y_start });

				int	node_distance = 50;

				while( true ){

					int	nodes_at_next_level = 0;

					for ( Object[] entry: level_nodes ){

						nodes_at_next_level += ((Node)entry[0]).getChildren().size();
					}

					if ( nodes_at_next_level == 0 ){

						break;
					}

					node_distance += level_depth;

					double node_slice_angle = slice_angle/nodes_at_next_level;

					double current_angle = angle;

					if ( nodes_at_next_level > 1 ){

						current_angle = current_angle - (slice_angle/2);

						current_angle += (slice_angle - node_slice_angle*(nodes_at_next_level-1))/2;
					}

					List<Object[]> next_level_nodes = new ArrayList<>();

					for ( Object[] entry: level_nodes ){

						Node			node 	= (Node)entry[0];
						float			node_x	= (Float)entry[1];
						float			node_y	= (Float)entry[2];

						int seg_start_x = scale.getX(node_x, node_y);
						int seg_start_y = scale.getY(node_x, node_y);
						
						List<Node> kids = node.getChildren();

						for ( Node kid: kids ){

							float	kid_x = (float)( node_distance*Math.sin( current_angle ));
							float	kid_y = (float)( node_distance*Math.cos( current_angle ));

							next_level_nodes.add( new Object[]{ kid, kid_x, kid_y } );

							current_angle += node_slice_angle;

							int seg_end_x = scale.getX(kid_x, kid_y);
							int seg_end_y = scale.getY(kid_x, kid_y);

							gc.drawLine(seg_start_x, seg_start_y, seg_end_x, seg_end_y );

							gc.drawOval( seg_end_x, seg_end_y, 1, 1 );
							
							String kid_name = kid.getName();
							
							if ( kid_name != null ){
								
								Point extent = gc.textExtent( kid_name );
								
								int	x_pos;
								int y_pos	= seg_end_y;
							
								if ( seg_end_x < x_origin ){
									
									x_pos = seg_end_x - extent.x;
									
								}else{
									
									x_pos = seg_end_x;
								}
								
								int kid_type = kid.getType();
								
								Color fg = null;
								
								if ( kid_type == Node.TYPE_2 ){
									
									fg = dark?Colors.grey:Colors.black;
									
								}else if ( kid_type == Node.TYPE_3 ){
									
									fg = Colors.fadedGreen;
								}
								
								if ( fg == null ){
									
									gc.drawText( kid_name, x_pos, y_pos );
									
								}else{
									
									Color old = gc.getForeground();
									
									gc.setForeground( fg );
									
									gc.drawText( kid_name, x_pos, y_pos );
									
									gc.setForeground( old );
								}
								
								Rectangle text_area = new Rectangle( x_pos, y_pos, extent.x, extent.y );
								
								node_text_map.put( kid, text_area );
							}
						}
					}

					level_nodes = next_level_nodes;
				}
			}

			float x_end = (float)( 850*Math.sin( angle ));
			float y_end = (float)( 850*Math.cos( angle ));

		 	int text_x = scale.getX(x_end, y_end);
			int text_y = scale.getY(x_end, y_end);

			String desc = activity.getDescription();

			if ( complete_time >= 0 && result_str.length() == 0 ){

				if ( state_maybe_null != null ){

					result_str = ( desc.length()==0?"":": " ) + state_maybe_null.getResult();
				}
			}

			gc.drawText( desc + result_str, text_x, text_y );

			//gc.drawLine(x_origin, y_origin, (int)x_end, (int)y_end );

			gc.setAlpha( 255 );
		}

		private void
		setColour(
			GC		gc )
		{
			boolean dark = Utils.isDarkAppearanceNative();
			
			if ( complete_time != -1 && draw_count > 1 ){

				int age = (int)( SystemTime.getMonotonousTime() - complete_time );

				gc.setAlpha( Math.max( 0, 200 - (255*age/FADE_OUT)));

				gc.setForeground( dark?Colors.grey:Colors.black );

			}else{

				gc.setAlpha( 255 );

				int type = activity.getType();

				if ( type == Activity.TYPE_1 ){

					gc.setForeground( ColorCache.getColor( gc.getDevice(), 20, 200, 20 ));

				}else if ( type == Activity.TYPE_2 ){

					gc.setForeground( ColorCache.getColor( gc.getDevice(), 140, 160, 40 ));

				}else if ( type == Activity.TYPE_3 ){

					gc.setForeground( dark?ColorCache.getColor( gc.getDevice(), 100, 150, 220 ):ColorCache.getColor( gc.getDevice(), 20, 20, 220 ));

				}else{

					gc.setForeground( ColorCache.getColor( gc.getDevice(), 40, 140, 160 ));
				}
			}
		}
	}

	public interface
	ActivityFilter
	{
		public boolean
		accept(
			Activity		activity );
	}
}
