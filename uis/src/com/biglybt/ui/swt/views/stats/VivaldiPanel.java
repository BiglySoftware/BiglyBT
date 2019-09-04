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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.core.dht.control.DHTControlContact;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.vivaldi.ver1.*;
import com.biglybt.core.dht.netcoords.vivaldi.ver1.impl.*;
import com.biglybt.ui.swt.utils.ColorCache;

public class VivaldiPanel {
  private static final int ALPHA_FOCUS = 255;
  private static final int ALPHA_NOFOCUS = 150;

  Display display;
  Composite parent;

  Canvas canvas;
  Scale scale;

  private boolean mouseLeftDown = false;
  private boolean mouseRightDown = false;
  private int xDown;
  private int yDown;

  private boolean  	disableAutoScale 	= false;
  private long		lastAutoScale		= 0;

  private boolean antiAliasingAvailable = true;
	private List<DHTControlContact> lastContacts;
	private DHTTransportContact lastSelf;

	private Image img;

  private int alpha = 255;

  private boolean autoAlpha = false;

  private List<Object[]>	currentPositions = new ArrayList<>();

  private class Scale {
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

    public
    Scale()
    {
    	reset();
    }

    public int getX(float x,float y) {
      return (int) (((x * Math.cos(rotation) + y * Math.sin(rotation))-minX)/(maxX - minX) * width);
    }

    public int getY(float x,float y) {
      return (int) (((y * Math.cos(rotation) - x * Math.sin(rotation))-minY)/(maxY-minY) * height);
    }

    public void
    reset()
    {
    	minX = -1000;
        maxX = 1000;
        minY = -1000;
        maxY = 1000;
        rotation = 0;

        disableAutoScale 	= false;
        lastAutoScale		= 0;
     }
  }

  public VivaldiPanel(Composite parent) {
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
					e.gc.drawText(MessageText.getString("VivaldiView.notAvailable"), 10,
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
        refreshContacts(lastContacts, lastSelf);
      }

      @Override
    	public void mouseDoubleClick(MouseEvent e) {
    		scale.reset();
    	}
    });

    canvas.addMouseTrackListener(new MouseTrackAdapter() {
    	@Override
    	public void mouseHover(MouseEvent e) {
    		int x = e.x;
    		int	y = e.y;

    		DHTControlContact closest = null;

    		int		closest_distance = Integer.MAX_VALUE;
    		int		x_coord		 	= 0;
    		int		y_coord			= 0;
    		float	height			= -1;

    		for ( Object[] entry: currentPositions ){

       			int		e_x = (Integer)entry[0];
     			int		e_y = (Integer)entry[1];

       			long	x_diff = x - e_x;
       			long	y_diff = y - e_y;

       			int distance = (int)Math.sqrt( x_diff*x_diff + y_diff*y_diff );

       			if ( distance < closest_distance ){

       				closest_distance 	= distance;
       				x_coord				= e_x;
       				y_coord				= e_y;
       				height				= (Float)entry[2];
       				closest				= (DHTControlContact)entry[3];
       			}
    		}

    		if ( closest_distance <= 25 ){

    			InetAddress address = closest.getTransportContact().getTransportAddress().getAddress();

    			String[] details = PeerUtils.getCountryDetails( address );

    			String tt = address.getHostAddress();

    			if ( details != null ){

    				tt += ": " + details[0] + "/" + details[1];
    			}

    			tt += " (x="+ (((int)(x_coord*10000))/10000.0f) +
    					",y=" + (((int)(y_coord*10000))/10000.0f) + 
    					",h=" + (((int)(height*10000))/10000.0f) + 
    					")";

    			Utils.setTT(canvas, tt );

    		}else{

    			Utils.setTT(canvas, "Use mouse wheel to scale, left+drag to move, right+drag to rotate" );
    		}
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

        int deltaY = event.count * -5;
        // scaleFactor>1 means zoom in, this happens when
        // deltaY<0 which happens when the mouse is moved up.
        float scaleFactor = 1 - (float) deltaY / 300;
        if(scaleFactor <= 0) scaleFactor = 0.01f;

        // Scalefactor of e.g. 3 makes elements 3 times larger
        float moveFactor = 1 - 1/scaleFactor;

				Canvas canvas = ((Canvas) event.widget);
				Point canvasSize = canvas.getSize();
				// event.x, event.y are relative to control
				float mouseXpct = (event.x + 1) / (float) canvasSize.x;
				float mouseYpct = (event.y + 1) / (float) canvasSize.y;
				float xOfs = (mouseXpct - 0.5f) * (scale.saveMaxX - scale.saveMinX);
				float yOfs = (mouseYpct - 0.5f) * (scale.saveMaxY - scale.saveMinY);

				float centerX = ((scale.saveMinX + scale.saveMaxX)/2) + xOfs;
        scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
        scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

        float centerY = (scale.saveMinY + scale.saveMaxY)/2 + yOfs;
        scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
        scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);

        disableAutoScale = true;
        refreshContacts(lastContacts, lastSelf);
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
          disableAutoScale = true;
          refreshContacts(lastContacts, lastSelf);
        }
        if(mouseRightDown || (mouseLeftDown && (event.stateMask & SWT.MOD4) > 0)) {
          int deltaX = event.x - xDown;
	        int deltaY = event.y - yDown;
	        int diffX = Math.abs(deltaX);
	        int diffY = Math.abs(deltaY);
	        // Don't start rotating until a few px movement.  Helps when
	        // user just wants to zoom (move up/down) or rotate (move left/right) 
	        // and doesn't have steady hand
          if (diffY > diffX && diffX <= 3) {
          	deltaX = 0;
          }
	        if (diffY > diffX && diffY <= 3) {
		        deltaY = 0;
	        }
          scale.rotation = scale.saveRotation - (float) deltaX / 100;

          // scaleFactor>1 means zoom in, this happens when
          // deltaY<0 which happens when the mouse is moved up.
          float scaleFactor = 1 - (float) deltaY / 300;
          if(scaleFactor <= 0) scaleFactor = 0.01f;

          // Scalefactor of e.g. 3 makes elements 3 times larger
          float moveFactor = 1 - 1/scaleFactor;

	        Canvas canvas = ((Canvas) event.widget);
	        Point canvasSize = canvas.getSize();
	        // event.x, event.y are relative to control
	        float mouseXpct = (xDown + 1) / (float) canvasSize.x;
	        float mouseYpct = (yDown + 1) / (float) canvasSize.y;
	        float xOfs = (mouseXpct - 0.5f) * (scale.saveMaxX - scale.saveMinX);
	        float yOfs = (mouseYpct - 0.5f) * (scale.saveMaxY - scale.saveMinY);

	        float centerX = (scale.saveMinX + scale.saveMaxX)/2 + xOfs;
          scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
          scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

          float centerY = (scale.saveMinY + scale.saveMaxY)/2 + yOfs;
          scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
          scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
          disableAutoScale = true;
          refreshContacts(lastContacts, lastSelf);
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
  }

  public void setLayoutData(Object data) {
    canvas.setLayoutData(data);
  }

	private boolean isRefreshQueued = false;
	public void refreshContacts(
		List<DHTControlContact> contacts,
		DHTTransportContact self) {
		if (contacts == null || self == null) {
			return;
		}
		// always called in SWT Thread, so lastXxx won't be changing mid-build
		lastContacts = contacts;
		lastSelf = self;

		if (isRefreshQueued) {
			return;
		}
		isRefreshQueued = true;
		Utils.execSWTThreadLater(20, () -> {
			isRefreshQueued = false;
			_refreshContacts();
		});
	}

	public void
	_refreshContacts()
	{
	  	if (lastContacts == null || lastSelf == null) {
	  		return;
	  	}

	    if(canvas.isDisposed()) return;
			Point canvasSize = canvas.getSize();
			Rectangle size = new Rectangle(0, 0, canvasSize.x, canvasSize.y);

	    if (size.isEmpty()) {
	    	return;
	    }

	    scale.width = size.width;
	    scale.height = size.height;

	    Color white = ColorCache.getColor(display,255,255,255);
	    Color blue = ColorCache.getColor(display,66,87,104);

			boolean needNewImage = img == null || img.isDisposed();
			if (!needNewImage) {
				Rectangle bounds = img.getBounds();
				needNewImage = bounds.width != size.width || bounds.height != size.height;
			}
			if (needNewImage) {
				img = new Image(display,size);
			}

	    GC gc = new GC(img);

	    gc.setForeground(white);
	    gc.setBackground(white);

	    gc.fillRectangle(size);

	    if(SWT.getVersion() >= 3138 && antiAliasingAvailable) {
	    	try {
	    		//gc.setTextAntialias(SWT.ON);
	    		//gc.setAntialias(SWT.ON);
	      } catch(Exception e) {
	        antiAliasingAvailable = false;
	      }
	    }


	    gc.setForeground(blue);
	    gc.setBackground(white);

	    DHTNetworkPosition _ownPosition = lastSelf.getNetworkPosition(DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1);

	    if ( _ownPosition == null ){

	    	gc.dispose();

	    	return;
	    }

	    currentPositions.clear();

	    VivaldiPosition ownPosition = (VivaldiPosition)_ownPosition;
	    float ownErrorEstimate = ownPosition.getErrorEstimate();
	    HeightCoordinatesImpl ownCoords =
	    	(HeightCoordinatesImpl) ownPosition.getCoordinates();

	    gc.drawText("Our error: " + ownErrorEstimate,10,10);

	    Color black = ColorCache.getColor(display, 0, 0, 0);
	    gc.setBackground(black); // Color of the squares

	    // Draw all known positions of other contacts

	    long	total_distance 	= 0;

	    for (DHTControlContact contact : lastContacts) {
	      DHTNetworkPosition _position = contact.getTransportContact().getNetworkPosition(DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1);
	      if ( _position == null ){
	    	  continue;
	      }
	      VivaldiPosition position = (VivaldiPosition)_position;
	      HeightCoordinatesImpl coord = (HeightCoordinatesImpl) position.getCoordinates();
	      if(coord.isValid()) {
	    	int distance = (int)ownCoords.distance(coord);
	    	total_distance += distance;

	        draw(gc,coord.getX(),coord.getY(),coord.getH(),contact,distance,position.getErrorEstimate());
	      }
	    }

	    // Mark our own position
	    Color red = ColorCache.getColor(display, 255, 0, 0);
			gc.setForeground(red);
	    drawSelf(gc, ownCoords.getX(), ownCoords.getY(),
							 ownCoords.getH(), ownErrorEstimate);


	    gc.dispose();

	    boolean	skip_redraw = false;

	    if ( !disableAutoScale ){

	    	int num_pos = currentPositions.size();

	    	if ( num_pos > 0 ){

	        	long now = SystemTime.getMonotonousTime();

	        	if ( now - lastAutoScale >= 5*1000 ){

	        		lastAutoScale = now;

		    		float	min_x = Float.MAX_VALUE;
		    		float	min_y = Float.MAX_VALUE;
		    		float	max_x = Float.MIN_VALUE;
		    		float	max_y = Float.MIN_VALUE;

		    		int average_distance = (int)( total_distance/num_pos );

					for ( Object[] entry: currentPositions ){

						if ( num_pos > 25 ){

							int	distance = (Integer)entry[6];

							if ( distance >= average_distance * 4 ){

								continue;
							}
						}

			   			float	x = (Float)entry[4];
			   			float	y = (Float)entry[5];

			 			min_x = Math.min(min_x,x );
			 			min_y = Math.min(min_y,y );
			 			max_x = Math.max(max_x,x );
			 			max_y = Math.max(max_y,y );
					}

					float new_min_x = min_x - 50;
					float new_max_x = max_x + 50;
					float new_min_y = min_y - 50;
					float new_max_y = max_y + 50;

					if ( 	scale.minX != new_min_x ||
							scale.maxX != new_max_x ||
							scale.minY != new_min_y ||
							scale.maxY != new_max_y ){

						scale.minX = new_min_x;
						scale.maxX = new_max_x;
						scale.minY = new_min_y;
						scale.maxY = new_max_y;
					}

					//System.out.println(scale.minX+","+ scale.maxX+","+scale.minY+","+scale.maxY+" -> " + new_min_x + "," +new_max_x+","+new_min_y+","+new_max_y);

					_refreshContacts();

					skip_redraw = true;
	        	}
	    	}
	    }

	    if ( !skip_redraw ){

	    	canvas.redraw();
	    }
  }

  public void refresh(List<VivaldiPosition> vivaldiPositions) {
    if(canvas.isDisposed()) return;
    Rectangle size = canvas.getBounds();

    scale.width = size.width;
    scale.height = size.height;

    if (img != null && !img.isDisposed()) {
    	img.dispose();
    }

    img = new Image(display,size);
    GC gc = new GC(img);

    Color white = ColorCache.getColor(display,255,255,255);
    gc.setForeground(white);
    gc.setBackground(white);
    gc.fillRectangle(size);

    Color blue = ColorCache.getColor(display,66,87,104);
    gc.setForeground(blue);
    gc.setBackground(blue);



    for (VivaldiPosition position : vivaldiPositions) {
      HeightCoordinatesImpl coord = (HeightCoordinatesImpl) position.getCoordinates();

      float error = position.getErrorEstimate() - VivaldiPosition.ERROR_MIN;
      if(error < 0) error = 0;
      if(error > 1) error = 1;
      int blueComponent = (int) (255 - error * 255);
      int redComponent = (int) (255*error);
      // Don't use ColorCache, as our color creation is temporary and
      // varying
      Color drawColor = new Color(display,redComponent,50,blueComponent);
      gc.setForeground(drawColor);
      draw(gc,coord.getX(),coord.getY(),coord.getH());
      drawColor.dispose();
    }

    gc.dispose();

    canvas.redraw();
  }

  private void draw(GC gc,float x,float y,float h) {
    int x0 = scale.getX(x,y);
    int y0 = scale.getY(x,y);
    gc.fillRectangle(x0-1,y0-1,3,3);
    gc.drawLine(x0,y0,x0,(int)(y0-200*h/(scale.maxY-scale.minY)));
  }

  private void draw(GC gc,float x,float y,float h,DHTControlContact contact,int distance,float error) {
    if(x == 0 && y == 0) return;
    if(error > 1) error = 1;
    int errDisplay = (int) (100 * error);
    int x0 = scale.getX(x,y);
    int y0 = scale.getY(x,y);

    Image img = ImageRepository.getCountryFlag( contact.getTransportContact().getTransportAddress().getAddress(), true );

    if ( img != null ){
    	Rectangle bounds = img.getBounds();
    	int old = gc.getAlpha();
    	gc.setAlpha( 150 );
    	gc.drawImage( img, x0-bounds.width/2, y0-bounds.height);
    	gc.setAlpha( old );
    }

    gc.fillRectangle(x0-1,y0-1,3,3);
    //int elevation =(int) ( 200*h/(scale.maxY-scale.minY));
    //gc.drawLine(x0,y0,x0,y0-elevation);

    //String text = /*contact.getTransportContact().getAddress().getAddress().getHostAddress() + " (" + */distance + " ms \nerr:"+errDisplay+"%";
    String text = /*contact.getTransportContact().getAddress().getAddress().getHostAddress() + " (" + */distance + " ms "+errDisplay+"%";

    int lineReturn = text.indexOf("\n");
    int xOffset = gc.getFontMetrics().getAverageCharWidth() * (lineReturn != -1 ? lineReturn:text.length()) / 2;
    gc.drawText(text,x0-xOffset,y0,true);

    currentPositions.add( new Object[]{ x0, y0, h, contact, x, y, distance });
  }

  // Mark our own position
  private void drawSelf(GC gc, float x, float y, float h, float errorEstimate){
  	int x0 = scale.getX(x, y);
		int y0 = scale.getY(x, y);
		//gc.drawOval(x0-50, y0-50, 100, 100);
		gc.drawLine(x0-15, y0, x0+15, y0); // Horizontal
		gc.drawLine(x0, y0-15, x0, y0+15); // Vertical
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
}
