/*
 * File    : SpeedGraphic.java
 * By      : Olivier
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
package com.biglybt.ui.swt.components.graphics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class
MultiPlotGraphic
	extends ScaledGraphic
	implements ParameterListener
{
	private static final int	DEFAULT_ENTRIES	= 2000;

	public static MultiPlotGraphic
	getInstance(
		ValueSource[]	sources,
		ValueFormater 	formatter )
	{
	    return(  new MultiPlotGraphic( new Scale(), sources, formatter, DEFAULT_ENTRIES ));
	}

	public static MultiPlotGraphic
	getInstance(
		int				num_entries,
		ValueSource[]	sources,
		ValueFormater 	formatter )
	{
	    return(  new MultiPlotGraphic( new Scale(), sources, formatter, num_entries ));
	}
	
	private ValueSource[]	value_sources;

	private int internalLoop;
	private int graphicsUpdate;
	private Point oldSize;

	private Image bufferImage;

	private int					nbValues		= 0;
	private int					maxEntries		= DEFAULT_ENTRIES;
	private int[][]				all_values;
	private int					currentPosition	= 0;

	private boolean				update_outstanding = false;

	private int					update_period_millis;
	private TimerEventPeriodic	update_event;

	private boolean				maintain_history;

	private SimpleDateFormat	timeFormatter = new SimpleDateFormat("HH:mm", Locale.US );


	private
	MultiPlotGraphic(
		Scale 			scale,
		ValueSource[]	sources,
		ValueFormater 	formater,
		int				num_entries )
	{
		super( scale,formater );

		value_sources	= sources;

		maxEntries		= num_entries;
		
		init( null );

	    COConfigurationManager.addAndFireParameterListeners(
	    	new String[]{ "Graphics Update", "Stats Graph Dividers" }, this );
	}
	
	private void
	init(
		int[][]	history )
	{
		nbValues		= 0;
		all_values		= new int[value_sources.length][maxEntries];
		currentPosition	= 0;

		if ( history != null ){

			if ( history.length != value_sources.length ){

				Debug.out( "Incompatible history records, ignored" );

			}else{
				if ( history.length > 0 ){

					int	history_entries = history[0].length;

					int	offset = Math.max( history_entries - maxEntries, 0 );

					for ( int i=offset; i<history_entries;i++){

						for ( int j=0;j<history.length;j++){

							all_values[j][nbValues] = history[j][i];
						}

						nbValues++;
					}
				}

				currentPosition = nbValues%maxEntries;
			}
		}

		update_outstanding = true;
	}

	public int[][]
	getHistory()
	{
		int[][] result = new int[all_values.length][nbValues];
		
		int offset;
		
		if ( nbValues == maxEntries ){
		
			offset = currentPosition;
			
		}else{
			
			offset = 0;
		}
		
		for ( int i=0;i<nbValues;i++){
			for ( int j=0;j<result.length;j++){
				result[j][i] = all_values[j][(i+offset)%maxEntries];
			}
		}

		return( result );
	}
	
	@Override
	public void
	initialize(
		Canvas 	canvas )
	{
		initialize( canvas, true );
	}

	public void
	initialize(
		Canvas 	canvas,
		boolean	is_active )
	{
	  	super.initialize(canvas);

	  	drawCanvas.addPaintListener(new PaintListener() {
				@Override
				public void paintControl(PaintEvent e) {
					if (bufferImage != null && !bufferImage.isDisposed()) {
						Rectangle bounds = bufferImage.getBounds();
						if (bounds.width >= ( e.width + e.x ) && bounds.height >= ( e.height + e.y )) {

							e.gc.drawImage(bufferImage, e.x, e.y, e.width, e.height, e.x, e.y, e.width, e.height);
						}
					}
				}
			});

	  	drawCanvas.addListener(SWT.Resize, new Listener() {
				@Override
				public void handleEvent(Event event) {
					drawChart(true);
				}
			});

	  	setActive( is_active );
	}

	  @Override
	  protected void
	  addMenuItems(
		Menu	menu )
	  {
		  new MenuItem( menu, SWT.SEPARATOR );
		  
		  MenuItem mi_reset = new MenuItem( menu, SWT.PUSH );

		  mi_reset.setText(  MessageText.getString( "label.clear.history" ));

		  mi_reset.addListener(SWT.Selection, new Listener() {
			  @Override
			  public void handleEvent(Event e) {
				  try{
				   	this_mon.enter();
				   	
				   	nbValues		= 0;
				   	currentPosition	= 0;
			 		
				   	for ( int i=0;i<all_values.length;i++ ){
				   		all_values[i] = new int[all_values[i].length];
				   	}		
				  }finally{
					  
					this_mon.exit();
				  }
				  
				  refresh( true );
			  }
		  });
	  }
	  
	public void
	setActive(
		boolean	active )
	{
		setActive( active, 1000 );
	}
	
	public void
	setActive(
		boolean	active,
		int		period_millis )
	{
		update_period_millis = period_millis;
		
		if ( active ){

			if ( update_event != null ){

				return;
			}

		  	update_event = SimpleTimer.addPeriodicEvent(
		  		"MPG:updater",
		  		period_millis,
		  		new TimerEventPerformer()
		  		{
		  			@Override
					  public void
		  			perform(
		  				TimerEvent event )
		  			{
		  				if ( !maintain_history && drawCanvas.isDisposed()){

		  					if ( update_event != null ){

		  						update_event.cancel();

		  						update_event = null;
		  					}
		  				}else{
			  				int[]	new_values = new int[value_sources.length];

			  				for ( int i=0;i<new_values.length;i++){

			  					new_values[i] = value_sources[i].getValue();
			  				}

			  				addIntsValue( new_values );
		  				}
		  			}
		  		});
		}else{

			if ( update_event != null ){

				update_event.cancel();

				update_event = null;
			}
		}
	}

	public void
	reset(
		int[][]		history )
	{
		init( history );

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					refresh( true );
				}
			});
	}

	public int[]
	getAverage(
		int		num_entries )
	{
	    try{
	    	this_mon.enter();

	    	long[]	averages = new long[all_values.length];
	    		
	    	if ( nbValues < num_entries ){
	    		
	    		num_entries = nbValues;
	    	}
	    	
	    	if ( num_entries > 0 ){
	    		
	    		int start	= currentPosition-num_entries;
	    			    	
		    	if ( start < 0 ){
			    	if ( nbValues < maxEntries ){
		    			start = 0;
		    		}else{
		    			start += maxEntries;
		    		}
		    	}
		    	
		    	for ( int i=start;i<start+num_entries;i++){
		    		
		    		int	pos = i%maxEntries;
		    		
		    		for ( int j=0;j<averages.length;j++){
		    			averages[j] += all_values[j][pos];
		    		}
		    	}
		    	
		    	for ( int j=0;j<averages.length;j++){
	    			averages[j] /= num_entries;
	    		}
	    	}
	    	
	    	int[] i_averages =  new int[averages.length];
	    	
	    	for ( int j=0;j<averages.length;j++){
	    		i_averages[j] = (int)averages[j];
	    	}
	    	
	    	return( i_averages );
	    	
	    }finally{

	    	this_mon.exit();
	    }
	}
	
	private void
	addIntsValue(
		int[] new_values)
	{
	    try{
	    	this_mon.enter();

	    	if ( all_values.length < new_values.length ){

	    		int[][]	new_all_values = new int[new_values.length][];

			    System.arraycopy(all_values, 0, new_all_values, 0, all_values.length);

	    		for (int i=all_values.length;i<new_all_values.length; i++ ){

	    			new_all_values[i] = new int[maxEntries];
	    		}

	    		all_values = new_all_values;
	    	}

		    for (int i=0;i<new_values.length;i++){

		        all_values[i][currentPosition] = new_values[i];
	    	}

		    currentPosition++;

		    if(nbValues < maxEntries){

		      nbValues++;
		    }

		    currentPosition %= maxEntries;


	    }finally{

	    	this_mon.exit();
	    }

	    if ( update_outstanding ){

	    	update_outstanding = false;

			Utils.execSWTThread(
				new Runnable()
				{
					@Override
					public void
					run()
					{
						refresh( true );
					}
				});
	    }
	}


	@Override
	public void
	refresh(
		boolean force )
	{
		if ( drawCanvas == null || drawCanvas.isDisposed()){

			return;
		}

		Rectangle bounds = drawCanvas.getClientArea();

		if ( bounds.height < 1 || bounds.width  < 1 || bounds.width > 10000 || bounds.height > 10000){

			return;
		}

			// inflate # of values only if necessary

		if ( bounds.width > maxEntries ){

			try{
				this_mon.enter();
				
				boolean	cycled = nbValues == maxEntries;
				
				while( maxEntries < bounds.width ){

					maxEntries += 100;
				}

				for( int i=0;i<all_values.length;i++ ){

					int[] oldValues	= all_values[i];
					
					int[] newValues = new int[maxEntries];

					if ( cycled ){
						
						int	pos = currentPosition;
						
						for ( int j=0;j<nbValues;j++){
							
							newValues[j] = oldValues[pos++];
							
							if ( pos == nbValues ){
								
								pos = 0;
							}
						}
					}else{
						
						System.arraycopy( oldValues, 0, newValues, 0, nbValues );
					}

					all_values[i] = newValues;
				}
				
				currentPosition = nbValues;
				
			}finally{

				this_mon.exit();
			}
		}

		boolean sizeChanged = (oldSize == null || oldSize.x != bounds.width || oldSize.y != bounds.height);

		oldSize = new Point(bounds.width,bounds.height);

		internalLoop++;

		if( internalLoop > graphicsUpdate ){
			internalLoop = 0;
		}

		if ( internalLoop == 0 || sizeChanged || force ){

			drawChart( sizeChanged );

				// to get the scale to redraw correctly we need to force a second drawing as it
				// is the result of the initial draw that sets the scale...

			if ( force ){

				drawChart( true );
			}
		}

		drawCanvas.redraw();
		drawCanvas.update();
	}

	protected void
	drawChart(
		boolean sizeChanged)
	{
		if ( drawCanvas == null || drawCanvas.isDisposed() || !drawCanvas.isVisible()){

			return;
		}

		GC gcImage = null;

		try{
			this_mon.enter();

			drawScale( sizeChanged );

			if ( bufferScale == null || bufferScale.isDisposed()){

				return;
			}

			Rectangle bounds = drawCanvas.getClientArea();

			if ( bounds.isEmpty()){
				return;
			}

				//If bufferedImage is not null, dispose it

			if (bufferImage != null && !bufferImage.isDisposed()){

				bufferImage.dispose();
			}

			bufferImage = new Image(drawCanvas.getDisplay(), bounds);

			gcImage = new GC(bufferImage);

			gcImage.drawImage(bufferScale, 0, 0);

			gcImage.setAntialias( SWT.ON );
			gcImage.setTextAntialias( SWT.ON );

			Set<ValueSource>	invisible_sources = new HashSet<>();

			for ( int i=0;i<value_sources.length;i++){

				ValueSource source = value_sources[i];

				if (( source.getStyle() & ValueSource.STYLE_INVISIBLE ) != 0 ){

					invisible_sources.add( source );
				}
			}

			int[] oldTargetValues = new int[all_values.length];

			int[] maxs = new int[all_values.length];

			long now		= SystemTime.getCurrentTime();
			
			int next_secs = 60;

			int last_xpos = -1;
			
			for (int x = 0; x < bounds.width - 71; x++){

				int position = currentPosition - x - 1;

				if ( position < 0 ){

					position += maxEntries;

					if ( position < 0 ){

						position = 0;
					}
				}

				boolean has_value = nbValues>=maxEntries||position<nbValues;

				if ( has_value ){

					int	this_age = (x*update_period_millis)/1000;;
					
					if ( this_age >= next_secs ){
						
						next_secs += 60;
						
						long time = now - (this_age*1000);
						
						String str = timeFormatter.format( new Date( time ));
												
						Point p = gcImage.stringExtent( str );
						
						int xDraw = bounds.width - 71 - x;

						int xPos = xDraw-p.x/2;
						
						if ( xPos >= 0 ){
						
							if ( last_xpos < 0 ||  xPos + p.x < last_xpos ){
							
								gcImage.setForeground( colorGrey );

								gcImage.drawText( str, xPos, 0, true );
								
								last_xpos = xPos;
							}
						}
					}
				}

				for (int chartIdx = 0; chartIdx < all_values.length; chartIdx++){

					ValueSource source = value_sources[chartIdx];

					if ( invisible_sources.contains( source )){

						continue;
					}

					int value = all_values[chartIdx][position];

					if (value > maxs[chartIdx]){

						maxs[chartIdx] = value;
					}
				}
			}

			Set<ValueSource>	bold_sources = new HashSet<>();
			Set<ValueSource>	dotted_sources = new HashSet<>();

			int max = 0;

			for ( int i=0;i<maxs.length;i++){

				ValueSource source = value_sources[i];

				if ( invisible_sources.contains( source )){

					continue;
				}

				if (( source.getStyle() & ValueSource.STYLE_BOLD ) != 0 ){

					bold_sources.add( source );
				}

				if (( source.getStyle() & ValueSource.STYLE_DOTTED ) != 0 ){

					dotted_sources.add( source );
				}

				if ( !source.isTrimmable()){

					max = Math.max( max, maxs[i] );
				}
			}

			int max_primary = max;

			for ( int i=0;i<maxs.length;i++){

				ValueSource source = value_sources[i];

				if ( invisible_sources.contains( source )){

					continue;
				}

				if ( source.isTrimmable()){

						// trim secondary indicators so we don't loose the more important info

					int m = maxs[i];

					if ( max < m ){

						if ( m <= 2 * max_primary ){

							max = m;

						}else{

							max = 2 * max_primary;

							break;
						}
					}
				}
			}

			int kInB = DisplayFormatters.getKinB();

			if ( max > 5*kInB ){

				max = (( max + kInB - 1 )/kInB)*kInB;
			}

			scale.setMax( max );

			int[]	prev_x = new int[value_sources.length];
			int[]	prev_y = new int[value_sources.length];

			int	bounds_width_adj = bounds.width - 71;

			int	cycles = bold_sources.size()==0?2:3;


			for (int x = 0; x < bounds_width_adj; x++){

				int position = currentPosition - x - 1;

				if (position < 0){

					position += maxEntries;

					if ( position < 0 ){

						position = 0;
					}
				}

				int xDraw = bounds_width_adj - x;

				for ( int order=0;order<cycles;order++){

					for (int chartIdx = 0; chartIdx < all_values.length; chartIdx++){

						ValueSource source = value_sources[chartIdx];

						if ( invisible_sources.contains( source )){

							continue;
						}

						boolean is_bold = bold_sources.contains( source );

						if ( is_bold && order != 2 ){

							continue;
						}

						boolean is_dotted = dotted_sources.contains( source );

						if ( ( source.isTrimmable() == (order==0 ) && order < 2 ) || ( is_bold && order == 2 )){

							Color line_color = source.getLineColor();

							int targetValue = all_values[chartIdx][position];

							int oldTargetValue = oldTargetValues[chartIdx];

							if ( x > 0 ){

								int trimmed;

								if ( is_dotted ){

									trimmed = 2;

								}else{

									trimmed = 0;

									if (targetValue > max){
										targetValue = max;
										trimmed++;
									}

									if (oldTargetValue > max){
										oldTargetValue = max;
										trimmed++;
									}
								}

								boolean force_draw = ( trimmed == 2 && position % 4 == 0 ) || xDraw == 1;

								int h1 = bounds.height - scale.getScaledValue(targetValue) - 2;

								if ( x == 1 ){

									int h2 = bounds.height - scale.getScaledValue(oldTargetValue) - 2;

									prev_x[chartIdx] = xDraw+1;
									prev_y[chartIdx] = h2;
								}

								if ( trimmed < 2 || force_draw ){

									if ( h1 != prev_y[chartIdx] || force_draw ){

										gcImage.setAlpha( source.getAlpha());
										gcImage.setLineWidth( trimmed==2?3:is_bold?4:2 );
										gcImage.setForeground( line_color );

										gcImage.drawLine(xDraw+1, prev_y[chartIdx], prev_x[chartIdx], prev_y[chartIdx]);
										gcImage.drawLine(xDraw, h1, xDraw + 1, prev_y[chartIdx]);

										prev_x[chartIdx] = xDraw;
										prev_y[chartIdx] = h1;
									}
								}else{

									prev_x[chartIdx] = xDraw;
									prev_y[chartIdx] = h1;
								}
							}

							oldTargetValues[chartIdx] = all_values[chartIdx][position];
						}
					}
				}
			}

			if ( nbValues > 0 ){

				for ( int order=0;order<cycles;order++){

					for ( int chartIdx = 0; chartIdx < all_values.length; chartIdx++){

						ValueSource source = value_sources[chartIdx];

						if ( invisible_sources.contains( source )){

							continue;
						}

						boolean is_bold = bold_sources.contains( source );

						if ( is_bold && order != 2 ){

							continue;
						}

						if ( ( source.isTrimmable() == (order==0 ) && order < 2 ) || ( is_bold && order == 2 )){

							int	style = source.getStyle();

							boolean show_label = ( style & ValueSource.STYLE_HIDE_LABEL ) == 0;
								
							if ( !bold_sources.isEmpty()){
								
								show_label = is_bold;
							}
							
							if ( show_label ){

								int	average_val = computeAverage( chartIdx, currentPosition - 6 );

								int average_mod = average_val;

								if ( average_mod > max ){

									average_mod = max;
								}

								int height = bounds.height - scale.getScaledValue( average_mod) - 2;

								gcImage.setAlpha( 255 );

								gcImage.setForeground( source.getLineColor());

								gcImage.drawText(formater.format( average_val ), bounds.width - 65, height - 12, SWT.DRAW_TRANSPARENT);

								Color bg = gcImage.getBackground();

								if (( style & ValueSource.STYLE_DOWN ) != 0 ){

									int	x = bounds.width - 72;
									int y = height - 12;

									gcImage.setBackground( source.getLineColor());

									gcImage.fillPolygon(new int[] { x, y, x+7, y, x+3, y+7 });

									gcImage.setBackground( bg );

								}else  if (( style & ValueSource.STYLE_UP ) != 0 ){

									int	x = bounds.width - 72;
									int y = height - 12;

									gcImage.setBackground( source.getLineColor());

									gcImage.fillPolygon(new int[] { x, y+7, x+7, y+7, x+3, y });

									gcImage.setBackground( bg );
								
								}else  if (( style & ValueSource.STYLE_BLOB ) != 0 ){

									int	x = bounds.width - 72;
									int y = height - 12;

									gcImage.setBackground( source.getLineColor());

									gcImage.fillOval( x, y, 5, 5 );

									gcImage.setBackground( bg );
								}
							}
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );

		}finally{

			if ( gcImage != null ){

				gcImage.dispose();
			}

			this_mon.exit();
		}
	}

	private int
	computeAverage(
		int	line_index,
		int position )
	{
		long sum = 0;
		for(int i = -5 ; i < 6 ; i++) {
			int pos = position + i;
			pos %= maxEntries;
			if (pos < 0)
				pos += maxEntries;
			sum += all_values[line_index][pos];
		}
		return(int)(sum / 11);

	}

	@Override
	public void
	parameterChanged(
		String parameter )
	{
		graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");

		boolean update_dividers = COConfigurationManager.getBooleanParameter("Stats Graph Dividers");

		int update_divider_width = update_dividers ? 60 : 0;

		setUpdateDividerWidth( update_divider_width );
	}
	
	@Override
	public void
	dispose()
	{
		dispose( false );
	}
	
	public void
	dispose(
		boolean	_maintain_history )
	{
		maintain_history = _maintain_history;
		
		super.dispose();

		if ( bufferImage != null && ! bufferImage.isDisposed()){

			bufferImage.dispose();
		}

		if ( update_event != null && !maintain_history ){

			update_event.cancel();

			update_event = null;
		}

		COConfigurationManager.removeParameterListener("Graphics Update",this);
		COConfigurationManager.removeParameterListener("Stats Graph Dividers" ,this);
	}
}
