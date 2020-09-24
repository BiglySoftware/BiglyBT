/*
 * File    : IpFilterEditor.java
 * Created : 8 oct. 2003 13:18:42
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

package com.biglybt.ui.swt.config;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

/**
 * @author Olivier
 *
 */
public class IpFilterEditor {

  Core core;

  boolean done = false;
  
  public
  IpFilterEditor(
	Core 		_core,
	Shell 		parent,
	IpRange 	existing_range,
	Runnable	run_when_done )
  {
  	core	= _core;

  	String existing_desc 	= existing_range==null?null:existing_range.getDescription();
  	String existing_start 	= existing_range==null?null:existing_range.getStartIp();
  	String existing_end		= existing_range==null?null:existing_range.getEndIp();
  	
  	IpRange new_v4 = core.getIpFilterManager().getIPFilter().createRange(1,false);
  	IpRange new_v6 = core.getIpFilterManager().getIPFilter().createRange(2,false);

    final Shell shell = ShellFactory.createShell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
    Messages.setLanguageText(shell,"ConfigView.section.ipfilter.editFilter");
    Utils.setShellIcon(shell);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    shell.setLayout(layout);

    Label label = new Label(shell, SWT.NULL);
    Messages.setLanguageText(label, "label.description");

    final Text textDescription = new Text(shell, SWT.BORDER);
    GridData gridData = new GridData();
    gridData.widthHint = 300;
    textDescription.setLayoutData(gridData);

    label = new Label(shell, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.section.ipfilter.start");

    final Text textStartIp = new Text(shell, SWT.BORDER);
    gridData = new GridData();
    gridData.widthHint = 120;
    textStartIp.setLayoutData(gridData);

    label = new Label(shell, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.section.ipfilter.end");

    final Text textEndIp = new Text(shell, SWT.BORDER);
    gridData = new GridData();
    gridData.widthHint = 120;
    textEndIp.setLayoutData(gridData);

    final Button ok = new Button(shell, SWT.PUSH);
    Messages.setLanguageText(ok, "Button.ok");
    shell.setDefaultButton(ok);

    ok.setEnabled(existing_range!=null);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    gridData.widthHint = 100;
    ok.setLayoutData(gridData);
    ok.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
    	if ( existing_range == null ){
    		new_v4.setDescription( textDescription.getText());
    		new_v4.setStartIp( textStartIp.getText());
    		new_v4.setEndIp( textEndIp.getText());
    		new_v4.checkValid();
    		if ( new_v4.isValid()){
    			core.getIpFilterManager().getIPFilter().addRange(new_v4);
    		}else{
    			new_v6.setDescription( textDescription.getText());
    			new_v6.setStartIp( textStartIp.getText());
    			new_v6.setEndIp( textEndIp.getText());
    			new_v6.checkValid();
       			core.getIpFilterManager().getIPFilter().addRange(new_v6);
    		}
    	}else{
    		existing_range.setDescription( textDescription.getText());
    		existing_range.setStartIp( textStartIp.getText());
    		existing_range.setEndIp( textEndIp.getText());
    		existing_range.checkValid();
    	}
 
    	done	= true;
    	
        shell.dispose();
      }
    });

    textStartIp.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent event) {
    	boolean valid = false;
      
    	if ( existing_range == null ){
    		new_v4.setStartIp( textStartIp.getText());
    		new_v4.checkValid();
    		valid = new_v4.isValid();
    		if ( !valid ){
    			new_v6.setStartIp( textStartIp.getText());
    			new_v6.checkValid();
        		valid = new_v6.isValid();
    		}
    	}else{
    		existing_range.setStartIp( textStartIp.getText());
    		existing_range.checkValid();
    		valid = existing_range.isValid();
    	}
        ok.setEnabled(valid);
      }
    });

    textEndIp.addModifyListener(new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent event) {
          	boolean valid = false;
            
        	if ( existing_range == null ){
        		new_v4.setEndIp( textEndIp.getText());
        		new_v4.checkValid();
        		valid = new_v4.isValid();
        		if ( !valid ){
        			new_v6.setEndIp( textEndIp.getText());
        			new_v6.checkValid();
            		valid = new_v6.isValid();
        		}
        	}else{
        		existing_range.setEndIp( textEndIp.getText());
        		existing_range.checkValid();
        		valid = existing_range.isValid();
        	}
            ok.setEnabled(valid);
          }
     });

    if (existing_range != null) {
          textDescription.setText(existing_range.getDescription());
          textStartIp.setText(existing_range.getStartIp());
          textEndIp.setText(existing_range.getEndIp());
    }

    shell.addListener(
    	SWT.Dispose, (ev)->{ 		
    		if ( !done && existing_range != null ){
    			existing_range.setDescription( existing_desc);
        		existing_range.setStartIp( existing_start );
        		existing_range.setEndIp( existing_end );
        		existing_range.checkValid();
    		} 
    		
    		if ( run_when_done != null ){
    			
    			run_when_done.run();
    		}
    	});
    
    shell.pack();
    Utils.centerWindowRelativeTo(shell, parent);
    shell.open();
  }

}
