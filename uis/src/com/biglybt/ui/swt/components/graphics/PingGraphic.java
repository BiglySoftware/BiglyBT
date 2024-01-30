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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.ui.swt.mainwindow.Colors;

/**
 * @author Olivier
 *
 */
public class PingGraphic extends ScaledGraphic implements ParameterListener {

  private static final int	ENTRIES	= 2000;

  private static final int COLOR_AVERAGE = 0;

  public static Color[] defaultColors = new Color[] {
  	Colors.grey,Colors.blues[Colors.BLUES_MIDDARK], Colors.fadedGreen,Colors.fadedRed };

  private int internalLoop;
  private int graphicsUpdate;
  private Point oldSize;

  protected Image bufferImage;

  private int nbValues = 0;

  private int[][] all_values	= new int[1][ENTRIES];
  private int currentPosition;

  private boolean externalAverage;		// if true then average is in position [0]

  private  Color[]	colors = defaultColors;

  private PingGraphic(Scale scale,ValueFormater formater) {
    super(scale,formater);

    currentPosition = 0;

    COConfigurationManager.addParameterListener("Graphics Update",this);
    parameterChanged("Graphics Update");
  }

  public static PingGraphic getInstance() {
    return new PingGraphic(new Scale( false ),new ValueFormater() {
      @Override
      public String format(int value) {
        return value + TimeFormatter.MS_SUFFIX;
      }
    });
  }

  public void
  setColors(
	Color[]		_colors )
  {
	  colors = _colors;
  }

  public void
  setExternalAverage(
		boolean		b )
  {
	externalAverage = b;
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
  
  public void addIntsValue(int[] new_values) {
    try{
    	this_mon.enter();

    	if ( all_values.length < new_values.length ){

    		int[][]	new_all_values = new int[new_values.length][];

		    System.arraycopy(all_values, 0, new_all_values, 0, all_values.length);

    		for (int i=all_values.length;i<new_all_values.length; i++ ){

    			new_all_values[i] = new int[ENTRIES];
    		}

    		all_values = new_all_values;
    	}

	    for (int i=0;i<new_values.length;i++){

	        all_values[i][currentPosition] = new_values[i];
    	}

	    currentPosition++;

	    if(nbValues < ENTRIES){

	      nbValues++;
	    }

	    if(currentPosition >= ENTRIES){

	      currentPosition = 0;
	    }

    }finally{

    	this_mon.exit();
    }
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
  
  @Override
  public void
  refresh( boolean force )
  {
	  refresh();
  }

  public void refresh() {
    if(drawCanvas == null || drawCanvas.isDisposed()){
      return;
		}

    Rectangle bounds = drawCanvas.getClientArea();
    if(bounds.height < 30 || bounds.width  < 100 || bounds.width > 2000 || bounds.height > 2000)
      return;

    boolean sizeChanged = (oldSize == null || oldSize.x != bounds.width || oldSize.y != bounds.height);
    oldSize = new Point(bounds.width,bounds.height);

    internalLoop++;
    if(internalLoop > graphicsUpdate)
      internalLoop = 0;


    if(internalLoop == 0 || sizeChanged) {
	    drawChart(sizeChanged);
    }

    drawCanvas.redraw();
    drawCanvas.update();
  }

  protected void drawChart(boolean sizeChanged) {
	if (drawCanvas == null || drawCanvas.isDisposed() || !drawCanvas.isVisible()){
		
		return;
	}
	  
   try{
   	  this_mon.enter();

   	  // should create bufferscale
      drawScale(sizeChanged);

    	if (bufferScale == null || bufferScale.isDisposed()) {
    		return;
    	}

      Rectangle bounds = drawCanvas.getClientArea();

      //If bufferedImage is not null, dispose it
      if(bufferImage != null && ! bufferImage.isDisposed()){
        bufferImage.dispose();
      }
      
      if ( bounds.isEmpty()){
    	  bufferImage = null;
    	  return;
      }
      
      bufferImage = new Image(drawCanvas.getDisplay(),bounds);

      GC gcImage = new GC(bufferImage);

      gcImage.drawImage(bufferScale,0,0);

      gcImage.setAntialias( SWT.ON );

      int oldAverage = 0;
      int[] oldTargetValues = new int[all_values.length];
      int[] maxs = new int[all_values.length];
      for(int x = 0 ; x < bounds.width - 71 ; x++) {
        int position = currentPosition - x -1;
        if(position < 0)
          position+= 2000;
        for (int z=0;z<all_values.length;z++){
        	int value = all_values[z][position];
        	if(value > maxs[z]){
        		maxs[z] = value;
        	}
        }
      }
      int	max = 0;
      for (int i=0;i<maxs.length;i++){
    	  if(maxs[i] > max) {
    	    max = maxs[i];
        }
      }

      scale.setMax(max);

      int lastAverage = -1;
      for(int x = 0 ; x < bounds.width - 71 ; x++) {
        int position = currentPosition - x -1;
        if(position < 0)
          position+= 2000;

        int xDraw = bounds.width - 71 - x;
        gcImage.setLineWidth(1);
        for (int z=externalAverage?1:0;z<all_values.length;z++){
	        int targetValue 	= all_values[z][position];
	        int oldTargetValue 	= oldTargetValues[z];

	        if ( x > 1 ){
		        int h1 = bounds.height - scale.getScaledValue(targetValue) - 2;
		        int h2 = bounds.height - scale.getScaledValue(oldTargetValue) - 2;
		        gcImage.setForeground( externalAverage?colors[z]: (z <= 2 ? colors[z+1] : colors[3]));
	            gcImage.drawLine(xDraw,h1,xDraw+1, h2);
	        }

	        oldTargetValues[z] = all_values[z][position];
        }

        int average = computeAverage(position);
        if(x > 6) {
          int h1 = bounds.height - scale.getScaledValue(average) - 2;
          int h2 = bounds.height - scale.getScaledValue(oldAverage) - 2;
          gcImage.setForeground(colors[COLOR_AVERAGE]);
          gcImage.setLineWidth(2);
          gcImage.drawLine(xDraw,h1,xDraw+1, h2);
          if ( lastAverage == -1 ){
         	  lastAverage = oldAverage;
          }
        }
        oldAverage = average;
      }

      if(lastAverage >= 0) {
        int height = bounds.height - scale.getScaledValue(lastAverage) - 2;
        gcImage.setForeground(colors[COLOR_AVERAGE]);
        gcImage.drawText(formater.format(lastAverage),bounds.width - 65,height - 12,true);
      }

      gcImage.dispose();

    }finally{

    	this_mon.exit();
    }
  }

  protected int computeAverage(int position) {
    int sum = 0;
    int nbItems = 0;
    for(int i = -5 ; i < 6 ; i++) {
      int pos = position + i;
      if (pos < 0)
        pos += 2000;
      if(pos >= 2000)
        pos -= 2000;
      for(int z=0 ; z < (externalAverage?1:all_values.length) ; z++) {
        sum += all_values[z][pos];
        nbItems++;
      }
    }
    return (sum / nbItems);

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
}
