/*
 * File    : SpeedGraphic.java
 * Created : 15 d�c. 2003}
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.mainwindow.Colors;

/**
 * @author Olivier
 *
 */
public class SpeedGraphic extends ScaledGraphic implements ParameterListener {

	private static final int	DEFAULT_ENTRIES	= 2000;

	public static final int COLOR_AVERAGE = 0;
	public static final int COLOR_MAINSPEED = 1;
	public static final int COLOR_OVERHEAD = 2;
	public static final int COLOR_LIMIT = 3;
	public static final int COLOR_OTHERS = 4;
	public static final int COLOR_TRIMMED = 5;

	public Color[] colors = new Color[] {
			Colors.red, Colors.blues[Colors.BLUES_MIDDARK], Colors.colorInverse, Colors.blue, Colors.grey,
			Colors.light_grey
	};

	private int internalLoop;
	private int graphicsUpdate;
	private Point oldSize;

	protected Image bufferImage;

	private int					nbValues		= 0;
	private int					maxEntries		= DEFAULT_ENTRIES;
	private int[][]				all_values		= new int[1][maxEntries];

	private long				startTime		= -1;
	private int[]				ages			= new int[maxEntries];

	private int					currentPosition;

	private SimpleDateFormat	timeFormatter = new SimpleDateFormat("HH:mm", Locale.US );

	private Map<Integer,Integer>	timePositions = 
		new LinkedHashMap<Integer,Integer>(256,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
					Map.Entry<Integer,Integer> eldest)
			{
				return size() > 256;
			}
		};
		
	private SpeedGraphic(Scale scale,ValueFormater formater) {
		super(scale,formater);

		currentPosition = 0;

		COConfigurationManager.addAndFireParameterListener("Graphics Update",this);
	}

  @Override
  public void initialize(Canvas canvas) {
  	super.initialize(canvas);

  	drawCanvas.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				if (bufferImage != null && !bufferImage.isDisposed()) {
					Rectangle bounds = bufferImage.getBounds();
					if (bounds.width >= ( e.width + e.x ) && bounds.height >= ( e.height + e.y )) {

						e.gc.drawImage(bufferImage, e.x, e.y, e.width, e.height, e.x, e.y,
								e.width, e.height);
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
  }

  public static SpeedGraphic getInstance() {
    return new SpeedGraphic(new Scale(),new ValueFormater() {
      @Override
      public String format(int value) {
        return DisplayFormatters.formatByteCountToKiBEtcPerSec(value);
      }
    });
  }

  public static SpeedGraphic getInstance(ValueFormater formatter) {
	    return new SpeedGraphic(new Scale(),formatter);
  }

  public static SpeedGraphic getInstance(Scale scale, ValueFormater formatter) {
	    return new SpeedGraphic(scale,formatter);
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
			   	
			   	startTime = -1;
			   	
			  }finally{
				  
				this_mon.exit();
			  }
			  
			  refresh( true );
		  }
	  });
  }
  
  public void addIntsValue(int[] new_values) {
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

	    long now = SystemTime.getMonotonousTime();
	    
	    if ( startTime == -1 ){
	    	startTime = now;
	    }
	    ages[currentPosition] = (int)((now - startTime )/1000);
	    
	    currentPosition++;

	    if(nbValues < maxEntries){

	      nbValues++;
	    }

	    currentPosition %= maxEntries;


    }finally{

    	this_mon.exit();
    }
  }

  public void addIntValue(int value) {
	  addIntsValue( new int[]{ value });
  }

  @Override
  public void refresh(boolean force) {
    if(drawCanvas == null || drawCanvas.isDisposed())
      return;

    Rectangle bounds = drawCanvas.getClientArea();
    if(bounds.height < 30 || bounds.width  < 100 || bounds.width > 10000 || bounds.height > 10000)
      return;

    // inflate # of values only if necessary
    if(bounds.width > maxEntries)
    {
    	try {
    		this_mon.enter();

    		while(maxEntries < bounds.width)
    			maxEntries += 1000;

    		for(int i=0;i<all_values.length;i++)
    		{
    			int[] newValues = new int[maxEntries];
    			System.arraycopy(all_values[i], 0, newValues, 0, all_values[i].length);
    			all_values[i] = newValues;
    		}

    		int[] newAges = new int[maxEntries];
			System.arraycopy(ages, 0, newAges, 0, ages.length);
			ages = newAges;
    	} finally {
    		this_mon.exit();
    	}


    }


    boolean sizeChanged = (oldSize == null || oldSize.x != bounds.width || oldSize.y != bounds.height);
    oldSize = new Point(bounds.width,bounds.height);

    internalLoop++;
    if(internalLoop > graphicsUpdate)
      internalLoop = 0;


    if(internalLoop == 0 || sizeChanged || force ) {
	    drawChart(sizeChanged);

	    	// second drawing required to pick up and redraw the scale correctly
	    if ( force ){
	    	drawChart(true);
	    }
    }

    drawCanvas.redraw();
    drawCanvas.update();
  }

  protected void drawChart(boolean sizeChanged) {
		if (drawCanvas == null || drawCanvas.isDisposed() || !drawCanvas.isVisible()){
		
			return;
		}
		
		GC gcImage = null;
		try
		{
			this_mon.enter();

			drawScale(sizeChanged);

			if (bufferScale == null || bufferScale.isDisposed())
				return;

			Rectangle bounds = drawCanvas.getClientArea();
			if (bounds.isEmpty())
				return;


			//If bufferedImage is not null, dispose it
			if (bufferImage != null && !bufferImage.isDisposed())
				bufferImage.dispose();

			bufferImage = new Image(drawCanvas.getDisplay(), bounds);
			gcImage = new GC(bufferImage);
			gcImage.drawImage(bufferScale, 0, 0);

			gcImage.setAntialias( SWT.ON );

			int oldAverage = 0;
			int[] oldTargetValues = new int[all_values.length];

			int[] maxs = new int[all_values.length];

			for (int x = 0; x < bounds.width - 71; x++)
			{
				int position = currentPosition - x - 1;
				if (position < 0)
				{
					position += maxEntries;
					if (position < 0)
					{
						position = 0;
					}
				}
				for (int chartIdx = 0; chartIdx < all_values.length; chartIdx++)
				{
					int value = all_values[chartIdx][position];
					if (value > maxs[chartIdx])
					{
						maxs[chartIdx] = value;
					}
				}
			}

			int max = maxs[0];
			int max_primary = max;

			for (int i = 1; i < maxs.length; i++)
			{
				int m = maxs[i];
				if (i == 1)
				{
					if (max < m)
					{
						max = m;
						max_primary = max;
					}
				} else
				{
					// trim secondary indicators so we don't loose the more important info
					if (max < m)
					{
						if (m <= 2 * max_primary)
						{
							max = m;
						} else
						{
							max = 2 * max_primary;
							break;
						}
					}
				}
			}

			scale.setMax(max);

			long now		= SystemTime.getCurrentTime();
									
			int mono_secs = (int)((SystemTime.getMonotonousTime()-startTime)/1000);
			int next_secs = 60;
			int	timestamp_num = 0;
			
			int last_xpos = -1;
			
		    int lastAverage = -1;

			for (int x = 0; x < bounds.width - 71; x++){
			
				int position = currentPosition - x - 1;
				
				if (position < 0){
				
					position += maxEntries;
					
					if (position < 0){
					
						position = 0;
					}
				}
								
				int xDraw = bounds.width - 71 - x;
				int height = scale.getScaledValue(all_values[0][position]);

				int	this_age = ages[position];
				
				if ( this_age > 0 ){
					
					int offset = mono_secs - this_age;
					
					if ( offset >= next_secs ){
						
						next_secs += 60;
						
						long time = now - (offset*1000);
						
						String str = timeFormatter.format( new Date( time ));
												
						Point p = gcImage.stringExtent( str );
						
						int xPos = xDraw-p.x/2;
												
						if ( xPos >= 0 ){
						
							if ( last_xpos < 0 ||  xPos + p.x < last_xpos ){
							
									// there's an issue with time sync that can cause jittering in the
									// location of the timestamps
																		
								Integer old_pos = timePositions.get( timestamp_num );
								
								if ( old_pos != null && Math.abs( old_pos - xPos ) <= 2){
								
									xPos = old_pos;
								}
								
								gcImage.setForeground( colorGrey );

								gcImage.drawText( str, xPos, 0, true );
								
								last_xpos = xPos;
								
								timePositions.put( timestamp_num, xPos );
								
								timestamp_num++;
							}
						}
					}
				}

				
				gcImage.setForeground(colors[COLOR_MAINSPEED]);
				
				gcImage.drawLine( xDraw, bounds.height - 1 - height, xDraw,  bounds.height );
				
				if ( all_values.length > 1 ){
					
					gcImage.setForeground(colors[COLOR_OVERHEAD]);
					height = scale.getScaledValue(all_values[1][position]);
					gcImage.drawLine( xDraw, bounds.height - 1 - height, xDraw,  bounds.height );
				}
				
				for (int chartIdx = 2; chartIdx < all_values.length; chartIdx++)
				{
					int targetValue = all_values[chartIdx][position];
					int oldTargetValue = oldTargetValues[chartIdx];
					if (x > 1 && (chartIdx == 2 && (targetValue > 0 && oldTargetValue > 0) || (chartIdx > 2 && (targetValue > 0 || oldTargetValue > 0))))
					{
						int trimmed = 0;
						if (targetValue > max)
						{
							targetValue = max;
							trimmed++;
						}
						if (oldTargetValue > max)
						{
							oldTargetValue = max;
							trimmed++;
						}
						if (trimmed < 2 || trimmed == 2 && position % 3 == 0)
						{
							int h1 = bounds.height - scale.getScaledValue(targetValue) - 2;
							int h2 = bounds.height - scale.getScaledValue(oldTargetValue) - 2;
							gcImage.setForeground(chartIdx == 2 ? colors[COLOR_LIMIT] : (trimmed > 0 ? colors[COLOR_TRIMMED] : colors[COLOR_OTHERS]));
							gcImage.drawLine(xDraw, h1, xDraw + 1, h2);
						}
					}
					oldTargetValues[chartIdx] = all_values[chartIdx][position];
				}
				int average = computeAverage(position);
				if (x > 6)
				{
					int h1 = bounds.height - scale.getScaledValue(average) - 2;
					int h2 = bounds.height - scale.getScaledValue(oldAverage) - 2;
					gcImage.setForeground(colors[COLOR_AVERAGE]);
					gcImage.drawLine(xDraw, h1, xDraw + 1, h2);
					if ( lastAverage == -1 ){
						lastAverage = oldAverage;
					}
				}
				oldAverage = average;
			}

			if (lastAverage >= 0){
				int height = bounds.height - scale.getScaledValue(lastAverage) - 2;
				gcImage.setForeground(colors[COLOR_AVERAGE]);
				gcImage.drawText(formater.format(lastAverage), bounds.width - 65, height - 12, true);
			}

		} catch (Exception e)
		{
			Debug.out("Warning", e);
		} finally
		{
			if (gcImage != null)
			{
				gcImage.dispose();
			}
			this_mon.exit();
		}
	}

  protected int computeAverage(int position) {
    long sum = 0;
    for(int i = -5 ; i < 6 ; i++) {
      int pos = position + i;
      pos %= maxEntries;
      if (pos < 0)
        pos += maxEntries;
      sum += all_values[0][pos];
    }
    return(int)(sum / 11);

  }

  @Override
  public void parameterChanged(String parameter) {
    graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");
  }

  @Override
  public void dispose() {
    super.dispose();
    if(bufferImage != null && ! bufferImage.isDisposed()) {
      bufferImage.dispose();
    }
    COConfigurationManager.removeParameterListener("Graphics Update",this);
  }

	public void setLineColors(Color average, Color speed, Color overhead, Color limit, Color others, Color trimmed) {
		if (average != null) {
			colors[COLOR_AVERAGE] = average;
		}
		if (speed != null) {
			colors[COLOR_MAINSPEED] = speed;
		}
		if (overhead != null) {
			colors[COLOR_OVERHEAD] = overhead;
		}
		if (limit != null) {
			colors[COLOR_LIMIT] = limit;
		}
		if (others != null) {
			colors[COLOR_OTHERS] = others;
		}
		if (trimmed != null) {
			colors[COLOR_TRIMMED] = trimmed;
		}
		if (drawCanvas != null && !drawCanvas.isDisposed()) {
			drawCanvas.redraw();
		}
	}

	public void setLineColors(Color[] newChangeableColorSet) {
		colors = newChangeableColorSet;
		if (drawCanvas != null && !drawCanvas.isDisposed()) {
			drawCanvas.redraw();
		}
	}
}
