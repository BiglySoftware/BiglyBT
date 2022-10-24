/*
 * Created on 12 May 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.ui.swt.minibar;

import java.util.Iterator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.DoubleBufferedLabel;
import com.biglybt.ui.swt.components.shell.ShellFactory.AEShell;
import com.biglybt.ui.swt.mainwindow.Colors;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author Allan Crooks
 *
 */
public abstract class MiniBar implements MenuBuildUtils.MenuBuilder {

	protected final Color bg_colour;
	protected final Color fg_colour;
	protected final Color fg_text_colour;
	
	{
		if ( Utils.isDarkAppearanceNative() || Utils.isDarkAppearancePartial()){
		
			fg_text_colour 	= Colors.white;
			fg_colour 		= Colors.blues[Colors.BLUES_MIDDARK];
			bg_colour 		= Colors.black;
			
		}else{
			
			fg_text_colour	= null;
			fg_colour 		= Colors.blues[Colors.BLUES_DARKEST];
			bg_colour 		= Colors.blues[Colors.BLUES_LIGHTEST];
		}
	}
	//
	// These are components which are related to the bar itself.
	//
	protected Label lDrag;
	protected MiniBar stucked;
	protected Monitor[] screens;
	protected int xPressed, yPressed;
	protected boolean moving;
	protected int hSize;
	protected Shell splash;
	protected MiniBarManager manager;

	private Font bold_font = null;

	//
	// These are components used during the construction of the bar.
	//
	private boolean constructing = false;
	private boolean constructed = false;
	private Menu menu;
	private MouseMoveListener mMoveListener;
	private MouseListener mListener;
	private int xSize;
	private boolean separateDataProt;

	private float width_multiplier = 1.0f;

	protected MiniBar(MiniBarManager manager) {
		this.manager = manager;
		setPrebuildValues();
		this.separateDataProt = DisplayFormatters.isDataProtSeparate();
	}

	private void setPrebuildValues() {
		this.constructing = false;
		this.constructed = false;
		this.xSize = 0;
		this.hSize = -1;
	}

	//
	// These methods provide a construction API for subclasses.
	//
	private void assertConstructing() {
		if (!this.constructing)
			throw new RuntimeException("not constructing!");
	}

	private Font createBoldFont(Font original) {
		FontData[] font_data = original.getFontData();
		for (int i=0; i<font_data.length; i++) {
			font_data[i].setStyle(font_data[i].getStyle() | SWT.BOLD);
		}
		return new Font(original.getDevice(), font_data);
	}

	protected final void createGap(int width) {
		width = (int)(width * width_multiplier );
		// We create a label just so we can attach the menu to it.
		assertConstructing();
		Label result = new Label(splash, SWT.NONE);
	    result.setBackground(bg_colour);
	    result.setForeground(fg_colour);
	    result.setText("");
	    result.addMouseListener(this.mListener);
	    result.addMouseMoveListener(this.mMoveListener);
	    result.setLocation(this.xSize, 0);
	    result.setSize(width, hSize);
	    result.setMenu(this.menu);
	    this.xSize += width;
	}

	protected final Label createFixedTextLabel(String msg_key, boolean add_colon, boolean bold) {
		assertConstructing();
	    Label result = new Label(splash, SWT.NONE);
	    result.setBackground(bg_colour);
	    result.setForeground(fg_colour);
	    result.setText(MessageText.getString(msg_key) + ((add_colon) ? ":" : ""));
	    if (bold) {
	    	if (this.bold_font == null) {
	    		this.bold_font = createBoldFont(result.getFont());
	    	}
	    	result.setFont(this.bold_font);
	    }
	    result.addMouseListener(this.mListener);
	    result.addMouseMoveListener(this.mMoveListener);
	    result.pack();
	    result.setLocation(this.xSize, 0);
	    result.setMenu(this.menu);
	    if (this.hSize == -1) {
	        int hSizeText = result.getSize().y;
	        int hSizeImage = this.lDrag.getSize().y;
	        this.hSize = hSizeText > hSizeImage ? hSizeText : hSizeImage;
	    }
	    this.xSize += result.getSize().x + 3;

	    return( result );
	}

	protected final Label createFixedLabel(int width) {
		assertConstructing();
	    Label result = new Label(splash, SWT.NONE);
	    result.setBackground(bg_colour);
	    result.setSize(width, SWT.DEFAULT );
	    result.setLocation(this.xSize, 0);
	    result.addMouseListener(this.mListener);
	    result.addMouseMoveListener(this.mMoveListener);
	    result.setMenu(this.menu);
	    if (this.hSize == -1) {
	        int hSizeText = result.getSize().y;
	        int hSizeImage = this.lDrag.getSize().y;
	        this.hSize = hSizeText > hSizeImage ? hSizeText : hSizeImage;
	    }
	    this.xSize += width;

	    return( result );
	}

	protected final DoubleBufferedLabel createDataLabel(int width, boolean centered) {
		width = (int)(width * width_multiplier );
		assertConstructing();
		DoubleBufferedLabel result = new DoubleBufferedLabel(splash, (centered ? SWT.CENTER : SWT.NULL) | SWT.DOUBLE_BUFFERED );
	    result.setBackground(bg_colour);
	    result.setForeground(fg_text_colour);
	    result.setText("");
	    result.addMouseListener(this.mListener);
	    result.addMouseMoveListener(this.mMoveListener);
	    if (this.hSize == -1) {
	    	throw new RuntimeException("must add fixed text label first!");
	    }
	    result.setSize(width, hSize);
	    result.setLocation(this.xSize, 0);
	    result.setMenu(this.menu);
	    this.xSize += width + 3;
	    return result;
	}

	protected final DoubleBufferedLabel createDataLabel(int width) {
		return createDataLabel(width, false);
	}

	protected final DoubleBufferedLabel createSpeedLabel() {
		return createDataLabel(separateDataProt ? 110 : 65, separateDataProt);
	}

	protected void updateSpeedLabel(DoubleBufferedLabel label, long data_rate, long protocol_rate) {
		if (separateDataProt) {
			label.setText(DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(data_rate, protocol_rate));
		}
		else {
			label.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(data_rate+protocol_rate));
		}
	}

	protected final ProgressBar createProgressBar(int min, int max, int width, final ProgressBarText pbt) {
		width = (int)(width * width_multiplier );
		final ProgressBar result = new ProgressBar(splash, SWT.SMOOTH);
		result.setBackground(bg_colour);
		result.setForeground(Colors.blues[Colors.BLUES_MIDLIGHT]);
		result.setMinimum(min);
		result.setMaximum(max);
		result.addMouseListener(this.mListener);
		result.addMouseMoveListener(this.mMoveListener);
	    if (this.hSize == -1) {
	    	throw new RuntimeException("must add fixed text label first!");
	    }
		result.setSize(width, hSize);
		result.setLocation(this.xSize, 0);
		result.setMenu(this.menu);
		this.xSize += width + 3;

		// Add a listener to display text on the progress bar.
		if (pbt != null) {
			result.addListener(SWT.Paint, new Listener() {
	    		@Override
			    public void handleEvent(Event event) {
	    			Color old = event.gc.getForeground();
	    			event.gc.setForeground(Colors.black);
	    			int	char_width = event.gc.getFontMetrics().getAverageCharWidth();
	    			String pb_text = pbt.convert(result.getSelection());
	    			event.gc.drawText(pb_text, (result.getSize().x - pb_text.length() * char_width )/2, -1, true);
	    			event.gc.setForeground(old);
	    		}
			});
		}

		return result;
	}

	protected final ProgressBar createPercentProgressBar(int width) {
		return createProgressBar(0, 1000, width, new ProgressBarText() {
			@Override
			public String convert(int value) {
				return DisplayFormatters.formatPercentFromThousands(value);
			}
		});
	}

	protected static interface ProgressBarText {
		public String convert(int value);
	}


	//
	// These methods define the main MiniBar behaviour.
	//

	public final void construct(final Shell main) {
		if (this.constructed) {
			throw new RuntimeException("already constructed!");
		}

		this.constructing = true;

		this.stucked = null;
		this.splash = com.biglybt.ui.swt.components.shell.ShellFactory
				.createShell(SWT.ON_TOP);
		((AEShell) splash).setAdjustPXforDPI(false);

		int	trans = COConfigurationManager.getIntParameter( "Bar Transparency" );

		if ( trans > 0 && trans <= 100 ){

			int alpha = (int)((255*(100.0f-trans))/100);

			splash.setAlpha( alpha );
		}
		
		manager.register(this);
		final DisposeListener mainDisposeListener;
		main.addDisposeListener(mainDisposeListener = new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent event) {
				close();
			}
		});

		// cleanup dangling references
		splash.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (main.isDisposed()) return;
				main.removeDisposeListener(mainDisposeListener);
			}
		});


	    screens = main.getDisplay().getMonitors();
	    build();
	}

	private void build() {



	    lDrag = new Label(splash, SWT.NULL);

	    int	testTextHeight = 0;
	    try{
	    	GC gc = new GC(lDrag);
	    	gc.setFont(splash.getDisplay().getSystemFont());
	    	Point textSize = gc.textExtent( "BiglyBT Rocks innit: 100 MB/sec");
	    	int textWidth 	= textSize.x;
	    	testTextHeight 	= textSize.y;

	    	if ( textWidth > 139 ){	// 139 is what parg gets normally on Windows
	    		width_multiplier = textWidth/139.0f;
	    	}
	    	gc.dispose();
	    }catch( Throwable e ){
	    	Debug.out( e);
	    }
	    if(!Constants.isOSX) {
	      lDrag.setImage(ImageLoader.getInstance().getImage("dragger"));
	      lDrag.addDisposeListener(new DisposeListener() {
					@Override
					public void widgetDisposed(DisposeEvent e) {
			      ImageLoader.getInstance().releaseImage("dragger");
					}
				});
	    }
	    lDrag.pack();

	    int	yPad = 0;
	    Point lDragSize = lDrag.getSize();
	    this.xSize = lDragSize.x + 3;
	    if ( lDragSize.y < testTextHeight ){
	    	yPad = ( testTextHeight - lDragSize.y )/2;
	    }
	    lDrag.setLocation(0, yPad);

	    this.mListener = new MouseAdapter() {
	      int old_alpha;

	      @Override
	      public void mouseDown(MouseEvent e) {
	        xPressed = e.x;
	        yPressed = e.y;
	        moving = true;
	        old_alpha = splash.getAlpha();
	        if ( old_alpha != 255 ){
	        	splash.setAlpha( 255 );
	        }
	        //System.out.println("Position : " + xPressed + " , " + yPressed);
	      }

	      @Override
	      public void mouseUp(MouseEvent e) {
	        moving = false;
	        if ( old_alpha != 255 ){
	        	splash.setAlpha( old_alpha );
	        }
	      }

	      @Override
	      public void mouseDoubleClick(MouseEvent e) {
	    	 doubleClick();
	      }

	    };
	    this.mMoveListener = new MouseMoveListener() {
	      @Override
	      public void mouseMove(MouseEvent e) {
	        if (moving) {
	          int dX = xPressed - e.x;
	          int dY = yPressed - e.y;
	          //System.out.println("dX,dY : " + dX + " , " + dY);
	          Point currentLoc = splash.getLocation();
	          currentLoc.x -= dX;
	          currentLoc.y -= dY;
	          setSnapLocation(currentLoc);
	          //System.out.println("Position : " + xPressed + " , " + yPressed);
	        }
	      }
	    };

		splash.setBackground( bg_colour );
	    splash.setForeground( fg_colour );
	    splash.addMouseListener(mListener);
	    splash.addMouseMoveListener(mMoveListener);
	    lDrag.addMouseListener(mListener);
	    lDrag.addMouseMoveListener(mMoveListener);

	    this.menu = new Menu(splash, SWT.POP_UP);
	    MenuBuildUtils.addMaintenanceListenerForMenu(menu, this);
	    this.beginConstruction();

	    splash.addListener(SWT.Deiconify, new Listener() {
	        @Override
	        public void handleEvent(Event e) {
	          splash.setVisible(true);
	          splash.setActive();
	        }
	      });
	      splash.setSize(xSize + 3, hSize + 2);

	    // Tidy up construction variables.
	    this.mListener = null;
	    this.mMoveListener = null;
	    this.menu = null;

	    // Avoid doing a refresh on construction.
	    this.refresh();
	    this.constructing = false;
	    this.constructed = true;

	    // Allow subclasses to determine the initial position of the splash object.
	    Point point = this.getInitialLocation();
	    if (point == null) {
	    	Rectangle clientArea = splash.getMonitor().getClientArea();
	    	point = new Point(clientArea.x, clientArea.y);
	    }
	    if (point != null) {splash.setLocation(point);}

	    Utils.verifyShellRect(splash, true);

	    splash.setVisible(true);

	}

	public void buildMenu(Menu menu) {
		com.biglybt.pif.ui.menus.MenuItem[] menu_items;
		Object[] plugin_context_objs = this.getPluginMenuContextObjects();
		String[] plugin_menu_ids = this.getPluginMenuIdentifiers(plugin_context_objs);
		if (plugin_menu_ids != null) {
			menu_items = MenuItemManager.getInstance().getAllAsArray(plugin_menu_ids);
			if (menu_items.length > 0) {
				MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
						// This will retrieve the plugin download object for associated menus.
						new MenuBuildUtils.MenuItemPluginMenuControllerImpl(plugin_context_objs)
				);
				new MenuItem(menu, SWT.SEPARATOR);
			}
		}

	    MenuItem itemClose = new MenuItem(menu,SWT.NULL);
	    itemClose.setText(MessageText.getString("wizard.close"));
	    itemClose.addListener(SWT.Selection,new Listener() {
	    	@Override
		    public void handleEvent(Event e) {
	    		close();
	    	}
	    });
	}

	protected final void setSnapLocation(Point currentLoc) {

		Rectangle dim = new Rectangle(currentLoc.x,currentLoc.y,splash.getBounds().width,splash.getBounds().height);

		// find the screen we mostly reside in
		int topIntersectArea = 0;
		int bestScreen = 0;
		for(int i=0;i<screens.length;i++)
		{
			Rectangle curScreen = screens[i].getClientArea();
			curScreen.intersect(dim);
			int area = curScreen.width*curScreen.height;
			if(area > topIntersectArea)
			{
				bestScreen = i;
				topIntersectArea = area;
			}
		}

		Rectangle screen = screens[bestScreen].getClientArea();

		if (currentLoc.x-screen.x < 10)
			currentLoc.x = screen.x;
		else if (currentLoc.x-screen.x > screen.width - dim.width - 10)
			currentLoc.x = screen.x + screen.width - dim.width;
		if (currentLoc.y-screen.y < 10)
			currentLoc.y = screen.y;
		MiniBar mw = this;
		int height = 0;
		while (mw != null) {
			Shell s = mw.getShell();
			if (s.isDisposed())
				mw = null;
			else {
				height += s.getBounds().height - 1;
				mw = mw.getStucked();
				if (mw == this)
					mw = null;
			}
		}
		if (currentLoc.y-screen.y > screen.height - height - 10)
			currentLoc.y = screen.y + screen.height - height;

		MiniBarManager g_manager = MiniBarManager.getManager();
		try {
			g_manager.getMiniBarMonitor().enter();
			if (g_manager.countMiniBars() > 1) {
				Iterator itr = g_manager.getMiniBarIterator();
				while (itr.hasNext()) {
					MiniBar downloadBar = (MiniBar) itr.next();
					Point location = downloadBar.getShell().getLocation();
					// isn't the height always 10?
					// Gudy : No it depends on your system font.
					location.y += downloadBar.getShell().getBounds().height;
					//Stucking to someone else
					if (downloadBar != this && downloadBar.getStucked() == null
							|| downloadBar.getStucked() == this) {
						if (Math.abs(location.x - currentLoc.x) < 10
								&& location.y - currentLoc.y < 10
								&& location.y - currentLoc.y > 0) {
							downloadBar.setStucked(this);
							currentLoc.x = location.x;
							currentLoc.y = location.y - 1;
						}
					}
					//Un-stucking from someone
					if (downloadBar != this && downloadBar.getStucked() == this) {
						if (Math.abs(location.x - currentLoc.x) > 10
								|| Math.abs(location.y - currentLoc.y) > 10)
							downloadBar.setStucked(null);
					}
				}
			}
		}
		finally {
			g_manager.getMiniBarMonitor().exit();
		}

		splash.setLocation(currentLoc);
		MiniBar mwCurrent = this;
		while (mwCurrent != null) {
			currentLoc.y += mwCurrent.getShell().getBounds().height - 1;
			MiniBar mwChild = mwCurrent.getStucked();
			if (mwChild != null && mwChild != this) {
				Shell s = mwChild.getShell();
				if (s.isDisposed()) {
					mwCurrent.setStucked(null);
					mwCurrent = null;
				}
				else {
					mwCurrent = mwChild;
					mwCurrent.getShell().setLocation(currentLoc);
				}
			}
			else
				mwCurrent = null;
		}
	}



	//
	// These methods define the management of MiniBars.
	//
	public Shell getShell() {
		return this.splash;
	}

	public void setVisible(boolean visible) {
		splash.setVisible(visible);
	}

	public final boolean hasSameContext(MiniBar m) {
		return this.hasContext(m.getContextObject());
	}

	public final boolean hasContext(Object context) {
		Object my_context = this.getContextObject();
		if (my_context == null) {
			return context == null;
		}
		else {
			return my_context.equals(context);
		}
	}

	public MiniBar getStucked() {
		return this.stucked;
	}

	public void setStucked(MiniBar mw) {
		this.stucked = mw;
	}

	// Have to be in the SWT thread to do this.
	public final void forceSaveLocation() {
		if (!splash.isDisposed()) {
			this.storeLastLocation(splash.getLocation());
		}
	}

	public final void close() {
		if (!splash.isDisposed()) {
			Display display = splash.getDisplay();
			if (display != null && !display.isDisposed()) {
				display.asyncExec(new AERunnable() {
					@Override
					public void runSupport() {dispose();}
				});
			}
		}
		manager.unregister(this);
	}

	public void dispose() {
		if (!splash.isDisposed()) {
			this.forceSaveLocation();
			splash.dispose();
		}
		if (bold_font != null && !bold_font.isDisposed()) {bold_font.dispose();}
	}

	public final void refresh() {
		if (splash.isDisposed()) {return;}
		refresh0();
	}

	//
	// Subclass methods.
	//
	protected abstract void refresh0();
	protected abstract void beginConstruction();
	protected abstract Object getContextObject();

	public String[] getPluginMenuIdentifiers(Object[] context) {
		return null;
	}

	public Object[] getPluginMenuContextObjects() {
		return null;
	}

	protected Point getInitialLocation() {
		return null;
	}

	protected void
	doubleClick()
	{
	}

	protected void storeLastLocation(Point point) {
		// Do nothing.
	}
}
