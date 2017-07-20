/*
 * File    : BlockedIpsWindow.java
 * Created : 17 d�c. 2003}
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
package com.biglybt.ui.swt;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.*;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;

/**
 * @author Olivier
 *
 */
public class
BlockedIpsWindow
{
  static Core core;
  static Shell instance;

  private BlockedIpsWindow(){}

  public static void
  show(
  		Core _core,
		Display 		display,
		String 			ipsBlocked,
		String 			ipsBanned)
  {
      if(instance == null || instance.isDisposed())
      {
          instance = create(_core, display, ipsBlocked, ipsBanned);
          instance.addDisposeListener(new DisposeListener() {
              @Override
              public void widgetDisposed(DisposeEvent event) {
                  instance = null;
              }
          });
      }
      else
      {
          instance.open();
      }
  }

  private static Shell
  create(
  		Core _core,
		Display 		display,
		String 			ipsBlocked,
		String 			ipsBanned)
  {
  	core	= _core;

    final int styles;
    if(Constants.isOSX) {
        styles = SWT.SHELL_TRIM;
    }
    else {
        styles = SWT.DIALOG_TRIM | SWT.MAX | SWT.RESIZE | SWT.APPLICATION_MODAL;
    }

    final Shell window = com.biglybt.ui.swt.components.shell.ShellFactory.createShell(display,styles);
    Messages.setLanguageText(window,"ConfigView.section.ipfilter.list.title");
    Utils.setShellIcon(window);
    FormLayout layout = new FormLayout();
    try {
      layout.spacing = 5;
    } catch (NoSuchFieldError e) {
      /* Ignore for Pre 3.0 SWT.. */
    }
    layout.marginHeight = 5;
    layout.marginWidth = 5;
    window.setLayout(layout);
    FormData formData;

    	// text blocked area

    final StyledText textBlocked = new StyledText(window,SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    Button btnClear = new Button(window,SWT.PUSH);
    textBlocked.setEditable(false);

    final StyledText textBanned = new StyledText(window,SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    Button btnOk = new Button(window,SWT.PUSH);
    Button btnReset = new Button(window,SWT.PUSH);
    textBanned.setEditable(false);


    formData = new FormData();
    formData.left = new FormAttachment(0,0);
    formData.right = new FormAttachment(100,0);
    formData.top = new FormAttachment(0,0);
    formData.bottom = new FormAttachment(40,0);
    Utils.setLayoutData(textBlocked, formData);
    textBlocked.setText(ipsBlocked);


    // label blocked area

    Label	blockedInfo = new Label(window, SWT.NULL);
    Messages.setLanguageText(blockedInfo,"ConfigView.section.ipfilter.blockedinfo");
    formData = new FormData();
    formData.top = new FormAttachment(textBlocked);
    formData.right = new FormAttachment(btnClear);
    formData.left = new FormAttachment(0,0);
    Utils.setLayoutData(blockedInfo,  formData );

    	// clear button


    Messages.setLanguageText(btnClear,"Button.clear");
    formData = new FormData();
    formData.top = new FormAttachment(textBlocked);
    formData.right = new FormAttachment(95,0 );
    //formData.bottom = new FormAttachment(textBanned);
    formData.width = 70;
    Utils.setLayoutData(btnClear, formData);
    btnClear.addListener(SWT.Selection,new Listener() {

    @Override
    public void handleEvent(Event e) {

    	core.getIpFilterManager().getIPFilter().clearBlockedIPs();

    	textBlocked.setText( "" );
    }
    });


    	// text banned area
    formData = new FormData();
    formData.left = new FormAttachment(0,0);
    formData.right = new FormAttachment(100,0);
    formData.top = new FormAttachment(btnClear);
    formData.bottom = new FormAttachment(btnOk);
    Utils.setLayoutData(textBanned, formData);
    textBanned.setText(ipsBanned);

    	// label banned area

    Label	bannedInfo = new Label(window, SWT.NULL);
    Messages.setLanguageText(bannedInfo,"ConfigView.section.ipfilter.bannedinfo");
    formData = new FormData();
    formData.right = new FormAttachment(btnReset);
    formData.left = new FormAttachment(0,0);
    formData.bottom = new FormAttachment(100,0);
    Utils.setLayoutData(bannedInfo,  formData );

    	// reset button

    Messages.setLanguageText(btnReset,"Button.reset");
    formData = new FormData();
    formData.right = new FormAttachment(btnOk);
    formData.bottom = new FormAttachment(100,0);
    formData.width = 70;
    Utils.setLayoutData(btnReset, formData);
    btnReset.addListener(SWT.Selection,new Listener() {

    @Override
    public void handleEvent(Event e) {
    	core.getIpFilterManager().getIPFilter().clearBannedIps();
    	core.getIpFilterManager().getBadIps().clearBadIps();

    	textBanned.setText( "" );
    	}
    });
    	// ok button

    Messages.setLanguageText(btnOk,"Button.ok");
    formData = new FormData();
    formData.right = new FormAttachment(95,0);
    formData.bottom = new FormAttachment(100,0);
    formData.width = 70;
    Utils.setLayoutData(btnOk, formData);
    btnOk.addListener(SWT.Selection,new Listener() {

    @Override
    public void handleEvent(Event e) {
      window.dispose();
    }
    });

    window.setDefaultButton( btnOk );

    window.addListener(SWT.Traverse, new Listener() {
		@Override
		public void handleEvent(Event e) {
			if ( e.character == SWT.ESC){
			     window.dispose();
			 }
		}
    });

		if (!Utils.linkShellMetricsToConfig(window, "BlockedIpsWindow")) {
			window.setSize(620, 450);
			if (!Constants.isOSX)
				Utils.centreWindow(window);
		}
		window.layout();
    window.open();
    return window;
  }

  public static void
  showBlockedIps(
  		Core core,
  		Shell 		mainWindow)
  {
    StringBuilder sbBlocked = new StringBuilder();
    StringBuilder sbBanned = new StringBuilder();
    BlockedIp[] blocked = core.getIpFilterManager().getIPFilter().getBlockedIps();
    String inRange = MessageText.getString("ConfigView.section.ipfilter.list.inrange");
    String notInRange = MessageText.getString("ConfigView.section.ipfilter.list.notinrange");
    String bannedMessage = MessageText.getString( "ConfigView.section.ipfilter.list.banned" );
    String badDataMessage = MessageText.getString( "ConfigView.section.ipfilter.list.baddata" );

    for(int i=0;i<blocked.length;i++){
      BlockedIp bIp = blocked[i];
      if (!bIp.isLoggable()){
    	  continue;
      }
      sbBlocked.append(DisplayFormatters.formatTimeStamp(bIp.getBlockedTime()));
      sbBlocked.append("\t[");
      sbBlocked.append( bIp.getTorrentName() );
      sbBlocked.append("] \t");
      sbBlocked.append(bIp.getBlockedIp());
      IpRange range = bIp.getBlockingRange();
      if(range == null) {
        sbBlocked.append(' ');
        sbBlocked.append(notInRange);
        sbBlocked.append('\n');
      } else {
        sbBlocked.append(' ');
        sbBlocked.append(inRange);
        sbBlocked.append(range.toString());
        sbBlocked.append('\n');
      }
    }

    BannedIp[]	banned_ips = core.getIpFilterManager().getIPFilter().getBannedIps();

    for(int i=0;i<banned_ips.length;i++){
    	BannedIp bIp = banned_ips[i];
      sbBanned.append(DisplayFormatters.formatTimeStamp(bIp.getBanningTime()));
      sbBanned.append("\t[");
      sbBanned.append( bIp.getTorrentName() );
      sbBanned.append("] \t" );
      sbBanned.append( bIp.getIp());
      sbBanned.append( " " );
      sbBanned.append( bannedMessage );
      sbBanned.append( "\n");
    }

    BadIp[]	bad_ips = core.getIpFilterManager().getBadIps().getBadIps();
    for(int i=0;i<bad_ips.length;i++){
    	BadIp bIp = bad_ips[i];
        sbBanned.append(DisplayFormatters.formatTimeStamp(bIp.getLastTime()));
        sbBanned.append( "\t" );
        sbBanned.append( bIp.getIp());
        sbBanned.append( " " );
        sbBanned.append( badDataMessage );
        sbBanned.append( " " );
        sbBanned.append( bIp.getNumberOfWarnings());
        sbBanned.append( "\n" );
    }

    if(mainWindow == null || mainWindow.isDisposed())
      return;
    BlockedIpsWindow.show(core,mainWindow.getDisplay(),sbBlocked.toString(),sbBanned.toString());
  }
}
