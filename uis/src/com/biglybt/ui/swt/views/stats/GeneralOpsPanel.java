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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.PeersViewBase;

public class
GeneralOpsPanel
{
	private static final int ALPHA_FOCUS = 255;
	private static final int ALPHA_NOFOCUS = 150;

	private static final int	FADE_OUT	= 10*1000;

	Display display;
	Composite parent;

	Canvas canvas;
	Scale scale;

	private int		min_slots	= 8;

	private boolean	unavailable;

	private boolean mouseLeftDown = false;
	private boolean mouseRightDown = false;
	private int xDown;
	private int yDown;

	private Image img;

	private int alpha = 255;

	private boolean autoAlpha = false;

	private ActivityFilter	filter;

	private Map<Activity,ActivityDetail>	activity_map = new HashMap<>();

	private TimerEventPeriodic timeout_timer;

	private Map<Node,Rectangle>	node_text_map = new HashMap<>();
	
	private static class Scale {
		int width;
		int height;

		float minX = -1000;
		float maxX = 1000;
		float minY = -1000;
		float maxY = 1000;
		double rotation = 0;

		float saveMinX;
		float saveMaxX;
		float saveMinY;
		float saveMaxY;
		double saveRotation;

		public int getX(float x,float y) {
			return (int) (((x * Math.cos(rotation) + y * Math.sin(rotation))-minX)/(maxX - minX) * width);
		}

		public int getY(float x,float y) {
			return (int) (((y * Math.cos(rotation) - x * Math.sin(rotation))-minY)/(maxY-minY) * height);
		}
	}

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
				
				if ( mouseLeftDown ){
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
				
				if ( mouseRightDown ){
					
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
				
				if(event.button == 1) mouseLeftDown = false;
				if(event.button == 3) mouseRightDown = false;
				
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
			private long last_refresh;
			@Override
			public void mouseMove(MouseEvent event) {
				boolean	do_refresh = false;
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
					do_refresh = true;
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
					do_refresh = true;
				}

				if ( do_refresh ){

					long now = SystemTime.getMonotonousTime();

					if ( now - last_refresh >= 250 ){

						last_refresh = now;

						refresh();
					}
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

			if ( details == null ){

				details = new ActivityDetail( activity );

				activity_map.put( activity, details );
			}

			if ( removed ){

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
		scale.minX 		= min_x;
		scale.maxX 		= max_x;
		scale.minY 		= min_y;
		scale.maxY 		= max_y;
		scale.rotation 	= rot;
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

		scale.width = size.width;
		scale.height = size.height;

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

		gc.setForeground( ColorCache.getColor( gc.getDevice(), 0, 0, 0 ));

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
									
									fg = Colors.black;
									
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
			if ( complete_time != -1 && draw_count > 1 ){

				int age = (int)( SystemTime.getMonotonousTime() - complete_time );

				gc.setAlpha( Math.max( 0, 200 - (255*age/FADE_OUT)));

				gc.setForeground( ColorCache.getColor( gc.getDevice(), 0, 0, 0 ));

			}else{

				gc.setAlpha( 255 );

				int type = activity.getType();

				if ( type == Activity.TYPE_1 ){

					gc.setForeground( ColorCache.getColor( gc.getDevice(), 20, 200, 20 ));

				}else if ( type == Activity.TYPE_2 ){

					gc.setForeground( ColorCache.getColor( gc.getDevice(), 140, 160, 40 ));

				}else if ( type == Activity.TYPE_3 ){

					gc.setForeground( ColorCache.getColor( gc.getDevice(), 20, 20, 220 ));

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
