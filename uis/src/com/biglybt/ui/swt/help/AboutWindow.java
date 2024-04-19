/*
 * File    : AboutWindow.java
 * Created : 18 d�c. 2003}
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
package com.biglybt.ui.swt.help;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.SWTThread;
import com.biglybt.ui.swt.mainwindow.SWTThreadAlreadyInstanciatedException;
import com.biglybt.update.CorePatchLevel;

/**
 * @author Olivier
 *
 */
public class AboutWindow {
	private final static String IMG_SPLASH = "logo_splash";

  static Image image;
  static AEMonitor	class_mon	= new AEMonitor( "AboutWindow" );
  private static Shell instance;
	private static Image imgSrc;
	private static int paintColorTo = 0;
	private static int paintColorDir = 2;

	private static Image imageToDispose;

  public static void show() {
  	Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				_show();
			}
		});
  }

  private static void _show() {
    if(instance != null)
    {
        instance.open();
        return;
    }

    paintColorTo = 0;

    final Shell window = ShellFactory.createMainShell(SWT.DIALOG_TRIM);
    Utils.setShellIcon(window);
    final Display display = window.getDisplay();

    window.setText(MessageText.getString("MainWindow.about.title") + " " + Constants.getCurrentVersion());
    GridData gridData;
    window.setLayout(new GridLayout(2, false));

    ImageLoader imageLoader = ImageLoader.getInstance();
    imgSrc = imageLoader.getImage(IMG_SPLASH);
    if (imgSrc != null) {
      int w = imgSrc.getBounds().width;
      int h = imgSrc.getBounds().height;

      Image imgGray = new Image(display, imageLoader.getImage(IMG_SPLASH),
					SWT.IMAGE_GRAY);
      imageLoader.releaseImage(IMG_SPLASH);

      Image image2 = new Image(display, w, h);
      GC gc = new GC(image2);
      gc.setBackground(Utils.getSkinnedBackground( window ));
      gc.fillRectangle(image2.getBounds());
      gc.dispose();
      imageToDispose = image = Utils.renderTransparency(display, image2, imgGray, new Point(0, 0), 180);
      image2.dispose();
      imgGray.dispose();
    }

    final Canvas labelImage = new Canvas(window, SWT.DOUBLE_BUFFERED);
    //labelImage.setImage(image);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
    gridData.horizontalSpan = 2;
    gridData.horizontalIndent = gridData.verticalIndent = 0;
    final Rectangle imgBounds = image.getBounds();
		final Rectangle boundsColor = imgSrc.getBounds();
		gridData.widthHint = 300;
    gridData.heightHint = imgBounds.height + imgBounds.y + 20;
		labelImage.setLayoutData(gridData);
    labelImage.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				try{
					Rectangle clipping = e.gc.getClipping();
					int ofs = (labelImage.getSize().x - boundsColor.width) / 2;
					if (paintColorTo > 0) {
						e.gc.drawImage(imgSrc, 0, 0, paintColorTo, boundsColor.height, ofs, 10, paintColorTo, boundsColor.height);
					}

					if (clipping.x + clipping.width > ofs + paintColorTo && imgBounds.width - paintColorTo - 1 > 0) {
						e.gc.drawImage(image,
								paintColorTo + 1, 0, imgBounds.width - paintColorTo - 1, imgBounds.height,
								paintColorTo + 1 + ofs, 10, imgBounds.width - paintColorTo - 1, imgBounds.height);
					}
				}catch( Throwable f ){
					// seen some 'argument not valid errors spewed here, couldn't track down
					// the cause though :( parg.
				}
			}
		});

    Group gInternet = Utils.createSkinnedGroup(window, SWT.NULL);
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    gridLayout.makeColumnsEqualWidth = true;
    gInternet.setLayout(gridLayout);
    Messages.setLanguageText(gInternet, "MainWindow.about.section.internet"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		gInternet.setLayoutData(gridData);

    Group gSys = Utils.createSkinnedGroup(window, SWT.NULL);
    gSys.setLayout(new GridLayout());
    Messages.setLanguageText(gSys, "MainWindow.about.section.system"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gridData.verticalSpan = 1;
		gSys.setLayoutData(gridData);

    String swt = "";
    if (Utils.isGTK) {
    	try {
    		swt = "/" + System.getProperty("org.eclipse.swt.internal.gtk.version");
			} catch (Throwable e1) {
				// TODO Auto-generated catch block
			}
    }
	  Point dpi = Utils.getDisplay().getDPI();
	  swt += ", zoom=" + Utils.getDeviceZoom() + ", dpi=" + (dpi.x == dpi.y ? dpi.x : dpi.x + "x" + dpi.y);

    Text txtSysInfo = new Text(gSys, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
    txtSysInfo.setBackground(Colors.getSystemColor(display, SWT.COLOR_WIDGET_BACKGROUND));

    String about_text =

    		"Java " + System.getProperty("java.version") + " (" + (Constants.is64Bit?64:32) + " bit)\n  "
    				+ System.getProperty("java.vendor") + "\n"
    				+ System.getProperty("java.home") + "\n\n"
    				+ "SWT v" + Utils.getSWTVersionAndRevision() + ", " + SWT.getPlatform() + swt + "\n"
    				+ Constants.OSName + " v"
    				+ Constants.OSVersion + ", "
    				+ Constants.OSArch + " (" + (Constants.isOS64Bit?64:32) + " bit)\n"
    				+ Constants.APP_NAME.charAt(0) + Constants.getCurrentVersion() + (Constants.SUBVERSION.length()==0?"":("-"+Constants.SUBVERSION)) + "/" + CorePatchLevel.getCurrentPatchLevel() + " "
    				+ COConfigurationManager.getStringParameter("ui") + " " + MessageText.getCurrentLocale();

    txtSysInfo.setText( about_text );
		gridData = new GridData(GridData.FILL_BOTH);
		txtSysInfo.setLayoutData(gridData);
    if (window.getCaret() != null)
    	window.getCaret().setVisible(false);

		final String[][] link = {
			{
				"homepage",
				"bugreports",
				"forumdiscussion",
				"wiki",
				"!" + MessageText.getString("ConfigView.section.plugins"),
				"!" + MessageText.getString("ConfigView.section.plugins") + " (Tor)",
			},
			{
				Constants.URL_CLIENT_HOME,
				Constants.URL_BUG_REPORTS,
				Constants.URL_FORUMS,
				Constants.URL_WIKI,
				Constants.PLUGINS_WEB_SITE,
				"https://xp5qetgwtj6w62sutz2xnjpx2l7j4z56mbt7xr7wmejerbg67g7nwfyd.onion"
			}
		};

	Button copy = new Button( gSys, SWT.PUSH );
	copy.setLayoutData( new GridData( GridData.HORIZONTAL_ALIGN_END ));
	copy.setText( MessageText.getString( "label.copy" ));
	copy.addListener( SWT.Selection,(ev)->{ClipboardCopy.copyToClipBoard(about_text);});
	
    for (int i = 0; i < link[0].length; i++) {
      final CLabel linkLabel = new CLabel(gInternet, SWT.NONE);
      if (link[0][i].startsWith("!")) {
        linkLabel.setText(link[0][i].substring(1));
      } else {
      	linkLabel.setText(MessageText.getString("MainWindow.about.internet." + link[0][i]));
      }
      linkLabel.setData(link[1][i]);
      linkLabel.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
      Utils.setLinkForeground(linkLabel);
      gridData = new GridData(GridData.FILL_HORIZONTAL);
      gridData.horizontalSpan = 1;
			linkLabel.setLayoutData(gridData);
      linkLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseDoubleClick(MouseEvent arg0) {
        	Utils.launch((String) ((CLabel) arg0.widget).getData());
        }
        @Override
        public void mouseUp(MouseEvent arg0) {
        	Utils.launch((String) ((CLabel) arg0.widget).getData());
        }
      });
      ClipboardCopy.addCopyToClipMenu( linkLabel );
    }

    Listener keyListener =  new Listener() {
      @Override
      public void handleEvent(Event e) {
        if(e.character == SWT.ESC) {
          window.dispose();
        }
      }
    };

    window.addListener(SWT.KeyUp,keyListener);

    window.pack();
    txtSysInfo.setFocus();
    Utils.centreWindow(window);
    window.open();

    instance = window;
    window.addDisposeListener(new DisposeListener() {
        @Override
        public void widgetDisposed(DisposeEvent event) {
            instance = null;
            disposeImage();
        }
    });

		final int maxX = image.getBounds().width;
		final int maxY = image.getBounds().height;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (image == null || image.isDisposed() || labelImage.isDisposed()) {
					return;
				}
				if (display.isDisposed()) {
					return;
				}
				paintColorTo += paintColorDir;

				Utils.execSWTThreadLater(7 * paintColorDir, this);

				int ofs = (labelImage.getSize().x - boundsColor.width) / 2;
				labelImage.redraw(paintColorTo - paintColorDir + ofs, 10, paintColorDir, maxY, true);

				if (paintColorTo >= maxX || paintColorTo <= 0) {
					paintColorTo = 0;
					//paintColorDir = (int) (Math.random() * 5) + 2;
					Image tmp = image;
					image = imgSrc;
					imgSrc = tmp;
				}
			}
    });

  }

  public static void
  disposeImage()
  {
  	try{
  		class_mon.enter();
			Utils.disposeSWTObjects(new Object[] {
				imageToDispose
			});
	    ImageLoader imageLoader = ImageLoader.getInstance();
	    imageLoader.releaseImage(IMG_SPLASH);
	    image = null;
	    imgSrc = null;
  	}finally{

  		class_mon.exit();
  	}
  }

  public static void main(String[] args) {
  	try {
  		Display display = new Display();
  		Colors.getInstance();
			SWTThread.createInstance(null);
			show();

			while (!display.isDisposed() && instance != null && !instance.isDisposed()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}

			if (!display.isDisposed()) {
				display.dispose();
			}
		} catch (SWTThreadAlreadyInstanciatedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
